package com.aphrodite.feature.email

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aphrodite.core.database.entity.EmailAccountEntity
import com.aphrodite.core.database.entity.EmailHeader
import com.aphrodite.designsystem.components.AphroditeButton
import com.aphrodite.designsystem.components.AphroditeButtonSize
import com.aphrodite.designsystem.components.AphroditeButtonStyle
import com.aphrodite.designsystem.components.AphroditeCard
import com.aphrodite.designsystem.components.AphroditeDivider
import com.aphrodite.designsystem.components.AphroditeEmptyState
import com.aphrodite.designsystem.components.AphroditeIconButton
import com.aphrodite.designsystem.components.AphroditeSectionHeader
import com.aphrodite.designsystem.components.AphroditeSpinner
import com.aphrodite.designsystem.components.AphroditeTextField
import com.aphrodite.designsystem.icons.AphroditeIcons
import com.aphrodite.designsystem.theme.AphroditeRadius
import com.aphrodite.designsystem.theme.AphroditeSpacing
import com.aphrodite.designsystem.theme.aphroditeColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DATE_FORMAT = SimpleDateFormat("d MMM HH:mm", Locale.getDefault())

/**
 * Email: accounts and the IMAP read path.
 *
 * Sending is not built, so there is no compose button — a button that cannot send would be worse
 * than its absence.
 */
@Composable
fun EmailScreen(
    state: EmailUiState,
    onAddAccount: () -> Unit,
    onEditAccount: (EmailAccountEntity) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onSelectAccount: (String) -> Unit,
    onSelectFolder: (String) -> Unit,
    onDraftChange: (AccountDraft) -> Unit,
    onCancelDraft: () -> Unit,
    onTest: () -> Unit,
    onSaveAccount: () -> Unit,
    onOpenMessage: (EmailHeader) -> Unit,
    onCloseMessage: () -> Unit,
    onCompose: () -> Unit,
    onReply: (com.aphrodite.core.database.entity.EmailBody) -> Unit,
    onComposeChange: (ComposeDraft) -> Unit,
    onCancelCompose: () -> Unit,
    onSend: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = aphroditeColors
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.panel)
                .padding(horizontal = AphroditeSpacing.sm, vertical = AphroditeSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.openMessage != null) {
                AphroditeIconButton(
                    icon = AphroditeIcons.ArrowLeft,
                    contentDescription = "Back",
                    onClick = onCloseMessage,
                    size = 30.dp,
                )
            }
            Text(
                text = state.openMessage?.header?.subject
                    ?: state.activeAccount?.address
                    ?: "Email",
                color = colors.fg,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (state.loading) {
                AphroditeSpinner(size = 14.dp)
                Box(Modifier.size(AphroditeSpacing.sm))
            }
            if (state.openMessage == null) {
                AphroditeIconButton(
                    icon = AphroditeIcons.Edit,
                    contentDescription = "Compose",
                    onClick = onCompose,
                    size = 28.dp,
                    enabled = state.activeAccount != null,
                )
                AphroditeIconButton(
                    icon = AphroditeIcons.Refresh,
                    contentDescription = "Refresh",
                    onClick = onRefresh,
                    size = 28.dp,
                )
                AphroditeIconButton(
                    icon = AphroditeIcons.Plus,
                    contentDescription = "Add account",
                    onClick = onAddAccount,
                    size = 28.dp,
                )
            }
        }
        AphroditeDivider()

        if (state.error != null || state.notice != null) {
            val tint = if (state.error != null) colors.colorError else colors.colorSuccess
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tint.copy(alpha = 0.14f))
                    .padding(horizontal = AphroditeSpacing.md, vertical = AphroditeSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.error ?: state.notice.orEmpty(),
                    color = tint,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                AphroditeIconButton(
                    icon = AphroditeIcons.Close,
                    contentDescription = "Dismiss",
                    onClick = onDismiss,
                    size = 22.dp,
                    tint = tint,
                )
            }
        }

        when {
            state.compose != null -> ComposeForm(
                draft = state.compose,
                onChange = onComposeChange,
                onCancel = onCancelCompose,
                onSend = onSend,
                loading = state.loading,
            )

            state.draft != null -> AccountForm(
                draft = state.draft,
                onChange = onDraftChange,
                onCancel = onCancelDraft,
                onTest = onTest,
                onSave = onSaveAccount,
                loading = state.loading,
            )

            state.openMessage != null -> Reader(
                state = state,
                onClose = onCloseMessage,
                onReply = onReply,
            )

            state.accounts.isEmpty() -> AphroditeEmptyState(
                title = "No mail accounts",
                description = "Add an IMAP account to read your inbox.",
                icon = AphroditeIcons.Mail,
                action = {
                    AphroditeButton(text = "Add account", onClick = onAddAccount, icon = AphroditeIcons.Plus)
                },
            )

            else -> Inbox(
                state = state,
                onSelectFolder = onSelectFolder,
                onOpen = onOpenMessage,
                onEditAccount = onEditAccount,
                onDeleteAccount = onDeleteAccount,
            )
        }
    }
}

