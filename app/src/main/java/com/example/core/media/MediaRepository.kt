package com.example.core.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.example.core.common.FarError
import com.example.core.common.FarResult
import com.example.core.common.farCatching
import com.example.core.database.AlbumEntity
import com.example.core.database.AlbumMediaRef
import com.example.core.database.ExcludedFolder
import com.example.core.database.FarDatabase
import com.example.core.database.FavoriteEntity
import com.example.core.database.MediaTagRef
import com.example.core.database.SearchHistory
import com.example.core.database.TagEntity
import com.example.feature.gallery.GalleryItem
import com.example.feature.gallery.LibraryZoom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class MediaKind { All, Photos, Videos }
enum class SortKey { Taken, Added, Modified, Name, Size }

data class MediaFilter(
    val kind: MediaKind = MediaKind.All,
    val nameQuery: String? = null,
    val pathContains: String? = null,
    val excludedPaths: List<String> = emptyList(),
    val minSizeBytes: Long? = null,
    val onlyIds: Set<Long>? = null,
    val forceEmpty: Boolean = false,
    val trashedOnly: Boolean = false,
)

data class MediaRow(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mime: String,
    val isVideo: Boolean,
    val sizeBytes: Long,
    val takenMs: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val relativePath: String?,
)

class MediaRepository(private val resolver: ContentResolver) {

    private val filesUri: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    fun pager(filter: MediaFilter, sort: SortKey, descending: Boolean): Flow<PagingData<MediaRow>> =
        Pager(PagingConfig(pageSize = 120, prefetchDistance = 120, enablePlaceholders = false, initialLoadSize = 240)) {
            MediaPagingSource(resolver, filesUri, filter, sort, descending)
        }.flow

    suspend fun queryAll(
        filter: MediaFilter = MediaFilter(),
        sort: SortKey = SortKey.Taken,
        descending: Boolean = true,
        limit: Int = 2000,
    ): FarResult<List<MediaRow>> = withContext(Dispatchers.IO) {
        farCatching { queryMediaStore(resolver, filesUri, filter, sort, descending, offset = 0, limit = limit) }
    }

    /** Real consent flows. Never bypassed: IntentSender is launched with StartIntentSenderForResult. */
    fun trashRequest(uris: List<Uri>, trash: Boolean): IntentSender =
        MediaStore.createTrashRequest(resolver, uris, trash).intentSender

    fun deleteRequest(uris: List<Uri>): IntentSender =
        MediaStore.createDeleteRequest(resolver, uris).intentSender

