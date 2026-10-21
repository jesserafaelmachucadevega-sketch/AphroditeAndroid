package com.aphrodite.core.llm.agent

import com.aphrodite.core.llm.tools.ExpressionParser
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Covers the two pieces of pure logic the agent depends on: recovering tool calls a small model
 * wrote as text, and doing arithmetic the model should not be trusted to do in its head.
 */
class PromptedToolParserTest {

    private val tools = setOf("web_search", "fetch_url", "calculate", "current_time")

    @Test
    fun `a fenced call is recovered and removed from the reply`() {
        val text = """
            I'll look that up.
            ```json
            {"tool": "web_search", "arguments": {"query": "kotlin 2.4 release notes"}}
            ```
        """.trimIndent()

        val result = PromptedToolParser.parse(text, tools)
        assertEquals(1, result.calls.size)
        assertEquals("web_search", result.calls[0].name)
        assertTrue(result.calls[0].arguments.contains("kotlin 2.4"))
        assertTrue(!result.visible.contains("web_search"))
        assertTrue(result.visible.contains("I'll look that up."))
    }

    @Test
    fun `a bare call object is recovered`() {
        val text = """Let me check. {"name": "current_time", "arguments": {}}"""
        val result = PromptedToolParser.parse(text, tools)
        assertEquals(1, result.calls.size)
        assertEquals("current_time", result.calls[0].name)
    }

    @Test
    fun `the plural envelope is understood`() {
        val text = """{"tool_calls": [{"name": "calculate", "arguments": {"expression": "2+2"}}]}"""
        val result = PromptedToolParser.parse(text, tools)
        assertEquals(1, result.calls.size)
        assertEquals("calculate", result.calls[0].name)
    }

    @Test
    fun `an unknown tool name is treated as prose`() {
        // Important: executing a name the app does not have would produce a confusing error.
        val text = """{"tool": "delete_everything", "arguments": {}}"""
        val result = PromptedToolParser.parse(text, tools)
        assertTrue(result.calls.isEmpty())
        assertEquals(text, result.visible)
    }

    @Test
    fun `ordinary json in an answer is left alone`() {
        val text = """The config looks like {"name": "server", "port": 8080}."""
        val result = PromptedToolParser.parse(text, tools)
        assertTrue(result.calls.isEmpty())
        assertEquals(text, result.visible)
    }

    @Test
    fun `no known tools means no parsing`() {
        val text = """{"tool": "web_search", "arguments": {"query": "x"}}"""
        val result = PromptedToolParser.parse(text, emptySet())
        assertTrue(result.calls.isEmpty())
        assertEquals(text, result.visible)
    }

    @Test
    fun `string arguments are accepted`() {
        val text = """{"tool": "web_search", "arguments": "{\"query\": \"weather\"}"}"""
        val result = PromptedToolParser.parse(text, tools)
        assertEquals(1, result.calls.size)
        assertTrue(result.calls[0].arguments.contains("weather"))
    }

    @Test
    fun `plain prose is untouched`() {
        val text = "Paris is the capital of France."
        val result = PromptedToolParser.parse(text, tools)
        assertTrue(result.calls.isEmpty())
        assertEquals(text, result.visible)
    }
}

class ExpressionParserTest {

    private fun eval(expression: String): Double = ExpressionParser(expression).parse()

    @Test
    fun `simple arithmetic`() {
        assertEquals(4.0, eval("2+2"))
        assertEquals(10.0, eval("2 * 5"))
        assertEquals(2.5, eval("5 / 2"))
    }

    @Test
    fun `precedence is respected`() {
        assertEquals(14.0, eval("2 + 3 * 4"))
        assertEquals(20.0, eval("(2 + 3) * 4"))
        assertEquals(8.0, eval("2 ^ 3"))
    }

    @Test
    fun `unary minus works`() {
        assertEquals(-5.0, eval("-5"))
        assertEquals(-1.0, eval("2 + -3"))
    }

    @Test
    fun `functions work`() {
        assertEquals(3.0, eval("sqrt(9)"))
        assertEquals(4.0, eval("abs(-4)"))
        assertEquals(3.0, eval("round(3.4)"))
        assertEquals(2.0, eval("min(5, 2)"))
        assertEquals(5.0, eval("max(5, 2)"))
    }

    @Test
    fun `realistic expression`() {
        // 8% tax on 1200, split three ways.
        assertTrue(abs(eval("(1200 * 1.08) / 3") - 432.0) < 0.0001)
    }

    @Test
    fun `division by zero is reported rather than returning infinity`() {
        assertFailsWith<IllegalArgumentException> { eval("1 / 0") }
        assertFailsWith<IllegalArgumentException> { eval("1 % 0") }
    }

    @Test
    fun `unknown functions and malformed input fail`() {
        assertFailsWith<IllegalArgumentException> { eval("exec(1)") }
        assertFailsWith<IllegalArgumentException> { eval("2 +") }
        assertFailsWith<IllegalArgumentException> { eval("(1 + 2") }
        assertFailsWith<IllegalArgumentException> { eval("hello") }
    }
}
