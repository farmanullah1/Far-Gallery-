package com.example.feature.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.R
import com.example.core.common.LocalDiagnostics
import com.example.core.common.safeParseMediaUri
import com.example.core.media.ExifScrubber
import com.example.core.media.HistogramData
import com.example.core.media.HistogramEngine
import com.example.core.media.HistogramOverlay
import com.example.core.media.QuickExifInfo
import com.example.ui.feedback.ErrorAction
import com.example.ui.feedback.ErrorCard
import com.example.ui.feedback.ErrorInfo
import com.example.ui.glass.GlassKind
import com.example.ui.glass.glass
import com.example.ui.glass.glassSource
import com.example.ui.theme.FarSpacing
import com.example.ui.theme.LocalHapticsEnabled
import com.example.ui.theme.MonoFont
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Immutable
data class ViewerItem(
    val id: Long,
    val uri: String,
    val title: String,
    val subtitle: String?,
    val isVideo: Boolean,
    val isFavorite: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    items: List<ViewerItem>,
    startIndex: Int,
    glass: HazeState,
    onClose: () -> Unit,
    onFavorite: (ViewerItem) -> Unit,
    onShare: (ViewerItem) -> Unit,
    onEdit: (ViewerItem) -> Unit,
    onDelete: (ViewerItem) -> Unit,
    detailsContent: @Composable (ViewerItem) -> Unit,
    videoContent: @Composable (ViewerItem) -> Unit,
    onAddToAlbum: ((ViewerItem) -> Unit)? = null,
    onCompare: ((ViewerItem) -> Unit)? = null,
    onOpenWith: ((ViewerItem) -> Unit)? = null,
    imageModifier: @Composable (ViewerItem) -> Modifier = { Modifier },
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        BackHandler(onBack = onClose)
        Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            ErrorCard(
                ErrorInfo(
                    title = stringResource(R.string.viewer_item_unavailable),
                    whatHappened = stringResource(R.string.viewer_item_unavailable_body),
                    actions = listOf(ErrorAction(stringResource(R.string.action_close), true, onClose)),
                )
            )
        }
        return
    }

    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val hapticsOn = LocalHapticsEnabled.current
    var chromeVisible by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var dismissProgress by remember { mutableFloatStateOf(0f) }
    var showDetails by remember { mutableStateOf(false) }
    var slideshowPlaying by remember { mutableStateOf(false) }
    var slideshowIntervalMs by remember { mutableLongStateOf(4000L) }
    var showExifPill by remember { mutableStateOf(false) }
    var quickExif by remember { mutableStateOf<QuickExifInfo?>(null) }
    var showHistogram by remember { mutableStateOf(false) }
    var histogramData by remember { mutableStateOf<HistogramData?>(null) }
    var histogramLoading by remember { mutableStateOf(false) }

    val currentItem = items.getOrNull(pager.currentPage)
    LaunchedEffect(currentItem?.uri, showExifPill) {
        val parsed = currentItem?.uri?.let(::safeParseMediaUri)
        if (showExifPill && currentItem != null && !currentItem.isVideo && parsed != null) {
            quickExif = ExifScrubber.readQuickExif(ctx.contentResolver, parsed)
        } else {
            quickExif = null
        }
    }
    LaunchedEffect(currentItem?.uri, showHistogram) {
        val parsed = currentItem?.uri?.let(::safeParseMediaUri)
        if (showHistogram && currentItem != null && !currentItem.isVideo && parsed != null) {
            histogramLoading = true
            histogramData = HistogramEngine.fromUri(ctx, parsed)
            histogramLoading = false
        } else {
            histogramData = null
            histogramLoading = false
        }
    }

    LaunchedEffect(slideshowPlaying, pager.currentPage, slideshowIntervalMs, items.size) {
        if (slideshowPlaying && items.size > 1 && !zoomed) {
            delay(slideshowIntervalMs)
            val next = (pager.currentPage + 1) % items.size
            pager.animateScrollToPage(next)
        }
    }

    BackHandler {
        when {
            showDetails -> showDetails = false
            slideshowPlaying -> slideshowPlaying = false
            else -> onClose()
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = 1f - dismissProgress * 0.9f))) {
        HorizontalPager(
            state = pager,
            userScrollEnabled = !zoomed,
            beyondViewportPageCount = 1,
            key = { items[it].id },
            modifier = Modifier
                .fillMaxSize()
                .glassSource(glass),
        ) { page ->
            val item = items[page]
            if (item.isVideo) {
                videoContent(item)
            } else {
                ZoomableImage(
                    uri = item.uri,
                    onZoomed = {
                        zoomed = it
                        if (it) slideshowPlaying = false
                    },
                    onDismissProgress = { dismissProgress = it },
                    onDismiss = onClose,
                    onTap = {
                        if (slideshowPlaying) slideshowPlaying = false
                        chromeVisible = !chromeVisible
                    },
                    onOpenWith = onOpenWith?.let { fn -> { fn(item) } },
                    imageModifier = imageModifier(item),
                )
            }
        }

        val current = currentItem
        if (current != null) {
            AnimatedVisibility(
                visible = chromeVisible && dismissProgress == 0f,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = FarSpacing.screenPadding, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .glass(glass, RoundedCornerShape(28.dp), GlassKind.Overlay)
                            .border(0.75.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(28.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(48.dp).testTag("viewer_back_button"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = Color.White)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                current.title,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            current.subtitle?.let {
                                Text(
                                    it,
                                    color = Color.White.copy(0.75f),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (!current.isVideo) {
                            IconButton(
                                onClick = { showHistogram = !showHistogram },
                                modifier = Modifier.size(48.dp).testTag("viewer_histogram_button"),
                            ) {
                                Icon(
                                    Icons.Filled.BarChart,
                                    stringResource(R.string.viewer_histogram),
                                    tint = if (showHistogram) MaterialTheme.colorScheme.primary else Color.White,
                                )
                            }
                            IconButton(
                                onClick = { showExifPill = !showExifPill },
                                modifier = Modifier.size(48.dp).testTag("viewer_exif_pill_button"),
                            ) {
                                Icon(
                                    Icons.Filled.CameraAlt,
                                    "Toggle EXIF overlay",
                                    tint = if (showExifPill) MaterialTheme.colorScheme.primary else Color.White,
                                )
                            }
                        }
                        if (items.size > 1) {
                            IconButton(
                                onClick = {
                                    slideshowPlaying = !slideshowPlaying
                                    if (slideshowPlaying) chromeVisible = false
                                },
                                modifier = Modifier.size(48.dp).testTag("viewer_slideshow_button"),
                            ) {
                                Icon(
                                    if (slideshowPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    if (slideshowPlaying) "Pause slideshow" else "Start slideshow",
                                    tint = if (slideshowPlaying) MaterialTheme.colorScheme.primary else Color.White,
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onFavorite(current)
                            },
                            modifier = Modifier.size(48.dp).testTag("viewer_favorite_button"),
                        ) {
                            Icon(
                                if (current.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                if (current.isFavorite) "Remove from favorites" else "Add to favorites",
                                tint = if (current.isFavorite) Color(0xFFFF375F) else Color.White,
                            )
                        }
                    }

                    if (showExifPill && !current.isVideo) {
                        val exifText = quickExif?.summaryLine() ?: stringResource(R.string.viewer_no_exif)
                        Row(
                            Modifier
                                .glass(glass, RoundedCornerShape(18.dp), GlassKind.Chip)
                                .border(0.75.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(18.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("viewer_exif_pill"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(exifText, color = Color.White, style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFont))
                        }
                    }

                    if (slideshowPlaying) {
                        Row(
                            Modifier
                                .glass(glass, RoundedCornerShape(22.dp), GlassKind.Chip)
                                .border(0.75.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(22.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            listOf(3000L to "3s", 5000L to "5s", 8000L to "8s").forEach { (ms, label) ->
                                TextButton(
                                    onClick = { slideshowIntervalMs = ms },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                                ) {
                                    Text(
                                        label,
                                        color = if (slideshowIntervalMs == ms) MaterialTheme.colorScheme.primary else Color.White,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = chromeVisible && dismissProgress == 0f,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showHistogram && !current.isVideo) {
                        HistogramOverlay(
                            data = histogramData,
                            loading = histogramLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .testTag("viewer_histogram_overlay"),
                        )
                    }
                    if (items.size > 1 && !current.isVideo) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            itemsIndexed(items, key = { _, it -> it.id }) { idx, thumb ->
                                val active = idx == pager.currentPage
                                Box(
                                    Modifier
                                        .size(if (active) 50.dp else 44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .then(
                                            if (active) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp))
                                            else Modifier
                                        )
                                        .clickable { scope.launch { pager.animateScrollToPage(idx) } }
                                ) {
                                    AsyncImage(
                                        model = thumb.uri,
                                        contentDescription = thumb.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = FarSpacing.screenPadding)
                            .glass(glass, RoundedCornerShape(32.dp), GlassKind.Overlay)
                            .border(0.75.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(32.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        IconButton(onClick = { onShare(current) }, modifier = Modifier.size(48.dp).testTag("viewer_share_button")) {
                            Icon(Icons.Filled.Share, stringResource(R.string.action_share), tint = Color.White)
                        }
                        if (!current.isVideo) {
                            IconButton(onClick = { onEdit(current) }, modifier = Modifier.size(48.dp).testTag("viewer_edit_button")) {
                                Icon(Icons.Filled.Edit, stringResource(R.string.action_edit), tint = Color.White)
                            }
                        }
                        if (onCompare != null && !current.isVideo && current.id > 0L) {
                            IconButton(onClick = { onCompare(current) }, modifier = Modifier.size(48.dp).testTag("viewer_compare_button")) {
                                Icon(Icons.Filled.Compare, stringResource(R.string.viewer_compare), tint = Color.White)
                            }
                        }
                        if (onAddToAlbum != null) {
                            IconButton(onClick = { onAddToAlbum(current) }, modifier = Modifier.size(48.dp).testTag("viewer_album_button")) {
                                Icon(Icons.Filled.PhotoAlbum, stringResource(R.string.viewer_add_to_album), tint = Color.White)
                            }
                        }
                        IconButton(onClick = { showDetails = true }, modifier = Modifier.size(48.dp).testTag("viewer_details_button")) {
                            Icon(Icons.Filled.Info, stringResource(R.string.viewer_details), tint = Color.White)
                        }
                        IconButton(onClick = { onDelete(current) }, modifier = Modifier.size(48.dp).testTag("viewer_delete_button")) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.action_delete), tint = Color.White)
                        }
                    }
                }
            }

            if (showDetails) {
                ModalBottomSheet(
                    onDismissRequest = { showDetails = false },
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                        detailsContent(current)
                    }
                }
            }
        }
    }
}

/**
 * Pinch/double-tap zoom, pan when zoomed, swipe-down-to-dismiss at 1x.
 * Horizontal drags are NOT consumed at 1x so the pager keeps working.
 */
@Composable
fun ZoomableImage(
    uri: String,
    onZoomed: (Boolean) -> Unit,
    onDismissProgress: (Float) -> Unit,
    onDismiss: () -> Unit,
    onTap: () -> Unit,
    onOpenWith: (() -> Unit)? = null,
    imageModifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var retryNonce by remember(uri) { mutableIntStateOf(0) }
    var scale by remember(uri) { mutableFloatStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }
    var dragY by remember(uri) { mutableFloatStateOf(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var failed by remember(uri, retryNonce) { mutableStateOf(false) }
    val slop = LocalViewConfiguration.current.touchSlop

    fun clampOffset(o: Offset, s: Float): Offset {
        val maxX = (size.width * (s - 1f)) / 2f
        val maxY = (size.height * (s - 1f)) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(uri) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                        onZoomed(scale > 1f)
                    },
                )
            }
            .pointerInput(uri) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var totalDy = 0f
                    var totalDx = 0f
                    do {
                        val event = awaitPointerEvent()
                        val multi = event.changes.count { it.pressed } > 1
                        if (multi || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 6f)
                            offset = clampOffset(offset + event.calculatePan(), scale)
                            onZoomed(scale > 1f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else {
                            val pan = event.calculatePan()
                            totalDx += pan.x
                            totalDy += pan.y
                            if (totalDy > slop && abs(totalDy) > abs(totalDx) * 1.5f) {
                                dragY = totalDy
                                onDismissProgress((dragY / (size.height.coerceAtLeast(1) * 0.4f)).coerceIn(0f, 1f))
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    if (dragY > size.height * 0.18f) onDismiss()
                    dragY = 0f
                    onDismissProgress(0f)
                    if (scale < 1.02f) {
                        scale = 1f
                        offset = Offset.Zero
                        onZoomed(false)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (failed) {
            val actions = buildList {
                add(ErrorAction(stringResource(R.string.action_retry), true) {
                    failed = false
                    retryNonce++
                })
                onOpenWith?.let { add(ErrorAction(stringResource(R.string.viewer_open_with), false, it)) }
                add(ErrorAction(stringResource(R.string.action_close), false, onDismiss))
            }
            ErrorCard(
                ErrorInfo(
                    title = stringResource(R.string.viewer_preview_unavailable),
                    whatHappened = stringResource(R.string.viewer_preview_unavailable_body),
                    code = "FG-COR",
                    actions = actions,
                ),
            )
        } else {
            val dragScale = 1f - (dragY / (size.height.coerceAtLeast(1) * 0.4f)).coerceIn(0f, 1f) * 0.15f
            SubcomposeAsyncImage(
                model = "$uri#$retryNonce",
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onError = {
                    failed = true
                    LocalDiagnostics.recordEvent(ctx, "FG-COR", "viewer_decode", it.result.throwable.javaClass.simpleName)
                },
                modifier = imageModifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale * dragScale
                        scaleY = scale * dragScale
                        translationX = offset.x
                        translationY = offset.y + dragY
                    },
                loading = { Box(Modifier.size(48.dp)) },
                error = { Icon(Icons.Filled.BrokenImage, null, tint = Color.White.copy(0.6f)) },
            )
        }
    }
}
