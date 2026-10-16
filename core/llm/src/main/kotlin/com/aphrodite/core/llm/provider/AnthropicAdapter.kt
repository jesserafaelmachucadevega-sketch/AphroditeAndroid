package com.aphrodite.core.llm.provider

import com.aphrodite.core.database.entity.ProviderKind
import com.aphrodite.core.llm.model.ContentBlock
import com.aphrodite.core.llm.model.LlmMessage
import com.aphrodite.core.llm.model.LlmRequest
import com.aphrodite.core.llm.model.LlmRole
import com.aphrodite.core.llm.model.ModelDescriptor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Anthropic's Messages API.
 *
 * Differs from the OpenAI shape in three ways that matter: the system prompt is a top-level
 * field rather than a message, tool definitions use `input_schema` rather than `parameters`,
 * and tool results are `tool_result` content blocks on a user turn rather than their own role.
 */
class AnthropicAdapter : ProviderAdapter {

    override val kind: ProviderKind = ProviderKind.Anthropic
    override val chatPath: String get() = "/messages"

    override fun buildStreamRequest(request: LlmRequest): Request = build(request, stream = true)

    override fun buildRequest(request: LlmRequest): Request = build(request, stream = false)

    private fun build(request: LlmRequest, stream: Boolean): Request {
        val (system, turns) = AdapterSupport.splitSystem(request.messages)

        val body = buildJsonObject {
            put("model", request.model)
            // Anthropic requires max_tokens; fall back to a sane ceiling rather than omitting it.
            put("max_tokens", request.settings.maxOutputTokens ?: DEFAULT_MAX_TOKENS)
            system?.let { put("system", it) }
            put("messages", buildMessages(turns))
            if (stream) put("stream", true)

            // Opus 4.7 and later reject sampling controls outright.
            if (!omitsSamplingControls(request.model)) {
                request.settings.temperature?.let { put("temperature", it.coerceIn(0.0, 1.0)) }
                request.settings.topP?.let { put("top_p", it) }
            }

            if (request.tools.isNotEmpty()) {
                putJsonArray("tools") {
                    request.tools.forEach { tool ->
                        add(
                            buildJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("input_schema", tool.parameters)
                            },
                        )
                    }
                }
            }

            // Extended thinking is opt-in and needs a budget.
            if (request.settings.thinkingMode == "on") {
                putJsonObject("thinking") {
                    put("type", "enabled")
                    put("budget_tokens", THINKING_BUDGET_TOKENS)
                }
            }
        }

