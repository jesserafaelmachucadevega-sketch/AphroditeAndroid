package com.aphrodite.core.common.layout

/**
 * The layout mode the user chose in Settings -> Appearance -> Layout.
 *
 * [Automatic] follows the device; every other value pins the interface to one form factor so
 * the layout can be overridden on hardware that reports itself differently.
 */
enum class LayoutMode(val id: String, val label: String) {
    Automatic("automatic", "Automatic"),
    Phone("phone", "Phone"),
    Tablet("tablet", "Tablet"),
    Foldable("fold", "Foldable"),
    Desktop("desktop", "Desktop"),
    ;

    companion object {
        fun fromId(id: String?): LayoutMode = entries.firstOrNull { it.id == id } ?: Automatic
    }
}
