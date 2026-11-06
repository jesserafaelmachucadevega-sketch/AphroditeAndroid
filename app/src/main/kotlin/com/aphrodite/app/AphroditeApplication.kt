package com.aphrodite.app

import android.app.Application
import com.aphrodite.core.llm.PresetSeeder
import com.aphrodite.core.llm.mcp.McpManager
import com.aphrodite.feature.tasks.TaskScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AphroditeApplication : Application() {

    @Inject
    lateinit var mcpManager: McpManager

    @Inject
    lateinit var presetSeeder: PresetSeeder

    @Inject
    lateinit var taskScheduler: TaskScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Connect configured MCP servers in the background. Failure is per-server and never
        // blocks startup: an unreachable server must not stop the app from opening.
        scope.launch {
            runCatching { presetSeeder.seedIfEmpty() }
            runCatching { mcpManager.refreshAll() }
            // Rebuild background schedules: a task may have been edited while the app was closed.
            runCatching { taskScheduler.syncAll() }
        }
    }
}
