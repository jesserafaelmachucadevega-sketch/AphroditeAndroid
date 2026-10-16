package com.aphrodite.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * The built-in theme registry.
 *
 * Base colours are lifted verbatim from the Odysseus theme module so the ported UI matches the
 * original exactly; every remaining token is derived below the same way the stylesheet derives
 * its defaults.
 */
object AphroditeThemes {

    val dark = AphroditeThemeSpec(
        id = "dark",
        displayName = "Dark",
        bg = Color(0xFF282C34),
        fg = Color(0xFF9CDEF2),
        panel = Color(0xFF111111),
        border = Color(0xFF355A66),
        red = Color(0xFFE06C75),
        defaultPattern = BackgroundPattern.None,
    )

    val light = AphroditeThemeSpec(
        id = "light",
        displayName = "Light",
        bg = Color(0xFFF3F5F7),
        fg = Color(0xFF26313A),
        panel = Color(0xFFFFFFFF),
        border = Color(0xFFCBD3DA),
        red = Color(0xFFB34D5C),
        defaultPattern = BackgroundPattern.Dots,
    )

    val midnight = AphroditeThemeSpec(
        id = "midnight",
        displayName = "Midnight",
        bg = Color(0xFF0D1117),
        fg = Color(0xFFC9D1D9),
        panel = Color(0xFF161B22),
        border = Color(0xFF30363D),
        red = Color(0xFFF85149),
        defaultPattern = BackgroundPattern.Rain,
        effectColor = Color(0xFFFFFFFF),
        effectIntensity = 0.5f,
    )

    val cyberpunk = AphroditeThemeSpec(
        id = "cyberpunk",
        displayName = "Cyberpunk",
        bg = Color(0xFF10121A),
        fg = Color(0xFF9BDDE0),
        panel = Color(0xFF171A26),
        border = Color(0xFF514B78),
        red = Color(0xFFD45AA8),
        defaultPattern = BackgroundPattern.Synapse,
        effectColor = Color(0xFF62C4C7),
        effectIntensity = 0.55f,
    )

    val retrowave = AphroditeThemeSpec(
        id = "retrowave",
        displayName = "Retrowave",
        bg = Color(0xFF1A1A2E),
        fg = Color(0xFFE94560),
        panel = Color(0xFF16213E),
        border = Color(0xFF533483),
        red = Color(0xFFE94560),
        defaultPattern = BackgroundPattern.Embers,
    )

    val forest = AphroditeThemeSpec(
        id = "forest",
        displayName = "Forest",
        bg = Color(0xFF1B2A1B),
        fg = Color(0xFFA8D5A2),
        panel = Color(0xFF142414),
        border = Color(0xFF3D6B3D),
        red = Color(0xFF7CB871),
        defaultPattern = BackgroundPattern.Petals,
    )

    val ocean = AphroditeThemeSpec(
        id = "ocean",
        displayName = "Starfield",
        bg = Color(0xFF0B1A2C),
        fg = Color(0xFF64D2FF),
        panel = Color(0xFF091422),
        border = Color(0xFF1E5074),
        red = Color(0xFF4FACFE),
        defaultPattern = BackgroundPattern.StarfieldDepth,
    )

    val ume = AphroditeThemeSpec(
        id = "ume",
        displayName = "Ume",
        bg = Color(0xFF271D2B),
        fg = Color(0xFFF1D8E9),
        panel = Color(0xFF1D1721),
        border = Color(0xFF66475F),
        red = Color(0xFFD98BB7),
        defaultPattern = BackgroundPattern.Petals,
        effectColor = Color(0xFFF5A0C0),
    )

    val terminal = AphroditeThemeSpec(
        id = "terminal",
        displayName = "Terminal",
        bg = Color(0xFF0B1110),
        fg = Color(0xFFB8E6C1),
        panel = Color(0xFF080D0C),
        border = Color(0xFF28553A),
        red = Color(0xFF6BD38B),
        defaultPattern = BackgroundPattern.AsciiFireflies,
        effectIntensity = 0.8f,
    )

    val organs = AphroditeThemeSpec(
        id = "organs",
        displayName = "Organs",
        bg = Color(0xFF0A0406),
        fg = Color(0xFFEFE1C8),
        panel = Color(0xFF15080A),
        border = Color(0xFF3A1519),
        red = Color(0xFFC83240),
        defaultPattern = BackgroundPattern.Rain,
        effectColor = Color(0xFF451616),
        effectIntensity = 0.75f,
        effectSize = 0.75f,
        effectSpeed = 0.05f,
    )

