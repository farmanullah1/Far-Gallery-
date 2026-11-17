package com.example.feature.camera

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPoint
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.feedback.ErrorAction
import com.example.ui.feedback.ErrorCard
import com.example.ui.feedback.ErrorInfo
import com.example.ui.theme.FarPalette
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

enum class CameraMode(val label: String) {
    SloMo("SLO-MO"),
    Video("VIDEO"),
    Photo("PHOTO"),
    Square("SQUARE"),
    Portrait("PORTRAIT"),
}

enum class FlashMode { Off, Auto, On }

/** Everything shown is derived from REAL capabilities queried from CameraInfo; nothing is assumed. */
@Immutable
data class CameraCapabilities(
    val lensZoomStops: List<Float>,
    val minZoom: Float,
    val maxZoom: Float,
    val hasFlash: Boolean,
    val exposureRange: ClosedFloatingPointRange<Float>,
    val modes: List<CameraMode>,
    val hasFrontCamera: Boolean,
)

@Immutable
data class CameraUiState(
    val mode: CameraMode = CameraMode.Photo,
    val zoom: Float = 1f,
    val flash: FlashMode = FlashMode.Off,
    val recording: Boolean = false,
    val recordingSeconds: Int = 0,
    val timerSeconds: Int = 0,
    val countdown: Int? = null,
    val exposureEv: Float = 0f,
    val lastCaptureUri: String? = null,
    val gridOn: Boolean = false,
    val qrEnabled: Boolean = false,
    val qrResult: String? = null,
    val permissionGranted: Boolean = true,
    val error: ErrorInfo? = null,
)

