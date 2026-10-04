package com.crank.music.data.remote

import com.crank.music.data.remote.gaana.GaanaRemoteDataSource
import com.crank.music.domain.model.Album
import com.crank.music.domain.model.AlbumWithKind
import com.crank.music.domain.model.ContentSource
import com.crank.music.domain.model.Song
import com.crank.music.core.awaitOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Normalises a value for comparison: lowercased and stripped to letters and digits, so
 * "Sanam Re" / "sanam re!" / "SANAM RE" all collapse together.
 */
internal fun normalizeForMatch(value: String): String =
    value.lowercase().filter(Char::isLetterOrDigit)

/** Every credited artist in a credit list, normalised: "Mithoon, Arijit Singh" -> both names. */
private fun artistTokens(credits: String): Set<String> =
    credits.split(',')
        .map(::normalizeForMatch)
        .filterTo(HashSet()) { it.isNotBlank() }

/**
 * True when two records are the same piece of content and should be shown once.
 *
 * ## Why this is a predicate and not a lookup key
 *
 * The two sources credit artists differently. Gaana lists every contributor in whatever order its
 * page uses ("Mithoon, Arijit Singh") while YouTube names the primary artist ("Arijit Singh"), and
 * either one may lead. No single derived string can be equal for both — a key-based de-duplication
 * therefore *cannot* recognise the same track across sources, which a test caught: taking the
 * leading artist of each side produced `"sanamre|mithoon"` against `"sanamre|arijitsingh"` and the
 * duplicate survived.
 *
 * Matching instead on **a shared artist token** is order-independent and immune to a differing
 * number of credits, so both spellings above resolve to the same pair.
 *
 * Failing to match is the safe direction: the cost is one duplicated row, whereas a false match
 * would delete a real, different track from the list.
 */
internal fun matchesSameTrack(a: Song, b: Song): Boolean {
    if (normalizeForMatch(a.title) != normalizeForMatch(b.title)) return false
    val mine = artistTokens(a.artistName)
    val theirs = artistTokens(b.artistName)
    return mine.any(theirs::contains)
}

/** As [matchesSameTrack], for albums. */
internal fun matchesSameAlbum(a: Album, b: Album): Boolean {
    if (normalizeForMatch(a.title) != normalizeForMatch(b.title)) return false
    val mine = artistTokens(a.artistName)
    val theirs = artistTokens(b.artistName)
    return mine.any(theirs::contains)
}

/**
 * [primary] in its own order and completeness, followed by the [secondary] entries that are not
 * already represented by anything in it.
 *
 * Primary is never reordered and never filtered, so adding a second catalogue cannot change what
 * an existing user already sees — it can only append to it. Duplicates *within* [secondary] are
 * collapsed by the same test, because each accepted candidate joins the kept list as it goes.
 *
 * Deliberately a linear comparison rather than a hash index: the lists are capped at 25 entries
 * (see `GaanaRemoteDataSource.SEARCH_LIMIT`), so at most a few hundred comparisons per screen —
 * far cheaper than the complexity of an index that cannot express an artist-overlap match anyway.
 */
internal fun <T> appendDistinct(
    primary: List<T>,
    secondary: List<T>,
    matches: (T, T) -> Boolean,
): List<T> {
    val kept = primary.toMutableList()
    for (candidate in secondary) {
        if (kept.none { matches(it, candidate) }) kept += candidate
    }
    return kept
}

/**
 * Presents YouTube and Gaana as one [RemoteDataSource].
 *
 * ## Policy: add, never replace
 *
 * YouTube answers first and its results are kept in their original order and completeness. Gaana
 * results are appended only where YouTube has nothing equivalent. Two consequences, both
 * deliberate:
 *
 *  - Existing behaviour is preserved exactly. A user who never configures a Gaana server sees a
 *    byte-for-byte identical result list.
 *  - A Gaana outage cannot make results worse. Its methods already return empty on any failure
 *    (see [GaanaRemoteDataSource] and `GaanaApi`), so the YouTube half stands on its own.
 *
 * The alternative — preferring Gaana and falling back to YouTube — was rejected because it would
 * silently *remove* results for every non-Indian query the moment a server was configured.
 *
 * ## Why the two calls run concurrently
 *
 * Sequentially, every search would pay both latencies. They are genuinely independent, and
 * `awaitOrNull` is the idiom `core/CoroutineDiscipline.kt` provides for exactly this fan-out: a
 * failure in one half returns `null` instead of losing the other, while cancellation still
 * propagates.
 */
@Singleton
class RemoteSourceRouter @Inject constructor(
    private val youtube: YouTubeRemoteDataSource,
    private val gaana: GaanaRemoteDataSource,
) : RemoteDataSource {

    override suspend fun searchMusic(query: String): List<Song> = coroutineScope {
        val fromYouTube = async { youtube.searchMusic(query) }
        val fromGaana = async { gaana.searchMusic(query) }
        appendDistinct(
            primary = fromYouTube.awaitOrNull().orEmpty(),
            secondary = fromGaana.awaitOrNull().orEmpty(),
            matches = ::matchesSameTrack,
        )
    }

    override suspend fun searchAlbums(query: String): List<Album> = coroutineScope {
        val fromYouTube = async { youtube.searchAlbums(query) }
        val fromGaana = async { gaana.searchAlbums(query) }
        appendDistinct(
            primary = fromYouTube.awaitOrNull().orEmpty(),
            secondary = fromGaana.awaitOrNull().orEmpty(),
            matches = ::matchesSameAlbum,
        )
    }

    /**
     * Merged on the album identity, not on `kind`. The two sources disagree about kinds — Gaana
     * declares none at all — so merging on it would keep the same album twice.
     */
    override suspend fun searchAlbumsWithKind(query: String): List<AlbumWithKind> = coroutineScope {
        val fromYouTube = async { youtube.searchAlbumsWithKind(query) }
        val fromGaana = async { gaana.searchAlbumsWithKind(query) }
        appendDistinct(
            primary = fromYouTube.awaitOrNull().orEmpty(),
            secondary = fromGaana.awaitOrNull().orEmpty(),
            // Merged on the album itself, not on `kind`: the sources disagree about kinds (Gaana
            // declares none), so keying on it would keep the same album twice.
            matches = { a, b -> matchesSameAlbum(a.album, b.album) },
        )
    }

    override suspend fun getHomeData(): List<Album> = coroutineScope {
        val fromYouTube = async { youtube.getHomeData() }
        val fromGaana = async { gaana.getHomeData() }
        appendDistinct(
            primary = fromYouTube.awaitOrNull().orEmpty(),
            secondary = fromGaana.awaitOrNull().orEmpty(),
            matches = ::matchesSameAlbum,
        )
    }

    companion object {
        /** Exposed for tests: asserts an id belongs to the Gaana source without the network. */
        internal fun isGaanaTrack(id: String): Boolean =
            ContentSource.of(id) == ContentSource.GAANA
    }
}
