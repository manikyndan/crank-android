package com.crank.music.data.remote.gaana

import com.crank.music.data.remote.RemoteDataSource
import com.crank.music.data.remote.StreamData
import com.crank.music.data.remote.StreamResolver
import com.crank.music.domain.model.Album
import com.crank.music.domain.model.AlbumWithKind
import com.crank.music.domain.model.Song
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gaana as a second catalogue behind the existing [RemoteDataSource] seam.
 *
 * ## What this source is, and is not
 *
 * It exists to add Gaana's catalogue — which is strongest on Indian music — alongside YouTube
 * rather than instead of it. YouTube remains the default: it is global, and it is the path the
 * lyrics, downloads and PoToken machinery is built around. Nothing here replaces it.
 *
 * ## Everything is inert until configured
 *
 * With no [GaanaApi.baseUrl] every method returns empty. That is the honest representation of
 * "this source is switched off": no error, no invented result, and no effect on the YouTube path.
 *
 * ## Why there is no lyric lookup here
 *
 * `MusicRepository.getLyricsByVideoId` asks YouTube Music by video id, and a Gaana `seokey` is not
 * one. Gaana tracks therefore get lyrics only from LRCLIB, which matches on title and artist. That
 * is a real reduction in coverage for this source, and it is better stated than papered over by
 * passing a seokey to an endpoint that would return nothing.
 */
@Singleton
class GaanaRemoteDataSource @Inject constructor(
    private val api: GaanaApi,
) : RemoteDataSource {

    /** True when a usable server is configured. The router uses this to pick a source. */
    suspend fun isConfigured(): Boolean = api.baseUrl() != null

    override suspend fun searchMusic(query: String): List<Song> {
        if (query.isBlank()) return emptyList()
        val base = api.baseUrl() ?: return emptyList()
        return api.searchSongs(base, query, SEARCH_LIMIT).mapNotNull { it.toSongOrNull() }
    }

    override suspend fun searchAlbums(query: String): List<Album> {
        if (query.isBlank()) return emptyList()
        val base = api.baseUrl() ?: return emptyList()
        return api.searchAlbums(base, query, SEARCH_LIMIT).mapNotNull { it.toAlbumOrNull() }
    }

    /**
     * Gaana's search cards declare no release kind, so the bucket is genuinely unknown. Reporting
     * `""` is the honest answer; guessing "Album" would file every single under full albums.
     */
    override suspend fun searchAlbumsWithKind(query: String): List<AlbumWithKind> =
        searchAlbums(query).map { AlbumWithKind(it, "") }

    /**
     * New releases, which is the only Gaana discovery endpoint that returns albums.
     *
     * `/trending` and `/charts` return tracks and playlists respectively, so neither can fill an
     * album feed without inventing album records — which is why the home feed asks for releases.
     */
    override suspend fun getHomeData(): List<Album> {
        val base = api.baseUrl() ?: return emptyList()
        return api.newReleases(base, DEFAULT_LANGUAGE, HOME_LIMIT)
            ?.albums
            .orEmpty()
            .mapNotNull { it.toAlbumOrNull() }
    }

    /**
     * The real tracklist of a Gaana album, addressed by its bare `seokey`.
     *
     * Each track carries its own freshly minted `stream_urls`, so this returns playable songs —
     * there is no second lookup per row.
     */
    suspend fun browseAlbum(albumSeokey: String): List<Song> {
        if (albumSeokey.isBlank()) return emptyList()
        val base = api.baseUrl() ?: return emptyList()
        return api.albumInfo(base, albumSeokey)
            ?.tracks
            .orEmpty()
            .mapNotNull { it.toSongOrNull() }
    }

    /**
     * Resolves a track to a playable HLS URL, or `null` when it cannot be played.
     *
     * The URL is minted on demand rather than carried from search, because Gaana's tokens last
     * exactly four hours — a URL captured at search time would be dead by the next day, and
     * `StreamResolver.isDirectlyPlayable` would now correctly refuse it, which would surface as an
     * unexplained silent skip rather than a re-resolve.
     *
     * `null` is a real outcome: a track Gaana will not stream has no `stream_urls` at all, and the
     * caller must report it as a failure rather than fall back to another track.
     */
    suspend fun resolveStream(seokey: String, maxBitrateKbps: Int?): StreamData? {
        if (seokey.isBlank()) return null
        val base = api.baseUrl() ?: return null

        val info = api.songInfo(base, seokey) ?: return null
        val url = GaanaStreamSelection.select(info.streamUrls?.urls, maxBitrateKbps) ?: return null

        // Gaana's CDN carries its authorization in the signed URL itself, so — like YouTube's —
        // it needs no extra headers. Sent as an empty map rather than omitted so this is explicit.
        return StreamData(
            url = url,
            headers = emptyMap(),
            expiresInSeconds = StreamResolver.expirySecondsOf(url)
                ?.let { exp -> (exp - System.currentTimeMillis() / 1000L).toInt() }
                ?.takeIf { it > 0 },
            sourceClient = SOURCE_CLIENT,
        )
    }

    companion object {
        private const val SOURCE_CLIENT = "GAANA"

        /**
         * GaanaPy caps `limit` at 100. 25 is a deliberate middle: enough to fill a search screen,
         * well inside the cap, and one upstream page for the scraper rather than several.
         */
        private const val SEARCH_LIMIT = 25
        private const val HOME_LIMIT = 20

        /**
         * `/trending` and `/newreleases` require a language and reject a blank one. Hindi is the
         * default because it is Gaana's largest catalogue; it is not a claim about the user.
         */
        private const val DEFAULT_LANGUAGE = "Hindi"
    }
}