@Composable
fun CameraScreen(
    controller: LifecycleCameraController,
    caps: CameraCapabilities,
    state: CameraUiState,
    onMode: (CameraMode) -> Unit,
    onZoom: (Float) -> Unit,
    onFlash: (FlashMode) -> Unit,
    onExposure: (Float) -> Unit,
    onCycleTimer: () -> Unit,
    onToggleGrid: () -> Unit,
    onToggleQr: () -> Unit,
    onDismissQr: () -> Unit,
    onShutter: () -> Unit,
    onFlip: () -> Unit,
    onOpenLast: () -> Unit,
    onClose: () -> Unit,
    onRequestPermission: () -> Unit,
    lastThumbnail: @Composable (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.permissionGranted) {
        Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            ErrorCard(
                ErrorInfo(
                    title = "Camera access needed",
                    whatHappened = "Allow camera access to take photos and videos. The rest of Far Gallery keeps working without it.",
                    originalsSafe = false,
                    actions = listOf(
                        ErrorAction("Allow camera", true, onRequestPermission),
                        ErrorAction("Close", false, onClose),
                    ),
                )
            )
        }
        return
    }

    var expanded by remember { mutableStateOf(false) }
    var showEvSlider by remember { mutableStateOf(false) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var flipTurns by remember { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    val previewView = remember { mutableStateOf<PreviewView?>(null) }
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Box(modifier.fillMaxSize().background(Color.Black)) {
        val viewfinder = if (state.mode == CameraMode.Square) {
            Modifier.align(Alignment.TopCenter).padding(top = 96.dp).fillMaxWidth().aspectRatio(1f)
        } else {
            Modifier.fillMaxSize().padding(top = 96.dp, bottom = 230.dp)
        }

        CameraPreview(
            controller = controller,
            onPreviewView = { previewView.value = it },
            modifier = viewfinder
                .clip(RoundedCornerShape(20.dp))
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        onZoom((state.zoom * zoom).coerceIn(caps.minZoom, caps.maxZoom))
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        focusPoint = pos
                        previewView.value?.let { pv ->
                            val p: MeteringPoint = pv.meteringPointFactory.createPoint(pos.x, pos.y)
                            controller.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(p).build())
                        }
                    }
                },
        )
        if (state.gridOn) {
            Box(viewfinder) {
                GridOverlay()
                LevelIndicator()
            }
        }
        FocusIndicator(focusPoint, state.exposureEv, caps.exposureRange, onExposure) { focusPoint = null }

        // -------- Top bar --------
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircleIcon(Icons.Filled.Close, "Close camera", onClick = onClose)
                    if (caps.hasFlash) {
                        CircleIcon(
                            icon = when (state.flash) {
                                FlashMode.Off -> Icons.Filled.FlashOff
                                FlashMode.Auto -> Icons.Filled.FlashAuto
                                FlashMode.On -> Icons.Filled.FlashOn
                            },
                            desc = "Flash ${state.flash.name}",
                            tint = if (state.flash == FlashMode.On) FarPalette.CameraYellow else Color.White,
                        ) { onFlash(FlashMode.entries[(state.flash.ordinal + 1) % 3]) }
                    }
                }

                if (state.recording) RecordTimerPill(state.recordingSeconds)

                CircleIcon(
                    icon = Icons.Filled.KeyboardArrowUp,
                    desc = if (expanded) "Hide camera options" else "Show camera options",
                    modifier = Modifier.rotate(if (expanded) 180f else 0f).testTag("camera_options_toggle"),
                ) { expanded = !expanded }
            }

            AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(Color.White.copy(0.14f), RoundedCornerShape(22.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        OptionChip(
                            label = if (state.timerSeconds == 0) "Timer Off" else "${state.timerSeconds}s",
                            active = state.timerSeconds > 0,
                            onClick = onCycleTimer,
                            icon = Icons.Filled.Timer,
                        )
                        OptionChip(
                            label = if (state.mode == CameraMode.Square) "1:1" else "4:3",
                            active = state.mode == CameraMode.Square,
                            onClick = {
                                onMode(if (state.mode == CameraMode.Square) CameraMode.Photo else CameraMode.Square)
                            },
                        )
                        OptionChip(
                            label = "EV ${"%.1f".format(state.exposureEv)}",
                            active = showEvSlider || state.exposureEv != 0f,
                            onClick = { showEvSlider = !showEvSlider },
                        )
                        OptionChip(
                            label = "Grid",
                            active = state.gridOn,
                            onClick = onToggleGrid,
                            icon = Icons.Filled.GridOn,
                        )
                        OptionChip(
                            label = "QR",
                            active = state.qrEnabled,
                            onClick = onToggleQr,
                            icon = Icons.Filled.QrCodeScanner,
                        )
                    }
                    if (showEvSlider && caps.exposureRange.endInclusive > caps.exposureRange.start) {
                        Slider(
                            value = state.exposureEv,
                            onValueChange = onExposure,
                            valueRange = caps.exposureRange,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
            }

            state.qrResult?.let { payload ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(Color.Black.copy(0.75f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(payload, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    TextButton(onClick = { clipboard.setText(AnnotatedString(payload)); onDismissQr() }) {
                        Text("Copy", color = FarPalette.CameraYellow)
                    }
                    if (payload.startsWith("http://") || payload.startsWith("https://")) {
                        TextButton(onClick = {
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(payload))) }
                            onDismissQr()
                        }) { Text("Open", color = FarPalette.CameraYellow) }
                    }
                    TextButton(onClick = onDismissQr) { Text("Dismiss", color = Color.White) }
                }
            }
        }

        // -------- Countdown overlay --------
        state.countdown?.let {
            Text("$it", color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Light, modifier = Modifier.align(Alignment.Center))
        }

        // -------- Bottom controls --------
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ZoomChips(caps.lensZoomStops, state.zoom) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onZoom(it)
            }
            if (!state.recording) {
                ModeCarousel(caps.modes, state.mode) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onMode(it)
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(0.15f))
                        .clickable(enabled = state.lastCaptureUri != null, onClick = onOpenLast)
                        .semantics { contentDescription = "Open last capture" },
                ) { state.lastCaptureUri?.let { lastThumbnail(it) } }

                Shutter(state.mode, state.recording) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onShutter()
                }

                val turn by animateFloatAsState(flipTurns, label = "flip")
                if (caps.hasFrontCamera) {
                    CircleIcon(
                        icon = Icons.Filled.FlipCameraAndroid,
                        desc = "Switch camera",
                        size = 52.dp,
                        modifier = Modifier.rotate(turn),
                        background = Color.White.copy(0.15f),
                    ) { flipTurns += 180f; onFlip() }
                } else {
                    Box(Modifier.size(52.dp))
                }
            }
        }

        state.error?.let { ErrorCard(it, Modifier.align(Alignment.Center)) }
    }
}

