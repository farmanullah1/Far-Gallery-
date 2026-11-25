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

    enum class PdfLayoutMode {
        PhotoBook,
        ContactSheet,
    }

    /**
     * Writes an arbitrary multi-page PDF 1.4 document from encoded page JPEG descriptors or bitmaps.
     * Object layout:
     *   1: Catalog (/Pages 2 0 R)
     *   2: Pages (/Kids [...] /Count N)
     *   For page i in 0 until N:
     *     pageObjId    = 3 + i * 3
     *     contentObjId = 4 + i * 3
     *     imageObjId   = 5 + i * 3
     */
    fun writeMultiPageBitmapPdf(
        pages: List<Bitmap>,
        output: OutputStream,
        quality: Int = 90,
    ) {
        require(pages.isNotEmpty()) { "At least one page required" }
        val encodedPages = pages.map { bmp -> encodePageDescriptor(bmp, quality) }
        writeEncodedPagesPdf(encodedPages, output)
    }

    suspend fun exportMultiPagePdf(
        ctx: Context,
        uris: List<Uri>,
        mode: PdfLayoutMode,
        title: String? = null,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): FarResult<Uri> = withContext(Dispatchers.IO) {
        farCatching {
            val capped = uris.take(24)
            require(capped.isNotEmpty()) { "No photos selected" }
            val descriptors = ArrayList<EncodedPdfPage>()
            if (mode == PdfLayoutMode.PhotoBook) {
                capped.forEachIndexed { idx, uri ->
                    onProgress(idx, capped.size)
                    val loaded = when (val r = EditorEngine.load(ctx, uri, 1800)) {
                        is FarResult.Ok -> r.value
                        is FarResult.Err -> throw IllegalStateException("decode failed (${r.error.code})")
                    }
                    try {
                        descriptors += encodePageDescriptor(loaded, 90)
                    } finally {
                        loaded.recycle()
                    }
                }
            } else {
                val perSheet = 6
                val chunks = capped.chunked(perSheet)
                val sheetW = 1240
                val sheetH = 1754 // ~150dpi A4
                val margin = 44f
                val gap = 28f
                val cellW = (sheetW - margin * 2f - gap) / 2f
                val cellH = (sheetH - margin * 2f - gap * 2f) / 3f
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)

                chunks.forEachIndexed { sheetIdx, batch ->
                    val sheet = Bitmap.createBitmap(sheetW, sheetH, Bitmap.Config.ARGB_8888)
                    try {
                        val canvas = Canvas(sheet)
                        canvas.drawColor(Color.WHITE)
                        batch.forEachIndexed { cellIdx, uri ->
                            val globalIdx = sheetIdx * perSheet + cellIdx
                            onProgress(globalIdx, capped.size)
                            val bmp = when (val r = EditorEngine.load(ctx, uri, 720)) {
                                is FarResult.Ok -> r.value
                                is FarResult.Err -> throw IllegalStateException("decode failed (${r.error.code})")
                            }
                            try {
                                val col = cellIdx % 2
                                val row = cellIdx / 2
                                val left = margin + col * (cellW + gap)
                                val top = margin + row * (cellH + gap)
                                val scale = minOf(cellW / bmp.width.coerceAtLeast(1), cellH / bmp.height.coerceAtLeast(1))
                                val drawW = bmp.width * scale
                                val drawH = bmp.height * scale
                                val dx = left + (cellW - drawW) / 2f
                                val dy = top + (cellH - drawH) / 2f
                                val dstRect = android.graphics.RectF(dx, dy, dx + drawW, dy + drawH)
                                canvas.drawBitmap(bmp, null, dstRect, paint)
                            } finally {
                                bmp.recycle()
                            }
                        }
                        descriptors += encodePageDescriptor(sheet, 90)
                    } finally {
                        sheet.recycle()
                    }
                }
            }
            onProgress(capped.size, capped.size)

            val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())
            val safeBase = title
                ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
                ?.take(30)
                ?.ifBlank { null }
                ?: if (mode == PdfLayoutMode.ContactSheet) "FAR_SHEET_$stamp" else "FAR_BOOK_$stamp"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "$safeBase.pdf")
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Far Gallery")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val out = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw java.io.IOException("insert pdf failed")
            try {
                resolver.openOutputStream(out)?.use { stream ->
                    writeEncodedPagesPdf(descriptors, stream)
                } ?: throw java.io.IOException("openOutputStream returned null")
                resolver.update(out, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            } catch (e: Throwable) {
                resolver.delete(out, null, null)
                throw e
            }
            out
        }
    }

    private data class EncodedPdfPage(
        val imgW: Int,
        val imgH: Int,
        val jpgBytes: ByteArray,
    )

    private fun encodePageDescriptor(bitmap: Bitmap, quality: Int): EncodedPdfPage {
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
        return EncodedPdfPage(
            imgW = bitmap.width.coerceAtLeast(1),
            imgH = bitmap.height.coerceAtLeast(1),
            jpgBytes = jpgBytes,
        )
    }

    private fun writeEncodedPagesPdf(pages: List<EncodedPdfPage>, output: OutputStream) {
        val buf = ByteArrayOutputStream()
        fun writeAscii(s: String) = buf.write(s.toByteArray(Charsets.US_ASCII))

        val totalObjects = 2 + pages.size * 3
        val offsets = IntArray(totalObjects + 1)

        writeAscii("%PDF-1.4\n%\u00E2\u00E3\u00CF\u00D3\n")

        offsets[1] = buf.size()
        writeAscii("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        offsets[2] = buf.size()
        val kids = pages.indices.joinToString(" ") { idx -> "${3 + idx * 3} 0 R" }
        writeAscii("2 0 obj\n<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>\nendobj\n")

        pages.forEachIndexed { idx, p ->
            val pageObj = 3 + idx * 3
            val contentObj = 4 + idx * 3
            val imgObj = 5 + idx * 3
            val landscape = p.imgW > p.imgH
            val pageW = if (landscape) 842f else 595f
            val pageH = if (landscape) 595f else 842f
            val margin = 24f
            val scale = minOf((pageW - margin * 2f) / p.imgW, (pageH - margin * 2f) / p.imgH)
            val drawW = p.imgW * scale
            val drawH = p.imgH * scale
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

            offsets[pageObj] = buf.size()
            writeAscii(
                String.format(
                    Locale.US,
                    "$pageObj 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 %.0f %.0f] /Resources << /XObject << /Im0 $imgObj 0 R >> >> /Contents $contentObj 0 R >>\nendobj\n",
                    pageW,
                    pageH,
                )
            )

            offsets[contentObj] = buf.size()
            writeAscii("$contentObj 0 obj\n<< /Length ${contentStream.size} >>\nstream\n")
            buf.write(contentStream)
            writeAscii("endstream\nendobj\n")

            offsets[imgObj] = buf.size()
            writeAscii(
                "$imgObj 0 obj\n<< /Type /XObject /Subtype /Image /Width ${p.imgW} /Height ${p.imgH} " +
                    "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${p.jpgBytes.size} >>\nstream\n"
            )
            buf.write(p.jpgBytes)
            writeAscii("\nendstream\nendobj\n")
        }

        val xrefOffset = buf.size()
        writeAscii("xref\n0 ${totalObjects + 1}\n")
        writeAscii("0000000000 65535 f \n")
        for (i in 1..totalObjects) {
            writeAscii(String.format(Locale.US, "%010d 00000 n \n", offsets[i]))
        }
        writeAscii("trailer\n<< /Size ${totalObjects + 1} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        buf.writeTo(output)
    }
}
