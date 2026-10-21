package com.aphrodite.core.llm.mcp

import com.aphrodite.core.database.dao.McpDao
import com.aphrodite.core.database.entity.McpServerEntity
import com.aphrodite.core.database.entity.McpToolEntity
import com.aphrodite.core.database.entity.McpTransport
import com.aphrodite.core.llm.agent.Tool
import com.aphrodite.core.llm.agent.ToolOutcome
import com.aphrodite.core.network.HttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** What a server reported about itself during `initialize`. */
data class McpServerInfo(
    val name: String,
    val version: String?,
    val protocolVersion: String?,
)

/** A tool as the server describes it. */
data class McpToolDescriptor(
    val name: String,
    val description: String?,
    val inputSchema: JsonObject?,
)

/** A live connection's mutable state. */
data class McpConnection(
    val serverId: String,
    val url: String,
    val transport: McpTransport,
    val sessionId: String?,
    val bearerToken: String?,
    val info: McpServerInfo,
)

/**
 * A Model Context Protocol client.
 *
 * Speaks JSON-RPC 2.0 over Streamable HTTP, and over SSE for older servers. Odysseus also supports
 * `stdio`, which spawns a subprocess; Android cannot execute arbitrary binaries from app storage, so
 * that transport is absent rather than broken — run the server as a service and point this at it.
 *
 * Streamable HTTP responses may come back as either a plain JSON body or an event stream, and a
 * request's answer can be preceded by unrelated notification frames, so the response reader scans
 * for the frame whose `id` matches the request.
 */
