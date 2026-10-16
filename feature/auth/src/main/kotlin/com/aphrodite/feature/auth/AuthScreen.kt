package com.aphrodite.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aphrodite.designsystem.components.AphroditeButton
import com.aphrodite.designsystem.components.AphroditeButtonSize
import com.aphrodite.designsystem.components.AphroditeCard
import com.aphrodite.designsystem.components.AphroditePasswordField
import com.aphrodite.designsystem.components.AphroditeSpinner
import com.aphrodite.designsystem.components.AphroditeTextField
import com.aphrodite.designsystem.icons.AphroditeIcons
import com.aphrodite.designsystem.theme.AphroditeRadius
import com.aphrodite.designsystem.theme.AphroditeSpacing
import com.aphrodite.designsystem.theme.aphroditeColors

/**
 * The sign-in surface.
 *
 * One screen covers first-run setup, returning sign-in and the second-factor challenge, so the
 * app never flashes between different screens while it works out which one applies.
 */
@Composable
fun AuthScreen(
    state: AuthUiState,
    onUsernameChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onTotpCodeChange: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = aphroditeColors
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .padding(AphroditeSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandHeader(stage = state.stage)

            Box(Modifier.size(AphroditeSpacing.xl))

            AphroditeCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(AphroditeSpacing.lg),
            ) {
                when (state.stage) {
                    AuthStage.Loading -> LoadingBody()
                    AuthStage.Setup -> SetupBody(
                        state = state,
                        onUsernameChange = onUsernameChange,
                        onDisplayNameChange = onDisplayNameChange,
                        onPasswordChange = onPasswordChange,
                        onConfirmPasswordChange = onConfirmPasswordChange,
                        onTogglePassword = onTogglePassword,
                        onSubmit = onSubmit,
                        focusRequester = focusRequester,
                    )
                    AuthStage.Login -> LoginBody(
                        state = state,
                        onUsernameChange = onUsernameChange,
                        onPasswordChange = onPasswordChange,
                        onTogglePassword = onTogglePassword,
                        onSubmit = onSubmit,
                        focusRequester = focusRequester,
                    )
                    AuthStage.TotpChallenge -> TotpBody(
                        state = state,
                        onCodeChange = onTotpCodeChange,
                        onSubmit = onSubmit,
                        focusRequester = focusRequester,
                    )
                    AuthStage.Unlocked -> LoadingBody()
                }
            }

            if (state.stage != AuthStage.Loading && state.stage != AuthStage.Unlocked) {
                Box(Modifier.size(AphroditeSpacing.lg))
                FooterNote(state)
            }
        }
    }
}

@Composable
private fun BrandHeader(stage: AuthStage) {
    val colors = aphroditeColors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(AphroditeRadius.lg))
                .background(colors.accentPrimary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = AphroditeIcons.Sparkles,
                contentDescription = null,
                tint = colors.accentPrimary,
                modifier = Modifier.size(28.dp),
            )
        }
        Box(Modifier.size(AphroditeSpacing.md))
        Text(
            text = "Aphrodite",
            color = colors.fg,
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = when (stage) {
                AuthStage.Setup -> "Create your profile to get started"
                AuthStage.TotpChallenge -> "Enter your two-factor code"
                AuthStage.Login -> "Welcome back"
                else -> "Starting up"
            },
            color = colors.colorMuted,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun LoadingBody() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(AphroditeSpacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        AphroditeSpinner(size = 24.dp)
    }
}

