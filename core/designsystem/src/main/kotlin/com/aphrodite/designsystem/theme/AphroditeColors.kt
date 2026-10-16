package com.aphrodite.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The complete Aphrodite colour token set.
 *
 * These map one-to-one onto the CSS custom properties the Odysseus UI is built from, so the
 * ported screens keep the same visual identity. Themes only author a handful of base values
 * ([AphroditeThemeSpec]); everything else falls back to the values here, exactly like the
 * stylesheet defaults do.
 */
@Immutable
data class AphroditeColors(
    // Core palette
    val bg: Color,
    val fg: Color,
    val panel: Color,
    val border: Color,
    val red: Color,
    val green: Color,
    val warn: Color,

    // Syntax highlighting
    val hlBg: Color,
    val hlFg: Color,
    val hlKeyword: Color,
    val hlString: Color,
    val hlComment: Color,
    val hlFunction: Color,
    val hlNumber: Color,
    val hlBuiltin: Color,
    val hlVariable: Color,
    val hlParams: Color,

    // Semantic
    val colorError: Color,
    val colorErrorLight: Color,
    val colorSuccess: Color,
    val colorWarning: Color,
    val colorDanger: Color,
    val colorRecording: Color,
    val colorRecordingHover: Color,
    val colorMuted: Color,
    val colorMutedAlt: Color,
    val colorAccent: Color,
    val colorAgentActive: Color,
    val colorBrandBlue: Color,
    val colorBlindOrange: Color,
    val colorSaveGreen: Color,
    val colorLinkHover: Color,
    val colorSubheader: Color,
    val accentWarm: Color,

    // Selects
    val selectBg: Color,
    val selectFg: Color,
    val selectOptionBg: Color,
    val selectOptionFg: Color,
    val selectOptionActiveBg: Color,

    // Chat / chrome surfaces (the web app's "advanced" overridable set)
    val userBubbleBg: Color,
    val aiBubbleBg: Color,
    val bubbleBorder: Color,
    val sidebarBg: Color,
    val sectionAccent: Color,
    val brandColor: Color,
    val inputBg: Color,
    val inputBorder: Color,
    val sendBtnBg: Color,
    val sendBtnHover: Color,
    val codeBg: Color,
    val codeFg: Color,
    val toggleBg: Color,
    val toggleActive: Color,
    val accentPrimary: Color,
    val accentError: Color,
) {
    /** True when this palette is a light theme; drives status-bar icon contrast. */
    val isLight: Boolean
        get() = bg.luminance() > 0.5f
}

private fun Color.luminance(): Float = (0.299f * red + 0.587f * green + 0.114f * blue)

/** Theme-polish overrides a theme author may set, mirroring `THEMES[*].advanced`. */
@Immutable
data class AphroditeThemeAdvanced(
    val sendBtnBg: Color? = null,
    val sendBtnHover: Color? = null,
    val userBubbleBg: Color? = null,
    val aiBubbleBg: Color? = null,
    val inputBg: Color? = null,
    val brandColor: Color? = null,
)

/**
 * A theme is authored with the same five base colours the Odysseus theme registry uses, plus
 * optional chat-surface overrides. Everything else derives from these.
 */
@Immutable
data class AphroditeThemeSpec(
    val id: String,
    val displayName: String,
    val bg: Color,
    val fg: Color,
    val panel: Color,
    val border: Color,
    val red: Color,
    val advanced: AphroditeThemeAdvanced = AphroditeThemeAdvanced(),
    val defaultPattern: BackgroundPattern = BackgroundPattern.None,
    val effectColor: Color? = null,
    val effectIntensity: Float = 1f,
    val effectSize: Float = 1f,
    val effectSpeed: Float = 1f,
    val frosted: Boolean = false,
)

/** Animated background patterns shipped with the app. */
enum class BackgroundPattern(val id: String, val label: String) {
    None("none", "None"),
    Dots("dots", "Dots"),
    Rain("rain", "Rain"),
    Petals("petals", "Petals"),
    Synapse("synapse", "Synapse"),
    Embers("embers", "Embers"),
    Starfield("starfield", "Starfield"),
    StarfieldDepth("starfield-depth", "Starfield Depth"),
    Sparkles("sparkles", "Sparkles"),
    AsciiFireflies("ascii-fireflies", "ASCII Fireflies"),
    PerlinFlow("perlin-flow", "Perlin Flow"),
    ;

    companion object {
        fun fromId(id: String?): BackgroundPattern =
            entries.firstOrNull { it.id == id } ?: None
    }
}

/** Spacing scale, mirroring the padded list/row conventions in the stylesheet. */
@Immutable
object AphroditeSpacing {
    val none: Dp = 0.dp
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val huge: Dp = 48.dp
}

/** Corner radii, mirroring the modal/card/input rounding used across the app. */
@Immutable
object AphroditeRadius {
    val xs: Dp = 3.dp
    val sm: Dp = 6.dp
    val md: Dp = 10.dp
    val lg: Dp = 14.dp
    val xl: Dp = 20.dp
    val pill: Dp = 999.dp
}

/** Interface density, mirroring `density-compact` / `density-spacious` root classes. */
enum class AphroditeDensity(val id: String, val label: String, val fontScale: Float) {
    Compact("compact", "Compact", 13f / 15f),
    Comfortable("comfortable", "Comfortable", 1f),
    Spacious("spacious", "Spacious", 16f / 15f),
    ;

    companion object {
        fun fromId(id: String?): AphroditeDensity =
            entries.firstOrNull { it.id == id } ?: Comfortable
    }
}

/** Font family choices, mirroring `FONT_MAP` in the Odysseus theme module. */
enum class AphroditeFont(
    val id: String,
    val label: String,
    val cssStack: String,
    val bundledAsset: String?,
) {
    Mono("mono", "Fira Code", "'Fira Code', monospace", "fonts/FiraCode-Regular.ttf"),
    Sans("sans", "Sans", "system-ui, sans-serif", null),
    Serif("serif", "Serif", "Georgia, serif", null),
    OpenDyslexic("opendyslexic", "OpenDyslexic", "'OpenDyslexic', sans-serif", "fonts/OpenDyslexic-Regular.ttf"),
    ;

    companion object {
        fun fromId(id: String?): AphroditeFont =
            entries.firstOrNull { it.id == id } ?: Mono
    }
}
