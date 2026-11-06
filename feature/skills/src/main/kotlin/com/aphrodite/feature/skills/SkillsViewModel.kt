package com.aphrodite.feature.skills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aphrodite.core.database.dao.SkillDao
import com.aphrodite.core.database.entity.SkillEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** An unsaved edit. [id] is null when the skill is new. */
data class SkillDraft(
    val id: String? = null,
    val name: String = "",
    val description: String = "",
    val body: String = "",
)

data class SkillsUiState(
    val skills: List<SkillEntity> = emptyList(),
    val draft: SkillDraft? = null,
    val error: String? = null,
)

/**
 * Reusable instruction sets.
 *
 * Editing is a full-screen draft rather than inline, because a skill body is prose and a small
 * field would make it impossible to write one properly.
 */
@HiltViewModel
class SkillsViewModel @Inject constructor(
    private val dao: SkillDao,
) : ViewModel() {

    private val _state = MutableStateFlow(SkillsUiState())
    val state: StateFlow<SkillsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            dao.observeAll().collect { skills -> _state.update { it.copy(skills = skills) } }
        }
    }

    fun startAdd() = _state.update { it.copy(draft = SkillDraft(), error = null) }

    fun startEdit(skill: SkillEntity) = _state.update {
        it.copy(
            draft = SkillDraft(skill.id, skill.name, skill.description, skill.body),
            error = null,
        )
    }

    fun onDraftChange(draft: SkillDraft) = _state.update { it.copy(draft = draft) }

    fun cancelEdit() = _state.update { it.copy(draft = null, error = null) }

    fun save() {
        val draft = _state.value.draft ?: return
        if (draft.name.isBlank() || draft.body.isBlank()) {
            _state.update { it.copy(error = "A skill needs a name and some instructions.") }
            return
        }
        viewModelScope.launch {
            // The description is what the model sees in the prompt; an empty one means the model
            // cannot tell when the skill applies, so it is filled from the first line.
            val description = draft.description.ifBlank {
                draft.body.lineSequence().firstOrNull { it.isNotBlank() }?.take(160).orEmpty()
            }
            val now = System.currentTimeMillis()
            val existing = draft.id?.let { dao.get(it) }
            dao.upsert(
                SkillEntity(
                    id = draft.id ?: UUID.randomUUID().toString(),
                    name = draft.name.trim(),
                    description = description.trim(),
                    body = draft.body,
                    enabled = existing?.enabled ?: true,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
            )
            _state.update { it.copy(draft = null) }
        }
    }

    fun toggleEnabled(skill: SkillEntity) {
        viewModelScope.launch {
            dao.setEnabled(skill.id, !skill.enabled, System.currentTimeMillis())
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { dao.delete(id) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}
