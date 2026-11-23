package com.example.feature.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import coil.compose.SubcomposeAsyncImage
import com.example.Access
import com.example.R
import com.example.core.common.LocalDiagnostics
import com.example.core.media.MediaKind
import com.example.core.media.SortKey
import com.example.ui.components.FarFilterDrawer
import com.example.ui.components.FarFilterPill
import com.example.ui.feedback.EmptyState
import com.example.ui.feedback.ErrorAction
import com.example.ui.feedback.ErrorCard
import com.example.ui.feedback.ErrorInfo
import com.example.ui.feedback.skeleton
import com.example.ui.glass.FarGlassState
import com.example.ui.glass.GlassKind
import com.example.ui.glass.glass
import com.example.ui.glass.glassSource
import com.example.ui.theme.FarMotion
import com.example.ui.theme.FarSpacing
import com.example.ui.theme.LocalHapticsEnabled
import com.example.ui.theme.LocalIsDark
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.LocalSemanticColors

/** ---------- UI models (built from Room + MediaStore, never fabricated) ---------- */
sealed interface GalleryItem {
    val key: String

    @Immutable
    data class Header(val title: String, val subtitle: String? = null, val anchorId: Long = 0L) : GalleryItem {
        override val key get() = "h_${title}_$anchorId"
    }

    @Immutable
    data class Media(
        val id: Long,
        val uri: String,
        val description: String,
        val isVideo: Boolean = false,
        val durationLabel: String? = null,
        val isFavorite: Boolean = false,
        val isMotion: Boolean = false,
        val available: Boolean = true,
        val takenMs: Long = 0L,
    ) : GalleryItem {
        override val key get() = "m_$id"
    }
}

