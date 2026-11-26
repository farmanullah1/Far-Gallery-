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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale

data class IndexedTextLine(
    val number: Int,
    val content: String,
    val matched: Boolean,
)

data class HexDumpRow(
    val offsetHex: String,
    val hexBytes: String,
    val ascii: String,
)

internal fun detectBinarySignature(header: ByteArray): String {
    if (header.isEmpty()) return "Empty file"
    fun startsWithBytes(vararg vals: Int): Boolean {
        if (header.size < vals.size) return false
        for (i in vals.indices) {
            if ((header[i].toInt() and 0xFF) != vals[i]) return false
        }
        return true
    }
    return when {
        startsWithBytes(0x89, 0x50, 0x4E, 0x47) -> "PNG image"
        startsWithBytes(0xFF, 0xD8, 0xFF) -> "JPEG image"
        startsWithBytes(0x47, 0x49, 0x46, 0x38) -> "GIF image"
        startsWithBytes(0x25, 0x50, 0x44, 0x46) -> "PDF document"
        startsWithBytes(0x50, 0x4B, 0x03, 0x04) || startsWithBytes(0x50, 0x4B, 0x05, 0x06) -> "ZIP archive / APK"
        startsWithBytes(0x7F, 0x45, 0x4C, 0x46) -> "ELF binary"
        startsWithBytes(0x53, 0x51, 0x4C, 0x69, 0x74, 0x65) -> "SQLite database"
        startsWithBytes(0x4F, 0x67, 0x67, 0x53) -> "Ogg media"
        header.size >= 12 &&
            startsWithBytes(0x52, 0x49, 0x46, 0x46) &&
            header[8].toInt().toChar() == 'W' &&
            header[9].toInt().toChar() == 'E' -> "WebP image"
        header.size >= 8 &&
            header[4].toInt().toChar() == 'f' &&
            header[5].toInt().toChar() == 't' &&
            header[6].toInt().toChar() == 'y' &&
            header[7].toInt().toChar() == 'p' -> "MP4 / ISO media"
        else -> "Binary data"
    }
}

internal fun formatHexDump(bytes: ByteArray, bytesPerRow: Int = 8): List<HexDumpRow> {
    if (bytes.isEmpty() || bytesPerRow <= 0) return emptyList()
    val rows = ArrayList<HexDumpRow>((bytes.size + bytesPerRow - 1) / bytesPerRow)
    var offset = 0
    while (offset < bytes.size) {
        val end = minOf(offset + bytesPerRow, bytes.size)
        val hexBuilder = StringBuilder(bytesPerRow * 3)
        val asciiBuilder = StringBuilder(bytesPerRow)
        for (i in offset until offset + bytesPerRow) {
            if (i < end) {
                val b = bytes[i].toInt() and 0xFF
                hexBuilder.append(String.format(Locale.US, "%02X ", b))
                asciiBuilder.append(if (b in 0x20..0x7E) b.toChar() else '.')
            } else {
                hexBuilder.append("   ")
            }
        }
        rows.add(
            HexDumpRow(
                offsetHex = String.format(Locale.US, "%06X", offset),
                hexBytes = hexBuilder.toString().trimEnd(),
                ascii = asciiBuilder.toString(),
            )
        )
        offset += bytesPerRow
    }
    return rows
}

internal fun filterTextLines(lines: List<String>, query: String): List<IndexedTextLine> {
    val q = query.trim()
    if (q.isEmpty()) {
        return lines.mapIndexed { idx, line -> IndexedTextLine(idx + 1, line, matched = false) }
    }
    return lines.mapIndexedNotNull { idx, line ->
        if (line.contains(q, ignoreCase = true)) {
            IndexedTextLine(idx + 1, line, matched = true)
        } else null
    }
}

private val TEXT_EXTENSIONS = setOf(
    "txt", "md", "markdown", "json", "xml", "csv", "tsv", "log",
    "kt", "kts", "java", "py", "js", "ts", "html", "css", "yaml", "yml", "ini", "toml", "sh", "sql",
)

