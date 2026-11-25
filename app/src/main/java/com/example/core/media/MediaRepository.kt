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
import com.example.core.database.FolderRuleEntity
import com.example.core.database.MediaTagRef
import com.example.core.database.QuarantineItemEntity
import com.example.core.database.RecentView
import com.example.core.database.SavedSearch
import com.example.core.database.ScanCheckpointEntity
import com.example.core.database.SearchHistory
import com.example.core.database.TagEntity
import com.example.feature.gallery.GalleryItem
import com.example.feature.gallery.LibraryZoom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
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
    val pathOrIds: Set<Long>? = null,
    val excludedPaths: List<String> = emptyList(),
    val minSizeBytes: Long? = null,
    val minDateMs: Long? = null,
    val maxDateMs: Long? = null,
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

data class MediaLibraryStats(
    val photoCount: Int,
    val videoCount: Int,
    val totalBytes: Long,
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
        limit: Int? = null,
    ): FarResult<List<MediaRow>> = withContext(Dispatchers.IO) {
        farCatching {
            val out = ArrayList<MediaRow>()
            val seen = HashSet<Long>()
            val chunkSize = 1000
            var offset = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                val remaining = limit?.let { (it - out.size).coerceAtMost(chunkSize) } ?: chunkSize
                if (remaining <= 0) break
                val batch = queryMediaStore(resolver, filesUri, filter, sort, descending, offset = offset, limit = remaining)
                batch.forEach { row ->
                    if (seen.add(row.id)) out += row
                }
                if (batch.size < remaining) break
                offset += batch.size
            }
            out
        }
    }

    suspend fun computeStats(filter: MediaFilter = MediaFilter()): FarResult<MediaLibraryStats> = withContext(Dispatchers.IO) {
        farCatching {
            computeMediaStats(resolver, filesUri, filter)
        }
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
                    val input = r.openInputStream(sourceUri) ?: throw java.io.IOException("openInputStream returned null")
                    input.use { src ->
                        val output = r.openOutputStream(out) ?: throw java.io.IOException("openOutputStream returned null")
                        output.use { dst -> src.copyTo(dst) }
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
    filter.pathContains?.let { path ->
        val extra = filter.pathOrIds.orEmpty()
        if (extra.isEmpty()) {
            sel.append(" AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?")
            args += "%$path%"
        } else {
            val idClauses = extra.chunked(400).joinToString(" OR ") { chunk ->
                "${MediaStore.MediaColumns._ID} IN (${chunk.joinToString(",")})"
            }
            sel.append(" AND (${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? OR $idClauses)")
            args += "%$path%"
        }
    }
    filter.excludedPaths.forEach {
        sel.append(" AND (${MediaStore.MediaColumns.RELATIVE_PATH} IS NULL OR ${MediaStore.MediaColumns.RELATIVE_PATH} NOT LIKE ?)")
        args += "$it%"
    }
    filter.minSizeBytes?.let {
        sel.append(" AND ${MediaStore.MediaColumns.SIZE} >= ?")
        args += it.toString()
    }
    filter.minDateMs?.let {
        val sec = (it / 1000L).toString()
        sel.append(" AND ((${MediaStore.MediaColumns.DATE_TAKEN} >= ?) OR (${MediaStore.MediaColumns.DATE_TAKEN} IS NULL AND ${MediaStore.MediaColumns.DATE_ADDED} >= ?))")
        args += it.toString()
        args += sec
    }
    filter.maxDateMs?.let {
        val sec = (it / 1000L).toString()
        sel.append(" AND ((${MediaStore.MediaColumns.DATE_TAKEN} <= ?) OR (${MediaStore.MediaColumns.DATE_TAKEN} IS NULL AND ${MediaStore.MediaColumns.DATE_ADDED} <= ?))")
        args += it.toString()
        args += sec
    }
    filter.onlyIds?.let { ids ->
        if (ids.isEmpty()) return emptyList()
        val idClauses = ids.chunked(400).joinToString(" OR ") { chunk ->
            "${MediaStore.MediaColumns._ID} IN (${chunk.joinToString(",")})"
        }
        sel.append(" AND ($idClauses)")
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
        val iId = c.getColumnIndex(MediaStore.MediaColumns._ID)
        val iName = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
        val iMime = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
        val iType = c.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val iSize = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
        val iTaken = c.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
        val iAdded = c.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
        val iDur = c.getColumnIndex(MediaStore.MediaColumns.DURATION)
        val iW = c.getColumnIndex(MediaStore.MediaColumns.WIDTH)
        val iH = c.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
        val iPath = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
        if (iId < 0 || iType < 0) return out
        while (c.moveToNext()) {
            val isVideo = c.getInt(iType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            val id = c.getLong(iId)
            val takenRaw = if (iTaken >= 0 && !c.isNull(iTaken)) c.getLong(iTaken) else 0L
            val addedSec = if (iAdded >= 0 && !c.isNull(iAdded)) c.getLong(iAdded) else 0L
            val taken = takenRaw.takeIf { it > 0L } ?: (addedSec * 1000L)
            val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            out += MediaRow(
                id = id,
                uri = ContentUris.withAppendedId(collection, id),
                name = if (iName >= 0 && !c.isNull(iName)) c.getString(iName).orEmpty() else "",
                mime = if (iMime >= 0 && !c.isNull(iMime)) c.getString(iMime).orEmpty() else "",
                isVideo = isVideo,
                sizeBytes = if (iSize >= 0 && !c.isNull(iSize)) c.getLong(iSize).coerceAtLeast(0L) else 0L,
                takenMs = taken,
                durationMs = if (iDur >= 0 && !c.isNull(iDur)) c.getLong(iDur).coerceAtLeast(0L) else 0L,
                width = if (iW >= 0 && !c.isNull(iW)) c.getInt(iW) else 0,
                height = if (iH >= 0 && !c.isNull(iH)) c.getInt(iH) else 0,
                relativePath = if (iPath >= 0 && !c.isNull(iPath)) c.getString(iPath) else null,
            )
        }
    }
    return out
}

internal suspend fun computeMediaStats(
    resolver: ContentResolver,
    base: Uri,
    filter: MediaFilter,
    chunkSize: Int = 2000,
): MediaLibraryStats {
    if (filter.forceEmpty) return MediaLibraryStats(0, 0, 0L)
    val sel = StringBuilder("(${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?,?))")
    val args = ArrayList<String>()
    args += when (filter.kind) {
        MediaKind.Videos -> listOf("3", "3")
        MediaKind.Photos -> listOf("1", "1")
        MediaKind.All -> listOf("1", "3")
    }
    filter.excludedPaths.forEach {
        sel.append(" AND (${MediaStore.MediaColumns.RELATIVE_PATH} IS NULL OR ${MediaStore.MediaColumns.RELATIVE_PATH} NOT LIKE ?)")
        args += "$it%"
    }
    val projection = arrayOf(
        MediaStore.Files.FileColumns.MEDIA_TYPE,
        MediaStore.MediaColumns.SIZE,
    )
    var photos = 0
    var videos = 0
    var bytes = 0L
    var offset = 0
    while (true) {
        currentCoroutineContext().ensureActive()
        val bundle = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, sel.toString())
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args.toTypedArray())
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.MediaColumns._ID))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, chunkSize)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, if (filter.trashedOnly) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE)
        }
        var count = 0
        resolver.query(base, projection, bundle, null)?.use { c ->
            val iType = c.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val iSize = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
            if (iType < 0) return MediaLibraryStats(photos, videos, bytes)
            while (c.moveToNext()) {
                count++
                if (c.getInt(iType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) videos++ else photos++
                if (iSize >= 0 && !c.isNull(iSize)) bytes += c.getLong(iSize).coerceAtLeast(0L)
            }
        }
        if (count < chunkSize) break
        offset += count
    }
    return MediaLibraryStats(photos, videos, bytes)
}

