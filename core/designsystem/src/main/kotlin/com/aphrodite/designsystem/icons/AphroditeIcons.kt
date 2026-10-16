package com.aphrodite.designsystem.icons

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The Aphrodite icon set.
 *
 * Every icon is a 24x24 stroked vector with round caps and joins, matching the inline SVG
 * convention the upstream interface uses throughout. Icons carry no colour of their own; tint
 * them with `Icon(imageVector = ..., tint = ...)`.
 */
@Suppress("UnusedReceiverParameter")
object AphroditeIcons {

    // ── Sections and navigation ───────────────────────────────────────────────

    val Chat: ImageVector by lazy {
        strokeIcon(
            "Aphrodite.Chat",
            "M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z",
        )
    }

    val Mail: ImageVector by lazy {
        strokeIcon(
            "Aphrodite.Mail",
            "M2 4h20v16H2z",
            "m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7",
        )
    }

    val Notes: ImageVector by lazy {
        strokeIcon(
            "Aphrodite.Notes",
            "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z",
            "M14 2v6h6",
            "M16 13H8",
            "M16 17H8",
            "M10 9H8",
        )
    }

    val Tasks: ImageVector by lazy {
        strokeIcon(
            "Aphrodite.Tasks",
            "M9 11l3 3L22 4",
            "M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11",
        )
    }

    val Calendar: ImageVector by lazy {
        strokeIcon(
            "Aphrodite.Calendar",
            "M3 4h18v18H3z",
            "M16 2v4",
            "M8 2v4",
            "M3 10h18",
        )
    }

    val Memory: ImageVector by lazy {
        strokeIcon(
            "Aphrodite.Memory",
            "M9.5 2A2.5 2.5 0 0 1 12 4.5v15a2.5 2.5 0 0 1-4.96.44 2.5 2.5 0 0 1-2.96-3.08 3 3 0 0 1-.34-5.58 2.5 2.5 0 0 1 1.32-4.24 2.5 2.5 0 0 1 1.98-3A2.5 2.5 0 0 1 9.5 2z",
            "M14.5 2A2.5 2.5 0 0 0 12 4.5v15a2.5 2.5 0 0 0 4.96.44 2.5 2.5 0 0 0 2.96-3.08 3 3 0 0 0 .34-5.58 2.5 2.5 0 0 0-1.32-4.24 2.5 2.5 0 0 0-1.98-3A2.5 2.5 0 0 0 14.5 2z",
        )
    }

    val Skills: ImageVector by lazy {
        strokeIcon(
            "Aphrodite.Skills",
            "m12 3 1.9 4.8L18.7 9.7l-4.8 1.9L12 16.4l-1.9-4.8L5.3 9.7l4.8-1.9z",
            "M18 15l.9 2.2L21 18l-2.1.8L18 21l-.9-2.2L15 18l2.1-.8z",
            "M6 2l.6 1.5L8 4l-1.4.5L6 6l-.6-1.5L4 4l1.4-.5z",
        )
    }

    val Sparkles: ImageVector by lazy {
        strokeIcon(
            "Aphrodite.Sparkles",
            "m12 2 2.2 5.6L19.8 10l-5.6 2.4L12 18l-2.2-5.6L4.2 10l5.6-2.4z",
            "M19 16l.8 2L22 19l-2.2.9L19 22l-.8-2.1L16 19l2.2-1z",
        )
    }

    val Gallery: ImageVector by lazy {
        strokeIcon(
            "Aphrodite.Gallery",
            "M3 3h18v18H3z",
            "M8.5 10a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z",
            "m21 15-5-5L5 21",
        )
    }

    val Library: ImageVector by lazy {
        strokeIcon(
            "Aphrodite.Library",
            "M4 19.5A2.5 2.5 0 0 1 6.5 17H20",
            "M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z",
        )
    }

    val Research: ImageVector by lazy {
        strokeIcon(
            "Aphrodite.Research",
            "M11 3a8 8 0 1 0 0 16 8 8 0 0 0 0-16z",
            "m21 21-4.35-4.35",
            "M11 8v6",
            "M8 11h6",
        )
    }

    val Compare: ImageVector by lazy {
        strokeIcon(
            "Aphrodite.Compare",
            "M12 2v20",
            "M3 6h6",
            "M15 6h6",
            "M6 6v9a3 3 0 0 0 3 3",
            "M18 6v9a3 3 0 0 1-3 3",
        )
    }

