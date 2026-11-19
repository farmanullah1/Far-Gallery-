package com.example.core.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.example.core.common.FarError
import com.example.core.common.FarResult
import com.example.core.common.farCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class DocEntry(
    val docId: String,
    val name: String,
    val mime: String,
    val size: Long?,
    val modified: Long?,
    val flags: Int,
) {
    val isDir get() = mime == Document.MIME_TYPE_DIR
    val canRename get() = flags and Document.FLAG_SUPPORTS_RENAME != 0
    val canDelete get() = flags and Document.FLAG_SUPPORTS_DELETE != 0
    val canMove get() = flags and Document.FLAG_SUPPORTS_MOVE != 0
    val canWriteInto get() = flags and Document.FLAG_DIR_SUPPORTS_CREATE != 0
}

enum class Collision { Rename, Replace, Skip }

sealed interface OpEvent {
    data class Progress(val done: Int, val total: Int, val current: String) : OpEvent
    data class Finished(val done: Int, val skipped: List<String>, val failed: List<Pair<String, FarError>>) : OpEvent
    data class Aborted(val error: FarError) : OpEvent
}

/** All SAF work. Provider quirks are expected: every call is wrapped, and unsupported actions are reported, never faked. */
class DocumentOps(private val r: ContentResolver) {
    private val projection = arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_SIZE,
        Document.COLUMN_LAST_MODIFIED,
        Document.COLUMN_FLAGS,
    )

    fun rootDocId(tree: Uri): String = DocumentsContract.getTreeDocumentId(tree)
    fun docUri(tree: Uri, docId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)

    fun list(tree: Uri, parentDocId: String): FarResult<List<DocEntry>> = farCatching {
        val out = ArrayList<DocEntry>()
        r.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId), projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                out += DocEntry(
                    docId = c.getString(0),
                    name = c.getString(1).orEmpty(),
                    mime = c.getString(2).orEmpty(),
                    size = if (c.isNull(3)) null else c.getLong(3),
                    modified = if (c.isNull(4)) null else c.getLong(4),
                    flags = c.getInt(5),
                )
            }
        } ?: throw IllegalStateException("provider returned null")
        out
    }

    fun create(tree: Uri, parentDocId: String, mime: String, name: String): FarResult<String> = farCatching {
        val u = DocumentsContract.createDocument(r, docUri(tree, parentDocId), mime, name)
            ?: throw java.io.IOException("create failed")
        DocumentsContract.getDocumentId(u)
    }

    fun rename(tree: Uri, docId: String, newName: String): FarResult<Unit> = farCatching {
        DocumentsContract.renameDocument(r, docUri(tree, docId), newName)
            ?: throw java.io.IOException("rename refused")
        Unit
    }

    fun delete(tree: Uri, docId: String): FarResult<Unit> = farCatching {
        if (!DocumentsContract.deleteDocument(r, docUri(tree, docId))) throw java.io.IOException("delete refused")
        Unit
    }

    fun uniqueName(existing: Set<String>, name: String): String {
        if (name !in existing) return name
        val dot = name.lastIndexOf('.').takeIf { it > 0 }
        val base = dot?.let { name.substring(0, it) } ?: name
        val ext = dot?.let { name.substring(it) } ?: ""
        var i = 1
        while ("$base ($i)$ext" in existing) i++
        return "$base ($i)$ext"
    }

    /**
     * Copy or move [items] into destParent: never overwrite silently, verify size before success,
     * delete the source only after the copy is verified, stop cleanly on cancel, keep going after a single-item failure.
     */
    fun transfer(
        srcTree: Uri,
        items: List<DocEntry>,
        dstTree: Uri,
        dstParentDocId: String,
        move: Boolean,
        policy: Collision,
    ): Flow<OpEvent> = flow {
        val skipped = ArrayList<String>()
        val failed = ArrayList<Pair<String, FarError>>()
        var done = 0
        val existing = (list(dstTree, dstParentDocId) as? FarResult.Ok)?.value?.map { it.name }?.toMutableSet()
            ?: run { emit(OpEvent.Aborted(FarError.ProviderUnavailable())); return@flow }

        items.forEachIndexed { index, item ->
            currentCoroutineContext().ensureActive()
            emit(OpEvent.Progress(index, items.size, item.name))
            if (item.name in existing && policy == Collision.Skip) {
                skipped += item.name
                return@forEachIndexed
            }
            var targetName = item.name
            if (item.name in existing) {
                if (policy == Collision.Rename) {
                    targetName = uniqueName(existing, item.name)
                } else if (policy == Collision.Replace) {
                    (list(dstTree, dstParentDocId) as? FarResult.Ok)?.value
                        ?.firstOrNull { it.name == item.name }
                        ?.let { delete(dstTree, it.docId) }
                }
            }
            val res = if (item.isDir) copyDir(srcTree, item, dstTree, dstParentDocId, targetName)
            else copyFile(srcTree, item, dstTree, dstParentDocId, targetName)
            when (res) {
                is FarResult.Ok -> {
                    existing += targetName
                    done++
                    if (move) delete(srcTree, item.docId).let { if (it is FarResult.Err) failed += item.name to it.error }
                }
                is FarResult.Err -> {
                    failed += item.name to res.error
                    if (res.error is FarError.StorageFull || res.error is FarError.VolumeRemoved) {
                        emit(OpEvent.Aborted(res.error))
                        return@flow
                    }
                }
            }
        }
        emit(OpEvent.Finished(done, skipped, failed))
    }.flowOn(Dispatchers.IO)

    private fun copyFile(srcTree: Uri, f: DocEntry, dstTree: Uri, parent: String, name: String): FarResult<Unit> {
        val created = create(dstTree, parent, f.mime.ifEmpty { "application/octet-stream" }, name)
        val newId = (created as? FarResult.Ok)?.value ?: return (created as FarResult.Err)
        val res = farCatching {
            r.openInputStream(docUri(srcTree, f.docId))!!.use { i ->
                r.openOutputStream(docUri(dstTree, newId))!!.use { o -> i.copyTo(o) }
            }
            val got = r.query(docUri(dstTree, newId), arrayOf(Document.COLUMN_SIZE), null, null, null)?.use {
                if (it.moveToFirst()) it.getLong(0) else -1L
            } ?: -1L
            if (f.size != null && got >= 0 && got != f.size) throw java.io.IOException("size mismatch")
        }
        if (res is FarResult.Err) delete(dstTree, newId)
        return res
    }

    private fun copyDir(srcTree: Uri, d: DocEntry, dstTree: Uri, parent: String, name: String, depth: Int = 0): FarResult<Unit> {
        if (depth > 32) return FarResult.Err(FarError.InvalidInput("folder too deep"))
        val newDir = (create(dstTree, parent, Document.MIME_TYPE_DIR, name) as? FarResult.Ok)?.value
            ?: return FarResult.Err(FarError.Io())
        val kids = (list(srcTree, d.docId) as? FarResult.Ok)?.value
            ?: return FarResult.Err(FarError.ProviderUnavailable())
        for (k in kids) {
            val r = if (k.isDir) copyDir(srcTree, k, dstTree, newDir, k.name, depth + 1)
            else copyFile(srcTree, k, dstTree, newDir, k.name)
            if (r is FarResult.Err) return r
        }
        return FarResult.Ok(Unit)
    }

    /** Extract a ZIP file into a newly created subfolder in [dstParentDocId] with SafeZip protection. */
    fun extractZip(tree: Uri, dstParentDocId: String, zipEntry: DocEntry): FarResult<Int> = farCatching {
        val existing = (list(tree, dstParentDocId) as? FarResult.Ok)?.value?.map { it.name }?.toSet() ?: emptySet()
        val baseFolder = uniqueName(existing, zipEntry.name.removeSuffix(".zip").ifBlank { "Extracted" })
        val rootOutId = (create(tree, dstParentDocId, Document.MIME_TYPE_DIR, baseFolder) as? FarResult.Ok)?.value
            ?: throw java.io.IOException("create target folder failed")
        val dirCache = HashMap<String, String>().apply { put("", rootOutId) }

        fun ensureDir(relDir: String): String {
            dirCache[relDir]?.let { return it }
            val parts = relDir.split('/').filter { it.isNotEmpty() }
            var curPath = ""
            var curId = rootOutId
            for (p in parts) {
                curPath = if (curPath.isEmpty()) p else "$curPath/$p"
                curId = dirCache.getOrPut(curPath) {
                    (create(tree, curId, Document.MIME_TYPE_DIR, p) as? FarResult.Ok)?.value
                        ?: throw java.io.IOException("create subfolder failed")
                }
            }
            return curId
        }

        var count = 0
        try {
            r.openInputStream(docUri(tree, zipEntry.docId))!!.use { input ->
                SafeZip.extract(input, zipEntry.size ?: 0L) { path, isDir, data ->
                    if (isDir) {
                        if (path.isNotEmpty()) ensureDir(path)
                    } else {
                        val slash = path.lastIndexOf('/')
                        val parentRel = if (slash > 0) path.substring(0, slash) else ""
                        val fileName = if (slash >= 0) path.substring(slash + 1) else path
                        val parentId = ensureDir(parentRel)
                        val docId = (create(tree, parentId, "application/octet-stream", fileName) as? FarResult.Ok)?.value
                            ?: throw java.io.IOException("create extracted file failed")
                        r.openOutputStream(docUri(tree, docId))!!.use { out -> data.copyTo(out) }
                        count++
                    }
                }
            }
        } catch (t: Throwable) {
            delete(tree, rootOutId)
            throw t
        }
        count
    }

    /** Compress selected SAF items (files and folders) into a new .zip file in [dstParentDocId]. */
    fun createZip(
        tree: Uri,
        dstParentDocId: String,
        items: List<DocEntry>,
        archiveName: String,
        onProgress: (done: Int, current: String) -> Unit = { _, _ -> },
    ): FarResult<String> = farCatching {
        val existing = (list(tree, dstParentDocId) as? FarResult.Ok)?.value?.map { it.name }?.toSet() ?: emptySet()
        val cleanBase = archiveName.trim().removeSuffix(".zip").ifBlank { "Archive" }
        val finalName = uniqueName(existing, "$cleanBase.zip")
        val docId = (create(tree, dstParentDocId, "application/zip", finalName) as? FarResult.Ok)?.value
            ?: throw java.io.IOException("create archive failed")

        fun collect(entry: DocEntry, prefix: String, acc: MutableList<Pair<String, DocEntry?>>, depth: Int = 0) {
            if (depth > 32) return
            val rel = if (prefix.isEmpty()) entry.name else "$prefix/${entry.name}"
            if (entry.isDir) {
                acc += "$rel/" to null
                val children = (list(tree, entry.docId) as? FarResult.Ok)?.value.orEmpty()
                children.forEach { child -> collect(child, rel, acc, depth + 1) }
            } else {
                acc += rel to entry
            }
        }

        val queue = ArrayList<Pair<String, DocEntry?>>()
        items.forEach { collect(it, "", queue) }

        try {
            r.openOutputStream(docUri(tree, docId))!!.use { out ->
                var done = 0
                SafeZip.create(
                    entries = queue.asSequence().map { (path, entry) ->
                        val isDir = entry == null
                        val name = path.substringAfterLast('/', path).ifBlank { path }
                        onProgress(done++, name)
                        Triple(path, isDir) {
                            if (entry == null) null else r.openInputStream(docUri(tree, entry.docId))
                        }
                    },
                    output = out,
                )
            }
        } catch (t: Throwable) {
            delete(tree, docId)
            throw t
        }
        finalName
    }
}

