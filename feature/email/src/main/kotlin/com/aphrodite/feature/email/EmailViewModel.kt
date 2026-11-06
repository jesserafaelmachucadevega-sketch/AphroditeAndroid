package com.aphrodite.feature.email

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aphrodite.core.database.dao.EmailDao
import com.aphrodite.core.database.entity.EmailAccountEntity
import com.aphrodite.core.database.entity.EmailBody
import com.aphrodite.core.database.entity.EmailHeader
import com.aphrodite.core.security.SecretStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AccountDraft(
    val id: String? = null,
    val address: String = "",
    val displayName: String = "",
    val imapHost: String = "",
    val imapPort: String = "993",
    val imapSecurity: String = "ssl",
    val imapUsername: String = "",
    val password: String = "",
    val hasStoredPassword: Boolean = false,
) {
    val isNew: Boolean get() = id == null
}

data class EmailUiState(
    val accounts: List<EmailAccountEntity> = emptyList(),
    val activeAccount: EmailAccountEntity? = null,
    val folders: List<String> = emptyList(),
    val folder: String = "INBOX",
    val messages: List<EmailHeader> = emptyList(),
    val openMessage: EmailBody? = null,
    val draft: AccountDraft? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

/**
 * Email: accounts and the IMAP read path.
 *
 * Sending is not wired yet — there is no SMTP code behind the compose button — so this view model
 * deliberately exposes no compose action rather than a button that cannot work.
 */
@HiltViewModel
class EmailViewModel @Inject constructor(
    private val dao: EmailDao,
    private val imap: ImapClient,
    private val secrets: SecretStore,
) : ViewModel() {

    private val _state = MutableStateFlow(EmailUiState())
    val state: StateFlow<EmailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            dao.observeAll().collect { accounts ->
                val active = _state.value.activeAccount?.let { current ->
                    accounts.firstOrNull { it.id == current.id }
                } ?: accounts.firstOrNull { it.isDefault } ?: accounts.firstOrNull()
                _state.update { it.copy(accounts = accounts, activeAccount = active) }
            }
        }
    }

    fun startAddAccount() = _state.update { it.copy(draft = AccountDraft(), error = null, notice = null) }

    fun startEditAccount(account: EmailAccountEntity) = _state.update {
        it.copy(
            draft = AccountDraft(
                id = account.id,
                address = account.address,
                displayName = account.displayName.orEmpty(),
                imapHost = account.imapHost,
                imapPort = account.imapPort.toString(),
                imapSecurity = account.imapSecurity,
                imapUsername = account.imapUsername,
                hasStoredPassword = account.imapPasswordRef != null,
            ),
            error = null,
        )
    }

    fun onDraftChange(draft: AccountDraft) = _state.update { it.copy(draft = draft, error = null) }

    fun cancelDraft() = _state.update { it.copy(draft = null) }

    /** Verifies the credentials without saving, so a typo is caught before it is stored. */
    fun testConnection() {
        val draft = _state.value.draft ?: return
        toggleLoading(true)
        viewModelScope.launch {
            val password = resolvePassword(draft)
            if (password == null) {
                _state.update { it.copy(loading = false, error = "Enter the password.") }
                return@launch
            }
            val result = imap.testConnection(draft.toAccount(), password)
            _state.update {
                it.copy(
                    loading = false,
                    error = result.exceptionOrNull()?.let { error -> describe(error) },
                    notice = if (result.isSuccess) {
                        "Connected. ${result.getOrDefault(emptyList()).size} folders found."
                    } else {
                        null
                    },
                )
            }
        }
    }

    fun saveAccount() {
        val draft = _state.value.draft ?: return
        if (draft.address.isBlank() || draft.imapHost.isBlank() || draft.imapUsername.isBlank()) {
            _state.update { it.copy(error = "Address, IMAP host and username are required.") }
            return
        }
        viewModelScope.launch {
            if (dao.byAddress(draft.address.trim())?.id != draft.id) {
                _state.update { it.copy(error = "That address is already configured.") }
                return@launch
            }
            val now = System.currentTimeMillis()
            val existing = draft.id?.let { dao.get(it) }
            val id = existing?.id ?: UUID.randomUUID().toString()

            val password = draft.password.ifBlank { null }
            val ref = existing?.imapPasswordRef ?: if (password != null) "imap:$id" else null
            if (password != null) {
                secrets.put(ref ?: "imap:$id", password.trim())
            }

            dao.upsert(
                EmailAccountEntity(
                    id = id,
                    address = draft.address.trim(),
                    displayName = draft.displayName.trim().takeIf { it.isNotBlank() },
                    imapHost = draft.imapHost.trim(),
                    imapPort = draft.imapPort.toIntOrNull() ?: 993,
                    imapSecurity = draft.imapSecurity,
                    imapUsername = draft.imapUsername.trim(),
                    imapPasswordRef = ref,
                    smtpHost = existing?.smtpHost,
                    smtpPort = existing?.smtpPort ?: 587,
                    smtpSecurity = existing?.smtpSecurity ?: "starttls",
                    smtpUsername = existing?.smtpUsername,
                    smtpPasswordRef = existing?.smtpPasswordRef,
                    enabled = true,
                    isDefault = existing?.isDefault ?: (dao.defaultAccount() == null),
                    lastSyncAt = existing?.lastSyncAt,
                    lastError = null,
                    createdAt = existing?.createdAt ?: now,
                ),
            )
            _state.update { it.copy(draft = null, notice = "Account saved.") }
            loadMessages(id, "INBOX")
        }
    }

    fun deleteAccount(id: String) {
        viewModelScope.launch {
            dao.get(id)?.imapPasswordRef?.let { secrets.remove(it) }
            dao.delete(id)
            _state.update { it.copy(messages = emptyList(), openMessage = null, notice = "Account removed.") }
        }
    }

    fun selectAccount(accountId: String) {
        val account = _state.value.accounts.firstOrNull { it.id == accountId } ?: return
        _state.update { it.copy(activeAccount = account) }
        loadMessages(accountId, "INBOX")
    }

    fun selectFolder(folder: String) {
        val accountId = _state.value.activeAccount?.id ?: return
        _state.update { it.copy(folder = folder) }
        loadMessages(accountId, folder)
    }

    fun loadMessages(accountId: String, folder: String) {
        toggleLoading(true)
        viewModelScope.launch {
            val account = dao.get(accountId) ?: return@launch
            val password = account.imapPasswordRef?.let { secrets.get(it) }
            if (password == null) {
                _state.update { it.copy(loading = false, error = "No password stored for this account.") }
                return@launch
            }
            val result = imap.listMessages(account, password, folder)
            _state.update {
                it.copy(
                    loading = false,
                    folder = folder,
                    messages = result.getOrDefault(emptyList()),
                    error = result.exceptionOrNull()?.let { error -> describe(error) },
                )
            }
            // Folder list is loaded once per account rather than on every refresh.
            if (_state.value.folders.isEmpty()) {
                imap.listFolders(account, password).onSuccess { folders ->
                    _state.update { it.copy(folders = folders) }
                }
            }
        }
    }

    fun open(header: EmailHeader) {
        toggleLoading(true)
        viewModelScope.launch {
            val account = _state.value.activeAccount ?: return@launch
            val password = account.imapPasswordRef?.let { secrets.get(it) }
            if (password == null) {
                _state.update { it.copy(loading = false, error = "No password stored.") }
                return@launch
            }
            val result = imap.fetchBody(account, password, header.folder, header.uid)
            _state.update {
                it.copy(
                    loading = false,
                    openMessage = result.getOrNull(),
                    error = result.exceptionOrNull()?.let { error -> describe(error) },
                )
            }
        }
    }

    fun closeMessage() = _state.update { it.copy(openMessage = null) }

    fun dismiss() = _state.update { it.copy(error = null, notice = null) }

    private suspend fun resolvePassword(draft: AccountDraft): String? {
        if (draft.password.isNotBlank()) return draft.password
        val ref = draft.id?.let { dao.get(it)?.imapPasswordRef } ?: return null
        return secrets.get(ref)
    }

    private fun toggleLoading(value: Boolean) = _state.update { it.copy(loading = value) }

    private fun AccountDraft.toAccount() = EmailAccountEntity(
        id = id ?: "test",
        address = address,
        imapHost = imapHost,
        imapPort = imapPort.toIntOrNull() ?: 993,
        imapSecurity = imapSecurity,
        imapUsername = imapUsername,
        createdAt = 0,
    )

    /** Turns a mail exception into something a person can act on. */
    private fun describe(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("authentication", ignoreCase = true) ||
                message.contains("LOGIN", ignoreCase = true) ->
                "The server rejected the username or password. Many providers need an app password."
            message.contains("certificate", ignoreCase = true) ||
                message.contains("SSL", ignoreCase = true) ->
                "The TLS handshake failed. Check the host and security setting."
            message.contains("timed out", ignoreCase = true) ||
                message.contains("timeout", ignoreCase = true) ->
                "The server did not respond. Check the host and port."
            message.contains("UnknownHost", ignoreCase = true) ->
                "Could not resolve the host. Check the hostname."
            else -> message.ifBlank { "The mail server returned an error." }
        }
    }
}
