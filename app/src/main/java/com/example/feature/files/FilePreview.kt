package com.example.feature.files

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.core.common.FarResult
import com.example.core.common.farCatching
import com.example.ui.feedback.ErrorCard
import com.example.ui.feedback.ErrorInfo
import com.example.ui.theme.MonoFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** In-app previews for files opened from the Files tab. Anything else goes to "Open with". */
@Composable
fun FilePreview(uri: Uri, mime: String, modifier: Modifier = Modifier) {
    when {
        mime == "application/pdf" -> PdfPreview(uri, modifier)
        mime.startsWith("text/") || mime in setOf("application/json", "application/xml") -> TextPreview(uri, modifier)
        else -> ErrorCard(
            ErrorInfo(
                "No preview for this file",
                "Far Files can't show this type here. Use Open with to view it in another app.",
                true,
                "FG-PRV-TYPE",
            ),
            modifier,
        )
    }
}

@Composable
private fun TextPreview(uri: Uri, modifier: Modifier) {
    val ctx = LocalContext.current
    var text by remember(uri) { mutableStateOf<FarResult<String>?>(null) }
    LaunchedEffect(uri) {
        text = withContext(Dispatchers.IO) {
            farCatching {
                ctx.contentResolver.openInputStream(uri)!!.use { it.readNBytes(256 * 1024).toString(Charsets.UTF_8) }
            }
        }
    }
    when (val t = text) {
        null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is FarResult.Err -> ErrorCard(ErrorInfo("Can't read this file", "The file couldn't be opened for preview.", true, t.error.code), modifier)
        is FarResult.Ok -> Text(
            t.value,
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            fontFamily = MonoFont,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PdfPreview(uri: Uri, modifier: Modifier) {
    val ctx = LocalContext.current
    var renderer by remember(uri) { mutableStateOf<PdfRenderer?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }
    DisposableEffect(uri) {
        val pfd = runCatching { ctx.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
        val r = pfd?.let { runCatching { PdfRenderer(it) }.getOrNull() }
        if (r == null) failed = true else renderer = r
        onDispose {
            runCatching { renderer?.close() }
            runCatching { pfd?.close() }
        }
    }
    if (failed) {
        ErrorCard(ErrorInfo("Can't preview this PDF", "It may be damaged or password-protected. Use Open with.", true, "FG-PDF"), modifier)
        return
    }
    val r = renderer ?: return
    LazyColumn(modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(List(r.pageCount) { it }) { _, index ->
            val bmp by produceBitmap(r, index)
            bmp?.let { Image(it.asImageBitmap(), "Page ${index + 1}", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth) }
        }
    }
}

private val pdfLock = Any()

@Composable
private fun produceBitmap(r: PdfRenderer, index: Int) = produceState<Bitmap?>(null, r, index) {
    value = withContext(Dispatchers.Default) {
        runCatching {
            synchronized(pdfLock) {
                r.openPage(index).use { p ->
                    val w = 1080
                    val h = (w.toFloat() / p.width * p.height).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                        it.eraseColor(Color.WHITE)
                        p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }.getOrNull()
    }
}
