package com.example.feature.camera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPoint
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.IncomingRoute
import com.example.core.common.LocalDiagnostics
import com.example.core.database.FarDatabase
import com.example.core.media.MediaScanner
import com.example.feature.settings.SettingsStore
import com.example.ui.feedback.ErrorAction
import com.example.ui.feedback.ErrorInfo
import com.google.mlkit.common.sdkinternal.MlKitContext
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class FarCameraManager(private val context: Context) {
    val controller = LifecycleCameraController(context).apply {
        setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        videoCaptureQualitySelector = QualitySelector.fromOrderedList(listOf(Quality.FHD, Quality.HD, Quality.SD))
    }
    private var recording: Recording? = null
    private val mainExecutor get() = ContextCompat.getMainExecutor(context)

    suspend fun capabilities(): CameraCapabilities? = runCatching {
        val ready = suspendCancellableCoroutine { cont ->
            val f = controller.initializationFuture
            f.addListener({ cont.resume(runCatching { f.get() }.isSuccess) }, mainExecutor)
        }
        val info = controller.cameraInfo ?: return null
        if (!ready) return null
        val zoom = info.zoomState.value
        val min = zoom?.minZoomRatio ?: 1f
        val max = zoom?.maxZoomRatio ?: 1f
        val stops = buildList {
            if (min <= 0.6f) add(0.5f)
            add(1f)
            if (max >= 2f) add(2f)
            if (max >= 5f) add(5f)
        }
        val step = info.exposureState.exposureCompensationStep.toFloat()
        val range = info.exposureState.exposureCompensationRange
        val pm = context.packageManager
        CameraCapabilities(
            lensZoomStops = stops,
            minZoom = min,
            maxZoom = max,
            hasFlash = info.hasFlashUnit(),
            exposureRange = (range.lower * step)..(range.upper * step),
            modes = listOf(CameraMode.Video, CameraMode.Photo, CameraMode.Square, CameraMode.Scan),
            hasFrontCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT),
        )
    }.getOrNull()

    @Volatile
    var mirrorFrontCamera: Boolean = true

    fun setMode(mode: CameraMode) {
        controller.setEnabledUseCases(
            if (mode == CameraMode.Video) CameraController.VIDEO_CAPTURE
            else CameraController.IMAGE_CAPTURE
        )
    }

    fun setZoom(ratio: Float) {
        controller.setZoomRatio(ratio)
    }

    fun setFlash(mode: FlashMode) {
        controller.imageCaptureFlashMode = when (mode) {
            FlashMode.Off -> ImageCapture.FLASH_MODE_OFF
            FlashMode.Auto -> ImageCapture.FLASH_MODE_AUTO
            FlashMode.On -> ImageCapture.FLASH_MODE_ON
        }
        controller.enableTorch(mode == FlashMode.On && recording != null)
    }

    fun setExposureEv(ev: Float) {
        val step = controller.cameraInfo?.exposureState?.exposureCompensationStep?.toFloat()?.takeIf { it > 0f } ?: return
        controller.cameraControl?.setExposureCompensationIndex((ev / step).roundToInt())
    }

    fun focusAt(point: MeteringPoint, lockAeAf: Boolean) {
        val builder = FocusMeteringAction.Builder(point)
        if (lockAeAf) builder.disableAutoCancel()
        controller.cameraControl?.startFocusAndMetering(builder.build())
    }

    fun clearFocusLock() {
        controller.cameraControl?.cancelFocusAndMetering()
    }

    fun flip() {
        controller.cameraSelector = if (controller.cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    fun takePhoto(prefix: String = "IMG_", onDone: (Result<Uri>) -> Unit) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, prefix + stamp())
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
        }
        val metadata = ImageCapture.Metadata().apply {
            isReversedHorizontal = mirrorFrontCamera && controller.cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA
        }
        val opts = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values,
        ).setMetadata(metadata).build()
        runCatching {
            controller.takePicture(opts, mainExecutor, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                    r.savedUri?.let { onDone(Result.success(it)) } ?: onDone(Result.failure(IllegalStateException("no uri")))
                }

                override fun onError(e: ImageCaptureException) {
                    onDone(Result.failure(e))
                }
            })
        }.onFailure { onDone(Result.failure(it)) }
    }

    suspend fun takePhotoAwait(prefix: String = "IMG_"): Result<Uri> =
        suspendCancellableCoroutine { c -> takePhoto(prefix) { c.resume(it) } }

    @SuppressLint("MissingPermission")
    fun startRecording(maxBytes: Long?, maxSeconds: Int?, onEvent: (VideoRecordEvent) -> Unit): Result<Unit> = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "VID_" + stamp())
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/Camera")
        }
        val b = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values)
        maxBytes?.let { b.setFileSizeLimit(it) }
        maxSeconds?.let { b.setDurationLimitMillis(it * 1000L) }
        val audio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        recording = controller.startRecording(b.build(), AudioConfig.create(audio), mainExecutor) { e ->
            if (e is VideoRecordEvent.Finalize) {
                recording = null
                controller.enableTorch(false)
            }
            onEvent(e)
        }
    }

    fun stopRecording() {
        runCatching { recording?.stop() }
        controller.enableTorch(false)
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    fun enableQr(onFound: (String) -> Unit) {
        runCatching { MlKitContext.initializeIfNeeded(context.applicationContext) }
        val scanner = BarcodeScanning.getClient()
        controller.setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
        controller.setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        controller.setImageAnalysisAnalyzer(mainExecutor) { proxy ->
            val media = proxy.image
            if (media == null) {
                proxy.close()
                return@setImageAnalysisAnalyzer
            }
            scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                .addOnSuccessListener { list -> list.firstOrNull()?.rawValue?.let(onFound) }
                .addOnCompleteListener { proxy.close() }
        }
    }

    fun disableQr(mode: CameraMode) {
        controller.clearImageAnalysisAnalyzer()
        setMode(mode)
    }

    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
}

