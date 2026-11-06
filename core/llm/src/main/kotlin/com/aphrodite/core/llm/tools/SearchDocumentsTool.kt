package com.aphrodite.core.llm.tools

import com.aphrodite.core.llm.agent.Tool
import com.aphrodite.core.llm.agent.ToolOutcome
import com.aphrodite.core.llm.agent.bounded
import com.aphrodite.core.llm.agent.optionalInt
import com.aphrodite.core.llm.agent.requireString
import com.aphrodite.core.llm.rag.RagRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject

/**
 * Retrieval over the user's own documents.
 *
 * The whole point of this tool is grounding: the constitution tells the model not to answer from
 * memory when a verification tool exists, and for anything the user wrote themselves this is that
 * tool. Returning the source title alongside each passage matters as much as the passage — an
 * answer the user cannot trace back is barely better than a guess.
 */
class SearchDocumentsTool @Inject constructor(
    private val rag: RagRepository,
) : Tool {

    override val name = "search_documents"
    override val description =
        "Search the user's own stored documents and return the closest passages, with the " +
            "document each came from."

    override val whenToUse =
        "Use whenever the question could be answered by the user's own notes, documents or " +
            "reference material. Look it up rather than recalling it — these are documents the " +
            "model has never seen."

    override val parameters: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put("description", "What to look for. Prefer words likely to appear in the text.")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "How many passages to return, 1 to 10. Defaults to 5.")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("query")) })
    }

    override suspend fun execute(arguments: JsonObject): ToolOutcome {
        val query = arguments.requireString("query")
            ?: return ToolOutcome.Failure(
                "No query was provided.",
                "Call search_documents with a \"query\" string.",
            )

        val limit = (arguments.optionalInt("limit") ?: 5).coerceIn(1, 10)

        val result = rag.search(query, limit)
        result.exceptionOrNull()?.let { error ->
            return ToolOutcome.Failure(
                error.message ?: "Searching documents failed.",
                // The most common cause is a provider with no embeddings endpoint, which is worth
                // naming here rather than making the model guess at a workaround.
                "The embedding provider may not support /embeddings. Ask the user to check " +
                    "Settings → Providers.",
            )
        }

        val hits = result.getOrDefault(emptyList())
        if (hits.isEmpty()) {
            return ToolOutcome.Success(
                "No documents matched \"$query\". Either nothing is indexed yet, or the wording " +
                    "does not appear in the stored text.",
            )
        }

        return ToolOutcome.Success(
            buildString {
                appendLine("Top passages for \"$query\":")
                hits.forEachIndexed { index, hit ->
                    appendLine()
                    appendLine("${index + 1}. [${hit.documentTitle}] (relevance ${"%.2f".format(hit.score)})")
                    appendLine(hit.text)
                }
            }.trim().bounded(),
        )
    }
}
