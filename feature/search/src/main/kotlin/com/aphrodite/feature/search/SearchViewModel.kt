package com.aphrodite.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aphrodite.core.database.dao.SearchDao
import com.aphrodite.core.database.dao.SearchHit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<SearchHit> = emptyList(),
    val searching: Boolean = false,
    val error: String? = null,
) {
    val hasSearched: Boolean get() = query.length >= MIN_QUERY
}

private const val MIN_QUERY = 2

/**
 * Search across chats, notes, documents, memories and contacts.
 *
 * Queries are debounced. Every keystroke would otherwise run five LIKE scans, and on a phone with
 * a large corpus that is felt as the field lagging behind the typing.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val dao: SearchDao,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var pending: Job? = null

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query, searching = query.length >= MIN_QUERY) }
        pending?.cancel()

        if (query.length < MIN_QUERY) {
            _state.update { it.copy(results = emptyList(), searching = false) }
            return
        }

        pending = viewModelScope.launch {
            delay(180)
            runCatching { dao.search(query) }
                .onSuccess { hits -> _state.update { it.copy(results = hits, searching = false) } }
                .onFailure { error ->
                    _state.update { it.copy(searching = false, error = error.message) }
                }
        }
    }

    fun clear() = _state.update { SearchUiState() }

    fun dismissError() = _state.update { it.copy(error = null) }
}