@Composable
private fun Inbox(
    state: EmailUiState,
    onSelectFolder: (String) -> Unit,
    onOpen: (EmailHeader) -> Unit,
    onEditAccount: (EmailAccountEntity) -> Unit,
    onDeleteAccount: (String) -> Unit,
) {
    val colors = aphroditeColors
    val folders = if (state.folders.isEmpty()) listOf("INBOX") else state.folders

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = AphroditeSpacing.sm, vertical = AphroditeSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm),
        ) {
            folders.forEach { folder ->
                val selected = folder == state.folder
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AphroditeRadius.xs))
                        .background(if (selected) colors.accentPrimary.copy(alpha = 0.2f) else colors.toggleBg)
                        .border(
                            1.dp,
                            if (selected) colors.accentPrimary else colors.border,
                            RoundedCornerShape(AphroditeRadius.xs),
                        )
                        .clickable(role = Role.Button) { onSelectFolder(folder) }
                        .padding(horizontal = AphroditeSpacing.md, vertical = AphroditeSpacing.sm),
                ) {
                    Text(
                        text = folder.substringAfterLast('/'),
                        color = if (selected) colors.accentPrimary else colors.fg,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        AphroditeDivider()

        if (state.messages.isEmpty()) {
            AphroditeEmptyState(
                title = if (state.loading) "Loading…" else "Nothing here",
                description = if (state.loading) null else "This folder has no messages.",
                icon = AphroditeIcons.Mail,
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.messages, key = { it.folder + it.uid }) { header ->
                    MessageRow(header = header, onClick = { onOpen(header) })
                }
            }
        }

        state.activeAccount?.let { account ->
            AphroditeDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AphroditeSpacing.sm, vertical = AphroditeSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${account.imapHost}:${account.imapPort}",
                    color = colors.colorMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
                AphroditeIconButton(
                    icon = AphroditeIcons.Edit,
                    contentDescription = "Edit account",
                    onClick = { onEditAccount(account) },
                    size = 24.dp,
                )
                AphroditeIconButton(
                    icon = AphroditeIcons.Trash,
                    contentDescription = "Remove account",
                    onClick = { onDeleteAccount(account.id) },
                    size = 24.dp,
                    tint = colors.colorError,
                )
            }
        }
    }
}

@Composable
private fun MessageRow(header: EmailHeader, onClick: () -> Unit) {
    val colors = aphroditeColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .background(if (header.seen) Color.Transparent else colors.accentPrimary.copy(alpha = 0.06f))
            .padding(horizontal = AphroditeSpacing.md, vertical = AphroditeSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(AphroditeRadius.pill))
                .background(if (header.seen) Color.Transparent else colors.accentPrimary),
        )
        Box(Modifier.size(AphroditeSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = header.from,
                color = if (header.seen) colors.fg else colors.fg,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                fontWeight = if (header.seen) FontWeight.Normal else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = header.subject,
                color = colors.fg,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = DATE_FORMAT.format(Date(header.date)),
            color = colors.colorMuted,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        )
    }
    AphroditeDivider()
}

