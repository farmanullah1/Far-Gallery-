package com.example.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.R
import com.example.core.common.FarResult
import com.example.core.common.LocalDiagnostics
import com.example.core.common.safeParseMediaUri
import com.example.core.database.AlbumEntity
import com.example.core.database.TagEntity
import com.example.core.media.ExifScrubber
import com.example.core.media.MediaRow
import com.example.core.media.QuickExifInfo
import com.example.feature.files.FileEntry
import com.example.feature.files.FilePreview
import com.example.feature.files.FilesEvent
import com.example.feature.files.FilesScreen
import com.example.feature.files.FilesViewModel
import com.example.feature.files.humanSize
import com.example.feature.viewer.ViewerItem
import com.example.ui.components.FarTopBar
import com.example.ui.feedback.EmptyState
import com.example.ui.feedback.GlassToast
import com.example.ui.feedback.OperationProgressSheet
import com.example.ui.theme.FarSpacing
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.theme.MonoFont
import com.example.ui.glass.FarGlassState
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaDetailsSheetContent(
    item: ViewerItem,
    row: MediaRow?,
    tags: List<TagEntity>,
    ocrText: String?,
    ocrLoading: Boolean,
    onAddTag: (String) -> Unit,
    onRemoveTag: (Long) -> Unit,
    onScanOcr: () -> Unit,
    onShareClean: (() -> Unit)? = null,
    onSaveCleanCopy: (() -> Unit)? = null,
    onExportPdf: (() -> Unit)? = null,
    onTagClick: ((String) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val semantic = LocalSemanticColors.current
    var newTag by remember { mutableStateOf("") }
    var exifInfo by remember(item.uri) { mutableStateOf<QuickExifInfo?>(null) }

    LaunchedEffect(item.uri, item.isVideo) {
        val parsed = safeParseMediaUri(item.uri)
        exifInfo = if (!item.isVideo && parsed != null) ExifScrubber.readQuickExif(ctx.contentResolver, parsed) else null
    }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (row != null) {
            val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            Text(
                buildString {
                    append(if (row.isVideo) "Video" else "Photo")
                    if (row.width > 0 && row.height > 0) append(" • ${row.width}×${row.height}")
                    append(" • ${Formatter.formatFileSize(ctx, row.sizeBytes)}")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Taken: ${fmt.format(Date(row.takenMs))}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            row.relativePath?.let {
                Text(
                    "Folder: $it",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        exifInfo?.summaryLine()?.let { line ->
            Text(
                line,
                style = MaterialTheme.typography.labelMedium,
                color = if (exifInfo?.hasGps == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!item.isVideo && (onShareClean != null || onSaveCleanCopy != null || onExportPdf != null)) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                onShareClean?.let {
                    OutlinedButton(
                        onClick = it,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("details_share_clean"),
                    ) {
                        Icon(Icons.Filled.Share, null, Modifier.size(16.dp))
                        Text(" Share clean", maxLines = 1)
                    }
                }
                onSaveCleanCopy?.let {
                    OutlinedButton(
                        onClick = it,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("details_save_clean"),
                    ) {
                        Icon(Icons.Filled.Security, null, Modifier.size(16.dp))
                        Text(" Save clean copy", maxLines = 1)
                    }
                }
                onExportPdf?.let {
                    OutlinedButton(
                        onClick = it,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("details_export_pdf"),
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, null, Modifier.size(16.dp))
                        Text(" " + stringResource(R.string.viewer_export_pdf), maxLines = 1)
                    }
                }
            }
        }

        // Local Room tags
        Text("Local tags", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { onTagClick?.invoke(tag.name) ?: onRemoveTag(tag.id) },
                        label = { Text(tag.name) },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                "Remove tag",
                                Modifier
                                    .size(18.dp)
                                    .clickable { onRemoveTag(tag.id) },
                            )
                        },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newTag,
                onValueChange = { newTag = it },
                placeholder = { Text("Add tag") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    if (newTag.isNotBlank()) {
                        onAddTag(newTag.trim())
                        newTag = ""
                    }
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Filled.Add, "Add tag", tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        // On-device OCR text
        if (!item.isVideo) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Text in photo (on-device OCR)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (ocrLoading) {
                    CircularProgressIndicator(Modifier.size(18.dp))
                } else {
                    TextButton(
                        onClick = onScanOcr,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    ) { Text(if (ocrText == null) "Scan text" else "Rescan") }
                }
            }
            if (!ocrText.isNullOrBlank()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(semantic.elevatedSurface, RoundedCornerShape(14.dp))
                        .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(ocrText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    TextButton(
                        onClick = { copyPlainTextToClipboard(ctx, "OCR Text", ocrText) },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    ) {
                        Icon(Icons.Filled.ContentCopy, null, Modifier.size(16.dp))
                        Text(" Copy text", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            } else if (ocrText != null) {
                Text("No readable text detected.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    }
}

@Composable
fun AlbumPickerDialog(
    albums: List<AlbumEntity>,
    onPickAlbum: (AlbumEntity) -> Unit,
    onCreateAlbum: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to album") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (creating) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Album name") },
                        singleLine = true,
                    )
                } else if (albums.isEmpty()) {
                    Text("No albums yet. Create one below.")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(albums, key = { it.id }) { a ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPickAlbum(a) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Filled.PhotoAlbum, null, tint = MaterialTheme.colorScheme.primary)
                                Text(a.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        onCreateAlbum(name.trim())
                        creating = false
                        name = ""
                    }
                }) { Text(stringResource(R.string.action_create)) }
            } else {
                TextButton(onClick = { creating = true }) { Text("New album") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
fun AlbumCoverPickerDialog(
    albumName: String,
    candidates: List<MediaRow>,
    onPickCover: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cover for “$albumName”") },
        text = {
            if (candidates.isEmpty()) {
                Text("Add photos to this album first to pick a custom cover.")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    gridItems(candidates, key = { it.id }) { row ->
                        AsyncImage(
                            model = row.uri,
                            contentDescription = row.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onPickCover(row.id)
                                    onDismiss()
                                }
                                .testTag("cover_candidate_${row.id}"),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onPickCover(null)
                onDismiss()
            }) { Text("Reset auto") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
fun SubCollectionHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    FarTopBar(
        title = title,
        subtitle = subtitle,
        onBack = onBack,
        backTestTag = "collection_back_button",
        actions = {
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                TextButton(onClick = onSecondaryAction, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                    Icon(Icons.Filled.Image, null, Modifier.size(18.dp))
                    Text(" $secondaryActionLabel")
                }
            }
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                    Icon(Icons.Filled.RestoreFromTrash, null, Modifier.size(18.dp))
                    Text(" $actionLabel")
                }
            }
        },
    )
}

@Composable
fun ExcludedFoldersScreen(
    allFolders: List<String>,
    excluded: Set<String>,
    onToggleExclude: (String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val semantic = LocalSemanticColors.current
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding(),
    ) {
        SubCollectionHeader(
            title = stringResource(R.string.collection_excluded),
            onBack = onBack,
        )
        Text(
            stringResource(R.string.exclude_not_security_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FarSpacing.screenPadding, vertical = 8.dp)
                .background(semantic.elevatedSurface, RoundedCornerShape(16.dp))
                .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(16.dp))
                .padding(14.dp),
        )
        val combined = (allFolders + excluded).distinct().sorted()
        if (combined.isEmpty()) {
            EmptyState(
                Icons.Filled.Folder,
                "No media folders found",
                "Allow photo access first to choose folders to exclude from the main timeline.",
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = FarSpacing.screenPadding, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(combined, key = { it }) { path ->
                    val isExcluded = path in excluded
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(semantic.elevatedSurface, RoundedCornerShape(16.dp))
                            .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp))
                        Text(path, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Switch(checked = isExcluded, onCheckedChange = { onToggleExclude(path, it) })
                    }
                }
            }
        }
    }
}

@Composable
fun FilesTab(
    glass: FarGlassState,
    onOpenMedia: (uri: Uri, name: String, isVideo: Boolean) -> Unit = { _, _, _ -> },
) {
    val fvm: FilesViewModel = viewModel()
    val ui by fvm.ui.collectAsState()
    val op by fvm.operation.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf<FileEntry?>(null) }
    var deleting by remember { mutableStateOf<List<FileEntry>?>(null) }
    var zipping by remember { mutableStateOf<List<FileEntry>?>(null) }
    var props by remember { mutableStateOf<FilesEvent.Properties?>(null) }
    var previewing by remember { mutableStateOf<Triple<Uri, String, String>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(fvm::addGrant) }

    LaunchedEffect(Unit) {
        fvm.events.collect { e ->
            when (e) {
                FilesEvent.PickFolder -> folderPicker.launch(null)
                is FilesEvent.PreviewFile -> previewing = Triple(e.uri, e.mime, e.name)
                is FilesEvent.OpenGalleryMedia -> onOpenMedia(e.uri, e.name, e.isVideo)
                is FilesEvent.OpenFile -> runCatching {
                    ctx.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_VIEW)
                                .setDataAndType(e.uri, e.mime)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            null,
                        )
                    )
                }.onFailure { message = "No app found to open this file." }
                is FilesEvent.Share -> runCatching {
                    ctx.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND_MULTIPLE)
                                .setType("*/*")
                                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(e.uris))
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            null,
                        )
                    )
                }.onFailure { message = "Couldn't open share chooser." }
                is FilesEvent.AskRename -> renaming = e.entry
                is FilesEvent.AskDelete -> deleting = e.entries
                is FilesEvent.AskZip -> zipping = e.entries
                is FilesEvent.Message -> message = e.text
                is FilesEvent.Properties -> props = e
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        FilesScreen(ui, fvm, glass)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 84.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            op?.let {
                OperationProgressSheet(glass, it, onCancel = fvm::cancelOperation)
            }
            message?.let { m ->
                GlassToast(glass, m, stringResource(R.string.action_ok), onAction = { message = null })
            }
        }
    }

    renaming?.let { e ->
        var name by remember(e) { mutableStateOf(e.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.action_rename)) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(
                    onClick = { fvm.confirmRename(e, name.trim()); renaming = null },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) {
                    Text(stringResource(R.string.action_rename))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { renaming = null },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    zipping?.let { list ->
        val defaultName = remember(list, ui.crumbs) {
            if (list.size == 1) {
                "${list.first().name.substringBeforeLast('.')}.zip"
            } else {
                val folder = ui.crumbs.lastOrNull()?.replace(Regex("[^A-Za-z0-9_-]"), "_")?.take(18)?.ifBlank { null } ?: "FarFiles"
                val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                "${folder}_${stamp}.zip"
            }
        }
        var zipName by remember(list) { mutableStateOf(defaultName) }
        AlertDialog(
            onDismissRequest = { zipping = null },
            title = { Text("Create ZIP (${list.size} item(s))") },
            text = {
                OutlinedTextField(
                    value = zipName,
                    onValueChange = { zipName = it },
                    label = { Text("Archive name") },
                    singleLine = true,
                    modifier = Modifier.testTag("zip_name_input"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        fvm.confirmCreateZip(list, zipName)
                        zipping = null
                    },
                    enabled = zipName.isNotBlank(),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { zipping = null },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    deleting?.let { list ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${list.size} item(s)?") },
            text = { Text("This permanently deletes them from this location. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { fvm.confirmDelete(list); deleting = null },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleting = null },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    props?.let { propEvent ->
        val e = propEvent.entry
        val path = propEvent.path
        val fileUri = propEvent.uri
        var sha256 by remember(propEvent) { mutableStateOf<String?>(null) }
        var hashing by remember(propEvent) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { props = null },
            title = { Text(e.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Type: ${e.typeLabel}")
                    Text("Path: $path")
                    e.sizeBytes?.let { Text("Size: ${humanSize(it)}") }
                    e.modifiedLabel?.let { Text("Modified: $it") }
                    Text("Permissions: rename=${e.canRename}, move=${e.canMove}, delete=${e.canDelete}")
                    if (!e.isDirectory && fileUri != null) {
                        when {
                            hashing -> {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text(
                                    stringResource(R.string.files_computing_sha256),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            sha256 != null -> {
                                val digest = sha256!!
                                Text(
                                    "SHA-256: $digest",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFont),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("files_sha256_value"),
                                )
                                TextButton(
                                    onClick = {
                                        copyPlainTextToClipboard(ctx, "SHA-256", digest)
                                        message = ctx.getString(R.string.files_sha256_copied)
                                    },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("files_copy_sha256"),
                                ) {
                                    Icon(Icons.Filled.ContentCopy, null, Modifier.size(16.dp))
                                    Text(" " + stringResource(R.string.files_copy_sha256))
                                }
                            }
                            else -> {
                                OutlinedButton(
                                    onClick = {
                                        hashing = true
                                        scope.launch {
                                            when (val res = fvm.computeSha256(fileUri)) {
                                                is FarResult.Ok -> sha256 = res.value
                                                is FarResult.Err -> message = "Couldn't compute checksum (${res.error.code})."
                                            }
                                            hashing = false
                                        }
                                    },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("files_compute_sha256"),
                                ) {
                                    Text(stringResource(R.string.files_compute_sha256))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { props = null },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }

    previewing?.let { (uri, mime, name) ->
        Dialog(
            onDismissRequest = { previewing = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                    FarTopBar(
                        title = name,
                        onBack = { previewing = null },
                        actions = {
                            TextButton(
                                onClick = {
                                    runCatching {
                                        (ctx as? Activity ?: ctx).startActivity(
                                            Intent.createChooser(
                                                Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                                null,
                                            )
                                        )
                                    }.onFailure { err ->
                                        LocalDiagnostics.recordEvent(ctx, "FG-FILES-OPEN", "files_preview", err.javaClass.simpleName)
                                        message = "No app found to open this file."
                                    }
                                },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            ) { Text(stringResource(R.string.viewer_open_with)) }
                        },
                    )
                    FilePreview(
                        uri = uri,
                        mime = mime,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        name = name,
                        onExtract = if (ui.selectedPlaceId != "starred") {
                            {
                                previewing = null
                                fvm.extractZipByUri(uri, name)
                            }
                        } else null,
                    )
                }
            }
        }
    }
}
