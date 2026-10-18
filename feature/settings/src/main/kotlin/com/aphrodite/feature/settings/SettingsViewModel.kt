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
) : ViewModel() {

    private val _services = MutableStateFlow(ServicesState())
    val services: StateFlow<ServicesState> = _services.asStateFlow()

    private var selectedEndpointId: String? = null

    init {
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

    fun dismissMessages() = _services.update { it.copy(message = null, error = null) }

    private fun loadModels(endpointId: String) {
        viewModelScope.launch {
            endpoints.observeModels(endpointId).collect { models ->
                _services.update { it.copy(models = models) }
            }
        }
    }
}
