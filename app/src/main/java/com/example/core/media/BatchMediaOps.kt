package com.example.core.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import com.example.core.common.FarError
import com.example.core.common.FarResult
import com.example.core.common.LocalDiagnostics
import com.example.core.common.farCatching
import com.example.core.common.userTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.max

enum class ResizePreset(val label: String, val maxLongEdge: Int?, val scalePercent: Int?) {
    Edge2048("2048 px", 2048, null),
    Edge1600("1600 px", 1600, null),
    Edge1080("1080 px", 1080, null),
    Half("50%", null, 50),
}

data class BatchResizeConfig(
    val preset: ResizePreset = ResizePreset.Edge1600,
    val useWebp: Boolean = false,
    val quality: Int = 88,
    val stripExif: Boolean = true,
)

data class BatchOpSummary(
    val succeeded: Int,
    val skipped: Int,
    val failed: Int,
    val firstError: FarError? = null,
)

object BatchMediaOps {

    fun buildRenamedName(originalName: String, prefix: String, number: Int, zeroPad: Int = 3): String {
        val cleanPrefix = prefix.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifEmpty { "FAR" }
        val ext = originalName.substringAfterLast('.', "").lowercase()
        val seq = number.coerceAtLeast(1).toString().padStart(zeroPad, '0')
        return if (ext.isNotEmpty() && originalName.contains('.')) "${cleanPrefix}_$seq.$ext" else "${cleanPrefix}_$seq"
    }

    suspend fun resizeBatch(
        ctx: Context,
        rows: List<MediaRow>,
        config: BatchResizeConfig,
        onProgress: (done: Int, total: Int, currentName: String) -> Unit,
    ): BatchOpSummary = withContext(Dispatchers.IO) {
        val resolver = ctx.contentResolver
        var succeeded = 0
        var skipped = 0
        var failed = 0
        var firstErr: FarError? = null

        rows.forEachIndexed { idx, row ->
            currentCoroutineContext().ensureActive()
            onProgress(idx, rows.size, row.name)
            if (row.isVideo) {
                skipped++
                return@forEachIndexed
            }
            val r = farCatching {
                val srcExif = if (!config.stripExif) {
                    runCatching {
                        resolver.openInputStream(row.uri)?.use { ExifInterface(it) }
                    }.getOrNull()
                } else null

                val src = ImageDecoder.createSource(resolver, row.uri)
                val bmp = ImageDecoder.decodeBitmap(src) { d, info, _ ->
                    val w = info.size.width.coerceAtLeast(1)
                    val h = info.size.height.coerceAtLeast(1)
                    val longEdge = max(w, h)
                    val targetScale = when {
                        config.preset.maxLongEdge != null && longEdge > config.preset.maxLongEdge ->
                            config.preset.maxLongEdge.toFloat() / longEdge
                        config.preset.scalePercent != null ->
                            (config.preset.scalePercent / 100f).coerceIn(0.1f, 1f)
                        else -> 1f
                    }
                    if (targetScale < 0.999f) {
                        d.setTargetSize(
                            (w * targetScale).toInt().coerceAtLeast(1),
                            (h * targetScale).toInt().coerceAtLeast(1),
                        )
                    }
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    d.isMutableRequired = false
                }

                try {
                    val base = row.name.substringBeforeLast('.').take(36).ifBlank { "FAR_${row.id}" }
                    val ext = if (config.useWebp) "webp" else "jpg"
                    val mime = if (config.useWebp) "image/webp" else "image/jpeg"
                    val suffix = config.preset.maxLongEdge?.let { "_${it}px" } ?: "_${config.preset.scalePercent ?: 50}pct"
                    val outName = "${base}${suffix}_${System.currentTimeMillis() % 10000}.$ext"
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, outName)
                        put(MediaStore.Images.Media.MIME_TYPE, mime)
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Far Gallery")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val outUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: throw java.io.IOException("MediaStore insert returned null")
                    try {
                        resolver.openOutputStream(outUri)?.use { os ->
                            val format = if (config.useWebp) {
                                Bitmap.CompressFormat.WEBP_LOSSY
                            } else {
                                Bitmap.CompressFormat.JPEG
                            }
                            if (!bmp.compress(format, config.quality.coerceIn(40, 100), os)) {
                                throw java.io.IOException("Bitmap compress returned false")
                            }
                        } ?: throw java.io.IOException("openOutputStream returned null")

                        if (!config.stripExif && srcExif != null && !config.useWebp) {
                            copySafeExifTags(resolver, outUri, srcExif)
                        }
                        resolver.update(
                            outUri,
                            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                            null,
                            null,
                        )
                    } catch (e: Exception) {
                        runCatching { resolver.delete(outUri, null, null) }
                        throw e
                    }
                } finally {
                    bmp.recycle()
                }
            }
            when (r) {
                is FarResult.Ok -> succeeded++
                is FarResult.Err -> {
                    failed++
                    if (firstErr == null) firstErr = r.error
                    LocalDiagnostics.recordEvent(ctx, r.error.code, "batch_resize", r.error.userTitle())
                }
            }
        }
        onProgress(rows.size, rows.size, "")
        BatchOpSummary(succeeded = succeeded, skipped = skipped, failed = failed, firstError = firstErr)
    }

    suspend fun renameBatch(
        ctx: Context,
        rows: List<MediaRow>,
        prefix: String,
        startNumber: Int,
        onProgress: (done: Int, total: Int, currentName: String) -> Unit,
    ): BatchOpSummary = withContext(Dispatchers.IO) {
        val resolver = ctx.contentResolver
        var succeeded = 0
        var failed = 0
        var firstErr: FarError? = null

        rows.forEachIndexed { idx, row ->
            currentCoroutineContext().ensureActive()
            onProgress(idx, rows.size, row.name)
            val targetName = buildRenamedName(row.name, prefix, startNumber + idx)
            val r = farCatching {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, targetName)
                }
                val updated = resolver.update(row.uri, values, null, null)
                if (updated <= 0) throw java.io.IOException("rename updated 0 rows")
            }
            when (r) {
                is FarResult.Ok -> succeeded++
                is FarResult.Err -> {
                    failed++
                    if (firstErr == null) firstErr = r.error
                    LocalDiagnostics.recordEvent(ctx, r.error.code, "batch_rename", r.error.userTitle())
                }
            }
        }
        onProgress(rows.size, rows.size, "")
        BatchOpSummary(succeeded = succeeded, skipped = 0, failed = failed, firstError = firstErr)
    }

    private fun copySafeExifTags(resolver: android.content.ContentResolver, outUri: Uri, src: ExifInterface) {
        runCatching {
            resolver.openFileDescriptor(outUri, "rw")?.use { pfd ->
                val dst = ExifInterface(pfd.fileDescriptor)
                listOf(
                    ExifInterface.TAG_MAKE,
                    ExifInterface.TAG_MODEL,
                    ExifInterface.TAG_DATETIME_ORIGINAL,
                    ExifInterface.TAG_F_NUMBER,
                    ExifInterface.TAG_EXPOSURE_TIME,
                    ExifInterface.TAG_ISO_SPEED_RATINGS,
                    ExifInterface.TAG_FOCAL_LENGTH,
                ).forEach { tag ->
                    src.getAttribute(tag)?.let { dst.setAttribute(tag, it) }
                }
                dst.saveAttributes()
            }
        }
    }
}
