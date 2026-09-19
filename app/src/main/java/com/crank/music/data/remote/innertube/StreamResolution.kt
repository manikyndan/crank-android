package com.crank.music.data.remote.innertube

/**
 * The outcome of walking the stream client cascade, including a full record of what was tried.
 *
 * ## Why the failures are carried, not just logged
 *
 * The central lesson from Echo Music's own instrumentation: the exception message alone
 * ("could not find a stream") never says *which* clients were attempted or *why each one was
 * dropped*, and that is the only information that identifies the cause. Correlating a dozen
 * scattered log lines to reconstruct it is exactly the work that should be done once, here.
 *
 * So [attempts] is a first-class part of the result. A playback failure can then be reported as
 * "tried visionOS (NO_FORMATS), Android VR 1.65 (LOGIN_REQUIRED), TVHTML5 (TIMEOUT)" — which is
 * actionable — instead of a generic failure, which is not.
 */
data class StreamResolution(
    /** The playable URL, already n-decoded and carrying `pot=` when a token was available. */
    val url: String,
    /** The client that produced it. Worth surfacing: it changes what to expect from the stream. */
    val client: YouTubeClient,
    /** The format selected, so the caller can report quality and size. */
    val format: InnerTubePlayerResponse.Format,
    /**
     * How long the URL stays valid, from YouTube. Used to refuse to persist a URL that would be
     * dead by the time it is read back.
     */
    val expiresInSeconds: Int?,
    /** Whether a Proof-of-Origin token was attached. A URL without one 403s on the first byte. */
    val hasPoToken: Boolean,
    /** Everything tried before success, in order. Empty when the first client worked. */
    val attempts: List<ClientAttempt>,
)

/** One client's outcome during a cascade. */
data class ClientAttempt(
    val client: YouTubeClient,
    val outcome: Outcome,
    /** Human-readable detail: a status name, a byte count, an error message. */
    val detail: String? = null,
) {
    override fun toString(): String =
        "${client.label}=$outcome" + (detail?.let { "($it)" } ?: "")

    /**
     * Why a client was passed over.
     *
     * These are distinguished rather than collapsed into "failed" because they call for
     * different responses: [NO_FORMATS] means move on, [LOGIN_REQUIRED] means this client needs
     * an account rather than a different request, and [TOKEN_MISSING] means the client would
     * likely work if the BotGuard asset were present.
     */
    enum class Outcome {
        /** Returned usable formats and a valid stream URL. */
        ACCEPTED,

        /** Refused with `UNPLAYABLE`. Almost always the missing-token signature. */
        UNPLAYABLE,

        /** Refused and wants a signed-in session. */
        LOGIN_REQUIRED,

        /** Wants age verification. `WEB_CREATOR` is the only client that satisfies this. */
        AGE_RESTRICTED,

        /** Returned `OK` but offered no audio-bearing format. */
        NO_FORMATS,

        /** Returned a URL, but it failed validation (a 403 on a range request). */
        INVALID_URL,

        /** Returned formats but no expiry, meaning the response cannot be trusted. */
        NO_EXPIRY,

        /** A transport or decode error. */
        ERROR,

        /** Skipped without a request because the client requires login and none is present. */
        SKIPPED_LOGIN_REQUIRED,

        /** Skipped because the client needs a PoToken and none could be minted. */
        TOKEN_MISSING,
    }
}

/** Raised when every client in the cascade was tried and none produced a playable stream. */
class StreamUnavailableException(
    val videoId: String,
    val attempts: List<ClientAttempt>,
    /** Set when the cause is known to be environmental rather than per-track. */
    val hint: String? = null,
) : Exception(buildMessage(videoId, attempts, hint)) {
    private companion object {
        fun buildMessage(
            videoId: String,
            attempts: List<ClientAttempt>,
            hint: String?,
        ): String {
            val summary = attempts.joinToString(" | ")
            return buildString {
                append("No playable stream for $videoId after ${attempts.size} client(s).")
                if (summary.isNotEmpty()) {
                    append(" Tried: ")
                    append(summary)
                }
                if (hint != null) {
                    append(". ")
                    append(hint)
                }
            }
        }
    }
}

/**
 * Formats a cascade result for a single log line.
 *
 * Kept as a function rather than inline interpolation so that both the success and failure paths
 * produce identically-shaped output, which is what makes two runs comparable.
 */
fun formatCascade(videoId: String, attempts: List<ClientAttempt>): String =
    "cascade[$videoId] tried=${attempts.size} :: " +
        attempts.joinToString(" | ")

/**
 * Explains the most likely cause when a whole cascade fails, so the user-facing message points
 * at something specific rather than restating the symptom.
 *
 * The ordering reflects what is actually most probable given the measured behaviour of this
 * chain: a missing BotGuard asset takes out the leading clients and is actionable by the user,
 * whereas a universal refusal means the chain itself has aged out.
 */
fun diagnoseFailure(attempts: List<ClientAttempt>): String? {
    if (attempts.isEmpty()) return "No clients were attempted."

    val everyClientRefused = attempts.all {
        it.outcome == ClientAttempt.Outcome.UNPLAYABLE ||
            it.outcome == ClientAttempt.Outcome.LOGIN_REQUIRED
    }
    val sawTokenMissing = attempts.any { it.outcome == ClientAttempt.Outcome.TOKEN_MISSING }

    return when {
        sawTokenMissing ->
            "The BotGuard asset (po_token.html) is missing, so no Proof-of-Origin token could " +
                "be minted. YouTube refuses the token-gated clients without one. Add the asset " +
                "to app/src/main/assets/ to enable playback."

        everyClientRefused ->
            "Every client refused this track. If this affects all tracks, the client versions " +
                "or identities in YouTubeClients likely need updating."

        else -> null
    }
}
