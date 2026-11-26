package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.app.IncomingRoute
import com.example.app.IntentResults
import com.example.app.IntentRouter
import com.example.core.common.FarResult
import com.example.core.common.LocalDiagnostics
import com.example.core.common.safeParseMediaUri
import com.example.core.common.userTitle
import com.example.feature.camera.CameraCapabilities
import com.example.feature.camera.CameraMode
import com.example.feature.camera.CameraScreen
import com.example.feature.camera.CameraViewModel
import com.example.feature.editor.PdfExporter
import com.example.ui.theme.FarTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Hosts the camera for: the "Far Camera" launcher icon, other apps' capture requests, and camera shortcuts. */
class CameraActivity : AppCompatActivity() {
    private val vm: CameraViewModel by viewModels()
    private var request: IncomingRoute.Capture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        request = IntentRouter.classify(intent) as? IncomingRoute.Capture
        vm.captureRequest = request
        request?.let { req ->
            if (req.video) vm.setMode(CameraMode.Video)
            vm.onCaptured = { uri ->
                when (val res = IntentResults.capture(this, uri, req)) {
                    is FarResult.Ok -> Unit
                    is FarResult.Err -> {
                        LocalDiagnostics.recordEvent(this, res.error.code, "camera_capture_result", res.error.userTitle())
                        IntentResults.cancel(this)
                    }
                }
            }
        }
        setContent {
            FarTheme(forceDarkSystemBars = true) {
                CameraHost(
                    vm = vm,
                    onClose = {
                        if (request != null) IntentResults.cancel(this) else finish()
                    },
                    onOpenLast = ::openInGallery,
                    onEditLastScan = ::openEditInGallery,
                )
            }
        }
    }

    private fun openInGallery(uri: Uri) {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, contentResolver.getType(uri) ?: "image/*")
                    .setPackage(packageName)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }
    }

    private fun openEditInGallery(uri: Uri) {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_EDIT)
                    .setDataAndType(uri, contentResolver.getType(uri) ?: "image/*")
                    .setPackage(packageName)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }
    }

    override fun onStop() {
        super.onStop()
        vm.onAppStopped()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        if (vm.volumeKeyShutterEnabled && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) && event?.repeatCount == 0) {
            vm.shutter()
            true
        } else {
            super.onKeyDown(keyCode, event)
        }
}

@Composable
fun CameraHost(
    vm: CameraViewModel,
    onClose: () -> Unit,
    onOpenLast: (Uri) -> Unit,
    onEditLastScan: ((Uri) -> Unit)? = null,
    onScanPdfSaved: ((Uri) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by vm.state.collectAsState()
    val caps by vm.caps.collectAsState()
    var scanToast by remember { mutableStateOf<String?>(null) }
    var exportingPdf by remember { mutableStateOf(false) }

    LaunchedEffect(scanToast) {
        if (scanToast != null) {
            delay(3500)
            scanToast = null
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        vm.permission(
            r[Manifest.permission.CAMERA] == true ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
        vm.micPermission(
            r[Manifest.permission.RECORD_AUDIO] == true ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    LaunchedEffect(Unit) {
        val has = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasMic = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        vm.permission(has)
        vm.micPermission(hasMic)
        if (!has) launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) vm.onAppStopped() }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }

    val c = caps
    if (c == null && state.error == null && state.permissionGranted) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    CameraScreen(
        controller = vm.manager.controller,
        caps = c ?: CameraCapabilities(listOf(1f), 1f, 1f, false, 0f..0f, listOf(CameraMode.Photo), false),
        state = state,
        onMode = vm::setMode,
        onZoom = vm::setZoom,
        onFlash = vm::setFlash,
        onExposure = vm::setExposure,
        onCycleTimer = vm::cycleTimer,
        onToggleGrid = vm::toggleGrid,
        onToggleLevel = vm::toggleLevel,
        onToggleBurst = vm::toggleBurst,
        onFocusPoint = vm::setFocusLock,
        onClearFocusLock = vm::clearFocusLock,
        onToggleQr = vm::toggleQr,
        onDismissQr = vm::dismissQr,
        onShutter = vm::shutter,
        onFlip = vm::flip,
        onOpenLast = { state.lastCaptureUri?.let { safeParseMediaUri(it)?.let(onOpenLast) } },
        onEditLastScan = if (onEditLastScan != null) {
            { uriStr -> safeParseMediaUri(uriStr)?.let(onEditLastScan) }
        } else null,
        onExportLastScanPdf = { uriStr ->
            val parsed = safeParseMediaUri(uriStr)
            if (parsed != null && !exportingPdf) {
                exportingPdf = true
                scope.launch {
                    when (val res = PdfExporter.exportImageToPdf(ctx, parsed, "FarScan_${System.currentTimeMillis()}")) {
                        is FarResult.Ok -> {
                            scanToast = ctx.getString(R.string.viewer_pdf_saved)
                            onScanPdfSaved?.invoke(res.value)
                        }
                        is FarResult.Err -> {
                            LocalDiagnostics.recordEvent(ctx, res.error.code, "camera_pdf", res.error.userTitle())
                            scanToast = ctx.getString(R.string.viewer_pdf_failed)
                        }
                    }
                    exportingPdf = false
                }
            }
        },
        scanToast = scanToast,
        onDismissScanToast = { scanToast = null },
        onClose = onClose,
        onRequestPermission = { launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) },
        lastThumbnail = { uri -> AsyncImage(uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) },
    )
}
