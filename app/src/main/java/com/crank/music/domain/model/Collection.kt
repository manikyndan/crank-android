package com.crank.music.domain.model

/**
 * The single source of truth for "which collection is the user opening".
 *
 * ### Why this type exists
 *
 * Before this, four different parts of the app each invented their own id vocabulary and none of
 * them agreed:
 *
 * | Emitter | ids sent |
 * |---|---|
 * | `HomeViewModel` made-for-you + quick actions | `dm1`, `dw`, `rr`, `tc`, `or`, `rrw` |
 * | `HomeScreen` "See All" rows | `recently_played`, `recommended`, `trending` |
 * | `ExploreViewModel` featured playlists | `fp1` … `fp6` |
 * | `PlaylistDetailViewModel` (`when (playlistId)`) | `daily_mix_1`, `made_for_you_1`, … |
 *
 * Not one id matched. Every tap therefore fell through to the ViewModel's
 * `else -> playlistTitle.lowercase()` branch, and because `playlistTitle` was declared in the
 * route's `SavedStateHandle` but never actually part of the route, it always resolved to the
 * literal `"Playlist"` and ran `search("playlist")`.
 *
 * The observable result was that **every card in the app opened the same meaningless search
 * results** under the title "Playlist" — confirmed on device: tapping "Discover Weekly" produced a
 * screen titled "Playlist" listing "Deep Sleep Music", "Brahms Lullaby for Babies" and
 * "Sex Playlist", none of which have anything to do with the user's request.
 *
 * ### The fix
 *
 * One enum. [slug] is the wire value and the only thing that crosses a navigation boundary;
 * [title] and [subtitle] travel with it so the destination renders the right heading without
 * having to recognise the id. Adding a collection means adding one enum entry and one [query] —
 * there is no second place to update and no way to emit an id that the destination cannot resolve.
 */
enum class Collection(
    /** Stable wire value. Used in the nav route, so it must not change once shipped. */
    val slug: String,
    /** Heading shown on the destination screen. */
    val title: String,
    val subtitle: String,
    /** Search query backing this collection. See the note on [CollectionRepository]. */
    val query: String,
) {
    // --- Made for you -------------------------------------------------------------------------
    DISCOVER_WEEKLY(
        slug = "discover_weekly",
        title = "Discover Weekly",
        subtitle = "Fresh picks for you",
        query = "new music mix",
    ),
    RELEASE_RADAR(
        slug = "release_radar",
        title = "Release Radar",
        subtitle = "New releases you'll love",
        query = "new releases",
    ),
    DAILY_MIX_1(
        slug = "daily_mix_1",
        title = "Daily Mix 1",
        subtitle = "Pop & Charts",
        query = "top hits",
    ),
    DAILY_MIX_2(
        slug = "daily_mix_2",
        title = "Daily Mix 2",
        subtitle = "Chill & Easy",
        query = "chill hits",
    ),
    DAILY_MIX_3(
        slug = "daily_mix_3",
        title = "Daily Mix 3",
        subtitle = "Energy & Movement",
        query = "workout music",
    ),
    TIME_CAPSULE(
        slug = "time_capsule",
        title = "Time Capsule",
        subtitle = "Your nostalgic favorites",
        query = "classic hits",
    ),
    ON_REPEAT(
        slug = "on_repeat",
        title = "On Repeat",
        subtitle = "Songs you can't stop playing",
        query = "popular songs",
    ),
    REPEAT_REWIND(
        slug = "repeat_rewind",
        title = "Repeat Rewind",
        subtitle = "Your past favorites",
        query = "throwback hits",
    ),

    // --- Home sections ------------------------------------------------------------------------
    RECENTLY_PLAYED(
        slug = "recently_played",
        title = "Recently Played",
        subtitle = "Pick up where you left off",
        // Resolved from local history, not search. See CollectionRepository.
        query = "",
    ),
    RECOMMENDED(
        slug = "recommended",
        title = "Recommended For You",
        subtitle = "Based on your taste",
        query = "recommended music",
    ),
    TRENDING(
        slug = "trending",
        title = "Trending Now",
        subtitle = "What everyone is playing",
        query = "top hits",
    ),

    // --- Explore featured ---------------------------------------------------------------------
    TOP_HITS(
        slug = "top_hits",
        title = "Today's Top Hits",
        subtitle = "The biggest songs right now",
        query = "top hits 2024",
    ),
    RAP_CAVIAR(
        slug = "rap_caviar",
        title = "RapCaviar",
        subtitle = "New music from top artists",
        query = "hip hop hits",
    ),
    ALL_OUT_2020S(
        slug = "all_out_2020s",
        title = "All Out 2020s",
        subtitle = "The biggest songs of the 2020s",
        query = "2020s hits",
    ),
    ROCK_CLASSICS(
        slug = "rock_classics",
        title = "Rock Classics",
        subtitle = "Rock legends & iconic songs",
        query = "rock classics",
    ),
    CHILL_HITS(
        slug = "chill_hits",
        title = "Chill Hits",
        subtitle = "Kick back to the best chill hits",
        query = "chill hits",
    ),
    VIVA_LATINO(
        slug = "viva_latino",
        title = "Viva Latino",
        subtitle = "The biggest Latin hits",
        query = "latin hits",
    );

    companion object {
        /** Looks up a collection by its [slug], or `null` when the slug is unknown. */
        fun fromSlug(slug: String?): Collection? =
            slug?.let { wanted -> entries.firstOrNull { it.slug == wanted } }

        /**
         * Genre collections are addressed as `genre:<name>` rather than getting an enum entry each,
         * because the genre list is data, not a fixed set.
         */
        const val GENRE_PREFIX = "genre:"

        fun genre(name: String): String = GENRE_PREFIX + name

        fun genreOf(slug: String?): String? =
            slug?.takeIf { it.startsWith(GENRE_PREFIX) }?.removePrefix(GENRE_PREFIX)
    }
}
