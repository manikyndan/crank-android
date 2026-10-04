package com.crank.music.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.crank.music.data.local.SettingsStore
import com.crank.music.domain.model.StreamQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns the audio-quality settings into the bitrate ceiling for the network the device is on.
 *
 * Kept separate from [MusicRepositoryImpl] because the decision depends on two independent inputs —
 * the stored preference and the live network state — and the repository should not have to know how
 * either is obtained. It is also the one place where "Data Saver" turns into an actual number, which
 * makes the behaviour testable without a device.
 */
@Singleton
class AudioQualityResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore,
) {

    /**
     * The effective ceiling in kbps, or null for "best available".
     *
     * Rules, in order:
     *
     * 1. **Data Saver on a metered network** caps at [StreamQuality.LOW]. This is the whole point of
     *    the toggle; before this it did nothing at all.
     * 2. Otherwise the preference for the current transport is used — [SettingsStore.WIFI_QUALITY]
     *    on Wi-Fi/ethernet, [SettingsStore.MOBILE_QUALITY] on cellular.
     *
     * Data Saver deliberately does not overwrite the mobile preference. It is an override applied at
     * resolution time, so turning it off restores the user's own choice rather than leaving mobile
     * pinned to the lowest quality.
     *
     * Returns null (meaning "no ceiling") only when settings cannot be read at all, because failing
     * open to the best available stream is better than failing a play over a settings read.
     */
    suspend fun currentMaxBitrateKbps(): Int? {
        val isMetered = isOnMeteredNetwork()
        val dataSaver = settingsStore.getBoolean(SettingsStore.DATA_SAVER, false)

        if (dataSaver && isMetered) return StreamQuality.LOW.maxBitrateKbps

        val key = if (isMetered) SettingsStore.MOBILE_QUALITY else SettingsStore.WIFI_QUALITY
        val default = StreamQuality.HIGH.name
        val stored = settingsStore.getString(key, default)
        return StreamQuality.fromNameOrNull(stored)?.maxBitrateKbps
    }

    /**
     * Ceiling for a user-initiated download, in kbps.
     *
     * Downloads ignore the active network entirely — a download is an explicit "I want this on my
     * device" action, so what the user is on right now is not a reason to store a worse copy. Only
     * [SettingsStore.DOWNLOAD_QUALITY] applies, and Data Saver deliberately does not override it.
     */
    suspend fun downloadMaxBitrateKbps(): Int? {
        val stored = settingsStore.getString(SettingsStore.DOWNLOAD_QUALITY, StreamQuality.HIGH.name)
        return StreamQuality.fromNameOrNull(stored)?.maxBitrateKbps
    }

    /**
     * Whether the active network is metered.
     *
     * Defaults to `true` when the state cannot be determined. That is the conservative answer: it
     * applies the mobile preference (and honours Data Saver) on an unknown network, whereas
     * defaulting to false would let Data Saver silently stop applying on the connections where it
     * matters.
     */
    private fun isOnMeteredNetwork(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return true
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
