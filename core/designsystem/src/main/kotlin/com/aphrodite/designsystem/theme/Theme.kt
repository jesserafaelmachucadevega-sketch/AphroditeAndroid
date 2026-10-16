package com.aphrodite.designsystem.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** The full Aphrodite token set for the active theme. */
val LocalAphroditeColors = staticCompositionLocalOf<AphroditeColors> {
    error("AphroditeTheme was not applied above this composable")
}

/** The active theme spec, including animated-background configuration. */
val LocalAphroditeThemeSpec = staticCompositionLocalOf<AphroditeThemeSpec> {
    error("AphroditeTheme was not applied above this composable")
}

/** The active interface density. */
val LocalAphroditeDensity = staticCompositionLocalOf { AphroditeDensity.Comfortable }

/**
 * Applies the Aphrodite design system.
 *
 * @param themeId one of [AphroditeThemes]; unknown ids fall back to the default theme.
 * @param font the interface font family.
 * @param density interface density (compact / comfortable / spacious).
 */
@Composable
fun AphroditeTheme(
    themeId: String = AphroditeThemes.default.id,
    font: AphroditeFont = AphroditeFont.Mono,
    density: AphroditeDensity = AphroditeDensity.Comfortable,
    content: @Composable () -> Unit,
) {
    val spec = remember(themeId) { AphroditeThemes.byId(themeId) }
    val colors = remember(spec) { spec.toColors() }
    val family = rememberAphroditeFontFamily(font)
    val typography = remember(family) { aphroditeTypography(family) }
    val scheme = remember(colors) { colors.toMaterial3() }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = colors.isLight
                isAppearanceLightNavigationBars = colors.isLight
            }
        }
    }

    CompositionLocalProvider(
        LocalAphroditeColors provides colors,
        LocalAphroditeThemeSpec provides spec,
        LocalAphroditeDensity provides density,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            content = {
                CompositionLocalProvider(
                    LocalContentColor provides colors.fg,
                    content = content,
                )
            },
        )
    }
}

/** True when the active theme renders on a light background. */
val aphroditeIsLight: Boolean
    @Composable get() = LocalAphroditeColors.current.isLight

/** Convenience accessor mirroring `MaterialTheme.colorScheme` for the custom token set. */
val aphroditeColors: AphroditeColors
    @Composable get() = LocalAphroditeColors.current

/** Maps the Aphrodite tokens onto a Material 3 scheme so stock components stay on-theme. */
fun AphroditeColors.toMaterial3(): ColorScheme {
    val base = if (isLight) lightColorScheme() else darkColorScheme()
    val onPrimary = if (primaryIsLight(accentPrimary)) Color.Black else Color.White
    return base.copy(
        primary = accentPrimary,
        onPrimary = onPrimary,
        primaryContainer = userBubbleBg,
        onPrimaryContainer = fg,
        secondary = colorAccent,
        onSecondary = Color.Black,
        tertiary = accentWarm,
        onTertiary = Color.Black,
        background = bg,
        onBackground = fg,
        surface = panel,
        onSurface = fg,
        surfaceVariant = toggleBg,
        onSurfaceVariant = colorMuted,
        surfaceContainer = sidebarBg,
        surfaceContainerHigh = panel,
        surfaceContainerLow = bg,
        outline = border,
        outlineVariant = border,
        error = colorError,
        onError = Color.White,
        errorContainer = colorDanger,
        onErrorContainer = Color.White,
        inverseSurface = fg,
        inverseOnSurface = bg,
        scrim = Color.Black,
    )
}

private fun primaryIsLight(color: Color): Boolean =
    (0.299f * color.red + 0.587f * color.green + 0.114f * color.blue) > 0.6f

/** Resolves the effective default theme id, honouring the system setting when asked to follow it. */
@Composable
fun resolveThemeId(configured: String, followSystem: Boolean): String {
    if (!followSystem) return configured
    return if (isSystemInDarkTheme()) AphroditeThemes.dark.id else AphroditeThemes.light.id
}
