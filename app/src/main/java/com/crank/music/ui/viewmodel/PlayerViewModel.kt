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
import com.crank.music.data.local.HistoryEntity
import com.crank.music.data.local.QueueItemEntity
import com.crank.music.data.local.SongDao
import com.crank.music.domain.model.DownloadState
import com.crank.music.domain.model.Song
import com.crank.music.domain.repository.DownloadRepository
import com.crank.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

data class LyricsLine(
    val timestampMs: Long,
    val text: String
)

sealed class LyricsState {
    object Loading : LyricsState()
    data class Success(val lines: List<LyricsLine>) : LyricsState()
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
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _lyricsState = MutableStateFlow<LyricsState>(LyricsState.Loading)
    val lyricsState: StateFlow<LyricsState> = _lyricsState.asStateFlow()

    private val _history = MutableStateFlow<List<Song>>(emptyList())
    val history: StateFlow<List<Song>> = _history.asStateFlow()

    private val _currentSongDownloadState = MutableStateFlow(DownloadState.IDLE)
    val currentSongDownloadState: StateFlow<DownloadState> = _currentSongDownloadState.asStateFlow()

    private val playHistory = mutableListOf<Song>()
    private var historyIndex = -1

    private var sleepTimerJob: Job? = null
    private var positionSaveJob: Job? = null

    private val playRequestCounter = AtomicLong(0)

