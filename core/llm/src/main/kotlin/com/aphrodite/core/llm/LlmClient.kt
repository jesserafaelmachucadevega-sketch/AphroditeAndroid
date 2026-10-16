package com.aphrodite.core.llm

import com.aphrodite.core.database.entity.ProviderKind
import com.aphrodite.core.llm.model.LlmRequest
import com.aphrodite.core.llm.model.LlmResult
import com.aphrodite.core.llm.model.LlmUsage
import com.aphrodite.core.llm.model.ToolCall
import com.aphrodite.core.llm.provider.AnthropicAdapter
import com.aphrodite.core.llm.provider.GeminiAdapter
import com.aphrodite.core.llm.provider.OpenAiAdapter
import com.aphrodite.core.llm.provider.ProviderAdapter
import com.aphrodite.core.llm.provider.ProviderDelta
import com.aphrodite.core.llm.provider.ProviderHttpException
import com.aphrodite.core.llm.stream.LlmEvent
import com.aphrodite.core.network.HttpClientFactory
import com.aphrodite.core.network.SseClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs model requests against any configured provider.
 *
 * The client owns exactly one thing that adapters do not: assembling fragmented provider output
 * into the app's stream vocabulary. Tool calls in particular arrive in pieces — an index, then a
 * name, then argument fragments across many frames — and are only complete once the provider
 * says it has finished.
 */
@Singleton
class LlmClient @Inject constructor(
    private val httpFactory: HttpClientFactory,
    private val adapters: ProviderAdapterRegistry,
) {

    /** Streams a request, emitting normalised events as they arrive. */
    fun stream(request: LlmRequest): Flow<LlmEvent> = flow {
        val adapter = adapters.forKind(ProviderKind.fromId(request.providerId))
        val sse = SseClient(httpFactory.streaming)

        val assembler = StreamAssembler()

        try {
            sse.stream(adapter.buildStreamRequest(request)).collect { frame ->
                if (frame.data == DONE_SENTINEL) return@collect

                val payload = runCatching { JSON.parseToJsonElement(frame.data).jsonObject }.getOrNull()
                if (payload == null) return@collect

                for (delta in adapter.parseStreamPayload(payload)) {
                    assembler.accept(delta)?.forEach { emit(it) }
                }
            }
            // The socket closed; flush anything the provider left buffered.
            assembler.finish()?.forEach { emit(it) }
        } catch (e: CancellationException) {
            emit(LlmEvent.Cancelled)
            throw e
        } catch (e: ProviderHttpException) {
            emit(LlmEvent.Failed(e.body.ifBlank { "Provider returned HTTP ${e.status}" }, e.status))
        } catch (e: Exception) {
            emit(LlmEvent.Failed(e.message ?: "The request failed"))
        }
    }.flowOn(Dispatchers.IO)

    /** Runs a request without streaming, for probes and short utility calls. */
    suspend fun complete(request: LlmRequest): LlmResult {
        val adapter = adapters.forKind(ProviderKind.fromId(request.providerId))
        val call = httpFactory.standard.newCall(adapter.buildRequest(request))
        call.execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ProviderHttpException(response.code, body.take(500))
            val payload = runCatching { JSON.parseToJsonElement(body).jsonObject }.getOrNull()
                ?: return LlmResult(content = "", model = request.model)
            return when (val delta = adapter.parseResponse(payload)) {
                is ProviderDelta.TextDelta ->
                    LlmResult(content = delta.text, model = request.model)

                is ProviderDelta.Error ->
                    throw ProviderHttpException(502, delta.message)

                else -> LlmResult(content = "", model = request.model)
            }
        }
    }

    /** Fetches the models an endpoint advertises. */
    suspend fun listModels(
        kind: ProviderKind,
        baseUrl: String,
        apiKey: String?,
        headers: Map<String, String> = emptyMap(),
    ) = adapters.forKind(kind).listModels(httpFactory.standard, baseUrl, apiKey, headers)

    private companion object {
        const val DONE_SENTINEL = "[DONE]"
    }
}

/**
 * Reassembles fragmented provider output into complete events.
 *
 * Returns the events produced by one delta, or null when the delta only updated internal state.
 */
private class StreamAssembler {

    private val toolCalls = linkedMapOf<Int, ToolCallBuilder>()
    private var totalUsage = LlmUsage()
    private var latestUsage = LlmUsage()
    private var sawFinish = false

    fun accept(delta: ProviderDelta): List<LlmEvent>? = when (delta) {
        is ProviderDelta.TextDelta ->
            listOf(LlmEvent.Delta(delta.text))

        is ProviderDelta.ThinkingDelta ->
            listOf(LlmEvent.Thinking(delta.text))

        is ProviderDelta.ModelActual ->
            listOf(LlmEvent.ModelActual(requested = "", actual = delta.model))

        is ProviderDelta.ToolCallDelta -> {
            val builder = toolCalls.getOrPut(delta.index) { ToolCallBuilder(delta.index) }
            delta.id?.let { builder.id = it }
            delta.name?.let { builder.name = it }
            delta.argumentsDelta?.let { builder.arguments.append(it) }
            null
        }

        is ProviderDelta.Usage -> {
            // Providers report usage cumulatively in some payloads and per-chunk in others;
            // taking the latest non-zero value avoids double counting either way.
            latestUsage = LlmUsage(
                promptTokens = maxOf(latestUsage.promptTokens, delta.promptTokens),
                completionTokens = maxOf(latestUsage.completionTokens, delta.completionTokens),
                reasoningTokens = maxOf(latestUsage.reasoningTokens, delta.reasoningTokens),
            )
            totalUsage += delta.toUsage()
            null
        }

        is ProviderDelta.Finish -> {
            sawFinish = true
            null
        }

        is ProviderDelta.Error ->
            listOf(LlmEvent.Failed(delta.message, delta.status))
    }

    fun finish(): List<LlmEvent>? {
        val events = mutableListOf<LlmEvent>()

        val calls = toolCalls.values
            .mapNotNull { it.build() }
        if (calls.isNotEmpty()) events += LlmEvent.ToolCallsRequested(calls)

        val reported = if (latestUsage.totalTokens > 0) latestUsage else totalUsage
        if (reported.totalTokens > 0 || reported.promptTokens > 0) {
            events += LlmEvent.Metrics(reported)
        }

        if (sawFinish || calls.isNotEmpty()) events += LlmEvent.Completed
        return events.ifEmpty { null }
    }
}

private fun ProviderDelta.Usage.toUsage(): LlmUsage {
    val total = promptTokens + completionTokens + reasoningTokens
    return LlmUsage(promptTokens, completionTokens, reasoningTokens, total)
}

private class ToolCallBuilder(val index: Int) {
    var id: String? = null
    var name: String? = null
    val arguments = StringBuilder()

    fun build(): ToolCall? {
        val resolvedName = name ?: return null
        return ToolCall(
            // Providers that stream arguments without an id still need a stable handle.
            id = id ?: "call_$index",
            name = resolvedName,
            arguments = arguments.toString().ifBlank { "{}" },
        )
    }
}

/** Maps a provider family to the adapter that speaks its wire format. */
@Singleton
class ProviderAdapterRegistry @Inject constructor() {

    private val openAiShaped = OpenAiAdapter()
    private val anthropic = AnthropicAdapter()
    private val gemini = GeminiAdapter()

    fun forKind(kind: ProviderKind): ProviderAdapter = when {
        kind == ProviderKind.Anthropic -> anthropic
        kind == ProviderKind.Gemini -> gemini
        else -> openAiShaped
    }
}

/** Shared JSON parsing for the client. */
private val JSON = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}
