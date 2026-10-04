package com.crank.music.service

import android.content.Intent
import android.os.Process
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var mediaSession: MediaSession

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession.player
        if ((!player.playWhenReady) || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            // Stop and clear before letting the service go. stopSelf() alone left the Media3 media
            // notification posted with no service behind it: the panel stayed in the shade, and
            // tapping it reopened the app onto a session that had already been torn down.
            player.stop()
            player.clearMediaItems()
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // The player and the session are process-scoped @Singleton objects provided by
        // PlayerModule, and PlayerViewModel keeps using them after this service goes away.
        // Releasing them here — as this used to — destroyed the singletons for the rest of the
        // process: if the process survived a task swipe (it usually does), the next launch handed
        // the UI a released player, so play/pause was a no-op or threw. Media3's player belongs to
        // the app, not to this service; the OS reclaims it on process death, which is the only
        // point at which it is genuinely finished with.
        super.onDestroy()
    }
}

/**
 * Bridge from OS-level transport to the app's queue-aware playback.
 *
 * ExoPlayer holds exactly one media item — the queue lives in the
 * ViewModel's PlayQueue — so the session callback (wired in PlayerModule)
 * delegates skip requests here, and PlayerViewModel registers its
 * [playNext][com.crank.music.ui.viewmodel.PlayerViewModel.playNext] /
 * [playPrevious][com.crank.music.ui.viewmodel.PlayerViewModel.playPrevious]
 * as the handlers at startup.
 *
 * A plain object rather than an injected singleton so the session callback
 * needs no Hilt wiring of its own. Invoked on binder threads; handlers must
 * be thread-safe.
 */
object RemoteControlBridge {
    var onSkipToNext: (() -> Unit)? = null
    var onSkipToPrevious: (() -> Unit)? = null
}

/**
 * Advertises and routes OS transport (notification, lock screen, Bluetooth,
 * wired headset).
 *
 * The skip commands are advertised because the single-item player would
 * otherwise hide the next/previous buttons entirely; the interception routes
 * them to the queue-aware handlers on [RemoteControlBridge], since ExoPlayer's
 * own queue is empty. Play, pause and seek fall through to the single media
 * item, which honours them natively.
 */
@OptIn(UnstableApi::class)
internal object CrankSessionCallback : MediaSession.Callback {

    private const val TAG = "CRANK_SESSION"

    /**
     * Platform packages allowed to drive playback.
     *
     * Kept as an explicit allowlist rather than a "not a third-party app" heuristic, so an OEM
     * that ships its own media-control surface can be added deliberately instead of the check
     * silently passing everything that is not obviously foreign.
     */
    private val TRUSTED_SYSTEM_PACKAGES = setOf(
        "com.android.systemui",
        "com.android.bluetooth",
        "com.google.android.projection.gearhead",
        "com.google.android.apps.automotive.mediacenter",
        "android",
    )

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        if (!isTrustedController(session, controller)) {
            Log.w(
                TAG,
                "Rejecting media controller from '${controller.packageName}' " +
                    "(uid ${controller.uid})"
            )
            return MediaSession.ConnectionResult.reject()
        }

        val playerCommands = MediaSession.ConnectionResult
            .DEFAULT_PLAYER_COMMANDS
            .buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .build()
        return MediaSession.ConnectionResult.accept(
            MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS,
            playerCommands
        )
    }

    /**
     * Decides whether a connecting controller is allowed to drive playback.
     *
     * The service is exported (the system has to bind it), so without this check *any* app on the
     * device could connect and issue transport commands — previous/next, play, pause, seek. The
     * accepted set is deliberately narrow:
     *
     * - controllers from this app's own uid;
     * - Media3's own media-notification controller, Android Auto and the Auto companion;
     * - the platform system-ui packages, which host the media output switcher and media controls.
     *
     * Bluetooth and headset media buttons do not need a controller connection — the platform routes
     * `ACTION_MEDIA_BUTTON` to the active session — so excluding third-party apps does not disable
     * headset controls.
     */
    private fun isTrustedController(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): Boolean {
        if (controller.uid == Process.myUid()) return true
        if (session.isMediaNotificationController(controller)) return true
        if (session.isAutomotiveController(controller)) return true
        if (session.isAutoCompanionController(controller)) return true
        return controller.packageName in TRUSTED_SYSTEM_PACKAGES
    }

    // Still the only place a client's skip request can be routed to this app's queue: ExoPlayer
    // holds one media item, so its own seekToNext is a no-op. Suppressed because Media3 marks the
    // callback deprecated (it prefers controller-side command handling) but has not replaced it for
    // this single-item case — the callback is still invoked.
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onPlayerCommandRequest(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        playerCommand: Int,
    ): Int {
        // Returns the granted command code (not a result code): granting
        // as-is after the queue-aware handler has run.
        when (playerCommand) {
            Player.COMMAND_SEEK_TO_NEXT -> RemoteControlBridge.onSkipToNext?.invoke()
            Player.COMMAND_SEEK_TO_PREVIOUS -> RemoteControlBridge.onSkipToPrevious?.invoke()
        }
        return playerCommand
    }
}
