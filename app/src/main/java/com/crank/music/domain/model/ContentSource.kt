package com.crank.music.domain.model

/**
 * Which backend a [Song.id] belongs to.
 *
 * ## Why an id prefix rather than a new column
 *
 * `songs.id` is Room's `@PrimaryKey` and holds a YouTube video id as a bare string. Gaana
 * identifies a track by `seokey` — `sanam-re`, `tyler-herro` — which is *also* a bare string and
 * can therefore collide with a video id in the same column. Mixing the two unprefixed breaks
 * two things at once:
 *
 *  1. `SongDao.insertSong` is `@Insert(onConflict = REPLACE)`. A Gaana row sharing a key with a
 *     YouTube row would silently replace it, taking `isLiked` and `dateAdded` with it — the exact
 *     data-loss shape fixed in f8dae61 and a5b7fd8.
 *  2. Nothing downstream could tell which resolver to use for a given id.
 *
 * A prefix solves both and needs **no migration**, because YouTube ids keep the bare, unprefixed
 * form that every existing row already has. Prefixing YouTube instead would orphan every stored
 * song, like, download and queue entry — so the rule is: YouTube is the legacy unprefixed form,
 * and every new source is namespaced.
 */
enum class ContentSource(val idPrefix: String) {
    /** The original, unprefixed source. Legacy rows depend on its ids staying bare. */
    YOUTUBE(""),

    /** Gaana, addressed by `seokey`. */
    GAANA("gaana:"),
    ;

    companion object {
        private const val ALBUM_INFIX = "album:"

        /**
         * The source a [id] belongs to.
         *
         * Anything unrecognised is treated as [YOUTUBE] rather than rejected, because the ids
         * already in users' databases predate this enum and are all bare YouTube ids.
         */
        fun of(id: String): ContentSource =
            entries.firstOrNull { it.idPrefix.isNotEmpty() && id.startsWith(it.idPrefix) } ?: YOUTUBE

        /** A namespaced track id for a Gaana [seokey]. */
        fun gaanaTrackId(seokey: String): String = GAANA.idPrefix + seokey

        /** A namespaced album id for a Gaana album [seokey]. */
        fun gaanaAlbumId(seokey: String): String =
            GAANA.idPrefix + ALBUM_INFIX + seokey

        /**
         * The bare Gaana track `seokey` behind [id], or `null` when [id] is not a Gaana *track* id.
         *
         * Album ids are rejected deliberately: they share the prefix but address a collection, and
         * handing one to the track endpoint would 404.
         */
        fun gaanaTrackSeokeyOrNull(id: String): String? =
            id.takeIf { it.startsWith(GAANA.idPrefix) }
                ?.removePrefix(GAANA.idPrefix)
                ?.takeIf { !it.startsWith(ALBUM_INFIX) && it.isNotBlank() }

        /** The bare Gaana album `seokey` behind [id], or `null` when [id] is not a Gaana album id. */
        fun gaanaAlbumSeokeyOrNull(id: String): String? =
            id.takeIf { it.startsWith(GAANA.idPrefix + ALBUM_INFIX) }
                ?.removePrefix(GAANA.idPrefix + ALBUM_INFIX)
                ?.takeIf { it.isNotBlank() }
    }
}
