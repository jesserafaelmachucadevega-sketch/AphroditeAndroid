package com.aphrodite.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aphrodite.core.common.layout.LayoutMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.preferencesStore: DataStore<Preferences> by preferencesDataStore(
    name = "aphrodite_preferences",
)

/** Reads and writes the shell-level preferences. */
@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val layoutMode = stringPreferencesKey("layout_mode")
        val themeId = stringPreferencesKey("theme_id")
        val followSystemTheme = booleanPreferencesKey("follow_system_theme")
        val fontId = stringPreferencesKey("font_id")
        val densityId = stringPreferencesKey("density_id")
        val backgroundPattern = stringPreferencesKey("background_pattern")
        val effectIntensity = floatPreferencesKey("effect_intensity")
        val effectSpeed = floatPreferencesKey("effect_speed")
        val effectSize = floatPreferencesKey("effect_size")
        val backgroundAnimation = booleanPreferencesKey("background_animation")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val textScale = floatPreferencesKey("text_scale")
        val showBackgroundPattern = booleanPreferencesKey("show_background_pattern")
    }

    val preferences: Flow<AppPreferences> = context.preferencesStore.data.map { prefs ->
        AppPreferences(
            layoutMode = LayoutMode.fromId(prefs[Keys.layoutMode]),
            themeId = prefs[Keys.themeId] ?: "dark",
            followSystemTheme = prefs[Keys.followSystemTheme] ?: false,
            fontId = prefs[Keys.fontId] ?: "mono",
            densityId = prefs[Keys.densityId] ?: "comfortable",
            backgroundPatternOverride = prefs[Keys.backgroundPattern],
            effectIntensity = prefs[Keys.effectIntensity] ?: 1f,
            effectSpeed = prefs[Keys.effectSpeed] ?: 1f,
            effectSize = prefs[Keys.effectSize] ?: 1f,
            backgroundAnimationEnabled = prefs[Keys.backgroundAnimation] ?: true,
            reduceMotion = prefs[Keys.reduceMotion] ?: false,
            textScale = prefs[Keys.textScale] ?: 1f,
            showBackgroundPattern = prefs[Keys.showBackgroundPattern] ?: true,
        )
    }

    suspend fun setLayoutMode(mode: LayoutMode) = edit { it[Keys.layoutMode] = mode.id }

    suspend fun setTheme(themeId: String) = edit { it[Keys.themeId] = themeId }

    suspend fun setFollowSystemTheme(follow: Boolean) = edit { it[Keys.followSystemTheme] = follow }

    suspend fun setFont(fontId: String) = edit { it[Keys.fontId] = fontId }

    suspend fun setDensity(densityId: String) = edit { it[Keys.densityId] = densityId }

    /** Passing null clears the override so the theme's own pattern applies again. */
    suspend fun setBackgroundPattern(patternId: String?) = edit { prefs ->
        if (patternId == null) prefs.remove(Keys.backgroundPattern) else prefs[Keys.backgroundPattern] = patternId
    }

    suspend fun setEffectIntensity(value: Float) = edit { it[Keys.effectIntensity] = value.coerceIn(0f, 1f) }

    suspend fun setEffectSpeed(value: Float) = edit { it[Keys.effectSpeed] = value.coerceIn(0f, 3f) }

    suspend fun setEffectSize(value: Float) = edit { it[Keys.effectSize] = value.coerceIn(0.25f, 3f) }

    suspend fun setBackgroundAnimation(enabled: Boolean) = edit { it[Keys.backgroundAnimation] = enabled }

    suspend fun setReduceMotion(reduce: Boolean) = edit { it[Keys.reduceMotion] = reduce }

    suspend fun setTextScale(scale: Float) = edit { it[Keys.textScale] = scale.coerceIn(0.75f, 2f) }

    suspend fun setShowBackgroundPattern(show: Boolean) = edit { it[Keys.showBackgroundPattern] = show }

    /** Restores every shell preference to its default. */
    suspend fun resetToDefaults() = context.preferencesStore.edit { it.clear() }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.preferencesStore.edit(block)
    }
}
