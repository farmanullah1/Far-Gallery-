package com.example

import android.app.Application
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.core.app.ApplicationProvider
import com.example.app.FarApp
import com.example.app.IncomingRoute
import com.example.app.IntentRouter
import com.example.core.database.FarDatabase
import com.example.feature.gallery.GalleryItem
import com.example.feature.gallery.GalleryScreen
import com.example.feature.gallery.LibraryZoom
import com.example.feature.settings.SettingsStore
import com.example.ui.glass.rememberGlass
import com.example.ui.theme.FarTheme
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AppStartupRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test(timeout = 20_000)
    fun settingsStore_recoversFromCorruptDataStoreFile() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dsDir = File(app.filesDir, "datastore").apply { mkdirs() }
        File(dsDir, "far_settings.preferences_pb").writeBytes(byteArrayOf(0x7F, 0x00, 0x13, 0x37))
        val settings = SettingsStore(app).flow.first()
        assertNotNull(settings)
        assertEquals(ThemeMode.System, settings.theme)
    }

    @Test(timeout = 20_000)
    fun farDatabase_recoversFromIncompatibleExistingSchema() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dbFile = app.getDatabasePath("far.db").apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { raw ->
            raw.execSQL("CREATE TABLE IF NOT EXISTS legacy_table (id INTEGER PRIMARY KEY)")
            raw.version = 1
        }
        val db = FarDatabase.get(app)
        db.operations().markInterrupted(System.currentTimeMillis())
        assertEquals(0, db.albums().albums().first().size)
    }

    @Test(timeout = 30_000)
    fun farApp_bootsThroughOnboardingIntoAllMainTabsWithoutCrash() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = controller.get()
        val vm = AppViewModel(activity.application)

        composeRule.setContent {
            FarApp(vm = vm, activity = activity, startOnFiles = false)
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("onboarding_skip_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("onboarding_skip_button").assertIsDisplayed().performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("tab_albums").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("tab_albums").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("tab_search").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("tab_files").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("tab_photos").performClick()
        composeRule.waitForIdle()

        vm.update { it.copy(theme = ThemeMode.Amoled) }
        composeRule.waitForIdle()

        controller.pause().stop().destroy()
    }

    @Test(timeout = 20_000)
    fun galleryScreen_rendersPopulatedGridAndScrollbarWithoutCrash() {
        val sample = buildList<GalleryItem> {
            add(GalleryItem.Header(title = "Today", anchorId = 1L))
            repeat(24) { idx ->
                val id = idx + 1L
                add(
                    GalleryItem.Media(
                        id = id,
                        uri = "content://media/external/images/media/$id",
                        description = "Photo $id",
                        isVideo = idx % 5 == 0,
                        durationLabel = if (idx % 5 == 0) "0:12" else null,
                        takenMs = 1_700_000_000_000L - idx * 1000L,
                    )
                )
            }
        }

        composeRule.setContent {
            FarTheme(mode = ThemeMode.Amoled) {
                val glass = rememberGlass()
                val pagingItems = flowOf(PagingData.from(sample)).collectAsLazyPagingItems()
                GalleryScreen(
                    items = pagingItems,
                    glass = glass,
                    zoom = LibraryZoom.All,
                    onZoomChange = {},
                    selection = emptySet(),
                    onOpen = {},
                    onToggleSelect = {},
                    onRetry = {},
                    onRequestAccess = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("gallery_grid").assertIsDisplayed()
    }

    @Test(timeout = 10_000)
    fun intentRouter_handlesEmptyAndUnsupportedIntentsSafely() {
        assertEquals(IncomingRoute.None, IntentRouter.classify(null))
        assertEquals(IncomingRoute.None, IntentRouter.classify(Intent("com.example.UNKNOWN")))
    }

    @Test(timeout = 20_000)
    fun cameraActivity_createsAndResumesWithoutCrash() {
        val controller = Robolectric.buildActivity(
            CameraActivity::class.java,
            Intent(ApplicationProvider.getApplicationContext(), CameraActivity::class.java),
        ).create().start().resume()
        assertNotNull(controller.get())
        controller.pause().stop().destroy()
    }
}
