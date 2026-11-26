package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.nav.FarTab
import com.example.ui.nav.PhotosSubTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GalleryScreenNavigationAuditTest {

    @Test
    fun PhotosSubTab_declares_library_albums_search_secondary_nav_with_accessibility_labels() {
        val subs = PhotosSubTab.entries.toSet()
        assertEquals(
            setOf(PhotosSubTab.Library, PhotosSubTab.Albums, PhotosSubTab.Search),
            subs
        )
        subs.forEach { sub ->
            assertNotNull("PhotosSubTab must carry a labelRes for TalkBack", sub.labelRes)
            assertNotNull("PhotosSubTab must carry a test tag for UI automation", sub.tag)
        }
    }

    @Test
    fun FarTab_declares_three_primary_destinations_each_with_accessibility_labels() {
        val tabs = FarTab.entries.toSet()
        assertEquals(
            setOf(FarTab.Photos, FarTab.Camera, FarTab.Files),
            tabs
        )
        tabs.forEach { tab ->
            assertNotNull("FarTab must carry a labelRes for TalkBack", tab.labelRes)
            assertNotNull("FarTab must carry a filled icon vector", tab.filled)
            assertNotNull("FarTab must carry an outlined icon vector", tab.outlined)
            assertNotNull("FarTab must carry a test tag string for UI automation", tab.tag)
        }
    }
}
