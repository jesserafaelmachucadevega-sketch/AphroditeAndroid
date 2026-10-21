package com.aphrodite.core.llm.agent

import com.aphrodite.core.llm.LlmClient
import com.aphrodite.core.llm.SystemConstitution
import com.aphrodite.core.llm.model.LlmMessage
import com.aphrodite.core.llm.model.LlmRequest
import com.aphrodite.core.llm.model.LlmRole
import com.aphrodite.core.llm.model.LlmUsage
import com.aphrodite.core.llm.model.ToolCall
import com.aphrodite.core.llm.stream.LlmEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs a model turn, executing tools until it produces a final answer.
 *
 * Designed around the fact that a small local model is unreliable at tool use. Three things make
 * it work:
 *
 * 1. **Both call styles.** Native function calling is used when the provider offers it. If the
 *    model instead writes the call as text, [PromptedToolParser] recovers it, so a model without
 *    function-calling support still has tools.
 * 2. **Errors it can act on.** A failed call is fed back with a hint about what to change. A
 *    model given only "error" repeats the same call until the round budget runs out.
 * 3. **Bounded retries per target.** The same tool with the same arguments is only retried twice,
 *    which stops a small model looping on one broken call while leaving room to correct it.
 */
@Singleton
class AgentLoop @Inject constructor(
    private val client: LlmClient,
    private val registry: ToolRegistry,
) {
    fun run(
        request: LlmRequest,
        maxRounds: Int = DEFAULT_MAX_ROUNDS,
    ): Flow<LlmEvent> = flow {
        val systemPrompt = SystemConstitution.compose(request.persona, registry.inventory())
        val conversation = mutableListOf<LlmMessage>()
        conversation += LlmMessage(role = LlmRole.System, content = systemPrompt)
        conversation += request.messages

        val attempts = mutableMapOf<String, Int>()
        var lastVisible = StringBuilder()

        for (round in 0 until maxRounds) {
            if (round > 0) emit(LlmEvent.AgentStep(round))

            val roundRequest = request.copy(
                // Streaming a system message every round would duplicate it, so it is only in
                // the message list.
                messages = conversation.toList(),
                tools = registry.definitions(),
            )

            val text = StringBuilder()
            val nativeCalls = mutableListOf<ToolCall>()
            var usage = LlmUsage()
            var failed: LlmEvent.Failed? = null

            try {
                client.stream(roundRequest).collect { event ->
                    when (event) {
                        is LlmEvent.Delta -> {
                            text.append(event.text)
                            emit(event)
                        }

                        is LlmEvent.Thinking -> emit(event)

                        is LlmEvent.ToolCallsRequested -> nativeCalls += event.calls

                        is LlmEvent.Metrics -> usage += event.usage

                        is LlmEvent.Failed -> failed = event

                        LlmEvent.Completed -> Unit

                        LlmEvent.Cancelled -> throw CancellationException()

                        else -> emit(event)
                    }
                }
            } catch (e: CancellationException) {
                emit(LlmEvent.Cancelled)
                throw e
            }

            failed?.let {
                emit(it)
                return@flow
            }

            val raw = text.toString()
            lastVisible = StringBuilder(raw)

            // Prefer native calls; recover text-written ones only when there are none.
            val prompted = if (nativeCalls.isEmpty()) {
                PromptedToolParser.parse(raw, registry.names())
            } else {
                PromptedToolParser.Result(raw, emptyList())
            }

            if (prompted.hasCalls) {
                // The visible answer must not include the call syntax.
                emit(LlmEvent.FinalResponse(prompted.visible))
            }

            val calls = if (nativeCalls.isNotEmpty()) nativeCalls else prompted.calls
            if (calls.isEmpty()) {
                if (usage.totalTokens > 0) emit(LlmEvent.Metrics(usage))
                emit(LlmEvent.Completed)
                return@flow
            }

            // Record the assistant turn that asked for the tools, so the provider sees a valid
            // call/result pairing on the next round.
            conversation += LlmMessage(
                role = LlmRole.Assistant,
                content = prompted.visible,
                toolCalls = calls,
            )

            for (call in calls) {
                val key = "${call.name}:${call.arguments}"
                val attempt = (attempts[key] ?: 0) + 1
                attempts[key] = attempt

                if (attempt > MAX_ATTEMPTS_PER_TARGET) {
                    val message = "Skipped: ${call.name} already failed twice with these arguments."
                    emit(LlmEvent.ToolOutput(call.id, call.name, message, exitCode = 1))
                    conversation += LlmMessage(
                        role = LlmRole.Tool,
                        toolCallId = call.id,
                        name = call.name,
                        content = "$message Do not retry it; answer with what you have.",
                    )
                    continue
                }

                emit(LlmEvent.ToolStarted(call.id, call.name, describeCommand(call)))

                val tool = registry.find(call.name)
                if (tool == null) {
                    val message = "Unknown tool \"${call.name}\"."
                    emit(LlmEvent.ToolOutput(call.id, call.name, message, exitCode = 1))
                    conversation += LlmMessage(
                        role = LlmRole.Tool,
                        toolCallId = call.id,
                        name = call.name,
                        content = "$message Available tools: ${registry.names().joinToString(", ")}.",
                    )
                    continue
                }

                val outcome = runCatching { tool.execute(parseArguments(call)) }
                    .getOrElse { error ->
                        ToolOutcome.Failure(
                            "The tool threw: ${error.message ?: "unknown error"}",
                            "Check the arguments and try again once.",
                        )
                    }

                when (outcome) {
                    is ToolOutcome.Success -> {
                        emit(LlmEvent.ToolOutput(call.id, call.name, outcome.output, exitCode = 0))
                        conversation += LlmMessage(
                            role = LlmRole.Tool,
                            toolCallId = call.id,
                            name = call.name,
                            content = outcome.output,
                        )
                    }

                    is ToolOutcome.Failure -> {
                        emit(LlmEvent.ToolOutput(call.id, call.name, outcome.message, exitCode = 1))
                        // The hint is what makes the retry different from the first attempt.
                        conversation += LlmMessage(
                            role = LlmRole.Tool,
                            toolCallId = call.id,
                            name = call.name,
                            content = buildString {
                                append("Tool failed: ").append(outcome.message)
                                outcome.hint?.let { append("\nWhat to change: ").append(it) }
                                if (attempt >= MAX_ATTEMPTS_PER_TARGET) {
                                    append("\nThis was the final attempt for these arguments.")
                                }
                            },
                        )
                    }
                }
            }
        }

        // Round budget exhausted: keep whatever the model last wrote rather than dropping it.
        if (lastVisible.isNotBlank()) emit(LlmEvent.FinalResponse(lastVisible.toString()))
        emit(
            LlmEvent.Failed(
                "The agent used all $maxRounds rounds without finishing. The answer above is " +
                    "what it had so far.",
            ),
        )
    }

    private fun parseArguments(call: ToolCall): JsonObject =
        runCatching { Json.parseToJsonElement(call.arguments.ifBlank { "{}" }).jsonObject }
            .getOrElse { JsonObject(emptyMap()) }

    /** A short, human-readable summary of what the tool is being asked to do. */
    private fun describeCommand(call: ToolCall): String {
        val args = parseArguments(call)
        return (args["query"] ?: args["url"] ?: args["expression"])?.let { value ->
            value.toString().trim('"').take(120)
        } ?: ""
    }

    private companion object {
        const val DEFAULT_MAX_ROUNDS = 8
        const val MAX_ATTEMPTS_PER_TARGET = 2
    }
}