class McpClient @Inject constructor(
    private val httpFactory: HttpClientFactory,
) {
    private val ids = AtomicLong(1)

    suspend fun connect(
        url: String,
        transport: McpTransport,
        bearerToken: String?,
        extraHeaders: Map<String, String>,
    ): Pair<McpConnection, List<McpToolDescriptor>> = withContext(Dispatchers.IO) {
        val base = url.trim().trimEnd('/')
        val sessionId = if (transport == McpTransport.StreamableHttp) null else null

        // initialize
        val initResponse = rpc(
            url = if (transport == McpTransport.Sse) "$base/messages/?session_id=${UUID.randomUUID()}" else base,
            method = "initialize",
            params = buildJsonObject {
                put("protocolVersion", PROTOCOL_VERSION)
                putJsonObject("capabilities") { putJsonObject("tools") {} }
                putJsonObject("clientInfo") {
                    put("name", "Aphrodite")
                    put("version", "1.0")
                }
            },
            sessionId = sessionId,
            bearerToken = bearerToken,
            extraHeaders = extraHeaders,
            transport = transport,
        )

        val result = initResponse["result"] as? JsonObject
            ?: throw McpException(describeError(initResponse) ?: "The server rejected initialize")
        val serverInfo = McpServerInfo(
            name = result["serverInfo"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: "MCP server",
            version = result["serverInfo"]?.jsonObject?.get("version")?.jsonPrimitive?.contentOrNull,
            protocolVersion = result["protocolVersion"]?.jsonPrimitive?.contentOrNull,
        )

        val connection = McpConnection(
            serverId = "",
            url = base,
            transport = transport,
            sessionId = sessionId,
            bearerToken = bearerToken,
            info = serverInfo,
        )

        // The spec requires this notification before any other request.
        runCatching {
            notify(connection, "notifications/initialized", buildJsonObject {}, extraHeaders)
        }

        connection to listTools(connection, extraHeaders)
    }

    suspend fun listTools(
        connection: McpConnection,
        extraHeaders: Map<String, String> = emptyMap(),
    ): List<McpToolDescriptor> = withContext(Dispatchers.IO) {
        val response = rpc(
            url = connection.url,
            method = "tools/list",
            params = buildJsonObject {},
            sessionId = connection.sessionId,
            bearerToken = connection.bearerToken,
            extraHeaders = extraHeaders,
            transport = connection.transport,
        )

        val result = response["result"] as? JsonObject
            ?: throw McpException(describeError(response) ?: "tools/list failed")
        val tools = result["tools"] as? kotlinx.serialization.json.JsonArray ?: return@withContext emptyList()

        tools.mapNotNull { element ->
            val item = element.jsonObject
            val name = item["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            McpToolDescriptor(
                name = name,
                description = item["description"]?.jsonPrimitive?.contentOrNull,
                inputSchema = item["inputSchema"] as? JsonObject,
            )
        }
    }

    /** Calls a tool and flattens the returned content blocks into text. */
    suspend fun callTool(
        connection: McpConnection,
        name: String,
        arguments: JsonObject,
        extraHeaders: Map<String, String> = emptyMap(),
    ): ToolOutcome = withContext(Dispatchers.IO) {
        val response = runCatching {
            rpc(
                url = connection.url,
                method = "tools/call",
                params = buildJsonObject {
                    put("name", name)
                    put("arguments", arguments)
                },
                sessionId = connection.sessionId,
                bearerToken = connection.bearerToken,
                extraHeaders = extraHeaders,
                transport = connection.transport,
            )
        }.getOrElse { error ->
            return@withContext ToolOutcome.Failure(
                "Calling \"$name\" failed: ${error.message ?: "unknown error"}",
                "The server may be unreachable. Try again, or use a different tool.",
            )
        }

        val result = response["result"] as? JsonObject
            ?: return@withContext ToolOutcome.Failure(
                describeError(response) ?: "\"$name\" returned an error",
                "Check the arguments against the tool's schema.",
            )

        val isError = result["isError"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true
        val text = (result["content"] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { block ->
                val item = block.jsonObject
                if (item["type"]?.jsonPrimitive?.contentOrNull == "text") {
                    item["text"]?.jsonPrimitive?.contentOrNull
                } else {
                    null
                }
            }
            ?.joinToString("\n")
            .orEmpty()

        if (isError) {
            ToolOutcome.Failure(text.ifBlank { "\"$name\" reported an error" })
        } else {
            ToolOutcome.Success(text.ifBlank { "(no output)" })
        }
    }

    // ── Transport ────────────────────────────────────────────────────────────

    private fun rpc(
        url: String,
        method: String,
        params: JsonObject,
        sessionId: String?,
        bearerToken: String?,
        extraHeaders: Map<String, String>,
        transport: McpTransport,
    ): JsonObject {
        val id = ids.getAndIncrement()
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }

        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .apply {
                sessionId?.let { header("Mcp-Session-Id", it) }
                bearerToken?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
                extraHeaders.forEach { (k, v) -> header(k, v) }
            }
            .build()

        httpFactory.standard.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw McpException("HTTP ${response.code}: ${text.take(300)}")
            }
            return readRpcResponse(text, id)
        }
    }

    private fun notify(
        connection: McpConnection,
        method: String,
        params: JsonObject,
        extraHeaders: Map<String, String>,
    ) {
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
            put("params", params)
        }
        val request = Request.Builder()
            .url(connection.url)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .apply {
                connection.sessionId?.let { header("Mcp-Session-Id", it) }
                connection.bearerToken?.takeIf { it.isNotBlank() }
                    ?.let { header("Authorization", "Bearer $it") }
                extraHeaders.forEach { (k, v) -> header(k, v) }
            }
            .build()
        httpFactory.standard.newCall(request).execute().close()
    }

    /** Finds the JSON-RPC envelope for [id] in either a plain body or an event stream. */
    internal fun readRpcResponse(body: String, id: Long): JsonObject {
        val candidates = mutableListOf<JsonObject>()

        body.trim().takeIf { it.startsWith("{") }?.let { plain ->
            runCatching { JSON.parseToJsonElement(plain).jsonObject }.getOrNull()?.let(candidates::add)
        }

        if (candidates.isEmpty() || body.contains("data:")) {
            // Event stream: each `data:` line is a complete envelope. Notifications and progress
            // frames share the stream, so scan rather than taking the first.
            body.lineSequence()
                .filter { it.startsWith("data:") }
                .forEach { line ->
                    val payload = line.removePrefix("data:").trim()
                    runCatching { JSON.parseToJsonElement(payload).jsonObject }
                        .getOrNull()
                        ?.let(candidates::add)
                }
        }

        candidates.firstOrNull { envelope ->
            envelope["id"]?.jsonPrimitive?.let { it.contentOrNull?.toLongOrNull() } == id
        }?.let { return it }

        // Servers that omit the id on a single-response body still mean the request's answer.
        if (candidates.size == 1) return candidates.first()

        throw McpException("No response for request $id")
    }

    private fun describeError(envelope: JsonObject): String? {
        val error = envelope["error"] as? JsonObject ?: return null
        return error["message"]?.jsonPrimitive?.contentOrNull
            ?: "The server returned an error"
    }

    companion object {
        const val PROTOCOL_VERSION = "2024-11-05"
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        val JSON = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
    }
}

class McpException(message: String) : Exception(message)

/**
 * Exposes one remote MCP tool as a local [Tool], so the agent loop does not need to know where a
 * tool lives.
 */
class McpToolAdapter(
    private val client: McpClient,
    private val connection: McpConnection,
    private val descriptor: McpToolDescriptor,
    private val headers: Map<String, String>,
    /** Qualified so two servers exposing the same tool name do not collide. */
    override val name: String,
    private val serverName: String,
) : Tool {

    override val description: String =
        descriptor.description?.takeIf { it.isNotBlank() } ?: "A tool provided by $serverName."

    override val whenToUse: String =
        "Provided by the \"$serverName\" MCP server. ${descriptor.description.orEmpty()}".trim()

    override val parameters: JsonObject = descriptor.inputSchema ?: buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    }

    override suspend fun execute(arguments: JsonObject): ToolOutcome =
        client.callTool(connection, descriptor.name, arguments, headers)
}
