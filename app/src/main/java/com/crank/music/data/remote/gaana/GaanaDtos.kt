package com.crank.music.data.remote.gaana

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shapes for the GaanaPy server.
 *
 * Every field is optional with a default: the upstream is an unofficial scraper over Gaana's
 * live HTML, so a field disappearing is a matter of when, not if. Defaulting means a missing key
 * degrades one column instead of failing the whole response — with `ignoreUnknownKeys = true`
 * (see `NetworkModule`), the payload can also grow new keys without a crash.
 *
 * Note the types that are **not** what they look like: `duration` is a *string* of seconds, and
 * `album_id` / `artist_ids` are strings too. Parsing them as numbers would throw on the first
 * track. `favorite_count` in particular is an int on songs and a string on albums, which is why
 * it is not modelled at all — nothing here needs it.
 */

/** `images: { urls: { large_artwork, ... } }` */
@Serializable
data class GaanaImageSetDto(
    val urls: GaanaImageUrlsDto? = null,
)

@Serializable
data class GaanaImageUrlsDto(
    @SerialName("large_artwork") val large: String? = null,
    @SerialName("medium_artwork") val medium: String? = null,
    @SerialName("small_artwork") val small: String? = null,
) {
    /** Best available artwork, or empty when the source gave none. Never a stand-in. */
    val best: String
        get() = large?.takeIf { it.isNotBlank() }
            ?: medium?.takeIf { it.isNotBlank() }
            ?: small?.takeIf { it.isNotBlank() }
            ?: ""
}

/** `stream_urls: { urls: { very_high_quality, ... } }` — all HLS `.m3u8` master playlists. */
@Serializable
data class GaanaStreamUrlsDto(
    val urls: GaanaStreamQualitiesDto? = null,
)

@Serializable
data class GaanaStreamQualitiesDto(
    @SerialName("very_high_quality") val veryHigh: String? = null,
    @SerialName("high_quality") val high: String? = null,
    @SerialName("medium_quality") val medium: String? = null,
    @SerialName("low_quality") val low: String? = null,
)

/** A track, as returned by `/songs/search/`, `/songs/info/` and inside `/albums/info/`. */
@Serializable
data class GaanaSongDto(
    val seokey: String = "",
    val title: String = "",
    val artists: String = "",
    @SerialName("artist_seokeys") val artistSeokeys: String = "",
    @SerialName("artist_ids") val artistIds: String = "",
    val album: String = "",
    @SerialName("album_seokey") val albumSeokey: String = "",
    @SerialName("album_id") val albumId: String = "",
    /** Nominal track length in **seconds, as a string**. */
    val duration: String = "",
    val language: String = "",
    val genres: String = "",
    @SerialName("is_explicit") val isExplicit: Boolean = false,
    @SerialName("release_date") val releaseDate: String = "",
    val images: GaanaImageSetDto? = null,
    @SerialName("stream_urls") val streamUrls: GaanaStreamUrlsDto? = null,
)

/** An album, as returned by `/albums/search/`, and by `/albums/info/` (which adds `tracks`). */
@Serializable
data class GaanaAlbumDto(
    val seokey: String = "",
    val title: String = "",
    val artists: String = "",
    @SerialName("album_id") val albumId: String = "",
    val duration: String = "",
    @SerialName("track_count") val trackCount: String = "",
    @SerialName("release_date") val releaseDate: String = "",
    @SerialName("is_explicit") val isExplicit: Boolean = false,
    val language: String = "",
    val images: GaanaImageSetDto? = null,
    /** Present only on `/albums/info/`. */
    val tracks: List<GaanaSongDto>? = null,
)

/** One playlist card from `/charts`. Carries no stream URLs — it is a listing, not a tracklist. */
@Serializable
data class GaanaPlaylistDto(
    val seokey: String = "",
    val title: String = "",
    val language: String = "",
    @SerialName("is_explicit") val isExplicit: Boolean = false,
    val images: GaanaImageSetDto? = null,
)

/**
 * `/newreleases` — the one discovery endpoint that returns albums as well as tracks, which is what
 * lets the home feed be filled with real album cards instead of invented ones.
 */
@Serializable
data class GaanaNewReleasesDto(
    val tracks: List<GaanaSongDto> = emptyList(),
    val albums: List<GaanaAlbumDto> = emptyList(),
)

/** `GET /health` -> `{"status":"ok"}`. Modelled so the answer is checked, not string-matched ad hoc. */
@Serializable
data class GaanaHealthDto(
    val status: String = "",
)