enum class LibraryZoom(val labelRes: Int) {
    Years(R.string.zoom_years),
    Months(R.string.zoom_months),
    Days(R.string.zoom_days),
    All(R.string.zoom_all),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    items: LazyPagingItems<GalleryItem>,
    glass: FarGlassState,
    zoom: LibraryZoom,
    onZoomChange: (LibraryZoom) -> Unit,
    selection: Set<Long>,
    onOpen: (GalleryItem.Media) -> Unit,
    onToggleSelect: (GalleryItem.Media) -> Unit,
    onRetry: () -> Unit,
    onRequestAccess: () -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    initialColumns: Int = 3,
    access: Access = Access.Full,
    favoriteIds: Set<Long> = emptySet(),
    activeKind: MediaKind = MediaKind.All,
    favoritesOnly: Boolean = false,
    activeSort: SortKey = SortKey.Taken,
    ascending: Boolean = false,
    onKindChange: ((MediaKind) -> Unit)? = null,
    onToggleFavoritesOnly: (() -> Unit)? = null,
    onSortChange: ((SortKey) -> Unit)? = null,
    onToggleSortDirection: (() -> Unit)? = null,
    onCycleTheme: (() -> Unit)? = null,
    onPullRefresh: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onClearSelection: (() -> Unit)? = null,
    onToggleGroupSelect: ((Set<Long>) -> Unit)? = null,
    mediaTileModifier: @Composable (GalleryItem.Media) -> Modifier = { Modifier },
    gridState: LazyGridState = rememberLazyGridState(),
) {
    var columns by remember(initialColumns) { mutableIntStateOf(initialColumns.coerceIn(2, 6)) }
    var showColumnBadge by remember { mutableStateOf(false) }
    var filterDrawerOpen by remember { mutableStateOf(false) }
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    val selecting = selection.isNotEmpty()
    val haptic = LocalHapticFeedback.current
    val hapticsOn = LocalHapticsEnabled.current
    val reduced = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val condensed by remember {
        derivedStateOf { showTopBar && (gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 72) }
    }
    val hasFilterStrip = onKindChange != null || onToggleFavoritesOnly != null || onSortChange != null
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val measuredTopDp = with(density) { topBarHeightPx.toDp() }
    val targetTopInset = when {
        !showTopBar -> 8.dp
        topBarHeightPx > 0 -> measuredTopDp
        condensed && !selecting -> statusTop + 60.dp
        hasFilterStrip -> statusTop + 158.dp
        else -> statusTop + 114.dp
    }
    val topInset by animateDpAsState(targetTopInset, FarMotion.spatial(reduced), label = "galleryTopInset")
    val bottomInset = navBottom + FarSpacing.bottomDockClearance

    val ctx = androidx.compose.ui.platform.LocalContext.current
    val refresh = items.loadState.refresh
    val append = items.loadState.append
    var userRefreshing by remember { mutableStateOf(false) }
    val ptrState = rememberPullToRefreshState()

    LaunchedEffect(columns) {
        if (showColumnBadge) {
            delay(950)
            showColumnBadge = false
        }
    }
    LaunchedEffect(refresh) {
        if (refresh !is LoadState.Loading) {
            userRefreshing = false
        }
    }
    LaunchedEffect(refresh, append) {
        if (refresh is LoadState.Error) {
            LocalDiagnostics.recordEvent(ctx, "FG-MQ", "gallery_refresh", refresh.error.javaClass.simpleName)
        }
        if (append is LoadState.Error) {
            LocalDiagnostics.recordEvent(ctx, "FG-MQ-APP", "gallery_append", append.error.javaClass.simpleName)
        }
    }

    PullToRefreshBox(
        isRefreshing = userRefreshing && refresh is LoadState.Loading,
        onRefresh = {
            userRefreshing = true
            if (onPullRefresh != null) onPullRefresh() else items.refresh()
        },
        state = ptrState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = ptrState,
                isRefreshing = userRefreshing && refresh is LoadState.Loading,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topInset),
            )
        },
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        when {
            refresh is LoadState.Loading && items.itemCount == 0 -> SkeletonGrid(columns, topInset)
            refresh is LoadState.Error && items.itemCount == 0 -> ErrorCard(
                ErrorInfo(
                    title = stringResource(R.string.gallery_load_error_title),
                    whatHappened = stringResource(R.string.gallery_load_error_body),
                    code = "FG-MQ",
                    actions = listOf(ErrorAction(stringResource(R.string.action_retry), primary = true, onClick = onRetry)),
                ),
                Modifier.align(Alignment.Center),
            )
            items.itemCount == 0 -> EmptyState(
                icon = Icons.Filled.PhotoLibrary,
                title = stringResource(R.string.gallery_empty_title),
                body = if (access == Access.None) {
                    stringResource(R.string.gallery_empty_no_access)
                } else {
                    stringResource(R.string.gallery_empty_with_access)
                },
                primary = if (access != Access.Full) ErrorAction(stringResource(R.string.gallery_review_access), true, onRequestAccess) else null,
            )
            else -> Box(
                Modifier
                    .fillMaxSize()
                    .pinchToChangeColumns { delta ->
                        val next = (columns + delta).coerceIn(2, 6)
                        if (next != columns) {
                            columns = next
                            showColumnBadge = true
                            if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
            ) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier
                        .fillMaxSize()
                        .glassSource(glass)
                        .testTag("gallery_grid"),
                    contentPadding = PaddingValues(top = topInset, bottom = bottomInset),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(
                        count = items.itemCount,
                        key = items.itemKey { it.key },
                        span = { index ->
                            if (items.peek(index) is GalleryItem.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1)
                        },
                        contentType = { index -> items.peek(index)?.let { it::class } },
                    ) { index ->
                        when (val item = items[index]) {
                            is GalleryItem.Header -> {
                                val groupIds = remember(items.itemCount, index, item.anchorId) {
                                    buildSet {
                                        var i = index + 1
                                        while (i < items.itemCount) {
                                            when (val next = items.peek(i)) {
                                                is GalleryItem.Header -> break
                                                is GalleryItem.Media -> add(next.id)
                                                null -> Unit
                                            }
                                            i++
                                        }
                                    }
                                }
                                val allSelected = groupIds.isNotEmpty() && groupIds.all { it in selection }
                                DateHeader(
                                    h = item,
                                    selecting = selecting,
                                    allSelected = allSelected,
                                    onToggleGroup = if (onToggleGroupSelect != null && groupIds.isNotEmpty()) {
                                        {
                                            if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onToggleGroupSelect(groupIds)
                                        }
                                    } else null,
                                )
                            }
                            is GalleryItem.Media -> MediaTile(
                                item = item,
                                isFavorite = item.isFavorite || item.id in favoriteIds,
                                selecting = selecting,
                                selected = item.id in selection,
                                onClick = { if (selecting) onToggleSelect(item) else onOpen(item) },
                                onLongClick = { onToggleSelect(item) },
                                modifier = Modifier
                                    .animateItem()
                                    .then(mediaTileModifier(item)),
                            )
                            null -> Box(Modifier.aspectRatio(1f).skeleton())
                        }
                    }
                    if (append is LoadState.Error) {
                        item(
                            key = "append_error_footer",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            val semantic = LocalSemanticColors.current
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = FarSpacing.screenPadding, vertical = 12.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(semantic.elevatedSurface)
                                    .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(18.dp))
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    stringResource(R.string.gallery_append_error),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                androidx.compose.material3.TextButton(
                                    onClick = { items.retry() },
                                    modifier = Modifier.testTag("gallery_append_retry"),
                                ) {
                                    Text(stringResource(R.string.action_retry))
                                }
                            }
                        }
                    }
                }

                TimelineFastScrollbar(
                    gridState = gridState,
                    items = items,
                    glass = glass,
                    topMargin = topInset,
                    bottomMargin = bottomInset,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )

                AnimatedVisibility(
                    visible = showColumnBadge,
                    enter = fadeIn(FarMotion.effects(reduced)) + scaleIn(initialScale = 0.85f),
                    exit = fadeOut(FarMotion.effects(reduced)) + scaleOut(targetScale = 0.85f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topInset + 8.dp),
                ) {
                    Text(
                        "${columns}×",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .glass(glass, RoundedCornerShape(16.dp), GlassKind.Chip)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        if (showTopBar) {
            LibraryTopBar(
                glass = glass,
                zoom = zoom,
                onZoom = onZoomChange,
                selectedCount = selection.size,
                access = access,
                activeKind = activeKind,
                favoritesOnly = favoritesOnly,
                condensed = condensed && selection.isEmpty(),
                filterDrawerOpen = filterDrawerOpen,
                onToggleFilterDrawer = { filterDrawerOpen = !filterDrawerOpen },
                activeSort = activeSort,
                ascending = ascending,
                columns = columns,
                onColumnsChange = { c ->
                    columns = c
                    showColumnBadge = true
                },
                onKindChange = onKindChange,
                onToggleFavoritesOnly = onToggleFavoritesOnly,
                onSortChange = onSortChange,
                onToggleSortDirection = onToggleSortDirection,
                onCycleTheme = onCycleTheme,
                onRequestAccess = onRequestAccess,
                onOpenSettings = onOpenSettings,
                onClearSelection = onClearSelection,
                onScrollTop = { scope.launch { gridState.animateScrollToItem(0) } },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onSizeChanged { topBarHeightPx = it.height },
            )
        }
    }
}

