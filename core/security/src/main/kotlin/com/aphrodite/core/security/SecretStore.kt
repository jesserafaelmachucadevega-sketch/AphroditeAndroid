package com.aphrodite.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores credentials encrypted with a hardware-backed key.
 *
 * Values are addressed by an opaque reference so the database can record *which* secret a row
 * uses without ever holding the secret itself. The key lives in the Android Keystore and cannot
 * be exported; the ciphertext lives in a private file.
 *
 * Ciphertext format is `iv || ciphertext`, base64 encoded.
 */
@Singleton
class SecretStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private val file: File get() = File(context.filesDir, "secrets.enc")

    /** Reads and decrypts a value, or null when the reference is unknown or undecryptable. */
    suspend fun get(reference: String): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val stored = load()[reference] ?: return@withLock null
            runCatching { decrypt(stored) }.getOrNull()
        }
    }

    /** Encrypts and stores a value under [reference], replacing any existing value. */
    suspend fun put(reference: String, value: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val entries = load().toMutableMap()
            entries[reference] = encrypt(value)
            persist(entries)
        }
    }

    /** Removes a stored value. */
    suspend fun remove(reference: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val entries = load().toMutableMap()
            if (entries.remove(reference) != null) persist(entries)
        }
    }

    /** True when a value exists for the reference. */
    suspend fun contains(reference: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock { load().containsKey(reference) }
    }

    /** Every reference currently held, used by the credential manager UI. */
    suspend fun references(): Set<String> = withContext(Dispatchers.IO) {
        mutex.withLock { load().keys.toSet() }
    }

    /** Drops every stored secret. Used by the danger-zone wipe. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (file.exists()) file.delete()
        }
    }

    // ── Storage ───────────────────────────────────────────────────────────────

    private fun load(): Map<String, String> {
        if (!file.exists()) return emptyMap()
        return runCatching {
            file.readLines()
                .filter { it.isNotBlank() && it.contains('\t') }
                .associate { line ->
                    val index = line.indexOf('\t')
                    line.substring(0, index) to line.substring(index + 1)
                }
        }.getOrDefault(emptyMap())
    }

    private fun persist(entries: Map<String, String>) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(entries.entries.joinToString("\n") { "${it.key}\t${it.value}" })
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
    }

    // ── Cryptography ──────────────────────────────────────────────────────────

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    private fun decrypt(encoded: String): String {
        val bytes = Base64.getDecoder().decode(encoded)
        require(bytes.size > IV_LENGTH) { "Ciphertext is too short" }
        val iv = bytes.copyOfRange(0, IV_LENGTH)
        val payload = bytes.copyOfRange(IV_LENGTH, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(payload), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // Deliberately not requiring user authentication: the app needs to read mail and
                // CalDAV credentials for background sync while the device is locked.
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "aphrodite_master_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
    }
}
