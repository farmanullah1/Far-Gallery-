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
import com.example.feature.editor.FilterPreset
import com.example.feature.editor.matrixValues
import com.example.feature.settings.FarSettings
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.luminance
import com.example.core.common.LocalDiagnostics
import com.example.core.common.resolveSummary
import com.example.core.common.resolveTitle
import com.example.core.common.safeParseMediaUri
import com.example.core.media.BatchMediaOps
import com.example.core.media.HistogramEngine
import com.example.feature.albums.AlbumFilter
import com.example.feature.albums.AlbumListLogic
import com.example.feature.albums.AlbumSort
import com.example.feature.albums.AlbumUi
import com.example.feature.gallery.GalleryItem
import com.example.feature.storage.StorageAnalyzer
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.enforceThemeContrast
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
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

    @Test(timeout = 15_000)
    fun mainActivity_initializesWithoutCrash() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.create()
        controller.start()
        controller.resume()
        val activity = controller.get()
        assertNotNull(activity)
        controller.pause().stop().destroy()
    }

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

    @Test
    fun editState_jsonRoundTripPreservesPresetCropAndAdjustments() {
        val original = EditState(
            brightness = 0.12f,
            contrast = 0.08f,
            saturation = -0.08f,
            warmth = 0.28f,
            rotationQuarter = 3,
            flipH = true,
            cropRatio = 1.5f,
            preset = FilterPreset.WarmFilm.name,
        )
        val restored = EditState.fromJson(original.toJson())
        assertNotNull(restored)
        assertEquals(original.brightness, restored!!.brightness, 0.001f)
        assertEquals(original.warmth, restored.warmth, 0.001f)
        assertEquals(3, restored.rotationQuarter)
        assertTrue(restored.flipH)
        assertEquals(1.5f, restored.cropRatio!!, 0.001f)
        assertEquals(FilterPreset.WarmFilm.name, restored.preset)
    }

    @Test
    fun safeZip_createAndExtractRoundTripsSanitizedEntries() {
        val out = ByteArrayOutputStream()
        val entries = sequenceOf(
            Triple("album/", true) { null },
            Triple("album/note.txt", false) { ByteArrayInputStream("clean zip".toByteArray()) },
            Triple("../evil.txt", false) { ByteArrayInputStream("bad".toByteArray()) },
        )
        SafeZip.create(entries, out)
        val bytes = out.toByteArray()
        val extracted = mutableMapOf<String, String>()
        SafeZip.extract(ByteArrayInputStream(bytes), bytes.size.toLong()) { path, isDir, data ->
            if (!isDir) extracted[path] = data.readBytes().toString(Charsets.UTF_8)
        }
        assertEquals(mapOf("album/note.txt" to "clean zip"), extracted)
    }

    @Test
    fun storageAnalyzer_summarizesBucketsAndThresholds() {
        val rows = listOf(
            MediaRow(1L, Uri.parse("content://media/external/video/1"), "clip.mp4", "video/mp4", true, 50L * 1024 * 1024, 1L, 5000L, 1920, 1080, "Movies/"),
            MediaRow(2L, Uri.parse("content://media/external/images/2"), "shot.png", "image/png", false, 3L * 1024 * 1024, 2L, 0L, 1080, 2400, "Pictures/Screenshots/"),
            MediaRow(3L, Uri.parse("content://media/external/images/3"), "raw.jpg", "image/jpeg", false, 15L * 1024 * 1024, 3L, 0L, 4000, 3000, "DCIM/Camera/"),
        )
        val summary = StorageAnalyzer.summarize(rows, minLargeBytes = 10L * 1024 * 1024)
        assertEquals(68L * 1024 * 1024, summary.totalBytes)
        assertEquals(3, summary.buckets.size)
        assertEquals(listOf(1L, 3L), summary.largeFiles.map { it.id })
    }

    @Test
    fun histogramEngine_computesNormalizedBinsAndClipping() {
        val bmp = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        for (y in 0 until 10) {
            for (x in 0 until 10) {
                val color = if (y < 5) Color.BLACK else Color.WHITE
                bmp.setPixel(x, y, color)
            }
        }
        val hist = HistogramEngine.compute(bmp)
        assertEquals(64, hist.luma.size)
        assertEquals(50, hist.shadowClipPct)
        assertEquals(50, hist.highlightClipPct)
        assertEquals(1f, hist.luma[0], 0.001f)
        assertEquals(1f, hist.luma[63], 0.001f)

        val brightened = HistogramEngine.compute(bmp, EditState(brightness = 0.6f).matrixValues())
        assertTrue(brightened.shadowClipPct < 50)
        bmp.recycle()
    }

    @Test
    fun batchMediaOps_buildRenamedNameSanitizesAndPads() {
        assertEquals("Trip_001.jpg", BatchMediaOps.buildRenamedName("IMG_1234.JPG", "Trip", 1))
        assertEquals("My_Set_042.png", BatchMediaOps.buildRenamedName("photo.png", " My/Set ", 42))
        assertEquals("FAR_007", BatchMediaOps.buildRenamedName("noext", "   ", 7))
    }

    @Test
    fun themeMode_nextCyclesAllFourModes() {
        assertEquals(ThemeMode.Light, ThemeMode.System.next())
        assertEquals(ThemeMode.Dark, ThemeMode.Light.next())
        assertEquals(ThemeMode.Amoled, ThemeMode.Dark.next())
        assertEquals(ThemeMode.System, ThemeMode.Amoled.next())
    }

    @Test
    fun themeMode_labelResResolvesDistinctLocalizedStrings() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val labels = ThemeMode.entries.map { app.getString(it.labelRes()) }
        assertTrue(labels.all { it.isNotBlank() })
        assertEquals(ThemeMode.entries.size, labels.toSet().size)
    }

    @Test
    fun farError_resolveTitleAndSummaryReturnLocalizedStrings() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val errors: List<FarError> = listOf(
            FarError.Unsupported("HEVC"),
            FarError.Corrupt(),
            FarError.Missing(),
            FarError.PermissionRevoked("camera"),
            FarError.StorageFull(100L, 10L),
            FarError.VolumeRemoved("USB"),
            FarError.ProviderUnavailable(),
            FarError.OutOfMemory(),
            FarError.Io(),
            FarError.InvalidInput("empty name"),
            FarError.Unknown(),
        )
        errors.forEach { err ->
            val title = err.resolveTitle(app)
            val summary = err.resolveSummary(app)
            assertTrue("Blank title for $err", title.isNotBlank())
            assertTrue("Blank summary for $err", summary.isNotBlank())
        }
        assertTrue(FarError.Unsupported("AVIF").resolveSummary(app).contains("AVIF"))
        assertTrue(FarError.PermissionRevoked("camera").resolveSummary(app).contains("camera"))
        assertTrue(FarError.InvalidInput("empty name").resolveSummary(app).contains("empty name"))
    }

    @Test
    fun safeParseMediaUri_allowsContentAndFileAndRejectsUnsafeSchemes() {
        assertNotNull(safeParseMediaUri("content://media/external/images/media/42"))
        assertNotNull(safeParseMediaUri("file:///storage/emulated/0/DCIM/a.jpg"))
        assertNull(safeParseMediaUri(null))
        assertNull(safeParseMediaUri(""))
        assertNull(safeParseMediaUri("   "))
        assertNull(safeParseMediaUri("https://example.com/photo.jpg"))
        assertNull(safeParseMediaUri("javascript:alert(1)"))
        assertNull(safeParseMediaUri("relative/path.jpg"))
    }

    @Test
    fun localDiagnostics_ringBufferCapsAndClears() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        LocalDiagnostics.clearAll(app)
        repeat(35) { idx ->
            LocalDiagnostics.recordEvent(app, "FG-IO", "test", "entry #$idx")
        }
        val events = LocalDiagnostics.readEvents(app)
        assertEquals(30, events.size)
        assertTrue(events.first().contains("entry #34"))
        assertTrue(LocalDiagnostics.buildReport(app).contains("Recent error events (30):"))

        LocalDiagnostics.clearAll(app)
        assertTrue(LocalDiagnostics.readEvents(app).isEmpty())
    }

    @Test
    fun galleryItemHeader_keysRemainUniqueAcrossDuplicateTitles() {
        val first = GalleryItem.Header(title = "November 2026", subtitle = "5 items", anchorId = 101L)
        val second = GalleryItem.Header(title = "November 2026", subtitle = "3 items", anchorId = 205L)
        assertNotEquals(first.key, second.key)
    }

    @Test
    fun enforceThemeContrast_guaranteesLegibleForegroundsInDarkAndAmoledModes() {
        val lowContrastDark = darkColorScheme(
            background = ComposeColor(0xFF101014),
            onBackground = ComposeColor.Black,
            surface = ComposeColor(0xFF1A1A1E),
            onSurface = ComposeColor(0xFF111111),
            onSurfaceVariant = ComposeColor(0xFF1F1F22),
        )
        val fixedDark = enforceThemeContrast(lowContrastDark, dark = true, amoled = false)
        assertTrue(fixedDark.onBackground.luminance() >= 0.7f)
        assertTrue(fixedDark.onSurface.luminance() >= 0.7f)
        assertTrue(fixedDark.onSurfaceVariant.luminance() >= 0.45f)

        val fixedAmoled = enforceThemeContrast(lowContrastDark, dark = true, amoled = true)
        assertEquals(ComposeColor.Black, fixedAmoled.background)
        assertEquals(ComposeColor.White, fixedAmoled.onBackground)
        assertEquals(ComposeColor.White, fixedAmoled.onSurface)
    }

    @Test
    fun albumListLogic_filtersAndSortsCorrectly() {
        val sample = listOf(
            AlbumUi(1L, "Travel", count = 14, coverUri = null, pinned = false, createdAt = 200L),
            AlbumUi(2L, "Archive", count = 0, coverUri = null, pinned = true, createdAt = 100L),
            AlbumUi(3L, "Balloons", count = 5, coverUri = null, pinned = true, createdAt = 300L),
        )

        assertEquals(listOf(2L, 3L, 1L), AlbumListLogic.filterAndSort(sample, AlbumFilter.All, AlbumSort.Pinned).map { it.id })
        assertEquals(listOf(3L, 2L, 1L), AlbumListLogic.filterAndSort(sample, AlbumFilter.All, AlbumSort.Newest).map { it.id })
        assertEquals(listOf(3L, 2L, 1L), AlbumListLogic.filterAndSort(sample, AlbumFilter.All, AlbumSort.MostItems).map { it.id })
        assertEquals(listOf(2L, 3L, 1L), AlbumListLogic.filterAndSort(sample, AlbumFilter.All, AlbumSort.NameAZ).map { it.id })

        assertEquals(listOf(2L, 3L), AlbumListLogic.filterAndSort(sample, AlbumFilter.Pinned, AlbumSort.Pinned).map { it.id })
        assertEquals(listOf(3L, 1L), AlbumListLogic.filterAndSort(sample, AlbumFilter.NonEmpty, AlbumSort.Pinned).map { it.id })
    }
}
