package com.example

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.LayoutDirection
import com.example.app.QuickSharePresetDialog
import com.example.core.database.SavedSearch
import com.example.core.media.MediaRow
import com.example.core.media.QuickSharePreset
import com.example.core.storage.SafeZip
import com.example.feature.albums.DateRangeFilter
import com.example.feature.albums.IndexStatus
import com.example.feature.albums.SearchScope
import com.example.feature.albums.SearchScreen
import com.example.feature.albums.SizeFilter
import com.example.feature.camera.QrParser
import com.example.feature.camera.QrPayload
import com.example.feature.duplicates.DupGroup
import com.example.feature.duplicates.DupItem
import com.example.feature.duplicates.DuplicateLogic
import com.example.feature.duplicates.DuplicatesScreen
import com.example.feature.editor.EditState
import com.example.feature.editor.EditorEngine
import com.example.feature.editor.FilterPreset
import com.example.feature.editor.PdfExporter
import com.example.feature.files.FileEntry
import com.example.feature.files.FilesActions
import com.example.feature.files.FilesScreen
import com.example.feature.files.FilesUiState
import com.example.feature.files.FilesView
import com.example.feature.files.Place
import com.example.feature.files.SortColumn
import com.example.feature.storage.StorageAnalyzer
import com.example.feature.storage.StorageAnalyzerScreen
import com.example.feature.storage.StorageCategoryFilter
import com.example.ui.feedback.UiState
import com.example.ui.glass.rememberGlass
import com.example.ui.theme.FarTheme
import com.example.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class SuiteUpgradesRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test(timeout = 10_000)
    fun qrParser_parsesAllSupportedFormatsAndEscapedWifi() {
        val url = QrParser.parse("https://photos.example.org/album?id=7")
        assertTrue(url is QrPayload.Url)
        assertEquals("photos.example.org", (url as QrPayload.Url).host)

        val wifi = QrParser.parse("WIFI:T:WPA;S:Studio\\;Net;P:sec\\:ret;;")
        assertTrue(wifi is QrPayload.Wifi)
        wifi as QrPayload.Wifi
        assertEquals("Studio;Net", wifi.ssid)
        assertEquals("WPA", wifi.security)
        assertEquals("sec:ret", wifi.password)

        val phone = QrParser.parse("tel:+1-555-0100")
        assertTrue(phone is QrPayload.Phone)
        assertEquals("+1-555-0100", (phone as QrPayload.Phone).number)

        val email = QrParser.parse("mailto:hello@example.com?subject=Trip%20Photos")
        assertTrue(email is QrPayload.Email)
        email as QrPayload.Email
        assertEquals("hello@example.com", email.address)
        assertEquals("Trip Photos", email.subject)

        val vcard = QrParser.parse("BEGIN:VCARD\nFN:Sara Ali\nTEL:+923001234567\nEMAIL:sara@example.com\nORG:Studio\nEND:VCARD")
        assertTrue(vcard is QrPayload.Contact)
        vcard as QrPayload.Contact
        assertEquals("Sara Ali", vcard.name)
        assertEquals("+923001234567", vcard.phone)

        val plain = QrParser.parse("Just a receipt note #42")
        assertTrue(plain is QrPayload.PlainText)
    }

    @Test(timeout = 10_000)
    fun safeZipInspect_detectsUnsafeTraversalAndTalliesEntries() {
        val cleanBytes = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { z ->
                z.putNextEntry(ZipEntry("docs/readme.txt"))
                z.write("hello".toByteArray())
                z.closeEntry()
            }
        }.toByteArray()

        val clean = SafeZip.inspect(ByteArrayInputStream(cleanBytes))
        assertFalse(clean.hasUnsafeEntries)
        assertEquals(1, clean.entries.size)
        assertEquals("docs/readme.txt", clean.entries.first().path)

        val badBytes = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { z ->
                z.putNextEntry(ZipEntry("../evil.sh"))
                z.write("x".toByteArray())
                z.closeEntry()
            }
        }.toByteArray()

        val dirty = SafeZip.inspect(ByteArrayInputStream(badBytes))
        assertTrue(dirty.hasUnsafeEntries)
        assertFalse(dirty.entries.first().safe)
    }

    @Test(timeout = 10_000)
    fun editState_preservesVignetteAndHandlesLegacyRecipesWithoutV() {
        val state = EditState(
            brightness = 0.1f,
            contrast = 0.42f,
            saturation = -0.88f,
            warmth = -0.06f,
            vignette = 0.35f,
            rotationQuarter = 1,
            flipH = true,
            cropRatio = 1f,
            preset = FilterPreset.DocumentScan.name,
        )
        val parsed = EditState.fromJson(state.toJson())
        assertNotNull(parsed)
        assertEquals(state, parsed)

        val legacyJson = """{"b":0.2,"c":0.1,"s":0.0,"w":0.0,"r":2,"f":false,"crop":null,"preset":"Vivid"}"""
        val legacy = EditState.fromJson(legacyJson)!!
        assertEquals(0f, legacy.vignette, 0.001f)
        assertEquals(2, legacy.rotationQuarter)
    }

    @Test(timeout = 10_000)
    fun duplicateLogic_keepsProtectedAndAtLeastOneUnprotectedCopy() {
        fun row(id: Long, size: Long) = MediaRow(
            id = id,
            uri = Uri.parse("content://media/external/images/media/$id"),
            name = "IMG_$id.jpg",
            mime = "image/jpeg",
            isVideo = false,
            sizeBytes = size,
            takenMs = 1_700_000_000_000L,
            durationMs = 0L,
            width = 1000,
            height = 1000,
            relativePath = "DCIM/Camera/",
        )

        val g1 = DupGroup(
            hash = "h1",
            items = listOf(
                DupItem(row(1, 5000), isFavorite = true, inUserAlbum = false),
                DupItem(row(2, 5000), isFavorite = false, inUserAlbum = false),
            ),
        )
        val g2 = DupGroup(
            hash = "h2",
            items = listOf(
                DupItem(row(3, 8000), isFavorite = false, inUserAlbum = false),
                DupItem(row(4, 8000), isFavorite = false, inUserAlbum = false),
            ),
        )
        val marked = DuplicateLogic.smartSelectExtraCopies(listOf(g1, g2))
        assertEquals(setOf(2L, 4L), marked)
        assertEquals(13_000L, DuplicateLogic.reclaimableBytes(listOf(g1, g2), marked))
    }

    @Test(timeout = 10_000)
    fun storageAnalyzer_filtersCategoriesCorrectly() {
        fun item(id: Long, sizeMb: Long, isVideo: Boolean, path: String) = MediaRow(
            id = id,
            uri = Uri.parse("content://media/external/images/media/$id"),
            name = "F_$id",
            mime = if (isVideo) "video/mp4" else "image/jpeg",
            isVideo = isVideo,
            sizeBytes = sizeMb * 1024 * 1024,
            takenMs = 1_700_000_000_000L,
            durationMs = if (isVideo) 5000L else 0L,
            width = 1920,
            height = 1080,
            relativePath = path,
        )

        val rows = listOf(
            item(1, 40, isVideo = true, path = "Movies/"),
            item(2, 15, isVideo = false, path = "Pictures/Screenshots/"),
            item(3, 20, isVideo = false, path = "DCIM/Camera/"),
        )
        val shots = StorageAnalyzer.summarize(rows, minLargeBytes = 10L * 1024 * 1024, categoryFilter = StorageCategoryFilter.Screenshots)
        assertEquals(1, shots.largeFiles.size)
        assertEquals(2L, shots.largeFiles.first().id)

        val all = StorageAnalyzer.summarize(rows, minLargeBytes = 10L * 1024 * 1024, categoryFilter = StorageCategoryFilter.All)
        assertEquals(3, all.largeFiles.size)
    }

    @Test(timeout = 20_000)
    fun filesScreen_rendersSubfolderToggleAndPinInUrduRtl() {
        var subfolderToggled = false
        var pinToggled = false
        val fakeActions = object : FilesActions {
            override fun openPlace(id: String) {}
            override fun openEntry(e: FileEntry) {}
            override fun navigateToCrumb(index: Int) {}
            override fun up() {}
            override fun setView(v: FilesView) {}
            override fun sortBy(c: SortColumn) {}
            override fun toggleHidden() {}
            override fun toggleSelect(e: FileEntry) {}
            override fun setSearch(query: String) {}
            override fun selectAll() {}
            override fun clearSelection() {}
            override fun batchAction(action: String) {}
            override fun newFolder() {}
            override fun contextAction(e: FileEntry, action: String) {}
            override fun retry() {}
            override fun paste() {}
            override fun toggleSearchSubfolders() { subfolderToggled = true }
            override fun togglePinCurrent() { pinToggled = true }
        }
        val state = FilesUiState(
            places = listOf(Place("starred", "Starred", Icons.Filled.Star)),
            selectedPlaceId = "content://tree/1",
            crumbs = listOf("Downloads"),
            entries = UiState.Content(
                listOf(
                    FileEntry("doc1", "invoice.pdf", false, 4096, "Today", "PDF", "application/pdf", true, true, true)
                )
            ),
            folderSummary = 1 to 4096L,
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                FarTheme(mode = ThemeMode.Amoled) {
                    val glass = rememberGlass()
                    FilesScreen(state = state, actions = fakeActions, glass = glass)
                }
            }
        }

        composeRule.onNodeWithTag("files_pin_button").assertIsDisplayed().performClick()
        assertTrue(pinToggled)
        composeRule.onNodeWithTag("files_search_toggle_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("files_scope_subfolders").assertIsDisplayed().performClick()
        assertTrue(subfolderToggled)
    }

    @Test(timeout = 15_000)
    fun quickShareAndCleanupDialogs_workEndToEnd() {
        var picked: QuickSharePreset? = null
        composeRule.setContent {
            FarTheme(mode = ThemeMode.Light) {
                QuickSharePresetDialog(
                    count = 2,
                    defaultPreset = QuickSharePreset.PrivacyClean,
                    onPickPreset = { picked = it },
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithTag("share_preset_messaging1080").performClick()
        composeRule.onNodeWithTag("share_preset_confirm").performClick()
        assertEquals(QuickSharePreset.Messaging1080, picked)
    }

    @Test(timeout = 15_000)
    fun duplicatesScreen_smartSelectAndClearMarksToggleTrashButton() {
        fun row(id: Long) = MediaRow(
            id = id,
            uri = Uri.parse("content://media/external/images/media/$id"),
            name = "DUP_$id.jpg",
            mime = "image/jpeg",
            isVideo = false,
            sizeBytes = 250_000L,
            takenMs = 1_700_000_000_000L,
            durationMs = 0L,
            width = 1000,
            height = 1000,
            relativePath = "DCIM/Camera/",
        )
        val groups = listOf(
            DupGroup(
                hash = "abc",
                items = listOf(
                    DupItem(row(1), isFavorite = false, inUserAlbum = false),
                    DupItem(row(2), isFavorite = false, inUserAlbum = false),
                ),
            )
        )
        var trashed = 0
        composeRule.setContent {
            FarTheme(mode = ThemeMode.Amoled) {
                DuplicatesScreen(
                    scanning = null,
                    groups = groups,
                    onBack = {},
                    onTrash = { trashed = it.size },
                )
            }
        }
        composeRule.onNodeWithTag("dup_clear_marks_pill").performClick()
        composeRule.onNodeWithTag("dup_smart_select_pill").performClick()
        composeRule.onNodeWithTag("duplicates_trash_button").performClick()
        assertEquals(1, trashed)
    }

    @Test(timeout = 15_000)
    fun storageAnalyzerScreen_supportsCategoryFilterAndBatchTrashSelection() {
        val rows = listOf(
            MediaRow(
                id = 99L,
                uri = Uri.parse("content://media/external/video/media/99"),
                name = "clip_4k.mp4",
                mime = "video/mp4",
                isVideo = true,
                sizeBytes = 42L * 1024 * 1024,
                takenMs = 1_700_000_000_000L,
                durationMs = 20_000L,
                width = 3840,
                height = 2160,
                relativePath = "Movies/",
            )
        )
        var trashedCount = 0
        composeRule.setContent {
            FarTheme(mode = ThemeMode.Amoled) {
                StorageAnalyzerScreen(
                    rows = rows,
                    onBack = {},
                    onPreview = {},
                    onTrash = { trashedCount = it.size },
                )
            }
        }
        composeRule.onNodeWithTag("storage_cat_videos").performClick()
        composeRule.onNodeWithTag("storage_select_99").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("storage_trash_selected_button").assertIsDisplayed().performClick()
        assertEquals(1, trashedCount)
    }

    @Test(timeout = 10_000)
    fun editState_serializesStraightenAndCustomCropAndPreservesLegacyDefaults() {
        val edited = EditState(
            brightness = 0.15f,
            straightenDeg = -14.5f,
            cropLeft = 0.1f,
            cropTop = 0.2f,
            cropRight = 0.85f,
            cropBottom = 0.9f,
        )
        val roundtrip = EditState.fromJson(edited.toJson())!!
        assertEquals(-14.5f, roundtrip.straightenDeg, 0.01f)
        assertEquals(0.1f, roundtrip.cropLeft, 0.001f)
        assertEquals(0.2f, roundtrip.cropTop, 0.001f)
        assertEquals(0.85f, roundtrip.cropRight, 0.001f)
        assertEquals(0.9f, roundtrip.cropBottom, 0.001f)
        assertTrue(roundtrip.hasCustomCrop())

        val legacy = EditState.fromJson("""{"b":0.1,"c":0.0,"s":0.0,"w":0.0,"r":0,"f":false,"crop":null,"preset":null}""")!!
        assertEquals(0f, legacy.straightenDeg, 0.001f)
        assertEquals(0f, legacy.cropLeft, 0.001f)
        assertEquals(1f, legacy.cropRight, 0.001f)
        assertFalse(legacy.hasCustomCrop())
    }

    @Test(timeout = 10_000)
    fun editorEngine_appliesStraightenAutoFitAndCustomCropBounds() {
        val scale = EditorEngine.straightenScale(100, 80, 15f)
        assertTrue(scale > 1.2f)

        val src = Bitmap.createBitmap(100, 80, Bitmap.Config.ARGB_8888).apply {
            eraseColor(AndroidColor.BLUE)
        }
        val rendered = EditorEngine.render(
            src,
            EditState(
                straightenDeg = 10f,
                cropLeft = 0.1f,
                cropTop = 0.2f,
                cropRight = 0.6f,
                cropBottom = 0.7f,
            ),
        )
        assertEquals(50, rendered.width)
        assertEquals(40, rendered.height)
        src.recycle()
        rendered.recycle()
    }

    @Test(timeout = 10_000)
    fun pdfExporter_writesValidPdfDocumentBytes() {
        val bmp = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888).apply {
            eraseColor(AndroidColor.WHITE)
        }
        val out = ByteArrayOutputStream()
        PdfExporter.writeBitmapPdf(bmp, out)
        val bytes = out.toByteArray()
        assertTrue(bytes.size > 100)
        assertEquals("%PDF-", bytes.decodeToString(0, 5))
        bmp.recycle()
    }

    @Test(timeout = 15_000)
    fun searchScreen_displaysSavedSearchAndTagPillsAndTriggersCallbacks() {
        var appliedId: Long? = null
        var clickedTag: String? = null
        val saved = listOf(SavedSearch(id = 7L, name = "Receipts", queryJson = """{"q":"invoice"}"""))
        composeRule.setContent {
            FarTheme(mode = ThemeMode.Amoled) {
                val glass = rememberGlass()
                SearchScreen(
                    query = "",
                    onQuery = {},
                    onSubmitQuery = {},
                    scope = SearchScope.All,
                    onScope = {},
                    dateRange = DateRangeFilter.Any,
                    onDateRange = {},
                    sizeFilter = SizeFilter.Any,
                    onSizeFilter = {},
                    recent = emptyList(),
                    onClearRecent = {},
                    index = IndexStatus.Ready,
                    ocrCount = 3,
                    onRunOcrScan = {},
                    resultCount = null,
                    glass = glass,
                    results = {},
                    allTags = listOf("Travel", "Receipt"),
                    selectedTag = null,
                    onSelectTag = { clickedTag = it },
                    savedSearches = saved,
                    onSaveSearch = {},
                    onApplySavedSearch = { appliedId = it.id },
                    onDeleteSavedSearch = {},
                )
            }
        }
        composeRule.onNodeWithTag("saved_search_chip_7").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(7L, appliedId)
        composeRule.onNodeWithTag("search_tag_travel").performScrollTo().assertIsDisplayed().performClick()
        assertEquals("Travel", clickedTag)
    }
}
