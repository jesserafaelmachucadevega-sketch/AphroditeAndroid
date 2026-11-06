package com.aphrodite.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aphrodite.core.database.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    @Query("SELECT * FROM memories ORDER BY pinned DESC, updated_at DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY pinned DESC, updated_at DESC")
    suspend fun all(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun get(id: String): MemoryEntity?

    /** Pinned memories are always sent; this finds the rest that relate to a query. */
    @Query(
        "SELECT * FROM memories WHERE pinned = 1 " +
            "OR text LIKE '%' || :query || '%' " +
            "ORDER BY pinned DESC, updated_at DESC LIMIT :limit",
    )
    suspend fun relevant(query: String, limit: Int = 8): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE pinned = 1 ORDER BY updated_at DESC")
    suspend fun pinned(): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE memories SET pinned = :pinned, updated_at = :now WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, now: Long)

    @Query("SELECT COUNT(*) FROM memories")
    suspend fun count(): Int
}