@Composable
private fun LibraryTopBar(
    glass: FarGlassState,
    zoom: LibraryZoom,
    onZoom: (LibraryZoom) -> Unit,
    selectedCount: Int,
    access: Access,
    activeKind: MediaKind,
    favoritesOnly: Boolean,
    condensed: Boolean,
    filterDrawerOpen: Boolean,
    onToggleFilterDrawer: () -> Unit,
    activeSort: SortKey,
    ascending: Boolean,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    onKindChange: ((MediaKind) -> Unit)?,
    onToggleFavoritesOnly: (() -> Unit)?,
    onSortChange: ((SortKey) -> Unit)?,
    onToggleSortDirection: (() -> Unit)?,
    onCycleTheme: (() -> Unit)?,
    onRequestAccess: () -> Unit,
    onOpenSettings: (() -> Unit)?,
    onClearSelection: (() -> Unit)?,
    onScrollTop: () -> Unit,
    modifier: Modifier,
) {
    val reduced = LocalReducedMotion.current
    val isDark = LocalIsDark.current
    val semantic = LocalSemanticColors.current
    val vPad by animateDpAsState(if (condensed) 6.dp else 8.dp, FarMotion.spatial(reduced), label = "barPad")
    val hasAdvancedOptions = onSortChange != null || onToggleSortDirection != null || onKindChange != null
    val advancedActive = filterDrawerOpen || activeSort != SortKey.Taken || ascending
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = FarSpacing.screenPadding, vertical = vPad),
            verticalArrangement = Arrangement.spacedBy(if (condensed) 4.dp else 8.dp),
        ) {
            AnimatedVisibility(visible = !condensed) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (selectedCount > 0 && onClearSelection != null) {
                            IconButton(
                                onClick = onClearSelection,
                                modifier = Modifier.size(48.dp).testTag("clear_selection_button"),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    stringResource(R.string.cd_close),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        Text(
                            if (selectedCount > 0) stringResource(R.string.selected_count, selectedCount)
                            else stringResource(R.string.library_title),
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clickable(onClick = onScrollTop)
                                .semantics {
                                    if (selectedCount > 0) stateDescription = "$selectedCount items selected"
                                },
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (access != Access.Full) {
                            Text(
                                text = if (access == Access.Selected) stringResource(R.string.access_selected)
                                else stringResource(R.string.access_none),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .glass(glass, RoundedCornerShape(16.dp), GlassKind.Chip, elevated = false)
                                    .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(16.dp))
                                    .clickable(onClick = onRequestAccess)
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                                    .testTag("access_status_chip"),
                            )
                        }
                        if (onCycleTheme != null) {
                            IconButton(
                                onClick = onCycleTheme,
                                modifier = Modifier
                                    .size(48.dp)
                                    .glass(glass, CircleShape, GlassKind.Chip)
                                    .border(0.75.dp, semantic.cardBorder, CircleShape)
                                    .testTag("theme_cycle_button"),
                            ) {
                                Icon(
                                    if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                    stringResource(R.string.cd_cycle_theme),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        if (onOpenSettings != null) {
                            IconButton(
                                onClick = onOpenSettings,
                                modifier = Modifier
                                    .size(48.dp)
                                    .glass(glass, CircleShape, GlassKind.Chip)
                                    .border(0.75.dp, semantic.cardBorder, CircleShape)
                                    .testTag("settings_button"),
                            ) {
                                Icon(
                                    Icons.Outlined.Settings,
                                    stringResource(R.string.cd_settings),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            // Minimal glass zoom segmented bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .glass(glass, RoundedCornerShape(20.dp), GlassKind.Chip)
                    .border(0.75.dp, semantic.cardBorder, RoundedCornerShape(20.dp))
                    .padding(3.dp),
            ) {
                LibraryZoom.entries.forEach { z ->
                    val sel = z == zoom
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(17.dp))
                            .background(if (sel) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f) else Color.Transparent)
                            .clickable { onZoom(z) }
                            .padding(vertical = 7.dp)
                            .testTag("zoom_${z.name.lowercase()}")
                            .semantics { selected = sel },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(z.labelRes),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            color = if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            AnimatedVisibility(visible = !condensed && (onKindChange != null || onToggleFavoritesOnly != null || onSortChange != null)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onKindChange != null) {
                            item {
                                FarFilterPill(
                                    label = stringResource(R.string.zoom_all),
                                    selected = !favoritesOnly && activeKind == MediaKind.All,
                                    onClick = {
                                        if (favoritesOnly && onToggleFavoritesOnly != null) onToggleFavoritesOnly()
                                        onKindChange(MediaKind.All)
                                    },
                                    modifier = Modifier.testTag("filter_all"),
                                )
                            }
                            item {
                                FarFilterPill(
                                    label = stringResource(R.string.nav_photos),
                                    selected = !favoritesOnly && activeKind == MediaKind.Photos,
                                    onClick = {
                                        if (favoritesOnly && onToggleFavoritesOnly != null) onToggleFavoritesOnly()
                                        onKindChange(MediaKind.Photos)
                                    },
                                    modifier = Modifier.testTag("filter_photos"),
                                )
                            }
                            item {
                                FarFilterPill(
                                    label = stringResource(R.string.collection_videos),
                                    selected = !favoritesOnly && activeKind == MediaKind.Videos,
                                    onClick = {
                                        if (favoritesOnly && onToggleFavoritesOnly != null) onToggleFavoritesOnly()
                                        onKindChange(MediaKind.Videos)
                                    },
                                    modifier = Modifier.testTag("filter_videos"),
                                )
                            }
                        }
                        if (onToggleFavoritesOnly != null) {
                            item {
                                FarFilterPill(
                                    label = stringResource(R.string.collection_favorites),
                                    selected = favoritesOnly,
                                    onClick = onToggleFavoritesOnly,
                                    modifier = Modifier.testTag("filter_favorites"),
                                )
                            }
                        }
                        if (hasAdvancedOptions) {
                            item {
                                FarFilterPill(
                                    label = stringResource(R.string.filter_filters),
                                    selected = advancedActive,
                                    leadingIcon = Icons.Filled.Tune,
                                    onClick = onToggleFilterDrawer,
                                    modifier = Modifier.testTag("gallery_filters_toggle"),
                                )
                            }
                        }
                    }

                    FarFilterDrawer(visible = filterDrawerOpen, glass = glass) {
                        if (onSortChange != null) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.filter_sort),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (onToggleSortDirection != null) {
                                    FarFilterPill(
                                        label = stringResource(if (ascending) R.string.filter_ascending else R.string.filter_descending),
                                        selected = ascending,
                                        leadingIcon = Icons.Filled.SwapVert,
                                        onClick = onToggleSortDirection,
                                        modifier = Modifier.testTag("gallery_sort_dir_pill"),
                                    )
                                }
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val sorts = listOf(
                                    SortKey.Taken to R.string.sort_taken,
                                    SortKey.Modified to R.string.sort_modified,
                                    SortKey.Name to R.string.sort_name,
                                    SortKey.Size to R.string.sort_size,
                                )
                                items(sorts.size) { idx ->
                                    val (sk, labelRes) = sorts[idx]
                                    FarFilterPill(
                                        label = stringResource(labelRes),
                                        selected = activeSort == sk,
                                        onClick = { onSortChange(sk) },
                                        modifier = Modifier.testTag("gallery_sort_${sk.name.lowercase()}"),
                                    )
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.filter_density),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            listOf(2, 3, 4, 5).forEach { count ->
                                FarFilterPill(
                                    label = "${count}×",
                                    selected = columns == count,
                                    onClick = { onColumnsChange(count) },
                                    modifier = Modifier.testTag("gallery_cols_$count"),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineFastScrollbar(
    gridState: LazyGridState,
    items: LazyPagingItems<GalleryItem>,
    glass: FarGlassState,
    topMargin: androidx.compose.ui.unit.Dp,
    bottomMargin: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    if (items.itemCount < 18) return
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val hapticsOn = LocalHapticsEnabled.current
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val total = items.itemCount.coerceAtLeast(1)
    val scrollFraction by remember(total) {
        derivedStateOf {
            (gridState.firstVisibleItemIndex.toFloat() / (total - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
        }
    }
    val activeFraction = if (dragging) dragFraction else scrollFraction
    val activeIdx = (activeFraction * (total - 1)).toInt().coerceIn(0, total - 1)
    val dateBubble = remember(activeIdx, items.itemCount) {
        var label: String? = null
        for (i in activeIdx downTo (activeIdx - 24).coerceAtLeast(0)) {
            when (val entry = items.peek(i)) {
                is GalleryItem.Header -> {
                    label = entry.title
                    break
                }
                is GalleryItem.Media -> if (label == null && entry.takenMs > 0L) {
                    label = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())
                        .format(Instant.ofEpochMilli(entry.takenMs).atZone(ZoneId.systemDefault()))
                }
                null -> Unit
            }
        }
        label
    }

    LaunchedEffect(dateBubble, dragging) {
        if (dragging && dateBubble != null && hapticsOn) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val barAlpha by animateFloatAsState(if (dragging || gridState.isScrollInProgress) 1f else 0.35f, label = "scrollAlpha")

    BoxWithConstraints(
        modifier
            .padding(top = topMargin, bottom = bottomMargin, end = 4.dp)
            .fillMaxHeight()
            .width(36.dp)
            .alpha(barAlpha),
    ) {
        val density = LocalDensity.current
        val trackHeightPx = with(density) { (maxHeight - 44.dp).toPx().coerceAtLeast(1f) }
        val thumbOffsetPx = (activeFraction * trackHeightPx).toInt()

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(total, trackHeightPx) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            val f = (offset.y / trackHeightPx).coerceIn(0f, 1f)
                            dragFraction = f
                            scope.launch { gridState.scrollToItem((f * (total - 1)).toInt()) }
                        },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val f = (change.position.y / trackHeightPx).coerceIn(0f, 1f)
                            dragFraction = f
                            scope.launch { gridState.scrollToItem((f * (total - 1)).toInt()) }
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    )
                }
        ) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, thumbOffsetPx) }
                    .width(if (dragging) 10.dp else 6.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        if (dragging) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                    .testTag("timeline_fast_thumb")
            )
        }

        if (dragging && dateBubble != null) {
            Text(
                text = dateBubble,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(-with(density) { 42.dp.roundToPx() }, thumbOffsetPx) }
                    .glass(glass, RoundedCornerShape(16.dp), GlassKind.Chip)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DateHeader(
    h: GalleryItem.Header,
    selecting: Boolean = false,
    allSelected: Boolean = false,
    onToggleGroup: (() -> Unit)? = null,
) {
    val headerModifier = if (onToggleGroup != null) {
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selecting) onToggleGroup() },
                onLongClick = onToggleGroup,
            )
    } else {
        Modifier.fillMaxWidth()
    }
    Row(
        headerModifier.padding(start = 16.dp, end = 12.dp, top = 20.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(h.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            h.subtitle?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (onToggleGroup != null && selecting) {
            androidx.compose.material3.TextButton(
                onClick = onToggleGroup,
                modifier = Modifier.testTag("date_header_select_${h.anchorId}"),
            ) {
                Text(
                    stringResource(if (allSelected) R.string.gallery_deselect_group else R.string.gallery_select_group),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaTile(
    item: GalleryItem.Media,
    isFavorite: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val haptic = LocalHapticFeedback.current
    val hapticsOn = LocalHapticsEnabled.current
    val scale by animateFloatAsState(if (selected) 0.92f else 1f, FarMotion.spatial(reduced), label = "tileScale")
    Box(
        modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(RoundedCornerShape(2.dp))
            .testTag("media_tile_${item.id}")
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .semantics {
                contentDescription = buildString {
                    append(item.description)
                    if (isFavorite) append(", favorite")
                    if (!item.available) append(", unavailable")
                }
                this.selected = selected
            },
    ) {
        SubcomposeAsyncImage(
            model = item.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = { Box(Modifier.fillMaxSize().skeleton()) },
            error = {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.BrokenImage, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
        if (!item.available) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        if (isFavorite) {
            Icon(
                Icons.Filled.Favorite,
                null,
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(14.dp),
                tint = Color.White,
            )
        }
        if (item.isVideo) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(0.55f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(12.dp), tint = Color.White)
                item.durationLabel?.let { Text(it, color = Color.White, style = MaterialTheme.typography.labelMedium) }
            }
        }
        AnimatedVisibility(
            visible = selecting,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
            enter = scaleIn(FarMotion.spatial(reduced)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = Color.White)
            }
        }
    }
}

@Composable
private fun SkeletonGrid(columns: Int, topInset: androidx.compose.ui.unit.Dp) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        userScrollEnabled = false,
        contentPadding = PaddingValues(top = topInset),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(columns * 8) { Box(Modifier.aspectRatio(1f).skeleton()) }
    }
}

/**
 * Two-finger pinch changes column count without stealing one-finger scrolling.
 * Observes on the Initial pass and consumes only while two pointers are down.
 */
private fun Modifier.pinchToChangeColumns(onStep: (Int) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var accumulated = 1f
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                accumulated *= event.calculateZoom()
                event.changes.forEach { it.consume() }
                if (accumulated > 1.3f) {
                    onStep(-1)
                    accumulated = 1f
                } else if (accumulated < 0.77f) {
                    onStep(+1)
                    accumulated = 1f
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
