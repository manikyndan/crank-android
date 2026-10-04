package com.crank.music.data.remote.gaana

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contract between the settings screen and [GaanaApi.baseUrl]: both use
 * [validateGaanaBaseUrl], so nothing here can be accepted on screen and refused at runtime.
 */
class GaanaServerUrlTest {

    @Test
    fun `empty means the source is off, not an error`() {
        assertEquals(GaanaUrlValidation.Disabled, validateGaanaBaseUrl(""))
        assertEquals(GaanaUrlValidation.Disabled, validateGaanaBaseUrl("   "))
    }

    @Test
    fun `a lan address is accepted`() {
        assertEquals(
            GaanaUrlValidation.Ok("http://192.168.1.42:8000"),
            validateGaanaBaseUrl("http://192.168.1.42:8000"),
        )
    }

    @Test
    fun `https works and is preferred by the shape of the rule`() {
        assertEquals(
            GaanaUrlValidation.Ok("https://music.example.com/gaanapy"),
            validateGaanaBaseUrl("https://music.example.com/gaanapy"),
        )
    }

    /** Surrounding whitespace and trailing slashes are cosmetic, so the verdict ignores them. */
    @Test
    fun `surrounding whitespace and the trailing slash are normalised`() {
        assertEquals(
            GaanaUrlValidation.Ok("http://192.168.1.42:8000"),
            validateGaanaBaseUrl("  http://192.168.1.42:8000/  "),
        )
    }

    /**
     * Without a scheme Ktor treats the value as a relative reference and resolves it against
     * nothing, surfacing much later as an opaque connection failure rather than as a mistyped
     * setting. Pointing this out here is the whole reason validation exists.
     */
    @Test
    fun `a missing scheme is rejected with a fix attached`() {
        val verdict = validateGaanaBaseUrl("192.168.1.42:8000")
        assertTrue(verdict is GaanaUrlValidation.Rejected)
        assertTrue((verdict as GaanaUrlValidation.Rejected).reason.contains("http://"))
    }

    @Test
    fun `a scheme with no host is rejected`() {
        assertTrue(validateGaanaBaseUrl("http://") is GaanaUrlValidation.Rejected)
        assertTrue(validateGaanaBaseUrl("http:///path") is GaanaUrlValidation.Rejected)
    }

    @Test
    fun `a host with spaces is rejected`() {
        assertTrue(validateGaanaBaseUrl("http://192.168.1. 42:8000") is GaanaUrlValidation.Rejected)
    }

    @Test
    fun `an unsupported protocol is not a gaana server`() {
        assertTrue(validateGaanaBaseUrl("ftp://192.168.1.42:8000") is GaanaUrlValidation.Rejected)
    }
}
