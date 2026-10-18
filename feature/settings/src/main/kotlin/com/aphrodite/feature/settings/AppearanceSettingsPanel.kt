package com.aphrodite.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aphrodite.core.common.layout.LayoutMode
import com.aphrodite.designsystem.components.AphroditeCard
import com.aphrodite.designsystem.components.AphroditeCheckbox
import com.aphrodite.designsystem.components.AphroditeDivider
import com.aphrodite.designsystem.components.AphroditeSlider
import com.aphrodite.designsystem.components.AphroditeSwitchRow
import com.aphrodite.designsystem.components.AphroditeSectionHeader
import com.aphrodite.designsystem.icons.AphroditeIcons
import com.aphrodite.designsystem.theme.AphroditeDensity
import com.aphrodite.designsystem.theme.AphroditeFont
import com.aphrodite.designsystem.theme.AphroditeRadius
import com.aphrodite.designsystem.theme.AphroditeSpacing
import com.aphrodite.designsystem.theme.AphroditeThemeSpec
import com.aphrodite.designsystem.theme.BackgroundPattern
import com.aphrodite.designsystem.theme.aphroditeColors

/** Callbacks the appearance panel uses to persist changes. */
data class AppearanceHandlers(
    val setTheme: (String) -> Unit,
    val setFollowSystemTheme: (Boolean) -> Unit,
    val setLayoutMode: (LayoutMode) -> Unit,
    val setFont: (String) -> Unit,
    val setDensity: (String) -> Unit,
    val setBackgroundPattern: (String?) -> Unit,
    val setShowBackgroundPattern: (Boolean) -> Unit,
    val setBackgroundAnimation: (Boolean) -> Unit,
    val setEffectIntensity: (Float) -> Unit,
    val setEffectSpeed: (Float) -> Unit,
    val setEffectSize: (Float) -> Unit,
    val setReduceMotion: (Boolean) -> Unit,
    val setTextScale: (Float) -> Unit,
    val reset: () -> Unit,
)

/** The current appearance state the panel renders. */
data class AppearanceState(
    val themeId: String,
    val followSystemTheme: Boolean,
    val layoutMode: LayoutMode,
    val fontId: String,
    val densityId: String,
    val backgroundPatternOverride: String?,
    val showBackgroundPattern: Boolean,
    val backgroundAnimationEnabled: Boolean,
    val effectIntensity: Float,
    val effectSpeed: Float,
    val effectSize: Float,
    val reduceMotion: Boolean,
    val textScale: Float,
)

/**
 * Appearance settings.
 *
 * Layout is the first thing on the page because it is the setting most likely to be changed on
 * a new device.
 */
@Composable
fun AppearanceSettingsPanel(
    state: AppearanceState,
    handlers: AppearanceHandlers,
    themes: List<AphroditeThemeSpec>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            // The trailing space keeps the last control clear of the navigation bar rather than
            // sitting flush against it.
            .padding(start = AphroditeSpacing.lg, end = AphroditeSpacing.lg, top = AphroditeSpacing.lg, bottom = AphroditeSpacing.huge),
        verticalArrangement = Arrangement.spacedBy(AphroditeSpacing.lg),
    ) {
        LayoutSection(state, handlers)
        AphroditeDivider()
        ThemeSection(state, handlers, themes)
        AphroditeDivider()
        TypographySection(state, handlers)
        AphroditeDivider()
        BackgroundSection(state, handlers)
        AphroditeDivider()
        MotionSection(state, handlers)
    }
}

@Composable
private fun LayoutSection(state: AppearanceState, handlers: AppearanceHandlers) {
    Column {
        AphroditeSectionHeader(title = "Layout", icon = AphroditeIcons.Columns)
        Text(
            text = "Choose how the interface arranges itself. Automatic follows this device and " +
                "updates as you rotate, unfold or resize the window.",
            color = aphroditeColors.colorMuted,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
        Box(Modifier.height(AphroditeSpacing.md))

        LayoutMode.entries.forEach { mode ->
            LayoutOptionRow(
                mode = mode,
                selected = state.layoutMode == mode,
                onSelect = { handlers.setLayoutMode(mode) },
            )
            Box(Modifier.height(AphroditeSpacing.sm))
        }
    }
}

@Composable
private fun LayoutOptionRow(
    mode: LayoutMode,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = aphroditeColors
    AphroditeCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onSelect),
        selected = selected,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LayoutPreview(mode = mode, accent = if (selected) colors.accentPrimary else colors.colorMuted)
            Box(Modifier.width(AphroditeSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mode.label,
                    color = if (selected) colors.accentPrimary else colors.fg,
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = layoutDescription(mode),
                    color = colors.colorMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
            Box(Modifier.width(AphroditeSpacing.sm))
            AphroditeCheckbox(checked = selected, onCheckedChange = { onSelect() })
        }
    }
}

