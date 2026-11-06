package com.aphrodite.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** A stored document, indexed for retrieval. */
@Entity(
    tableName = "documents",
    indices = [Index("updated_at"), Index("indexed_at")],
)
@Serializable
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    @ColumnInfo(name = "mime") val mime: String = "text/plain",
    /** Null until the document has been chunked and embedded. */
    @ColumnInfo(name = "indexed_at") val indexedAt: Long? = null,
    @ColumnInfo(name = "chunk_count") val chunkCount: Int = 0,
    /** Which embedding model produced the vectors, so a change can invalidate them. */
    @ColumnInfo(name = "embedding_model") val embeddingModel: String? = null,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * One chunk of a document, with its vector.
 *
 * The vector is stored as a raw little-endian float array rather than JSON: it is written and read
 * in bulk, and JSON would roughly quadruple the size for no benefit.
 */
@Entity(
    tableName = "document_chunks",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("document_id")],
)
@Serializable
data class DocumentChunkEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "document_id") val documentId: String,
    val ordinal: Int,
    val text: String,
    @ColumnInfo(name = "embedding") val embedding: ByteArray,
)
