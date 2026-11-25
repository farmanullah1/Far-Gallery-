package com.example.core.vault

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.common.FarResult
import com.example.core.common.LocalDiagnostics
import com.example.core.common.farCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/** Abstraction allowing unit tests to supply an ephemeral SecretKey where AndroidKeyStore is unavailable. */
fun interface VaultKeyProvider {
    fun getOrCreateKey(): SecretKey
}

object AndroidKeyStoreVaultKeyProvider : VaultKeyProvider {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "FarVaultMasterKeyV1"

    override fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = ks.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        fun buildSpec(strongBox: Boolean): KeyGenParameterSpec =
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .apply {
                    if (strongBox && android.os.Build.VERSION.SDK_INT >= 28) {
                        setIsStrongBoxBacked(true)
                    }
                }
                .build()

        return try {
            generator.init(buildSpec(strongBox = true))
            generator.generateKey()
        } catch (_: StrongBoxUnavailableException) {
            generator.init(buildSpec(strongBox = false))
            generator.generateKey()
        } catch (_: Exception) {
            // Some OEMs/emulators wrap StrongBox unavailability in ProviderException or KeyStoreException
            generator.init(buildSpec(strongBox = false))
            generator.generateKey()
        }
    }
}

data class EncryptedPayload(
    val cipherBase64: String,
    val ivHex: String,
)

data class EncryptedStreamResult(
    val ivHex: String,
    val plainBytes: Long,
)

/**
 * Hardware-backed AES-256-GCM encryption engine for Private Vault media & Identity Document fields.
 * Uses chunked Cipher.update + final Cipher.doFinal so GCM auth tag verification never silently passes.
 */
class VaultCrypto(
    private val keyProvider: VaultKeyProvider = AndroidKeyStoreVaultKeyProvider,
) {
    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val CHUNK_BYTES = 64 * 1024

        fun bytesToHex(bytes: ByteArray): String =
            bytes.joinToString("") { "%02x".format(it) }

        fun hexToBytes(hex: String): ByteArray {
            val clean = hex.trim()
            require(clean.length % 2 == 0) { "Invalid hex length" }
            return ByteArray(clean.length / 2) { i ->
                clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }
    }

    fun encryptBytes(plain: ByteArray): EncryptedPayload {
        val key = keyProvider.getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(plain)
        val iv = cipher.iv ?: error("AES-GCM cipher did not emit IV")
        return EncryptedPayload(
            cipherBase64 = Base64.getEncoder().encodeToString(encrypted),
            ivHex = bytesToHex(iv),
        )
    }

    fun decryptBytes(cipherBase64: String, ivHex: String): ByteArray {
        val key = keyProvider.getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, hexToBytes(ivHex)))
        val decoded = Base64.getDecoder().decode(cipherBase64)
        return cipher.doFinal(decoded)
    }

    fun encryptString(plain: String): EncryptedPayload =
        encryptBytes(plain.toByteArray(Charsets.UTF_8))

    fun decryptString(cipherBase64: String, ivHex: String): String =
        decryptBytes(cipherBase64, ivHex).toString(Charsets.UTF_8)

    fun encryptStreamToFile(input: InputStream, destFile: File): EncryptedStreamResult {
        destFile.parentFile?.mkdirs()
        val tempFile = File(destFile.parentFile, "${destFile.name}.tmp")
        val key = keyProvider.getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv ?: error("AES-GCM cipher did not emit IV")
        var total = 0L
        try {
            FileOutputStream(tempFile).use { out ->
                val buf = ByteArray(CHUNK_BYTES)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (n > 0) {
                        total += n
                        val chunk = cipher.update(buf, 0, n)
                        if (chunk != null && chunk.isNotEmpty()) out.write(chunk)
                    }
                }
                val finalBlock = cipher.doFinal()
                if (finalBlock != null && finalBlock.isNotEmpty()) out.write(finalBlock)
                out.flush()
            }
            if (destFile.exists()) destFile.delete()
            if (!tempFile.renameTo(destFile)) {
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()
            }
        } catch (t: Throwable) {
            tempFile.delete()
            throw t
        }
        return EncryptedStreamResult(ivHex = bytesToHex(iv), plainBytes = total)
    }

    fun decryptFileToBytes(encryptedFile: File, ivHex: String): ByteArray {
        val key = keyProvider.getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, hexToBytes(ivHex)))
        val out = ByteArrayOutputStream()
        FileInputStream(encryptedFile).use { input ->
            val buf = ByteArray(CHUNK_BYTES)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                if (n > 0) {
                    val plain = cipher.update(buf, 0, n)
                    if (plain != null && plain.isNotEmpty()) out.write(plain)
                }
            }
            val finalBlock = cipher.doFinal()
            if (finalBlock != null && finalBlock.isNotEmpty()) out.write(finalBlock)
        }
        return out.toByteArray()
    }

    fun decryptFileToStream(encryptedFile: File, ivHex: String, output: OutputStream): Long {
        val key = keyProvider.getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, hexToBytes(ivHex)))
        var total = 0L
        FileInputStream(encryptedFile).use { input ->
            val buf = ByteArray(CHUNK_BYTES)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                if (n > 0) {
                    val plain = cipher.update(buf, 0, n)
                    if (plain != null && plain.isNotEmpty()) {
                        output.write(plain)
                        total += plain.size
                    }
                }
            }
            val finalBlock = cipher.doFinal()
            if (finalBlock != null && finalBlock.isNotEmpty()) {
                output.write(finalBlock)
                total += finalBlock.size
            }
            output.flush()
        }
        return total
    }
}

