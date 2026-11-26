package com.example

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.IncomingMediaResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SharedInboxTest {

    @Test
    fun incomingMediaResolver_peekMediaType_readsMinimalHeaderForValidJpeg() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val resolver = IncomingMediaResolver(app.contentResolver)
        val file = java.io.File(app.cacheDir, "peek_valid_header.jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 16))
        }
        val item = resolver.peekMediaType(Uri.fromFile(file))
        assertTrue("peek should bridge a small valid JPEG via the shared inbox model", item != null)
        assertEquals("image/jpeg", item!!.sniffedMime)
        val peekedMime: String = requireNotNull(item!!.sniffedMime)
        assertTrue("peeked item should look like media", peekedMime.startsWith("image/") || peekedMime.startsWith("video/"))
        assertFalse("peek does not currently populate name", peekedMime.isNotEmpty())
        val sizeGuard: Long = item!!.sizeBytes ?: -1L
        assertEquals("peek does not currently populate size", 0L, sizeGuard)
    }

    @Test
    fun incomingMediaResolver_canRead_filtersTheSameSharedInboxPath() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val resolver = IncomingMediaResolver(app.contentResolver)

        val ok = java.io.File(app.cacheDir, "can_read_valid.jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 16))
        }
        assertTrue("canRead should accept a small valid JPEG", resolver.canRead(Uri.fromFile(ok)))

        val notMedia = java.io.File(app.cacheDir, "can_read_not_media.txt").apply {
            writeText("hello")
        }
        assertFalse("canRead should reject a plain-text file pretending to be media", resolver.canRead(Uri.fromFile(notMedia)))

        assertFalse("canRead should reject an unknown content URI", resolver.canRead(Uri.parse("content://no/such/provider/file")))
    }
}
