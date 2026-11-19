package com.example.core.media

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.core.common.FarResult
import com.example.core.common.farCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

data class QuickExifInfo(
    val camera: String?,
    val aperture: String?,
    val exposure: String?,
    val iso: String?,
    val focalLength: String?,
    val hasGps: Boolean,
) {
    fun summaryLine(): String? {
        val parts = buildList {
            aperture?.let { add(it) }
            exposure?.let { add(it) }
            iso?.let { add(it) }
            focalLength?.let { add(it) }
            if (hasGps) add("GPS")
        }
        return if (parts.isEmpty()) camera else parts.joinToString(" · ")
    }
}

object ExifScrubber {

    suspend fun readQuickExif(resolver: ContentResolver, uri: Uri): QuickExifInfo? = withContext(Dispatchers.IO) {
        runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim().orEmpty()
                val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim().orEmpty()
                val camera = when {
                    model.isEmpty() -> make.ifEmpty { null }
                    model.startsWith(make, ignoreCase = true) -> model
                    make.isNotEmpty() -> "$make $model"
                    else -> model
                }
                val aperture = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
                    .takeIf { it > 0.0 }
                    ?.let { "f/${String.format(java.util.Locale.US, "%.1f", it)}" }
                val expRaw = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
                val exposure = when {
                    expRaw <= 0.0 -> null
                    expRaw >= 1.0 -> "${String.format(java.util.Locale.US, "%.1f", expRaw)}s"
                    else -> "1/${(1.0 / expRaw).toInt().coerceAtLeast(1)}s"
                }
                val isoVal = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
                    ?: exif.getAttribute("PhotographicSensitivity")
                val iso = isoVal?.takeIf { it.isNotBlank() }?.let { "ISO $it" }
                val focal = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM)
                    ?.toIntOrNull()
                    ?.takeIf { it > 0 }
                    ?.let { "${it}mm" }
                    ?: exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
                        .takeIf { it > 0.0 }
                        ?.let { "${it.toInt()}mm" }
                val hasGps = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE) != null &&
                    exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE) != null
                QuickExifInfo(camera, aperture, exposure, iso, focal, hasGps)
            }
        }.getOrNull()
    }

    /**
     * Re-encodes image(s) without GPS, camera, timestamp, or embedded EXIF thumbnails for clean sharing.
     * Videos or unreadable items fall back to their original URI.
     */
    suspend fun createStrippedShareCopies(ctx: Context, uris: List<Uri>): List<Uri> = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "stripped_shares").apply {
            if (exists()) listFiles()?.forEach { it.delete() } else mkdirs()
        }
        val authority = "${ctx.packageName}.fileprovider"
        uris.mapIndexed { idx, uri ->
            val mime = ctx.contentResolver.getType(uri).orEmpty()
            if (!mime.startsWith("image/")) {
                uri
            } else {
                runCatching {
                    val bmp = decodeForCleanExport(ctx.contentResolver, uri, 4096)
                    val outFile = File(dir, "clean_${System.currentTimeMillis()}_$idx.jpg")
                    try {
                        FileOutputStream(outFile).use { fos ->
                            if (!bmp.compress(Bitmap.CompressFormat.JPEG, 95, fos)) {
                                throw java.io.IOException("compress failed")
                            }
                        }
                    } finally {
                        bmp.recycle()
                    }
                    FileProvider.getUriForFile(ctx, authority, outFile)
                }.getOrElse { uri }
            }
        }
    }

    /** Saves an EXIF/GPS-free JPEG copy of [sourceUri] into Pictures/Far Gallery. */
    suspend fun saveCleanCopyToGallery(ctx: Context, sourceUri: Uri, baseName: String? = null): FarResult<Uri> =
        withContext(Dispatchers.IO) {
            farCatching {
                val bmp = decodeForCleanExport(ctx.contentResolver, sourceUri, 4096)
                try {
                    val cleanName = (baseName?.substringBeforeLast('.')?.take(36)?.ifBlank { null } ?: "CLEAN_${System.currentTimeMillis()}") + "_clean.jpg"
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, cleanName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Far Gallery")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val r = ctx.contentResolver
                    val out = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: throw java.io.IOException("insert failed")
                    try {
                        r.openOutputStream(out)!!.use { os ->
                            if (!bmp.compress(Bitmap.CompressFormat.JPEG, 95, os)) {
                                throw java.io.IOException("encode failed")
                            }
                        }
                        r.update(out, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                    } catch (e: Exception) {
                        r.delete(out, null, null)
                        throw e
                    }
                    out
                } finally {
                    bmp.recycle()
                }
            }
        }

    private fun decodeForCleanExport(resolver: ContentResolver, uri: Uri, maxEdge: Int): Bitmap {
        val src = ImageDecoder.createSource(resolver, uri)
        return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            val longEdge = max(info.size.width, info.size.height)
            if (longEdge > maxEdge) {
                val f = maxEdge.toFloat() / longEdge
                decoder.setTargetSize(
                    (info.size.width * f).toInt().coerceAtLeast(1),
                    (info.size.height * f).toInt().coerceAtLeast(1),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
    }
}