@Composable
private fun Reader(
    state: EmailUiState,
    onClose: () -> Unit,
    onReply: (com.aphrodite.core.database.entity.EmailBody) -> Unit,
) {
    val colors = aphroditeColors
    val body = state.openMessage ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AphroditeSpacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = body.header.subject,
                color = colors.fg,
                style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            AphroditeIconButton(
                icon = AphroditeIcons.Send,
                contentDescription = "Reply",
                onClick = { onReply(body) },
                size = 30.dp,
                tint = colors.accentPrimary,
            )
        }
        Box(Modifier.size(AphroditeSpacing.sm))
        Text(
            text = body.header.from,
            color = colors.colorMuted,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
        Text(
            text = DATE_FORMAT.format(Date(body.header.date)),
            color = colors.colorMuted,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        )
        Box(Modifier.size(AphroditeSpacing.md))
        AphroditeDivider()
        Box(Modifier.size(AphroditeSpacing.md))
        Text(
            text = body.text.ifBlank { "(no readable text)" },
            color = colors.fg,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        )
        if (body.attachments.isNotEmpty()) {
            Box(Modifier.size(AphroditeSpacing.lg))
            AphroditeSectionHeader(title = "Attachments", icon = AphroditeIcons.File)
            body.attachments.forEach { attachment ->
                Text(
                    text = "${attachment.name} · ${attachment.size / 1024} KB",
                    color = colors.colorMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
        Box(Modifier.size(AphroditeSpacing.huge))
    }
}

@Composable
private fun ComposeForm(
    draft: ComposeDraft,
    onChange: (ComposeDraft) -> Unit,
    onCancel: () -> Unit,
    onSend: () -> Unit,
    loading: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AphroditeSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AphroditeSpacing.md),
    ) {
        AphroditeSectionHeader(title = "New message", icon = AphroditeIcons.Edit)
        AphroditeTextField(
            value = draft.to,
            onValueChange = { onChange(draft.copy(to = it)) },
            placeholder = "To (comma separated)",
            leadingIcon = AphroditeIcons.User,
        )
        AphroditeTextField(
            value = draft.subject,
            onValueChange = { onChange(draft.copy(subject = it)) },
            placeholder = "Subject",
            leadingIcon = AphroditeIcons.Type,
        )
        AphroditeTextField(
            value = draft.body,
            onValueChange = { onChange(draft.copy(body = it)) },
            placeholder = "Message",
            leadingIcon = AphroditeIcons.Notes,
            singleLine = false,
            minHeight = 220.dp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm)) {
            AphroditeButton(
                text = "Send",
                onClick = onSend,
                icon = AphroditeIcons.Send,
                loading = loading,
                enabled = draft.canSend,
            )
            AphroditeButton(text = "Discard", onClick = onCancel, style = AphroditeButtonStyle.Secondary)
        }
        Box(Modifier.size(AphroditeSpacing.huge))
    }
}

