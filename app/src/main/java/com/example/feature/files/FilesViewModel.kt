package com.example.feature.files

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Star
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.common.FarError
import com.example.core.common.FarResult
import com.example.core.common.LocalDiagnostics
import com.example.core.common.resolveSummary
import com.example.core.common.resolveTitle
import com.example.core.common.safeParseMediaUri
import com.example.core.common.userTitle
import com.example.core.database.FarDatabase
import com.example.core.database.FileFavorite
import com.example.core.database.FolderGrant
import com.example.core.storage.Collision
import com.example.core.storage.DocEntry
import com.example.core.storage.DocumentOps
import com.example.core.storage.OpEvent
import com.example.ui.feedback.EmptyReason
import com.example.ui.feedback.ErrorAction
import com.example.ui.feedback.ErrorInfo
import com.example.ui.feedback.OperationUi
import com.example.ui.feedback.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

sealed interface FilesEvent {
    data class PreviewFile(val uri: Uri, val mime: String, val name: String) : FilesEvent
    data class OpenFile(val uri: Uri, val mime: String) : FilesEvent
    data class Share(val uris: List<Uri>) : FilesEvent
    data class AskRename(val entry: FileEntry) : FilesEvent
    data class AskDelete(val entries: List<FileEntry>) : FilesEvent
    data class AskZip(val entries: List<FileEntry>) : FilesEvent
    data class Properties(val entry: FileEntry, val path: String) : FilesEvent
    data object PickFolder : FilesEvent
    data class Message(val text: String, val undo: Boolean = false) : FilesEvent
}

class FilesViewModel(app: Application) : AndroidViewModel(app), FilesActions {
    private val ctx = app
    private val db = FarDatabase.get(app)
    private val ops = DocumentOps(app.contentResolver)

    private data class Clip(val tree: Uri, val items: List<DocEntry>, val move: Boolean)

    private val _ui = MutableStateFlow(
        FilesUiState(places = emptyList(), selectedPlaceId = null, crumbs = emptyList(), entries = UiState.Loading)
    )
    val ui: StateFlow<FilesUiState> = _ui.asStateFlow()
    private val _events = MutableSharedFlow<FilesEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<FilesEvent> = _events.asSharedFlow()
    private val _operation = MutableStateFlow<OperationUi?>(null)
    val operation: StateFlow<OperationUi?> = _operation.asStateFlow()

    private var tree: Uri? = null
    private val stack = ArrayList<Pair<String, String>>()
    private var raw: List<DocEntry> = emptyList()
    private var starredList: List<FileFavorite> = emptyList()
    private var clip: Clip? = null
    private var opJob: Job? = null

    init {
        viewModelScope.launch {
            combine(db.folders().grants(), db.folders().favoriteFolders()) { grants, favs -> grants to favs }
                .collect { (grants, favs) ->
                    starredList = favs
                    val places = buildList {
                        if (favs.isNotEmpty()) add(Place("starred", "Starred (${favs.size})", Icons.Filled.Star))
                        grants.forEach { add(Place(it.treeUri, it.displayName, Icons.Filled.Folder)) }
                        add(Place("add", "Add folder…", Icons.Filled.CreateNewFolder))
                    }
                    _ui.update { it.copy(places = places) }
                    if (_ui.value.selectedPlaceId == "starred") publishStarred()
                    else if (tree != null) publish()
                }
        }
    }

    fun addGrant(uri: Uri) = viewModelScope.launch {
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        val name = DocumentFile.fromTreeUri(ctx, uri)?.name ?: "Folder"
        db.folders().add(FolderGrant(uri.toString(), name, System.currentTimeMillis()))
    }

