package com.example

import android.graphics.Bitmap
import android.graphics.Color
import com.example.core.media.QuickExifInfo
import com.example.core.storage.Checksums
import com.example.feature.albums.DateRangeFilter
import com.example.feature.albums.SearchQueryParser
import com.example.feature.albums.SearchScope
import com.example.feature.albums.SizeFilter
import com.example.feature.camera.CameraCapabilities
import com.example.feature.camera.CameraMode
import com.example.feature.camera.formatHardwareSummary
import com.example.feature.camera.mapHardwareLevelLabel
import com.example.feature.camera.probeCamera2Details
import com.example.feature.editor.CollageAspect
import com.example.feature.editor.CollageBackground
import com.example.feature.editor.CollageConfig
import com.example.feature.editor.CollageEngine
import com.example.feature.editor.EditState
import com.example.feature.editor.EditorEngine
import com.example.feature.editor.PdfExporter
import com.example.feature.editor.centeredNormalizedCrop
import com.example.feature.editor.matrixValues
import com.example.core.common.FarResult
import com.example.core.common.LocalDiagnostics
import com.example.core.media.MediaRow
import com.example.core.storage.ExposedDocumentsStore
import com.example.core.storage.FarDocumentsProvider
import com.example.app.IncomingRoute
import com.example.app.IntentResults
import com.example.app.IntentRouter
import com.example.feature.duplicates.BlurDetector
import com.example.feature.duplicates.BurstDetector
import com.example.feature.files.detectBinarySignature
import com.example.feature.files.filterTextLines
import com.example.feature.files.formatHexDump
import com.example.feature.storage.StorageAnalyzer
import android.app.Activity
import android.content.Intent
import android.content.pm.ProviderInfo
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Robolectric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoadmapUpgradesTest {

    @Test
    fun naturalLanguageSearchParser_extractsScopeDateSizeTagsAndQuotes() {
        val p1 = SearchQueryParser.parse("receipts last month >5mb #tax", currentYear = 2026)
        assertEquals(listOf("receipts"), p1.textTerms)
        assertEquals(listOf("tax"), p1.tags)
        assertEquals(DateRangeFilter.Last30Days, p1.inferredDateRange)
        assertEquals(SizeFilter.Over5Mb, p1.inferredSizeFilter)
        assertNull(p1.inferredScope)

        val p2 = SearchQueryParser.parse("\"golden gate\" videos 7 days >25mb 2024", currentYear = 2026)
        assertEquals(listOf("golden gate"), p2.textTerms)
        assertEquals(SearchScope.Videos, p2.inferredScope)
        assertEquals(DateRangeFilter.Last7Days, p2.inferredDateRange)
        assertEquals(SizeFilter.Over25Mb, p2.inferredSizeFilter)
        assertEquals(2024, p2.yearFilter)

        val p3 = SearchQueryParser.parse("favorites", currentYear = 2026)
        assertEquals(SearchScope.Favorites, p3.inferredScope)
        assertTrue(p3.textTerms.isEmpty())
    }

    @Test
    fun collageEngine_layoutAndRenderCoverAllCounts() {
        for (count in 2..9) {
            val cells = CollageEngine.computeCells(count, 1080, 1080, 16)
            assertEquals("Expected $count cells", count, cells.size)
            cells.forEach { r ->
                assertTrue(r.width() > 50f && r.height() > 50f)
                assertTrue(r.left >= 0f && r.top >= 0f && r.right <= 1080.5f && r.bottom <= 1080.5f)
            }
        }

        val bitmaps = List(4) { idx ->
            Bitmap.createBitmap(120, 90, Bitmap.Config.ARGB_8888).apply {
                eraseColor(if (idx % 2 == 0) Color.BLUE else Color.GREEN)
            }
        }
        try {
            val out = CollageEngine.renderCollage(
                bitmaps,
                CollageConfig(
                    aspect = CollageAspect.Portrait,
                    spacingPx = 12,
                    background = CollageBackground.Slate,
                    longEdgePx = 1000,
                ),
            )
            try {
                assertEquals(800, out.width)
                assertEquals(1000, out.height)
            } finally {
                out.recycle()
            }
        } finally {
            bitmaps.forEach { it.recycle() }
        }
    }

    @Test
    fun multiPagePdfExporter_writesValidPdf14WithMultiplePages() {
        val pages = List(3) { idx ->
            Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888).apply {
                eraseColor(if (idx == 0) Color.RED else Color.DKGRAY)
            }
        }
        try {
            val out = ByteArrayOutputStream()
            PdfExporter.writeMultiPageBitmapPdf(pages, out)
            val ascii = out.toByteArray().toString(Charsets.ISO_8859_1)
            assertTrue(ascii.startsWith("%PDF-1.4"))
            assertTrue(ascii.contains("/Count 3"))
            assertEquals(3, Regex("/Type /Page\\b").findAll(ascii).count())
            assertTrue(ascii.trimEnd().endsWith("%%EOF"))
        } finally {
            pages.forEach { it.recycle() }
        }
    }

    @Test
    fun checksumVerify_normalizesPrefixesWhitespaceAndCase() {
        val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        assertNull(Checksums.matchesSha256(expected, "   "))
        assertTrue(Checksums.matchesSha256(expected, "SHA256: E3B0C442 98FC1C14 9AFBF4C8 996FB924 27AE41E4 649B934C A495991B 7852B855") == true)
        assertFalse(Checksums.matchesSha256(expected, "0000000000000000") == true)
    }

    @Test
    fun quickExifInfo_formatsCoordinatesAcrossHemispheres() {
        val sf = QuickExifInfo(null, null, null, null, null, true, 37.7749, -122.4194)
        assertEquals("37.7749° N, 122.4194° W", sf.formattedCoordinates())
        val sydney = QuickExifInfo(null, null, null, null, null, true, -33.8688, 151.2093)
        assertEquals("33.8688° S, 151.2093° E", sydney.formattedCoordinates())
        val none = QuickExifInfo(null, null, null, null, null, false)
        assertNull(none.formattedCoordinates())
    }

    @Test
    fun filePreviewHelpers_detectMagicSignaturesAndFormatHexAndFilterLines() {
        assertEquals("PNG image", detectBinarySignature(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D)))
        assertEquals("PDF document", detectBinarySignature("%PDF-1.7".toByteArray()))
        assertEquals("ZIP archive / APK", detectBinarySignature(byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
        assertEquals("Binary data", detectBinarySignature(byteArrayOf(0x01, 0x02, 0x03)))

        val hex = formatHexDump("PK\u0003\u0004hello!".toByteArray(), 8)
        assertEquals(2, hex.size)
        assertEquals("000000", hex[0].offsetHex)
        assertEquals("PK..hell", hex[0].ascii)
        assertEquals("000008", hex[1].offsetHex)

        val filtered = filterTextLines(listOf("alpha", "Beta item", "gamma", "BETA 2"), "beta")
        assertEquals(2, filtered.size)
        assertEquals(2, filtered[0].number)
        assertEquals(4, filtered[1].number)
        assertTrue(filtered.all { it.matched })
    }

    @Test
    fun camera2Capabilities_formatsHardwareSummaryAndMapsLevelsSafely() {
        assertEquals("LEVEL_3", mapHardwareLevelLabel(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3))
        assertEquals("FULL", mapHardwareLevelLabel(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL))
        assertEquals("LIMITED", mapHardwareLevelLabel(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED))
        assertEquals("LEGACY", mapHardwareLevelLabel(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY))
        assertEquals("Standard", mapHardwareLevelLabel(null))

        val rich = CameraCapabilities(
            lensZoomStops = listOf(1f, 2f),
            minZoom = 1f,
            maxZoom = 5f,
            hasFlash = true,
            exposureRange = -2f..2f,
            modes = listOf(CameraMode.Photo, CameraMode.Video),
            hasFrontCamera = true,
            hardwareLevelLabel = "LEVEL_3",
            supportsRaw = true,
            maxFps = 60,
            hasOpticalStabilization = true,
            focalLengthsMm = listOf(4.71f),
        )
        assertEquals("HW: LEVEL_3 · 60fps · 4.7mm · OIS · RAW supported", formatHardwareSummary(rich))

        val basic = CameraCapabilities(
            lensZoomStops = listOf(1f),
            minZoom = 1f,
            maxZoom = 2f,
            hasFlash = false,
            exposureRange = -1f..1f,
            modes = listOf(CameraMode.Photo),
            hasFrontCamera = false,
            hardwareLevelLabel = "LIMITED",
            supportsRaw = false,
            maxFps = 30,
            hasOpticalStabilization = false,
            focalLengthsMm = emptyList(),
        )
        assertEquals("HW: LIMITED · 30fps · JPEG", formatHardwareSummary(basic))

        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        val probed = probeCamera2Details(app, useFrontCamera = false)
        assertTrue(probed.maxFps >= 1)
    }

    @Test
    fun editorCropPresetsAndBeforeAfterSplit_computeExpectedAspectAndHalves() {
        // Wide 1600x900 photo cropped to Passport 35:45 portrait ratio
        val passportBox = centeredNormalizedCrop(1600, 900, 35f / 45f)
        assertEquals(0f, passportBox[1], 0.0001f)
        assertEquals(1f, passportBox[3], 0.0001f)
        val passportWidthPx = (passportBox[2] - passportBox[0]) * 1600f
        assertEquals(700f, passportWidthPx, 1f)

        // Square 1000x1000 photo cropped to Wallpaper 9:16 portrait ratio
        val wallpaperBox = centeredNormalizedCrop(1000, 1000, 9f / 16f)
        assertEquals(0f, wallpaperBox[1], 0.0001f)
        assertEquals(1f, wallpaperBox[3], 0.0001f)
        val wallpaperWidthPx = (wallpaperBox[2] - wallpaperBox[0]) * 1000f
        assertEquals(562.5f, wallpaperWidthPx, 1f)

        // Verify desaturated/darkened matrix values directly (JVM ShadowCanvas skips ColorMatrixColorFilter)
        val monoEdit = EditState(saturation = -1f, brightness = -0.35f, cropRatio = 35f / 45f)
        val matrix = monoEdit.matrixValues()
        assertEquals(20, matrix.size)
        assertTrue(matrix[4] < -40f)

        // Render cropped Passport Before/After split on 160x90 source -> 70x90 split bitmap
        val src = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(220, 40, 40))
        }
        val split = EditorEngine.renderBeforeAfterSplit(src, monoEdit)
        val rotatedSplit = EditorEngine.renderBeforeAfterSplit(src, EditState(rotationQuarter = 1))
        try {
            assertEquals(70, split.width)
            assertEquals(90, split.height)
            val leftPixel = split.getPixel(12, 45)
            val rightPixel = split.getPixel(55, 45)
            assertTrue(Color.red(leftPixel) > 150)
            assertEquals(255, Color.alpha(rightPixel))

            assertEquals(90, rotatedSplit.width)
            assertEquals(160, rotatedSplit.height)
        } finally {
            rotatedSplit.recycle()
            split.recycle()
            src.recycle()
        }
    }

    @Test
    fun documentsProvider_exposesOnlyExplicitlyOptedInRootsAndRejectsWritesOrUnauthorizedIds() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        ExposedDocumentsStore.clearAll(app)

        val info = ProviderInfo().apply {
            authority = "${app.packageName}.documents"
            exported = true
            grantUriPermissions = true
            readPermission = android.Manifest.permission.MANAGE_DOCUMENTS
            writePermission = android.Manifest.permission.MANAGE_DOCUMENTS
        }
        val provider = Robolectric.buildContentProvider(FarDocumentsProvider::class.java)
            .create(info)
            .get()

        // Default state exposes 0 roots
        provider.queryRoots(null).use { emptyRoots ->
            assertEquals(0, emptyRoots.count)
        }

        val tree = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADocuments/document/primary%3ADocuments")
        ExposedDocumentsStore.exposeFolder(app, tree, "My Documents")

        var rootDocId = ""
        provider.queryRoots(null).use { roots ->
            assertEquals(1, roots.count)
            assertTrue(roots.moveToFirst())
            val titleIdx = roots.getColumnIndex(DocumentsContract.Root.COLUMN_TITLE)
            val flagsIdx = roots.getColumnIndex(DocumentsContract.Root.COLUMN_FLAGS)
            val docIdIdx = roots.getColumnIndex(DocumentsContract.Root.COLUMN_DOCUMENT_ID)
            assertEquals("My Documents", roots.getString(titleIdx))
            assertTrue((roots.getInt(flagsIdx) and DocumentsContract.Root.FLAG_LOCAL_ONLY) != 0)
            rootDocId = roots.getString(docIdIdx)
            assertTrue(rootDocId.contains("::"))
        }

        // Write mode must be rejected with SecurityException
        var writeBlocked = false
        try {
            provider.openDocument(rootDocId, "w", null)
        } catch (_: SecurityException) {
            writeBlocked = true
        }
        assertTrue(writeBlocked)

        // Unapproved tree URI must be rejected with SecurityException
        val unapprovedDocId = FarDocumentsProvider.encodeDocId(
            "content://com.android.externalstorage.documents/tree/primary%3APrivate",
            "primary:Private/secret.txt",
        )
        var unauthorizedBlocked = false
        try {
            provider.queryDocument(unapprovedDocId, null)
        } catch (_: SecurityException) {
            unauthorizedBlocked = true
        }
        assertTrue(unauthorizedBlocked)

        // Removing the opt-in root hides it immediately
        ExposedDocumentsStore.unexposeFolder(app, tree.toString())
        provider.queryRoots(null).use { rootsAfter ->
            assertEquals(0, rootsAfter.count)
        }
    }

    @Test
    fun intentContracts_classifyMimePickCaptureAndBuildClipDataResults() {
        val imagePickIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg", "image/png"))
        }
        val classifiedPick = IntentRouter.classify(imagePickIntent) as IncomingRoute.Pick
        assertTrue(classifiedPick.multiple)
        assertEquals("image", classifiedPick.mediaKindHint())

        val videoPick = IncomingRoute.Pick(false, 1, listOf("video/mp4"))
        assertEquals("video", videoPick.mediaKindHint())
        assertEquals("all", IncomingRoute.Pick(true, 10, listOf("image/*", "video/*")).mediaKindHint())

        val outputUri = Uri.parse("content://example.app.fileprovider/captures/out.jpg")
        val capIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
        }
        val classifiedCap = IntentRouter.classify(capIntent) as IncomingRoute.Capture
        assertFalse(classifiedCap.video)
        assertEquals(outputUri, classifiedCap.output)

        val act = Robolectric.buildActivity(Activity::class.java).create().get()
        val u1 = Uri.parse("content://media/external/images/media/10")
        val u2 = Uri.parse("content://media/external/images/media/11")
        IntentResults.pick(act, listOf(u1, u2))
        val shadowAct = org.robolectric.Shadows.shadowOf(act)
        assertEquals(Activity.RESULT_OK, shadowAct.resultCode)
        val resIntent = shadowAct.resultIntent
        assertEquals(u1, resIntent.data)
        assertTrue((resIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
        assertEquals(2, resIntent.clipData?.itemCount)

        val cancelAct = Robolectric.buildActivity(Activity::class.java).create().get()
        IntentResults.cancel(cancelAct)
        assertEquals(Activity.RESULT_CANCELED, org.robolectric.Shadows.shadowOf(cancelAct).resultCode)
    }

    @Test
    fun blurDetector_distinguishesFlatBlurredImageFromSharpEdgeImage() {
        val w = 24
        val h = 24
        val flat = FloatArray(w * h) { 128f }
        val sharp = FloatArray(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            if ((x + y) % 2 == 0) 255f else 0f
        }
        val flatScore = BlurDetector.laplacianVariance(flat, w, h)
        val sharpScore = BlurDetector.laplacianVariance(sharp, w, h)
        assertEquals(0.0, flatScore, 0.001)
        assertTrue(sharpScore > BlurDetector.DEFAULT_BLUR_THRESHOLD)

        val flatBmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        val sharpBmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                sharpBmp.setPixel(x, y, if ((x + y) % 2 == 0) Color.WHITE else Color.BLACK)
            }
        }
        assertTrue(BlurDetector.scoreBitmap(flatBmp) < BlurDetector.DEFAULT_BLUR_THRESHOLD)
        assertTrue(BlurDetector.scoreBitmap(sharpBmp) > BlurDetector.DEFAULT_BLUR_THRESHOLD)
        flatBmp.recycle()
        sharpBmp.recycle()
    }

    @Test
    fun burstDetector_groupsRapidShotsAndSuggestsBestFrame() {
        val rows = listOf(
            MediaRow(1L, Uri.parse("content://media/1"), "IMG_1.jpg", "image/jpeg", false, 1_500_000L, 10_000L, 0L, 1920, 1080, "DCIM/Camera/"),
            MediaRow(2L, Uri.parse("content://media/2"), "IMG_2.jpg", "image/jpeg", false, 3_200_000L, 12_000L, 0L, 4000, 3000, "DCIM/Camera/"),
            MediaRow(3L, Uri.parse("content://media/3"), "IMG_3.jpg", "image/jpeg", false, 2_100_000L, 14_000L, 0L, 4000, 3000, "DCIM/Camera/"),
            MediaRow(4L, Uri.parse("content://media/4"), "IMG_4.jpg", "image/jpeg", false, 2_500_000L, 60_000L, 0L, 4000, 3000, "DCIM/Camera/"),
        )
        val bursts = BurstDetector.findBursts(rows, favorites = emptySet(), inAlbums = emptySet())
        assertEquals(1, bursts.size)
        assertEquals(3, bursts.first().items.size)
        assertEquals(2L, bursts.first().bestFrameId)
    }

    @Test
    fun storageAnalyzer_computesFolderTreemapAndTidySuggestions() {
        val now = 1_700_000_000_000L
        val fortyDaysAgo = now - (40L * 24L * 60L * 60L * 1000L)
        val rows = listOf(
            MediaRow(10L, Uri.parse("content://media/10"), "shot_old.png", "image/png", false, 2_000_000L, fortyDaysAgo, 0L, 1080, 2400, "Pictures/Screenshots/"),
            MediaRow(11L, Uri.parse("content://media/11"), "vid_big.mp4", "video/mp4", true, 55L * 1024L * 1024L, now - 1000L, 20_000L, 1920, 1080, "DCIM/Camera/"),
            MediaRow(12L, Uri.parse("content://media/12"), "cam.jpg", "image/jpeg", false, 4_000_000L, now - 500L, 0L, 4000, 3000, "DCIM/Camera/"),
        )
        val summary = StorageAnalyzer.summarize(rows, minLargeBytes = 10L * 1024L * 1024L, nowMs = now)
        assertEquals(2, summary.folderSlices.size)
        assertEquals("DCIM/Camera", summary.folderSlices.first().folderName)
        assertEquals(2, summary.suggestions.size)
        val oldShots = summary.suggestions.first { it.id == "old_shots" }
        assertTrue(10L in oldShots.itemIds)
        val bigVids = summary.suggestions.first { it.id == "large_videos" }
        assertTrue(11L in bigVids.itemIds)
    }

    @Test
    fun localDiagnostics_exportToDownloadsWritesSanitizedReportLocally() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        LocalDiagnostics.clearAll(app)
        LocalDiagnostics.recordEvent(app, "FG-TEST", "smoke_area", "sanitized_code_only")
        val report = LocalDiagnostics.buildReport(app)
        assertTrue(report.contains("zero PII"))
        assertTrue(report.contains("FG-TEST|smoke_area|sanitized_code_only"))

        val exportRes = LocalDiagnostics.exportToDownloads(app)
        assertTrue(exportRes is FarResult.Ok)
        val exported = (exportRes as FarResult.Ok).value
        assertTrue(exported.contains("far_diagnostics_"))
        if (exported.startsWith("/")) {
            val f = File(exported)
            assertTrue(f.exists())
            assertTrue(f.readText().contains("FG-TEST"))
        }
        LocalDiagnostics.clearAll(app)
    }
}
