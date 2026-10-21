package com.aphrodite.core.llm

/**
 * The app's standing system constitution.
 *
 * This is prepended to every conversation's system prompt, ahead of any persona prompt from a
 * preset. It is deliberately not user-editable: it describes how the runtime expects the model
 * to behave around tools and verification, and the rest of the app is built on that contract.
 */
object SystemConstitution {

    val TEXT: String = """
        SYSTEM CONSTITUTION: MOBILE AGENTIC ENGINE

        1. OPERATIONAL PARADIGM
        - You are a compact runtime agent optimized for hyper-precise logic, tool invocation, and structured execution.
        - You must prioritize accuracy and tool execution over raw memory generation.

        2. SCRATCHPAD MANDATE
        - Before generating any visible response or calling an external tool, you MUST open a <scratchpad> tag.
        - Inside <scratchpad>:
          a. Deconstruct the user's goal into atomic steps.
          b. Identify missing information.
          c. Determine if a local tool is required.
          d. Verify tool call syntax and arguments.

        3. TOOL INTEGRITY & FACTUAL BOUNDARIES
        - Do NOT generate factual claims, code syntaxes, or calculated values from memory if a verification tool exists.
        - If a tool fails, analyze the payload in <reflection> tags before retrying. Max 2 retries per tool target.

        4. RESPONSE STRUCTURE
        - Output strictly in requested formats (Markdown, JSON, or execution code).
        - Omit conversational filler ("Sure, I can help with that!"). Lead directly with the result or execution.
    """.trimIndent()

    /**
     * Builds the effective system prompt.
     *
     * The constitution always comes first so a persona cannot accidentally override the
     * operational rules; the persona is appended as additional context below it.
     *
     * [tools] is the inventory actually offered on this turn. Small local models have no way to
     * know what is available unless it is stated in the prompt, so the section is generated from
     * the live tool list rather than being static text. An empty list is stated plainly: better
     * that the model knows it has nothing to consult than that it invents a capability.
     */
    fun compose(
        personaPrompt: String?,
        tools: List<ToolSummary> = emptyList(),
    ): String {
        val persona = personaPrompt?.trim().orEmpty()
        val sections = listOfNotNull(
            TEXT,
            capabilitySection(tools),
            persona.ifEmpty { null },
        )
        return sections.joinToString("\n\n")
    }

    /** One capability as the model should see it: what it is called and when to reach for it. */
    data class ToolSummary(
        val name: String,
        val description: String,
    )

    private fun capabilitySection(tools: List<ToolSummary>): String {
        if (tools.isEmpty()) {
            return """
                5. AVAILABLE CAPABILITIES
                - No tools are connected in this session. Answer from your own knowledge.
                - When you are not certain, say so plainly: state what you do not know and what
                  would be needed to find out. Do not guess, and do not invent sources, figures,
                  citations, or API details.
            """.trimIndent()
        }

        return buildString {
            appendLine("5. AVAILABLE CAPABILITIES")
            appendLine(
                "- You have the following tools. They are the only way to reach information " +
                    "beyond your training data, so prefer them over memory for anything current, " +
                    "precise, or verifiable:",
            )
            tools.forEach { tool ->
                appendLine("  - ${tool.name}: ${tool.description}")
            }
            appendLine(
                "- Call a tool when the answer depends on facts you cannot be sure of. Do not " +
                    "call one for arithmetic or reasoning you can do directly.",
            )
            append(
                "- If every relevant tool fails, say what you tried and what you could not " +
                    "find. A stated gap is useful; a confident guess is not.",
            )
        }
    }
}

/**
 * Splits `<scratchpad>` and `<reflection>` blocks out of a model reply.
 *
 * The constitution instructs the model to write these tags, and they are working notes rather
 * than an answer. They are moved into the collapsible reasoning panel so the visible reply stays
 * clean, while the notes remain readable on demand.
 */
object ScratchpadExtractor {

    private val BLOCK = Regex(
        """<(scratchpad|reflection)>(.*?)</\1>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    /** An unterminated block, which happens when a stream is cut off mid-scratchpad. */
    private val OPEN_BLOCK = Regex(
        """<(scratchpad|reflection)>(.*)$""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    data class Parsed(val visible: String, val working: String)

    /**
     * The text so far with working notes removed.
     *
     * Used while a reply is still streaming: an unterminated scratchpad is truncated rather than
     * shown, so the user never watches internal notes scroll past and then vanish.
     */
    fun visibleOnly(raw: String): String {
        if (raw.isBlank()) return ""
        var visible = BLOCK.replace(raw, "")
        OPEN_BLOCK.find(visible)?.let { visible = visible.substring(0, it.range.first) }
        return visible.trim()
    }

    fun parse(raw: String): Parsed {
        if (raw.isBlank()) return Parsed(raw, "")

        val working = StringBuilder()
        BLOCK.findAll(raw).forEach { match ->
            val label = match.groupValues[1].lowercase().replaceFirstChar { it.uppercase() }
            val body = match.groupValues[2].trim()
            if (body.isNotEmpty()) {
                if (working.isNotEmpty()) working.append("\n\n")
                working.append(label).append('\n').append(body)
            }
        }

        var visible = BLOCK.replace(raw, "")
        OPEN_BLOCK.find(visible)?.let { match ->
            val label = match.groupValues[1].lowercase().replaceFirstChar { it.uppercase() }
            val body = match.groupValues[2].trim()
            if (body.isNotEmpty()) {
                if (working.isNotEmpty()) working.append("\n\n")
                working.append(label).append('\n').append(body)
            }
            visible = visible.substring(0, match.range.first)
        }

        return Parsed(visible.trim(), working.toString().trim())
    }
}
