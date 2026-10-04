package com.crank.music.data.remote.gaana

import com.crank.music.domain.model.Album
import com.crank.music.domain.model.ContentSource
import com.crank.music.domain.model.Song

/**
 * Gaana wire types → domain models.
 *
 * Pure functions, so every rule below is unit-testable without a network. Two of them are
 * deliberately strict: a track or album with no usable identity or title is dropped rather than
 * patched up, because a row with an invented title is worse than a missing row — it looks real.
 */

/**
 * A playable track, or `null` when the record cannot be represented honestly.
 *
 * `seokey` is required: it is the only durable handle Gaana gives us, and without it the track
 * could not be re-resolved when its four-hour URL expires — it would look playable and then fail.
 */
internal fun GaanaSongDto.toSongOrNull(): Song? {
    val key = seokey.takeIf { it.isNotBlank() } ?: return null
    val name = title.takeIf { it.isNotBlank() } ?: return null

    val id = ContentSource.gaanaTrackId(key)
    return Song(
        id = id,
        title = name,
        artistName = artists.takeIf { it.isNotBlank() } ?: "Unknown Artist",
        albumId = albumSeokey.takeIf { it.isNotBlank() }?.let { ContentSource.gaanaAlbumId(it) },
        albumName = album.takeIf { it.isNotBlank() },
        isExplicit = isExplicit,
        // `duration` arrives as a *string* of seconds. 0 means "unknown" honestly and the UI
        // decides how to render that — an invented length is the bug fixed in 4c819be.
        durationMs = duration.toLongOrNull()?.takeIf { it > 0 }?.times(1_000L) ?: 0L,
        artworkUrl = images?.urls?.best ?: "",
        isLocal = false,
        // Deliberately the id, not a URL: the same convention the YouTube path uses when it stores
        // a bare video id here. A non-URL means "resolve this", and the minted URL — which lives
        // only four hours — is never persisted into this column.
        streamUrl = id,
    )
}

/** A navigable album, or `null` when it has no identity or title. */
internal fun GaanaAlbumDto.toAlbumOrNull(): Album? {
    val key = seokey.takeIf { it.isNotBlank() } ?: return null
    val name = title.takeIf { it.isNotBlank() } ?: return null

    return Album(
        id = ContentSource.gaanaAlbumId(key),
        title = name,
        artistName = artists.takeIf { it.isNotBlank() } ?: "Unknown Artist",
        // "2016-01-04" -> "2016". Empty when absent or malformed; never a stand-in year.
        releaseYear = releaseDate
            .take(4)
            .takeIf { it.length == 4 && it.all(Char::isDigit) }
            ?: "",
        artworkUrl = images?.urls?.best ?: "",
        trackCount = trackCount.toIntOrNull()?.coerceAtLeast(0) ?: 0,
    )
}
