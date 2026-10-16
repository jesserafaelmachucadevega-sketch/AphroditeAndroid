package com.aphrodite.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A conversation.
 *
 * Field set mirrors the upstream session record so the ported screens behave the same way:
 * the model and endpoint the session was started with, whether retrieval is on, the folder it
 * was filed into, token and cost totals, and the two flags that drive the sidebar badges.
 */
@Entity(
    tableName = "sessions",
    indices = [
        Index("updated_at"),
        Index("archived"),
        Index("folder"),
        Index("is_important"),
    ],
)
@Serializable
data class SessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val model: String? = null,
    @ColumnInfo(name = "endpoint_id") val endpointId: String? = null,
    @ColumnInfo(name = "endpoint_name") val endpointName: String? = null,
    @ColumnInfo(name = "thinking_mode") val thinkingMode: String? = null,
    val mode: String = "chat",
    val rag: Boolean = false,
    val archived: Boolean = false,
    val folder: String? = null,
    val workspace: String? = null,
    @ColumnInfo(name = "total_tokens") val totalTokens: Long = 0,
    @ColumnInfo(name = "total_cost_usd") val totalCostUsd: Double = 0.0,
    @ColumnInfo(name = "is_important") val isImportant: Boolean = false,
    @ColumnInfo(name = "has_documents") val hasDocuments: Boolean = false,
    @ColumnInfo(name = "has_images") val hasImages: Boolean = false,
    @ColumnInfo(name = "message_count") val messageCount: Int = 0,
    @ColumnInfo(name = "last_message_at") val lastMessageAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** Who produced a message. */
enum class MessageRole { User, Assistant, System, Tool }

/**
 * A single turn in a conversation.
 *
 * [metadataJson] holds the structured extras the upstream app stores per message — timestamps,
 * metrics, tool events, sources, hidden reasoning, context-trim stats and attachment
 * references — so the schema stays stable as those grow.
 */
@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("session_id"),
        Index("session_id", "created_at"),
        Index("hidden"),
    ],
)
@Serializable
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    val role: MessageRole,
    val content: String,
    @ColumnInfo(name = "thinking") val thinking: String? = null,
    @ColumnInfo(name = "model") val model: String? = null,
    @ColumnInfo(name = "endpoint_id") val endpointId: String? = null,
    @ColumnInfo(name = "tokens") val tokens: Int = 0,
    @ColumnInfo(name = "cost_usd") val costUsd: Double = 0.0,
    @ColumnInfo(name = "hidden") val hidden: Boolean = false,
    @ColumnInfo(name = "stopped") val stopped: Boolean = false,
    @ColumnInfo(name = "metadata_json") val metadataJson: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** An attachment bound to a message, referencing an entry in the uploads table. */
@Entity(
    tableName = "chat_attachments",
    foreignKeys = [
        ForeignKey(
            entity = ChatMessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("message_id"), Index("upload_id")],
)
@Serializable
data class ChatAttachmentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "message_id") val messageId: String,
    @ColumnInfo(name = "upload_id") val uploadId: String,
    val name: String,
    val mime: String,
    val size: Long,
    @ColumnInfo(name = "width") val width: Int? = null,
    @ColumnInfo(name = "height") val height: Int? = null,
)

/**
 * A file stored by the app.
 *
 * Uploads are content addressed: [sha256] is unique so the same file is only written once, and
 * [localPath] points at the blob in app storage.
 */
@Entity(
    tableName = "uploads",
    indices = [Index(value = ["sha256"], unique = true), Index("created_at")],
)
@Serializable
data class UploadEntity(
    @PrimaryKey val id: String,
    val name: String,
    val mime: String,
    val size: Long,
    val sha256: String,
    @ColumnInfo(name = "local_path") val localPath: String,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String? = null,
    @ColumnInfo(name = "width") val width: Int? = null,
    @ColumnInfo(name = "height") val height: Int? = null,
    @ColumnInfo(name = "extracted_text") val extractedText: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
