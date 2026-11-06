package com.aphrodite.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aphrodite.core.common.result.AppResult
import com.aphrodite.core.database.entity.ModelEndpointEntity
import com.aphrodite.core.llm.EndpointRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Drives the provider configuration panel. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val endpoints: EndpointRepository,
    private val presetDao: com.aphrodite.core.database.dao.PresetDao,
    private val mcp: com.aphrodite.core.llm.mcp.McpManager,
) : ViewModel() {

    private val _services = MutableStateFlow(ServicesState())
    val services: StateFlow<ServicesState> = _services.asStateFlow()

    private val _presets = MutableStateFlow(PresetsState())
    val presets: StateFlow<PresetsState> = _presets.asStateFlow()

    private val _mcpState = MutableStateFlow(McpState())
    val mcpState: StateFlow<McpState> = _mcpState.asStateFlow()

    private var selectedEndpointId: String? = null

    init {
        viewModelScope.launch {
            mcp.observeServers().collect { servers -> _mcpState.update { it.copy(servers = servers) } }
        }
        viewModelScope.launch {
            mcp.observeAllTools().collect { tools -> _mcpState.update { it.copy(tools = tools) } }
        }
        viewModelScope.launch {
            mcp.status.collect { status -> _mcpState.update { it.copy(status = status) } }
        }
        viewModelScope.launch {
            presetDao.observeAll().collect { list ->
                _presets.update { it.copy(presets = list) }
            }
        }
        viewModelScope.launch {
            endpoints.observeEndpoints().collect { list ->
                _services.update { it.copy(endpoints = list) }
                // Follow the selected endpoint so its model list stays current.
                selectedEndpointId
                    ?.takeIf { id -> list.any { it.id == id } }
                    ?.let { id -> loadModels(id) }
            }
        }
    }

    fun startAdd() {
        _services.update {
            it.copy(
                editing = EndpointDraft(kind = com.aphrodite.core.database.entity.ProviderKind.OpenAi),
                message = null,
                error = null,
            )
        }
    }

    fun startEdit(endpoint: ModelEndpointEntity) {
        _services.update {
            it.copy(
                editing = EndpointDraft(
                    id = endpoint.id,
                    name = endpoint.name,
                    kind = endpoint.kind,
                    baseUrl = endpoint.baseUrl,
                    apiKey = "",
                    hasStoredKey = endpoint.apiKeyRef != null,
                    supportsTools = endpoint.supportsTools,
                    supportsVision = endpoint.supportsVision,
                    supportsReasoning = endpoint.supportsReasoning,
                ),
                message = null,
                error = null,
            )
        }
    }

    fun onDraftChange(draft: EndpointDraft) =
        _services.update { it.copy(editing = draft, error = null) }

    fun cancelEdit() = _services.update { it.copy(editing = null, error = null) }

    fun save() {
        val draft = _services.value.editing ?: return
        _services.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            val result = endpoints.save(
                id = draft.id,
                name = draft.name,
                kind = draft.kind,
                baseUrl = draft.baseUrl,
                // A blank field on an edit means "keep what is stored".
                apiKey = if (draft.id == null) draft.apiKey.ifBlank { null } else draft.apiKey.ifBlank { null },
                supportsTools = draft.supportsTools,
                supportsVision = draft.supportsVision,
                supportsReasoning = draft.supportsReasoning,
                maxContext = null,
            )
            when (result) {
                is AppResult.Success -> {
                    _services.update {
                        it.copy(
                            busy = false,
                            editing = null,
                            message = "Saved ${result.value.name}. Fetching models…",
                        )
                    }
                    // Discovering models straight away saves a second step.
                    refreshModels(result.value.id)
                }
                is AppResult.Failure ->
                    _services.update { it.copy(busy = false, error = result.error.message) }
            }
        }
    }

    fun delete(endpointId: String) {
        viewModelScope.launch {
            endpoints.delete(endpointId)
            if (selectedEndpointId == endpointId) selectedEndpointId = null
            _services.update { it.copy(message = "Provider removed", error = null) }
        }
    }

    fun toggleEnabled(endpointId: String, enabled: Boolean) {
        viewModelScope.launch { endpoints.setEnabled(endpointId, enabled) }
    }

    fun refreshModels(endpointId: String) {
        _services.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val result = endpoints.refreshModels(endpointId)) {
                is AppResult.Success -> {
                    selectedEndpointId = endpointId
                    loadModels(endpointId)
                    _services.update {
                        it.copy(busy = false, message = "Found ${result.value} models", error = null)
                    }
                }
                is AppResult.Failure ->
                    _services.update { it.copy(busy = false, error = result.error.message) }
            }
        }
    }

    fun selectModels(endpointId: String) {
        selectedEndpointId = endpointId
        loadModels(endpointId)
    }

    fun toggleFavourite(modelRowId: String, favourite: Boolean) {
        viewModelScope.launch { endpoints.setFavourite(modelRowId, favourite) }
    }

    fun onModelQueryChange(value: String) = _services.update { it.copy(modelQuery = value) }

    // ── Presets ──────────────────────────────────────────────────────────────

    fun activatePreset(id: String) {
        viewModelScope.launch {
            presetDao.setActive(id)
            _presets.update { it.copy(notice = "Active preset changed. New turns use it.") }
        }
    }

    fun editPreset(preset: com.aphrodite.core.database.entity.PresetEntity) {
        _presets.update {
            it.copy(
                draft = PresetDraft(
                    id = preset.id,
                    name = preset.name,
                    description = preset.description.orEmpty(),
                    systemPrompt = preset.systemPrompt,
                    temperature = preset.temperature?.toFloat() ?: 0.7f,
                    maxOutputTokens = preset.maxOutputTokens?.toString().orEmpty(),
                    isBuiltin = preset.isBuiltin,
                ),
                notice = null,
            )
        }
    }

    fun addPreset() = _presets.update {
        it.copy(
            draft = PresetDraft(
                id = null,
                name = "New preset",
                systemPrompt = com.aphrodite.core.llm.SystemConstitution.TEXT,
            ),
            notice = null,
        )
    }

    fun onPresetDraftChange(draft: PresetDraft) = _presets.update { it.copy(draft = draft) }

    fun cancelPresetEdit() = _presets.update { it.copy(draft = null) }

    fun savePreset() {
        val draft = _presets.value.draft ?: return
        if (draft.name.isBlank() || draft.systemPrompt.isBlank()) return
        viewModelScope.launch {
            val existing = draft.id?.let { presetDao.getById(it) }
            val now = System.currentTimeMillis()
            presetDao.upsert(
                com.aphrodite.core.database.entity.PresetEntity(
                    id = existing?.id ?: com.aphrodite.core.llm.PresetSeeder.newCustomId(),
                    name = draft.name.trim(),
                    description = draft.description.takeIf { it.isNotBlank() },
                    systemPrompt = draft.systemPrompt,
                    personaNotes = existing?.personaNotes,
                    memorySchema = existing?.memorySchema,
                    // Only send temperature when it differs from the default, so a provider's
                    // own default is preserved otherwise.
                    temperature = draft.temperature.takeIf { it != 0.7f }?.toDouble(),
                    topP = existing?.topP,
                    maxOutputTokens = draft.maxOutputTokens.toIntOrNull(),
                    thinkingMode = existing?.thinkingMode,
                    reasoningEffort = existing?.reasoningEffort,
                    showPersonaName = existing?.showPersonaName ?: false,
                    isBuiltin = existing?.isBuiltin ?: false,
                    enabled = existing?.enabled ?: false,
                    icon = existing?.icon,
                    color = existing?.color,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
            )
            _presets.update { it.copy(draft = null, notice = "Preset saved.") }
        }
    }

    fun deletePreset(id: String) {
        viewModelScope.launch {
            presetDao.deleteCustom(id)
            _presets.update { it.copy(notice = "Preset removed.") }
        }
    }

    // ── MCP ──────────────────────────────────────────────────────────────────

    fun startAddMcpServer() = _mcpState.update { it.copy(draft = McpDraft()) }

    fun onMcpDraftChange(draft: McpDraft) = _mcpState.update { it.copy(draft = draft) }

    fun cancelMcpEdit() = _mcpState.update { it.copy(draft = null) }

    fun saveMcpServer() {
        val draft = _mcpState.value.draft ?: return
        if (draft.name.isBlank() || draft.url.isBlank()) return
        viewModelScope.launch {
            val id = mcp.addServer(
                name = draft.name,
                transport = draft.transport,
                url = draft.url,
                bearerToken = draft.token.ifBlank { null },
            )
            _mcpState.update { it.copy(draft = null) }
            // Connect straight away so the result is visible rather than a pending list entry.
            mcp.refresh(id)
        }
    }

    fun deleteMcpServer(id: String) {
        viewModelScope.launch { mcp.deleteServer(id) }
    }

    fun toggleMcpServer(id: String, enabled: Boolean) {
        viewModelScope.launch { mcp.setEnabled(id, enabled) }
    }

    fun refreshMcpServer(id: String) {
        viewModelScope.launch { mcp.refresh(id) }
    }

    fun toggleMcpExpanded(id: String) = _mcpState.update { state ->
        state.copy(expanded = if (id in state.expanded) state.expanded - id else state.expanded + id)
    }

    fun dismissMessages() = _services.update { it.copy(message = null, error = null) }

    private fun loadModels(endpointId: String) {
        viewModelScope.launch {
            endpoints.observeModels(endpointId).collect { models ->
                _services.update { it.copy(models = models) }
            }
        }
    }
}
