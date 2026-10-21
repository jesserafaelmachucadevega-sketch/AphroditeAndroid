package com.aphrodite.core.llm.agent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * The result of running a tool.
 *
 * [Failure] deliberately carries a [hint]: a small model that receives only "error" will usually
 * repeat the same call. A short, specific sentence about what to change is what actually breaks
 * the loop.
 */
sealed interface ToolOutcome {
    data class Success(val output: String) : ToolOutcome

    data class Failure(
        val message: String,
        val hint: String? = null,
        val retryable: Boolean = true,
    ) : ToolOutcome
}

/**
 * Something the model can do.
 *
 * [whenToUse] is not sent to the provider's tool schema — it goes into the system prompt's
 * capability inventory, because knowing *when* to reach for a tool is what small models get
 * wrong, and a JSON schema does not convey it.
 */
interface Tool {
    val name: String
    val description: String

    /** One sentence aimed at the model: when this is the right tool. */
    val whenToUse: String

    /** JSON Schema for the arguments object. */
    val parameters: JsonObject

    suspend fun execute(arguments: JsonObject): ToolOutcome
}

/** Reads a required string argument, reporting a useful error when it is missing. */
internal fun JsonObject.requireString(key: String): String? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

internal fun JsonObject.optionalInt(key: String): Int? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull

/** Cap on any single tool result, so one large page cannot blow the context window. */
internal const val MAX_TOOL_OUTPUT_CHARS = 12_000

internal fun String.bounded(limit: Int = MAX_TOOL_OUTPUT_CHARS): String =
    if (length <= limit) this else take(limit) + "\n\n[truncated at $limit characters]"
