package com.example.core.vault

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.core.common.FarResult
import com.example.core.common.farCatching
import com.example.core.database.FarDatabase
import com.example.core.database.IdentityDocEntity
import com.example.core.database.VaultItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

enum class IdentityDocType(val wire: String, val label: String) {
    Passport("Passport", "Passport"),
    NationalId("National ID", "National ID"),
    DriversLicense("Driver's License", "Driver's License"),
    Insurance("Insurance", "Insurance"),
    Custom("Custom", "Custom"),
    ;

    companion object {
        fun fromWire(value: String): IdentityDocType =
            entries.firstOrNull { it.wire == value } ?: Custom
    }
}

sealed interface DocExpiryStatus {
    data object NoExpiry : DocExpiryStatus
    data class Expired(val daysAgo: Int) : DocExpiryStatus
    data class ExpiringSoon(val daysLeft: Int) : DocExpiryStatus
    data class Valid(val daysLeft: Int) : DocExpiryStatus
}

data class DecryptedIdentityDoc(
    val entity: IdentityDocEntity,
    val docNumber: String,
    val notes: String,
    val expiryStatus: DocExpiryStatus,
)

class VaultRepository(
    private val crypto: VaultCrypto = VaultCrypto(),
) {
    companion object {
        private const val VAULT_DIR_NAME = "vault"
        private const val DAY_MS = 24L * 60L * 60L * 1000L

        fun vaultDir(ctx: Context): File {
            val dir = File(ctx.filesDir, VAULT_DIR_NAME)
            if (!dir.exists()) dir.mkdirs()
            val nomedia = File(dir, ".nomedia")
            if (!nomedia.exists()) runCatching { nomedia.createNewFile() }
            return dir
        }

        fun computeExpiryStatus(expiryMs: Long?, nowMs: Long = System.currentTimeMillis()): DocExpiryStatus {
            if (expiryMs == null || expiryMs <= 0L) return DocExpiryStatus.NoExpiry
            val diffDays = ((expiryMs - nowMs).toDouble() / DAY_MS).roundToInt()
            return when {
                expiryMs < nowMs -> DocExpiryStatus.Expired(max(1, -diffDays))
                diffDays <= 90 -> DocExpiryStatus.ExpiringSoon(max(0, diffDays))
                else -> DocExpiryStatus.Valid(diffDays)
            }
        }
    }

    suspend fun importMedia(
        ctx: Context,
        db: FarDatabase,
        uris: List<Uri>,
    ): FarResult<List<VaultItemEntity>> = withContext(Dispatchers.IO) {
        farCatching {
            val dir = vaultDir(ctx)
            val resolver = ctx.contentResolver
            val imported = ArrayList<VaultItemEntity>(uris.size)
            for (uri in uris) {
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                val isVideo = mime.startsWith("video/")
                val name = queryDisplayName(ctx, uri)
                    ?: if (isVideo) "Vault_Video_${System.currentTimeMillis()}.mp4"
                    else "Vault_Photo_${System.currentTimeMillis()}.jpg"

                val id = UUID.randomUUID().toString()
                val fileName = "$id.bin"
                val destFile = File(dir, fileName)

                val enc = resolver.openInputStream(uri)?.use { input ->
                    crypto.encryptStreamToFile(input, destFile)
                } ?: throw IOException("Unable to read source media")

                val dims = if (!isVideo) {
                    runCatching {
                        val plain = crypto.decryptFileToBytes(destFile, enc.ivHex)
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(plain, 0, plain.size, opts)
                        opts.outWidth.coerceAtLeast(0) to opts.outHeight.coerceAtLeast(0)
                    }.getOrDefault(0 to 0)
                } else 0 to 0

                val entity = VaultItemEntity(
                    id = id,
                    encryptedFileName = fileName,
                    ivHex = enc.ivHex,
                    originalName = name,
                    mimeType = mime,
                    sizeBytes = enc.plainBytes,
                    isVideo = isVideo,
                    width = dims.first,
                    height = dims.second,
                    createdAt = System.currentTimeMillis(),
                    originalUri = uri.toString(),
                )
                db.vaultItems().upsert(entity)
                imported += entity
            }
            imported
        }
    }

    /**
     * Decrypts a Vault media item completely in memory without ever touching cacheDir or public storage.
     */
    suspend fun decodeInMemoryBitmap(
        ctx: Context,
        item: VaultItemEntity,
        maxDimension: Int = 1600,
    ): FarResult<Bitmap> = withContext(Dispatchers.IO) {
        farCatching {
            val file = File(vaultDir(ctx), item.encryptedFileName)
            if (!file.exists()) throw IOException("Encrypted blob missing")
            if (item.isVideo) {
                // Keep in-memory video frame decoding bounded to prevent OOM
                if (item.sizeBytes > 24L * 1024L * 1024L) {
                    throw IOException("Encrypted video preview omitted to preserve memory")
                }
                val bytes = crypto.decryptFileToBytes(file, item.ivHex)
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(object : MediaDataSource() {
                        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                            if (position >= bytes.size) return -1
                            val avail = (bytes.size - position.toInt()).coerceAtMost(size)
                            System.arraycopy(bytes, position.toInt(), buffer, offset, avail)
                            return avail
                        }
                        override fun getSize(): Long = bytes.size.toLong()
                        override fun close() {}
                    })
                    mmr.getFrameAtTime(0L) ?: throw IOException("Unable to decode video frame")
                } finally {
                    runCatching { mmr.release() }
                    bytes.fill(0)
                }
            } else {
                val bytes = crypto.decryptFileToBytes(file, item.ivHex)
                try {
                    decodeSampled(bytes, maxDimension)
                        ?: throw IOException("Unable to decode decrypted image")
                } finally {
                    bytes.fill(0)
                }
            }
        }
    }

    suspend fun decodeSideAttachmentBitmap(
        ctx: Context,
        fileName: String,
        ivHex: String,
        maxDimension: Int = 1400,
    ): FarResult<Bitmap> = withContext(Dispatchers.IO) {
        farCatching {
            val file = File(vaultDir(ctx), fileName)
            if (!file.exists()) throw IOException("Encrypted attachment missing")
            val bytes = crypto.decryptFileToBytes(file, ivHex)
            try {
                decodeSampled(bytes, maxDimension)
                    ?: throw IOException("Unable to decode attachment bitmap")
            } finally {
                bytes.fill(0)
            }
        }
    }

    /**
     * Exports a verified AES-GCM decrypted copy back into MediaStore (Pictures/Far Gallery or Movies/Far Gallery).
     * Never clears IS_PENDING unless GCM authentication tag succeeds cleanly.
     */
    suspend fun exportToGallery(ctx: Context, item: VaultItemEntity): FarResult<Uri> =
        withContext(Dispatchers.IO) {
            farCatching {
                val srcFile = File(vaultDir(ctx), item.encryptedFileName)
                if (!srcFile.exists()) throw IOException("Vault item file not found")
                val r = ctx.contentResolver
                val collection = if (item.isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                val relPath = if (item.isVideo) "Movies/Far Gallery" else "Pictures/Far Gallery"
                val safeName = item.originalName.ifBlank {
                    if (item.isVideo) "Restored_${System.currentTimeMillis()}.mp4"
                    else "Restored_${System.currentTimeMillis()}.jpg"
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                    put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val outUri = r.insert(collection, values)
                    ?: throw IOException("Could not create MediaStore target")
                try {
                    r.openOutputStream(outUri)?.use { out ->
                        crypto.decryptFileToStream(srcFile, item.ivHex, out)
                    } ?: throw IOException("Could not open MediaStore output stream")
                    r.update(
                        outUri,
                        ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                        null,
                        null,
                    )
                } catch (t: Throwable) {
                    r.delete(outUri, null, null)
                    throw t
                }
                outUri
            }
        }

    suspend fun deleteVaultItem(ctx: Context, db: FarDatabase, item: VaultItemEntity) {
        withContext(Dispatchers.IO) {
            runCatching { File(vaultDir(ctx), item.encryptedFileName).delete() }
            db.vaultItems().delete(item.id)
        }
    }

    suspend fun saveIdentityDoc(
        ctx: Context,
        db: FarDatabase,
        existingId: String?,
        docType: IdentityDocType,
        title: String,
        holderName: String,
        docNumber: String,
        issuer: String,
        issuedDateMs: Long?,
        expiryDateMs: Long?,
        notes: String,
        newFrontUri: Uri?,
        newBackUri: Uri?,
        clearFront: Boolean = false,
        clearBack: Boolean = false,
    ): FarResult<IdentityDocEntity> = withContext(Dispatchers.IO) {
        farCatching {
            val now = System.currentTimeMillis()
            val prev = existingId?.let { db.identityDocs().get(it) }
            val id = prev?.id ?: UUID.randomUUID().toString()
            val dir = vaultDir(ctx)

            val encNum = crypto.encryptString(docNumber.trim())
            val encNotes = notes.trim().takeIf { it.isNotEmpty() }?.let { crypto.encryptString(it) }

            var frontName = prev?.frontFileName
            var frontIv = prev?.frontIvHex
            if (clearFront && frontName != null) {
                File(dir, frontName).delete()
                frontName = null
                frontIv = null
            }
            if (newFrontUri != null) {
                frontName?.let { File(dir, it).delete() }
                val fName = "id_${id}_front_${now}.bin"
                val fRes = ctx.contentResolver.openInputStream(newFrontUri)?.use {
                    crypto.encryptStreamToFile(it, File(dir, fName))
                } ?: throw IOException("Could not read front attachment")
                frontName = fName
                frontIv = fRes.ivHex
            }

            var backName = prev?.backFileName
            var backIv = prev?.backIvHex
            if (clearBack && backName != null) {
                File(dir, backName).delete()
                backName = null
                backIv = null
            }
            if (newBackUri != null) {
                backName?.let { File(dir, it).delete() }
                val bName = "id_${id}_back_${now}.bin"
                val bRes = ctx.contentResolver.openInputStream(newBackUri)?.use {
                    crypto.encryptStreamToFile(it, File(dir, bName))
                } ?: throw IOException("Could not read back attachment")
                backName = bName
                backIv = bRes.ivHex
            }

            val entity = IdentityDocEntity(
                id = id,
                docType = docType.wire,
                title = title.trim(),
                holderName = holderName.trim(),
                docNumberCipherB64 = encNum.cipherBase64,
                docNumberIvHex = encNum.ivHex,
                issuer = issuer.trim(),
                issuedDateMs = issuedDateMs,
                expiryDateMs = expiryDateMs,
                notesCipherB64 = encNotes?.cipherBase64,
                notesIvHex = encNotes?.ivHex,
                frontFileName = frontName,
                frontIvHex = frontIv,
                backFileName = backName,
                backIvHex = backIv,
                createdAt = prev?.createdAt ?: now,
                updatedAt = now,
            )
            db.identityDocs().upsert(entity)
            entity
        }
    }

    fun decryptIdentityDoc(
        entity: IdentityDocEntity,
        nowMs: Long = System.currentTimeMillis(),
    ): DecryptedIdentityDoc {
        val number = runCatching {
            crypto.decryptString(entity.docNumberCipherB64, entity.docNumberIvHex)
        }.getOrDefault("Unavailable")
        val notes = if (!entity.notesCipherB64.isNullOrBlank() && !entity.notesIvHex.isNullOrBlank()) {
            runCatching {
                crypto.decryptString(entity.notesCipherB64, entity.notesIvHex)
            }.getOrDefault("")
        } else ""
        return DecryptedIdentityDoc(
            entity = entity,
            docNumber = number,
            notes = notes,
            expiryStatus = computeExpiryStatus(entity.expiryDateMs, nowMs),
        )
    }

    suspend fun deleteIdentityDoc(ctx: Context, db: FarDatabase, doc: IdentityDocEntity) {
        withContext(Dispatchers.IO) {
            val dir = vaultDir(ctx)
            doc.frontFileName?.let { File(dir, it).delete() }
            doc.backFileName?.let { File(dir, it).delete() }
            db.identityDocs().delete(doc.id)
        }
    }

    private fun queryDisplayName(ctx: Context, uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun decodeSampled(bytes: ByteArray, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}
