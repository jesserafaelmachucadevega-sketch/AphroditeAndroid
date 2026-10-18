package com.aphrodite.app.navigation

import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aphrodite.app.AppViewModel
import com.aphrodite.app.BuildConfig
import com.aphrodite.core.adaptive.AphroditeNavigationScaffold
import com.aphrodite.core.adaptive.DeviceProfile
import com.aphrodite.core.adaptive.LocalDeviceProfile
import com.aphrodite.core.datastore.AppPreferences
import com.aphrodite.designsystem.components.AphroditeTopBar
import com.aphrodite.designsystem.theme.AphroditeThemes
import com.aphrodite.feature.settings.AppearanceHandlers
import com.aphrodite.feature.settings.AppearanceState
import com.aphrodite.feature.settings.BuildInfo
import com.aphrodite.feature.chat.ChatScreen
import com.aphrodite.feature.chat.ChatViewModel
import com.aphrodite.feature.settings.SettingsCategory
import com.aphrodite.feature.settings.SettingsScreen
import com.aphrodite.feature.settings.SettingsViewModel
import com.aphrodite.feature.settings.ServicesHandlers

/** Hosts the navigation graph inside the adaptive shell. */
@Composable
fun AphroditeNavHost(
    viewModel: AppViewModel,
    chatViewModel: ChatViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val preferences by viewModel.state.collectAsStateWithLifecycle()
    val chatState by chatViewModel.state.collectAsStateWithLifecycle()
    val servicesState by settingsViewModel.services.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.SETTINGS
    val profile = LocalDeviceProfile.current
    val context = LocalContext.current

    // Providers first: on a fresh install, configuring one is the only thing that matters.
    var settingsCategory by rememberSaveable { mutableStateOf(SettingsCategory.Services) }

    val destinations = remember { destinationsFor(isAdmin = true) }
    val buildInfo = remember(context) { collectBuildInfo() }

    AphroditeNavigationScaffold(
        destinations = destinations,
        selectedRoute = currentRoute,
        onNavigate = { route ->
            // Themes and Admin are shortcuts into Settings with a category preselected, not
            // separate screens. Rendering a second copy of the settings surface meant the tab
            // strip there could not actually change category, and it missed the providers
            // wiring entirely.
            val target = when (route) {
                Routes.THEMES -> {
                    settingsCategory = SettingsCategory.Appearance
                    Routes.SETTINGS
                }
                Routes.ADMIN -> {
                    settingsCategory = SettingsCategory.About
                    Routes.SETTINGS
                }
                else -> route
            }
            if (target != currentRoute) {
                navController.navigate(target) {
                    launchSingleTop = true
                    restoreState = true
                }
            }
        },
        appTitle = "Aphrodite",
        topBar = { openNavigation ->
            AphroditeTopBar(
                title = titleFor(currentRoute),
                showMenuButton = profile == DeviceProfile.Phone,
                onMenuClick = openNavigation,
            )
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.CHAT,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Routes.CHAT) {
                ChatScreen(
                    state = chatState,
                    onSelectSession = chatViewModel::selectSession,
                    onNewSession = chatViewModel::newSession,
                    onDeleteSession = chatViewModel::deleteSession,
                    onSearchChange = chatViewModel::onSearchChange,
                    onInputChange = chatViewModel::onInputChange,
                    onSend = chatViewModel::send,
                    onStop = chatViewModel::stopStreaming,
                    onDismissError = chatViewModel::dismissError,
                    onOpenProviders = {
                        settingsCategory = SettingsCategory.Services
                        navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                    },
                    onToggleModelPicker = chatViewModel::toggleModelPicker,
                    onModelQueryChange = chatViewModel::onModelQueryChange,
                    onSelectModel = chatViewModel::selectModel,
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    state = preferences.toAppearanceState(),
                    handlers = viewModel.toAppearanceHandlers(),
                    themes = AphroditeThemes.all,
                    selected = settingsCategory,
                    onSelect = { settingsCategory = it },
                    buildInfo = buildInfo,
                    servicesState = servicesState,
                    servicesHandlers = settingsViewModel.toServicesHandlers(),
                )
            }
        }
    }
}

/** Wires the providers panel's callbacks to its view model. */
private fun SettingsViewModel.toServicesHandlers(): ServicesHandlers = ServicesHandlers(
    onStartAdd = ::startAdd,
    onStartEdit = ::startEdit,
    onDraftChange = ::onDraftChange,
    onCancelEdit = ::cancelEdit,
    onSave = ::save,
    onDelete = ::delete,
    onToggleEnabled = ::toggleEnabled,
    onRefreshModels = ::refreshModels,
    onSelectModels = ::selectModels,
    onToggleFavourite = ::toggleFavourite,
    onModelQueryChange = ::onModelQueryChange,
)

private fun titleFor(route: String): String = when (route) {
    Routes.CHAT -> "Chats"
    Routes.SETTINGS -> "Settings"
    Routes.THEMES -> "Themes"
    Routes.ADMIN -> "Admin"
    else -> "Aphrodite"
}

private fun collectBuildInfo(): BuildInfo = BuildInfo(
    versionName = BuildConfig.VERSION_NAME,
    versionCode = BuildConfig.VERSION_CODE,
    applicationId = BuildConfig.APPLICATION_ID,
    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
    androidRelease = Build.VERSION.RELEASE,
    apiLevel = Build.VERSION.SDK_INT,
    isAdmin = true,
)

/** Copies the stored preferences into the shape the appearance panel renders. */
private fun AppPreferences.toAppearanceState(): AppearanceState = AppearanceState(
    themeId = themeId,
    followSystemTheme = followSystemTheme,
    layoutMode = layoutMode,
    fontId = fontId,
    densityId = densityId,
    backgroundPatternOverride = backgroundPatternOverride,
    showBackgroundPattern = showBackgroundPattern,
    backgroundAnimationEnabled = backgroundAnimationEnabled,
    effectIntensity = effectIntensity,
    effectSpeed = effectSpeed,
    effectSize = effectSize,
    reduceMotion = reduceMotion,
    textScale = textScale,
)

/** Wires the appearance panel's callbacks to the view model. */
private fun AppViewModel.toAppearanceHandlers(): AppearanceHandlers = AppearanceHandlers(
    setTheme = ::setTheme,
    setFollowSystemTheme = ::setFollowSystemTheme,
    setLayoutMode = ::setLayoutMode,
    setFont = ::setFont,
    setDensity = ::setDensity,
    setBackgroundPattern = ::setBackgroundPattern,
    setShowBackgroundPattern = ::setShowBackgroundPattern,
    setBackgroundAnimation = ::setBackgroundAnimation,
    setEffectIntensity = ::setEffectIntensity,
    setEffectSpeed = ::setEffectSpeed,
    setEffectSize = ::setEffectSize,
    setReduceMotion = ::setReduceMotion,
    setTextScale = ::setTextScale,
    reset = ::resetAppearance,
)
