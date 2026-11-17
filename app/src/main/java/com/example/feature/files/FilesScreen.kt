package com.example.feature.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.feedback.EmptyState
import com.example.ui.feedback.ErrorAction
import com.example.ui.feedback.ErrorCard
import com.example.ui.feedback.UiState
import com.example.ui.feedback.skeleton
import com.example.ui.glass.GlassKind
import com.example.ui.glass.glass
import com.example.ui.glass.glassSource
import dev.chrisbanes.haze.HazeState

@Immutable
data class FileEntry(
    val id: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long?,
    val modifiedLabel: String?,
    val typeLabel: String,
    val mime: String?,
    val canRename: Boolean,
    val canDelete: Boolean,
    val canMove: Boolean,
    val isStarred: Boolean = false,
)

@Immutable
data class Place(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val usedFraction: Float? = null,
    val available: Boolean = true,
)

enum class SortColumn { Name, Size, Type, Modified }
enum class FilesView { Grid, List }

@Immutable
data class FilesUiState(
    val places: List<Place>,
    val selectedPlaceId: String?,
    val crumbs: List<String>,
    val entries: UiState<List<FileEntry>>,
    val view: FilesView = FilesView.List,
    val sort: SortColumn = SortColumn.Name,
    val ascending: Boolean = true,
    val showHidden: Boolean = false,
    val selection: Set<String> = emptySet(),
    val clipboardCount: Int = 0,
)

interface FilesActions {
    fun openPlace(id: String)
    fun openEntry(e: FileEntry)
    fun navigateToCrumb(index: Int)
    fun up()
    fun setView(v: FilesView)
    fun sortBy(c: SortColumn)
    fun toggleHidden()
    fun toggleSelect(e: FileEntry)
    fun newFolder()
    fun contextAction(e: FileEntry, action: String)
    fun retry()
    fun paste()
}

/** Phone = Apple Files (Locations + push navigation). >= 840dp = Ubuntu Files (sidebar + path bar + columns). */
@Composable
fun FilesScreen(state: FilesUiState, actions: FilesActions, glass: HazeState, modifier: Modifier = Modifier) {
    BackHandler(enabled = state.selectedPlaceId != null) {
        actions.up()
    }
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (maxWidth >= 840.dp) DesktopLayout(state, actions, glass) else PhoneLayout(state, actions, glass)
    }
}

/* ------------------------------ Phone (Apple Files) ------------------------------ */
@Composable
private fun PhoneLayout(state: FilesUiState, a: FilesActions, glass: HazeState) {
    if (state.selectedPlaceId == null) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize().glassSource(glass),
                contentPadding = PaddingValues(top = 110.dp, bottom = 132.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        "Locations",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                items(state.places, key = { it.id }) { p -> PlaceRow(p) { a.openPlace(p.id) } }
                if (state.places.none { it.id != "add" && it.id != "starred" }) {
                    item {
                        EmptyState(
                            Icons.Filled.Folder,
                            "Grant a folder to browse",
                            "Pick Internal storage, Downloads, or an SD card with Android's folder picker. Far Files only touches folders you choose.",
                            Modifier.height(260.dp),
                            primary = ErrorAction("Add folder…", true) { a.openPlace("add") },
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.nav_files),
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.statusBarsPadding().padding(16.dp),
            )
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .glass(glass, RoundedCornerShape(28.dp))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = a::up, modifier = Modifier.testTag("files_up_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Up")
                }
                Text(
                    state.crumbs.lastOrNull() ?: stringResource(R.string.nav_files),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.clipboardCount > 0) {
                    TextButton(onClick = a::paste) { Text(stringResource(R.string.action_paste, state.clipboardCount)) }
                }
                ViewToggle(state.view, a::setView)
                if (state.selectedPlaceId != "starred") {
                    IconButton(onClick = a::newFolder, modifier = Modifier.testTag("files_new_folder_button")) {
                        Icon(Icons.Filled.CreateNewFolder, "New folder")
                    }
                }
            }
            EntriesPane(state, a, Modifier.weight(1f).glassSource(glass), showColumns = false)
        }
    }
}

