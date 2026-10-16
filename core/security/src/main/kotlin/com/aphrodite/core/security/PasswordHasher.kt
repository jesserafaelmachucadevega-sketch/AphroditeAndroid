package com.aphrodite.core.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Password hashing for local profiles.
 *
 * Uses PBKDF2-HMAC-SHA256 with a per-password random salt and a work factor tuned for a
 * mid-range phone. The stored format is self-describing so the iteration count can be raised
 * later without invalidating existing passwords:
 *
 * `pbkdf2-sha256$<iterations>$<salt-b64>$<hash-b64>`
 */
object PasswordHasher {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PREFIX = "pbkdf2-sha256"
    private const val ITERATIONS = 210_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256

    /** Hashes [password] into a self-describing string suitable for storage. */
    fun hash(password: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val derived = derive(password, salt, ITERATIONS)
        return buildString {
            append(PREFIX).append('$')
            append(ITERATIONS).append('$')
            append(Base64.getEncoder().encodeToString(salt)).append('$')
            append(Base64.getEncoder().encodeToString(derived))
        }
    }

    /**
     * Verifies [password] against a stored hash.
     *
     * Malformed stored values fail closed rather than throwing, so a corrupt row cannot be used
     * to bypass the check.
     */
    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = runCatching { Base64.getDecoder().decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(parts[3]) }.getOrNull() ?: return false

        val derived = derive(password, salt, iterations)
        return constantTimeEquals(derived, expected)
    }

    /** True when a stored value was produced with a lower work factor than the current target. */
    fun needsRehash(stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 4 || parts[0] != PREFIX) return true
        return (parts[1].toIntOrNull() ?: 0) < ITERATIONS
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}
