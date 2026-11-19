package com.example.feature.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.foundation.lazy.LazyRow
import com.example.R
import com.example.ui.components.FarFilterDrawer
import com.example.ui.components.FarFilterPill
import com.example.ui.feedback.EmptyReason
import com.example.ui.feedback.EmptyState
import com.example.ui.feedback.ErrorAction
import com.example.ui.feedback.ErrorCard
import com.example.ui.feedback.UiState
import com.example.ui.feedback.skeleton
import com.example.ui.glass.GlassKind
import com.example.ui.glass.glass
import com.example.ui.glass.glassSource
import com.example.ui.theme.FarSpacing
import com.example.ui.theme.LocalSemanticColors
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
    val searchQuery: String = "",
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
    fun setSearch(query: String)
    fun selectAll()
    fun clearSelection()
    fun batchAction(action: String)
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
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (maxWidth >= 840.dp) DesktopLayout(state, actions, glass) else PhoneLayout(state, actions, glass)
        }
    }
}

/* ------------------------------ Phone (Apple Files) ------------------------------ */
@Composable
private fun PhoneLayout(state: FilesUiState, a: FilesActions, glass: HazeState) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    if (state.selectedPlaceId == null) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize().glassSource(glass),
                contentPadding = PaddingValues(
                    top = statusTop + 72.dp,
                    bottom = navBottom + FarSpacing.bottomDockClearance,
                    start = FarSpacing.screenPadding,
                    end = FarSpacing.screenPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "locations_header") {
                    Text(
                        stringResource(R.string.files_locations),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                items(state.places, key = { it.id }) { p -> PlaceRow(p) { a.openPlace(p.id) } }
                if (state.places.none { it.id != "add" && it.id != "starred" }) {
                    item(key = "locations_empty") {
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
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = FarSpacing.screenPadding, vertical = 10.dp),
            )
        }
    } else {
        var searching by remember(state.selectedPlaceId) { mutableStateOf(false) }
        var filterDrawerOpen by remember(state.selectedPlaceId) { mutableStateOf(false) }
        var sortOpen by remember { mutableStateOf(false) }
        val semantic = LocalSemanticColors.current
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = FarSpacing.screenPadding, vertical = 8.dp)
                        .glass(glass, RoundedCornerShape(26.dp))
                        .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(26.dp))
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = a::up, modifier = Modifier.size(48.dp).testTag("files_up_button")) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.cd_back),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        if (state.selection.isNotEmpty()) stringResource(R.string.selected_count, state.selection.size)
                        else state.crumbs.lastOrNull() ?: stringResource(R.string.nav_files),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.clipboardCount > 0) {
                        TextButton(onClick = a::paste, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                            Text(stringResource(R.string.action_paste, state.clipboardCount))
                        }
                    }
                    IconButton(
                        onClick = {
                            searching = !searching
                            if (!searching) a.setSearch("")
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            if (searching) Icons.Filled.Close else Icons.Filled.Search,
                            stringResource(R.string.files_filter_hint),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Box {
                        IconButton(onClick = { sortOpen = true }, modifier = Modifier.size(48.dp).testTag("files_sort_button")) {
                            Icon(Icons.Filled.Sort, "Sort", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                            SortColumn.entries.forEach { col ->
                                val arrow = if (state.sort == col) (if (state.ascending) " ▲" else " ▼") else ""
                                DropdownMenuItem(
                                    text = { Text("${col.name}$arrow", color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = { sortOpen = false; a.sortBy(col) },
                                )
                            }
                        }
                    }
                    ViewToggle(state.view, a::setView)
                    if (state.selectedPlaceId != "starred") {
                        IconButton(onClick = a::newFolder, modifier = Modifier.size(48.dp).testTag("files_new_folder_button")) {
                            Icon(Icons.Filled.CreateNewFolder, "New folder", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                // 1-row quick filter & layout pills + collapsible FarFilterDrawer
                LazyRow(
                    contentPadding = PaddingValues(horizontal = FarSpacing.screenPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    item(key = "files_sort_pill") {
                        FarFilterPill(
                            label = "${state.sort.name} ${if (state.ascending) "↑" else "↓"}",
                            selected = filterDrawerOpen,
                            leadingIcon = Icons.Filled.Tune,
                            onClick = { filterDrawerOpen = !filterDrawerOpen },
                            modifier = Modifier.testTag("files_filters_toggle"),
                        )
                    }
                    item(key = "files_hidden_pill") {
                        FarFilterPill(
                            label = stringResource(R.string.filter_hidden_files),
                            selected = state.showHidden,
                            leadingIcon = Icons.Filled.VisibilityOff,
                            onClick = a::toggleHidden,
                            modifier = Modifier.testTag("files_hidden_pill"),
                        )
                    }
                    item(key = "files_view_pill") {
                        FarFilterPill(
                            label = if (state.view == FilesView.Grid) "Grid" else "List",
                            selected = state.view == FilesView.Grid,
                            leadingIcon = if (state.view == FilesView.Grid) Icons.Filled.GridView else Icons.Filled.ViewList,
                            onClick = { a.setView(if (state.view == FilesView.Grid) FilesView.List else FilesView.Grid) },
                            modifier = Modifier.testTag("files_view_pill"),
                        )
                    }
                }

                FarFilterDrawer(
                    visible = filterDrawerOpen,
                    glass = glass,
                    modifier = Modifier.padding(horizontal = FarSpacing.screenPadding, vertical = 4.dp),
                ) {
                    Text(
                        stringResource(R.string.filter_sort),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(SortColumn.entries.size) { idx ->
                            val col = SortColumn.entries[idx]
                            val active = state.sort == col
                            FarFilterPill(
                                label = col.name + if (active) (if (state.ascending) " ▲" else " ▼") else "",
                                selected = active,
                                leadingIcon = if (active) Icons.Filled.SwapVert else null,
                                onClick = { a.sortBy(col) },
                                modifier = Modifier.testTag("files_sort_pill_${col.name.lowercase()}"),
                            )
                        }
                    }
                }

                if (searching) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = a::setSearch,
                        placeholder = { Text(stringResource(R.string.files_filter_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = FarSpacing.screenPadding, vertical = 4.dp)
                            .testTag("files_folder_search_input"),
                    )
                }
                EntriesPane(state, a, Modifier.weight(1f).glassSource(glass), showColumns = false)
            }

            if (state.selection.isNotEmpty() && state.selectedPlaceId != "starred") {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = FarSpacing.screenPadding)
                        .padding(bottom = 84.dp)
                        .glass(glass, RoundedCornerShape(26.dp), GlassKind.Bar)
                        .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(26.dp))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = a::clearSelection, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, stringResource(R.string.cd_close))
                    }
                    TextButton(onClick = a::selectAll, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                        Text(stringResource(R.string.batch_all))
                    }
                    TextButton(onClick = { a.batchAction("copy") }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                        Text(stringResource(R.string.action_copy))
                    }
                    TextButton(onClick = { a.batchAction("move") }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                        Text(stringResource(R.string.action_move))
                    }
                    TextButton(onClick = { a.batchAction("zip") }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                        Text("ZIP")
                    }
                    TextButton(onClick = { a.batchAction("delete") }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
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
                    Text(
                        p.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
                IconButton(onClick = a::up, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = MaterialTheme.colorScheme.onSurface)
                }
                PathBar(state.crumbs, a::navigateToCrumb, Modifier.weight(1f))
                if (state.clipboardCount > 0) {
                    TextButton(onClick = a::paste, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                        Text(stringResource(R.string.action_paste, state.clipboardCount))
                    }
                }
                TextButton(onClick = a::toggleHidden, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                    Text(if (state.showHidden) "Hide hidden" else "Show hidden")
                }
                ViewToggle(state.view, a::setView)
                if (state.selectedPlaceId != null && state.selectedPlaceId != "starred") {
                    IconButton(onClick = a::newFolder, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.CreateNewFolder, "New folder", tint = MaterialTheme.colorScheme.onSurface)
                    }
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
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ViewToggle(view: FilesView, onChange: (FilesView) -> Unit) {
    IconButton(
        onClick = { onChange(if (view == FilesView.Grid) FilesView.List else FilesView.Grid) },
        modifier = Modifier.size(48.dp),
    ) {
        Icon(
            if (view == FilesView.Grid) Icons.Filled.ViewList else Icons.Filled.GridView,
            if (view == FilesView.Grid) "List view" else "Grid view",
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/* ------------------------------ Entries ------------------------------ */
@Composable
private fun EntriesPane(state: FilesUiState, a: FilesActions, modifier: Modifier, showColumns: Boolean) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier) {
        when (val s = state.entries) {
            UiState.Loading -> Column(
                Modifier.padding(top = 12.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(10) {
                    Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = FarSpacing.screenPadding).clip(RoundedCornerShape(12.dp)).skeleton())
                }
            }
            is UiState.Failure -> ErrorCard(s.error, Modifier.align(Alignment.Center))
            is UiState.Empty -> if (s.reason == EmptyReason.NoResults) {
                EmptyState(
                    Icons.Filled.Search,
                    stringResource(R.string.files_no_match_title),
                    stringResource(R.string.files_no_match_body, state.searchQuery),
                    primary = ErrorAction(stringResource(R.string.files_clear_search), true) { a.setSearch("") },
                )
            } else {
                EmptyState(
                    Icons.Filled.Folder,
                    stringResource(R.string.files_empty_title),
                    stringResource(R.string.files_empty_body),
                )
            }
            is UiState.Content -> {
                val list = s.data
                val bottomPad = navBottom + FarSpacing.bottomDockClearance + if (state.selection.isNotEmpty()) 56.dp else 0.dp
                if (state.view == FilesView.List) {
                    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = bottomPad)) {
                        if (showColumns) item(key = "header") { ColumnHeader(state, a) }
                        items(list, key = { it.id }) { e -> EntryRow(e, e.id in state.selection, a) }
                    }
                } else {
                    LazyVerticalGrid(
                        GridCells.Adaptive(112.dp),
                        contentPadding = PaddingValues(start = FarSpacing.screenPadding, end = FarSpacing.screenPadding, top = 8.dp, bottom = bottomPad),
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
                Text(
                    e.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
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
            Text(
                e.name,
                Modifier.padding(top = 6.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
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
            DropdownMenuItem(
                text = { Text(label, color = if (key == "delete") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
                enabled = enabled,
                onClick = { dismiss(); a.contextAction(e, key) },
            )
        }
        DropdownMenuItem(
            text = { Text("Select", color = MaterialTheme.colorScheme.onSurface) },
            onClick = { dismiss(); a.toggleSelect(e) },
        )
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
    val semantic = LocalSemanticColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(semantic.elevatedSurface)
            .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(16.dp))
            .clickable(enabled = p.available, onClick = onClick)
            .padding(14.dp)
            .testTag("place_${p.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(p.icon, null, Modifier.size(28.dp), tint = if (p.available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(
                p.label + if (!p.available) " (unavailable)" else "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
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
