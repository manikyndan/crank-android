package com.crank.music.domain.model

/**
 * A selectable streaming quality, and the bitrate ceiling it imposes on format selection.
 *
 * This lives in the domain rather than next to the settings screen because the data layer has to
 * honour it: the choice is only meaningful if [maxBitrateKbps] actually reaches
 * [com.crank.music.data.remote.innertube.StreamCascadeResolver], which previously always picked the
 * highest-bitrate format available and ignored the setting entirely.
 *
 * ## What was removed
 *
 * The screen also offered Lossless (FLAC), Dolby Atmos, sample rate and bit depth. None of those can
 * be delivered by this app's source: YouTube's InnerTube responses expose AAC/Opus audio in an
 * adaptive container, not FLAC, and offer no Atmos or hi-res variants. `LOSSLESS` was the *default*
 * for Wi-Fi, so the app advertised a codec it could never play, and the "current format" panel read
 * a hardcoded `"FLAC 1.4.3 • 48kHz / 24-bit"`. Every one of those controls is gone rather than
 * left in place pretending.
 */
enum class StreamQuality(
    val label: String,
    /** Nominal ceiling used for format selection, in kilobits per second. */
    val maxBitrateKbps: Int,
    val isHighUsage: Boolean
) {
    LOW("Low", 96, false),
    MEDIUM("Medium", 160, false),
    HIGH("High", 320, true);

    /** Shown next to the label, e.g. "160 kbps". Derived so it cannot drift from the ceiling. */
    val kbps: String get() = "$maxBitrateKbps kbps"

    /**
     * Rough on-disk size of ten typical tracks (~3.5 minutes each).
     *
     * Computed from [maxBitrateKbps] rather than hardcoded, so the estimate and the ceiling it
     * describes can never disagree — the previous Screen hardcoded separate strings for both.
     */
    val dataPerTenSongs: String get() {
        val bytes = maxBitrateKbps * 1000L / 8L * TEN_SONGS_SECONDS
        return "~${bytes / 1_048_576L} MB per 10 songs"
    }

    companion object {
        /** Ten tracks of ~3.5 minutes. */
        private const val TEN_SONGS_SECONDS = 2100L

        /** Resolves a stored enum name, or null when the stored value is unknown. */
        fun fromNameOrNull(name: String): StreamQuality? =
            entries.firstOrNull { it.name == name }
    }
}
