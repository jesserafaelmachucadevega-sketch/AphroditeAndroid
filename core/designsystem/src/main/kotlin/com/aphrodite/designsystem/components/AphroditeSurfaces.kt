package com.aphrodite.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aphrodite.designsystem.theme.AphroditeRadius
import com.aphrodite.designsystem.theme.AphroditeSpacing
import com.aphrodite.designsystem.theme.aphroditeColors

/** A bordered panel used for cards, list rows and grouped settings. */
@Composable
fun AphroditeCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    borderColor: Color? = null,
    background: Color? = null,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(AphroditeSpacing.md),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val colors = aphroditeColors
    val resolvedBorder = when {
        borderColor != null -> borderColor
        selected -> colors.accentPrimary
        else -> colors.border
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AphroditeRadius.md))
            .background(background ?: colors.panel)
            .border(1.dp, resolvedBorder, RoundedCornerShape(AphroditeRadius.md))
            .then(
                if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier,
            )
            .padding(contentPadding),
        content = content,
    )
}

/**
 * The standard row for every list surface: optional leading slot, a title with optional
 * subtitle, an optional trailing slot and an optional active indicator.
 */
@Composable
fun AphroditeListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    active: Boolean = false,
    accentColor: Color? = null,
    titleColor: Color? = null,
    maxLines: Int = 1,
) {
    val colors = aphroditeColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AphroditeRadius.xs))
            .background(if (active) colors.accentPrimary.copy(alpha = 0.14f) else Color.Transparent)
            .then(
                if (onClick != null) {
                    Modifier.combinedClickable(
                        role = Role.Button,
                        onLongClick = onLongClick,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = AphroditeSpacing.sm, vertical = AphroditeSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (accentColor != null) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(AphroditeRadius.pill))
                    .background(accentColor),
            )
            Box(Modifier.size(AphroditeSpacing.sm))
        }
        if (leading != null) {
            leading()
            Box(Modifier.size(AphroditeSpacing.sm))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (active) colors.accentPrimary else (titleColor ?: colors.fg),
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = colors.colorMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Box(Modifier.size(AphroditeSpacing.sm))
            trailing()
        }
    }
}

/** A collapsible section header used by the sidebar and grouped panels. */
@Composable
fun AphroditeSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    onToggle: (() -> Unit)? = null,
    collapsed: Boolean = false,
) {
    val colors = aphroditeColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onToggle != null) Modifier.clickable(role = Role.Button, onClick = onToggle) else Modifier)
            .padding(horizontal = AphroditeSpacing.sm, vertical = AphroditeSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.colorSubheader,
                modifier = Modifier.size(13.dp),
            )
            Box(Modifier.size(AphroditeSpacing.sm))
        }
        Text(
            text = title.uppercase(),
            color = colors.colorSubheader,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (collapsed) {
            Icon(
                imageVector = com.aphrodite.designsystem.icons.AphroditeIcons.ChevronRight,
                contentDescription = "Expand",
                tint = colors.colorMuted,
                modifier = Modifier.size(12.dp),
            )
        }
        if (trailing != null) trailing()
    }
}

/** The placeholder shown when a surface has nothing to display. */
@Composable
fun AphroditeEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = aphroditeColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(AphroditeSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.colorMuted.copy(alpha = 0.5f),
                modifier = Modifier.size(36.dp),
            )
            Box(Modifier.height(AphroditeSpacing.md))
        }
        Text(
            text = title,
            color = colors.fg,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        )
        if (description != null) {
            Box(Modifier.height(AphroditeSpacing.xs))
            Text(
                text = description,
                color = colors.colorMuted,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
        }
        if (action != null) {
            Box(Modifier.height(AphroditeSpacing.lg))
            action()
        }
    }
}

/** A small count or status pill. */
@Composable
fun AphroditeBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val colors = aphroditeColors
    val background = color ?: colors.accentPrimary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AphroditeRadius.pill))
            .background(background)
            .padding(horizontal = AphroditeSpacing.sm, vertical = 2.dp),
    ) {
        Text(
            text = text,
            color = onAccent(background),
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** A small unread/status dot. */
@Composable
fun AphroditeDot(
    modifier: Modifier = Modifier,
    color: Color? = null,
    size: androidx.compose.ui.unit.Dp = 7.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(AphroditeRadius.pill))
            .background(color ?: aphroditeColors.accentPrimary),
    )
}

/** A themed horizontal rule. */
@Composable
fun AphroditeDivider(modifier: Modifier = Modifier, color: Color? = null) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = color ?: aphroditeColors.border,
    )
}

/** A determinate or indeterminate progress bar. */
@Composable
fun AphroditeProgressBar(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    color: Color? = null,
) {
    val accent = color ?: aphroditeColors.accentPrimary
    if (progress == null) {
        LinearProgressIndicator(
            modifier = modifier.fillMaxWidth().height(2.dp),
            color = accent,
            trackColor = aphroditeColors.toggleBg,
        )
    } else {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = modifier.fillMaxWidth().height(2.dp),
            color = accent,
            trackColor = aphroditeColors.toggleBg,
        )
    }
}

/** A centred spinner with an optional label. */
@Composable
fun AphroditeLoading(
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val colors = aphroditeColors
    Column(
        modifier = modifier.padding(AphroditeSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = colors.accentPrimary,
            strokeWidth = 2.dp,
        )
        if (label != null) {
            Box(Modifier.height(AphroditeSpacing.md))
            Text(
                text = label,
                color = colors.colorMuted,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** A small inline spinner for buttons and toolbars. */
@Composable
fun AphroditeSpinner(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 14.dp,
    color: Color? = null,
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = color ?: aphroditeColors.accentPrimary,
        strokeWidth = 1.5.dp,
    )
}

/** A key/value row used throughout settings and detail panes. */
@Composable
fun AphroditeKeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    val colors = aphroditeColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AphroditeSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = colors.colorMuted,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = valueColor ?: colors.fg,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1.4f),
        )
    }
}
