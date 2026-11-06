package com.aphrodite.feature.gallery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.aphrodite.designsystem.components.AphroditeButton
import com.aphrodite.designsystem.components.AphroditeButtonSize
import com.aphrodite.designsystem.components.AphroditeButtonStyle
import com.aphrodite.designsystem.components.AphroditeIconButton
import com.aphrodite.designsystem.components.AphroditeSpinner
import com.aphrodite.designsystem.icons.AphroditeIcons
import com.aphrodite.designsystem.theme.AphroditeSpacing
import com.aphrodite.designsystem.theme.aphroditeColors

/**
 * The image editor.
 *
 * Every change replays from the untouched original rather than building on the previous result, so
 * undo restores exactly what was there before and repeated edits cannot compound rounding error
 * into visible degradation.
 */
@Composable
fun ImageEditorRoute(
    item: GalleryItem,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(item.uri) { viewModel.load(item) }

    ImageEditorScreen(
        state = state,
        onRotate = viewModel::rotate,
        onFlip = viewModel::flip,
        onCrop = viewModel::crop,
        onUndo = viewModel::undo,
        onReset = viewModel::reset,
        onBrightness = viewModel::onBrightness,
        onContrast = viewModel::onContrast,
        onSaturation = viewModel::onSaturation,
        onSave = viewModel::save,
        onClose = onClose,
        onDismissError = viewModel::dismissError,
        onDismissSaved = viewModel::dismissSaved,
        modifier = modifier,
    )
}

@Composable
private fun ImageEditorScreen(
    state: EditorUiState,
    onRotate: (Int) -> Unit,
    onFlip: (Boolean) -> Unit,
    onCrop: (Float?) -> Unit,
    onUndo: () -> Unit,
    onReset: () -> Unit,
    onBrightness: (Float) -> Unit,
    onContrast: (Float) -> Unit,
    onSaturation: (Float) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onDismissError: () -> Unit,
    onDismissSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = aphroditeColors

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .padding(AphroditeSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AphroditeIconButton(
                icon = AphroditeIcons.ArrowLeft,
                contentDescription = "Back",
                onClick = onClose,
                size = 28.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Edit",
                    color = colors.fg,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = if (state.outputWidth > 0) {
                        "${state.outputWidth} × ${state.outputHeight} · saved as a new copy"
                    } else {
                        "Loading…"
                    },
                    color = colors.colorSubheader,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            AphroditeButton(
                text = "Undo",
                onClick = onUndo,
                style = AphroditeButtonStyle.Ghost,
                size = AphroditeButtonSize.Small,
                enabled = state.canUndo,
            )
            AphroditeButton(
                text = "Reset",
                onClick = onReset,
                style = AphroditeButtonStyle.Ghost,
                size = AphroditeButtonSize.Small,
                enabled = state.canUndo,
            )
            AphroditeButton(
                text = if (state.saving) "Saving…" else "Save",
                onClick = onSave,
                icon = AphroditeIcons.Save,
                size = AphroditeButtonSize.Small,
                enabled = state.preview != null && !state.saving,
            )
        }

        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.loading -> AphroditeSpinner()
                state.preview != null -> Image(
                    bitmap = state.preview.asImageBitmap(),
                    contentDescription = "Editing ${state.item?.name.orEmpty()}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        state.savedName?.let { name ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = AphroditeSpacing.md, vertical = AphroditeSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Saved as $name",
                    color = colors.colorSuccess,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                AphroditeIconButton(
                    icon = AphroditeIcons.Close,
                    contentDescription = "Dismiss",
                    onClick = onDismissSaved,
                    size = 22.dp,
                )
            }
        }

        state.error?.let { message ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = AphroditeSpacing.md, vertical = AphroditeSpacing.xs),
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

        Column(modifier = Modifier.fillMaxWidth().padding(AphroditeSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AphroditeButton(
                    text = "Rotate left",
                    onClick = { onRotate(-90) },
                    icon = AphroditeIcons.RotateCcw,
                    style = AphroditeButtonStyle.Secondary,
                    size = AphroditeButtonSize.Small,
                )
                AphroditeButton(
                    text = "Rotate right",
                    onClick = { onRotate(90) },
                    icon = AphroditeIcons.RotateCw,
                    style = AphroditeButtonStyle.Secondary,
                    size = AphroditeButtonSize.Small,
                )
                AphroditeButton(
                    text = "Flip across",
                    onClick = { onFlip(true) },
                    icon = AphroditeIcons.FlipHorizontal,
                    style = AphroditeButtonStyle.Secondary,
                    size = AphroditeButtonSize.Small,
                )
                AphroditeButton(
                    text = "Flip down",
                    onClick = { onFlip(false) },
                    icon = AphroditeIcons.FlipVertical,
                    style = AphroditeButtonStyle.Secondary,
                    size = AphroditeButtonSize.Small,
                )
                CropRatio.entries.forEach { option ->
                    AphroditeButton(
                        text = option.label,
                        onClick = { onCrop(option.ratio) },
                        style = AphroditeButtonStyle.Ghost,
                        size = AphroditeButtonSize.Small,
                    )
                }
            }

            Stepper("Brightness", state.adjustments.brightness, -0.5f, 0.5f, onBrightness)
            Stepper("Contrast", state.adjustments.contrast, 0.5f, 2f, onContrast)
            Stepper("Saturation", state.adjustments.saturation, 0f, 2f, onSaturation)
        }
    }
}

/** Stepped control: the exact value stays readable, which a slider on a phone does not. */
@Composable
private fun Stepper(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit,
) {
    val colors = aphroditeColors
    val step = (max - min) / 20f
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm),
    ) {
        Text(
            text = label,
            color = colors.colorSubheader,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        AphroditeButton(
            text = "−",
            onClick = { onChange((value - step).coerceAtLeast(min)) },
            style = AphroditeButtonStyle.Ghost,
            size = AphroditeButtonSize.Small,
        )
        Text(
            text = "%.2f".format(value),
            color = colors.fg,
            style = MaterialTheme.typography.bodySmall,
        )
        AphroditeButton(
            text = "+",
            onClick = { onChange((value + step).coerceAtMost(max)) },
            style = AphroditeButtonStyle.Ghost,
            size = AphroditeButtonSize.Small,
        )
    }
}
