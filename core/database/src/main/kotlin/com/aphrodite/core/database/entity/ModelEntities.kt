package com.aphrodite.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * The provider families the app can talk to.
 *
 * Most are OpenAI-compatible and differ only in their base URL and a few payload quirks;
 * [Anthropic] and [Gemini] have their own request and response shapes, and [OnDevice] is served
 * by the bundled inference engine rather than over the network.
 */
enum class ProviderKind(val id: String, val label: String) {
    OpenAiCompatible("openai", "OpenAI-compatible"),
    OpenAi("openai-official", "OpenAI"),
    Anthropic("anthropic", "Anthropic"),
    Gemini("gemini", "Google Gemini"),
    Ollama("ollama", "Ollama"),
    LmStudio("lmstudio", "LM Studio"),
    LlamaCpp("llamacpp", "llama.cpp"),
    Vllm("vllm", "vLLM"),
    OpenRouter("openrouter", "OpenRouter"),
    Groq("groq", "Groq"),
    DeepSeek("deepseek", "DeepSeek"),
    Mistral("mistral", "Mistral"),
    Xai("xai", "xAI"),
    Together("together", "Together"),
    Fireworks("fireworks", "Fireworks"),
    Cerebras("cerebras", "Cerebras"),
    Featherless("featherless", "Featherless"),
    Nvidia("nvidia", "NVIDIA"),
    Moonshot("moonshot", "Moonshot / Kimi"),
    Copilot("copilot", "GitHub Copilot"),
    ChatGptSubscription("chatgpt-subscription", "ChatGPT Subscription"),
    OnDevice("on-device", "On this device"),
    ;

    /** True when the provider speaks the OpenAI chat-completions shape unchanged. */
    val isOpenAiShaped: Boolean
        get() = this in setOf(
            OpenAiCompatible, OpenAi, Ollama, LmStudio, LlamaCpp, Vllm, OpenRouter, Groq,
            DeepSeek, Mistral, Xai, Together, Fireworks, Cerebras, Featherless, Nvidia,
            Moonshot, Copilot, ChatGptSubscription,
        )

    /** True when the endpoint lives on the local network rather than the public internet. */
    val isLocalHost: Boolean
        get() = this in setOf(Ollama, LmStudio, LlamaCpp, Vllm, OnDevice)

    companion object {
        fun fromId(id: String?): ProviderKind =
            entries.firstOrNull { it.id == id } ?: OpenAiCompatible
    }
}

/**
 * A configured place to send model requests.
 *
 * [apiKeyRef] is a key into the encrypted secret store, never the key itself, so exporting the
 * database cannot leak credentials.
 */
@Entity(
    tableName = "model_endpoints",
    indices = [Index("kind"), Index("enabled"), Index("sort_order")],
)
@Serializable
data class ModelEndpointEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: ProviderKind,
    @ColumnInfo(name = "base_url") val baseUrl: String,
    @ColumnInfo(name = "api_key_ref") val apiKeyRef: String? = null,
    @ColumnInfo(name = "supports_tools") val supportsTools: Boolean = true,
    @ColumnInfo(name = "supports_vision") val supportsVision: Boolean = false,
    @ColumnInfo(name = "supports_reasoning") val supportsReasoning: Boolean = false,
    @ColumnInfo(name = "supports_streaming") val supportsStreaming: Boolean = true,
    @ColumnInfo(name = "max_context") val maxContext: Int? = null,
    @ColumnInfo(name = "enabled") val enabled: Boolean = true,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "last_probed_at") val lastProbedAt: Long? = null,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** A model discovered on, or added manually to, an endpoint. */
@Entity(
    tableName = "models",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = ModelEndpointEntity::class,
            parentColumns = ["id"],
            childColumns = ["endpoint_id"],
            onDelete = androidx.room.ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("endpoint_id"), Index("is_favourite"), Index(value = ["endpoint_id", "model_id"], unique = true)],
)
@Serializable
data class ModelEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "endpoint_id") val endpointId: String,
    @ColumnInfo(name = "model_id") val modelId: String,
    @ColumnInfo(name = "display_name") val displayName: String? = null,
    @ColumnInfo(name = "context_length") val contextLength: Int? = null,
    @ColumnInfo(name = "max_output") val maxOutput: Int? = null,
    val vision: Boolean = false,
    val tools: Boolean = false,
    val reasoning: Boolean = false,
    @ColumnInfo(name = "input_cost_per_million") val inputCostPerMillion: Double? = null,
    @ColumnInfo(name = "output_cost_per_million") val outputCostPerMillion: Double? = null,
    @ColumnInfo(name = "is_favourite") val isFavourite: Boolean = false,
    @ColumnInfo(name = "is_hidden") val isHidden: Boolean = false,
    @ColumnInfo(name = "discovered_at") val discoveredAt: Long,
)

/**
 * A persona preset.
 *
 * Carries the persona text, the optional memory-continuity notes and schema, and the sampling
 * defaults applied when it is active.
 */
@Entity(
    tableName = "presets",
    indices = [Index("is_builtin"), Index("enabled")],
)
@Serializable
data class PresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String? = null,
    @ColumnInfo(name = "system_prompt") val systemPrompt: String,
    @ColumnInfo(name = "persona_notes") val personaNotes: String? = null,
    @ColumnInfo(name = "memory_schema") val memorySchema: String? = null,
    val temperature: Double? = null,
    @ColumnInfo(name = "top_p") val topP: Double? = null,
    @ColumnInfo(name = "max_output_tokens") val maxOutputTokens: Int? = null,
    @ColumnInfo(name = "thinking_mode") val thinkingMode: String? = null,
    @ColumnInfo(name = "reasoning_effort") val reasoningEffort: String? = null,
    @ColumnInfo(name = "show_persona_name") val showPersonaName: Boolean = false,
    @ColumnInfo(name = "is_builtin") val isBuiltin: Boolean = false,
    val enabled: Boolean = true,
    val icon: String? = null,
    val color: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** A reusable prompt snippet, expanded from the composer with a slash command. */
@Entity(
    tableName = "preset_templates",
    indices = [Index("group_id"), Index("shortcut")],
)
@Serializable
data class PresetTemplateEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_id") val groupId: String? = null,
    val name: String,
    val shortcut: String? = null,
    val body: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** A folder for grouping preset templates. */
@Entity(tableName = "preset_groups")
@Serializable
data class PresetGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
)