    suspend fun saveIncomingCopy(ctx: Context, sourceUri: Uri, isVideo: Boolean, displayName: String?): FarResult<Uri> =
        withContext(Dispatchers.IO) {
            farCatching {
                val r = ctx.contentResolver
                val ext = if (isVideo) "mp4" else "jpg"
                val safeName = (displayName?.substringBeforeLast('.')?.take(40)?.ifBlank { null } ?: "FAR_${System.currentTimeMillis()}") + ".$ext"
                val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                    put(MediaStore.MediaColumns.MIME_TYPE, r.getType(sourceUri) ?: if (isVideo) "video/mp4" else "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, if (isVideo) "Movies/Far Gallery" else "Pictures/Far Gallery")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val out = r.insert(collection, values) ?: throw java.io.IOException("insert failed")
                try {
                    r.openInputStream(sourceUri)!!.use { input ->
                        r.openOutputStream(out)!!.use { output -> input.copyTo(output) }
                    }
                    r.update(out, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                } catch (e: Exception) {
                    r.delete(out, null, null)
                    throw e
                }
                out
            }
        }

    suspend fun exists(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        farCatching {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { it.moveToFirst() } ?: false
        }.let { (it as? FarResult.Ok)?.value ?: false }
    }
}

internal fun queryMediaStore(
    resolver: ContentResolver,
    base: Uri,
    filter: MediaFilter,
    sort: SortKey,
    desc: Boolean,
    offset: Int,
    limit: Int,
): List<MediaRow> {
    if (filter.forceEmpty) return emptyList()
    val sel = StringBuilder("(${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?,?))")
    val args = ArrayList<String>()
    args += when (filter.kind) {
        MediaKind.Videos -> listOf("3", "3")
        MediaKind.Photos -> listOf("1", "1")
        MediaKind.All -> listOf("1", "3")
    }
    filter.nameQuery?.takeIf { it.isNotBlank() }?.let {
        sel.append(" AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?")
        args += "%${it.trim()}%"
    }
    filter.pathContains?.let {
        sel.append(" AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?")
        args += "%$it%"
    }
    filter.excludedPaths.forEach {
        sel.append(" AND (${MediaStore.MediaColumns.RELATIVE_PATH} IS NULL OR ${MediaStore.MediaColumns.RELATIVE_PATH} NOT LIKE ?)")
        args += "$it%"
    }
    filter.minSizeBytes?.let {
        sel.append(" AND ${MediaStore.MediaColumns.SIZE} >= ?")
        args += it.toString()
    }
    filter.onlyIds?.let { ids ->
        if (ids.isEmpty()) return emptyList()
        sel.append(" AND ${MediaStore.MediaColumns._ID} IN (${ids.joinToString(",")})")
    }

    val sortCols = when (sort) {
        SortKey.Taken -> arrayOf(MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.DATE_ADDED)
        SortKey.Added -> arrayOf(MediaStore.MediaColumns.DATE_ADDED)
        SortKey.Modified -> arrayOf(MediaStore.MediaColumns.DATE_MODIFIED)
        SortKey.Name -> arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
        SortKey.Size -> arrayOf(MediaStore.MediaColumns.SIZE)
    }
    val bundle = Bundle().apply {
        putString(ContentResolver.QUERY_ARG_SQL_SELECTION, sel.toString())
        putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args.toTypedArray())
        putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, sortCols)
        putInt(
            ContentResolver.QUERY_ARG_SORT_DIRECTION,
            if (desc) ContentResolver.QUERY_SORT_DIRECTION_DESCENDING else ContentResolver.QUERY_SORT_DIRECTION_ASCENDING,
        )
        putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
        putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
        putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, if (filter.trashedOnly) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE)
    }
    val projection = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.MIME_TYPE,
        MediaStore.Files.FileColumns.MEDIA_TYPE,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.DATE_TAKEN,
        MediaStore.MediaColumns.DATE_ADDED,
        MediaStore.MediaColumns.DURATION,
        MediaStore.MediaColumns.WIDTH,
        MediaStore.MediaColumns.HEIGHT,
        MediaStore.MediaColumns.RELATIVE_PATH,
    )
    val out = ArrayList<MediaRow>(limit.coerceAtMost(500))
    resolver.query(base, projection, bundle, null)?.use { c ->
        val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        val iMime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
        val iType = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
        val iTaken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
        val iAdded = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
        val iDur = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
        val iW = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
        val iH = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
        val iPath = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
        while (c.moveToNext()) {
            val isVideo = c.getInt(iType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            val id = c.getLong(iId)
            val taken = c.getLong(iTaken).takeIf { it > 0 } ?: (c.getLong(iAdded) * 1000)
            val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            out += MediaRow(
                id = id,
                uri = ContentUris.withAppendedId(collection, id),
                name = c.getString(iName).orEmpty(),
                mime = c.getString(iMime).orEmpty(),
                isVideo = isVideo,
                sizeBytes = c.getLong(iSize),
                takenMs = taken,
                durationMs = c.getLong(iDur),
                width = c.getInt(iW),
                height = c.getInt(iH),
                relativePath = c.getString(iPath),
            )
        }
    }
    return out
}