    val Cookbook: ImageVector by lazy {
        strokeIcon(
            "Aphrodite.Cookbook",
            "M6 2h12a2 2 0 0 1 2 2v18H6a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z",
            "M8 7h8",
            "M8 11h5",
        )
    }

    val Tools: ImageVector by lazy {
        strokeIcon(
            "Aphrodite.Tools",
            "M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z",
        )
    }

    val Settings: ImageVector by lazy {
        strokeIcon(
            "Aphrodite.Settings",
            "M4 21v-7",
            "M4 10V3",
            "M12 21v-9",
            "M12 8V3",
            "M20 21v-5",
            "M20 12V3",
            "M1 14h6",
            "M9 8h6",
            "M17 16h6",
        )
    }

    val Palette: ImageVector by lazy {
        strokeIcon(
            "Aphrodite.Palette",
            "M12 2a10 10 0 0 0 0 20 2.5 2.5 0 0 0 0-5 2.5 2.5 0 0 1 0-5h2.5A7.5 7.5 0 0 0 22 4.5 10 10 0 0 0 12 2z",
            "M7.5 10.5a1 1 0 1 0 0-2 1 1 0 0 0 0 2z",
            "M12 7.5a1 1 0 1 0 0-2 1 1 0 0 0 0 2z",
            "M16.5 10.5a1 1 0 1 0 0-2 1 1 0 0 0 0 2z",
        )
    }

    val Admin: ImageVector by lazy {
        strokeIcon(
            "Aphrodite.Admin",
            "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z",
        )
    }

    val Home: ImageVector by lazy {
        strokeIcon(
            "Aphrodite.Home",
            "m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
            "M9 22V12h6v10",
        )
    }

    val Menu: ImageVector by lazy {
        strokeIcon("Aphrodite.Menu", "M3 12h18", "M3 6h18", "M3 18h18")
    }

    val MoreVertical: ImageVector by lazy {
        strokeIcon("Aphrodite.MoreVertical", "M12 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2z", "M12 6a1 1 0 1 0 0-2 1 1 0 0 0 0 2z", "M12 20a1 1 0 1 0 0-2 1 1 0 0 0 0 2z")
    }

    val MoreHorizontal: ImageVector by lazy {
        strokeIcon("Aphrodite.MoreHorizontal", "M5 12a1 1 0 1 0 0-2 1 1 0 0 0 0 2z", "M12 12a1 1 0 1 0 0-2 1 1 0 0 0 0 2z", "M19 12a1 1 0 1 0 0-2 1 1 0 0 0 0 2z")
    }

    val ChevronDown: ImageVector by lazy { strokeIcon("Aphrodite.ChevronDown", "m6 9 6 6 6-6") }
    val ChevronUp: ImageVector by lazy { strokeIcon("Aphrodite.ChevronUp", "m18 15-6-6-6 6") }
    val ChevronLeft: ImageVector by lazy { strokeIcon("Aphrodite.ChevronLeft", "m15 18-6-6 6-6") }
    val ChevronRight: ImageVector by lazy { strokeIcon("Aphrodite.ChevronRight", "m9 18 6-6-6-6") }
    val ChevronsLeft: ImageVector by lazy { strokeIcon("Aphrodite.ChevronsLeft", "m11 17-5-5 5-5", "m18 17-5-5 5-5") }
    val ChevronsRight: ImageVector by lazy { strokeIcon("Aphrodite.ChevronsRight", "m13 17 5-5-5-5", "m6 17 5-5-5-5") }

    val ArrowLeft: ImageVector by lazy { strokeIcon("Aphrodite.ArrowLeft", "M19 12H5", "m12 19-7-7 7-7") }
    val ArrowRight: ImageVector by lazy { strokeIcon("Aphrodite.ArrowRight", "M5 12h14", "m12 5 7 7-7 7") }
    val ArrowUp: ImageVector by lazy { strokeIcon("Aphrodite.ArrowUp", "M12 19V5", "m5 12 7-7 7 7") }
    val ArrowDown: ImageVector by lazy { strokeIcon("Aphrodite.ArrowDown", "M12 5v14", "m19 12-7 7-7-7") }

    // ── Actions ───────────────────────────────────────────────────────────────