/* ------------------------------ Desktop / tablet (Ubuntu Files) ------------------------------ */
@Composable
private fun DesktopLayout(state: FilesUiState, a: FilesActions, glass: HazeState) {
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .width(240.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.6f))
                .statusBarsPadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "Places",
                Modifier.padding(8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.places.forEach { p ->
                val sel = p.id == state.selectedPlaceId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (sel) MaterialTheme.colorScheme.primary.copy(0.16f) else Color.Transparent)
                        .clickable { a.openPlace(p.id) }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        p.icon,
                        null,
                        Modifier.size(20.dp),
                        tint = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(p.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(10.dp)
                    .glass(glass, RoundedCornerShape(22.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(onClick = a::up) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) }
                PathBar(state.crumbs, a::navigateToCrumb, Modifier.weight(1f))
                if (state.clipboardCount > 0) {
                    TextButton(onClick = a::paste) { Text(stringResource(R.string.action_paste, state.clipboardCount)) }
                }
                TextButton(onClick = a::toggleHidden) { Text(if (state.showHidden) "Hide hidden" else "Show hidden") }
                ViewToggle(state.view, a::setView)
                if (state.selectedPlaceId != null && state.selectedPlaceId != "starred") {
                    IconButton(onClick = a::newFolder) { Icon(Icons.Filled.CreateNewFolder, "New folder") }
                }
            }
            if (state.selectedPlaceId == null) {
                EmptyState(
                    Icons.Filled.Folder,
                    "Select a folder on the left",
                    "Or add a new folder using Android's folder picker.",
                    primary = ErrorAction("Add folder…", true) { a.openPlace("add") },
                )
            } else {
                EntriesPane(state, a, Modifier.weight(1f).glassSource(glass), showColumns = true)
            }
        }
    }
}

