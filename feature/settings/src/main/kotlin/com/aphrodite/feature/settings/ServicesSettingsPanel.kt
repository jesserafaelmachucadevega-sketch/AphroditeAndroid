package com.aphrodite.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aphrodite.core.database.entity.ModelEndpointEntity
import com.aphrodite.core.database.entity.ModelEntity
import com.aphrodite.core.database.entity.ProviderKind
import com.aphrodite.designsystem.components.AphroditeBadge
import com.aphrodite.designsystem.components.AphroditeButton
import com.aphrodite.designsystem.components.AphroditeButtonSize
import com.aphrodite.designsystem.components.AphroditeButtonStyle
import com.aphrodite.designsystem.components.AphroditeCard
import com.aphrodite.designsystem.components.AphroditeDivider
import com.aphrodite.designsystem.components.AphroditeEmptyState
import com.aphrodite.designsystem.components.AphroditeIconButton
import com.aphrodite.designsystem.components.AphroditeSectionHeader
import com.aphrodite.designsystem.components.AphroditeSearchField
import com.aphrodite.designsystem.components.AphroditeSpinner
import com.aphrodite.designsystem.components.AphroditeTextField
import com.aphrodite.designsystem.icons.AphroditeIcons
import com.aphrodite.designsystem.theme.AphroditeRadius
import com.aphrodite.designsystem.theme.AphroditeSpacing
import com.aphrodite.designsystem.theme.aphroditeColors

/** State of the providers panel. */
data class ServicesState(
    val endpoints: List<ModelEndpointEntity> = emptyList(),
    val models: List<ModelEntity> = emptyList(),
    val editing: EndpointDraft? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    /** Filter text for the model list. */
    val modelQuery: String = "",
) {
    /** Models after applying the filter, free ones first. */
    val visibleModels: List<ModelEntity>
        get() = if (modelQuery.isBlank()) {
            models
        } else {
            models.filter { it.modelId.contains(modelQuery, ignoreCase = true) }
        }
}

/** The endpoint form's contents while it is open. */
data class EndpointDraft(
    val id: String? = null,
    val name: String = "",
    val kind: ProviderKind = ProviderKind.OpenAiCompatible,
    val baseUrl: String = "",
    val apiKey: String = "",
    val hasStoredKey: Boolean = false,
    val supportsTools: Boolean = true,
    val supportsVision: Boolean = false,
    val supportsReasoning: Boolean = false,
)

/** Callbacks the providers panel uses. */
data class ServicesHandlers(
    val onStartAdd: () -> Unit,
    val onStartEdit: (ModelEndpointEntity) -> Unit,
    val onDraftChange: (EndpointDraft) -> Unit,
    val onCancelEdit: () -> Unit,
    val onSave: () -> Unit,
    val onDelete: (String) -> Unit,
    val onToggleEnabled: (String, Boolean) -> Unit,
    val onRefreshModels: (String) -> Unit,
    val onSelectModels: (String) -> Unit,
    val onToggleFavourite: (String, Boolean) -> Unit,
    val onModelQueryChange: (String) -> Unit,
)

/**
 * Provider configuration.
 *
 * Deliberately keeps the form on the same screen as the list: adding a provider is the first
 * thing anyone has to do, and a separate screen for it would be an extra step for no benefit.
 */
@Composable
fun ServicesSettingsPanel(
    state: ServicesState,
    handlers: ServicesHandlers,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(
                start = AphroditeSpacing.lg,
                end = AphroditeSpacing.lg,
                top = AphroditeSpacing.lg,
                // Keeps the last control clear of the navigation bar.
                bottom = AphroditeSpacing.huge,
            ),
        verticalArrangement = Arrangement.spacedBy(AphroditeSpacing.lg),
    ) {
        if (state.editing != null) {
            EndpointForm(draft = state.editing, handlers = handlers, busy = state.busy)
            AphroditeDivider()
        }

        Column {
            AphroditeSectionHeader(title = "Providers", icon = AphroditeIcons.Server)
            Text(
                text = "Add a provider and fetch its models. Keys are stored encrypted on this " +
                    "device and are never written to the database in the clear.",
                color = aphroditeColors.colorMuted,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
            Box(Modifier.size(AphroditeSpacing.md))

            state.error?.let { Banner(it, isError = true) }
            state.message?.let { Banner(it, isError = false) }
            if (state.error != null || state.message != null) Box(Modifier.size(AphroditeSpacing.sm))

            if (state.endpoints.isEmpty()) {
                AphroditeEmptyState(
                    title = "No providers yet",
                    description = "Add one to start chatting.",
                    icon = AphroditeIcons.Server,
                    action = {
                        AphroditeButton(
                            text = "Add provider",
                            onClick = handlers.onStartAdd,
                            icon = AphroditeIcons.Plus,
                        )
                    },
                )
            } else {
                state.endpoints.forEach { endpoint ->
                    EndpointRow(
                        endpoint = endpoint,
                        selected = state.models.isNotEmpty() && state.models.first().endpointId == endpoint.id,
                        onEdit = { handlers.onStartEdit(endpoint) },
                        onDelete = { handlers.onDelete(endpoint.id) },
                        onToggle = { handlers.onToggleEnabled(endpoint.id, !endpoint.enabled) },
                        onRefresh = { handlers.onRefreshModels(endpoint.id) },
                        onSelect = { handlers.onSelectModels(endpoint.id) },
                    )
                    Box(Modifier.size(AphroditeSpacing.sm))
                }
                AphroditeButton(
                    text = "Add provider",
                    onClick = handlers.onStartAdd,
                    icon = AphroditeIcons.Plus,
                    style = AphroditeButtonStyle.Secondary,
                )

                if (state.models.isNotEmpty()) {
                    Box(Modifier.size(AphroditeSpacing.lg))
                    ModelList(state = state, handlers = handlers)
                }
            }
        }
    }
}