@Composable
private fun OptionChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) FarPalette.CameraYellow.copy(0.24f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(14.dp), tint = if (active) FarPalette.CameraYellow else Color.White)
        }
        Text(
            label,
            color = if (active) FarPalette.CameraYellow else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CameraPreview(
    controller: LifecycleCameraController,
    onPreviewView: (PreviewView) -> Unit,
    modifier: Modifier,
) {
    val owner = LocalLifecycleOwner.current
    AndroidView(
        modifier = modifier,
        factory = { ctx: Context ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                this.controller = controller
                controller.bindToLifecycle(owner)
                onPreviewView(this)
            }
        },
    )
}

@Composable
private fun ZoomChips(stops: List<Float>, current: Float, onPick: (Float) -> Unit) {
    if (stops.isEmpty()) return
    Row(Modifier.background(Color.Black.copy(0.35f), CircleShape).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        stops.forEach { z ->
            val selected = kotlin.math.abs(current - z) < 0.15f
            val size by animateDpAsState(if (selected) 40.dp else 32.dp, label = "chip")
            Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(Color.Black.copy(0.4f))
                    .clickable { onPick(z) }
                    .semantics { role = Role.Button; contentDescription = "Zoom ${z}x" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (selected) "${trim(current)}×" else trim(z),
                    color = if (selected) FarPalette.CameraYellow else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun trim(v: Float) = if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(v)

@Composable
private fun ModeCarousel(modes: List<CameraMode>, current: CameraMode, onPick: (CameraMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        modes.forEach { m ->
            val c by animateColorAsState(if (m == current) FarPalette.CameraYellow else Color.White, label = "mode")
            Text(
                m.label,
                color = c,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { onPick(m) }
                    .padding(vertical = 6.dp)
                    .testTag("camera_mode_${m.name.lowercase()}"),
            )
        }
    }
}

@Composable
private fun Shutter(mode: CameraMode, recording: Boolean, onClick: () -> Unit) {
    val isVideo = mode == CameraMode.Video || mode == CameraMode.SloMo
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(if (pressed) 0.9f else 1f, label = "press")
    val innerColor by animateColorAsState(if (isVideo) FarPalette.RecordRed else Color.White, label = "inner")
    val innerSize by animateDpAsState(if (recording) 30.dp else 60.dp, label = "innerSize")
    val corner by animateDpAsState(if (recording) 8.dp else 30.dp, label = "corner")
    Box(
        Modifier
            .size(76.dp)
            .border(BorderStroke(4.dp, Color.White), CircleShape)
            .testTag("camera_shutter_button")
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { onClick() },
                )
            }
            .semantics {
                role = Role.Button
                contentDescription = if (isVideo) (if (recording) "Stop recording" else "Start recording") else "Take photo"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(innerSize).scale(press).background(innerColor, RoundedCornerShape(corner)))
    }
}

@Composable
private fun RecordTimerPill(seconds: Int) {
    Row(
        Modifier
            .background(FarPalette.RecordRed, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("%d:%02d".format(seconds / 60, seconds % 60), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CircleIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    size: Dp = 44.dp,
    tint: Color = Color.White,
    background: Color = Color.Black.copy(0.35f),
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button; contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint)
    }
}

/** Yellow focus square + vertical exposure drag that fades after 1.5s. */
@Composable
private fun FocusIndicator(
    point: Offset?,
    ev: Float,
    range: ClosedFloatingPointRange<Float>,
    onEv: (Float) -> Unit,
    onGone: () -> Unit,
) {
    if (point == null) return
    val s = remember(point) { Animatable(1.4f) }
    LaunchedEffect(point) { s.animateTo(1f); delay(1800); onGone() }
    val density = LocalDensity.current
    val half = with(density) { 36.dp.toPx() }
    val top = with(density) { 96.dp.toPx() }
    Box(
        Modifier
            .offset { IntOffset((point.x - half).roundToInt(), (point.y - half + top).roundToInt()) }
            .size(72.dp)
            .pointerInput(range) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (range.endInclusive > range.start) {
                        val next = (ev - dragAmount * 0.02f).coerceIn(range.start, range.endInclusive)
                        onEv(next)
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize().scale(s.value)) {
            drawRect(FarPalette.CameraYellow, style = Stroke(2.dp.toPx()))
        }
    }
}
