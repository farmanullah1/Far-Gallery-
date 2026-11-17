package com.example.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.core.database.AlbumEntity
import com.example.core.database.TagEntity
import com.example.core.media.MediaRow
import com.example.feature.files.FileEntry
import com.example.feature.files.FilePreview
import com.example.feature.files.FilesEvent
import com.example.feature.files.FilesScreen
import com.example.feature.files.FilesViewModel
import com.example.feature.files.humanSize
import com.example.feature.viewer.ViewerItem
import com.example.ui.feedback.EmptyState
import com.example.ui.feedback.GlassToast
import com.example.ui.feedback.OperationProgressSheet
import dev.chrisbanes.haze.HazeState
import java.text.DateFormat
import java.util.Date

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
) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var newTag by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(item.title, style = MaterialTheme.typography.titleMedium)
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

        // Local Room tags
        Text("Local tags", style = MaterialTheme.typography.titleMedium)
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { onRemoveTag(tag.id) },
                        label = { Text(tag.name) },
                        trailingIcon = { Icon(Icons.Filled.Close, "Remove tag", Modifier.size(14.dp)) },
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
            ) {
                Icon(Icons.Filled.Add, "Add tag")
            }
        }

        // On-device OCR text
        if (!item.isVideo) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Text in photo (on-device OCR)", style = MaterialTheme.typography.titleMedium)
                if (ocrLoading) {
                    CircularProgressIndicator(Modifier.size(18.dp))
                } else {
                    TextButton(onClick = onScanOcr) { Text(if (ocrText == null) "Scan text" else "Rescan") }
                }
            }
            if (!ocrText.isNullOrBlank()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(ocrText, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { clipboard.setText(AnnotatedString(ocrText)) }) {
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
                                Text(a.name, style = MaterialTheme.typography.bodyLarge)
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
fun SubCollectionHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("collection_back_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
            }
            Column {
                Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Icon(Icons.Filled.RestoreFromTrash, null, Modifier.size(18.dp))
                Text(" $actionLabel")
            }
        }
    }
}

@Composable
fun ExcludedFoldersScreen(
    allFolders: List<String>,
    excluded: Set<String>,
    onToggleExclude: (String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
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
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(combined, key = { it }) { path ->
                    val isExcluded = path in excluded
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp))
                        Text(path, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = isExcluded, onCheckedChange = { onToggleExclude(path, it) })
                    }
                }
            }
        }
    }
}

@Composable
fun FilesTab(glass: HazeState) {
    val fvm: FilesViewModel = viewModel()
    val ui by fvm.ui.collectAsState()
    val op by fvm.operation.collectAsState()
    val ctx = LocalContext.current
    var renaming by remember { mutableStateOf<FileEntry?>(null) }
    var deleting by remember { mutableStateOf<List<FileEntry>?>(null) }
    var props by remember { mutableStateOf<Pair<FileEntry, String>?>(null) }
    var previewing by remember { mutableStateOf<Triple<Uri, String, String>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(fvm::addGrant) }

    LaunchedEffect(Unit) {
        fvm.events.collect { e ->
            when (e) {
                FilesEvent.PickFolder -> folderPicker.launch(null)
                is FilesEvent.PreviewFile -> previewing = Triple(e.uri, e.mime, e.name)
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
                is FilesEvent.Message -> message = e.text
                is FilesEvent.Properties -> props = e.entry to e.path
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        FilesScreen(ui, fvm, glass)
        op?.let {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)) {
                OperationProgressSheet(glass, it, onCancel = fvm::cancelOperation)
            }
        }
        message?.let { m ->
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)) {
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
                TextButton({ fvm.confirmRename(e, name.trim()); renaming = null }) {
                    Text(stringResource(R.string.action_rename))
                }
            },
            dismissButton = {
                TextButton({ renaming = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    deleting?.let { list ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${list.size} item(s)?") },
            text = { Text("This permanently deletes them from this location. This can't be undone.") },
            confirmButton = {
                TextButton({ fvm.confirmDelete(list); deleting = null }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton({ deleting = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    props?.let { (e, path) ->
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
                }
            },
            confirmButton = { TextButton({ props = null }) { Text(stringResource(R.string.action_ok)) } },
        )
    }

    previewing?.let { (uri, mime, name) ->
        Dialog(
            onDismissRequest = { previewing = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(onClick = { previewing = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                        }
                        Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1)
                        TextButton(onClick = {
                            runCatching {
                                (ctx as? Activity ?: ctx).startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                        null,
                                    )
                                )
                            }
                        }) { Text("Open with") }
                    }
                    FilePreview(uri, mime, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}
