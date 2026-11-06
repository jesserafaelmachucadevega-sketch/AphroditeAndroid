package com.aphrodite.feature.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aphrodite.core.database.dao.ModelDao
import com.aphrodite.core.database.entity.ModelEntity
import com.aphrodite.core.llm.EndpointRepository
import com.aphrodite.core.llm.LlmClient
import com.aphrodite.core.llm.model.LlmMessage
import com.aphrodite.core.llm.model.LlmRequest
import com.aphrodite.core.llm.model.LlmRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where one model's answer to the shared prompt has got to. */
enum class RunStatus { Running, Done, Failed }

data class CompareRun(
    val modelKey: String,
    val label: String,
    val endpointName: String,
    val status: RunStatus = RunStatus.Running,
    val text: String = "",
    val error: String? = null,
    val elapsedMs: Long = 0,
    val tokens: Long = 0,
    val costUsd: Double = 0.0,
)

/** One selectable model, flattened for the picker. */
data class ModelChoice(
    val key: String,
    val label: String,
    val endpointName: String,
)

data class CompareUiState(
    val prompt: String = "",
    val choices: List<ModelChoice> = emptyList(),
    val selected: List<String> = emptyList(),
    val runs: List<CompareRun> = emptyList(),
    val running: Boolean = false,
    val error: String? = null,
) {
    /** Three is the practical limit: past that the columns are too narrow to read. */
    val canRun: Boolean
        get() = !running && prompt.isNotBlank() && selected.size in 2..MAX_MODELS

    companion object {
        const val MAX_MODELS = 3
    }
}

/**
 * Runs one prompt against several models at once.
 *
 * The runs are independent and run concurrently: a comparison where the second model waits for the
 * first is three times slower for no gain, and the whole point is to see the answers next to each
 * other rather than one after another.
 *
 * Results are not saved. A comparison is about judging models against each other in the moment, and
 * filing each answer as a session would clutter the chat list with near-duplicates of the same
 * question.
 */
@HiltViewModel
class CompareViewModel @Inject constructor(
    private val models: ModelDao,
    private val endpoints: EndpointRepository,
    private val client: LlmClient,
) : ViewModel() {

    private val _state = MutableStateFlow(CompareUiState())
    val state: StateFlow<CompareUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            models.observeAll().collect { list ->
                _state.update { current ->
                    val choices = list.map { it.toChoice() }
                    current.copy(
                        choices = choices,
                        // Drop anything that has since been removed, so Run cannot target a model
                        // that no longer exists.
                        selected = current.selected.filter { key -> choices.any { it.key == key } },
                    )
                }
            }
        }
    }

    private fun ModelEntity.toChoice() = ModelChoice(
        key = id,
        label = displayName ?: modelId,
        endpointName = endpointId,
    )

    fun onPromptChange(prompt: String) = _state.update { it.copy(prompt = prompt) }

    fun toggleModel(key: String) {
        _state.update { current ->
            val selected = when {
                key in current.selected -> current.selected - key
                current.selected.size >= CompareUiState.MAX_MODELS -> current.selected
                else -> current.selected + key
            }
            current.copy(selected = selected, error = null)
        }
    }

    fun clearResults() = _state.update { it.copy(runs = emptyList(), error = null) }

    fun run() {
        val current = _state.value
        if (!current.canRun) return
        val prompt = current.prompt.trim()

        val targets = current.choices.filter { it.key in current.selected }
        _state.update { state ->
            state.copy(
                running = true,
                error = null,
                runs = targets.map { choice ->
                    CompareRun(
                        modelKey = choice.key,
                        label = choice.label,
                        endpointName = endpointOf(choice.key),
                    )
                },
            )
        }

        viewModelScope.launch {
            val rows = models.observeAll().first()
            targets.forEach { choice ->
                val entity = rows.firstOrNull { it.id == choice.key }
                if (entity == null) {
                    finish(choice.key, error = "That model is no longer available.")
                    return@forEach
                }
                // Launched rather than awaited so every model starts at the same moment.
                launch { runOne(entity, choice.label, prompt) }
            }
        }
    }

    private fun endpointOf(modelId: String): String =
        _state.value.choices.firstOrNull { it.key == modelId }?.endpointName.orEmpty()

    private suspend fun runOne(entity: ModelEntity, label: String, prompt: String) {
        val started = System.currentTimeMillis()
        val endpoint = endpoints.observeEnabledEndpoints().first().firstOrNull { it.id == entity.endpointId }
        if (endpoint == null) {
            finish(entity.id, error = "The endpoint for this model is disabled or missing.")
            return
        }

        val request = LlmRequest(
            baseUrl = endpoint.baseUrl,
            apiKey = endpoint.apiKeyRef?.let { endpoints.resolve(endpoint.id)?.apiKey },
            model = entity.modelId,
            providerId = endpoint.kind.id,
            messages = listOf(LlmMessage(role = LlmRole.User, content = prompt)),
        )

        runCatching { client.complete(request) }
            .onSuccess { result ->
                _state.update { state ->
                    state.copy(
                        runs = state.runs.map { run ->
                            if (run.modelKey != entity.id) {
                                run
                            } else {
                                run.copy(
                                    status = RunStatus.Done,
                                    text = result.content.ifBlank { "(empty reply)" },
                                    elapsedMs = System.currentTimeMillis() - started,
                                    tokens = result.usage.totalTokens.toLong(),
                                    costUsd = result.estimatedCostUsd,
                                )
                            }
                        },
                    )
                }
            }
            .onFailure { error ->
                finish(entity.id, error = error.message ?: "The request failed.")
            }
    }

    private fun finish(modelKey: String, error: String?) {
        _state.update { state ->
            val runs = state.runs.map { run ->
                if (run.modelKey == modelKey && run.status == RunStatus.Running) {
                    run.copy(status = RunStatus.Failed, error = error)
                } else {
                    run
                }
            }
            state.copy(runs = runs, running = runs.any { it.status == RunStatus.Running })
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}
