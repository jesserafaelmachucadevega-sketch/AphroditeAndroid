package com.aphrodite.feature.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aphrodite.core.database.dao.ContactDao
import com.aphrodite.core.database.entity.ContactEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ContactDraft(
    val id: String? = null,
    val displayName: String = "",
    val email: String = "",
    val phone: String = "",
    val organization: String = "",
    val title: String = "",
    val notes: String = "",
) {
    val isNew: Boolean get() = id == null
}

data class ContactsUiState(
    val contacts: List<ContactEntity> = emptyList(),
    val query: String = "",
    val draft: ContactDraft? = null,
    val error: String? = null,
) {
    val visible: List<ContactEntity>
        get() = if (query.isBlank()) {
            contacts
        } else {
            contacts.filter {
                it.displayName.contains(query, ignoreCase = true) ||
                    it.email?.contains(query, ignoreCase = true) == true ||
                    it.organization?.contains(query, ignoreCase = true) == true
            }
        }
}

/**
 * Contacts.
 *
 * Local only for now. CardDAV is modelled on the entity — remote href, etag, a sync-pending flag —
 * but no client runs against it, so nothing here pretends to be synced.
 */
@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val dao: ContactDao,
) : ViewModel() {

    private val _state = MutableStateFlow(ContactsUiState())
    val state: StateFlow<ContactsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            dao.observeAll().collect { contacts -> _state.update { it.copy(contacts = contacts) } }
        }
    }

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    fun startAdd() = _state.update {
        it.copy(draft = ContactDraft(), error = null)
    }

    fun startEdit(contactId: String) {
        viewModelScope.launch {
            val contact = dao.getById(contactId) ?: return@launch
            _state.update {
                it.copy(
                    draft = ContactDraft(
                        id = contact.id,
                        displayName = contact.displayName,
                        email = contact.email.orEmpty(),
                        phone = contact.phone.orEmpty(),
                        organization = contact.organization.orEmpty(),
                        title = contact.title.orEmpty(),
                        notes = contact.notes.orEmpty(),
                    ),
                    error = null,
                )
            }
        }
    }

    fun onDraftChange(draft: ContactDraft) = _state.update { it.copy(draft = draft) }

    fun cancelEdit() = _state.update { it.copy(draft = null) }

    fun save() {
        val draft = _state.value.draft ?: return
        if (draft.displayName.isBlank()) {
            _state.update { it.copy(error = "A contact needs a name.") }
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = draft.id?.let { dao.getById(it) }
            dao.upsert(
                ContactEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    uid = existing?.uid ?: UUID.randomUUID().toString(),
                    displayName = draft.displayName.trim(),
                    email = draft.email.trim().takeIf { it.isNotBlank() },
                    phone = draft.phone.trim().takeIf { it.isNotBlank() },
                    organization = draft.organization.trim().takeIf { it.isNotBlank() },
                    title = draft.title.trim().takeIf { it.isNotBlank() },
                    notes = draft.notes.takeIf { it.isNotBlank() },
                    emailsJson = existing?.emailsJson,
                    phonesJson = existing?.phonesJson,
                    remoteHref = existing?.remoteHref,
                    remoteEtag = existing?.remoteEtag,
                    // Only a contact that came from a server needs pushing back to one.
                    syncPending = existing?.remoteHref != null,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
            )
            _state.update { it.copy(draft = null) }
        }
    }

    fun delete(contactId: String) {
        viewModelScope.launch { dao.delete(contactId) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}
