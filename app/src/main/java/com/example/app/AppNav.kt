package com.example.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.testTag
import com.example.Access
import com.example.AppViewModel
import com.example.CameraHost
import com.example.Permissions
import com.example.R
import com.example.core.common.FarResult
import com.example.core.common.LocalDiagnostics
import com.example.core.common.safeParseMediaUri
import com.example.core.common.userTitle
import com.example.core.database.EditRecipe
import com.example.core.database.FarDatabase
import com.example.core.media.BatchMediaOps
import com.example.core.media.ExifScrubber
import com.example.core.media.GalleryViewModel
import com.example.core.media.MediaFilter
import com.example.core.media.MediaKind
import com.example.core.media.MediaRepository
import com.example.core.media.MediaRow
import com.example.core.media.OcrIndexer
import com.example.core.media.SortKey
import com.example.feature.albums.AlbumSort
import com.example.feature.albums.AlbumUi
import com.example.feature.albums.AlbumsScreen
import com.example.feature.albums.DateRangeFilter
import com.example.feature.albums.IndexStatus
import com.example.feature.albums.MemoryRules
import com.example.feature.albums.MemoryUi
import com.example.feature.albums.SearchScope
import com.example.feature.albums.SearchScreen
import com.example.feature.albums.SizeFilter
import com.example.feature.albums.SystemCollection
import com.example.feature.camera.CameraViewModel
import com.example.feature.duplicates.DupGroup
import com.example.feature.duplicates.DupScan
import com.example.feature.duplicates.DuplicateFinder
import com.example.feature.duplicates.DuplicatesScreen
import com.example.feature.editor.EditorEngine
import com.example.feature.editor.EditorScreen
import com.example.feature.editor.EditState
import com.example.feature.gallery.GalleryItem
import com.example.feature.gallery.GalleryScreen
import com.example.feature.onboarding.OnboardingScreen
import com.example.feature.settings.SettingsScreen
import com.example.feature.storage.StorageAnalyzerScreen
import com.example.feature.storage.TrashManagerScreen
import com.example.feature.video.FrameGrabber
import com.example.feature.video.TrimPanel
import com.example.feature.video.VideoPlayer
import com.example.feature.video.VideoTrimmer
import com.example.feature.viewer.CompareScreen
import com.example.feature.viewer.ViewerItem
import com.example.feature.viewer.ViewerScreen
import com.example.ui.components.RecoveryBanner
import com.example.ui.feedback.FarErrorBoundary
import com.example.ui.feedback.GlassToast
import com.example.ui.feedback.OperationProgressSheet
import com.example.ui.feedback.OperationUi
import com.example.ui.glass.GlassKind
import com.example.ui.glass.glass
import com.example.ui.glass.rememberGlass
import com.example.ui.nav.FarTab
import com.example.ui.nav.GlassBottomBar
import com.example.ui.nav.GlassNavRail
import com.example.ui.theme.FarPalette
import com.example.ui.theme.FarSpacing
import com.example.ui.theme.FarTheme
import com.example.ui.theme.LocalSemanticColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.farShared(key: Any, sts: SharedTransitionScope, avs: AnimatedVisibilityScope): Modifier =
    with(sts) { this@farShared.sharedElement(rememberSharedContentState(key), avs) }

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FarApp(vm: AppViewModel, activity: Activity, startOnFiles: Boolean) {
    val settings by vm.settings.collectAsState()
    val s = settings ?: return
    val access by vm.access.collectAsState()
    val safeMode by vm.safeMode.collectAsState()
    val route by vm.route.collectAsState()
    val incoming by vm.incoming.collectAsState()
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current

    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vm.refreshAccess() }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }

    FarTheme(
        mode = s.theme,
        accent = FarPalette.curated.getOrElse(s.accentIndex) { FarPalette.Blue },
        useDynamicColor = s.dynamicColor,
        reduceMotion = s.reduceMotion,
        reduceTransparency = s.reduceTransparency,
        hapticsEnabled = s.haptics,
    ) {
        val nav = rememberNavController()
        val photosGlass = rememberGlass()
        val albumsGlass = rememberGlass()
        val searchGlass = rememberGlass()
        val filesGlass = rememberGlass()
        val subGlass = rememberGlass()
        val scope = rememberCoroutineScope()
        val db = remember { FarDatabase.get(ctx) }
        val repo = remember { MediaRepository(ctx.contentResolver) }
        val ocrIndexer = remember { OcrIndexer(ctx) }
        val gvm: GalleryViewModel = viewModel(factory = GalleryViewModel.factory(ctx.contentResolver, db))
        val items = gvm.items.collectAsLazyPagingItems()
        val selection by gvm.selection.collectAsState()
        val zoom by gvm.zoom.collectAsState()
        val filter by gvm.filter.collectAsState()
        val gallerySort by gvm.sort.collectAsState()
        val galleryDesc by gvm.descending.collectAsState()
        val favorites by gvm.favoriteIds.collectAsState()
        val albums by gvm.albums.collectAsState()
        val albumRefs by gvm.albumRefs.collectAsState()
        val libraryRows by gvm.libraryRows.collectAsState()
        val recentSearches by gvm.recentSearches.collectAsState()
        val ocrCount by gvm.ocrCount.collectAsState()
        val excludedFolders by gvm.excludedFolders.collectAsState()
        val interruptedOps by remember(db) {
            db.operations().interrupted().catch { emit(emptyList()) }
        }.collectAsState(initial = emptyList())

        var viewerList by remember { mutableStateOf<List<ViewerItem>>(emptyList()) }
        var viewerIndex by remember { mutableIntStateOf(0) }
        var tab by rememberSaveable { mutableStateOf(if (startOnFiles) FarTab.Files else FarTab.Photos) }
        var toast by remember { mutableStateOf<String?>(null) }
        var favoritesFilter by rememberSaveable { mutableStateOf(false) }
        var albumPickerTargets by remember { mutableStateOf<List<Long>?>(null) }
        var coverPickerAlbum by remember { mutableStateOf<AlbumUi?>(null) }

        var searchQuery by rememberSaveable { mutableStateOf("") }
        var searchScope by remember { mutableStateOf(SearchScope.All) }
        var dateRange by remember { mutableStateOf(DateRangeFilter.Any) }
        var sizeFilter by remember { mutableStateOf(SizeFilter.Any) }
        var ocrPercent by remember { mutableStateOf<Int?>(null) }

        var trimTarget by remember { mutableStateOf<Pair<Uri, Long>?>(null) }
        var trimBusy by remember { mutableStateOf(false) }
        var trimProgress by remember { mutableStateOf<Float?>(null) }

        var trashedRefreshNonce by remember { mutableIntStateOf(0) }
        var batchResizeTargets by remember { mutableStateOf<List<MediaRow>?>(null) }
        var batchRenameTargets by remember { mutableStateOf<List<MediaRow>?>(null) }
        var batchProgress by remember { mutableStateOf<OperationUi?>(null) }
        var batchJob by remember { mutableStateOf<Job?>(null) }
        var comparePickerSourceId by remember { mutableStateOf<Long?>(null) }

        LaunchedEffect(libraryRows, favorites) {
            LibraryStatsBridge.syncFromRows(ctx, libraryRows, favorites.size)
        }

        val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            vm.refreshAccess()
            gvm.refreshAll()
            items.refresh()
        }
        val selectedOnlyPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            if (uris.isNotEmpty()) {
                viewerList = uris.mapIndexed { idx, uri ->
                    ViewerItem(id = -(idx + 1L), uri = uri.toString(), title = "Selected item", subtitle = null, isVideo = false, isFavorite = false)
                }
                viewerIndex = 0
                nav.navigate("viewer")
            }
        }
        val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
            if (res.resultCode == Activity.RESULT_OK) {
                toast = "Moved to Recently Deleted"
                trashedRefreshNonce++
                gvm.clearSelection()
                gvm.refreshAll()
                items.refresh()
            } else {
                toast = "Nothing was deleted"
            }
        }
        val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
            if (res.resultCode == Activity.RESULT_OK) {
                toast = "Restored to library"
                trashedRefreshNonce++
                gvm.refreshAll()
                items.refresh()
            }
        }
        val permanentDeleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
            if (res.resultCode == Activity.RESULT_OK) {
                toast = "Permanently deleted"
                trashedRefreshNonce++
                gvm.refreshAll()
                items.refresh()
            } else {
                toast = "Nothing was deleted"
            }
        }

        fun trash(uris: List<Uri>) {
            if (uris.isEmpty()) return
            runCatching { trashLauncher.launch(IntentSenderRequest.Builder(repo.trashRequest(uris, true)).build()) }
                .onFailure {
                    LocalDiagnostics.recordEvent(ctx, "FG-TRASH", "trash_request", it.javaClass.simpleName)
                    toast = "Couldn't open delete confirmation."
                }
        }

        val pick = route as? IncomingRoute.Pick

        // Handle launch intents (VIEW/SEND/EDIT/OpenFolder/Shortcuts)
        LaunchedEffect(route, incoming) {
            when (val r = route) {
                IncomingRoute.None, is IncomingRoute.Pick -> Unit
                IncomingRoute.ShortcutCamera -> {
                    vm.consumeRoute()
                    nav.navigate("camera")
                }
                IncomingRoute.ShortcutFavorites -> {
                    vm.consumeRoute()
                    nav.navigate("collection/favorites")
                }
                IncomingRoute.ShortcutSearch -> {
                    vm.consumeRoute()
                    tab = FarTab.Search
                }
                is IncomingRoute.OpenFolder -> {
                    vm.consumeRoute()
                    tab = FarTab.Files
                }
                is IncomingRoute.Edit -> {
                    vm.consumeRoute()
                    nav.navigate("editor/${Uri.encode(r.uri.toString())}")
                }
                is IncomingRoute.ViewOrReceive -> {
                    val res = incoming ?: return@LaunchedEffect
                    if (res.accepted.isNotEmpty()) {
                        viewerList = res.accepted.mapIndexed { idx, item ->
                            val isVid = (item.sniffedMime ?: item.declaredMime).orEmpty().startsWith("video/")
                            ViewerItem(
                                id = -(idx + 100L),
                                uri = item.uri.toString(),
                                title = item.displayName ?: "Shared media",
                                subtitle = if (item.mimeMismatch) "File extension differs from contents" else null,
                                isVideo = isVid,
                                isFavorite = false,
                            )
                        }
                        viewerIndex = 0
                        if (r.shared && res.accepted.size == 1) {
                            val first = res.accepted.first()
                            toast = "Opened shared item. Tap Share or save a copy."
                        }
                        nav.navigate("viewer")
                    } else if (res.rejected.isNotEmpty()) {
                        toast = "Couldn't open item (${res.rejected.first().second.code})"
                    }
                    vm.consumeRoute()
                }
                is IncomingRoute.Capture -> Unit
            }
        }

        // Update gallery filter when Search tab or Favorites filter is active
        LaunchedEffect(tab, searchQuery, searchScope, dateRange, sizeFilter, favoritesFilter, favorites) {
            when (tab) {
                FarTab.Search -> {
                    val q = searchQuery.trim()
                    val matchedIds: Set<Long>? = if (q.isNotBlank()) {
                        val nameMatches = libraryRows.filter { it.name.contains(q, ignoreCase = true) }.map { it.id }
                        val tagMatches = db.tags().findMediaIdsByTagQuery(q)
                        val ocrMatches = if (s.ocrEnabled) db.ocr().searchIds(q) else emptyList()
                        (nameMatches + tagMatches + ocrMatches).toSet()
                    } else {
                        null
                    }
                    val favIntersection = if (searchScope == SearchScope.Favorites) favorites else null
                    val finalIds = when {
                        matchedIds != null && favIntersection != null -> matchedIds.intersect(favIntersection)
                        matchedIds != null -> matchedIds
                        else -> favIntersection
                    }
                    val now = System.currentTimeMillis()
                    val startOfYearMs = LocalDate.now().withDayOfYear(1)
                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val (minDateMs, maxDateMs) = when (dateRange) {
                        DateRangeFilter.Any -> null to null
                        DateRangeFilter.Last7Days -> (now - 7L * 86_400_000L) to null
                        DateRangeFilter.Last30Days -> (now - 30L * 86_400_000L) to null
                        DateRangeFilter.ThisYear -> startOfYearMs to null
                        DateRangeFilter.Older -> null to (startOfYearMs - 1L)
                    }
                    gvm.filter.value = MediaFilter(
                        kind = when (searchScope) {
                            SearchScope.Photos -> MediaKind.Photos
                            SearchScope.Videos -> MediaKind.Videos
                            else -> MediaKind.All
                        },
                        pathContains = if (searchScope == SearchScope.Screenshots) "Screenshots" else null,
                        minSizeBytes = sizeFilter.minBytes,
                        minDateMs = minDateMs,
                        maxDateMs = maxDateMs,
                        onlyIds = finalIds,
                        forceEmpty = (q.isNotBlank() && (finalIds == null || finalIds.isEmpty())) ||
                            (searchScope == SearchScope.Favorites && favorites.isEmpty()),
                    )
                }
                FarTab.Photos -> {
                    gvm.filter.value = MediaFilter(
                        kind = filter.kind,
                        onlyIds = if (favoritesFilter) favorites else null,
                        forceEmpty = favoritesFilter && favorites.isEmpty(),
                    )
                }
                else -> Unit
            }
        }

        // Compute live AlbumUi cards & MemoryUi cards from Room + libraryRows
        val rowById = remember(libraryRows) { libraryRows.associateBy { it.id } }
        val albumUis = remember(albums, albumRefs, rowById) {
            albums.map { a ->
                val mediaIds = albumRefs.filter { it.albumId == a.id }.map { it.mediaId }
                val coverRow = a.coverMediaId?.let { rowById[it] } ?: mediaIds.firstNotNullOfOrNull { rowById[it] }
                AlbumUi(
                    id = a.id,
                    name = a.name,
                    count = mediaIds.size,
                    coverUri = coverRow?.uri?.toString(),
                    pinned = a.pinned,
                    createdAt = a.createdAt,
                )
            }
        }
        val memories = remember(libraryRows, s.memoriesEnabled) {
            if (!s.memoriesEnabled || libraryRows.isEmpty()) emptyList()
            else {
                val onThisDay = MemoryRules.onThisDay(libraryRows.map { it.id to it.takenMs })
                val dayMemories = onThisDay.entries.sortedByDescending { it.key }.map { (year, ids) ->
                    MemoryUi(
                        id = "year_$year",
                        title = "On this day ($year)",
                        subtitle = "${ids.size} moments",
                        coverUri = ids.firstNotNullOfOrNull { rowById[it]?.uri?.toString() },
                        mediaIds = ids.toSet(),
                    )
                }
                if (dayMemories.isNotEmpty()) dayMemories
                else {
                    val recent = libraryRows.take(12)
                    if (recent.size >= 3) listOf(
                        MemoryUi(
                            id = "recent_highlights",
                            title = "Recent highlights",
                            subtitle = "${recent.size} latest photos & videos",
                            coverUri = recent.firstOrNull()?.uri?.toString(),
                            mediaIds = recent.map { it.id }.toSet(),
                        )
                    ) else emptyList()
                }
            }
        }

        SharedTransitionLayout {
            NavHost(nav, startDestination = if (s.onboardingDone || pick != null) "main" else "onboarding") {
                composable("onboarding") {
                    OnboardingScreen(
                        onFullAccess = {
                            permLauncher.launch(Permissions.mediaPermissions())
                            vm.update { it.copy(onboardingDone = true) }
                            nav.navigate("main") { popUpTo("onboarding") { inclusive = true } }
                        },
                        onSelectedOnly = {
                            if (android.os.Build.VERSION.SDK_INT >= 34) {
                                permLauncher.launch(Permissions.mediaPermissions())
                            } else {
                                selectedOnlyPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                            }
                            vm.update { it.copy(onboardingDone = true) }
                            nav.navigate("main") { popUpTo("onboarding") { inclusive = true } }
                        },
                        onSkip = {
                            vm.update { it.copy(onboardingDone = true) }
                            nav.navigate("main") { popUpTo("onboarding") { inclusive = true } }
                        },
                    )
                }

                composable("main") {
                    val semantic = LocalSemanticColors.current
                    val activeGlass = when (tab) {
                        FarTab.Photos -> photosGlass
                        FarTab.Albums -> albumsGlass
                        FarTab.Search -> searchGlass
                        FarTab.Files -> filesGlass
                    }
                    val hasActiveSearch = tab == FarTab.Search && (
                        searchQuery.isNotBlank() ||
                            searchScope != SearchScope.All ||
                            dateRange != DateRangeFilter.Any ||
                            sizeFilter != SizeFilter.Any
                        )
                    BackHandler(
                        enabled = selection.isNotEmpty() || hasActiveSearch || (tab == FarTab.Photos && favoritesFilter),
                    ) {
                        when {
                            selection.isNotEmpty() -> gvm.clearSelection()
                            hasActiveSearch -> {
                                searchQuery = ""
                                searchScope = SearchScope.All
                                dateRange = DateRangeFilter.Any
                                sizeFilter = SizeFilter.Any
                            }
                            tab == FarTab.Photos && favoritesFilter -> favoritesFilter = false
                        }
                    }

                    FarErrorBoundary(
                        faultCode = null,
                        onRecover = {
                            gvm.refreshAll()
                            items.refresh()
                        },
                    ) {
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            val wide = maxWidth >= 600.dp
                            Row(Modifier.fillMaxSize()) {
                                if (wide && selection.isEmpty()) {
                                    GlassNavRail(
                                        glass = activeGlass,
                                        selected = tab,
                                        onSelect = { tab = it },
                                        onCamera = { nav.navigate("camera") },
                                    )
                                }
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    AnimatedContent(tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tabs") { t ->
                                        when (t) {
                                            FarTab.Photos -> GalleryScreen(
                                                items = items,
                                                glass = photosGlass,
                                                zoom = zoom,
                                                onZoomChange = { gvm.zoom.value = it },
                                                selection = selection,
                                                onOpen = { m ->
                                                    if (pick != null) gvm.toggleSelect(m.id) else {
                                                        val snap = items.itemSnapshotList.items.filterIsInstance<GalleryItem.Media>()
                                                        viewerList = snap.map {
                                                            ViewerItem(it.id, it.uri, it.description, null, it.isVideo, it.id in favorites)
                                                        }
                                                        viewerIndex = snap.indexOfFirst { it.id == m.id }.coerceAtLeast(0)
                                                        nav.navigate("viewer")
                                                    }
                                                },
                                                onToggleSelect = { gvm.toggleSelect(it.id) },
                                                onRetry = { items.retry() },
                                                onRequestAccess = {
                                                    if (access == Access.None) permLauncher.launch(Permissions.mediaPermissions())
                                                    else {
                                                        runCatching {
                                                            activity.startActivity(
                                                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                                                            )
                                                        }.onFailure { err ->
                                                            LocalDiagnostics.recordEvent(ctx, "FG-SETTINGS", "app_details", err.javaClass.simpleName)
                                                            toast = "Open Android Settings to manage photo access."
                                                        }
                                                    }
                                                },
                                                initialColumns = s.gridColumns,
                                                access = access,
                                                favoriteIds = favorites,
                                                activeKind = filter.kind,
                                                favoritesOnly = favoritesFilter,
                                                activeSort = gallerySort,
                                                ascending = !galleryDesc,
                                                onKindChange = { k -> gvm.setKind(k) },
                                                onToggleFavoritesOnly = { favoritesFilter = !favoritesFilter },
                                                onSortChange = { gvm.setSort(it) },
                                                onToggleSortDirection = { gvm.toggleSortDirection() },
                                                onCycleTheme = {
                                                    val next = s.theme.next()
                                                    vm.update { it.copy(theme = next) }
                                                    ThemeTileService.syncTile(ctx)
                                                    toast = ctx.getString(R.string.theme_switched, ctx.getString(next.labelRes()))
                                                },
                                                onPullRefresh = {
                                                    gvm.refreshAll()
                                                    items.refresh()
                                                    LibraryStatsBridge.syncFromRows(ctx, libraryRows, favorites.size)
                                                },
                                                onOpenSettings = { nav.navigate("settings") },
                                                onClearSelection = { gvm.clearSelection() },
                                            )

                                            FarTab.Albums -> AlbumsScreen(
                                                albums = albumUis,
                                                memories = memories,
                                                albumSort = s.albumSort,
                                                onAlbumSortChange = { sort -> vm.update { it.copy(albumSort = sort) } },
                                                glass = albumsGlass,
                                                onOpenAlbum = { nav.navigate("album/${it.id}") },
                                                onOpenMemory = { nav.navigate("collection/memory_${it.id}") },
                                                onOpenCollection = { col ->
                                                    when (col) {
                                                        SystemCollection.Favorites -> nav.navigate("collection/favorites")
                                                        SystemCollection.Videos -> nav.navigate("collection/videos")
                                                        SystemCollection.Screenshots -> nav.navigate("collection/screenshots")
                                                        SystemCollection.Camera -> nav.navigate("collection/camera")
                                                        SystemCollection.StorageCleanup -> nav.navigate("storage")
                                                        SystemCollection.Duplicates -> nav.navigate("duplicates")
                                                        SystemCollection.RecentlyDeleted -> nav.navigate("collection/trash")
                                                        SystemCollection.ExcludedFolders -> nav.navigate("excluded")
                                                    }
                                                },
                                                onCreateAlbum = { name -> gvm.createAlbum(name) },
                                                onRenameAlbum = { album, name -> gvm.renameAlbum(album.id, name) },
                                                onTogglePin = { album -> gvm.togglePinAlbum(album.id, album.pinned) },
                                                onPickCover = { album -> coverPickerAlbum = album },
                                                onDeleteAlbum = { album -> gvm.deleteAlbum(album.id) },
                                            )

                                            FarTab.Search -> SearchScreen(
                                                query = searchQuery,
                                                onQuery = { searchQuery = it },
                                                onSubmitQuery = { q -> gvm.addSearchHistory(q) },
                                                scope = searchScope,
                                                onScope = { searchScope = it },
                                                dateRange = dateRange,
                                                onDateRange = { dateRange = it },
                                                sizeFilter = sizeFilter,
                                                onSizeFilter = { sizeFilter = it },
                                                recent = recentSearches,
                                                onClearRecent = { gvm.clearSearchHistory() },
                                                index = when {
                                                    !s.ocrEnabled -> IndexStatus.Disabled
                                                    ocrPercent != null -> IndexStatus.Indexing(ocrPercent ?: 0)
                                                    access == Access.Selected -> IndexStatus.LimitedAccess
                                                    else -> IndexStatus.Ready
                                                },
                                                ocrCount = ocrCount,
                                                onRunOcrScan = {
                                                    scope.launch {
                                                        ocrPercent = 0
                                                        val found = ocrIndexer.indexBatch(libraryRows, maxItems = 40) { done, total ->
                                                            ocrPercent = ((done * 100f) / total.coerceAtLeast(1)).toInt()
                                                        }
                                                        ocrPercent = null
                                                        toast = if (found > 0) "Indexed text in $found photo(s)" else "OCR scan finished"
                                                    }
                                                },
                                                resultCount = if (
                                                    searchQuery.isBlank() &&
                                                    searchScope == SearchScope.All &&
                                                    dateRange == DateRangeFilter.Any &&
                                                    sizeFilter == SizeFilter.Any
                                                ) null else items.itemCount,
                                                glass = searchGlass,
                                                results = {
                                                    GalleryScreen(
                                                        items = items,
                                                        glass = searchGlass,
                                                        zoom = zoom,
                                                        onZoomChange = { gvm.zoom.value = it },
                                                        selection = selection,
                                                        onOpen = { m ->
                                                            val snap = items.itemSnapshotList.items.filterIsInstance<GalleryItem.Media>()
                                                            viewerList = snap.map {
                                                                ViewerItem(it.id, it.uri, it.description, null, it.isVideo, it.id in favorites)
                                                            }
                                                            viewerIndex = snap.indexOfFirst { it.id == m.id }.coerceAtLeast(0)
                                                            if (searchQuery.isNotBlank()) gvm.addSearchHistory(searchQuery)
                                                            nav.navigate("viewer")
                                                        },
                                                        onToggleSelect = { gvm.toggleSelect(it.id) },
                                                        onRetry = { items.retry() },
                                                        onRequestAccess = { permLauncher.launch(Permissions.mediaPermissions()) },
                                                        showTopBar = false,
                                                        favoriteIds = favorites,
                                                    )
                                                },
                                            )

                                            FarTab.Files -> FilesTab(filesGlass)
                                        }
                                    }

                                    if (!wide) {
                                        Box(Modifier.align(Alignment.BottomCenter)) {
                                            GlassBottomBar(
                                                glass = activeGlass,
                                                selected = tab,
                                                onSelect = { tab = it },
                                                onCamera = { nav.navigate("camera") },
                                                visible = selection.isEmpty(),
                                            )
                                        }
                                    }

                                    if (selection.isNotEmpty()) {
                                        Row(
                                            Modifier
                                                .align(Alignment.BottomCenter)
                                                .navigationBarsPadding()
                                                .padding(horizontal = FarSpacing.screenPadding, vertical = 12.dp)
                                                .glass(activeGlass, RoundedCornerShape(28.dp), GlassKind.Bar)
                                                .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(28.dp))
                                                .horizontalScroll(rememberScrollState())
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                "${selection.size}",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(horizontal = 8.dp),
                                            )
                                        if (pick != null) {
                                            TextButton(
                                                onClick = {
                                                    val uris = items.itemSnapshotList.items
                                                        .filterIsInstance<GalleryItem.Media>()
                                                        .filter { it.id in selection }
                                                        .mapNotNull { safeParseMediaUri(it.uri) }
                                                    IntentResults.pick(activity, uris.take(pick.maxItems))
                                                },
                                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                                            ) { Text("Return ${selection.size}") }
                                        } else {
                                            TextButton(
                                                onClick = {
                                                    val allIds = items.itemSnapshotList.items
                                                        .filterIsInstance<GalleryItem.Media>()
                                                        .map { it.id }
                                                        .toSet()
                                                    gvm.setSelected(allIds)
                                                },
                                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                                            ) { Text(stringResource(R.string.batch_all)) }
                                            val allFav = selection.all { it in favorites }
                                            TextButton(
                                                onClick = {
                                                    gvm.setFavorites(selection, !allFav)
                                                    toast = if (!allFav) "Added ${selection.size} to Favorites" else "Removed from Favorites"
                                                },
                                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                                            ) { Text(stringResource(if (allFav) R.string.batch_unfav else R.string.batch_fav)) }
                                            TextButton(
                                                onClick = { albumPickerTargets = selection.toList() },
                                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                                            ) {
                                                Text(stringResource(R.string.batch_album))
                                            }
                                            if (selection.size == 2) {
                                                val pair = selection.toList()
                                                if (pair.all { rowById[it] != null }) {
                                                    TextButton(
                                                        onClick = {
                                                            gvm.clearSelection()
                                                            nav.navigate("compare/${pair[0]}/${pair[1]}")
                                                        },
                                                        modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("batch_compare_button"),
                                                    ) { Text(stringResource(R.string.batch_compare)) }
                                                }
                                            }
                                            val selectedRows = selection.mapNotNull { rowById[it] }
                                            if (selectedRows.any { !it.isVideo }) {
                                                TextButton(
                                                    onClick = { batchResizeTargets = selectedRows },
                                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("batch_resize_button"),
                                                ) { Text(stringResource(R.string.batch_resize)) }
                                            }
                                            if (selectedRows.isNotEmpty()) {
                                                TextButton(
                                                    onClick = { batchRenameTargets = selectedRows },
                                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("batch_rename_button"),
                                                ) { Text(stringResource(R.string.batch_rename)) }
                                            }
                                            TextButton(
                                                onClick = {
                                                    val rawUris = items.itemSnapshotList.items
                                                        .filterIsInstance<GalleryItem.Media>()
                                                        .filter { it.id in selection }
                                                        .mapNotNull { safeParseMediaUri(it.uri) }
                                                    if (rawUris.isNotEmpty()) {
                                                        scope.launch {
                                                            val shareUris = if (s.stripLocationOnShare) {
                                                                ArrayList(ExifScrubber.createStrippedShareCopies(ctx, rawUris))
                                                            } else {
                                                                ArrayList(rawUris)
                                                            }
                                                            runCatching {
                                                                activity.startActivity(
                                                                    Intent.createChooser(
                                                                        Intent(Intent.ACTION_SEND_MULTIPLE)
                                                                            .setType("*/*")
                                                                            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, shareUris)
                                                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                                                        null,
                                                                    )
                                                                )
                                                            }.onFailure { err ->
                                                                LocalDiagnostics.recordEvent(ctx, "FG-SHARE", "batch_share", err.javaClass.simpleName)
                                                                toast = "No app found to share"
                                                            }
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                                            ) { Text(stringResource(R.string.action_share)) }
                                            TextButton(
                                                onClick = {
                                                    val uris = items.itemSnapshotList.items
                                                        .filterIsInstance<GalleryItem.Media>()
                                                        .filter { it.id in selection }
                                                        .mapNotNull { safeParseMediaUri(it.uri) }
                                                    trash(uris)
                                                },
                                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                                            ) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
                                        }
                                    }
                                }

                                val bottomOverlayClearance = when {
                                    selection.isNotEmpty() -> 80.dp
                                    !wide -> 84.dp
                                    else -> 20.dp
                                }
                                Column(
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .navigationBarsPadding()
                                        .padding(bottom = bottomOverlayClearance),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    batchProgress?.let { op ->
                                        OperationProgressSheet(
                                            glass = activeGlass,
                                            op = op,
                                            onCancel = {
                                                batchJob?.cancel()
                                                batchProgress = null
                                                toast = "Batch operation cancelled"
                                            },
                                        )
                                    }

                                    if (interruptedOps.isNotEmpty()) {
                                        val firstOp = interruptedOps.first()
                                        RecoveryBanner(
                                            glass = activeGlass,
                                            icon = Icons.Outlined.WarningAmber,
                                            title = "${firstOp.type} stopped unexpectedly",
                                            body = "${firstOp.done} of ${firstOp.total} completed before interruption.",
                                            actionLabel = stringResource(R.string.action_dismiss),
                                            onAction = { scope.launch { db.operations().dismiss(firstOp.id) } },
                                            modifier = Modifier.padding(horizontal = FarSpacing.screenPadding),
                                        )
                                    }

                                    toast?.let { t ->
                                        GlassToast(activeGlass, t, stringResource(R.string.action_ok), onAction = { toast = null })
                                    }
                                }
                            }
                        }
                    }
                    }
                }

                composable("album/{albumId}") { entry ->
                    val albumId = entry.arguments?.getString("albumId")?.toLongOrNull() ?: 0L
                    val album = albums.firstOrNull { it.id == albumId }
                    val ids = remember(albumRefs, albumId) {
                        albumRefs.filter { it.albumId == albumId }.map { it.mediaId }.toSet()
                    }
                    FarErrorBoundary(
                        faultCode = if (albumId <= 0L) "FG-INP-ALBUM" else null,
                        onRecover = { gvm.refreshAll() },
                        onBack = { nav.popBackStack() },
                    ) {
                        val subGvm: GalleryViewModel = viewModel(
                            key = "album_$albumId",
                            factory = GalleryViewModel.factory(
                                ctx.contentResolver,
                                db,
                                MediaFilter(onlyIds = ids, forceEmpty = ids.isEmpty()),
                            ),
                        )
                        LaunchedEffect(ids) {
                            subGvm.filter.value = MediaFilter(onlyIds = ids, forceEmpty = ids.isEmpty())
                        }
                        val subItems = subGvm.items.collectAsLazyPagingItems()
                        val subSel by subGvm.selection.collectAsState()
                        val subZoom by subGvm.zoom.collectAsState()
                        val subSort by subGvm.sort.collectAsState()
                        val subDesc by subGvm.descending.collectAsState()

                        BackHandler { nav.popBackStack() }
                        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            SubCollectionHeader(
                                title = album?.name ?: stringResource(R.string.albums_title),
                                subtitle = "${ids.size} items",
                                onBack = { nav.popBackStack() },
                                secondaryActionLabel = if (ids.isNotEmpty() && album != null) "Cover" else null,
                                onSecondaryAction = if (ids.isNotEmpty() && album != null) {
                                    {
                                        coverPickerAlbum = AlbumUi(album.id, album.name, ids.size, null, album.pinned, album.createdAt)
                                    }
                                } else null,
                            )
                            GalleryScreen(
                                items = subItems,
                                glass = subGlass,
                                zoom = subZoom,
                                onZoomChange = { subGvm.zoom.value = it },
                                selection = subSel,
                                onOpen = { m ->
                                    val snap = subItems.itemSnapshotList.items.filterIsInstance<GalleryItem.Media>()
                                    viewerList = snap.map { ViewerItem(it.id, it.uri, it.description, null, it.isVideo, it.id in favorites) }
                                    viewerIndex = snap.indexOfFirst { it.id == m.id }.coerceAtLeast(0)
                                    nav.navigate("viewer")
                                },
                                onToggleSelect = { subGvm.toggleSelect(it.id) },
                                onRetry = { subItems.retry() },
                                onRequestAccess = { permLauncher.launch(Permissions.mediaPermissions()) },
                                showTopBar = false,
                                favoriteIds = favorites,
                                activeSort = subSort,
                                ascending = !subDesc,
                                onSortChange = { subGvm.setSort(it) },
                                onToggleSortDirection = { subGvm.toggleSortDirection() },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                composable("collection/{kind}") { entry ->
                    val kind = entry.arguments?.getString("kind").orEmpty()
                    FarErrorBoundary(
                        faultCode = if (kind.isBlank()) "FG-INP-COL" else null,
                        onRecover = {
                            gvm.refreshAll()
                            trashedRefreshNonce++
                        },
                        onBack = { nav.popBackStack() },
                    ) {
                        if (kind == "trash") {
                            var trashedRows by remember { mutableStateOf<List<MediaRow>>(emptyList()) }
                            var trashLoading by remember { mutableStateOf(true) }
                            LaunchedEffect(trashedRefreshNonce) {
                                trashLoading = true
                                val res = repo.queryAll(MediaFilter(trashedOnly = true), SortKey.Modified, descending = true)
                                trashedRows = (res as? FarResult.Ok)?.value ?: emptyList()
                                trashLoading = false
                            }
                            TrashManagerScreen(
                                rows = trashedRows,
                                loading = trashLoading,
                                onOpen = { row ->
                                    viewerList = listOf(
                                        ViewerItem(row.id, row.uri.toString(), row.name, null, row.isVideo, row.id in favorites)
                                    )
                                    viewerIndex = 0
                                    nav.navigate("viewer")
                                },
                                onRestore = { uris ->
                                    runCatching {
                                        restoreLauncher.launch(IntentSenderRequest.Builder(repo.trashRequest(uris, false)).build())
                                    }.onFailure {
                                        LocalDiagnostics.recordEvent(ctx, "FG-RESTORE", "trash_restore", it.javaClass.simpleName)
                                        toast = "Couldn't open restore confirmation."
                                    }
                                },
                                onPermanentDelete = { uris ->
                                    runCatching {
                                        permanentDeleteLauncher.launch(IntentSenderRequest.Builder(repo.deleteRequest(uris)).build())
                                    }.onFailure {
                                        LocalDiagnostics.recordEvent(ctx, "FG-DEL-PERM", "trash_empty", it.javaClass.simpleName)
                                        toast = "Couldn't open delete confirmation."
                                    }
                                },
                                onBack = { nav.popBackStack() },
                            )
                        } else {
                            val mem = if (kind.startsWith("memory_")) memories.firstOrNull { it.id == kind.removePrefix("memory_") } else null
                            val colFilter = when (kind) {
                                "favorites" -> MediaFilter(onlyIds = favorites, forceEmpty = favorites.isEmpty())
                                "videos" -> MediaFilter(kind = MediaKind.Videos)
                                "screenshots" -> MediaFilter(pathContains = "Screenshots")
                                "camera" -> MediaFilter(pathContains = "DCIM/Camera")
                                else -> if (mem != null) MediaFilter(onlyIds = mem.mediaIds, forceEmpty = mem.mediaIds.isEmpty()) else MediaFilter()
                            }
                            val subGvm: GalleryViewModel = viewModel(
                                key = "col_$kind",
                                factory = GalleryViewModel.factory(ctx.contentResolver, db, colFilter),
                            )
                            LaunchedEffect(colFilter) { subGvm.filter.value = colFilter }
                            val subItems = subGvm.items.collectAsLazyPagingItems()
                            val subSel by subGvm.selection.collectAsState()
                            val subZoom by subGvm.zoom.collectAsState()
                            val subSort by subGvm.sort.collectAsState()
                            val subDesc by subGvm.descending.collectAsState()
                            val title = when (kind) {
                                "favorites" -> stringResource(R.string.collection_favorites)
                                "videos" -> stringResource(R.string.collection_videos)
                                "screenshots" -> stringResource(R.string.collection_screenshots)
                                "camera" -> stringResource(R.string.collection_camera)
                                else -> mem?.title ?: stringResource(R.string.collections_title)
                            }

                            BackHandler { nav.popBackStack() }
                            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                                SubCollectionHeader(
                                    title = title,
                                    subtitle = mem?.subtitle,
                                    onBack = { nav.popBackStack() },
                                )
                                GalleryScreen(
                                    items = subItems,
                                    glass = subGlass,
                                    zoom = subZoom,
                                    onZoomChange = { subGvm.zoom.value = it },
                                    selection = subSel,
                                    onOpen = { m ->
                                        val snap = subItems.itemSnapshotList.items.filterIsInstance<GalleryItem.Media>()
                                        viewerList = snap.map { ViewerItem(it.id, it.uri, it.description, null, it.isVideo, it.id in favorites) }
                                        viewerIndex = snap.indexOfFirst { it.id == m.id }.coerceAtLeast(0)
                                        nav.navigate("viewer")
                                    },
                                    onToggleSelect = { subGvm.toggleSelect(it.id) },
                                    onRetry = { subItems.retry() },
                                    onRequestAccess = { permLauncher.launch(Permissions.mediaPermissions()) },
                                    showTopBar = false,
                                    favoriteIds = favorites,
                                    activeSort = subSort,
                                    ascending = !subDesc,
                                    onSortChange = { subGvm.setSort(it) },
                                    onToggleSortDirection = { subGvm.toggleSortDirection() },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                composable("compare/{leftId}/{rightId}") { entry ->
                    val leftId = entry.arguments?.getString("leftId")?.toLongOrNull() ?: 0L
                    val rightId = entry.arguments?.getString("rightId")?.toLongOrNull() ?: 0L
                    FarErrorBoundary(
                        faultCode = if (leftId <= 0L || rightId <= 0L) "FG-INP-CMP" else null,
                        onRecover = { gvm.refreshAll() },
                        onBack = { nav.popBackStack() },
                    ) {
                        CompareScreen(
                            left = rowById[leftId],
                            right = rowById[rightId],
                            glass = subGlass,
                            onTrash = { row ->
                                trash(listOf(row.uri))
                                nav.popBackStack()
                            },
                            onBack = { nav.popBackStack() },
                        )
                    }
                }

                composable("duplicates") {
                    BackHandler { nav.popBackStack() }
                    FarErrorBoundary(
                        faultCode = null,
                        onRecover = { gvm.refreshAll() },
                        onBack = { nav.popBackStack() },
                    ) {
                        var scanning by remember { mutableStateOf<DupScan.Progress?>(DupScan.Progress(0, 0)) }
                        var groups by remember { mutableStateOf<List<DupGroup>>(emptyList()) }
                        val inAlbumIds = remember(albumRefs) { albumRefs.map { it.mediaId }.toSet() }
                        LaunchedEffect(libraryRows, favorites, inAlbumIds) {
                            DuplicateFinder(ctx.contentResolver).scan(libraryRows, favorites, inAlbumIds).collect { ev ->
                                when (ev) {
                                    is DupScan.Progress -> scanning = ev
                                    is DupScan.Done -> {
                                        scanning = null
                                        groups = ev.groups
                                    }
                                }
                            }
                        }
                        DuplicatesScreen(
                            scanning = scanning,
                            groups = groups,
                            onBack = { nav.popBackStack() },
                            onTrash = { trash(it) },
                        )
                    }
                }

                composable("excluded") {
                    FarErrorBoundary(
                        faultCode = null,
                        onRecover = { gvm.refreshAll() },
                        onBack = { nav.popBackStack() },
                    ) {
                        val folders = remember(libraryRows) {
                            libraryRows.mapNotNull { it.relativePath?.trimEnd('/') }.distinct().sorted()
                        }
                        ExcludedFoldersScreen(
                            allFolders = folders,
                            excluded = excludedFolders.toSet(),
                            onToggleExclude = { path, ex ->
                                if (ex) gvm.excludeFolder(path) else gvm.includeFolder(path)
                            },
                            onBack = { nav.popBackStack() },
                        )
                    }
                }

                composable("viewer") {
                    FarErrorBoundary(
                        faultCode = null,
                        onRecover = { gvm.refreshAll() },
                        onBack = { nav.popBackStack() },
                    ) {
                        val liveList = remember(viewerList, favorites) {
                            viewerList.map { it.copy(isFavorite = it.id in favorites) }
                        }
                        ViewerScreen(
                            items = liveList,
                            startIndex = viewerIndex,
                            glass = subGlass,
                            onClose = { nav.popBackStack() },
                            onFavorite = { item -> if (item.id > 0) gvm.toggleFavorite(item.id) },
                            onShare = { item ->
                                val raw = safeParseMediaUri(item.uri) ?: return@ViewerScreen
                                scope.launch {
                                    val outUri = if (s.stripLocationOnShare && !item.isVideo) {
                                        ExifScrubber.createStrippedShareCopies(ctx, listOf(raw)).firstOrNull() ?: raw
                                    } else {
                                        raw
                                    }
                                    runCatching {
                                        activity.startActivity(
                                            Intent.createChooser(
                                                Intent(Intent.ACTION_SEND)
                                                    .setType(if (item.isVideo) "video/*" else "image/*")
                                                    .putExtra(Intent.EXTRA_STREAM, outUri)
                                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                                null,
                                            )
                                        )
                                    }.onFailure { err ->
                                        LocalDiagnostics.recordEvent(ctx, "FG-SHARE", "viewer_share", err.javaClass.simpleName)
                                        toast = "No app found to share"
                                    }
                                }
                            },
                            onEdit = { nav.navigate("editor/${Uri.encode(it.uri)}?mediaId=${it.id}") },
                            onDelete = { safeParseMediaUri(it.uri)?.let { uri -> trash(listOf(uri)) } },
                            onAddToAlbum = { item -> if (item.id > 0) albumPickerTargets = listOf(item.id) },
                            onCompare = { item -> comparePickerSourceId = item.id },
                            onOpenWith = { item ->
                                val uri = safeParseMediaUri(item.uri) ?: return@ViewerScreen
                                runCatching {
                                    activity.startActivity(
                                        Intent.createChooser(
                                            Intent(Intent.ACTION_VIEW)
                                                .setDataAndType(uri, if (item.isVideo) "video/*" else "image/*")
                                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                            null,
                                        )
                                    )
                                }.onFailure { err ->
                                    LocalDiagnostics.recordEvent(ctx, "FG-OPEN", "viewer_open_with", err.javaClass.simpleName)
                                    toast = "No app found to open this item"
                                }
                            },
                            detailsContent = { item ->
                                val tags by remember(db, item.id) {
                                    db.tags().tagsForMedia(item.id).catch { emit(emptyList()) }
                                }.collectAsState(initial = emptyList())
                                var ocrText by remember(item.id) { mutableStateOf<String?>(null) }
                                var ocrBusy by remember(item.id) { mutableStateOf(false) }
                                LaunchedEffect(item.id) {
                                    if (item.id > 0) ocrText = db.ocr().get(item.id)?.extractedText
                                }
                                MediaDetailsSheetContent(
                                    item = item,
                                    row = rowById[item.id],
                                    tags = tags,
                                    ocrText = ocrText,
                                    ocrLoading = ocrBusy,
                                    onAddTag = { tagName -> if (item.id > 0) gvm.addTag(item.id, tagName) },
                                    onRemoveTag = { tagId -> if (item.id > 0) gvm.removeTag(item.id, tagId) },
                                    onScanOcr = {
                                        val uri = safeParseMediaUri(item.uri) ?: return@MediaDetailsSheetContent
                                        scope.launch {
                                            ocrBusy = true
                                            val res = ocrIndexer.extractAndIndex(item.id, uri)
                                            ocrText = (res as? FarResult.Ok)?.value ?: ""
                                            ocrBusy = false
                                        }
                                    },
                                    onShareClean = {
                                        val uri = safeParseMediaUri(item.uri) ?: return@MediaDetailsSheetContent
                                        scope.launch {
                                            val clean = ExifScrubber.createStrippedShareCopies(ctx, listOf(uri)).firstOrNull() ?: uri
                                            runCatching {
                                                activity.startActivity(
                                                    Intent.createChooser(
                                                        Intent(Intent.ACTION_SEND)
                                                            .setType("image/*")
                                                            .putExtra(Intent.EXTRA_STREAM, clean)
                                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                                        null,
                                                    )
                                                )
                                            }.onFailure { err ->
                                                LocalDiagnostics.recordEvent(ctx, "FG-SHARE", "viewer_share_clean", err.javaClass.simpleName)
                                                toast = "No app found to share"
                                            }
                                        }
                                    },
                                    onSaveCleanCopy = {
                                        val uri = safeParseMediaUri(item.uri) ?: return@MediaDetailsSheetContent
                                        scope.launch {
                                            when (ExifScrubber.saveCleanCopyToGallery(ctx, uri, item.title)) {
                                                is FarResult.Ok -> {
                                                    toast = "Saved clean copy without GPS/EXIF"
                                                    gvm.refreshAll()
                                                    items.refresh()
                                                }
                                                is FarResult.Err -> toast = "Couldn't save clean copy"
                                            }
                                        }
                                    },
                                )
                            },
                            videoContent = { item ->
                                VideoPlayer(
                                    uri = item.uri,
                                    glass = subGlass,
                                    resumeMs = null,
                                    onPosition = {},
                                    onFrameGrab = { ms ->
                                        val uri = safeParseMediaUri(item.uri) ?: return@VideoPlayer
                                        scope.launch {
                                            when (FrameGrabber.grab(ctx, uri, ms)) {
                                                is FarResult.Ok -> toast = "Saved frame to Pictures/Far Gallery"
                                                is FarResult.Err -> toast = "Couldn't capture frame"
                                            }
                                        }
                                    },
                                    onTrim = { dur -> safeParseMediaUri(item.uri)?.let { trimTarget = it to dur } },
                                    onOpenWith = {
                                        val uri = safeParseMediaUri(item.uri) ?: return@VideoPlayer
                                        runCatching {
                                            activity.startActivity(
                                                Intent.createChooser(
                                                    Intent(Intent.ACTION_VIEW)
                                                        .setDataAndType(uri, "video/*")
                                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                                    null,
                                                )
                                            )
                                        }.onFailure { err ->
                                            LocalDiagnostics.recordEvent(ctx, "FG-OPEN", "video_open_with", err.javaClass.simpleName)
                                            toast = "No app found to open this video"
                                        }
                                    },
                                )
                            },
                        )
                    }
                }

                composable("camera") {
                    BackHandler { nav.popBackStack() }
                    FarErrorBoundary(
                        faultCode = null,
                        onRecover = { nav.popBackStack() },
                        onBack = { nav.popBackStack() },
                    ) {
                        val cvm: CameraViewModel = viewModel()
                        LaunchedEffect(s.cameraGrid) { cvm.setGridInitial(s.cameraGrid) }
                        CameraHost(
                            vm = cvm,
                            onClose = { nav.popBackStack() },
                            onOpenLast = { capturedUri ->
                                viewerList = listOf(
                                    ViewerItem(
                                        id = -1L,
                                        uri = capturedUri.toString(),
                                        title = "Recent capture",
                                        subtitle = null,
                                        isVideo = false,
                                        isFavorite = false,
                                    )
                                )
                                viewerIndex = 0
                                nav.navigate("viewer")
                            },
                        )
                    }
                }

                composable("editor/{uri}?mediaId={mediaId}") { entry ->
                    val rawArg = entry.arguments?.getString("uri").orEmpty()
                    val parsedUri = remember(rawArg) { safeParseMediaUri(Uri.decode(rawArg)) }
                    val mediaId = entry.arguments?.getString("mediaId")?.toLongOrNull() ?: 0L
                    FarErrorBoundary(
                        faultCode = if (rawArg.isBlank()) "FG-INP-EDIT" else null,
                        onRecover = { gvm.refreshAll() },
                        onBack = { nav.popBackStack() },
                    ) {
                        var bmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                        var err by remember { mutableStateOf<String?>(null) }
                        var saving by remember { mutableStateOf(false) }
                        var initialRecipe by remember(mediaId) { mutableStateOf<EditState?>(null) }
                        var recipeReady by remember(parsedUri, mediaId) { mutableStateOf(mediaId <= 0L) }
                        var editorRetryNonce by remember { mutableIntStateOf(0) }

                        LaunchedEffect(parsedUri, mediaId, editorRetryNonce) {
                            err = null
                            if (parsedUri == null) {
                                LocalDiagnostics.recordEvent(ctx, "FG-INP", "editor_route", "invalid uri")
                                err = "This image link is invalid. The original is untouched."
                                return@LaunchedEffect
                            }
                            if (mediaId > 0L) {
                                initialRecipe = EditState.fromJson(db.editRecipes().latestForMedia(mediaId)?.operationsJson)
                                recipeReady = true
                            }
                            when (val loaded = EditorEngine.load(ctx, parsedUri, 2048)) {
                                is FarResult.Ok -> bmp = loaded.value
                                is FarResult.Err -> {
                                    LocalDiagnostics.recordEvent(ctx, loaded.error.code, "editor_load", loaded.error.userTitle())
                                    err = "This image can't be edited here. The original is untouched."
                                }
                            }
                        }

                        EditorScreen(
                            source = if (recipeReady) bmp else null,
                            loading = (!recipeReady || bmp == null) && err == null,
                            errorText = err,
                            glass = subGlass,
                            saving = saving,
                            initialState = initialRecipe,
                            stripExifDefault = s.stripLocationOnShare,
                            onRetry = { err = null; bmp = null; editorRetryNonce++ },
                            onCancel = { nav.popBackStack() },
                            onSave = { st, stripExif ->
                                val targetUri = parsedUri ?: return@EditorScreen
                                val b = bmp ?: return@EditorScreen
                                saving = true
                                scope.launch {
                                    when (val res = EditorEngine.saveCopy(ctx, targetUri, b, st, 94, stripExif)) {
                                        is FarResult.Ok -> {
                                            if (mediaId > 0) {
                                                db.editRecipes().upsert(
                                                    EditRecipe(
                                                        mediaId = mediaId,
                                                        sourceSize = rowById[mediaId]?.sizeBytes ?: 0L,
                                                        operationsJson = st.toJson(),
                                                        outputUri = res.value.toString(),
                                                        updatedAt = System.currentTimeMillis(),
                                                    )
                                                )
                                            }
                                            toast = if (stripExif) "Saved clean copy" else "Saved a copy"
                                            gvm.refreshAll()
                                            items.refresh()
                                            nav.popBackStack()
                                        }
                                        is FarResult.Err -> {
                                            saving = false
                                            LocalDiagnostics.recordEvent(ctx, res.error.code, "editor_save", res.error.userTitle())
                                            toast = "Couldn't save (${res.error.code}). Original is untouched."
                                        }
                                    }
                                }
                            },
                        )
                    }
                }

                composable("storage") {
                    FarErrorBoundary(
                        faultCode = null,
                        onRecover = { gvm.refreshAll() },
                        onBack = { nav.popBackStack() },
                    ) {
                        StorageAnalyzerScreen(
                            rows = libraryRows,
                            onBack = { nav.popBackStack() },
                            onPreview = { row ->
                                viewerList = listOf(
                                    ViewerItem(
                                        id = row.id,
                                        uri = row.uri.toString(),
                                        title = row.name,
                                        subtitle = null,
                                        isVideo = row.isVideo,
                                        isFavorite = row.id in favorites,
                                    )
                                )
                                viewerIndex = 0
                                nav.navigate("viewer")
                            },
                            onTrash = { trash(it) },
                        )
                    }
                }

                composable("settings") {
                    FarErrorBoundary(
                        faultCode = null,
                        onRecover = { nav.popBackStack() },
                        onBack = { nav.popBackStack() },
                    ) {
                        SettingsScreen(
                            s = s,
                            ocrCount = ocrCount,
                            safeMode = safeMode,
                            onLeaveSafeMode = { vm.leaveSafeMode() },
                            onChange = { t -> vm.update(t) },
                            onOpenSystemDefaults = {
                                runCatching { activity.startActivity(DefaultApps.systemDefaults()) }
                                    .onFailure { toast = "Open Android Settings -> Apps -> Default apps" }
                            },
                            onFixDefault = { _, st ->
                                val intent = when (st) {
                                    is DefaultStatus.Other -> DefaultApps.appDetails(st.packageName)
                                    else -> DefaultApps.systemDefaults()
                                }
                                runCatching { activity.startActivity(intent) }
                                    .onFailure { toast = "Open Android Settings -> Apps to set defaults" }
                            },
                            onClearCache = {
                                ctx.cacheDir.deleteRecursively()
                                toast = "Thumbnail cache cleared"
                            },
                            onDeleteOcrIndex = {
                                scope.launch {
                                    db.ocr().clearAll()
                                    toast = "Text index deleted"
                                }
                            },
                            onManageAccess = { permLauncher.launch(Permissions.mediaPermissions()) },
                            onOpenStorage = { nav.navigate("storage") },
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
            }
        }

        albumPickerTargets?.let { targetIds ->
            AlbumPickerDialog(
                albums = albums,
                onPickAlbum = { a ->
                    gvm.addMediaToAlbum(a.id, targetIds)
                    gvm.clearSelection()
                    albumPickerTargets = null
                    toast = "Added ${targetIds.size} item(s) to ${a.name}"
                },
                onCreateAlbum = { name -> gvm.createAlbum(name) },
                onDismiss = { albumPickerTargets = null },
            )
        }

        coverPickerAlbum?.let { album ->
            val candidates = remember(albumRefs, rowById, album.id) {
                albumRefs.filter { it.albumId == album.id }.mapNotNull { rowById[it.mediaId] }
            }
            AlbumCoverPickerDialog(
                albumName = album.name,
                candidates = candidates,
                onPickCover = { mediaId ->
                    gvm.setAlbumCover(album.id, mediaId)
                    toast = if (mediaId == null) "Reset cover to auto" else "Album cover updated"
                },
                onDismiss = { coverPickerAlbum = null },
            )
        }

        trimTarget?.let { (uri, durMs) ->
            ModalBottomSheet(onDismissRequest = { if (!trimBusy) trimTarget = null }) {
                TrimPanel(
                    durationMs = durMs,
                    busy = trimBusy,
                    progress = trimProgress,
                    onSave = { startMs, endMs ->
                        trimBusy = true
                        scope.launch {
                            when (val res = VideoTrimmer.trim(ctx, uri, startMs, endMs) { trimProgress = it }) {
                                is FarResult.Ok -> {
                                    toast = "Trimmed copy saved to Movies/Far Gallery"
                                    gvm.refreshAll()
                                    items.refresh()
                                }
                                is FarResult.Err -> {
                                    LocalDiagnostics.recordEvent(ctx, res.error.code, "video_trim", res.error.userTitle())
                                    toast = "Couldn't trim video (${res.error.code}). Original is untouched."
                                }
                            }
                            trimBusy = false
                            trimProgress = null
                            trimTarget = null
                        }
                    },
                    onCancel = { trimTarget = null },
                )
            }
        }

        batchResizeTargets?.let { targets ->
            BatchResizeDialog(
                count = targets.size,
                stripExifDefault = s.stripLocationOnShare,
                onConfirm = { config ->
                    batchResizeTargets = null
                    gvm.clearSelection()
                    batchJob?.cancel()
                    batchJob = scope.launch {
                        batchProgress = OperationUi("Resizing copies", null, 0, targets.size)
                        try {
                            val summary = BatchMediaOps.resizeBatch(ctx, targets, config) { done, total, name ->
                                batchProgress = OperationUi("Resizing copies", name, done, total)
                            }
                            toast = buildString {
                                append("Saved ${summary.succeeded} resized copy(ies)")
                                if (summary.skipped > 0) append(", ${summary.skipped} video(s) skipped")
                                if (summary.failed > 0) append(", ${summary.failed} failed")
                            }
                            gvm.refreshAll()
                            items.refresh()
                        } finally {
                            batchProgress = null
                        }
                    }
                },
                onDismiss = { batchResizeTargets = null },
            )
        }

        batchRenameTargets?.let { targets ->
            BatchRenameDialog(
                sampleNames = targets.map { it.name },
                onConfirm = { prefix, startNumber ->
                    batchRenameTargets = null
                    gvm.clearSelection()
                    batchJob?.cancel()
                    batchJob = scope.launch {
                        batchProgress = OperationUi("Renaming items", null, 0, targets.size)
                        try {
                            val summary = BatchMediaOps.renameBatch(ctx, targets, prefix, startNumber) { done, total, name ->
                                batchProgress = OperationUi("Renaming items", name, done, total)
                            }
                            toast = buildString {
                                append("Renamed ${summary.succeeded} item(s)")
                                if (summary.failed > 0) append(", ${summary.failed} blocked by Android")
                            }
                            gvm.refreshAll()
                            items.refresh()
                        } finally {
                            batchProgress = null
                        }
                    }
                },
                onDismiss = { batchRenameTargets = null },
            )
        }

        comparePickerSourceId?.let { srcId ->
            CompareSecondPickerDialog(
                excludeId = srcId,
                candidates = libraryRows,
                onPick = { second ->
                    comparePickerSourceId = null
                    nav.navigate("compare/$srcId/${second.id}")
                },
                onDismiss = { comparePickerSourceId = null },
            )
        }
    }
}