private fun layoutDescription(mode: LayoutMode): String = when (mode) {
    LayoutMode.Automatic -> "Match the current device and window"
    LayoutMode.Phone -> "Bottom bar, one pane at a time"
    LayoutMode.Tablet -> "Navigation rail with list and detail side by side"
    LayoutMode.Foldable -> "Adapts around the hinge as you open and close"
    LayoutMode.Desktop -> "Three resizable panes with keyboard shortcuts"
}

/** A tiny schematic showing how each mode arranges its panes. */
@Composable
private fun LayoutPreview(mode: LayoutMode, accent: Color) {
    val colors = aphroditeColors

    Box(
        modifier = Modifier
            .width(54.dp)
            .aspectRatio(if (mode == LayoutMode.Desktop) 1.6f else 1.35f)
            .clip(RoundedCornerShape(AphroditeRadius.xs))
            .background(colors.bg)
            .border(1.dp, colors.border, RoundedCornerShape(AphroditeRadius.xs)),
    ) {
        when (mode) {
            LayoutMode.Automatic -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AphroditeIcons.Zap,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )
            }

            LayoutMode.Phone -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f).background(accent.copy(alpha = 0.12f)))
                Box(Modifier.fillMaxWidth().height(6.dp).background(accent.copy(alpha = 0.7f)))
            }

            LayoutMode.Tablet, LayoutMode.Foldable, LayoutMode.Desktop -> Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(7.dp).fillMaxHeight().background(accent.copy(alpha = 0.7f)))
                Box(Modifier.weight(1f).fillMaxHeight().background(accent.copy(alpha = 0.16f)))
                Box(Modifier.width(1.dp).fillMaxHeight().background(colors.border))
                Box(Modifier.weight(1.3f).fillMaxHeight().background(accent.copy(alpha = 0.3f)))
                if (mode == LayoutMode.Desktop) {
                    Box(Modifier.width(1.dp).fillMaxHeight().background(colors.border))
                    Box(Modifier.weight(1f).fillMaxHeight().background(accent.copy(alpha = 0.45f)))
                }
            }
        }
    }
}

