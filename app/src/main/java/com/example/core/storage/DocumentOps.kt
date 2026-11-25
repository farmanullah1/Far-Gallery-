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
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
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

data class SearchHit(
    val entry: DocEntry,
    val relPath: String,
    val breadcrumb: List<Pair<String, String>>,
)

data class ZipEntrySummary(
    val path: String,
    val isDir: Boolean,
    val sizeBytes: Long,
    val safe: Boolean,
)

data class ZipInspection(
    val entries: List<ZipEntrySummary>,
    val totalUncompressedBytes: Long,
    val hasUnsafeEntries: Boolean,
    val truncated: Boolean,
)

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

    fun inspectDoc(documentUri: Uri): DocEntry? = runCatching {
        r.query(documentUri, projection, null, null, null)?.use { c ->
            if (!c.moveToFirst()) null
            else DocEntry(
                docId = c.getString(0),
                name = c.getString(1).orEmpty(),
                mime = c.getString(2).orEmpty(),
                size = if (c.isNull(3)) null else c.getLong(3),
                modified = if (c.isNull(4)) null else c.getLong(4),
                flags = c.getInt(5),
            )
        }
    }.getOrNull()

    /**
     * Bounded, cancellable BFS across subfolders of [startDocId].
     * Emits cumulative matches periodically so large SD trees stay responsive.
     */
    fun searchRecursive(
        tree: Uri,
        startDocId: String,
        startLabel: String,
        query: String,
        showHidden: Boolean,
        maxDepth: Int = 5,
        maxResults: Int = 200,
    ): Flow<List<SearchHit>> = flow {
        val needle = query.trim()
        if (needle.isEmpty()) {
            emit(emptyList())
            return@flow
        }
        data class Node(val docId: String, val relPath: String, val crumbs: List<Pair<String, String>>, val depth: Int)
        val queue = ArrayDeque<Node>()
        queue.addLast(Node(startDocId, "", listOf(startDocId to startLabel), 0))
        val hits = ArrayList<SearchHit>()
        var lastEmitCount = 0

        while (queue.isNotEmpty() && hits.size < maxResults) {
            currentCoroutineContext().ensureActive()
            val cur = queue.removeFirst()
            val children = (list(tree, cur.docId) as? FarResult.Ok)?.value ?: continue
            for (child in children) {
                currentCoroutineContext().ensureActive()
                if (!showHidden && child.name.startsWith(".")) continue
                val childRel = if (cur.relPath.isEmpty()) child.name else "${cur.relPath}/${child.name}"
                if (child.name.contains(needle, ignoreCase = true)) {
                    hits += SearchHit(
                        entry = child,
                        relPath = cur.relPath.ifEmpty { startLabel },
                        breadcrumb = cur.crumbs + (child.docId to child.name),
                    )
                    if (hits.size - lastEmitCount >= 12) {
                        lastEmitCount = hits.size
                        emit(hits.toList())
                    }
                    if (hits.size >= maxResults) break
                }
                if (child.isDir && cur.depth < maxDepth) {
                    queue.addLast(Node(child.docId, childRel, cur.crumbs + (child.docId to child.name), cur.depth + 1))
                }
            }
        }
        emit(hits.toList())
    }.flowOn(Dispatchers.IO)

    fun create(tree: Uri, parentDocId: String, mime: String, name: String): FarResult<String> = farCatching {
        val u = DocumentsContract.createDocument(r, docUri(tree, parentDocId), mime, name)
            ?: throw java.io.IOException("create failed")
        DocumentsContract.getDocumentId(u)
    }

    fun rename(tree: Uri, docId: String, newName: String): FarResult<Unit> {
        val target = docUri(tree, docId)
        val info = inspectDoc(target)
        if (info != null && !info.canRename) {
            return FarResult.Err(FarError.Unsupported("provider does not support rename"))
        }
        return farCatching {
            DocumentsContract.renameDocument(r, target, newName)
                ?: throw java.io.IOException("rename refused")
            Unit
        }
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

    /** Returns the list of item names that already exist in [dstParentDocId]. */
    fun findConflicts(dstTree: Uri, dstParentDocId: String, items: List<DocEntry>): List<String> {
        val existing = (list(dstTree, dstParentDocId) as? FarResult.Ok)?.value?.map { it.name }?.toSet() ?: return emptyList()
        return items.map { it.name }.filter { it in existing }.distinct()
    }

    /**
     * Copy or move [items] into destParent: never overwrite silently unless policy == Replace, verify size before success,
     * try DocumentsContract.moveDocument fast-path on same-tree moves, fall back to verified copy-delete across volumes,
     * honor [paused] between and inside transfers, and stop cleanly on cancel.
     */
    fun transfer(
        srcTree: Uri,
        items: List<DocEntry>,
        dstTree: Uri,
        dstParentDocId: String,
        move: Boolean,
        policy: Collision,
        srcParentDocId: String? = null,
        paused: StateFlow<Boolean>? = null,
        onItemResult: (item: DocEntry, ok: Boolean, destUri: String?, errorCode: String?) -> Unit = { _, _, _, _ -> },
    ): Flow<OpEvent> = flow {
        val skipped = ArrayList<String>()
        val failed = ArrayList<Pair<String, FarError>>()
        var done = 0
        val existing = (list(dstTree, dstParentDocId) as? FarResult.Ok)?.value?.map { it.name }?.toMutableSet()
            ?: run { emit(OpEvent.Aborted(FarError.ProviderUnavailable())); return@flow }

        for ((index, item) in items.withIndex()) {
            currentCoroutineContext().ensureActive()
            while (paused?.value == true) {
                currentCoroutineContext().ensureActive()
                delay(120)
            }
            emit(OpEvent.Progress(index, items.size, item.name))
            if (item.name in existing && policy == Collision.Skip) {
                skipped += item.name
                onItemResult(item, true, null, null)
                continue
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

            // Fast-path native SAF move within the same document tree when no rename is needed
            if (move && srcTree == dstTree && srcParentDocId != null && item.canMove && targetName == item.name) {
                val movedUri = runCatching {
                    DocumentsContract.moveDocument(
                        r,
                        docUri(srcTree, item.docId),
                        docUri(srcTree, srcParentDocId),
                        docUri(dstTree, dstParentDocId),
                    )
                }.getOrNull()
                if (movedUri != null) {
                    existing += targetName
                    done++
                    onItemResult(item, true, movedUri.toString(), null)
                    continue
                }
            }

            val res = if (item.isDir) copyDir(srcTree, item, dstTree, dstParentDocId, targetName, paused = paused)
            else copyFile(srcTree, item, dstTree, dstParentDocId, targetName, paused)
            when (res) {
                is FarResult.Ok -> {
                    existing += targetName
                    done++
                    val destStr = res.value
                    if (move) {
                        val delRes = delete(srcTree, item.docId)
                        if (delRes is FarResult.Err) {
                            failed += item.name to delRes.error
                            onItemResult(item, false, destStr, delRes.error.code)
                        } else {
                            onItemResult(item, true, destStr, null)
                        }
                    } else {
                        onItemResult(item, true, destStr, null)
                    }
                }
                is FarResult.Err -> {
                    failed += item.name to res.error
                    onItemResult(item, false, null, res.error.code)
                    if (res.error is FarError.StorageFull || res.error is FarError.VolumeRemoved) {
                        emit(OpEvent.Aborted(res.error))
                        return@flow
                    }
                }
            }
        }
        emit(OpEvent.Finished(done, skipped, failed))
    }.flowOn(Dispatchers.IO)

    private suspend fun copyFile(
        srcTree: Uri,
        f: DocEntry,
        dstTree: Uri,
        parent: String,
        name: String,
        paused: StateFlow<Boolean>? = null,
    ): FarResult<String> {
        val created = create(dstTree, parent, f.mime.ifEmpty { "application/octet-stream" }, name)
        val newId = (created as? FarResult.Ok)?.value ?: return (created as FarResult.Err)
        val dstDocUri = docUri(dstTree, newId)
        val res = farCatching {
            var totalWritten = 0L
            val input = r.openInputStream(docUri(srcTree, f.docId)) ?: throw java.io.IOException("openInputStream returned null")
            input.use { i ->
                val output = r.openOutputStream(dstDocUri) ?: throw java.io.IOException("openOutputStream returned null")
                output.use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        while (paused?.value == true) {
                            currentCoroutineContext().ensureActive()
                            delay(120)
                        }
                        val n = i.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        totalWritten += n
                    }
                    o.flush()
                }
            }
            val got = r.query(dstDocUri, arrayOf(Document.COLUMN_SIZE), null, null, null)?.use {
                if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else -1L
            } ?: -1L
            if (f.size != null && got >= 0 && got != f.size) throw java.io.IOException("size mismatch")
            if (f.size != null && f.size > 0 && totalWritten != f.size) throw java.io.IOException("short copy")
            dstDocUri.toString()
        }
        if (res is FarResult.Err) delete(dstTree, newId)
        return res
    }

    private suspend fun copyDir(
        srcTree: Uri,
        d: DocEntry,
        dstTree: Uri,
        parent: String,
        name: String,
        depth: Int = 0,
        paused: StateFlow<Boolean>? = null,
    ): FarResult<String> {
        if (depth > 32) return FarResult.Err(FarError.InvalidInput("folder too deep"))
        val newDir = (create(dstTree, parent, Document.MIME_TYPE_DIR, name) as? FarResult.Ok)?.value
            ?: return FarResult.Err(FarError.Io())
        val kids = (list(srcTree, d.docId) as? FarResult.Ok)?.value
            ?: return FarResult.Err(FarError.ProviderUnavailable())
        for (k in kids) {
            val r = if (k.isDir) copyDir(srcTree, k, dstTree, newDir, k.name, depth + 1, paused)
            else copyFile(srcTree, k, dstTree, newDir, k.name, paused)
            if (r is FarResult.Err) return r
        }
        return FarResult.Ok(docUri(dstTree, newDir).toString())
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
            val zipInput = r.openInputStream(docUri(tree, zipEntry.docId)) ?: throw java.io.IOException("openInputStream returned null")
            zipInput.use { input ->
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
                        val fileOut = r.openOutputStream(docUri(tree, docId)) ?: throw java.io.IOException("openOutputStream returned null")
                        fileOut.use { out -> data.copyTo(out) }
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
            val outStream = r.openOutputStream(docUri(tree, docId)) ?: throw java.io.IOException("openOutputStream returned null")
            outStream.use { out ->
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

    fun inspectZip(uri: Uri): FarResult<ZipInspection> = farCatching {
        val input = r.openInputStream(uri) ?: throw java.io.IOException("openInputStream returned null")
        input.use { SafeZip.inspect(it) }
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

    /**
     * Compares [actualHex] with [expectedInput], normalizing whitespace, case, and optional "sha256:" prefix.
     * Returns null when [expectedInput] is blank.
     */
    fun matchesSha256(actualHex: String, expectedInput: String): Boolean? {
        val normalizedExpected = expectedInput.trim()
            .removePrefix("sha256:")
            .removePrefix("SHA256:")
            .replace(" ", "")
            .replace(":", "")
            .lowercase()
        if (normalizedExpected.isEmpty()) return null
        return actualHex.trim().lowercase() == normalizedExpected
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

    /** Reads ZIP table of contents without writing anything to storage, validating every path for traversal safety. */
    fun inspect(input: InputStream, maxEntries: Int = 500): ZipInspection {
        val list = ArrayList<ZipEntrySummary>()
        var total = 0L
        var unsafe = false
        var truncated = false
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (list.size >= maxEntries) {
                    truncated = true
                    break
                }
                val safePath = sanitize(e.name)
                if (safePath == null) unsafe = true
                val declaredSize = e.size.coerceAtLeast(0L)
                if (!e.isDirectory) total += declaredSize
                list += ZipEntrySummary(
                    path = safePath ?: e.name,
                    isDir = e.isDirectory,
                    sizeBytes = declaredSize,
                    safe = safePath != null,
                )
            }
        }
        return ZipInspection(
            entries = list,
            totalUncompressedBytes = total,
            hasUnsafeEntries = unsafe,
            truncated = truncated,
        )
    }
}
