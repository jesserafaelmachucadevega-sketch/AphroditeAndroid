package com.aphrodite.core.llm.agent

import com.aphrodite.core.llm.model.ToolCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts tool calls a model wrote as text instead of through a native function-calling API.
 *
 * Small local models frequently cannot emit native tool calls — the runtime either has no
 * function-calling support or the model was not trained for it. Without this, those models have
 * no tools at all, which is the single biggest thing holding them back.
 *
 * The accepted shapes, in order of how often models actually produce them:
 *
 * ```json
 * {"tool": "web_search", "arguments": {"query": "..."}}
 * {"name": "web_search", "parameters": {"query": "..."}}
 * {"tool_calls": [{"name": "...", "arguments": {...}}]}
 * ```
 *
 * Both fenced (```json ... ```) and bare objects are handled. Anything that does not match is
 * left alone, so ordinary JSON in an answer is not mistaken for a tool call.
 */
object PromptedToolParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val FENCED = Regex("""```(?:json|tool|tool_call)?\s*\n?(.*?)```""", RegexOption.DOT_MATCHES_ALL)

    /**
     * One JSON object, allowing a single level of nesting for `{"tool": ..., "arguments": {...}}`.
     *
     * Written as a raw string with explicit escapes rather than nested quantifier syntax: Android
     * compiles patterns with ICU, not the desktop JDK's engine, and some constructs that pass on
     * the JVM throw PatternSyntaxException on device. Note that unit tests run on the JVM, so they
     * cannot catch this.
     */
    private val BARE_OBJECT = Regex("""\{[^{}]*(?:\{[^{}]*\}[^{}]*)?\}""")

    private val KNOWN_KEYS = setOf("tool", "name", "tool_name", "function", "tool_calls", "arguments", "parameters", "args", "input")

    data class Result(
        /** The reply with the tool-call blocks removed. */
        val visible: String,
        val calls: List<ToolCall>,
    ) {
        val hasCalls: Boolean get() = calls.isNotEmpty()
    }

    /**
     * Finds tool calls in [text] and returns the remainder.
     *
     * [knownTools] is used to reject lookalike JSON: an object naming a tool that does not exist
     * is prose, not a call, and executing it would produce a confusing error.
     */
    fun parse(text: String, knownTools: Set<String>): Result {
        if (text.isBlank() || knownTools.isEmpty()) return Result(text, emptyList())

        val calls = mutableListOf<ToolCall>()
        val consumed = mutableListOf<IntRange>()

        fun consider(candidate: String, range: IntRange) {
            extract(candidate, knownTools)?.let { found ->
                calls += found
                consumed += range
            }
        }

        FENCED.findAll(text).forEach { match ->
            val body = match.groupValues[1].trim()
            if (body.startsWith("{") || body.startsWith("[")) {
                consider(body, match.range)
            }
        }

        // Bare objects outside those fences. Scanning the already-stripped text avoids
        // reprocessing what a fence contained.
        val withoutFences = FENCED.replace(text) { match ->
            if (consumed.any { it.first == match.range.first }) " ".repeat(match.value.length)
            else match.value
        }
        BARE_OBJECT.findAll(withoutFences).forEach { match ->
            val body = match.value
            if (body.any { it == '"' } && KNOWN_KEYS.any { body.contains("\"$it\"") }) {
                consider(body, match.range)
            }
        }

        if (calls.isEmpty()) return Result(text, emptyList())

        // Remove the consumed ranges back-to-front so earlier indices stay valid.
        val visible = buildString {
            var cursor = 0
            consumed.sortedBy { it.first }.forEach { range ->
                if (range.first > cursor) append(text, cursor, range.first)
                cursor = maxOf(cursor, range.last + 1)
            }
            if (cursor < text.length) append(text, cursor, text.length)
        }.trim()

        return Result(visible, calls)
    }

    /** Parses one object (or an array of them) into a call, or null when it is not a tool call. */
    private fun extract(body: String, knownTools: Set<String>): ToolCall? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null

        // Explicit envelope: {"tool_calls":[{...}]} — take the first actionable entry.
        (root["tool_calls"] as? kotlinx.serialization.json.JsonArray)?.forEach { entry ->
            (entry as? JsonObject)?.let { single -> extractSingle(single, knownTools)?.let { return it } }
        }

        return extractSingle(root, knownTools)
    }

    private fun extractSingle(root: JsonObject, knownTools: Set<String>): ToolCall? {
        // {"tool": "x"} or {"function": {"name": "x"}}
        val name = (root["tool"] ?: root["name"] ?: root["tool_name"] ?: root["function"])
            ?.let { element ->
                when (element) {
                    is JsonObject -> element["name"]?.jsonPrimitive?.contentOrNull
                    else -> element.jsonPrimitive.contentOrNull
                }
            }
            ?: return null

        if (name !in knownTools) return null

        val arguments = (root["arguments"] ?: root["parameters"] ?: root["args"] ?: root["input"])
        val argumentsJson = when (arguments) {
            null -> "{}"
            is JsonObject -> arguments.toString()
            else -> arguments.jsonPrimitive.contentOrNull ?: "{}"
        }

        return ToolCall(
            id = "prompted_" + name + "_" + Integer.toHexString(argumentsJson.hashCode()),
            name = name,
            arguments = argumentsJson,
        )
    }
}
