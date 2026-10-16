package com.aphrodite.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.aphrodite.designsystem.icons.AphroditeIcons
import com.aphrodite.designsystem.theme.AphroditeRadius
import com.aphrodite.designsystem.theme.AphroditeSpacing
import com.aphrodite.designsystem.theme.aphroditeColors

/** A single-line input styled to the Aphrodite panel/border convention. */
@Composable
fun AphroditeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onTrailingIconClick: (() -> Unit)? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
    minHeight: androidx.compose.ui.unit.Dp = 40.dp,
) {
    val colors = aphroditeColors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val borderColor = when {
        isError -> colors.colorError
        focused -> colors.accentPrimary
        else -> colors.inputBorder
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = minHeight)
            .clip(RoundedCornerShape(AphroditeRadius.sm))
            .background(colors.inputBg)
            .border(1.dp, borderColor, RoundedCornerShape(AphroditeRadius.sm))
            .padding(horizontal = AphroditeSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = colors.colorMuted,
                modifier = Modifier.size(16.dp),
            )
            Box(Modifier.size(AphroditeSpacing.sm))
        }

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty() && placeholder != null) {
                Text(
                    text = placeholder,
                    color = colors.colorMuted.copy(alpha = 0.7f),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                textStyle = TextStyle(
                    color = colors.fg,
                    fontSize = androidx.compose.material3.MaterialTheme.typography.bodyMedium.fontSize,
                ),
                cursorBrush = SolidColor(colors.accentPrimary),
                visualTransformation = visualTransformation,
                interactionSource = interactionSource,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                keyboardActions = KeyboardActions(
                    onDone = { onImeAction?.invoke() },
                    onGo = { onImeAction?.invoke() },
                    onSend = { onImeAction?.invoke() },
                    onSearch = { onImeAction?.invoke() },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AphroditeSpacing.md),
            )
        }

        if (trailingIcon != null) {
            Box(Modifier.size(AphroditeSpacing.sm))
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = colors.colorMuted,
                modifier = Modifier
                    .size(18.dp)
                    .then(
                        if (onTrailingIconClick != null) {
                            Modifier.clickable(role = Role.Button, onClick = onTrailingIconClick)
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}

/** A password input with a reveal toggle. */
@Composable
fun AphroditePasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Password",
    isError: Boolean = false,
    revealed: Boolean = false,
    onToggleReveal: () -> Unit = {},
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
) {
    AphroditeTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        leadingIcon = AphroditeIcons.Lock,
        trailingIcon = if (revealed) AphroditeIcons.EyeOff else AphroditeIcons.Eye,
        onTrailingIconClick = onToggleReveal,
        isError = isError,
        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardType = KeyboardType.Password,
        imeAction = imeAction,
        onImeAction = onImeAction,
    )
}

/** The search field used by every list surface. */
@Composable
fun AphroditeSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    onClear: () -> Unit = { onValueChange("") },
) {
    AphroditeTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        leadingIcon = AphroditeIcons.Search,
        trailingIcon = if (value.isNotEmpty()) AphroditeIcons.Close else null,
        onTrailingIconClick = if (value.isNotEmpty()) onClear else null,
        imeAction = ImeAction.Search,
        minHeight = 36.dp,
    )
}

/** A labelled row with a trailing switch. */
@Composable
fun AphroditeSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    val colors = aphroditeColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = AphroditeSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (enabled) colors.fg else colors.fg.copy(alpha = 0.5f),
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
            if (description != null) {
                Text(
                    text = description,
                    color = colors.colorMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
        }
        AphroditeSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** A pill switch matching the theme's toggle colours. */
@Composable
fun AphroditeSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = aphroditeColors
    val trackColor = if (checked) colors.toggleActive else colors.toggleBg
    val thumbColor = if (checked) onAccent(colors.toggleActive) else colors.colorMuted

    Box(
        modifier = modifier
            .size(width = 38.dp, height = 22.dp)
            .clip(RoundedCornerShape(AphroditeRadius.pill))
            .background(if (enabled) trackColor else trackColor.copy(alpha = 0.5f))
            .border(1.dp, colors.border, RoundedCornerShape(AphroditeRadius.pill))
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .size(16.dp)
                .clip(RoundedCornerShape(AphroditeRadius.pill))
                .background(thumbColor),
        )
    }
}

/** A labelled checkbox row. */
@Composable
fun AphroditeCheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checkedColor: Color? = null,
) {
    val colors = aphroditeColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Checkbox) { onCheckedChange(!checked) }
            .padding(vertical = AphroditeSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AphroditeCheckbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled, checkedColor = checkedColor)
        Box(Modifier.size(AphroditeSpacing.md))
        Text(
            text = label,
            color = if (enabled) colors.fg else colors.fg.copy(alpha = 0.5f),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A square checkbox; also used for note checklists. */
@Composable
fun AphroditeCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checkedColor: Color? = null,
) {
    val colors = aphroditeColors
    val accent = checkedColor ?: colors.accentPrimary
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(RoundedCornerShape(AphroditeRadius.xs))
            .background(if (checked) accent else Color.Transparent)
            .border(1.dp, if (checked) accent else colors.border, RoundedCornerShape(AphroditeRadius.xs))
            .clickable(enabled = enabled, role = Role.Checkbox) { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = AphroditeIcons.Check,
                contentDescription = null,
                tint = onAccent(accent),
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/**
 * A labelled slider used across settings and the media tools.
 *
 * Built on the Material slider but with the theme's accent and compact metrics.
 */
@Composable
fun AphroditeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    label: String? = null,
    enabled: Boolean = true,
) {
    val colors = aphroditeColors
    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = label,
                    color = colors.fg,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = formatSliderValue(value, valueRange),
                    color = colors.colorMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
            Box(Modifier.height(AphroditeSpacing.xs))
        }
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            modifier = Modifier.height(24.dp),
        )
    }
}

private fun formatSliderValue(value: Float, range: ClosedFloatingPointRange<Float>): String {
    val span = range.endInclusive - range.start
    return if (span <= 2f) {
        val decimals = if (span <= 0.2f) 2 else 1
        String.format("%.${decimals}f", value)
    } else {
        value.toInt().toString()
    }
}
