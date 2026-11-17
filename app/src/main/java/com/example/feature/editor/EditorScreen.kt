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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.common.FarResult
import com.example.core.common.farCatching
import com.example.ui.glass.GlassKind
import com.example.ui.glass.glass
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

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
) {
    fun toJson(): String =
        "{\"b\":$brightness,\"c\":$contrast,\"s\":$saturation,\"w\":$warmth,\"r\":$rotationQuarter,\"f\":$flipH,\"crop\":${cropRatio ?: "null"}}"
}

class EditHistory {
    private val undo = ArrayDeque<EditState>()
    private val redo = ArrayDeque<EditState>()
    var current by mutableStateOf(EditState())
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

    /** Save a high-res COPY to Pictures/Far Gallery. Never touches the original; EXIF is stripped by design. */
    suspend fun saveCopy(
        ctx: Context,
        sourceUri: Uri?,
        fallbackPreview: Bitmap,
        s: EditState,
        quality: Int = 94,
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

enum class EditTab(val label: String) { Adjust("Adjust"), Crop("Crop"), Rotate("Rotate") }

@Composable
fun EditorScreen(
    source: Bitmap?,
    loading: Boolean,
    errorText: String?,
    glass: HazeState,
    saving: Boolean,
    onCancel: () -> Unit,
    onSave: (EditState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val history = remember { EditHistory() }
    var tab by remember { mutableStateOf(EditTab.Adjust) }
    var comparing by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val s = history.current
    val dirty = s != EditState()

    val requestExit = {
        if (dirty && !saving) confirmDiscard = true else onCancel()
    }
    BackHandler(onBack = requestExit)

    Box(modifier.fillMaxSize().background(Color.Black)) {
        when {
            loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            errorText != null -> Text(errorText, Modifier.align(Alignment.Center).padding(32.dp), color = Color.White)
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

        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp)
                .glass(glass, RoundedCornerShape(26.dp), GlassKind.Overlay)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(requestExit, modifier = Modifier.testTag("editor_cancel_button")) { Text("Cancel", color = Color.White) }
            Row {
                TextButton(history::undo, enabled = history.canUndo) { Text("Undo", color = Color.White) }
                TextButton(history::redo, enabled = history.canRedo) { Text("Redo", color = Color.White) }
            }
            TextButton(
                onClick = { onSave(s) },
                enabled = source != null && !saving,
                modifier = Modifier.testTag("editor_save_button"),
            ) {
                Text(if (saving) "Saving…" else "Save copy", color = MaterialTheme.colorScheme.primary)
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp)
                .glass(glass, RoundedCornerShape(28.dp), GlassKind.Sheet)
                .padding(16.dp),
        ) {
            when (tab) {
                EditTab.Adjust -> {
                    AdjustSlider("Brightness", s.brightness) { history.commit(s.copy(brightness = it)) }
                    AdjustSlider("Contrast", s.contrast) { history.commit(s.copy(contrast = it)) }
                    AdjustSlider("Saturation", s.saturation) { history.commit(s.copy(saturation = it)) }
                    AdjustSlider("Warmth", s.warmth) { history.commit(s.copy(warmth = it)) }
                }
                EditTab.Crop -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("Original" to null, "1:1" to 1f, "4:3" to 4f / 3f, "3:2" to 1.5f, "16:9" to 16f / 9f).forEach { (l, r) ->
                        TextButton({ history.commit(s.copy(cropRatio = r)) }) {
                            Text(l, color = if (s.cropRatio == r) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                EditTab.Rotate -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton({ history.commit(s.copy(rotationQuarter = (s.rotationQuarter + 3) % 4)) }) { Text("Rotate left") }
                    TextButton({ history.commit(s.copy(rotationQuarter = (s.rotationQuarter + 1) % 4)) }) { Text("Rotate right") }
                    TextButton({ history.commit(s.copy(flipH = !s.flipH)) }) { Text("Flip") }
                }
            }
            Text(
                "Press and hold photo to compare. Saved as a new JPEG; original and EXIF stay untouched.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                EditTab.entries.forEach { t ->
                    TextButton({ tab = t }) {
                        Text(t.label, color = if (t == tab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (dirty) {
                    TextButton(history::reset) { Text("Reset", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard edits?") },
            text = { Text("Your original photo will stay untouched.") },
            confirmButton = {
                TextButton({ confirmDiscard = false; onCancel() }) { Text("Discard") }
            },
            dismissButton = {
                TextButton({ confirmDiscard = false }) { Text("Keep editing") }
            },
        )
    }
}

@Composable
private fun AdjustSlider(label: String, value: Float, onCommit: (Float) -> Unit) {
    var live by remember(value) { mutableFloatStateOf(value) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.padding(end = 8.dp), style = MaterialTheme.typography.labelMedium)
        Slider(live, { live = it }, onValueChangeFinished = { onCommit(live) }, valueRange = -1f..1f)
    }
}
