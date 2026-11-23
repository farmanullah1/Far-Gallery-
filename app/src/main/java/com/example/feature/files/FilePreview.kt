package com.example.feature.files

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.common.FarResult
import com.example.core.common.farCatching
import com.example.core.storage.DocumentOps
import com.example.core.storage.ZipInspection
import com.example.ui.feedback.ErrorCard
import com.example.ui.feedback.ErrorInfo
import com.example.ui.theme.FarSpacing
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.theme.MonoFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** In-app previews for files opened from the Files tab. Anything else goes to "Open with". */
@Composable
fun FilePreview(
    uri: Uri,
    mime: String,
    modifier: Modifier = Modifier,
    name: String = "",
    onExtract: (() -> Unit)? = null,
) {
    val isZip = mime == "application/zip" ||
        mime == "application/x-zip-compressed" ||
        name.endsWith(".zip", ignoreCase = true)
    when {
        mime == "application/pdf" -> PdfPreview(uri, modifier)
        isZip -> ZipPreview(uri, onExtract, modifier)
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
private fun ZipPreview(
    uri: Uri,
    onExtract: (() -> Unit)?,
    modifier: Modifier,
) {
    val ctx = LocalContext.current
    val semantic = LocalSemanticColors.current
    var inspection by remember(uri) { mutableStateOf<FarResult<ZipInspection>?>(null) }

    LaunchedEffect(uri) {
        inspection = withContext(Dispatchers.IO) {
            DocumentOps(ctx.contentResolver).inspectZip(uri)
        }
    }

    when (val res = inspection) {
        null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is FarResult.Err -> ErrorCard(
            ErrorInfo("Can't inspect archive", "This ZIP file may be damaged or unreadable.", true, res.error.code),
            modifier,
        )
        is FarResult.Ok -> {
            val z = res.value
            Column(modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FarSpacing.screenPadding, vertical = 10.dp)
                        .background(semantic.elevatedSurface, RoundedCornerShape(18.dp))
                        .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(18.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Filled.FolderZip, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.files_zip_preview_entries, z.entries.size),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                Formatter.formatFileSize(ctx, z.totalUncompressedBytes),
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFont),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (z.hasUnsafeEntries) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Filled.WarningAmber, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            Text(
                                stringResource(R.string.files_zip_preview_unsafe),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    if (onExtract != null && !z.hasUnsafeEntries) {
                        Button(
                            onClick = onExtract,
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("zip_preview_extract_button"),
                        ) {
                            Text(stringResource(R.string.files_extract_zip))
                        }
                    }
                }
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = FarSpacing.screenPadding, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(z.entries) { entry ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                if (entry.isDir) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                                null,
                                Modifier.size(18.dp),
                                tint = if (!entry.safe) MaterialTheme.colorScheme.error
                                else if (entry.isDir) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                entry.path,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (!entry.safe) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!entry.isDir) {
                                Text(
                                    Formatter.formatFileSize(ctx, entry.sizeBytes),
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFont),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
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
    Column(modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.files_pdf_page_count, r.pageCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = FarSpacing.screenPadding, vertical = 6.dp),
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(List(r.pageCount) { it }) { _, index ->
                val bmp by produceBitmap(r, index)
                bmp?.let { Image(it.asImageBitmap(), "Page ${index + 1}", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth) }
            }
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
