package com.aphrodite.feature.gallery

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
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

/** Where an image came from. Determines whether it can be deleted from the app. */
enum class GallerySource { Device, Upload }

data class GalleryItem(
    val id: String,
    val uri: String,
    val name: String,
    val width: Int,
    val height: Int,
    val addedAt: Long,
    val source: GallerySource,
)

data class GalleryUiState(
    val items: List<GalleryItem> = emptyList(),
    val query: String = "",
    val loading: Boolean = true,
    val permissionGranted: Boolean = false,
    val openIndex: Int? = null,
    val editing: GalleryItem? = null,
    val error: String? = null,
) {
    val visible: List<GalleryItem>
        get() = if (query.isBlank()) items else items.filter { it.name.contains(query, ignoreCase = true) }

    val open: GalleryItem? get() = openIndex?.let { visible.getOrNull(it) }
}

/**
 * The device's images, plus anything uploaded into Aphrodite.
 *
 * Both sources appear in one grid because from the user's point of view there is one gallery; which
 * storage a picture happens to live in is an implementation detail. The source is still tracked,
 * because deleting from the app must not silently delete a photo from the device.
 */
@HiltViewModel
class GalleryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uploads: UploadDao,
) : ViewModel() {

    private val _state = MutableStateFlow(GalleryUiState())
    val state: StateFlow<GalleryUiState> = _state.asStateFlow()

    init {
        load(hasPermission())
    }

    /** READ_MEDIA_IMAGES from 33; older releases used the broad storage permission. */
    fun requiredPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasPermission(): Boolean =
        context.checkSelfPermission(requiredPermission()) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    fun onPermissionResult(granted: Boolean) = load(granted)

    fun refresh() = load(hasPermission())

    private fun load(granted: Boolean) {
        _state.update { it.copy(permissionGranted = granted, loading = true) }
        viewModelScope.launch {
            uploads.observeAll().collect { stored ->
                val fromApp = stored
                    .filter { it.mime.startsWith("image/") }
                    .map { upload ->
                        GalleryItem(
                            id = upload.id,
                            // File path, so Coil reads the blob directly rather than through a
                            // content provider that has no entry for app-private storage.
                            uri = upload.localPath,
                            name = upload.name,
                            width = upload.width ?: 0,
                            height = upload.height ?: 0,
                            addedAt = upload.createdAt,
                            source = GallerySource.Upload,
                        )
                    }
                val fromDevice = if (granted) deviceImages() else emptyList()
                _state.update {
                    it.copy(
                        items = (fromApp + fromDevice).sortedByDescending { item -> item.addedAt },
                        permissionGranted = granted,
                        loading = false,
                    )
                }
            }
        }
    }

    private suspend fun deviceImages(): List<GalleryItem> = withContext(Dispatchers.IO) {
        runCatching {
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.DATE_ADDED,
            )
            val items = mutableListOf<GalleryItem>()
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                while (cursor.moveToNext() && items.size < MAX_DEVICE_IMAGES) {
                    val id = cursor.getLong(idColumn)
                    items += GalleryItem(
                        id = id.toString(),
                        uri = ContentUris.withAppendedId(collection, id).toString(),
                        name = cursor.getString(nameColumn).orEmpty(),
                        width = cursor.getInt(widthColumn),
                        height = cursor.getInt(heightColumn),
                        // MediaStore stores seconds here, unlike every other timestamp in Android.
                        addedAt = cursor.getLong(dateColumn) * 1000L,
                        source = GallerySource.Device,
                    )
                }
            }
            items
        }.getOrDefault(emptyList())
    }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query, openIndex = null) }

    fun open(item: GalleryItem) = _state.update {
        it.copy(openIndex = it.visible.indexOf(item).takeIf { index -> index >= 0 })
    }

    fun close() = _state.update { it.copy(openIndex = null) }

    fun startEditing(item: GalleryItem) = _state.update { it.copy(editing = item, openIndex = null) }

    fun stopEditing() = _state.update { it.copy(editing = null) }

    fun showNext() = _state.update { current ->
        val index = current.openIndex ?: return@update current
        if (index + 1 >= current.visible.size) current else current.copy(openIndex = index + 1)
    }

    fun showPrevious() = _state.update { current ->
        val index = current.openIndex ?: return@update current
        if (index <= 0) current else current.copy(openIndex = index - 1)
    }

    /** Only app uploads can be removed; deleting a device photo is not this app's business. */
    fun delete(item: GalleryItem) {
        if (item.source != GallerySource.Upload) {
            _state.update { it.copy(error = "This image is on the device. Delete it from your gallery app.") }
            return
        }
        viewModelScope.launch {
            uploads.delete(item.id)
            _state.update { it.copy(openIndex = null) }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private companion object {
        /** A phone gallery can hold tens of thousands; this grid is not a file manager. */
        const val MAX_DEVICE_IMAGES = 2000
    }
}