private class MediaPagingSource(
    private val resolver: ContentResolver,
    private val base: Uri,
    private val filter: MediaFilter,
    private val sort: SortKey,
    private val desc: Boolean,
) : PagingSource<Int, MediaRow>() {

    private val seenIds = java.util.Collections.synchronizedSet(HashSet<Long>())

    override fun getRefreshKey(state: PagingState<Int, MediaRow>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaRow> = withContext(Dispatchers.IO) {
        val offset = params.key ?: 0
        if (offset == 0) seenIds.clear()
        val limit = params.loadSize
        when (val r = farCatching { queryMediaStore(resolver, base, filter, sort, desc, offset, limit) }) {
            is FarResult.Ok -> {
                val raw = r.value
                val unique = raw.filter { seenIds.add(it.id) }
                LoadResult.Page(
                    data = unique,
                    prevKey = if (offset == 0) null else (offset - limit).coerceAtLeast(0),
                    nextKey = if (raw.size < limit) null else offset + raw.size,
                )
            }
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

    val favoriteIds: StateFlow<Set<Long>> = db.favorites().ids()
        .map { it.toSet() }
        .catch { emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val folderRules: StateFlow<List<FolderRuleEntity>> = db.folderRules().all()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val quarantineItems: StateFlow<List<QuarantineItemEntity>> = db.quarantine().all()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val excludedFolders: StateFlow<List<String>> = combine(db.folders().excluded(), db.folderRules().all()) { ex, rules ->
        (ex.map { it.relativePath } + rules.filter { it.excludedFromTimeline || it.hidden }.map { it.relativePath }).distinct()
    }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val albums: StateFlow<List<AlbumEntity>> = db.albums().albums()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val albumRefs: StateFlow<List<AlbumMediaRef>> = db.albums().allRefs()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recentSearches: StateFlow<List<String>> = db.history().recent()
        .map { list -> list.map { it.query } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val ocrCount: StateFlow<Int> = db.ocr().indexedCount()
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val allTags: StateFlow<List<TagEntity>> = db.tags().allTags()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val savedSearches: StateFlow<List<SavedSearch>> = db.savedSearches().all()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recentViewIds: StateFlow<List<Long>> = db.history().recentViews()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val farCapturedIds: StateFlow<Set<Long>> = db.mediaAssets().capturedByFar()
        .map { list -> list.map { it.mediaStoreId }.toSet() }
        .catch { emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val libraryRows: StateFlow<List<MediaRow>> =
        combine(excludedFolders, refreshNonce) { ex, _ -> ex }
            .flatMapLatest { ex ->
                kotlinx.coroutines.flow.flow {
                    val res = repo.queryAll(MediaFilter(excludedPaths = ex), SortKey.Taken, descending = true)
                    emit((res as? FarResult.Ok)?.value ?: emptyList())
                }
            }
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val queryTrigger: Flow<Params> = combine(
        combine(filter, sort, descending) { f, s, d -> Triple(f, s, d) },
        combine(excludedFolders, zoom, refreshNonce) { ex, z, n -> Triple(ex, z, n) },
    ) { (f, s, d), (ex, z, n) ->
        Params(f.copy(excludedPaths = ex), s, d, z, n)
    }

    val items: Flow<PagingData<GalleryItem>> = queryTrigger
        .flatMapLatest { p ->
            repo.pager(p.filter, p.sort, p.desc).map { page ->
                page.map<MediaRow, GalleryItem> { it.toItem(emptySet()) }.withHeaders(p.zoom, p.sort)
            }
        }
        .catch { emit(PagingData.empty()) }
        .cachedIn(viewModelScope)

    private data class Params(val filter: MediaFilter, val sort: SortKey, val desc: Boolean, val zoom: LibraryZoom, val nonce: Int)

    fun refreshAll() {
        refreshNonce.value++
    }

    fun setKind(k: MediaKind) { filter.value = filter.value.copy(kind = k) }
    fun setSort(s: SortKey) { sort.value = s }
    fun toggleSortDirection() { descending.value = !descending.value }
    fun toggleSelect(id: Long) { selection.value = selection.value.let { if (id in it) it - id else it + id } }
    fun setSelected(ids: Set<Long>) { selection.value = ids }
    fun clearSelection() { selection.value = emptySet() }

    fun toggleFavorite(id: Long) = viewModelScope.launch {
        if (id in favoriteIds.value) db.favorites().remove(id)
        else db.favorites().add(FavoriteEntity(id, System.currentTimeMillis()))
    }

    fun setFavorites(ids: Collection<Long>, favorite: Boolean) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        ids.forEach { id ->
            if (favorite) db.favorites().add(FavoriteEntity(id, now))
            else db.favorites().remove(id)
        }
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
    fun removeSearchHistory(q: String) = viewModelScope.launch { db.history().remove(q) }

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

    suspend fun findMediaIdsForTag(tagName: String): Set<Long> =
        db.tags().findMediaIdsByExactTag(tagName).toSet()

    fun recordRecentView(mediaId: Long) = viewModelScope.launch {
        if (mediaId > 0L) db.history().view(RecentView(mediaId, System.currentTimeMillis()))
    }

    fun saveSearch(name: String, queryJson: String) = viewModelScope.launch {
        val clean = name.trim()
        if (clean.isNotEmpty()) db.savedSearches().upsert(SavedSearch(name = clean, queryJson = queryJson))
    }

    fun deleteSavedSearch(id: Long) = viewModelScope.launch {
        db.savedSearches().delete(id)
    }

    fun updateFolderRule(
        relativePath: String,
        hidden: Boolean,
        excludedFromTimeline: Boolean,
        excludedFromOcr: Boolean,
    ) = viewModelScope.launch {
        val clean = relativePath.trim()
        if (clean.isEmpty()) return@launch
        db.folderRules().upsert(
            FolderRuleEntity(
                relativePath = clean,
                hidden = hidden,
                excludedFromTimeline = excludedFromTimeline,
                excludedFromOcr = excludedFromOcr,
                updatedAt = System.currentTimeMillis(),
            )
        )
        if (excludedFromTimeline || hidden) db.folders().exclude(ExcludedFolder(clean))
        else db.folders().include(clean)
        refreshNonce.value++
    }

    fun dismissQuarantine(uri: String) = viewModelScope.launch {
        db.quarantine().remove(uri)
    }

    fun clearQuarantine() = viewModelScope.launch {
        db.quarantine().clearAll()
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

/** Date headers: granularity follows the Years / Months / Days / All control (skipped on Name/Size sorts). */
private fun PagingData<GalleryItem>.withHeaders(zoom: LibraryZoom, sort: SortKey): PagingData<GalleryItem> {
    if (sort == SortKey.Name || sort == SortKey.Size) return this
    return insertSeparators { before, after ->
        val a = after as? GalleryItem.Media ?: return@insertSeparators null
        val b = before as? GalleryItem.Media
        val ma = a.takenDate()
        val mb = b?.takenDate()
        val key: (LocalDate) -> Any = when (zoom) {
            LibraryZoom.Years -> { d -> d.year }
            LibraryZoom.Months -> { d -> d.year * 100 + d.monthValue }
            else -> { d -> d }
        }
        if (mb == null || key(ma) != key(mb)) {
            GalleryItem.Header(title = headerLabel(ma, zoom), anchorId = a.id)
        } else null
    }
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
