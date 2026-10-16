package com.aphrodite.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aphrodite.app.navigation.AphroditeNavHost
import com.aphrodite.core.adaptive.AphroditeAdaptiveProvider
import com.aphrodite.core.datastore.AppPreferences
import com.aphrodite.designsystem.background.AnimatedBackground
import com.aphrodite.designsystem.theme.AphroditeDensity
import com.aphrodite.designsystem.theme.AphroditeFont
import com.aphrodite.designsystem.theme.AphroditeTheme
import com.aphrodite.designsystem.theme.BackgroundPattern
import com.aphrodite.designsystem.theme.LocalAphroditeColors
import com.aphrodite.designsystem.theme.LocalAphroditeThemeSpec
import com.aphrodite.designsystem.theme.resolveThemeId
import com.aphrodite.feature.auth.AuthScreen
import com.aphrodite.feature.auth.AuthStage
import com.aphrodite.feature.auth.AuthViewModel

/**
 * The application root.
 *
 * Resolves the theme and the layout profile from stored preferences, draws the ambient
 * background, and shows either the sign-in surface or the main shell depending on whether a
 * local profile is unlocked.
 */
@Composable
fun AphroditeApp(
    viewModel: AppViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val preferences by viewModel.state.collectAsStateWithLifecycle()
    val authState by authViewModel.state.collectAsStateWithLifecycle()

    AphroditeTheme(
        themeId = resolveThemeId(preferences.themeId, preferences.followSystemTheme),
        font = AphroditeFont.fromId(preferences.fontId),
        density = AphroditeDensity.fromId(preferences.densityId),
    ) {
        AphroditeAdaptiveProvider(mode = preferences.layoutMode) {
            RootBackground(preferences) {
                if (authState.stage == AuthStage.Unlocked) {
                    AphroditeNavHost(viewModel = viewModel)
                } else {
                    AuthScreen(
                        state = authState,
                        onUsernameChange = authViewModel::onUsernameChange,
                        onDisplayNameChange = authViewModel::onDisplayNameChange,
                        onPasswordChange = authViewModel::onPasswordChange,
                        onConfirmPasswordChange = authViewModel::onConfirmPasswordChange,
                        onTotpCodeChange = authViewModel::onTotpCodeChange,
                        onTogglePassword = authViewModel::togglePasswordVisibility,
                        onSubmit = authViewModel::submit,
                    )
                }
            }
        }
    }
}

/**
 * Draws the ambient background behind the app.
 *
 * Per-user overrides multiply the theme's own tuning so a theme keeps its character unless the
 * user deliberately changes it.
 */
@Composable
private fun RootBackground(
    preferences: AppPreferences,
    content: @Composable () -> Unit,
) {
    val colors = LocalAphroditeColors.current
    val spec = LocalAphroditeThemeSpec.current

    val pattern = preferences.backgroundPatternOverride
        ?.let { BackgroundPattern.fromId(it) }
        ?: spec.defaultPattern

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        if (preferences.showBackgroundPattern) {
            AnimatedBackground(
                pattern = pattern,
                colors = colors,
                intensity = (preferences.effectIntensity * spec.effectIntensity).coerceIn(0f, 1f),
                sizeScale = preferences.effectSize * spec.effectSize,
                speed = preferences.effectSpeed * spec.effectSpeed,
                effectColor = spec.effectColor ?: colors.fg,
                animate = preferences.animateBackgrounds,
            )
        }
        content()
    }
}
