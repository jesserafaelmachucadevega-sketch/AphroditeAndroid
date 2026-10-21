package com.aphrodite.feature.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aphrodite.core.adaptive.AphroditeTwoPane
import com.aphrodite.core.database.entity.NoteEntity
import com.aphrodite.core.database.entity.NoteKind
import com.aphrodite.designsystem.components.AphroditeButton
import com.aphrodite.designsystem.components.AphroditeButtonStyle
import com.aphrodite.designsystem.components.AphroditeCheckbox
import com.aphrodite.designsystem.components.AphroditeDivider
import com.aphrodite.designsystem.components.AphroditeEmptyState
import com.aphrodite.designsystem.components.AphroditeIconButton
import com.aphrodite.designsystem.components.AphroditeSearchField
import com.aphrodite.designsystem.icons.AphroditeIcons
import com.aphrodite.designsystem.theme.AphroditeRadius
import com.aphrodite.designsystem.theme.AphroditeSpacing
import com.aphrodite.designsystem.theme.aphroditeColors

/** The note accent palette, matching the swatches the upstream app offers. */
private val NoteColors = listOf(
    "#E06C75", "#D19A66", "#E5C07B", "#98C379", "#56B6C2", "#61AFEF", "#C678DD", "#ABB2BF",
)

/**
 * Notes.
 *
 * Cards beside the editor on wide layouts, one pane at a time on phones — the same
 * list/detail treatment as the rest of the app.
 */
@Composable
fun NotesScreen(
    state: NotesUiState,
    onQueryChange: (String) -> Unit,
    onToggleArchived: () -> Unit,
    onNewNote: () -> Unit,
    onNewChecklist: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onTogglePin: (String, Boolean) -> Unit,
    onSetArchived: (String, Boolean) -> Unit,
    onCloseEditor: () -> Unit,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onColorChange: (String?) -> Unit,
    onTogglePinnedDraft: () -> Unit,
    onToggleArchivedDraft: () -> Unit,
    onAddItem: () -> Unit,
    onUpdateItem: (String, String) -> Unit,
    onToggleItem: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AphroditeTwoPane(
        showDetail = state.draft != null,
        modifier = modifier,
        listWidth = 320.dp,
        list = {
            NoteList(
                state = state,
                onQueryChange = onQueryChange,
                onToggleArchived = onToggleArchived,
                onNewNote = onNewNote,
                onNewChecklist = onNewChecklist,
                onEdit = onEdit,
                onDelete = onDelete,
                onTogglePin = onTogglePin,
                onSetArchived = onSetArchived,
            )
        },
        emptyDetail = {
            AphroditeEmptyState(
                title = "Pick a note",
                description = "Or start a new one.",
                icon = AphroditeIcons.Notes,
            )
        },
        detail = {
            state.draft?.let { draft ->
                NoteEditor(
                    draft = draft,
                    onClose = onCloseEditor,
                    onTitleChange = onTitleChange,
                    onBodyChange = onBodyChange,
                    onColorChange = onColorChange,
                    onTogglePinned = onTogglePinnedDraft,
                    onToggleArchived = onToggleArchivedDraft,
                    onAddItem = onAddItem,
                    onUpdateItem = onUpdateItem,
                    onToggleItem = onToggleItem,
                    onRemoveItem = onRemoveItem,
                    onSave = onSave,
                )
            }
        },
    )
}

