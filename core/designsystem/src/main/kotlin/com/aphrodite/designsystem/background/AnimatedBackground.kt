package com.aphrodite.designsystem.background

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import com.aphrodite.designsystem.theme.AphroditeColors
import com.aphrodite.designsystem.theme.BackgroundPattern
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Draws the ambient background for a theme.
 *
 * Patterns are drawn behind content on a full-size canvas. Animation is driven by the frame
 * clock and can be switched off entirely for reduced-motion users, in which case each effect
 * still renders as a static composition rather than disappearing.
 *
 * @param pattern which effect to draw.
 * @param colors the active theme tokens.
 * @param intensity overall opacity multiplier (the theme's `effectIntensity`).
 * @param sizeScale particle scale multiplier (the theme's `effectSize`).
 * @param speed animation speed multiplier (the theme's `effectSpeed`).
 */
@Composable
fun AnimatedBackground(
    pattern: BackgroundPattern,
    colors: AphroditeColors,
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    sizeScale: Float = 1f,
    speed: Float = 1f,
    effectColor: Color = colors.fg,
    animate: Boolean = true,
) {
    if (pattern == BackgroundPattern.None) return

    var elapsed by remember { mutableLongStateOf(0L) }
    if (animate) {
        LaunchedFrameClock { millis -> elapsed = millis }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val state = BackgroundState(
            timeSeconds = elapsed / 1000f,
            intensity = intensity.coerceIn(0f, 1f),
            sizeScale = sizeScale,
            speed = speed,
            effectColor = effectColor,
            baseColor = colors.bg,
            accentColor = colors.red,
        )
        when (pattern) {
            BackgroundPattern.None -> Unit
            BackgroundPattern.Dots -> drawDots(state)
            BackgroundPattern.Rain -> drawRain(state)
            BackgroundPattern.Petals -> drawPetals(state)
            BackgroundPattern.Synapse -> drawSynapse(state)
            BackgroundPattern.Embers -> drawEmbers(state)
            BackgroundPattern.Starfield -> drawStarfield(state, depth = false)
            BackgroundPattern.StarfieldDepth -> drawStarfield(state, depth = true)
            BackgroundPattern.Sparkles -> drawSparkles(state)
            BackgroundPattern.AsciiFireflies -> drawAsciiFireflies(state)
            BackgroundPattern.PerlinFlow -> drawPerlinFlow(state)
        }
    }
}

@Composable
private fun LaunchedFrameClock(onFrame: (Long) -> Unit) {
    LaunchedEffect(Unit) {
        while (true) {
            withInfiniteAnimationFrameMillis { millis -> onFrame(millis) }
        }
    }
}

/** Everything a pattern needs, pre-resolved so the draw functions stay pure. */
private class BackgroundState(
    val timeSeconds: Float,
    val intensity: Float,
    val sizeScale: Float,
    val speed: Float,
    val effectColor: Color,
    val baseColor: Color,
    val accentColor: Color,
) {
    val t: Float get() = timeSeconds * speed
    fun alpha(base: Float): Float = (base * intensity).coerceIn(0f, 1f)
    fun scaled(value: Float): Float = value * sizeScale
}

// ── Effects ──────────────────────────────────────────────────────────────────

private fun DrawScope.drawDots(s: BackgroundState) {
    val spacing = s.scaled(28f)
    if (spacing <= 0f) return
    val radius = s.scaled(1.6f)
    val color = s.effectColor.copy(alpha = s.alpha(0.18f))
    var y = spacing / 2f
    while (y < size.height) {
        var x = spacing / 2f
        while (x < size.width) {
            drawCircle(color = color, radius = radius, center = Offset(x, y))
            x += spacing
        }
        y += spacing
    }
}

private fun DrawScope.drawRain(s: BackgroundState) {
    val drops = 90
    val color = s.effectColor
    val length = s.scaled(18f)
    repeat(drops) { i ->
        val r = Random(i)
        val x = r.nextFloat() * size.width
        val phase = r.nextFloat()
        val y = ((phase + s.t * (0.6f + r.nextFloat() * 0.8f)) % 1f) * (size.height + length) - length
        val a = s.alpha(0.10f + r.nextFloat() * 0.18f)
        drawLine(
            color = color.copy(alpha = a),
            start = Offset(x, y),
            end = Offset(x - s.scaled(2f), y + length),
            strokeWidth = s.scaled(1.2f),
        )
    }
}

private fun DrawScope.drawPetals(s: BackgroundState) {
    val count = 26
    val color = s.effectColor
    repeat(count) { i ->
        val r = Random(i * 31)
        val baseX = r.nextFloat() * size.width
        val bob = sin((s.t * 0.5f + r.nextFloat() * 6f).toDouble()).toFloat()
        val x = baseX + bob * s.scaled(30f)
        val y = ((r.nextFloat() + s.t * (0.03f + r.nextFloat() * 0.05f)) % 1f) * size.height
        val petalW = s.scaled(4f + r.nextFloat() * 5f)
        val petalH = petalW * 1.7f
        val rotation = (s.t * 20f + r.nextFloat() * 360f) % 360f
        val a = s.alpha(0.10f + r.nextFloat() * 0.16f)
        rotate(degrees = rotation, pivot = Offset(x, y)) {
            drawOval(
                color = color.copy(alpha = a),
                topLeft = Offset(x - petalW / 2f, y - petalH / 2f),
                size = Size(petalW, petalH),
            )
        }
    }
}

private fun DrawScope.drawSynapse(s: BackgroundState) {
    val nodes = 22
    val color = s.effectColor
    val points = ArrayList<Offset>(nodes)
    repeat(nodes) { i ->
        val r = Random(i * 17)
        val drift = sin((s.t * 0.25f + i).toDouble()).toFloat()
        points += Offset(
            x = (r.nextFloat() + drift * 0.04f).mod(1f) * size.width,
            y = (r.nextFloat() + cos((s.t * 0.2f + i * 0.7f).toDouble()).toFloat() * 0.04f).mod(1f) * size.height,
        )
    }
    val maxLink = s.scaled(170f)
    for (i in 0 until nodes) {
        for (j in i + 1 until nodes) {
            val d = (points[i] - points[j]).getDistance()
            if (d < maxLink) {
                val a = s.alpha(0.12f * (1f - d / maxLink))
                drawLine(color = color.copy(alpha = a), start = points[i], end = points[j], strokeWidth = 1f)
            }
        }
    }
    points.forEachIndexed { i, p ->
        val pulse = 0.5f + 0.5f * sin((s.t * 1.6f + i).toDouble()).toFloat()
        drawCircle(color = color.copy(alpha = s.alpha(0.25f + pulse * 0.3f)), radius = s.scaled(1.8f + pulse), center = p)
    }
}

private fun DrawScope.drawEmbers(s: BackgroundState) {
    val count = 60
    repeat(count) { i ->
        val r = Random(i * 13)
        val lateral = sin((s.t * 0.6f + r.nextFloat() * 8f).toDouble()).toFloat()
        val x = (r.nextFloat() + lateral * 0.03f).mod(1f) * size.width
        val y = (1f - ((r.nextFloat() + s.t * (0.05f + r.nextFloat() * 0.1f)) % 1f)) * size.height
        val fade = ((r.nextFloat() + s.t * 0.1f) % 1f)
        val a = s.alpha(0.35f * (1f - fade))
        drawCircle(color = s.effectColor.copy(alpha = a), radius = s.scaled(1f + r.nextFloat() * 2f), center = Offset(x, y))
    }
}

private fun DrawScope.drawStarfield(s: BackgroundState, depth: Boolean) {
    val layers = if (depth) 3 else 1
    repeat(layers) { layer ->
        val count = if (depth) 70 + layer * 40 else 120
        val parallax = if (depth) 0.35f + layer * 0.5f else 1f
        repeat(count) { i ->
            val r = Random(layer * 997 + i)
            val twinkle = 0.55f + 0.45f * sin((s.t * 1.2f * parallax + i).toDouble()).toFloat()
            val x = ((r.nextFloat() + s.t * 0.01f * parallax) % 1f) * size.width
            val y = r.nextFloat() * size.height
            val radius = s.scaled(0.6f + r.nextFloat() * (if (depth) 1.6f else 1.2f)) * parallax
            drawCircle(color = s.effectColor.copy(alpha = s.alpha(0.5f * twinkle)), radius = radius, center = Offset(x, y))
        }
    }
}

private fun DrawScope.drawSparkles(s: BackgroundState) {
    val count = 48
    repeat(count) { i ->
        val r = Random(i * 41)
        val x = r.nextFloat() * size.width
        val y = r.nextFloat() * size.height
        val cycle = ((s.t * 0.7f + r.nextFloat() * 3f) % 1f)
        // Triangular envelope so each sparkle fades in and out.
        val brightness = 1f - abs(cycle * 2f - 1f)
        if (brightness <= 0.02f) return@repeat
        val arm = s.scaled(2f + r.nextFloat() * 5f) * brightness
        val a = s.alpha(0.65f * brightness)
        val c = s.effectColor.copy(alpha = a)
        drawLine(c, Offset(x - arm, y), Offset(x + arm, y), strokeWidth = s.scaled(1.1f))
        drawLine(c, Offset(x, y - arm), Offset(x, y + arm), strokeWidth = s.scaled(1.1f))
    }
}

private const val FIREFLY_GLYPHS = "·.:*+oO@"

private fun DrawScope.drawAsciiFireflies(s: BackgroundState) {
    val count = 40
    val glyphWidth = s.scaled(10f)
    val glyphHeight = s.scaled(16f)
    repeat(count) { i ->
        val r = Random(i * 7)
        val x = r.nextFloat() * size.width
        val y = ((r.nextFloat() + s.t * (0.01f + r.nextFloat() * 0.02f)) % 1f) * size.height
        val cycle = ((s.t * 0.5f + r.nextFloat() * 4f) % 1f)
        val brightness = 1f - abs(cycle * 2f - 1f)
        val glyphIndex = (brightness * (FIREFLY_GLYPHS.length - 1)).toInt().coerceIn(0, FIREFLY_GLYPHS.length - 1)
        val glyph = FIREFLY_GLYPHS[glyphIndex]
        val a = s.alpha(0.55f * brightness)
        if (a <= 0.02f) return@repeat
        translate(left = x, top = y) {
            drawAsciiGlyph(glyph, s.effectColor.copy(alpha = a), glyphWidth, glyphHeight)
        }
    }
}

/** Draws a single monospace glyph as strokes so no font resource is required. */
private fun DrawScope.drawAsciiGlyph(glyph: Char, color: Color, w: Float, h: Float) {
    val stroke = (w * 0.14f).coerceAtLeast(1f)
    fun seg(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = stroke)

    when (glyph) {
        '·' -> drawCircle(color, radius = stroke * 1.2f, center = Offset(w / 2f, h / 2f))
        ':' -> {
            drawCircle(color, radius = stroke, center = Offset(w / 2f, h * 0.32f))
            drawCircle(color, radius = stroke, center = Offset(w / 2f, h * 0.68f))
        }
        '.' -> drawCircle(color, radius = stroke, center = Offset(w / 2f, h * 0.8f))
        '*' -> {
            seg(w / 2f, h * 0.2f, w / 2f, h * 0.8f)
            seg(w * 0.2f, h / 2f, w * 0.8f, h / 2f)
            seg(w * 0.26f, h * 0.26f, w * 0.74f, h * 0.74f)
            seg(w * 0.74f, h * 0.26f, w * 0.26f, h * 0.74f)
        }
        '+' -> {
            seg(w / 2f, h * 0.15f, w / 2f, h * 0.85f)
            seg(w * 0.15f, h / 2f, w * 0.85f, h / 2f)
        }
        'o' -> drawCircle(color, radius = w * 0.32f, center = Offset(w / 2f, h / 2f), style = Stroke(width = stroke))
        'O' -> drawCircle(color, radius = w * 0.42f, center = Offset(w / 2f, h / 2f), style = Stroke(width = stroke))
        '@' -> {
            drawCircle(color, radius = w * 0.42f, center = Offset(w / 2f, h / 2f), style = Stroke(width = stroke))
            drawCircle(color, radius = w * 0.14f, center = Offset(w / 2f, h / 2f))
        }
        else -> drawCircle(color, radius = stroke, center = Offset(w / 2f, h / 2f))
    }
}

private fun DrawScope.drawPerlinFlow(s: BackgroundState) {
    val color = s.effectColor
    val step = s.scaled(34f)
    if (step <= 0f) return
    val segLength = step * 0.55f
    var y = step / 2f
    while (y < size.height) {
        var x = step / 2f
        while (x < size.width) {
            val n = valueNoise(x * 0.008f, y * 0.008f, s.t * 0.12f)
            val angle = n * PI.toFloat() * 2f
            val dx = cos(angle) * segLength
            val dy = sin(angle) * segLength
            // Fade lines whose angle is near-vertical so the field reads as flow, not noise.
            val a = s.alpha(0.10f + 0.16f * abs(cos(angle)))
            drawLine(
                color = color.copy(alpha = a),
                start = Offset(x - dx / 2f, y - dy / 2f),
                end = Offset(x + dx / 2f, y + dy / 2f),
                strokeWidth = s.scaled(1.1f),
            )
            x += step
        }
        y += step
    }
}

// ── Value noise ──────────────────────────────────────────────────────────────

private fun hash(x: Int, y: Int, z: Int): Float {
    var h = x * 374761393 + y * 668265263 + z * 2246822519.toInt()
    h = (h xor (h shr 13)) * 1274126177
    h = h xor (h shr 16)
    return (h and 0x7fffffff) / 0x7fffffff.toFloat()
}

private fun smooth(t: Float): Float = t * t * (3f - 2f * t)

private fun valueNoise(x: Float, y: Float, z: Float): Float {
    val xi = floor(x).toInt()
    val yi = floor(y).toInt()
    val zi = floor(z).toInt()
    val xf = smooth(x - xi)
    val yf = smooth(y - yi)
    val zf = smooth(z - zi)

    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    val c000 = hash(xi, yi, zi)
    val c100 = hash(xi + 1, yi, zi)
    val c010 = hash(xi, yi + 1, zi)
    val c110 = hash(xi + 1, yi + 1, zi)
    val c001 = hash(xi, yi, zi + 1)
    val c101 = hash(xi + 1, yi, zi + 1)
    val c011 = hash(xi, yi + 1, zi + 1)
    val c111 = hash(xi + 1, yi + 1, zi + 1)

    val x00 = lerp(c000, c100, xf)
    val x10 = lerp(c010, c110, xf)
    val x01 = lerp(c001, c101, xf)
    val x11 = lerp(c011, c111, xf)

    return lerp(lerp(x00, x10, yf), lerp(x01, x11, yf), zf)
}
