package com.aphrodite.feature.cookbook

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.aphrodite.core.database.dao.LocalModelDao
import com.aphrodite.core.database.entity.LocalModelEntity
import com.aphrodite.core.database.entity.LocalModelStatus
import com.aphrodite.core.network.HttpClientFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Downloads GGUF model files and validates them.
 *
 * Validation is not optional here. A model is hundreds of megabytes pulled over a phone connection,
 * and a truncated or error-page download that only fails at load time wastes the whole download and
 * is impossible to diagnose from the model list.
 */
@Singleton
class LocalModelRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: LocalModelDao,
    private val httpFactory: HttpClientFactory,
) {
    /** Problems found while reading the file, before it is trusted. */
    sealed interface Verification {
        data class Valid(val version: Int) : Verification
        data class Invalid(val reason: String) : Verification
    }

    val modelsDirectory: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    suspend fun download(modelId: String, url: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val record = dao.getById(modelId) ?: error("That download is no longer tracked.")
            val target = File(modelsDirectory, record.fileName)
            val partial = File(modelsDirectory, "${record.fileName}.part")

            // Resume from whatever arrived last time. Restarting a multi-gigabyte download because
            // the train went into a tunnel is the difference between usable and not.
            var existing = if (partial.exists()) partial.length() else 0L

            val request = Request.Builder()
                .url(normaliseUrl(url))
                .apply { if (existing > 0) header("Range", "bytes=$existing-") }
                .build()

            httpFactory.modelDownload.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error(
                        if (response.code == 416) {
                            // The range was rejected, so the partial file is unusable.
                            partial.delete()
                            "The saved part of this download was rejected. Try again."
                        } else {
                            "Download failed (HTTP ${response.code})."
                        },
                    )
                }

                val body = response.body ?: error("The server returned nothing.")
                val total = body.contentLength().let { length ->
                    if (length > 0) existing + length else 0L
                }
                dao.finish(modelId, LocalModelStatus.Downloading, total, existing, null, System.currentTimeMillis())

                body.byteStream().use { input ->
                    RandomAccessFile(partial, "rw").use { output ->
                        output.seek(existing)
                        val buffer = ByteArray(64 * 1024)
                        var sinceReport = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            existing += read
                            sinceReport += read
                            // Reported in blocks rather than per read: a row per 64KB would write
                            // to the database thousands of times for one model.
                            if (sinceReport >= 512 * 1024) {
                                sinceReport = 0
                                dao.recordProgress(modelId, existing, System.currentTimeMillis())
                            }
                        }
                    }
                }
            }

            when (val verification = verify(partial)) {
                is Verification.Invalid -> {
                    partial.delete()
                    error(verification.reason)
                }
                is Verification.Valid -> {
                    if (target.exists()) target.delete()
                    partial.renameTo(target)
                    dao.finish(
                        modelId,
                        LocalModelStatus.Ready,
                        target.length(),
                        target.length(),
                        null,
                        System.currentTimeMillis(),
                    )
                    dao.recordMetadata(
                        modelId,
                        guessQuantization(record.fileName),
                        guessParameters(record.fileName),
                        guessContextLength(record.fileName),
                        System.currentTimeMillis(),
                    )
                }
            }
        }.onFailure { error ->
            dao.finish(
                modelId,
                LocalModelStatus.Failed,
                dao.getById(modelId)?.sizeBytes ?: 0,
                dao.getById(modelId)?.downloadedBytes ?: 0,
                error.message ?: "The download failed.",
                System.currentTimeMillis(),
            )
            // The part file is kept on purpose so a retry resumes instead of starting over.
        }
    }

    /** Copies a model the user already has on the device. */
    suspend fun import(uri: Uri, displayName: String?): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val name = displayName ?: queryName(uri) ?: "model-${UUID.randomUUID()}.gguf"
            if (!name.endsWith(".gguf", ignoreCase = true)) {
                error("Only GGUF files can be used.")
            }
            val target = File(modelsDirectory, name)
            val id = UUID.randomUUID().toString()

            dao.upsert(
                LocalModelEntity(
                    id = id,
                    name = name.removeSuffix(".gguf"),
                    fileName = name,
                    path = target.absolutePath,
                    sizeBytes = 0,
                    status = LocalModelStatus.Downloading,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )

            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output, 256 * 1024) }
            } ?: error("That file could not be opened.")

            when (val verification = verify(target)) {
                is Verification.Invalid -> {
                    target.delete()
                    dao.delete(id)
                    error(verification.reason)
                }
                is Verification.Valid -> {
                    dao.finish(id, LocalModelStatus.Ready, target.length(), target.length(), null, System.currentTimeMillis())
                    dao.recordMetadata(
                        id,
                        guessQuantization(name),
                        guessParameters(name),
                        guessContextLength(name),
                        System.currentTimeMillis(),
                    )
                    id
                }
            }
        }
    }

    /**
     * Checks the GGUF header.
     *
     * Only the header. Reading the metadata tables would let the list show a model's real name and
     * architecture, but it means parsing an arbitrary key/value block that changes between format
     * versions. The magic and version are enough to tell a real model file from an HTML error page
     * or a truncated download, which is the failure that actually happens.
     */
    private fun verify(file: File): Verification {
        if (!file.exists() || file.length() < HEADER_BYTES) {
            return Verification.Invalid("The file is too small to be a model — the download did not complete.")
        }
        return runCatching {
            file.inputStream().use { stream ->
                val magic = ByteArray(4).also { stream.read(it) }
                if (String(magic, Charsets.US_ASCII) != "GGUF") {
                    Verification.Invalid("This is not a GGUF model file.")
                } else {
                    val version = ByteArray(4).also { stream.read(it) }
                    Verification.Valid(
                        (version[0].toInt() and 0xFF) or ((version[1].toInt() and 0xFF) shl 8) or
                            ((version[2].toInt() and 0xFF) shl 16) or ((version[3].toInt() and 0xFF) shl 24),
                    )
                }
            }
        }.getOrElse { Verification.Invalid(it.message ?: "The file could not be read.") }
    }

    /** Accepts a file page URL as well as a direct link, since that is what people copy. */
    private fun normaliseUrl(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.contains("huggingface.co") && trimmed.contains("/blob/")) {
            trimmed.replace("/blob/", "/resolve/")
        } else {
            trimmed
        }
    }

    private fun queryName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()

    fun delete(model: LocalModelEntity) {
        runCatching {
            File(model.path).delete()
            File("${model.path}.part").delete()
        }
    }

    /**
     * Quantisation and parameter count read from the file name.
     *
     * Not from the file. GGUF names follow a convention that encodes both — `...-Q4_K_M.gguf`,
     * `...-7B-...` — and the convention is reliable enough in practice that parsing the metadata
     * block to learn the same thing would be work without a payoff.
     */
    private fun guessQuantization(name: String): String? =
        Regex("(?i)\\b(I?Q\\d(?:_[A-Z0-9]+)*|F16|F32|BF16)\\b").find(name)?.value?.uppercase()

    private fun guessParameters(name: String): Double? =
        Regex("(?i)\\b(\\d+(?:\\.\\d+)?)\\s*B\\b").find(name)?.groupValues?.get(1)?.toDoubleOrNull()

    private fun guessContextLength(name: String): Int? =
        Regex("(?i)\\b(\\d{3,6})\\s*(?:k)?\\s*ctx\\b").find(name)?.groupValues?.get(1)?.toIntOrNull()

    private companion object {
        /** Magic plus version. */
        const val HEADER_BYTES = 8L
    }
}