/**
 * The models discovered on the selected endpoint.
 *
 * A catalogue can run to hundreds of entries, so the list is height-bounded and filterable
 * rather than pushing the rest of the panel off the screen.
 */
@Composable
private fun ModelList(state: ServicesState, handlers: ServicesHandlers) {
    val colors = aphroditeColors
    Column {
        AphroditeSectionHeader(
            title = "Models (${state.models.size})",
            icon = AphroditeIcons.Cpu,
        )
        AphroditeSearchField(
            value = state.modelQuery,
            onValueChange = handlers.onModelQueryChange,
            placeholder = "Filter models",
        )
        Box(Modifier.size(AphroditeSpacing.sm))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .clip(RoundedCornerShape(AphroditeRadius.xs))
                .border(1.dp, colors.border, RoundedCornerShape(AphroditeRadius.xs)),
        ) {
            items(state.visibleModels, key = { it.id }) { model ->
                ModelRow(model = model, onToggleFavourite = handlers.onToggleFavourite)
            }
        }
    }
}

@Composable
private fun ModelRow(model: ModelEntity, onToggleFavourite: (String, Boolean) -> Unit) {
    val colors = aphroditeColors
    val isFree = model.modelId.endsWith(":free", ignoreCase = true)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.panel)
            .padding(horizontal = AphroditeSpacing.sm, vertical = AphroditeSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = model.modelId,
            color = colors.fg,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isFree) {
            AphroditeBadge(text = "free", color = colors.colorSuccess)
            Box(Modifier.size(AphroditeSpacing.sm))
        }
        AphroditeIconButton(
            icon = AphroditeIcons.Star,
            contentDescription = if (model.isFavourite) "Unfavourite" else "Favourite",
            onClick = { onToggleFavourite(model.id, !model.isFavourite) },
            size = 24.dp,
            tint = if (model.isFavourite) colors.accentPrimary else colors.colorMuted,
        )
    }
}

