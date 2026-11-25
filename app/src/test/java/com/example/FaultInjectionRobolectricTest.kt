package com.example

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.FarError
import com.example.core.common.FarResult
import com.example.core.common.IncomingMediaResolver
import com.example.core.common.farCatching
import com.example.feature.camera.CameraMode
import com.example.feature.settings.VideoPositionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.TimeoutException

@RunWith(RobolectricTestRunner::class)
class FaultInjectionRobolectricTest {

    @Test(expected = CancellationException::class)
    fun farCatching_alwaysRethrowsCancellationException() {
        farCatching<Unit> { throw CancellationException("cancelled") }
    }

    @Test
    fun farCatching_mapsTypedErrorsAccurately() {
        assertTrue((farCatching<Unit> { throw TimeoutException("slow") } as FarResult.Err).error is FarError.Timeout)
        assertTrue((farCatching<Unit> { throw FileNotFoundException("gone") } as FarResult.Err).error is FarError.Missing)
        assertTrue((farCatching<Unit> { throw SecurityException("denied") } as FarResult.Err).error is FarError.PermissionRevoked)
        assertTrue((farCatching<Unit> { throw IOException("write failed: ENOSPC") } as FarResult.Err).error is FarError.StorageFull)
        assertTrue((farCatching<Unit> { throw OutOfMemoryError("oom") } as FarResult.Err).error is FarError.OutOfMemory)
    }

    @Test
    fun incomingMediaResolver_rejectsNonMediaAndReadsSafeFileUri() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val resolver = IncomingMediaResolver(app.contentResolver)

        // Unsupported scheme / missing file
        val missingIntent = Intent(Intent.ACTION_VIEW).setData(Uri.fromFile(File(app.cacheDir, "does_not_exist.jpg")))
        val missingRes = resolver.resolve(missingIntent)
        assertTrue(missingRes.accepted.isEmpty())
        assertEquals(1, missingRes.rejected.size)

        // Non-media text masquerading as an image
        val fakeImg = File(app.cacheDir, "not_an_image.jpg").apply { writeText("hello plain text") }
        val fakeIntent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, Uri.fromFile(fakeImg))
        val fakeRes = resolver.resolve(fakeIntent)
        assertTrue(fakeRes.accepted.isEmpty())
        assertEquals(1, fakeRes.rejected.size)

        // Real JPEG header via read-only file://
        val realJpg = File(app.cacheDir, "valid_header.jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 16))
        }
        val okIntent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, Uri.fromFile(realJpg))
        val okRes = resolver.resolve(okIntent)
        assertEquals(1, okRes.accepted.size)
        assertEquals("image/jpeg", okRes.accepted.first().sniffedMime)
    }

    @Test
    fun videoPositionStore_persistsAndClearsPositions() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = VideoPositionStore(app)
        val uri = "content://media/external/video/media/42"
        store.save(uri, 12_500L)
        assertEquals(12_500L, store.get(uri))
        store.clear()
        assertNull(store.get(uri))
    }

    @Test
    fun cameraModes_containOnlyBackedModes() {
        val names = CameraMode.entries.map { it.name }.toSet()
        assertFalse("SloMo" in names)
        assertFalse("Portrait" in names)
        assertTrue("Photo" in names && "Video" in names && "Square" in names && "Scan" in names)
    }
}