@Composable
private fun SetupBody(
    state: AuthUiState,
    onUsernameChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
) {
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(verticalArrangement = Arrangement.spacedBy(AphroditeSpacing.md)) {
        AphroditeTextField(
            value = state.username,
            onValueChange = onUsernameChange,
            placeholder = "Username",
            leadingIcon = AphroditeIcons.User,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next,
            modifier = Modifier.focusRequester(focusRequester),
        )
        AphroditeTextField(
            value = state.displayName,
            onValueChange = onDisplayNameChange,
            placeholder = "Display name (optional)",
            leadingIcon = AphroditeIcons.Type,
            imeAction = ImeAction.Next,
        )
        AphroditePasswordField(
            value = state.password,
            onValueChange = onPasswordChange,
            placeholder = "Password (at least ${AuthRepository.MIN_PASSWORD_LENGTH} characters)",
            revealed = state.showPassword,
            onToggleReveal = onTogglePassword,
            imeAction = ImeAction.Next,
        )
        AphroditePasswordField(
            value = state.confirmPassword,
            onValueChange = onConfirmPasswordChange,
            placeholder = "Confirm password",
            revealed = state.showPassword,
            onToggleReveal = onTogglePassword,
            imeAction = ImeAction.Done,
            onImeAction = onSubmit,
        )
        ErrorLine(state.error)
        AphroditeButton(
            text = "Create profile",
            onClick = onSubmit,
            enabled = state.canSubmit,
            loading = state.busy,
            fillWidth = true,
            size = AphroditeButtonSize.Large,
        )
    }
}

@Composable
private fun LoginBody(
    state: AuthUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AphroditeSpacing.md)) {
        if (state.profiles.size > 1) {
            Text(
                text = "Choose a profile",
                color = aphroditeColors.colorMuted,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AphroditeSpacing.sm)) {
                state.profiles.take(4).forEach { profile ->
                    val selected = profile.username.equals(state.username, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AphroditeRadius.xs))
                            .background(
                                if (selected) aphroditeColors.accentPrimary.copy(alpha = 0.18f)
                                else aphroditeColors.toggleBg,
                            )
                            .clickable(role = Role.RadioButton) { onUsernameChange(profile.username) }
                            .padding(horizontal = AphroditeSpacing.md, vertical = AphroditeSpacing.sm),
                    ) {
                        Text(
                            text = profile.displayName,
                            color = if (selected) aphroditeColors.accentPrimary else aphroditeColors.fg,
                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }

        AphroditeTextField(
            value = state.username,
            onValueChange = onUsernameChange,
            placeholder = "Username",
            leadingIcon = AphroditeIcons.User,
            imeAction = ImeAction.Next,
            modifier = Modifier.focusRequester(focusRequester),
        )
        AphroditePasswordField(
            value = state.password,
            onValueChange = onPasswordChange,
            revealed = state.showPassword,
            onToggleReveal = onTogglePassword,
            imeAction = ImeAction.Done,
            onImeAction = onSubmit,
        )
        ErrorLine(state.error)
        AphroditeButton(
            text = "Sign in",
            onClick = onSubmit,
            enabled = state.canSubmit,
            loading = state.busy,
            fillWidth = true,
            size = AphroditeButtonSize.Large,
        )
    }
}

@Composable
private fun TotpBody(
    state: AuthUiState,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
) {
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(verticalArrangement = Arrangement.spacedBy(AphroditeSpacing.md)) {
        Text(
            text = "Open your authenticator app and enter the six digit code for Aphrodite. " +
                "A backup code works here too.",
            color = aphroditeColors.colorMuted,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
        AphroditeTextField(
            value = state.totpCode,
            onValueChange = onCodeChange,
            placeholder = "000000",
            leadingIcon = AphroditeIcons.Shield,
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
            onImeAction = onSubmit,
            modifier = Modifier.focusRequester(focusRequester),
        )
        ErrorLine(state.error)
        AphroditeButton(
            text = "Unlock",
            onClick = onSubmit,
            enabled = state.canSubmit,
            loading = state.busy,
            fillWidth = true,
            size = AphroditeButtonSize.Large,
        )
    }
}

@Composable
private fun ErrorLine(error: String?) {
    if (error == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = AphroditeIcons.AlertCircle,
            contentDescription = null,
            tint = aphroditeColors.colorError,
            modifier = Modifier.size(14.dp),
        )
        Box(Modifier.size(AphroditeSpacing.sm))
        Text(
            text = error,
            color = aphroditeColors.colorError,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun FooterNote(state: AuthUiState) {
    val colors = aphroditeColors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = AphroditeIcons.Lock,
                contentDescription = null,
                tint = colors.colorMuted,
                modifier = Modifier.size(11.dp),
            )
            Box(Modifier.size(AphroditeSpacing.xs))
            Text(
                text = "Everything stays on this device",
                color = colors.colorMuted,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
        }
    }
}
