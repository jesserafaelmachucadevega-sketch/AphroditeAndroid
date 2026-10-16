package com.aphrodite.core.adaptive

import com.aphrodite.core.common.layout.LayoutMode

/** The form factor the interface is actually laid out for. */
enum class DeviceProfile(val id: String) {
    Phone("phone"),
    Tablet("tablet"),
    Fold("fold"),
    Desktop("desktop"),
    ;

    /** True when the layout has room for a permanent rail beside the content. */
    val hasRail: Boolean get() = this != Phone

    /** True when list and detail can sit side by side. */
    val supportsTwoPanes: Boolean get() = this != Phone
}

/** How a foldable is currently positioned. */
enum class FoldPosture {
    /** Flat and unfolded. */
    Flat,

    /** Half-opened, like a laptop: content above, controls below. */
    Tabletop,

    /** Half-opened held upright. */
    Book,
}

/** Raw window facts the profile resolver works from. */
data class WindowFacts(
    val widthDp: Int,
    val heightDp: Int,
    val smallestWidthDp: Int,
    val hasFoldingFeature: Boolean,
    val isSeparating: Boolean,
    val posture: FoldPosture,
)

/**
 * Resolves the layout mode and the window facts into the form factor to render.
 *
 * This is deliberately a pure function so every combination can be unit tested without a
 * device.
 */
fun resolveDeviceProfile(mode: LayoutMode, facts: WindowFacts): DeviceProfile = when (mode) {
    LayoutMode.Phone -> DeviceProfile.Phone
    LayoutMode.Tablet -> DeviceProfile.Tablet
    LayoutMode.Foldable -> DeviceProfile.Fold
    LayoutMode.Desktop -> DeviceProfile.Desktop
    LayoutMode.Automatic -> automaticProfile(facts)
}

private fun automaticProfile(facts: WindowFacts): DeviceProfile = when {
    // A separating hinge is the strongest signal: the content has to be split around it.
    facts.hasFoldingFeature && facts.isSeparating -> DeviceProfile.Fold

    // A window this wide, on a device at least this large, has room for three panes — a
    // desktop monitor, a large external display or a maximised freeform window.
    facts.widthDp >= DESKTOP_MIN_WIDTH_DP && facts.smallestWidthDp >= DESKTOP_MIN_SW_DP ->
        DeviceProfile.Desktop

    // Tablet class is judged on smallest width so it does not flip when a tablet is rotated.
    // Using current width here would turn every landscape phone into a tablet.
    facts.smallestWidthDp >= TABLET_MIN_WIDTH_DP -> DeviceProfile.Tablet

    else -> DeviceProfile.Phone
}

/** The usual tablet breakpoint in dp, measured as smallest screen width. */
const val TABLET_MIN_WIDTH_DP = 600

/** A window wide enough for a rail plus three panes. */
const val DESKTOP_MIN_WIDTH_DP = 1000

/** Smallest width a device needs before three panes are offered. */
const val DESKTOP_MIN_SW_DP = 800
