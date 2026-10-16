package com.aphrodite.feature.auth

import com.aphrodite.core.common.result.AppError
import com.aphrodite.core.common.result.AppResult
import com.aphrodite.core.database.dao.UserDao
import com.aphrodite.core.database.entity.AuthSessionEntity
import com.aphrodite.core.database.entity.UserProfileEntity
import com.aphrodite.core.database.entity.UserRole
import com.aphrodite.core.security.PasswordHasher
import com.aphrodite.core.security.SecretStore
import com.aphrodite.core.security.TotpGenerator
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** The result of a sign-in attempt, including the two-factor branch. */
sealed interface LoginOutcome {
    data class Success(val profile: UserProfileEntity) : LoginOutcome
    /** Credentials were valid but a second factor is required. */
    data class RequiresTotp(val profile: UserProfileEntity) : LoginOutcome
}

/** Everything an enrolment screen needs to show. */
data class TotpEnrolment(
    val secret: String,
    val provisioningUri: String,
    val backupCodes: List<String>,
)

/**
 * Owns local profiles and sign-in.
 *
 * There is no server: the first profile created becomes the owner, and subsequent profiles are
 * members. Password hashing and the second factor are entirely local.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val userDao: UserDao,
    private val secretStore: SecretStore,
) {
    /** Emits true while the app has no profile and needs first-run setup. */
    fun observeNeedsSetup(): Flow<Boolean> = userDao.observeNeedsSetup()

    fun observeProfiles(): Flow<List<UserProfileEntity>> = userDao.observeProfiles()

    fun observeSession(): Flow<AuthSessionEntity?> = userDao.observeSession()

    suspend fun profiles(): List<UserProfileEntity> = userDao.profiles()

    suspend fun profile(id: String): UserProfileEntity? = userDao.profile(id)

    /**
     * Creates the first profile, which always becomes the owner so there is always someone who
     * can manage the app.
     */
    suspend fun createProfile(
        username: String,
        displayName: String,
        password: String,
        role: UserRole? = null,
    ): AppResult<UserProfileEntity> {
        val normalized = username.trim().lowercase()
        when {
            normalized.length < MIN_USERNAME_LENGTH ->
                return AppResult.Failure(AppError.Invalid("Username must be at least $MIN_USERNAME_LENGTH characters"))
            normalized.any { !it.isLetterOrDigit() && it !in "-_." } ->
                return AppResult.Failure(AppError.Invalid("Username may only contain letters, digits, dot, dash and underscore"))
            password.length < MIN_PASSWORD_LENGTH ->
                return AppResult.Failure(AppError.Invalid("Password must be at least $MIN_PASSWORD_LENGTH characters"))
        }
        if (userDao.byUsername(normalized) != null) {
            return AppResult.Failure(AppError.Invalid("That username is already taken"))
        }

        val isFirst = userDao.count() == 0
        val profile = UserProfileEntity(
            id = UUID.randomUUID().toString(),
            username = normalized,
            displayName = displayName.trim().ifEmpty { normalized },
            passwordHash = PasswordHasher.hash(password),
            role = role ?: if (isFirst) UserRole.Owner else UserRole.Member,
            createdAt = System.currentTimeMillis(),
        )
        userDao.upsert(profile)
        return AppResult.Success(profile)
    }

    /** Verifies credentials and, when 2FA is on, returns the second-factor branch. */
    suspend fun login(username: String, password: String): AppResult<LoginOutcome> {
        val profile = userDao.byUsername(username.trim().lowercase())
            ?: return AppResult.Failure(AppError.Invalid("Incorrect username or password"))

        if (!PasswordHasher.verify(password, profile.passwordHash)) {
            return AppResult.Failure(AppError.Invalid("Incorrect username or password"))
        }

        // Transparently upgrade the stored hash if the work factor has moved on.
        if (PasswordHasher.needsRehash(profile.passwordHash)) {
            userDao.setPasswordHash(profile.id, PasswordHasher.hash(password))
        }

        return AppResult.Success(
            if (profile.totpEnabled) LoginOutcome.RequiresTotp(profile) else LoginOutcome.Success(profile),
        )
    }

    /**
     * Completes the second factor, accepting either a live TOTP code or an unused backup code.
     *
     * Backup codes are single use: the consumed hash is removed as part of the same call.
     */
    suspend fun completeTotp(profileId: String, code: String): AppResult<LoginOutcome> {
        val profile = userDao.profile(profileId)
            ?: return AppResult.Failure(AppError.Invalid("Profile no longer exists"))

        val secretRef = profile.totpSecretRef
            ?: return AppResult.Failure(AppError.Unexpected("Two-factor is enabled but no secret is stored"))
        val secret = secretStore.get(secretRef)
            ?: return AppResult.Failure(AppError.Unexpected("Could not read the two-factor secret"))

        if (TotpGenerator.verify(secret, code)) {
            return AppResult.Success(LoginOutcome.Success(profile))
        }

        if (consumeBackupCode(profile, code)) {
            return AppResult.Success(LoginOutcome.Success(profile))
        }

        return AppResult.Failure(AppError.Invalid("That code is not valid"))
    }

    private suspend fun consumeBackupCode(profile: UserProfileEntity, code: String): Boolean {
        val stored = profile.backupCodeHashes?.lines()?.filter { it.isNotBlank() } ?: return false
        val normalized = code.trim().uppercase().replace("-", "")
        val index = stored.indexOfFirst { PasswordHasher.verify(normalized, it) }
        if (index < 0) return false

        val remaining = stored.toMutableList().apply { removeAt(index) }
        userDao.setBackupCodes(profile.id, remaining.joinToString("\n").ifEmpty { null })
        return true
    }

    /** Marks a profile as signed in and opens the lock grace period. */
    suspend fun openSession(profileId: String, now: Long = System.currentTimeMillis()) {
        userDao.recordLogin(profileId, now)
        userDao.setSession(
            AuthSessionEntity(
                userId = profileId,
                unlockedAt = now,
                expiresAt = now + SESSION_GRACE_MILLIS,
            ),
        )
    }

    suspend fun logout() = userDao.clearSession()

    /** Re-opens the grace period after a successful re-authentication. */
    suspend fun refreshSession(now: Long = System.currentTimeMillis()) {
        val session = userDao.session() ?: return
        userDao.setSession(session.copy(unlockedAt = now, expiresAt = now + SESSION_GRACE_MILLIS))
    }

    // ── Two-factor management ────────────────────────────────────────────────

    /** Begins enrolment: generates a secret and single-use backup codes. */
    suspend fun beginTotpEnrolment(profileId: String): AppResult<TotpEnrolment> {
        val profile = userDao.profile(profileId)
            ?: return AppResult.Failure(AppError.Invalid("Profile no longer exists"))

        val secret = TotpGenerator.generateSecret()
        val reference = "totp:${profile.id}"
        secretStore.put(reference, secret)

        val codes = List(BACKUP_CODE_COUNT) { generateBackupCode() }
        userDao.setTotp(
            id = profile.id,
            ref = reference,
            enabled = false,
            codes = codes.joinToString("\n") { PasswordHasher.hash(it) },
        )

        return AppResult.Success(
            TotpEnrolment(
                secret = secret,
                provisioningUri = TotpGenerator.provisioningUri(secret, profile.username),
                backupCodes = codes,
            ),
        )
    }

    /** Confirms enrolment by proving the authenticator produces a matching code. */
    suspend fun confirmTotp(profileId: String, code: String): AppResult<Unit> {
        val profile = userDao.profile(profileId)
            ?: return AppResult.Failure(AppError.Invalid("Profile no longer exists"))
        val reference = profile.totpSecretRef
            ?: return AppResult.Failure(AppError.Invalid("Start enrolment before confirming"))
        val secret = secretStore.get(reference)
            ?: return AppResult.Failure(AppError.Unexpected("Could not read the two-factor secret"))

        if (!TotpGenerator.verify(secret, code)) {
            return AppResult.Failure(AppError.Invalid("That code did not match. Check the device clock and try again."))
        }

        userDao.setTotp(profile.id, reference, enabled = true, codes = profile.backupCodeHashes)
        return AppResult.Success(Unit)
    }

    /** Turns off the second factor, requiring the password so a borrowed device cannot. */
    suspend fun disableTotp(profileId: String, password: String): AppResult<Unit> {
        val profile = userDao.profile(profileId)
            ?: return AppResult.Failure(AppError.Invalid("Profile no longer exists"))
        if (!PasswordHasher.verify(password, profile.passwordHash)) {
            return AppResult.Failure(AppError.Invalid("Incorrect password"))
        }
        profile.totpSecretRef?.let { secretStore.remove(it) }
        userDao.setTotp(profile.id, ref = null, enabled = false, codes = null)
        return AppResult.Success(Unit)
    }

    /** Issues a fresh set of backup codes, invalidating the previous ones. */
    suspend fun regenerateBackupCodes(profileId: String): AppResult<List<String>> {
        val profile = userDao.profile(profileId)
            ?: return AppResult.Failure(AppError.Invalid("Profile no longer exists"))
        val codes = List(BACKUP_CODE_COUNT) { generateBackupCode() }
        userDao.setBackupCodes(profile.id, codes.joinToString("\n") { PasswordHasher.hash(it) })
        return AppResult.Success(codes)
    }

    // ── Account management ───────────────────────────────────────────────────

    suspend fun changePassword(profileId: String, current: String, new: String): AppResult<Unit> {
        val profile = userDao.profile(profileId)
            ?: return AppResult.Failure(AppError.Invalid("Profile no longer exists"))
        if (!PasswordHasher.verify(current, profile.passwordHash)) {
            return AppResult.Failure(AppError.Invalid("Current password is incorrect"))
        }
        if (new.length < MIN_PASSWORD_LENGTH) {
            return AppResult.Failure(AppError.Invalid("New password must be at least $MIN_PASSWORD_LENGTH characters"))
        }
        userDao.setPasswordHash(profile.id, PasswordHasher.hash(new))
        return AppResult.Success(Unit)
    }

    suspend fun setBiometricEnabled(profileId: String, enabled: Boolean) {
        userDao.setBiometricEnabled(profileId, enabled)
    }

    suspend fun renameProfile(profileId: String, displayName: String, role: UserRole, color: String?) {
        userDao.updateProfile(profileId, displayName.trim(), role.id, color)
    }

    /** Removes a profile. The owner cannot be removed while other profiles exist. */
    suspend fun deleteProfile(profileId: String): AppResult<Unit> {
        val profile = userDao.profile(profileId)
            ?: return AppResult.Failure(AppError.Invalid("Profile no longer exists"))
        if (profile.role == UserRole.Owner && userDao.count() > 1) {
            return AppResult.Failure(AppError.Invalid("The owner profile cannot be removed"))
        }
        profile.totpSecretRef?.let { secretStore.remove(it) }
        userDao.delete(profile.id)
        if (userDao.session()?.userId == profileId) userDao.clearSession()
        return AppResult.Success(Unit)
    }

    private fun generateBackupCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = java.security.SecureRandom()
        return (1..BACKUP_CODE_LENGTH)
            .map { alphabet[random.nextInt(alphabet.length)] }
            .joinToString("")
    }

    companion object {
        const val MIN_USERNAME_LENGTH = 2
        const val MIN_PASSWORD_LENGTH = 8
        const val BACKUP_CODE_COUNT = 8
        const val BACKUP_CODE_LENGTH = 10

        /** How long the app stays unlocked after authenticating before asking again. */
        const val SESSION_GRACE_MILLIS = 12L * 60L * 60L * 1000L
    }
}
