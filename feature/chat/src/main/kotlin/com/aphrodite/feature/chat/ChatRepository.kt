package com.aphrodite.feature.chat

import com.aphrodite.core.database.dao.ChatMessageDao
import com.aphrodite.core.database.dao.ModelEndpointDao
import com.aphrodite.core.database.dao.PresetDao
import com.aphrodite.core.database.dao.SessionDao
import com.aphrodite.core.database.entity.ChatMessageEntity
import com.aphrodite.core.database.entity.MessageRole
import com.aphrodite.core.database.entity.SessionEntity
import com.aphrodite.core.llm.EndpointRepository
import com.aphrodite.core.llm.model.GenerationSettings
import com.aphrodite.core.llm.model.LlmMessage
import com.aphrodite.core.llm.model.LlmRequest
import com.aphrodite.core.llm.model.LlmRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns conversations: the session list, the transcript, and turning a session's history into a
 * request a provider can answer.
 */
@Singleton
class ChatRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val messageDao: ChatMessageDao,
    private val presetDao: PresetDao,
    private val endpointDao: ModelEndpointDao,
    private val endpoints: EndpointRepository,
) {
    fun observeSessions(): Flow<List<SessionEntity>> = sessionDao.observeActive()

    /**
     * True once at least one provider is configured.
     *
     * The chat surface uses this to tell a first-time user where to go instead of showing an
     * empty box with no explanation.
     */
    fun observeHasProvider(): Flow<Boolean> = endpointDao.observeEnabled().map { it.isNotEmpty() }

    /** Every model across every endpoint, free ones first, for the in-chat picker. */
    fun observeAllModels(): Flow<List<com.aphrodite.core.database.entity.ModelEntity>> =
        endpoints.observeAllModels()

    fun observeEndpoints(): Flow<List<com.aphrodite.core.database.entity.ModelEndpointEntity>> =
        endpoints.observeEndpoints()

    fun observeArchived(): Flow<List<SessionEntity>> = sessionDao.observeArchived()

    fun observeFolders(): Flow<List<String>> = sessionDao.observeFolders()

    fun observeMessages(sessionId: String): Flow<List<ChatMessageEntity>> =
        messageDao.observeForSession(sessionId)

    fun observeSession(sessionId: String): Flow<SessionEntity?> = sessionDao.observeById(sessionId)

    suspend fun session(sessionId: String): SessionEntity? = sessionDao.getById(sessionId)

    suspend fun search(query: String): List<SessionEntity> = sessionDao.search(query)

    /** Creates a conversation and returns it. */
    suspend fun createSession(
        name: String? = null,
        model: String? = null,
        endpointId: String? = null,
    ): SessionEntity {
        val now = System.currentTimeMillis()
        val session = SessionEntity(
            id = UUID.randomUUID().toString(),
            name = name?.takeIf { it.isNotBlank() } ?: "New chat",
            model = model,
            endpointId = endpointId,
            createdAt = now,
            updatedAt = now,
        )
        sessionDao.upsert(session)
        return session
    }

    suspend fun deleteSession(sessionId: String) = sessionDao.delete(sessionId)

    suspend fun renameSession(sessionId: String, name: String) =
        sessionDao.rename(sessionId, name, System.currentTimeMillis())

    suspend fun setArchived(sessionId: String, archived: Boolean) =
        sessionDao.setArchived(sessionId, archived, System.currentTimeMillis())

    suspend fun setImportant(sessionId: String, important: Boolean) =
        sessionDao.setImportant(sessionId, important, System.currentTimeMillis())

    suspend fun setSessionModel(sessionId: String, model: String?, endpointId: String?) =
        sessionDao.setModel(sessionId, model, endpointId, System.currentTimeMillis())

    suspend fun clearMessages(sessionId: String) {
        messageDao.deleteForSession(sessionId)
        messageDao.refreshSessionStats(sessionId, System.currentTimeMillis())
    }

    /** Removes [messageId] and every message that followed it. */
    suspend fun truncateFrom(sessionId: String, messageId: String) {
        messageDao.truncateFrom(sessionId, messageId)
        messageDao.refreshSessionStats(sessionId, System.currentTimeMillis())
    }

    suspend fun deleteMessage(sessionId: String, messageId: String) {
        messageDao.delete(messageId)
        messageDao.refreshSessionStats(sessionId, System.currentTimeMillis())
    }

    /**
     * Appends a message and refreshes the session counters.
     *
     * The session name follows the first user message, which is how the upstream app names
     * conversations and is friendlier than leaving everything "New chat".
     */
    suspend fun append(
        sessionId: String,
        role: MessageRole,
        content: String,
        thinking: String? = null,
        model: String? = null,
        tokens: Int = 0,
        costUsd: Double = 0.0,
        metadataJson: String? = null,
    ): ChatMessageEntity {
        val now = System.currentTimeMillis()
        val message = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = role,
            content = content,
            thinking = thinking,
            model = model,
            tokens = tokens,
            costUsd = costUsd,
            metadataJson = metadataJson,
            createdAt = now,
        )
        messageDao.upsert(message)
        messageDao.refreshSessionStats(sessionId, now)

        if (role == MessageRole.User) {
            val session = sessionDao.getById(sessionId)
            if (session != null && (session.name == "New chat" || session.name.isBlank())) {
                sessionDao.rename(sessionId, deriveTitle(content), now)
            }
        }
        return message
    }

    suspend fun updateMessage(message: ChatMessageEntity) {
        messageDao.update(message)
        messageDao.refreshSessionStats(message.sessionId, System.currentTimeMillis())
    }

    suspend fun message(messageId: String): ChatMessageEntity? = messageDao.getById(messageId)

    /** Adds a completed exchange's token and cost totals to the session. */
    suspend fun recordUsage(sessionId: String, tokens: Int, costUsd: Double) {
        val session = sessionDao.getById(sessionId) ?: return
        sessionDao.upsert(
            session.copy(
                totalTokens = session.totalTokens + tokens,
                totalCostUsd = session.totalCostUsd + costUsd,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Builds the request for the next turn.
     *
     * History is sent oldest first, the active preset contributes its system prompt and sampling
     * defaults, and per-session overrides win over the preset.
     */
    suspend fun buildRequest(
        sessionId: String,
        presetId: String? = null,
        historyLimit: Int = 200,
    ): LlmRequest? {
        val session = sessionDao.getById(sessionId) ?: return null
        val endpointId = session.endpointId ?: return null
        val model = session.model ?: return null

        val resolved = endpoints.resolve(endpointId) ?: return null
        val preset = presetId?.let { presetDao.getById(it) }
            ?: presetDao.builtins().firstOrNull()

        val history = messageDao.forSession(sessionId).takeLast(historyLimit)

        val messages = buildList {
            preset?.systemPrompt?.takeIf { it.isNotBlank() }?.let {
                add(LlmMessage(role = LlmRole.System, content = it))
            }
            history.forEach { entry ->
                add(
                    LlmMessage(
                        role = when (entry.role) {
                            MessageRole.User -> LlmRole.User
                            MessageRole.Assistant -> LlmRole.Assistant
                            MessageRole.System -> LlmRole.System
                            MessageRole.Tool -> LlmRole.Tool
                        },
                        content = entry.content,
                    ),
                )
            }
        }

        return LlmRequest(
            baseUrl = resolved.endpoint.baseUrl,
            apiKey = resolved.apiKey,
            model = model,
            providerId = resolved.endpoint.kind.id,
            messages = messages,
            settings = GenerationSettings(
                temperature = preset?.temperature,
                topP = preset?.topP,
                maxOutputTokens = preset?.maxOutputTokens,
                reasoningEffort = preset?.reasoningEffort,
            ),
        )
    }

    /** The endpoint and model most recently available, used when starting a new chat. */
    suspend fun defaultSelection(): Pair<String, String>? {
        val endpoint = endpointDao.observeEnabled().first().firstOrNull() ?: return null
        val model = endpoints.modelsFor(endpoint.id).firstOrNull() ?: return null
        return endpoint.id to model.modelId
    }

    private fun deriveTitle(content: String): String {
        val firstLine = content.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return when {
            firstLine.isEmpty() -> "New chat"
            firstLine.length <= 60 -> firstLine
            else -> firstLine.take(57).trimEnd() + "…"
        }
    }
}
