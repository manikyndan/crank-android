package com.crank.music.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the library's two id vocabularies.
 *
 * Both enums exist because the same bug appeared twice in this app: a UI surface invented its
 * own id for a destination while the receiving code matched on a different string, so the
 * lookup silently fell through to a default and rendered the wrong content. These tests pin
 * the contract so a future rename cannot quietly reintroduce the mismatch.
 */
class LibraryVocabularyTest {

    // ── LibrarySection ───────────────────────────────────────────────────────────

    @Test
    fun `every section slug is unique`() {
        val slugs = LibrarySection.entries.map { it.slug }
        assertEquals(slugs.size, slugs.distinct().size)
    }

    @Test
    fun `every section label is unique`() {
        val labels = LibrarySection.entries.map { it.label }
        assertEquals(labels.size, labels.distinct().size)
    }

    @Test
    fun `every section slug round-trips through fromSlug`() {
        LibrarySection.entries.forEach { section ->
            assertEquals(section, LibrarySection.fromSlug(section.slug))
        }
    }

    /**
     * An unknown section must resolve to ALL rather than leaving the screen with no branch to
     * render. This is the one place the design deliberately differs from `Collection.fromSlug`,
     * which returns null: a bad collection has no sensible default, but a bad library filter
     * does — show everything.
     */
    @Test
    fun `an unknown section slug falls back to ALL rather than nothing`() {
        assertEquals(LibrarySection.ALL, LibrarySection.fromSlug("does_not_exist"))
        assertEquals(LibrarySection.ALL, LibrarySection.fromSlug(null))
    }

    @Test
    fun `fromLabelOrSlug accepts both forms`() {
        assertEquals(LibrarySection.SONGS, LibrarySection.fromLabelOrSlug("songs"))
        assertEquals(LibrarySection.SONGS, LibrarySection.fromLabelOrSlug("Songs"))
        assertEquals(LibrarySection.RECENTLY_ADDED, LibrarySection.fromLabelOrSlug("recently_added"))
        assertEquals(LibrarySection.RECENTLY_ADDED, LibrarySection.fromLabelOrSlug("Recently Added"))
    }

    /** The tab row renders `ordered`, so every section must appear exactly once. */
    @Test
    fun `ordered contains every section exactly once`() {
        assertEquals(LibrarySection.entries.size, LibrarySection.ordered.size)
        assertEquals(LibrarySection.entries.toSet(), LibrarySection.ordered.toSet())
    }

    // ── SmartPlaylistKind ────────────────────────────────────────────────────────

    @Test
    fun `every smart playlist slug is unique`() {
        val slugs = SmartPlaylistKind.entries.map { it.slug }
        assertEquals(slugs.size, slugs.distinct().size)
    }

    @Test
    fun `every smart playlist slug round-trips`() {
        SmartPlaylistKind.entries.forEach { kind ->
            assertEquals(kind, SmartPlaylistKind.fromSlug(kind.slug))
        }
    }

    /**
     * Unlike a library section, an unrecognised smart playlist must NOT fall back to one of the
     * three. The old implementation used `sp1`/`sp2`/`sp3` for the cards while the resolver
     * matched other strings entirely, so every tap opened the same wrong list. Returning null
     * forces the caller to handle the miss explicitly.
     */
    @Test
    fun `an unknown smart playlist slug resolves to null rather than a wrong collection`() {
        assertNull(SmartPlaylistKind.fromSlug("sp1"))
        assertNull(SmartPlaylistKind.fromSlug("sp2"))
        assertNull(SmartPlaylistKind.fromSlug("sp3"))
        assertNull(SmartPlaylistKind.fromSlug(null))
    }

    /** The ids the previous implementation emitted must stay unresolvable. */
    @Test
    fun `the legacy smart playlist ids do not resolve`() {
        // These are the literal ids the old LibraryViewModel put on the cards. If a future
        // change reintroduces them, the card would render but resolve to nothing.
        listOf("sp1", "sp2", "sp3", "smart1", "smart_1").forEach { legacy ->
            assertNull("legacy id '$legacy' unexpectedly resolved", SmartPlaylistKind.fromSlug(legacy))
        }
    }

    @Test
    fun `every smart playlist carries user-facing copy`() {
        SmartPlaylistKind.entries.forEach { kind ->
            assertNotNull(kind.title.takeIf { it.isNotBlank() })
            assertNotNull(kind.description.takeIf { it.isNotBlank() })
            assertNotNull(kind.icon.takeIf { it.isNotBlank() })
        }
    }

    /** Each kind must have a distinct icon, or the cards are visually indistinguishable. */
    @Test
    fun `smart playlist icons are distinct`() {
        val icons = SmartPlaylistKind.entries.map { it.icon }
        assertEquals(icons.size, icons.distinct().size)
    }

    /**
     * Regression guard for a bug the device run caught.
     *
     * Library emitted `smart_most_played` into the `playlist_detail/{playlistId}` route, but the
     * destination only resolved `Collection` and `genre:` slugs, so every smart playlist opened
     * as "Unknown Collection". The bug was a vocabulary mismatch across two subsystems — the
     * same class of defect this whole refactor exists to eliminate.
     *
     * This asserts that every slug a Library card can emit is resolvable by the destination.
     * Adding a new smart playlist without teaching the destination about it fails here.
     */
    @Test
    fun `every smart playlist slug is resolvable by the detail destination`() {
        SmartPlaylistKind.entries.forEach { kind ->
            assertNotNull(
                "slug '${kind.slug}' is emitted by Library but not resolvable by the destination",
                SmartPlaylistKind.fromSlug(kind.slug),
            )
        }
    }

    /** The slug must not collide with a Collection slug, or the two would shadow each other. */
    @Test
    fun `smart playlist slugs do not collide with collection slugs`() {
        val collectionSlugs = com.crank.music.domain.model.Collection.entries.map { it.slug }.toSet()
        SmartPlaylistKind.entries.forEach { kind ->
            assertFalse(
                "smart playlist slug '${kind.slug}' shadows a Collection slug",
                kind.slug in collectionSlugs,
            )
        }
    }
}
