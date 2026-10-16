package com.aphrodite.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aphrodite.designsystem.icons.AphroditeIcons
import com.aphrodite.designsystem.theme.AphroditeSpacing
import com.aphrodite.designsystem.theme.aphroditeColors

/** One action in the top bar's trailing group. */
data class AphroditeTopBarAction(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
    val tint: androidx.compose.ui.graphics.Color? = null,
)

/**
 * The app's top bar.
 *
 * Shows a navigation affordance only when the layout needs one: on compact layouts the drawer
 * has to be reachable from here, while rail layouts already expose navigation permanently.
 */
@Composable
fun AphroditeTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showMenuButton: Boolean = false,
    onMenuClick: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: List<AphroditeTopBarAction> = emptyList(),
    bottom: (@Composable () -> Unit)? = null,
) {
    val colors = aphroditeColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.panel),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = AphroditeSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.xs),
        ) {
            when {
                onBack != null -> AphroditeIconButton(
                    icon = AphroditeIcons.ArrowLeft,
                    contentDescription = "Back",
                    onClick = onBack,
                    size = 32.dp,
                )
                showMenuButton -> AphroditeIconButton(
                    icon = AphroditeIcons.Menu,
                    contentDescription = "Open navigation",
                    onClick = onMenuClick,
                    size = 32.dp,
                )
            }

            if (leading != null) leading()

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = colors.fg,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = colors.colorMuted,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            actions.forEach { action ->
                AphroditeIconButton(
                    icon = action.icon,
                    contentDescription = action.contentDescription,
                    onClick = action.onClick,
                    tint = action.tint,
                    size = 32.dp,
                )
            }
        }
        if (bottom != null) bottom()
        AphroditeDivider()
    }
}

/** A compact status pill used in top bars, e.g. the active model or connection state. */
@Composable
fun AphroditeStatusPill(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = aphroditeColors
    val tint = color ?: colors.colorMuted
    Row(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            } else {
                Modifier
            },
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AphroditeDot(color = tint, size = 6.dp)
        Box(Modifier.size(AphroditeSpacing.sm))
        Text(
            text = text,
            color = tint,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
