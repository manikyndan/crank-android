package com.crank.music

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.crank.music.data.remote.NewPipeDownloader
import com.crank.music.data.remote.potoken.PoTokenWebView
import com.crank.music.service.UpdateNotificationHelper
import dagger.hilt.android.HiltAndroidApp
import org.schabi.newpipe.extractor.NewPipe
import javax.inject.Inject

@HiltAndroidApp
class CrankApplication : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var imageLoader: ImageLoader

    override fun onCreate() {
        super.onCreate()
        NewPipe.init(NewPipeDownloader())
        createNotificationChannels()

        // Playback depends on a Proof-of-Origin token that can only be minted from the BotGuard
        // asset (`po_token.html`), which is deliberately not committed to source control. Warn
        // loudly at startup so a missing asset is discoverable immediately, rather than as a
        // first-byte HTTP 403 that only shows up the first time a track is played.
        if (!PoTokenWebView.isAssetPresent(this)) {
            Log.w(TAG, PoTokenWebView.missingAssetMessage())
        }
    }

    /**
     * Creates the update notification channel.
     *
     * ## Why the API-level guard is load-bearing
     *
     * Both `NotificationChannel` and `NotificationManager.createNotificationChannel` are API 26,
     * and this app's `minSdk` is 24. On Android 7.0/7.1 the old unconditional call threw
     * `NoClassDefFoundError` during `Application.onCreate`, so the app crashed on every launch
     * before a single frame was drawn. The channel concept simply does not exist below 26, and
     * notifications posted with a channel id fall back to the legacy behaviour there, so
     * returning early is correct rather than a degradation.
     *
     * The channel id/name are taken from [UpdateNotificationHelper] so the two definitions
     * cannot drift apart.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            UpdateNotificationHelper.CHANNEL_ID,
            UpdateNotificationHelper.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for app updates"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return imageLoader
    }

    private companion object {
        const val TAG = "CRANK_APP"
    }
}
