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
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.media.ExifInterface
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
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.max

enum class FilterPreset(
    val label: String,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val warmth: Float,
) {
    Original("Original", 0f, 0f, 0f, 0f),
    Vivid("Vivid", 0.05f, 0.16f, 0.34f, 0.04f),
    WarmFilm("Warm Film", 0.04f, 0.08f, -0.08f, 0.28f),
    Matte("Matte", 0.08f, -0.18f, -0.14f, 0.06f),
    CoolCrisp("Cool Crisp", 0.02f, 0.18f, 0.10f, -0.24f),
    Noir("Noir B&W", 0f, 0.28f, -1f, 0f),
}

/** One immutable snapshot per undo step. Replaying it on the source bitmap is the whole edit model (non-destructive). */
@Immutable
data class EditState(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val rotationQuarter: Int = 0,
    val flipH: Boolean = false,
    val cropRatio: Float? = null,
    val preset: String = FilterPreset.Original.name,
) {
    fun toJson(): String = JSONObject().apply {
        put("b", brightness.toDouble())
        put("c", contrast.toDouble())
        put("s", saturation.toDouble())
        put("w", warmth.toDouble())
        put("r", rotationQuarter)
        put("f", flipH)
        if (cropRatio != null) put("crop", cropRatio.toDouble()) else put("crop", JSONObject.NULL)
        put("preset", preset)
    }.toString()

    companion object {
        fun fromJson(json: String?): EditState? {
            if (json.isNullOrBlank()) return null
            return runCatching {
                val obj = JSONObject(json)
                EditState(
                    brightness = obj.optDouble("b", 0.0).toFloat().coerceIn(-1f, 1f),
                    contrast = obj.optDouble("c", 0.0).toFloat().coerceIn(-1f, 1f),
                    saturation = obj.optDouble("s", 0.0).toFloat().coerceIn(-1f, 1f),
                    warmth = obj.optDouble("w", 0.0).toFloat().coerceIn(-1f, 1f),
                    rotationQuarter = obj.optInt("r", 0).mod(4),
                    flipH = obj.optBoolean("f", false),
                    cropRatio = if (obj.isNull("crop")) null else obj.optDouble("crop").toFloat(),
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

    fun render(src: Bitmap, s: EditState): Bitmap {
        val m = Matrix().apply {
            postRotate(90f * s.rotationQuarter)
            if (s.flipH) postScale(-1f, 1f)
        }
        var out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
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
            out = Bitmap.createBitmap(out, ((w - cw) / 2).coerceAtLeast(0), ((h - ch) / 2).coerceAtLeast(0), cw, ch)
        }
        val result = Bitmap.createBitmap(out.width, out.height, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(
            out,
            0f,
            0f,
            Paint().apply { colorFilter = ColorMatrixColorFilter(AColorMatrix(s.matrixValues())) },
        )
        return result
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
                r.openOutputStream(out)!!.use {
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

enum class EditTab(val label: String) { Adjust("Adjust"), Presets("Presets"), Crop("Crop"), Rotate("Rotate") }

@Composable
fun EditorScreen(
    source: Bitmap?,
    loading: Boolean,
    errorText: String?,
    glass: HazeState,
    saving: Boolean,
    onCancel: () -> Unit,
    onSave: (EditState, Boolean) -> Unit,
    initialState: EditState? = null,
    stripExifDefault: Boolean = true,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val history = remember(initialState) { EditHistory(initialState ?: EditState()) }
    var tab by remember { mutableStateOf(EditTab.Adjust) }
    var comparing by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var stripExif by remember(stripExifDefault) { mutableStateOf(stripExifDefault) }
    var showHistogram by remember { mutableStateOf(false) }
    var histogram by remember { mutableStateOf<HistogramData?>(null) }
    val haptic = LocalHapticFeedback.current
    val hapticsOn = LocalHapticsEnabled.current
    val s = history.current
    val dirty = s != EditState()

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
                val cropMod = if (active.cropRatio != null) {
                    Modifier.aspectRatio(active.cropRatio).clip(RoundedCornerShape(4.dp))
                } else {
                    Modifier.fillMaxSize()
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(top = 96.dp, bottom = 248.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    comparing = true
                                    tryAwaitRelease()
                                    comparing = false
                                }
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = source.asImageBitmap(),
                        contentDescription = if (comparing) "Original photo" else "Edited photo",
                        contentScale = if (active.cropRatio != null) ContentScale.Crop else ContentScale.Fit,
                        colorFilter = ColorFilter.colorMatrix(ColorMatrix(active.matrixValues())),
                        modifier = cropMod.graphicsLayer {
                            rotationZ = 90f * active.rotationQuarter
                            scaleX = if (active.flipH) -1f else 1f
                        },
                    )
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
                        AdjustSlider("Brightness", s.brightness) { history.commit(s.copy(brightness = it)) }
                        AdjustSlider("Contrast", s.contrast) { history.commit(s.copy(contrast = it)) }
                        AdjustSlider("Saturation", s.saturation) { history.commit(s.copy(saturation = it)) }
                        AdjustSlider("Warmth", s.warmth) { history.commit(s.copy(warmth = it)) }
                    }
                    EditTab.Presets -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(FilterPreset.entries) { p ->
                            val active = s.preset == p.name
                            FarFilterPill(
                                label = p.label,
                                selected = active,
                                onClick = {
                                    if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    history.commit(
                                        s.copy(
                                            brightness = p.brightness,
                                            contrast = p.contrast,
                                            saturation = p.saturation,
                                            warmth = p.warmth,
                                            preset = p.name,
                                        )
                                    )
                                },
                                modifier = Modifier.testTag("preset_${p.name.lowercase()}"),
                            )
                        }
                    }
                    EditTab.Crop -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("Original" to null, "1:1" to 1f, "4:3" to 4f / 3f, "3:2" to 1.5f, "16:9" to 16f / 9f).forEach { (l, r) ->
                            TextButton(
                                onClick = {
                                    if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    history.commit(s.copy(cropRatio = r))
                                },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            ) {
                                Text(l, color = if (s.cropRatio == r) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                    EditTab.Rotate -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(
                            onClick = {
                                if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                history.commit(s.copy(rotationQuarter = (s.rotationQuarter + 3) % 4))
                            },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        ) { Text("Rotate left", color = MaterialTheme.colorScheme.onSurface) }
                        TextButton(
                            onClick = {
                                if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                history.commit(s.copy(rotationQuarter = (s.rotationQuarter + 1) % 4))
                            },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        ) { Text("Rotate right", color = MaterialTheme.colorScheme.onSurface) }
                        TextButton(
                            onClick = {
                                if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                history.commit(s.copy(flipH = !s.flipH))
                            },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        ) { Text("Flip", color = MaterialTheme.colorScheme.onSurface) }
                    }
                }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.editor_hold_compare),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                FarFilterPill(
                    label = stringResource(R.string.viewer_histogram),
                    selected = showHistogram,
                    onClick = { showHistogram = !showHistogram },
                    modifier = Modifier.testTag("editor_histogram_chip"),
                )
                FarFilterPill(
                    label = if (stripExif) "Strip EXIF: On" else "EXIF: Off",
                    selected = stripExif,
                    onClick = { stripExif = !stripExif },
                    modifier = Modifier.testTag("editor_strip_exif_chip"),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                EditTab.entries.forEach { t ->
                    TextButton(
                        onClick = { tab = t },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    ) {
                        Text(t.label, color = if (t == tab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
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
private fun AdjustSlider(label: String, value: Float, onCommit: (Float) -> Unit) {
    var live by remember(value) { mutableFloatStateOf(value) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            Modifier.padding(end = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Slider(live, { live = it }, onValueChangeFinished = { onCommit(live) }, valueRange = -1f..1f)
    }
}
