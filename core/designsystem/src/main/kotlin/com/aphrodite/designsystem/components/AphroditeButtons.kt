package com.aphrodite.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aphrodite.designsystem.theme.AphroditeRadius
import com.aphrodite.designsystem.theme.AphroditeSpacing
import com.aphrodite.designsystem.theme.aphroditeColors

/** Visual weight of a button. */
enum class AphroditeButtonStyle { Primary, Secondary, Ghost, Danger }

/** Sizes a button can take, matching the compact controls the upstream UI uses. */
enum class AphroditeButtonSize(val height: Dp, val horizontalPadding: Dp, val contentPadding: PaddingValues) {
    Small(28.dp, AphroditeSpacing.md, PaddingValues(horizontal = AphroditeSpacing.md)),
    Medium(36.dp, AphroditeSpacing.lg, PaddingValues(horizontal = AphroditeSpacing.lg)),
    Large(44.dp, AphroditeSpacing.xl, PaddingValues(horizontal = AphroditeSpacing.xl)),
}

/**
 * The standard Aphrodite button.
 *
 * Uses flat fills and a 1dp border rather than Material elevation, matching the upstream
 * interface's styling.
 */
@Composable
fun AphroditeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AphroditeButtonStyle = AphroditeButtonStyle.Primary,
    size: AphroditeButtonSize = AphroditeButtonSize.Medium,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    fillWidth: Boolean = false,
) {
    val colors = aphroditeColors
    val (background, foreground, borderColor) = when (style) {
        AphroditeButtonStyle.Primary -> Triple(colors.accentPrimary, onAccent(colors.accentPrimary), null)
        AphroditeButtonStyle.Secondary -> Triple(colors.panel, colors.fg, colors.border)
        AphroditeButtonStyle.Ghost -> Triple(Color.Transparent, colors.fg, null)
        AphroditeButtonStyle.Danger -> Triple(colors.colorDanger, Color.White, null)
    }

    val effectiveForeground = if (enabled) foreground else foreground.copy(alpha = 0.45f)
    val effectiveBackground = if (enabled) background else background.copy(alpha = 0.40f)

    Row(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .defaultMinSize(minHeight = size.height)
            .clip(RoundedCornerShape(AphroditeRadius.sm))
            .background(effectiveBackground)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, RoundedCornerShape(AphroditeRadius.sm)) else Modifier)
            .clickable(
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(size.contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(size.height * 0.44f),
                color = effectiveForeground,
                strokeWidth = 2.dp,
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = effectiveForeground,
                modifier = Modifier.size(size.height * 0.48f),
            )
        }
        if (loading || icon != null) {
            Box(Modifier.size(AphroditeSpacing.sm))
        }
        Text(
            text = text,
            color = effectiveForeground,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

/** An icon-only button with a square hit target. */
@Composable
fun AphroditeIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    enabled: Boolean = true,
    size: Dp = 36.dp,
    filled: Boolean = false,
) {
    val colors = aphroditeColors
    val resolvedTint = (tint ?: colors.fg).let { if (enabled) it else it.copy(alpha = 0.4f) }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(AphroditeRadius.sm))
            .then(if (filled) Modifier.background(colors.toggleBg) else Modifier)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = resolvedTint,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/** A compact icon button intended for dense toolbars. */
@Composable
fun AphroditeToolbarIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val colors = aphroditeColors
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(RoundedCornerShape(AphroditeRadius.xs))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint ?: colors.colorMuted,
            modifier = Modifier.size(15.dp),
        )
    }
}

/** Picks a legible foreground for a filled button. */
fun onAccent(color: Color): Color {
    val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    return if (luminance > 0.6f) Color.Black else Color.White
}

/** A bordered container used for toolbars and inline groupings. */
@Composable
fun AphroditeOutlineBox(
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    val colors = aphroditeColors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AphroditeRadius.sm))
            .background(colors.panel)
            .border(border ?: BorderStroke(1.dp, colors.border), RoundedCornerShape(AphroditeRadius.sm)),
    ) {
        content()
    }
}