    val gpt = AphroditeThemeSpec(
        id = "gpt",
        displayName = "GPT",
        bg = Color(0xFF212121),
        fg = Color(0xFFECECEC),
        panel = Color(0xFF171717),
        border = Color(0xFF424242),
        red = Color(0xFF949494),
        advanced = AphroditeThemeAdvanced(
            sendBtnBg = Color(0xFF949494),
            sendBtnHover = Color(0xFF7F7F7F),
            userBubbleBg = Color(0xFF2F2F2F),
            aiBubbleBg = Color(0xFF171717),
            inputBg = Color(0xFF2F2F2F),
            brandColor = Color(0xFFFFFFFF),
        ),
        defaultPattern = BackgroundPattern.None,
    )

    val claude = AphroditeThemeSpec(
        id = "claude",
        displayName = "Claude",
        bg = Color(0xFF1F1E1B),
        fg = Color(0xFFF5F1E8),
        panel = Color(0xFF2B2926),
        border = Color(0xFF514B43),
        red = Color(0xFFD97757),
        defaultPattern = BackgroundPattern.None,
    )

    val cute = AphroditeThemeSpec(
        id = "cute",
        displayName = "Cute",
        bg = Color(0xFFFFF4F7),
        fg = Color(0xFF63394D),
        panel = Color(0xFFFFFAFD),
        border = Color(0xFFEDC7D5),
        red = Color(0xFFD65F88),
        defaultPattern = BackgroundPattern.Sparkles,
        effectColor = Color(0xFFFF8CB8),
    )

    val eclipse = AphroditeThemeSpec(
        id = "eclipse",
        displayName = "Eclipse",
        bg = Color(0xFF17191C),
        fg = Color(0xFFE8E4DC),
        panel = Color(0xFF0F1113),
        border = Color(0xFF3C4248),
        red = Color(0xFF46C2B3),
        defaultPattern = BackgroundPattern.StarfieldDepth,
        effectColor = Color(0xFF46C2B3),
    )

    val porcelain = AphroditeThemeSpec(
        id = "porcelain",
        displayName = "Porcelain",
        bg = Color(0xFFEDF0F2),
        fg = Color(0xFF252A30),
        panel = Color(0xFFFFFFFF),
        border = Color(0xFFC7CDD2),
        red = Color(0xFF3478C9),
        defaultPattern = BackgroundPattern.Dots,
    )

    val arcade = AphroditeThemeSpec(
        id = "arcade",
        displayName = "Arcade",
        bg = Color(0xFF15131D),
        fg = Color(0xFFF4E85C),
        panel = Color(0xFF0B1720),
        border = Color(0xFF305F68),
        red = Color(0xFFFF4F91),
        defaultPattern = BackgroundPattern.Synapse,
        effectColor = Color(0xFFFF4F91),
    )

    val blueprint = AphroditeThemeSpec(
        id = "blueprint",
        displayName = "Blueprint",
        bg = Color(0xFF10263B),
        fg = Color(0xFFE8F1F5),
        panel = Color(0xFF091A29),
        border = Color(0xFF48758D),
        red = Color(0xFFE6B84A),
        defaultPattern = BackgroundPattern.Dots,
        effectColor = Color(0xFFE6B84A),
    )

    val monolith = AphroditeThemeSpec(
        id = "monolith",
        displayName = "Monolith",
        bg = Color(0xFF202020),
        fg = Color(0xFFD8D8D2),
        panel = Color(0xFF121212),
        border = Color(0xFF50504B),
        red = Color(0xFFB6E05C),
        defaultPattern = BackgroundPattern.PerlinFlow,
        effectColor = Color(0xFFB6E05C),
    )

    val yoyo = AphroditeThemeSpec(
        id = "yoyo",
        displayName = "Yoyo",
        bg = Color(0xFF211F23),
        fg = Color(0xFFDFDBD7),
        panel = Color(0xFF141415),
        border = Color(0xFF3E5146),
        red = Color(0xFFE09D6C),
        defaultPattern = BackgroundPattern.AsciiFireflies,
        effectColor = Color(0xFFB8E6C1),
        effectIntensity = 0.8f,
    )

    /** Registry order is the order shown in the theme picker. */
    val all: List<AphroditeThemeSpec> = listOf(
        dark, light, midnight, cyberpunk, retrowave, forest, ocean, ume, terminal, organs,
        gpt, claude, cute, eclipse, porcelain, arcade, blueprint, monolith, yoyo,
    )

    val default: AphroditeThemeSpec = dark

