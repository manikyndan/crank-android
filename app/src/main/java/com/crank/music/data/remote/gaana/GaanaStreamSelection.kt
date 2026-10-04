package com.crank.music.data.remote.gaana

/**
 * Choosing a quality tier from Gaana's four `stream_urls` variants.
 *
 * Pure and free of Android, so the mapping from the user's Audio Quality setting onto Gaana's
 * tiers is unit-testable without a network or a device.
 */
object GaanaStreamSelection {

    /**
     * Approximate bitrate of each tier, in kbps.
     *
     * Taken from the filenames Gaana serves (`320.mp4`, `128.mp4`, `64.mp4`, `16.mp4`) — the same
     * numbers as the tier names. They are used only for *ordering* and for comparison against the
     * user's ceiling, never reported to the UI as a measured bitrate, because a value read off a
     * URL is not a measurement of the stream.
     */
    const val VERY_HIGH_KBPS = 320
    const val HIGH_KBPS = 128
    const val MEDIUM_KBPS = 64
    const val LOW_KBPS = 16

    /**
     * The URL for the best tier at or below [maxBitrateKbps], or `null` when Gaana offered none.
     *
     * `null` means "the caller must not invent a stream" — an empty `stream_urls` block is a real
     * answer (a track that is not streamable) and must surface as a failure, not as a silent
     * fall-through to some other track.
     *
     * [maxBitrateKbps] `null` means "no ceiling expressed" and takes the highest tier. A ceiling
     * below the lowest tier still returns the lowest rather than nothing: serving 16 kbps is the
     * honest interpretation of a Data Saver request, whereas returning `null` would look like the
     * song is unavailable.
     */
    fun select(qualities: GaanaStreamQualitiesDto?, maxBitrateKbps: Int?): String? {
        // Descending by bitrate; the order matters, see the `first` below.
        val available = buildList {
            qualities?.veryHigh?.takeIf { it.isNotBlank() }?.let { add(VERY_HIGH_KBPS to it) }
            qualities?.high?.takeIf { it.isNotBlank() }?.let { add(HIGH_KBPS to it) }
            qualities?.medium?.takeIf { it.isNotBlank() }?.let { add(MEDIUM_KBPS to it) }
            qualities?.low?.takeIf { it.isNotBlank() }?.let { add(LOW_KBPS to it) }
        }

        if (available.isEmpty()) return null
        if (maxBitrateKbps == null) return available.first().second

        return (available.firstOrNull { (kbps, _) -> kbps <= maxBitrateKbps } ?: available.last()).second
    }
}
