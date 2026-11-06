package com.aphrodite.feature.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aphrodite.core.adaptive.AphroditeTwoPane
import com.aphrodite.core.database.entity.ContactEntity
import com.aphrodite.designsystem.components.AphroditeButton
import com.aphrodite.designsystem.components.AphroditeButtonStyle
import com.aphrodite.designsystem.components.AphroditeCard
import com.aphrodite.designsystem.components.AphroditeDivider
import com.aphrodite.designsystem.components.AphroditeEmptyState
import com.aphrodite.designsystem.components.AphroditeIconButton
import com.aphrodite.designsystem.components.AphroditeSearchField
import com.aphrodite.designsystem.components.AphroditeSectionHeader
import com.aphrodite.designsystem.components.AphroditeTextField
import com.aphrodite.designsystem.icons.AphroditeIcons
import com.aphrodite.designsystem.theme.AphroditeRadius
import com.aphrodite.designsystem.theme.AphroditeSpacing
import com.aphrodite.designsystem.theme.aphroditeColors

/** Contacts: a searchable address book with a simple editor. */
@Composable
fun ContactsScreen(
    state: ContactsUiState,
    onQueryChange: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDraftChange: (ContactDraft) -> Unit,
    onCancelEdit: () -> Unit,
    onSave: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AphroditeTwoPane(
        showDetail = state.draft != null,
        modifier = modifier,
        listWidth = 340.dp,
        list = {
            ContactList(
                state = state,
                onQueryChange = onQueryChange,
                onAdd = onAdd,
                onEdit = onEdit,
                onDelete = onDelete,
            )
        },
        emptyDetail = {
            AphroditeEmptyState(
                title = "Pick a contact",
                description = "Or add one.",
                icon = AphroditeIcons.Users,
            )
        },
        detail = {
            state.draft?.let { draft ->
                ContactEditor(
                    draft = draft,
                    error = state.error,
                    onChange = onDraftChange,
                    onCancel = onCancelEdit,
                    onSave = onSave,
                    onDismissError = onDismissError,
                )
            }
        },
    )
}

@Composable
private fun ContactList(
    state: ContactsUiState,
    onQueryChange: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
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
                text = "Contacts",
                color = colors.colorSubheader,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            AphroditeIconButton(
                icon = AphroditeIcons.Plus,
                contentDescription = "New contact",
                onClick = onAdd,
                size = 28.dp,
            )
        }
        Box(Modifier.padding(horizontal = AphroditeSpacing.sm)) {
            AphroditeSearchField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = "Search contacts",
            )
        }
        Box(Modifier.size(AphroditeSpacing.sm))
        AphroditeDivider()

        if (state.visible.isEmpty()) {
            AphroditeEmptyState(
                title = if (state.query.isBlank()) "No contacts yet" else "No matches",
                description = if (state.query.isBlank()) "Tap + to add one." else null,
                icon = AphroditeIcons.Users,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(AphroditeSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AphroditeSpacing.xs),
            ) {
                items(state.visible, key = { it.id }) { contact ->
                    ContactRow(
                        contact = contact,
                        onClick = { onEdit(contact.id) },
                        onDelete = { onDelete(contact.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: ContactEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val colors = aphroditeColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AphroditeRadius.xs))
            .padding(vertical = AphroditeSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(AphroditeRadius.pill))
                .background(colors.accentPrimary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = contact.displayName.take(1).uppercase(),
                color = colors.accentPrimary,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            )
        }
        Box(Modifier.size(AphroditeSpacing.sm))
        Column(
            modifier = Modifier
                .weight(1f)
                .androidClickable(onClick),
        ) {
            Text(
                text = contact.displayName,
                color = colors.fg,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val secondary = contact.email ?: contact.organization ?: contact.phone
            if (secondary != null) {
                Text(
                    text = secondary,
                    color = colors.colorMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AphroditeIconButton(
            icon = AphroditeIcons.Trash,
            contentDescription = "Delete",
            onClick = onDelete,
            size = 24.dp,
            tint = colors.colorMuted,
        )
    }
}

@Composable
private fun ContactEditor(
    draft: ContactDraft,
    error: String?,
    onChange: (ContactDraft) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onDismissError: () -> Unit,
) {
    val colors = aphroditeColors
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
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
                onClick = onCancel,
                size = 30.dp,
            )
            Box(Modifier.weight(1f))
            AphroditeButton(
                text = "Save",
                onClick = onSave,
                icon = AphroditeIcons.Check,
                size = com.aphrodite.designsystem.components.AphroditeButtonSize.Small,
            )
        }
        AphroditeDivider()

        Column(
            modifier = Modifier.fillMaxWidth().padding(AphroditeSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AphroditeSpacing.md),
        ) {
            if (error != null) {
                Text(
                    text = error,
                    color = colors.colorError,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    modifier = Modifier.androidClickable(onDismissError),
                )
            }
            AphroditeSectionHeader(
                title = if (draft.isNew) "New contact" else "Edit contact",
                icon = AphroditeIcons.User,
            )
            AphroditeTextField(
                value = draft.displayName,
                onValueChange = { onChange(draft.copy(displayName = it)) },
                placeholder = "Name",
                leadingIcon = AphroditeIcons.User,
            )
            AphroditeTextField(
                value = draft.email,
                onValueChange = { onChange(draft.copy(email = it)) },
                placeholder = "Email",
                leadingIcon = AphroditeIcons.Mail,
            )
            AphroditeTextField(
                value = draft.phone,
                onValueChange = { onChange(draft.copy(phone = it)) },
                placeholder = "Phone",
                leadingIcon = AphroditeIcons.Smartphone,
            )
            AphroditeTextField(
                value = draft.organization,
                onValueChange = { onChange(draft.copy(organization = it)) },
                placeholder = "Organisation",
                leadingIcon = AphroditeIcons.Home,
            )
            AphroditeTextField(
                value = draft.title,
                onValueChange = { onChange(draft.copy(title = it)) },
                placeholder = "Job title",
                leadingIcon = AphroditeIcons.Tag,
            )
            AphroditeTextField(
                value = draft.notes,
                onValueChange = { onChange(draft.copy(notes = it)) },
                placeholder = "Notes",
                leadingIcon = AphroditeIcons.Notes,
            )
            AphroditeButton(
                text = "Cancel",
                onClick = onCancel,
                style = AphroditeButtonStyle.Secondary,
            )
            Box(Modifier.size(AphroditeSpacing.huge))
        }
    }
}

private fun Modifier.androidClickable(onClick: () -> Unit): Modifier = clickable(onClick = onClick)
