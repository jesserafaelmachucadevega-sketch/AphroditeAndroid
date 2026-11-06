package com.aphrodite.feature.email

import com.aphrodite.core.database.entity.EmailAccountEntity
import com.aphrodite.core.database.entity.EmailAttachmentMeta
import com.aphrodite.core.database.entity.EmailBody
import com.aphrodite.core.database.entity.EmailHeader
import jakarta.mail.BodyPart
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.internet.InternetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A minimal IMAP reader.
 *
 * Deliberately reads with `FetchProfile` covering only envelopes and flags for the list, and fetches
 * the body for one message at a time. Pulling every body to render a list would move megabytes over
 * a phone connection for text that is never shown.
 *
 * Connections are opened per operation rather than pooled: on a phone the process can be killed at
 * any point, and a half-open socket is worse than reconnecting.
 */
@Singleton
class ImapClient @Inject constructor() {

    /** Verifies credentials and returns the folder list, or a failure message. */
    suspend fun testConnection(account: EmailAccountEntity, password: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                withStore(account, password) { store ->
                    store.defaultFolder.list("*")
                        .filter { it.type and Folder.HOLDS_MESSAGES != 0 }
                        .map { it.fullName }
                }
            }
        }

    suspend fun listFolders(account: EmailAccountEntity, password: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                withStore(account, password) { store ->
                    store.defaultFolder.list("*")
                        .filter { it.type and Folder.HOLDS_MESSAGES != 0 }
                        .map { it.fullName }
                        .distinct()
                }
            }
        }

    /** Lists the newest messages in [folderName]. */
    suspend fun listMessages(
        account: EmailAccountEntity,
        password: String,
        folderName: String,
        limit: Int = 50,
    ): Result<List<EmailHeader>> = withContext(Dispatchers.IO) {
        runCatching {
            withStore(account, password) { store ->
                val folder = store.getFolder(folderName)
                // READ_ONLY so listing does not mark anything as seen.
                folder.open(Folder.READ_ONLY)
                try {
                    val total = folder.messageCount
                    if (total == 0) return@withStore emptyList()
                    val start = (total - limit + 1).coerceAtLeast(1)

                    val messages = folder.getMessages(start, total)
                    val profile = jakarta.mail.FetchProfile().apply {
                        add(jakarta.mail.FetchProfile.Item.ENVELOPE)
                        add(jakarta.mail.FetchProfile.Item.FLAGS)
                    }
                    folder.fetch(messages, profile)

                    messages.map { message -> message.toHeader(folderName) }
                        .sortedByDescending { it.date }
                } finally {
                    folder.close(false)
                }
            }
        }
    }

    /** Fetches one message body. Marking it seen is the caller's decision. */
    suspend fun fetchBody(
        account: EmailAccountEntity,
        password: String,
        folderName: String,
        uid: String,
        markSeen: Boolean = true,
    ): Result<EmailBody> = withContext(Dispatchers.IO) {
        runCatching {
            withStore(account, password) { store ->
                val folder = store.getFolder(folderName)
                folder.open(if (markSeen) Folder.READ_WRITE else Folder.READ_ONLY)
                try {
                    val message = folder.search(
                        jakarta.mail.search.HeaderTerm("Message-ID", uid),
                    ).firstOrNull()
                        ?: folder.getMessage(uid.toIntOrNull() ?: 1)

                    val attachments = mutableListOf<EmailAttachmentMeta>()
                    val text = StringBuilder()
                    var html: String? = null

                    fun walk(part: jakarta.mail.Part, index: Int) {
                        when (val content = runCatching { part.content }.getOrNull()) {
                            is Multipart -> {
                                for (i in 0 until content.count) {
                                    walk(content.getBodyPart(i), i)
                                }
                            }
                            is String -> {
                                val mime = part.contentType.orEmpty()
                                when {
                                    mime.contains("text/html", ignoreCase = true) -> html = content
                                    mime.contains("text/plain", ignoreCase = true) -> text.append(content)
                                    part.disposition == jakarta.mail.Part.ATTACHMENT ->
                                        attachments += EmailAttachmentMeta(
                                            index = index,
                                            name = part.fileName ?: "attachment",
                                            mime = mime,
                                            size = part.size.toLong(),
                                        )
                                    else -> Unit
                                }
                            }
                            else -> Unit
                        }
                    }

                    walk(message, 0)

                    EmailBody(
                        header = message.toHeader(folderName),
                        // Fall back to stripping the HTML when a message is HTML-only.
                        text = text.toString().ifBlank { html?.let(::htmlToText).orEmpty() },
                        html = html,
                        attachments = attachments,
                    )
                } finally {
                    folder.close(false)
                }
            }
        }
    }

    // ── Plumbing ─────────────────────────────────────────────────────────────

    private fun <T> withStore(
        account: EmailAccountEntity,
        password: String,
        block: (Store) -> T,
    ): T {
        val protocol = "imap"
        val props = Properties().apply {
            put("mail.store.protocol", protocol)
            put("mail.$protocol.host", account.imapHost)
            put("mail.$protocol.port", account.imapPort.toString())
            put("mail.$protocol.connectiontimeout", TIMEOUT_MS)
            put("mail.$protocol.timeout", TIMEOUT_MS)
            put("mail.$protocol.writetimeout", TIMEOUT_MS)
            // Many servers refuse UTF-7 folder names otherwise.
            put("mail.mime.charset", "UTF-8")
            when (account.imapSecurity) {
                "ssl" -> {
                    put("mail.$protocol.ssl.enable", "true")
                    put("mail.$protocol.ssl.checkserveridentity", "true")
                }
                "starttls" -> {
                    put("mail.$protocol.starttls.enable", "true")
                    put("mail.$protocol.starttls.required", "true")
                }
                else -> put("mail.$protocol.ssl.enable", "false")
            }
        }

        val session = Session.getInstance(props)
        val store = session.getStore(protocol)
        store.connect(account.imapHost, account.imapPort, account.imapUsername, password)
        return try {
            block(store)
        } finally {
            runCatching { store.close() }
        }
    }

    private fun Message.toHeader(folder: String): EmailHeader {
        val message = this
        val from = runCatching { message.from?.firstOrNull() }.getOrNull() as? InternetAddress
        return EmailHeader(
            // Message-ID is stable across connections; fall back to the sequence number when a
            // server omits it, which some bulk senders do.
            uid = runCatching { message.getHeader("Message-ID")?.firstOrNull() }.getOrNull()
                ?: message.messageNumber.toString(),
            folder = folder,
            subject = runCatching { message.subject }.getOrNull() ?: "(no subject)",
            from = from?.let { it.personal ?: it.address } ?: "(unknown sender)",
            to = runCatching {
                (message.getRecipients(Message.RecipientType.TO)?.firstOrNull() as? InternetAddress)?.address
            }.getOrNull().orEmpty(),
            date = runCatching { message.sentDate?.time ?: message.receivedDate?.time }.getOrNull()
                ?: System.currentTimeMillis(),
            seen = runCatching { message.flags?.contains(jakarta.mail.Flags.Flag.SEEN) }
                .getOrNull() ?: false,
            flagged = runCatching { message.flags?.contains(jakarta.mail.Flags.Flag.FLAGGED) }
                .getOrNull() ?: false,
            preview = "",
        )
    }

    /** Strips tags so an HTML-only message still has readable text. */
    private fun htmlToText(html: String): String = html
        .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</(p|div|li|tr|h[1-6])>"), "\n")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    private companion object {
        const val TIMEOUT_MS = "20000"
    }
}
