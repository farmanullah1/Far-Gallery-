package com.example.feature.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.provider.MediaStore
import com.example.core.common.FarResult
import com.example.core.common.farCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

enum class CollageAspect(val label: String, val ratio: Float) {
    Square("1:1", 1f),
    Portrait("4:5", 4f / 5f),
    Story("9:16", 9f / 16f),
    Wide("16:9", 16f / 9f),
}

enum class CollageBackground(val label: String, val colorInt: Int) {
    White("White", Color.WHITE),
    Slate("Slate", Color.rgb(28, 30, 34)),
    Black("Black", Color.BLACK),
}

data class CollageConfig(
    val aspect: CollageAspect = CollageAspect.Square,
    val spacingPx: Int = 20,
    val cornerRadiusPx: Float = 20f,
    val background: CollageBackground = CollageBackground.White,
    val longEdgePx: Int = 2048,
)

/**
 * Offline Multi-Photo Collage Engine supporting 2..9 photos in crisp responsive grids,
 * center-cropped cleanly and exported to Pictures/Far Gallery with EXIF stripped.
 */
object CollageEngine {

    const val MIN_PHOTOS = 2
    const val MAX_PHOTOS = 9

    fun outputDimensions(aspect: CollageAspect, longEdgePx: Int = 2048): Pair<Int, Int> {
        val edge = longEdgePx.coerceIn(720, 4096)
        return if (aspect.ratio >= 1f) {
            edge to (edge / aspect.ratio).roundToInt().coerceAtLeast(1)
        } else {
            (edge * aspect.ratio).roundToInt().coerceAtLeast(1) to edge
        }
    }

    /** Pure layout geometry calculator for 2..9 tiles inside [width]x[height] with uniform [spacing]. */
    fun computeCells(count: Int, width: Int, height: Int, spacing: Int): List<RectF> {
        val n = count.coerceIn(MIN_PHOTOS, MAX_PHOTOS)
        val w = width.coerceAtLeast(64).toFloat()
        val h = height.coerceAtLeast(64).toFloat()
        val gap = spacing.coerceAtLeast(0).toFloat()

        val rowCounts: List<Int> = when (n) {
            2 -> if (w >= h) listOf(2) else listOf(1, 1)
            3 -> if (w >= h) listOf(1, 2) else listOf(1, 2)
            4 -> listOf(2, 2)
            5 -> listOf(2, 3)
            6 -> if (w >= h) listOf(3, 3) else listOf(2, 2, 2)
            7 -> listOf(2, 2, 3)
            8 -> listOf(3, 2, 3)
            else -> listOf(3, 3, 3)
        }

        val numRows = rowCounts.size
        val usableH = (h - gap * (numRows + 1)).coerceAtLeast(numRows.toFloat())
        val rowH = usableH / numRows

        val cells = ArrayList<RectF>(n)
        var y = gap
        rowCounts.forEach { cols ->
            val usableW = (w - gap * (cols + 1)).coerceAtLeast(cols.toFloat())
            val cellW = usableW / cols
            var x = gap
            repeat(cols) {
                cells += RectF(x, y, x + cellW, y + rowH)
                x += cellW + gap
            }
            y += rowH + gap
        }
        return cells
    }

    fun renderCollage(bitmaps: List<Bitmap>, config: CollageConfig = CollageConfig()): Bitmap {
        require(bitmaps.size >= MIN_PHOTOS) { "At least $MIN_PHOTOS bitmaps required" }
        val chosen = bitmaps.take(MAX_PHOTOS)
        val (outW, outH) = outputDimensions(config.aspect, config.longEdgePx)
        val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(config.background.colorInt)

        val cells = computeCells(chosen.size, outW, outH, config.spacingPx)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val radius = if (config.spacingPx > 0) config.cornerRadiusPx.coerceAtLeast(0f) else 0f

        chosen.forEachIndexed { idx, src ->
            val rect = cells.getOrNull(idx) ?: return@forEachIndexed
            val matrix = centerCropMatrix(src.width, src.height, rect)
            val saveCount = canvas.save()
            if (radius > 0f) {
                val clipPath = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
                canvas.clipPath(clipPath)
            } else {
                canvas.clipRect(rect)
            }
            canvas.drawBitmap(src, matrix, paint)
            canvas.restoreToCount(saveCount)
        }
        return result
    }

    suspend fun createAndSaveCollage(
        ctx: Context,
        uris: List<Uri>,
        config: CollageConfig = CollageConfig(),
    ): FarResult<Uri> = withContext(Dispatchers.IO) {
        farCatching {
            val sources = uris.take(MAX_PHOTOS)
            require(sources.size >= MIN_PHOTOS) { "Select at least 2 photos for a collage" }
            val decodeMax = (config.longEdgePx / 2).coerceIn(640, 1280)
            val bitmaps = ArrayList<Bitmap>(sources.size)
            try {
                sources.forEach { uri ->
                    when (val loaded = EditorEngine.load(ctx, uri, decodeMax)) {
                        is FarResult.Ok -> bitmaps += loaded.value
                        is FarResult.Err -> throw IllegalStateException("decode failed (${loaded.error.code})")
                    }
                }
                val outBmp = renderCollage(bitmaps, config)
                try {
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val name = "FAR_COLLAGE_$stamp.jpg"
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, name)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Far Gallery")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val resolver = ctx.contentResolver
                    val outUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: throw java.io.IOException("MediaStore insert returned null")
                    try {
                        resolver.openOutputStream(outUri)?.use { os ->
                            if (!outBmp.compress(Bitmap.CompressFormat.JPEG, 92, os)) {
                                throw java.io.IOException("compress returned false")
                            }
                        } ?: throw java.io.IOException("openOutputStream returned null")
                        resolver.update(
                            outUri,
                            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                            null,
                            null,
                        )
                    } catch (e: Throwable) {
                        runCatching { resolver.delete(outUri, null, null) }
                        throw e
                    }
                    outUri
                } finally {
                    outBmp.recycle()
                }
            } finally {
                bitmaps.forEach { if (!it.isRecycled) it.recycle() }
            }
        }
    }

    private fun centerCropMatrix(srcW: Int, srcH: Int, dst: RectF): Matrix {
        val sw = srcW.coerceAtLeast(1).toFloat()
        val sh = srcH.coerceAtLeast(1).toFloat()
        val dw = dst.width().coerceAtLeast(1f)
        val dh = dst.height().coerceAtLeast(1f)
        val scale = max(dw / sw, dh / sh)
        val dx = dst.left + (dw - sw * scale) * 0.5f
        val dy = dst.top + (dh - sh * scale) * 0.5f
        return Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
    }
}
