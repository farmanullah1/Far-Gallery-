package com.example.feature.viewer

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.example.R
import com.example.core.common.LocalDiagnostics
import com.example.core.media.MediaRow
import com.example.ui.components.FarFilterPill
import com.example.ui.feedback.ErrorAction
import com.example.ui.feedback.ErrorCard
import com.example.ui.feedback.ErrorInfo
import com.example.ui.glass.GlassKind
import com.example.ui.glass.glass
import com.example.ui.glass.glassSource
import com.example.ui.theme.FarSpacing
import com.example.ui.theme.MonoFont
import dev.chrisbanes.haze.HazeState

@Composable
fun CompareScreen(
    left: MediaRow?,
    right: MediaRow?,
    glass: HazeState,
    onTrash: (MediaRow) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    var swapped by remember { mutableStateOf(false) }
    val paneA = if (swapped) right else left
    val paneB = if (swapped) left else right

    if (paneA == null || paneB == null) {
        Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            ErrorCard(
                ErrorInfo(
                    title = stringResource(R.string.viewer_item_unavailable),
                    whatHappened = stringResource(R.string.viewer_item_unavailable_body),
                    actions = listOf(ErrorAction(stringResource(R.string.action_close), true, onBack)),
                ),
            )
        }
        return
    }

    var syncZoom by remember { mutableStateOf(true) }
    var forceSideBySide by remember { mutableStateOf<Boolean?>(null) }

    var sharedScale by remember { mutableFloatStateOf(1f) }
    var sharedOffset by remember { mutableStateOf(Offset.Zero) }

    var leftScale by remember { mutableFloatStateOf(1f) }
    var leftOffset by remember { mutableStateOf(Offset.Zero) }
    var rightScale by remember { mutableFloatStateOf(1f) }
    var rightOffset by remember { mutableStateOf(Offset.Zero) }

    val ctx = LocalContext.current

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val sideBySide = forceSideBySide ?: (maxWidth >= 560.dp)

        Box(Modifier.fillMaxSize().glassSource(glass)) {
            if (sideBySide) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(top = 112.dp, bottom = 128.dp, start = 6.dp, end = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ComparePane(
                        row = paneA,
                        badge = "A",
                        scale = if (syncZoom) sharedScale else leftScale,
                        offset = if (syncZoom) sharedOffset else leftOffset,
                        onTransform = { s, o ->
                            if (syncZoom) {
                                sharedScale = s
                                sharedOffset = o
                            } else {
                                leftScale = s
                                leftOffset = o
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    ComparePane(
                        row = paneB,
                        badge = "B",
                        scale = if (syncZoom) sharedScale else rightScale,
                        offset = if (syncZoom) sharedOffset else rightOffset,
                        onTransform = { s, o ->
                            if (syncZoom) {
                                sharedScale = s
                                sharedOffset = o
                            } else {
                                rightScale = s
                                rightOffset = o
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(top = 112.dp, bottom = 128.dp, start = 6.dp, end = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ComparePane(
                        row = paneA,
                        badge = "A",
                        scale = if (syncZoom) sharedScale else leftScale,
                        offset = if (syncZoom) sharedOffset else leftOffset,
                        onTransform = { s, o ->
                            if (syncZoom) {
                                sharedScale = s
                                sharedOffset = o
                            } else {
                                leftScale = s
                                leftOffset = o
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    ComparePane(
                        row = paneB,
                        badge = "B",
                        scale = if (syncZoom) sharedScale else rightScale,
                        offset = if (syncZoom) sharedOffset else rightOffset,
                        onTransform = { s, o ->
                            if (syncZoom) {
                                sharedScale = s
                                sharedOffset = o
                            } else {
                                rightScale = s
                                rightOffset = o
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        }

        // Top toolbar
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = FarSpacing.screenPadding, vertical = 10.dp)
                .glass(glass, RoundedCornerShape(26.dp), GlassKind.Overlay)
                .border(0.75.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(26.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp).testTag("compare_back_button"),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = Color.White)
            }
            Text(
                stringResource(R.string.compare_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            FarFilterPill(
                label = stringResource(if (syncZoom) R.string.compare_sync_on else R.string.compare_sync_off),
                selected = syncZoom,
                onClick = { syncZoom = !syncZoom },
                leadingIcon = if (syncZoom) Icons.Filled.Link else Icons.Filled.LinkOff,
                onDarkSurface = true,
                modifier = Modifier.testTag("compare_sync_chip"),
            )
            IconButton(
                onClick = { swapped = !swapped },
                modifier = Modifier.size(48.dp).testTag("compare_swap_button"),
            ) {
                Icon(Icons.Filled.SwapHoriz, "Swap photos", tint = Color.White)
            }
            IconButton(
                onClick = { forceSideBySide = !sideBySide },
                modifier = Modifier.size(48.dp).testTag("compare_layout_button"),
            ) {
                Icon(
                    if (sideBySide) Icons.Filled.ViewAgenda else Icons.Filled.ViewColumn,
                    "Toggle split orientation",
                    tint = Color.White,
                )
            }
        }

        // Bottom info & Keep/Trash decision bar
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = FarSpacing.screenPadding, vertical = 12.dp)
                .glass(glass, RoundedCornerShape(26.dp), GlassKind.Overlay)
                .border(0.75.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(26.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompareMetaPill("A", paneA, ctx, Modifier.weight(1f))
                CompareMetaPill("B", paneB, ctx, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { onTrash(paneB) },
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("compare_keep_left"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) {
                    Icon(Icons.Filled.DeleteOutline, null, Modifier.size(16.dp))
                    Text(" ${stringResource(R.string.compare_keep_left)}", maxLines = 1)
                }
                OutlinedButton(
                    onClick = { onTrash(paneA) },
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("compare_keep_right"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) {
                    Icon(Icons.Filled.DeleteOutline, null, Modifier.size(16.dp))
                    Text(" ${stringResource(R.string.compare_keep_right)}", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun CompareMetaPill(
    tag: String,
    row: MediaRow,
    ctx: android.content.Context,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            "$tag: ${row.name}",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            buildString {
                if (row.width > 0 && row.height > 0) append("${row.width}×${row.height} · ")
                append(Formatter.formatShortFileSize(ctx, row.sizeBytes))
            },
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFont),
            maxLines = 1,
        )
    }
}

@Composable
private fun ComparePane(
    row: MediaRow,
    badge: String,
    scale: Float,
    offset: Offset,
    onTransform: (Float, Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var retryNonce by remember(row.uri) { mutableIntStateOf(0) }
    var failed by remember(row.uri, retryNonce) { mutableStateOf(false) }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = (boxSize.width * (s - 1f)) / 2f
        val maxY = (boxSize.height * (s - 1f)) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF101014))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
            .onSizeChanged { boxSize = it }
            .pointerInput(row.uri) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.05f) onTransform(1f, Offset.Zero)
                        else onTransform(2.5f, Offset.Zero)
                    },
                )
            }
            .pointerInput(row.uri, scale, offset) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var curScale = scale
                    var curOffset = offset
                    do {
                        val event = awaitPointerEvent()
                        val multi = event.changes.count { it.pressed } > 1
                        if (multi || curScale > 1f) {
                            curScale = (curScale * event.calculateZoom()).coerceIn(1f, 6f)
                            curOffset = clamp(curOffset + event.calculatePan(), curScale)
                            onTransform(curScale, curOffset)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (curScale < 1.02f) onTransform(1f, Offset.Zero)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (failed) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.BrokenImage, null, tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(32.dp))
                Text(stringResource(R.string.viewer_preview_unavailable), color = Color.White, style = MaterialTheme.typography.labelMedium)
                TextButton(
                    onClick = { failed = false; retryNonce++ },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) {
                    Text(stringResource(R.string.action_retry), color = Color.White)
                }
            }
        } else {
            SubcomposeAsyncImage(
                model = "${row.uri}#$retryNonce",
                contentDescription = row.name,
                contentScale = ContentScale.Fit,
                onError = { err ->
                    failed = true
                    LocalDiagnostics.recordEvent(ctx, "FG-CMP-DEC", "compare_pane", err.result.throwable.javaClass.simpleName)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
        }
        Text(
            badge,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