@Composable
private fun AccountForm(
    draft: AccountDraft,
    onChange: (AccountDraft) -> Unit,
    onCancel: () -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    loading: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AphroditeSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AphroditeSpacing.md),
    ) {
        AphroditeSectionHeader(
            title = if (draft.isNew) "Add account" else "Edit account",
            icon = AphroditeIcons.Mail,
        )
        AphroditeTextField(
            value = draft.address,
            onValueChange = { onChange(draft.copy(address = it)) },
            placeholder = "you@example.com",
            leadingIcon = AphroditeIcons.Mail,
        )
        AphroditeTextField(
            value = draft.imapHost,
            onValueChange = { onChange(draft.copy(imapHost = it)) },
            placeholder = "IMAP host, e.g. imap.gmail.com",
            leadingIcon = AphroditeIcons.Server,
        )
        AphroditeTextField(
            value = draft.imapPort,
            onValueChange = { onChange(draft.copy(imapPort = it.filter(Char::isDigit))) },
            placeholder = "IMAP port (993)",
            leadingIcon = AphroditeIcons.Tag,
        )
        AphroditeTextField(
            value = draft.imapUsername,
            onValueChange = { onChange(draft.copy(imapUsername = it)) },
            placeholder = "IMAP username",
            leadingIcon = AphroditeIcons.User,
        )
        AphroditeTextField(
            value = draft.password,
            onValueChange = { onChange(draft.copy(password = it)) },
            placeholder = if (draft.hasStoredPassword) "Leave blank to keep the stored password" else "Password or app password",
            leadingIcon = AphroditeIcons.Key,
        )

        Text(
            text = "Security",
            color = aphroditeColors.fg,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm)) {
            listOf("ssl" to "SSL", "starttls" to "STARTTLS", "none" to "None").forEach { (value, label) ->
                val selected = draft.imapSecurity == value
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AphroditeRadius.xs))
                        .background(
                            if (selected) aphroditeColors.accentPrimary.copy(alpha = 0.2f)
                            else aphroditeColors.toggleBg,
                        )
                        .border(
                            1.dp,
                            if (selected) aphroditeColors.accentPrimary else aphroditeColors.border,
                            RoundedCornerShape(AphroditeRadius.xs),
                        )
                        .clickable(role = Role.RadioButton) { onChange(draft.copy(imapSecurity = value)) }
                        .padding(horizontal = AphroditeSpacing.md, vertical = AphroditeSpacing.sm),
                ) {
                    Text(
                        text = label,
                        color = if (selected) aphroditeColors.accentPrimary else aphroditeColors.fg,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        Text(
            text = "Outgoing (SMTP)",
            color = aphroditeColors.fg,
            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
        )
        AphroditeTextField(
            value = draft.smtpHost,
            onValueChange = { onChange(draft.copy(smtpHost = it)) },
            placeholder = "SMTP host, e.g. smtp.gmail.com",
            leadingIcon = AphroditeIcons.Server,
        )
        AphroditeTextField(
            value = draft.smtpPort,
            onValueChange = { onChange(draft.copy(smtpPort = it.filter(Char::isDigit))) },
            placeholder = "SMTP port (587)",
            leadingIcon = AphroditeIcons.Tag,
        )
        AphroditeTextField(
            value = draft.smtpUsername,
            onValueChange = { onChange(draft.copy(smtpUsername = it)) },
            placeholder = "SMTP username (defaults to the IMAP one)",
            leadingIcon = AphroditeIcons.User,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm)) {
            listOf("starttls" to "STARTTLS", "ssl" to "SSL", "none" to "None").forEach { (value, label) ->
                val selected = draft.smtpSecurity == value
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AphroditeRadius.xs))
                        .background(
                            if (selected) aphroditeColors.accentPrimary.copy(alpha = 0.2f)
                            else aphroditeColors.toggleBg,
                        )
                        .border(
                            1.dp,
                            if (selected) aphroditeColors.accentPrimary else aphroditeColors.border,
                            RoundedCornerShape(AphroditeRadius.xs),
                        )
                        .clickable(role = Role.RadioButton) { onChange(draft.copy(smtpSecurity = value)) }
                        .padding(horizontal = AphroditeSpacing.md, vertical = AphroditeSpacing.sm),
                ) {
                    Text(
                        text = label,
                        color = if (selected) aphroditeColors.accentPrimary else aphroditeColors.fg,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm)) {
            AphroditeButton(
                text = "Test",
                onClick = onTest,
                icon = AphroditeIcons.Zap,
                style = AphroditeButtonStyle.Secondary,
                loading = loading,
                enabled = draft.imapHost.isNotBlank() && draft.imapUsername.isNotBlank(),
            )
            AphroditeButton(
                text = "Save",
                onClick = onSave,
                icon = AphroditeIcons.Check,
                enabled = draft.address.isNotBlank() && draft.imapHost.isNotBlank(),
            )
            AphroditeButton(text = "Cancel", onClick = onCancel, style = AphroditeButtonStyle.Ghost)
        }
        Box(Modifier.size(AphroditeSpacing.huge))
    }
}
