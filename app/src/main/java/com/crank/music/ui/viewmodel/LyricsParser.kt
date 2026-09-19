package com.crank.music.ui.viewmodel

/**
 * A single line of lyrics with the moment it should be highlighted.
 *
 * [timestampMs] is the line's start time. For lyrics that carry no timings the caller still
 * assigns one — see [LyricsParser.estimateTimings] — because a line without a timestamp can
 * never become the active line, and the display depends on knowing which line is current.
 */
data class LyricsLine(
    val timestampMs: Long,
    val text: String,
)

/**
 * Whether a set of lyrics carries real timings, or timings we invented.
 *
 * ## Why the distinction has to be visible to the UI
 *
 * The player estimates timestamps for plain, unsynced lyrics so that scrolling still works. But
 * estimated timings are evenly spaced on an assumption — they are not where the singer actually
 * is. Highlighting a line as "now playing" on the strength of an invented timestamp tells the
 * user something false.
 *
 * So the display can treat unsynced lyrics differently: scroll them, but do not assert which
 * line is current.
 */
enum class LyricsTiming {
    /** Timestamps came from the source. The active line is meaningful. */
    SYNCED,

    /** Timestamps were estimated. Scroll, but do not claim to know the current line. */
    ESTIMATED,
}

/**
 * The result of parsing a lyrics document.
 *
 * [lines] is always sorted by timestamp and free of empty entries, so callers can rely on binary
 * search and on index order matching time order.
 */
data class ParsedLyrics(
    val lines: List<LyricsLine>,
    val timing: LyricsTiming,
) {
    val isEmpty: Boolean get() = lines.isEmpty()

    companion object {
        val EMPTY = ParsedLyrics(emptyList(), LyricsTiming.ESTIMATED)
    }
}

/**
 * Parses LRC-format and plain-text lyrics.
 *
 * ## What was wrong with the previous parser
 *
 * It was a private method on the view model with a single regex, `\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)`,
 * applied with `matchEntire`. Four real LRC features broke against it:
 *
 * 1. **Multiple timestamps on one line.** LRC repeats a line's text under several timestamps
 *    (`[00:12.00][01:30.00]Chorus`). `matchEntire` requires the whole line to be one timestamp
 *    plus text, so every such line was dropped.
 * 2. **Minute counts over 99.** `\d{2}` caps at two digits. A track past 99 minutes — or, more
 *    commonly, a `[100:00.00]`-style marker from a mis-tagged file — failed to match and the line
 *    vanished. Hours are common in some sources: `[1:02:03.45]`.
 * 3. **Missing fractional part.** `[00:12]` without milliseconds is valid LRC and did not match.
 * 4. **Metadata tags.** `[ar:Artist]`, `[ti:Title]`, `[offset:+500]` are legitimate LRC lines. The
 *    old regex rejected them (correctly) but also ignored `[offset:]`, which several sources use
 *    to shift every timestamp — so timings were systematically off for those files.
 *
 * Extracted from the view model so it can be tested directly. Lyrics parsing is pure string
 * handling with a lot of edge cases and no Android dependency, which makes it exactly the sort of
 * thing that should not live behind a `ViewModel` where it can only be exercised through a
 * player.
 */
object LyricsParser {

    /**
     * Whether the line starts with a valid `[mm:ss]` / `[h:mm:ss.ff]` time signature.
     *
     * Only ever consulted to route a line between the timed and untimed branch. The actual parsing
     * is done by [stripLeadingTimestamps], which walks the prefix one bracket at a time; keeping a
     * separate regex for the routing decision would mean two definitions of "is a timestamp" that
     * can drift apart, which is exactly how the malformed-prefix case slipped through.
     */
    private fun startsWithTimestamp(line: String): Boolean =
        stripLeadingTimestamps(line, emptyList()).offsets.isNotEmpty()

    /** LRC metadata tags: `[ar:...]`, `[ti:...]`, `[offset:...]`. */
    private val METADATA_TAG = Regex("""^\[([a-zA-Z#]+):(.*)]$""")

