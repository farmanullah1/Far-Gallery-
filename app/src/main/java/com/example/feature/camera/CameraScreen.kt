package com.example.feature.camera

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.app.copyPlainTextToClipboard
import com.example.core.common.LocalDiagnostics
import com.example.core.common.safeParseMediaUri
import com.example.ui.feedback.ErrorAction
import com.example.ui.feedback.ErrorCard
import com.example.ui.feedback.ErrorInfo
import com.example.ui.theme.FarPalette
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

enum class CameraMode(val label: String, val labelRes: Int) {
    Video("VIDEO", R.string.camera_mode_video),
    Photo("PHOTO", R.string.camera_mode_photo),
    Square("SQUARE", R.string.camera_mode_square),
    Scan("SCAN", R.string.camera_mode_scan),
}

enum class FlashMode { Off, Auto, On }

/** Everything shown is derived from REAL capabilities queried from CameraInfo & Camera2; nothing is assumed. */
@Immutable
data class CameraCapabilities(
    val lensZoomStops: List<Float>,
    val minZoom: Float,
    val maxZoom: Float,
    val hasFlash: Boolean,
    val exposureRange: ClosedFloatingPointRange<Float>,
    val modes: List<CameraMode>,
    val hasFrontCamera: Boolean,
    val hardwareLevelLabel: String = "Standard",
    val supportsRaw: Boolean = false,
    val maxFps: Int = 30,
    val hasOpticalStabilization: Boolean = false,
    val focalLengthsMm: List<Float> = emptyList(),
)