    override fun openPlace(id: String) {
        if (id == "add") {
            _events.tryEmit(FilesEvent.PickFolder)
            return
        }
        if (id == "starred") {
            tree = null
            stack.clear()
            _ui.update { it.copy(selectedPlaceId = "starred", crumbs = listOf("Starred"), selection = emptySet()) }
            publishStarred()
            return
        }
        val parsed = safeParseMediaUri(id) ?: return
        tree = parsed
        stack.clear()
        val root = ops.rootDocId(parsed)
        stack += root to (_ui.value.places.firstOrNull { it.id == id }?.label ?: "Folder")
        _ui.update { it.copy(selectedPlaceId = id, selection = emptySet()) }
        load()
    }

    private fun publishStarred() {
        val entries = starredList.map { f ->
            FileEntry(
                id = f.documentUri,
                name = f.name,
                isDirectory = false,
                sizeBytes = null,
                modifiedLabel = null,
                typeLabel = "Starred",
                mime = "*/*",
                canRename = false,
                canDelete = false,
                canMove = false,
                isStarred = true,
            )
        }
        _ui.update {
            it.copy(entries = if (entries.isEmpty()) UiState.Empty(EmptyReason.EmptyFolder) else UiState.Content(entries))
        }
    }

    private fun load() {
        val t = tree ?: return
        _ui.update { it.copy(entries = UiState.Loading, crumbs = stack.map { s -> s.second }) }
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) { ops.list(t, stack.last().first) }
            when (res) {
                is FarResult.Ok -> {
                    raw = res.value
                    publish()
                }
                is FarResult.Err -> {
                    LocalDiagnostics.recordEvent(ctx, res.error.code, "files_list", res.error.resolveTitle(ctx))
                    _ui.update { it.copy(entries = UiState.Failure(errorFor(res.error))) }
                }
            }
        }
    }

    private fun errorFor(e: FarError) = when (e) {
        is FarError.PermissionRevoked -> ErrorInfo(
            title = e.resolveTitle(ctx),
            whatHappened = e.resolveSummary(ctx),
            originalsSafe = true,
            code = e.code,
            actions = listOf(ErrorAction("Choose folder again", true) { _events.tryEmit(FilesEvent.PickFolder) }),
        )
        else -> ErrorInfo(
            title = e.resolveTitle(ctx),
            whatHappened = e.resolveSummary(ctx),
            originalsSafe = true,
            code = e.code,
            actions = listOf(ErrorAction(ctx.getString(R.string.action_retry), true) { load() }),
        )
    }

    private fun publish() {
        val s = _ui.value
        val t = tree
        val starredUris = starredList.map { it.documentUri }.toSet()
        val q = s.searchQuery.trim()
        var list = raw
            .filter { s.showHidden || !it.name.startsWith(".") }
            .filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
        val cmp: Comparator<DocEntry> = when (s.sort) {
            SortColumn.Name -> compareBy({ !it.isDir }, { it.name.lowercase() })
            SortColumn.Size -> compareBy({ !it.isDir }, { it.size ?: -1L })
            SortColumn.Type -> compareBy({ !it.isDir }, { it.mime }, { it.name.lowercase() })
            SortColumn.Modified -> compareBy({ !it.isDir }, { it.modified ?: 0L })
        }
        list = list.sortedWith(cmp).let { if (s.ascending) it else it.reversed().sortedBy { e -> !e.isDir } }
        val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        val entries = list.map {
            val u = if (t != null) ops.docUri(t, it.docId).toString() else ""
            FileEntry(
                id = it.docId,
                name = it.name,
                isDirectory = it.isDir,
                sizeBytes = it.size,
                modifiedLabel = it.modified?.let { m -> fmt.format(Date(m)) },
                typeLabel = typeLabel(it),
                mime = it.mime,
                canRename = it.canRename,
                canDelete = it.canDelete,
                canMove = it.canMove,
                isStarred = u in starredUris,
            )
        }
        val emptyReason = if (q.isNotEmpty()) EmptyReason.NoResults else EmptyReason.EmptyFolder
        _ui.update {
            it.copy(
                entries = if (entries.isEmpty()) UiState.Empty(emptyReason) else UiState.Content(entries),
                clipboardCount = clip?.items?.size ?: 0,
            )
        }
    }

    private fun typeLabel(e: DocEntry) = when {
        e.isDir -> "Folder"
        e.mime.startsWith("image/") -> "Image"
        e.mime.startsWith("video/") -> "Video"
        e.mime.startsWith("audio/") -> "Audio"
        e.mime == "application/pdf" -> "PDF"
        e.mime == "application/zip" -> "ZIP archive"
        e.mime.startsWith("text/") -> "Text"
        else -> "File"
    }

    private fun find(e: FileEntry) = raw.firstOrNull { it.docId == e.id }

    override fun openEntry(e: FileEntry) {
        if (_ui.value.selectedPlaceId == "starred") {
            val uri = safeParseMediaUri(e.id) ?: return
            _events.tryEmit(FilesEvent.OpenFile(uri, ctx.contentResolver.getType(uri) ?: "*/*"))
            return
        }
        val t = tree ?: return
        if (_ui.value.selection.isNotEmpty()) {
            toggleSelect(e)
            return
        }
        if (e.isDirectory) {
            stack += e.id to e.name
            load()
        } else {
            val uri = ops.docUri(t, e.id)
            val mime = e.mime ?: "*/*"
            if (mime == "application/pdf" || mime.startsWith("text/") || mime in setOf("application/json", "application/xml")) {
                _events.tryEmit(FilesEvent.PreviewFile(uri, mime, e.name))
            } else {
                _events.tryEmit(FilesEvent.OpenFile(uri, mime))
            }
        }
    }

    override fun navigateToCrumb(index: Int) {
        while (stack.size > index + 1) stack.removeAt(stack.lastIndex)
        load()
    }

    override fun up() {
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            load()
        } else {
            tree = null
            stack.clear()
            _ui.update { it.copy(selectedPlaceId = null, crumbs = emptyList(), selection = emptySet()) }
        }
    }

    override fun setView(v: FilesView) = _ui.update { it.copy(view = v) }

    override fun sortBy(c: SortColumn) {
        _ui.update { it.copy(sort = c, ascending = if (it.sort == c) !it.ascending else true) }
        publish()
    }

    override fun toggleHidden() {
        _ui.update { it.copy(showHidden = !it.showHidden) }
        publish()
    }

    override fun toggleSelect(e: FileEntry) =
        _ui.update { it.copy(selection = if (e.id in it.selection) it.selection - e.id else it.selection + e.id) }

    override fun setSearch(query: String) {
        _ui.update { it.copy(searchQuery = query) }
        if (_ui.value.selectedPlaceId == "starred") publishStarred() else publish()
    }

    override fun selectAll() {
        val visible = (_ui.value.entries as? UiState.Content)?.data?.map { it.id }?.toSet().orEmpty()
        _ui.update { it.copy(selection = visible) }
    }

    override fun clearSelection() {
        _ui.update { it.copy(selection = emptySet()) }
    }

    override fun batchAction(action: String) {
        val t = tree ?: return
        val selectedDocs = _ui.value.selection.mapNotNull { id -> raw.firstOrNull { it.docId == id } }
        if (selectedDocs.isEmpty()) return
        when (action) {
            "copy" -> {
                clip = Clip(t, selectedDocs, false)
                _ui.update { it.copy(selection = emptySet(), clipboardCount = selectedDocs.size) }
                _events.tryEmit(FilesEvent.Message("${selectedDocs.size} copied. Open a folder and tap Paste."))
            }
            "move" -> {
                clip = Clip(t, selectedDocs, true)
                _ui.update { it.copy(selection = emptySet(), clipboardCount = selectedDocs.size) }
                _events.tryEmit(FilesEvent.Message("${selectedDocs.size} ready to move. Open a folder and tap Paste."))
            }
            "share" -> {
                val uris = selectedDocs.filter { !it.isDir }.map { ops.docUri(t, it.docId) }
                if (uris.isNotEmpty()) _events.tryEmit(FilesEvent.Share(uris))
                else _events.tryEmit(FilesEvent.Message("Select at least one file to share."))
            }
            "zip" -> _events.tryEmit(
                FilesEvent.AskZip(
                    selectedDocs.map { d -> FileEntry(d.docId, d.name, d.isDir, d.size, null, "", d.mime, d.canRename, d.canDelete, d.canMove) }
                )
            )
            "delete" -> _events.tryEmit(
                FilesEvent.AskDelete(
                    selectedDocs.map { d -> FileEntry(d.docId, d.name, d.isDir, d.size, null, "", d.mime, d.canRename, d.canDelete, d.canMove) }
                )
            )
        }
    }

    fun confirmCreateZip(entries: List<FileEntry>, archiveName: String) {
        val t = tree ?: return
        val parent = stack.lastOrNull()?.first ?: return
        val docs = entries.mapNotNull { e -> raw.firstOrNull { it.docId == e.id } }
        if (docs.isEmpty()) return
        _ui.update { it.copy(selection = emptySet()) }
        runOp("Creating ZIP", docs.size) { step ->
            when (val res = ops.createZip(t, parent, docs, archiveName) { done, cur -> step(done.coerceAtMost(docs.size), cur) }) {
                is FarResult.Ok -> _events.tryEmit(FilesEvent.Message("Created ${res.value}"))
                is FarResult.Err -> {
                    LocalDiagnostics.recordEvent(ctx, res.error.code, "files_zip", res.error.userTitle())
                    _events.tryEmit(FilesEvent.Message("Couldn't create ZIP (${res.error.code})."))
                }
            }
        }
    }

    override fun retry() = load()

    override fun newFolder() {
        val t = tree ?: return
        if (stack.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val name = ops.uniqueName(raw.map { it.name }.toSet(), "New folder")
            when (ops.create(t, stack.last().first, DocumentsContract.Document.MIME_TYPE_DIR, name)) {
                is FarResult.Ok -> load()
                is FarResult.Err -> _events.tryEmit(FilesEvent.Message("Couldn't create the folder here. This location may be read-only."))
            }
        }
    }

    override fun contextAction(e: FileEntry, action: String) {
        val t = tree ?: return
        val sel = _ui.value.selection.takeIf { e.id in it && it.size > 1 }
            ?.mapNotNull { id -> raw.firstOrNull { it.docId == id } } ?: listOfNotNull(find(e))
        when (action) {
            "open" -> openEntry(e)
            "share" -> _events.tryEmit(FilesEvent.Share(sel.filter { !it.isDir }.map { ops.docUri(t, it.docId) }))
            "copy" -> {
                clip = Clip(t, sel, false)
                publish()
                _events.tryEmit(FilesEvent.Message("${sel.size} copied. Open a folder and tap Paste."))
            }
            "move" -> {
                clip = Clip(t, sel, true)
                publish()
                _events.tryEmit(FilesEvent.Message("${sel.size} ready to move. Open a folder and tap Paste."))
            }
            "rename" -> _events.tryEmit(FilesEvent.AskRename(e))
            "delete" -> _events.tryEmit(
                FilesEvent.AskDelete(
                    sel.map { d -> FileEntry(d.docId, d.name, d.isDir, d.size, null, "", d.mime, d.canRename, d.canDelete, d.canMove) }
                )
            )
            "star" -> viewModelScope.launch(Dispatchers.IO) {
                val uriStr = ops.docUri(t, e.id).toString()
                if (e.isStarred) db.folders().removeFavoriteFile(uriStr)
                else db.folders().addFavoriteFile(FileFavorite(uriStr, e.name))
            }
            "extract" -> {
                val zip = find(e) ?: return
                val parent = stack.lastOrNull()?.first ?: return
                runOp("Extracting", 1) { step ->
                    step(0, zip.name)
                    when (val res = ops.extractZip(t, parent, zip)) {
                        is FarResult.Ok -> _events.tryEmit(FilesEvent.Message("Extracted ${res.value} file(s)"))
                        is FarResult.Err -> {
                            LocalDiagnostics.recordEvent(ctx, res.error.code, "files_unzip", res.error.userTitle())
                            _events.tryEmit(FilesEvent.Message("ZIP extraction stopped (${res.error.code}). Original archive is intact."))
                        }
                    }
                }
            }
            "properties" -> _events.tryEmit(FilesEvent.Properties(e, stack.joinToString("/") { it.second } + "/" + e.name))
        }
    }

    fun confirmRename(e: FileEntry, newName: String) {
        val t = tree ?: return
        if (newName.isBlank() || '/' in newName) {
            _events.tryEmit(FilesEvent.Message("That name isn't allowed."))
            return
        }
        if (raw.any { it.name == newName && it.docId != e.id }) {
            _events.tryEmit(FilesEvent.Message("An item named “$newName” already exists."))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            when (val res = ops.rename(t, e.id, newName)) {
                is FarResult.Ok -> load()
                is FarResult.Err -> {
                    LocalDiagnostics.recordEvent(ctx, res.error.code, "files_rename", res.error.userTitle())
                    _events.tryEmit(FilesEvent.Message("This provider doesn't allow renaming here."))
                }
            }
        }
    }

    fun confirmDelete(entries: List<FileEntry>) {
        val t = tree ?: return
        runOp("Deleting", entries.size) { step ->
            var failed = 0
            entries.forEachIndexed { i, e ->
                step(i, e.name)
                val res = ops.delete(t, e.id)
                if (res is FarResult.Err) {
                    failed++
                    LocalDiagnostics.recordEvent(ctx, res.error.code, "files_delete", res.error.userTitle())
                }
            }
            if (failed > 0) _events.tryEmit(FilesEvent.Message("$failed item(s) couldn't be deleted. They were left in place."))
        }
    }

    override fun paste() {
        val c = clip ?: return
        val dst = tree ?: return
        if (stack.isEmpty()) return
        opJob?.cancel()
        opJob = viewModelScope.launch {
            val parent = stack.last().first
            ops.transfer(c.tree, c.items, dst, parent, c.move, Collision.Rename).collect { ev ->
                when (ev) {
                    is OpEvent.Progress -> _operation.value = OperationUi(
                        if (c.move) "Moving" else "Copying",
                        ev.current,
                        ev.done,
                        ev.total,
                        canPause = false,
                    )
                    is OpEvent.Finished -> {
                        _operation.value = null
                        if (c.move) clip = null
                        _events.tryEmit(
                            FilesEvent.Message(
                                "${ev.done} done" + if (ev.failed.isNotEmpty()) ", ${ev.failed.size} failed (originals kept)" else ""
                            )
                        )
                        load()
                    }
                    is OpEvent.Aborted -> {
                        _operation.value = null
                        LocalDiagnostics.recordEvent(ctx, ev.error.code, "files_transfer", ev.error.resolveTitle(ctx))
                        _events.tryEmit(FilesEvent.Message(ev.error.resolveSummary(ctx)))
                        load()
                    }
                }
            }
        }
    }

    fun cancelOperation() {
        opJob?.cancel()
        _operation.value = null
        load()
    }

    private fun runOp(title: String, total: Int, block: suspend (step: (Int, String) -> Unit) -> Unit) {
        opJob = viewModelScope.launch(Dispatchers.IO) {
            _operation.value = OperationUi(title, null, 0, total)
            try {
                block { i, n -> _operation.value = OperationUi(title, n, i, total) }
            } finally {
                _operation.value = null
                load()
            }
        }
    }
}
