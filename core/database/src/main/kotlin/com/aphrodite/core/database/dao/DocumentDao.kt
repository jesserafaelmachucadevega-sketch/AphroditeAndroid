package com.aphrodite.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aphrodite.core.database.entity.DocumentChunkEntity
import com.aphrodite.core.database.entity.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun get(id: String): DocumentEntity?

    @Query("SELECT * FROM documents ORDER BY updated_at DESC")
    suspend fun all(): List<DocumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<DocumentChunkEntity>)

    @Query("DELETE FROM document_chunks WHERE document_id = :documentId")
    suspend fun deleteChunks(documentId: String)

    @Query("SELECT * FROM document_chunks WHERE document_id = :documentId ORDER BY ordinal ASC")
    suspend fun chunksFor(documentId: String): List<DocumentChunkEntity>

    /**
     * Every chunk, for brute-force retrieval.
     *
     * Exact cosine over a few thousand chunks is fast enough on a phone and needs no index to
     * maintain or rebuild. An approximate index is the right call only once this stops being true.
     */
    @Query("SELECT * FROM document_chunks")
    suspend fun allChunks(): List<DocumentChunkEntity>

    @Query("SELECT COUNT(*) FROM document_chunks")
    suspend fun chunkCount(): Int

    @Query("UPDATE documents SET indexed_at = :at, chunk_count = :count, embedding_model = :model, last_error = :error WHERE id = :id")
    suspend fun recordIndex(id: String, at: Long?, count: Int, model: String?, error: String?)
}
