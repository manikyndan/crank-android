package com.crank.music.ui.viewmodel

import com.crank.music.domain.model.Song
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the "lyrics always refer to the playing track" invariant.
 *
 * The requirement is that metadata, artwork, lyrics and audio are never misaligned. The single
 * mechanism the player relies on is [lyricsAreForCurrentTrack]: a late lyrics response for a track
 * the user has already skipped past must be discarded, or the wrong song's words appear under the
 * right title. That rule is pure and therefore testable without an Android runtime.
 */
class LyricsTrackCorrespondenceTest {

    @Test
    fun `lyrics for the playing track are accepted`() {
        assertTrue(lyricsAreForCurrentTrack("abc", "abc"))
    }

    @Test
    fun `lyrics for a different track are rejected`() {
        assertFalse(lyricsAreForCurrentTrack("abc", "xyz"))
    }

    @Test
    fun `lyrics are rejected when nothing is playing`() {
        assertFalse(lyricsAreForCurrentTrack("abc", null))
    }

    @Test
    fun `lyrics are rejected when current track id is blank`() {
        // A request always carries a real id; a blank current id means no definitive track, so the
        // safe choice is to discard rather than assume identity.
        assertFalse(lyricsAreForCurrentTrack("abc", ""))
    }

    @Test
    fun `replaying the same track keeps its lyrics`() {
        // The same song requested twice (replay) must not be treated as a switch: the second
        // response is still correct for the current track.
        val id = "dQw4w9WgXcQ"
        assertTrue(lyricsAreForCurrentTrack(id, id))
    }

    @Test
    fun `lyrics correspondence does not depend on song content`() {
        // The check is by id only. Two distinct Song objects with the same id are the same track;
        // the comparison must not require object identity or field equality.
        val requested = Song(
            id = "same",
            title = "Different Title",
            artistName = "Different Artist",
            albumId = null,
            durationMs = 100L,
            artworkUrl = "",
            isLocal = false,
        )
        assertTrue(lyricsAreForCurrentTrack(requested.id, "same"))
    }
}
