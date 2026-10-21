package com.aphrodite.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aphrodite.core.adaptive.AphroditeTwoPane
import com.aphrodite.core.database.entity.TaskEntity
import com.aphrodite.core.database.entity.TaskRunEntity
import com.aphrodite.core.database.entity.TaskTrigger
import com.aphrodite.designsystem.components.AphroditeButton
import com.aphrodite.designsystem.components.AphroditeButtonSize
import com.aphrodite.designsystem.components.AphroditeButtonStyle
import com.aphrodite.designsystem.components.AphroditeCard
import com.aphrodite.designsystem.components.AphroditeDivider
import com.aphrodite.designsystem.components.AphroditeEmptyState
import com.aphrodite.designsystem.components.AphroditeIconButton
import com.aphrodite.designsystem.components.AphroditeSectionHeader
import com.aphrodite.designsystem.components.AphroditeSpinner
import com.aphrodite.designsystem.components.AphroditeSwitch
import com.aphrodite.designsystem.components.AphroditeTextField
import com.aphrodite.designsystem.icons.AphroditeIcons
import com.aphrodite.designsystem.theme.AphroditeRadius
import com.aphrodite.designsystem.theme.AphroditeSpacing
import com.aphrodite.designsystem.theme.aphroditeColors

/**
 * Tasks.
 *
 * A task is a saved prompt with a trigger. Running one sends it through the agent loop, so it can
 * use tools — a "summarise today's AI news" task can actually search.
 */
@Composable
fun TasksScreen(
    state: TasksUiState,
    onAdd: () -> Unit,
    onEdit: (TaskEntity) -> Unit,
    onDelete: (String) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onRun: (String) -> Unit,
    onDraftChange: (TaskDraft) -> Unit,
    onCancelEdit: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AphroditeTwoPane(
        showDetail = true,
        modifier = modifier,
        listWidth = 340.dp,
        list = {
            TaskList(
                state = state,
                onAdd = onAdd,
                onEdit = onEdit,
                onDelete = onDelete,
                onToggleEnabled = onToggleEnabled,
                onRun = onRun,
            )
        },
        detail = {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (state.error != null || state.notice != null) {
                    Banner(
                        message = state.error ?: state.notice.orEmpty(),
                        isError = state.error != null,
                        onDismiss = onDismiss,
                    )
                }
                if (state.draft != null) {
                    TaskEditor(
                        draft = state.draft,
                        onChange = onDraftChange,
                        onCancel = onCancelEdit,
                        onSave = onSave,
                    )
                } else {
                    RunHistory(state.runs)
                }
            }
        },
    )
}

