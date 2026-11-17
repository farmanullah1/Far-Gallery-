package com.example

import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.example.app.IncomingRoute
import com.example.app.IntentRouter
import com.example.core.common.FarError
import com.example.core.common.FarResult
import com.example.core.common.IncomingMediaResolver
import com.example.core.common.farCatching
import com.example.core.media.MediaRow
import com.example.core.storage.Checksums
import com.example.core.storage.SafeZip
import com.example.feature.albums.MemoryRules
import com.example.feature.duplicates.DuplicateFinder
import com.example.feature.duplicates.DupScan
import com.example.feature.editor.EditHistory
import com.example.feature.editor.EditState
import com.example.feature.settings.FarSettings
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class ExampleRobolectricTest {

    private val resolver: ContentResolver by lazy {
        ApplicationProvider.getApplicationContext<Application>().contentResolver
    }

    @Test
    fun farCatching_mapsIoAndSecurityErrors() {
        val missing = farCatching<String> { throw FileNotFoundException("gone") }
        assertTrue(missing is FarResult.Err && missing.error is FarError.Missing)

        val denied = farCatching<String> { throw SecurityException("nope") }
        assertTrue(denied is FarResult.Err && denied.error is FarError.PermissionRevoked)
    }

    @Test
    fun safeZip_rejectsTraversalAndExtractsSafeEntries() {
        assertNull(SafeZip.sanitize("../secret.txt"))
        assertNull(SafeZip.sanitize("/etc/passwd"))
        assertNull(SafeZip.sanitize("C:\\Windows\\system.ini"))
        assertEquals("folder/photo.jpg", SafeZip.sanitize("folder\\./photo.jpg"))

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            zos.putNextEntry(ZipEntry("docs/hello.txt"))
            zos.write("hello far gallery".toByteArray())
            zos.closeEntry()
        }
        val extracted = ArrayList<String>()
        SafeZip.extract(ByteArrayInputStream(out.toByteArray()), out.size().toLong()) { path, isDir, _ ->
            if (!isDir) extracted += path
        }
        assertEquals(listOf("docs/hello.txt"), extracted)
    }

    @Test
    fun checksums_computesStableSha256() {
        val a = Checksums.sha256(ByteArrayInputStream("far-gallery".toByteArray()))
        val b = Checksums.sha256(ByteArrayInputStream("far-gallery".toByteArray()))
        assertEquals(64, a.length)
        assertEquals(a, b)
    }

    @Test
    fun incomingResolver_sniffsMagicHeadersAndRejectsNonContentSchemes() {
        val mediaResolver = IncomingMediaResolver(resolver)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0, 0, 0, 0, 0, 0, 0)
        assertEquals("image/jpeg", mediaResolver.sniffMime(jpeg, jpeg.size))

        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)
        assertEquals("image/png", mediaResolver.sniffMime(png, png.size))

        val badIntent = Intent(Intent.ACTION_VIEW).setData(Uri.parse("file:///sdcard/photo.jpg"))
        val res = mediaResolver.resolve(badIntent)
        assertTrue(res.accepted.isEmpty())
        assertEquals(1, res.rejected.size)
    }

    @Test
    fun intentRouter_classifiesShortcutsPickAndCapture() {
        assertEquals(
            IncomingRoute.ShortcutCamera,
            IntentRouter.classify(Intent(Intent.ACTION_VIEW, Uri.parse("farGallery://camera"))),
        )
        assertEquals(
            IncomingRoute.ShortcutFavorites,
            IntentRouter.classify(Intent(Intent.ACTION_VIEW, Uri.parse("farGallery://favorites"))),
        )
        assertEquals(
            IncomingRoute.ShortcutSearch,
            IntentRouter.classify(Intent(Intent.ACTION_VIEW, Uri.parse("farGallery://search"))),
        )
        val pick = IntentRouter.classify(Intent(Intent.ACTION_GET_CONTENT).setType("image/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true))
        assertTrue(pick is IncomingRoute.Pick && pick.multiple)

        val cap = IntentRouter.classify(Intent(MediaStore.ACTION_VIDEO_CAPTURE).putExtra(MediaStore.EXTRA_DURATION_LIMIT, 30))
        assertTrue(cap is IncomingRoute.Capture && cap.video && cap.maxDurationSec == 30)
    }

    @Test
    fun editHistory_undoAndRedoPreserveSnapshots() {
        val h = EditHistory()
        assertFalse(h.canUndo)
        h.commit(EditState(brightness = 0.25f))
        h.commit(EditState(brightness = 0.25f, contrast = 0.4f))
        assertTrue(h.canUndo)
        h.undo()
        assertEquals(0.25f, h.current.brightness, 0.001f)
        assertEquals(0f, h.current.contrast, 0.001f)
        assertTrue(h.canRedo)
        h.redo()
        assertEquals(0.4f, h.current.contrast, 0.001f)
    }

    @Test
    fun memoryRules_onThisDayMatchesSameMonthAndDayInEarlierYears() {
        val today = LocalDate.of(2026, 11, 17)
        val ms2024 = LocalDate.of(2024, 11, 17).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val ms2026 = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val msOther = LocalDate.of(2024, 5, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val result = MemoryRules.onThisDay(listOf(1L to ms2024, 2L to ms2026, 3L to msOther), today)
        assertEquals(setOf(2024), result.keys)
        assertEquals(listOf(1L), result[2024])
    }

    @Test
    fun duplicateFinder_groupsUniqueSizesWithoutCrashingOnMissingStreams() = runTest {
        val finder = DuplicateFinder(resolver)
        val row = MediaRow(1L, Uri.parse("content://media/external/images/media/99999"), "a.jpg", "image/jpeg", false, 100L, 0L, 0L, 100, 100, null)
        val events = finder.scan(listOf(row), favorites = setOf(1L), inAlbums = emptySet()).toList()
        val done = events.lastOrNull() as? DupScan.Done
        assertNotNull(done)
        assertTrue(done!!.groups.isEmpty())
    }

    @Test
    fun defaultSettings_matchesOfflineDesign() {
        val s = FarSettings()
        assertEquals(ThemeMode.System, s.theme)
        assertTrue(s.memoriesEnabled)
        assertTrue(s.ocrEnabled)
        assertTrue(s.volumeKeyShutter)
    }
}
