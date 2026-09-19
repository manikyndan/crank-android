package com.crank.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the playlist-resolution contract.
 *
 * The defect these tests guard against was not a crash — it was a silent vocabulary mismatch.
 * `HomeViewModel` emitted `"dm1"`, `HomeScreen` emitted `"recently_played"`, `ExploreViewModel`
 * emitted `"fp1"`, and `PlaylistDetailViewModel` only recognised `"daily_mix_1"`. Nothing threw;
 * every lookup simply fell through to a default and the user got unrelated search results under
 * the title "Playlist". A test asserting "an id resolves to the right collection" is therefore the
 * right level of defence, because the failure mode is a wrong answer, not an exception.
 */
class CollectionTest {

    @Test
    fun `every slug is unique`() {
        val slugs = Collection.entries.map { it.slug }
        assertEquals(
            "Duplicate slugs would make fromSlug return the wrong collection",
            slugs.size,
            slugs.toSet().size,
        )
    }

    @Test
    fun `every slug round-trips through fromSlug`() {
        Collection.entries.forEach { collection ->
            assertEquals(
                "Slug '${collection.slug}' did not resolve back to $collection",
                collection,
                Collection.fromSlug(collection.slug),
            )
        }
    }

    @Test
    fun `every collection has a non-blank title`() {
        Collection.entries.forEach { collection ->
            assertTrue(
                "$collection has a blank title, so its screen would render an empty heading",
                collection.title.isNotBlank(),
            )
        }
    }

    @Test
    fun `every searchable collection has a non-blank query`() {
        Collection.entries
            .filter { it != Collection.RECENTLY_PLAYED }
            .forEach { collection ->
                assertTrue(
                    "$collection has a blank query, so its screen would show nothing",
                    collection.query.isNotBlank(),
                )
            }
    }

    @Test
    fun `recently played is backed by local history rather than a search`() {
        // This one is deliberately query-less: it reads the Room history table. If it ever gains a
        // search query it has been wired to the wrong data source.
        assertEquals("", Collection.RECENTLY_PLAYED.query)
    }

    @Test
    fun `an unknown slug resolves to null rather than a wrong collection`() {
        // The old code answered this case with `else -> search(playlistTitle.lowercase())`, which
        // is how an unrecognised id produced a plausible-looking but unrelated screen. Null forces
        // the caller to decide, and the UI renders an explicit unknown state.
        assertNull(Collection.fromSlug("definitely_not_a_collection"))
        assertNull(Collection.fromSlug(""))
        assertNull(Collection.fromSlug(null))
    }

    @Test
    fun `the legacy emitter ids that used to be sent do not resolve`() {
        // Documents the removed vocabulary. These were the values the Home and Explore screens
        // actually sent before the fix; none of them matched anything the destination understood.
        // They must stay unresolvable so a regression that reintroduces them shows up here.
        val legacyIds = listOf("dm1", "dw", "rr", "tc", "or", "rrw", "fp1", "fp2", "made_for_you")
        legacyIds.forEach { legacy ->
            assertNull(
                "'$legacy' is a removed emitter id and must not resolve to a collection",
                Collection.fromSlug(legacy),
            )
        }
    }

    // --- Genre addressing -------------------------------------------------------------------

    @Test
    fun `a genre slug round-trips through genreOf`() {
        assertEquals("Pop", Collection.genreOf(Collection.genre("Pop")))
        assertEquals("Hip hop", Collection.genreOf(Collection.genre("Hip hop")))
    }

    @Test
    fun `a genre slug is not mistaken for a collection`() {
        // The two id spaces share one route parameter, so the parser must not confuse them.
        assertNull(Collection.fromSlug(Collection.genre("Pop")))
    }

    @Test
    fun `a collection slug is not mistaken for a genre`() {
        assertNull(Collection.genreOf(Collection.TOP_HITS.slug))
        assertNull(Collection.genreOf(null))
    }

    @Test
    fun `genreOf preserves names containing spaces and ampersands`() {
        val name = "R&B"
        assertEquals(name, Collection.genreOf(Collection.genre(name)))
    }

    // --- Coverage of the surfaces that were broken ------------------------------------------

    @Test
    fun `the collections the Home made-for-you row opens all resolve`() {
        val homeCollections = listOf(
            Collection.DISCOVER_WEEKLY,
            Collection.RELEASE_RADAR,
            Collection.DAILY_MIX_1,
            Collection.TIME_CAPSULE,
            Collection.ON_REPEAT,
            Collection.REPEAT_REWIND,
        )
        homeCollections.forEach { collection ->
            assertNotNull(
                "Home renders a card for $collection that must be openable",
                Collection.fromSlug(collection.slug),
            )
        }
    }

    @Test
    fun `the collections the Explore featured row opens all resolve`() {
        val exploreCollections = listOf(
            Collection.TOP_HITS,
            Collection.RAP_CAVIAR,
            Collection.ALL_OUT_2020S,
            Collection.ROCK_CLASSICS,
            Collection.CHILL_HITS,
            Collection.VIVA_LATINO,
        )
        exploreCollections.forEach { collection ->
            assertNotNull(
                "Explore renders a card for $collection that must be openable",
                Collection.fromSlug(collection.slug),
            )
        }
    }

    @Test
    fun `the See All rows resolve`() {
        // These three were previously emitted as raw literals ("recently_played", "recommended",
        // "trending") that existed only at the call site.
        listOf(Collection.RECENTLY_PLAYED, Collection.RECOMMENDED, Collection.TRENDING)
            .forEach { collection ->
                assertNotNull(Collection.fromSlug(collection.slug))
            }
    }
}
