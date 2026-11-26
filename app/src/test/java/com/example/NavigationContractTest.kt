package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.nav.FarTab
import com.example.ui.nav.PhotosSubTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationContractTest {

    @Test
    fun FarTab_declares_three_primary_destinations_with_accessibility_labels() {
        val tabs = FarTab.entries.toSet()
        assertEquals(
            setOf(FarTab.Photos, FarTab.Camera, FarTab.Files),
            tabs
        )
        tabs.forEach { tab ->
            assertNotNull("FarTab.${tab.name} must carry labelRes", tab.labelRes)
            assertNotNull("FarTab.${tab.name} must carry filled icon vector", tab.filled)
            assertNotNull("FarTab.${tab.name} must carry outlined icon vector", tab.outlined)
            assertNotNull("FarTab.${tab.name} must carry test tag", tab.tag)
        }
    }

    @Test
    fun PhotosSubTab_declares_library_albums_search_secondary_nav_with_accessibility_labels() {
        val subs = PhotosSubTab.entries.toSet()
        assertEquals(
            setOf(PhotosSubTab.Library, PhotosSubTab.Albums, PhotosSubTab.Search),
            subs
        )
        subs.forEach { sub ->
            assertNotNull("PhotosSubTab.${sub.name} must carry labelRes", sub.labelRes)
            assertNotNull("PhotosSubTab.${sub.name} must carry test tag", sub.tag)
        }
    }
}
