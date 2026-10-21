package com.aphrodite.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aphrodite.core.database.entity.McpServerEntity
import com.aphrodite.core.database.entity.McpToolEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface McpDao {

    @Query("SELECT * FROM mcp_servers ORDER BY name ASC")
    fun observeServers(): Flow<List<McpServerEntity>>

    @Query("SELECT * FROM mcp_servers WHERE enabled = 1 ORDER BY name ASC")
    suspend fun enabledServers(): List<McpServerEntity>

    @Query("SELECT * FROM mcp_servers WHERE id = :id")
    suspend fun server(id: String): McpServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertServer(server: McpServerEntity)

    @Query("DELETE FROM mcp_servers WHERE id = :id")
    suspend fun deleteServer(id: String)

    @Query("UPDATE mcp_servers SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query(
        "UPDATE mcp_servers SET last_connected_at = :at, last_error = :error, tool_count = :toolCount " +
            "WHERE id = :id",
    )
    suspend fun recordConnection(id: String, at: Long?, error: String?, toolCount: Int)

    @Query("SELECT * FROM mcp_tools WHERE server_id = :serverId ORDER BY name ASC")
    fun observeTools(serverId: String): Flow<List<McpToolEntity>>

    @Query("SELECT * FROM mcp_tools WHERE enabled = 1")
    suspend fun enabledTools(): List<McpToolEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTools(tools: List<McpToolEntity>)

    @Query("DELETE FROM mcp_tools WHERE server_id = :serverId")
    suspend fun deleteTools(serverId: String)

    @Query("UPDATE mcp_tools SET enabled = :enabled WHERE id = :id")
    suspend fun setToolEnabled(id: String, enabled: Boolean)
}
