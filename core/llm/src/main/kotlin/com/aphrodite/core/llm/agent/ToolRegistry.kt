package com.aphrodite.core.llm.agent

import com.aphrodite.core.llm.SystemConstitution
import com.aphrodite.core.llm.model.ToolDefinition
import com.aphrodite.core.llm.tools.CalculateTool
import com.aphrodite.core.llm.tools.CurrentTimeTool
import com.aphrodite.core.llm.tools.FetchUrlTool
import com.aphrodite.core.llm.tools.WebSearchTool
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything the model can call.
 *
 * The registry is the single source for three different consumers: the provider's tool schemas,
 * the system prompt's capability inventory, and dispatch. Keeping them derived from one list is
 * what stops the prompt from advertising a tool that cannot actually be executed.
 */
@Singleton
class ToolRegistry @Inject constructor(
    webSearch: WebSearchTool,
    fetchUrl: FetchUrlTool,
    currentTime: CurrentTimeTool,
    calculate: CalculateTool,
    private val mcp: com.aphrodite.core.llm.mcp.McpManager,
) {
    private val builtIn: List<Tool> = listOf(webSearch, fetchUrl, currentTime, calculate)

    /**
     * Built-in tools plus everything currently exposed by connected MCP servers.
     *
     * Read as a property rather than a captured list so a server connecting mid-session is picked
     * up by the next turn without restarting anything.
     */
    val tools: List<Tool> get() = builtIn + mcp.tools.value

    /** Schemas sent to providers that support native function calling. */
    fun definitions(): List<ToolDefinition> = tools.map { tool ->
        ToolDefinition(
            name = tool.name,
            description = tool.description,
            parameters = tool.parameters,
        )
    }

    /** Names and guidance injected into the system prompt. */
    fun inventory(): List<SystemConstitution.ToolSummary> = tools.map { tool ->
        SystemConstitution.ToolSummary(
            name = tool.name,
            description = "${tool.description} ${tool.whenToUse}",
        )
    }

    fun names(): Set<String> = tools.mapTo(mutableSetOf()) { it.name }

    fun find(name: String): Tool? = tools.firstOrNull { it.name == name }
}
