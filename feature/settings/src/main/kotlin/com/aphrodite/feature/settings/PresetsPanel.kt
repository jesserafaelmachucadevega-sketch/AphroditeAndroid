package com.aphrodite.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aphrodite.core.database.entity.PresetEntity
import com.aphrodite.designsystem.components.AphroditeButton
import com.aphrodite.designsystem.components.AphroditeButtonSize
import com.aphrodite.designsystem.components.AphroditeButtonStyle
import com.aphrodite.designsystem.components.AphroditeCard
import com.aphrodite.designsystem.components.AphroditeDivider
import com.aphrodite.designsystem.components.AphroditeIconButton
import com.aphrodite.designsystem.components.AphroditeSectionHeader
import com.aphrodite.designsystem.components.AphroditeSlider
import com.aphrodite.designsystem.components.AphroditeTextField
import com.aphrodite.designsystem.icons.AphroditeIcons
import com.aphrodite.designsystem.theme.AphroditeRadius
import com.aphrodite.designsystem.theme.AphroditeSpacing
import com.aphrodite.designsystem.theme.aphroditeColors

/** The preset form's contents while it is open. */
data class PresetDraft(
    val id: String? = null,
    val name: String = "",
    val description: String = "",
    val systemPrompt: String = "",
    val temperature: Float = 0.7f,
    val maxOutputTokens: String = "",
    val isBuiltin: Boolean = false,
)

data class PresetsState(
    val presets: List<PresetEntity> = emptyList(),
    val draft: PresetDraft? = null,
    val notice: String? = null,
)

data class PresetsHandlers(
    val onActivate: (String) -> Unit,
    val onEdit: (PresetEntity) -> Unit,
    val onAdd: () -> Unit,
    val onChange: (PresetDraft) -> Unit,
    val onCancel: () -> Unit,
    val onSave: () -> Unit,
    val onDelete: (String) -> Unit,
)

/**
 * Preset management.
 *
 * A preset is the whole system prompt for a conversation. Exactly one is active at a time, and
 * the seeded "Aphrodite Constitution" starts as it — so the rules that govern tool use are
 * editable here rather than compiled into the app.
 */