    /**
     * Pulls the bracketed spans off the front of a line, e.g. `[00:12][01:30]Chorus`.
     *
     * Walks the leading run of brackets rather than regex-matching them, because the goal is not
     * to *find* timestamps but to consume the prefix and hand back the remainder.
     *
     * Returns whether every bracket was a valid timestamp alongside whatever follows them. The
     * caller needs both facts, because they answer different questions: the offsets are the
     * timings, and `allTimestamps` distinguishes a line that was *entirely* markup from one that
     * merely *starts* with it.
     */
    private tailrec fun stripLeadingTimestamps(
        line: String,
        offsets: List<Long>,
        allTimestamps: Boolean = true,
    ): LeadingPrefix {
        val end = line.indexOf(']', startIndex = 1)

        // No bracketed prefix left. Whatever remains is the text: lyric prose if the brackets all
        // parsed, or the leftover of a malformed prefix if one did not.
        if (!line.startsWith("[") || end <= 0) return LeadingPrefix(offsets, line, allTimestamps)

        val timestampMs = parseTimestampSpan(line.substring(1, end))
        if (timestampMs == null) {
            // A bracketed prefix that is not a time signature. Keep walking — a malformed stamp can
            // sit in front of real ones, as in `[ab:cd.ef][00:10]Chorus` — but remember that this
            // line contained markup, so the caller can drop it rather than show the leftover text.
            return stripLeadingTimestamps(line.substring(end + 1), offsets, allTimestamps = false)
        }

        return stripLeadingTimestamps(line.substring(end + 1), offsets + timestampMs, allTimestamps)
    }

    /**
     * A line's leading brackets, split into the timings they carried and what followed them.
     *
     * [allTimestamps] is false when any leading bracket failed to parse. Such a line is broken
     * markup, not lyrics: `[ab:cd.ef]Bad timestamp` would otherwise be published as the text
     * `Bad timestamp`, which is not what the file author wrote as a lyric.
     */
    private data class LeadingPrefix(
        val offsets: List<Long>,
        val text: String,
        val allTimestamps: Boolean,
    )

    /**
     * Parses one `mm:ss[.ff]` / `h:mm:ss[.ff]` time signature, or returns `null`.
     *
     * The digit check has to happen *before* the numeric conversion, not as a result of it. `[00:xx]`
     * is the case that proves it: `"xx".toLongOrNull()` already returns `null`, but `[foo:xx]` would
     * also be caught that way, and so would a line of stream credits — while what we actually want
     * to drop is the narrow, unambiguous shape of a broken time signature, which is digits on both
     * sides of the colon and nowhere else. Requiring digits is also what distinguishes it from
     * bracketed prose, e.g. `[him: her]`, which this must leave alone.
     */
    private fun parseTimestampSpan(span: String): Long? {
        val groups = span.split(':')
        if (groups.size !in 2..3) return null

        // Every group must be digits; the last may additionally carry a fractional part.
        val last = groups.last()
        val fractionSeparator = last.indexOfFirst { it == '.' || it == ':' }
        val wholePart = if (fractionSeparator >= 0) last.substring(0, fractionSeparator) else last
        val fractionPart = if (fractionSeparator >= 0) last.substring(fractionSeparator + 1) else ""

        if (wholePart.isEmpty() || wholePart.any { !it.isDigit() }) return null
        if (fractionPart.any { !it.isDigit() }) return null
        if (groups.dropLast(1).any { group -> group.isEmpty() || group.any { !it.isDigit() } }) return null

        val numbers = groups.dropLast(1).map { it.toLong() }
        val seconds = wholePart.toLong()

        val hours: Long
        val minutes: Long
        if (numbers.size == 2) {
            // h:mm:ss — the fraction binds to the seconds group.
            hours = numbers[0]
            minutes = numbers[1]
        } else {
            hours = 0L
            minutes = numbers[0]
        }

        val fractionMs =
            when (fractionPart.length) {
                0 -> 0L
                1 -> fractionPart.toLong() * 100
                2 -> fractionPart.toLong() * 10
                else -> fractionPart.substring(0, 3).toLong()
            }

        return hours * 3_600_000 + minutes * 60_000 + seconds * 1_000 + fractionMs
    }

    /** How long each line is assumed to last when no timings are available. */
    private const val ESTIMATED_LINE_DURATION_MS = 4_000L