fun captureErrorInfo(e: Throwable, retry: () -> Unit): ErrorInfo = when ((e as? ImageCaptureException)?.imageCaptureError) {
    ImageCapture.ERROR_FILE_IO -> ErrorInfo(
        "Couldn't save the photo",
        "The photo couldn't be written. Storage may be full or unavailable.",
        true,
        "FG-CAM-IO",
        listOf(ErrorAction("Try again", true, retry)),
    )
    ImageCapture.ERROR_CAMERA_CLOSED -> ErrorInfo(
        "Camera is busy",
        "Another app may be using the camera.",
        true,
        "FG-CAM-BUSY",
        listOf(ErrorAction("Try again", true, retry)),
    )
    else -> ErrorInfo(
        "Photo not captured",
        "The camera didn't return a photo.",
        true,
        "FG-CAM-CAP",
        listOf(ErrorAction("Try again", true, retry)),
    )
}

class CameraViewModel(app: Application) : AndroidViewModel(app) {
    val manager = FarCameraManager(app)
    private val _state = MutableStateFlow(CameraUiState())
    val state: StateFlow<CameraUiState> = _state.asStateFlow()
    private val _caps = MutableStateFlow<CameraCapabilities?>(null)
    val caps: StateFlow<CameraCapabilities?> = _caps.asStateFlow()
    private var timerJob: Job? = null
    private var recJob: Job? = null
    private var burstJob: Job? = null
    var captureRequest: IncomingRoute.Capture? = null
    var onCaptured: ((Uri) -> Unit)? = null
    @Volatile
    var volumeKeyShutterEnabled: Boolean = true
    private var settingsSynced = false

    init {
        viewModelScope.launch {
            SettingsStore(app).flow.collect { s ->
                manager.mirrorFrontCamera = s.mirrorSelfie
                volumeKeyShutterEnabled = s.volumeKeyShutter
                if (!settingsSynced) {
                    settingsSynced = true
                    _state.update { it.copy(gridOn = s.cameraGrid) }
                }
            }
        }
        initCamera()
    }