    val Plus: ImageVector by lazy { strokeIcon("Aphrodite.Plus", "M12 5v14", "M5 12h14") }
    val Minus: ImageVector by lazy { strokeIcon("Aphrodite.Minus", "M5 12h14") }
    val Close: ImageVector by lazy { strokeIcon("Aphrodite.Close", "M18 6 6 18", "m6 6 12 12") }
    val Check: ImageVector by lazy { strokeIcon("Aphrodite.Check", "M20 6 9 17l-5-5") }
    val Search: ImageVector by lazy { strokeIcon("Aphrodite.Search", "M11 3a8 8 0 1 0 0 16 8 8 0 0 0 0-16z", "m21 21-4.35-4.35") }
    val Filter: ImageVector by lazy { strokeIcon("Aphrodite.Filter", "M22 3H2l8 9.46V19l4 2v-8.54z") }
    val Sort: ImageVector by lazy { strokeIcon("Aphrodite.Sort", "M11 5h10", "M11 9h7", "M11 13h4", "M3 17l3 3 3-3", "M6 18V4") }
    val Refresh: ImageVector by lazy {
        strokeIcon("Aphrodite.Refresh", "M21 2v6h-6", "M3 12a9 9 0 0 1 15-6.7L21 8", "M3 22v-6h6", "M21 12a9 9 0 0 1-15 6.7L3 16")
    }
    val Download: ImageVector by lazy {
        strokeIcon("Aphrodite.Download", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "m7 10 5 5 5-5", "M12 15V3")
    }
    val Upload: ImageVector by lazy {
        strokeIcon("Aphrodite.Upload", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "m17 8-5-5-5 5", "M12 3v12")
    }
    val Trash: ImageVector by lazy {
        strokeIcon("Aphrodite.Trash", "M3 6h18", "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6", "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2", "M10 11v6", "M14 11v6")
    }
    val Edit: ImageVector by lazy {
        strokeIcon("Aphrodite.Edit", "M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7", "M18.5 2.5a2.12 2.12 0 0 1 3 3L12 15l-4 1 1-4z")
    }
    val Copy: ImageVector by lazy {
        strokeIcon("Aphrodite.Copy", "M9 2h9a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-9a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z", "M5 6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h9a2 2 0 0 0 2-2")
    }
    val Save: ImageVector by lazy {
        strokeIcon("Aphrodite.Save", "M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z", "M17 21v-8H7v8", "M7 3v5h8")
    }
    val Send: ImageVector by lazy { strokeIcon("Aphrodite.Send", "m22 2-7 20-4-9-9-4z", "M22 2 11 13") }
    val Share: ImageVector by lazy {
        strokeIcon("Aphrodite.Share", "M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8", "m16 6-4-4-4 4", "M12 2v13")
    }
    val ExternalLink: ImageVector by lazy {
        strokeIcon("Aphrodite.ExternalLink", "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6", "M15 3h6v6", "M10 14 21 3")
    }
    val Pin: ImageVector by lazy {
        strokeIcon("Aphrodite.Pin", "M12 17v5", "M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H8a2 2 0 0 0 0 4 1 1 0 0 1 1 1z")
    }
    val Archive: ImageVector by lazy {
        strokeIcon("Aphrodite.Archive", "M21 8v13H3V8", "M1 3h22v5H1z", "M10 12h4")
    }
    val Folder: ImageVector by lazy {
        strokeIcon("Aphrodite.Folder", "M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z")
    }
    val Tag: ImageVector by lazy {
        strokeIcon("Aphrodite.Tag", "M20.59 13.41 13.42 20.58a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z", "M7 7h.01")
    }
    val Bell: ImageVector by lazy {
        strokeIcon("Aphrodite.Bell", "M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9", "M13.73 21a2 2 0 0 1-3.46 0")
    }
    val BellOff: ImageVector by lazy {
        strokeIcon("Aphrodite.BellOff", "M13.73 21a2 2 0 0 1-3.46 0", "M18.63 13A17.9 17.9 0 0 1 18 8", "M6.26 6.26A5.86 5.86 0 0 0 6 8c0 7-3 9-3 9h14", "M18 8a6 6 0 0 0-9.33-5", "m1 1 22 22")
    }
    val Clock: ImageVector by lazy { strokeIcon("Aphrodite.Clock", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z", "M12 7v5l3 2") }
    val History: ImageVector by lazy {
        strokeIcon("Aphrodite.History", "M3 3v5h5", "M3.05 13A9 9 0 1 0 6 5.3L3 8", "M12 7v5l4 2")
    }
    val Undo: ImageVector by lazy { strokeIcon("Aphrodite.Undo", "M3 7v6h6", "M21 17a9 9 0 0 0-9-9 9 9 0 0 0-6 2.3L3 13") }
    val Redo: ImageVector by lazy { strokeIcon("Aphrodite.Redo", "M21 7v6h-6", "M3 17a9 9 0 0 1 9-9 9 9 0 0 1 6 2.3l3 2.7") }

    // ── Status and feedback ───────────────────────────────────────────────────

    val Info: ImageVector by lazy { strokeIcon("Aphrodite.Info", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z", "M12 16v-4", "M12 8h.01") }
    val AlertTriangle: ImageVector by lazy {
        strokeIcon("Aphrodite.AlertTriangle", "m10.29 3.86-8.19 14.14A2 2 0 0 0 3.82 21h16.36a2 2 0 0 0 1.72-3L13.71 3.86a2 2 0 0 0-3.42 0z", "M12 9v4", "M12 17h.01")
    }
    val AlertCircle: ImageVector by lazy { strokeIcon("Aphrodite.AlertCircle", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z", "M12 8v4", "M12 16h.01") }
    val CheckCircle: ImageVector by lazy { strokeIcon("Aphrodite.CheckCircle", "M22 11.08V12a10 10 0 1 1-5.93-9.14", "m22 4-10 10.01-3-3") }
    val HelpCircle: ImageVector by lazy { strokeIcon("Aphrodite.HelpCircle", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z", "M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3", "M12 17h.01") }
    val Loader: ImageVector by lazy {
        strokeIcon("Aphrodite.Loader", "M12 2v4", "M12 18v4", "M4.93 4.93l2.83 2.83", "M16.24 16.24l2.83 2.83", "M2 12h4", "M18 12h4", "M4.93 19.07l2.83-2.83", "M16.24 7.76l2.83-2.83")
    }
    val Zap: ImageVector by lazy { strokeIcon("Aphrodite.Zap", "M13 2 3 14h9l-1 8 10-12h-9z") }
    val Activity: ImageVector by lazy { strokeIcon("Aphrodite.Activity", "M22 12h-4l-3 9L9 3l-3 9H2") }
    val Star: ImageVector by lazy { strokeIcon("Aphrodite.Star", "m12 2 3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01z") }
    val Heart: ImageVector by lazy {
        strokeIcon("Aphrodite.Heart", "M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z")
    }
    val Flag: ImageVector by lazy { strokeIcon("Aphrodite.Flag", "M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z", "M4 22v-7") }

    // ── People and security ───────────────────────────────────────────────────

    val User: ImageVector by lazy { strokeIcon("Aphrodite.User", "M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2", "M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z") }
    val Users: ImageVector by lazy {
        strokeIcon("Aphrodite.Users", "M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2", "M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z", "M23 21v-2a4 4 0 0 0-3-3.87", "M16 3.13a4 4 0 0 1 0 7.75")
    }
    val Lock: ImageVector by lazy { strokeIcon("Aphrodite.Lock", "M5 11h14v11H5z", "M8 11V7a4 4 0 0 1 8 0v4") }
    val Unlock: ImageVector by lazy { strokeIcon("Aphrodite.Unlock", "M5 11h14v11H5z", "M8 11V7a4 4 0 0 1 7.9-.9") }
    val Key: ImageVector by lazy { strokeIcon("Aphrodite.Key", "m15.5 7.5 3 3L22 7l-3-3", "M2 17a5 5 0 0 0 5 5c1.4 0 2.5-1.1 2.5-2.5S8.4 17 7 17a1 1 0 0 1 0-2 3 3 0 1 0-3 3") }
    val Shield: ImageVector by lazy { strokeIcon("Aphrodite.Shield", "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z") }
    val Eye: ImageVector by lazy { strokeIcon("Aphrodite.Eye", "M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z", "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z") }
    val EyeOff: ImageVector by lazy {
        strokeIcon("Aphrodite.EyeOff", "M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94", "M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19", "M14.12 14.12a3 3 0 1 1-4.24-4.24", "m1 1 22 22")
    }
    val LogOut: ImageVector by lazy {
        strokeIcon("Aphrodite.LogOut", "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4", "m16 17 5-5-5-5", "M21 12H9")
    }

    // ── Media and files ───────────────────────────────────────────────────────

    val Paperclip: ImageVector by lazy {
        strokeIcon("Aphrodite.Paperclip", "M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48")
    }
    val Image: ImageVector by lazy {
        strokeIcon("Aphrodite.Image", "M3 3h18v18H3z", "M8.5 10a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z", "m21 15-5-5L5 21")
    }
    val Camera: ImageVector by lazy {
        strokeIcon("Aphrodite.Camera", "M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z", "M12 17a4 4 0 1 0 0-8 4 4 0 0 0 0 8z")
    }
    val Mic: ImageVector by lazy {
        strokeIcon("Aphrodite.Mic", "M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z", "M19 10v2a7 7 0 0 1-14 0v-2", "M12 19v4", "M8 23h8")
    }
    val MicOff: ImageVector by lazy {
        strokeIcon("Aphrodite.MicOff", "M1 1l22 22", "M9 9v3a3 3 0 0 0 5.12 2.12", "M15 9.34V4a3 3 0 0 0-5.94-.6", "M17 16.95A7 7 0 0 1 5 12v-2", "M19 10v2a7 7 0 0 1-.11 1.23", "M12 19v4", "M8 23h8")
    }
    val Volume: ImageVector by lazy {
        strokeIcon("Aphrodite.Volume", "M11 5 6 9H2v6h4l5 4z", "M19.07 4.93a10 10 0 0 1 0 14.14", "M15.54 8.46a5 5 0 0 1 0 7.07")
    }
    val VolumeOff: ImageVector by lazy { strokeIcon("Aphrodite.VolumeOff", "M11 5 6 9H2v6h4l5 4z", "m23 9-6 6", "m17 9 6 6") }
    val Play: ImageVector by lazy { strokeIcon("Aphrodite.Play", "m5 3 14 9-14 9z") }
    val Pause: ImageVector by lazy { strokeIcon("Aphrodite.Pause", "M6 4h4v16H6z", "M14 4h4v16h-4z") }
    val Stop: ImageVector by lazy { strokeIcon("Aphrodite.Stop", "M5 5h14v14H5z") }
    val File: ImageVector by lazy {
        strokeIcon("Aphrodite.File", "M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z", "M13 2v7h7")
    }
    val FilePlus: ImageVector by lazy {
        strokeIcon("Aphrodite.FilePlus", "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z", "M14 2v6h6", "M12 18v-6", "M9 15h6")
    }
    val Clipboard: ImageVector by lazy {
        strokeIcon("Aphrodite.Clipboard", "M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2", "M9 2h6a1 1 0 0 1 1 1v2a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1z")
    }

    // ── Systems and infrastructure ────────────────────────────────────────────

    val Globe: ImageVector by lazy {
        strokeIcon("Aphrodite.Globe", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z", "M3 12h18", "M12 3a15 15 0 0 1 0 18 15 15 0 0 1 0-18z")
    }
    val Server: ImageVector by lazy {
        strokeIcon("Aphrodite.Server", "M2 3h20v7H2z", "M2 14h20v7H2z", "M6 6.5h.01", "M6 17.5h.01")
    }
    val Database: ImageVector by lazy {
        strokeIcon("Aphrodite.Database", "M12 8c4.97 0 9-1.34 9-3s-4.03-3-9-3-9 1.34-9 3 4.03 3 9 3z", "M3 5v14c0 1.66 4.03 3 9 3s9-1.34 9-3V5", "M3 12c0 1.66 4.03 3 9 3s9-1.34 9-3")
    }
    val HardDrive: ImageVector by lazy {
        strokeIcon("Aphrodite.HardDrive", "M22 12H2", "M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z", "M6 16h.01", "M10 16h.01")
    }
    val Cpu: ImageVector by lazy {
        strokeIcon("Aphrodite.Cpu", "M4 4h16v16H4z", "M9 9h6v6H9z", "M9 1v3", "M15 1v3", "M9 20v3", "M15 20v3", "M20 9h3", "M20 14h3", "M1 9h3", "M1 14h3")
    }
    val Terminal: ImageVector by lazy { strokeIcon("Aphrodite.Terminal", "m4 17 6-6-6-6", "M12 19h8") }
    val Code: ImageVector by lazy { strokeIcon("Aphrodite.Code", "m16 18 6-6-6-6", "m8 6-6 6 6 6") }
    val Link: ImageVector by lazy {
        strokeIcon("Aphrodite.Link", "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71", "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71")
    }
    val Wifi: ImageVector by lazy {
        strokeIcon("Aphrodite.Wifi", "M5 12.55a11 11 0 0 1 14.08 0", "M1.42 9a16 16 0 0 1 21.16 0", "M8.53 16.11a6 6 0 0 1 6.95 0", "M12 20h.01")
    }
    val Cloud: ImageVector by lazy { strokeIcon("Aphrodite.Cloud", "M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z") }

    // ── Interface and layout ──────────────────────────────────────────────────

    val Sun: ImageVector by lazy {
        strokeIcon("Aphrodite.Sun", "M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8z", "M12 1v2", "M12 21v2", "M4.22 4.22l1.42 1.42", "M18.36 18.36l1.42 1.42", "M1 12h2", "M21 12h2", "M4.22 19.78l1.42-1.42", "M18.36 5.64l1.42-1.42")
    }
    val Moon: ImageVector by lazy { strokeIcon("Aphrodite.Moon", "M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z") }
    val Monitor: ImageVector by lazy {
        strokeIcon("Aphrodite.Monitor", "M2 3h20v14H2z", "M8 21h8", "M12 17v4")
    }
    val Smartphone: ImageVector by lazy {
        strokeIcon("Aphrodite.Smartphone", "M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z", "M12 18h.01")
    }
    val Tablet: ImageVector by lazy {
        strokeIcon("Aphrodite.Tablet", "M5 2h14a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z", "M12 18h.01")
    }
    val Fold: ImageVector by lazy {
        strokeIcon("Aphrodite.Fold", "M12 2v20", "M3 5h6v14H3z", "M15 5h6v14h-6z")
    }
    val Grid: ImageVector by lazy { strokeIcon("Aphrodite.Grid", "M3 3h7v7H3z", "M14 3h7v7h-7z", "M14 14h7v7h-7z", "M3 14h7v7H3z") }
    val List: ImageVector by lazy { strokeIcon("Aphrodite.List", "M8 6h13", "M8 12h13", "M8 18h13", "M3 6h.01", "M3 12h.01", "M3 18h.01") }
    val Columns: ImageVector by lazy { strokeIcon("Aphrodite.Columns", "M3 3h18v18H3z", "M12 3v18") }
    val Maximize: ImageVector by lazy { strokeIcon("Aphrodite.Maximize", "M8 3H5a2 2 0 0 0-2 2v3", "M21 8V5a2 2 0 0 0-2-2h-3", "M3 16v3a2 2 0 0 0 2 2h3", "M16 21h3a2 2 0 0 0 2-2v-3") }
    val Minimize: ImageVector by lazy { strokeIcon("Aphrodite.Minimize", "M8 3v3a2 2 0 0 1-2 2H3", "M21 8h-3a2 2 0 0 1-2-2V3", "M3 16h3a2 2 0 0 1 2 2v3", "M16 21v-3a2 2 0 0 1 2-2h3") }
    val Sliders: ImageVector by lazy {
        strokeIcon("Aphrodite.Sliders", "M4 21v-7", "M4 10V3", "M12 21v-9", "M12 8V3", "M20 21v-5", "M20 12V3", "M1 14h6", "M9 8h6", "M17 16h6")
    }
    val Type: ImageVector by lazy { strokeIcon("Aphrodite.Type", "M4 7V4h16v3", "M9 20h6", "M12 4v16") }
    val Bold: ImageVector by lazy { strokeIcon("Aphrodite.Bold", "M6 4h8a4 4 0 0 1 0 8H6z", "M6 12h9a4 4 0 0 1 0 8H6z") }
    val Italic: ImageVector by lazy { strokeIcon("Aphrodite.Italic", "M19 4h-9", "M14 20H5", "M15 4 9 20") }
    val Underline: ImageVector by lazy { strokeIcon("Aphrodite.Underline", "M6 3v7a6 6 0 0 0 12 0V3", "M4 21h16") }
    val Quote: ImageVector by lazy {
        strokeIcon("Aphrodite.Quote", "M3 21c3 0 7-1 7-8V5c0-1.25-.76-2-2-2H4c-1.25 0-2 .75-2 1.97V11c0 1.25.75 2 2 2h3", "M15 21c3 0 7-1 7-8V5c0-1.25-.76-2-2-2h-4c-1.25 0-2 .75-2 1.97V11c0 1.25.75 2 2 2h3")
    }
    val ListOrdered: ImageVector by lazy { strokeIcon("Aphrodite.ListOrdered", "M10 6h11", "M10 12h11", "M10 18h11", "M4 6h1v4", "M4 10h2", "M6 18H4c0-1 2-2 2-3s-1-1.5-2-1") }
    val ListUnordered: ImageVector by lazy { strokeIcon("Aphrodite.ListUnordered", "M8 6h13", "M8 12h13", "M8 18h13", "M3 6h.01", "M3 12h.01", "M3 18h.01") }

    // ── Image editing ─────────────────────────────────────────────────────────

    val Crop: ImageVector by lazy { strokeIcon("Aphrodite.Crop", "M6 2v14a2 2 0 0 0 2 2h14", "M18 22V8a2 2 0 0 0-2-2H2") }
    val RotateCw: ImageVector by lazy { strokeIcon("Aphrodite.RotateCw", "M23 4v6h-6", "M20.49 15a9 9 0 1 1-2.12-9.36L23 10") }
    val RotateCcw: ImageVector by lazy { strokeIcon("Aphrodite.RotateCcw", "M1 4v6h6", "M3.51 15a9 9 0 1 0 2.12-9.36L1 10") }
    val FlipHorizontal: ImageVector by lazy { strokeIcon("Aphrodite.FlipHorizontal", "M12 3v18", "M3 7v10l9-5z", "m21 7-9 5 9 5z") }
    val FlipVertical: ImageVector by lazy { strokeIcon("Aphrodite.FlipVertical", "M3 12h18", "M7 3h10l-5 9z", "m7 21 5-9 5 9z") }
    val Brush: ImageVector by lazy {
        strokeIcon("Aphrodite.Brush", "M9.06 11.9l-3.5 3.5a2.85 2.85 0 1 0 4.03 4.03l8.49-8.49a4.5 4.5 0 1 0-6.36-6.36L3.18 12.62", "M14 7l3 3")
    }
    val Eraser: ImageVector by lazy {
        strokeIcon("Aphrodite.Eraser", "m7 21-4.3-4.3a1 1 0 0 1 0-1.4L13.4 4.6a2 2 0 0 1 2.8 0l4.2 4.2a2 2 0 0 1 0 2.8L11 21z", "M22 21H7", "m5 11 8 8")
    }
    val Layers: ImageVector by lazy {
        strokeIcon("Aphrodite.Layers", "m12 2 10 6-10 6L2 8z", "m2 14 10 6 10-6", "m2 11 10 6 10-6")
    }
    val Pipette: ImageVector by lazy {
        strokeIcon("Aphrodite.Pipette", "m2 22 1-1h3l9-9", "M3 21v-3l9-9", "m15 6 3.5-3.5a2.12 2.12 0 0 1 3 3L18 9l3 3-3 3-9-9 3-3z")
    }
    val Contrast: ImageVector by lazy { strokeIcon("Aphrodite.Contrast", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z", "M12 3v18") }
    val Droplet: ImageVector by lazy { strokeIcon("Aphrodite.Droplet", "M12 2.69l5.66 5.66a8 8 0 1 1-11.31 0z") }
    val Move: ImageVector by lazy {
        strokeIcon("Aphrodite.Move", "M5 9l-3 3 3 3", "M9 5l3-3 3 3", "M15 19l-3 3-3-3", "M19 9l3 3-3 3", "M2 12h20", "M12 2v20")
    }
    val Scissors: ImageVector by lazy {
        strokeIcon("Aphrodite.Scissors", "M6 9a3 3 0 1 0 0-6 3 3 0 0 0 0 6z", "M6 21a3 3 0 1 0 0-6 3 3 0 0 0 0 6z", "M20 4 8.12 15.88", "m14.47 14.48 5.53 5.52", "M8.12 8.12 12 12")
    }
    val Magic: ImageVector by lazy {
        strokeIcon("Aphrodite.Magic", "m15 4 5 5", "M3 21 17.5 6.5a2.12 2.12 0 0 0-3-3L0 18", "M9 6l1-3", "M6 9 3 8", "M11 11l-1 3", "M14 8l3 1")
    }
}
