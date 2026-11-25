package com.example.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.example.core.common.FarResult
import com.example.core.common.farCatching
import com.example.core.database.FarDatabase
import com.example.core.database.OcrIndexEntity
import com.google.mlkit.common.sdkinternal.MlKitContext
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.max

/** Offline on-device ML Kit OCR helper for Viewer Live Text and local Search indexing. */
class OcrIndexer(private val context: Context) {
    private val db = FarDatabase.get(context)

    suspend fun recognizeText(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        runCatching { MlKitContext.initializeIfNeeded(context.applicationContext) }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { result -> if (cont.isActive) cont.resume(result.text.trim()) }
                    .addOnFailureListener { if (cont.isActive) cont.resume("") }
            }
        } catch (_: Exception) {
            ""
        } finally {
            runCatching { recognizer.close() }
        }
    }

    suspend fun extractAndIndex(mediaId: Long, uri: Uri): FarResult<String> = withContext(Dispatchers.IO) {
        val existing = db.ocr().get(mediaId)
        if (existing != null) return@withContext FarResult.Ok(existing.extractedText)
        farCatching {
            val bmp = decodeBounded(uri, 1280)
            try {
                val text = recognizeText(bmp)
                db.ocr().upsert(OcrIndexEntity(mediaId, uri.toString(), text, System.currentTimeMillis()))
                text
            } finally {
                bmp.recycle()
            }
        }
    }

    suspend fun indexBatch(
        rows: List<MediaRow>,
        maxItems: Int = 40,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Int = withContext(Dispatchers.IO) {
        val candidates = ArrayList<MediaRow>(maxItems)
        for (row in rows) {
            currentCoroutineContext().ensureActive()
            if (!row.isVideo && db.ocr().get(row.id) == null) {
                candidates += row
                if (candidates.size >= maxItems) break
            }
        }
        if (candidates.isEmpty()) return@withContext 0
        var withText = 0
        val total = candidates.size
        candidates.forEachIndexed { idx, row ->
            currentCoroutineContext().ensureActive()
            runCatching {
                val bmp = decodeBounded(row.uri, 1024)
                try {
                    val text = recognizeText(bmp)
                    db.ocr().upsert(OcrIndexEntity(row.id, row.uri.toString(), text, System.currentTimeMillis()))
                    if (text.isNotBlank()) withText++
                } finally {
                    bmp.recycle()
                }
            }
            onProgress(idx + 1, total)
        }
        withText
    }

    private fun decodeBounded(uri: Uri, maxEdge: Int): Bitmap {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            val longEdge = max(info.size.width, info.size.height)
            if (longEdge > maxEdge) {
                val f = maxEdge.toFloat() / longEdge
                decoder.setTargetSize((info.size.width * f).toInt().coerceAtLeast(1), (info.size.height * f).toInt().coerceAtLeast(1))
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
    }
}