@Composable
private fun Banner(message: String, isError: Boolean, onDismiss: () -> Unit) {
    val colors = aphroditeColors
    val tint = if (isError) colors.colorError else colors.colorSuccess
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = AphroditeSpacing.md, vertical = AphroditeSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isError) AphroditeIcons.AlertCircle else AphroditeIcons.CheckCircle,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(13.dp),
        )
        Box(Modifier.size(AphroditeSpacing.sm))
        Text(
            text = message,
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

@Composable
private fun TaskList(
    state: TasksUiState,
    onAdd: () -> Unit,
    onEdit: (TaskEntity) -> Unit,
    onDelete: (String) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onRun: (String) -> Unit,
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
                text = "Tasks",
                color = colors.colorSubheader,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            AphroditeIconButton(
                icon = AphroditeIcons.Plus,
                contentDescription = "New task",
                onClick = onAdd,
                size = 28.dp,
            )
        }
        AphroditeDivider()

        if (state.tasks.isEmpty()) {
            AphroditeEmptyState(
                title = "No tasks yet",
                description = "A task is a saved prompt you can run on a schedule.",
                icon = AphroditeIcons.Tasks,
                action = { AphroditeButton(text = "New task", onClick = onAdd, icon = AphroditeIcons.Plus) },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(AphroditeSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm),
            ) {
                items(state.tasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        running = state.runningTaskId == task.id,
                        onEdit = { onEdit(task) },
                        onDelete = { onDelete(task.id) },
                        onToggle = { onToggleEnabled(task.id, it) },
                        onRun = { onRun(task.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: TaskEntity,
    running: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRun: () -> Unit,
) {
    val colors = aphroditeColors
    AphroditeCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.name,
                    color = colors.fg,
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = triggerLabel(task),
                    color = colors.colorMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                )
            }
            AphroditeSwitch(checked = task.enabled, onCheckedChange = onToggle)
        }
        val description = task.description
        if (!description.isNullOrBlank()) {
            Box(Modifier.size(AphroditeSpacing.xs))
            Text(
                text = description,
                color = colors.colorMuted,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.size(AphroditeSpacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (running) {
                AphroditeSpinner(size = 14.dp)
                Box(Modifier.size(AphroditeSpacing.sm))
                Text(
                    text = "Running…",
                    color = colors.colorMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                )
            } else {
                AphroditeButton(
                    text = "Run now",
                    onClick = onRun,
                    icon = AphroditeIcons.Play,
                    size = AphroditeButtonSize.Small,
                    style = AphroditeButtonStyle.Secondary,
                )
            }
            Box(Modifier.weight(1f))
            AphroditeIconButton(
                icon = AphroditeIcons.Edit,
                contentDescription = "Edit",
                onClick = onEdit,
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

private fun triggerLabel(task: TaskEntity): String = when (task.triggerKind) {
    TaskTrigger.Manual -> "Manual · runs when you ask"
    TaskTrigger.Interval -> "Every ${task.triggerValue ?: "?"} minutes"
    TaskTrigger.Daily -> "Daily"
    TaskTrigger.Weekly -> "Weekly"
    TaskTrigger.Cron -> "Cron ${task.triggerValue.orEmpty()}"
    TaskTrigger.OnEvent -> "On event"
    TaskTrigger.Webhook -> "On webhook"
}

@Composable
private fun TaskEditor(
    draft: TaskDraft,
    onChange: (TaskDraft) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(AphroditeSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AphroditeSpacing.md),
    ) {
        AphroditeSectionHeader(
            title = if (draft.isNew) "New task" else "Edit task",
            icon = AphroditeIcons.Tasks,
        )
        AphroditeTextField(
            value = draft.name,
            onValueChange = { onChange(draft.copy(name = it)) },
            placeholder = "Name, e.g. Morning AI news",
            leadingIcon = AphroditeIcons.Type,
        )
        AphroditeTextField(
            value = draft.description,
            onValueChange = { onChange(draft.copy(description = it)) },
            placeholder = "Description (optional)",
            leadingIcon = AphroditeIcons.Notes,
        )
        AphroditeTextField(
            value = draft.prompt,
            onValueChange = { onChange(draft.copy(prompt = it)) },
            placeholder = "The prompt to run",
            leadingIcon = AphroditeIcons.Chat,
        )

        Text(
            text = "Trigger",
            color = aphroditeColors.fg,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm)) {
            listOf(TaskTrigger.Manual, TaskTrigger.Interval).forEach { trigger ->
                val selected = draft.trigger == trigger
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
                        .clickableSurface { onChange(draft.copy(trigger = trigger)) }
                        .padding(horizontal = AphroditeSpacing.md, vertical = AphroditeSpacing.sm),
                ) {
                    Text(
                        text = if (trigger == TaskTrigger.Manual) "Manual" else "Every N minutes",
                        color = if (selected) aphroditeColors.accentPrimary else aphroditeColors.fg,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        if (draft.trigger == TaskTrigger.Interval) {
            AphroditeTextField(
                value = draft.intervalMinutes,
                onValueChange = { onChange(draft.copy(intervalMinutes = it.filter(Char::isDigit))) },
                placeholder = "Minutes between runs",
                leadingIcon = AphroditeIcons.Clock,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm)) {
            AphroditeButton(
                text = if (draft.isNew) "Create" else "Save",
                onClick = onSave,
                icon = AphroditeIcons.Check,
                enabled = draft.name.isNotBlank() && draft.prompt.isNotBlank(),
            )
            AphroditeButton(
                text = "Cancel",
                onClick = onCancel,
                style = AphroditeButtonStyle.Secondary,
            )
        }
        Box(Modifier.size(AphroditeSpacing.huge))
    }
}

@Composable
private fun RunHistory(runs: List<TaskRunEntity>) {
    val colors = aphroditeColors
    Column(modifier = Modifier.fillMaxWidth().padding(AphroditeSpacing.lg)) {
        AphroditeSectionHeader(title = "Recent runs", icon = AphroditeIcons.History)
        if (runs.isEmpty()) {
            AphroditeEmptyState(
                title = "Nothing has run yet",
                description = "Pick a task and press Run now.",
                icon = AphroditeIcons.History,
            )
        } else {
            runs.forEach { run ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AphroditeSpacing.sm),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (run.status == "success") {
                                AphroditeIcons.CheckCircle
                            } else {
                                AphroditeIcons.AlertCircle
                            },
                            contentDescription = null,
                            tint = if (run.status == "success") colors.colorSuccess else colors.colorError,
                            modifier = Modifier.size(12.dp),
                        )
                        Box(Modifier.size(AphroditeSpacing.sm))
                        Text(
                            text = run.status.replaceFirstChar { it.uppercase() },
                            color = colors.fg,
                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        )
                        Box(Modifier.weight(1f))
                        run.durationMs?.let {
                            Text(
                                text = "${it / 1000.0}s",
                                color = colors.colorMuted,
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    val body = run.error ?: run.output
                    if (!body.isNullOrBlank()) {
                        Box(Modifier.size(AphroditeSpacing.xs))
                        Text(
                            text = body,
                            color = if (run.error != null) colors.colorError else colors.colorMuted,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    AphroditeDivider(modifier = Modifier.padding(top = AphroditeSpacing.sm))
                }
            }
        }
    }
}

/** Keeps the chip modifier chain readable at the call site. */
private fun Modifier.clickableSurface(onClick: () -> Unit): Modifier = clickable(onClick = onClick)