private class MediaPagingSource(
    private val resolver: ContentResolver,
    private val base: Uri,
    private val filter: MediaFilter,
    private val sort: SortKey,
    private val desc: Boolean,
) : PagingSource<Int, MediaRow>() {

    override fun getRefreshKey(state: PagingState<Int, MediaRow>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaRow> = withContext(Dispatchers.IO) {
        val offset = params.key ?: 0
        val limit = params.loadSize
        when (val r = farCatching { queryMediaStore(resolver, base, filter, sort, desc, offset, limit) }) {
            is FarResult.Ok -> LoadResult.Page(
                data = r.value,
                prevKey = if (offset == 0) null else (offset - limit).coerceAtLeast(0),
                nextKey = if (r.value.size < limit) null else offset + r.value.size,
            )
            is FarResult.Err -> LoadResult.Error(MediaQueryException(r.error))
        }
    }
}

class MediaQueryException(val error: FarError) : Exception(error.code)

/* ----------------------------- Gallery ViewModel ----------------------------- */
@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModel(
    private val repo: MediaRepository,
    private val db: FarDatabase,
    initialFilter: MediaFilter = MediaFilter(),
) : ViewModel() {

    val filter = MutableStateFlow(initialFilter)
    val sort = MutableStateFlow(SortKey.Taken)
    val descending = MutableStateFlow(true)
    val zoom = MutableStateFlow(LibraryZoom.All)
    val selection = MutableStateFlow<Set<Long>>(emptySet())
    private val refreshNonce = MutableStateFlow(0)

    val favoriteIds: StateFlow<Set<Long>> = db.favorites().ids().map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val excludedFolders: StateFlow<List<String>> = db.folders().excluded().map { list -> list.map { it.relativePath } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val albums: StateFlow<List<AlbumEntity>> = db.albums().albums()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val albumRefs: StateFlow<List<AlbumMediaRef>> = db.albums().allRefs()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recentSearches: StateFlow<List<String>> = db.history().recent().map { list -> list.map { it.query } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val ocrCount: StateFlow<Int> = db.ocr().indexedCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val libraryRows: StateFlow<List<MediaRow>> =
        combine(excludedFolders, refreshNonce) { ex, _ -> ex }
            .flatMapLatest { ex ->
                kotlinx.coroutines.flow.flow {
                    val res = repo.queryAll(MediaFilter(excludedPaths = ex), SortKey.Taken, descending = true)
                    emit((res as? FarResult.Ok)?.value ?: emptyList())
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val items: Flow<PagingData<GalleryItem>> =
        combine(filter, sort, descending, excludedFolders, zoom) { f, s, d, ex, z -> Params(f.copy(excludedPaths = ex), s, d, z) }
            .flatMapLatest { p ->
                combine(
                    repo.pager(p.filter, p.sort, p.desc).cachedIn(viewModelScope),
                    favoriteIds,
                ) { page, favs ->
                    page.map<MediaRow, GalleryItem> { it.toItem(favs) }.withHeaders(p.zoom)
                }
            }.cachedIn(viewModelScope)

    private data class Params(val filter: MediaFilter, val sort: SortKey, val desc: Boolean, val zoom: LibraryZoom)

    fun refreshAll() {
        refreshNonce.value++
    }

    fun setKind(k: MediaKind) { filter.value = filter.value.copy(kind = k) }
    fun toggleSelect(id: Long) { selection.value = selection.value.let { if (id in it) it - id else it + id } }
    fun setSelected(ids: Set<Long>) { selection.value = ids }
    fun clearSelection() { selection.value = emptySet() }

    fun toggleFavorite(id: Long) = viewModelScope.launch {
        if (id in favoriteIds.value) db.favorites().remove(id)
        else db.favorites().add(FavoriteEntity(id, System.currentTimeMillis()))
    }

    fun createAlbum(name: String, description: String? = null) = viewModelScope.launch {
        db.albums().insert(AlbumEntity(name = name, description = description, createdAt = System.currentTimeMillis()))
    }

    fun renameAlbum(albumId: Long, name: String) = viewModelScope.launch { db.albums().rename(albumId, name) }
    fun togglePinAlbum(albumId: Long, pinned: Boolean) = viewModelScope.launch { db.albums().pin(albumId, !pinned) }
    fun setAlbumCover(albumId: Long, mediaId: Long?) = viewModelScope.launch { db.albums().setCover(albumId, mediaId) }

    fun deleteAlbum(albumId: Long) = viewModelScope.launch {
        db.albums().clearAlbum(albumId)
        db.albums().deleteAlbum(albumId)
    }

    fun addMediaToAlbum(albumId: Long, mediaIds: Collection<Long>) = viewModelScope.launch {
        val refs = mediaIds.mapIndexed { idx, id -> AlbumMediaRef(albumId, id, idx) }
        db.albums().addMedia(refs)
    }

    fun removeMediaFromAlbum(albumId: Long, mediaIds: List<Long>) = viewModelScope.launch {
        db.albums().removeMedia(albumId, mediaIds)
    }

    fun excludeFolder(relativePath: String) = viewModelScope.launch {
        db.folders().exclude(ExcludedFolder(relativePath))
        refreshNonce.value++
    }

    fun includeFolder(relativePath: String) = viewModelScope.launch {
        db.folders().include(relativePath)
        refreshNonce.value++
    }

    fun addSearchHistory(q: String) = viewModelScope.launch {
        val clean = q.trim()
        if (clean.isNotEmpty()) db.history().add(SearchHistory(clean, System.currentTimeMillis()))
    }

    fun clearSearchHistory() = viewModelScope.launch { db.history().clear() }

    fun addTag(mediaId: Long, rawName: String) = viewModelScope.launch {
        val clean = rawName.trim()
        if (clean.isEmpty()) return@launch
        val existing = db.tags().findByName(clean)
        val tagId = existing?.id ?: db.tags().insertTag(TagEntity(name = clean)).let { inserted ->
            if (inserted > 0) inserted else db.tags().findByName(clean)?.id ?: return@launch
        }
        db.tags().attach(MediaTagRef(mediaId, tagId))
    }

    fun removeTag(mediaId: Long, tagId: Long) = viewModelScope.launch {
        db.tags().detach(mediaId, tagId)
    }

    companion object {
        fun factory(resolver: ContentResolver, db: FarDatabase, filter: MediaFilter = MediaFilter()): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { GalleryViewModel(MediaRepository(resolver), db, filter) }
            }
    }
}

private fun MediaRow.toItem(favs: Set<Long>) = GalleryItem.Media(
    id = id,
    uri = uri.toString(),
    description = (if (isVideo) "Video, " else "Photo, ") +
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
            .format(Instant.ofEpochMilli(takenMs).atZone(ZoneId.systemDefault())),
    isVideo = isVideo,
    durationLabel = if (isVideo) durationMs.toClock() else null,
    isFavorite = id in favs,
    isMotion = false,
    available = true,
    takenMs = takenMs,
)

private fun Long.toClock(): String {
    val s = this / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60)
    else "%d:%02d".format(s / 60, s % 60)
}

/** Date headers: granularity follows the Years / Months / Days / All control. */
private fun PagingData<GalleryItem>.withHeaders(zoom: LibraryZoom): PagingData<GalleryItem> =
    insertSeparators { before, after ->
        val a = after as? GalleryItem.Media ?: return@insertSeparators null
        val b = before as? GalleryItem.Media
        val ma = a.takenDate()
        val mb = b?.takenDate()
        val key: (LocalDate) -> Any = when (zoom) {
            LibraryZoom.Years -> { d -> d.year }
            LibraryZoom.Months -> { d -> d.year * 100 + d.monthValue }
            else -> { d -> d }
        }
        if (mb == null || key(ma) != key(mb)) GalleryItem.Header(headerLabel(ma, zoom)) else null
    }

private fun GalleryItem.Media.takenDate(): LocalDate =
    Instant.ofEpochMilli(takenMs).atZone(ZoneId.systemDefault()).toLocalDate()

private fun headerLabel(d: LocalDate, zoom: LibraryZoom): String {
    val today = LocalDate.now()
    return when (zoom) {
        LibraryZoom.Years -> d.year.toString()
        LibraryZoom.Months -> DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()).format(d)
        else -> when (ChronoUnit.DAYS.between(d, today)) {
            0L -> "Today"
            1L -> "Yesterday"
            in 2L..6L -> DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()).format(d)
            else -> DateTimeFormatter.ofPattern(if (d.year == today.year) "EEE, d MMM" else "d MMM yyyy", Locale.getDefault()).format(d)
        }
    }
}
