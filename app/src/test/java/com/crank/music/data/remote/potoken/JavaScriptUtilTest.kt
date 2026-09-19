package com.crank.music.data.remote.potoken

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Tests for the BotGuard token encoding helpers.
 *
 * These cover the parts of the PoToken pipeline that do **not** need a WebView, which is the
 * only part that can be verified without a device. They are here because every one of these
 * transforms fails *silently* when wrong: a mangled token is still a non-empty string, and the
 * resulting rejection looks like an anti-bot refusal rather than an encoding bug.
 */
class JavaScriptUtilTest {

    // region u8ToBase64

    @Test
    fun `u8ToBase64 encodes simple ascii bytes`() {
        // "abc" -> [97, 98, 99] -> base64 "YWJj"
        assertEquals("YWJj", u8ToBase64("97,98,99"))
    }

    @Test
    fun `u8ToBase64 treats values above 127 as unsigned`() {
        // 255 must be treated as an unsigned byte, not as the signed value -1.
        // "//8=" in standard base64 means bytes [255, 255]; URL-safe becomes "__8=".
        val encoded = u8ToBase64("255,255")
        assertEquals("__8=", encoded)
    }

    @Test
    fun `u8ToBase64 produces url-safe output with no plus or slash`() {
        // Bytes chosen so the standard alphabet would emit both '+' and '/'.
        // 0xFB 0xFF 0xBF -> standard " +//" area; the point is only that neither survives.
        val encoded = u8ToBase64("251,255,191")
        assertTrue("must not contain '+' but was $encoded", !encoded.contains('+'))
        assertTrue("must not contain '/' but was $encoded", !encoded.contains('/'))
    }

    @Test
    fun `u8ToBase64 replaces plus with dash and slash with underscore`() {
        // Round-trip the transformation explicitly: whatever standard base64 we would have
        // produced, the URL-safe form must map the two special characters.
        val encoded = u8ToBase64("251,239")
        assertTrue(
            "expected '-' or '_' in url-safe output, got $encoded",
            encoded.contains('-') || encoded.contains('_')
        )
    }

    // endregion

    // region stringToU8

    @Test
    fun `stringToU8 renders a javascript uint8array literal`() {
        val result = stringToU8("ab")
        assertEquals("new Uint8Array([97,98])", result)
    }

    @Test
    fun `stringToU8 emits unsigned values for multibyte characters`() {
        // A non-ascii identifier must not produce negative numbers, which would be invalid JS
        // array contents and would poison the token.
        val result = stringToU8("\u00e9")
        assertTrue("must not contain a minus sign, got $result", !result.contains('-'))
    }

    // endregion

    // region descramble

    @Test
    fun `descramble reverses the plus 97 byte shift`() {
        // Scrambling is: encode each byte MINUS 97, then base64. descramble undoes it by ADDING
        // 97. Direction matters and is easy to invert; this test pins it.
        // 'A' = 65; 65 - 97 = -32 -> as a byte, 224. 'B' = 66 -> -31 -> 225.
        val scrambled = byteArrayOf(224.toByte(), 225.toByte()).toByteStringBase64()
        assertEquals("AB", descramble(scrambled))
    }

    @Test
    fun `descramble is the exact inverse of adding 97`() {
        val original = "hello"
        val scrambledBytes =
            original.toByteArray().map { (it - 97).toByte() }.toByteArray()
        val scrambled = scrambledBytes.toByteStringBase64()
        assertEquals(original, descramble(scrambled))
    }

    // endregion

    // region parseChallengeData

    @Test
    fun `parseChallengeData reads the unscrambled form`() {
        // Element 1 is not a string, so the challenge is taken from element 0 directly.
        val raw =
            """[[
                "msg-id",
                null,
                null,
                "interpreter-hash",
                "program-source",
                "globalName",
                null,
                "experiments-blob"
            ]]"""

        val parsed = Json.parseToJsonElement(parseChallengeData(raw)).jsonObject

        assertEquals("msg-id", parsed["messageId"]?.jsonPrimitive?.content)
        assertEquals("interpreter-hash", parsed["interpreterHash"]?.jsonPrimitive?.content)
        assertEquals("program-source", parsed["program"]?.jsonPrimitive?.content)
        assertEquals("globalName", parsed["globalName"]?.jsonPrimitive?.content)
        assertEquals("experiments-blob", parsed["clientExperimentsStateBlob"]?.jsonPrimitive?.content)
    }

    @Test
    fun `parseChallengeData descrambles when element one is a string`() {
        // Build the scrambled payload the way the server does: `descramble` ADDS 97 to each
        // byte, so scrambling is encoding each byte MINUS 97 and then base64.
        val challengeJson =
            """["msg2",null,null,"hash2","prog2","global2",null,"blob2"]"""
        val scrambled =
            challengeJson.toByteArray().map { (it - 97).toByte() }.toByteArray().toByteStringBase64()

        val raw = """[null,"$scrambled"]"""

        val parsed = Json.parseToJsonElement(parseChallengeData(raw)).jsonObject

        assertEquals("msg2", parsed["messageId"]?.jsonPrimitive?.content)
        assertEquals("prog2", parsed["program"]?.jsonPrimitive?.content)
    }

    // endregion

    // region parseIntegrityTokenData

    @Test
    fun `parseIntegrityTokenData extracts a uint8array literal and a lifetime`() {
        // base64 of the single byte 0x41 ('A') is "QQ=="; url-safe form is "QQ..".
        val raw = """["QQ..", 3600]"""

        val (tokenLiteral, lifetimeSeconds) = parseIntegrityTokenData(raw)

        assertEquals("new Uint8Array([65])", tokenLiteral)
        assertEquals(3600L, lifetimeSeconds)
    }

    @Test
    fun `parseIntegrityTokenData accepts the dash and underscore alphabet`() {
        // 0xFF 0xFF in url-safe base64 is "__8=".
        val raw = """["__8=", 7200]"""

        val (tokenLiteral, lifetimeSeconds) = parseIntegrityTokenData(raw)

        assertEquals("new Uint8Array([255,255])", tokenLiteral)
        assertEquals(7200L, lifetimeSeconds)
    }

    // endregion

    // region Guard rails on the documented coupling

    @Test
    fun `descrambling differs from a plain base64 decode`() {
        // Guards the +97 step specifically. If the shift were dropped, `descramble` would reduce
        // to a plain decode and this test would catch it: the scrambled payload is constructed
        // by subtracting 97, so a plain decode yields mojibake rather than the original text.
        val original = "guard"
        val scrambled =
            original.toByteArray()
                .map { (it - 97).toByte() }
                .toByteArray()
                .toByteStringBase64()

        assertNotEquals(original, descrambleOfPlainDecodeForTestOnly(scrambled))
        assertEquals(original, descramble(scrambled))
    }

    // endregion
}

/** Encodes bytes with okio's standard base64, used only to build test fixtures. */
private fun ByteArray.toByteStringBase64(): String = okio.ByteString.of(*this).base64()

/**
 * A deliberately shift-free decode, used to prove [descramble] is not equivalent to a plain
 * base64 decode. Implemented via the shared `base64`/`ByteString` machinery rather than a
 * deprecated decode entry point so the test compiles against current okio.
 */
private fun descrambleOfPlainDecodeForTestOnly(base64: String): String {
    // Re-encode is not what we want here; instead decode through java.util.Base64, which has no
    // deprecated surface, then interpret as text.
    val normalized = base64.replace('-', '+').replace('_', '/').replace('.', '=')
    return String(java.util.Base64.getDecoder().decode(normalized))
}