@Composable
private fun PathBar(crumbs: List<String>, onCrumb: (Int) -> Unit, modifier: Modifier) {
    Row(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        crumbs.forEachIndexed { i, c ->
            if (i > 0) Icon(Icons.Filled.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                c,
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onCrumb(i) }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ViewToggle(view: FilesView, onChange: (FilesView) -> Unit) {
    IconButton(onClick = { onChange(if (view == FilesView.Grid) FilesView.List else FilesView.Grid) }) {
        Icon(if (view == FilesView.Grid) Icons.Filled.ViewList else Icons.Filled.GridView, if (view == FilesView.Grid) "List view" else "Grid view")
    }
}

/* ------------------------------ Entries ------------------------------ */
@Composable
private fun EntriesPane(state: FilesUiState, a: FilesActions, modifier: Modifier, showColumns: Boolean) {
    Box(modifier) {
        when (val s = state.entries) {
            UiState.Loading -> Column(
                Modifier.padding(top = 12.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(10) {
                    Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).skeleton())
                }
            }
            is UiState.Failure -> ErrorCard(s.error, Modifier.align(Alignment.Center))
            is UiState.Empty -> EmptyState(Icons.Filled.Folder, "This folder is empty", "Nothing to show here.")
            is UiState.Content -> {
                val list = s.data
                if (state.view == FilesView.List) {
                    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 132.dp)) {
                        if (showColumns) item(key = "header") { ColumnHeader(state, a) }
                        items(list, key = { it.id }) { e -> EntryRow(e, e.id in state.selection, a) }
                    }
                } else {
                    LazyVerticalGrid(
                        GridCells.Adaptive(112.dp),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 132.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(list, key = { it.id }) { e -> EntryTile(e, e.id in state.selection, a) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnHeader(state: FilesUiState, a: FilesActions) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        listOf(SortColumn.Name to 4f, SortColumn.Size to 1.2f, SortColumn.Type to 1.5f, SortColumn.Modified to 2f).forEach { (c, w) ->
            val active = state.sort == c
            Text(
                c.name + if (active) (if (state.ascending) " ▲" else " ▼") else "",
                Modifier.weight(w).clickable { a.sortBy(c) },
                style = MaterialTheme.typography.labelMedium,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(e: FileEntry, selected: Boolean, a: FilesActions) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (selected) MaterialTheme.colorScheme.primary.copy(0.14f) else Color.Transparent)
                .combinedClickable(onClick = { a.openEntry(e) }, onLongClick = { menu = true })
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(4f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FileIcon(e)
                Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                if (e.isStarred) Icon(Icons.Filled.Star, "Starred", Modifier.size(14.dp), tint = Color(0xFFFF9F0A))
            }
            Text(e.sizeBytes?.let(::humanSize) ?: "—", Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(e.typeLabel, Modifier.weight(1.5f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(e.modifiedLabel ?: "—", Modifier.weight(2f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        EntryMenu(menu, { menu = false }, e, a)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryTile(e: FileEntry, selected: Boolean, a: FilesActions) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) MaterialTheme.colorScheme.primary.copy(0.14f) else Color.Transparent)
                .combinedClickable(onClick = { a.openEntry(e) }, onLongClick = { menu = true })
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FileIcon(e, 56.dp)
            Text(e.name, Modifier.padding(top = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
        }
        EntryMenu(menu, { menu = false }, e, a)
    }
}

@Composable
private fun EntryMenu(open: Boolean, dismiss: () -> Unit, e: FileEntry, a: FilesActions) {
    val isZip = !e.isDirectory && (e.mime == "application/zip" || e.name.endsWith(".zip", ignoreCase = true))
    DropdownMenu(expanded = open, onDismissRequest = dismiss) {
        val entries = buildList {
            add(Triple("Open", "open", true))
            add(Triple("Share", "share", !e.isDirectory))
            add(Triple("Copy", "copy", true))
            add(Triple("Move", "move", e.canMove))
            add(Triple(stringResource(R.string.action_rename), "rename", e.canRename))
            add(Triple(if (e.isStarred) "Unstar" else "Star", "star", true))
            if (isZip) add(Triple("Extract ZIP here", "extract", true))
            add(Triple("Properties", "properties", true))
            add(Triple(stringResource(R.string.action_delete), "delete", e.canDelete))
        }
        entries.forEach { (label, key, enabled) ->
            DropdownMenuItem(text = { Text(label) }, enabled = enabled, onClick = { dismiss(); a.contextAction(e, key) })
        }
    }
}

@Composable
private fun FileIcon(e: FileEntry, size: androidx.compose.ui.unit.Dp = 28.dp) {
    val (icon, tint) = when {
        e.isDirectory -> Icons.Filled.Folder to Color(0xFF4DA3FF)
        e.mime?.startsWith("image/") == true -> Icons.Filled.Image to Color(0xFF30D158)
        e.mime?.startsWith("video/") == true -> Icons.Filled.Movie to Color(0xFFBF5AF2)
        e.mime == "application/zip" || e.name.endsWith(".zip", ignoreCase = true) -> Icons.Filled.FolderZip to Color(0xFFFF9F0A)
        else -> Icons.AutoMirrored.Filled.InsertDriveFile to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Icon(icon, null, Modifier.size(size), tint = tint)
}

@Composable
private fun PlaceRow(p: Place, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = p.available, onClick = onClick)
            .padding(14.dp)
            .testTag("place_${p.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(p.icon, null, Modifier.size(28.dp), tint = if (p.available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(p.label + if (!p.available) " (unavailable)" else "", style = MaterialTheme.typography.bodyLarge)
            p.usedFraction?.let { LinearProgressIndicator(progress = { it }, Modifier.fillMaxWidth().padding(top = 6.dp)) }
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun humanSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "%.1f KB".format(b / 1024.0)
    b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / 1048576.0)
    else -> "%.2f GB".format(b / 1073741824.0)
}
