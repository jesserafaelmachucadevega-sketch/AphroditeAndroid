package com.aphrodite.core.llm.provider

import com.aphrodite.core.database.entity.ProviderKind
import com.aphrodite.core.llm.model.LlmMessage
import com.aphrodite.core.llm.model.LlmRequest
import com.aphrodite.core.llm.model.LlmRole
import com.aphrodite.core.llm.model.ModelDescriptor
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * What a provider sent in one stream frame, normalised.
 *
 * Adapters translate their vendor payload into these, and the client assembles them into the
 * app's stream vocabulary. Keeping the vendor shapes out of the client is what lets one loop
 * drive every provider.
 */
sealed interface ProviderDelta {
    data class TextDelta(val text: String) : ProviderDelta
    data class ThinkingDelta(val text: String) : ProviderDelta

    /** Tool call fragments arrive incrementally and are joined by index. */
    data class ToolCallDelta(
        val index: Int,
        val id: String? = null,
        val name: String? = null,
        val argumentsDelta: String? = null,
    ) : ProviderDelta

    data class Usage(
        val promptTokens: Int = 0,
        val completionTokens: Int = 0,
        val reasoningTokens: Int = 0,
    ) : ProviderDelta

    /** The provider answered with a different model than requested. */
    data class ModelActual(val model: String) : ProviderDelta

    data class Finish(val reason: String?) : ProviderDelta

    data class Error(val message: String, val status: Int? = null) : ProviderDelta
}

/**
 * Translates between the app's request shape and one provider family's wire format.
 *
 * Everything that differs between providers lives behind this interface: the request body, the
 * authentication headers, how reasoning is separated from visible text, how tool calls are
 * framed, and what the model list looks like.
 */
interface ProviderAdapter {

    val kind: ProviderKind

    /** Builds the streaming HTTP request for [request]. */
    fun buildStreamRequest(request: LlmRequest): Request

    /**
     * Builds a non-streaming request, used where a provider or a call does not need streaming.
     */
    fun buildRequest(request: LlmRequest): Request

    /** Parses one decoded SSE payload into normalised deltas. */
    fun parseStreamPayload(payload: JsonObject): List<ProviderDelta>

    /** Parses a complete non-streaming response body. */
    fun parseResponse(payload: JsonObject): ProviderDelta

    /** Fetches the model list from an endpoint. */
    suspend fun listModels(
        client: OkHttpClient,
        baseUrl: String,
        apiKey: String?,
        extraHeaders: Map<String, String> = emptyMap(),
    ): List<ModelDescriptor>

    /** Provider-specific default path for the chat endpoint, relative to the base URL. */
    val chatPath: String get() = "/chat/completions"
}

/** Helpers shared by the adapters. */
internal object AdapterSupport {

    /** Joins a base URL and a path without doubling or dropping the separator. */
    fun url(baseUrl: String, path: String): String {
        val base = baseUrl.trimEnd('/')
        val suffix = if (path.startsWith("/")) path else "/$path"
        // A base URL that already ends in the full path (some users paste it that way) wins.
        return if (base.endsWith(suffix)) base else base + suffix
    }

    /**
     * Reduces a message list to a single system prompt string plus the remaining turns.
     *
     * Providers differ on whether system messages are a separate field or just the first turn;
     * this lets each adapter place them where it needs to.
     */
    fun splitSystem(messages: List<LlmMessage>): Pair<String?, List<LlmMessage>> {
        val systemText = messages
            .filter { it.role == LlmRole.System }
            .joinToString("\n\n") { it.text }
            .ifBlank { null }
        return systemText to messages.filterNot { it.role == LlmRole.System }
    }

    /** Normalises a base URL that may or may not include a version segment. */
    fun normalizeBase(baseUrl: String, fallback: String): String =
        baseUrl.trim().ifEmpty { fallback }.trimEnd('/')
}
