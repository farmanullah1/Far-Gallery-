package com.example.feature.albums

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.components.FarFilterDrawer
import com.example.ui.components.FarFilterPill
import com.example.ui.feedback.EmptyState
import com.example.ui.feedback.ErrorAction
import com.example.ui.glass.GlassKind
import com.example.ui.glass.glass
import com.example.ui.glass.glassSource
import com.example.ui.theme.FarMotion
import com.example.ui.theme.FarSpacing
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.LocalSemanticColors
import dev.chrisbanes.haze.HazeState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/* ------------------------------------ Albums & Collections ------------------------------------ */
enum class AlbumSort(val label: String) {
    Pinned("Pinned"),
    Newest("Newest"),
    MostItems("Most items"),
    NameAZ("A–Z"),
}

enum class AlbumFilter(val labelRes: Int) {
    All(R.string.filter_all_albums),
    Pinned(R.string.filter_pinned),
    NonEmpty(R.string.filter_non_empty),
}

object AlbumListLogic {
    fun filterAndSort(
        albums: List<AlbumUi>,
        filter: AlbumFilter,
        sort: AlbumSort,
    ): List<AlbumUi> {
        val filtered = when (filter) {
            AlbumFilter.All -> albums
            AlbumFilter.Pinned -> albums.filter { it.pinned }
            AlbumFilter.NonEmpty -> albums.filter { it.count > 0 }
        }
        return when (sort) {
            AlbumSort.Pinned -> filtered.sortedWith(compareByDescending<AlbumUi> { it.pinned }.thenBy { it.name.lowercase() })
            AlbumSort.Newest -> filtered.sortedWith(compareByDescending<AlbumUi> { it.pinned }.thenByDescending { it.createdAt })
            AlbumSort.MostItems -> filtered.sortedWith(compareByDescending<AlbumUi> { it.pinned }.thenByDescending { it.count })
            AlbumSort.NameAZ -> filtered.sortedBy { it.name.lowercase() }
        }
    }
}

@Immutable
data class AlbumUi(
    val id: Long,
    val name: String,
    val count: Int,
    val coverUri: String?,
    val pinned: Boolean,
    val createdAt: Long = 0L,
)

@Immutable
data class MemoryUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val coverUri: String?,
    val mediaIds: Set<Long>,
)

