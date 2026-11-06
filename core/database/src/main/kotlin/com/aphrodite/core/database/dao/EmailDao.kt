package com.aphrodite.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aphrodite.core.database.entity.EmailAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmailDao {

    @Query("SELECT * FROM email_accounts ORDER BY is_default DESC, address ASC")
    fun observeAll(): Flow<List<EmailAccountEntity>>

    @Query("SELECT * FROM email_accounts WHERE enabled = 1 ORDER BY is_default DESC, address ASC")
    suspend fun enabled(): List<EmailAccountEntity>

    @Query("SELECT * FROM email_accounts WHERE id = :id")
    suspend fun get(id: String): EmailAccountEntity?

    @Query("SELECT * FROM email_accounts WHERE address = :address COLLATE NOCASE LIMIT 1")
    suspend fun byAddress(address: String): EmailAccountEntity?

    @Query("SELECT * FROM email_accounts WHERE is_default = 1 LIMIT 1")
    suspend fun defaultAccount(): EmailAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: EmailAccountEntity)

    @Query("DELETE FROM email_accounts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE email_accounts SET is_default = 0")
    suspend fun clearDefaults()

    @Query("UPDATE email_accounts SET is_default = 1 WHERE id = :id")
    suspend fun markDefault(id: String)

    @androidx.room.Transaction
    suspend fun setDefault(id: String) {
        clearDefaults()
        markDefault(id)
    }

    @Query("UPDATE email_accounts SET last_error = :error, last_sync_at = :at WHERE id = :id")
    suspend fun recordSync(id: String, error: String?, at: Long?)

    @Query("UPDATE email_accounts SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)
}