    /**
     * Parses [raw] into timed lines.
     *
     * @param durationMs the track length, used to space estimates across the whole song. Passing
     *   the real duration makes unsynced lyrics scroll at roughly the right pace instead of
     *   drifting ahead of the music; passing 0 falls back to a fixed per-line interval.
     */
    fun parse(raw: String, durationMs: Long = 0L): ParsedLyrics {
        if (raw.isBlank()) return ParsedLyrics.EMPTY

        val offsetMs = parseOffsetMs(raw)
        val timed = mutableListOf<LyricsLine>()
        val untimed = mutableListOf<String>()

        for (rawLine in raw.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            // A pure metadata tag carries no lyric text. Skipping it before the timestamp scan
            // matters because [ar:...] would otherwise match the tag pattern and be treated as
            // text for a line with no timestamp.
            val asMetadata = METADATA_TAG.matchEntire(line)
            if (asMetadata != null && !startsWithTimestamp(line)) {
                val key = asMetadata.groupValues[1].lowercase()
                if (key == "offset") {
                    // Handled up front by parseOffsetMs; skipping again here keeps this branch
                    // from falling through to `untimed` and appearing as a lyric line.
                    continue
                }
                // Other tags (ar/ti/al/by) are metadata, not lyrics.
                if (key in KNOWN_METADATA_KEYS) continue
            }

            if (!startsWithTimestamp(line)) {
                // No timestamp. Three things land here and they must be told apart:
                //
                //   * a plain-lyrics line, which we keep as-is;
                //   * a line whose leading brackets are broken markup, e.g. `[ab:cd.ef]Bad text`,
                //     which we drop — publishing `Bad text` would attribute the author's text to a
                //     line they never wrote, and publishing the brackets would show raw markup;
                //   * a line with brackets buried in prose, e.g. `She left [him: her] there`, which
                //     we keep verbatim. The brackets are punctuation, not a timestamp.
                val prefix = stripLeadingTimestamps(line, emptyList())

                // Broke out of the leading brackets: markup, not lyrics.
                if (!prefix.allTimestamps) continue

                if (prefix.text.isNotBlank()) untimed += prefix.text
                continue
            }

            // At least one timestamp parsed. Use the walker's own remaining text rather than a
            // regex match, so both branches agree on what a line's lyric text is.
            val prefix = stripLeadingTimestamps(line, emptyList())
            if (!prefix.allTimestamps) continue
            if (prefix.offsets.isEmpty()) continue

            val text = prefix.text
            if (text.isEmpty()) continue

            for (base in prefix.offsets) {
                timed += LyricsLine(timestampMs = (base + offsetMs).coerceAtLeast(0L), text = text)
            }
        }

        // A file can legitimately mix both. When it has real timings, use them and append any
        // untimed lines at the end rather than discarding them — the text is still worth reading.
        if (timed.isNotEmpty()) {
            val sorted = timed.distinctBy { it.timestampMs to it.text }.sortedBy { it.timestampMs }
            if (untimed.isEmpty()) return ParsedLyrics(sorted, LyricsTiming.SYNCED)

            val tailStart = (sorted.last().timestampMs + ESTIMATED_LINE_DURATION_MS)
            val tail = untimed.mapIndexed { index, text ->
                LyricsLine(tailStart + index * ESTIMATED_LINE_DURATION_MS, text)
            }
            return ParsedLyrics(sorted + tail, LyricsTiming.SYNCED)
        }

        if (untimed.isEmpty()) return ParsedLyrics.EMPTY

        return ParsedLyrics(
            lines = estimateTimings(untimed, durationMs),
            timing = LyricsTiming.ESTIMATED,
        )
    }


    /**
     * Reads the LRC `[offset:+500]` tag, in milliseconds.
     *
     * Sources use this to shift every timestamp in the file, typically to correct for encoder or
     * release timing. Ignoring it leaves all lyrics consistently early or late by the offset,
     * which reads as "the lyrics are out of sync" even though the timestamps themselves parsed.
     */
    private fun parseOffsetMs(raw: String): Long {
        for (line in raw.lineSequence()) {
            val match = METADATA_TAG.matchEntire(line.trim()) ?: continue
            if (match.groupValues[1].lowercase() != "offset") continue
            return match.groupValues[2].trim().toLongOrNull() ?: 0L
        }
        return 0L
    }

    /**
     * Spaces untimed lines across the track.
     *
     * With a known [durationMs] the lines are spread proportionally, which keeps scrolling in
     * step with the music instead of racing ahead. Without one, a fixed interval is used: a
     * guess, but a bounded one, and the result is flagged [LyricsTiming.ESTIMATED] either way.
     */
    private fun estimateTimings(lines: List<String>, durationMs: Long): List<LyricsLine> {
        if (lines.isEmpty()) return emptyList()

        val interval =
            if (durationMs > 0L && durationMs / lines.size >= MIN_ESTIMATED_INTERVAL_MS) {
                durationMs / lines.size
            } else {
                ESTIMATED_LINE_DURATION_MS
            }

        return lines.mapIndexed { index, text -> LyricsLine(index * interval, text) }
    }

    /**
     * Lowest per-line spacing used when spreading lines over a known duration.
     *
     * Guards the pathological case of a very short track with many lines, where proportional
     * spacing would put several lines on the same millisecond and make the active line ambiguous.
     */
    private const val MIN_ESTIMATED_INTERVAL_MS = 250L

    /** LRC tags that are metadata rather than lyric text. */
    private val KNOWN_METADATA_KEYS =
        setOf("ar", "ti", "al", "by", "au", "re", "ve", "length", "offset")
}
