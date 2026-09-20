package com.crank.music.service

import android.content.Intent
import androidx.media3.common.Player
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
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession.run {
            player.release()
            release()
        }
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
internal object CrankSessionCallback : MediaSession.Callback {
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
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
