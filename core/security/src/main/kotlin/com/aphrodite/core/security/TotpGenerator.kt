package com.aphrodite.core.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 6238 time-based one-time passwords, compatible with the authenticator apps the upstream
 * two-factor flow expects.
 */
object TotpGenerator {

    const val DEFAULT_DIGITS = 6
    const val DEFAULT_PERIOD_SECONDS = 30

    /** The alphabet used by RFC 4648 base32, which is how shared secrets are normally written. */
    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /**
     * The code for [secret] at [timestampMillis].
     *
     * [algorithm] is the HMAC name, defaulting to SHA-1 for compatibility with existing
     * authenticator enrolments.
     */
    fun generate(
        secret: String,
        timestampMillis: Long = System.currentTimeMillis(),
        digits: Int = DEFAULT_DIGITS,
        periodSeconds: Int = DEFAULT_PERIOD_SECONDS,
        algorithm: String = "HmacSHA1",
    ): String {
        val counter = (timestampMillis / 1000) / periodSeconds
        val hash = hmac(algorithm, decodeBase32(secret), counter.toBigEndianBytes())
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        val modulus = powerOfTen(digits)
        return (binary % modulus).toString().padStart(digits, '0')
    }

    /** Seconds until the current code expires. */
    fun secondsRemaining(
        timestampMillis: Long = System.currentTimeMillis(),
        periodSeconds: Int = DEFAULT_PERIOD_SECONDS,
    ): Int {
        val elapsed = (timestampMillis / 1000) % periodSeconds
        return (periodSeconds - elapsed).toInt()
    }

    /**
     * Checks a submitted code against the current window.
     *
     * A window of one step either side is accepted to tolerate clock drift between the device
     * and the code generator, which is the same tolerance the upstream server uses.
     */
    fun verify(
        secret: String,
        code: String,
        timestampMillis: Long = System.currentTimeMillis(),
        digits: Int = DEFAULT_DIGITS,
        periodSeconds: Int = DEFAULT_PERIOD_SECONDS,
        window: Int = 1,
    ): Boolean {
        val candidate = code.trim().replace(" ", "")
        if (candidate.length != digits || candidate.any { !it.isDigit() }) return false

        for (step in -window..window) {
            val at = timestampMillis + step * periodSeconds * 1000L
            if (constantTimeEquals(generate(secret, at, digits, periodSeconds), candidate)) return true
        }
        return false
    }

    /** Generates a new random base32 secret suitable for enrolment. */
    fun generateSecret(byteLength: Int = 20): String {
        val bytes = ByteArray(byteLength)
        java.security.SecureRandom().nextBytes(bytes)
        return encodeBase32(bytes)
    }

    /**
     * The `otpauth://` URI an authenticator app scans.
     *
     * The QR code is rendered by the caller from this string.
     */
    fun provisioningUri(
        secret: String,
        accountName: String,
        issuer: String = "Aphrodite",
        digits: Int = DEFAULT_DIGITS,
        periodSeconds: Int = DEFAULT_PERIOD_SECONDS,
    ): String {
        // The label keeps its colon literal; only the two halves are percent-encoded. Encoding
        // the whole label would turn "Issuer:user" into "Issuer%3Auser", which some
        // authenticator apps then display as a single unparsed token.
        val label = "${encode(issuer)}:${encode(accountName)}"
        return buildString {
            append("otpauth://totp/")
            append(label)
            append("?secret=").append(secret)
            append("&issuer=").append(encode(issuer))
            append("&algorithm=SHA1")
            append("&digits=").append(digits)
            append("&period=").append(periodSeconds)
        }
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun hmac(algorithm: String, key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(key, algorithm))
        return mac.doFinal(data)
    }

    private fun Long.toBigEndianBytes(): ByteArray = byteArrayOf(
        (this shr 56).toByte(),
        (this shr 48).toByte(),
        (this shr 40).toByte(),
        (this shr 32).toByte(),
        (this shr 24).toByte(),
        (this shr 16).toByte(),
        (this shr 8).toByte(),
        this.toByte(),
    )

    private fun powerOfTen(digits: Int): Int {
        var result = 1
        repeat(digits) { result *= 10 }
        return result
    }

    /** Compares without leaking length-independent timing, for code verification. */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    /** Decodes base32, ignoring padding and whitespace and accepting lower case. */
    fun decodeBase32(input: String): ByteArray {
        val cleaned = input.uppercase().filter { it in BASE32_ALPHABET }
        val output = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bitsLeft = 0
        for (char in cleaned) {
            val value = BASE32_ALPHABET.indexOf(char)
            if (value < 0) continue
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                output.write((buffer shr (bitsLeft - 8)) and 0xFF)
                bitsLeft -= 8
            }
        }
        return output.toByteArray()
    }

    /** Encodes base32 without padding, which is how shared secrets are written. */
    fun encodeBase32(bytes: ByteArray): String {
        val output = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                output.append(BASE32_ALPHABET[(buffer shr (bitsLeft - 5)) and 0x1F])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) {
            output.append(BASE32_ALPHABET[(buffer shl (5 - bitsLeft)) and 0x1F])
        }
        return output.toString()
    }
}