@Composable
fun PresetsSettingsPanel(
    state: PresetsState,
    handlers: PresetsHandlers,
    modifier: Modifier = Modifier,
) {
    val colors = aphroditeColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(
                start = AphroditeSpacing.lg,
                end = AphroditeSpacing.lg,
                top = AphroditeSpacing.lg,
                bottom = AphroditeSpacing.huge,
            ),
        verticalArrangement = Arrangement.spacedBy(AphroditeSpacing.lg),
    ) {
        Column {
            AphroditeSectionHeader(title = "Active preset", icon = AphroditeIcons.Skills)
            Text(
                text = "The active preset supplies the entire system prompt, including the " +
                    "operational rules. Exactly one is active at a time.",
                color = colors.colorMuted,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
            Box(Modifier.size(AphroditeSpacing.md))

            state.notice?.let {
                Text(
                    text = it,
                    color = colors.colorSuccess,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
                Box(Modifier.size(AphroditeSpacing.sm))
            }

            state.presets.forEach { preset ->
                PresetCard(
                    preset = preset,
                    onActivate = { handlers.onActivate(preset.id) },
                    onEdit = { handlers.onEdit(preset) },
                    onDelete = { handlers.onDelete(preset.id) },
                )
                Box(Modifier.size(AphroditeSpacing.sm))
            }

            AphroditeButton(
                text = "New preset",
                onClick = handlers.onAdd,
                icon = AphroditeIcons.Plus,
                style = AphroditeButtonStyle.Secondary,
            )
        }

        if (state.draft != null) {
            AphroditeDivider()
            PresetEditor(draft = state.draft, handlers = handlers)
        }
    }
}

@Composable
private fun PresetCard(
    preset: PresetEntity,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = aphroditeColors
    AphroditeCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onActivate),
        selected = preset.enabled,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (preset.enabled) AphroditeIcons.CheckCircle else AphroditeIcons.Minus,
                contentDescription = null,
                tint = if (preset.enabled) colors.accentPrimary else colors.colorMuted,
                modifier = Modifier.size(14.dp),
            )
            Box(Modifier.size(AphroditeSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.name,
                    color = if (preset.enabled) colors.accentPrimary else colors.fg,
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                preset.description?.let {
                    Text(
                        text = it,
                        color = colors.colorMuted,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            AphroditeIconButton(
                icon = AphroditeIcons.Edit,
                contentDescription = "Edit",
                onClick = onEdit,
                size = 26.dp,
            )
            if (!preset.isBuiltin) {
                AphroditeIconButton(
                    icon = AphroditeIcons.Trash,
                    contentDescription = "Delete",
                    onClick = onDelete,
                    size = 26.dp,
                    tint = colors.colorError,
                )
            }
        }
    }
}

@Composable
private fun PresetEditor(draft: PresetDraft, handlers: PresetsHandlers) {
    val colors = aphroditeColors
    Column(verticalArrangement = Arrangement.spacedBy(AphroditeSpacing.md)) {
        AphroditeSectionHeader(
            title = if (draft.id == null) "New preset" else "Edit preset",
            icon = AphroditeIcons.Skills,
        )

        AphroditeTextField(
            value = draft.name,
            onValueChange = { handlers.onChange(draft.copy(name = it)) },
            placeholder = "Name",
            leadingIcon = AphroditeIcons.Type,
        )
        AphroditeTextField(
            value = draft.description,
            onValueChange = { handlers.onChange(draft.copy(description = it)) },
            placeholder = "Description (optional)",
            leadingIcon = AphroditeIcons.Notes,
        )

        Text(
            text = "System prompt",
            color = colors.fg,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
        // A plain scrollable text area rather than the single-line field: a system prompt is
        // hundreds of lines and needs to be readable while editing.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp, max = 420.dp)
                .clip(RoundedCornerShape(AphroditeRadius.sm))
                .background(colors.inputBg)
                .border(1.dp, colors.inputBorder, RoundedCornerShape(AphroditeRadius.sm))
                .padding(AphroditeSpacing.md)
                .verticalScroll(rememberScrollState()),
        ) {
            BasicTextField(
                value = draft.systemPrompt,
                onValueChange = { handlers.onChange(draft.copy(systemPrompt = it)) },
                textStyle = TextStyle(
                    color = colors.fg,
                    fontFamily = FontFamily.Monospace,
                    fontSize = androidx.compose.material3.MaterialTheme.typography.bodySmall.fontSize,
                    lineHeight = androidx.compose.material3.MaterialTheme.typography.bodyMedium.lineHeight,
                ),
                cursorBrush = SolidColor(colors.accentPrimary),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (draft.systemPrompt.isEmpty()) {
                        Text(
                            text = "System prompt…",
                            color = colors.colorMuted.copy(alpha = 0.6f),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        )
                    }
                    inner()
                },
            )
        }

        AphroditeSlider(
            value = draft.temperature,
            onValueChange = { handlers.onChange(draft.copy(temperature = it)) },
            valueRange = 0f..1.5f,
            label = "Temperature",
        )
        AphroditeTextField(
            value = draft.maxOutputTokens,
            onValueChange = { handlers.onChange(draft.copy(maxOutputTokens = it.filter(Char::isDigit))) },
            placeholder = "Max output tokens (blank for provider default)",
            leadingIcon = AphroditeIcons.Cpu,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm)) {
            AphroditeButton(
                text = "Save",
                onClick = handlers.onSave,
                icon = AphroditeIcons.Check,
                enabled = draft.name.isNotBlank() && draft.systemPrompt.isNotBlank(),
            )
            AphroditeButton(
                text = "Cancel",
                onClick = handlers.onCancel,
                style = AphroditeButtonStyle.Secondary,
            )
            Box(Modifier.weight(1f))
            AphroditeButton(
                text = "Reset to constitution",
                onClick = {
                    handlers.onChange(draft.copy(systemPrompt = com.aphrodite.core.llm.SystemConstitution.TEXT))
                },
                style = AphroditeButtonStyle.Ghost,
                size = AphroditeButtonSize.Small,
            )
        }
    }
}
