package com.aphrodite.designsystem.theme

import android.content.res.AssetManager
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Resolves a theme font choice to a Compose [FontFamily].
 *
 * Bundled faces are loaded from `assets/fonts`; when a face is absent on install the platform
 * family is used instead so text never fails to render.
 */
@Composable
fun rememberAphroditeFontFamily(font: AphroditeFont): FontFamily {
    val assets = LocalContext.current.assets
    return remember(font) {
        when (font) {
            AphroditeFont.Sans -> FontFamily.SansSerif
            AphroditeFont.Serif -> FontFamily.Serif
            AphroditeFont.Mono -> bundledOr(assets, "fonts/FiraCode-Regular.ttf", FontFamily.Monospace)
            AphroditeFont.OpenDyslexic ->
                bundledOr(assets, "fonts/OpenDyslexic-Regular.ttf", FontFamily.SansSerif)
        }
    }
}

private fun bundledOr(assets: AssetManager, path: String, fallback: FontFamily): FontFamily {
    val present = runCatching { assets.open(path).close() }.isSuccess
    if (!present) return fallback
    return runCatching { FontFamily(Font(path, assets)) }.getOrDefault(fallback)
}

/** The app's type scale, matching the sizes the stylesheet uses for chrome, body and code. */
fun aphroditeTypography(family: FontFamily): Typography = Typography(
    displayLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Light, fontSize = 40.sp, lineHeight = 48.sp),
    displayMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Light, fontSize = 32.sp, lineHeight = 40.sp),
    displaySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 26.sp, lineHeight = 34.sp),
    headlineLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    headlineSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 17.sp),
    bodyLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Light, fontSize = 11.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 15.sp),
    labelSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 10.sp, lineHeight = 14.sp),
)

/** Monospace style used for code blocks, tool output and terminal surfaces. */
val CodeTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 11.sp,
    lineHeight = 16.sp,
)
