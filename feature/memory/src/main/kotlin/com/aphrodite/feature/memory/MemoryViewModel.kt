package com.aphrodite.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aphrodite.core.database.dao.MemoryDao
import com.aphrodite.core.database.entity.MemoryCategory
import com.aphrodite.core.database.entity.MemoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class MemoryUiState(
    val memories: List<MemoryEntity> = emptyList(),
    val query: String = "",
    val draft: String = "",
    val draftCategory: MemoryCategory = MemoryCategory.Fact,
    val adding: Boolean = false,
    val error: String? = null,
) {
    /** Pinned first, then a filter across the text. */
    val visible: List<MemoryEntity>
        get() = memories.filter { query.isBlank() || it.text.contains(query, ignoreCase = true) }
}

/**
 * Durable facts, and the switch that decides which of them the model sees every turn.
 *
 * Pinning is the important control here. Everything pinned is sent with every request, so it
 * shapes every answer; everything unpinned is dormant unless the model goes looking for it.
 */
@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val dao: MemoryDao,
) : ViewModel() {

    private val _state = MutableStateFlow(MemoryUiState())
    val state: StateFlow<MemoryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            dao.observeAll().collect { memories -> _state.update { it.copy(memories = memories) } }
        }
    }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query) }

    fun startAdd() = _state.update { it.copy(adding = true, draft = "", error = null) }

    fun cancelAdd() = _state.update { it.copy(adding = false, draft = "") }

    fun onDraftChange(text: String) = _state.update { it.copy(draft = text) }

    fun onCategoryChange(category: MemoryCategory) = _state.update { it.copy(draftCategory = category) }

    fun save() {
        val current = _state.value
        val text = current.draft.trim()
        if (text.isBlank()) {
            _state.update { it.copy(error = "A memory needs some text.") }
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            dao.upsert(
                MemoryEntity(
                    id = UUID.randomUUID().toString(),
                    text = text,
                    category = current.draftCategory,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            _state.update { it.copy(adding = false, draft = "") }
        }
    }

    fun togglePin(memory: MemoryEntity) {
        viewModelScope.launch {
            dao.setPinned(memory.id, !memory.pinned, System.currentTimeMillis())
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { dao.delete(id) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}
