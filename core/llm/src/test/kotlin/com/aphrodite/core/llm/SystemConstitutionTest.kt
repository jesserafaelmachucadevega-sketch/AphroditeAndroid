package com.aphrodite.core.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SystemConstitutionTest {

    @Test
    fun `the constitution carries every section`() {
        val text = SystemConstitution.TEXT
        assertTrue(text.contains("MOBILE AGENTIC ENGINE"))
        assertTrue(text.contains("OPERATIONAL PARADIGM"))
        assertTrue(text.contains("SCRATCHPAD MANDATE"))
        assertTrue(text.contains("TOOL INTEGRITY"))
        assertTrue(text.contains("RESPONSE STRUCTURE"))
    }

    @Test
    fun `a persona is appended after the constitution`() {
        val composed = SystemConstitution.compose("You are a pirate.")
        assertTrue(composed.startsWith("SYSTEM CONSTITUTION"))
        assertTrue(composed.endsWith("You are a pirate."))
        assertTrue(composed.indexOf("RESPONSE STRUCTURE") < composed.indexOf("You are a pirate."))
    }

    @Test
    fun `no persona still yields the constitution and capabilities`() {
        val composed = SystemConstitution.compose(null)
        assertTrue(composed.startsWith("SYSTEM CONSTITUTION"))
        assertTrue(composed.contains("AVAILABLE CAPABILITIES"))
        assertEquals(composed, SystemConstitution.compose("   "))
    }

    // ── Capability inventory ─────────────────────────────────────────────────

    @Test
    fun `an empty inventory says so rather than implying tools exist`() {
        val composed = SystemConstitution.compose(null, tools = emptyList())
        assertTrue(composed.contains("No tools are connected"))
        // The anti-hallucination instruction must survive with no tools available.
        assertTrue(composed.contains("say so plainly"))
    }

    @Test
    fun `a populated inventory lists every tool by name`() {
        val composed = SystemConstitution.compose(
            personaPrompt = null,
            tools = listOf(
                SystemConstitution.ToolSummary("web_search", "Search the public web for current pages."),
                SystemConstitution.ToolSummary("fetch_url", "Read a single URL and extract its text."),
            ),
        )
        assertTrue(composed.contains("web_search:"))
        assertTrue(composed.contains("fetch_url:"))
        assertTrue(composed.contains("Search the public web"))
        assertTrue(composed.contains("prefer them over memory"))
        assertTrue(!composed.contains("No tools are connected"))
    }

    @Test
    fun `the inventory comes before the persona`() {
        val composed = SystemConstitution.compose(
            personaPrompt = "You are terse.",
            tools = listOf(SystemConstitution.ToolSummary("web_search", "Search.")),
        )
        assertTrue(composed.indexOf("AVAILABLE CAPABILITIES") < composed.indexOf("You are terse."))
    }

    // ── Scratchpad extraction ────────────────────────────────────────────────

    @Test
    fun `scratchpad is lifted out of the visible reply`() {
        val raw = """
            <scratchpad>
            Goal: answer the question.
            Need: nothing external.
            </scratchpad>
            The capital of France is Paris.
        """.trimIndent()

        val parsed = ScratchpadExtractor.parse(raw)
        assertEquals("The capital of France is Paris.", parsed.visible)
        assertTrue(parsed.working.contains("Goal: answer the question."))
        assertTrue(parsed.working.startsWith("Scratchpad"))
    }

    @Test
    fun `reflection is captured separately from scratchpad`() {
        val raw = """
            <scratchpad>Plan: call the tool.</scratchpad>
            <reflection>The tool returned 404. Retrying once.</reflection>
            Done.
        """.trimIndent()

        val parsed = ScratchpadExtractor.parse(raw)
        assertEquals("Done.", parsed.visible)
        assertTrue(parsed.working.contains("Scratchpad"))
        assertTrue(parsed.working.contains("Reflection"))
        assertTrue(parsed.working.contains("Retrying once."))
    }

    @Test
    fun `tags are matched case insensitively`() {
        val parsed = ScratchpadExtractor.parse("<SCRATCHPAD>thinking</SCRATCHPAD>\nAnswer.")
        assertEquals("Answer.", parsed.visible)
        assertTrue(parsed.working.contains("thinking"))
    }

    @Test
    fun `text with no working notes is untouched`() {
        val raw = "Just a plain answer."
        val parsed = ScratchpadExtractor.parse(raw)
        assertEquals(raw, parsed.visible)
        assertEquals("", parsed.working)
    }

    @Test
    fun `an unterminated scratchpad is truncated during streaming`() {
        // What the UI sees mid-stream: the block has opened but not closed.
        val partial = "<scratchpad>working on it…"
        assertEquals("", ScratchpadExtractor.visibleOnly(partial))
    }

    @Test
    fun `a closed block is hidden while streaming`() {
        val partial = "<scratchpad>plan</scratchpad>\nFirst words"
        assertEquals("First words", ScratchpadExtractor.visibleOnly(partial))
    }

    @Test
    fun `an unterminated block still yields its text on completion`() {
        // A stream cut off mid-scratchpad should not silently lose the notes.
        val parsed = ScratchpadExtractor.parse("<scratchpad>half a thought")
        assertEquals("", parsed.visible)
        assertTrue(parsed.working.contains("half a thought"))
    }

    @Test
    fun `blank input is handled`() {
        assertEquals("", ScratchpadExtractor.parse("").visible)
        assertEquals("", ScratchpadExtractor.visibleOnly(""))
    }
}
