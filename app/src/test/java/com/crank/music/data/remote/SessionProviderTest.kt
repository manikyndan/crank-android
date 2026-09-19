package com.crank.music.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for visitor-data extraction from the `sw.js_data` payload.
 *
 * These matter because both traps in this payload fail with messages that do not point at the
 * cause: forgetting the XSSI prefix produces "unexpected token at offset 0", and looking at the
 * wrong nesting level produces a plain null that is indistinguishable from an empty response.
 * Either one silently degrades playback to the preview-length clients.
 *
 * The fixture below mirrors the real payload's structure, which was captured and inspected
 * directly rather than assumed.
 */
class SessionProviderTest {

    /** A realistic response: XSSI guard, blank line, then the nested array. */
    private fun payload(vararg fields: String): String {
        val inner = fields.joinToString(",") { "\"$it\"" }
        return ")]}'\n\n[[\"yt.sw.adr\",null,[$inner]]]"
    }

    private val realVisitorData =
        "CgtEakZZTjd0bk92RSj9w7jVBjIKCgJJThIEGgAgImLfAgrcAjIxLllUPWV1LXlzYkZXSU5kTTFHcmV5ejR4" +
            "Y0JoaDhWZ3J4bWx0U2wwa21pTjA3YVhnZ0thNXNrWFBPcUU0V3JZNjRRRVNZeTdla0ZVeXhqangwSDhjb3Vt" +
            "MU5RVVMwUmdTWkQ0a2ZKUFJKNFZoMVNGOG14cjhqcnlCaWY"

    @Test
    fun `extracts visitor data from a realistic payload`() {
        val raw = payload("en-GB", "IN", "103.114.211.143", realVisitorData)

        assertEquals(realVisitorData, SessionProvider.extractVisitorData(raw))
    }

    @Test
    fun `strips the xssi guard before parsing`() {
        // Without stripping, the body is not JSON and parsing fails. Asserting the value is
        // found — rather than only that no exception was thrown — is what makes this test able
        // to fail if the guard handling regresses.
        val raw = payload(realVisitorData)
        assertEquals(realVisitorData, SessionProvider.extractVisitorData(raw))
    }

    @Test
    fun `tolerates a body with no xssi guard`() {
        val raw = "[[\"yt.sw.adr\",null,[\"$realVisitorData\"]]]"
        assertEquals(realVisitorData, SessionProvider.extractVisitorData(raw))
    }

    @Test
    fun `tolerates a leading byte order mark`() {
        // Some responses include a BOM. A fixed-offset read of the guard would miss it.
        val raw = "\uFEFF)]}'\n\n[[\"yt.sw.adr\",null,[\"$realVisitorData\"]]]"
        assertEquals(realVisitorData, SessionProvider.extractVisitorData(raw))
    }

    @Test
    fun `finds visitor data among other fields regardless of position`() {
        val raw = payload("a", "b", "c", "d", realVisitorData, "e")
        assertEquals(realVisitorData, SessionProvider.extractVisitorData(raw))
    }

    @Test
    fun `accepts the Cgs prefix as well as Cgt`() {
        // The prefix is `Cgt` or `Cgs`; both are valid visitor ids.
        val cgsToken = "Cgs" + "X".repeat(60)
        val raw = payload("en-GB", cgsToken)
        assertEquals(cgsToken, SessionProvider.extractVisitorData(raw))
    }

    @Test
    fun `ignores Cg-prefixed fields that are too short to be visitor data`() {
        // A bare "Cg" is not a visitor id. Latching onto it would produce a token bound to
        // nothing, which fails much later and much less clearly.
        val raw = payload("Cg", "other", realVisitorData)
        assertEquals(realVisitorData, SessionProvider.extractVisitorData(raw))
    }

    @Test
    fun `returns null when no visitor data is present`() {
        val raw = payload("en-GB", "IN", "not-a-visitor-id")
        assertNull(SessionProvider.extractVisitorData(raw))
    }

    @Test
    fun `returns null on malformed json rather than throwing`() {
        // A truncated or HTML error body must degrade to "no visitor data", not crash playback.
        assertNull(SessionProvider.extractVisitorData(")]}'\n\nnot json at all"))
    }

    @Test
    fun `returns null on an empty body`() {
        assertNull(SessionProvider.extractVisitorData(""))
    }

    @Test
    fun `returns null when the expected nesting is absent`() {
        // A well-formed JSON body of the wrong shape — for example an error object.
        assertNull(SessionProvider.extractVisitorData("""{"error":"unavailable"}"""))
    }

    @Test
    fun `does not match visitor data appearing outside the expected array`() {
        // Guards against a regex-over-the-whole-body implementation: a token-shaped string in an
        // unrelated field must not be picked up.
        val raw = """{"unrelated":"$realVisitorData"}"""
        assertNull(SessionProvider.extractVisitorData(raw))
    }
}
