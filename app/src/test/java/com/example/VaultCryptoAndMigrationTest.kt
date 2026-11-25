package com.example

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.FarResult
import com.example.core.database.FarDatabase
import com.example.core.database.IdentityDocEntity
import com.example.core.database.MetadataBackup
import com.example.core.database.VaultItemEntity
import com.example.core.vault.DocExpiryStatus
import com.example.core.vault.PinVerifyResult
import com.example.core.vault.VaultCrypto
import com.example.core.vault.VaultKeyProvider
import com.example.core.vault.VaultPinStore
import com.example.core.vault.VaultRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.GeneralSecurityException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@RunWith(RobolectricTestRunner::class)
class VaultCryptoAndMigrationTest {

    private fun testKeyProvider(): VaultKeyProvider {
        val gen = KeyGenerator.getInstance("AES").apply { init(256) }
        val key: SecretKey = gen.generateKey()
        return VaultKeyProvider { key }
    }

    @Test
    fun aesGcm_roundTripAndTamperDetection() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val crypto = VaultCrypto(testKeyProvider())
        val dir = VaultRepository.vaultDir(app)
        assertTrue(File(dir, ".nomedia").exists())

        val secretText = "PASSPORT-987654-XY"
        val enc1 = crypto.encryptString(secretText)
        val enc2 = crypto.encryptString(secretText)
        assertNotEquals("Each encryption must use a fresh random IV", enc1.ivHex, enc2.ivHex)
        assertEquals(secretText, crypto.decryptString(enc1.cipherBase64, enc1.ivHex))

        val blobFile = File(dir, "test_media.bin").apply { delete() }
        val samplePayload = ByteArray(150_000) { (it % 251).toByte() }
        val streamEnc = crypto.encryptStreamToFile(ByteArrayInputStream(samplePayload), blobFile)
        assertEquals(samplePayload.size.toLong(), streamEnc.plainBytes)

        val outStream = ByteArrayOutputStream()
        crypto.decryptFileToStream(blobFile, streamEnc.ivHex, outStream)
        assertArrayEquals(samplePayload, outStream.toByteArray())

        // Flip a byte in the encrypted payload and assert GCM auth tag rejection
        val raw = blobFile.readBytes()
        raw[raw.size / 2] = (raw[raw.size / 2].toInt() xor 0xFF).toByte()
        blobFile.writeBytes(raw)

