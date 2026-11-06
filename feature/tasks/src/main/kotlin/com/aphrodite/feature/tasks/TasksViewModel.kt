package com.aphrodite.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aphrodite.core.database.dao.TaskDao
import com.aphrodite.core.database.entity.TaskAction
import com.aphrodite.core.database.entity.TaskEntity
import com.aphrodite.core.database.entity.TaskRunEntity
import com.aphrodite.core.database.entity.TaskTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class TaskDraft(
    val id: String? = null,
    val name: String = "",
    val description: String = "",
    val prompt: String = "",
    val trigger: TaskTrigger = TaskTrigger.Manual,
    val intervalMinutes: String = "60",
    val enabled: Boolean = true,
) {
    val isNew: Boolean get() = id == null
}

data class TasksUiState(
    val tasks: List<TaskEntity> = emptyList(),
    val runs: List<TaskRunEntity> = emptyList(),
    val draft: TaskDraft? = null,
    val runningTaskId: String? = null,
    val error: String? = null,
    val notice: String? = null,
)

/**
 * Tasks: scheduled or on-demand prompts run through the agent.
 *
 * "Run now" executes the task's prompt through the same agent loop chat uses, so a task can search
 * the web or read a page, not just generate text. The output and any error land in the run history.
 */
@HiltViewModel
class TasksViewModel @Inject constructor(
    private val taskDao: TaskDao,
    private val runner: TaskRunner,
    private val scheduler: TaskScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(TasksUiState())
    val state: StateFlow<TasksUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            taskDao.observeAll().collect { tasks -> _state.update { it.copy(tasks = tasks) } }
        }
        viewModelScope.launch {
            taskDao.observeRecentRuns(50).collect { runs -> _state.update { it.copy(runs = runs) } }
        }
    }

    fun startAdd() = _state.update { it.copy(draft = TaskDraft(), error = null, notice = null) }

    fun startEdit(task: TaskEntity) = _state.update {
        it.copy(
            draft = TaskDraft(
                id = task.id,
                name = task.name,
                description = task.description.orEmpty(),
                prompt = task.payload,
                trigger = task.triggerKind,
                intervalMinutes = task.triggerValue ?: "60",
                enabled = task.enabled,
            ),
            error = null,
            notice = null,
        )
    }

    fun cancelEdit() = _state.update { it.copy(draft = null) }

    fun onDraftChange(draft: TaskDraft) = _state.update { it.copy(draft = draft, error = null) }

    fun save() {
        val draft = _state.value.draft ?: return
        if (draft.name.isBlank() || draft.prompt.isBlank()) {
            _state.update { it.copy(error = "A task needs a name and a prompt.") }
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = draft.id?.let { taskDao.getById(it) }
            val interval = (draft.intervalMinutes.toLongOrNull() ?: 60L)
                .coerceAtLeast(TaskScheduler.MIN_INTERVAL_MINUTES)
            val triggerValue = if (draft.trigger == TaskTrigger.Interval) interval.toString() else null
            val id = existing?.id ?: UUID.randomUUID().toString()

            taskDao.upsert(
                TaskEntity(
                    id = id,
                    name = draft.name.trim(),
                    description = draft.description.takeIf { it.isNotBlank() },
                    triggerKind = draft.trigger,
                    triggerValue = triggerValue,
                    actionKind = TaskAction.AgentPrompt,
                    payload = draft.prompt,
                    outputTarget = existing?.outputTarget,
                    sessionId = existing?.sessionId,
                    model = existing?.model,
                    endpointId = existing?.endpointId,
                    enabled = draft.enabled,
                    notifyOn = existing?.notifyOn ?: "failure",
                    webhookToken = existing?.webhookToken,
                    lastRunAt = existing?.lastRunAt,
                    nextRunAt = if (draft.trigger == TaskTrigger.Interval) now + interval * 60_000L else null,
                    runCount = existing?.runCount ?: 0,
                    failureCount = existing?.failureCount ?: 0,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
            )
            _state.update { it.copy(draft = null, notice = "Task saved.") }
            // Enqueue or cancel the background schedule to match what was just saved.
            scheduler.sync(id)
        }
    }

    fun delete(taskId: String) {
        viewModelScope.launch {
            scheduler.sync(taskId)
            taskDao.deleteRuns(taskId)
            taskDao.delete(taskId)
            _state.update { it.copy(notice = "Task removed.") }
        }
    }

    fun setEnabled(taskId: String, enabled: Boolean) {
        viewModelScope.launch {
            taskDao.setEnabled(taskId, enabled, System.currentTimeMillis())
            scheduler.sync(taskId)
        }
    }

    /** Executes a task now and records the outcome. */
    fun run(taskId: String) {
        if (_state.value.runningTaskId != null) return
        viewModelScope.launch {
            _state.update { it.copy(runningTaskId = taskId, error = null, notice = null) }
            val record = runCatching { runner.run(taskId) }.getOrNull()
            _state.update {
                it.copy(
                    runningTaskId = null,
                    notice = if (record?.status == "success") "Task finished." else null,
                    error = if (record == null || record.status != "success") {
                        record?.error ?: "The run failed."
                    } else {
                        null
                    },
                )
            }
        }
    }

    fun dismiss() = _state.update { it.copy(error = null, notice = null) }
}
