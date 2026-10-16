package com.aphrodite.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aphrodite.core.common.result.AppResult
import com.aphrodite.core.database.entity.UserProfileEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which surface the auth flow is showing. */
enum class AuthStage { Loading, Setup, Login, TotpChallenge, Unlocked }

data class AuthUiState(
    val stage: AuthStage = AuthStage.Loading,
    val username: String = "",
    val displayName: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val totpCode: String = "",
    val error: String? = null,
    val busy: Boolean = false,
    val profiles: List<UserProfileEntity> = emptyList(),
    val activeProfile: UserProfileEntity? = null,
    val showPassword: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !busy && when (stage) {
            AuthStage.Setup -> username.isNotBlank() && password.length >= AuthRepository.MIN_PASSWORD_LENGTH
            AuthStage.Login -> username.isNotBlank() && password.isNotEmpty()
            AuthStage.TotpChallenge -> totpCode.length >= 6
            else -> false
        }
}

/**
 * Drives the sign-in flow.
 *
 * The app has no server, so this resolves entirely against the local database: first-run brings
 * up the setup form, an existing profile brings up sign-in, and a profile with two-factor
 * enabled brings up the code challenge.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private var pendingTotpProfileId: String? = null

    init {
        viewModelScope.launch {
            repository.observeNeedsSetup().collect { needsSetup ->
                if (_state.value.stage == AuthStage.Unlocked) return@collect
                if (needsSetup) {
                    _state.update { it.copy(stage = AuthStage.Setup) }
                } else {
                    val profiles = repository.profiles()
                    _state.update { current ->
                        current.copy(
                            stage = AuthStage.Login,
                            profiles = profiles,
                            username = current.username.ifEmpty { profiles.firstOrNull()?.username.orEmpty() },
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            repository.observeSession().collect { session ->
                if (session == null) {
                    if (_state.value.stage == AuthStage.Unlocked) {
                        _state.update { it.copy(stage = AuthStage.Login, password = "", totpCode = "") }
                    }
                    return@collect
                }
                val expiresAt = session.expiresAt
                val expired = expiresAt != null && expiresAt <= System.currentTimeMillis()
                if (!expired) {
                    val profile = repository.profile(session.userId)
                    _state.update { it.copy(stage = AuthStage.Unlocked, activeProfile = profile) }
                } else if (_state.value.stage == AuthStage.Unlocked) {
                    _state.update { it.copy(stage = AuthStage.Login, password = "", totpCode = "") }
                }
            }
        }
    }

    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, error = null) }

    fun onDisplayNameChange(value: String) = _state.update { it.copy(displayName = value, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun onConfirmPasswordChange(value: String) = _state.update { it.copy(confirmPassword = value, error = null) }

    fun onTotpCodeChange(value: String) =
        _state.update { it.copy(totpCode = value.filter(Char::isDigit).take(6), error = null) }

    fun togglePasswordVisibility() = _state.update { it.copy(showPassword = !it.showPassword) }

    fun switchToSetup() = _state.update { it.copy(stage = AuthStage.Setup, error = null) }

    fun switchToLogin() = _state.update {
        it.copy(stage = AuthStage.Login, error = null, password = "", confirmPassword = "", totpCode = "")
    }

    fun selectProfile(username: String) = _state.update { it.copy(username = username, error = null) }

    /** Runs whichever action the current stage calls for. */
    fun submit() {
        when (_state.value.stage) {
            AuthStage.Setup -> createProfile()
            AuthStage.Login -> signIn()
            AuthStage.TotpChallenge -> submitTotp()
            else -> Unit
        }
    }

    private fun createProfile() {
        val snapshot = _state.value
        if (snapshot.password != snapshot.confirmPassword) {
            _state.update { it.copy(error = "The two passwords do not match") }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.createProfile(snapshot.username, snapshot.displayName, snapshot.password)) {
                is AppResult.Success -> repository.openSession(result.value.id)
                is AppResult.Failure ->
                    _state.update { it.copy(busy = false, error = result.error.message) }
            }
        }
    }

    private fun signIn() {
        val snapshot = _state.value
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.login(snapshot.username, snapshot.password)) {
                is AppResult.Success -> when (val outcome = result.value) {
                    is LoginOutcome.Success -> repository.openSession(outcome.profile.id)
                    is LoginOutcome.RequiresTotp -> {
                        pendingTotpProfileId = outcome.profile.id
                        _state.update {
                            it.copy(
                                busy = false,
                                stage = AuthStage.TotpChallenge,
                                totpCode = "",
                                password = "",
                                activeProfile = outcome.profile,
                            )
                        }
                    }
                }
                is AppResult.Failure ->
                    _state.update { it.copy(busy = false, error = result.error.message) }
            }
        }
    }

    private fun submitTotp() {
        val profileId = pendingTotpProfileId ?: return
        val code = _state.value.totpCode
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.completeTotp(profileId, code)) {
                is AppResult.Success -> {
                    pendingTotpProfileId = null
                    repository.openSession(profileId)
                }
                is AppResult.Failure -> _state.update {
                    it.copy(busy = false, totpCode = "", error = result.error.message)
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { repository.logout() }
    }
}
