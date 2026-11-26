package com.example.shared.local

import android.net.Uri
import com.example.core.common.FarError
import com.example.core.common.FarResult

/**
 * Source of a shared-library media item.
 *
 * This is the smallest useful way to say where an in-app media item came from
 * without coupling the shared model to one storage mechanism.
 *
 * MediaStore-backed items are the main shared library content. SAF-backed items
 * come from arbitrary documents or user-selected providers. App-private items are
 * created and stored by the app itself.
 */
enum class MediaSource {
    /** Shared photos/videos from MediaStore. */
    MediaStore,

    /** Arbitrary documents or user-selected provider content via SAF. */
    SAF,

    /** App-created or app-stored content such as captures or exports. */
    AppPrivate,
}

/**
 * Minimal shared representation for a media item that Photos, Camera, and Files
 * can all reason about.
 *
 * This is intentionally small for this increment. It exists so later features can
 * build against one contract instead of constantly re-deriving the model.
 *
 * Rules:
 *  - Normalize only what the source actually provides.
 *  - Do not fabricate values for unavailable fields.
 *  - Do not use this interface to claim a missing file exists.
 */
interface MediaItem {
    /** Stable identity within the app. */
    val id: String

    /** Where the item came from. */
    val source: MediaSource

    /** The item's URI. */
    val uri: Uri

    /** User-visible name, when available. */
    val name: String?

    /** Declared or sniffed MIME type, when available. */
    val mimeType: String?

    /** Size in bytes, when available. */
    val sizeBytes: Long?

    /** Image width in pixels, when available. */
    val width: Int?

    /** Image height in pixels, when available. */
    val height: Int?

    /** Video duration in milliseconds, when available. */
    val durationMs: Long?

    /** Indicates whether this item is treated as a photo for library purposes. */
    val isPhoto: Boolean

    /** Indicates whether this item is treated as a video for library purposes. */
    val isVideo: Boolean

    /** Optional SHA-256 hash, when computed. */
    val contentHash: String?

    /**
     * Convenience check for “is this likely a media item we can show in a timeline?”
     */
    val isMedia: Boolean
        get() = isPhoto || isVideo
}

/**
 * Minimal reader contract for a media source.
 *
 * Each source can implement this so the shared layer can query and normalize items
 * without knowing the storage mechanism.
 *
 * Results and errors are returned through [FarResult] so failures stay typed and
 * do not leak as exceptions into the UI.
 */
interface MediaSourceReader {

    /**
     * Returns a page of normalized items from this source.
     *
     * @param request describes what the caller wants; the source decides how to page it.
     */
    suspend fun readPage(request: MediaPageRequest): FarResult<MediaPage>
}

/**
 * A single page of normalized media items from one source.
 */
data class MediaPage(
    /** The items in this page. */
    val items: List<MediaItem>,

    /** Whether more pages are likely available. */
    val hasMore: Boolean,

    /** Opaque cursor/offset/position the caller can pass back for the next page, if supported. */
    val nextPageToken: String? = null,
)

/**
 * Request parameters for a media page.
 *
 * Keep this small. Sources may ignore fields they do not support.
 */
data class MediaPageRequest(
    /** Continuation token from a previous page, if any. */
    val pageToken: String? = null,

    /** Approximate maximum items for this page. */
    val limit: Int = 120,

    /** If true, the caller mainly wants photos. */
    val preferPhotos: Boolean = false,

    /** If true, the caller mainly wants videos. */
    val preferVideos: Boolean = false,
)

/**
 * Common error mapping helper for media source reads.
 *
 * This is a utility function, not an extension on `FarError.Companion`.
 */
internal fun mediaSourceError(cause: Throwable): FarError = when (cause) {
    is SecurityException -> FarError.PermissionRevoked("media")
    is IllegalArgumentException -> FarError.InvalidInput(cause.message ?: "invalid media request")
    is OutOfMemoryError -> FarError.OutOfMemory()
    is java.io.IOException -> FarError.Io()
    is kotlin.coroutines.cancellation.CancellationException -> FarError.Cancelled()
    else -> FarError.Unknown()
}