private val Context.vaultSecurityStore by preferencesDataStore(
    name = "far_vault_security",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

data class VaultSecurityState(
    val isPinConfigured: Boolean = false,
    val biometricEnabled: Boolean = true,
    val failedAttempts: Int = 0,
    val lockedUntilMs: Long = 0L,
)

sealed interface PinVerifyResult {
    data object Verified : PinVerifyResult
    data class Invalid(val failedAttempts: Int, val lockedUntilMs: Long) : PinVerifyResult
    data class Cooldown(val lockedUntilMs: Long) : PinVerifyResult
    data object NotConfigured : PinVerifyResult
}

/** Salted PBKDF2-HMAC-SHA256 PIN verification and cooldown store backed by Preferences DataStore. */
class VaultPinStore(private val ctx: Context) {
    private object K {
        val salt = stringPreferencesKey("pin_salt_b64")
        val hash = stringPreferencesKey("pin_hash_b64")
        val failed = intPreferencesKey("failed_attempts")
        val lockedUntil = longPreferencesKey("locked_until_ms")
        val biometricEnabled = booleanPreferencesKey("biometric_enabled")
    }

    companion object {
        private const val PBKDF2_ALGO = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS = 120_000
        private const val KEY_BITS = 256
        private const val MAX_ATTEMPTS_BEFORE_COOLDOWN = 5

        fun isValidPinFormat(pin: String): Boolean =
            pin.length in 4..8 && pin.all { it in '0'..'9' }
    }

    val stateFlow: Flow<VaultSecurityState> = ctx.vaultSecurityStore.data
        .catch { err ->
            LocalDiagnostics.recordEvent(ctx, "FG-VAULT-PREFS", "vault_prefs_read", err.javaClass.simpleName)
            emit(emptyPreferences())
        }
        .map { p ->
            VaultSecurityState(
                isPinConfigured = !p[K.salt].isNullOrBlank() && !p[K.hash].isNullOrBlank(),
                biometricEnabled = p[K.biometricEnabled] ?: true,
                failedAttempts = p[K.failed] ?: 0,
                lockedUntilMs = p[K.lockedUntil] ?: 0L,
            )
        }

    suspend fun currentState(): VaultSecurityState = stateFlow.first()

    suspend fun setupPin(pin: String): FarResult<Unit> = withContext(Dispatchers.Default) {
        farCatching {
            require(isValidPinFormat(pin)) { "PIN must be 4 to 8 digits" }
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val digest = pbkdf2(pin, salt)
            ctx.vaultSecurityStore.edit { p ->
                p[K.salt] = Base64.getEncoder().encodeToString(salt)
                p[K.hash] = Base64.getEncoder().encodeToString(digest)
                p[K.failed] = 0
                p[K.lockedUntil] = 0L
            }
            Unit
        }
    }

    suspend fun verifyPin(pin: String, nowMs: Long = System.currentTimeMillis()): PinVerifyResult =
        withContext(Dispatchers.Default) {
            val p: Preferences = ctx.vaultSecurityStore.data.catch { emit(emptyPreferences()) }.first()
            val saltB64 = p[K.salt]
            val hashB64 = p[K.hash]
            if (saltB64.isNullOrBlank() || hashB64.isNullOrBlank()) return@withContext PinVerifyResult.NotConfigured

            val lockedUntil = p[K.lockedUntil] ?: 0L
            if (nowMs < lockedUntil) {
                return@withContext PinVerifyResult.Cooldown(lockedUntil)
            }

            val salt = runCatching { Base64.getDecoder().decode(saltB64) }.getOrNull()
                ?: return@withContext PinVerifyResult.NotConfigured
            val expected = runCatching { Base64.getDecoder().decode(hashB64) }.getOrNull()
                ?: return@withContext PinVerifyResult.NotConfigured
            val actual = pbkdf2(pin, salt)
            val match = MessageDigest.isEqual(actual, expected)
            actual.fill(0)

            if (match) {
                ctx.vaultSecurityStore.edit { mut ->
                    mut[K.failed] = 0
                    mut[K.lockedUntil] = 0L
                }
                PinVerifyResult.Verified
            } else {
                var newFailed = 1
                var nextLock = 0L
                ctx.vaultSecurityStore.edit { mut ->
                    newFailed = (mut[K.failed] ?: 0) + 1
                    mut[K.failed] = newFailed
                    if (newFailed >= MAX_ATTEMPTS_BEFORE_COOLDOWN) {
                        val exponent = (newFailed - MAX_ATTEMPTS_BEFORE_COOLDOWN).coerceIn(0, 4)
                        val cooldownSec = (30L shl exponent).coerceAtMost(300L)
                        nextLock = nowMs + cooldownSec * 1000L
                        mut[K.lockedUntil] = nextLock
                    }
                }
                PinVerifyResult.Invalid(newFailed, nextLock)
            }
        }

    suspend fun changePin(oldPin: String, newPin: String): FarResult<Boolean> =
        withContext(Dispatchers.Default) {
            farCatching {
                require(isValidPinFormat(newPin)) { "New PIN must be 4 to 8 digits" }
                when (verifyPin(oldPin)) {
                    PinVerifyResult.Verified -> {
                        when (val res = setupPin(newPin)) {
                            is FarResult.Ok -> true
                            is FarResult.Err -> throw IllegalStateException(res.error.code)
                        }
                    }
                    else -> false
                }
            }
        }

    suspend fun recordBiometricUnlock() {
        ctx.vaultSecurityStore.edit { p ->
            p[K.failed] = 0
            p[K.lockedUntil] = 0L
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        ctx.vaultSecurityStore.edit { p -> p[K.biometricEnabled] = enabled }
    }

    private fun pbkdf2(pin: String, salt: ByteArray): ByteArray {
        val chars = pin.toCharArray()
        val spec = PBEKeySpec(chars, salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(PBKDF2_ALGO).generateSecret(spec).encoded
        } finally {
            chars.fill('\u0000')
            spec.clearPassword()
        }
    }
}
