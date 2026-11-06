package com.aphrodite.feature.cookbook

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aphrodite.core.database.dao.LocalModelDao
import com.aphrodite.core.database.entity.LocalModelEntity
import com.aphrodite.core.database.entity.LocalModelStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class CookbookUiState(
    val models: List<LocalModelEntity> = emptyList(),
    val storageUsed: Long = 0,
    val url: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
) {
    val readyCount: Int get() = models.count { it.status == LocalModelStatus.Ready }
}

/**
 * On-device models.
 *
 * The list is honest about what a model is: a file. It can be downloaded, imported, inspected and
 * deleted, and the screen says plainly which part of the pipeline is in place and which is not,
 * rather than implying a model can be run when it cannot.
 */
@HiltViewModel
class CookbookViewModel @Inject constructor(
    private val dao: LocalModelDao,
    private val repository: LocalModelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CookbookUiState())
    val state: StateFlow<CookbookUiState> = _state.asStateFlow()

    private val downloads = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            dao.observeAll().collect { models -> _state.update { it.copy(models = models) } }
        }
        viewModelScope.launch {
            dao.observeTotalSize().collect { total -> _state.update { it.copy(storageUsed = total) } }
        }
        // A download cannot survive the process, so anything left mid-flight is stale. Saying so is
        // better than showing a progress bar that will never move.
        viewModelScope.launch {
            dao.failAllIncomplete(
                "Interrupted. Start it again to resume from where it stopped.",
                System.currentTimeMillis(),
            )
        }
    }

    fun onUrlChange(url: String) = _state.update { it.copy(url = url, error = null) }

    fun addFromUrl() {
        val url = _state.value.url.trim()
        if (url.isBlank()) {
            _state.update { it.copy(error = "Paste a link to a .gguf file.") }
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            _state.update { it.copy(error = "That does not look like a web address.") }
            return
        }

        val fileName = url.substringBefore('?').substringAfterLast('/')
        if (!fileName.endsWith(".gguf", ignoreCase = true)) {
            _state.update { it.copy(error = "The link must point at a .gguf file.") }
            return
        }

        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            dao.upsert(
                LocalModelEntity(
                    id = id,
                    name = fileName.removeSuffix(".gguf"),
                    fileName = fileName,
                    path = java.io.File(repository.modelsDirectory, fileName).absolutePath,
                    sizeBytes = 0,
                    status = LocalModelStatus.Downloading,
                    sourceUrl = url,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        _state.update { it.copy(url = "", busy = true) }

        downloads[id] = viewModelScope.launch {
            val result = repository.download(id, url)
            _state.update {
                it.copy(
                    busy = downloads.values.any { job -> job.isActive },
                    error = result.exceptionOrNull()?.message,
                    notice = if (result.isSuccess) "Downloaded." else null,
                )
            }
            downloads.remove(id)
        }
    }

    fun importFrom(uri: Uri, name: String?) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = repository.import(uri, name)
            _state.update {
                it.copy(
                    busy = false,
                    error = result.exceptionOrNull()?.message,
                    notice = result.getOrNull()?.let { "Imported." },
                )
            }
        }
    }

    fun delete(model: LocalModelEntity) {
        downloads[model.id]?.cancel()
        downloads.remove(model.id)
        viewModelScope.launch {
            repository.delete(model)
            dao.delete(model.id)
            _state.update { it.copy(notice = "Removed ${model.name}.") }
        }
    }

    fun cancelDownload(model: LocalModelEntity) {
        downloads[model.id]?.cancel()
        downloads.remove(model.id)
        viewModelScope.launch {
            dao.finish(
                model.id,
                LocalModelStatus.Failed,
                model.sizeBytes,
                model.downloadedBytes,
                "Cancelled.",
                System.currentTimeMillis(),
            )
        }
        _state.update { it.copy(notice = "Download cancelled. Starting it again resumes.") }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun dismissNotice() = _state.update { it.copy(notice = null) }
}
