package com.example

import android.app.Application
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.FarError
import com.example.core.common.FarResult
import com.example.core.storage.Checksums
import com.example.core.storage.Collision
import com.example.core.storage.DocEntry
import com.example.core.storage.DocumentOps
import com.example.core.storage.SafeZip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class SafOpsAndConflictTest {

    @Test
    fun safeZip_rejectsTraversalAndWindowsDrivePaths() {
        assertNull(SafeZip.sanitize("../secret.txt"))
        assertNull(SafeZip.sanitize("folder/../../etc/passwd"))
        assertNull(SafeZip.sanitize("/etc/passwd"))
        assertNull(SafeZip.sanitize("\\windows\\system32\\cmd.exe"))
        assertNull(SafeZip.sanitize("C:\\users\\test.txt"))
        assertNull(SafeZip.sanitize("bad\u0000name.txt"))
        assertEquals("docs/readme.txt", SafeZip.sanitize("docs\\./readme.txt"))
    }

    @Test
    fun safeZip_createInspectExtractRoundTripPreservesBytes() {
        val payloadA = "Hello Far Gallery Archive".toByteArray()
        val payloadB = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val zipOut = ByteArrayOutputStream()
        val count = SafeZip.create(
            entries = sequenceOf(
                Triple("notes/", true) { null },
                Triple("notes/hello.txt", false) { ByteArrayInputStream(payloadA) },
                Triple("bin/data.bin", false) { ByteArrayInputStream(payloadB) },
            ),
            output = zipOut,
        )
        assertEquals(3, count)

        val zipBytes = zipOut.toByteArray()
        val inspection = SafeZip.inspect(ByteArrayInputStream(zipBytes))
        assertFalse(inspection.hasUnsafeEntries)
        assertEquals(3, inspection.entries.size)
        assertTrue(inspection.entries.any { it.path == "notes/hello.txt" && !it.isDir })

        val extracted = mutableMapOf<String, ByteArray>()
        SafeZip.extract(ByteArrayInputStream(zipBytes), zipBytes.size.toLong()) { path, isDir, data ->
            if (!isDir) extracted[path] = data.readBytes()
        }
        assertTrue(payloadA.contentEquals(extracted["notes/hello.txt"]))
        assertTrue(payloadB.contentEquals(extracted["bin/data.bin"]))
    }

    @Test
    fun documentOps_uniqueNameAndSha256WorkDeterministically() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val ops = DocumentOps(app.contentResolver)
        val existing = setOf("report.pdf", "report (1).pdf", "photo")
        assertEquals("report (2).pdf", ops.uniqueName(existing, "report.pdf"))
        assertEquals("photo (1)", ops.uniqueName(existing, "photo"))
        assertEquals("fresh.txt", ops.uniqueName(existing, "fresh.txt"))

        val digest = Checksums.sha256(ByteArrayInputStream("abc".toByteArray()))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", digest)
    }

    @Test
    fun documentOps_findConflictsAndUnsupportedRenameHandledCleanly() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val tree = Uri.parse("content://com.example.fake.docs/tree/root")
        val provider = FakeDocsProvider(
            children = listOf(
                DocEntry("doc1", "invoice.pdf", "application/pdf", 120L, 1000L, 0),
                DocEntry("doc2", "notes.txt", "text/plain", 40L, 1000L, DocumentsContract.Document.FLAG_SUPPORTS_RENAME),
            )
        )
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("com.example.fake.docs", provider)
        val ops = DocumentOps(app.contentResolver)

        val candidates = listOf(
            DocEntry("x1", "invoice.pdf", "application/pdf", 120L, 1000L, 0),
            DocEntry("x2", "unique.jpg", "image/jpeg", 500L, 1000L, 0),
        )
        val conflicts = ops.findConflicts(tree, "root", candidates)
        assertEquals(listOf("invoice.pdf"), conflicts)

        val renameBlocked = ops.rename(tree, "doc1", "renamed.pdf")
        assertTrue(renameBlocked is FarResult.Err && renameBlocked.error is FarError.Unsupported)

        assertEquals(3, Collision.entries.size)
    }

    private class FakeDocsProvider(private val children: List<DocEntry>) : android.content.ContentProvider() {
        override fun onCreate() = true
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): android.database.Cursor {
            val cols = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_FLAGS,
            )
            val c = MatrixCursor(cols)
            val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            val list = if (uri.pathSegments.contains("children")) children
            else children.filter { it.docId == docId }
            list.forEach { d -> c.addRow(arrayOf<Any?>(d.docId, d.name, d.mime, d.size, d.modified, d.flags)) }
            return c
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: android.content.ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: android.content.ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }
}
