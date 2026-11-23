package com.example.feature.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.example.core.common.FarResult
import com.example.core.common.farCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Locale

/** Offline Scan-to-PDF exporter writing standard single-page PDF 1.4 documents to Downloads/Far Gallery. */
object PdfExporter {

    fun writeBitmapPdf(bitmap: Bitmap, output: OutputStream, quality: Int = 92) {
        val opaque = if (bitmap.hasAlpha() || bitmap.config == Bitmap.Config.HARDWARE) {
            Bitmap.createBitmap(bitmap.width.coerceAtLeast(1), bitmap.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888).also { dst ->
                Canvas(dst).apply {
                    drawColor(Color.WHITE)
                    drawBitmap(bitmap, 0f, 0f, null)
                }
            }
        } else {
            bitmap
        }
        val jpgBytes = try {
            ByteArrayOutputStream().apply {
                opaque.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(50, 100), this)
            }.toByteArray()
        } finally {
            if (opaque !== bitmap) opaque.recycle()
        }

        val imgW = bitmap.width.coerceAtLeast(1)
        val imgH = bitmap.height.coerceAtLeast(1)
        val landscape = imgW > imgH
        val pageW = if (landscape) 842f else 595f
        val pageH = if (landscape) 595f else 842f
        val margin = 24f
        val scale = minOf((pageW - margin * 2f) / imgW, (pageH - margin * 2f) / imgH)
        val drawW = imgW * scale
        val drawH = imgH * scale
        val left = (pageW - drawW) / 2f
        val bottom = (pageH - drawH) / 2f

        val contentStream = String.format(
            Locale.US,
            "q %.2f 0 0 %.2f %.2f %.2f cm /Im0 Do Q\n",
            drawW,
            drawH,
            left,
            bottom,
        ).toByteArray(Charsets.US_ASCII)

        val buf = ByteArrayOutputStream()
        fun writeAscii(s: String) = buf.write(s.toByteArray(Charsets.US_ASCII))

        writeAscii("%PDF-1.4\n%\u00E2\u00E3\u00CF\u00D3\n")
        val offsets = IntArray(6)

        offsets[1] = buf.size()
        writeAscii("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        offsets[2] = buf.size()
        writeAscii("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")

        offsets[3] = buf.size()
        writeAscii(
            String.format(
                Locale.US,
                "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 %.0f %.0f] /Resources << /XObject << /Im0 5 0 R >> >> /Contents 4 0 R >>\nendobj\n",
                pageW,
                pageH,
            )
        )

        offsets[4] = buf.size()
        writeAscii("4 0 obj\n<< /Length ${contentStream.size} >>\nstream\n")
        buf.write(contentStream)
        writeAscii("endstream\nendobj\n")

        offsets[5] = buf.size()
        writeAscii(
            "5 0 obj\n<< /Type /XObject /Subtype /Image /Width $imgW /Height $imgH " +
                "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpgBytes.size} >>\nstream\n"
        )
        buf.write(jpgBytes)
        writeAscii("\nendstream\nendobj\n")

        val xrefOffset = buf.size()
        writeAscii("xref\n0 6\n")
        writeAscii("0000000000 65535 f \n")
        for (i in 1..5) {
            writeAscii(String.format(Locale.US, "%010d 00000 n \n", offsets[i]))
        }
        writeAscii("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        buf.writeTo(output)
    }

    suspend fun exportImageToPdf(
        ctx: Context,
        sourceUri: Uri,
        displayTitle: String? = null,
    ): FarResult<Uri> = withContext(Dispatchers.IO) {
        farCatching {
            val loaded = when (val r = EditorEngine.load(ctx, sourceUri, 2400)) {
                is FarResult.Ok -> r.value
                is FarResult.Err -> throw IllegalStateException("decode failed (${r.error.code})")
            }
            val safeBase = displayTitle
                ?.substringBeforeLast('.')
                ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
                ?.take(36)
                ?.ifBlank { null }
                ?: "SCAN_${System.currentTimeMillis()}"
            val fileName = "${safeBase}.pdf"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Far Gallery")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val out = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw java.io.IOException("insert pdf failed")
            try {
                resolver.openOutputStream(out)?.use { stream ->
                    writeBitmapPdf(loaded, stream)
                } ?: throw java.io.IOException("openOutputStream returned null")
                resolver.update(out, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            } catch (e: Throwable) {
                resolver.delete(out, null, null)
                throw e
            } finally {
                loaded.recycle()
            }
            out
        }
    }
}
