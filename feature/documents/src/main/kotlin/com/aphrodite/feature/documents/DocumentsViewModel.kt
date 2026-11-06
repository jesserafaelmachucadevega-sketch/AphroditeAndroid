package com.aphrodite.feature.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aphrodite.core.database.dao.DocumentDao
import com.aphrodite.core.database.entity.DocumentEntity
import com.aphrodite.core.llm.rag.RagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class DocumentDraft(val title: String = "", val body: String = "")

data class DocumentsUiState(
    val documents: List<DocumentEntity> = emptyList(),
    val draft: DocumentDraft? = null,
    val indexingId: String? = null,
    val error: String? = null,
    val notice: String? = null,
)

/**
 * Documents and their retrieval index.
 *
 * Adding a document indexes it straight away. Two-step "save then remember to index" leaves
 * documents silently unsearchable, and the whole point of the feature is that the agent can find
 * them.
 */
@HiltViewModel
class DocumentsViewModel @Inject constructor(
    private val dao: DocumentDao,
    private val rag: RagRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DocumentsUiState())
    val state: StateFlow<DocumentsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            dao.observeAll().collect { documents -> _state.update { it.copy(documents = documents) } }
        }
    }

    fun startAdd() = _state.update { it.copy(draft = DocumentDraft(), error = null, notice = null) }

    fun onDraftChange(draft: DocumentDraft) = _state.update { it.copy(draft = draft) }

    fun cancelAdd() = _state.update { it.copy(draft = null) }

    fun save() {
        val draft = _state.value.draft ?: return
        if (draft.title.isBlank() || draft.body.isBlank()) {
            _state.update { it.copy(error = "A document needs a title and some text.") }
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()
            dao.upsert(
                DocumentEntity(
                    id = id,
                    title = draft.title.trim(),
                    body = draft.body,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            _state.update { it.copy(draft = null, indexingId = id, notice = "Indexing…") }
            index(id)
        }
    }

    fun reindex(id: String) {
        _state.update { it.copy(indexingId = id, error = null, notice = "Indexing…") }
        viewModelScope.launch { index(id) }
    }

    private suspend fun index(id: String) {
        val result = rag.index(id)
        _state.update {
            it.copy(
                indexingId = null,
                notice = result.getOrNull()?.let { count -> "Indexed $count passages." },
                error = result.exceptionOrNull()?.message,
            )
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            dao.delete(id)
            _state.update { it.copy(notice = "Document removed.") }
        }
    }

    fun dismiss() = _state.update { it.copy(error = null, notice = null) }
}
