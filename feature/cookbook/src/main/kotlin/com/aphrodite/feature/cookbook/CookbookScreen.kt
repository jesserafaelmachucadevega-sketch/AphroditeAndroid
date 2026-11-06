package com.aphrodite.feature.cookbook

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
import com.aphrodite.core.database.entity.LocalModelEntity
import com.aphrodite.core.database.entity.LocalModelStatus
import com.aphrodite.designsystem.components.AphroditeButton
import com.aphrodite.designsystem.components.AphroditeButtonSize
import com.aphrodite.designsystem.components.AphroditeButtonStyle
import com.aphrodite.designsystem.components.AphroditeCard
import com.aphrodite.designsystem.components.AphroditeEmptyState
import com.aphrodite.designsystem.components.AphroditeIconButton
import com.aphrodite.designsystem.components.AphroditeSectionHeader
import com.aphrodite.designsystem.components.AphroditeTextField
import com.aphrodite.designsystem.icons.AphroditeIcons
import com.aphrodite.designsystem.theme.AphroditeSpacing
import com.aphrodite.designsystem.theme.aphroditeColors
import java.util.Locale

/**
 * On-device models.
 *
 * The header states which part of the pipeline exists, because a list of models that cannot yet be
 * loaded would otherwise read as a bug rather than as work in progress.
 */
@Composable
fun CookbookScreen(
    state: CookbookUiState,
    onUrlChange: (String) -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onDelete: (LocalModelEntity) -> Unit,
    onCancelDownload: (LocalModelEntity) -> Unit,
    onDismissError: () -> Unit,
    onDismissNotice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = aphroditeColors

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(AphroditeSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm),
    ) {
        item {
            AphroditeSectionHeader(title = "Local models", icon = AphroditeIcons.Cookbook)
            Text(
                text = "${state.readyCount} ready · ${formatBytes(state.storageUsed)} on device",
                color = colors.colorSubheader,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Manage model files here. Running them on the device needs the native " +
                    "inference engine, which is not built yet — until then use a local runtime " +
                    "such as Ollama or LM Studio through Providers.",
                color = colors.colorWarning,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = AphroditeSpacing.xs),
            )
        }

        item {
            AphroditeTextField(
                value = state.url,
                onValueChange = onUrlChange,
                placeholder = "Paste a link to a .gguf file",
                leadingIcon = AphroditeIcons.Link,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = AphroditeSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm),
            ) {
                AphroditeButton(
                    text = "Download",
                    onClick = onAdd,
                    icon = AphroditeIcons.Download,
                    size = AphroditeButtonSize.Small,
                    enabled = state.url.isNotBlank(),
                )
                AphroditeButton(
                    text = "Import from device",
                    onClick = onImport,
                    icon = AphroditeIcons.Upload,
                    style = AphroditeButtonStyle.Secondary,
                    size = AphroditeButtonSize.Small,
                )
            }
            Text(
                text = "A file page link from Hugging Face works as well as a direct download link.",
                color = colors.colorSubheader,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = AphroditeSpacing.xs),
            )
        }

        state.error?.let { message ->
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
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

        state.notice?.let { message ->
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = message,
                        color = colors.colorSuccess,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    AphroditeIconButton(
                        icon = AphroditeIcons.Close,
                        contentDescription = "Dismiss",
                        onClick = onDismissNotice,
                        size = 22.dp,
                    )
                }
            }
        }

        if (state.models.isEmpty()) {
            item {
                AphroditeEmptyState(
                    title = "No models yet",
                    description = "Download one, or import a GGUF file you already have.",
                    icon = AphroditeIcons.Cookbook,
                )
            }
        }

        items(state.models, key = { it.id }) { model ->
            ModelRow(
                model = model,
                onDelete = { onDelete(model) },
                onCancel = { onCancelDownload(model) },
            )
        }
    }
}

@Composable
private fun ModelRow(
    model: LocalModelEntity,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = aphroditeColors
    AphroditeCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.name,
                    color = colors.fg,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        model.quantization,
                        model.parametersB?.let { "%.1fB".format(it) },
                        if (model.status == LocalModelStatus.Ready) formatBytes(model.sizeBytes) else null,
                    ).joinToString(" · ").ifBlank { formatBytes(model.sizeBytes) },
                    color = colors.colorSubheader,
                    style = MaterialTheme.typography.labelSmall,
                )
                model.error?.let {
                    Text(
                        text = it,
                        color = colors.colorError,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when (model.status) {
                LocalModelStatus.Downloading -> {
                    Text(
                        text = percent(model),
                        color = colors.colorAccent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    AphroditeIconButton(
                        icon = AphroditeIcons.Close,
                        contentDescription = "Cancel download",
                        onClick = onCancel,
                        size = 24.dp,
                    )
                }
                LocalModelStatus.Ready -> AphroditeIconButton(
                    icon = AphroditeIcons.Trash,
                    contentDescription = "Delete",
                    onClick = onDelete,
                    size = 24.dp,
                    tint = colors.colorError,
                )
                LocalModelStatus.Failed -> AphroditeIconButton(
                    icon = AphroditeIcons.Trash,
                    contentDescription = "Delete",
                    onClick = onDelete,
                    size = 24.dp,
                    tint = colors.colorError,
                )
            }
        }
    }
}

private fun percent(model: LocalModelEntity): String =
    if (model.sizeBytes <= 0) {
        formatBytes(model.downloadedBytes)
    } else {
        "${(model.downloadedBytes * 100 / model.sizeBytes).coerceIn(0, 100)}%"
    }

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "0 MB"
    bytes < 1024L * 1024L -> "%.0f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> "%.0f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0)).let { String.format(Locale.US, it) }
}
