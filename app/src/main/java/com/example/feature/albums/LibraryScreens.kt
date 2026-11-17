package com.example.feature.albums

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.feedback.EmptyState
import com.example.ui.glass.GlassKind
import com.example.ui.glass.glass
import com.example.ui.glass.glassSource
import dev.chrisbanes.haze.HazeState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/* ------------------------------------ Albums & Collections ------------------------------------ */
@Immutable
data class AlbumUi(
    val id: Long,
    val name: String,
    val count: Int,
    val coverUri: String?,
    val pinned: Boolean,
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
    Duplicates(R.string.collection_duplicates, Icons.Filled.ContentCopy),
    RecentlyDeleted(R.string.collection_trash, Icons.Filled.DeleteOutline),
    ExcludedFolders(R.string.collection_excluded, Icons.Filled.FolderOff),
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumsScreen(
    albums: List<AlbumUi>,
    memories: List<MemoryUi>,
    glass: HazeState,
    onOpenAlbum: (AlbumUi) -> Unit,
    onOpenMemory: (MemoryUi) -> Unit,
    onOpenCollection: (SystemCollection) -> Unit,
    onCreateAlbum: (String) -> Unit,
    onRenameAlbum: (AlbumUi, String) -> Unit,
    onTogglePin: (AlbumUi) -> Unit,
    onDeleteAlbum: (AlbumUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<AlbumUi?>(null) }
    var deleting by remember { mutableStateOf<AlbumUi?>(null) }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyVerticalGrid(
            GridCells.Fixed(2),
            Modifier.fillMaxSize().glassSource(glass),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 112.dp, bottom = 132.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (memories.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Column {
                        Text(
                            stringResource(R.string.memories_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(memories, key = { it.id }) { m -> MemoryCard(m) { onOpenMemory(m) } }
                        }
                    }
                }
            }

            item(span = { GridItemSpan(2) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.collections_title), style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(SystemCollection.entries) { col ->
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { onOpenCollection(col) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                                    .testTag("collection_${col.name.lowercase()}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(col.icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(stringResource(col.labelRes), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            if (albums.isEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    EmptyState(
                        Icons.Filled.PhotoAlbum,
                        "No albums yet",
                        "Create an album to collect photos from anywhere. Original files stay where they are.",
                        Modifier.height(260.dp),
                    )
                }
            } else {
                items(albums, key = { it.id }) { a ->
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
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
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
                                        .padding(8.dp)
                                        .size(18.dp),
                                    tint = Color.White,
                                )
                            }
                            IconButton(
                                onClick = { menu = true },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(36.dp)
                                    .padding(4.dp)
                                    .background(Color.Black.copy(0.35f), CircleShape),
                            ) {
                                Icon(Icons.Filled.MoreVert, "Album options", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (a.pinned) "Unpin" else "Pin to top") },
                                    onClick = { menu = false; onTogglePin(a) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_rename)) },
                                    onClick = { menu = false; renaming = a },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete album") },
                                    onClick = { menu = false; deleting = a },
                                )
                            }
                        }
                        Text(a.name, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text("${a.count}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.albums_title), style = MaterialTheme.typography.displayLarge)
            IconButton(
                onClick = { creating = true },
                modifier = Modifier
                    .glass(glass, RoundedCornerShape(22.dp), GlassKind.Chip)
                    .testTag("new_album_button"),
            ) {
                Icon(Icons.Filled.Add, "New album")
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
    Large("Large files"),
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
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxSize().glassSource(glass).padding(top = 196.dp)) {
            when {
                query.isBlank() && scope == SearchScope.All -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("On-device image text (OCR)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                when (index) {
                                    is IndexStatus.Indexing -> "Indexing photos… ${index.percent}%"
                                    IndexStatus.Disabled -> "Turned off in Settings"
                                    IndexStatus.LimitedAccess -> "$ocrCount indexed (selected access)"
                                    IndexStatus.Ready -> "$ocrCount photos indexed locally"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (index is IndexStatus.Ready || index is IndexStatus.LimitedAccess) {
                            TextButton(onClick = onRunOcrScan, modifier = Modifier.testTag("run_ocr_scan_button")) {
                                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Scan now")
                            }
                        }
                    }

                    if (recent.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Recent", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClearRecent) { Text("Clear") }
                        }
                        recent.forEach { r ->
                            Text(
                                r,
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onQuery(r); onSubmitQuery(r) }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    } else {
                        Text(
                            "Search file names, tags, and — once scanned — text inside photos.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                resultCount == 0 -> EmptyState(
                    Icons.Filled.Search,
                    if (index is IndexStatus.Indexing) "Still indexing (${index.percent}%)" else "No results for “$query”",
                    when (index) {
                        is IndexStatus.Indexing -> "Text in images isn't fully searchable yet. File-name results are complete."
                        IndexStatus.LimitedAccess -> "Far Gallery can only search the photos you've allowed."
                        else -> "Try a different spelling or switch filter chips."
                    },
                )
                else -> results()
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.nav_search), style = MaterialTheme.typography.displayLarge)
            Row(
                Modifier
                    .fillMaxWidth()
                    .glass(glass, RoundedCornerShape(22.dp), GlassKind.Bar)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, Modifier.padding(end = 10.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            "File name, tag, or photo text…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    IconButton(onClick = { onQuery("") }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, "Clear search", Modifier.size(18.dp))
                    }
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SearchScope.entries) { s ->
                    FilterChip(
                        selected = s == scope,
                        onClick = { onScope(s) },
                        label = { Text(s.label) },
                        modifier = Modifier.testTag("search_scope_${s.name.lowercase()}"),
                    )
                }
            }
        }
    }
}