    fun byId(id: String?): AphroditeThemeSpec = all.firstOrNull { it.id == id } ?: default
}

/**
 * Expands a five-colour theme spec into the full token set.
 *
 * The light-family themes swap in the stylesheet's light syntax palette; everything else keeps
 * the dark defaults.
 */
fun AphroditeThemeSpec.toColors(): AphroditeColors {
    val light = bg.luminance() > 0.5f

    val syntax = if (light) {
        SyntaxPalette(
            bg = Color(0xFFF9F9F9),
            fg = Color(0xFF2B2B2B),
            keyword = Color(0xFF7928A1),
            string = Color(0xFF986801),
            comment = Color(0xFF6A737D),
            function = Color(0xFF005CC5),
            number = Color(0xFF986801),
            builtin = Color(0xFF0070A0),
            variable = Color(0xFF383A42),
            params = Color(0xFF4A4F5C),
        )
    } else {
        SyntaxPalette(
            bg = Color(0xFF1E2228),
            fg = fg,
            keyword = Color(0xFFC678DD),
            string = Color(0xFFE5C07B),
            comment = Color(0xFF828997),
            function = Color(0xFF61AFEF),
            number = Color(0xFFD19A66),
            builtin = Color(0xFF56B6C2),
            variable = Color(0xFFABB2BF),
            params = Color(0xFFA8C0D4),
        )
    }

    return AphroditeColors(
        bg = bg,
        fg = fg,
        panel = panel,
        border = border,
        red = red,
        green = Color(0xFF50FA7B),
        warn = Color(0xFFF0AD4E),

        hlBg = syntax.bg,
        hlFg = syntax.fg,
        hlKeyword = syntax.keyword,
        hlString = syntax.string,
        hlComment = syntax.comment,
        hlFunction = syntax.function,
        hlNumber = syntax.number,
        hlBuiltin = syntax.builtin,
        hlVariable = syntax.variable,
        hlParams = syntax.params,

        colorError = Color(0xFFFF4444),
        colorErrorLight = Color(0xFFFF6666),
        colorSuccess = Color(0xFF4CAF50),
        colorWarning = Color(0xFFF0AD4E),
        colorDanger = Color(0xFFC0392B),
        colorRecording = Color(0xFFFF3B30),
        colorRecordingHover = Color(0xFFD63031),
        colorMuted = Color(0xFF888888),
        colorMutedAlt = Color(0xFF6B7280),
        colorAccent = Color(0xFF00AAFF),
        colorAgentActive = Color(0xFF00FF00),
        colorBrandBlue = Color(0xFF3B82F6),
        colorBlindOrange = Color(0xFFFF9800),
        colorSaveGreen = Color(0xFF4CAF50),
        colorLinkHover = Color(0xFF66C7FF),
        colorSubheader = Color(0xFF6B8A94),
        accentWarm = Color(0xFFD19A66),

        selectBg = if (light) Color(0xFFEAEAEA) else bg,
        selectFg = fg,
        selectOptionBg = if (light) panel else panel.blend(bg, 0.26f),
        selectOptionFg = fg,
        selectOptionActiveBg = if (light) panel.blend(red, 0.16f) else panel.blend(red, 0.24f),

        userBubbleBg = advanced.userBubbleBg ?: bg.blend(red, 0.10f),
        aiBubbleBg = advanced.aiBubbleBg ?: panel,
        bubbleBorder = border,
        sidebarBg = panel,
        sectionAccent = red,
        brandColor = advanced.brandColor ?: red,
        inputBg = advanced.inputBg ?: panel,
        inputBorder = border,
        sendBtnBg = advanced.sendBtnBg ?: red,
        sendBtnHover = advanced.sendBtnHover ?: red.blend(Color.White, 0.18f),
        codeBg = syntax.bg,
        codeFg = syntax.fg,
        toggleBg = panel.blend(bg, 0.4f),
        toggleActive = red,
        accentPrimary = red,
        accentError = Color(0xFFFF4444),
    )
}

private class SyntaxPalette(
    val bg: Color,
    val fg: Color,
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val function: Color,
    val number: Color,
    val builtin: Color,
    val variable: Color,
    val params: Color,
)

private fun Color.luminance(): Float = (0.299f * red + 0.587f * green + 0.114f * blue)

/** Linear blend towards [other]; the Compose equivalent of `color-mix(in srgb, a X%, b)`. */
internal fun Color.blend(other: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = red + (other.red - red) * f,
        green = green + (other.green - green) * f,
        blue = blue + (other.blue - blue) * f,
        alpha = alpha + (other.alpha - alpha) * f,
    )
}
