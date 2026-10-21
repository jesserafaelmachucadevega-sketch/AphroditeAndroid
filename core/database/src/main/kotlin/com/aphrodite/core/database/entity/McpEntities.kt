package com.aphrodite.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * How to reach an MCP server.
 *
 * Odysseus also supports `stdio`, which spawns a subprocess. Android cannot execute arbitrary
 * binaries from app storage, so that transport has no on-device equivalent and is not offered.
 * The two network transports cover the same servers when they are run as a service.
 */
enum class McpTransport(val id: String, val label: String) {
    StreamableHttp("http", "Streamable HTTP"),
    Sse("sse", "SSE"),
    ;

    companion object {
        fun fromId(id: String?): McpTransport =
            entries.firstOrNull { it.id == id } ?: StreamableHttp
    }
}

/** A configured MCP server. */
@Entity(
    tableName = "mcp_servers",
    indices = [Index("enabled"), Index(value = ["name"], unique = true)],
)
@Serializable
data class McpServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val transport: McpTransport,
    val url: String,
    /** Extra request headers, as a JSON object. Auth tokens live in the secret store instead. */
    @ColumnInfo(name = "headers_json") val headersJson: String? = null,
    /** Reference into the encrypted secret store holding a bearer token, if any. */
    @ColumnInfo(name = "auth_ref") val authRef: String? = null,
    val enabled: Boolean = true,
    /** Comma separated tool names the user has switched off for this server. */
    @ColumnInfo(name = "disabled_tools") val disabledTools: String? = null,
    @ColumnInfo(name = "last_connected_at") val lastConnectedAt: Long? = null,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    @ColumnInfo(name = "tool_count") val toolCount: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** A tool discovered on an MCP server. */
@Entity(
    tableName = "mcp_tools",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = McpServerEntity::class,
            parentColumns = ["id"],
            childColumns = ["server_id"],
            onDelete = androidx.room.ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("server_id"), Index(value = ["server_id", "name"], unique = true)],
)
@Serializable
data class McpToolEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "server_id") val serverId: String,
    /** Name as the server reports it, without the qualified prefix. */
    val name: String,
    val description: String? = null,
    @ColumnInfo(name = "input_schema_json") val inputSchemaJson: String? = null,
    val enabled: Boolean = true,
    @ColumnInfo(name = "discovered_at") val discoveredAt: Long,
)
