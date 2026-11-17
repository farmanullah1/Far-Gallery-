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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.Access
import com.example.AppViewModel
import com.example.CameraHost
import com.example.Permissions
import com.example.R
import com.example.core.common.FarResult
import com.example.core.database.EditRecipe
import com.example.core.database.FarDatabase
import com.example.core.media.GalleryViewModel
import com.example.core.media.MediaFilter
import com.example.core.media.MediaKind
import com.example.core.media.MediaRepository
import com.example.core.media.OcrIndexer
import com.example.feature.albums.AlbumUi
import com.example.feature.albums.AlbumsScreen
import com.example.feature.albums.IndexStatus
import com.example.feature.albums.MemoryRules
import com.example.feature.albums.MemoryUi
import com.example.feature.albums.SearchScope
import com.example.feature.albums.SearchScreen
import com.example.feature.albums.SystemCollection
import com.example.feature.camera.CameraViewModel
import com.example.feature.duplicates.DupGroup
import com.example.feature.duplicates.DupScan
import com.example.feature.duplicates.DuplicateFinder
import com.example.feature.duplicates.DuplicatesScreen
import com.example.feature.editor.EditorEngine
import com.example.feature.editor.EditorScreen
import com.example.feature.gallery.GalleryItem
import com.example.feature.gallery.GalleryScreen
import com.example.feature.onboarding.OnboardingScreen
import com.example.feature.settings.SettingsScreen
import com.example.feature.video.FrameGrabber
import com.example.feature.video.TrimPanel
import com.example.feature.video.VideoPlayer
import com.example.feature.video.VideoTrimmer
import com.example.feature.viewer.ViewerItem
import com.example.feature.viewer.ViewerScreen
import com.example.ui.feedback.GlassToast
import com.example.ui.glass.GlassKind
import com.example.ui.glass.glass
import com.example.ui.glass.rememberGlass
import com.example.ui.nav.FarTab
import com.example.ui.nav.GlassBottomBar
import com.example.ui.nav.GlassNavRail
import com.example.ui.theme.FarPalette
import com.example.ui.theme.FarTheme
import kotlinx.coroutines.launch

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
        val glass = rememberGlass()
        val scope = rememberCoroutineScope()
        val db = remember { FarDatabase.get(ctx) }
        val repo = remember { MediaRepository(ctx.contentResolver) }
        val ocrIndexer = remember { OcrIndexer(ctx) }
        val gvm: GalleryViewModel = viewModel(factory = GalleryViewModel.factory(ctx.contentResolver, db))
        val items = gvm.items.collectAsLazyPagingItems()
        val selection by gvm.selection.collectAsState()
        val zoom by gvm.zoom.collectAsState()
        val filter by gvm.filter.collectAsState()
        val favorites by gvm.favoriteIds.collectAsState()
        val albums by gvm.albums.collectAsState()
        val albumRefs by gvm.albumRefs.collectAsState()
        val libraryRows by gvm.libraryRows.collectAsState()
        val recentSearches by gvm.recentSearches.collectAsState()
        val ocrCount by gvm.ocrCount.collectAsState()
        val excludedFolders by gvm.excludedFolders.collectAsState()
        val interruptedOps by db.operations().interrupted().collectAsState(initial = emptyList())

        var viewerList by remember { mutableStateOf<List<ViewerItem>>(emptyList()) }
        var viewerIndex by remember { mutableIntStateOf(0) }
        var tab by rememberSaveable { mutableStateOf(if (startOnFiles) FarTab.Files else FarTab.Photos) }
        var toast by remember { mutableStateOf<String?>(null) }
        var favoritesFilter by rememberSaveable { mutableStateOf(false) }
        var albumPickerTargets by remember { mutableStateOf<List<Long>?>(null) }

        var searchQuery by rememberSaveable { mutableStateOf("") }
        var searchScope by remember { mutableStateOf(SearchScope.All) }
        var ocrPercent by remember { mutableStateOf<Int?>(null) }

        var trimTarget by remember { mutableStateOf<Pair<Uri, Long>?>(null) }
        var trimBusy by remember { mutableStateOf(false) }
        var trimProgress by remember { mutableStateOf<Float?>(null) }

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
                gvm.refreshAll()
                items.refresh()
            }
        }

        fun trash(uris: List<Uri>) {
            if (uris.isEmpty()) return
            runCatching { trashLauncher.launch(IntentSenderRequest.Builder(repo.trashRequest(uris, true)).build()) }
                .onFailure { toast = "Couldn't open delete confirmation." }
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
        LaunchedEffect(tab, searchQuery, searchScope, favoritesFilter, favorites) {
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
                    gvm.filter.value = MediaFilter(
                        kind = when (searchScope) {
                            SearchScope.Photos -> MediaKind.Photos
                            SearchScope.Videos -> MediaKind.Videos
                            else -> MediaKind.All
                        },
                        pathContains = if (searchScope == SearchScope.Screenshots) "Screenshots" else null,
                        minSizeBytes = if (searchScope == SearchScope.Large) 100L * 1024 * 1024 else null,
                        onlyIds = finalIds,
                        forceEmpty = q.isNotBlank() && (finalIds == null || finalIds.isEmpty()),
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
                    val avs = this
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val wide = maxWidth >= 600.dp
                        Row(Modifier.fillMaxSize()) {
                            if (wide && selection.isEmpty()) {
                                GlassNavRail(
                                    glass = glass,
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
                                            glass = glass,
                                            zoom = zoom,
                                            onZoomChange = { gvm.zoom.value = it },
                                            selection = selection,
                                            onOpen = { m ->
                                                if (pick != null) gvm.toggleSelect(m.id) else {
                                                    val snap = items.itemSnapshotList.items.filterIsInstance<GalleryItem.Media>()
                                                    viewerList = snap.map { ViewerItem(it.id, it.uri, it.description, null, it.isVideo, it.isFavorite) }
                                                    viewerIndex = snap.indexOfFirst { it.id == m.id }.coerceAtLeast(0)
                                                    nav.navigate("viewer")
                                                }
                                            },
                                            onToggleSelect = { gvm.toggleSelect(it.id) },
                                            onRetry = { items.retry() },
                                            onRequestAccess = {
                                                if (access == Access.None) permLauncher.launch(Permissions.mediaPermissions())
                                                else activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                                            },
                                            initialColumns = s.gridColumns,
                                            access = access,
                                            activeKind = filter.kind,
                                            favoritesOnly = favoritesFilter,
                                            onKindChange = { k -> gvm.setKind(k) },
                                            onToggleFavoritesOnly = { favoritesFilter = !favoritesFilter },
                                            onOpenSettings = { nav.navigate("settings") },
                                            onClearSelection = { gvm.clearSelection() },
                                            mediaTileModifier = { m -> Modifier.farShared("media_${m.id}", this@SharedTransitionLayout, avs) },
                                        )

                                        FarTab.Albums -> AlbumsScreen(
                                            albums = albumUis,
                                            memories = memories,
                                            glass = glass,
                                            onOpenAlbum = { nav.navigate("album/${it.id}") },
                                            onOpenMemory = { nav.navigate("collection/memory_${it.id}") },
                                            onOpenCollection = { col ->
                                                when (col) {
                                                    SystemCollection.Favorites -> nav.navigate("collection/favorites")
                                                    SystemCollection.Videos -> nav.navigate("collection/videos")
                                                    SystemCollection.Screenshots -> nav.navigate("collection/screenshots")
                                                    SystemCollection.Camera -> nav.navigate("collection/camera")
                                                    SystemCollection.Duplicates -> nav.navigate("duplicates")
                                                    SystemCollection.RecentlyDeleted -> nav.navigate("collection/trash")
                                                    SystemCollection.ExcludedFolders -> nav.navigate("excluded")
                                                }
                                            },
                                            onCreateAlbum = { name -> gvm.createAlbum(name) },
                                            onRenameAlbum = { album, name -> gvm.renameAlbum(album.id, name) },
                                            onTogglePin = { album -> gvm.togglePinAlbum(album.id, album.pinned) },
                                            onDeleteAlbum = { album -> gvm.deleteAlbum(album.id) },
                                        )

                                        FarTab.Search -> SearchScreen(
                                            query = searchQuery,
                                            onQuery = { searchQuery = it },
                                            onSubmitQuery = { q -> gvm.addSearchHistory(q) },
                                            scope = searchScope,
                                            onScope = { searchScope = it },
                                            recent = recentSearches,
                                            onClearRecent = { gvm.clearSearchHistory() },
                                            index = when {
                                                !s.ocrEnabled -> IndexStatus.Disabled
                                                ocrPercent != null -> IndexStatus.Indexing(ocrPercent!!)
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
                                            resultCount = if (searchQuery.isBlank() && searchScope == SearchScope.All) null else items.itemCount,
                                            glass = glass,
                                            results = {
                                                GalleryScreen(
                                                    items = items,
                                                    glass = glass,
                                                    zoom = zoom,
                                                    onZoomChange = { gvm.zoom.value = it },
                                                    selection = selection,
                                                    onOpen = { m ->
                                                        val snap = items.itemSnapshotList.items.filterIsInstance<GalleryItem.Media>()
                                                        viewerList = snap.map { ViewerItem(it.id, it.uri, it.description, null, it.isVideo, it.isFavorite) }
                                                        viewerIndex = snap.indexOfFirst { it.id == m.id }.coerceAtLeast(0)
                                                        if (searchQuery.isNotBlank()) gvm.addSearchHistory(searchQuery)
                                                        nav.navigate("viewer")
                                                    },
                                                    onToggleSelect = { gvm.toggleSelect(it.id) },
                                                    onRetry = { items.retry() },
                                                    onRequestAccess = { permLauncher.launch(Permissions.mediaPermissions()) },
                                                    showTopBar = false,
                                                )
                                            },
                                        )

                                        FarTab.Files -> FilesTab(glass)
                                    }
                                }

                                if (!wide) {
                                    Box(Modifier.align(Alignment.BottomCenter)) {
                                        GlassBottomBar(
                                            glass = glass,
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
                                            .padding(16.dp)
                                            .glass(glass, RoundedCornerShape(28.dp), GlassKind.Bar)
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("${selection.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))
                                        if (pick != null) {
                                            TextButton(onClick = {
                                                val uris = items.itemSnapshotList.items
                                                    .filterIsInstance<GalleryItem.Media>()
                                                    .filter { it.id in selection }
                                                    .map { Uri.parse(it.uri) }
                                                IntentResults.pick(activity, uris.take(pick.maxItems))
                                            }) { Text("Return ${selection.size}") }
                                        } else {
                                            TextButton(onClick = { albumPickerTargets = selection.toList() }) { Text("Album") }
                                            TextButton(onClick = {
                                                val uris = ArrayList(
                                                    items.itemSnapshotList.items
                                                        .filterIsInstance<GalleryItem.Media>()
                                                        .filter { it.id in selection }
                                                        .map { Uri.parse(it.uri) }
                                                )
                                                if (uris.isNotEmpty()) {
                                                    activity.startActivity(
                                                        Intent.createChooser(
                                                            Intent(Intent.ACTION_SEND_MULTIPLE)
                                                                .setType("*/*")
                                                                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                                            null,
                                                        )
                                                    )
                                                }
                                            }) { Text(stringResource(R.string.action_share)) }
                                            TextButton(onClick = {
                                                val uris = items.itemSnapshotList.items
                                                    .filterIsInstance<GalleryItem.Media>()
                                                    .filter { it.id in selection }
                                                    .map { Uri.parse(it.uri) }
                                                trash(uris)
                                            }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
                                        }
                                    }
                                }

                                if (interruptedOps.isNotEmpty()) {
                                    val firstOp = interruptedOps.first()
                                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 152.dp)) {
                                        GlassToast(
                                            glass = glass,
                                            message = "${firstOp.type} stopped unexpectedly (${firstOp.done}/${firstOp.total})",
                                            actionLabel = "Dismiss",
                                            onAction = { scope.launch { db.operations().dismiss(firstOp.id) } },
                                        )
                                    }
                                }

                                toast?.let { t ->
                                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)) {
                                        GlassToast(glass, t, stringResource(R.string.action_ok), onAction = { toast = null })
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
                    val avs = this
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

                    BackHandler { nav.popBackStack() }
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        SubCollectionHeader(
                            title = album?.name ?: stringResource(R.string.albums_title),
                            subtitle = "${ids.size} items",
                            onBack = { nav.popBackStack() },
                        )
                        GalleryScreen(
                            items = subItems,
                            glass = glass,
                            zoom = subZoom,
                            onZoomChange = { subGvm.zoom.value = it },
                            selection = subSel,
                            onOpen = { m ->
                                val snap = subItems.itemSnapshotList.items.filterIsInstance<GalleryItem.Media>()
                                viewerList = snap.map { ViewerItem(it.id, it.uri, it.description, null, it.isVideo, it.isFavorite) }
                                viewerIndex = snap.indexOfFirst { it.id == m.id }.coerceAtLeast(0)
                                nav.navigate("viewer")
                            },
                            onToggleSelect = { subGvm.toggleSelect(it.id) },
                            onRetry = { subItems.retry() },
                            onRequestAccess = { permLauncher.launch(Permissions.mediaPermissions()) },
                            showTopBar = false,
                            modifier = Modifier.weight(1f),
                            mediaTileModifier = { m -> Modifier.farShared("media_${m.id}", this@SharedTransitionLayout, avs) },
                        )
                    }
                }

                composable("collection/{kind}") { entry ->
                    val kind = entry.arguments?.getString("kind").orEmpty()
                    val mem = if (kind.startsWith("memory_")) memories.firstOrNull { it.id == kind.removePrefix("memory_") } else null
                    val colFilter = when (kind) {
                        "favorites" -> MediaFilter(onlyIds = favorites, forceEmpty = favorites.isEmpty())
                        "videos" -> MediaFilter(kind = MediaKind.Videos)
                        "screenshots" -> MediaFilter(pathContains = "Screenshots")
                        "camera" -> MediaFilter(pathContains = "DCIM/Camera")
                        "trash" -> MediaFilter(trashedOnly = true)
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
                    val title = when (kind) {
                        "favorites" -> stringResource(R.string.collection_favorites)
                        "videos" -> stringResource(R.string.collection_videos)
                        "screenshots" -> stringResource(R.string.collection_screenshots)
                        "camera" -> stringResource(R.string.collection_camera)
                        "trash" -> stringResource(R.string.collection_trash)
                        else -> mem?.title ?: stringResource(R.string.collections_title)
                    }

                    BackHandler { nav.popBackStack() }
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        SubCollectionHeader(
                            title = title,
                            subtitle = mem?.subtitle,
                            onBack = { nav.popBackStack() },
                            actionLabel = if (kind == "trash" && subSel.isNotEmpty()) "Restore" else null,
                            onAction = if (kind == "trash" && subSel.isNotEmpty()) {
                                {
                                    val uris = subItems.itemSnapshotList.items
                                        .filterIsInstance<GalleryItem.Media>()
                                        .filter { it.id in subSel }
                                        .map { Uri.parse(it.uri) }
                                    if (uris.isNotEmpty()) {
                                        restoreLauncher.launch(IntentSenderRequest.Builder(repo.trashRequest(uris, false)).build())
                                    }
                                }
                            } else null,
                        )
                        GalleryScreen(
                            items = subItems,
                            glass = glass,
                            zoom = subZoom,
                            onZoomChange = { subGvm.zoom.value = it },
                            selection = subSel,
                            onOpen = { m ->
                                val snap = subItems.itemSnapshotList.items.filterIsInstance<GalleryItem.Media>()
                                viewerList = snap.map { ViewerItem(it.id, it.uri, it.description, null, it.isVideo, it.isFavorite) }
                                viewerIndex = snap.indexOfFirst { it.id == m.id }.coerceAtLeast(0)
                                nav.navigate("viewer")
                            },
                            onToggleSelect = { subGvm.toggleSelect(it.id) },
                            onRetry = { subItems.retry() },
                            onRequestAccess = { permLauncher.launch(Permissions.mediaPermissions()) },
                            showTopBar = false,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                composable("duplicates") {
                    BackHandler { nav.popBackStack() }
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

                composable("excluded") {
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

                composable("viewer") {
                    val avs = this
                    val liveList = remember(viewerList, favorites) {
                        viewerList.map { it.copy(isFavorite = it.id in favorites) }
                    }
                    ViewerScreen(
                        items = liveList,
                        startIndex = viewerIndex,
                        glass = glass,
                        onClose = { nav.popBackStack() },
                        onFavorite = { item -> if (item.id > 0) gvm.toggleFavorite(item.id) },
                        onShare = { item ->
                            activity.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND)
                                        .setType(if (item.isVideo) "video/*" else "image/*")
                                        .putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uri))
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                    null,
                                )
                            )
                        },
                        onEdit = { nav.navigate("editor/${Uri.encode(it.uri)}?mediaId=${it.id}") },
                        onDelete = { trash(listOf(Uri.parse(it.uri))) },
                        onAddToAlbum = { item -> if (item.id > 0) albumPickerTargets = listOf(item.id) },
                        detailsContent = { item ->
                            val tags by db.tags().tagsForMedia(item.id).collectAsState(initial = emptyList())
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
                                    scope.launch {
                                        ocrBusy = true
                                        val res = ocrIndexer.extractAndIndex(item.id, Uri.parse(item.uri))
                                        ocrText = (res as? FarResult.Ok)?.value ?: ""
                                        ocrBusy = false
                                    }
                                },
                            )
                        },
                        videoContent = { item ->
                            VideoPlayer(
                                uri = item.uri,
                                glass = glass,
                                resumeMs = null,
                                onPosition = {},
                                onFrameGrab = { ms ->
                                    scope.launch {
                                        when (FrameGrabber.grab(ctx, Uri.parse(item.uri), ms)) {
                                            is FarResult.Ok -> toast = "Saved frame to Pictures/Far Gallery"
                                            is FarResult.Err -> toast = "Couldn't capture frame"
                                        }
                                    }
                                },
                                onTrim = { dur -> trimTarget = Uri.parse(item.uri) to dur },
                                onOpenWith = {
                                    activity.startActivity(
                                        Intent.createChooser(
                                            Intent(Intent.ACTION_VIEW)
                                                .setDataAndType(Uri.parse(item.uri), "video/*")
                                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                            null,
                                        )
                                    )
                                },
                            )
                        },
                        imageModifier = { it2 -> Modifier.farShared("media_${it2.id}", this@SharedTransitionLayout, avs) },
                    )
                }

                composable("camera") {
                    BackHandler { nav.popBackStack() }
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

                composable("editor/{uri}?mediaId={mediaId}") { entry ->
                    val uri = Uri.parse(entry.arguments?.getString("uri").orEmpty())
                    val mediaId = entry.arguments?.getString("mediaId")?.toLongOrNull() ?: 0L
                    var bmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                    var err by remember { mutableStateOf<String?>(null) }
                    var saving by remember { mutableStateOf(false) }

                    LaunchedEffect(uri) {
                        when (val loaded = EditorEngine.load(ctx, uri, 2048)) {
                            is FarResult.Ok -> bmp = loaded.value
                            is FarResult.Err -> err = "This image can't be edited here. The original is untouched."
                        }
                    }

                    EditorScreen(
                        source = bmp,
                        loading = bmp == null && err == null,
                        errorText = err,
                        glass = glass,
                        saving = saving,
                        onCancel = { nav.popBackStack() },
                        onSave = { st ->
                            val b = bmp ?: return@EditorScreen
                            saving = true
                            scope.launch {
                                when (val res = EditorEngine.saveCopy(ctx, uri, b, st, 94)) {
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
                                        toast = "Saved a copy"
                                        gvm.refreshAll()
                                        items.refresh()
                                        nav.popBackStack()
                                    }
                                    is FarResult.Err -> {
                                        saving = false
                                        toast = "Couldn't save. Your original is untouched."
                                    }
                                }
                            }
                        },
                    )
                }

                composable("settings") {
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
                        onBack = { nav.popBackStack() },
                    )
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

        trimTarget?.let { (uri, durMs) ->
            ModalBottomSheet(onDismissRequest = { if (!trimBusy) trimTarget = null }) {
                TrimPanel(
                    durationMs = durMs,
                    busy = trimBusy,
                    progress = trimProgress,
                    onSave = { startMs, endMs ->
                        trimBusy = true
                        scope.launch {
                            when (VideoTrimmer.trim(ctx, uri, startMs, endMs) { trimProgress = it }) {
                                is FarResult.Ok -> {
                                    toast = "Trimmed copy saved to Movies/Far Gallery"
                                    gvm.refreshAll()
                                    items.refresh()
                                }
                                is FarResult.Err -> toast = "Couldn't trim video. Original is untouched."
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
    }
}
