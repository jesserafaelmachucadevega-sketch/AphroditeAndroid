package com.aphrodite.core.llm.provider

import com.aphrodite.core.database.entity.ProviderKind
import com.aphrodite.core.llm.model.ContentBlock
import com.aphrodite.core.llm.model.LlmMessage
import com.aphrodite.core.llm.model.LlmRequest
import com.aphrodite.core.llm.model.LlmRole
import com.aphrodite.core.llm.model.ModelDescriptor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Google's Gemini API.
 *
 * Notable differences from the OpenAI shape: the model is part of the path rather than the body,
 * the API key is a query parameter, assistant turns are called `model`, the system prompt is a
 * separate `systemInstruction`, and tool parameters are an OpenAPI-style subset.
 */
class GeminiAdapter : ProviderAdapter {

    override val kind: ProviderKind = ProviderKind.Gemini

    override fun buildStreamRequest(request: LlmRequest): Request = build(request, stream = true)

    override fun buildRequest(request: LlmRequest): Request = build(request, stream = false)

    private fun build(request: LlmRequest, stream: Boolean): Request {
        val (system, turns) = AdapterSupport.splitSystem(request.messages)

        val body = buildJsonObject {
            system?.let {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") { add(buildJsonObject { put("text", it) }) }
                }
            }

            put("contents", buildContents(turns))

            if (request.tools.isNotEmpty()) {
                putJsonArray("tools") {
                    add(
                        buildJsonObject {
                            putJsonArray("functionDeclarations") {
                                request.tools.forEach { tool ->
                                    add(
                                        buildJsonObject {
                                            put("name", tool.name)
                                            put("description", tool.description)
                                            put("parameters", tool.parameters)
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            }

            putJsonObject("generationConfig") {
                request.settings.temperature?.let { put("temperature", it) }
                request.settings.topP?.let { put("topP", it) }
                request.settings.maxOutputTokens?.let { put("maxOutputTokens", it) }
                // Ask Gemini to include its reasoning summary when the user wants to see it.
                if (request.settings.thinkingMode == "on") {
                    putJsonObject("thinkingConfig") { put("includeThoughts", true) }
                }
            }
        }

        val base = AdapterSupport.normalizeBase(request.baseUrl, DEFAULT_BASE)
        val method = if (stream) "streamGenerateContent" else "generateContent"
        val urlBuilder = "${base}/models/${request.model}:$method".toHttpUrlOrNull()?.newBuilder()
            ?.apply { if (stream) addQueryParameter("alt", "sse") }
            ?.apply { request.apiKey?.takeIf { it.isNotBlank() }?.let { addQueryParameter("key", it) } }
            ?.build()
            ?: error("Invalid Gemini base URL: ${request.baseUrl}")

        val builder = Request.Builder()
            .url(urlBuilder)
            .header("Content-Type", "application/json")
            .header("Accept", if (stream) "text/event-stream" else "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))

        request.headers.forEach { (name, value) -> builder.header(name, value) }
        return builder.build()
    }

    private fun buildContents(messages: List<LlmMessage>): JsonArray = buildJsonArray {
        messages.forEach { message ->
            add(
                buildJsonObject {
                    // Gemini only understands two roles; tool traffic is folded into "user".
                    put("role", if (message.role == LlmRole.Assistant) "model" else "user")
                    put("parts", buildParts(message))
                },
            )
        }
    }

    private fun buildParts(message: LlmMessage): JsonArray {
        val parts = mutableListOf<JsonObject>()

        if (message.role == LlmRole.Tool) {
            parts += buildJsonObject {
                putJsonObject("functionResponse") {
                    put("name", message.name ?: "tool")
                    putJsonObject("response") { put("result", message.text) }
                }
            }
            return JsonArray(parts)
        }

        message.toolCalls.forEach { call ->
            parts += buildJsonObject {
                putJsonObject("functionCall") {
                    put("name", call.name)
                    put(
                        "args",
                        runCatching { JSON.parseToJsonElement(call.arguments) }
                            .getOrElse { JsonObject(emptyMap()) },
                    )
                }
            }
        }

        if (message.content.isNotBlank()) {
            parts += buildJsonObject { put("text", message.content) }
        }

        message.blocks.forEach { block ->
            when (block) {
                is ContentBlock.Text -> Unit
                is ContentBlock.Image -> parts += buildJsonObject {
                    putJsonObject("inlineData") {
                        put("mimeType", block.mimeType)
                        put("data", block.base64.orEmpty())
                    }
                }
                is ContentBlock.Document -> parts += buildJsonObject {
                    putJsonObject("inlineData") {
                        put("mimeType", block.mimeType)
                        put("data", block.base64)
                    }
                }
            }
        }

        // Gemini rejects a parts array with nothing in it.
        if (parts.isEmpty()) {
            parts += buildJsonObject { put("text", " ") }
        }
        return JsonArray(parts)
    }

    override fun parseStreamPayload(payload: JsonObject): List<ProviderDelta> {
        val out = mutableListOf<ProviderDelta>()

        payload["error"]?.let { error ->
            val message = error.jsonObject["message"]?.jsonPrimitive?.contentOrNull
                ?: "Provider reported an error"
            return listOf(ProviderDelta.Error(message, error.jsonObject["code"]?.jsonPrimitive?.intOrNull))
        }

        payload["usageMetadata"]?.jsonObject?.let { usage ->
            val prompt = usage["promptTokenCount"]?.jsonPrimitive?.intOrNull ?: 0
            val completion = usage["candidatesTokenCount"]?.jsonPrimitive?.intOrNull ?: 0
            val thoughts = usage["thoughtsTokenCount"]?.jsonPrimitive?.intOrNull ?: 0
            if (prompt > 0 || completion > 0) {
                out += ProviderDelta.Usage(prompt, completion, thoughts)
            }
        }

        val candidate = payload["candidates"]?.jsonArray?.firstOrNull()?.jsonObject ?: return out

        candidate["content"]?.jsonObject?.get("parts")?.jsonArray?.forEach { part ->
            val obj = part.jsonObject
            val text = obj["text"]?.jsonPrimitive?.contentOrNull
            if (!text.isNullOrEmpty()) {
                // Gemini marks reasoning parts with a flag rather than a separate field.
                val isThought = obj["thought"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true
                out += if (isThought) ProviderDelta.ThinkingDelta(text) else ProviderDelta.TextDelta(text)
            }
            obj["functionCall"]?.jsonObject?.let { call ->
                out += ProviderDelta.ToolCallDelta(
                    index = 0,
                    id = null,
                    name = call["name"]?.jsonPrimitive?.contentOrNull,
                    argumentsDelta = call["args"]?.toString(),
                )
            }
        }

        candidate["finishReason"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() && it != "FINISH_REASON_UNSPECIFIED" }
            ?.let { out += ProviderDelta.Finish(it) }

        return out
    }

    override fun parseResponse(payload: JsonObject): ProviderDelta {
        payload["error"]?.let { error ->
            return ProviderDelta.Error(
                error.jsonObject["message"]?.jsonPrimitive?.contentOrNull ?: "Provider reported an error",
            )
        }
        val parts = payload["candidates"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?: return ProviderDelta.Error("Provider returned no content")
        return ProviderDelta.TextDelta(
            parts.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() },
        )
    }

    override suspend fun listModels(
        client: OkHttpClient,
        baseUrl: String,
        apiKey: String?,
        extraHeaders: Map<String, String>,
    ): List<ModelDescriptor> {
        val base = AdapterSupport.normalizeBase(baseUrl, DEFAULT_BASE)
        val url = "${base}/models".toHttpUrlOrNull()?.newBuilder()
            ?.apply { apiKey?.takeIf { it.isNotBlank() }?.let { addQueryParameter("key", it) } }
            ?.build()
            ?: throw ProviderHttpException(400, "Invalid Gemini base URL")

        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
        extraHeaders.forEach { (k, v) -> builder.header(k, v) }

        return client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ProviderHttpException(response.code, body.take(500))
            val root = runCatching { JSON.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
            (root["models"] as? JsonArray).orEmpty().mapNotNull { element ->
                val item = element.jsonObject
                val name = item["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                // Names arrive as "models/gemini-2.5-pro"; the id is the part after the slash.
                ModelDescriptor(
                    id = name.substringAfter("models/", name),
                    displayName = item["displayName"]?.jsonPrimitive?.contentOrNull,
                    contextLength = item["inputTokenLimit"]?.jsonPrimitive?.intOrNull,
                    maxOutput = item["outputTokenLimit"]?.jsonPrimitive?.intOrNull,
                )
            }.filter { it.id.isNotBlank() }
        }
    }

    private companion object {
        const val DEFAULT_BASE = "https://generativelanguage.googleapis.com/v1beta"
    }
}
