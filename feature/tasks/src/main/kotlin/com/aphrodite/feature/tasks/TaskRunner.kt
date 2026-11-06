package com.aphrodite.feature.tasks

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aphrodite.core.database.dao.TaskDao
import com.aphrodite.core.database.entity.TaskEntity
import com.aphrodite.core.database.entity.TaskRunEntity
import com.aphrodite.core.database.entity.TaskTrigger
import com.aphrodite.core.llm.EndpointRepository
import com.aphrodite.core.llm.ResolvedEndpoint
import com.aphrodite.core.llm.agent.AgentLoop
import com.aphrodite.core.llm.model.LlmMessage
import com.aphrodite.core.llm.model.LlmRequest
import com.aphrodite.core.llm.model.LlmRole
import com.aphrodite.core.llm.stream.LlmEvent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes a task by running its prompt through the agent loop.
 *
 * Shared by the UI's "Run now" and the background worker so both produce the same run record and
 * update the same counters. A task that behaves differently depending on how it was triggered
 * would be untrustworthy.
 */
@Singleton
class TaskRunner @Inject constructor(
    private val taskDao: TaskDao,
    private val endpoints: EndpointRepository,
    private val agent: AgentLoop,
) {
    /** Runs [taskId] and records the outcome. Safe to call from a worker or the UI. */
    suspend fun run(taskId: String): TaskRunEntity? {
        val task = taskDao.getById(taskId) ?: return null
        val startedAt = System.currentTimeMillis()
        val runId = UUID.randomUUID().toString()

        val selection = resolveModel(task)
        if (selection == null) {
            return finish(task, runId, startedAt, null, "No model is configured. Add a provider first.")
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

        return finish(task, runId, startedAt, output.toString().ifBlank { null }, failure)
    }

    private suspend fun finish(
        task: TaskEntity,
        runId: String,
        startedAt: Long,
        output: String?,
        error: String?,
    ): TaskRunEntity {
        val finishedAt = System.currentTimeMillis()
        val run = TaskRunEntity(
            id = runId,
            taskId = task.id,
            status = if (error == null) "success" else "failed",
            output = output,
            error = error,
            startedAt = startedAt,
            finishedAt = finishedAt,
            durationMs = finishedAt - startedAt,
        )
        taskDao.insertRun(run)

        val now = System.currentTimeMillis()
        taskDao.upsert(
            task.copy(
                lastRunAt = now,
                runCount = task.runCount + 1,
                failureCount = task.failureCount + if (error == null) 0 else 1,
                nextRunAt = nextRunAt(task, now),
                updatedAt = now,
            ),
        )
        return run
    }

    private fun nextRunAt(task: TaskEntity, now: Long): Long? = when (task.triggerKind) {
        TaskTrigger.Interval -> now + (task.triggerValue?.toLongOrNull() ?: 60L) * 60_000L
        TaskTrigger.Manual -> null
        else -> now + 60 * 60_000L
    }

    private suspend fun resolveModel(task: TaskEntity): Pair<ResolvedEndpoint, String>? {
        val endpointId = task.endpointId
        if (endpointId != null) {
            val resolved = endpoints.resolve(endpointId)
            val model = task.model ?: endpoints.modelsFor(endpointId).firstOrNull()?.modelId
            if (resolved != null && model != null) return resolved to model
        }
        val first = endpoints.observeEnabledEndpoints().first().firstOrNull() ?: return null
        val model = endpoints.modelsFor(first.id).firstOrNull()?.modelId ?: return null
        val resolved = endpoints.resolve(first.id) ?: return null
        return resolved to model
    }
}

/**
 * Runs one due task in the background.
 *
 * Dependencies are taken from a Hilt entry point rather than `@HiltWorker`: that would require a
 * custom `WorkerFactory` and a `Configuration.Provider` on the Application, which is a lot of
 * wiring for one worker.
 */
class TaskWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TaskWorkerEntryPoint {
        fun taskRunner(): TaskRunner
        fun taskScheduler(): TaskScheduler
    }

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            TaskWorkerEntryPoint::class.java,
        )

        val task = runCatching { entryPoint.taskRunner().run(taskId) }.getOrNull()
        // A failure is recorded on the run; retrying would spend model calls on a broken task.
        return if (task == null) Result.failure() else Result.success()
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
    }
}

/**
 * Keeps background schedules in step with the database.
 *
 * WorkManager's floor for periodic work is 15 minutes, so a shorter interval is clamped rather
 * than silently never firing. Each task gets its own uniquely-named periodic work so cancelling
 * one does not disturb the others.
 */
@Singleton
class TaskScheduler @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val taskDao: TaskDao,
) {
    /** Re-enqueues or cancels the schedule for one task after it is saved or toggled. */
    suspend fun sync(taskId: String) {
        val task = taskDao.getById(taskId) ?: return
        val workManager = WorkManager.getInstance(context)
        val name = workName(taskId)

        if (!task.enabled || task.triggerKind != TaskTrigger.Interval) {
            workManager.cancelUniqueWork(name)
            return
        }

        val minutes = (task.triggerValue?.toLongOrNull() ?: 60L).coerceAtLeast(MIN_INTERVAL_MINUTES)
        val request = PeriodicWorkRequestBuilder<TaskWorker>(minutes, TimeUnit.MINUTES)
            .setInputData(androidx.work.workDataOf(TaskWorker.KEY_TASK_ID to taskId))
            .setConstraints(
                Constraints.Builder()
                    // Tasks usually need a model call, which needs the network.
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            name,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /** Rebuilds every schedule; used at startup so edits made while the app was closed apply. */
    suspend fun syncAll() {
        taskDao.observeAll().first().forEach { task -> sync(task.id) }
    }

    private fun workName(taskId: String): String = "task-$taskId"

    companion object {
        /** WorkManager refuses periodic work shorter than this. */
        const val MIN_INTERVAL_MINUTES = 15L
    }
}
