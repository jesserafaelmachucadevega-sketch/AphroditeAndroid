package com.aphrodite.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aphrodite.core.database.entity.UploadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadDao {

    @Query("SELECT * FROM uploads ORDER BY created_at DESC")
    fun observeAll(): Flow<List<UploadEntity>>

    @Query("SELECT * FROM uploads WHERE id = :id")
    suspend fun getById(id: String): UploadEntity?

    /** Deduplication: the same bytes are only stored once. */
    @Query("SELECT * FROM uploads WHERE sha256 = :sha256 LIMIT 1")
    suspend fun getByHash(sha256: String): UploadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(upload: UploadEntity)

    @Query("UPDATE uploads SET extracted_text = :text WHERE id = :id")
    suspend fun setExtractedText(id: String, text: String?)

    @Query("UPDATE uploads SET thumbnail_path = :path WHERE id = :id")
    suspend fun setThumbnail(id: String, path: String?)

    @Query("DELETE FROM uploads WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM uploads")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(size), 0) FROM uploads")
    suspend fun totalBytes(): Long

    /** Uploads not referenced by any message — candidates for cleanup. */
    @Query(
        "SELECT * FROM uploads WHERE id NOT IN (SELECT upload_id FROM chat_attachments) " +
            "AND created_at < :olderThan",
    )
    suspend fun orphaned(olderThan: Long): List<UploadEntity>
}
