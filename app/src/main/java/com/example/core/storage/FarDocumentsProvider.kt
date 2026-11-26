package com.example.core.storage

import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.util.Base64
import com.example.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileNotFoundException

data class ExposedRoot(
    val treeUri: String,
    val displayName: String,
    val addedAt: Long,
)

/**
 * Opt-in registry of SAF folder trees that the user explicitly chose to expose via [FarDocumentsProvider].
 * Nothing is exposed by default; Private Vault, app internal files, and hidden caches are never exposed.
 */
object ExposedDocumentsStore {
    private const val PREFS_NAME = "far_exposed_documents"
    private const val KEY_ROOTS_JSON = "roots_json"

    fun authority(ctx: Context): String = "${ctx.packageName}.documents"

    fun listExposed(ctx: Context): List<ExposedRoot> = runCatching {
        val raw = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ROOTS_JSON, null)
            ?.takeIf { it.isNotBlank() }
            ?: return@runCatching emptyList()
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val tree = obj.optString("treeUri").trim()
                val name = obj.optString("displayName").trim().ifEmpty { "Folder" }
                val added = obj.optLong("addedAt", 0L)
                if (tree.startsWith("content://") && !tree.contains(authority(ctx))) {
                    add(ExposedRoot(treeUri = tree, displayName = name, addedAt = added))
                }
            }
        }
    }.getOrDefault(emptyList())

    fun isExposed(ctx: Context, treeUri: String): Boolean =
        listExposed(ctx).any { it.treeUri == treeUri }

    fun exposeFolder(ctx: Context, treeUri: Uri, displayName: String) {
        val rawTree = treeUri.toString().trim()
        if (!rawTree.startsWith("content://") || rawTree.contains(authority(ctx))) return
        val current = listExposed(ctx).filterNot { it.treeUri == rawTree }
        val updated = current + ExposedRoot(
            treeUri = rawTree,
            displayName = displayName.trim().ifEmpty { "Folder" },
            addedAt = System.currentTimeMillis(),
        )
        saveList(ctx, updated)
    }

    fun unexposeFolder(ctx: Context, treeUri: String) {
        val current = listExposed(ctx)
        val filtered = current.filterNot { it.treeUri == treeUri }
        if (filtered.size != current.size) {
            saveList(ctx, filtered)
        }
    }

    fun clearAll(ctx: Context) {
        saveList(ctx, emptyList())
    }

    private fun saveList(ctx: Context, roots: List<ExposedRoot>) {
        val arr = JSONArray()
        roots.forEach { r ->
            arr.put(
                JSONObject().apply {
                    put("treeUri", r.treeUri)
                    put("displayName", r.displayName)
                    put("addedAt", r.addedAt)
                }
            )
        }
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ROOTS_JSON, arr.toString())
            .apply()
        runCatching {
            ctx.contentResolver.notifyChange(DocumentsContract.buildRootsUri(authority(ctx)), null)
        }
    }
}

/**
 * Local-only DocumentsProvider exposing ONLY SAF folders explicitly approved in [ExposedDocumentsStore].
 * Rejects write modes and never exposes Private Vault, databases, or internal app files.
 */
