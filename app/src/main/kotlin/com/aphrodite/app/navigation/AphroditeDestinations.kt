package com.aphrodite.app.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.aphrodite.core.adaptive.AphroditeDestination
import com.aphrodite.designsystem.icons.AphroditeIcons

/**
 * Every navigable surface in the app.
 *
 * The upstream interface groups its sidebar into Chats, Email and Tools; the same grouping is
 * reused here so the drawer reads the same way. `inBottomBar` marks the destinations promoted
 * to the compact bottom bar.
 */
object Routes {
    const val CHAT = "chat"
    const val EMAIL = "email"
    const val NOTES = "notes"
    const val TASKS = "tasks"
    const val CALENDAR = "calendar"
    const val CONTACTS = "contacts"
    const val DOCUMENTS = "documents"
    const val GALLERY = "gallery"
    const val MEMORY = "memory"
    const val SKILLS = "skills"
    const val RESEARCH = "research"
    const val COMPARE = "compare"
    const val COOKBOOK = "cookbook"
    const val SEARCH = "search"
    const val TOOLS = "tools"
    const val THEMES = "themes"
    const val SETTINGS = "settings"
    const val ADMIN = "admin"
}

/** The section labels used by the drawer, matching the upstream sidebar. */
object Sections {
    const val CHATS = "Chats"
    const val MAIL = "Email"
    const val TOOLS = "Tools"
    const val SYSTEM = "System"
}

val aphroditeDestinations: List<AphroditeDestination> = listOf(
    AphroditeDestination(
        route = Routes.CHAT,
        label = "Chats",
        icon = AphroditeIcons.Chat,
        section = Sections.CHATS,
        inBottomBar = true,
    ),
    AphroditeDestination(
        route = Routes.NOTES,
        label = "Notes",
        icon = AphroditeIcons.Notes,
        section = Sections.TOOLS,
        inBottomBar = true,
    ),
    AphroditeDestination(
        route = Routes.TASKS,
        label = "Tasks",
        icon = AphroditeIcons.Tasks,
        section = Sections.TOOLS,
    ),
    AphroditeDestination(
        route = Routes.SETTINGS,
        label = "Settings",
        icon = AphroditeIcons.Settings,
        section = Sections.SYSTEM,
        inBottomBar = true,
    ),
    AphroditeDestination(
        route = Routes.THEMES,
        label = "Themes",
        icon = AphroditeIcons.Palette,
        section = Sections.SYSTEM,
    ),
    AphroditeDestination(
        route = Routes.ADMIN,
        label = "Admin",
        icon = AphroditeIcons.Admin,
        section = Sections.SYSTEM,
        requiresAdmin = true,
    ),
)

/** Destinations the given privilege level is allowed to see. */
fun destinationsFor(isAdmin: Boolean): List<AphroditeDestination> =
    aphroditeDestinations.filter { isAdmin || !it.requiresAdmin }

/** Look up the icon for a route, used by deep links and the bottom bar. */
fun iconForRoute(route: String): ImageVector =
    aphroditeDestinations.firstOrNull { it.route == route }?.icon ?: AphroditeIcons.Home
