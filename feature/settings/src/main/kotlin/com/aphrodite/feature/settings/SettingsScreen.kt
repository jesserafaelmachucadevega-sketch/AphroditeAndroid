package com.aphrodite.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aphrodite.designsystem.components.AphroditeButton
import com.aphrodite.designsystem.components.AphroditeButtonStyle
import com.aphrodite.designsystem.components.AphroditeCard
import com.aphrodite.designsystem.components.AphroditeDivider
import com.aphrodite.designsystem.components.AphroditeEmptyState
import com.aphrodite.designsystem.components.AphroditeKeyValueRow
import com.aphrodite.designsystem.components.AphroditeSectionHeader
import com.aphrodite.designsystem.icons.AphroditeIcons
import com.aphrodite.designsystem.theme.AphroditeSpacing
import com.aphrodite.designsystem.theme.AphroditeThemeSpec
import com.aphrodite.designsystem.theme.aphroditeColors

/** A settings destination. Grows as each subsystem lands. */
enum class SettingsCategory(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val group: String,
) {
    Services("services", "Providers", AphroditeIcons.Server, "Models"),
    Appearance("appearance", "Appearance", AphroditeIcons.Palette, "Interface"),
    About("about", "About", AphroditeIcons.Info, "System"),
}

/** Build information surfaced on the About panel. */
data class BuildInfo(
    val versionName: String,
    val versionCode: Int,
    val applicationId: String,
    val deviceModel: String,
    val androidRelease: String,
    val apiLevel: Int,
    val isAdmin: Boolean,
)

/**
 * The settings surface.
 *
 * Renders as a category list beside the panel on wide layouts, and as a full-screen panel on
 * compact ones — the same two-pane treatment every list/detail surface in the app uses.
 */
@Composable
fun SettingsScreen(
    state: AppearanceState,
    handlers: AppearanceHandlers,
    themes: List<AphroditeThemeSpec>,
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
    buildInfo: BuildInfo,
    modifier: Modifier = Modifier,
    servicesState: ServicesState = ServicesState(),
    servicesHandlers: ServicesHandlers? = null,
) {
    val profile = com.aphrodite.core.adaptive.LocalDeviceProfile.current
    val twoPane = profile.supportsTwoPanes

    if (twoPane) {
        Row(modifier = modifier.fillMaxSize()) {
            SettingsCategoryList(
                selected = selected,
                onSelect = onSelect,
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxHeight(),
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(aphroditeColors.border),
            )
            Box(modifier = Modifier.weight(1f)) {
                SettingsPanel(selected, state, handlers, themes, buildInfo, servicesState, servicesHandlers)
            }
        }
    } else {
        SettingsPanel(
            category = selected,
            state = state,
            handlers = handlers,
            themes = themes,
            buildInfo = buildInfo,
            servicesState = servicesState,
            servicesHandlers = servicesHandlers,
            showCategoryPicker = true,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun SettingsCategoryList(
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = aphroditeColors
    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AphroditeSpacing.sm, vertical = AphroditeSpacing.md),
    ) {
        SettingsCategory.entries.groupBy { it.group }.forEach { (group, categories) ->
            AphroditeSectionHeader(title = group)
            categories.forEach { category ->
                val active = category == selected
                com.aphrodite.designsystem.components.AphroditeListItem(
                    title = category.label,
                    leading = {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            tint = if (active) colors.accentPrimary else colors.colorMuted,
                            modifier = Modifier.size(15.dp),
                        )
                    },
                    onClick = { onSelect(category) },
                    active = active,
                )
            }
            Box(Modifier.size(AphroditeSpacing.sm))
        }
    }
}

@Composable
private fun SettingsPanel(
    category: SettingsCategory,
    state: AppearanceState,
    handlers: AppearanceHandlers,
    themes: List<AphroditeThemeSpec>,
    buildInfo: BuildInfo,
    servicesState: ServicesState,
    servicesHandlers: ServicesHandlers?,
    showCategoryPicker: Boolean = false,
    onSelect: (SettingsCategory) -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (showCategoryPicker) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AphroditeSpacing.md, vertical = AphroditeSpacing.sm),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm)) {
                    SettingsCategory.entries.forEach { entry ->
                        val active = entry == category
                        AphroditeButton(
                            text = entry.label,
                            onClick = { onSelect(entry) },
                            style = if (active) AphroditeButtonStyle.Primary else AphroditeButtonStyle.Secondary,
                            size = com.aphrodite.designsystem.components.AphroditeButtonSize.Small,
                        )
                    }
                }
            }
            AphroditeDivider()
        }

        Box(modifier = Modifier.weight(1f)) {
            when (category) {
                SettingsCategory.Services ->
                    if (servicesHandlers != null) {
                        ServicesSettingsPanel(state = servicesState, handlers = servicesHandlers)
                    } else {
                        AphroditeEmptyState(
                            title = "Providers unavailable",
                            description = "The model layer did not initialise.",
                            icon = AphroditeIcons.Server,
                        )
                    }

                SettingsCategory.Appearance -> AppearanceSettingsPanel(
                    state = state,
                    handlers = handlers,
                    themes = themes,
                )
                SettingsCategory.About -> AboutPanel(buildInfo, handlers)
            }
        }
    }
}

@Composable
private fun AboutPanel(buildInfo: BuildInfo, handlers: AppearanceHandlers) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(AphroditeSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AphroditeSpacing.lg),
    ) {
        Column {
            AphroditeSectionHeader(title = "Application", icon = AphroditeIcons.Info)
            AphroditeCard(modifier = Modifier.fillMaxWidth()) {
                AphroditeKeyValueRow("Version", "${buildInfo.versionName} (${buildInfo.versionCode})")
                AphroditeKeyValueRow("Package", buildInfo.applicationId)
            }
        }

        Column {
            AphroditeSectionHeader(title = "Device", icon = AphroditeIcons.Smartphone)
            AphroditeCard(modifier = Modifier.fillMaxWidth()) {
                AphroditeKeyValueRow("Model", buildInfo.deviceModel)
                AphroditeKeyValueRow("Android", buildInfo.androidRelease)
                AphroditeKeyValueRow("API level", buildInfo.apiLevel.toString())
            }
        }

        Column {
            AphroditeSectionHeader(title = "Maintenance", icon = AphroditeIcons.Refresh)
            Text(
                text = "Restoring the defaults clears every appearance choice: theme, font, " +
                    "density, layout mode and background settings.",
                color = aphroditeColors.colorMuted,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
            Box(Modifier.size(AphroditeSpacing.md))
            AphroditeButton(
                text = "Restore appearance defaults",
                onClick = handlers.reset,
                style = AphroditeButtonStyle.Danger,
                icon = AphroditeIcons.Refresh,
            )
        }
    }
}