    private var originalQueue = listOf<Song>()
    private var shuffledIndices = mutableListOf<Int>()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playerState.update { it.copy(isPlaying = isPlaying) }
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
                generateLyricsForSong(song)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    _playerState.update {
                        it.copy(
                            duration = player.duration.coerceAtLeast(0L),
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
                Player.STATE_BUFFERING -> {
                    _playerState.update { it.copy(isLoading = true) }
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
            viewModelScope.launch {
                delay(1000)
                playNext()
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _playerState.update { it.copy(repeatMode = repeatMode) }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _playerState.update { it.copy(shuffleModeEnabled = shuffleModeEnabled) }
        }
    }

    init {
        player.addListener(playerListener)
        startProgressTracker()
        loadHistory()
        startPositionSaving()
        observeCurrentSongDownload()
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

                player.repeatMode = savedState.repeatMode
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
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(savedSong.title)
                                .setArtist(savedSong.artistName)
                                .setAlbumTitle(savedSong.albumId)
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
                val queueIds = savedState.queueJson.split(",").filter { it.isNotBlank() }
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
                    _queue.value = queueSongs
                    originalQueue = queueSongs
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
                    _queue.value = songs
                    originalQueue = songs.toList()
                }
            } catch (e: Exception) {
                Log.e("CRANK_PLAYER", "Failed to load queue: ${e.message}")
            }
        }
    }

    private fun startProgressTracker() {
        viewModelScope.launch {
            while (isActive) {
                val dur = player.duration.coerceAtLeast(0L)
                val pos = player.currentPosition.coerceAtLeast(0L)
                val sessionId = if (player.audioSessionId != 0 && player.audioSessionId != -1) player.audioSessionId else null
                _playerState.update {
                    it.copy(
                        isPlaying = player.isPlaying,
                        progress = pos,
                        duration = if (dur > 0L) dur else it.duration,
                        audioSessionId = sessionId ?: it.audioSessionId,
                        repeatMode = player.repeatMode,
                        shuffleModeEnabled = player.shuffleModeEnabled
                    )
                }
                delay(500L)
            }
        }
    }

    private fun startPositionSaving() {
        positionSaveJob = viewModelScope.launch {
            while (isActive) {
                delay(5000L)
                saveCurrentPlaybackPosition()
            }
        }
    }

    private fun observeCurrentSongDownload() {
        viewModelScope.launch {
            _playerState.collect { state ->
                val song = state.currentSong ?: return@collect
                try {
                    downloadRepository.getDownloadState(song.id).collect { downloadState ->
                        _currentSongDownloadState.value = downloadState
                    }
                } catch (e: Exception) {
                    Log.e("CRANK_PLAYER", "Failed to observe download state: ${e.message}")
                }
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
                        queueJson = _queue.value.joinToString(",") { it.id },
                        repeatMode = player.repeatMode,
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
        recordHistory(currentSong)

        when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> {
                player.seekTo(0)
                player.play()
            }
            else -> {
                playNext()
            }
        }
    }

    private fun recordHistory(song: Song) {
        viewModelScope.launch {
            try {
                val entity = HistoryEntity(
                    songId = song.id,
                    title = song.title,
                    artistName = song.artistName,
                    albumId = song.albumId,
                    durationMs = song.durationMs,
                    artworkUrl = song.artworkUrl,
                    streamUrl = song.streamUrl,
                    playedAt = System.currentTimeMillis()
                )
                songDao.insertHistoryItem(entity)
            } catch (e: Exception) {
                Log.e("CRANK_PLAYER", "Failed to record history: ${e.message}")
            }
        }
    }

    /**
     * Turns a [Song] into a playable URL.
     *
     * Returns `null` when the song has no playable source at all, so callers can
     * report a specific reason rather than a generic failure.
     *
     * Throws whatever the resolver throws. Note that a [Song.streamUrl] which is
     * already an absolute URL is passed through untouched (some sources hand back
     * a direct CDN link), while anything else — a bare video ID, most commonly —
     * is treated as an identifier to resolve.
     */
    private suspend fun resolvePlayableUrl(song: Song): String? {
        if (!song.isPlayable) return null

        val target = if (song.streamUrl.isNotBlank()) song.streamUrl else song.id
        val streamData = if (target.startsWith("http://") || target.startsWith("https://")) {
            com.crank.music.data.remote.StreamData(url = target)
        } else {
            musicRepository.getSongStreamUrl(target, song.title, song.artistName)
        }
        return streamData.url.takeIf { it.isNotBlank() }
    }

    /**
     * Message shown when a recognised-but-unsourced track is played.
     *
     * Recognition returns metadata only — a title and an artist, with no audio
     * attached. Saying so plainly, and pointing at the search box, is far more
     * useful than the resolver exception this used to produce.
     */
    private fun unresolvedMessage(song: Song): String =
        "This is a recognised track with no audio source yet. " +
            "Search for \"${song.title}\" by ${song.artistName} to play it."

    fun playSong(song: Song) {
        val requestId = playRequestCounter.incrementAndGet()

        if (historyIndex >= 0 && historyIndex < playHistory.size - 1) {
            playHistory.subList(historyIndex + 1, playHistory.size).clear()
        }
        playHistory.add(song)
        historyIndex = playHistory.size - 1

        viewModelScope.launch {
            _playerState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                // A recognised-but-unsourced track carries a marker instead of a URL.
                // Fail fast and explain it, rather than falling through to eight
                // network round-trips that cannot succeed.
                if (!song.isPlayable) {
                    Log.d("CRANK_PLAYER", "Song '${song.title}' has no playable source (recognised only)")
                    _playerState.update {
                        it.copy(isLoading = false, errorMessage = unresolvedMessage(song))
                    }
                    return@launch
                }

                val resolvedUrl = resolvePlayableUrl(song)

                if (requestId != playRequestCounter.get()) {
                    Log.d("CRANK_PLAYER", "Stale request $requestId discarded for: ${song.title}")
                    return@launch
                }

                if (resolvedUrl == null) {
                    Log.e("CRANK_PLAYER", "Empty stream URL for ${song.title} (${song.id})")
                    _playerState.update {
                        it.copy(isLoading = false, errorMessage = "No audio stream available for: ${song.title}")
                    }
                    return@launch
                }

                Log.d("CRANK_PLAYER", "Playing: ${song.title} (${song.id}) -> ${resolvedUrl.take(80)}...")

                val mediaItem = MediaItem.Builder()
                    .setMediaId(song.id)
                    .setUri(resolvedUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artistName)
                            .setAlbumTitle(song.albumId)
                            .setArtworkUri(if (song.artworkUrl.isNotBlank()) song.artworkUrl.toUri() else null)
                            .build()
                    )
                    .build()

                player.setMediaItem(mediaItem)
                player.prepare()
                player.play()

                _playerState.update {
                    it.copy(
                        currentSong = song.copy(streamUrl = resolvedUrl),
                        isLoading = false,
                        errorMessage = null
                    )
                }
                generateLyricsForSong(song)
                saveCurrentPlaybackPosition()
            } catch (e: Exception) {
                Log.e("CRANK_PLAYER", "Failed to play ${song.title} (${song.id}): ${e.message}", e)
                if (requestId == playRequestCounter.get()) {
                    _playerState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to load: ${e.message ?: "Unknown error"}"
                        )
                    }
                }
            }
        }
    }

    fun playSongWithContext(song: Song, contextList: List<Song>) {
        originalQueue = contextList.toList()
        if (_playerState.value.shuffleModeEnabled) {
            buildShuffledQueue(contextList, song)
        } else {
            val songIndex = contextList.indexOfFirst { it.id == song.id }
            if (songIndex >= 0) {
                _queue.value = contextList.drop(songIndex + 1)
            } else {
                _queue.value = emptyList()
            }
        }
        saveQueueToRoom()
        playSong(song)
    }

    private fun buildShuffledQueue(songs: List<Song>, currentSong: Song) {
        shuffledIndices = (songs.indices).toMutableList()
        shuffledIndices.shuffle()
        val currentIdx = songs.indexOfFirst { it.id == currentSong.id }
        if (currentIdx >= 0) {
            shuffledIndices.remove(currentIdx)
            shuffledIndices.add(0, currentIdx)
        }
        val currentPos = shuffledIndices.indexOf(currentIdx)
        _queue.value = shuffledIndices.drop(currentPos + 1).map { songs[it] }
    }

    fun toggleRepeatMode() {
        val nextMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = nextMode
        _playerState.update { it.copy(repeatMode = nextMode) }
        saveCurrentPlaybackPosition()
    }

    fun toggleShuffle() {
        val nextShuffle = !_playerState.value.shuffleModeEnabled
        player.shuffleModeEnabled = nextShuffle
        _playerState.update { it.copy(shuffleModeEnabled = nextShuffle) }

        val currentSong = _playerState.value.currentSong ?: return
        if (nextShuffle) {
            if (originalQueue.isNotEmpty()) {
                buildShuffledQueue(originalQueue, currentSong)
            }
        } else {
            if (originalQueue.isNotEmpty()) {
                val currentIdx = originalQueue.indexOfFirst { it.id == currentSong.id }
                if (currentIdx >= 0) {
                    _queue.value = originalQueue.drop(currentIdx + 1)
                } else {
                    _queue.value = emptyList()
                }
            }
        }
        saveQueueToRoom()
        saveCurrentPlaybackPosition()
    }

    fun setPlaybackSpeed(speed: Float) {
        val currentPitch = _playerState.value.playbackPitch
        player.playbackParameters = PlaybackParameters(speed, currentPitch)
        _playerState.update { it.copy(playbackSpeed = speed) }
    }

    fun setPlaybackPitch(pitch: Float) {
        val currentSpeed = _playerState.value.playbackSpeed
        player.playbackParameters = PlaybackParameters(currentSpeed, pitch)
        _playerState.update { it.copy(playbackPitch = pitch) }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _playerState.update { it.copy(sleepTimerMinutes = 0, remainingSleepTimeMs = 0L) }
            return
        }

        var remainingMs = minutes * 60 * 1000L
        _playerState.update { it.copy(sleepTimerMinutes = minutes, remainingSleepTimeMs = remainingMs) }

        sleepTimerJob = viewModelScope.launch {
            while (remainingMs > 0) {
                delay(1000L)
                remainingMs -= 1000L
                _playerState.update { it.copy(remainingSleepTimeMs = remainingMs.coerceAtLeast(0L)) }
            }
            if (player.isPlaying) {
                player.pause()
            }
            _playerState.update { it.copy(sleepTimerMinutes = 0, remainingSleepTimeMs = 0L) }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _playerState.update { it.copy(sleepTimerMinutes = 0, remainingSleepTimeMs = 0L) }
    }

    fun addToQueue(song: Song) {
        val updated = _queue.value + song
        _queue.value = updated
        originalQueue = updated
        saveQueueToRoom()
    }

    fun removeFromQueue(index: Int) {
        val current = _queue.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _queue.value = current
            originalQueue = current
            saveQueueToRoom()
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val current = _queue.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _queue.value = current
            originalQueue = current
            saveQueueToRoom()
        }
    }

    fun clearQueue() {
        _queue.value = emptyList()
        originalQueue = emptyList()
        viewModelScope.launch { songDao.clearQueue() }
    }

    private fun saveQueueToRoom() {
        viewModelScope.launch {
            try {
                songDao.clearQueue()
                val queue = _queue.value
                val entities = queue.mapIndexed { index, song ->
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
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0)
            }
            player.play()
        }
    }

    fun seekTo(position: Long) {
        player.seekTo(position)
        _playerState.update { it.copy(progress = position) }
    }

    fun playPrevious() {
        if (playHistory.size > 1 && historyIndex > 0) {
            historyIndex--
            val prevSong = playHistory[historyIndex]
            viewModelScope.launch {
                _playerState.update { it.copy(isLoading = true, errorMessage = null) }
                try {
                    if (!prevSong.isPlayable) {
                        _playerState.update {
                            it.copy(isLoading = false, errorMessage = unresolvedMessage(prevSong))
                        }
                        return@launch
                    }

                    val resolvedUrl = resolvePlayableUrl(prevSong)
                    if (resolvedUrl != null) {
                        val mediaItem = MediaItem.Builder()
                            .setMediaId(prevSong.id)
                            .setUri(resolvedUrl)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(prevSong.title)
                                    .setArtist(prevSong.artistName)
                                    .setAlbumTitle(prevSong.albumId)
                                    .setArtworkUri(if (prevSong.artworkUrl.isNotBlank()) prevSong.artworkUrl.toUri() else null)
                                    .build()
                            )
                            .build()
                        player.setMediaItem(mediaItem)
                        player.prepare()
                        player.play()
                        _playerState.update {
                            it.copy(
                                currentSong = prevSong.copy(streamUrl = resolvedUrl),
                                isLoading = false,
                                errorMessage = null
                            )
                        }
                    } else {
                        _playerState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "No audio stream available for: ${prevSong.title}"
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CRANK_PLAYER", "Failed to play previous: ${e.message}")
                    _playerState.update { it.copy(isLoading = false) }
                }
            }
        } else {
            player.seekTo(0)
        }
    }

    fun playNext() {
        val currentQueue = _queue.value
        if (currentQueue.isNotEmpty()) {
            val nextSong = currentQueue.first()
            val remaining = currentQueue.drop(1)
            _queue.value = remaining
            saveQueueToRoom()
            playSong(nextSong)
        } else {
            if (player.repeatMode == Player.REPEAT_MODE_ALL) {
                val currentSong = _playerState.value.currentSong ?: return
                if (originalQueue.isNotEmpty()) {
                    if (_playerState.value.shuffleModeEnabled) {
                        buildShuffledQueue(originalQueue, currentSong)
                        val nextIdx = _queue.value.firstOrNull()
                        if (nextIdx != null) {
                            playNext()
                        }
                    } else {
                        _queue.value = originalQueue
                        saveQueueToRoom()
                        playNext()
                    }
                }
            } else {
                _playerState.update { it.copy(isPlaying = false) }
            }
        }
    }

    fun dismissError() {
        _playerState.update { it.copy(errorMessage = null) }
    }

    fun downloadCurrentSong() {
        val song = _playerState.value.currentSong ?: return
        viewModelScope.launch {
            _currentSongDownloadState.value = DownloadState.DOWNLOADING
            try {
                downloadRepository.downloadSong(song)
                downloadRepository.getDownloadState(song.id).collect { state ->
                    _currentSongDownloadState.value = state
                }
            } catch (e: Exception) {
                Log.e("CRANK_PLAYER", "Download failed: ${e.message}")
                _currentSongDownloadState.value = DownloadState.FAILED
            }
        }
    }

    private fun generateLyricsForSong(song: Song) {
        _lyricsState.value = LyricsState.Loading
        viewModelScope.launch {
            try {
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    fetchLyricsFromLRCLIB(song.title, song.artistName, song.durationMs / 1000)
                }
                if (result != null) {
                    _lyricsState.value = LyricsState.Success(result)
                } else {
                    _lyricsState.value = LyricsState.Unavailable
                }
            } catch (e: Exception) {
                Log.e("CRANK_PLAYER", "Lyrics fetch failed: ${e.message}")
                _lyricsState.value = LyricsState.Unavailable
            }
        }
    }

    private suspend fun fetchLyricsFromLRCLIB(title: String, artist: String, durationSec: Long): List<LyricsLine>? {
        return try {
            val httpResponse = musicRepository.searchLyrics(title, artist)
            if (httpResponse != null) {
                val synced = httpResponse.syncedLyrics
                if (!synced.isNullOrBlank()) {
                    val lines = parseLRC(synced)
                    if (lines.isNotEmpty()) return lines
                }
                val plain = httpResponse.plainLyrics
                if (!plain.isNullOrBlank()) {
                    val lines = plain.lines()
                        .filter { it.isNotBlank() }
                        .mapIndexed { index, line -> LyricsLine(index * 4000L, line.trim()) }
                    if (lines.isNotEmpty()) return lines
                }
            }
            null
        } catch (e: Exception) {
            Log.e("CRANK_LYRICS", "LRCLIB failed for '$title': ${e.message}")
            null
        }
    }

    private fun parseLRC(lrc: String): List<LyricsLine> {
        val regex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)""")
        return lrc.lines().mapNotNull { line ->
            val match = regex.matchEntire(line.trim()) ?: return@mapNotNull null
            val minutes = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val seconds = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            val centis = match.groupValues[3].let {
                if (it.length == 2) it.toLongOrNull()!! * 10 else it.toLongOrNull()!!
            }
            val text = match.groupValues[4].trim()
            if (text.isBlank()) return@mapNotNull null
            LyricsLine(
                timestampMs = minutes * 60_000 + seconds * 1_000 + centis * 10,
                text = text
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        saveCurrentPlaybackPosition()
        player.removeListener(playerListener)
        sleepTimerJob?.cancel()
        positionSaveJob?.cancel()
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
}
