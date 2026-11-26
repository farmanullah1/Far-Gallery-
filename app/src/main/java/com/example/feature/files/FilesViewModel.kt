package com.example.feature.files

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
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
import com.example.core.common.farCatching
import com.example.core.common.resolveSummary
import com.example.core.common.resolveTitle
import com.example.core.common.safeParseMediaUri
import com.example.core.common.userTitle
import com.example.core.database.FarDatabase
import com.example.core.database.FileFavorite
import com.example.core.database.FolderGrant
import com.example.core.database.OperationItemEntity
import com.example.core.storage.Checksums
import com.example.core.storage.Collision
import com.example.core.storage.DocEntry
import com.example.core.storage.DocumentOps
import com.example.core.storage.ExposedDocumentsStore
import com.example.core.storage.FileTransferWorker
import com.example.core.storage.OpEvent
import com.example.core.storage.SearchHit
import com.example.core.storage.TransferQueueController
import com.example.feature.settings.PinnedFolderShortcut
import com.example.feature.settings.SettingsStore
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.UUID

sealed interface FilesEvent {
    data class PreviewFile(val uri: Uri, val mime: String, val name: String) : FilesEvent
    data class OpenGalleryMedia(val uri: Uri, val name: String, val isVideo: Boolean) : FilesEvent
    data class OpenFile(val uri: Uri, val mime: String) : FilesEvent
    data class Share(val uris: List<Uri>) : FilesEvent
    data class AskRename(val entry: FileEntry) : FilesEvent
    data class AskDelete(val entries: List<FileEntry>) : FilesEvent
    data class AskZip(val entries: List<FileEntry>) : FilesEvent
    data class AskConflict(val conflictingNames: List<String>, val totalCount: Int, val isMove: Boolean) : FilesEvent
    data class Properties(val entry: FileEntry, val path: String, val uri: Uri? = null) : FilesEvent
    data object PickFolder : FilesEvent
    data class Message(val text: String, val undo: Boolean = false) : FilesEvent
}

class FilesViewModel(app: Application) : AndroidViewModel(app), FilesActions {
    private val ctx = app
    private val db = FarDatabase.get(app)
    private val ops = DocumentOps(app.contentResolver)
    private val settings = SettingsStore(app)

    private data class Clip(val tree: Uri, val srcParentDocId: String?, val items: List<DocEntry>, val move: Boolean)

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
    private var grantsCache: List<FolderGrant> = emptyList()
    private var pinnedCache: List<PinnedFolderShortcut> = emptyList()
    private var recursiveHits: List<SearchHit> = emptyList()
    private var clip: Clip? = null
    private var opJob: Job? = null
    private var searchJob: Job? = null
    private val exposedTrees = MutableStateFlow(
        ExposedDocumentsStore.listExposed(app).map { it.treeUri }.toSet()
    )

    private data class PlaceSources(
        val grants: List<FolderGrant>,
        val favs: List<FileFavorite>,
        val pinned: List<PinnedFolderShortcut>,
        val exposed: Set<String>,
    )

    init {
        viewModelScope.launch {
            combine(db.folders().grants(), db.folders().favoriteFolders(), settings.flow, exposedTrees) { grants, favs, s, exposed ->
                PlaceSources(grants, favs, s.pinnedFolders, exposed)
            }
                .catch { emit(PlaceSources(emptyList(), emptyList(), emptyList(), emptySet())) }
                .collect { q ->
                    grantsCache = q.grants
                    starredList = q.favs
                    pinnedCache = q.pinned
                    val places = buildList {
                        if (q.favs.isNotEmpty()) add(Place("starred", "Starred (${q.favs.size})", Icons.Filled.Star))
                        q.pinned.forEach { p -> add(Place("pin_${p.id}", p.label, Icons.Filled.Bookmark)) }
                        q.grants.forEach {
                            add(
                                Place(
                                    id = it.treeUri,
                                    label = it.displayName,
                                    icon = Icons.Filled.Folder,
                                    isGrant = true,
                                    isExposedToPicker = it.treeUri in q.exposed,
                                )
                            )
                        }
                        add(Place("add", "Add folder…", Icons.Filled.CreateNewFolder))
                    }
                    _ui.update { it.copy(places = places, isCurrentPinned = isCurrentLocationPinned()) }
                    if (_ui.value.selectedPlaceId == "starred") publishStarred()
                    else if (tree != null && !(_ui.value.searchSubfolders && _ui.value.searchQuery.isNotBlank())) publish()
                }
        }
    }

