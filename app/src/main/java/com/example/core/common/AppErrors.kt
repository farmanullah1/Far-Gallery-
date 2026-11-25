package com.example.core.common

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.IntentCompat
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.TimeoutException
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
    data class Cancelled(override val code: String = "FG-CNC") : FarError
    data class Timeout(override val code: String = "FG-TMO") : FarError
    data class Hardware(val detail: String = "", override val code: String = "FG-HW") : FarError
    data class Model(val detail: String = "", override val code: String = "FG-MDL") : FarError
    data class Unknown(override val code: String = "FG-UNK") : FarError
}

sealed interface FarResult<out T> {
    data class Ok<T>(val value: T) : FarResult<T>
    data class Err(val error: FarError) : FarResult<Nothing>
}

/**
 * Wrap any I/O or decode call at the repository boundary.
 * - CancellationException is ALWAYS rethrown (never swallowed).
 * - TimeoutException maps to FarError.Timeout cleanly.
 * - OutOfMemoryError is mapped only here (around bitmap/decoder work); caller should downsample + trim caches.
 * - Anything unexpected becomes Unknown so the UI shows a safe card instead of crashing.
 */
inline fun <T> farCatching(block: () -> T): FarResult<T> = try {
    FarResult.Ok(block())
} catch (e: CancellationException) {
    throw e
} catch (_: TimeoutException) {
    FarResult.Err(FarError.Timeout())
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

/** Privacy-safe local crash & error log utilities (no file names, URIs, GPS, or OCR text). */
object LocalDiagnostics {
    private const val MAX_LOG_LINES = 30

    fun readLastCrash(ctx: Context): String? {
        val f = File(ctx.filesDir, "last_crash.txt")
        return if (f.exists()) runCatching { f.readText().takeIf { it.isNotBlank() } }.getOrNull() else null
    }

    fun dismissLastCrash(ctx: Context) {
        runCatching { File(ctx.filesDir, "last_crash.txt").delete() }
    }

    fun recordEvent(ctx: Context, code: String, area: String, detail: String = "") {
        runCatching {
            val f = File(ctx.filesDir, "diagnostics.log")
            val existing = if (f.exists()) f.readLines() else emptyList()
            val safeDetail = detail.replace('\n', ' ').take(80)
            val entry = "${System.currentTimeMillis()}|$code|$area|$safeDetail"
            val trimmed = (existing + entry).takeLast(MAX_LOG_LINES)
            f.writeText(trimmed.joinToString("\n"))
        }
    }

    fun readEvents(ctx: Context): List<String> {
        val f = File(ctx.filesDir, "diagnostics.log")
        return if (f.exists()) runCatching { f.readLines().filter { it.isNotBlank() }.asReversed() }.getOrDefault(emptyList())
        else emptyList()
    }

    fun clearAll(ctx: Context) {
        runCatching {
            File(ctx.filesDir, "last_crash.txt").delete()
            File(ctx.filesDir, "diagnostics.log").delete()
        }
    }

    fun buildReport(ctx: Context): String = buildString {
        appendLine("Far Gallery Local Diagnostics (offline, zero PII)")
        appendLine("SDK: ${android.os.Build.VERSION.SDK_INT}")
        readLastCrash(ctx)?.let { appendLine("Last crash: $it") }
        val events = readEvents(ctx)
        if (events.isEmpty()) {
            appendLine("Recent error events: none")
        } else {
            appendLine("Recent error events (${events.size}):")
            events.forEach { appendLine(" - $it") }
        }
    }.trim()
}

fun FarError.userTitle(): String = when (this) {
    is FarError.Unsupported -> "Unsupported file format"
    is FarError.Corrupt -> "Damaged or unreadable file"
    is FarError.Missing -> "File not found"
    is FarError.PermissionRevoked -> "Permission required"
    is FarError.StorageFull -> "Not enough storage space"
    is FarError.VolumeRemoved -> "Storage drive disconnected"
    is FarError.ProviderUnavailable -> "Media provider unavailable"
    is FarError.OutOfMemory -> "Image too large to process"
    is FarError.Io -> "File read/write failed"
    is FarError.InvalidInput -> "Invalid operation"
    is FarError.Cancelled -> "Operation cancelled"
    is FarError.Timeout -> "Operation timed out"
    is FarError.Hardware -> "Camera or hardware unavailable"
    is FarError.Model -> "On-device model unavailable"
    is FarError.Unknown -> "Unexpected problem"
}

fun FarError.userSummary(): String = when (this) {
    is FarError.Unsupported -> "This format isn't supported here ($reason)."
    is FarError.Corrupt -> "The file appears damaged or incomplete. Originals are untouched."
    is FarError.Missing -> "The item was moved or deleted outside Far Gallery."
    is FarError.PermissionRevoked -> "Android blocked access to $scope. Re-grant access to continue."
    is FarError.StorageFull -> "Free up space on your device and try again."
    is FarError.VolumeRemoved -> "The SD card or USB volume is no longer mounted."
    is FarError.ProviderUnavailable -> "The storage provider stopped responding. Try again shortly."
    is FarError.OutOfMemory -> "Try processing a smaller batch or lower resolution."
    is FarError.Io -> "An I/O error prevented completion. Original files are safe."
    is FarError.InvalidInput -> "Check your input and try again ($reason)."
    is FarError.Cancelled -> "The task was stopped before finishing. Originals are untouched."
    is FarError.Timeout -> "The operation took too long to finish. Try again on a smaller batch."
    is FarError.Hardware -> if (detail.isBlank()) "The device hardware isn't ready right now." else "The device hardware isn't ready ($detail)."
    is FarError.Model -> if (detail.isBlank()) "The offline recognition model isn't ready yet." else "The offline recognition model isn't ready ($detail)."
    is FarError.Unknown -> "Something went wrong, but your original files were not modified."
}

fun FarError.resolveTitle(ctx: Context): String = when (this) {
    is FarError.Unsupported -> ctx.getString(com.example.R.string.err_unsupported_title)
    is FarError.Corrupt -> ctx.getString(com.example.R.string.err_corrupt_title)
    is FarError.Missing -> ctx.getString(com.example.R.string.err_missing_title)
    is FarError.PermissionRevoked -> ctx.getString(com.example.R.string.err_permission_title)
    is FarError.StorageFull -> ctx.getString(com.example.R.string.err_storage_title)
    is FarError.VolumeRemoved -> ctx.getString(com.example.R.string.err_volume_title)
    is FarError.ProviderUnavailable -> ctx.getString(com.example.R.string.err_provider_title)
    is FarError.OutOfMemory -> ctx.getString(com.example.R.string.err_oom_title)
    is FarError.Io -> ctx.getString(com.example.R.string.err_io_title)
    is FarError.InvalidInput -> ctx.getString(com.example.R.string.err_invalid_title)
    is FarError.Cancelled -> ctx.getString(com.example.R.string.err_cancelled_title)
    is FarError.Timeout -> ctx.getString(com.example.R.string.err_timeout_title)
    is FarError.Hardware -> ctx.getString(com.example.R.string.err_hardware_title)
    is FarError.Model -> ctx.getString(com.example.R.string.err_model_title)
    is FarError.Unknown -> ctx.getString(com.example.R.string.err_unknown_title)
}

fun FarError.resolveSummary(ctx: Context): String = when (this) {
    is FarError.Unsupported -> ctx.getString(com.example.R.string.err_unsupported_summary, reason)
    is FarError.Corrupt -> ctx.getString(com.example.R.string.err_corrupt_summary)
    is FarError.Missing -> ctx.getString(com.example.R.string.err_missing_summary)
    is FarError.PermissionRevoked -> ctx.getString(com.example.R.string.err_permission_summary, scope)
    is FarError.StorageFull -> ctx.getString(com.example.R.string.err_storage_summary)
    is FarError.VolumeRemoved -> ctx.getString(com.example.R.string.err_volume_summary)
    is FarError.ProviderUnavailable -> ctx.getString(com.example.R.string.err_provider_summary)
    is FarError.OutOfMemory -> ctx.getString(com.example.R.string.err_oom_summary)
    is FarError.Io -> ctx.getString(com.example.R.string.err_io_summary)
    is FarError.InvalidInput -> ctx.getString(com.example.R.string.err_invalid_summary, reason)
    is FarError.Cancelled -> ctx.getString(com.example.R.string.err_cancelled_summary)
    is FarError.Timeout -> ctx.getString(com.example.R.string.err_timeout_summary)
    is FarError.Hardware -> ctx.getString(com.example.R.string.err_hardware_summary)
    is FarError.Model -> ctx.getString(com.example.R.string.err_model_summary)
    is FarError.Unknown -> ctx.getString(com.example.R.string.err_unknown_summary)
}

fun safeParseMediaUri(raw: String?): Uri? {
    if (raw.isNullOrBlank()) return null
    return runCatching { Uri.parse(raw) }.getOrNull()
        ?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT || it.scheme == ContentResolver.SCHEME_FILE }
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
        when (intent.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(uris::add)
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(uris::addAll)
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
        val scheme = uri.scheme
        var name: String? = null
        var size: Long? = null
        when (scheme) {
            ContentResolver.SCHEME_CONTENT -> {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) }
                        c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
                    }
                }
            }
            ContentResolver.SCHEME_FILE -> {
                val file = uri.path?.let(::File)?.canonicalFile
                    ?.takeIf { it.isFile && it.canRead() }
                    ?: throw FileNotFoundException("unreadable file uri")
                name = file.name
                size = file.length()
            }
            else -> throw IllegalArgumentException("unsupported uri scheme")
        }
        val declared = if (scheme == ContentResolver.SCHEME_CONTENT) resolver.getType(uri) else null
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
