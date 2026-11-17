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
import com.example.feature.camera.CameraCapabilities
import com.example.feature.camera.CameraMode
import com.example.feature.camera.CameraScreen
import com.example.feature.camera.CameraViewModel
import com.example.ui.theme.FarTheme

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
            vm.onCaptured = { uri -> IntentResults.capture(this, uri, req) }
        }
        setContent {
            FarTheme(forceDarkSystemBars = true) {
                CameraHost(vm, onClose = { finish() }, onOpenLast = ::openInGallery)
            }
        }
    }

    private fun openInGallery(uri: Uri) = startActivity(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, contentResolver.getType(uri) ?: "image/*")
            .setPackage(packageName)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    )

    override fun onStop() {
        super.onStop()
        vm.onAppStopped()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) && event?.repeatCount == 0) {
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
) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsState()
    val caps by vm.caps.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        vm.permission(
            r[Manifest.permission.CAMERA] == true ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    LaunchedEffect(Unit) {
        val has = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        vm.permission(has)
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
        onToggleQr = vm::toggleQr,
        onDismissQr = vm::dismissQr,
        onShutter = vm::shutter,
        onFlip = vm::flip,
        onOpenLast = { state.lastCaptureUri?.let { onOpenLast(Uri.parse(it)) } },
        onClose = onClose,
        onRequestPermission = { launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) },
        lastThumbnail = { uri -> AsyncImage(uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) },
    )
}