enum class SystemCollection(val labelRes: Int, val icon: ImageVector) {
    Favorites(R.string.collection_favorites, Icons.Filled.Favorite),
    Videos(R.string.collection_videos, Icons.Filled.Videocam),
    Screenshots(R.string.collection_screenshots, Icons.Filled.Screenshot),
    Camera(R.string.collection_camera, Icons.Filled.PhotoCamera),
    StorageCleanup(R.string.collection_storage_cleanup, Icons.Filled.CleaningServices),
    Duplicates(R.string.collection_duplicates, Icons.Filled.ContentCopy),
    RecentlyDeleted(R.string.collection_trash, Icons.Filled.DeleteOutline),
    ExcludedFolders(R.string.collection_excluded, Icons.Filled.FolderOff),
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumsScreen(
    albums: List<AlbumUi>,
    memories: List<MemoryUi>,
    albumSort: AlbumSort,
    onAlbumSortChange: (AlbumSort) -> Unit,
    glass: HazeState,
    onOpenAlbum: (AlbumUi) -> Unit,
    onOpenMemory: (MemoryUi) -> Unit,
    onOpenCollection: (SystemCollection) -> Unit,
    onCreateAlbum: (String) -> Unit,
    onRenameAlbum: (AlbumUi, String) -> Unit,
    onTogglePin: (AlbumUi) -> Unit,
    onPickCover: (AlbumUi) -> Unit,
    onDeleteAlbum: (AlbumUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<AlbumUi?>(null) }
    var deleting by remember { mutableStateOf<AlbumUi?>(null) }
    var sortMenu by remember { mutableStateOf(false) }
    var albumFilter by remember { mutableStateOf(AlbumFilter.All) }
    var filterDrawerOpen by remember { mutableStateOf(false) }
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    val sortedAlbums = remember(albums, albumFilter, albumSort) {
        AlbumListLogic.filterAndSort(albums, albumFilter, albumSort)
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val semantic = LocalSemanticColors.current
    val reduced = LocalReducedMotion.current
    val density = LocalDensity.current
    val targetTopInset = if (topBarHeightPx > 0) with(density) { topBarHeightPx.toDp() } else statusTop + 124.dp
    val topInset by animateDpAsState(targetTopInset, FarMotion.spatial(reduced), label = "albumsTopInset")

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            LazyVerticalGrid(
                GridCells.Fixed(2),
                Modifier.fillMaxSize().glassSource(glass),
                contentPadding = PaddingValues(
                    start = FarSpacing.screenPadding,
                    end = FarSpacing.screenPadding,
                    top = topInset,
                    bottom = navBottom + FarSpacing.bottomDockClearance,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (memories.isNotEmpty()) {
                    item(key = "memories_section", span = { GridItemSpan(2) }) {
                        Column {
                            Text(
                                stringResource(R.string.memories_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(memories, key = { it.id }) { m -> MemoryCard(m) { onOpenMemory(m) } }
                            }
                        }
                    }
                }

                item(key = "collections_section", span = { GridItemSpan(2) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.collections_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(SystemCollection.entries, key = { it.name }) { col ->
                                Row(
                                    Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(semantic.elevatedSurface)
                                        .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(18.dp))
                                        .clickable { onOpenCollection(col) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                        .testTag("collection_${col.name.lowercase()}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(col.icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        stringResource(col.labelRes),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }

                if (sortedAlbums.isEmpty()) {
                    item(key = "albums_empty_section", span = { GridItemSpan(2) }) {
                        EmptyState(
                            Icons.Filled.PhotoAlbum,
                            stringResource(R.string.albums_empty_title),
                            stringResource(R.string.albums_empty_body),
                            Modifier.height(260.dp),
                            primary = if (albumFilter != AlbumFilter.All) {
                                ErrorAction(stringResource(R.string.action_clear_all), true) {
                                    albumFilter = AlbumFilter.All
                                }
                            } else null,
                        )
                    }
                } else {
                    items(sortedAlbums, key = { it.id }) { a ->
                        var menu by remember { mutableStateOf(false) }
                        Column(
                            Modifier
                                .testTag("album_card_${a.id}")
                                .combinedClickable(
                                    onClick = { onOpenAlbum(a) },
                                    onLongClick = { menu = true },
                                )
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(semantic.elevatedSurface)
                                    .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(16.dp))
                            ) {
                                if (a.coverUri != null) {
                                    AsyncImage(a.coverUri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    Icon(
                                        Icons.Filled.PhotoAlbum,
                                        null,
                                        Modifier.align(Alignment.Center).size(36.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (a.pinned) {
                                    Icon(
                                        Icons.Filled.PushPin,
                                        "Pinned",
                                        Modifier
                                            .align(Alignment.TopStart)
                                            .padding(10.dp)
                                            .size(16.dp),
                                        tint = Color.White,
                                    )
                                }
                                IconButton(
                                    onClick = { menu = true },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(48.dp)
                                        .padding(8.dp)
                                        .background(Color.Black.copy(0.42f), CircleShape),
                                ) {
                                    Icon(Icons.Filled.MoreVert, "Album options", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(if (a.pinned) "Unpin" else "Pin to top", color = MaterialTheme.colorScheme.onSurface) },
                                        onClick = { menu = false; onTogglePin(a) },
                                    )
                                    if (a.count > 0) {
                                        DropdownMenuItem(
                                            text = { Text("Set cover photo", color = MaterialTheme.colorScheme.onSurface) },
                                            leadingIcon = { Icon(Icons.Filled.Image, null, tint = MaterialTheme.colorScheme.onSurface) },
                                            onClick = { menu = false; onPickCover(a) },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_rename), color = MaterialTheme.colorScheme.onSurface) },
                                        onClick = { menu = false; renaming = a },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete album", color = MaterialTheme.colorScheme.error) },
                                        onClick = { menu = false; deleting = a },
                                    )
                                }
                            }
                            Text(
                                a.name,
                                Modifier.padding(top = 6.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text("${a.count}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .onSizeChanged { topBarHeightPx = it.height }
                    .statusBarsPadding()
                    .padding(horizontal = FarSpacing.screenPadding, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.albums_title),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            IconButton(
                                onClick = { sortMenu = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .glass(glass, CircleShape, GlassKind.Chip)
                                    .border(0.75.dp, semantic.cardBorder, CircleShape)
                                    .testTag("album_sort_button"),
                            ) {
                                Icon(
                                    Icons.Filled.Sort,
                                    stringResource(R.string.albums_sort),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                AlbumSort.entries.forEach { s ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (s == albumSort) "✓ ${s.label}" else s.label,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        },
                                        onClick = {
                                            sortMenu = false
                                            onAlbumSortChange(s)
                                        },
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = { creating = true },
                            modifier = Modifier
                                .size(48.dp)
                                .glass(glass, CircleShape, GlassKind.Chip)
                                .border(0.75.dp, semantic.cardBorder, CircleShape)
                                .testTag("new_album_button"),
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                stringResource(R.string.albums_new),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AlbumFilter.entries, key = { it.name }) { f ->
                        FarFilterPill(
                            label = stringResource(f.labelRes),
                            selected = albumFilter == f,
                            onClick = { albumFilter = f },
                            modifier = Modifier.testTag("album_filter_${f.name.lowercase()}"),
                        )
                    }
                    item(key = "album_sort_drawer_toggle") {
                        FarFilterPill(
                            label = stringResource(R.string.filter_sort),
                            selected = filterDrawerOpen || albumSort != AlbumSort.Pinned,
                            leadingIcon = Icons.Filled.Tune,
                            onClick = { filterDrawerOpen = !filterDrawerOpen },
                            modifier = Modifier.testTag("album_filters_toggle"),
                        )
                    }
                }

                FarFilterDrawer(visible = filterDrawerOpen, glass = glass) {
                    Text(
                        stringResource(R.string.albums_sort),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AlbumSort.entries, key = { it.name }) { s ->
                            FarFilterPill(
                                label = s.label,
                                selected = albumSort == s,
                                onClick = { onAlbumSortChange(s) },
                                modifier = Modifier.testTag("album_sort_pill_${s.name.lowercase()}"),
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New album") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        onCreateAlbum(name.trim())
                        creating = false
                    }
                }) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    renaming?.let { album ->
        var name by remember(album) { mutableStateOf(album.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename album") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        onRenameAlbum(album, name.trim())
                        renaming = null
                    }
                }) { Text(stringResource(R.string.action_rename)) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    deleting?.let { album ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${album.name}”?") },
            text = { Text("Only the album grouping is removed. Your photos and videos stay on your device.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteAlbum(album)
                    deleting = null
                }) { Text("Delete album") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun MemoryCard(m: MemoryUi, onClick: () -> Unit) {
    Box(
        Modifier
            .width(240.dp)
            .height(150.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .testTag("memory_card_${m.id}")
    ) {
        m.coverUri?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(0.48f))
                .padding(12.dp)
        ) {
            Text(m.title, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(m.subtitle, color = Color.White.copy(0.82f), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Memories are plain rules over real dates (no cloud, no faces): "On this day" = same month/day in earlier years. */
object MemoryRules {
    fun onThisDay(takenDates: List<Pair<Long, Long>>, today: LocalDate = LocalDate.now()): Map<Int, List<Long>> =
        takenDates
            .groupBy({ Instant.ofEpochMilli(it.second).atZone(ZoneId.systemDefault()).toLocalDate() }, { it.first })
            .filterKeys { it.monthValue == today.monthValue && it.dayOfMonth == today.dayOfMonth && it.year < today.year }
            .entries
            .associate { it.key.year to it.value }
}

/* ------------------------------------ Search ------------------------------------ */
enum class SearchScope(val label: String) {
    All("All"),
    Photos("Photos"),
    Videos("Videos"),
    Screenshots("Screenshots"),
    Favorites("Favorites"),
}

enum class DateRangeFilter(val label: String) {
    Any("Any date"),
    Last7Days("7 days"),
    Last30Days("30 days"),
    ThisYear("This year"),
    Older("Older"),
}

enum class SizeFilter(val label: String, val minBytes: Long?) {
    Any("Any size", null),
    Over5Mb("> 5 MB", 5L * 1024 * 1024),
    Over25Mb("> 25 MB", 25L * 1024 * 1024),
    Over100Mb("> 100 MB", 100L * 1024 * 1024),
}

sealed interface IndexStatus {
    data object Ready : IndexStatus
    data class Indexing(val percent: Int) : IndexStatus
    data object Disabled : IndexStatus
    data object LimitedAccess : IndexStatus
}

@Composable
fun SearchScreen(
    query: String,
    onQuery: (String) -> Unit,
    onSubmitQuery: (String) -> Unit,
    scope: SearchScope,
    onScope: (SearchScope) -> Unit,
    dateRange: DateRangeFilter,
    onDateRange: (DateRangeFilter) -> Unit,
    sizeFilter: SizeFilter,
    onSizeFilter: (SizeFilter) -> Unit,
    recent: List<String>,
    onClearRecent: () -> Unit,
    index: IndexStatus,
    ocrCount: Int,
    onRunOcrScan: () -> Unit,
    resultCount: Int?,
    glass: HazeState,
    results: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasActiveFilter = query.isNotBlank() || scope != SearchScope.All || dateRange != DateRangeFilter.Any || sizeFilter != SizeFilter.Any
    val hasSecondaryFilter = dateRange != DateRangeFilter.Any || sizeFilter != SizeFilter.Any
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val semantic = LocalSemanticColors.current
    val reduced = LocalReducedMotion.current
    val density = LocalDensity.current
    var filterDrawerOpen by remember { mutableStateOf(false) }
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    val targetTopInset = if (topBarHeightPx > 0) with(density) { topBarHeightPx.toDp() } else statusTop + 158.dp
    val topInset by animateDpAsState(targetTopInset, FarMotion.spatial(reduced), label = "searchTopInset")

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .glassSource(glass)
                    .padding(top = topInset),
            ) {
                when {
                    !hasActiveFilter -> Column(
                        Modifier
                            .padding(horizontal = FarSpacing.screenPadding)
                            .padding(bottom = navBottom + FarSpacing.bottomDockClearance),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(semantic.elevatedSurface)
                                .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(18.dp))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.search_ocr_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    when (index) {
                                        is IndexStatus.Indexing -> stringResource(R.string.search_ocr_indexing, index.percent)
                                        IndexStatus.Disabled -> stringResource(R.string.search_ocr_disabled)
                                        IndexStatus.LimitedAccess -> stringResource(R.string.search_ocr_limited, ocrCount)
                                        IndexStatus.Ready -> stringResource(R.string.search_ocr_ready, ocrCount)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (index is IndexStatus.Ready || index is IndexStatus.LimitedAccess) {
                                TextButton(
                                    onClick = onRunOcrScan,
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("run_ocr_scan_button"),
                                ) {
                                    Icon(Icons.Filled.AutoAwesome, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.search_scan_now))
                                }
                            }
                        }

                        if (recent.isNotEmpty()) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.search_recent),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                TextButton(onClick = onClearRecent, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                                    Text(stringResource(R.string.action_clear))
                                }
                            }
                            recent.forEach { r ->
                                Text(
                                    r,
                                    Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onQuery(r); onSubmitQuery(r) }
                                        .padding(vertical = 12.dp, horizontal = 10.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        } else {
                            Text(
                                stringResource(R.string.search_tip),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    resultCount == 0 -> EmptyState(
                        Icons.Filled.Search,
                        if (index is IndexStatus.Indexing) stringResource(R.string.search_empty_indexing_title, index.percent)
                        else stringResource(R.string.search_empty_title),
                        when (index) {
                            is IndexStatus.Indexing -> stringResource(R.string.search_empty_indexing_body)
                            IndexStatus.LimitedAccess -> stringResource(R.string.search_empty_limited_body)
                            else -> stringResource(R.string.search_empty_body)
                        },
                        primary = if (scope != SearchScope.All || dateRange != DateRangeFilter.Any || sizeFilter != SizeFilter.Any) {
                            ErrorAction(stringResource(R.string.action_clear_all), true) {
                                onScope(SearchScope.All)
                                onDateRange(DateRangeFilter.Any)
                                onSizeFilter(SizeFilter.Any)
                            }
                        } else null,
                    )
                    else -> results()
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .onSizeChanged { topBarHeightPx = it.height }
                    .statusBarsPadding()
                    .padding(horizontal = FarSpacing.screenPadding, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.nav_search),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (hasActiveFilter) {
                        TextButton(
                            onClick = {
                                onQuery("")
                                onScope(SearchScope.All)
                                onDateRange(DateRangeFilter.Any)
                                onSizeFilter(SizeFilter.Any)
                            },
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("search_reset_filters"),
                        ) {
                            Text(stringResource(R.string.action_clear_all))
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .glass(glass, RoundedCornerShape(24.dp), GlassKind.Bar)
                        .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(24.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, null, Modifier.padding(end = 10.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                stringResource(R.string.search_placeholder),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQuery,
                            singleLine = true,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_input"),
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.Filled.Close,
                                stringResource(R.string.files_clear_search),
                                Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                // 1-row quick scope pills + collapsible Filters drawer trigger
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SearchScope.entries, key = { it.name }) { s ->
                        FarFilterPill(
                            label = s.label,
                            selected = s == scope,
                            onClick = { onScope(s) },
                            modifier = Modifier.testTag("search_scope_${s.name.lowercase()}"),
                        )
                    }
                    item(key = "search_filters_toggle") {
                        FarFilterPill(
                            label = stringResource(R.string.filter_filters),
                            selected = filterDrawerOpen || hasSecondaryFilter,
                            leadingIcon = Icons.Filled.Tune,
                            onClick = { filterDrawerOpen = !filterDrawerOpen },
                            modifier = Modifier.testTag("search_filters_toggle"),
                        )
                    }
                }

                FarFilterDrawer(visible = filterDrawerOpen, glass = glass) {
                    Text(
                        stringResource(R.string.filter_date),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(DateRangeFilter.entries, key = { it.name }) { d ->
                            FarFilterPill(
                                label = d.label,
                                selected = d == dateRange,
                                onClick = { onDateRange(d) },
                                modifier = Modifier.testTag("search_date_${d.name.lowercase()}"),
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.filter_size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(SizeFilter.entries, key = { it.name }) { sz ->
                            FarFilterPill(
                                label = sz.label,
                                selected = sz == sizeFilter,
                                onClick = { onSizeFilter(sz) },
                                modifier = Modifier.testTag("search_size_${sz.name.lowercase()}"),
                            )
                        }
                    }
                }
            }
        }
    }
}
