package com.aphrodite.core.datastore

import androidx.compose.runtime.Immutable
import com.aphrodite.core.common.layout.LayoutMode

/**
 * Everything the user can change about how the app looks and behaves at the shell level.
 *
 * Feature-specific settings live with their features; this is deliberately limited to the
 * presentation and layout choices that the whole app reads.
 */
@Immutable
data class AppPreferences(
    val layoutMode: LayoutMode = LayoutMode.Automatic,
    val themeId: String = "dark",
    val followSystemTheme: Boolean = false,
    val fontId: String = "mono",
    val densityId: String = "comfortable",
    /** Null means "use whatever the selected theme specifies". */
    val backgroundPatternOverride: String? = null,
    val effectIntensity: Float = 1f,
    val effectSpeed: Float = 1f,
    val effectSize: Float = 1f,
    val backgroundAnimationEnabled: Boolean = true,
    val reduceMotion: Boolean = false,
    /** Multiplier applied on top of the system font scale. */
    val textScale: Float = 1f,
    /** Whether the animated background is drawn at all. */
    val showBackgroundPattern: Boolean = true,
) {
    /** True when backgrounds should animate: enabled here and not disabled by accessibility. */
    val animateBackgrounds: Boolean
        get() = showBackgroundPattern && backgroundAnimationEnabled && !reduceMotion
}
