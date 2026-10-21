package com.aphrodite.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aphrodite.core.database.dao.TaskDao
import com.aphrodite.core.database.entity.TaskAction
import com.aphrodite.core.database.entity.TaskEntity
import com.aphrodite.core.database.entity.TaskRunEntity
import com.aphrodite.core.database.entity.TaskTrigger
import com.aphrodite.core.database.entity.ModelEndpointEntity
import com.aphrodite.core.llm.EndpointRepository
import com.aphrodite.core.llm.agent.AgentLoop
import com.aphrodite.core.llm.model.LlmMessage
import com.aphrodite.core.llm.model.LlmRequest
import com.aphrodite.core.llm.model.LlmRole
import com.aphrodite.core.llm.stream.LlmEvent
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
    private val endpoints: EndpointRepository,
    private val agent: AgentLoop,
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
            val triggerValue = when (draft.trigger) {
                TaskTrigger.Interval -> draft.intervalMinutes.ifBlank { "60" }
                else -> null
            }
            val nextRun = when (draft.trigger) {
                TaskTrigger.Manual -> null
                TaskTrigger.Interval -> now + (triggerValue?.toLongOrNull() ?: 60L) * 60_000L
                else -> now + 60 * 60_000L
            }

            taskDao.upsert(
                TaskEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(),
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
                    nextRunAt = nextRun,
                    runCount = existing?.runCount ?: 0,
                    failureCount = existing?.failureCount ?: 0,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
            )
            _state.update { it.copy(draft = null, notice = "Task saved.") }
        }
    }

    fun delete(taskId: String) {
        viewModelScope.launch {
            taskDao.deleteRuns(taskId)
            taskDao.delete(taskId)
            _state.update { it.copy(notice = "Task removed.") }
        }
    }

    fun setEnabled(taskId: String, enabled: Boolean) {
        viewModelScope.launch {
            taskDao.setEnabled(taskId, enabled, System.currentTimeMillis())
        }
    }

    /** Executes a task now and records the outcome. */
    fun run(taskId: String) {
        if (_state.value.runningTaskId != null) return
        viewModelScope.launch {
            val task = taskDao.getById(taskId) ?: return@launch
            _state.update { it.copy(runningTaskId = taskId, error = null, notice = null) }

            val startedAt = System.currentTimeMillis()
            val runId = UUID.randomUUID().toString()

            val selection = resolveModel(task)
            if (selection == null) {
                finish(task, runId, startedAt, null, "No model is configured. Add a provider first.")
                return@launch
            }
            val (resolved, model) = selection

            val request = LlmRequest(
                baseUrl = resolved.endpoint.baseUrl,
                apiKey = resolved.apiKey,
                model = model,
                providerId = resolved.endpoint.kind.id,
                messages = listOf(LlmMessage(role = LlmRole.User, content = task.payload)),
            )

            val output = StringBuilder()
            var failure: String? = null
            runCatching {
                agent.run(request).collect { event ->
                    when (event) {
                        is LlmEvent.Delta -> output.append(event.text)
                        is LlmEvent.FinalResponse -> {
                            output.clear()
                            output.append(event.content)
                        }
                        is LlmEvent.Failed -> failure = event.message
                        else -> Unit
                    }
                }
            }.onFailure { failure = it.message ?: "The run failed" }

            finish(task, runId, startedAt, output.toString().ifBlank { null }, failure)
        }
    }

    private suspend fun finish(
        task: TaskEntity,
        runId: String,
        startedAt: Long,
        output: String?,
        error: String?,
    ) {
        val finishedAt = System.currentTimeMillis()
        taskDao.insertRun(
            TaskRunEntity(
                id = runId,
                taskId = task.id,
                status = if (error == null) "success" else "failed",
                output = output,
                error = error,
                startedAt = startedAt,
                finishedAt = finishedAt,
                durationMs = finishedAt - startedAt,
            ),
        )
        val now = System.currentTimeMillis()
        taskDao.upsert(
            task.copy(
                lastRunAt = now,
                runCount = task.runCount + 1,
                failureCount = task.failureCount + if (error == null) 0 else 1,
                nextRunAt = when (task.triggerKind) {
                    TaskTrigger.Interval ->
                        now + (task.triggerValue?.toLongOrNull() ?: 60L) * 60_000L
                    TaskTrigger.Manual -> null
                    else -> now + 60 * 60_000L
                },
                updatedAt = now,
            ),
        )
        _state.update {
            it.copy(
                runningTaskId = null,
                notice = if (error == null) "Task finished." else null,
                error = error,
            )
        }
    }

    private suspend fun resolveModel(task: TaskEntity): Pair<com.aphrodite.core.llm.ResolvedEndpoint, String>? {
        val endpointId = task.endpointId
        if (endpointId != null) {
            val resolved = endpoints.resolve(endpointId)
            val model = task.model ?: endpoints.modelsFor(endpointId).firstOrNull()?.modelId
            if (resolved != null && model != null) return resolved to model
        }
        val firstEndpoint: ModelEndpointEntity = endpoints.observeEnabledEndpoints().first().firstOrNull()
            ?: return null
        val model = endpoints.modelsFor(firstEndpoint.id).firstOrNull()?.modelId ?: return null
        val resolved = endpoints.resolve(firstEndpoint.id) ?: return null
        return resolved to model
    }

    fun dismiss() = _state.update { it.copy(error = null, notice = null) }
}
