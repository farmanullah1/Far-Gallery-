package com.example.core.storage

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.core.common.FarResult
import com.example.core.common.LocalDiagnostics
import com.example.core.common.safeParseMediaUri
import com.example.core.common.userTitle
import com.example.core.database.FarDatabase
import com.example.core.database.OperationItemEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

/**
 * Coordinates pause/resume state and Room OperationItemEntity checkpoints for large SAF transfers.
 */
object TransferQueueController {
    val paused = MutableStateFlow(false)

    fun setPaused(isPaused: Boolean) {
        paused.value = isPaused
    }
}

/**
 * WorkManager-backed worker for checkpointed copy/move file queues persisted in Room `operation_item`.
 */
class FileTransferWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val opId = inputData.getString(KEY_OP_ID) ?: return Result.failure()
        val srcTreeStr = inputData.getString(KEY_SRC_TREE) ?: return Result.failure()
        val dstTreeStr = inputData.getString(KEY_DST_TREE) ?: return Result.failure()
        val srcParent = inputData.getString(KEY_SRC_PARENT)
        val dstParent = inputData.getString(KEY_DST_PARENT) ?: return Result.failure()
        val move = inputData.getBoolean(KEY_MOVE, false)
        val policy = inputData.getString(KEY_POLICY)?.let {
            runCatching { Collision.valueOf(it) }.getOrNull()
        } ?: Collision.Rename

        val srcTree = safeParseMediaUri(srcTreeStr) ?: return Result.failure()
        val dstTree = safeParseMediaUri(dstTreeStr) ?: return Result.failure()

        val db = FarDatabase.get(applicationContext)
        val ops = DocumentOps(applicationContext.contentResolver)
        val pendingItems = db.operationItems().forOperation(opId).filter { it.status == STATUS_PENDING }
        if (pendingItems.isEmpty()) return Result.success()

        val docEntries = pendingItems.mapNotNull { row ->
            val u = safeParseMediaUri(row.sourceUri) ?: return@mapNotNull null
            val entry = ops.inspectDoc(u) ?: return@mapNotNull null
            row to entry
        }

        var done = 0
        val total = docEntries.size
        for ((row, entry) in docEntries) {
            while (TransferQueueController.paused.value && !isStopped) {
                delay(150)
            }
            if (isStopped) return Result.retry()

            var itemSucceeded = false
            ops.transfer(
                srcTree = srcTree,
                items = listOf(entry),
                dstTree = dstTree,
                dstParentDocId = dstParent,
                move = move,
                policy = policy,
                srcParentDocId = srcParent,
            ).collect { ev ->
                when (ev) {
                    is OpEvent.Finished -> {
                        itemSucceeded = ev.failed.isEmpty() && ev.done > 0
                        val errCode = ev.failed.firstOrNull()?.second?.code
                        db.operationItems().updateStatus(
                            id = row.id,
                            status = if (itemSucceeded) STATUS_DONE else STATUS_FAILED,
                            destUri = dstTreeStr,
                            errorCode = errCode,
                            now = System.currentTimeMillis(),
                        )
                    }
                    is OpEvent.Aborted -> {
                        LocalDiagnostics.recordEvent(applicationContext, ev.error.code, "transfer_worker", ev.error.userTitle())
                        db.operationItems().updateStatus(
                            id = row.id,
                            status = STATUS_FAILED,
                            destUri = null,
                            errorCode = ev.error.code,
                            now = System.currentTimeMillis(),
                        )
                    }
                    is OpEvent.Progress -> Unit
                }
            }
            done++
            setProgress(workDataOf("done" to done, "total" to total, "current" to entry.name))
        }
        return Result.success(workDataOf("done" to done, "total" to total))
    }

    companion object {
        const val KEY_OP_ID = "op_id"
        const val KEY_SRC_TREE = "src_tree"
        const val KEY_DST_TREE = "dst_tree"
        const val KEY_SRC_PARENT = "src_parent"
        const val KEY_DST_PARENT = "dst_parent"
        const val KEY_MOVE = "move"
        const val KEY_POLICY = "policy"

        const val STATUS_PENDING = "PENDING"
        const val STATUS_DONE = "DONE"
        const val STATUS_FAILED = "FAILED"

        suspend fun enqueue(
            ctx: Context,
            db: FarDatabase,
            srcTree: Uri,
            dstTree: Uri,
            srcParentDocId: String?,
            dstParentDocId: String,
            items: List<DocEntry>,
            move: Boolean,
            policy: Collision,
        ): String {
            val opId = UUID.randomUUID().toString()
            val ops = DocumentOps(ctx.contentResolver)
            val now = System.currentTimeMillis()
            db.operationItems().upsertAll(
                items.map { e ->
                    OperationItemEntity(
                        operationId = opId,
                        sourceUri = ops.docUri(srcTree, e.docId).toString(),
                        status = STATUS_PENDING,
                        updatedAt = now,
                    )
                }
            )
            val data = Data.Builder()
                .putString(KEY_OP_ID, opId)
                .putString(KEY_SRC_TREE, srcTree.toString())
                .putString(KEY_DST_TREE, dstTree.toString())
                .putString(KEY_SRC_PARENT, srcParentDocId)
                .putString(KEY_DST_PARENT, dstParentDocId)
                .putBoolean(KEY_MOVE, move)
                .putString(KEY_POLICY, policy.name)
                .build()
            val req = OneTimeWorkRequestBuilder<FileTransferWorker>()
                .setInputData(data)
                .build()
            WorkManager.getInstance(ctx.applicationContext).enqueueUniqueWork(
                "far_transfer_$opId",
                ExistingWorkPolicy.KEEP,
                req,
            )
            return opId
        }
    }
}
