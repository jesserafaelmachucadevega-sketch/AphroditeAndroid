package com.aphrodite.feature.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aphrodite.core.adaptive.AphroditeTwoPane
import com.aphrodite.core.database.entity.MemoryCategory
import com.aphrodite.core.database.entity.MemoryEntity
import com.aphrodite.designsystem.components.AphroditeButton
import com.aphrodite.designsystem.components.AphroditeButtonStyle
import com.aphrodite.designsystem.components.AphroditeCard
import com.aphrodite.designsystem.components.AphroditeButtonSize
import com.aphrodite.designsystem.components.AphroditeDivider
import com.aphrodite.designsystem.components.AphroditeEmptyState
import com.aphrodite.designsystem.components.AphroditeIconButton
import com.aphrodite.designsystem.components.AphroditeSectionHeader
import com.aphrodite.designsystem.components.AphroditeTextField
import com.aphrodite.designsystem.icons.AphroditeIcons
import com.aphrodite.designsystem.theme.AphroditeSpacing
import com.aphrodite.designsystem.theme.aphroditeColors

/**
 * Durable memories.
 *
 * The screen explains what pinning does, because it is the only thing here that changes the
 * model's behaviour, and a user who does not know that will not understand why an answer changed.
 */
@Composable
fun MemoryScreen(
    state: MemoryUiState,
    onQueryChange: (String) -> Unit,
    onAdd: () -> Unit,
    onCancelAdd: () -> Unit,
    onDraftChange: (String) -> Unit,
    onCategoryChange: (MemoryCategory) -> Unit,
    onSave: () -> Unit,
    onTogglePin: (MemoryEntity) -> Unit,
    onDelete: (String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = aphroditeColors

    AphroditeTwoPane(
        showDetail = state.adding,
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
                        text = "Memory",
                        color = colors.colorSubheader,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    AphroditeIconButton(
                        icon = AphroditeIcons.Plus,
                        contentDescription = "Add memory",
                        onClick = onAdd,
                        size = 28.dp,
                    )
                }
                AphroditeTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = "Filter memories",
                    leadingIcon = AphroditeIcons.Search,
                )
                AphroditeDivider()

                if (state.visible.isEmpty()) {
                    AphroditeEmptyState(
                        title = if (state.memories.isEmpty()) "Nothing remembered" else "No matches",
                        description = if (state.memories.isEmpty()) {
                            "Facts saved here reach the model in every conversation."
                        } else {
                            "No memory contains \"${state.query}\"."
                        },
                        icon = AphroditeIcons.Memory,
                        action = if (state.memories.isEmpty()) {
                            { AphroditeButton(text = "Add memory", onClick = onAdd, icon = AphroditeIcons.Plus) }
                        } else {
                            null
                        },
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.visible, key = { it.id }) { memory ->
                            MemoryRow(
                                memory = memory,
                                onTogglePin = { onTogglePin(memory) },
                                onDelete = { onDelete(memory.id) },
                            )
                        }
                    }
                }
            }
        },
        emptyDetail = {
            AphroditeEmptyState(
                title = "How memory works",
                description = "Pinned memories are sent with every request. Everything else is " +
                    "found on demand when the model searches for it.",
                icon = AphroditeIcons.Memory,
            )
        },
        detail = {
            if (state.adding) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(AphroditeSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(AphroditeSpacing.md),
                ) {
                    AphroditeSectionHeader(title = "New memory", icon = AphroditeIcons.Memory)
                    AphroditeTextField(
                        value = state.draft,
                        onValueChange = onDraftChange,
                        placeholder = "One short sentence",
                        leadingIcon = AphroditeIcons.Type,
                        singleLine = false,
                        minHeight = 120.dp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.xs)) {
                        MemoryCategory.entries.forEach { category ->
                            AphroditeButton(
                                text = category.label,
                                onClick = { onCategoryChange(category) },
                                size = AphroditeButtonSize.Small,
                                style = if (state.draftCategory == category) {
                                    AphroditeButtonStyle.Primary
                                } else {
                                    AphroditeButtonStyle.Secondary
                                },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm)) {
                        AphroditeButton(
                            text = "Save",
                            onClick = onSave,
                            icon = AphroditeIcons.Check,
                            enabled = state.draft.isNotBlank(),
                        )
                        AphroditeButton(
                            text = "Cancel",
                            onClick = onCancelAdd,
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

    state.error?.takeIf { !state.adding }?.let { message ->
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
private fun MemoryRow(
    memory: MemoryEntity,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = aphroditeColors
    Column(modifier = Modifier.fillMaxWidth().padding(AphroditeSpacing.sm)) {
        AphroditeCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = memory.text,
                        color = colors.fg,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = memory.category.label + if (memory.pinned) " · sent every request" else "",
                        color = if (memory.pinned) colors.colorSuccess else colors.colorSubheader,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AphroditeIconButton(
                    icon = AphroditeIcons.Pin,
                    contentDescription = if (memory.pinned) "Unpin" else "Pin",
                    onClick = onTogglePin,
                    size = 26.dp,
                    tint = if (memory.pinned) colors.colorWarning else colors.colorSubheader,
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
