package com.crank.music.data.remote.potoken

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

/**
 * Encoding and parsing helpers for the BotGuard Proof-of-Origin token flow.
 *
 * Adapted from the Echo Music project (GPL-3.0).
 *
 * Everything here is a pure function of its input. That is deliberate: the rest of the PoToken
 * pipeline needs a WebView and cannot be unit-tested, so the parts that *can* be verified in
 * isolation were kept free of Android dependencies and are covered by `JavaScriptUtilTest`.
 */

/**
 * Parses the raw challenge response from the BotGuard `/Create` endpoint into a JavaScript
 * object literal that can be embedded directly in a `evaluateJavascript` call.
 *
 * ## The scrambled form
 *
 * The endpoint sometimes returns the challenge *scrambled* as base64 in element 1 rather than
 * plain in element 0. When it does, the payload must be descrambled before use — see
 * [descramble]. Getting this branch wrong yields a challenge whose fields are all garbage,
 * which surfaces later as an opaque token-generation failure rather than a parse error, so the
 * `isString` check matters.
 */
fun parseChallengeData(rawChallengeData: String): String {
    val scrambled = Json.parseToJsonElement(rawChallengeData).jsonArray

    val challengeData =
        if (scrambled.size > 1 && scrambled[1].jsonPrimitive.isString) {
            val descrambled = descramble(scrambled[1].jsonPrimitive.content)
            Json.parseToJsonElement(descrambled).jsonArray
        } else {
            scrambled[0].jsonArray
        }

    val messageId = challengeData[0].jsonPrimitive.content
    val interpreterHash = challengeData[3].jsonPrimitive.content
    val program = challengeData[4].jsonPrimitive.content
    val globalName = challengeData[5].jsonPrimitive.content
    val clientExperimentsStateBlob = challengeData[7].jsonPrimitive.content

    // Indices 1 and 2 hold the interpreter source, either inline or as a trusted resource URL.
    // Both may be null; the JS side handles a null by fetching the interpreter itself.
    val safeScriptValue =
        challengeData[1].takeIf { it !is JsonNull }?.jsonArray?.find { it.jsonPrimitive.isString }
    val trustedResourceUrlValue =
        challengeData[2].takeIf { it !is JsonNull }?.jsonArray?.find { it.jsonPrimitive.isString }

    return Json.encodeToString(
        JsonObject.serializer(),
        JsonObject(
            mapOf(
                "messageId" to JsonPrimitive(messageId),
                "interpreterJavascript" to
                    JsonObject(
                        mapOf(
                            "privateDoNotAccessOrElseSafeScriptWrappedValue" to
                                (safeScriptValue ?: JsonNull),
                            "privateDoNotAccessOrElseTrustedResourceUrlWrappedValue" to
                                (trustedResourceUrlValue ?: JsonNull),
                        )
                    ),
                "interpreterHash" to JsonPrimitive(interpreterHash),
                "program" to JsonPrimitive(program),
                "globalName" to JsonPrimitive(globalName),
                "clientExperimentsStateBlob" to JsonPrimitive(clientExperimentsStateBlob),
            )
        )
    )
}

/**
 * Parses the `/GenerateIT` response into the integrity token as a JavaScript `Uint8Array`
 * literal, plus that token's lifetime in seconds.
 *
 * The result is a pair rather than a data class because the two halves are consumed
 * immediately and separately — the array goes into JS, the duration drives expiry arithmetic.
 */
fun parseIntegrityTokenData(rawIntegrityTokenData: String): Pair<String, Long> {
    val integrityTokenData = Json.parseToJsonElement(rawIntegrityTokenData).jsonArray
    return base64ToU8(integrityTokenData[0].jsonPrimitive.content) to
        integrityTokenData[1].jsonPrimitive.long
}

/**
 * Converts an identifier string to a JavaScript `Uint8Array` literal.
 *
 * The identifier is either a session id (producing a session-bound token) or a video id
 * (producing a video-bound one). **Which one is bound changes whether YouTube accepts the
 * token**, so callers must be deliberate about what they pass.
 */
fun stringToU8(identifier: String): String = newUint8Array(identifier.toByteArray())

/**
 * Converts a poToken from the JS `Uint8Array.toString()` form — comma-separated decimal byte
 * values such as `"97,98,99"` — into the base64 form YouTube expects.
 *
 * ## The two non-obvious parts
 *
 * 1. Each element is parsed as an **unsigned** byte (`toUByte()`) before being narrowed back to
 *    a signed `Byte`. Values above 127 otherwise fail or wrap incorrectly, and the failure is
 *    silent — you get a well-formed token that YouTube rejects.
 * 2. The output is **URL-safe base64**: `+` becomes `-` and `/` becomes `_`. Dropping that step
 *    produces a token that corrupts when appended to a stream URL as a query parameter.
 */
fun u8ToBase64(poToken: String): String {
    return poToken
        .split(",")
        .map { it.toUByte().toByte() }
        .toByteArray()
        .toByteString()
        .base64()
        .replace("+", "-")
        .replace("/", "_")
}

/**
 * Reverses the BotGuard challenge scrambling: base64-decode, then add 97 to every byte.
 *
 * The `+97` is not a guess — it is the exact inverse of the server's obfuscation. There is no
 * way to derive it from the payload, so it is preserved verbatim from upstream.
 */
fun descramble(scrambledChallenge: String): String {
    return base64ToByteString(scrambledChallenge)
        .map { (it + 97).toByte() }
        .toByteArray()
        .decodeToString()
}

/** Decodes YouTube's base64 variant and returns it as a JavaScript `Uint8Array` literal. */
private fun base64ToU8(base64: String): String = newUint8Array(base64ToByteString(base64))

/** Renders a byte array as a JavaScript `new Uint8Array([...])` literal. */
private fun newUint8Array(contents: ByteArray): String =
    "new Uint8Array([" +
        contents.joinToString(separator = ",") { it.toUByte().toString() } +
        "])"

/**
 * Decodes YouTube's base64 variant, which differs from the standard alphabet in two ways:
 * `-`/`_` replace `+`/`/`, and `.` is used in place of `=` for padding.
 */
private fun base64ToByteString(base64: String): ByteArray {
    val base64Mod = base64.replace('-', '+').replace('_', '/').replace('.', '=')

    return (base64Mod.decodeBase64() ?: throw PoTokenException("Cannot base64 decode")).toByteArray()
}