/** In-app previews for files opened from the Files tab. Anything else uses a safe read-only hex inspector + "Open with". */
@Composable
fun FilePreview(
    uri: Uri,
    mime: String,
    modifier: Modifier = Modifier,
    name: String = "",
    onExtract: (() -> Unit)? = null,
) {
    val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
    val isZip = mime == "application/zip" ||
        mime == "application/x-zip-compressed" ||
        ext == "zip"
    val isText = mime.startsWith("text/") ||
        mime in setOf("application/json", "application/xml", "application/javascript") ||
        ext in TEXT_EXTENSIONS
    when {
        mime == "application/pdf" || ext == "pdf" -> PdfPreview(uri, modifier)
        isZip -> ZipPreview(uri, onExtract, modifier)
        isText -> TextPreview(uri, modifier)
        else -> HexPreview(uri, modifier)
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
    val scope = rememberCoroutineScope()
    val semantic = LocalSemanticColors.current
    var text by remember(uri) { mutableStateOf<FarResult<String>?>(null) }
    var showFind by remember(uri) { mutableStateOf(false) }
    var findQuery by remember(uri) { mutableStateOf("") }
    var editing by remember(uri) { mutableStateOf(false) }
    var draftText by remember(uri) { mutableStateOf("") }
    var saveFeedback by remember(uri) { mutableStateOf<Pair<Boolean, String>?>(null) }

    LaunchedEffect(uri) {
        text = withContext(Dispatchers.IO) {
            farCatching {
                val stream = ctx.contentResolver.openInputStream(uri)
                    ?: throw FileNotFoundException("Preview stream unavailable")
                stream.use { it.readNBytes(256 * 1024).toString(Charsets.UTF_8) }
            }
        }
    }

    when (val t = text) {
        null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is FarResult.Err -> ErrorCard(ErrorInfo("Can't read this file", "The file couldn't be opened for preview.", true, t.error.code), modifier)
        is FarResult.Ok -> {
            val allLines = remember(t.value) { t.value.lines() }
            val visibleLines = remember(allLines, findQuery, showFind) {
                if (showFind) filterTextLines(allLines, findQuery) else filterTextLines(allLines, "")
            }
            Column(modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FarSpacing.screenPadding, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (showFind && findQuery.isNotBlank()) stringResource(R.string.files_preview_matches, visibleLines.size)
                        else stringResource(R.string.files_preview_matches, allLines.size),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFont),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (!editing) {
                        TextButton(
                            onClick = {
                                showFind = !showFind
                                if (!showFind) findQuery = ""
                            },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("file_preview_find_button"),
                        ) {
                            Icon(Icons.Filled.Search, null, Modifier.size(16.dp))
                            Text(" " + stringResource(R.string.files_preview_find))
                        }
                        TextButton(
                            onClick = {
                                draftText = t.value
                                saveFeedback = null
                                editing = true
                            },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("file_preview_edit_button"),
                        ) {
                            Icon(Icons.Filled.Edit, null, Modifier.size(16.dp))
                            Text(" " + stringResource(R.string.files_preview_edit))
                        }
                    } else {
                        TextButton(
                            onClick = { editing = false },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    val saved = withContext(Dispatchers.IO) {
                                        farCatching {
                                            val os = ctx.contentResolver.openOutputStream(uri, "wt")
                                                ?: throw IOException("Write stream unavailable")
                                            os.use { it.write(draftText.toByteArray(Charsets.UTF_8)) }
                                        }
                                    }
                                    when (saved) {
                                        is FarResult.Ok -> {
                                            text = FarResult.Ok(draftText)
                                            editing = false
                                            saveFeedback = true to ctx.getString(R.string.files_preview_saved)
                                        }
                                        is FarResult.Err -> {
                                            saveFeedback = false to ctx.getString(R.string.files_preview_save_failed)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("file_preview_save_button"),
                        ) {
                            Icon(Icons.Filled.Save, null, Modifier.size(16.dp))
                            Text(" " + stringResource(R.string.files_preview_save))
                        }
                    }
                }
                saveFeedback?.let { (ok, msg) ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (ok) semantic.success else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = FarSpacing.screenPadding, vertical = 2.dp),
                    )
                }
                if (showFind && !editing) {
                    OutlinedTextField(
                        value = findQuery,
                        onValueChange = { findQuery = it },
                        placeholder = { Text(stringResource(R.string.files_preview_find_hint)) },
                        singleLine = true,
                        trailingIcon = if (findQuery.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = { findQuery = "" },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(Icons.Filled.Close, stringResource(R.string.files_clear_search))
                                }
                            }
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = FarSpacing.screenPadding, vertical = 4.dp)
                            .testTag("file_preview_find_input"),
                    )
                }
                if (editing) {
                    OutlinedTextField(
                        value = draftText,
                        onValueChange = { draftText = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFont),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = FarSpacing.screenPadding, vertical = 8.dp)
                            .testTag("file_preview_editor_input"),
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = FarSpacing.screenPadding, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        items(visibleLines, key = { it.number }) { line ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (line.matched) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else androidx.compose.ui.graphics.Color.Transparent,
                                        RoundedCornerShape(6.dp),
                                    )
                                    .padding(vertical = 2.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    line.number.toString(),
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFont),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(38.dp),
                                )
                                Text(
                                    line.content,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFont),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
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
private fun HexPreview(uri: Uri, modifier: Modifier) {
    val ctx = LocalContext.current
    val semantic = LocalSemanticColors.current
    var headerBytes by remember(uri) { mutableStateOf<FarResult<ByteArray>?>(null) }

    LaunchedEffect(uri) {
        headerBytes = withContext(Dispatchers.IO) {
            farCatching {
                val stream = ctx.contentResolver.openInputStream(uri)
                    ?: throw FileNotFoundException("Binary preview stream unavailable")
                stream.use { it.readNBytes(4096) }
            }
        }
    }

    when (val res = headerBytes) {
        null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is FarResult.Err -> ErrorCard(
            ErrorInfo("No preview for this file", "Far Files can't open this file here. Use Open with to view it in another app.", true, res.error.code),
            modifier,
        )
        is FarResult.Ok -> {
            val bytes = res.value
            val sig = remember(bytes) { detectBinarySignature(bytes) }
            val rows = remember(bytes) { formatHexDump(bytes, 8) }
            Column(modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FarSpacing.screenPadding, vertical = 10.dp)
                        .background(semantic.elevatedSurface, RoundedCornerShape(16.dp))
                        .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        stringResource(R.string.files_preview_hex_header),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.files_preview_hex_signature, sig, bytes.size),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFont),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("files_preview_hex_signature"),
                    )
                }
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = FarSpacing.screenPadding, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    items(rows, key = { it.offsetHex }) { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                row.offsetHex,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFont),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                row.hexBytes,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFont),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                row.ascii,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFont),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
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
