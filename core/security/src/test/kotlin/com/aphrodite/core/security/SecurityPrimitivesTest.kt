package com.aphrodite.core.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Verifies the local credential primitives.
 *
 * The TOTP vectors are the RFC 6238 reference values, which is the only way to be sure codes
 * will match what an authenticator app produces.
 */
class SecurityPrimitivesTest {

    // ── TOTP ──────────────────────────────────────────────────────────────────

    /** The RFC 6238 appendix B seed, base32 encoded. */
    private val rfcSecret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

    @Test
    fun `matches the RFC 6238 reference vectors`() {
        assertEquals("94287082", codeAt(59L))
        assertEquals("07081804", codeAt(1111111109L))
        assertEquals("14050471", codeAt(1111111111L))
        assertEquals("89005924", codeAt(1234567890L))
        assertEquals("69279037", codeAt(2000000000L))
        assertEquals("65353130", codeAt(20000000000L))
    }

    private fun codeAt(seconds: Long): String = TotpGenerator.generate(
        secret = rfcSecret,
        timestampMillis = seconds * 1000L,
        digits = 8,
    )

    @Test
    fun `verification accepts the current code`() {
        val now = 1_700_000_000_000L
        val code = TotpGenerator.generate(rfcSecret, now)
        assertTrue(TotpGenerator.verify(rfcSecret, code, now))
    }

    @Test
    fun `verification tolerates one step of clock drift`() {
        val now = 1_700_000_000_000L
        val code = TotpGenerator.generate(rfcSecret, now)
        // The device clock is 30 seconds behind the generator.
        assertTrue(TotpGenerator.verify(rfcSecret, code, now + 30_000L))
        // ...and 30 seconds ahead.
        assertTrue(TotpGenerator.verify(rfcSecret, code, now - 30_000L))
    }

    @Test
    fun `verification rejects a code from far outside the window`() {
        val now = 1_700_000_000_000L
        val code = TotpGenerator.generate(rfcSecret, now)
        assertFalse(TotpGenerator.verify(rfcSecret, code, now + 300_000L))
    }

    @Test
    fun `verification rejects malformed input rather than throwing`() {
        val now = 1_700_000_000_000L
        assertFalse(TotpGenerator.verify(rfcSecret, "", now))
        assertFalse(TotpGenerator.verify(rfcSecret, "abcdef", now))
        assertFalse(TotpGenerator.verify(rfcSecret, "12345", now))
        assertFalse(TotpGenerator.verify(rfcSecret, "1234567", now))
    }

    @Test
    fun `seconds remaining counts down across the period`() {
        assertEquals(30, TotpGenerator.secondsRemaining(0L))
        assertEquals(29, TotpGenerator.secondsRemaining(1_000L))
        assertEquals(1, TotpGenerator.secondsRemaining(29_000L))
        assertEquals(30, TotpGenerator.secondsRemaining(30_000L))
    }

    @Test
    fun `base32 round trips`() {
        val original = "Hello, Aphrodite!".toByteArray()
        val encoded = TotpGenerator.encodeBase32(original)
        assertTrue(TotpGenerator.decodeBase32(encoded).contentEquals(original))
    }

    @Test
    fun `base32 decoding ignores case padding and separators`() {
        val expected = TotpGenerator.decodeBase32("GEZDGNBVGY3TQOJQ")
        assertTrue(TotpGenerator.decodeBase32("gezd gnbv gy3t qojq").contentEquals(expected))
        assertTrue(TotpGenerator.decodeBase32("GEZDGNBVGY3TQOJQ====").contentEquals(expected))
    }

    @Test
    fun `generated secrets are usable and unique`() {
        val a = TotpGenerator.generateSecret()
        val b = TotpGenerator.generateSecret()
        assertNotEquals(a, b)
        // A generated secret must decode back to 20 bytes.
        assertEquals(20, TotpGenerator.decodeBase32(a).size)
        // And must produce a code that verifies against itself.
        val now = 1_700_000_000_000L
        assertTrue(TotpGenerator.verify(a, TotpGenerator.generate(a, now), now))
    }

    @Test
    fun `provisioning uri carries the enrolment parameters`() {
        val uri = TotpGenerator.provisioningUri("ABCDEFGH", "jesse", issuer = "Aphrodite")
        assertTrue(uri.startsWith("otpauth://totp/Aphrodite:jesse?"))
        assertTrue(uri.contains("secret=ABCDEFGH"))
        assertTrue(uri.contains("digits=6"))
        assertTrue(uri.contains("period=30"))
    }

    // ── Password hashing ──────────────────────────────────────────────────────

    @Test
    fun `a hashed password verifies`() {
        val stored = PasswordHasher.hash("correct horse battery staple")
        assertTrue(PasswordHasher.verify("correct horse battery staple", stored))
    }

    @Test
    fun `a wrong password does not verify`() {
        val stored = PasswordHasher.hash("correct horse battery staple")
        assertFalse(PasswordHasher.verify("Correct horse battery staple", stored))
        assertFalse(PasswordHasher.verify("", stored))
        assertFalse(PasswordHasher.verify("correct horse battery stapl", stored))
    }

    @Test
    fun `the same password hashes differently each time`() {
        val first = PasswordHasher.hash("hunter2")
        val second = PasswordHasher.hash("hunter2")
        assertNotEquals(first, second)
        assertTrue(PasswordHasher.verify("hunter2", first))
        assertTrue(PasswordHasher.verify("hunter2", second))
    }

    @Test
    fun `a malformed stored hash fails closed`() {
        assertFalse(PasswordHasher.verify("hunter2", ""))
        assertFalse(PasswordHasher.verify("hunter2", "not-a-hash"))
        assertFalse(PasswordHasher.verify("hunter2", "pbkdf2-sha256\$abc\$def\$ghi"))
        assertFalse(PasswordHasher.verify("hunter2", "scrypt\$1000\$AAAA\$BBBB"))
    }

    @Test
    fun `the stored format records its own work factor`() {
        val stored = PasswordHasher.hash("hunter2")
        val parts = stored.split('$')
        assertEquals(4, parts.size)
        assertEquals("pbkdf2-sha256", parts[0])
        assertTrue(parts[1].toInt() >= 200_000)

        assertFalse(PasswordHasher.needsRehash(stored))
        assertTrue(PasswordHasher.needsRehash("pbkdf2-sha256\$1000\$AAAA\$BBBB"))
        assertTrue(PasswordHasher.needsRehash("garbage"))
    }
}