@Composable
private fun Banner(message: String, isError: Boolean) {
    val colors = aphroditeColors
    val tint = if (isError) colors.colorError else colors.colorSuccess
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AphroditeRadius.xs))
            .background(tint.copy(alpha = 0.14f))
            .padding(AphroditeSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isError) AphroditeIcons.AlertCircle else AphroditeIcons.CheckCircle,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Box(Modifier.size(AphroditeSpacing.sm))
        Text(
            text = message,
            color = tint,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun EndpointRow(
    endpoint: ModelEndpointEntity,
    selected: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: () -> Unit,
) {
    val colors = aphroditeColors
    AphroditeCard(modifier = Modifier.fillMaxWidth(), selected = selected) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = endpoint.name,
                    color = if (selected) colors.accentPrimary else colors.fg,
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "${endpoint.kind.label} · ${endpoint.baseUrl}",
                    color = colors.colorMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                endpoint.lastError?.let { error ->
                    Text(
                        text = error,
                        color = colors.colorError,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            AphroditeIconButton(
                icon = if (endpoint.enabled) AphroditeIcons.Eye else AphroditeIcons.EyeOff,
                contentDescription = if (endpoint.enabled) "Disable" else "Enable",
                onClick = onToggle,
                size = 30.dp,
                tint = if (endpoint.enabled) colors.colorSuccess else colors.colorMuted,
            )
            AphroditeIconButton(
                icon = AphroditeIcons.Refresh,
                contentDescription = "Fetch models",
                onClick = onRefresh,
                size = 30.dp,
            )
            AphroditeIconButton(
                icon = AphroditeIcons.Edit,
                contentDescription = "Edit",
                onClick = onEdit,
                size = 30.dp,
            )
            AphroditeIconButton(
                icon = AphroditeIcons.Trash,
                contentDescription = "Delete",
                onClick = onDelete,
                size = 30.dp,
                tint = colors.colorError,
            )
        }
    }
}

@Composable
private fun EndpointForm(
    draft: EndpointDraft,
    handlers: ServicesHandlers,
    busy: Boolean,
) {
    val colors = aphroditeColors
    Column(verticalArrangement = Arrangement.spacedBy(AphroditeSpacing.md)) {
        AphroditeSectionHeader(
            title = if (draft.id == null) "New provider" else "Edit provider",
            icon = AphroditeIcons.Server,
        )

        AphroditeTextField(
            value = draft.name,
            onValueChange = { handlers.onDraftChange(draft.copy(name = it)) },
            placeholder = "Name, e.g. OpenAI",
            leadingIcon = AphroditeIcons.Type,
        )

        // Provider family: horizontally scrollable chips rather than a dropdown, which keeps
        // the whole list visible on a tablet.
        Text(
            text = "Provider",
            color = colors.fg,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm),
        ) {
            ProviderKind.entries.forEach { kind ->
                val selected = draft.kind == kind
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AphroditeRadius.xs))
                        .background(if (selected) colors.accentPrimary.copy(alpha = 0.2f) else colors.toggleBg)
                        .border(
                            1.dp,
                            if (selected) colors.accentPrimary else colors.border,
                            RoundedCornerShape(AphroditeRadius.xs),
                        )
                        .clickable(role = Role.RadioButton) {
                            handlers.onDraftChange(
                                draft.copy(kind = kind, baseUrl = if (draft.baseUrl.isBlank()) defaultUrlFor(kind) else draft.baseUrl),
                            )
                        }
                        .padding(horizontal = AphroditeSpacing.md, vertical = AphroditeSpacing.sm),
                ) {
                    Text(
                        text = kind.label,
                        color = if (selected) colors.accentPrimary else colors.fg,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }
        }

        AphroditeTextField(
            value = draft.baseUrl,
            onValueChange = { handlers.onDraftChange(draft.copy(baseUrl = it)) },
            placeholder = "Base URL",
            leadingIcon = AphroditeIcons.Link,
        )

        AphroditeTextField(
            value = draft.apiKey,
            onValueChange = { handlers.onDraftChange(draft.copy(apiKey = it)) },
            placeholder = when {
                draft.hasStoredKey -> "Leave blank to keep the stored key"
                draft.kind.isLocalHost -> "API key (usually not needed)"
                else -> "API key"
            },
            leadingIcon = AphroditeIcons.Key,
        )

        CapabilityToggle("Supports tools", draft.supportsTools) {
            handlers.onDraftChange(draft.copy(supportsTools = it))
        }
        CapabilityToggle("Supports images", draft.supportsVision) {
            handlers.onDraftChange(draft.copy(supportsVision = it))
        }
        CapabilityToggle("Supports reasoning", draft.supportsReasoning) {
            handlers.onDraftChange(draft.copy(supportsReasoning = it))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm)) {
            AphroditeButton(
                text = if (draft.id == null) "Add" else "Save",
                onClick = handlers.onSave,
                loading = busy,
                enabled = draft.name.isNotBlank() && draft.baseUrl.isNotBlank(),
                icon = AphroditeIcons.Check,
            )
            AphroditeButton(
                text = "Cancel",
                onClick = handlers.onCancelEdit,
                style = AphroditeButtonStyle.Secondary,
            )
        }
    }
}

@Composable
private fun CapabilityToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    com.aphrodite.designsystem.components.AphroditeSwitchRow(
        label = label,
        checked = checked,
        onCheckedChange = onChange,
    )
}

/** Sensible starting URLs so the common providers work without anyone looking them up. */
fun defaultUrlFor(kind: ProviderKind): String = when (kind) {
    ProviderKind.OpenAi, ProviderKind.OpenAiCompatible -> "https://api.openai.com/v1"
    ProviderKind.Anthropic -> "https://api.anthropic.com"
    ProviderKind.Gemini -> "https://generativelanguage.googleapis.com/v1beta"
    ProviderKind.Ollama -> "http://localhost:11434/v1"
    ProviderKind.LmStudio -> "http://localhost:1234/v1"
    ProviderKind.LlamaCpp -> "http://localhost:8080/v1"
    ProviderKind.Vllm -> "http://localhost:8000/v1"
    ProviderKind.OpenRouter -> "https://openrouter.ai/api/v1"
    ProviderKind.Groq -> "https://api.groq.com/openai/v1"
    ProviderKind.DeepSeek -> "https://api.deepseek.com/v1"
    ProviderKind.Mistral -> "https://api.mistral.ai/v1"
    ProviderKind.Xai -> "https://api.x.ai/v1"
    ProviderKind.Together -> "https://api.together.xyz/v1"
    ProviderKind.Fireworks -> "https://api.fireworks.ai/inference/v1"
    ProviderKind.Cerebras -> "https://api.cerebras.ai/v1"
    ProviderKind.Featherless -> "https://api.featherless.ai/v1"
    ProviderKind.Nvidia -> "https://integrate.api.nvidia.com/v1"
    ProviderKind.Moonshot -> "https://api.moonshot.ai/v1"
    ProviderKind.Copilot -> "https://api.githubcopilot.com"
    ProviderKind.ChatGptSubscription -> "https://chatgpt.com/backend-api/codex"
    ProviderKind.OnDevice -> "http://127.0.0.1:8081/v1"
}
