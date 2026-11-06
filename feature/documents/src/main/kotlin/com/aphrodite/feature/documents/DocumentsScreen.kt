package com.aphrodite.feature.documents

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aphrodite.core.adaptive.AphroditeTwoPane
import com.aphrodite.core.database.entity.DocumentEntity
import com.aphrodite.designsystem.components.AphroditeButton
import com.aphrodite.designsystem.components.AphroditeButtonStyle
import com.aphrodite.designsystem.components.AphroditeCard
import com.aphrodite.designsystem.components.AphroditeDivider
import com.aphrodite.designsystem.components.AphroditeEmptyState
import com.aphrodite.designsystem.components.AphroditeIconButton
import com.aphrodite.designsystem.components.AphroditeSectionHeader
import com.aphrodite.designsystem.components.AphroditeSpinner
import com.aphrodite.designsystem.components.AphroditeTextField
import com.aphrodite.designsystem.icons.AphroditeIcons
import com.aphrodite.designsystem.theme.AphroditeSpacing
import com.aphrodite.designsystem.theme.aphroditeColors

/**
 * Documents and their retrieval index.
 *
 * The panel says plainly when nothing is indexed, because the agent's `search_documents` tool will
 * otherwise look broken rather than empty.
 */
@Composable
fun DocumentsScreen(
    state: DocumentsUiState,
    onAdd: () -> Unit,
    onDraftChange: (DocumentDraft) -> Unit,
    onCancelAdd: () -> Unit,
    onSave: () -> Unit,
    onReindex: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = aphroditeColors
    AphroditeTwoPane(
        showDetail = state.draft != null,
        modifier = modifier,
        listWidth = 360.dp,
        list = {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AphroditeSpacing.sm, vertical = AphroditeSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Documents",
                        color = colors.colorSubheader,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    AphroditeIconButton(
                        icon = AphroditeIcons.Plus,
                        contentDescription = "Add document",
                        onClick = onAdd,
                        size = 28.dp,
                    )
                }
                AphroditeDivider()

                if (state.documents.isEmpty()) {
                    AphroditeEmptyState(
                        title = "No documents",
                        description = "Add text and the agent can search it instead of guessing.",
                        icon = AphroditeIcons.Library,
                        action = {
                            AphroditeButton(text = "Add document", onClick = onAdd, icon = AphroditeIcons.Plus)
                        },
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.documents, key = { it.id }) { document ->
                            DocumentRow(
                                document = document,
                                indexing = state.indexingId == document.id,
                                onReindex = { onReindex(document.id) },
                                onDelete = { onDelete(document.id) },
                            )
                        }
                    }
                }
            }
        },
        emptyDetail = {
            AphroditeEmptyState(
                title = "Searchable by the agent",
                description = "Everything here is available to the model through search_documents.",
                icon = AphroditeIcons.Library,
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
                    AphroditeSectionHeader(title = "Add document", icon = AphroditeIcons.Library)
                    AphroditeTextField(
                        value = draft.title,
                        onValueChange = { onDraftChange(draft.copy(title = it)) },
                        placeholder = "Title",
                        leadingIcon = AphroditeIcons.Type,
                    )
                    AphroditeTextField(
                        value = draft.body,
                        onValueChange = { onDraftChange(draft.copy(body = it)) },
                        placeholder = "Paste or type the text",
                        leadingIcon = AphroditeIcons.Notes,
                        singleLine = false,
                        minHeight = 320.dp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm)) {
                        AphroditeButton(
                            text = "Save and index",
                            onClick = onSave,
                            icon = AphroditeIcons.Check,
                            enabled = draft.title.isNotBlank() && draft.body.isNotBlank(),
                        )
                        AphroditeButton(
                            text = "Cancel",
                            onClick = onCancelAdd,
                            style = AphroditeButtonStyle.Secondary,
                        )
                    }
                }
            }
        },
    )

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
}

@Composable
private fun DocumentRow(
    document: DocumentEntity,
    indexing: Boolean,
    onReindex: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = aphroditeColors
    Column(modifier = Modifier.fillMaxWidth().padding(AphroditeSpacing.sm)) {
        AphroditeCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.title,
                        color = colors.fg,
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when {
                            indexing -> "Indexing…"
                            document.indexedAt != null -> "${document.chunkCount} passages indexed"
                            else -> "Not indexed"
                        },
                        color = if (document.indexedAt != null) colors.colorSuccess else colors.colorWarning,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    )
                    document.lastError?.let {
                        Text(
                            text = it,
                            color = colors.colorError,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (indexing) {
                    AphroditeSpinner(size = 14.dp)
                } else {
                    AphroditeIconButton(
                        icon = AphroditeIcons.Refresh,
                        contentDescription = "Re-index",
                        onClick = onReindex,
                        size = 26.dp,
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
}
