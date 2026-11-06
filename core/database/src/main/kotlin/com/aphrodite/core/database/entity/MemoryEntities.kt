package com.aphrodite.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** What kind of thing a memory records. */
enum class MemoryCategory(val id: String, val label: String) {
    Fact("fact", "Fact"),
    Preference("preference", "Preference"),
    Person("person", "Person"),
    Project("project", "Project"),
    ;

    companion object {
        fun fromId(id: String?): MemoryCategory = entries.firstOrNull { it.id == id } ?: Fact
    }
}

/**
 * A durable fact the assistant should remember across conversations.
 *
 * Memories are short by design. A memory long enough to need summarising is a document, and
 * putting documents here would bury the few lines that actually matter.
 */
@Entity(
    tableName = "memories",
    indices = [Index("category"), Index("pinned"), Index("created_at"), Index("source_session_id")],
)
@Serializable
data class MemoryEntity(
    @PrimaryKey val id: String,
    val text: String,
    val category: MemoryCategory = MemoryCategory.Fact,
    val pinned: Boolean = false,
    /** Optional vector for semantic recall; null until an embedding provider is configured. */
    @ColumnInfo(name = "embedding") val embedding: ByteArray? = null,
    @ColumnInfo(name = "source_session_id") val sourceSessionId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
