package com.aphrodite.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * What a local profile is allowed to do.
 *
 * The upstream app gates admin surfaces on a server-side flag; offline there is no server to
 * ask, so the same distinction is modelled locally. [Owner] sees everything, [Member] sees
 * everything except the admin and danger-zone surfaces, and [Guest] is restricted to read-only
 * browsing of content that is not marked private.
 */
enum class UserRole(val id: String, val label: String) {
    Owner("owner", "Owner"),
    Member("member", "Member"),
    Guest("guest", "Guest"),
    ;

    val isAdmin: Boolean get() = this == Owner

    companion object {
        fun fromId(id: String?): UserRole = entries.firstOrNull { it.id == id } ?: Member
    }
}

/**
 * A local profile.
 *
 * Passwords are stored as PBKDF2 hashes; the optional TOTP secret is a reference into the
 * encrypted secret store rather than the secret itself, and the backup codes are stored only as
 * hashes so a database dump cannot be replayed against the second factor.
 */
@Entity(
    tableName = "user_profiles",
    indices = [Index(value = ["username"], unique = true)],
)
@Serializable
data class UserProfileEntity(
    @PrimaryKey val id: String,
    val username: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "password_hash") val passwordHash: String,
    val role: UserRole = UserRole.Member,
    @ColumnInfo(name = "totp_secret_ref") val totpSecretRef: String? = null,
    @ColumnInfo(name = "totp_enabled") val totpEnabled: Boolean = false,
    /** Newline separated hashes of the one-time backup codes. */
    @ColumnInfo(name = "backup_code_hashes") val backupCodeHashes: String? = null,
    @ColumnInfo(name = "biometric_enabled") val biometricEnabled: Boolean = false,
    @ColumnInfo(name = "avatar_color") val avatarColor: String? = null,
    @ColumnInfo(name = "last_login_at") val lastLoginAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** The single in-flight session, so the app can restore who is signed in after a restart. */
@Entity(tableName = "auth_session")
@Serializable
data class AuthSessionEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "unlocked_at") val unlockedAt: Long,
    /** When the lock grace period ends; after this the app asks for a credential again. */
    @ColumnInfo(name = "expires_at") val expiresAt: Long?,
)
