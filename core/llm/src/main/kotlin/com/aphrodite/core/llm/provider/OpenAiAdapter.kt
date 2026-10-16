package com.aphrodite.core.llm.provider

import com.aphrodite.core.database.entity.ProviderKind
import com.aphrodite.core.llm.model.ContentBlock
import com.aphrodite.core.llm.model.LlmMessage
import com.aphrodite.core.llm.model.LlmRequest
import com.aphrodite.core.llm.model.LlmRole
import com.aphrodite.core.llm.model.ModelDescriptor
import com.aphrodite.core.llm.model.ToolDefinition
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
 * The OpenAI chat-completions wire format.
 *
 * This is by far the most widely implemented shape, so it covers OpenAI itself, every
 * OpenAI-compatible gateway, the local runtimes (Ollama, LM Studio, llama.cpp, vLLM) and the
 * hosted providers that copy it. The class is parameterised by [kind] only so diagnostics and
 * the model list can behave slightly differently.
 */
open class OpenAiAdapter(
    override val kind: ProviderKind = ProviderKind.OpenAiCompatible,
) : ProviderAdapter {

    override val chatPath: String get() = "/chat/completions"

    protected open fun defaultPath(): String = chatPath

    override fun buildStreamRequest(request: LlmRequest): Request =
        buildRequestInternal(request, stream = true)

    override fun buildRequest(request: LlmRequest): Request =
        buildRequestInternal(request, stream = false)

    private fun buildRequestInternal(request: LlmRequest, stream: Boolean): Request {
        val body = buildJsonObject {
            put("model", request.model)
            put("messages", buildMessages(request.messages))
            put("stream", stream)
            if (stream && request.settings.includeUsage) {
                // Without this most OpenAI-shaped providers omit usage on streamed calls.
                putJsonObject("stream_options") { put("include_usage", true) }
            }
            request.settings.temperature?.let { put("temperature", it) }
            request.settings.topP?.let { put("top_p", it) }
            request.settings.maxOutputTokens?.let { put("max_tokens", it) }

            if (request.tools.isNotEmpty()) {
                put("tools", buildTools(request.tools))
                put("tool_choice", "auto")
            }

            // Recognised by several providers as a way to ask for reasoning output.
            when (request.settings.reasoningEffort) {
                "low", "medium", "high" -> put("reasoning_effort", request.settings.reasoningEffort)
            }
        }

        val builder = Request.Builder()
            .url(AdapterSupport.url(request.baseUrl, defaultPath()))
            .header("Content-Type", "application/json")
            .header("Accept", if (stream) "text/event-stream" else "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))

        request.apiKey?.takeIf { it.isNotBlank() }?.let {
            builder.header("Authorization", "Bearer $it")
        }
        request.headers.forEach { (name, value) -> builder.header(name, value) }

        return builder.build()
    }

    /** Builds the messages array, expanding multimodal blocks where they are present. */
    protected fun buildMessages(messages: List<LlmMessage>): JsonArray = buildJsonArray {
        messages.forEach { message -> add(buildMessage(message)) }
    }

    protected fun buildMessage(message: LlmMessage): JsonObject = buildJsonObject {
        put("role", wireRole(message.role))

        // A tool result is its own message shape rather than ordinary content.
        if (message.role == LlmRole.Tool) {
            put("tool_call_id", message.toolCallId ?: "")
            put("content", message.text)
            return@buildJsonObject
        }

        if (message.toolCalls.isNotEmpty()) {
            putJsonArray("tool_calls") {
                message.toolCalls.forEach { call ->
                    add(
                        buildJsonObject {
                            put("id", call.id)
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", call.name)
                                put("arguments", call.arguments)
                            }
                        },
                    )
                }
            }
        }

        val media = message.blocks.filterNot { it is ContentBlock.Text }
        if (media.isEmpty()) {
            put("content", message.content)
        } else {
            // Multimodal content is a list of typed parts.
            putJsonArray("content") {
                if (message.content.isNotBlank()) {
                    add(buildJsonObject { put("type", "text"); put("text", message.content) })
                }
                media.forEach { block ->
                    when (block) {
                        is ContentBlock.Text ->
                            add(buildJsonObject { put("type", "text"); put("text", block.text) })

                        is ContentBlock.Image -> add(
                            buildJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") {
                                    put(
                                        "url",
                                        block.url
                                            ?: "data:${block.mimeType};base64,${block.base64.orEmpty()}",
                                    )
                                }
                            },
                        )

                        is ContentBlock.Document -> add(
                            buildJsonObject {
                                put("type", "text")
                                put("text", "[Attached document: ${block.name ?: block.mimeType}]")
                            },
                        )
                    }
                }
            }
        }
    }

    protected fun wireRole(role: LlmRole): String = when (role) {
        LlmRole.System -> "system"
        LlmRole.User -> "user"
        LlmRole.Assistant -> "assistant"
        LlmRole.Tool -> "tool"
    }

    protected fun buildTools(tools: List<ToolDefinition>): JsonArray = buildJsonArray {
        tools.forEach { tool ->
            add(
                buildJsonObject {
                    put("type", "function")
                    putJsonObject("function") {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("parameters", tool.parameters)
                    }
                },
            )
        }
    }

    override fun parseStreamPayload(payload: JsonObject): List<ProviderDelta> {
        // Some providers report failures as a normal-looking frame.
        payload["error"]?.let { element ->
            val message = element.jsonObject["message"]?.jsonPrimitive?.contentOrNull
                ?: element.jsonPrimitive.contentOrNull
                ?: "Provider reported an error"
            val status = element.jsonObject["code"]?.jsonPrimitive?.intOrNull
            return listOf(ProviderDelta.Error(message, status))
        }

        val out = mutableListOf<ProviderDelta>()

        payload["model"]?.jsonPrimitive?.contentOrNull?.let { out += ProviderDelta.ModelActual(it) }

        payload["usage"]?.let { usage ->
            if (usage is JsonObject) {
                val prompt = usage["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                val completion = usage["completion_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                val reasoning = usage["completion_tokens_details"]
                    ?.jsonObject?.get("reasoning_tokens")?.jsonPrimitive?.intOrNull
                    ?: usage["reasoning_tokens"]?.jsonPrimitive?.intOrNull
                    ?: 0
                if (prompt > 0 || completion > 0) {
                    out += ProviderDelta.Usage(prompt, completion, reasoning)
                }
            }
        }

        val choice = payload["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return out

        choice["finish_reason"]?.jsonPrimitive?.contentOrNull?.let { reason ->
            if (reason.isNotBlank() && reason != "null") out += ProviderDelta.Finish(reason)
        }

        val delta = choice["delta"]?.jsonObject ?: choice["message"]?.jsonObject ?: return out

        // Visible text.
        delta["content"]?.let { element ->
            when (element) {
                is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotEmpty() }?.let {
                    out += ProviderDelta.TextDelta(it)
                }
                is JsonArray -> element.forEach { part ->
                    part.jsonObject["text"]?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { out += ProviderDelta.TextDelta(it) }
                }
                else -> Unit
            }
        }

        // Hidden reasoning, under whichever name the provider uses.
        listOf("reasoning_content", "reasoning", "thinking").forEach { key ->
            delta[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let {
                out += ProviderDelta.ThinkingDelta(it)
            }
        }

        delta["tool_calls"]?.jsonArray?.forEach { element ->
            val call = element.jsonObject
            val index = call["index"]?.jsonPrimitive?.intOrNull ?: 0
            val function = call["function"] as? JsonObject
            out += ProviderDelta.ToolCallDelta(
                index = index,
                id = call["id"]?.jsonPrimitive?.contentOrNull,
                name = function?.get("name")?.jsonPrimitive?.contentOrNull,
                argumentsDelta = function?.get("arguments")?.jsonPrimitive?.contentOrNull,
            )
        }

        return out
    }

    override fun parseResponse(payload: JsonObject): ProviderDelta {
        payload["error"]?.let { element ->
            val message = element.jsonObject["message"]?.jsonPrimitive?.contentOrNull
                ?: "Provider reported an error"
            return ProviderDelta.Error(message)
        }
        val choice = payload["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: return ProviderDelta.Error("Provider returned no choices")
        val message = choice["message"]?.jsonObject ?: return ProviderDelta.Error("Provider returned no message")
        val text = message["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
        return ProviderDelta.TextDelta(text)
    }

    override suspend fun listModels(
        client: OkHttpClient,
        baseUrl: String,
        apiKey: String?,
        extraHeaders: Map<String, String>,
    ): List<ModelDescriptor> {
        val builder = Request.Builder()
            .url(AdapterSupport.url(AdapterSupport.normalizeBase(baseUrl, "https://api.openai.com/v1"), "/models"))
            .header("Accept", "application/json")
            .get()
        apiKey?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
        extraHeaders.forEach { (k, v) -> builder.header(k, v) }

        return client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ProviderHttpException(response.code, body.take(500))
            }
            parseModelList(body)
        }
    }

    /** Accepts either `{data:[...]}` or `{models:[...]}`, and items keyed `id`, `name` or `model`. */
    protected fun parseModelList(body: String): List<ModelDescriptor> {
        val root = runCatching { JSON.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
        val array = (root["data"] ?: root["models"])?.jsonArray ?: return emptyList()

        return array.mapNotNull { element ->
            val item = element.jsonObject
            val id = (item["id"] ?: item["name"] ?: item["model"])?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null
            ModelDescriptor(
                id = id,
                displayName = item["display_name"]?.jsonPrimitive?.contentOrNull
                    ?: item["name"]?.jsonPrimitive?.contentOrNull,
                contextLength = item["context_length"]?.jsonPrimitive?.intOrNull
                    ?: item["context_window"]?.jsonPrimitive?.intOrNull
                    ?: item["max_context_length"]?.jsonPrimitive?.intOrNull,
                supportsVision = item["supports_vision"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull(),
                supportsTools = item["supports_tools"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull(),
            )
        }.distinctBy { it.id }.sortedBy { it.id }
    }
}
