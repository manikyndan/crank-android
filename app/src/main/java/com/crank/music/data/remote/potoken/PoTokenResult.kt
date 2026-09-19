package com.crank.music.data.remote.potoken

/**
 * A pair of Proof-of-Origin tokens, distinguished by what each one is **bound to**.
 *
 * ## Why two fields rather than one
 *
 * YouTube binds a PoToken to a specific context, and the two contexts are different:
 *
 * - [streamingDataPoToken] is appended to the googlevideo stream URL as the `pot=` query
 *   parameter. It must be **session-bound** — minted from the session id, which is the
 *   account's data-sync id when signed in and the visitor data id otherwise.
 * - [playerRequestPoToken] is sent in the `/player` request body as
 *   `serviceIntegrityDimensions.poToken`. It must be **video-id-bound** in general.
 *
 * Getting this backwards is the failure mode that is hardest to diagnose, because both tokens
 * are well-formed and non-empty. The stream URL simply 403s on the first byte while the
 * `/player` call keeps working, or vice versa.
 *
 * ## The WEB_REMIX exception, which is why both fields currently hold the same value
 *
 * yt-dlp's `get_webpo_content_binding` treats `WEB_REMIX` as a special case: for that client,
 * the *player* context is also session-bound. Since [YouTubeClients.MAIN_CLIENT] is
 * `WEB_REMIX`, both fields correctly receive the session-bound token.
 *
 * **This is a coupling, not a coincidence.** If the main client is ever changed away from
 * `WEB_REMIX`, [playerRequestPoToken] must be switched to a video-id-bound token, or
 * age-restricted playback will break in a way that looks like a server-side refusal. The
 * `tokenBindingNote` documents the invariant at the point of construction.
 *
 * A distinct video-bound field was deliberately not added: there is no client in the current
 * chain that needs one, and an unused field invites exactly the confusion this comment exists
 * to prevent.
 */
data class PoTokenResult(
    /** Session-bound. Goes into the `/player` body for a `WEB_REMIX` main client. */
    val playerRequestPoToken: String,
    /** Session-bound. Appended to the stream URL as `pot=`. */
    val streamingDataPoToken: String,
)
