package com.aphrodite.feature.compare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aphrodite.designsystem.components.AphroditeButton
import com.aphrodite.designsystem.components.AphroditeButtonSize
import com.aphrodite.designsystem.components.AphroditeButtonStyle
import com.aphrodite.designsystem.components.AphroditeCard
import com.aphrodite.designsystem.components.AphroditeDivider
import com.aphrodite.designsystem.components.AphroditeEmptyState
import com.aphrodite.designsystem.components.AphroditeIconButton
import com.aphrodite.designsystem.components.AphroditeSpinner
import com.aphrodite.designsystem.components.AphroditeTextField
import com.aphrodite.designsystem.icons.AphroditeIcons
import com.aphrodite.designsystem.theme.AphroditeSpacing
import com.aphrodite.designsystem.theme.aphroditeColors

/**
 * One prompt, several models, side by side.
 *
 * Columns rather than a stacked list, because the comparison is the point: answers that have to be
 * scrolled between are answers you cannot actually compare.
 */
@Composable
fun CompareScreen(
    state: CompareUiState,
    onPromptChange: (String) -> Unit,
    onToggleModel: (String) -> Unit,
    onRun: () -> Unit,
    onClear: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = aphroditeColors

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(AphroditeSpacing.md)) {
            AphroditeTextField(
                value = state.prompt,
                onValueChange = onPromptChange,
                placeholder = "The prompt to send to every model",
                leadingIcon = AphroditeIcons.Compare,
                singleLine = false,
                minHeight = 80.dp,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = AphroditeSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.xs),
            ) {
                Text(
                    text = "${state.selected.size} of ${CompareUiState.MAX_MODELS} selected",
                    color = colors.colorSubheader,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
                if (state.runs.isNotEmpty() && !state.running) {
                    AphroditeButton(
                        text = "Clear",
                        onClick = onClear,
                        style = AphroditeButtonStyle.Secondary,
                        size = AphroditeButtonSize.Small,
                    )
                }
                AphroditeButton(
                    text = if (state.running) "Running…" else "Run",
                    onClick = onRun,
                    icon = AphroditeIcons.Play,
                    enabled = state.canRun,
                    size = AphroditeButtonSize.Small,
                )
            }
        }

        if (state.choices.isEmpty()) {
            AphroditeEmptyState(
                title = "No models yet",
                description = "Add a provider and its models first.",
                icon = AphroditeIcons.Compare,
            )
            return@Column
        }

        ModelPicker(state = state, onToggleModel = onToggleModel)
        AphroditeDivider()

        state.error?.let { message ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = AphroditeSpacing.md),
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

        if (state.runs.isEmpty()) {
            AphroditeEmptyState(
                title = "Compare models",
                description = "Pick two or three models and send them the same prompt.",
                icon = AphroditeIcons.Compare,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxSize().padding(AphroditeSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm),
            ) {
                state.runs.forEach { run ->
                    RunColumn(run = run, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun ModelPicker(state: CompareUiState, onToggleModel: (String) -> Unit) {
    val colors = aphroditeColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AphroditeSpacing.md, vertical = AphroditeSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.xs),
    ) {
        state.choices.take(6).forEach { choice ->
            val selected = choice.key in state.selected
            AphroditeButton(
                text = choice.label,
                onClick = { onToggleModel(choice.key) },
                style = if (selected) AphroditeButtonStyle.Primary else AphroditeButtonStyle.Secondary,
                size = AphroditeButtonSize.Small,
            )
        }
    }
    if (state.choices.size > 6) {
        Text(
            text = "${state.choices.size - 6} more models are available in Providers.",
            color = colors.colorSubheader,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = AphroditeSpacing.md),
        )
    }
}

@Composable
private fun RunColumn(run: CompareRun, modifier: Modifier = Modifier) {
    val colors = aphroditeColors
    AphroditeCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                text = run.label,
                color = colors.fg,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when (run.status) {
                    RunStatus.Running -> "Running…"
                    RunStatus.Failed -> "Failed"
                    RunStatus.Done ->
                        "${run.elapsedMs} ms · ${run.tokens} tokens" +
                            if (run.costUsd > 0.0) " · $${"%.4f".format(run.costUsd)}" else ""
                },
                color = when (run.status) {
                    RunStatus.Failed -> colors.colorError
                    RunStatus.Done -> colors.colorSuccess
                    RunStatus.Running -> colors.colorAccent
                },
                style = MaterialTheme.typography.labelSmall,
            )

            when (run.status) {
                RunStatus.Running -> AphroditeSpinner(size = 16.dp)
                RunStatus.Failed -> Text(
                    text = run.error.orEmpty(),
                    color = colors.colorError,
                    style = MaterialTheme.typography.bodySmall,
                )
                RunStatus.Done -> Text(
                    text = run.text,
                    color = colors.fg,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = AphroditeSpacing.sm),
                )
            }
        }
    }
}
