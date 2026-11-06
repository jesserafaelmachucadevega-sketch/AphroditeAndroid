package com.aphrodite.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Where an on-device model is in its life cycle. */
enum class LocalModelStatus(val id: String, val label: String) {
    Downloading("downloading", "Downloading"),
    Ready("ready", "Ready"),
    Failed("failed", "Failed"),
    ;

    companion object {
        fun fromId(id: String?): LocalModelStatus =
            entries.firstOrNull { it.id == id } ?: Failed
    }
}

/**
 * A GGUF model file held on the device.
 *
 * The file is the model, so the row is mostly bookkeeping: where it is, how big it should be, and
 * whether it arrived intact. [downloadedBytes] is tracked separately from [sizeBytes] because a
 * download that stops halfway is the common case on a phone, and resuming needs to know where it
 * got to.
 */
@Entity(
    tableName = "local_models",
    indices = [Index("status"), Index(value = ["path"], unique = true)],
)
@Serializable
data class LocalModelEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    val path: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "downloaded_bytes") val downloadedBytes: Long = 0,
    val status: LocalModelStatus = LocalModelStatus.Ready,
    /** Null when the file was imported from device storage rather than downloaded. */
    @ColumnInfo(name = "source_url") val sourceUrl: String? = null,
    /** Read out of the GGUF header, so the list can show something meaningful. */
    val quantization: String? = null,
    @ColumnInfo(name = "parameters_b") val parametersB: Double? = null,
    @ColumnInfo(name = "context_length") val contextLength: Int? = null,
    val error: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
