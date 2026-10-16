package com.aphrodite.core.llm.stream

import com.aphrodite.core.llm.model.LlmUsage
import com.aphrodite.core.llm.model.ToolCall

/**
 * The streaming event vocabulary.
 *
 * These mirror the event shapes the upstream app emits on its chat stream, so the rendering
 * layer that consumes them behaves identically whether the run is a plain answer or a full
 * agent loop with tool calls.
 */
sealed interface LlmEvent {

    /** A chunk of visible answer text. */
    data class Delta(val text: String) : LlmEvent

    /** A chunk of hidden reasoning, shown in the collapsible thinking panel. */
    data class Thinking(val text: String) : LlmEvent

    /** The agent loop advanced to another round. */
    data class AgentStep(val round: Int) : LlmEvent

    /** A tool started running. */
    data class ToolStarted(
        val callId: String,
        val tool: String,
        val command: String,
    ) : LlmEvent

    /** A tool produced output. */
    data class ToolOutput(
        val callId: String,
        val tool: String,
        val output: String,
        val exitCode: Int? = null,
    ) : LlmEvent

    /** A tool reported progress without finishing. */
    data class ToolProgress(
        val callId: String,
        val tool: String,
        val message: String,
        val elapsedSeconds: Int? = null,
    ) : LlmEvent

    /** The tools the model asked for, once the call has fully arrived. */
    data class ToolCallsRequested(val calls: List<ToolCall>) : LlmEvent

    /** A complete replacement for the turn's text, superseding any deltas already shown. */
    data class FinalResponse(val content: String) : LlmEvent

    /** Token and timing accounting. */
    data class Metrics(val usage: LlmUsage) : LlmEvent

    /** The provider answered with a different model than the one requested. */
    data class ModelActual(val requested: String, val actual: String) : LlmEvent

    /**
     * The run is waiting on the user.
     *
     * [approvalId] is opaque: the UI hands it back with a decision and never inspects it.
     */
    data class AskUser(
        val approvalId: String,
        val title: String,
        val body: String,
        val options: List<AskUserOption>,
    ) : LlmEvent

    /** The app should open or update a surface as a side effect of the run. */
    data class UiControl(val action: String, val payload: Map<String, String>) : LlmEvent

    /** The run finished cleanly. */
    data object Completed : LlmEvent

    /** The run failed. */
    data class Failed(val message: String, val status: Int? = null) : LlmEvent

    /** The run was cancelled. */
    data object Cancelled : LlmEvent
}

/** A choice on an ask-user card. */
data class AskUserOption(
    /** `approve`, `approve_task` or `deny`. */
    val decision: String,
    val label: String,
    val description: String? = null,
)
