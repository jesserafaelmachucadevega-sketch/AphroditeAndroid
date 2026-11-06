package com.aphrodite.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aphrodite.core.database.entity.LocalModelEntity
import com.aphrodite.core.database.entity.LocalModelStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalModelDao {

    @Query("SELECT * FROM local_models ORDER BY created_at DESC")
    fun observeAll(): Flow<List<LocalModelEntity>>

    @Query("SELECT * FROM local_models WHERE id = :id")
    suspend fun getById(id: String): LocalModelEntity?

    @Query("SELECT * FROM local_models WHERE status = 'ready' ORDER BY name ASC")
    suspend fun ready(): List<LocalModelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(model: LocalModelEntity)

    @Query("UPDATE local_models SET downloaded_bytes = :bytes, updated_at = :now WHERE id = :id")
    suspend fun recordProgress(id: String, bytes: Long, now: Long)

    @Query(
        "UPDATE local_models SET status = :status, size_bytes = :sizeBytes, " +
            "downloaded_bytes = :downloadedBytes, error = :error, updated_at = :now WHERE id = :id",
    )
    suspend fun finish(
        id: String,
        status: LocalModelStatus,
        sizeBytes: Long,
        downloadedBytes: Long,
        error: String?,
        now: Long,
    )

    @Query(
        "UPDATE local_models SET quantization = :quantization, parameters_b = :parametersB, " +
            "context_length = :contextLength, updated_at = :now WHERE id = :id",
    )
    suspend fun recordMetadata(
        id: String,
        quantization: String?,
        parametersB: Double?,
        contextLength: Int?,
        now: Long,
    )

    @Query("DELETE FROM local_models WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE local_models SET status = 'failed', error = :error, updated_at = :now")
    suspend fun failAllIncomplete(error: String, now: Long)

    @Query("SELECT COALESCE(SUM(size_bytes), 0) FROM local_models WHERE status = 'ready'")
    fun observeTotalSize(): Flow<Long>
}