        var rejected = false
        try {
            crypto.decryptFileToBytes(blobFile, streamEnc.ivHex)
        } catch (_: GeneralSecurityException) {
            rejected = true
        } finally {
            blobFile.delete()
        }
        assertTrue("Tampered ciphertext must fail GCM tag verification", rejected)
    }

    @Test
    fun pinStore_setupVerifyCooldownAndChange() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = VaultPinStore(app)
        assertTrue(store.setupPin("246810") is FarResult.Ok)
        assertEquals(PinVerifyResult.Verified, store.verifyPin("246810", nowMs = 1_000L))

        // Trigger 5 consecutive invalid attempts -> cooldown kicks in
        var lastResult: PinVerifyResult? = null
        for (i in 1..5) {
            lastResult = store.verifyPin("000000", nowMs = 2_000L + i)
        }
        assertTrue(lastResult is PinVerifyResult.Invalid)
        val lockedUntil = (lastResult as PinVerifyResult.Invalid).lockedUntilMs
        assertTrue("5th failed attempt must set cooldown", lockedUntil > 2_005L)

        // Even correct PIN is blocked during cooldown
        val blocked = store.verifyPin("246810", nowMs = lockedUntil - 1_000L)
        assertTrue(blocked is PinVerifyResult.Cooldown)

        // After cooldown expires, correct PIN succeeds & resets counter
        assertEquals(PinVerifyResult.Verified, store.verifyPin("246810", nowMs = lockedUntil + 1_000L))
        assertEquals(0, store.currentState().failedAttempts)

        // Change PIN requires correct old PIN
        assertEquals(FarResult.Ok(false), store.changePin("999999", "135790"))
        assertEquals(FarResult.Ok(true), store.changePin("246810", "135790"))
        assertEquals(PinVerifyResult.Verified, store.verifyPin("135790", nowMs = lockedUntil + 5_000L))
    }

    @Test
    fun docExpiryStatus_classifiesBoundariesAccurately() {
        val now = 1_700_000_000_000L
        val day = 24L * 60L * 60L * 1000L
        assertEquals(DocExpiryStatus.NoExpiry, VaultRepository.computeExpiryStatus(null, now))
        assertTrue(VaultRepository.computeExpiryStatus(now - 3 * day, now) is DocExpiryStatus.Expired)
        assertTrue(VaultRepository.computeExpiryStatus(now + 15 * day, now) is DocExpiryStatus.ExpiringSoon)
        assertTrue(VaultRepository.computeExpiryStatus(now + 180 * day, now) is DocExpiryStatus.Valid)
    }

    @Test
    fun migrate2To3_preservesV2DataAndMetadataBackupExcludesVault() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val schema2File = listOf(
            File("schemas/com.example.core.database.FarDatabase/2.json"),
            File("app/schemas/com.example.core.database.FarDatabase/2.json"),
        ).firstOrNull { it.exists() } ?: error("2.json schema not found")

        val schemaJson = JSONObject(schema2File.readText()).getJSONObject("database")
        val dbFile = app.getDatabasePath("migration-test-2-3.db").apply {
            parentFile?.mkdirs()
            delete()
        }
        File("${dbFile.absolutePath}-wal").delete()
        File("${dbFile.absolutePath}-shm").delete()

        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { raw ->
            val entities = schemaJson.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                val createSql = entity.getString("createSql").replace("\${TABLE_NAME}", table)
                raw.execSQL(createSql)
                val indices = entity.optJSONArray("indices")
                if (indices != null) {
                    for (j in 0 until indices.length()) {
                        val idxSql = indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table)
                        raw.execSQL(idxSql)
                    }
                }
            }
            val setupStmts = schemaJson.optJSONArray("setupQueries")
            if (setupStmts != null) {
                for (i in 0 until setupStmts.length()) {
                    raw.execSQL(setupStmts.getString(i))
                }
            }
            raw.execSQL("INSERT INTO album (id, name, createdAt, pinned, sortOrder) VALUES (1, 'V2 Album', 2000, 1, 0)")
            raw.execSQL("INSERT INTO favorite (mediaId, addedAt) VALUES (202, 2000)")
            raw.version = 2
        }

        val migrated = Room.databaseBuilder(app, FarDatabase::class.java, dbFile.name)
            .addMigrations(FarDatabase.MIGRATION_1_2, FarDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals("V2 Album", migrated.albums().albums().first().first().name)
            assertTrue(202L in migrated.favorites().ids().first())

            migrated.vaultItems().upsert(
                VaultItemEntity(
                    id = "v-1",
                    encryptedFileName = "v-1.bin",
                    ivHex = "00112233445566778899aabb",
                    originalName = "SecretPhoto.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = 4096L,
                    isVideo = false,
                    createdAt = 5000L,
                )
            )
            migrated.identityDocs().upsert(
                IdentityDocEntity(
                    id = "id-1",
                    docType = "Passport",
                    title = "Travel Passport",
                    holderName = "Alex Doe",
                    docNumberCipherB64 = "SECRET_CIPHER_BLOB",
                    docNumberIvHex = "00112233445566778899aabb",
                    issuer = "Authority",
                    createdAt = 5000L,
                    updatedAt = 5000L,
                )
            )

            assertEquals(1, migrated.vaultItems().all().first().size)
            assertEquals(1, migrated.identityDocs().all().first().size)

            val backupJson = (MetadataBackup.exportJson(migrated) as FarResult.Ok).value
            assertFalse("MetadataBackup must never include vault items", "SecretPhoto.jpg" in backupJson)
            assertFalse("MetadataBackup must never include identity docs", "SECRET_CIPHER_BLOB" in backupJson)
            assertFalse("MetadataBackup must never include vault table keys", "vault_item" in backupJson)
        } finally {
            migrated.close()
        }
    }
}
