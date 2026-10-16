package com.aphrodite.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aphrodite.core.common.layout.LayoutMode
import com.aphrodite.core.datastore.AppPreferences
import com.aphrodite.core.datastore.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Owns the shell preferences and forwards writes to the backing store. */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val preferences: PreferencesRepository,
) : ViewModel() {

    val state: StateFlow<AppPreferences> = preferences.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppPreferences(),
    )

    fun setLayoutMode(mode: LayoutMode) = launch { preferences.setLayoutMode(mode) }

    fun setTheme(themeId: String) = launch { preferences.setTheme(themeId) }

    fun setFollowSystemTheme(follow: Boolean) = launch { preferences.setFollowSystemTheme(follow) }

    fun setFont(fontId: String) = launch { preferences.setFont(fontId) }

    fun setDensity(densityId: String) = launch { preferences.setDensity(densityId) }

    fun setBackgroundPattern(patternId: String?) = launch { preferences.setBackgroundPattern(patternId) }

    fun setEffectIntensity(value: Float) = launch { preferences.setEffectIntensity(value) }

    fun setEffectSpeed(value: Float) = launch { preferences.setEffectSpeed(value) }

    fun setEffectSize(value: Float) = launch { preferences.setEffectSize(value) }

    fun setBackgroundAnimation(enabled: Boolean) = launch { preferences.setBackgroundAnimation(enabled) }

    fun setShowBackgroundPattern(show: Boolean) = launch { preferences.setShowBackgroundPattern(show) }

    fun setReduceMotion(reduce: Boolean) = launch { preferences.setReduceMotion(reduce) }

    fun setTextScale(scale: Float) = launch { preferences.setTextScale(scale) }

    fun resetAppearance() = launch { preferences.resetToDefaults() }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
