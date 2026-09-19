package com.crank.music.data.remote.potoken

/**
 * Base type for PoToken pipeline failures.
 *
 * Kept distinct from generic `Exception` so the playback layer can tell "the token service
 * politely declined" apart from "something is broken in our code", and decide accordingly:
 * the former should fall through to a non-token client, the latter should be surfaced.
 */
open class PoTokenException(message: String) : Exception(message)

/**
 * The system WebView is present but unusable — typically an OEM build whose JavaScript engine
 * throws on the BotGuard interpreter.
 *
 * Treated as **permanent** for the lifetime of the process. Retrying costs several seconds per
 * attempt and cannot succeed, so the generator latches this and skips WebView work entirely
 * from then on, letting playback proceed straight to non-token clients.
 */
class BadWebViewException(message: String) : Exception(message)
