package com.aphrodite.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aphrodite.core.database.dao.NoteDao
import com.aphrodite.core.database.entity.NoteEntity
import com.aphrodite.core.database.entity.NoteItemEntity
import com.aphrodite.core.database.entity.NoteKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** A note plus its checklist, as the editor works with it. */
data class NoteDraft(
    val id: String? = null,
    val title: String = "",
    val body: String = "",
    val kind: NoteKind = NoteKind.Text,
    val color: String? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val items: List<NoteItem> = emptyList(),
) {
    val isNew: Boolean get() = id == null
}

data class NoteItem(val id: String, val text: String, val checked: Boolean)

data class NotesUiState(
    val notes: List<NoteEntity> = emptyList(),
    val archived: List<NoteEntity> = emptyList(),
    val draft: NoteDraft? = null,
    val query: String = "",
    val showArchived: Boolean = false,
) {
    val visible: List<NoteEntity>
        get() {
            val source = if (showArchived) archived else notes
            if (query.isBlank()) return source
            return source.filter {
                it.title.contains(query, ignoreCase = true) || it.body.contains(query, ignoreCase = true)
            }
        }
}

/**
 * Notes: create, edit, checklist items, pin, archive.
 *
 * Reminder scheduling lives in the notifications module; this view model owns the note itself and
 * the reminder timestamp, not the alarm.
 */
@HiltViewModel
class NotesViewModel @Inject constructor(
    private val noteDao: NoteDao,
) : ViewModel() {

    private val _state = MutableStateFlow(NotesUiState())
    val state: StateFlow<NotesUiState> = _state.asStateFlow()

    private var itemsJob: Job? = null

    init {
        viewModelScope.launch {
            noteDao.observeActive().collect { notes -> _state.update { it.copy(notes = notes) } }
        }
        viewModelScope.launch {
            noteDao.observeArchived().collect { notes -> _state.update { it.copy(archived = notes) } }
        }
    }

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    fun toggleArchivedView() = _state.update { it.copy(showArchived = !it.showArchived) }

    fun newNote() = _state.update {
        it.copy(draft = NoteDraft(kind = NoteKind.Text))
    }

    fun newChecklist() = _state.update {
        it.copy(draft = NoteDraft(kind = NoteKind.Checklist, items = listOf(newItem())))
    }

    fun edit(noteId: String) {
        viewModelScope.launch {
            val note = noteDao.getById(noteId) ?: return@launch
            val items = noteDao.items(noteId).map { NoteItem(it.id, it.text, it.checked) }
            _state.update {
                it.copy(
                    draft = NoteDraft(
                        id = note.id,
                        title = note.title,
                        body = note.body,
                        kind = note.kind,
                        color = note.color,
                        pinned = note.pinned,
                        archived = note.archived,
                        items = items,
                    ),
                )
            }
        }
    }

    fun closeEditor() {
        itemsJob?.cancel()
        _state.update { it.copy(draft = null) }
    }

    fun onTitleChange(value: String) = updateDraft { it.copy(title = value) }

    fun onBodyChange(value: String) = updateDraft { it.copy(body = value) }

    fun onColorChange(color: String?) = updateDraft { it.copy(color = color) }

    fun togglePinned() = updateDraft { it.copy(pinned = !it.pinned) }

    fun toggleArchived() = updateDraft { it.copy(archived = !it.archived) }

    fun addItem() = updateDraft { it.copy(kind = NoteKind.Checklist, items = it.items + newItem()) }

    fun updateItem(id: String, text: String) = updateDraft { draft ->
        draft.copy(items = draft.items.map { if (it.id == id) it.copy(text = text) else it })
    }

    fun toggleItem(id: String) = updateDraft { draft ->
        draft.copy(items = draft.items.map { if (it.id == id) it.copy(checked = !it.checked) else it })
    }

    fun removeItem(id: String) = updateDraft { draft ->
        draft.copy(items = draft.items.filterNot { it.id == id })
    }

    /** Saves the draft, creating the note when it is new. */
    fun save() {
        val draft = _state.value.draft ?: return
        if (draft.title.isBlank() && draft.body.isBlank() && draft.items.none { it.text.isNotBlank() }) {
            closeEditor()
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = draft.id?.let { noteDao.getById(it) }
            val id = existing?.id ?: UUID.randomUUID().toString()

            noteDao.saveWithItems(
                note = NoteEntity(
                    id = id,
                    title = draft.title.trim(),
                    body = draft.body,
                    kind = if (draft.items.isNotEmpty()) NoteKind.Checklist else draft.kind,
                    color = draft.color,
                    pinned = draft.pinned,
                    archived = draft.archived,
                    sortOrder = existing?.sortOrder ?: 0,
                    reminderAt = existing?.reminderAt,
                    recurrenceRule = existing?.recurrenceRule,
                    goalTarget = existing?.goalTarget,
                    goalProgress = existing?.goalProgress ?: 0,
                    tags = existing?.tags,
                    sourceSessionId = existing?.sourceSessionId,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
                items = draft.items.mapIndexed { index, item ->
                    NoteItemEntity(
                        id = item.id,
                        noteId = id,
                        text = item.text,
                        checked = item.checked,
                        sortOrder = index,
                        createdAt = now,
                    )
                }.filter { it.text.isNotBlank() },
            )
            _state.update { it.copy(draft = null) }
        }
    }

    fun delete(noteId: String) {
        viewModelScope.launch { noteDao.delete(noteId) }
    }

    fun togglePinned(noteId: String, pinned: Boolean) {
        viewModelScope.launch { noteDao.setPinned(noteId, pinned, System.currentTimeMillis()) }
    }

    fun setArchived(noteId: String, archived: Boolean) {
        viewModelScope.launch { noteDao.setArchived(noteId, archived, System.currentTimeMillis()) }
    }

    private fun updateDraft(block: (NoteDraft) -> NoteDraft) {
        _state.update { current -> current.draft?.let { current.copy(draft = block(it)) } ?: current }
    }

    private fun newItem() = NoteItem(UUID.randomUUID().toString(), "", false)
}
