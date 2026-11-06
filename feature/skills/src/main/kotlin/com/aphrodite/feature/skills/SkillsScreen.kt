package com.aphrodite.feature.skills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aphrodite.core.adaptive.AphroditeTwoPane
import com.aphrodite.core.database.entity.SkillEntity
import com.aphrodite.designsystem.components.AphroditeButton
import com.aphrodite.designsystem.components.AphroditeButtonStyle
import com.aphrodite.designsystem.components.AphroditeCard
import com.aphrodite.designsystem.components.AphroditeDivider
import com.aphrodite.designsystem.components.AphroditeEmptyState
import com.aphrodite.designsystem.components.AphroditeIconButton
import com.aphrodite.designsystem.components.AphroditeSectionHeader
import com.aphrodite.designsystem.components.AphroditeTextField
import com.aphrodite.designsystem.icons.AphroditeIcons
import com.aphrodite.designsystem.theme.AphroditeSpacing
import com.aphrodite.designsystem.theme.aphroditeColors

/**
 * Saved instruction sets.
 *
 * The list is explicit that only the name and description reach the model, because someone who
 * expects a long skill to be permanently in the prompt will be puzzled when the model ignores it.
 */
@Composable
fun SkillsScreen(
    state: SkillsUiState,
    onAdd: () -> Unit,
    onEdit: (SkillEntity) -> Unit,
    onDraftChange: (SkillDraft) -> Unit,
    onCancelEdit: () -> Unit,
    onSave: () -> Unit,
    onToggleEnabled: (SkillEntity) -> Unit,
    onDelete: (String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = aphroditeColors

    AphroditeTwoPane(
        showDetail = state.draft != null,
        modifier = modifier,
        listWidth = 340.dp,
        list = {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AphroditeSpacing.sm, vertical = AphroditeSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Skills",
                        color = colors.colorSubheader,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    AphroditeIconButton(
                        icon = AphroditeIcons.Plus,
                        contentDescription = "Add skill",
                        onClick = onAdd,
                        size = 28.dp,
                    )
                }
                AphroditeDivider()

                if (state.skills.isEmpty()) {
                    AphroditeEmptyState(
                        title = "No skills yet",
                        description = "Skills are instructions the model loads when a task matches. " +
                            "Only their name and description sit in the prompt.",
                        icon = AphroditeIcons.Sparkles,
                        action = {
                            AphroditeButton(text = "Add skill", onClick = onAdd, icon = AphroditeIcons.Plus)
                        },
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.skills, key = { it.id }) { skill ->
                            SkillRow(
                                skill = skill,
                                onEdit = { onEdit(skill) },
                                onToggleEnabled = { onToggleEnabled(skill) },
                                onDelete = { onDelete(skill.id) },
                            )
                        }
                    }
                }
            }
        },
        emptyDetail = {
            AphroditeEmptyState(
                title = "Instructions on demand",
                description = "The model sees each skill's name and description, and loads the " +
                    "full instructions only when one applies.",
                icon = AphroditeIcons.Sparkles,
            )
        },
        detail = {
            state.draft?.let { draft ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(AphroditeSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(AphroditeSpacing.md),
                ) {
                    AphroditeSectionHeader(
                        title = if (draft.id == null) "New skill" else "Edit skill",
                        icon = AphroditeIcons.Sparkles,
                    )
                    AphroditeTextField(
                        value = draft.name,
                        onValueChange = { onDraftChange(draft.copy(name = it)) },
                        placeholder = "Name",
                        leadingIcon = AphroditeIcons.Type,
                    )
                    AphroditeTextField(
                        value = draft.description,
                        onValueChange = { onDraftChange(draft.copy(description = it)) },
                        placeholder = "When should this be used? (shown to the model)",
                        leadingIcon = AphroditeIcons.Search,
                    )
                    AphroditeTextField(
                        value = draft.body,
                        onValueChange = { onDraftChange(draft.copy(body = it)) },
                        placeholder = "The instructions themselves",
                        leadingIcon = AphroditeIcons.Notes,
                        singleLine = false,
                        minHeight = 280.dp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm)) {
                        AphroditeButton(
                            text = "Save",
                            onClick = onSave,
                            icon = AphroditeIcons.Check,
                            enabled = draft.name.isNotBlank() && draft.body.isNotBlank(),
                        )
                        AphroditeButton(
                            text = "Cancel",
                            onClick = onCancelEdit,
                            style = AphroditeButtonStyle.Secondary,
                        )
                    }
                    state.error?.let {
                        Text(text = it, color = colors.colorError, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
    )

    state.error?.takeIf { state.draft == null }?.let { message ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AphroditeSpacing.md, vertical = AphroditeSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                color = colors.colorError,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            AphroditeIconButton(
                icon = AphroditeIcons.Close,
                contentDescription = "Dismiss",
                onClick = onDismissError,
                size = 22.dp,
            )
        }
    }
}

@Composable
private fun SkillRow(
    skill: SkillEntity,
    onEdit: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = aphroditeColors
    Column(modifier = Modifier.fillMaxWidth().padding(AphroditeSpacing.sm)) {
        AphroditeCard(modifier = Modifier.fillMaxWidth(), onClick = onEdit) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = skill.name,
                        color = colors.fg,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = skill.description,
                        color = colors.colorSubheader,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (skill.enabled) "Offered to the model" else "Disabled",
                        color = if (skill.enabled) colors.colorSuccess else colors.colorWarning,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                AphroditeIconButton(
                    icon = if (skill.enabled) AphroditeIcons.CheckCircle else AphroditeIcons.Close,
                    contentDescription = if (skill.enabled) "Disable" else "Enable",
                    onClick = onToggleEnabled,
                    size = 26.dp,
                    tint = if (skill.enabled) colors.colorSuccess else colors.colorSubheader,
                )
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