@Composable
private fun NoteList(
    state: NotesUiState,
    onQueryChange: (String) -> Unit,
    onToggleArchived: () -> Unit,
    onNewNote: () -> Unit,
    onNewChecklist: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onTogglePin: (String, Boolean) -> Unit,
    onSetArchived: (String, Boolean) -> Unit,
) {
    val colors = aphroditeColors
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AphroditeSpacing.sm, vertical = AphroditeSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (state.showArchived) "Archived" else "Notes",
                color = colors.colorSubheader,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            AphroditeIconButton(
                icon = if (state.showArchived) AphroditeIcons.Notes else AphroditeIcons.Archive,
                contentDescription = if (state.showArchived) "Show notes" else "Show archived",
                onClick = onToggleArchived,
                size = 28.dp,
            )
            AphroditeIconButton(
                icon = AphroditeIcons.Tasks,
                contentDescription = "New checklist",
                onClick = onNewChecklist,
                size = 28.dp,
            )
            AphroditeIconButton(
                icon = AphroditeIcons.Plus,
                contentDescription = "New note",
                onClick = onNewNote,
                size = 28.dp,
            )
        }
        Box(Modifier.padding(horizontal = AphroditeSpacing.sm)) {
            AphroditeSearchField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = "Search notes",
            )
        }
        Box(Modifier.size(AphroditeSpacing.sm))
        AphroditeDivider()

        if (state.visible.isEmpty()) {
            AphroditeEmptyState(
                title = if (state.query.isBlank()) "No notes yet" else "No matches",
                description = if (state.query.isBlank()) "Tap + to write one." else null,
                icon = AphroditeIcons.Notes,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(AphroditeSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm),
            ) {
                items(state.visible, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        onClick = { onEdit(note.id) },
                        onDelete = { onDelete(note.id) },
                        onTogglePin = { onTogglePin(note.id, !note.pinned) },
                        onArchive = { onSetArchived(note.id, !note.archived) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteCard(
    note: NoteEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    onArchive: () -> Unit,
) {
    val colors = aphroditeColors
    val accent = note.color?.let { parseColor(it) } ?: colors.border

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AphroditeRadius.sm))
            .background(colors.panel)
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(AphroditeRadius.sm))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(AphroditeSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = note.title.ifBlank { "Untitled" },
                color = colors.fg,
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (note.pinned) {
                Icon(
                    imageVector = AphroditeIcons.Pin,
                    contentDescription = "Pinned",
                    tint = colors.accentPrimary,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
        if (note.body.isNotBlank()) {
            Box(Modifier.size(AphroditeSpacing.xs))
            Text(
                text = note.body,
                color = colors.colorMuted,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (note.kind == NoteKind.Checklist) {
            Box(Modifier.size(AphroditeSpacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AphroditeIcons.Tasks,
                    contentDescription = null,
                    tint = colors.colorMuted,
                    modifier = Modifier.size(10.dp),
                )
                Box(Modifier.size(AphroditeSpacing.xs))
                Text(
                    text = "Checklist",
                    color = colors.colorMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                )
            }
        }
        Box(Modifier.size(AphroditeSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.xs)) {
            AphroditeIconButton(
                icon = AphroditeIcons.Pin,
                contentDescription = if (note.pinned) "Unpin" else "Pin",
                onClick = onTogglePin,
                size = 24.dp,
                tint = if (note.pinned) colors.accentPrimary else colors.colorMuted,
            )
            AphroditeIconButton(
                icon = AphroditeIcons.Archive,
                contentDescription = if (note.archived) "Restore" else "Archive",
                onClick = onArchive,
                size = 24.dp,
                tint = colors.colorMuted,
            )
            Box(Modifier.weight(1f))
            AphroditeIconButton(
                icon = AphroditeIcons.Trash,
                contentDescription = "Delete",
                onClick = onDelete,
                size = 24.dp,
                tint = colors.colorError,
            )
        }
    }
}

@Composable
private fun NoteEditor(
    draft: NoteDraft,
    onClose: () -> Unit,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onColorChange: (String?) -> Unit,
    onTogglePinned: () -> Unit,
    onToggleArchived: () -> Unit,
    onAddItem: () -> Unit,
    onUpdateItem: (String, String) -> Unit,
    onToggleItem: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onSave: () -> Unit,
) {
    val colors = aphroditeColors
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.panel)
                .padding(horizontal = AphroditeSpacing.sm, vertical = AphroditeSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AphroditeIconButton(
                icon = AphroditeIcons.Close,
                contentDescription = "Close",
                onClick = onClose,
                size = 30.dp,
            )
            Box(Modifier.weight(1f))
            AphroditeIconButton(
                icon = AphroditeIcons.Pin,
                contentDescription = "Pin",
                onClick = onTogglePinned,
                size = 30.dp,
                tint = if (draft.pinned) colors.accentPrimary else colors.colorMuted,
            )
            AphroditeIconButton(
                icon = AphroditeIcons.Archive,
                contentDescription = "Archive",
                onClick = onToggleArchived,
                size = 30.dp,
                tint = if (draft.archived) colors.accentPrimary else colors.colorMuted,
            )
            AphroditeButton(
                text = "Save",
                onClick = onSave,
                icon = AphroditeIcons.Check,
                style = AphroditeButtonStyle.Primary,
                size = com.aphrodite.designsystem.components.AphroditeButtonSize.Small,
            )
        }
        AphroditeDivider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AphroditeSpacing.lg),
        ) {
            BasicTextField(
                value = draft.title,
                onValueChange = onTitleChange,
                textStyle = TextStyle(
                    color = colors.fg,
                    fontSize = androidx.compose.material3.MaterialTheme.typography.headlineSmall.fontSize,
                    fontWeight = FontWeight.SemiBold,
                ),
                cursorBrush = SolidColor(colors.accentPrimary),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (draft.title.isEmpty()) {
                        Text(
                            text = "Title",
                            color = colors.colorMuted.copy(alpha = 0.6f),
                            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                        )
                    }
                    inner()
                },
            )
            Box(Modifier.size(AphroditeSpacing.md))

            Row(horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm)) {
                NoteColors.forEach { hex ->
                    val selected = draft.color == hex
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(AphroditeRadius.pill))
                            .background(parseColor(hex))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) colors.fg else colors.border,
                                shape = RoundedCornerShape(AphroditeRadius.pill),
                            )
                            .clickable(role = Role.Button) {
                                onColorChange(if (selected) null else hex)
                            },
                    )
                }
            }

            Box(Modifier.size(AphroditeSpacing.md))
            AphroditeDivider()
            Box(Modifier.size(AphroditeSpacing.md))

            if (draft.items.isEmpty()) {
                BasicTextField(
                    value = draft.body,
                    onValueChange = onBodyChange,
                    textStyle = TextStyle(
                        color = colors.fg,
                        fontSize = androidx.compose.material3.MaterialTheme.typography.bodyMedium.fontSize,
                        lineHeight = androidx.compose.material3.MaterialTheme.typography.bodyMedium.lineHeight,
                    ),
                    cursorBrush = SolidColor(colors.accentPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp),
                    decorationBox = { inner ->
                        if (draft.body.isEmpty()) {
                            Text(
                                text = "Write something…",
                                color = colors.colorMuted.copy(alpha = 0.6f),
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            )
                        }
                        inner()
                    },
                )
                Box(Modifier.size(AphroditeSpacing.md))
                AphroditeButton(
                    text = "Add checklist",
                    onClick = onAddItem,
                    icon = AphroditeIcons.Tasks,
                    style = AphroditeButtonStyle.Ghost,
                    size = com.aphrodite.designsystem.components.AphroditeButtonSize.Small,
                )
            } else {
                draft.items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = AphroditeSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AphroditeCheckbox(
                            checked = item.checked,
                            onCheckedChange = { onToggleItem(item.id) },
                        )
                        Box(Modifier.size(AphroditeSpacing.sm))
                        BasicTextField(
                            value = item.text,
                            onValueChange = { onUpdateItem(item.id, it) },
                            textStyle = TextStyle(
                                color = if (item.checked) colors.colorMuted else colors.fg,
                                fontSize = androidx.compose.material3.MaterialTheme.typography.bodyMedium.fontSize,
                                textDecoration = if (item.checked) {
                                    androidx.compose.ui.text.style.TextDecoration.LineThrough
                                } else {
                                    null
                                },
                            ),
                            cursorBrush = SolidColor(colors.accentPrimary),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (item.text.isEmpty()) {
                                    Text(
                                        text = "Item",
                                        color = colors.colorMuted.copy(alpha = 0.6f),
                                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                inner()
                            },
                        )
                        AphroditeIconButton(
                            icon = AphroditeIcons.Close,
                            contentDescription = "Remove item",
                            onClick = { onRemoveItem(item.id) },
                            size = 24.dp,
                            tint = colors.colorMuted,
                        )
                    }
                }
                Box(Modifier.size(AphroditeSpacing.sm))
                AphroditeButton(
                    text = "Add item",
                    onClick = onAddItem,
                    icon = AphroditeIcons.Plus,
                    style = AphroditeButtonStyle.Ghost,
                    size = com.aphrodite.designsystem.components.AphroditeButtonSize.Small,
                )
            }
            Box(Modifier.size(AphroditeSpacing.huge))
        }
    }
}

/** Parses a `#RRGGBB` note colour, falling back to the muted border tone. */
private fun parseColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(Color(0xFF355A66))
