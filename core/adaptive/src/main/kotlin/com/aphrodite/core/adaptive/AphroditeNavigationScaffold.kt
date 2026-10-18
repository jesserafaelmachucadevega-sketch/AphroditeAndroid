package com.aphrodite.core.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aphrodite.designsystem.components.AphroditeSectionHeader
import com.aphrodite.designsystem.theme.AphroditeRadius
import com.aphrodite.designsystem.theme.AphroditeSpacing
import com.aphrodite.designsystem.theme.aphroditeColors
import kotlinx.coroutines.launch

/** One navigable surface in the app. */
data class AphroditeDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    /** Section this destination belongs to in the drawer. */
    val section: String,
    /** Whether the destination is prominent enough for the compact bottom bar. */
    val inBottomBar: Boolean = false,
    /** Whether the destination needs elevated privileges. */
    val requiresAdmin: Boolean = false,
)

/**
 * The app shell.
 *
 * The chrome is chosen from the resolved [DeviceProfile]:
 * - Phone gets a bottom bar plus a modal drawer holding the full section list.
 * - Tablet, Fold and Desktop get a permanent navigation rail, with the drawer still available
 *   for the complete list.
 *
 * A half-opened foldable switches to a tabletop arrangement, putting [tabletopContent] above
 * the fold and the navigation below it.
 */
@Composable
fun AphroditeNavigationScaffold(
    destinations: List<AphroditeDestination>,
    selectedRoute: String?,
    onNavigate: (String) -> Unit,
    appTitle: String,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    topBar: @Composable (openNavigation: () -> Unit) -> Unit = {},
    drawerFooter: (@Composable () -> Unit)? = null,
    tabletopContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val profile = LocalDeviceProfile.current
    val posture = LocalFoldPosture.current
    val colors = aphroditeColors

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }

    val primaryDestinations = destinations.filter { it.inBottomBar }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            AphroditeDrawer(
                appTitle = appTitle,
                destinations = destinations,
                selectedRoute = selectedRoute,
                onNavigate = { route ->
                    onNavigate(route)
                    scope.launch { drawerState.close() }
                },
                header = header,
                footer = drawerFooter,
            )
        },
    ) {
        if (profile == DeviceProfile.Phone) {
            Scaffold(
                modifier = modifier,
                containerColor = colors.bg,
                topBar = { topBar(openDrawer) },
                bottomBar = {
                    if (primaryDestinations.isNotEmpty()) {
                        NavigationBar(containerColor = colors.panel) {
                            primaryDestinations.forEach { destination ->
                                val selected = destination.route == selectedRoute
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { onNavigate(destination.route) },
                                    icon = {
                                        Icon(
                                            imageVector = destination.icon,
                                            contentDescription = destination.label,
                                            tint = if (selected) colors.accentPrimary else colors.colorMuted,
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = destination.label,
                                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                )
                            }
                            NavigationBarItem(
                                selected = false,
                                onClick = openDrawer,
                                icon = {
                                    Icon(
                                        imageVector = com.aphrodite.designsystem.icons.AphroditeIcons.Menu,
                                        contentDescription = "All sections",
                                        tint = colors.colorMuted,
                                    )
                                },
                                label = {
                                    Text(
                                        text = "More",
                                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                    )
                                },
                            )
                        }
                    }
                },
            ) { insets ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(insets),
                ) {
                    content()
                }
            }
        } else {
            val tabletop = profile == DeviceProfile.Fold && posture == FoldPosture.Tabletop &&
                tabletopContent != null

            Row(modifier = modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = colors.panel,
                    modifier = Modifier
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.systemBars),
                ) {
                    Box(Modifier.size(AphroditeSpacing.lg))
                    if (header != null) {
                        header()
                        Box(Modifier.size(AphroditeSpacing.sm))
                    }
                    destinations.filter { it.inBottomBar }.forEach { destination ->
                        RailItem(destination, selectedRoute, onNavigate)
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .weight(1f),
                    )
                    destinations.filterNot { it.inBottomBar }.take(4).forEach { destination ->
                        RailItem(destination, selectedRoute, onNavigate)
                    }
                    Box(Modifier.size(AphroditeSpacing.sm))
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        // Without this the content runs under the status and gesture bars.
                        // The rail applies its own insets; the content column has to as well,
                        // or the last item in any scrolling panel ends up unreachable.
                        .windowInsetsPadding(WindowInsets.systemBars),
                ) {
                    topBar(openDrawer)
                    if (tabletop) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            tabletopContent?.invoke()
                        }
                        HorizontalDivider(color = colors.border)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(if (tabletop) 1.2f else 1f),
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun RailItem(
    destination: AphroditeDestination,
    selectedRoute: String?,
    onNavigate: (String) -> Unit,
) {
    val colors = aphroditeColors
    val selected = destination.route == selectedRoute
    NavigationRailItem(
        selected = selected,
        onClick = { onNavigate(destination.route) },
        icon = {
            Icon(
                imageVector = destination.icon,
                contentDescription = destination.label,
                tint = if (selected) colors.accentPrimary else colors.colorMuted,
                modifier = Modifier.size(20.dp),
            )
        },
        label = {
            Text(
                text = destination.label,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        alwaysShowLabel = false,
    )
}

/** The full section list, grouped exactly the way the upstream sidebar groups it. */
@Composable
private fun AphroditeDrawer(
    appTitle: String,
    destinations: List<AphroditeDestination>,
    selectedRoute: String?,
    onNavigate: (String) -> Unit,
    header: (@Composable () -> Unit)?,
    footer: (@Composable () -> Unit)?,
) {
    val colors = aphroditeColors
    ModalDrawerSheet(
        drawerContainerColor = colors.panel,
        modifier = Modifier.width(280.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AphroditeSpacing.sm),
        ) {
            Box(Modifier.size(AphroditeSpacing.lg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm),
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(colors.accentPrimary, androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = appTitle.take(1).uppercase(),
                        color = com.aphrodite.designsystem.components.onAccent(colors.accentPrimary),
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    text = appTitle,
                    color = colors.fg,
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                )
            }

            if (header != null) {
                Box(Modifier.size(AphroditeSpacing.md))
                header()
            }

            Box(Modifier.size(AphroditeSpacing.sm))

            val sections = destinations.groupBy { it.section }
            sections.forEach { (sectionName, items) ->
                AphroditeSectionHeader(title = sectionName)
                items.forEach { destination ->
                    val selected = destination.route == selectedRoute
                    NavigationDrawerItem(
                        selected = selected,
                        onClick = { onNavigate(destination.route) },
                        label = {
                            Text(
                                text = destination.label,
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = null,
                                tint = if (selected) colors.accentPrimary else colors.colorMuted,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(AphroditeRadius.xs),
                        colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = colors.accentPrimary.copy(alpha = 0.14f),
                            unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            selectedTextColor = colors.accentPrimary,
                            unselectedTextColor = colors.fg,
                        ),
                    )
                }
            }

            if (footer != null) {
                Box(Modifier.size(AphroditeSpacing.md))
                HorizontalDivider(color = colors.border)
                Box(Modifier.size(AphroditeSpacing.sm))
                footer()
            }
            Box(Modifier.size(AphroditeSpacing.xl))
        }
    }
}
