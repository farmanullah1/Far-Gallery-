package com.example.core.media

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.Access
import com.example.Permissions
import com.example.core.common.FarResult
import com.example.core.common.LocalDiagnostics
import com.example.core.common.userSummary
import com.example.core.database.FarDatabase
import com.example.core.database.OcrIndexEntity
import com.example.core.database.QuarantineItemEntity
import com.example.core.database.ScanCheckpointEntity
import com.example.feature.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Battery- and thermal-aware WorkManager worker that indexes unindexed images into Room `ocr_index`
 * (and automatically updates `ocr_fts` FTS4 virtual table via Room triggers).
 */
class OcrIndexWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        if (Permissions.access(ctx) == Access.None) return@withContext Result.success()
        val settings = runCatching { SettingsStore(ctx).flow.first() }.getOrNull()
        if (settings != null && !settings.ocrEnabled) return@withContext Result.success()
        if (isThermallyThrottled(ctx)) return@withContext Result.retry()

        val db = FarDatabase.get(ctx)
        val repo = MediaRepository(ctx.contentResolver)
        val excludedPaths = runCatching {
            val ex = db.folders().excludedSnapshot().map { it.relativePath }
            val rules = db.folderRules().allSnapshot()
                .filter { it.hidden || it.excludedFromTimeline || it.excludedFromOcr }
                .map { it.relativePath }
            (ex + rules).distinct()
        }.getOrDefault(emptyList())

        val rowsRes = repo.queryAll(
            filter = MediaFilter(kind = MediaKind.Photos, excludedPaths = excludedPaths),
            sort = SortKey.Taken,
            descending = true,
        )
        val allPhotos = (rowsRes as? FarResult.Ok)?.value ?: return@withContext Result.retry()
        val unindexed = ArrayList<MediaRow>()
        for (row in allPhotos) {
            currentCoroutineContext().ensureActive()
            if (db.ocr().get(row.id) == null) {
                unindexed += row
            }
        }
        if (unindexed.isEmpty()) {
            db.scanCheckpoints().upsert(
                ScanCheckpointEntity(
                    scope = SCOPE_OCR,
                    lastSyncedAt = System.currentTimeMillis(),
                    scannedCount = allPhotos.size,
                )
            )
            return@withContext Result.success(workDataOf("done" to allPhotos.size, "total" to allPhotos.size))
        }

        val ocr = OcrIndexer(ctx)
        val batchSize = 25
        val slice = unindexed.take(batchSize)
        var done = 0
        for (row in slice) {
            currentCoroutineContext().ensureActive()
            if (isStopped || isThermallyThrottled(ctx)) break
            val res = ocr.extractAndIndex(row.id, row.uri)
            if (res is FarResult.Err) {
                LocalDiagnostics.recordEvent(ctx, res.error.code, "ocr_worker", row.mime)
                db.quarantine().upsert(
                    QuarantineItemEntity(
                        sourceUri = row.uri.toString(),
                        displayName = row.name.ifBlank { "Image ${row.id}" },
                        reason = res.error.userSummary(),
                        errorCode = res.error.code,
                        createdAt = System.currentTimeMillis(),
                    )
                )
                // Record empty marker so corrupt items don't loop infinitely
                db.ocr().upsert(OcrIndexEntity(row.id, row.uri.toString(), "", System.currentTimeMillis()))
            }
            done++
            setProgressAsync(workDataOf("done" to done, "total" to slice.size))
        }
        db.scanCheckpoints().upsert(
            ScanCheckpointEntity(
                scope = SCOPE_OCR,
                lastSyncedAt = System.currentTimeMillis(),
                lastMediaId = slice.lastOrNull()?.id ?: 0L,
                scannedCount = allPhotos.size - (unindexed.size - done).coerceAtLeast(0),
            )
        )
        return@withContext if (unindexed.size > slice.size && !isStopped) {
            // Schedule continuation if more photos remain
            OcrScheduler.enqueue(ctx, immediate = false)
            Result.success(workDataOf("done" to done, "remaining" to (unindexed.size - slice.size)))
        } else {
            Result.success(workDataOf("done" to done, "total" to slice.size))
        }
    }

    private fun isThermallyThrottled(ctx: Context): Boolean {
        val pm = ctx.getSystemService(PowerManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= 29) {
            pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
        } else {
            false
        }
    }

    companion object {
        const val WORK_NAME = "far_ocr_index"
        const val SCOPE_OCR = "ocr"
    }
}

object OcrScheduler {
    fun enqueue(ctx: Context, immediate: Boolean = false) {
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(!immediate)
                .build()
            val req = OneTimeWorkRequestBuilder<OcrIndexWorker>()
                .setConstraints(constraints)
                .build()
            val policy = if (immediate) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
            WorkManager.getInstance(ctx.applicationContext).enqueueUniqueWork(OcrIndexWorker.WORK_NAME, policy, req)
        }
    }

    fun cancel(ctx: Context) {
        runCatching {
            WorkManager.getInstance(ctx.applicationContext).cancelUniqueWork(OcrIndexWorker.WORK_NAME)
        }
    }
}
