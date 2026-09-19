package com.crank.music.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the lyrics parser.
 *
 * Every case here is a real LRC shape that the previous single-regex parser mishandled. They are
 * grouped by the feature they exercise rather than by method, because the failures are what
 * matter, not the internals.
 */
class LyricsParserTest {

    @Test
    fun `parses a basic timestamped line`() {
        val result = LyricsParser.parse("[00:12.34]Hello world")

        assertEquals(1, result.lines.size)
        assertEquals("Hello world", result.lines[0].text)
        assertEquals(12_340L, result.lines[0].timestampMs)
        assertEquals(LyricsTiming.SYNCED, result.timing)
    }

    @Test
    fun `parses two-digit fractions as centiseconds`() {
        // `[00:12.34]` is 12.34 seconds, not 12.0034 and not 12.340. The old parser multiplied by
        // ten unconditionally, which happens to be right for two digits — this pins it.
        assertEquals(12_340L, LyricsParser.parse("[00:12.34]x").lines[0].timestampMs)
    }

    @Test
    fun `parses three-digit fractions as milliseconds`() {
        assertEquals(12_345L, LyricsParser.parse("[00:12.345]x").lines[0].timestampMs)
    }

    @Test
    fun `parses a single-digit fraction`() {
        // `[00:12.5]` means 500ms, not 5ms.
        assertEquals(12_500L, LyricsParser.parse("[00:12.5]x").lines[0].timestampMs)
    }

    @Test
    fun `parses a timestamp with no fraction`() {
        // `[00:12]` is valid LRC and previously failed to match at all.
        assertEquals(12_000L, LyricsParser.parse("[00:12]x").lines[0].timestampMs)
    }

    @Test
    fun `parses timestamps past ninety-nine minutes`() {
        // The old regex required exactly two minute digits, so this line was silently dropped.
        val result = LyricsParser.parse("[100:00.00]Late in the track")

        assertEquals(1, result.lines.size)
        assertEquals(6_000_000L, result.lines[0].timestampMs)
    }

    @Test
    fun `parses hour-long timestamps`() {
        // h:mm:ss.xx appears in long mixes and DJ sets.
        val result = LyricsParser.parse("[1:02:03.45]Deep cut")

        assertEquals(1, result.lines.size)
        assertEquals(3_723_450L, result.lines[0].timestampMs)
    }

    @Test
    fun `expands a line with multiple timestamps`() {
        // The chorus pattern: one text repeated under several timestamps. `matchEntire` rejected
        // these outright, so every repeated chorus line was missing from the display.
        val result = LyricsParser.parse("[00:12.00][01:30.00]Chorus line")

        assertEquals(2, result.lines.size)
        assertEquals(listOf("Chorus line", "Chorus line"), result.lines.map { it.text })
        assertEquals(listOf(12_000L, 90_000L), result.lines.map { it.timestampMs })
    }

    @Test
    fun `sorts lines by timestamp`() {
        val result = LyricsParser.parse(
            """
            [00:30.00]Second
            [00:10.00]First
            [00:20.00]Middle
            """.trimIndent(),
        )

        assertEquals(listOf("First", "Middle", "Second"), result.lines.map { it.text })
    }

    @Test
    fun `skips metadata tags and keeps the lyrics`() {
        val result = LyricsParser.parse(
            """
            [ar:Some Artist]
            [ti:Some Title]
            [00:01.00]Real line
            """.trimIndent(),
        )

        assertEquals(listOf("Real line"), result.lines.map { it.text })
    }

    @Test
    fun `applies the offset tag to every timestamp`() {
        // Sources use `[offset:]` to shift the whole file. Ignoring it makes every line early or
        // late by a constant, which reads as "the lyrics are out of sync".
        val result = LyricsParser.parse(
            """
            [offset:+500]
            [00:10.00]Line
            """.trimIndent(),
        )

        assertEquals(10_500L, result.lines[0].timestampMs)
    }

    @Test
    fun `applies a negative offset`() {
        val result = LyricsParser.parse(
            """
            [offset:-200]
            [00:10.00]Line
            """.trimIndent(),
        )

        assertEquals(9_800L, result.lines[0].timestampMs)
    }

    @Test
    fun `clamps a negative result to zero rather than producing a negative timestamp`() {
        // A negative timestamp would never be <= progress for the first line, so the opening line
        // could never become active.
        val result = LyricsParser.parse(
            """
            [offset:-5000]
            [00:01.00]Opening line
            """.trimIndent(),
        )

        assertEquals(0L, result.lines[0].timestampMs)
    }

    @Test
    fun `drops empty timestamped lines`() {
        // A timestamp with no text is a gap marker, not a lyric.
        val result = LyricsParser.parse(
            """
            [00:05.00]
            [00:10.00]Actual line
            """.trimIndent(),
        )

        assertEquals(listOf("Actual line"), result.lines.map { it.text })
    }

    @Test
    fun `handles plain lyrics with no timestamps at all`() {
        val result = LyricsParser.parse(
            """
            First line
            Second line
            Third line
            """.trimIndent(),
        )

        assertEquals(3, result.lines.size)
        assertEquals(LyricsTiming.ESTIMATED, result.timing)
    }

    @Test
    fun `spreads plain lyrics across a known duration`() {
        // With a real duration the lines track the music instead of drifting ahead of it.
        val lines = (1..4).map { "Line $it" }.joinToString("\n")
        val result = LyricsParser.parse(lines, durationMs = 240_000L)

        assertEquals(4, result.lines.size)
        assertEquals(0L, result.lines[0].timestampMs)
        assertEquals(60_000L, result.lines[1].timestampMs)
        assertEquals(180_000L, result.lines[3].timestampMs)
    }

