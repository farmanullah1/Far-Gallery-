package com.example.feature.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix as AColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.common.FarResult
import com.example.core.common.farCatching
import com.example.core.media.HistogramData
import com.example.core.media.HistogramEngine
import com.example.core.media.HistogramOverlay
import com.example.ui.components.FarFilterPill
import com.example.ui.feedback.ErrorAction
import com.example.ui.feedback.ErrorCard
import com.example.ui.feedback.ErrorInfo
import com.example.ui.glass.GlassKind
import com.example.ui.glass.glass
import com.example.ui.theme.FarSpacing
import com.example.ui.theme.LocalHapticsEnabled
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.glass.FarGlassState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

enum class FilterPreset(
    val labelRes: Int,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val warmth: Float,
    val vignette: Float = 0f,
) {
    Original(R.string.editor_preset_original, 0f, 0f, 0f, 0f, 0f),
    Vivid(R.string.editor_preset_vivid, 0.05f, 0.16f, 0.34f, 0.04f, 0f),
    WarmFilm(R.string.editor_preset_warm_film, 0.04f, 0.08f, -0.08f, 0.28f, 0.14f),
    Matte(R.string.editor_preset_matte, 0.08f, -0.18f, -0.14f, 0.06f, 0.10f),
    CoolCrisp(R.string.editor_preset_cool_crisp, 0.02f, 0.18f, 0.10f, -0.24f, 0f),
    Noir(R.string.editor_preset_noir, 0f, 0.28f, -1f, 0f, 0.24f),
    DocumentScan(R.string.editor_preset_doc_scan, 0.08f, 0.42f, -0.88f, -0.06f, 0f),
    GoldenHour(R.string.editor_preset_golden_hour, 0.05f, 0.14f, 0.22f, 0.34f, 0.18f),
}

/** One immutable snapshot per undo step. Replaying it on the source bitmap is the whole edit model (non-destructive). */
@Immutable
data class EditState(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val vignette: Float = 0f,
    val rotationQuarter: Int = 0,
    val flipH: Boolean = false,
    val straightenDeg: Float = 0f,
    val cropRatio: Float? = null,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    val preset: String = FilterPreset.Original.name,
) {
    fun hasCustomCrop(): Boolean =
        cropLeft > 0.005f || cropTop > 0.005f || cropRight < 0.995f || cropBottom < 0.995f

    fun toJson(): String = JSONObject().apply {
        put("b", brightness.toDouble())
        put("c", contrast.toDouble())
        put("s", saturation.toDouble())
        put("w", warmth.toDouble())
        put("v", vignette.toDouble())
        put("r", rotationQuarter)
        put("f", flipH)
        put("sd", straightenDeg.toDouble())
        if (cropRatio != null) put("crop", cropRatio.toDouble()) else put("crop", JSONObject.NULL)
        put("cl", cropLeft.toDouble())
        put("ct", cropTop.toDouble())
        put("cr", cropRight.toDouble())
        put("cb", cropBottom.toDouble())
        put("preset", preset)
    }.toString()

    companion object {
        fun fromJson(json: String?): EditState? {
            if (json.isNullOrBlank()) return null
            return runCatching {
                val obj = JSONObject(json)
                val cl = obj.optDouble("cl", 0.0).toFloat().coerceIn(0f, 0.9f)
                val ct = obj.optDouble("ct", 0.0).toFloat().coerceIn(0f, 0.9f)
                val cr = obj.optDouble("cr", 1.0).toFloat().coerceIn((cl + 0.1f).coerceAtMost(1f), 1f)
                val cb = obj.optDouble("cb", 1.0).toFloat().coerceIn((ct + 0.1f).coerceAtMost(1f), 1f)
                EditState(
                    brightness = obj.optDouble("b", 0.0).toFloat().coerceIn(-1f, 1f),
                    contrast = obj.optDouble("c", 0.0).toFloat().coerceIn(-1f, 1f),
                    saturation = obj.optDouble("s", 0.0).toFloat().coerceIn(-1f, 1f),
                    warmth = obj.optDouble("w", 0.0).toFloat().coerceIn(-1f, 1f),
                    vignette = obj.optDouble("v", 0.0).toFloat().coerceIn(0f, 1f),
                    rotationQuarter = obj.optInt("r", 0).mod(4),
                    flipH = obj.optBoolean("f", false),
                    straightenDeg = obj.optDouble("sd", 0.0).toFloat().coerceIn(-45f, 45f),
                    cropRatio = if (obj.isNull("crop")) null else obj.optDouble("crop").toFloat(),
                    cropLeft = cl,
                    cropTop = ct,
                    cropRight = cr,
                    cropBottom = cb,
                    preset = obj.optString("preset", FilterPreset.Original.name),
                )
            }.getOrNull()
        }
    }
}

