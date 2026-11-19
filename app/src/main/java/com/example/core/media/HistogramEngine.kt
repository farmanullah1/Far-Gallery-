package com.example.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.MonoFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

@Immutable
data class HistogramData(
    val luma: FloatArray,
    val red: FloatArray,
    val green: FloatArray,
    val blue: FloatArray,
    val shadowClipPct: Int,
    val highlightClipPct: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HistogramData) return false
        return shadowClipPct == other.shadowClipPct &&
            highlightClipPct == other.highlightClipPct &&
            luma.contentEquals(other.luma) &&
            red.contentEquals(other.red) &&
            green.contentEquals(other.green) &&
            blue.contentEquals(other.blue)
    }

    override fun hashCode(): Int {
        var result = luma.contentHashCode()
        result = 31 * result + red.contentHashCode()
        result = 31 * result + green.contentHashCode()
        result = 31 * result + blue.contentHashCode()
        result = 31 * result + shadowClipPct
        result = 31 * result + highlightClipPct
        return result
    }
}

object HistogramEngine {
    private const val BINS = 64

    suspend fun fromUri(ctx: Context, uri: Uri): HistogramData? = withContext(Dispatchers.Default) {
        runCatching {
            val bmp = withContext(Dispatchers.IO) {
                val src = ImageDecoder.createSource(ctx.contentResolver, uri)
                ImageDecoder.decodeBitmap(src) { d, info, _ ->
                    val longEdge = max(info.size.width, info.size.height)
                    if (longEdge > 160) {
                        val f = 160f / longEdge
                        d.setTargetSize(
                            (info.size.width * f).toInt().coerceAtLeast(1),
                            (info.size.height * f).toInt().coerceAtLeast(1),
                        )
                    }
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    d.isMutableRequired = false
                }
            }
            try {
                compute(bmp, null)
            } finally {
                bmp.recycle()
            }
        }.getOrNull()
    }

    suspend fun computeAsync(bitmap: Bitmap, colorMatrix: FloatArray? = null): HistogramData? =
        withContext(Dispatchers.Default) {
            runCatching { compute(bitmap, colorMatrix) }.getOrNull()
        }

    fun compute(bitmap: Bitmap, colorMatrix: FloatArray? = null): HistogramData {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return HistogramData(
                FloatArray(BINS),
                FloatArray(BINS),
                FloatArray(BINS),
                FloatArray(BINS),
                0,
                0,
            )
        }
        val maxSampleEdge = 144
        val longEdge = max(bitmap.width, bitmap.height)
        val sampled: Bitmap
        val ownsSampled: Boolean
        if (longEdge > maxSampleEdge) {
            val f = maxSampleEdge.toFloat() / longEdge
            val tw = (bitmap.width * f).toInt().coerceAtLeast(1)
            val th = (bitmap.height * f).toInt().coerceAtLeast(1)
            sampled = Bitmap.createScaledBitmap(bitmap, tw, th, false)
            ownsSampled = sampled !== bitmap
        } else {
            sampled = bitmap
            ownsSampled = false
        }

        val w = sampled.width
        val h = sampled.height
        val pixels = IntArray(w * h)
        sampled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (ownsSampled) sampled.recycle()

        val lumaCounts = IntArray(BINS)
        val rCounts = IntArray(BINS)
        val gCounts = IntArray(BINS)
        val bCounts = IntArray(BINS)

        val m = colorMatrix?.takeIf { it.size >= 20 }
        var shadows = 0
        var highlights = 0

        for (p in pixels) {
            var r = ((p ushr 16) and 0xFF).toFloat()
            var g = ((p ushr 8) and 0xFF).toFloat()
            var b = (p and 0xFF).toFloat()

            if (m != null) {
                val nr = m[0] * r + m[1] * g + m[2] * b + m[4]
                val ng = m[5] * r + m[6] * g + m[7] * b + m[9]
                val nb = m[10] * r + m[11] * g + m[12] * b + m[14]
                r = nr.coerceIn(0f, 255f)
                g = ng.coerceIn(0f, 255f)
                b = nb.coerceIn(0f, 255f)
            }

            val ri = r.toInt().coerceIn(0, 255)
            val gi = g.toInt().coerceIn(0, 255)
            val bi = b.toInt().coerceIn(0, 255)
            val y = (0.2126f * ri + 0.7152f * gi + 0.0722f * bi).roundToInt().coerceIn(0, 255)

            if (y <= 3) shadows++
            if (y >= 252) highlights++

            rCounts[ri * BINS / 256]++
            gCounts[gi * BINS / 256]++
            bCounts[bi * BINS / 256]++
            lumaCounts[y * BINS / 256]++
        }

