package com.crank.music.domain.model

data class Song(
    val id: String,
    val title: String,
    val artistName: String,
    val albumId: String?,
    // Display name of the album this track belongs to, when the source provided one. Null when
    // unknown (e.g. search results). Carried through to the Room `songs` table so the Liked Songs
    // subtitle can show "Artist • Album" without a per-row network lookup.
    val albumName: String? = null,
    // True when the source flagged the track as explicit. Parsed from InnerTube's
    // MUSIC_EXPLICIT_BADGE / iTunes' trackExplicitness; never invented.
    val isExplicit: Boolean = false,
    val durationMs: Long,
    val artworkUrl: String,
    val isLocal: Boolean,
    val streamUrl: String = "",
) {
    /**
     * True when this song is known *about* but has no playable audio source.
     *
     * Recognised tracks (via song recognition) are metadata-only: we know the
     * title and artist, but nothing maps them to a stream yet. They are marked
     * with [UNRESOLVED_STREAM_PREFIX] so the player can fail fast with something
     * actionable rather than attempting to resolve a marker as if it were a
     * video ID.
     */
    val isPlayable: Boolean
        get() = !streamUrl.startsWith(UNRESOLVED_STREAM_PREFIX)

    companion object {
        /**
         * Marks a [streamUrl] as "not a real source yet".
         *
         * Defined here, on the domain model, rather than in either the recognition
         * feature or the player: both ends need to agree on it, and duplicating the
         * literal is exactly how the two would silently drift apart.
         */
        const val UNRESOLVED_STREAM_PREFIX = "audd:unresolved:"
    }
}