class EditHistory(initial: EditState = EditState()) {
    private val undo = ArrayDeque<EditState>()
    private val redo = ArrayDeque<EditState>()
    var current by mutableStateOf(initial)
        private set
    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    fun commit(s: EditState) {
        if (s != current) {
            undo.addLast(current)
            redo.clear()
            current = s
        }
    }

    fun undo() {
        undo.removeLastOrNull()?.let {
            redo.addLast(current)
            current = it
        }
    }

    fun redo() {
        redo.removeLastOrNull()?.let {
            undo.addLast(current)
            current = it
        }
    }

    fun reset() {
        if (current != EditState()) commit(EditState())
    }
}

/** 4x5 colour matrix shared by preview and export so what you see is what you save. */
fun EditState.matrixValues(): FloatArray {
    val m = AColorMatrix()
    val c = 1f + contrast
    val t = (1f - c) / 2f * 255f
    m.postConcat(
        AColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
    val b = brightness * 255f
    m.postConcat(
        AColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, b,
                0f, 1f, 0f, 0f, b,
                0f, 0f, 1f, 0f, b,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
    val s = AColorMatrix().apply { setSaturation((1f + saturation).coerceAtLeast(0f)) }
    m.postConcat(s)
    val w = warmth * 40f
    m.postConcat(
        AColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, w,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, -w,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
    return m.array
}

object EditorEngine {
    /** Bounded decode (long edge <= maxEdge) so editing never allocates an OOM bitmap by surprise. */
    suspend fun load(ctx: Context, uri: Uri, maxEdge: Int): FarResult<Bitmap> = withContext(Dispatchers.IO) {
        farCatching {
            val src = ImageDecoder.createSource(ctx.contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { d, info, _ ->
                val longEdge = max(info.size.width, info.size.height)
                if (longEdge > maxEdge) {
                    val f = maxEdge.toFloat() / longEdge
                    d.setTargetSize((info.size.width * f).toInt().coerceAtLeast(1), (info.size.height * f).toInt().coerceAtLeast(1))
                }
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                d.isMutableRequired = false
            }
        }
    }

    fun straightenScale(w: Int, h: Int, deg: Float): Float {
        if (abs(deg) <= 0.05f || w <= 0 || h <= 0) return 1f
        val rad = Math.toRadians(abs(deg).toDouble())
        val cosA = abs(cos(rad)).toFloat()
        val sinA = abs(sin(rad)).toFloat()
        val sx = cosA + (h.toFloat() / w.toFloat()) * sinA
        val sy = cosA + (w.toFloat() / h.toFloat()) * sinA
        return max(sx, sy).coerceAtLeast(1f)
    }

    fun renderGeometryOnly(src: Bitmap, s: EditState, applyCrop: Boolean = true): Bitmap {
        val m = Matrix().apply {
            postRotate(90f * s.rotationQuarter)
            if (s.flipH) postScale(-1f, 1f)
        }
        var out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (abs(s.straightenDeg) > 0.05f) {
            val w = out.width
            val h = out.height
            val scale = straightenScale(w, h, s.straightenDeg)
            val straightened = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val sm = Matrix().apply {
                postTranslate(-w / 2f, -h / 2f)
                postRotate(s.straightenDeg)
                postScale(scale, scale)
                postTranslate(w / 2f, h / 2f)
            }
            Canvas(straightened).drawBitmap(out, sm, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            if (out !== src) out.recycle()
            out = straightened
        }
        if (applyCrop) {
            if (s.hasCustomCrop()) {
                val w = out.width
                val h = out.height
                val leftPx = (s.cropLeft.coerceIn(0f, 0.9f) * w).roundToInt().coerceIn(0, (w - 1).coerceAtLeast(0))
                val topPx = (s.cropTop.coerceIn(0f, 0.9f) * h).roundToInt().coerceIn(0, (h - 1).coerceAtLeast(0))
                val rightPx = (s.cropRight.coerceIn(s.cropLeft + 0.1f, 1f) * w).roundToInt().coerceIn(leftPx + 1, w)
                val bottomPx = (s.cropBottom.coerceIn(s.cropTop + 0.1f, 1f) * h).roundToInt().coerceIn(topPx + 1, h)
                val cropped = Bitmap.createBitmap(out, leftPx, topPx, (rightPx - leftPx).coerceAtLeast(1), (bottomPx - topPx).coerceAtLeast(1))
                if (out !== src) out.recycle()
                out = cropped
            } else {
                s.cropRatio?.let { r ->
                    val w = out.width
                    val h = out.height
                    val cw: Int
                    val ch: Int
                    if (w.toFloat() / h > r) {
                        ch = h
                        cw = (h * r).toInt().coerceAtLeast(1)
                    } else {
                        cw = w
                        ch = (w / r).toInt().coerceAtLeast(1)
                    }
                    val cropped = Bitmap.createBitmap(out, ((w - cw) / 2).coerceAtLeast(0), ((h - ch) / 2).coerceAtLeast(0), cw, ch)
                    if (out !== src) out.recycle()
                    out = cropped
                }
            }
        }
        return out
    }

    fun render(src: Bitmap, s: EditState): Bitmap {
        val out = renderGeometryOnly(src, s, applyCrop = true)
        val result = Bitmap.createBitmap(out.width, out.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(
            out,
            0f,
            0f,
            Paint().apply { colorFilter = ColorMatrixColorFilter(AColorMatrix(s.matrixValues())) },
        )
        if (out !== src && out !== result) out.recycle()
        if (s.vignette > 0.01f) {
            val radius = max(result.width, result.height) * 0.72f
            val edgeAlpha = (s.vignette.coerceIn(0f, 1f) * 185).toInt()
            val edgeColor = android.graphics.Color.argb(edgeAlpha, 0, 0, 0)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    result.width / 2f,
                    result.height / 2f,
                    radius.coerceAtLeast(1f),
                    intArrayOf(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT, edgeColor),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, result.width.toFloat(), result.height.toFloat(), paint)
        }
        return result
    }

    fun buildPresetThumbnails(src: Bitmap, thumbSizePx: Int = 72): Map<FilterPreset, ImageBitmap> {
        val ratio = thumbSizePx.toFloat() / max(src.width, src.height).coerceAtLeast(1)
        val w = (src.width * ratio).toInt().coerceAtLeast(1)
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        val mini = Bitmap.createScaledBitmap(src, w, h, true)
        return try {
            FilterPreset.entries.associateWith { preset ->
                val state = EditState(
                    brightness = preset.brightness,
                    contrast = preset.contrast,
                    saturation = preset.saturation,
                    warmth = preset.warmth,
                    vignette = preset.vignette,
                    preset = preset.name,
                )
                render(mini, state).asImageBitmap()
            }
        } finally {
            mini.recycle()
        }
    }

    /** Save a high-res COPY to Pictures/Far Gallery. Never touches the original; strips GPS/EXIF by default. */
    suspend fun saveCopy(
        ctx: Context,
        sourceUri: Uri?,
        fallbackPreview: Bitmap,
        s: EditState,
        quality: Int = 94,
        stripExif: Boolean = true,
    ): FarResult<Uri> = withContext(Dispatchers.IO) {
        farCatching {
            val hiRes = sourceUri?.let { (load(ctx, it, 4096) as? FarResult.Ok)?.value }
            val srcToRender = hiRes ?: fallbackPreview
            val bmp = render(srcToRender, s)
            if (hiRes != null && hiRes !== fallbackPreview) hiRes.recycle()
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "EDIT_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Far Gallery")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val r = ctx.contentResolver
            val out = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: throw java.io.IOException("insert failed")
            try {
                val outStream = r.openOutputStream(out) ?: throw java.io.IOException("output stream unavailable")
                outStream.use {
                    if (!bmp.compress(Bitmap.CompressFormat.JPEG, quality, it)) throw java.io.IOException("encode failed")
                }
                if (!stripExif && sourceUri != null) {
                    runCatching {
                        val srcExif = r.openInputStream(sourceUri)?.use { ExifInterface(it) }
                        r.openFileDescriptor(out, "rw")?.use { pfd ->
                            val dstExif = ExifInterface(pfd.fileDescriptor)
                            listOf(
                                ExifInterface.TAG_MAKE,
                                ExifInterface.TAG_MODEL,
                                ExifInterface.TAG_F_NUMBER,
                                ExifInterface.TAG_EXPOSURE_TIME,
                                ExifInterface.TAG_FOCAL_LENGTH,
                                ExifInterface.TAG_DATETIME_ORIGINAL,
                            ).forEach { tag ->
                                srcExif?.getAttribute(tag)?.let { v -> dstExif.setAttribute(tag, v) }
                            }
                            dstExif.saveAttributes()
                        }
                    }
                }
                r.update(out, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                r.delete(out, null, null)
                throw e
            } finally {
                bmp.recycle()
            }
            out
        }
    }

    fun renderBeforeAfterSplit(src: Bitmap, s: EditState): Bitmap {
        val beforeBmp = renderGeometryOnly(src, s, applyCrop = true)
        val afterBmp = render(src, s)
        val w = afterBmp.width.coerceAtLeast(2)
        val h = afterBmp.height.coerceAtLeast(1)
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val splitX = w / 2
        val rightWidth = (w - splitX).coerceAtLeast(1)

        val beforeSlice = Bitmap.createBitmap(beforeBmp, 0, 0, splitX.coerceAtLeast(1), h)
        val afterSlice = Bitmap.createBitmap(afterBmp, splitX, 0, rightWidth, h)
        canvas.drawBitmap(beforeSlice, 0f, 0f, null)
        canvas.drawBitmap(afterSlice, splitX.toFloat(), 0f, null)
        beforeSlice.recycle()
        afterSlice.recycle()

        val dividerWidth = max(2f, w * 0.004f)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(140, 0, 0, 0)
            strokeWidth = dividerWidth + 2f
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            strokeWidth = dividerWidth
        }
        canvas.drawLine(splitX.toFloat(), 0f, splitX.toFloat(), h.toFloat(), shadowPaint)
        canvas.drawLine(splitX.toFloat(), 0f, splitX.toFloat(), h.toFloat(), linePaint)

        if (beforeBmp !== src && beforeBmp !== afterBmp) beforeBmp.recycle()
        if (afterBmp !== src) afterBmp.recycle()
        return result
    }

    /** Save an EXIF-stripped Before/After split JPEG to Pictures/Far Gallery. Never modifies original. */
    suspend fun saveBeforeAfterSplitCopy(
        ctx: Context,
        sourceUri: Uri?,
        fallbackPreview: Bitmap,
        s: EditState,
        quality: Int = 94,
        stripExif: Boolean = true,
    ): FarResult<Uri> = withContext(Dispatchers.IO) {
        farCatching {
            val hiRes = sourceUri?.let { (load(ctx, it, 4096) as? FarResult.Ok)?.value }
            val srcToRender = hiRes ?: fallbackPreview
            val bmp = renderBeforeAfterSplit(srcToRender, s)
            if (hiRes != null && hiRes !== fallbackPreview) hiRes.recycle()
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "SPLIT_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Far Gallery")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val r = ctx.contentResolver
            val out = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: throw java.io.IOException("insert failed")
            try {
                val outStream = r.openOutputStream(out) ?: throw java.io.IOException("output stream unavailable")
                outStream.use {
                    if (!bmp.compress(Bitmap.CompressFormat.JPEG, quality, it)) throw java.io.IOException("encode failed")
                }
                if (!stripExif && sourceUri != null) {
                    runCatching {
                        val srcExif = r.openInputStream(sourceUri)?.use { ExifInterface(it) }
                        r.openFileDescriptor(out, "rw")?.use { pfd ->
                            val dstExif = ExifInterface(pfd.fileDescriptor)
                            listOf(
                                ExifInterface.TAG_MAKE,
                                ExifInterface.TAG_MODEL,
                                ExifInterface.TAG_F_NUMBER,
                                ExifInterface.TAG_EXPOSURE_TIME,
                                ExifInterface.TAG_FOCAL_LENGTH,
                                ExifInterface.TAG_DATETIME_ORIGINAL,
                            ).forEach { tag ->
                                srcExif?.getAttribute(tag)?.let { v -> dstExif.setAttribute(tag, v) }
                            }
                            dstExif.saveAttributes()
                        }
                    }
                }
                r.update(out, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                r.delete(out, null, null)
                throw e
            } finally {
                bmp.recycle()
            }
            out
        }
    }
}

internal fun centeredNormalizedCrop(imgW: Int, imgH: Int, targetAspect: Float): FloatArray {
    val safeW = imgW.coerceAtLeast(1)
    val safeH = imgH.coerceAtLeast(1)
    val safeAspect = targetAspect.coerceAtLeast(0.05f)
    val imgAspect = safeW.toFloat() / safeH.toFloat()
    val (nw, nh) = if (imgAspect > safeAspect) {
        (safeAspect / imgAspect).coerceIn(0.1f, 1f) to 1f
    } else {
        1f to (imgAspect / safeAspect).coerceIn(0.1f, 1f)
    }
    val dx = (1f - nw) / 2f
    val dy = (1f - nh) / 2f
    return floatArrayOf(dx, dy, 1f - dx, 1f - dy)
}

enum class EditTab(val labelRes: Int) {
    Adjust(R.string.editor_tab_adjust),
    Presets(R.string.editor_tab_presets),
    Crop(R.string.editor_tab_crop),
    Rotate(R.string.editor_tab_rotate),
}

@Composable
fun EditorScreen(
    source: Bitmap?,
    loading: Boolean,
    errorText: String?,
    glass: FarGlassState,
    saving: Boolean,
    onCancel: () -> Unit,
    onSave: (EditState, Boolean) -> Unit,
    initialState: EditState? = null,
    stripExifDefault: Boolean = true,
    onRetry: (() -> Unit)? = null,
    copiedRecipe: EditState? = null,
    onCopyRecipe: ((EditState) -> Unit)? = null,
    onPasteRecipe: (() -> EditState?)? = null,
    onSaveSplit: ((EditState, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val history = remember(initialState) { EditHistory(initialState ?: EditState()) }
    var tab by remember { mutableStateOf(EditTab.Adjust) }
    var comparing by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var stripExif by remember(stripExifDefault) { mutableStateOf(stripExifDefault) }
    var showHistogram by remember { mutableStateOf(false) }
    var histogram by remember { mutableStateOf<HistogramData?>(null) }
    var presetThumbs by remember(source) { mutableStateOf<Map<FilterPreset, ImageBitmap>>(emptyMap()) }
    val haptic = LocalHapticFeedback.current
    val hapticsOn = LocalHapticsEnabled.current
    val s = history.current
    val dirty = s != EditState()

    LaunchedEffect(source) {
        if (source != null) {
            presetThumbs = withContext(Dispatchers.Default) {
                runCatching { EditorEngine.buildPresetThumbnails(source, 72) }.getOrDefault(emptyMap())
            }
        } else {
            presetThumbs = emptyMap()
        }
    }

    LaunchedEffect(source, s, comparing, showHistogram) {
        if (showHistogram && source != null) {
            delay(100)
            val activeMatrix = if (comparing) EditState().matrixValues() else s.matrixValues()
            histogram = HistogramEngine.computeAsync(source, activeMatrix)
        } else {
            histogram = null
        }
    }

    val requestExit = {
        if (dirty && !saving) confirmDiscard = true else onCancel()
    }
    BackHandler(onBack = requestExit)

    Box(modifier.fillMaxSize().background(Color.Black)) {
        when {
            loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            errorText != null -> ErrorCard(
                ErrorInfo(
                    title = errorText,
                    whatHappened = stringResource(R.string.err_corrupt_summary),
                    originalsSafe = true,
                    actions = buildList {
                        onRetry?.let { add(ErrorAction(stringResource(R.string.action_retry), true, it)) }
                        add(ErrorAction(stringResource(R.string.action_close), onRetry == null, onCancel))
                    },
                ),
                Modifier.align(Alignment.Center),
            )
            source != null -> {
                val active = if (comparing) EditState() else s
                val applyCropNow = tab != EditTab.Crop && !comparing
                val displayBitmap = remember(
                    source,
                    active.rotationQuarter,
                    active.flipH,
                    active.straightenDeg,
                    active.cropRatio,
                    active.cropLeft,
                    active.cropTop,
                    active.cropRight,
                    active.cropBottom,
                    applyCropNow,
                ) {
                    EditorEngine.renderGeometryOnly(source, active, applyCrop = applyCropNow)
                }
                val aspect = (displayBitmap.width.toFloat() / displayBitmap.height.coerceAtLeast(1).toFloat()).coerceIn(0.25f, 4f)
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .padding(top = 96.dp, bottom = 286.dp)
                        .pointerInput(tab) {
                            detectTapGestures(
                                onPress = {
                                    if (tab != EditTab.Crop) {
                                        comparing = true
                                        tryAwaitRelease()
                                        comparing = false
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val fitHeightFirst = maxWidth / maxHeight.coerceAtLeast(1.dp) > aspect
                    Box(
                        Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .aspectRatio(aspect, matchHeightConstraintsFirst = fitHeightFirst)
                        ) {
                            Image(
                                bitmap = displayBitmap.asImageBitmap(),
                                contentDescription = if (comparing) "Original photo" else "Edited photo",
                                contentScale = ContentScale.Fit,
                                colorFilter = ColorFilter.colorMatrix(ColorMatrix(active.matrixValues())),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .drawWithContent {
                                        drawContent()
                                        if (active.vignette > 0.01f) {
                                            val radius = max(size.width, size.height) * 0.72f
                                            drawRect(
                                                brush = Brush.radialGradient(
                                                    0f to Color.Transparent,
                                                    0.45f to Color.Transparent,
                                                    1f to Color.Black.copy(alpha = (active.vignette * 0.72f).coerceIn(0f, 0.75f)),
                                                    radius = radius.coerceAtLeast(1f),
                                                )
                                            )
                                        }
                                    },
                            )
                            if (tab == EditTab.Crop && !comparing) {
                                CropHandlesOverlay(
                                    left = s.cropLeft,
                                    top = s.cropTop,
                                    right = s.cropRight,
                                    bottom = s.cropBottom,
                                    onCommitCrop = { l, t, r, b ->
                                        history.commit(
                                            s.copy(
                                                cropRatio = null,
                                                cropLeft = l,
                                                cropTop = t,
                                                cropRight = r,
                                                cropBottom = b,
                                            )
                                        )
                                    },
                                )
                            }
                        }
                    }
                    if (comparing) {
                        Text(
                            "ORIGINAL",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                                .background(Color.Black.copy(0.65f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = FarSpacing.screenPadding, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .glass(glass, RoundedCornerShape(26.dp), GlassKind.Overlay)
                    .border(0.75.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(26.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = requestExit,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("editor_cancel_button"),
                ) { Text(stringResource(R.string.action_cancel), color = Color.White) }
                Row {
                    TextButton(
                        onClick = history::undo,
                        enabled = history.canUndo,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    ) { Text(stringResource(R.string.editor_undo), color = Color.White) }
                    TextButton(
                        onClick = history::redo,
                        enabled = history.canRedo,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    ) { Text(stringResource(R.string.editor_redo), color = Color.White) }
                }
                TextButton(
                    onClick = { onSave(s, stripExif) },
                    enabled = source != null && !saving,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("editor_save_button"),
                ) {
                    Text(
                        stringResource(if (saving) R.string.editor_saving else R.string.editor_save_copy),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (initialState != null && s == initialState) {
                Text(
                    "Saved recipe restored",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .glass(glass, RoundedCornerShape(14.dp), GlassKind.Chip)
                        .border(0.75.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }

            if (showHistogram && source != null) {
                HistogramOverlay(
                    data = histogram,
                    loading = histogram == null,
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .testTag("editor_histogram_overlay"),
                )
            }
        }

        val semantic = LocalSemanticColors.current
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = FarSpacing.screenPadding, vertical = 12.dp)
                    .glass(glass, RoundedCornerShape(28.dp), GlassKind.Sheet)
                    .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(28.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (tab) {
                    EditTab.Adjust -> {
                        AdjustSlider(stringResource(R.string.editor_brightness), s.brightness) {
                            history.commit(s.copy(brightness = it))
                        }
                        AdjustSlider(stringResource(R.string.editor_contrast), s.contrast) {
                            history.commit(s.copy(contrast = it))
                        }
                        AdjustSlider(stringResource(R.string.editor_saturation), s.saturation) {
                            history.commit(s.copy(saturation = it))
                        }
                        AdjustSlider(stringResource(R.string.editor_warmth), s.warmth) {
                            history.commit(s.copy(warmth = it))
                        }
                        AdjustSlider(
                            label = stringResource(R.string.editor_vignette),
                            value = s.vignette,
                            range = 0f..1f,
                        ) {
                            history.commit(s.copy(vignette = it))
                        }
                    }
                    EditTab.Presets -> LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(FilterPreset.entries) { p ->
                            val active = s.preset == p.name
                            val thumb = presetThumbs[p]
                            Column(
                                Modifier
                                    .defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
                                    .border(
                                        width = if (active) 1.5.dp else 0.75.dp,
                                        color = if (active) MaterialTheme.colorScheme.primary else semantic.cardBorder,
                                        shape = RoundedCornerShape(14.dp),
                                    )
                                    .clickable {
                                        if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        history.commit(
                                            s.copy(
                                                brightness = p.brightness,
                                                contrast = p.contrast,
                                                saturation = p.saturation,
                                                warmth = p.warmth,
                                                vignette = p.vignette,
                                                preset = p.name,
                                            )
                                        )
                                    }
                                    .padding(6.dp)
                                    .testTag("preset_${p.name.lowercase()}"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (thumb != null) {
                                    Image(
                                        bitmap = thumb,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(10.dp)),
                                    )
                                }
                                Text(
                                    text = stringResource(p.labelRes),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    EditTab.Crop -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LazyRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val ratios = listOf(
                                Triple(null as Float?, R.string.editor_preset_original, ""),
                                Triple(1f, null, "1:1"),
                                Triple(4f / 3f, null, "4:3"),
                                Triple(1.5f, null, "3:2"),
                                Triple(16f / 9f, null, "16:9"),
                                Triple(35f / 45f, R.string.editor_crop_passport, ""),
                                Triple(9f / 16f, R.string.editor_crop_wallpaper, ""),
                            )
                            items(ratios.size) { idx ->
                                val (r, res, fixedLabel) = ratios[idx]
                                val label = if (res != null) stringResource(res) else fixedLabel
                                TextButton(
                                    onClick = {
                                        if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (r == null) {
                                            history.commit(s.copy(cropRatio = null, cropLeft = 0f, cropTop = 0f, cropRight = 1f, cropBottom = 1f))
                                        } else {
                                            val orientedW = if (s.rotationQuarter % 2 == 1) (source?.height ?: 1) else (source?.width ?: 1)
                                            val orientedH = if (s.rotationQuarter % 2 == 1) (source?.width ?: 1) else (source?.height ?: 1)
                                            val box = centeredNormalizedCrop(orientedW, orientedH, r)
                                            history.commit(
                                                s.copy(
                                                    cropRatio = r,
                                                    cropLeft = box[0],
                                                    cropTop = box[1],
                                                    cropRight = box[2],
                                                    cropBottom = box[3],
                                                )
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("editor_crop_ratio_$idx"),
                                ) {
                                    Text(label, color = if (s.cropRatio == r) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                    EditTab.Rotate -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AdjustSlider(
                            label = "${stringResource(R.string.editor_straighten)} ${"%+.1f°".format(s.straightenDeg)}",
                            value = s.straightenDeg,
                            range = -45f..45f,
                        ) {
                            history.commit(s.copy(straightenDeg = it))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            TextButton(
                                onClick = {
                                    if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    history.commit(s.copy(rotationQuarter = (s.rotationQuarter + 3) % 4))
                                },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            ) { Text(stringResource(R.string.editor_rotate_left), color = MaterialTheme.colorScheme.onSurface) }
                            TextButton(
                                onClick = {
                                    if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    history.commit(s.copy(rotationQuarter = (s.rotationQuarter + 1) % 4))
                                },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            ) { Text(stringResource(R.string.editor_rotate_right), color = MaterialTheme.colorScheme.onSurface) }
                            TextButton(
                                onClick = {
                                    if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    history.commit(s.copy(flipH = !s.flipH))
                                },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            ) { Text(stringResource(R.string.editor_flip), color = MaterialTheme.colorScheme.onSurface) }
                            if (abs(s.straightenDeg) > 0.05f) {
                                TextButton(
                                    onClick = { history.commit(s.copy(straightenDeg = 0f)) },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("editor_reset_straighten"),
                                ) { Text("0°", color = MaterialTheme.colorScheme.primary) }
                            }
                        }
                    }
                }
                LazyRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (dirty && onCopyRecipe != null) {
                        item(key = "copy_recipe") {
                            FarFilterPill(
                                label = stringResource(R.string.editor_copy_edits),
                                selected = false,
                                onClick = { onCopyRecipe(s) },
                                modifier = Modifier.testTag("editor_copy_recipe_chip"),
                            )
                        }
                    }
                    if (dirty && !saving && onSaveSplit != null) {
                        item(key = "save_split") {
                            FarFilterPill(
                                label = stringResource(R.string.editor_save_split),
                                selected = false,
                                onClick = {
                                    if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSaveSplit(s, stripExif)
                                },
                                modifier = Modifier.testTag("editor_save_split_chip"),
                            )
                        }
                    }
                    if (copiedRecipe != null && onPasteRecipe != null) {
                        item(key = "paste_recipe") {
                            FarFilterPill(
                                label = stringResource(R.string.editor_paste_edits),
                                selected = true,
                                onClick = {
                                    onPasteRecipe()?.let { pasted ->
                                        if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        history.commit(pasted)
                                    }
                                },
                                modifier = Modifier.testTag("editor_paste_recipe_chip"),
                            )
                        }
                    }
                    item(key = "histogram_toggle") {
                        FarFilterPill(
                            label = stringResource(R.string.viewer_histogram),
                            selected = showHistogram,
                            onClick = { showHistogram = !showHistogram },
                            modifier = Modifier.testTag("editor_histogram_chip"),
                        )
                    }
                    item(key = "strip_exif_toggle") {
                        FarFilterPill(
                            label = stringResource(if (stripExif) R.string.editor_strip_exif_on else R.string.editor_strip_exif_off),
                            selected = stripExif,
                            onClick = { stripExif = !stripExif },
                            modifier = Modifier.testTag("editor_strip_exif_chip"),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    EditTab.entries.forEach { t ->
                        TextButton(
                            onClick = { tab = t },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        ) {
                            Text(
                                stringResource(t.labelRes),
                                color = if (t == tab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    if (dirty) {
                        TextButton(
                            onClick = {
                                if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                history.reset()
                            },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        ) { Text(stringResource(R.string.editor_reset), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            text = { Text(stringResource(R.string.editor_discard_body)) },
            confirmButton = {
                TextButton(
                    onClick = { confirmDiscard = false; onCancel() },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) { Text(stringResource(R.string.editor_discard)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDiscard = false },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) { Text(stringResource(R.string.editor_keep)) }
            },
        )
    }
}

@Composable
private fun CropHandlesOverlay(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    onCommitCrop: (Float, Float, Float, Float) -> Unit,
) {
    var l by remember(left, top, right, bottom) { mutableFloatStateOf(left) }
    var t by remember(left, top, right, bottom) { mutableFloatStateOf(top) }
    var r by remember(left, top, right, bottom) { mutableFloatStateOf(right) }
    var b by remember(left, top, right, bottom) { mutableFloatStateOf(bottom) }
    var activeCorner by remember { mutableStateOf<Int?>(null) } // 0=TL,1=TR,2=BL,3=BR
    val minSpan = 0.15f

    Canvas(
        Modifier
            .fillMaxSize()
            .testTag("editor_crop_overlay")
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { start ->
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val corners = listOf(
                            Offset(l * w, t * h),
                            Offset(r * w, t * h),
                            Offset(l * w, b * h),
                            Offset(r * w, b * h),
                        )
                        activeCorner = corners.indices.minByOrNull { idx ->
                            hypot((corners[idx].x - start.x).toDouble(), (corners[idx].y - start.y).toDouble())
                        }
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val dx = drag.x / w
                        val dy = drag.y / h
                        when (activeCorner) {
                            0 -> {
                                l = (l + dx).coerceIn(0f, r - minSpan)
                                t = (t + dy).coerceIn(0f, b - minSpan)
                            }
                            1 -> {
                                r = (r + dx).coerceIn(l + minSpan, 1f)
                                t = (t + dy).coerceIn(0f, b - minSpan)
                            }
                            2 -> {
                                l = (l + dx).coerceIn(0f, r - minSpan)
                                b = (b + dy).coerceIn(t + minSpan, 1f)
                            }
                            3 -> {
                                r = (r + dx).coerceIn(l + minSpan, 1f)
                                b = (b + dy).coerceIn(t + minSpan, 1f)
                            }
                        }
                    },
                    onDragEnd = {
                        activeCorner = null
                        onCommitCrop(l, t, r, b)
                    },
                    onDragCancel = {
                        activeCorner = null
                    },
                )
            }
    ) {
        val x1 = l * size.width
        val y1 = t * size.height
        val x2 = r * size.width
        val y2 = b * size.height
        val bw = (x2 - x1).coerceAtLeast(1f)
        val bh = (y2 - y1).coerceAtLeast(1f)
        val shade = Color.Black.copy(alpha = 0.52f)

        // Outer dimmed frame
        if (y1 > 0f) drawRect(shade, Offset.Zero, Size(size.width, y1))
        if (y2 < size.height) drawRect(shade, Offset(0f, y2), Size(size.width, size.height - y2))
        if (x1 > 0f) drawRect(shade, Offset(0f, y1), Size(x1, bh))
        if (x2 < size.width) drawRect(shade, Offset(x2, y1), Size(size.width - x2, bh))

        // Border & rule-of-thirds
        drawRect(Color.White, Offset(x1, y1), Size(bw, bh), style = Stroke(width = 2.dp.toPx()))
        val gridPaint = Color.White.copy(alpha = 0.45f)
        drawLine(gridPaint, Offset(x1 + bw / 3f, y1), Offset(x1 + bw / 3f, y2), 1.dp.toPx())
        drawLine(gridPaint, Offset(x1 + 2f * bw / 3f, y1), Offset(x1 + 2f * bw / 3f, y2), 1.dp.toPx())
        drawLine(gridPaint, Offset(x1, y1 + bh / 3f), Offset(x2, y1 + bh / 3f), 1.dp.toPx())
        drawLine(gridPaint, Offset(x1, y1 + 2f * bh / 3f), Offset(x2, y1 + 2f * bh / 3f), 1.dp.toPx())

        // Corner handles
        val handleLen = 18.dp.toPx()
        val stroke = 3.5.dp.toPx()
        // TL
        drawLine(Color.White, Offset(x1, y1), Offset(x1 + handleLen, y1), stroke)
        drawLine(Color.White, Offset(x1, y1), Offset(x1, y1 + handleLen), stroke)
        // TR
        drawLine(Color.White, Offset(x2, y1), Offset(x2 - handleLen, y1), stroke)
        drawLine(Color.White, Offset(x2, y1), Offset(x2, y1 + handleLen), stroke)
        // BL
        drawLine(Color.White, Offset(x1, y2), Offset(x1 + handleLen, y2), stroke)
        drawLine(Color.White, Offset(x1, y2), Offset(x1, y2 - handleLen), stroke)
        // BR
        drawLine(Color.White, Offset(x2, y2), Offset(x2 - handleLen, y2), stroke)
        drawLine(Color.White, Offset(x2, y2), Offset(x2, y2 - handleLen), stroke)
    }
}

@Composable
private fun AdjustSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float> = -1f..1f,
    onCommit: (Float) -> Unit,
) {
    var live by remember(value) { mutableFloatStateOf(value) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            Modifier.padding(end = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Slider(live, { live = it }, onValueChangeFinished = { onCommit(live) }, valueRange = range)
    }
}
