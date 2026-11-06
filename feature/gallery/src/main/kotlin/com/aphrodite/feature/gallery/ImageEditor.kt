package com.aphrodite.feature.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.aphrodite.core.database.entity.UploadEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Bitmap operations for the editor.
 *
 * Every operation takes a bitmap and returns a new one rather than mutating in place. That is what
 * makes undo trivial and correct: the operation list is replayed against the original, so a change
 * cannot accumulate rounding error or drift the way in-place edits do.
 */
object ImageEditor {

    /** Brightness, contrast and saturation, each neutral at 0 (or 1 for contrast and saturation). */
    data class Adjustments(
        val brightness: Float = 0f,
        val contrast: Float = 1f,
        val saturation: Float = 1f,
    ) {
        val isNeutral: Boolean
            get() = brightness == 0f && contrast == 1f && saturation == 1f
    }

    /** What the user has done, in order. Replayed against the source to produce the result. */
    sealed interface Operation {
        data class Rotate(val degrees: Int) : Operation
        data class Flip(val horizontal: Boolean) : Operation
        data class Crop(val ratio: Float?) : Operation
    }

    fun rotate(source: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun flip(source: Bitmap, horizontal: Boolean): Bitmap {
        val matrix = Matrix().apply {
            if (horizontal) postScale(-1f, 1f) else postScale(1f, -1f)
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Centres the largest rectangle of [ratio] that fits, then crops to it.
     *
     * Centre-cropping rather than freeform: the subject of a photo is nearly always in the middle,
     * and an arbitrary rectangle needs drag handles that are fiddly on a touch target the size of a
     * thumbnail. A fixed aspect ratio is the part people actually reach for.
     */
    fun crop(source: Bitmap, ratio: Float?): Bitmap {
        val target = ratio ?: return source
        val width = source.width
        val height = source.height
        if (width <= 0 || height <= 0) return source

        val current = width.toFloat() / height.toFloat()
        val (newWidth, newHeight) = if (current > target) {
            (height * target).roundToInt() to height
        } else {
            width to (width / target).roundToInt()
        }

        val clampedWidth = min(newWidth, width).coerceAtLeast(1)
        val clampedHeight = min(newHeight, height).coerceAtLeast(1)
        val left = (width - clampedWidth) / 2
        val top = (height - clampedHeight) / 2

        return Bitmap.createBitmap(source, left, top, clampedWidth, clampedHeight)
    }

    /** Applies brightness, contrast and saturation in one pass. */
    fun adjust(source: Bitmap, adjustments: Adjustments): Bitmap {
        if (adjustments.isNeutral) return source

        val matrix = ColorMatrix()

        if (adjustments.saturation != 1f) {
            val saturation = ColorMatrix().apply { setSaturation(adjustments.saturation) }
            matrix.postConcat(saturation)
        }

        if (adjustments.contrast != 1f || adjustments.brightness != 0f) {
            // Contrast pivots around mid-grey. Scaling without the offset drives the whole image
            // towards black or white instead of around the midpoint.
            val scale = adjustments.contrast
            val translate = (-0.5f * scale + 0.5f) * 255f + adjustments.brightness * 255f
            val tone = ColorMatrix(
                floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
            matrix.postConcat(tone)
        }

        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(
            source,
            0f,
            0f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) },
        )
        return output
    }

    /** Replays [operations] and [adjustments] against [source]. */
    fun render(source: Bitmap, operations: List<Operation>, adjustments: Adjustments): Bitmap {
        var current = source
        operations.forEach { operation ->
            current = when (operation) {
                is Operation.Rotate -> rotate(current, operation.degrees)
                is Operation.Flip -> flip(current, operation.horizontal)
                is Operation.Crop -> crop(current, operation.ratio)
            }
        }
        return adjust(current, adjustments)
    }

    // ── Loading and saving ───────────────────────────────────────────────────

    /**
     * Decodes an image, downscaling to [maxDimension].
     *
     * A modern phone camera produces 12 megapixels or more. Decoding several of those into ARGB
     * bitmaps while editing is how an image editor runs out of memory, and no preview needs more
     * than a couple of thousand pixels.
     */
    suspend fun load(context: Context, uri: String, maxDimension: Int = 2048): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val parsed = if (uri.startsWith("content://")) Uri.parse(uri) else null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.openSource(parsed, uri) { stream -> BitmapFactory.decodeStream(stream, null, bounds) }

                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / sample > maxDimension) sample *= 2

                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                context.openSource(parsed, uri) { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }?.let { decoded -> honourExif(context, parsed, uri, decoded) }
            }.getOrNull()
        }

    private fun <T> Context.openSource(
        parsed: Uri?,
        uri: String,
        block: (java.io.InputStream) -> T,
    ): T? = if (parsed != null) {
        contentResolver.openInputStream(parsed)?.use(block)
    } else {
        File(uri).inputStream().use(block)
    }

    /**
     * Applies the camera's rotation.
     *
     * Phone cameras record orientation in EXIF rather than rotating the pixels, so without this a
     * photo taken in portrait opens sideways. Editing it then bakes the wrong orientation in.
     */
    private fun honourExif(context: Context, parsed: Uri?, uri: String, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            val stream = if (parsed != null) {
                context.contentResolver.openInputStream(parsed)
            } else {
                File(uri).inputStream()
            }
            stream?.use { input ->
                when (androidx.exifinterface.media.ExifInterface(input).getAttributeInt(
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL,
                )) {
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)

        return if (degrees == 0) bitmap else rotate(bitmap, degrees)
    }

    /** Writes the result into app storage and returns the upload row describing it. */
    suspend fun save(
        context: Context,
        bitmap: Bitmap,
        name: String,
    ): Result<UploadEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(context.filesDir, "uploads").apply { mkdirs() }
            val bytes = java.io.ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
                stream.toByteArray()
            }
            val hash = bytes.sha256()
            val file = File(directory, "$hash.jpg")
            if (!file.exists()) file.writeBytes(bytes)

            val thumbnail = File(directory, "$hash-thumb.jpg")
            if (!thumbnail.exists()) {
                val scale = 320f / max(bitmap.width, bitmap.height).toFloat()
                val small = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                    (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                    true,
                )
                thumbnail.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                small.recycle()
            }

            UploadEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                mime = "image/jpeg",
                size = bytes.size.toLong(),
                sha256 = hash,
                localPath = file.absolutePath,
                thumbnailPath = thumbnail.absolutePath,
                width = bitmap.width,
                height = bitmap.height,
                createdAt = System.currentTimeMillis(),
            )
        }
    }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

    /** The size an image will end up as, so the editor can show it before saving. */
    fun predictSize(source: Bitmap, operations: List<Operation>): Pair<Int, Int> {
        var width = source.width
        var height = source.height
        operations.forEach { operation ->
            when (operation) {
                is Operation.Rotate -> if (operation.degrees % 180 != 0) {
                    val swap = width
                    width = height
                    height = swap
                }
                is Operation.Flip -> Unit
                is Operation.Crop -> {
                    val ratio = operation.ratio
                    if (ratio != null) {
                        val current = width.toFloat() / height.toFloat()
                        if (current > ratio) width = (height * ratio).roundToInt() else height = (width / ratio).roundToInt()
                    }
                }
            }
        }
        return width to height
    }

    /** Sizes a crop rectangle without stretching it, for preview overlays. */
    fun fitInside(sourceWidth: Int, sourceHeight: Int, bounds: RectF): RectF {
        if (sourceWidth <= 0 || sourceHeight <= 0) return bounds
        val scale = min(bounds.width() / sourceWidth, bounds.height() / sourceHeight)
        val width = sourceWidth * scale
        val height = sourceHeight * scale
        return RectF(
            bounds.left + (bounds.width() - width) / 2f,
            bounds.top + (bounds.height() - height) / 2f,
            bounds.left + (bounds.width() + width) / 2f,
            bounds.top + (bounds.height() + height) / 2f,
        )
    }
}
