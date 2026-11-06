package com.aphrodite.core.llm.tools

import com.aphrodite.core.database.dao.MemoryDao
import com.aphrodite.core.database.entity.MemoryCategory
import com.aphrodite.core.database.entity.MemoryEntity
import com.aphrodite.core.llm.agent.Tool
import com.aphrodite.core.llm.agent.ToolOutcome
import com.aphrodite.core.llm.agent.bounded
import com.aphrodite.core.llm.agent.requireString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject

/**
 * Writes a durable memory.
 *
 * Small models are told to call this when the user states a lasting fact. The alternative — the
 * model quietly hoping it will come up again — is exactly the recall failure the constitution
 * warns against.
 */
class SaveMemoryTool @Inject constructor(
    private val dao: MemoryDao,
) : Tool {

    override val name = "save_memory"
    override val description = "Remember a lasting fact about the user for future conversations."

    override val whenToUse =
        "When the user states a durable fact, preference, or detail about a person or project that " +
            "should still be true next time. Do not save passing context, one-off requests, or " +
            "anything already in the list of memories you were given."

    override val parameters: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("text") {
                put("type", "string")
                put("description", "The fact, as one short sentence.")
            }
            putJsonObject("category") {
                put("type", "string")
                put("description", "One of: fact, preference, person, project.")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("text")) })
    }

    override suspend fun execute(arguments: JsonObject): ToolOutcome {
        val text = arguments.requireString("text")?.trim()
            ?: return ToolOutcome.Failure("No text was provided.", "Call save_memory with a \"text\" value.")
        if (text.length > 500) {
            return ToolOutcome.Failure(
                "That memory is too long to store (${text.length} characters, limit 500).",
                "Store one short sentence. If the user wants a longer passage kept, that belongs " +
                    "in a document instead.",
            )
        }

        val category = MemoryCategory.fromId(
            arguments["category"]?.jsonPrimitive?.contentOrNull,
        )
        val now = System.currentTimeMillis()

        // Same text, same category: update rather than accumulate near-duplicates.
        val existing = dao.all().firstOrNull {
            it.text.equals(text, ignoreCase = true) && it.category == category
        }
        dao.upsert(
            existing?.copy(updatedAt = now)
                ?: MemoryEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    text = text,
                    category = category,
                    createdAt = now,
                    updatedAt = now,
                ),
        )

        return ToolOutcome.Success(
            if (existing != null) "Already remembered; refreshed it." else "Remembered: $text",
        )
    }
}

/**
 * Reads memories back.
 *
 * Pinned memories are already in the prompt, so this exists to reach the rest — the ones that were
 * saved months ago and did not match the current conversation's wording.
 */
class SearchMemoryTool @Inject constructor(
    private val dao: MemoryDao,
) : Tool {

    override val name = "search_memory"
    override val description = "Search stored memories for something the user said earlier."

    override val whenToUse =
        "When the user refers to something they told you before that is not in the memories " +
            "already provided. Look it up rather than guessing or asking them to repeat it."

    override val parameters: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put("description", "A word or phrase likely to appear in the memory.")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("query")) })
    }

    override suspend fun execute(arguments: JsonObject): ToolOutcome {
        val query = arguments.requireString("query")?.trim()
            ?: return ToolOutcome.Failure("No query was provided.", "Call search_memory with a \"query\" string.")

        val matches = dao.relevant(query, limit = 10)
        if (matches.isEmpty()) {
            return ToolOutcome.Success("Nothing stored matches \"$query\".")
        }

        return ToolOutcome.Success(
            buildString {
                appendLine("Memories matching \"$query\":")
                matches.forEach { appendLine("- [${it.category.label}] ${it.text}") }
            }.trim().bounded(),
        )
    }
}
