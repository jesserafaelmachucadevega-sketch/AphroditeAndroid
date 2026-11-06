package com.aphrodite.feature.speech

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
import com.aphrodite.designsystem.components.AphroditeButton
import com.aphrodite.designsystem.components.AphroditeButtonSize
import com.aphrodite.designsystem.components.AphroditeButtonStyle
import com.aphrodite.designsystem.components.AphroditeCard
import com.aphrodite.designsystem.components.AphroditeDivider
import com.aphrodite.designsystem.components.AphroditeEmptyState
import com.aphrodite.designsystem.components.AphroditeIconButton
import com.aphrodite.designsystem.components.AphroditeSectionHeader
import com.aphrodite.designsystem.icons.AphroditeIcons
import com.aphrodite.designsystem.theme.AphroditeSpacing
import com.aphrodite.designsystem.theme.aphroditeColors

/**
 * Speech settings, all backed by the platform's own engines.
 *
 * The voice list is the reason this screen exists. A device offers a dozen variants of one language
 * and defaults to a dated one of them, which is the usual reason text-to-speech sounds robotic.
 * Sorted best-quality-first so the good ones are not buried in the middle.
 */
@Composable
fun SpeechScreen(
    state: SpeechUiState,
    onSpeakRepliesChange: (Boolean) -> Unit,
    onVoiceSelected: (String) -> Unit,
    onRateChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onPreview: () -> Unit,
    onStopSpeaking: () -> Unit,
    onToggleDictation: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = aphroditeColors

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(AphroditeSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AphroditeSpacing.md),
    ) {
        item {
            AphroditeSectionHeader(title = "Reading answers aloud", icon = AphroditeIcons.Volume)
            Text(
                text = "Uses the text-to-speech engine installed on this device. " +
                    "Nothing is sent anywhere to read an answer.",
                color = colors.colorSubheader,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm),
            ) {
                AphroditeButton(
                    text = if (state.speakReplies) "On" else "Off",
                    onClick = { onSpeakRepliesChange(!state.speakReplies) },
                    icon = if (state.speakReplies) AphroditeIcons.CheckCircle else AphroditeIcons.Close,
                    size = AphroditeButtonSize.Small,
                    style = if (state.speakReplies) {
                        AphroditeButtonStyle.Primary
                    } else {
                        AphroditeButtonStyle.Secondary
                    },
                )
                Text(
                    text = if (state.speakReplies) "Answers are read as they finish" else "Silent",
                    color = colors.colorSubheader,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item { AphroditeDivider() }

        item {
            AphroditeSectionHeader(title = "Voice", icon = AphroditeIcons.Volume)
            if (!state.ready) {
                Text(
                    text = "Waiting for the speech engine…",
                    color = colors.colorSubheader,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (state.ready && state.voices.isEmpty()) {
            item {
                AphroditeEmptyState(
                    title = "No voices installed",
                    description = "The speech engine has no voices for this language. " +
                        "Install one from the device's text-to-speech settings.",
                    icon = AphroditeIcons.Volume,
                )
            }
        }

        items(state.voices, key = { it.name }) { voice ->
            VoiceRow(
                voice = voice,
                selected = voice.name == state.selectedVoice,
                onClick = { onVoiceSelected(voice.name) },
            )
        }

        if (state.voices.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm)) {
                    AphroditeButton(
                        text = "Read a sample",
                        onClick = onPreview,
                        icon = AphroditeIcons.Volume,
                        size = AphroditeButtonSize.Small,
                    )
                    AphroditeButton(
                        text = "Stop",
                        onClick = onStopSpeaking,
                        style = AphroditeButtonStyle.Secondary,
                        size = AphroditeButtonSize.Small,
                    )
                }
            }

            item { Adjuster("Speech rate", state.rate, 0.25f, 3f, onRateChange) }
            item { Adjuster("Pitch", state.pitch, 0.5f, 2f, onPitchChange) }
        }

        item { AphroditeDivider() }

        item {
            AphroditeSectionHeader(title = "Dictation", icon = AphroditeIcons.Mic)
            Text(
                text = when {
                    !state.recogniserAvailable -> "No speech recogniser is installed on this device."
                    !state.hasMicPermission -> "Microphone permission is needed. It will be requested on first use."
                    else -> "Uses the device's recogniser. It may send audio to a remote service — " +
                        "that is the platform's behaviour, not a choice made here."
                },
                color = if (!state.recogniserAvailable) colors.colorWarning else colors.colorSubheader,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (state.recogniserAvailable) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm),
                ) {
                    AphroditeButton(
                        text = if (state.dictationInProgress) "Stop listening" else "Try dictation",
                        onClick = onToggleDictation,
                        icon = AphroditeIcons.Mic,
                        size = AphroditeButtonSize.Small,
                        style = if (state.dictationInProgress) {
                            AphroditeButtonStyle.Danger
                        } else {
                            AphroditeButtonStyle.Secondary
                        },
                    )
                    if (state.dictationInProgress) {
                        Text(
                            text = "Listening…",
                            color = colors.colorAccent,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (state.dictationText.isNotBlank()) {
                item {
                    AphroditeCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = state.dictationText,
                            color = colors.fg,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
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
    }
}

@Composable
private fun VoiceRow(voice: VoiceOption, selected: Boolean, onClick: () -> Unit) {
    val colors = aphroditeColors
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = AphroditeSpacing.xs)) {
        AphroditeCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = voice.label,
                    color = if (selected) colors.colorAccent else colors.fg,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    // Network voices can bill and need a connection; offline ones always work.
                    text = if (voice.offline) "on device" else "needs network",
                    color = if (voice.offline) colors.colorSuccess else colors.colorWarning,
                    style = MaterialTheme.typography.labelSmall,
                )
                if (selected) {
                    AphroditeIconButton(
                        icon = AphroditeIcons.CheckCircle,
                        contentDescription = "Selected",
                        onClick = onClick,
                        size = 24.dp,
                        tint = colors.colorAccent,
                    )
                }
            }
        }
    }
}

/** A stepped control rather than a slider, so the exact value is readable and tappable. */
@Composable
private fun Adjuster(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit,
) {
    val colors = aphroditeColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm),
        modifier = Modifier.fillMaxWidth().padding(vertical = AphroditeSpacing.xs),
    ) {
        Text(
            text = label,
            color = colors.colorSubheader,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        AphroditeButton(
            text = "−",
            onClick = { onChange((value - 0.05f).coerceAtLeast(min)) },
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
            onClick = { onChange((value + 0.05f).coerceAtMost(max)) },
            style = AphroditeButtonStyle.Ghost,
            size = AphroditeButtonSize.Small,
        )
        AphroditeButton(
            text = "Reset",
            onClick = { onChange(1f) },
            style = AphroditeButtonStyle.Ghost,
            size = AphroditeButtonSize.Small,
        )
    }
}
