package com.example.core.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.core.common.FarResult
import com.example.core.common.LocalDiagnostics
import com.example.core.common.farCatching
import com.example.core.common.userTitle
import com.example.core.database.FarDatabase
import com.example.core.database.MediaAssetEntity
import com.example.core.database.ScanCheckpointEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class ScanSummary(
    val syncedCount: Int,
    val prunedCount: Int,
)

/**
 * Reconciles MediaStore source-of-truth rows into Room `media_asset` for canonical identity,
 * Far Camera capture provenance (`capturedByFar`), and fast incremental scanning.
 */
object MediaScanner {
    const val SCOPE_MEDIA_STORE = "media_store"

    suspend fun syncIncremental(ctx: Context, db: FarDatabase): FarResult<ScanSummary> = withContext(Dispatchers.IO) {
        farCatching {
            val resolver = ctx.contentResolver
            val base = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val now = System.currentTimeMillis()
            val dao = db.mediaAssets()
            val farIds = dao.allIdsWithCapturedByFar().toHashSet()
            val chunkSize = 1000
            var offset = 0
            var totalSynced = 0
            var maxId = 0L

            while (true) {
                currentCoroutineContext().ensureActive()
                val batch = queryMediaStore(
                    resolver = resolver,
                    base = base,
                    filter = MediaFilter(),
                    sort = SortKey.Taken,
                    desc = true,
                    offset = offset,
                    limit = chunkSize,
                )
                if (batch.isEmpty()) break
                val entities = batch.map { r ->
                    if (r.id > maxId) maxId = r.id
                    MediaAssetEntity(
                        mediaStoreId = r.id,
                        contentUri = r.uri.toString(),
                        displayName = r.name,
                        relativePath = r.relativePath.orEmpty(),
                        mimeType = r.mime,
                        sizeBytes = r.sizeBytes,
                        width = r.width,
                        height = r.height,
                        durationMs = r.durationMs,
                        capturedByFar = r.id in farIds,
                        isTrashed = false,
                        dateTakenMs = r.takenMs,
                        dateModifiedSec = r.takenMs / 1000L,
                        lastSeenAt = now,
                    )
                }
                dao.upsertAll(entities)
                totalSynced += entities.size
                if (batch.size < chunkSize) break
                offset += batch.size
            }

            val pruned = dao.deleteNotSeenSince(now)
            val versionHash = runCatching { MediaStore.getVersion(ctx).hashCode().toLong() }.getOrDefault(0L)
            db.scanCheckpoints().upsert(
                ScanCheckpointEntity(
                    scope = SCOPE_MEDIA_STORE,
                    lastSyncedAt = now,
                    lastGeneration = versionHash,
                    lastMediaId = maxId,
                    scannedCount = totalSynced,
                )
            )
            ScanSummary(syncedCount = totalSynced, prunedCount = pruned)
        }.also { res ->
            if (res is FarResult.Err) {
                LocalDiagnostics.recordEvent(ctx, res.error.code, "media_scanner", res.error.userTitle())
            }
        }
    }

    suspend fun markCapturedByFar(ctx: Context, db: FarDatabase, uri: Uri) = withContext(Dispatchers.IO) {
        runCatching {
            val id = ContentUris.parseId(uri)
            val existing = db.mediaAssets().get(id)
            if (existing != null) {
                db.mediaAssets().markCapturedByFar(id)
            } else {
                db.mediaAssets().upsertAll(
                    listOf(
                        MediaAssetEntity(
                            mediaStoreId = id,
                            contentUri = uri.toString(),
                            displayName = uri.lastPathSegment ?: "FAR_$id",
                            relativePath = "DCIM/Camera/",
                            mimeType = ctx.contentResolver.getType(uri) ?: "image/jpeg",
                            sizeBytes = 0L,
                            capturedByFar = true,
                            isTrashed = false,
                            dateTakenMs = System.currentTimeMillis(),
                            dateModifiedSec = System.currentTimeMillis() / 1000L,
                            lastSeenAt = System.currentTimeMillis(),
                        )
                    )
                )
            }
        }
    }
}