    override fun toggleExposeGrant(placeId: String) {
        val grant = grantsCache.firstOrNull { it.treeUri == placeId } ?: return
        val currentlyExposed = ExposedDocumentsStore.isExposed(ctx, grant.treeUri)
        if (currentlyExposed) {
            ExposedDocumentsStore.unexposeFolder(ctx, grant.treeUri)
            _events.tryEmit(FilesEvent.Message(ctx.getString(R.string.files_unexposed_msg, grant.displayName)))
        } else {
            safeParseMediaUri(grant.treeUri)?.let { parsed ->
                ExposedDocumentsStore.exposeFolder(ctx, parsed, grant.displayName)
                _events.tryEmit(FilesEvent.Message(ctx.getString(R.string.files_exposed_msg, grant.displayName)))
            }
        }
        exposedTrees.value = ExposedDocumentsStore.listExposed(ctx).map { it.treeUri }.toSet()
    }

    private fun isCurrentLocationPinned(): Boolean {
        val t = tree?.toString() ?: return false
        val cur = stack.lastOrNull()?.first ?: return false
        return pinnedCache.any { it.treeUri == t && it.docId == cur }
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
        searchJob?.cancel()
        if (id == "add") {
            _events.tryEmit(FilesEvent.PickFolder)
            return
        }
        if (id == "starred") {
            tree = null
            stack.clear()
            _ui.update {
                it.copy(
                    selectedPlaceId = "starred",
                    crumbs = listOf("Starred"),
                    selection = emptySet(),
                    searchSubfolders = false,
                    searchScanning = false,
                    isCurrentPinned = false,
                    folderSummary = null,
                )
            }
            publishStarred()
            return
        }
        if (id.startsWith("pin_")) {
            val pinId = id.removePrefix("pin_")
            val pin = pinnedCache.firstOrNull { it.id == pinId } ?: return
            val parsed = safeParseMediaUri(pin.treeUri) ?: return
            tree = parsed
            stack.clear()
            val rootId = ops.rootDocId(parsed)
            val rootLabel = grantsCache.firstOrNull { it.treeUri == pin.treeUri }?.displayName ?: "Folder"
            if (pin.docId == rootId) {
                stack += rootId to rootLabel
            } else {
                stack += rootId to rootLabel
                stack += pin.docId to pin.label
            }
            _ui.update { it.copy(selectedPlaceId = id, selection = emptySet(), searchQuery = "", searchSubfolders = false) }
            load()
            return
        }
        val parsed = safeParseMediaUri(id) ?: return
        tree = parsed
        stack.clear()
        val root = ops.rootDocId(parsed)
        stack += root to (_ui.value.places.firstOrNull { it.id == id }?.label ?: "Folder")
        _ui.update { it.copy(selectedPlaceId = id, selection = emptySet(), searchQuery = "", searchSubfolders = false) }
        load()
    }

    private fun publishStarred() {
        val currentList = starredList
        viewModelScope.launch {
            val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            val entries = withContext(Dispatchers.IO) {
                currentList.map { f ->
                    val parsed = safeParseMediaUri(f.documentUri)
                    val doc = parsed?.let { ops.inspectDoc(it) }
                    val mime = doc?.mime?.ifBlank { null } ?: parsed?.let { ctx.contentResolver.getType(it) } ?: "*/*"
                    FileEntry(
                        id = f.documentUri,
                        name = doc?.name?.ifBlank { f.name } ?: f.name,
                        isDirectory = doc?.isDir ?: false,
                        sizeBytes = doc?.size,
                        modifiedLabel = doc?.modified?.let { fmt.format(Date(it)) },
                        typeLabel = doc?.let(::typeLabel) ?: "Starred",
                        mime = mime,
                        canRename = false,
                        canDelete = false,
                        canMove = false,
                        isStarred = true,
                        contentUri = if (mime.startsWith("image/") || mime.startsWith("video/")) f.documentUri else null,
                    )
                }
            }
            val q = _ui.value.searchQuery.trim()
            val filtered = if (q.isEmpty()) entries else entries.filter { it.name.contains(q, ignoreCase = true) }
            val emptyReason = if (q.isNotEmpty()) EmptyReason.NoResults else EmptyReason.EmptyFolder
            _ui.update {
                it.copy(
                    entries = if (filtered.isEmpty()) UiState.Empty(emptyReason) else UiState.Content(filtered),
                    folderSummary = filtered.size to filtered.sumOf { e -> e.sizeBytes ?: 0L },
                    refreshing = false,
                )
            }
        }
    }