@Composable
private fun ThemeSection(
    state: AppearanceState,
    handlers: AppearanceHandlers,
    themes: List<AphroditeThemeSpec>,
) {
    Column {
        AphroditeSectionHeader(title = "Theme", icon = AphroditeIcons.Palette)
        AphroditeSwitchRow(
            label = "Follow system",
            description = "Use the light or dark theme to match the system setting",
            checked = state.followSystemTheme,
            onCheckedChange = handlers.setFollowSystemTheme,
        )
        Box(Modifier.height(AphroditeSpacing.sm))

        themes.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm),
            ) {
                row.forEach { theme ->
                    ThemeSwatch(
                        theme = theme,
                        selected = state.themeId == theme.id,
                        onSelect = { handlers.setTheme(theme.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
            Box(Modifier.height(AphroditeSpacing.sm))
        }
    }
}

@Composable
private fun ThemeSwatch(
    theme: AphroditeThemeSpec,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = aphroditeColors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AphroditeRadius.sm))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.accentPrimary else colors.border,
                shape = RoundedCornerShape(AphroditeRadius.sm),
            )
            .clickable(role = Role.RadioButton, onClick = onSelect)
            .padding(AphroditeSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .clip(RoundedCornerShape(AphroditeRadius.xs)),
        ) {
            Box(Modifier.weight(1f).height(22.dp).background(theme.bg))
            Box(Modifier.weight(0.6f).height(22.dp).background(theme.panel))
            Box(Modifier.weight(0.4f).height(22.dp).background(theme.red))
        }
        Box(Modifier.height(AphroditeSpacing.xs))
        Text(
            text = theme.displayName,
            color = if (selected) colors.accentPrimary else colors.colorMuted,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun TypographySection(state: AppearanceState, handlers: AppearanceHandlers) {
    Column {
        AphroditeSectionHeader(title = "Typography", icon = AphroditeIcons.Type)

        Text(
            text = "Font",
            color = aphroditeColors.fg,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
        Box(Modifier.height(AphroditeSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm)) {
            AphroditeFont.entries.forEach { font ->
                val selected = state.fontId == font.id
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AphroditeRadius.xs))
                        .background(if (selected) aphroditeColors.accentPrimary.copy(alpha = 0.2f) else aphroditeColors.toggleBg)
                        .border(
                            1.dp,
                            if (selected) aphroditeColors.accentPrimary else aphroditeColors.border,
                            RoundedCornerShape(AphroditeRadius.xs),
                        )
                        .clickable(role = Role.RadioButton) { handlers.setFont(font.id) }
                        .padding(horizontal = AphroditeSpacing.md, vertical = AphroditeSpacing.sm),
                ) {
                    Text(
                        text = font.label,
                        color = if (selected) aphroditeColors.accentPrimary else aphroditeColors.fg,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        Box(Modifier.height(AphroditeSpacing.lg))
        Text(
            text = "Density",
            color = aphroditeColors.fg,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
        Box(Modifier.height(AphroditeSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm)) {
            AphroditeDensity.entries.forEach { density ->
                val selected = state.densityId == density.id
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AphroditeRadius.xs))
                        .background(if (selected) aphroditeColors.accentPrimary.copy(alpha = 0.2f) else aphroditeColors.toggleBg)
                        .border(
                            1.dp,
                            if (selected) aphroditeColors.accentPrimary else aphroditeColors.border,
                            RoundedCornerShape(AphroditeRadius.xs),
                        )
                        .clickable(role = Role.RadioButton) { handlers.setDensity(density.id) }
                        .padding(horizontal = AphroditeSpacing.md, vertical = AphroditeSpacing.sm),
                ) {
                    Text(
                        text = density.label,
                        color = if (selected) aphroditeColors.accentPrimary else aphroditeColors.fg,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        Box(Modifier.height(AphroditeSpacing.lg))
        AphroditeSlider(
            value = state.textScale,
            onValueChange = handlers.setTextScale,
            valueRange = 0.75f..2f,
            label = "Text size",
        )
    }
}

@Composable
private fun BackgroundSection(state: AppearanceState, handlers: AppearanceHandlers) {
    Column {
        AphroditeSectionHeader(title = "Background", icon = AphroditeIcons.Sparkles)

        AphroditeSwitchRow(
            label = "Show background pattern",
            description = "Draw the ambient pattern behind the interface",
            checked = state.showBackgroundPattern,
            onCheckedChange = handlers.setShowBackgroundPattern,
        )

        if (state.showBackgroundPattern) {
            Box(Modifier.height(AphroditeSpacing.sm))
            Text(
                text = "Pattern",
                color = aphroditeColors.fg,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
            Box(Modifier.height(AphroditeSpacing.sm))

            val options = listOf<BackgroundPattern?>(null) + BackgroundPattern.entries
            options.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm),
                ) {
                    row.forEach { pattern ->
                        val selected = state.backgroundPatternOverride == pattern?.id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(AphroditeRadius.xs))
                                .background(if (selected) aphroditeColors.accentPrimary.copy(alpha = 0.2f) else aphroditeColors.toggleBg)
                                .border(
                                    1.dp,
                                    if (selected) aphroditeColors.accentPrimary else aphroditeColors.border,
                                    RoundedCornerShape(AphroditeRadius.xs),
                                )
                                .clickable(role = Role.RadioButton) { handlers.setBackgroundPattern(pattern?.id) }
                                .padding(vertical = AphroditeSpacing.sm),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = pattern?.label ?: "Theme default",
                                color = if (selected) aphroditeColors.accentPrimary else aphroditeColors.fg,
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    }
                    repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                }
                Box(Modifier.height(AphroditeSpacing.sm))
            }

            Box(Modifier.height(AphroditeSpacing.sm))
            AphroditeSlider(
                value = state.effectIntensity,
                onValueChange = handlers.setEffectIntensity,
                valueRange = 0f..1f,
                label = "Intensity",
            )
            Box(Modifier.height(AphroditeSpacing.sm))
            AphroditeSlider(
                value = state.effectSpeed,
                onValueChange = handlers.setEffectSpeed,
                valueRange = 0f..3f,
                label = "Speed",
            )
            Box(Modifier.height(AphroditeSpacing.sm))
            AphroditeSlider(
                value = state.effectSize,
                onValueChange = handlers.setEffectSize,
                valueRange = 0.25f..3f,
                label = "Size",
            )
        }
    }
}

@Composable
private fun MotionSection(state: AppearanceState, handlers: AppearanceHandlers) {
    Column {
        AphroditeSectionHeader(title = "Motion", icon = AphroditeIcons.Zap)
        AphroditeSwitchRow(
            label = "Animate background",
            description = "Turn off to keep the pattern still",
            checked = state.backgroundAnimationEnabled,
            onCheckedChange = handlers.setBackgroundAnimation,
        )
        AphroditeSwitchRow(
            label = "Reduce motion",
            description = "Minimise animation across the app",
            checked = state.reduceMotion,
            onCheckedChange = handlers.setReduceMotion,
        )
    }
}