class FarDocumentsProvider : DocumentsProvider() {

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cols = projection ?: DEFAULT_ROOT_PROJECTION
        val cursor = MatrixCursor(cols)
        val ctx = context ?: return cursor
        ExposedDocumentsStore.listExposed(ctx).forEach { root ->
            val parsedTree = runCatching { Uri.parse(root.treeUri) }.getOrNull() ?: return@forEach
            val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(parsedTree) }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: return@forEach
            val encodedDocId = encodeDocId(root.treeUri, rootDocId)
            val rootId = encodeRootId(root.treeUri)
            val row = cursor.newRow()
            cols.forEach { col ->
                when (col) {
                    Root.COLUMN_ROOT_ID -> row.add(rootId)
                    Root.COLUMN_DOCUMENT_ID -> row.add(encodedDocId)
                    Root.COLUMN_TITLE -> row.add(root.displayName)
                    Root.COLUMN_SUMMARY -> row.add(ctx.getString(R.string.provider_root_summary))
                    Root.COLUMN_FLAGS -> row.add(Root.FLAG_LOCAL_ONLY)
                    Root.COLUMN_MIME_TYPES -> row.add("*/*")
                    Root.COLUMN_ICON -> row.add(R.mipmap.ic_launcher_files)
                    else -> row.add(null)
                }
            }
        }
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val ctx = context ?: throw FileNotFoundException("Provider detached")
        val cols = projection ?: DEFAULT_DOCUMENT_PROJECTION
        val (treeUriStr, docId) = requireAllowedDoc(ctx, documentId)
        val treeUri = Uri.parse(treeUriStr)
        val targetUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        val cursor = MatrixCursor(cols)
        val queryCols = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
        )
        runCatching {
            ctx.contentResolver.query(targetUri, queryCols, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val underlyingId = c.getString(0)?.takeIf { it.isNotBlank() } ?: docId
                    val name = c.getString(1).orEmpty()
                    val mime = c.getString(2).orEmpty().ifBlank { Document.MIME_TYPE_DIR }
                    val size = if (c.isNull(3)) null else c.getLong(3)
                    val modified = if (c.isNull(4)) null else c.getLong(4)
                    val flags = maskReadOnlyFlags(c.getInt(5))
                    addDocRow(cursor, cols, encodeDocId(treeUriStr, underlyingId), name, mime, size, modified, flags)
                }
            }
        }
        if (cursor.count == 0) {
            val exposed = ExposedDocumentsStore.listExposed(ctx).firstOrNull { it.treeUri == treeUriStr }
                ?: throw FileNotFoundException("Document not found")
            val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            if (docId == rootDocId) {
                addDocRow(cursor, cols, documentId, exposed.displayName, Document.MIME_TYPE_DIR, null, exposed.addedAt, 0)
            } else {
                throw FileNotFoundException("Document not found")
            }
        }
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val ctx = context ?: throw FileNotFoundException("Provider detached")
        val cols = projection ?: DEFAULT_DOCUMENT_PROJECTION
        val (treeUriStr, parentDocId) = requireAllowedDoc(ctx, parentDocumentId)
        val treeUri = Uri.parse(treeUriStr)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val cursor = MatrixCursor(cols)
        val queryCols = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
        )
        ctx.contentResolver.query(childrenUri, queryCols, null, null, sortOrder)?.use { c ->
            while (c.moveToNext()) {
                val childId = c.getString(0) ?: continue
                val name = c.getString(1).orEmpty()
                val mime = c.getString(2).orEmpty().ifBlank { "application/octet-stream" }
                val size = if (c.isNull(3)) null else c.getLong(3)
                val modified = if (c.isNull(4)) null else c.getLong(4)
                val flags = maskReadOnlyFlags(c.getInt(5))
                addDocRow(cursor, cols, encodeDocId(treeUriStr, childId), name, mime, size, modified, flags)
            }
        }
        return cursor
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        if (mode != "r") {
            throw SecurityException("Far DocumentsProvider exposes read-only access")
        }
        val ctx = context ?: throw FileNotFoundException("Provider detached")
        val (treeUriStr, docId) = requireAllowedDoc(ctx, documentId)
        val targetUri = DocumentsContract.buildDocumentUriUsingTree(Uri.parse(treeUriStr), docId)
        return ctx.contentResolver.openFileDescriptor(targetUri, "r", signal)
            ?: throw FileNotFoundException("Unable to open document")
    }

    override fun openDocumentThumbnail(
        documentId: String,
        sizeHint: Point?,
        signal: CancellationSignal?,
    ): AssetFileDescriptor? {
        val ctx = context ?: return null
        val (treeUriStr, docId) = decodeDocId(documentId) ?: return null
        if (!isRootAllowed(ctx, treeUriStr)) return null
        val targetUri = DocumentsContract.buildDocumentUriUsingTree(Uri.parse(treeUriStr), docId)
        val hint = sizeHint ?: Point(256, 256)
        val opts = Bundle().apply { putParcelable(ContentResolver.EXTRA_SIZE, hint) }
        return runCatching {
            ctx.contentResolver.openTypedAssetFileDescriptor(targetUri, "image/*", opts, signal)
        }.getOrNull()
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val ctx = context ?: return false
        val parent = decodeDocId(parentDocumentId) ?: return false
        val child = decodeDocId(documentId) ?: return false
        if (parent.first != child.first || !isRootAllowed(ctx, parent.first)) return false
        return true
    }

    private fun requireAllowedDoc(ctx: Context, encodedDocumentId: String): Pair<String, String> {
        val decoded = decodeDocId(encodedDocumentId)
            ?: throw SecurityException("Invalid document identifier")
        if (!isRootAllowed(ctx, decoded.first)) {
            throw SecurityException("Folder is not explicitly exposed by user")
        }
        return decoded
    }

    private fun isRootAllowed(ctx: Context, treeUriStr: String): Boolean =
        treeUriStr.startsWith("content://") &&
            !treeUriStr.contains(ExposedDocumentsStore.authority(ctx)) &&
            ExposedDocumentsStore.isExposed(ctx, treeUriStr)

    private fun maskReadOnlyFlags(rawFlags: Int): Int {
        val allowed = Document.FLAG_SUPPORTS_THUMBNAIL
        return rawFlags and allowed
    }

    private fun addDocRow(
        cursor: MatrixCursor,
        cols: Array<out String>,
        encodedDocId: String,
        name: String,
        mime: String,
        size: Long?,
        modified: Long?,
        flags: Int,
    ) {
        val row = cursor.newRow()
        cols.forEach { col ->
            when (col) {
                Document.COLUMN_DOCUMENT_ID -> row.add(encodedDocId)
                Document.COLUMN_DISPLAY_NAME -> row.add(name)
                Document.COLUMN_MIME_TYPE -> row.add(mime)
                Document.COLUMN_SIZE -> row.add(size)
                Document.COLUMN_LAST_MODIFIED -> row.add(modified)
                Document.COLUMN_FLAGS -> row.add(flags)
                else -> row.add(null)
            }
        }
    }

    companion object {
        private const val DELIM = "::"
        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_DOCUMENT_ID,
        )
        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
        )

        internal fun encodeRootId(treeUri: String): String =
            Base64.encodeToString(treeUri.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

        internal fun encodeDocId(treeUri: String, childDocId: String): String =
            "${encodeRootId(treeUri)}$DELIM$childDocId"

        internal fun decodeDocId(encoded: String): Pair<String, String>? {
            val idx = encoded.indexOf(DELIM)
            if (idx <= 0) return null
            val b64Tree = encoded.substring(0, idx)
            val childId = encoded.substring(idx + DELIM.length)
            if (childId.isEmpty()) return null
            val tree = runCatching {
                String(Base64.decode(b64Tree, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)
            }.getOrNull()?.takeIf { it.startsWith("content://") } ?: return null
            return tree to childId
        }
    }
}
