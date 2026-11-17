package com.example.core.common

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** ---------- Typed errors ---------- */
sealed interface FarError {
    val code: String
    data class Unsupported(val reason: String, override val code: String = "FG-FMT") : FarError
    data class Corrupt(override val code: String = "FG-COR") : FarError
    data class Missing(override val code: String = "FG-MIS") : FarError
    data class PermissionRevoked(val scope: String, override val code: String = "FG-PRM") : FarError
    data class StorageFull(val neededBytes: Long?, val availableBytes: Long?, override val code: String = "FG-SPC") : FarError
    data class VolumeRemoved(val label: String?, override val code: String = "FG-VOL") : FarError
    data class ProviderUnavailable(override val code: String = "FG-PRV") : FarError
    data class OutOfMemory(override val code: String = "FG-OOM") : FarError
    data class Io(override val code: String = "FG-IO") : FarError
    data class InvalidInput(val reason: String, override val code: String = "FG-INP") : FarError
    data class Unknown(override val code: String = "FG-UNK") : FarError
}

sealed interface FarResult<out T> {
    data class Ok<T>(val value: T) : FarResult<T>
    data class Err(val error: FarError) : FarResult<Nothing>
}

/**
 * Wrap any I/O or decode call at the repository boundary.
 * - CancellationException is ALWAYS rethrown (never swallowed).
 * - OutOfMemoryError is mapped only here (around bitmap/decoder work); caller should downsample + trim caches.
 * - Anything unexpected becomes Unknown so the UI shows a safe card instead of crashing.
 */
inline fun <T> farCatching(block: () -> T): FarResult<T> = try {
    FarResult.Ok(block())
} catch (e: CancellationException) {
    throw e
} catch (_: FileNotFoundException) {
    FarResult.Err(FarError.Missing())
} catch (_: SecurityException) {
    FarResult.Err(FarError.PermissionRevoked("media"))
} catch (_: OutOfMemoryError) {
    FarResult.Err(FarError.OutOfMemory())
} catch (e: IOException) {
    val msg = e.message.orEmpty()
    when {
        "ENOSPC" in msg || "No space left" in msg -> FarResult.Err(FarError.StorageFull(null, null))
        else -> FarResult.Err(FarError.Io())
    }
} catch (_: IllegalStateException) {
    FarResult.Err(FarError.ProviderUnavailable())
} catch (_: IllegalArgumentException) {
    FarResult.Err(FarError.InvalidInput("bad argument"))
} catch (_: RuntimeException) {
    FarResult.Err(FarError.Unknown())
}

/** Privacy-safe local crash note utilities (no file names, URIs, GPS, or OCR text). */
object LocalDiagnostics {
    fun readLastCrash(ctx: Context): String? {
        val f = File(ctx.filesDir, "last_crash.txt")
        return if (f.exists()) runCatching { f.readText().takeIf { it.isNotBlank() } }.getOrNull() else null
    }

    fun dismissLastCrash(ctx: Context) {
        runCatching { File(ctx.filesDir, "last_crash.txt").delete() }
    }
}

/** ---------- Incoming media validation ---------- */
data class IncomingItem(
    val uri: Uri,
    val displayName: String?,
    val sizeBytes: Long?,
    val declaredMime: String?,
    val sniffedMime: String?,
    val mimeMismatch: Boolean,
)

data class IncomingResult(val accepted: List<IncomingItem>, val rejected: List<Pair<Uri?, FarError>>)

class IncomingMediaResolver(private val resolver: ContentResolver, private val maxItems: Int = 500) {

    private val allowedActions = setOf(Intent.ACTION_VIEW, Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)

    /** Call from a background dispatcher; never from the main thread. */
    fun resolve(intent: Intent): IncomingResult {
        if (intent.action !in allowedActions) {
            return IncomingResult(emptyList(), listOf(null to FarError.InvalidInput("unsupported action")))
        }
        val uris = LinkedHashSet<Uri>()
        intent.data?.let(uris::add)
        @Suppress("DEPRECATION")
        when (intent.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let(uris::add)
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let(uris::addAll)
        }
        intent.clipData?.let { clip: ClipData ->
            for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(uris::add)
        }

        val accepted = ArrayList<IncomingItem>()
        val rejected = ArrayList<Pair<Uri?, FarError>>()
        for (uri in uris.take(maxItems)) {
            when (val r = farCatching { inspect(uri) }) {
                is FarResult.Ok -> accepted += r.value
                is FarResult.Err -> rejected += uri to r.error
            }
        }
        if (uris.size > maxItems) rejected += null to FarError.InvalidInput("too many items (max $maxItems)")
        return IncomingResult(accepted, rejected)
    }

    private fun inspect(uri: Uri): IncomingItem {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) throw IllegalArgumentException("scheme")
        var name: String? = null
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
            }
        }
        val declared = resolver.getType(uri)
        val header = ByteArray(32)
        val read = resolver.openInputStream(uri)?.use { it.read(header) } ?: throw FileNotFoundException()
        val sniffed = sniffMime(header, read)
        val mismatch = declared != null && sniffed != null &&
            !declared.equals(sniffed, ignoreCase = true) &&
            !(declared.startsWith("video/") && sniffed.startsWith("video/"))
        val effective = sniffed ?: declared
        if (effective == null || !(effective.startsWith("image/") || effective.startsWith("video/"))) {
            throw IllegalArgumentException("not media")
        }
        return IncomingItem(uri, name, size, declared, sniffed, mismatch)
    }

    fun sniffMime(b: ByteArray, n: Int): String? {
        fun at(i: Int, vararg v: Int) = n > i + v.size - 1 && v.indices.all { (b[i + it].toInt() and 0xFF) == v[it] }
        fun ascii(i: Int, s: String) = n >= i + s.length && s.indices.all { b[i + it].toInt().toChar() == s[it] }
        return when {
            at(0, 0xFF, 0xD8, 0xFF) -> "image/jpeg"
            at(0, 0x89, 0x50, 0x4E, 0x47) -> "image/png"
            ascii(0, "GIF8") -> "image/gif"
            ascii(0, "RIFF") && ascii(8, "WEBP") -> "image/webp"
            ascii(4, "ftyp") && (ascii(8, "heic") || ascii(8, "heix") || ascii(8, "mif1") || ascii(8, "msf1")) -> "image/heic"
            ascii(4, "ftyp") && ascii(8, "avif") -> "image/avif"
            ascii(4, "ftyp") && ascii(8, "qt") -> "video/quicktime"
            ascii(4, "ftyp") -> "video/mp4"
            at(0, 0x1A, 0x45, 0xDF, 0xA3) -> "video/x-matroska"
            ascii(0, "BM") -> "image/bmp"
            at(0, 0x49, 0x49, 0x2A, 0x00) || at(0, 0x4D, 0x4D, 0x00, 0x2A) -> "image/tiff"
            else -> null
        }
    }
}
