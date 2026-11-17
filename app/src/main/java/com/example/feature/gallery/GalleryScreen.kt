package com.example.feature.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.SubcomposeAsyncImage
import com.example.Access
import com.example.R
import com.example.core.media.MediaKind
import com.example.ui.feedback.EmptyState
import com.example.ui.feedback.ErrorAction
import com.example.ui.feedback.ErrorCard
import com.example.ui.feedback.ErrorInfo
import com.example.ui.feedback.skeleton
import com.example.ui.glass.GlassKind
import com.example.ui.glass.glass
import com.example.ui.glass.glassSource
import com.example.ui.theme.FarMotion
import com.example.ui.theme.LocalHapticsEnabled
import com.example.ui.theme.LocalReducedMotion
import dev.chrisbanes.haze.HazeState

/** ---------- UI models (built from Room + MediaStore, never fabricated) ---------- */
sealed interface GalleryItem {
    val key: String

    @Immutable
    data class Header(val title: String, val subtitle: String? = null) : GalleryItem {
        override val key get() = "h_$title"
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

@Composable
fun GalleryScreen(
    items: LazyPagingItems<GalleryItem>,
    glass: HazeState,
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
    activeKind: MediaKind = MediaKind.All,
    favoritesOnly: Boolean = false,
    onKindChange: ((MediaKind) -> Unit)? = null,
    onToggleFavoritesOnly: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onClearSelection: (() -> Unit)? = null,
    mediaTileModifier: @Composable (GalleryItem.Media) -> Modifier = { Modifier },
    gridState: LazyGridState = rememberLazyGridState(),
) {
    var columns by remember(initialColumns) { mutableIntStateOf(initialColumns.coerceIn(2, 6)) }
    val selecting = selection.isNotEmpty()
    val topInset = if (showTopBar) 198.dp else 12.dp

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val refresh = items.loadState.refresh
        when {
            refresh is LoadState.Loading && items.itemCount == 0 -> SkeletonGrid(columns, topInset)
            refresh is LoadState.Error && items.itemCount == 0 -> ErrorCard(
                ErrorInfo(
                    title = "Can't load your library",
                    whatHappened = "Far Gallery couldn't read the media index just now.",
                    code = "FG-MQ",
                    actions = listOf(ErrorAction(stringResource(R.string.action_retry), primary = true, onClick = onRetry)),
                ),
                Modifier.align(Alignment.Center),
            )
            items.itemCount == 0 -> EmptyState(
                icon = Icons.Filled.PhotoLibrary,
                title = "No photos or videos yet",
                body = if (access == Access.None) {
                    "Far Gallery can only show media it has access to. Allow access, or pick photos to share."
                } else {
                    "Photos and videos on this device or matching this filter will appear here."
                },
                primary = if (access != Access.Full) ErrorAction("Review access", true, onRequestAccess) else null,
            )
            else -> Box(
                Modifier
                    .fillMaxSize()
                    .pinchToChangeColumns { delta -> columns = (columns + delta).coerceIn(2, 6) }
            ) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier
                        .fillMaxSize()
                        .glassSource(glass)
                        .testTag("gallery_grid"),
                    contentPadding = PaddingValues(top = topInset, bottom = 132.dp),
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
                            is GalleryItem.Header -> DateHeader(item)
                            is GalleryItem.Media -> MediaTile(
                                item = item,
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
                onKindChange = onKindChange,
                onToggleFavoritesOnly = onToggleFavoritesOnly,
                onRequestAccess = onRequestAccess,
                onOpenSettings = onOpenSettings,
                onClearSelection = onClearSelection,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun LibraryTopBar(
    glass: HazeState,
    zoom: LibraryZoom,
    onZoom: (LibraryZoom) -> Unit,
    selectedCount: Int,
    access: Access,
    activeKind: MediaKind,
    favoritesOnly: Boolean,
    onKindChange: ((MediaKind) -> Unit)?,
    onToggleFavoritesOnly: (() -> Unit)?,
    onRequestAccess: () -> Unit,
    onOpenSettings: (() -> Unit)?,
    onClearSelection: (() -> Unit)?,
    modifier: Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedCount > 0 && onClearSelection != null) {
                    IconButton(onClick = onClearSelection, modifier = Modifier.testTag("clear_selection_button")) {
                        Icon(Icons.Filled.Close, stringResource(R.string.cd_close))
                    }
                }
                Text(
                    if (selectedCount > 0) stringResource(R.string.selected_count, selectedCount)
                    else stringResource(R.string.library_title),
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier.semantics {
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
                        modifier = Modifier
                            .glass(glass, RoundedCornerShape(16.dp), GlassKind.Chip, elevated = false)
                            .clickable(onClick = onRequestAccess)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("access_status_chip"),
                    )
                }
                if (onOpenSettings != null) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .glass(glass, CircleShape, GlassKind.Chip)
                            .testTag("settings_button"),
                    ) {
                        Icon(Icons.Outlined.Settings, stringResource(R.string.cd_settings))
                    }
                }
            }
        }

        // Apple-Photos style segmented control in glass
        Row(
            Modifier
                .fillMaxWidth()
                .glass(glass, RoundedCornerShape(20.dp), GlassKind.Chip)
                .padding(3.dp),
        ) {
            LibraryZoom.entries.forEach { z ->
                val sel = z == zoom
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(17.dp))
                        .background(if (sel) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onZoom(z) }
                        .padding(vertical = 7.dp)
                        .testTag("zoom_${z.name.lowercase()}")
                        .semantics { selected = sel },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(z.labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (onKindChange != null || onToggleFavoritesOnly != null) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onKindChange != null) {
                    item {
                        FilterChip(
                            selected = !favoritesOnly && activeKind == MediaKind.All,
                            onClick = {
                                if (favoritesOnly && onToggleFavoritesOnly != null) onToggleFavoritesOnly()
                                onKindChange(MediaKind.All)
                            },
                            label = { Text(stringResource(R.string.zoom_all)) },
                            modifier = Modifier.testTag("filter_all"),
                        )
                    }
                    item {
                        FilterChip(
                            selected = !favoritesOnly && activeKind == MediaKind.Photos,
                            onClick = {
                                if (favoritesOnly && onToggleFavoritesOnly != null) onToggleFavoritesOnly()
                                onKindChange(MediaKind.Photos)
                            },
                            label = { Text(stringResource(R.string.nav_photos)) },
                            modifier = Modifier.testTag("filter_photos"),
                        )
                    }
                    item {
                        FilterChip(
                            selected = !favoritesOnly && activeKind == MediaKind.Videos,
                            onClick = {
                                if (favoritesOnly && onToggleFavoritesOnly != null) onToggleFavoritesOnly()
                                onKindChange(MediaKind.Videos)
                            },
                            label = { Text(stringResource(R.string.collection_videos)) },
                            modifier = Modifier.testTag("filter_videos"),
                        )
                    }
                }
                if (onToggleFavoritesOnly != null) {
                    item {
                        FilterChip(
                            selected = favoritesOnly,
                            onClick = onToggleFavoritesOnly,
                            label = { Text(stringResource(R.string.collection_favorites)) },
                            modifier = Modifier.testTag("filter_favorites"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DateHeader(h: GalleryItem.Header) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp)) {
        Text(h.title, style = MaterialTheme.typography.titleMedium)
        h.subtitle?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaTile(
    item: GalleryItem.Media,
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
                    if (item.isFavorite) append(", favorite")
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
        if (item.isFavorite) {
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
