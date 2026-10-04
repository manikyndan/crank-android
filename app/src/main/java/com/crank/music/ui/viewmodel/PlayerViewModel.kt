package com.crank.music.ui.viewmodel

import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.crank.music.data.local.QueueItemEntity
import com.crank.music.data.local.SettingsStore
import com.crank.music.data.local.SongDao
import com.crank.music.data.local.toEntity
import com.crank.music.data.remote.StreamResolver
import com.crank.music.domain.model.DownloadState
import com.crank.music.domain.model.DownloadStateResolver
import com.crank.music.domain.model.Song
import com.crank.music.ui.theme.isLight
import com.crank.music.domain.repository.DownloadRepository
import com.crank.music.domain.repository.MusicRepository
import com.crank.music.service.RemoteControlBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * The lyrics for the current track.
 *
 * [Success.timing] carries whether the timestamps are real or estimated, so the display can
 * avoid claiming to know the current line when it does not. See [LyricsTiming].
 */
sealed class LyricsState {
    object Loading : LyricsState()

    data class Success(
        val lines: List<LyricsLine>,
        val timing: LyricsTiming = LyricsTiming.SYNCED,
    ) : LyricsState()

    object Unavailable : LyricsState()
}

data class PlayerState(
    val isPlaying: Boolean = false,
    val currentSong: Song? = null,
    val progress: Long = 0L,
    val duration: Long = 0L,
    val audioSessionId: Int? = null,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleModeEnabled: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val playbackPitch: Float = 1.0f,
    val sleepTimerMinutes: Int = 0,
    val remainingSleepTimeMs: Long = 0L,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@OptIn(UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val player: ExoPlayer,
    private val musicRepository: MusicRepository,
    private val songDao: SongDao,
    private val downloadRepository: DownloadRepository,
    private val audioEffects: com.crank.music.data.audio.AudioEffectsController,
    private val themePreference: com.crank.music.ui.theme.ThemePreference,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    /**
     * The current theme mode, so the player's overflow menu can label its Dark Mode row.
     *
     * Reuses the same [ThemePreference] the theme root reads, rather than a second copy — the
     * menu and the applied theme can then never disagree.
     */
    val themeMode: StateFlow<com.crank.music.ui.viewmodel.ThemeMode?> = themePreference.mode

    /** Flips between Light and Dark. `AUTO`/`OLED` resolve to a light/dark starting point. */
    fun toggleDarkMode(systemInDarkTheme: Boolean) {
        val current = themePreference.mode.value
        val isDarkNow = current?.isLight(systemInDarkTheme)?.not() ?: systemInDarkTheme
        viewModelScope.launch {
            themePreference.setMode(
                if (isDarkNow) ThemeMode.LIGHT else ThemeMode.DARK,
            )
        }
    }

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    /**
     * The upcoming songs, as shown on the queue screen.
     *
     * Mirrors [playQueue] rather than being the source of truth: keeping one authoritative object
     * and publishing a view of it is what makes the queue impossible to desynchronise.
     */
    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    /**
     * Replaces the queue and republishes it.
     *
     * Every mutation goes through here. Assigning `playQueue` directly and forgetting to update
     * `_queue` is the desynchronisation this method exists to make impossible.
     */
    private fun setQueue(updated: PlayQueue) {
        playQueue = updated
        _queue.value = updated.upNext
    }

    /**
     * Lyrics for the current track, owned by [LyricsSession].
     *
     * The two network sources, the exact-before-fuzzy ordering and the stale-response guard all live
     * there now; this is the flow the UI reads, unchanged.
     */
    private val lyricsSession = LyricsSession(
        scope = viewModelScope,
        musicRepository = musicRepository,
        currentSongId = { _playerState.value.currentSong?.id },
    )

    val lyricsState: StateFlow<LyricsState> = lyricsSession.state

    private val _history = MutableStateFlow<List<Song>>(emptyList())
    val history: StateFlow<List<Song>> = _history.asStateFlow()

    private val _currentSongDownloadState = MutableStateFlow(DownloadState.IDLE)
    val currentSongDownloadState: StateFlow<DownloadState> = _currentSongDownloadState.asStateFlow()

    /**
     * Live progress of the current song's download, as a fraction of the known total.
     *
     * **null means the total size is not known yet** — Media3 reports `contentLength = -1` until
     * the server answers, which is the first moment of every transfer. The indicator renders
     * that as an indeterminate sweep rather than a ring frozen at 0%, because a ring sitting at
     * zero looks like a stall.
     *
     * Deliberately a separate flow from [currentSongDownloadState]. The state changes a handful
     * of times across a whole download, while this moves continuously; publishing both through
     * one flow would make each progress sample indistinguishable from a state change and force
     * the UI to re-evaluate the state machine on every one of them.
     */
    private val _currentSongDownloadFraction = MutableStateFlow<Float?>(null)
    val currentSongDownloadFraction: StateFlow<Float?> = _currentSongDownloadFraction.asStateFlow()

    private val _isCurrentLiked = MutableStateFlow(false)
    val isCurrentLiked: StateFlow<Boolean> = _isCurrentLiked.asStateFlow()

    private val playHistory = PlaybackHistory()

    /**
     * Pauses playback when the user's chosen interval elapses.
     *
     * Extracted from this class; see [SleepTimerController]. Its state is mirrored into
     * [_playerState] by a collector in `init`, so `PlayerState.sleepTimerMinutes` /
     * `remainingSleepTimeMs` remain the single thing the UI reads — the screens did not change.
     */
    private val sleepTimer = SleepTimerController(viewModelScope)

    /**
     * Listening-history rows and the clock that measures how long each song was actually heard.
     *
     * Extracted from this class; see [HistoryRecorder] for the three invariants it keeps. The
     * recorder owns the row writes, the privacy gate and the tick accumulator; this class only
     * tells it when a play starts and when the player is ticking.
     */
    private val historyRecorder = HistoryRecorder(
        songDao = songDao,
        settingsStore = settingsStore,
        scope = viewModelScope
    )

    private var positionSaveJob: Job? = null

    private val playRequestCounter = AtomicLong(0)

    /**
     * Which song the user most recently asked to hear.
     *
     * ## Why this is not redundant with [playRequestCounter]
     *
     * [playRequestCounter] only guards the *error reporting* inside [playSong]: it stops a stale
     * attempt from writing a error message, but it does nothing to stop a stale attempt from
     * reaching the player. Resolution is the slow part — a cold cascade can take seconds, and
     * the first caller to finish wins the `setMediaItem` call. Tap song A, tap song B a moment
     * later, and A resolving second would play **A while the UI shows B**. That is the
     * "wrong song plays" report.
     *
     * A monotonically increasing token is used rather than comparing song ids, because the same
     * song can legitimately be requested twice; comparing ids would treat the second request as
     * a duplicate of the first and let the older, slower resolve land.
     *
     * Written on the main thread (all play entry points are) and read inside coroutines, so it is
     * `@Volatile` for visibility rather than for atomicity.
     */
    @Volatile
    private var activePlayToken = 0L

    /**
     * Play-attempt tokens whose 403 retry has already been spent.
     *
     * ## Why not "the song id we already retried"
     *
     * A per-song-id guard looks correct but breaks repeat-one. The song never changes, so the id
     * guard blocks the retry on the *second* loop of the same track — and repeat-one is exactly
     * where an expiring URL is most likely to bite, because the track is held on screen far
     * longer than one pass. Tracking the attempt token instead means each new play gets its own
     * single retry, which is the intended budget.
     *
     * The set is bounded by clearing it whenever it grows past [MAX_RETRY_TOKENS]; stale tokens
     * are worthless by then because the counter only moves forward.
     */
    private val failedRetryTokens = mutableSetOf<Long>()

    /**
     * The play-attempt token that the player's current error belongs to.
     *
     * Updated when media is handed to the player, so the retry guard can tell which attempt
     * actually failed rather than guessing from state that may have moved on.
     */
    @Volatile
    private var lastErrorToken = -1L

    /**
     * The queue, as a single object with stated invariants.
     *
     * Replaces the previous trio of `_queue` / `originalQueue` / `shuffledIndices`, which had to
     * be kept consistent by hand at every call site and were not — see [PlayQueue] for the
     * specific defects that caused.
     */
    private var playQueue = PlayQueue()

    /**
     * How many tracks in a row have been skipped because they failed to load.
     *
     * Bounds the auto-skip so a queue where nothing resolves cannot spin forever — see
     * [handlePlaybackFailure]. Reset to zero as soon as a track plays successfully.
     */
    private var consecutiveAutoSkips = 0

    /**
     * Wake counter for the progress tracker.
     *
     * Bumped by the player listener on any change that can move the progress bar or the play state.
     * While playback is stopped the tracker waits on this instead of polling, so an idle app is not
     * doing 2 Hz work in a coroutine that outlives every screen.
     */
    private val refreshSignal = MutableStateFlow(0L)

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playerState.update { it.copy(isPlaying = isPlaying) }
            // Wakes the progress tracker immediately out of its idle wait, so the bar starts moving
            // on the tick the user presses play rather than up to a poll interval later.
            wakeProgressTracker()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            // A seek, a repeat, or a skipped track while paused must still land in the UI, and the
            // tracker is parked when nothing is playing.
            wakeProgressTracker()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val song = mediaItem?.toSong()
            if (song != null) {
                _playerState.update {
                    it.copy(
                        currentSong = song,
                        duration = player.duration.coerceAtLeast(0L)
                    )
                }
                lyricsSession.load(song)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    val knownDuration = player.duration.coerceAtLeast(0L)
                    _playerState.update {
                        it.copy(
                            duration = knownDuration,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                    // The player is the first place the true running time is known. Rows that
                    // reached the library without one get it recorded here, which is what replaces
                    // the "—" in Liked Songs with a real duration.
                    persistKnownDuration(knownDuration)
                }
                Player.STATE_BUFFERING -> {
                    _playerState.update { it.copy(isLoading = true) }
                }
                Player.STATE_IDLE -> {
                    // Nothing is loading once the player is stopped or has no media. Without
                    // this case the spinner could stay up after playback stopped, because no
                    // other state transition clears it.
                    _playerState.update { it.copy(isLoading = false) }
                }
                Player.STATE_ENDED -> {
                    _playerState.update { it.copy(isLoading = false) }
                    handleSongEnd()
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("CRANK_PLAYER", "ExoPlayer playback error: ${error.message}", error)
            _playerState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Playback failed: ${error.message ?: "Unknown error"}"
                )
            }

            // A 403 from YouTube means the signed URL we were handed has expired
            // (or been rejected). The track is fine — the link is not — so the
            // right response is one re-resolve, not a skip. Skipping is what the
            // user sees as "the app jumped to the next song for no reason".
            //
            // The budget is per *attempt*, not per song id: with repeat-one, the same song is
            // legitimately played over and over, and a per-id guard would let the retry fire
            // again on every loop. The request token is bumped on each play, so the guard below
            // tracks "have we already retried the attempt that is currently on the player".
            val song = _playerState.value.currentSong
            if (song != null && isExpiredUrlError(error) && failedRetryTokens.add(lastErrorToken)) {
                Log.d("CRANK_PLAYER", "403 on '${song.title}' — re-resolving once")
                viewModelScope.launch {
                    // A retry is still a fresh play attempt for supersede purposes, so it claims
                    // a new token. Otherwise a resolve already in flight would be treated as
                    // newer and this retry would be discarded — leaving the track dead.
                    val token = playRequestCounter.incrementAndGet()
                    activePlayToken = token
                    _playerState.update { it.copy(isLoading = true) }
                    try {
                        resolveAndPlay(
                            song,
                            forceRefresh = true,
                            reason = "stream URL expired",
                            token = token,
                        )
                    } catch (e: Exception) {
                        Log.e("CRANK_PLAYER", "Re-resolve failed for '${song.title}': ${e.message}")
                        if (!isSuperseded(token)) {
                            _playerState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage =
                                        "Stream expired and could not be refreshed: " +
                                            (e.message ?: "unknown error"),
                                )
                            }
                        }
                    }
                }
                return
            }

            viewModelScope.launch {
                delay(1000)
                playNext()
            }
        }

        // Deliberately NOT mirrored from ExoPlayer. The player holds exactly one MediaItem, so its
        // own repeatMode can only loop that single item forever — it can never advance the app's
        // queue, and while it loops, STATE_ENDED never fires, so handleSongEnd() is never reached.
        // Repeat is owned by `_playerState.repeatMode` (see toggleRepeatMode); mirroring ExoPlayer
        // here would immediately overwrite the app's value with OFF.

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _playerState.update { it.copy(shuffleModeEnabled = shuffleModeEnabled) }
        }
    }

    init {
        player.addListener(playerListener)
        // The session (built in PlayerModule) delegates OS transport here.
        // playNext/playPrevious post work to viewModelScope and touch the
        // thread-safe player, so binder-thread calls from the notification,
        // lock screen or Bluetooth are fine.
        RemoteControlBridge.onSkipToNext = { playNext() }
        RemoteControlBridge.onSkipToPrevious = { playPrevious() }
        startProgressTracker()
        loadHistory()
        restorePlaybackSpeed()
        startPositionSaving()
        observeCurrentSongDownload()
        observeCurrentSongDownloadProgress()
        observeCurrentSongLiked()
        // Mirrors the extracted sleep timer into the state the UI already reads, so no screen had to
        // learn about SleepTimerController. Started before restoreLastPlayback() so the first
        // emissions are not lost.
        viewModelScope.launch {
            sleepTimer.state.collect { timer ->
                _playerState.update {
                    it.copy(
                        sleepTimerMinutes = timer.totalMinutes,
                        remainingSleepTimeMs = timer.remainingMs
                    )
                }
            }
        }
        // restoreLastPlayback() owns the queue when a saved session exists; it
        // delegates to loadQueueFromRoom() otherwise. Running them concurrently
        // would let the two sources race for _queue.value.
        restoreLastPlayback()
    }

    private fun restoreLastPlayback() {
        viewModelScope.launch {
            try {
                val savedState = songDao.getPlaybackState()

                // No saved session: the queue table is still worth reading, since
                // the user may have queued tracks without ever pressing play.
                if (savedState == null) {
                    loadQueueFromRoom()
                    return@launch
                }

                val savedSong = Song(
                    id = savedState.songId,
                    title = savedState.songTitle.ifBlank { "Unknown Track" },
                    artistName = savedState.songArtist.ifBlank { "Unknown Artist" },
                    albumId = savedState.songAlbumId,
                    durationMs = savedState.songDurationMs,
                    artworkUrl = savedState.songArtwork,
                    isLocal = false,
                    streamUrl = savedState.songStreamUrl
                )

                // Clamp the restored position to the track's own length so a stale
                // or corrupt value can never seek past the end of the media.
                val restoredPositionMs = savedState.positionMs
                    .coerceAtLeast(0L)
                    .let { pos ->
                        val knownDuration = savedState.songDurationMs
                        if (knownDuration > 0L) pos.coerceAtMost(knownDuration) else pos
                    }

                _playerState.update {
                    it.copy(
                        currentSong = savedSong,
                        progress = restoredPositionMs,
                        duration = savedState.songDurationMs.coerceAtLeast(0L),
                        repeatMode = savedState.repeatMode,
                        shuffleModeEnabled = savedState.shuffleEnabled
                    )
                }

                // ExoPlayer's own repeatMode is left OFF: the restored repeat mode is app state,
                // and pushing REPEAT_MODE_ALL into a single-item player would loop that item and
                // never emit STATE_ENDED, so the restored queue could never advance.
                player.shuffleModeEnabled = savedState.shuffleEnabled

                // Restore the actual media item so the user can hit play and
                // resume in place. Only do this when we have something playable;
                // neither a video ID nor an unresolved marker is enough to build a
                // MediaItem, and handing the marker to ExoPlayer would surface a
                // raw player error instead of the metadata-only state we want.
                if (savedState.songStreamUrl.isNotBlank() && savedSong.isPlayable) {
                    val mediaItem = MediaItem.Builder()
                        .setMediaId(savedSong.id)
                        .setUri(savedState.songStreamUrl)
                        // Must match the key the download was written under
                        // (DownloadRepositoryImpl.downloadSong). Without it, Media3 keys the cache
                        // on the resolved URL string — which carries expiring signature
                        // parameters, so it differs on every resolve — and a downloaded track
                        // never hits its own cached bytes.
                        .setCustomCacheKey(savedSong.id)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(savedSong.title)
                                .setArtist(savedSong.artistName)
                                // The album id is often an opaque handle, not a name —
                                // publishing it would print gibberish under the title
                                // on the lock screen. Null omits the line instead.
                                .setAlbumTitle(
                                    savedSong.albumId?.takeIf {
                                        it.isNotBlank() && !looksLikeOpaqueId(it)
                                    }
                                )
                                .setArtworkUri(
                                    if (savedSong.artworkUrl.isNotBlank()) savedSong.artworkUrl.toUri() else null
                                )
                                .build()
                        )
                        .build()

                    player.setMediaItem(mediaItem)
                    player.prepare()
                    // seekTo() before any play() call positions the player; the
                    // track stays paused so we don't surprise the user on launch.
                    player.seekTo(restoredPositionMs)
                    Log.d(
                        "CRANK_PLAYER",
                        "Restored position ${restoredPositionMs}ms for '${savedSong.title}'"
                    )
                } else {
                    Log.d("CRANK_PLAYER", "Restored metadata only (no streamUrl) for '${savedSong.title}'")
                }

                // Prefer the queue snapshot stored alongside the playback state,
                // but fall back to the queue table when the ids no longer resolve
                // (e.g. the song rows were pruned after the snapshot was written).
                val queueIds = decodeQueueIds(savedState.queueJson)
                val queueSongs = queueIds.mapNotNull { id ->
                    val song = songDao.getSongById(id) ?: return@mapNotNull null
                    Song(
                        id = song.id,
                        title = song.title,
                        artistName = song.artistName,
                        albumId = song.albumId,
                        durationMs = song.durationMs,
                        artworkUrl = song.artworkUrl,
                        isLocal = song.isLocal,
                        streamUrl = song.streamUrl
                    )
                }
                if (queueSongs.isNotEmpty()) {
                    setQueue(PlayQueue().restored(queueSongs))
                } else {
                    loadQueueFromRoom()
                }

                Log.d("CRANK_PLAYER", "Restored: ${savedSong.title} by ${savedSong.artistName}, queue: ${queueSongs.size} songs")
            } catch (e: Exception) {
                Log.e("CRANK_PLAYER", "Failed to restore playback: ${e.message}")
            }
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            try {
                songDao.getHistory().collect { entities ->
                    _history.value = entities.map { entity ->
                        Song(
                            id = entity.songId,
                            title = entity.title,
                            artistName = entity.artistName,
                            albumId = entity.albumId,
                            durationMs = entity.durationMs,
                            artworkUrl = entity.artworkUrl,
                            isLocal = false,
                            streamUrl = entity.streamUrl
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("CRANK_PLAYER", "Failed to load history: ${e.message}")
            }
        }
    }

    private fun loadQueueFromRoom() {
        viewModelScope.launch {
            try {
                songDao.getQueueItems().collect { queueEntities ->
                    if (_queue.value.isNotEmpty()) return@collect
                    val songs = queueEntities.sortedBy { it.queueOrder }.map { entity ->
                        Song(
                            id = entity.songId,
                            title = entity.title,
                            artistName = entity.artistName,
                            albumId = entity.albumId,
                            durationMs = entity.durationMs,
                            artworkUrl = entity.artworkUrl,
                            streamUrl = entity.streamUrl,
                            isLocal = false
                        )
                    }
                    setQueue(PlayQueue().restored(songs))
                }
            } catch (e: Exception) {
                Log.e("CRANK_PLAYER", "Failed to load queue: ${e.message}")
            }
        }
    }

    /**
     * Publishes playback progress and measures listening time.
     *
     * The tick doubles as the listening clock: each tick while the player is actually playing adds
     * one interval to the current song's accumulator. That is the only definition of "listened"
     * that survives a skip, a pause or a seek — using the track's duration instead is what made the
     * statistics overstate how much the user had heard.
     *
     * The accumulator is flushed to the database every [TICKS_PER_FLUSH] ticks rather than every
     * tick, and immediately whenever playback pauses or the track changes, so at most one interval
     * of listening time is lost when the app is killed.
     */
    private fun startProgressTracker() {
        viewModelScope.launch {
            var ticksSinceFlush = 0
            var wasPlaying = false
            while (isActive) {
                val dur = player.duration.coerceAtLeast(0L)
                val pos = player.currentPosition.coerceAtLeast(0L)
                val isPlayingNow = player.isPlaying
                val sessionId = if (player.audioSessionId != 0 && player.audioSessionId != -1) player.audioSessionId else null

                // Bind the process-scoped audio effects to the session as soon as one exists, so a
                // saved equalizer preset applies to playback even if the user never opens the
                // equalizer screen this session. No-op when the session is unchanged.
                if (sessionId != null) audioEffects.attach(sessionId)

                if (isPlayingNow) {
                    historyRecorder.accumulateTick(PROGRESS_TICK_MS)
                    ticksSinceFlush++
                    if (ticksSinceFlush >= TICKS_PER_FLUSH) {
                        ticksSinceFlush = 0
                        historyRecorder.flushListeningTime()
                    }
                } else if (wasPlaying) {
                    // Playback just paused or stopped: persist what has been accumulated so far.
                    ticksSinceFlush = 0
                    historyRecorder.flushListeningTime()
                }
                wasPlaying = isPlayingNow

                _playerState.update {
                    it.copy(
                        isPlaying = isPlayingNow,
                        progress = pos,
                        duration = if (dur > 0L) dur else it.duration,
                        audioSessionId = sessionId ?: it.audioSessionId,
                        // repeatMode is intentionally not refreshed from the player: it is
                        // app-owned state (the player is always REPEAT_MODE_OFF), so copying it
                        // here would reset the user's choice every 500ms.
                        shuffleModeEnabled = player.shuffleModeEnabled
                    )
                }

                if (isPlayingNow) {
                    // A progress bar needs a cadence, so this is a poll — but only while audio is
                    // actually moving.
                    delay(PROGRESS_TICK_MS)
                } else {
                    // Nothing is playing, so nothing on screen is changing. This used to keep
                    // ticking anyway, which meant a ViewModel-scoped 500 ms coroutine ran for the
                    // entire life of the app — including while the user was on another screen with
                    // playback stopped — waking the process twice a second to read three values that
                    // could not have changed. Park here until the player reports a state change, with
                    // a slow timeout as a safety net in case a listener callback is missed.
                    val seen = refreshSignal.value
                    withTimeoutOrNull(IDLE_REFRESH_MS) {
                        refreshSignal.first { it != seen }
                    }
                }
            }
        }
    }

    /**
     * Increments the tracker's wake counter, releasing it from its idle wait.
     *
     * Safe to call from a player listener, which ExoPlayer invokes on the application's main thread.
     */
    private fun wakeProgressTracker() {
        refreshSignal.value += 1
    }

    private fun startPositionSaving() {
        positionSaveJob = viewModelScope.launch {
            while (isActive) {
                delay(5000L)
                saveCurrentPlaybackPosition()
            }
        }
    }

    /**
     * Download state for the current song, under a strict rule:
     *
     * - tick: engine reports COMPLETED, or the database row says fully saved;
     * - spinner: engine is actively working AND (tapped recently this session
     *   OR bytes are provably on disk and growing);
     * - arrow: everything else, including stale queued entries with zero
     *   bytes (expired URL retry loops) and FAILED (tap retries fresh).
     *
     * A 4s poll re-resolves while the track is current so a stalled spinner
     * decays back to the arrow instead of spinning forever. `collectLatest`
     * restarts everything per track, so state can never stick to the
     * previous song.
     */
    private val downloadTapTimes = mutableMapOf<String, Long>()
    private val downloadLastBytes = mutableMapOf<String, Long>()

    private fun observeCurrentSongDownload() {
        viewModelScope.launch {
            _playerState.map { it.currentSong?.id }
                .distinctUntilChanged()
                .collectLatest { songId ->
                    if (songId == null) {
                        _currentSongDownloadState.value = DownloadState.IDLE
                        return@collectLatest
                    }
                    _currentSongDownloadState.value = resolveDownloadState(songId)
                    kotlinx.coroutines.coroutineScope {
                        launch {
                            try {
                                downloadRepository.getDownloadState(songId).collect {
                                    _currentSongDownloadState.value =
                                        resolveDownloadState(songId)
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e("CRANK_PLAYER", "Failed to observe download state: ${e.message}")
                            }
                        }
                        launch {
                            while (true) {
                                kotlinx.coroutines.delay(4000)
                                _currentSongDownloadState.value =
                                    resolveDownloadState(songId)
                            }
                        }
                    }
                }
        }
    }

    /**
     * Publishes the current song's download fraction as it moves.
     *
     * Separate from [observeCurrentSongDownload] on purpose. That one owns the *state* and
     * re-derives it on a 4s poll, which is the right cadence for a value that changes a few
     * times per download. A progress ring needs the transfer's own cadence, so this subscribes
     * to the engine's progress stream instead and never touches the state machine — the two
     * cannot fight over the same flow.
     *
     * `collectLatest` keyed on the song id is what makes skipping safe: the previous song's
     * collection is cancelled the instant the id changes, so its remaining ticks can never land
     * on the new song. The fraction is also cleared before the new subscription starts, because
     * leaving the old value in place for even one frame would show the new song already part
     * downloaded.
     */
    private fun observeCurrentSongDownloadProgress() {
        viewModelScope.launch {
            _playerState.map { it.currentSong?.id }
                .distinctUntilChanged()
                .collectLatest { songId ->
                    _currentSongDownloadFraction.value = null
                    if (songId == null) return@collectLatest
                    try {
                        downloadRepository.getDownloadProgress(songId).collect { progress ->
                            // The rule for what these numbers mean lives on the model, so this
                            // stays a plain pass-through and both surfaces get the same answer.
                            _currentSongDownloadFraction.value = progress?.uiFraction
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("CRANK_PLAYER", "Failed to observe download progress: ${e.message}")
                    }
                }
        }
    }

    /**
     * Reads the two sources of truth and hands the decision to [DownloadStateResolver].
     *
     * This method's only job now is the I/O: fetch the engine snapshot and the library row,
     * then record the byte count for the next pass. The precedence rules — in particular that
     * a song already on disk always reports COMPLETED — live in the resolver, where they are
     * covered by unit tests instead of being re-derived here by eye.
     */
    private suspend fun resolveDownloadState(songId: String): DownloadState {
        return try {
            val snapshot = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                downloadRepository.getDownloadSnapshot(songId)
            }
            // The durable flag. `markComplete` is the only writer that sets this true, and it
            // runs when the bytes are fully on disk; a fresh download resets it to false. So a
            // true here means "downloaded", not "requested" — see the note in DownloadRepositoryImpl.
            val savedLocal = try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    songDao.getSongById(songId)?.isLocal == true
                }
            } catch (e: Exception) {
                false
            }

            val tappedAt = downloadTapTimes[songId] ?: 0L
            val tappedFresh = System.currentTimeMillis() - tappedAt < TAP_FRESH_WINDOW_MS

            // Read the previous count before overwriting it, so the resolver can tell a moving
            // transfer from one stalled on stale bytes.
            val previousBytes = downloadLastBytes[songId]
            snapshot?.let { downloadLastBytes[songId] = it.downloadedBytes }

            DownloadStateResolver.resolve(
                managerState = snapshot?.state,
                downloadedOnDisk = savedLocal,
                engineTransferring = snapshot?.isTransferring == true,
                tappedRecently = tappedFresh,
                previousBytes = previousBytes,
                currentBytes = snapshot?.downloadedBytes ?: 0L,
            )
        } catch (e: Exception) {
            Log.e("CRANK_PLAYER", "Failed to resolve download state: ${e.message}")
            DownloadState.IDLE
        }
    }

    /**
     * Tracks whether the current song is in Library → Liked Music.
     *
     * Read per track change (not as a Flow) because likes are toggled from
     * several screens; a stale cached value would show the wrong heart.
     * [toggleLikeCurrentSong] refreshes it after every write.
     */
    private fun observeCurrentSongLiked() {
        viewModelScope.launch {
            var lastId: String? = null
            _playerState.collect { state ->
                val song = state.currentSong ?: run {
                    lastId = null
                    _isCurrentLiked.value = false
                    return@collect
                }
                if (song.id == lastId) return@collect
                lastId = song.id
                _isCurrentLiked.value = try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        songDao.getSongById(song.id)?.isLiked == true
                    }
                } catch (e: Exception) {
                    Log.e("CRANK_PLAYER", "Failed to read liked state: ${e.message}")
                    false
                }
            }
        }
    }

    /** Toggles the current song in Library → Liked Music. */
    fun toggleLikeCurrentSong() {
        val song = _playerState.value.currentSong ?: return
        viewModelScope.launch {
            try {
                val liked = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val existing = songDao.getSongById(song.id)
                    if (existing != null) {
                        songDao.updateSong(existing.copy(isLiked = !existing.isLiked))
                        !existing.isLiked
                    } else {
                        songDao.insertSong(song.toEntity(isLiked = true))
                        true
                    }
                }
                _isCurrentLiked.value = liked
            } catch (e: Exception) {
                Log.e("CRANK_PLAYER", "Failed to toggle like: ${e.message}")
            }
        }
    }

    /**
     * Records the real running time on any stored row that arrived without one.
     *
     * Tracks from the Home feed are built without a parsed duration, so liking one straight from
     * there saves it with `durationMs = 0` and the Liked Songs row renders "—" instead of "3:45".
     * The player learns the true length on prepare — the first moment it is genuinely known — so
     * this backfills rows already in the library as well as new ones, from real playback rather
     * than an invented default.
     *
     * Only fills a blank: an existing non-zero duration is left alone, so a value the parser
     * already got right is never overwritten by a re-resolve.
     */
    private fun persistKnownDuration(durationMs: Long) {
        if (durationMs <= 0L) return
        val song = _playerState.value.currentSong ?: return
        if (song.durationMs > 0L) return

        viewModelScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val existing = songDao.getSongById(song.id)
                    if (existing != null && existing.durationMs <= 0L) {
                        songDao.updateSong(existing.copy(durationMs = durationMs))
                    }
                }
                // Keeps the on-screen track in step with the row, so the duration shows without
                // waiting for the liked-list query to re-emit.
                _playerState.update {
                    it.copy(currentSong = it.currentSong?.copy(durationMs = durationMs))
                }
            } catch (e: Exception) {
                Log.d("CRANK_PLAYER", "Could not record duration for ${song.id}: ${e.message}")
            }
        }
    }

    private fun saveCurrentPlaybackPosition() {
        val song = _playerState.value.currentSong ?: return
        viewModelScope.launch {
            try {
                songDao.clearPlaybackState()
                songDao.insertPlaybackState(
                    com.crank.music.data.local.PlaybackPositionEntity(
                        songId = song.id,
                        songTitle = song.title,
                        songArtist = song.artistName,
                        songArtwork = song.artworkUrl,
                        songAlbumId = song.albumId,
                        songDurationMs = song.durationMs,
                        songStreamUrl = song.streamUrl,
                        positionMs = player.currentPosition.coerceAtLeast(0),
                        queueJson = encodeQueueIds(PlayQueue.persistableSongs(playQueue)),
                        repeatMode = _playerState.value.repeatMode,
                        shuffleEnabled = player.shuffleModeEnabled
                    )
                )
            } catch (e: Exception) {
                Log.e("CRANK_PLAYER", "Failed to save position: ${e.message}")
            }
        }
    }

    private fun handleSongEnd() {
        val currentSong = _playerState.value.currentSong ?: return

        // History is recorded once, when the track actually starts (see resolveAndPlay). Recording
        // it again here only rewrote `playedAt` to the moment the song *finished*, which made
        // "recently played" order by end time and double-wrote every track — it was invisible only
        // because the history primary key is the song id.

        // Reads the app's own repeat mode, not ExoPlayer's: `player.repeatMode` is always OFF by
        // design (see toggleRepeatMode), so branching on it here would have made Repeat-One
        // unreachable as well.
        when (_playerState.value.repeatMode) {
            Player.REPEAT_MODE_ONE -> {
                player.seekTo(0)
                player.play()
            }
            else -> {
                playNext()
            }
        }
    }

    /**
     * Turns a [Song] into a playable URL.
     *
     * Returns `null` when the song has no playable source at all, so callers can
     * report a specific reason rather than a generic failure.
     *
     * A [Song.streamUrl] that is a *stable* absolute URL is passed through
     * untouched. A YouTube URL is not stable: it carries an `expire` parameter
     * and turns into a hard HTTP 403 once that passes, so a stale one is always
     * re-resolved against `song.id` rather than replayed. That is what made
     * resuming a saved session fail: the persisted URL was days old, the request
     * came back 403, and the player skipped the track.
     *
     * Throws whatever the resolver throws.
     */
    private suspend fun resolvePlayableUrl(song: Song): String? {
        if (!song.isPlayable) return null

        val streamUrl = song.streamUrl
        if (StreamResolver.isDirectlyPlayable(streamUrl)) {
            return streamUrl.takeIf { it.isNotBlank() }
        }

        if (streamUrl.startsWith("http")) {
            Log.d(
                "CRANK_PLAYER",
                "Discarding stale stream URL for '${song.title}' and re-resolving ${song.id}"
            )
        }

        // song.id is authoritative: even when streamUrl held a URL, that URL is
        // the thing we just rejected, so the identifier is the only useful input.
        val streamData = musicRepository.getSongStreamUrl(song.id, song.title, song.artistName)
        return streamData.url.takeIf { it.isNotBlank() }
    }

    companion object {
        /** Progress-publish interval, in milliseconds. Also the listening-clock resolution. */
        private const val PROGRESS_TICK_MS = 500L

        /**
         * Longest the idle progress tracker will wait before checking again.
         *
         * Only a backstop: the listener wakes it as soon as anything actually happens. One second
         * keeps a paused seek from showing a stale time for noticeably long without the 2 Hz poll the
         * tracker used to run for the whole life of the process.
         */
        private const val IDLE_REFRESH_MS = 1_000L

        /**
         * Ticks between listening-time writes while playing.
         *
         * Thirty ticks is 15 seconds: frequent enough that a killed process loses at most one
         * interval, rare enough that the statistics tables are not written every half-second.
         */
        private const val TICKS_PER_FLUSH = 30

        /**
         * How long after a tap a zero-byte download still earns the spinner.
         *
         * Covers slow starts; past this with no bytes the entry is treated as
         * stale and the arrow (retry) returns.
         */
        private const val TAP_FRESH_WINDOW_MS = 60_000L

        /**
         * How large [failedRetryTokens] may grow before it is cleared.
         *
         * Only a cap on memory. Tokens are monotonically increasing, so anything in the set is
         * already stale by the time the set is this large and clearing costs nothing.
         */
        private const val MAX_RETRY_TOKENS = 64

        /**
         * How many tracks in a row may be skipped automatically before playback gives up.
         *
         * Bounds [handlePlaybackFailure]: without it, a queue where every track fails would skip
         * forever. Three is enough to step over a run of bad entries but short enough that a
         * genuinely broken queue reports the error instead of churning.
         */
        private const val MAX_CONSECUTIVE_AUTO_SKIPS = 3

        /**
         * Separator for the queue snapshot.
         *
         * A newline rather than a comma. Song ids can contain commas — Media3 and some upstream
         * services use ids like `artist,track` — so a comma-joined snapshot splits into more
         * segments than there were songs, and the extra segments resolve to nothing. The result
         * is a queue that silently loses tracks across a restart. Newlines do not occur in ids.
         */
        private const val QUEUE_SEPARATOR = "\n"

        /**
         * Serialises the queue's song ids for the playback snapshot.
         *
         * Exposed for testing: this value round-trips through the database, and a separator that
         * collides with the data is invisible until a user restarts the app and finds tracks
         * missing.
         */
        fun encodeQueueIds(songs: List<Song>): String =
            songs.joinToString(QUEUE_SEPARATOR) { it.id }

        /**
         * Reverses [encodeQueueIds], tolerating a snapshot written by an older build.
         *
         * The comma fallback exists because the previous version joined on commas: without it, a
         * queue saved before the upgrade would be read as one unusable id. Such a snapshot is
         * only accepted when it does not also contain separators, so a legitimately new snapshot
         * whose ids happen to contain commas is never misread.
         */
        fun decodeQueueIds(snapshot: String): List<String> {
            if (snapshot.isBlank()) return emptyList()

            val separator =
                if (snapshot.contains(QUEUE_SEPARATOR)) QUEUE_SEPARATOR else LEGACY_QUEUE_SEPARATOR

            return snapshot.split(separator).filter { it.isNotBlank() }
        }

        /** The separator used by builds before the queue snapshot switched to newlines. */
        private const val LEGACY_QUEUE_SEPARATOR = ","
    }

    /**
     * True when [error] indicates the CDN rejected our signed media URL.
     *
     * Matched on the message rather than the cause type on purpose: the 403 is wrapped several
     * layers deep (`ExoPlaybackException` → `Source error` →
     * `HttpDataSource$InvalidResponseCodeException`), and walking that chain couples this class
     * to ExoPlayer's internal exception hierarchy. The message is stable across Media3 releases
     * and is what the log shows.
     */
    private fun isExpiredUrlError(error: PlaybackException): Boolean {
        val text = generateSequence(error) { it.cause as? PlaybackException }
            .joinToString(" ") { it.message.orEmpty() }
        return text.contains("403") || text.contains("InvalidResponseCode")
    }

    /**
     * Message shown when a recognised-but-unsourced track is played.
     *
     * Recognition returns metadata only — a title and an artist, with no audio attached. Saying
     * so plainly, and pointing at the search box, is far more useful than the resolver exception
     * this used to produce.
     */
    private fun unresolvedMessage(song: Song): String =
        "This is a recognised track with no audio source yet. " +
            "Search for \"${song.title}\" by ${song.artistName} to play it."

    /**
     * Resolves [song] and hands it to the player, reporting failures on the
     * player state.
     *
     * [forceRefresh] skips the "this URL still looks valid" shortcut, which is
     * what the 403-retry path needs: the URL just failed, so its own expiry
     * stamp cannot be trusted to tell us whether it works.
     *
     * ## The supersede check
     *
     * Resolution is slow and this method is called from several places. If the user asks for
     * another song while this one is still resolving, the correct behaviour is to abandon this
     * attempt silently — writing it to the player after the fact is what makes the app play a
     * track the user did not choose. Every write below is therefore guarded on [token] still
     * being the active request.
     */
    private suspend fun resolveAndPlay(
        song: Song,
        forceRefresh: Boolean = false,
        reason: String = "play",
        token: Long = activePlayToken,
    ) {
        if (!song.isPlayable) {
            Log.d("CRANK_PLAYER", "Song '${song.title}' has no playable source (recognised only)")
            if (isSuperseded(token)) return
            _playerState.update {
                it.copy(isLoading = false, errorMessage = unresolvedMessage(song))
            }
            return
        }

        val resolvedUrl = try {
            if (forceRefresh) {
                val streamData = musicRepository.getSongStreamUrl(song.id, song.title, song.artistName)
                streamData.url.takeIf { it.isNotBlank() }
            } else {
                resolvePlayableUrl(song)
            }
        } catch (e: Exception) {
            // Abandoning here rather than rethrowing: a superseded request that fails should
            // not surface an error for a song the user has already moved on from.
            if (isSuperseded(token)) {
                Log.d("CRANK_PLAYER", "Abandoning failed resolve for '${song.title}' (superseded)")
                return
            }
            throw e
        }

        // The resolve just cost us a network round trip; the user may have picked something else
        // in the meantime. Stopping here is the whole point of the token.
        if (isSuperseded(token)) {
            Log.d(
                "CRANK_PLAYER",
                "Discarding resolved stream for '${song.title}': superseded by a newer request",
            )
            return
        }

        if (resolvedUrl == null) {
            Log.e("CRANK_PLAYER", "Empty stream URL for ${song.title} (${song.id})")
            _playerState.update {
                it.copy(isLoading = false, errorMessage = "No audio stream available for: ${song.title}")
            }
            return
        }

        Log.d("CRANK_PLAYER", "$reason: ${song.title} (${song.id}) -> ${resolvedUrl.take(80)}...")

        val mediaItem = MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(resolvedUrl)
            // Keyed by song id, not by the resolved URL, so a downloaded track is served from the
            // bytes Media3 already stored under that same key. See downloadSong() in
            // DownloadRepositoryImpl for the other half of this contract.
            .setCustomCacheKey(song.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artistName)
                    .setAlbumTitle(
                        song.albumId?.takeIf { it.isNotBlank() && !looksLikeOpaqueId(it) }
                    )
                    .setArtworkUri(if (song.artworkUrl.isNotBlank()) song.artworkUrl.toUri() else null)
                    .build()
            )
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        // The track is genuinely starting (token verified current above), so
        // this — not queue insertion, not track end — is the history moment.
        historyRecorder.recordPlay(song)

        // Record which attempt is now on the player, so a later 403 can be attributed to it.
        lastErrorToken = token

        // A track started, so the auto-skip streak is broken. Without this, a long session with
        // occasional failures would eventually trip the bound even though most tracks play.
        consecutiveAutoSkips = 0

        _playerState.update {
            it.copy(
                currentSong = song.copy(streamUrl = resolvedUrl),
                isLoading = false,
                errorMessage = null
            )
        }
        lyricsSession.load(song)
        saveCurrentPlaybackPosition()
    }

    /**
     * True when a newer play request has been made since [token] was issued.
     *
     * @see activePlayToken
     */
    private fun isSuperseded(token: Long): Boolean = token != activePlayToken

    /**
     * Reports a track that could not be played, advancing the queue when it is safe to.
     *
     * A single unresolvable track must not end the session — a queue that stops dead on one bad
     * entry is the failure a listener actually notices, especially with the screen off and no way
     * to intervene. So the default response is to move on to the next track.
     *
     * It is bounded, though: a queue where *nothing* resolves would otherwise skip forever. After
     * [MAX_CONSECUTIVE_AUTO_SKIPS] failures in a row the error is surfaced and playback stops,
     * which is the honest outcome. The counter is reset the moment a track plays, so a long
     * session with occasional bad entries keeps skipping rather than tripping the bound.
     */
    private fun handlePlaybackFailure(token: Long, song: Song, cause: Throwable) {
        // A newer play request has already taken over; this failure is stale and must not act.
        if (isSuperseded(token)) return

        if (consecutiveAutoSkips < MAX_CONSECUTIVE_AUTO_SKIPS && playQueue.peekNext() != null) {
            consecutiveAutoSkips++
            Log.w(
                "CRANK_PLAYER",
                "Skipping '${song.title}' (${song.id}) after load failure " +
                    "($consecutiveAutoSkips/$MAX_CONSECUTIVE_AUTO_SKIPS): ${cause.message}",
            )
            playNext()
            return
        }

        consecutiveAutoSkips = 0
        _playerState.update {
            it.copy(
                isLoading = false,
                errorMessage = "Failed to load: ${cause.message ?: "Unknown error"}",
            )
        }
    }

    fun playSong(song: Song) {
        val requestId = playRequestCounter.incrementAndGet()

        // Claim the play slot before doing anything slow. Any resolve still in flight for a
        // previously requested song will see a different token and abandon itself.
        val token = requestId
        activePlayToken = token

        // This attempt gets its own 403 retry, so the record of spent retries stays bounded
        // rather than growing for the life of the process.
        if (failedRetryTokens.size > MAX_RETRY_TOKENS) failedRetryTokens.clear()
        failedRetryTokens.remove(token)

        playHistory.record(song)

        viewModelScope.launch {
            _playerState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                resolveAndPlay(song, token = token)
            } catch (e: Exception) {
                Log.e("CRANK_PLAYER", "Failed to play ${song.title} (${song.id}): ${e.message}", e)
                handlePlaybackFailure(token, song, e)
            }
        }
    }

    /**
     * Starts [song] with [contextList] as the queue and repeat source.
     *
     * Note that the queue is built *before* playback starts, and includes songs that come before
     * [song] in [contextList]. The old implementation dropped them (`contextList.drop(index + 1)`),
     * which meant Previous could not reach a track the user had just skipped past — pressing
     * Previous replayed the current song from the start instead.
     */
    fun playSongWithContext(song: Song, contextList: List<Song>) {
        setQueue(
            PlayQueue().fromContext(
                currentSong = song,
                contextList = contextList,
                shuffle = _playerState.value.shuffleModeEnabled,
            )
        )
        saveQueueToRoom()
        playSong(song)
    }

    /**
     * Cycles Off -> All -> One.
     *
     * The mode is held in [_playerState] and never pushed into ExoPlayer. ExoPlayer has a single
     * MediaItem queued at a time — the app's queue lives in [PlayQueue] and is advanced by
     * [playNext] — so setting `player.repeatMode = REPEAT_MODE_ALL` did not repeat the queue. It
     * repeated the current track, and because that never reaches STATE_ENDED, [handleSongEnd] was
     * never called and the queue never advanced: Repeat-All looked like "play this song forever".
     */
    fun toggleRepeatMode() {
        val nextMode = when (_playerState.value.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _playerState.update { it.copy(repeatMode = nextMode) }
        saveCurrentPlaybackPosition()
    }

    /**
     * Turns shuffle on or off, rebuilding the upcoming order from the context.
     *
     * The rebuild is delegated to [PlayQueue.reshuffled], which re-appends songs the user queued
     * by hand. The previous implementation rebuilt from the context alone, so toggling shuffle
     * discarded anything added with "add to queue" — the queue visibly shrank, with no message.
     */
    fun toggleShuffle() {
        val nextShuffle = !_playerState.value.shuffleModeEnabled
        player.shuffleModeEnabled = nextShuffle
        _playerState.update { it.copy(shuffleModeEnabled = nextShuffle) }

        val currentSong = _playerState.value.currentSong ?: return
        setQueue(playQueue.reshuffled(currentSong, nextShuffle))
        saveQueueToRoom()
        saveCurrentPlaybackPosition()
    }

    fun setPlaybackSpeed(speed: Float) {
        val currentPitch = _playerState.value.playbackPitch
        player.playbackParameters = PlaybackParameters(speed, currentPitch)
        _playerState.update { it.copy(playbackSpeed = speed) }
        // Persisted so the Playback Settings screen means something across restarts; a setting that
        // silently reverts to 1x on every launch is the same class of bug as a toggle that does
        // nothing.
        viewModelScope.launch {
            runCatching { settingsStore.putFloat(SettingsStore.PLAYBACK_SPEED, speed) }
        }
    }

    /**
     * Restores the speed saved by [setPlaybackSpeed].
     *
     * Applied through [setPlaybackSpeed] rather than directly to the player so the state flow and the
     * engine cannot disagree; the write-back is harmless because the value is unchanged.
     */
    private fun restorePlaybackSpeed() {
        viewModelScope.launch {
            val saved = runCatching { settingsStore.getFloat(SettingsStore.PLAYBACK_SPEED, 1.0f) }
                .getOrDefault(1.0f)
            if (saved != 1.0f) setPlaybackSpeed(saved)
        }
    }

    fun setPlaybackPitch(pitch: Float) {
        val currentSpeed = _playerState.value.playbackSpeed
        player.playbackParameters = PlaybackParameters(currentSpeed, pitch)
        _playerState.update { it.copy(playbackPitch = pitch) }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimer.start(minutes) {
            if (player.isPlaying) player.pause()
        }
    }

    fun cancelSleepTimer() {
        sleepTimer.cancel()
    }

    /**
     * Appends [song] to the queue.
     *
     * Tracked as a manual addition so it survives a later shuffle toggle. The previous
     * implementation overwrote `originalQueue` with the pending list, which meant a single
     * hand-queued song silently *replaced* the album as the repeat-all source.
     */
    fun addToQueue(song: Song) {
        setQueue(playQueue.addManual(song))
        saveQueueToRoom()
    }

    fun removeFromQueue(index: Int) {
        val updated = playQueue.removeAt(index)
        if (updated == playQueue) return
        setQueue(updated)
        saveQueueToRoom()
        saveCurrentPlaybackPosition()
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val updated = playQueue.move(fromIndex, toIndex)
        if (updated == playQueue) return
        setQueue(updated)
        saveQueueToRoom()
    }

    fun clearQueue() {
        setQueue(playQueue.cleared())
        // No saveCurrentPlaybackPosition() here: the snapshot would immediately re-persist an
        // empty queue alongside a still-playing track, and the restore path treats an empty
        // snapshot as "no queue" and falls through to the table we just cleared.
        viewModelScope.launch { songDao.clearQueue() }
    }

    private fun saveQueueToRoom() {
        val snapshot = playQueue
        viewModelScope.launch {
            try {
                songDao.clearQueue()
                // Persist only the head of the queue: the snapshot is rewritten on a timer, so
                // the write must stay bounded regardless of how long the queue grows.
                val entities =
                    PlayQueue.persistableSongs(snapshot).mapIndexed { index, song ->
                        QueueItemEntity(
                            songId = song.id,
                            title = song.title,
                            artistName = song.artistName,
                            albumId = song.albumId,
                            durationMs = song.durationMs,
                            artworkUrl = song.artworkUrl,
                            streamUrl = song.streamUrl,
                            queueOrder = index
                        )
                    }
                if (entities.isNotEmpty()) {
                    songDao.insertQueueItems(entities)
                }
            } catch (e: Exception) {
                Log.e("CRANK_PLAYER", "Failed to save queue: ${e.message}")
            }
        }
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
            return
        }

        // Restoring a session sets currentSong but deliberately loads no media
        // item (the saved URL may be unusable). That left Play doing nothing at
        // all: ExoPlayer had no item, so play() was a no-op and the button
        // looked broken. Re-resolve and actually start the track instead.
        if (player.mediaItemCount == 0) {
            val song = _playerState.value.currentSong
            if (song == null) {
                Log.d("CRANK_PLAYER", "Play pressed with nothing loaded; ignoring")
                return
            }
            Log.d("CRANK_PLAYER", "Play pressed with no media item; loading '${song.title}'")
            val token = playRequestCounter.incrementAndGet()
            activePlayToken = token
            viewModelScope.launch {
                _playerState.update { it.copy(isLoading = true, errorMessage = null) }
                try {
                    resolveAndPlay(song, forceRefresh = true, reason = "Play", token = token)
                } catch (e: Exception) {
                    Log.e("CRANK_PLAYER", "Failed to start '${song.title}': ${e.message}")
                    handlePlaybackFailure(token, song, e)
                }
            }
            return
        }

        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0)
        }
        player.play()
    }

    fun seekTo(position: Long) {
        player.seekTo(position)
        _playerState.update { it.copy(progress = position) }
    }

    fun playPrevious() {
        val prevSong = playHistory.previous()

        if (prevSong == null) {
            // No earlier track: restart the current one rather than doing nothing. This branch is
            // reached both before anything has played and when we are already at the start of the
            // trail.
            player.seekTo(0)
            return
        }

        // Claim the play slot, exactly as playSong does. Without this, a resolve still in
        // flight for the track the user was on would win the race and overwrite the
        // previous track — the same wrong-song defect, reached from the other direction.
        val token = playRequestCounter.incrementAndGet()
        activePlayToken = token

        viewModelScope.launch {
            _playerState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                resolveAndPlay(prevSong, reason = "Previous", token = token)
            } catch (e: Exception) {
                Log.e("CRANK_PLAYER", "Failed to play previous: ${e.message}")
                handlePlaybackFailure(token, prevSong, e)
            }
        }
    }

    /**
     * Advances to the next track, wrapping for repeat-all.
     *
     * ## What was wrong
     *
     * The wrap branch set `_queue.value = originalQueue` and recursed. Because `originalQueue`
     * was the context *including the track that had just finished*, the first song of the new
     * pass was the one already playing — so repeat-all replayed the last track twice before
     * moving on. When shuffle was on it recursed through `buildShuffledQueue` and could re-enter
     * `playNext` with an empty queue, recursing until the stack ran out.
     *
     * Now the wrap is a single, non-recursive step: rebuild the upcoming list from the context
     * with the current song excluded, then take its head. A `null` result means there is nothing
     * to repeat, and playback stops rather than looping.
     */
    fun playNext() {
        val next = playQueue.peekNext()
        if (next != null) {
            setQueue(playQueue.dropFirst())
            saveQueueToRoom()
            playSong(next)
            return
        }

        if (_playerState.value.repeatMode == Player.REPEAT_MODE_ALL) {
            val currentSong = _playerState.value.currentSong
            if (currentSong == null) {
                Log.w("CRANK_PLAYER", "Repeat-all with no current song; stopping")
                _playerState.update { it.copy(isPlaying = false) }
                return
            }

            val wrapped =
                playQueue.forRepeatAll(currentSong, _playerState.value.shuffleModeEnabled)

            if (wrapped == null || wrapped.upNext.isEmpty()) {
                // Nothing to wrap to — a context of one, or none at all. Stopping is correct;
                // retrying would spin.
                Log.d("CRANK_PLAYER", "Repeat-all with nothing to repeat; stopping")
                _playerState.update { it.copy(isPlaying = false) }
                return
            }

            setQueue(wrapped)
            saveQueueToRoom()
            playNext()
            return
        }

        _playerState.update { it.copy(isPlaying = false) }
    }

    fun dismissError() {
        _playerState.update { it.copy(errorMessage = null) }
    }

    /**
     * Starts downloading the current song.
     *
     * Fire-and-forget by design: [observeCurrentSongDownload] is the single
     * owner of [_currentSongDownloadState] (database ground truth + live
     * feed), so this must not write it beyond the instant optimistic tick.
     * The previous endless collect here raced the observer and painted the
     * old song's state onto the new one after a skip.
     */
    fun downloadCurrentSong() {
        val song = _playerState.value.currentSong ?: return
        downloadTapTimes[song.id] = System.currentTimeMillis()
        viewModelScope.launch {
            if (_playerState.value.currentSong?.id == song.id) {
                _currentSongDownloadState.value = DownloadState.DOWNLOADING
            }
            try {
                downloadRepository.downloadSong(song)
            } catch (e: Exception) {
                Log.e("CRANK_PLAYER", "Download failed: ${e.message}")
                if (_playerState.value.currentSong?.id == song.id) {
                    _currentSongDownloadState.value = DownloadState.FAILED
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        saveCurrentPlaybackPosition()
        player.removeListener(playerListener)
        sleepTimer.cancel()
        positionSaveJob?.cancel()

        // Persist whatever the listening clock has accumulated but not yet flushed. This cannot use
        // viewModelScope — it is already cancelled by the time onCleared runs — so the recorder puts
        // it on an application-lifetime scope, which is the only context that outlives this ViewModel.
        historyRecorder.flushListeningTimeOnProcessScope()

        // Detach from the OS-transport bridge. This ViewModel is activity-scoped, so onCleared
        // runs as the app's last activity finishes — but the bridge is a process-wide singleton
        // and the lambdas it holds capture `this`. Left installed, a finished ViewModel stayed
        // reachable and could still receive notification / lock-screen / Bluetooth skip commands.
        // Clearing makes those commands no-op until a new instance registers in its init block.
        RemoteControlBridge.onSkipToNext = null
        RemoteControlBridge.onSkipToPrevious = null
    }

    private fun MediaItem.toSong(): Song {
        val metadata = mediaMetadata
        return Song(
            id = mediaId,
            title = metadata.title?.toString() ?: "Unknown Track",
            artistName = metadata.artist?.toString() ?: "Unknown Artist",
            albumId = null,
            durationMs = player.duration.coerceAtLeast(0L),
            artworkUrl = metadata.artworkUri?.toString() ?: "",
            isLocal = false
        )
    }

    // LyricsSession, the exact-before-fuzzy ordering, the stale-response guard and the top-level
    // `lyricsAreForCurrentTrack` comparison all moved to LyricsSession.kt, along with the sleep timer
    // (now SleepTimerController.kt). Both keep their test coverage: the lyrics track-correspondence
    // test resolves the same package-private function from its new home.
}
