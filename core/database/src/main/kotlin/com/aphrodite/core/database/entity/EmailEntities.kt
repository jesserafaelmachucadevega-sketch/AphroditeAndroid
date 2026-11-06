package com.aphrodite.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * An IMAP/SMTP account.
 *
 * Passwords are references into the encrypted secret store, never the value itself. IMAP host and
 * credentials are separate from SMTP because providers frequently use different hosts for each,
 * and some allow reading with a password that will not send.
 */
@Entity(
    tableName = "email_accounts",
    indices = [Index("enabled"), Index(value = ["address"], unique = true)],
)
@Serializable
data class EmailAccountEntity(
    @PrimaryKey val id: String,
    /** The From address shown to recipients. */
    val address: String,
    val displayName: String? = null,

    @ColumnInfo(name = "imap_host") val imapHost: String,
    @ColumnInfo(name = "imap_port") val imapPort: Int = 993,
    /** `ssl`, `starttls` or `none`. */
    @ColumnInfo(name = "imap_security") val imapSecurity: String = "ssl",
    @ColumnInfo(name = "imap_username") val imapUsername: String,
    @ColumnInfo(name = "imap_password_ref") val imapPasswordRef: String? = null,

    @ColumnInfo(name = "smtp_host") val smtpHost: String? = null,
    @ColumnInfo(name = "smtp_port") val smtpPort: Int = 587,
    @ColumnInfo(name = "smtp_security") val smtpSecurity: String = "starttls",
    @ColumnInfo(name = "smtp_username") val smtpUsername: String? = null,
    @ColumnInfo(name = "smtp_password_ref") val smtpPasswordRef: String? = null,

    val enabled: Boolean = true,
    @ColumnInfo(name = "is_default") val isDefault: Boolean = false,
    @ColumnInfo(name = "last_sync_at") val lastSyncAt: Long? = null,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** One message header, as listed in a folder. Bodies are fetched on open. */
data class EmailHeader(
    val uid: String,
    val folder: String,
    val subject: String,
    val from: String,
    val to: String,
    val date: Long,
    val seen: Boolean,
    val flagged: Boolean,
    val preview: String,
)

/** A fetched message. */
data class EmailBody(
    val header: EmailHeader,
    val text: String,
    val html: String? = null,
    val attachments: List<EmailAttachmentMeta> = emptyList(),
)

data class EmailAttachmentMeta(
    val index: Int,
    val name: String,
    val mime: String,
    val size: Long,
)
