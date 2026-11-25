package com.example

import android.app.Application
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.FarResult
import com.example.core.database.FarDatabase
import com.example.core.database.OcrIndexEntity
import com.example.core.database.QuarantineItemEntity
import com.example.core.media.GalleryViewModel
import com.example.core.media.MediaFilter
import com.example.core.media.MediaRepository
import com.example.core.media.MediaScanner
import com.example.core.media.SortKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LargeLibraryScaleTest {

    private class FakeMediaProvider(var itemCount: Int = 3250) : ContentProvider() {
        private val cols = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor = querySlice(0, itemCount)

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            queryArgs: Bundle?,
            cancellationSignal: CancellationSignal?,
        ): Cursor {
            val offset = queryArgs?.getInt(android.content.ContentResolver.QUERY_ARG_OFFSET, 0) ?: 0
            val limit = queryArgs?.getInt(android.content.ContentResolver.QUERY_ARG_LIMIT, itemCount) ?: itemCount
            return querySlice(offset, limit)
        }

        private fun querySlice(offset: Int, limit: Int): Cursor {
            val cursor = MatrixCursor(cols)
            val end = (offset + limit).coerceAtMost(itemCount)
            for (i in offset until end) {
                val id = (i + 1).toLong()
                val isVid = i % 10 == 0
                cursor.addRow(
                    arrayOf<Any>(
                        id,
                        if (isVid) "VID_$id.mp4" else "IMG_$id.jpg",
                        if (isVid) "video/mp4" else "image/jpeg",
                        if (isVid) MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO else MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE,
                        1024L * 100L,
                        1_700_000_000_000L - i * 1000L,
                        1_700_000_000L - i,
                        if (isVid) 5000L else 0L,
                        1920,
                        1080,
                        "DCIM/Camera/",
                    )
                )
            }
            return cursor
        }

        override fun getType(uri: Uri): String = "image/jpeg"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }

    @Test
    fun chunkedQueryAndScanner_exceed2000CapAndPreserveCapturedByFar() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val provider = FakeMediaProvider(itemCount = 3250)
        provider.onCreate()
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY, provider)

        val repo = MediaRepository(app.contentResolver)
        val allRes = repo.queryAll(MediaFilter(), SortKey.Taken, descending = true)
        assertTrue(allRes is FarResult.Ok)
        val rows = (allRes as FarResult.Ok).value
        assertEquals(3250, rows.size)

        val statsRes = repo.computeStats(MediaFilter())
        assertTrue(statsRes is FarResult.Ok)
        val stats = (statsRes as FarResult.Ok).value
        assertEquals(3250, stats.photoCount + stats.videoCount)
        assertEquals(3250L * 102400L, stats.totalBytes)

        val db = Room.inMemoryDatabaseBuilder(app, FarDatabase::class.java).allowMainThreadQueries().build()
        try {
            val capturedUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 42L)
            MediaScanner.markCapturedByFar(app, db, capturedUri)

            val sync1 = MediaScanner.syncIncremental(app, db)
            assertTrue(sync1 is FarResult.Ok)
            assertEquals(3250, (sync1 as FarResult.Ok).value.syncedCount)
            assertEquals(3250, db.mediaAssets().count())
            assertTrue(db.mediaAssets().get(42L)?.capturedByFar == true)
            assertNotNull(db.scanCheckpoints().get(MediaScanner.SCOPE_MEDIA_STORE))

            // Shrink provider to simulate deletion & verify stale rows are pruned while capturedByFar stays for #42
            provider.itemCount = 1200
            Thread.sleep(2)
            val sync2 = MediaScanner.syncIncremental(app, db)
            assertTrue(sync2 is FarResult.Ok)
            assertEquals(1200, db.mediaAssets().count())
            assertTrue(db.mediaAssets().get(42L)?.capturedByFar == true)
        } finally {
            db.close()
        }
    }

    @Test
    fun ocrFts4Search_andFolderRulesAndQuarantineWork() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, FarDatabase::class.java).allowMainThreadQueries().build()
        try {
            db.ocr().upsert(OcrIndexEntity(1L, "content://1", "Passport Tokyo Gate B12", 100L))
            db.ocr().upsert(OcrIndexEntity(2L, "content://2", "Monthly Receipt #449-A", 100L))

            assertEquals(listOf(1L), db.ocr().searchIds("tokyo b12"))
            assertEquals(listOf(2L), db.ocr().searchIds("449-A"))

            val gvm = GalleryViewModel(MediaRepository(app.contentResolver), db)
            gvm.updateFolderRule("Pictures/Private/", hidden = true, excludedFromTimeline = true, excludedFromOcr = true)
            repeat(20) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                if ("Pictures/Private/" in gvm.excludedFolders.value) return@repeat
                kotlinx.coroutines.delay(25)
            }
            val excluded = gvm.excludedFolders.value
            assertTrue("Pictures/Private/" in excluded)

            db.quarantine().upsert(QuarantineItemEntity("content://bad", "bad.jpg", "corrupt", "FG-CRPT", 100L))
            repeat(20) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                if (gvm.quarantineItems.value.isNotEmpty()) return@repeat
                kotlinx.coroutines.delay(25)
            }
            assertEquals(1, gvm.quarantineItems.value.size)
            gvm.dismissQuarantine("content://bad")
            repeat(20) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                if (gvm.quarantineItems.value.isEmpty()) return@repeat
                kotlinx.coroutines.delay(25)
            }
            assertFalse(gvm.quarantineItems.value.any { it.sourceUri == "content://bad" })
        } finally {
            db.close()
        }
    }
}