        var peak = 1
        for (i in 0 until BINS) {
            if (rCounts[i] > peak) peak = rCounts[i]
            if (gCounts[i] > peak) peak = gCounts[i]
            if (bCounts[i] > peak) peak = bCounts[i]
            if (lumaCounts[i] > peak) peak = lumaCounts[i]
        }

        val normFactor = peak.toFloat()
        val lumaNorm = FloatArray(BINS) { (lumaCounts[it] / normFactor).coerceIn(0f, 1f) }
        val rNorm = FloatArray(BINS) { (rCounts[it] / normFactor).coerceIn(0f, 1f) }
        val gNorm = FloatArray(BINS) { (gCounts[it] / normFactor).coerceIn(0f, 1f) }
        val bNorm = FloatArray(BINS) { (bCounts[it] / normFactor).coerceIn(0f, 1f) }

        val total = pixels.size.coerceAtLeast(1)
        return HistogramData(
            luma = lumaNorm,
            red = rNorm,
            green = gNorm,
            blue = bNorm,
            shadowClipPct = ((shadows * 100f) / total).roundToInt().coerceIn(0, 100),
            highlightClipPct = ((highlights * 100f) / total).roundToInt().coerceIn(0, 100),
        )
    }
}

@Composable
fun HistogramOverlay(
    data: HistogramData?,
    loading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.histogram_title),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
            if (data != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.histogram_clipped_shadows, data.shadowClipPct),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFont),
                        color = if (data.shadowClipPct >= 8) Color(0xFFFFB340) else Color.White.copy(alpha = 0.75f),
                    )
                    Text(
                        stringResource(R.string.histogram_clipped_highlights, data.highlightClipPct),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFont),
                        color = if (data.highlightClipPct >= 8) Color(0xFFFF6961) else Color.White.copy(alpha = 0.75f),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (loading && data == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else if (data != null) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    // Subtle gridlines at 25%, 50%, 75%
                    listOf(0.25f, 0.5f, 0.75f).forEach { frac ->
                        drawLine(
                            color = Color.White.copy(alpha = 0.14f),
                            start = Offset(w * frac, 0f),
                            end = Offset(w * frac, h),
                            strokeWidth = 1f,
                        )
                    }

                    fun drawChannel(bins: FloatArray, fill: Color, stroke: Color) {
                        if (bins.isEmpty()) return
                        val step = w / (bins.size - 1).coerceAtLeast(1)
                        val fillPath = Path().apply {
                            moveTo(0f, h)
                            bins.forEachIndexed { idx, v ->
                                lineTo(idx * step, h - (v * (h - 3f)))
                            }
                            lineTo(w, h)
                            close()
                        }
                        val linePath = Path().apply {
                            bins.forEachIndexed { idx, v ->
                                val x = idx * step
                                val y = h - (v * (h - 3f))
                                if (idx == 0) moveTo(x, y) else lineTo(x, y)
                            }
                        }
                        drawPath(fillPath, fill)
                        drawPath(linePath, stroke, style = Stroke(width = 1.6f))
                    }

                    drawChannel(data.red, Color(0xFFFF453A).copy(alpha = 0.24f), Color(0xFFFF453A).copy(alpha = 0.85f))
                    drawChannel(data.green, Color(0xFF30D158).copy(alpha = 0.22f), Color(0xFF30D158).copy(alpha = 0.85f))
                    drawChannel(data.blue, Color(0xFF0A84FF).copy(alpha = 0.24f), Color(0xFF0A84FF).copy(alpha = 0.85f))
                    drawChannel(data.luma, Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.92f))
                }
            }
        }
    }
}
