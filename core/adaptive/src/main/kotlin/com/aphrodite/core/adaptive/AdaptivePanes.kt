package com.aphrodite.core.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aphrodite.designsystem.theme.aphroditeColors

/**
 * Lays out a list and a detail surface.
 *
 * When the profile has room and a detail is selected, both render side by side with the list
 * held at [listWidth]; otherwise exactly one pane is shown. Folding around a hinge is handled
 * by the caller splitting content into [list] and [detail] that already respect
 * [LocalHingeBounds].
 */
@Composable
fun AphroditeTwoPane(
    showDetail: Boolean,
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    listWidth: Dp = 320.dp,
    emptyDetail: (@Composable () -> Unit)? = null,
) {
    val profile = LocalDeviceProfile.current
    val colors = aphroditeColors
    val twoPane = profile.supportsTwoPanes

    if (twoPane) {
        Row(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(listWidth)
                    .fillMaxHeight()
                    .background(colors.panel),
            ) {
                list()
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(colors.border),
            )
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (showDetail) detail() else emptyDetail?.invoke()
            }
        }
    } else if (showDetail) {
        Box(modifier = modifier.fillMaxSize()) { detail() }
    } else {
        Box(modifier = modifier.fillMaxSize()) { list() }
    }
}

/**
 * Lays out up to three panes for desktop-class windows.
 *
 * Below the desktop profile this collapses to [AphroditeTwoPane] behaviour with the secondary
 * pane promoted into the detail slot.
 */
@Composable
fun AphroditeThreePane(
    showSecondary: Boolean,
    showDetail: Boolean,
    primary: @Composable () -> Unit,
    secondary: @Composable () -> Unit,
    detail: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    primaryWidth: Dp = 300.dp,
    secondaryWidth: Dp = 320.dp,
    emptyDetail: (@Composable () -> Unit)? = null,
) {
    val profile = LocalDeviceProfile.current
    val colors = aphroditeColors

    if (profile == DeviceProfile.Desktop) {
        Row(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(primaryWidth)
                    .fillMaxHeight()
                    .background(colors.panel),
            ) {
                primary()
            }
            PaneDivider()
            Box(
                modifier = Modifier
                    .width(secondaryWidth)
                    .fillMaxHeight()
                    .background(colors.panel),
            ) {
                secondary()
            }
            PaneDivider()
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (showDetail) detail() else emptyDetail?.invoke()
            }
        }
    } else {
        AphroditeTwoPane(
            showDetail = showDetail || showSecondary,
            list = secondary,
            detail = detail,
            modifier = modifier,
            listWidth = secondaryWidth,
            emptyDetail = emptyDetail,
        )
    }
}

@Composable
private fun PaneDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(aphroditeColors.border),
    )
}

/** A column that reserves room above the hinge on a half-opened foldable. */
@Composable
fun AphroditeTabletopContent(
    controls: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val posture = LocalFoldPosture.current
    if (posture == FoldPosture.Tabletop) {
        Column(modifier = modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) { content() }
            Box(modifier = Modifier.fillMaxWidth().background(aphroditeColors.panel)) { controls() }
        }
    } else {
        Box(modifier = modifier.fillMaxSize()) { content() }
    }
}
