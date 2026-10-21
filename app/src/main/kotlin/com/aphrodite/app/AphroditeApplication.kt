package com.aphrodite.app

import android.app.Application
import com.aphrodite.core.llm.mcp.McpManager
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Connect configured MCP servers in the background. Failure is per-server and never
        // blocks startup: an unreachable server must not stop the app from opening.
        scope.launch {
            runCatching { mcpManager.refreshAll() }
        }
    }
}
