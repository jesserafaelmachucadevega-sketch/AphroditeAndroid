package com.aphrodite.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aphrodite.core.database.entity.ChatMessageEntity
import com.aphrodite.core.database.entity.MessageRole
import com.aphrodite.core.database.entity.SessionEntity
import com.aphrodite.core.llm.LlmClient
import com.aphrodite.core.llm.stream.LlmEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Everything the chat surface renders. */
data class ChatUiState(
    val sessions: List<SessionEntity> = emptyList(),
    val activeSession: SessionEntity? = null,
    val messages: List<ChatMessageEntity> = emptyList(),
    val input: String = "",
    /** Answer text accumulated so far for the in-flight turn. */
    val streamingText: String = "",
    val streamingThinking: String = "",
    val isStreaming: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    /** False until a provider is configured, so the empty state can point at Settings. */
    val hasProvider: Boolean = true,
    /** Every discovered model, free ones first. */
    val allModels: List<com.aphrodite.core.database.entity.ModelEntity> = emptyList(),
    val allEndpoints: List<com.aphrodite.core.database.entity.ModelEndpointEntity> = emptyList(),
    val showModelPicker: Boolean = false,
    val modelQuery: String = "",
) {
    val canSend: Boolean get() = !isStreaming && input.isNotBlank()

    /** The model the active session will use. */
    val currentModel: String? get() = activeSession?.model

    /** Models after applying the picker's filter. */
    val visibleModels: List<com.aphrodite.core.database.entity.ModelEntity>
        get() = if (modelQuery.isBlank()) {
            allModels
        } else {
            allModels.filter { it.modelId.contains(modelQuery, ignoreCase = true) }
        }

    /** Sessions after applying the search box, which filters locally. */
    val visibleSessions: List<SessionEntity>
        get() = if (searchQuery.isBlank()) {
            sessions
        } else {
            sessions.filter { session ->
                session.name.contains(searchQuery, ignoreCase = true) ||
                    session.folder?.contains(searchQuery, ignoreCase = true) == true
            }
        }
}