        val builder = Request.Builder()
            .url(AdapterSupport.url(AdapterSupport.normalizeBase(request.baseUrl, "https://api.anthropic.com"), "/v1/messages"))
            .header("Content-Type", "application/json")
            .header("Accept", if (stream) "text/event-stream" else "application/json")
            .header("anthropic-version", ANTHROPIC_VERSION)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))

        request.apiKey?.takeIf { it.isNotBlank() }?.let { builder.header("x-api-key", it) }
        request.headers.forEach { (name, value) -> builder.header(name, value) }

        return builder.build()
    }

    private fun buildMessages(messages: List<LlmMessage>): JsonArray = buildJsonArray {
        messages.forEach { message ->
            when (message.role) {
                LlmRole.Tool -> add(
                    buildJsonObject {
                        put("role", "user")
                        putJsonArray("content") {
                            add(
                                buildJsonObject {
                                    put("type", "tool_result")
                                    put("tool_use_id", message.toolCallId ?: "")
                                    put("content", message.text)
                                },
                            )
                        }
                    },
                )

                else -> add(
                    buildJsonObject {
                        put("role", if (message.role == LlmRole.Assistant) "assistant" else "user")
                        put("content", buildContent(message))
                    },
                )
            }
        }
    }

    private fun buildContent(message: LlmMessage): JsonArray {
        val parts = mutableListOf<JsonObject>()

        // Assistant turns that requested tools must echo the tool_use blocks back.
        message.toolCalls.forEach { call ->
            parts += buildJsonObject {
                put("type", "tool_use")
                put("id", call.id)
                put("name", call.name)
                put(
                    "input",
                    runCatching { JSON.parseToJsonElement(call.arguments) }
                        .getOrElse { JsonObject(emptyMap()) },
                )
            }
        }

        if (message.content.isNotBlank()) {
            parts += buildJsonObject { put("type", "text"); put("text", message.content) }
        }

        message.blocks.forEach { block ->
            when (block) {
                is ContentBlock.Text -> Unit // already emitted above
                is ContentBlock.Image -> parts += buildJsonObject {
                    put("type", "image")
                    putJsonObject("source") {
                        put("type", "base64")
                        put("media_type", block.mimeType)
                        put("data", block.base64.orEmpty())
                    }
                }
                is ContentBlock.Document -> parts += buildJsonObject {
                    put("type", "document")
                    putJsonObject("source") {
                        put("type", "base64")
                        put("media_type", block.mimeType)
                        put("data", block.base64)
                    }
                }
            }
        }

        // Anthropic rejects an empty content array, so guarantee at least one block.
        if (parts.isEmpty()) {
            parts += buildJsonObject { put("type", "text"); put("text", " ") }
        }
        return JsonArray(parts)
    }

    override fun parseStreamPayload(payload: JsonObject): List<ProviderDelta> {
        val out = mutableListOf<ProviderDelta>()
        when (payload["type"]?.jsonPrimitive?.contentOrNull) {
            "error" -> {
                val error = payload["error"] as? JsonObject
                out += ProviderDelta.Error(
                    error?.get("message")?.jsonPrimitive?.contentOrNull ?: "Provider reported an error",
                    status = null,
                )
            }

            "message_start" -> {
                payload["message"]?.jsonObject?.let { message ->
                    message["model"]?.jsonPrimitive?.contentOrNull?.let { out += ProviderDelta.ModelActual(it) }
                    message["usage"]?.jsonObject?.let { usage ->
                        out += ProviderDelta.Usage(
                            promptTokens = usage["input_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                            completionTokens = usage["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                        )
                    }
                }
            }

            "content_block_start" -> {
                // A tool_use block opens here; the arguments stream in as deltas.
                val block = payload["content_block"]?.jsonObject
                val index = payload["index"]?.jsonPrimitive?.intOrNull ?: 0
                if (block?.get("type")?.jsonPrimitive?.contentOrNull == "tool_use") {
                    out += ProviderDelta.ToolCallDelta(
                        index = index,
                        id = block["id"]?.jsonPrimitive?.contentOrNull,
                        name = block["name"]?.jsonPrimitive?.contentOrNull,
                    )
                }
            }

            "content_block_delta" -> {
                val index = payload["index"]?.jsonPrimitive?.intOrNull ?: 0
                when (val delta = payload["delta"] as? JsonObject) {
                    null -> Unit
                    else -> when (delta["type"]?.jsonPrimitive?.contentOrNull) {
                        "text_delta" ->
                            delta["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
                                ?.let { out += ProviderDelta.TextDelta(it) }

                        "thinking_delta" ->
                            delta["thinking"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
                                ?.let { out += ProviderDelta.ThinkingDelta(it) }

                        "input_json_delta" ->
                            delta["partial_json"]?.jsonPrimitive?.contentOrNull
                                ?.let { out += ProviderDelta.ToolCallDelta(index = index, argumentsDelta = it) }

                        else -> Unit
                    }
                }
            }

            "message_delta" -> {
                payload["delta"]?.jsonObject?.get("stop_reason")?.jsonPrimitive?.contentOrNull
                    ?.let { out += ProviderDelta.Finish(it) }
                payload["usage"]?.jsonObject?.let { usage ->
                    val completion = usage["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                    if (completion > 0) out += ProviderDelta.Usage(completionTokens = completion)
                }
            }

            "message_stop" -> out += ProviderDelta.Finish("end_turn")
            else -> Unit
        }
        return out
    }

    override fun parseResponse(payload: JsonObject): ProviderDelta {
        val content = payload["content"]?.jsonArray ?: return ProviderDelta.Error("Provider returned no content")
        val text = content.joinToString("") { part ->
            val obj = part.jsonObject
            if (obj["type"]?.jsonPrimitive?.contentOrNull == "text") {
                obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
            } else {
                ""
            }
        }
        return ProviderDelta.TextDelta(text)
    }

    override suspend fun listModels(
        client: OkHttpClient,
        baseUrl: String,
        apiKey: String?,
        extraHeaders: Map<String, String>,
    ): List<ModelDescriptor> {
        val builder = Request.Builder()
            .url(AdapterSupport.url(AdapterSupport.normalizeBase(baseUrl, "https://api.anthropic.com"), "/v1/models"))
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("Accept", "application/json")
            .get()
        apiKey?.takeIf { it.isNotBlank() }?.let { builder.header("x-api-key", it) }
        extraHeaders.forEach { (k, v) -> builder.header(k, v) }

        return client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ProviderHttpException(response.code, body.take(500))
            val root = runCatching { JSON.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
            (root["data"] as? JsonArray).orEmpty().mapNotNull { element ->
                val item = element.jsonObject
                val id = item["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                ModelDescriptor(id = id, displayName = item["display_name"]?.jsonPrimitive?.contentOrNull)
            }
        }
    }

    private fun omitsSamplingControls(model: String): Boolean {
        val lower = model.lowercase()
        // Opus 4.7 and major-only Opus releases reject temperature and top_p.
        return lower.contains("opus-4-7") || lower.contains("opus-4.7") || OPUS_MAJOR_ONLY.matches(lower)
    }

    private companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val DEFAULT_MAX_TOKENS = 8192
        const val THINKING_BUDGET_TOKENS = 4096
        val OPUS_MAJOR_ONLY = Regex("""claude-opus-(\d+)$""")
    }
}