/* ------------------------------ Checksums ------------------------------ */
object Checksums {
    fun sha256(input: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

/* ------------------------------ Safe ZIP extraction ------------------------------ */
object SafeZip {
    const val MAX_ENTRIES = 5_000
    const val MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024
    const val MAX_RATIO = 200

    /** Reject absolute paths, "..", drive prefixes and NULs. Returns null if unsafe. */
    fun sanitize(raw: String): String? {
        if (raw.contains('\u0000') || raw.startsWith("/") || raw.startsWith("\\") || Regex("^[A-Za-z]:").containsMatchIn(raw)) {
            return null
        }
        val parts = raw.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.any { it == ".." }) return null
        return parts.joinToString("/")
    }

    /** Streams entries to [sink]; throws IllegalArgumentException on any safety-limit violation so caller cleans partial output. */
    fun extract(input: InputStream, compressedSize: Long, sink: (path: String, isDir: Boolean, data: InputStream) -> Unit) {
        var entries = 0
        var total = 0L
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (++entries > MAX_ENTRIES) throw IllegalArgumentException("too many entries")
                val safe = sanitize(e.name) ?: throw IllegalArgumentException("unsafe path")
                if (e.isDirectory) {
                    sink(safe, true, z)
                    continue
                }
                val counting = object : InputStream() {
                    override fun read(): Int {
                        val b = z.read()
                        if (b >= 0) bump(1)
                        return b
                    }

                    override fun read(buf: ByteArray, off: Int, len: Int): Int {
                        val n = z.read(buf, off, len)
                        if (n > 0) bump(n.toLong())
                        return n
                    }

                    fun bump(n: Long) {
                        total += n
                        if (total > MAX_TOTAL_BYTES) throw IllegalArgumentException("archive too large")
                        if (compressedSize > 0 && total / compressedSize > MAX_RATIO) {
                            throw IllegalArgumentException("suspicious compression ratio")
                        }
                    }
                }
                sink(safe, false, counting)
            }
        }
    }

    /** Creates a ZIP archive onto [output] with sanitized relative entry names and size/entry safety caps. */
    fun create(entries: Sequence<Triple<String, Boolean, () -> InputStream?>>, output: OutputStream): Int {
        var count = 0
        ZipOutputStream(output).use { zos ->
            for ((rawPath, isDir, openStream) in entries) {
                if (++count > MAX_ENTRIES) throw IllegalArgumentException("too many entries")
                val safe = sanitize(rawPath) ?: continue
                if (safe.isEmpty()) continue
                if (isDir) {
                    val dirName = if (safe.endsWith("/")) safe else "$safe/"
                    zos.putNextEntry(ZipEntry(dirName))
                    zos.closeEntry()
                } else {
                    zos.putNextEntry(ZipEntry(safe))
                    openStream()?.use { input -> input.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
        return count
    }
}