internal fun formatHardwareSummary(caps: CameraCapabilities): String = buildList {
    add("HW: ${caps.hardwareLevelLabel}")
    add("${caps.maxFps}fps")
    caps.focalLengthsMm.firstOrNull()?.let { add("${"%.1f".format(java.util.Locale.US, it)}mm") }
    if (caps.hasOpticalStabilization) add("OIS")
    add(if (caps.supportsRaw) "RAW supported" else "JPEG")
}.joinToString(" · ")

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
    val aeAfLocked: Boolean = false,
    val lastCaptureUri: String? = null,
    val lastCaptureWasScan: Boolean = false,
    val gridOn: Boolean = false,
    val levelEnabled: Boolean = false,
    val burstMode: Boolean = false,
    val burstCount: Int? = null,
    val qrEnabled: Boolean = false,
    val qrResult: String? = null,
    val permissionGranted: Boolean = true,
    val micPermissionGranted: Boolean = true,
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
    onToggleLevel: () -> Unit = {},
    onToggleBurst: () -> Unit = {},
    onFocusPoint: (MeteringPoint?, Boolean) -> Unit = { _, _ -> },
    onClearFocusLock: () -> Unit = {},
    onEditLastScan: ((String) -> Unit)? = null,
    onExportLastScanPdf: ((String) -> Unit)? = null,
    scanToast: String? = null,
    onDismissScanToast: () -> Unit = {},
) {
    if (!state.permissionGranted) {
        Box(modifier.fillMaxSize().background(Color.Black)) {
            CircleIcon(
                icon = Icons.Filled.Close,
                desc = stringResource(R.string.cd_close_camera),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .testTag("camera_close_button"),
                onClick = onClose,
            )
            ErrorCard(
                ErrorInfo(
                    title = stringResource(R.string.camera_perm_title),
                    whatHappened = stringResource(R.string.camera_perm_body),
                    originalsSafe = false,
                    actions = listOf(
                        ErrorAction(stringResource(R.string.camera_perm_allow), true, onRequestPermission),
                        ErrorAction(stringResource(R.string.action_close), false, onClose),
                    ),
                ),
                modifier = Modifier.align(Alignment.Center),
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
                .pointerInput(caps.minZoom, caps.maxZoom, state.zoom) {
                    detectTransformGestures { _, _, zoom, _ ->
                        onZoom((state.zoom * zoom).coerceIn(caps.minZoom, caps.maxZoom))
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { pos ->
                            focusPoint = pos
                            val pt = previewView.value?.meteringPointFactory?.createPoint(pos.x, pos.y)
                            onFocusPoint(pt, false)
                        },
                        onLongPress = { pos ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            focusPoint = pos
                            val pt = previewView.value?.meteringPointFactory?.createPoint(pos.x, pos.y)
                            onFocusPoint(pt, true)
                        },
                    )
                },
        )
        if (state.gridOn || state.levelEnabled || state.mode == CameraMode.Scan) {
            Box(viewfinder) {
                if (state.gridOn) GridOverlay()
                if (state.levelEnabled) LevelIndicator()
                if (state.mode == CameraMode.Scan) ScanFrameOverlay()
            }
        }
        FocusIndicator(
            point = focusPoint,
            ev = state.exposureEv,
            locked = state.aeAfLocked,
            range = caps.exposureRange,
            onEv = onExposure,
        ) {
            if (!state.aeAfLocked) focusPoint = null
        }

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
                    CircleIcon(
                        icon = Icons.Filled.Close,
                        desc = stringResource(R.string.cd_close_camera),
                        modifier = Modifier.testTag("camera_close_button"),
                        onClick = onClose,
                    )
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

                when {
                    state.recording -> RecordTimerPill(state.recordingSeconds)
                    state.burstCount != null -> Text(
                        text = stringResource(R.string.camera_burst_counter, state.burstCount),
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(FarPalette.CameraYellow, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                            .testTag("camera_burst_counter_pill"),
                    )
                    state.aeAfLocked -> Row(
                        Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(FarPalette.CameraYellow.copy(alpha = 0.92f))
                            .clickable {
                                focusPoint = null
                                onClearFocusLock()
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("camera_ae_af_lock_pill"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Filled.Lock, null, Modifier.size(14.dp), tint = Color.Black)
                        Text(
                            stringResource(R.string.camera_ae_af_locked),
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    state.mode == CameraMode.Video && !state.micPermissionGranted -> Row(
                        Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.68f))
                            .border(0.75.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                            .clickable(onClick = onRequestPermission)
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                            .testTag("camera_mic_muted_pill"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Filled.MicOff, null, Modifier.size(14.dp), tint = FarPalette.CameraYellow)
                        Text(
                            stringResource(R.string.camera_mic_muted),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                CircleIcon(
                    icon = Icons.Filled.KeyboardArrowUp,
                    desc = stringResource(if (expanded) R.string.cd_camera_options_hide else R.string.cd_camera_options_show),
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
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OptionChip(
                            label = if (state.timerSeconds == 0) stringResource(R.string.camera_timer_off)
                            else stringResource(R.string.camera_timer_sec, state.timerSeconds),
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
                            label = "EV ${"%+.1f".format(state.exposureEv)}",
                            active = showEvSlider || state.exposureEv != 0f,
                            onClick = { showEvSlider = !showEvSlider },
                        )
                        OptionChip(
                            label = stringResource(R.string.camera_grid),
                            active = state.gridOn,
                            onClick = onToggleGrid,
                            icon = Icons.Filled.GridOn,
                        )
                        OptionChip(
                            label = stringResource(R.string.camera_level),
                            active = state.levelEnabled,
                            onClick = onToggleLevel,
                            icon = Icons.Filled.ScreenRotation,
                            modifier = Modifier.testTag("camera_level_chip"),
                        )
                        if (state.mode == CameraMode.Photo || state.mode == CameraMode.Square) {
                            OptionChip(
                                label = stringResource(R.string.camera_burst),
                                active = state.burstMode,
                                onClick = onToggleBurst,
                                icon = Icons.Filled.BurstMode,
                                modifier = Modifier.testTag("camera_burst_chip"),
                            )
                        }
                        OptionChip(
                            label = stringResource(R.string.camera_qr),
                            active = state.qrEnabled,
                            onClick = onToggleQr,
                            icon = Icons.Filled.QrCodeScanner,
                        )
                    }
                    if (showEvSlider && caps.exposureRange.endInclusive > caps.exposureRange.start) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Slider(
                                value = state.exposureEv,
                                onValueChange = { next ->
                                    if ((state.exposureEv < 0f && next >= 0f) || (state.exposureEv > 0f && next <= 0f)) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    onExposure(next)
                                },
                                valueRange = caps.exposureRange,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("camera_ev_slider"),
                            )
                            if (state.exposureEv != 0f) {
                                TextButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onExposure(0f)
                                    },
                                    modifier = Modifier.testTag("camera_ev_reset_button"),
                                ) {
                                    Text("0.0 EV", color = FarPalette.CameraYellow)
                                }
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.camera_hw_summary_label, formatHardwareSummary(caps)),
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("camera_hw_summary"),
                    )
                }
            }

            state.qrResult?.let { payload ->
                QrActionCard(
                    payload = payload,
                    onCopyText = { value ->
                        copyPlainTextToClipboard(ctx, "QR", value)
                        onDismissQr()
                    },
                    onOpenUri = { target ->
                        val parsed = safeParseMediaUri(target)
                        if (parsed != null) {
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, parsed)) }
                                .onFailure { err -> LocalDiagnostics.recordEvent(ctx, "FG-CAM-QR", "camera_qr", err.javaClass.simpleName) }
                        }
                        onDismissQr()
                    },
                    onDialNumber = { number ->
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:$number"))) }
                            .onFailure { err -> LocalDiagnostics.recordEvent(ctx, "FG-CAM-QR", "camera_qr", err.javaClass.simpleName) }
                        onDismissQr()
                    },
                    onSendEmail = { mailtoUri ->
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, android.net.Uri.parse(mailtoUri))) }
                            .onFailure { err -> LocalDiagnostics.recordEvent(ctx, "FG-CAM-QR", "camera_qr", err.javaClass.simpleName) }
                        onDismissQr()
                    },
                    onDismiss = onDismissQr,
                    modifier = Modifier.padding(top = 8.dp),
                )
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            scanToast?.let { msg ->
                Row(
                    Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black.copy(alpha = 0.84f))
                        .border(0.75.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
                        .clickable(onClick = onDismissScanToast)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("camera_scan_toast"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        msg,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            if (state.lastCaptureWasScan && state.lastCaptureUri != null && (onEditLastScan != null || onExportLastScanPdf != null)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onEditLastScan != null) {
                        Row(
                            Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(18.dp))
                                .background(FarPalette.CameraYellow.copy(alpha = 0.92f))
                                .clickable { onEditLastScan(state.lastCaptureUri) }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .testTag("camera_edit_scan_pill"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Filled.CropFree, null, Modifier.size(16.dp), tint = Color.Black)
                            Text(
                                stringResource(R.string.camera_scan_edit),
                                color = Color.Black,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    if (onExportLastScanPdf != null) {
                        Row(
                            Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.Black.copy(alpha = 0.78f))
                                .border(0.75.dp, FarPalette.CameraYellow.copy(alpha = 0.75f), RoundedCornerShape(18.dp))
                                .clickable { onExportLastScanPdf(state.lastCaptureUri) }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .testTag("camera_scan_pdf_pill"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, null, Modifier.size(16.dp), tint = FarPalette.CameraYellow)
                            Text(
                                stringResource(R.string.viewer_export_pdf),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

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
                val lastDesc = stringResource(R.string.cd_open_last_capture)
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(0.15f))
                        .clickable(enabled = state.lastCaptureUri != null, onClick = onOpenLast)
                        .semantics { contentDescription = lastDesc },
                ) { state.lastCaptureUri?.let { lastThumbnail(it) } }

                Shutter(state.mode, state.recording) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onShutter()
                }

                val turn by animateFloatAsState(flipTurns, label = "flip")
                if (caps.hasFrontCamera) {
                    CircleIcon(
                        icon = Icons.Filled.FlipCameraAndroid,
                        desc = stringResource(R.string.cd_switch_camera),
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
private fun QrActionCard(
    payload: String,
    onCopyText: (String) -> Unit,
    onOpenUri: (String) -> Unit,
    onDialNumber: (String) -> Unit,
    onSendEmail: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val parsed = remember(payload) { QrParser.parse(payload) }
    val (icon, titleRes, detail) = when (parsed) {
        is QrPayload.Url -> Triple(Icons.Filled.Link, R.string.camera_qr_url, parsed.host)
        is QrPayload.Wifi -> Triple(Icons.Filled.Wifi, R.string.camera_qr_wifi, "${parsed.ssid} (${parsed.security})")
        is QrPayload.Phone -> Triple(Icons.Filled.Phone, R.string.camera_qr_phone, parsed.number)
        is QrPayload.Email -> Triple(Icons.Filled.Email, R.string.camera_qr_email, parsed.address)
        is QrPayload.Contact -> Triple(
            Icons.Filled.ContactPage,
            R.string.camera_qr_contact,
            listOfNotNull(parsed.name, parsed.org, parsed.phone ?: parsed.email).joinToString(" · "),
        )
        is QrPayload.PlainText -> Triple(Icons.Filled.QrCodeScanner, R.string.camera_qr_text, parsed.raw)
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(Color.Black.copy(0.82f), RoundedCornerShape(20.dp))
            .border(0.75.dp, Color.White.copy(0.22f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("camera_qr_action_card"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = FarPalette.CameraYellow)
            Text(
                stringResource(titleRes),
                color = FarPalette.CameraYellow,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            detail,
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.End,
        ) {
            when (parsed) {
                is QrPayload.Url -> {
                    TextButton(onClick = { onOpenUri(parsed.raw) }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                        Text(stringResource(R.string.action_open), color = FarPalette.CameraYellow)
                    }
                    TextButton(onClick = { onCopyText(parsed.raw) }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                        Text(stringResource(R.string.action_copy), color = Color.White)
                    }
                }
                is QrPayload.Wifi -> {
                    if (parsed.password.isNotEmpty()) {
                        TextButton(onClick = { onCopyText(parsed.password) }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                            Text(stringResource(R.string.camera_qr_copy_password), color = FarPalette.CameraYellow)
                        }
                    } else {
                        TextButton(onClick = { onCopyText(parsed.ssid) }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                            Text(stringResource(R.string.action_copy), color = FarPalette.CameraYellow)
                        }
                    }
                }
                is QrPayload.Phone -> {
                    TextButton(onClick = { onDialNumber(parsed.number) }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                        Text(stringResource(R.string.camera_qr_dial), color = FarPalette.CameraYellow)
                    }
                    TextButton(onClick = { onCopyText(parsed.number) }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                        Text(stringResource(R.string.action_copy), color = Color.White)
                    }
                }
                is QrPayload.Email -> {
                    TextButton(onClick = { onSendEmail(parsed.raw) }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                        Text(stringResource(R.string.camera_qr_email_action), color = FarPalette.CameraYellow)
                    }
                    TextButton(onClick = { onCopyText(parsed.address) }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                        Text(stringResource(R.string.action_copy), color = Color.White)
                    }
                }
                is QrPayload.Contact -> {
                    TextButton(onClick = { onCopyText(detail) }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                        Text(stringResource(R.string.action_copy), color = FarPalette.CameraYellow)
                    }
                }
                is QrPayload.PlainText -> {
                    TextButton(onClick = { onCopyText(parsed.raw) }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                        Text(stringResource(R.string.action_copy), color = FarPalette.CameraYellow)
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                Text(stringResource(R.string.action_dismiss), color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun OptionChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        modifier
            .minimumInteractiveComponentSize()
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
    Row(
        Modifier
            .background(Color.Black.copy(0.35f), CircleShape)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stops.forEach { z ->
            val selected = kotlin.math.abs(current - z) < 0.15f
            val size by animateDpAsState(if (selected) 40.dp else 32.dp, label = "chip")
            Box(
                Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .clickable { onPick(z) }
                    .semantics { role = Role.Button; contentDescription = "Zoom ${z}x" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(size)
                        .clip(CircleShape)
                        .background(Color.Black.copy(0.4f)),
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
}

private fun trim(v: Float) = if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(v)

@Composable
private fun ModeCarousel(modes: List<CameraMode>, current: CameraMode, onPick: (CameraMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        modes.forEach { m ->
            val c by animateColorAsState(if (m == current) FarPalette.CameraYellow else Color.White, label = "mode")
            Box(
                Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPick(m) }
                    .padding(horizontal = 6.dp, vertical = 8.dp)
                    .testTag("camera_mode_${m.name.lowercase()}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(m.labelRes),
                    color = c,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun Shutter(mode: CameraMode, recording: Boolean, onClick: () -> Unit) {
    val isVideo = mode == CameraMode.Video
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(if (pressed) 0.9f else 1f, label = "press")
    val innerColor by animateColorAsState(if (isVideo) FarPalette.RecordRed else Color.White, label = "inner")
    val innerSize by animateDpAsState(if (recording) 30.dp else 60.dp, label = "innerSize")
    val corner by animateDpAsState(if (recording) 8.dp else 30.dp, label = "corner")
    val desc = stringResource(
        when {
            isVideo && recording -> R.string.cd_stop_recording
            isVideo -> R.string.cd_start_recording
            else -> R.string.cd_take_photo
        }
    )
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
                contentDescription = desc
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
    size: Dp = 48.dp,
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

/** Yellow focus square + EV numeric badge + vertical exposure drag. */
@Composable
private fun FocusIndicator(
    point: Offset?,
    ev: Float,
    locked: Boolean,
    range: ClosedFloatingPointRange<Float>,
    onEv: (Float) -> Unit,
    onGone: () -> Unit,
) {
    if (point == null) return
    val s = remember(point) { Animatable(1.4f) }
    LaunchedEffect(point, locked) {
        s.animateTo(1f)
        if (!locked) {
            delay(1800)
            onGone()
        }
    }
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
            drawRect(FarPalette.CameraYellow, style = Stroke(if (locked) 3.dp.toPx() else 2.dp.toPx()))
        }
        if (ev != 0f || locked) {
            Text(
                text = if (locked) stringResource(R.string.camera_ae_af_locked) else "%+.1f EV".format(ev),
                color = Color.Black,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 18.dp)
                    .background(FarPalette.CameraYellow, RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}
