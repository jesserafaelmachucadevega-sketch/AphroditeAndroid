package com.aphrodite.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.aphrodite.core.database.entity.ChatAttachmentEntity
import com.aphrodite.core.database.entity.ChatMessageEntity
import com.aphrodite.core.database.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Query("SELECT * FROM sessions WHERE archived = 0 ORDER BY is_important DESC, COALESCE(last_message_at, created_at) DESC")
    fun observeActive(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE archived = 1 ORDER BY COALESCE(last_message_at, created_at) DESC")
    fun observeArchived(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeById(id: String): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: String): SessionEntity?

    @Query(
        "SELECT * FROM sessions WHERE archived = 0 AND " +
            "(name LIKE '%' || :query || '%' OR folder LIKE '%' || :query || '%') " +
            "ORDER BY COALESCE(last_message_at, created_at) DESC",
    )
    suspend fun search(query: String): List<SessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM sessions WHERE id IN (:ids)")
    suspend fun deleteMany(ids: List<String>)

    @Query("DELETE FROM sessions WHERE archived = 1")
    suspend fun deleteArchived()

    @Query("UPDATE sessions SET archived = :archived, updated_at = :now WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, now: Long)

    @Query("UPDATE sessions SET is_important = :important, updated_at = :now WHERE id = :id")
    suspend fun setImportant(id: String, important: Boolean, now: Long)

    @Query("UPDATE sessions SET folder = :folder, updated_at = :now WHERE id = :id")
    suspend fun setFolder(id: String, folder: String?, now: Long)

    @Query("UPDATE sessions SET name = :name, updated_at = :now WHERE id = :id")
    suspend fun rename(id: String, name: String, now: Long)

    @Query("UPDATE sessions SET model = :model, endpoint_id = :endpointId, updated_at = :now WHERE id = :id")
    suspend fun setModel(id: String, model: String?, endpointId: String?, now: Long)

    @Query("SELECT DISTINCT folder FROM sessions WHERE folder IS NOT NULL AND folder != '' ORDER BY folder")
    fun observeFolders(): Flow<List<String>>

    @Query("SELECT COALESCE(MAX(updated_at), 0) FROM sessions")
    suspend fun latestUpdate(): Long
}

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId AND hidden = 0 ORDER BY created_at ASC")
    fun observeForSession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId AND hidden = 0 ORDER BY created_at ASC")
    suspend fun forSession(sessionId: String): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY created_at ASC LIMIT :limit OFFSET :offset")
    suspend fun page(sessionId: String, limit: Int, offset: Int): List<ChatMessageEntity>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE session_id = :sessionId")
    suspend fun countForSession(sessionId: String): Int

    @Query("SELECT * FROM chat_messages WHERE id = :id")
    suspend fun getById(id: String): ChatMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<ChatMessageEntity>)

    @Update
    suspend fun update(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun delete(id: String)

    /** Removes the given message and every message after it, used by "delete from here". */
    @Query(
        "DELETE FROM chat_messages WHERE session_id = :sessionId AND created_at >= " +
            "(SELECT created_at FROM chat_messages WHERE id = :messageId)",
    )
    suspend fun truncateFrom(sessionId: String, messageId: String)

    @Query("DELETE FROM chat_messages WHERE session_id = :sessionId")
    suspend fun deleteForSession(sessionId: String)

    @Query("UPDATE chat_messages SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean)

    /** Full-text search across message bodies, used by the in-app chat search. */
    @Query("SELECT * FROM chat_messages WHERE content LIKE '%' || :query || '%' ORDER BY created_at DESC LIMIT :limit")
    suspend fun search(query: String, limit: Int): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttachments(attachments: List<ChatAttachmentEntity>)

    @Query("SELECT * FROM chat_attachments WHERE message_id = :messageId")
    suspend fun attachmentsFor(messageId: String): List<ChatAttachmentEntity>

    @Query("SELECT * FROM chat_attachments WHERE message_id IN (:messageIds)")
    suspend fun attachmentsForMany(messageIds: List<String>): List<ChatAttachmentEntity>

    /** Recomputes the denormalised counters the sidebar reads. */
    @Transaction
    suspend fun refreshSessionStats(sessionId: String, now: Long) {
        val count = countForSession(sessionId)
        setStats(sessionId, count, now)
    }

    @Query(
        "UPDATE sessions SET message_count = :count, " +
            "last_message_at = :now, updated_at = :now, " +
            "has_images = EXISTS(SELECT 1 FROM chat_attachments ca " +
            "  JOIN chat_messages m ON m.id = ca.message_id " +
            "  WHERE m.session_id = sessions.id AND ca.mime LIKE 'image/%') " +
            "WHERE id = :sessionId",
    )
    suspend fun setStats(sessionId: String, count: Int, now: Long)
}
