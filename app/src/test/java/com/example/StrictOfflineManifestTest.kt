package com.example

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.google.mlkit.common.sdkinternal.MlKitContext
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class StrictOfflineManifestTest {

    @Test
    fun mergedManifest_containsNoNetworkPermissionsOrTelemetryTransport() {
        val candidates = listOf(
            File("build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml"),
            File("app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml"),
        )
        val merged = candidates.firstOrNull { it.exists() }
            ?: error("Merged debug manifest not found at ${candidates.joinToString()}")
        val xml = merged.readText()

        assertFalse("Strict Offline: INTERNET must not appear in merged manifest", "android.permission.INTERNET" in xml)
        assertFalse("Strict Offline: ACCESS_NETWORK_STATE must not appear in merged manifest", "android.permission.ACCESS_NETWORK_STATE" in xml)
        assertFalse("Strict Offline: DataTransport CCT discovery must be stripped", "TransportBackendDiscovery" in xml)
        assertFalse("Strict Offline: DataTransport JobInfoSchedulerService must be stripped", "JobInfoSchedulerService" in xml)
        assertFalse("Strict Offline: DataTransport AlarmManagerSchedulerBroadcastReceiver must be stripped", "AlarmManagerSchedulerBroadcastReceiver" in xml)
    }

    @Test
    fun runtimePackageInfo_hasNoInternetPermissionGrantedOrRequested() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        @Suppress("DEPRECATION")
        val pkgInfo = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
        val requested = pkgInfo.requestedPermissions?.toList().orEmpty()
        assertFalse(Manifest.permission.INTERNET in requested)
        assertFalse(Manifest.permission.ACCESS_NETWORK_STATE in requested)
    }

    @Test
    fun mlKitClients_constructCleanlyOffline() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        MlKitContext.initializeIfNeeded(app)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val scanner = BarcodeScanning.getClient()
        assertNotNull(recognizer)
        assertNotNull(scanner)
        recognizer.close()
        scanner.close()
    }
}
