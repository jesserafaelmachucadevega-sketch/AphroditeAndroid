package com.aphrodite.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aphrodite.core.database.entity.AuthSessionEntity
import com.aphrodite.core.database.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Query("SELECT * FROM user_profiles ORDER BY created_at ASC")
    fun observeProfiles(): Flow<List<UserProfileEntity>>

    @Query("SELECT * FROM user_profiles ORDER BY created_at ASC")
    suspend fun profiles(): List<UserProfileEntity>

    @Query("SELECT * FROM user_profiles WHERE id = :id")
    suspend fun profile(id: String): UserProfileEntity?

    @Query("SELECT * FROM user_profiles WHERE username = :username COLLATE NOCASE LIMIT 1")
    suspend fun byUsername(username: String): UserProfileEntity?

    @Query("SELECT COUNT(*) FROM user_profiles")
    suspend fun count(): Int

    /** True when no profile exists yet, which means the app needs first-run setup. */
    @Query("SELECT COUNT(*) = 0 FROM user_profiles")
    fun observeNeedsSetup(): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfileEntity)

    @Query("DELETE FROM user_profiles WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE user_profiles SET password_hash = :hash WHERE id = :id")
    suspend fun setPasswordHash(id: String, hash: String)

    @Query("UPDATE user_profiles SET totp_secret_ref = :ref, totp_enabled = :enabled, backup_code_hashes = :codes WHERE id = :id")
    suspend fun setTotp(id: String, ref: String?, enabled: Boolean, codes: String?)

    @Query("UPDATE user_profiles SET backup_code_hashes = :codes WHERE id = :id")
    suspend fun setBackupCodes(id: String, codes: String?)

    @Query("UPDATE user_profiles SET biometric_enabled = :enabled WHERE id = :id")
    suspend fun setBiometricEnabled(id: String, enabled: Boolean)

    @Query("UPDATE user_profiles SET display_name = :name, role = :role, avatar_color = :color WHERE id = :id")
    suspend fun updateProfile(id: String, name: String, role: String, color: String?)

    @Query("UPDATE user_profiles SET last_login_at = :at WHERE id = :id")
    suspend fun recordLogin(id: String, at: Long)

    @Query("SELECT * FROM auth_session WHERE id = 1")
    fun observeSession(): Flow<AuthSessionEntity?>

    @Query("SELECT * FROM auth_session WHERE id = 1")
    suspend fun session(): AuthSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSession(session: AuthSessionEntity)

    @Query("DELETE FROM auth_session")
    suspend fun clearSession()
}
