package com.aphrodite.feature.gallery

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aphrodite.core.database.dao.UploadDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Aspect ratios offered for cropping. Null means keep the original shape. */
enum class CropRatio(val label: String, val ratio: Float?) {
    Original("Original", null),
    Square("Square", 1f),
    Wide("16:9", 16f / 9f),
    Classic("4:3", 4f / 3f),
    Portrait("3:4", 3f / 4f),
}

data class EditorUiState(
    val item: GalleryItem? = null,
    val preview: Bitmap? = null,
    val operations: List<ImageEditor.Operation> = emptyList(),
    val adjustments: ImageEditor.Adjustments = ImageEditor.Adjustments(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val savedName: String? = null,
    val outputWidth: Int = 0,
    val outputHeight: Int = 0,
    val error: String? = null,
) {
    val canUndo: Boolean get() = operations.isNotEmpty() || !adjustments.isNeutral
}

/**
 * Edits one image and saves the result as a new upload.
 *
 * Opens on a copy rather than editing in place. Overwriting the original would mean a crop is
 * unrecoverable, and the original is often the only copy of a photo.
 */
@HiltViewModel
class EditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uploads: UploadDao,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private var source: Bitmap? = null

    fun load(item: GalleryItem) {
        if (_state.value.item?.uri == item.uri) return
        _state.value = EditorUiState(item = item, loading = true)
        viewModelScope.launch {
            val decoded = ImageEditor.load(context, item.uri)
            if (decoded == null) {
                _state.update { it.copy(loading = false, error = "That image could not be opened.") }
                return@launch
            }
            source = decoded
            render()
        }
    }

    fun rotate(degrees: Int) = addOperation(ImageEditor.Operation.Rotate(degrees))

    fun flip(horizontal: Boolean) = addOperation(ImageEditor.Operation.Flip(horizontal))

    /** Replaces any existing crop rather than stacking one on another. */
    fun crop(ratio: Float?) {
        _state.update { current ->
            val withoutCrop = current.operations.filterNot { it is ImageEditor.Operation.Crop }
            current.copy(operations = withoutCrop + ImageEditor.Operation.Crop(ratio))
        }
        render()
    }

    private fun addOperation(operation: ImageEditor.Operation) {
        _state.update { it.copy(operations = it.operations + operation) }
        render()
    }

    fun undo() {
        _state.update { current ->
            when {
                current.operations.isNotEmpty() -> current.copy(operations = current.operations.dropLast(1))
                else -> current.copy(adjustments = ImageEditor.Adjustments())
            }
        }
        render()
    }

    fun reset() {
        _state.update { it.copy(operations = emptyList(), adjustments = ImageEditor.Adjustments()) }
        render()
    }

    fun onBrightness(value: Float) = updateAdjustments { it.copy(brightness = value) }

    fun onContrast(value: Float) = updateAdjustments { it.copy(contrast = value) }

    fun onSaturation(value: Float) = updateAdjustments { it.copy(saturation = value) }

    private fun updateAdjustments(transform: (ImageEditor.Adjustments) -> ImageEditor.Adjustments) {
        _state.update { it.copy(adjustments = transform(it.adjustments)) }
        render()
    }

    /**
     * Recomputes the preview.
     *
     * Replayed from the untouched source every time rather than applied to the previous preview.
     * Editing the previous result would compound rounding at every step until the image visibly
     * degraded, and undoing would not truly restore what was there before.
     */
    private fun render() {
        val original = source ?: return
        val operations = _state.value.operations
        val adjustments = _state.value.adjustments
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                ImageEditor.render(original, operations, adjustments)
            }
            val (width, height) = ImageEditor.predictSize(original, operations)
            _state.update {
                it.copy(
                    preview = result,
                    loading = false,
                    outputWidth = width,
                    outputHeight = height,
                )
            }
        }
    }

    fun save() {
        val original = source ?: return
        val current = _state.value
        if (current.saving) return
        _state.update { it.copy(saving = true, error = null) }

        viewModelScope.launch {
            val rendered = withContext(Dispatchers.Default) {
                ImageEditor.render(original, current.operations, current.adjustments)
            }
            val baseName = current.item?.name?.substringBeforeLast('.') ?: "image"
            ImageEditor.save(context, rendered, "${baseName}_edited.jpg")
                .onSuccess { upload ->
                    uploads.upsert(upload)
                    _state.update { it.copy(saving = false, savedName = upload.name) }
                }
                .onFailure { error ->
                    _state.update { it.copy(saving = false, error = error.message ?: "Saving failed.") }
                }
        }
    }

    fun dismissSaved() = _state.update { it.copy(savedName = null) }

    fun dismissError() = _state.update { it.copy(error = null) }
}