    private fun load() {
        val t = tree ?: return
        _ui.update {
            it.copy(
                entries = if (it.refreshing) it.entries else UiState.Loading,
                crumbs = stack.map { s -> s.second },
                isCurrentPinned = isCurrentLocationPinned(),
            )
        }
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) { ops.list(t, stack.last().first) }
            when (res) {
                is FarResult.Ok -> {
                    raw = res.value
                    _ui.update { it.copy(refreshing = false) }
                    publish()
                }
                is FarResult.Err -> {
                    LocalDiagnostics.recordEvent(ctx, res.error.code, "files_list", res.error.resolveTitle(ctx))
                    _ui.update { it.copy(entries = UiState.Failure(errorFor(res.error)), refreshing = false) }
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
            val isThumb = !it.isDir && (it.mime.startsWith("image/") || it.mime.startsWith("video/"))
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
                contentUri = if (isThumb && u.isNotEmpty()) u else null,
            )
        }
        val emptyReason = if (q.isNotEmpty()) EmptyReason.NoResults else EmptyReason.EmptyFolder
        val totalBytes = list.sumOf { if (it.isDir) 0L else (it.size ?: 0L) }
        _ui.update {
            it.copy(
                entries = if (entries.isEmpty()) UiState.Empty(emptyReason) else UiState.Content(entries),
                clipboardCount = clip?.items?.size ?: 0,
                folderSummary = list.size to totalBytes,
            )
        }
    }

    private fun runRecursiveSearch(query: String) {
        searchJob?.cancel()
        val t = tree ?: return
        val start = stack.lastOrNull() ?: return
        if (query.isBlank()) {
            recursiveHits = emptyList()
            _ui.update { it.copy(searchScanning = false) }
            publish()
            return
        }
        val starredUris = starredList.map { it.documentUri }.toSet()
        val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        _ui.update { it.copy(searchScanning = true) }
        searchJob = viewModelScope.launch {
            ops.searchRecursive(
                tree = t,
                startDocId = start.first,
                startLabel = start.second,
                query = query,
                showHidden = _ui.value.showHidden,
            )
                .onCompletion { _ui.update { st -> st.copy(searchScanning = false) } }
                .catch { _ui.update { st -> st.copy(searchScanning = false) } }
                .collect { hits ->
                    recursiveHits = hits
                    val mapped = hits.map { hit ->
                        val doc = hit.entry
                        val u = ops.docUri(t, doc.docId).toString()
                        val isThumb = !doc.isDir && (doc.mime.startsWith("image/") || doc.mime.startsWith("video/"))
                        FileEntry(
                            id = doc.docId,
                            name = doc.name,
                            isDirectory = doc.isDir,
                            sizeBytes = doc.size,
                            modifiedLabel = doc.modified?.let { m -> fmt.format(Date(m)) },
                            typeLabel = typeLabel(doc),
                            mime = doc.mime,
                            canRename = doc.canRename,
                            canDelete = doc.canDelete,
                            canMove = doc.canMove,
                            isStarred = u in starredUris,
                            contentUri = if (isThumb) u else null,
                            subtitlePath = hit.relPath,
                        )
                    }
                    _ui.update { st ->
                        st.copy(
                            entries = if (mapped.isEmpty()) UiState.Empty(EmptyReason.NoResults) else UiState.Content(mapped),
                            folderSummary = mapped.size to mapped.sumOf { if (it.isDirectory) 0L else (it.sizeBytes ?: 0L) },
                        )
                    }
                }
        }
    }

    private fun typeLabel(e: DocEntry) = when {
        e.isDir -> "Folder"
        e.mime.startsWith("image/") -> "Image"
        e.mime.startsWith("video/") -> "Video"
        e.mime.startsWith("audio/") -> "Audio"
        e.mime == "application/pdf" -> "PDF"
        e.mime == "application/zip" || e.mime == "application/x-zip-compressed" || e.name.endsWith(".zip", true) -> "ZIP archive"
        e.mime.startsWith("text/") -> "Text"
        else -> "File"
    }

    private fun find(e: FileEntry): DocEntry? =
        raw.firstOrNull { it.docId == e.id } ?: recursiveHits.firstOrNull { it.entry.docId == e.id }?.entry

    private fun emitFileOpen(uri: Uri, mime: String, name: String) {
        val isZip = mime == "application/zip" || mime == "application/x-zip-compressed" || name.endsWith(".zip", ignoreCase = true)
        when {
            mime.startsWith("image/") -> _events.tryEmit(FilesEvent.OpenGalleryMedia(uri, name, isVideo = false))
            mime.startsWith("video/") -> _events.tryEmit(FilesEvent.OpenGalleryMedia(uri, name, isVideo = true))
            isZip || mime == "application/pdf" || mime.startsWith("text/") || mime in setOf("application/json", "application/xml") ->
                _events.tryEmit(FilesEvent.PreviewFile(uri, mime, name))
            else -> _events.tryEmit(FilesEvent.OpenFile(uri, mime))
        }
    }

    override fun openEntry(e: FileEntry) {
        if (_ui.value.selectedPlaceId == "starred") {
            val uri = safeParseMediaUri(e.id) ?: return
            val mime = e.mime ?: ctx.contentResolver.getType(uri) ?: "*/*"
            emitFileOpen(uri, mime, e.name)
            return
        }
        val t = tree ?: return
        if (_ui.value.selection.isNotEmpty()) {
            toggleSelect(e)
            return
        }
        val hit = if (_ui.value.searchSubfolders && _ui.value.searchQuery.isNotBlank()) {
            recursiveHits.firstOrNull { it.entry.docId == e.id }
        } else null

        if (e.isDirectory) {
            searchJob?.cancel()
            if (hit != null) {
                stack.clear()
                stack.addAll(hit.breadcrumb)
            } else {
                stack += e.id to e.name
            }
            _ui.update { it.copy(searchQuery = "", searchSubfolders = false, searchScanning = false) }
            load()
        } else {
            val uri = ops.docUri(t, e.id)
            emitFileOpen(uri, e.mime ?: "*/*", e.name)
        }
    }

    override fun navigateToCrumb(index: Int) {
        searchJob?.cancel()
        while (stack.size > index + 1) stack.removeAt(stack.lastIndex)
        _ui.update { it.copy(searchQuery = "", searchSubfolders = false, searchScanning = false) }
        load()
    }

    override fun up() {
        searchJob?.cancel()
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            _ui.update { it.copy(searchQuery = "", searchSubfolders = false, searchScanning = false) }
            load()
        } else {
            tree = null
            stack.clear()
            _ui.update {
                it.copy(
                    selectedPlaceId = null,
                    crumbs = emptyList(),
                    selection = emptySet(),
                    searchQuery = "",
                    searchSubfolders = false,
                    searchScanning = false,
                    isCurrentPinned = false,
                    folderSummary = null,
                )
            }
        }
    }

    override fun setView(v: FilesView) = _ui.update { it.copy(view = v) }

    override fun sortBy(c: SortColumn) {
        _ui.update { it.copy(sort = c, ascending = if (it.sort == c) !it.ascending else true) }
        publish()
    }

    override fun toggleHidden() {
        _ui.update { it.copy(showHidden = !it.showHidden) }
        if (_ui.value.searchSubfolders && _ui.value.searchQuery.isNotBlank()) {
            runRecursiveSearch(_ui.value.searchQuery)
        } else {
            publish()
        }
    }

    override fun toggleSelect(e: FileEntry) =
        _ui.update { it.copy(selection = if (e.id in it.selection) it.selection - e.id else it.selection + e.id) }

    override fun setSearch(query: String) {
        _ui.update { it.copy(searchQuery = query) }
        when {
            _ui.value.selectedPlaceId == "starred" -> publishStarred()
            _ui.value.searchSubfolders -> runRecursiveSearch(query)
            else -> publish()
        }
    }

    override fun toggleSearchSubfolders() {
        val next = !_ui.value.searchSubfolders
        _ui.update { it.copy(searchSubfolders = next) }
        if (next && _ui.value.searchQuery.isNotBlank()) {
            runRecursiveSearch(_ui.value.searchQuery)
        } else {
            searchJob?.cancel()
            _ui.update { it.copy(searchScanning = false) }
            publish()
        }
    }

    override fun togglePinCurrent() {
        val t = tree?.toString() ?: return
        val cur = stack.lastOrNull() ?: return
        val docId = cur.first
        val label = cur.second
        viewModelScope.launch {
            val wasPinned = isCurrentLocationPinned()
            settings.update { s ->
                val list = s.pinnedFolders
                val updated = if (wasPinned) {
                    list.filterNot { it.treeUri == t && it.docId == docId }
                } else {
                    val id = "${t}_$docId".hashCode().toString()
                    (list.filterNot { it.treeUri == t && it.docId == docId }) + PinnedFolderShortcut(id, t, docId, label)
                }
                s.copy(pinnedFolders = updated)
            }
            _events.tryEmit(
                FilesEvent.Message(
                    ctx.getString(if (wasPinned) R.string.files_pinned_removed else R.string.files_pinned_saved)
                )
            )
        }
    }

    override fun refresh() {
        _ui.update { it.copy(refreshing = true) }
        if (_ui.value.selectedPlaceId == "starred") publishStarred() else load()
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
        val selectedDocs = _ui.value.selection.mapNotNull { id ->
            raw.firstOrNull { it.docId == id } ?: recursiveHits.firstOrNull { it.entry.docId == id }?.entry
        }
        if (selectedDocs.isEmpty()) return
        when (action) {
            "copy" -> {
                clip = Clip(t, stack.lastOrNull()?.first, selectedDocs, false)
                _ui.update { it.copy(selection = emptySet(), clipboardCount = selectedDocs.size) }
                _events.tryEmit(FilesEvent.Message("${selectedDocs.size} copied. Open a folder and tap Paste."))
            }
            "move" -> {
                clip = Clip(t, stack.lastOrNull()?.first, selectedDocs, true)
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
        val docs = entries.mapNotNull { e -> find(e) }
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

    fun extractZipByUri(uri: Uri, name: String) {
        val t = tree ?: return
        val parent = stack.lastOrNull()?.first ?: return
        val inMemory = raw.firstOrNull { ops.docUri(t, it.docId) == uri }
            ?: recursiveHits.firstOrNull { ops.docUri(t, it.entry.docId) == uri }?.entry
        runOp("Extracting", 1) { step ->
            step(0, name)
            val matched = inMemory ?: ops.inspectDoc(uri) ?: run {
                _events.tryEmit(FilesEvent.Message("ZIP archive is no longer accessible."))
                return@runOp
            }
            when (val res = ops.extractZip(t, parent, matched)) {
                is FarResult.Ok -> _events.tryEmit(FilesEvent.Message("Extracted ${res.value} file(s)"))
                is FarResult.Err -> {
                    LocalDiagnostics.recordEvent(ctx, res.error.code, "files_unzip", res.error.userTitle())
                    _events.tryEmit(FilesEvent.Message("ZIP extraction stopped (${res.error.code}). Original archive is intact."))
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
            ?.mapNotNull { id -> raw.firstOrNull { it.docId == id } ?: recursiveHits.firstOrNull { it.entry.docId == id }?.entry }
            ?: listOfNotNull(find(e))
        when (action) {
            "open" -> openEntry(e)
            "share" -> {
                val uris = sel.filter { !it.isDir }.map { ops.docUri(t, it.docId) }
                if (uris.isNotEmpty()) _events.tryEmit(FilesEvent.Share(uris))
            }
            "copy" -> {
                clip = Clip(t, stack.lastOrNull()?.first, sel, false)
                publish()
                _events.tryEmit(FilesEvent.Message("${sel.size} copied. Open a folder and tap Paste."))
            }
            "move" -> {
                clip = Clip(t, stack.lastOrNull()?.first, sel, true)
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
            "properties" -> {
                val docUri = if (e.isDirectory) null else ops.docUri(t, e.id)
                _events.tryEmit(FilesEvent.Properties(e, stack.joinToString("/") { it.second } + "/" + e.name, docUri))
            }
        }
    }

    suspend fun computeSha256(uri: Uri): FarResult<String> = withContext(Dispatchers.IO) {
        farCatching {
            ctx.contentResolver.openInputStream(uri)?.use { stream ->
                Checksums.sha256(stream)
            } ?: throw java.io.IOException("openInputStream returned null")
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
        val parent = stack.last().first
        viewModelScope.launch {
            val conflicts = withContext(Dispatchers.IO) { ops.findConflicts(dst, parent, c.items) }
            if (conflicts.isNotEmpty()) {
                _events.tryEmit(FilesEvent.AskConflict(conflicts, c.items.size, c.move))
            } else {
                confirmPasteWithPolicy(Collision.Rename)
            }
        }
    }

    fun confirmPasteWithPolicy(policy: Collision) {
        val c = clip ?: return
        val dst = tree ?: return
        if (stack.isEmpty()) return
        TransferQueueController.setPaused(false)
        opJob?.cancel()
        opJob = viewModelScope.launch {
            val parent = stack.last().first
            val opId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val queued = c.items.map { item ->
                OperationItemEntity(
                    operationId = opId,
                    sourceUri = ops.docUri(c.tree, item.docId).toString(),
                    status = FileTransferWorker.STATUS_PENDING,
                    updatedAt = now,
                )
            }
            withContext(Dispatchers.IO) { db.operationItems().upsertAll(queued) }
            val savedRows = withContext(Dispatchers.IO) { db.operationItems().forOperation(opId) }
            val rowBySource = savedRows.associateBy { it.sourceUri }

            ops.transfer(
                srcTree = c.tree,
                items = c.items,
                dstTree = dst,
                dstParentDocId = parent,
                move = c.move,
                policy = policy,
                srcParentDocId = c.srcParentDocId,
                paused = TransferQueueController.paused,
                onItemResult = { entry, ok, destStr, errCode ->
                    val srcUri = ops.docUri(c.tree, entry.docId).toString()
                    val rowId = rowBySource[srcUri]?.id
                    if (rowId != null) {
                        viewModelScope.launch(Dispatchers.IO) {
                            db.operationItems().updateStatus(
                                id = rowId,
                                status = if (ok) FileTransferWorker.STATUS_DONE else FileTransferWorker.STATUS_FAILED,
                                destUri = destStr,
                                errorCode = errCode,
                                now = System.currentTimeMillis(),
                            )
                        }
                    }
                },
            ).collect { ev ->
                when (ev) {
                    is OpEvent.Progress -> _operation.value = OperationUi(
                        title = if (c.move) "Moving" else "Copying",
                        currentItem = ev.current,
                        done = ev.done,
                        total = ev.total,
                        canPause = true,
                        paused = TransferQueueController.paused.value,
                    )
                    is OpEvent.Finished -> {
                        TransferQueueController.setPaused(false)
                        _operation.value = null
                        if (c.move && ev.failed.isEmpty()) clip = null
                        _events.tryEmit(
                            FilesEvent.Message(
                                buildString {
                                    append("${ev.done} done")
                                    if (ev.skipped.isNotEmpty()) append(", ${ev.skipped.size} skipped")
                                    if (ev.failed.isNotEmpty()) append(", ${ev.failed.size} failed (originals kept)")
                                }
                            )
                        )
                        load()
                    }
                    is OpEvent.Aborted -> {
                        TransferQueueController.setPaused(false)
                        _operation.value = null
                        LocalDiagnostics.recordEvent(ctx, ev.error.code, "files_transfer", ev.error.resolveTitle(ctx))
                        _events.tryEmit(FilesEvent.Message(ev.error.resolveSummary(ctx)))
                        load()
                    }
                }
            }
        }
    }

    fun togglePauseOperation() {
        val next = !TransferQueueController.paused.value
        TransferQueueController.setPaused(next)
        _operation.update { it?.copy(paused = next) }
    }

    fun cancelOperation() {
        TransferQueueController.setPaused(false)
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