    @Test
    fun `keeps estimated lines distinct for a very short track`() {
        // Proportional spacing on a short track with many lines would put several lines on the
        // same millisecond, making the active line ambiguous.
        val lines = (1..50).map { "Line $it" }.joinToString("\n")
        val result = LyricsParser.parse(lines, durationMs = 5_000L)

        val timestamps = result.lines.map { it.timestampMs }
        assertEquals("timestamps must be strictly increasing", timestamps.sorted(), timestamps)
        assertEquals("no duplicate timestamps", timestamps.size, timestamps.toSet().size)
    }

    @Test
    fun `flags estimated timings so the UI can avoid asserting a current line`() {
        // The point of the flag: invented timestamps must not be presented as the singer's
        // position.
        val result = LyricsParser.parse("Just some words")
        assertEquals(LyricsTiming.ESTIMATED, result.timing)
    }

    @Test
    fun `flags parsed timings as synced`() {
        assertEquals(LyricsTiming.SYNCED, LyricsParser.parse("[00:01.00]x").timing)
    }

    @Test
    fun `appends untimed lines after timed ones in a mixed file`() {
        val result = LyricsParser.parse(
            """
            [00:10.00]Timed line
            Untimed line
            """.trimIndent(),
        )

        assertEquals(listOf("Timed line", "Untimed line"), result.lines.map { it.text })
        assertEquals(LyricsTiming.SYNCED, result.timing)
        assertTrue(result.lines[1].timestampMs > result.lines[0].timestampMs)
    }

    @Test
    fun `returns empty for a blank document`() {
        assertTrue(LyricsParser.parse("").isEmpty)
        assertTrue(LyricsParser.parse("   \n\n  ").isEmpty)
    }

    @Test
    fun `returns empty for a document with only metadata`() {
        assertTrue(LyricsParser.parse("[ar:Artist]\n[ti:Title]").isEmpty)
    }

    @Test
    fun `de-duplicates identical timestamp and text pairs`() {
        // Some sources emit the same line twice. Showing it twice looks like a rendering fault.
        val result = LyricsParser.parse(
            """
            [00:10.00]Same line
            [00:10.00]Same line
            """.trimIndent(),
        )

        assertEquals(1, result.lines.size)
    }

    @Test
    fun `keeps different text at the same timestamp`() {
        // Two voices at once is legitimate and must not be collapsed by the de-duplication.
        val result = LyricsParser.parse(
            """
            [00:10.00]Voice one
            [00:10.00]Voice two
            """.trimIndent(),
        )

        assertEquals(2, result.lines.size)
    }

    @Test
    fun `ignores a malformed timestamp in an otherwise valid file`() {
        val result = LyricsParser.parse(
            """
            [00:10.00]Good line
            [ab:cd.ef]Bad timestamp
            [00:20.00]Another good line
            """.trimIndent(),
        )

        assertEquals(listOf("Good line", "Another good line"), result.lines.map { it.text })
    }

    @Test
    fun `drops a line whose timestamp mixes digits and letters`() {
        // Half-valid: the minutes are numeric but the seconds are not. The whole line is markup,
        // and the leftover text must not be published — `Half a timestamp` is what the file author
        // wrote as a label for the broken stamp, not a lyric.
        val result = LyricsParser.parse("[00:xx]Half a timestamp")

        assertTrue(result.lines.isEmpty())
    }

    @Test
    fun `keeps a lyric that merely contains brackets and a colon`() {
        // The other side of the same decision. A guard against malformed timestamps must not
        // become a guard against brackets: here the bracket is embedded in prose, so this is a
        // lyric and must survive verbatim. Only a *leading* bracket is treated as markup.
        val result = LyricsParser.parse("She left [him: her] standing there")

        assertEquals(listOf("She left [him: her] standing there"), result.lines.map { it.text })
    }

    @Test
    fun `keeps bracket markup that follows a real timestamp`() {
        // Once a line has matched a real timestamp, bracketed text after it is lyric text. The
        // guard only applies to lines that produced no timestamp at all.
        val result = LyricsParser.parse("[00:10.00]She left [him: her] standing there")

        assertEquals(1, result.lines.size)
        assertEquals("She left [him: her] standing there", result.lines.single().text)
    }

    @Test
    fun `drops a line whose broken prefix sits in front of a valid timestamp`() {
        // A malformed stamp can precede real ones. The line is still broken markup and must be
        // dropped whole, so the timings on either side are not disturbed.
        val result = LyricsParser.parse(
            """
            [00:10.00]Good line
            [ab:cd.ef][00:15.00]Markup line
            [00:20.00]Another good line
            """.trimIndent(),
        )

        assertEquals(listOf("Good line", "Another good line"), result.lines.map { it.text })
        assertEquals(listOf(10_000L, 20_000L), result.lines.map { it.timestampMs })
    }

    @Test
    fun `drops a bare timestamp with no text`() {
        // `[00:10]` on its own carries no lyric and must not become an empty entry.
        val result = LyricsParser.parse("[00:10]\n[00:20]Real line")

        assertEquals(listOf("Real line"), result.lines.map { it.text })
    }

    @Test
    fun `tolerates windows line endings`() {
        val result = LyricsParser.parse("[00:10.00]One\r\n[00:20.00]Two")
        assertEquals(listOf("One", "Two"), result.lines.map { it.text })
    }

    @Test
    fun `handles a source that repeats a text under a bracket pair plus a lone timestamp`() {
        // A realistically messy line: three timestamps, one text.
        val result = LyricsParser.parse("[00:05.00][00:45.00][01:20.50]Repeated hook")

        assertEquals(3, result.lines.size)
        assertEquals(setOf("Repeated hook"), result.lines.map { it.text }.toSet())
        assertEquals(listOf(5_000L, 45_000L, 80_500L), result.lines.map { it.timestampMs })
    }
}