    private fun initCamera() {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            val c = manager.capabilities()
            if (c == null) {
                LocalDiagnostics.recordEvent(getApplication(), "FG-CAM-NONE", "camera_init", "capabilities null")
                _state.update {
                    it.copy(
                        error = ErrorInfo(
                            "Camera unavailable",
                            "No usable camera was found, or it is in use by another app.",
                            true,
                            "FG-CAM-NONE",
                            listOf(ErrorAction("Retry", true) { retryInit() }),
                        )
                    )
                }
            }
            _caps.value = c
        }
    }

    fun retryInit() {
        initCamera()
    }

    fun permission(granted: Boolean) {
        _state.update { it.copy(permissionGranted = granted) }
        if (granted && _caps.value == null) {
            initCamera()
        }
    }

    fun micPermission(granted: Boolean) = _state.update { it.copy(micPermissionGranted = granted) }
    fun setGridInitial(on: Boolean) = _state.update { it.copy(gridOn = on) }
    fun toggleGrid() = _state.update { it.copy(gridOn = !it.gridOn) }
    fun toggleLevel() = _state.update { it.copy(levelEnabled = !it.levelEnabled) }
    fun toggleBurst() = _state.update { it.copy(burstMode = !it.burstMode) }

    fun setFocusLock(point: MeteringPoint?, lock: Boolean) {
        if (lock && point != null) {
            runCatching { manager.focusAt(point, lockAeAf = true) }
            _state.update { it.copy(aeAfLocked = true) }
        } else if (point != null) {
            runCatching { manager.focusAt(point, lockAeAf = false) }
            _state.update { it.copy(aeAfLocked = false) }
        } else {
            runCatching { manager.clearFocusLock() }
            _state.update { it.copy(aeAfLocked = false) }
        }
    }

    fun clearFocusLock() {
        runCatching { manager.clearFocusLock() }
        _state.update { it.copy(aeAfLocked = false) }
    }

    fun toggleQr() {
        val next = !_state.value.qrEnabled
        val res = runCatching {
            if (next) {
                manager.enableQr { code -> _state.update { it.copy(qrResult = code) } }
            } else {
                manager.disableQr(_state.value.mode)
            }
        }
        res.onFailure {
            LocalDiagnostics.recordEvent(getApplication(), "FG-CAM-QR", "camera_qr", it.javaClass.simpleName)
            _state.update { s ->
                s.copy(
                    qrEnabled = false,
                    error = ErrorInfo(
                        "QR scanner unavailable",
                        "Couldn't start camera analysis right now.",
                        true,
                        "FG-CAM-QR",
                        listOf(ErrorAction("OK", true) { dismissError() }),
                    ),
                )
            }
            return
        }
        _state.update { it.copy(qrEnabled = next, qrResult = if (next) it.qrResult else null) }
    }

    fun dismissQr() = _state.update { it.copy(qrResult = null) }

    fun setMode(m: CameraMode) {
        if (_state.value.recording || _state.value.burstCount != null) return
        runCatching { manager.setMode(m) }
            .onFailure {
                LocalDiagnostics.recordEvent(getApplication(), "FG-CAM-MODE", "camera_mode", it.javaClass.simpleName)
                _state.update { s ->
                    s.copy(
                        error = ErrorInfo(
                            "Couldn't switch camera mode",
                            "The camera isn't ready for ${m.label.lowercase()} yet.",
                            true,
                            "FG-CAM-MODE",
                            listOf(ErrorAction("OK", true) { dismissError() }),
                        )
                    )
                }
                return
            }
        _state.update {
            it.copy(
                mode = m,
                qrEnabled = false,
                burstMode = if (m == CameraMode.Photo || m == CameraMode.Square) it.burstMode else false,
            )
        }
    }

    fun setZoom(z: Float) {
        runCatching { manager.setZoom(z) }
        _state.update { it.copy(zoom = z) }
    }

    fun setFlash(f: FlashMode) {
        runCatching { manager.setFlash(f) }
        _state.update { it.copy(flash = f) }
    }

    fun setExposure(ev: Float) {
        runCatching { manager.setExposureEv(ev) }
        _state.update { it.copy(exposureEv = ev) }
    }

    fun cycleTimer() = _state.update {
        it.copy(timerSeconds = when (it.timerSeconds) { 0 -> 3; 3 -> 5; 5 -> 10; else -> 0 })
    }

    fun flip() {
        runCatching { manager.flip() }
        _state.update { it.copy(aeAfLocked = false) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun shutter() {
        val s = _state.value
        when (s.mode) {
            CameraMode.Video -> if (s.recording) stopVideo() else startVideo()
            else -> if (s.countdown != null) {
                timerJob?.cancel()
                _state.update { it.copy(countdown = null) }
            } else if (s.burstCount != null) {
                burstJob?.cancel()
                _state.update { it.copy(burstCount = null) }
            } else if (s.burstMode && (s.mode == CameraMode.Photo || s.mode == CameraMode.Square)) {
                burstJob = viewModelScope.launch { captureBurst() }
            } else {
                timerJob = viewModelScope.launch { countdownThenShoot(s.timerSeconds) }
            }
        }
    }

    private suspend fun captureBurst(maxShots: Int = 8) {
        if (lowStorage()) {
            _state.update {
                it.copy(
                    error = ErrorInfo(
                        "Not enough space",
                        "Free up storage to keep shooting.",
                        true,
                        "FG-SPC",
                        listOf(ErrorAction("OK", true) { dismissError() }),
                    )
                )
            }
            return
        }
        try {
            for (shot in 1..maxShots) {
                if (lowStorage()) break
                _state.update { it.copy(burstCount = shot) }
                val res = manager.takePhotoAwait(prefix = "BURST_")
                res.onSuccess { uri ->
                    _state.update { it.copy(lastCaptureUri = uri.toString(), lastCaptureWasScan = false) }
                    viewModelScope.launch {
                        MediaScanner.markCapturedByFar(getApplication(), FarDatabase.get(getApplication()), uri)
                    }
                    onCaptured?.invoke(uri)
                }.onFailure { e ->
                    LocalDiagnostics.recordEvent(getApplication(), "FG-CAM-BURST", "camera_burst", e.javaClass.simpleName)
                    if (shot == 1) {
                        _state.update { it.copy(error = captureErrorInfo(e) { dismissError() }) }
                    }
                    return
                }
                delay(180)
            }
        } finally {
            _state.update { it.copy(burstCount = null) }
        }
    }

    private suspend fun countdownThenShoot(seconds: Int) {
        for (n in seconds downTo 1) {
            _state.update { it.copy(countdown = n) }
            delay(1000)
        }
        _state.update { it.copy(countdown = null) }
        if (lowStorage()) {
            _state.update {
                it.copy(
                    error = ErrorInfo(
                        "Not enough space",
                        "Free up storage to keep shooting.",
                        true,
                        "FG-SPC",
                        listOf(ErrorAction("OK", true) { dismissError() }),
                    )
                )
            }
            return
        }
        val isScan = _state.value.mode == CameraMode.Scan
        manager.takePhotoAwait(prefix = if (isScan) "SCAN_" else "IMG_").fold(
            onSuccess = { uri ->
                _state.update { it.copy(lastCaptureUri = uri.toString(), lastCaptureWasScan = isScan) }
                viewModelScope.launch {
                    MediaScanner.markCapturedByFar(getApplication(), FarDatabase.get(getApplication()), uri)
                }
                onCaptured?.invoke(uri)
            },
            onFailure = { e ->
                LocalDiagnostics.recordEvent(getApplication(), "FG-CAM-CAP", "camera_photo", e.javaClass.simpleName)
                _state.update { it.copy(error = captureErrorInfo(e) { dismissError() }) }
            },
        )
    }

    private fun startVideo() {
        if (lowStorage()) {
            LocalDiagnostics.recordEvent(getApplication(), "FG-SPC", "camera_video", "low storage")
            _state.update {
                it.copy(
                    error = ErrorInfo(
                        "Not enough space",
                        "Free up storage before recording.",
                        true,
                        "FG-SPC",
                        listOf(ErrorAction("OK", true) { dismissError() }),
                    )
                )
            }
            return
        }
        val started = manager.startRecording(captureRequest?.maxBytes, captureRequest?.maxDurationSec) { e ->
            when (e) {
                is VideoRecordEvent.Start -> {
                    _state.update { it.copy(recording = true, recordingSeconds = 0) }
                    if (_state.value.flash == FlashMode.On) {
                        runCatching { manager.controller.enableTorch(true) }
                    }
                    recJob = viewModelScope.launch {
                        while (true) {
                            delay(1000)
                            _state.update { s -> s.copy(recordingSeconds = s.recordingSeconds + 1) }
                        }
                    }
                }
                is VideoRecordEvent.Finalize -> {
                    recJob?.cancel()
                    _state.update { it.copy(recording = false) }
                    if (e.hasError() &&
                        e.error != VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED &&
                        e.error != VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED
                    ) {
                        LocalDiagnostics.recordEvent(getApplication(), "FG-REC-${e.error}", "camera_video", "finalize ${e.error}")
                        _state.update {
                            it.copy(
                                error = ErrorInfo(
                                    "Recording problem",
                                    "The video may be incomplete. Anything already recorded is kept when possible.",
                                    true,
                                    "FG-REC-${e.error}",
                                    listOf(ErrorAction("OK", true) { dismissError() }),
                                )
                            )
                        }
                    } else {
                        val uri = e.outputResults.outputUri
                        _state.update { it.copy(lastCaptureUri = uri.toString(), lastCaptureWasScan = false) }
                        viewModelScope.launch {
                            MediaScanner.markCapturedByFar(getApplication(), FarDatabase.get(getApplication()), uri)
                        }
                        onCaptured?.invoke(uri)
                    }
                }
                else -> Unit
            }
        }
        started.onFailure {
            LocalDiagnostics.recordEvent(getApplication(), "FG-REC-START", "camera_video", it.javaClass.simpleName)
            _state.update { s ->
                s.copy(
                    error = ErrorInfo(
                        "Couldn't start recording",
                        "The video encoder or camera isn't ready yet.",
                        true,
                        "FG-REC-START",
                        listOf(ErrorAction("OK", true) { dismissError() }),
                    )
                )
            }
        }
    }

    private fun stopVideo() = manager.stopRecording()

    fun onAppStopped() {
        burstJob?.cancel()
        if (_state.value.recording) stopVideo()
    }

    private fun lowStorage(): Boolean = Environment.getExternalStorageDirectory().usableSpace < 200L * 1024 * 1024
}

@Composable
fun GridOverlay(modifier: Modifier = Modifier, divisions: Int = 3) {
    Canvas(modifier.fillMaxSize()) {
        repeat(divisions - 1) { i ->
            val x = size.width * (i + 1) / divisions
            val y = size.height * (i + 1) / divisions
            drawLine(Color.White.copy(0.35f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            drawLine(Color.White.copy(0.35f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
    }
}

@Composable
fun ScanFrameOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val marginX = size.width * 0.1f
        val marginY = size.height * 0.12f
        val left = marginX
        val right = size.width - marginX
        val top = marginY
        val bottom = size.height - marginY
        val arm = 26.dp.toPx()
        val stroke = 3.dp.toPx()
        val color = Color(0xFFFFD60A)
        // Top-left
        drawLine(color, Offset(left, top), Offset(left + arm, top), stroke)
        drawLine(color, Offset(left, top), Offset(left, top + arm), stroke)
        // Top-right
        drawLine(color, Offset(right - arm, top), Offset(right, top), stroke)
        drawLine(color, Offset(right, top), Offset(right, top + arm), stroke)
        // Bottom-left
        drawLine(color, Offset(left, bottom), Offset(left + arm, bottom), stroke)
        drawLine(color, Offset(left, bottom - arm), Offset(left, bottom), stroke)
        // Bottom-right
        drawLine(color, Offset(right - arm, bottom), Offset(right, bottom), stroke)
        drawLine(color, Offset(right, bottom - arm), Offset(right, bottom), stroke)
    }
}

@Composable
fun LevelIndicator(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var roll by remember { mutableFloatStateOf(0f) }
    var wasLevel by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val raw = Math.toDegrees(atan2(e.values[0].toDouble(), e.values[1].toDouble())).toFloat()
                // Smooth jitter while keeping rapid response
                roll = roll * 0.72f + raw * 0.28f
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        if (sm != null && sensor != null) sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(l) }
    }
    val level = abs(roll) < 1.2f || abs(abs(roll) - 180f) < 1.2f
    LaunchedEffect(level) {
        if (level && !wasLevel) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        wasLevel = level
    }
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val half = 40.dp.toPx()
            val gap = 10.dp.toPx()
            val col = if (level) Color(0xFFFFD60A) else Color.White.copy(0.78f)
            // Fixed horizontal reference ticks
            drawLine(Color.White.copy(0.45f), Offset(c.x - half - 14.dp.toPx(), c.y), Offset(c.x - half - 4.dp.toPx(), c.y), 1.5.dp.toPx())
            drawLine(Color.White.copy(0.45f), Offset(c.x + half + 4.dp.toPx(), c.y), Offset(c.x + half + 14.dp.toPx(), c.y), 1.5.dp.toPx())
            val a = Math.toRadians(roll.toDouble())
            val dx = (cos(a) * half).toFloat()
            val dy = (sin(a) * half).toFloat()
            val gdx = (cos(a) * gap).toFloat()
            val gdy = (sin(a) * gap).toFloat()
            drawLine(col, Offset(c.x - dx, c.y + dy), Offset(c.x - gdx, c.y + gdy), 2.5.dp.toPx())
            drawLine(col, Offset(c.x + gdx, c.y - gdy), Offset(c.x + dx, c.y - dy), 2.5.dp.toPx())
        }
        val normalizedDeg = ((roll.roundToInt() + 180) % 180).let { if (it > 90) it - 180 else it }
        Text(
            text = "${normalizedDeg}°",
            color = if (level) Color(0xFFFFD60A) else Color.White.copy(alpha = 0.85f),
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 26.dp)
                .background(Color.Black.copy(alpha = 0.48f), RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
