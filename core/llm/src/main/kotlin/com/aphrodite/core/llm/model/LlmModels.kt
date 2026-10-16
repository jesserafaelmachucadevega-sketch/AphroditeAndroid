package com.aphrodite.core.llm.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Who produced a turn in a provider request. */
enum class LlmRole { System, User, Assistant, Tool }

/** A block of content inside a message. Providers accept these as a list for multimodal input. */
sealed interface ContentBlock {
    data class Text(val text: String) : ContentBlock

    /** An image, either inline base64 or a URL the provider can fetch. */
    data class Image(val mimeType: String, val base64: String? = null, val url: String? = null) : ContentBlock

    /** A document the provider should read directly, where it supports that. */
    data class Document(val mimeType: String, val base64: String, val name: String? = null) : ContentBlock
}

/**
 * One message in a provider request.
 *
 * [toolCalls] is set on assistant turns that requested tools, and [toolCallId] identifies which
 * call a tool result answers.
 */
@Serializable
data class LlmMessage(
    val role: LlmRole,
    val content: String = "",
    val blocks: List<ContentBlock> = emptyList(),
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    val name: String? = null,
) {
    val text: String
        get() = content.ifEmpty { blocks.filterIsInstance<ContentBlock.Text>().joinToString("\n") { it.text } }
}

/** A tool the model may call. */
@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

/** A tool call the model asked for. */
@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

/** The result of running a tool, fed back to the model. */
@Serializable
data class ToolResult(
    val callId: String,
    val name: String,
    val output: String,
    val isError: Boolean = false,
)

/** Sampling and request-shaping options. */
@Serializable
data class GenerationSettings(
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxOutputTokens: Int? = null,
    /** `on`, `off`, or null to let the provider decide. */
    val thinkingMode: String? = null,
    val reasoningEffort: String? = null,
    /** Ask the provider to emit usage on the final stream frame. */
    val includeUsage: Boolean = true,
)

/** Token accounting for one call. */
@Serializable
data class LlmUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val reasoningTokens: Int = 0,
    val totalTokens: Int = 0,
) {
    operator fun plus(other: LlmUsage) = LlmUsage(
        promptTokens = promptTokens + other.promptTokens,
        completionTokens = completionTokens + other.completionTokens,
        reasoningTokens = reasoningTokens + other.reasoningTokens,
        totalTokens = totalTokens + other.totalTokens,
    )
}

/**
 * What a provider says about a model.
 *
 * All of these are optional because most providers report nothing beyond an id; the catalog
 * fills in known values and the probe fills in the rest.
 */
@Serializable
data class ModelDescriptor(
    val id: String,
    val displayName: String? = null,
    val contextLength: Int? = null,
    val maxOutput: Int? = null,
    val supportsTools: Boolean? = null,
    val supportsVision: Boolean? = null,
    val supportsReasoning: Boolean? = null,
    val inputCostPerMillion: Double? = null,
    val outputCostPerMillion: Double? = null,
)

/** A fully resolved request: endpoint + model + messages + tools + settings. */
data class LlmRequest(
    val baseUrl: String,
    val apiKey: String?,
    val model: String,
    val providerId: String,
    val messages: List<LlmMessage>,
    val tools: List<ToolDefinition> = emptyList(),
    val settings: GenerationSettings = GenerationSettings(),
    val headers: Map<String, String> = emptyMap(),
)

/** Rough token/cost estimate for a completed call. */
@Serializable
data class LlmResult(
    val content: String,
    val thinking: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val usage: LlmUsage = LlmUsage(),
    val model: String,
    val finishReason: String? = null,
    val estimatedCostUsd: Double = 0.0,
)