/**
 * Drives one conversation at a time.
 *
 * The transcript shown while a reply streams is the accumulated text rather than the database
 * rows, so the partial answer appears immediately and is only committed once the provider
 * finishes.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val client: LlmClient,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var streamJob: Job? = null
    private var messagesJob: Job? = null
    private var observedSessionId: String? = null

    init {
        viewModelScope.launch {
            repository.observeHasProvider().collect { hasProvider ->
                _state.update { it.copy(hasProvider = hasProvider) }
            }
        }
        viewModelScope.launch {
            repository.observeAllModels().collect { models ->
                _state.update { it.copy(allModels = models) }
            }
        }
        viewModelScope.launch {
            repository.observeEndpoints().collect { endpoints ->
                _state.update { it.copy(allEndpoints = endpoints) }
            }
        }
        viewModelScope.launch {
            repository.observeSessions().collect { sessions ->
                val previousActiveId = _state.value.activeSession?.id
                val active = sessions.firstOrNull { it.id == previousActiveId } ?: sessions.firstOrNull()
                _state.update { it.copy(sessions = sessions, activeSession = active) }

                // Follow the active session when the list changes underneath us, but do not
                // resubscribe when the same session is re-emitted.
                active?.let { session ->
                    if (observedSessionId != session.id) observeMessages(session.id)
                }
            }
        }
    }

    fun onInputChange(value: String) = _state.update { it.copy(input = value, error = null) }

    fun onSearchChange(value: String) = _state.update { it.copy(searchQuery = value) }

    fun toggleModelPicker() = _state.update {
        it.copy(showModelPicker = !it.showModelPicker, modelQuery = "")
    }

    fun onModelQueryChange(value: String) = _state.update { it.copy(modelQuery = value) }

    /** Points the active session at a different model. */
    fun selectModel(endpointId: String, modelId: String) {
        val session = _state.value.activeSession ?: return
        viewModelScope.launch { repository.setSessionModel(session.id, modelId, endpointId) }
        _state.update { it.copy(showModelPicker = false, modelQuery = "", error = null) }
    }

    fun selectSession(sessionId: String) {
        if (_state.value.activeSession?.id == sessionId) return
        stopStreaming()
        val session = _state.value.sessions.firstOrNull { it.id == sessionId } ?: return
        _state.update { it.copy(activeSession = session, streamingText = "", streamingThinking = "", error = null) }
        observeMessages(sessionId)
    }

    fun newSession() {
        stopStreaming()
        viewModelScope.launch {
            val selection = repository.defaultSelection()
            val session = repository.createSession(
                model = selection?.second,
                endpointId = selection?.first,
            )
            _state.update { it.copy(activeSession = session, error = null) }
            observeMessages(session.id)
        }
    }

    fun renameSession(sessionId: String, name: String) {
        viewModelScope.launch { repository.renameSession(sessionId, name) }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            if (_state.value.activeSession?.id == sessionId) {
                stopStreaming()
                _state.update { it.copy(activeSession = null, messages = emptyList()) }
            }
            repository.deleteSession(sessionId)
        }
    }

    fun archiveSession(sessionId: String, archived: Boolean) {
        viewModelScope.launch { repository.setArchived(sessionId, archived) }
    }

    fun clearMessages() {
        val sessionId = _state.value.activeSession?.id ?: return
        viewModelScope.launch { repository.clearMessages(sessionId) }
    }

    /** Sends the composer contents as the next turn. */
    fun send() {
        val session = _state.value.activeSession ?: return
        val text = _state.value.input.trim()
        if (text.isEmpty() || _state.value.isStreaming) return

        if (session.endpointId == null || session.model == null) {
            _state.update { it.copy(error = "Choose a model before sending. Add a provider in Settings.") }
            return
        }

        _state.update { it.copy(input = "", isStreaming = true, streamingText = "", streamingThinking = "", error = null) }

        streamJob = viewModelScope.launch {
            repository.append(session.id, MessageRole.User, text)

            val request = repository.buildRequest(session.id)
            if (request == null) {
                _state.update { it.copy(isStreaming = false, error = "Could not build the request for this model") }
                return@launch
            }

            val answer = StringBuilder()
            val thinking = StringBuilder()
            var failure: String? = null

            client.stream(request).collect { event ->
                when (event) {
                    is LlmEvent.Delta -> {
                        answer.append(event.text)
                        _state.update { it.copy(streamingText = answer.toString()) }
                    }

                    is LlmEvent.Thinking -> {
                        thinking.append(event.text)
                        _state.update { it.copy(streamingThinking = thinking.toString()) }
                    }

                    is LlmEvent.FinalResponse -> {
                        // A complete replacement supersedes whatever was streamed.
                        answer.clear()
                        answer.append(event.content)
                        _state.update { it.copy(streamingText = answer.toString()) }
                    }

                    is LlmEvent.Metrics -> {
                        val tokens = event.usage.totalTokens
                        if (tokens > 0) repository.recordUsage(session.id, tokens, 0.0)
                    }

                    is LlmEvent.Failed -> failure = event.message

                    LlmEvent.Cancelled -> failure = null

                    else -> Unit
                }
            }

            val text = answer.toString()
            when {
                text.isNotBlank() -> {
                    repository.append(
                        sessionId = session.id,
                        role = MessageRole.Assistant,
                        content = text,
                        thinking = thinking.toString().ifBlank { null },
                        model = request.model,
                    )
                }
                failure != null -> {
                    _state.update { it.copy(error = failure) }
                }
                else -> _state.update { it.copy(error = "The model returned nothing") }
            }

            _state.update { it.copy(isStreaming = false, streamingText = "", streamingThinking = "") }
        }
    }

    /** Stops the in-flight turn, keeping whatever the model had produced so far. */
    fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
        val session = _state.value.activeSession
        val partial = _state.value.streamingText
        if (session != null && partial.isNotBlank()) {
            viewModelScope.launch {
                repository.append(session.id, MessageRole.Assistant, partial)
            }
        }
        _state.update { it.copy(isStreaming = false, streamingText = "", streamingThinking = "") }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun retryLast() {
        val messages = _state.value.messages
        val lastUser = messages.lastOrNull { it.role == MessageRole.User } ?: return
        val sessionId = _state.value.activeSession?.id ?: return
        viewModelScope.launch {
            repository.truncateFrom(sessionId, lastUser.id)
            _state.update { it.copy(input = lastUser.content) }
            send()
        }
    }

    private fun observeMessages(sessionId: String) {
        messagesJob?.cancel()
        observedSessionId = sessionId
        messagesJob = viewModelScope.launch {
            repository.observeMessages(sessionId).collect { messages ->
                if (_state.value.activeSession?.id == sessionId) {
                    _state.update { it.copy(messages = messages) }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        streamJob?.cancel()
        messagesJob?.cancel()
    }
}
