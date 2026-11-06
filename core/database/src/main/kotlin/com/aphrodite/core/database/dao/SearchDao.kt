package com.aphrodite.core.database.dao

import androidx.room.Dao
import androidx.room.Query

/** One search result, whatever it came from. */
data class SearchHit(
    val id: String,
    val kind: String,
    val title: String,
    val snippet: String?,
    val updatedAt: Long,
)

/**
 * Search across everything the user has written.
 *
 * One query rather than five, so results interleave by recency instead of arriving grouped by
 * table — a note from this morning should outrank a document from last year regardless of type.
 *
 * This is plain substring matching, deliberately. It is what a person expects from a search box:
 * predictable, instant, and honest about what it found. Semantic search over the same corpus lives
 * in the documents module and answers a different question.
 */
@Dao
interface SearchDao {

    @Query(
        """
        SELECT id, 'chat' AS kind, name AS title, NULL AS snippet, updated_at AS updatedAt
            FROM sessions WHERE name LIKE '%' || :query || '%'
        UNION ALL
        SELECT id, 'note' AS kind,
            CASE WHEN title = '' THEN body ELSE title END AS title,
            body AS snippet, updated_at AS updatedAt
            FROM notes WHERE title LIKE '%' || :query || '%' OR body LIKE '%' || :query || '%'
        UNION ALL
        SELECT id, 'document' AS kind, title AS title, body AS snippet, updated_at AS updatedAt
            FROM documents WHERE title LIKE '%' || :query || '%' OR body LIKE '%' || :query || '%'
        UNION ALL
        SELECT id, 'memory' AS kind, text AS title, NULL AS snippet, updated_at AS updatedAt
            FROM memories WHERE text LIKE '%' || :query || '%'
        UNION ALL
        SELECT id, 'contact' AS kind, display_name AS title,
            COALESCE(email, phone, organization) AS snippet, updated_at AS updatedAt
            FROM contacts
            WHERE display_name LIKE '%' || :query || '%'
               OR email LIKE '%' || :query || '%'
               OR organization LIKE '%' || :query || '%'
        ORDER BY updatedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun search(query: String, limit: Int = 80): List<SearchHit>
}
