package com.aphrodite.core.llm.mcp

import com.aphrodite.core.database.dao.McpDao
import com.aphrodite.core.database.entity.McpServerEntity
import com.aphrodite.core.database.entity.McpToolEntity
import com.aphrodite.core.database.entity.McpTransport
import com.aphrodite.core.llm.agent.Tool
import com.aphrodite.core.security.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns MCP servers and the tools they expose.
 *
 * Connections are held in memory because a call needs the session the server handed out during
 * `initialize`. They are re-established on demand rather than kept alive: on a phone, a process can
 * be killed at any moment, and a tool call that fails because of a stale socket is worse than one
 * that reconnects first.
 */
@Singleton
class McpManager @Inject constructor(
    private val dao: McpDao,
    private val client: McpClient,
    private val secretStore: SecretStore,
) {
    private val _tools = MutableStateFlow<List<Tool>>(emptyList())
    val tools: StateFlow<List<Tool>> = _tools.asStateFlow()

    private val _status = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Server id to a short human-readable state, for the settings list. */
    val status: StateFlow<Map<String, String>> = _status.asStateFlow()

    private val connections = mutableMapOf<String, McpConnection>()
    private val toolEntities = mutableMapOf<String, List<McpToolEntity>>()

    fun observeServers(): Flow<List<McpServerEntity>> = dao.observeServers()

    fun observeTools(serverId: String): Flow<List<McpToolEntity>> = dao.observeTools(serverId)

    suspend fun addServer(
        name: String,
        transport: McpTransport,
        url: String,
        bearerToken: String?,
    ): String {
        val id = UUID.randomUUID().toString()
        val authRef = bearerToken?.takeIf { it.isNotBlank() }?.let { token ->
            // Same paste mistakes as provider keys: strip a copied "Bearer " prefix.
            val cleaned = token.trim()
                .removeSurrounding("\"")
                .removePrefix("Bearer ")
                .removePrefix("bearer ")
                .trim()
            "mcp:$id".also { secretStore.put(it, cleaned) }
        }
        dao.upsertServer(
            McpServerEntity(
                id = id,
                name = name.trim(),
                transport = transport,
                url = url.trim().trimEnd('/'),
                authRef = authRef,
                enabled = true,
                createdAt = System.currentTimeMillis(),
            ),
        )
        return id
    }

    suspend fun deleteServer(id: String) {
        dao.server(id)?.authRef?.let { secretStore.remove(it) }
        dao.deleteTools(id)
        dao.deleteServer(id)
        connections.remove(id)
        toolEntities.remove(id)
        rebuild()
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        dao.setEnabled(id, enabled)
        if (enabled) refresh(id) else {
            connections.remove(id)
            toolEntities.remove(id)
            rebuild()
        }
    }

    /**
     * Connects every enabled server and republishes the tool list.
     *
     * Failures are recorded per server and never abort the sweep: one unreachable server must not
     * cost the user the tools from the others.
     */
    suspend fun refreshAll() = withContext(Dispatchers.IO) {
        dao.enabledServers().forEach { server -> refresh(server.id) }
        rebuild()
    }

    suspend fun refresh(serverId: String) = withContext(Dispatchers.IO) {
        val server = dao.server(serverId) ?: return@withContext
        updateStatus(serverId, "Connecting…")

        val token = server.authRef?.let { secretStore.get(it) }

        runCatching {
            client.connect(server.url, server.transport, token, headersFor(server))
        }.fold(
            onSuccess = { (connection, descriptors) ->
                val bound = connection.copy(serverId = serverId)
                connections[serverId] = bound

                val now = System.currentTimeMillis()
                val disabled = server.disabledTools
                    ?.split(',')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.toSet()
                    ?: emptySet()

                val entities = descriptors.map { descriptor ->
                    McpToolEntity(
                        id = "$serverId:${descriptor.name}",
                        serverId = serverId,
                        name = descriptor.name,
                        description = descriptor.description,
                        inputSchemaJson = descriptor.inputSchema?.toString(),
                        enabled = descriptor.name !in disabled,
                        discoveredAt = now,
                    )
                }
                dao.deleteTools(serverId)
                dao.upsertTools(entities)
                toolEntities[serverId] = entities
                dao.recordConnection(serverId, now, null, entities.size)
                updateStatus(serverId, "Connected · ${entities.size} tools")
                rebuild()
            },
            onFailure = { error ->
                val message = error.message ?: "Could not connect"
                dao.recordConnection(serverId, null, message, 0)
                updateStatus(serverId, message)
            },
        )
    }

    /** Rebuilds the tool list the agent loop sees, honouring per-tool switches. */
    private fun rebuild() {
        val exposed = mutableListOf<Tool>()
        connections.forEach { (serverId, connection) ->
            val serverName = connection.info.name
            toolEntities[serverId]?.filter { it.enabled }?.forEach { entity ->
                val descriptor = McpToolDescriptor(
                    name = entity.name,
                    description = entity.description,
                    inputSchema = entity.inputSchemaJson?.let {
                        runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull()
                    },
                )
                exposed += McpToolAdapter(
                    client = client,
                    connection = connection,
                    descriptor = descriptor,
                    headers = emptyMap(),
                    // Qualified, so two servers exposing `search` do not overwrite each other.
                    name = "mcp__${sanitise(serverName)}__${entity.name}",
                    serverName = serverName,
                )
            }
        }
        _tools.value = exposed
    }

    private fun headersFor(server: McpServerEntity): Map<String, String> =
        server.headersJson
            ?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
            ?.mapNotNull { (key, value) ->
                (value as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                    ?.let { key to it }
            }
            ?.toMap()
            ?: emptyMap()

    /** Tool names must be safe identifiers, so server names are reduced to one. */
    private fun sanitise(value: String): String =
        value.lowercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")

    private fun updateStatus(serverId: String, message: String) {
        _status.value = _status.value + (serverId to message)
    }
}
