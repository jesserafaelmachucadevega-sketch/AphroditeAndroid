package com.aphrodite.feature.email

import com.aphrodite.core.database.entity.EmailAccountEntity
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends mail over SMTP.
 *
 * The From address comes from the account, not from the composer: a provider that rejects a
 * mismatched sender is common, and letting the user type one they do not own produces a confusing
 * bounce rather than an error here.
 */
@Singleton
class SmtpClient @Inject constructor() {

    suspend fun send(
        account: EmailAccountEntity,
        password: String,
        to: List<String>,
        subject: String,
        body: String,
        inReplyTo: String? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val host = account.smtpHost?.takeIf { it.isNotBlank() }
                ?: error("No SMTP host is configured for this account.")
            val username = account.smtpUsername?.takeIf { it.isNotBlank() } ?: account.imapUsername

            val props = Properties().apply {
                put("mail.smtp.host", host)
                put("mail.smtp.port", account.smtpPort.toString())
                put("mail.smtp.auth", "true")
                put("mail.smtp.connectiontimeout", TIMEOUT_MS)
                put("mail.smtp.timeout", TIMEOUT_MS)
                put("mail.smtp.writetimeout", TIMEOUT_MS)
                when (account.smtpSecurity) {
                    "ssl" -> put("mail.smtp.ssl.enable", "true")
                    "starttls" -> {
                        put("mail.smtp.starttls.enable", "true")
                        put("mail.smtp.starttls.required", "true")
                    }
                    else -> Unit
                }
            }

            val session = Session.getInstance(
                props,
                object : Authenticator() {
                    override fun getPasswordAuthentication() =
                        PasswordAuthentication(username, password)
                },
            )

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(account.address, account.displayName))
                setRecipients(
                    Message.RecipientType.TO,
                    to.map { InternetAddress(it.trim()) }.toTypedArray(),
                )
                this.subject = subject
                setText(body, "UTF-8")
                inReplyTo?.let { setHeader("In-Reply-To", it) }
                saveChanges()
            }

            Transport.send(message)
        }
    }

    private companion object {
        const val TIMEOUT_MS = "20000"
    }
}
