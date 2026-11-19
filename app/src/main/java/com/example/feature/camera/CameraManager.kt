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
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.IncomingRoute
import com.example.core.common.LocalDiagnostics
import com.example.ui.feedback.ErrorAction
import com.example.ui.feedback.ErrorInfo
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

    suspend fun capabilities(): CameraCapabilities? {
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
        return CameraCapabilities(
            lensZoomStops = stops,
            minZoom = min,
            maxZoom = max,
            hasFlash = info.hasFlashUnit(),
            exposureRange = (range.lower * step)..(range.upper * step),
            modes = listOf(CameraMode.Video, CameraMode.Photo, CameraMode.Square),
            hasFrontCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT),
        )
    }

    fun setMode(mode: CameraMode) {
        controller.setEnabledUseCases(
            if (mode == CameraMode.Video || mode == CameraMode.SloMo) CameraController.VIDEO_CAPTURE
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

    fun flip() {
        controller.cameraSelector = if (controller.cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    fun takePhoto(onDone: (Result<Uri>) -> Unit) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_" + stamp())
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
        }
        val opts = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values,
        ).build()
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

    suspend fun takePhotoAwait(): Result<Uri> = suspendCancellableCoroutine { c -> takePhoto { c.resume(it) } }

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
            if (e is VideoRecordEvent.Finalize) recording = null
            onEvent(e)
        }
    }

    fun stopRecording() {
        runCatching { recording?.stop() }
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    fun enableQr(onFound: (String) -> Unit) {
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

    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
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
    var captureRequest: IncomingRoute.Capture? = null
    var onCaptured: ((Uri) -> Unit)? = null

    init {
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

    fun permission(granted: Boolean) = _state.update { it.copy(permissionGranted = granted) }
    fun micPermission(granted: Boolean) = _state.update { it.copy(micPermissionGranted = granted) }
    fun setGridInitial(on: Boolean) = _state.update { it.copy(gridOn = on) }
    fun toggleGrid() = _state.update { it.copy(gridOn = !it.gridOn) }

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
        if (_state.value.recording) return
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
        _state.update { it.copy(mode = m, qrEnabled = false) }
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
    }
    fun dismissError() = _state.update { it.copy(error = null) }

    fun shutter() {
        val s = _state.value
        when (s.mode) {
            CameraMode.Video, CameraMode.SloMo -> if (s.recording) stopVideo() else startVideo()
            else -> if (s.countdown != null) {
                timerJob?.cancel()
                _state.update { it.copy(countdown = null) }
            } else {
                timerJob = viewModelScope.launch { countdownThenShoot(s.timerSeconds) }
            }
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
        manager.takePhotoAwait().fold(
            onSuccess = { uri ->
                _state.update { it.copy(lastCaptureUri = uri.toString()) }
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
                        _state.update { it.copy(lastCaptureUri = uri.toString()) }
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
fun LevelIndicator(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var roll by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                roll = Math.toDegrees(atan2(e.values[0].toDouble(), e.values[1].toDouble())).toFloat()
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        if (sensor != null) sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(l) }
    }
    val level = kotlin.math.abs(roll) < 1f || kotlin.math.abs(kotlin.math.abs(roll) - 180f) < 1f
    Canvas(modifier.fillMaxSize()) {
        val c = center
        val half = 28.dp.toPx()
        val col = if (level) Color(0xFFFFD60A) else Color.White.copy(0.7f)
        val a = Math.toRadians(roll.toDouble())
        val dx = (cos(a) * half).toFloat()
        val dy = (sin(a) * half).toFloat()
        drawLine(col, Offset(c.x - dx, c.y + dy), Offset(c.x + dx, c.y - dy), 2.dp.toPx())
    }
}
