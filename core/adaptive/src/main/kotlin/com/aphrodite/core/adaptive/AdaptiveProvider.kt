package com.aphrodite.core.adaptive

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import com.aphrodite.core.common.layout.LayoutMode

/** The form factor the interface is currently laid out for. */
val LocalDeviceProfile = staticCompositionLocalOf { DeviceProfile.Phone }

/** How a foldable is positioned right now. */
val LocalFoldPosture = staticCompositionLocalOf { FoldPosture.Flat }

/** The hinge bounds in pixels, when a separating fold is present. */
val LocalHingeBounds = staticCompositionLocalOf<Rect?> { null }

/** True when the window is wide enough to show a list and a detail pane together. */
val LocalSupportsTwoPanes = staticCompositionLocalOf { false }

/**
 * Resolves the device profile from the layout mode and the current window, and provides it to
 * the tree.
 *
 * The result updates live: rotating, unfolding or resizing the window re-resolves and every
 * consumer re-lays-out.
 */
@Composable
fun AphroditeAdaptiveProvider(
    mode: LayoutMode,
    content: @Composable () -> Unit,
) {
    val observed = rememberWindowFacts()
    val facts = observed.facts
    val profile = remember(mode, facts) { resolveDeviceProfile(mode, facts) }

    val hingeBounds = facts.takeIf { it.hasFoldingFeature && it.isSeparating }?.let { observed.hingeBoundsDp }

    CompositionLocalProvider(
        LocalDeviceProfile provides profile,
        LocalFoldPosture provides facts.posture,
        LocalHingeBounds provides hingeBounds,
        LocalSupportsTwoPanes provides profile.supportsTwoPanes,
        content = content,
    )
}

/** Window facts decorated with the resolved hinge rectangle. */
private data class ObservedFacts(
    val facts: WindowFacts,
    val hingeBoundsDp: Rect?,
)

/**
 * Collects the window layout stream and configuration into [WindowFacts].
 *
 * Configuration drives the dp sizes (they already account for the current window in
 * multi-window and desktop modes) and the window layout stream drives fold detection.
 */
@Composable
private fun rememberWindowFacts(): ObservedFacts {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val density = LocalDensity.current

    val layoutInfo: State<WindowLayoutInfo?> = produceState<WindowLayoutInfo?>(null, context) {
        val activity = context.findActivity() ?: return@produceState
        WindowInfoTracker.getOrCreate(context)
            .windowLayoutInfo(activity)
            .collect { info -> value = info }
    }

    val foldingFeature = layoutInfo.value?.displayFeatures
        ?.filterIsInstance<FoldingFeature>()
        ?.firstOrNull()

    val hingeRect = foldingFeature?.bounds?.let { bounds ->
        with(density) {
            Rect(
                left = bounds.left.toDp().value,
                top = bounds.top.toDp().value,
                right = bounds.right.toDp().value,
                bottom = bounds.bottom.toDp().value,
            )
        }
    }

    val posture = when {
        foldingFeature == null -> FoldPosture.Flat
        foldingFeature.state == FoldingFeature.State.HALF_OPENED &&
            foldingFeature.orientation == FoldingFeature.Orientation.HORIZONTAL -> FoldPosture.Tabletop
        foldingFeature.state == FoldingFeature.State.HALF_OPENED -> FoldPosture.Book
        else -> FoldPosture.Flat
    }

    return remember(configuration, foldingFeature, hingeRect) {
        ObservedFacts(
            facts = WindowFacts(
                widthDp = configuration.screenWidthDp,
                heightDp = configuration.screenHeightDp,
                smallestWidthDp = configuration.smallestScreenWidthDp,
                hasFoldingFeature = foldingFeature != null,
                isSeparating = foldingFeature?.isSeparating == true,
                posture = posture,
            ),
            hingeBoundsDp = hingeRect,
        )
    }
}

/** Walks the context chain to the hosting activity. */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** True when the configuration reports a landscape-shaped window. */
fun Configuration.isLandscape(): Boolean = screenWidthDp >= screenHeightDp
