package com.crank.music.ui.screens

import java.util.Locale

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil3.BitmapImage
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.crank.music.domain.model.DownloadState
import com.crank.music.ui.CrankTestTags
import com.crank.music.ui.components.DownloadControlVisuals
import com.crank.music.ui.components.DownloadProgressIcon
import com.crank.music.ui.components.PlaybackControlsSheet
import com.crank.music.ui.components.SleepTimerSheet
import com.crank.music.ui.components.animatedDownloadFraction
import com.crank.music.ui.theme.HeartRed
import com.crank.music.ui.theme.isLight
import com.crank.music.ui.viewmodel.PlayerViewModel
import com.crank.music.ui.viewmodel.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Apple Music red — shuffle/repeat-active + scrubber accents. */
private val AppleRed = Color(0xFFFA1744)

/** Gradient colors extracted from the artwork. Null = no artwork yet. */
private data class ArtColors(val top: Color, val bottom: Color)

/**
 * Apple Music full-screen Now Playing.
 *
 * Pure UI: reads [PlayerViewModel.playerState] and calls the existing player
 * methods only. Device volume goes through [AudioManager], not the player.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playerViewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    onEqualizerClick: () -> Unit = {},
    onCrankAiClick: () -> Unit = {},
    onQueueClick: () -> Unit = {},
    onLyricsClick: () -> Unit = {},
    /** Optional: artist name tap. Defaults to no-op so existing call sites are untouched. */
    onArtistClick: (String) -> Unit = {},
    libraryViewModel: com.crank.music.ui.viewmodel.LibraryViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val playerState by playerViewModel.playerState.collectAsStateWithLifecycle()
    val song = playerState.currentSong
    val downloadState by playerViewModel.currentSongDownloadState.collectAsStateWithLifecycle()
    val artworkUrl = song?.artworkUrl.orEmpty()

    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }

    // Dark Mode row state. Read from the same ThemePreference the theme root uses, so the icon can
    // never disagree with the theme actually in effect.
    val themeMode by playerViewModel.themeMode.collectAsStateWithLifecycle()
    val systemInDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val isDarkNow = !(themeMode ?: ThemeMode.DARK).isLight(systemInDarkTheme)
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showCastHint by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }

    val isLiked by playerViewModel.isCurrentLiked.collectAsStateWithLifecycle()

    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── Artwork-extracted gradient (the Apple signature) ──
    var artColors by remember { mutableStateOf<ArtColors?>(null) }
    LaunchedEffect(artworkUrl) {
        if (artworkUrl.isBlank()) {
            artColors = null
            return@LaunchedEffect
        }
        artColors = withContext(Dispatchers.Default) {
            runCatching {
                val loader = context.imageLoader
                val req = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .size(256)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(req)
                val bitmap = (result as? SuccessResult)
                    ?.image?.let { it as? BitmapImage }?.bitmap
                    ?: return@runCatching null
                val palette = Palette.from(bitmap).generate()
                val swatch = palette.vibrantSwatch
                    ?: palette.lightVibrantSwatch
                    ?: palette.dominantSwatch
                    ?: palette.mutedSwatch
                    ?: return@runCatching null
                val top = Color(
                    ColorUtils.blendARGB(swatch.rgb, android.graphics.Color.BLACK, 0.28f)
                )
                val deepRgb = palette.darkVibrantSwatch?.rgb
                    ?: palette.darkMutedSwatch?.rgb
                val bottom = if (deepRgb != null) Color(deepRgb)
                else Color(ColorUtils.blendARGB(swatch.rgb, android.graphics.Color.BLACK, 0.72f))
                ArtColors(top, bottom)
            }.getOrNull()
        }
    }
    val gradientTop by animateColorAsState(
        targetValue = artColors?.top ?: Color(0xFF2B2B30),
        animationSpec = tween(600),
        label = "bg_top"
    )
    val gradientBottom by animateColorAsState(
        targetValue = artColors?.bottom ?: Color(0xFF101014),
        animationSpec = tween(600),
        label = "bg_bottom"
    )

    // ── Device music volume (Apple slider controls output volume) ──
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var deviceVolume by remember {
        mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                deviceVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
        }
        // ContextCompat rather than the 3-arg `Context.registerReceiver(receiver, filter, flags)`
        // overload: that overload is API 26 while minSdk is 24, so the previous form crashed with
        // a `NoSuchMethodError` on Android 7.x the moment the player screen opened. The compat
        // helper calls the flagged overload where it exists and falls back to the 2-arg form below
        // Oreo. RECEIVER_NOT_EXPORTED only governs broadcasts from *other apps* — system
        // broadcasts such as VOLUME_CHANGED_ACTION are still delivered to a non-exported receiver.
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(showCastHint) {
        if (showCastHint) {
            delay(2200)
            showCastHint = false
        }
    }

    // Artwork breathes with playback: full scale while playing, 0.92 paused.
    val artScale by animateFloatAsState(
        targetValue = if (playerState.isPlaying) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "art_scale"
    )

    // Swipe-down-to-dismiss.
    var dismissOffset by remember { mutableStateOf(0f) }

    val progressFloat = if (playerState.duration > 0) {
        (playerState.progress.toFloat() / playerState.duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(gradientTop, gradientBottom)))
            .graphicsLayer { translationY = dismissOffset }
            .pointerInput(onBackClick) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        if (dragAmount > 0) {
                            change.consume()
                            dismissOffset += dragAmount
                        }
                    },
                    onDragEnd = {
                        if (dismissOffset > 320f) {
                            onBackClick()
                            dismissOffset = 0f
                        } else {
                            dismissOffset = 0f
                        }
                    },
                    onDragCancel = { dismissOffset = 0f }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Top bar: collapse left; lyrics + queue + more right ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onLyricsClick) {
                        Icon(
                            imageVector = Icons.Default.FormatQuote,
                            contentDescription = "Lyrics",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(25.dp)
                        )
                    }
                    IconButton(onClick = onQueueClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Up Next",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(25.dp)
                        )
                    }
                    IconButton(onClick = { showMoreSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "More options",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Artwork: swipe left/right = next/previous ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .graphicsLayer {
                        scaleX = artScale
                        scaleY = artScale
                    }
                    .pointerInput(song?.id) {
                        var totalX = 0f
                        detectHorizontalDragGestures(
                            onDragCancel = { totalX = 0f },
                            onDragEnd = {
                                when {
                                    totalX < -160f -> playerViewModel.playNext()
                                    totalX > 160f -> playerViewModel.playPrevious()
                                }
                                totalX = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                totalX += dragAmount
                            }
                        )
                    }
                    .shadow(
                        elevation = 30.dp,
                        shape = RoundedCornerShape(12.dp),
                        spotColor = Color.Black.copy(alpha = 0.5f),
                        ambientColor = Color.Black.copy(alpha = 0.25f)
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (artworkUrl.isNotBlank()) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = "Album Artwork",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(72.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ── Title / artist + download / add-to-playlist / like ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song?.title ?: "No Song Selected",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = song?.artistName ?: "Unknown Artist",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(
                            enabled = song?.artistName != null,
                            onClick = {
                                song?.artistName?.let {
                                    onArtistClick(it)
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                }
                            }
                        )
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Download control: arrow → progress ring → checkmark.
                        //
                        // The glyph, wording and tappability all come from
                        // DownloadControlVisuals, which the three-dot menu below reads as well,
                        // so the two surfaces cannot drift apart about the same song. The
                        // progress read is confined to this composable so a moving ring never
                        // recomposes the rest of the player.
                        DownloadControlButton(
                            state = downloadState,
                            progressFlow = playerViewModel.currentSongDownloadFraction,
                            enabled = song != null,
                            onDownload = { playerViewModel.downloadCurrentSong() },
                        )
                        IconButton(
                            onClick = { showPlaylistSheet = true },
                            modifier = Modifier.size(40.dp),
                            enabled = song != null
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add to playlist",
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            playerViewModel.toggleLikeCurrentSong()
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        },
                        modifier = Modifier.size(40.dp),
                        enabled = song != null
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isLiked) "Liked" else "Like",
                            tint = if (isLiked) HeartRed else Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── Scrubber: elapsed left, remaining right ──
            AppleScrubber(
                progress = progressFloat,
                onSeek = { percent ->
                    playerViewModel.seekTo((percent * playerState.duration).toLong())
                },
                onDragStart = { isScrubbing = true },
                onDragEnd = { isScrubbing = false },
                modifier = Modifier.fillMaxWidth()
            )
            val timeScale by animateFloatAsState(
                targetValue = if (isScrubbing) 1.18f else 1f,
                animationSpec = tween(150),
                label = "time_scale"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatMs(playerState.progress),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp * timeScale,
                    fontWeight = if (isScrubbing) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = formatRemaining(playerState.duration - playerState.progress),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp * timeScale,
                    fontWeight = if (isScrubbing) FontWeight.Bold else FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ── Transport: prev | play | next ──
            Row(
                modifier = Modifier.fillMaxWidth(0.85f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { playerViewModel.playPrevious() },
                    modifier = Modifier.size(56.dp).testTag(CrankTestTags.Player.PREVIOUS)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }
                ApplePlayPauseButton(
                    isPlaying = playerState.isPlaying,
                    onClick = { playerViewModel.togglePlayPause() },
                    modifier = Modifier.testTag(CrankTestTags.Player.PLAY_PAUSE)
                )
                IconButton(
                    onClick = { playerViewModel.playNext() },
                    modifier = Modifier.size(56.dp).testTag(CrankTestTags.Player.NEXT)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }

            // ── Shuffle | volume | repeat ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        playerViewModel.toggleShuffle()
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    modifier = Modifier.size(44.dp).testTag(CrankTestTags.Player.SHUFFLE)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playerState.shuffleModeEnabled) AppleRed
                        else Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeDown,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
                Slider(
                    value = deviceVolume.toFloat(),
                    onValueChange = {
                        deviceVolume = it.toInt()
                        audioManager.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            it.toInt(),
                            0
                        )
                    },
                    valueRange = 0f..maxVolume.toFloat(),
                    steps = 0,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White.copy(alpha = 0.9f),
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
                IconButton(
                    onClick = {
                        playerViewModel.toggleRepeatMode()
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    modifier = Modifier.size(44.dp).testTag(CrankTestTags.Player.REPEAT)
                ) {
                    Icon(
                        imageVector = if (playerState.repeatMode == Player.REPEAT_MODE_ONE)
                            Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (playerState.repeatMode != Player.REPEAT_MODE_OFF) AppleRed
                        else Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Lyrics / queue live in the top bar now; keep bottom breathing room.
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (showCastHint) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Casting isn't available in this build",
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }
    }

    if (showMoreSheet) {
        val current = playerState.currentSong
        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 32.dp)
            ) {
                // Same state, same glyph, same wording as the player control above — both read
                // DownloadControlVisuals. This row additionally shows live progress: a ring
                // around the glyph plus the percentage on the right.
                DownloadMenuRow(
                    state = downloadState,
                    progressFlow = playerViewModel.currentSongDownloadFraction,
                    enabled = current != null && DownloadControlVisuals.isActionable(downloadState),
                    // Deliberately does NOT dismiss the sheet. Every other row here is a
                    // fire-and-forget action, so closing on tap is right for them — but this row
                    // is the only place the download's progress is shown, and dismissing it would
                    // hide the animation the tap just started. Staying open is what lets the row
                    // walk Download → Downloading 0%…100% → Downloaded in front of the user.
                    onClick = { playerViewModel.downloadCurrentSong() }
                )
                MoreRow(
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    label = "Add to Queue",
                    enabled = current != null,
                    onClick = {
                        current?.let { playerViewModel.addToQueue(it) }
                        showMoreSheet = false
                    }
                )
                MoreRow(
                    icon = Icons.Default.Cast,
                    label = "Cast",
                    onClick = {
                        showMoreSheet = false
                        showCastHint = true
                    }
                )
                // Sits directly below Cast, as specified. The glyph shows the *current* state
                // (moon while dark, sun while light) so the row reads as a status as well as a
                // control; tapping flips it and the whole app re-themes immediately.
                MoreRow(
                    icon = if (isDarkNow) Icons.Default.DarkMode else Icons.Default.LightMode,
                    label = "Dark Mode",
                    onClick = {
                        playerViewModel.toggleDarkMode(systemInDarkTheme)
                        showMoreSheet = false
                    }
                )
                MoreRow(
                    icon = Icons.Default.Person,
                    label = "Go to Artist",
                    enabled = current?.artistName != null,
                    onClick = {
                        showMoreSheet = false
                        current?.artistName?.let(onArtistClick)
                    }
                )
                MoreRow(
                    icon = Icons.Default.Share,
                    label = "Share",
                    enabled = current != null,
                    onClick = {
                        showMoreSheet = false
                        current?.let {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Listening to \"${it.title}\" by ${it.artistName} on Crank Music"
                                )
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                        }
                    }
                )
                MoreRow(
                    icon = Icons.Default.Bedtime,
                    label = "Sleep Timer",
                    onClick = {
                        showMoreSheet = false
                        showSleepTimerSheet = true
                    }
                )
                MoreRow(
                    icon = Icons.Default.Speed,
                    label = "Playback Speed",
                    onClick = {
                        showMoreSheet = false
                        showSpeedSheet = true
                    }
                )
                MoreRow(
                    icon = Icons.Default.GraphicEq,
                    label = "Equalizer",
                    onClick = {
                        showMoreSheet = false
                        onEqualizerClick()
                    }
                )
            }
        }
    }

    // ── Add-to-playlist sheet (Library-backed) ──
    if (showPlaylistSheet) {
        val track = playerState.currentSong
        AddToPlaylistSheet(
            libraryViewModel = libraryViewModel,
            onAdd = { playlistId ->
                track?.let { libraryViewModel.addSongToPlaylist(playlistId, it) }
                showPlaylistSheet = false
            },
            onDismiss = { showPlaylistSheet = false }
        )
    }

    if (showSleepTimerSheet) {        SleepTimerSheet(
            activeMinutes = playerState.sleepTimerMinutes,
            remainingMs = playerState.remainingSleepTimeMs,
            onSetTimer = { minutes -> playerViewModel.setSleepTimer(minutes) },
            onCancelTimer = { playerViewModel.cancelSleepTimer() },
            onDismiss = { showSleepTimerSheet = false }
        )
    }

    if (showSpeedSheet) {
        PlaybackControlsSheet(
            currentSpeed = playerState.playbackSpeed,
            onSpeedSelected = { speed -> playerViewModel.setPlaybackSpeed(speed) },
            onDismiss = { showSpeedSheet = false }
        )
    }
}

/**
 * Playlist picker: existing user playlists + inline create. All Library-backed.
 *
 * `internal` rather than `private` so the mini player can raise the same sheet — one picker for
 * both surfaces, instead of a second copy that drifts from this one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddToPlaylistSheet(
    libraryViewModel: com.crank.music.ui.viewmodel.LibraryViewModel,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val libraryState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Add to playlist",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("New playlist name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.size(8.dp))
                androidx.compose.material3.TextButton(
                    onClick = {
                        libraryViewModel.updateCreatePlaylistName(newName)
                        libraryViewModel.createPlaylist()
                        newName = ""
                    },
                    enabled = newName.isNotBlank()
                ) {
                    Text("Create")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (libraryState.playlists.isEmpty()) {
                Text(
                    text = "No playlists yet — create one above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                libraryState.playlists.forEach { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAdd(playlist.id) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${playlist.songCount} songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * The download control in the player's action row.
 *
 * Three states, one slot, so the row's layout never shifts as the state changes:
 * - not downloaded — a tappable Download arrow;
 * - downloading — a determinate progress ring sweeping around the glyph;
 * - downloaded — a plain checkmark with no tap target, because the audio is already on disk and
 *   an enabled control would only invite a duplicate transfer.
 *
 * The progress flow is collected here rather than in the caller. A transfer reports progress
 * continuously, and reading it higher up would recompose the whole player screen — artwork,
 * lyrics, transport bar — on every tick. Scoping it to this composable keeps the animation from
 * ever touching the controls it sits beside.
 */
@Composable
private fun DownloadControlButton(
    state: DownloadState,
    progressFlow: StateFlow<Float?>,
    enabled: Boolean,
    onDownload: () -> Unit,
) {
    val fraction by progressFlow.collectAsStateWithLifecycle()
    val animated = animatedDownloadFraction(fraction)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(40.dp)
    ) {
        if (DownloadControlVisuals.isActionable(state)) {
            IconButton(
                onClick = onDownload,
                enabled = enabled,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = DownloadControlVisuals.icon(state),
                    contentDescription = DownloadControlVisuals.label(state),
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(22.dp)
                )
            }
        } else {
            DownloadProgressIcon(
                state = state,
                fraction = animated,
                tint = Color.White,
                ringSize = 30.dp,
                // No label sits beside this control, so the glyph has to carry the state.
                contentDescription = DownloadControlVisuals.label(state),
            )
        }
    }
}

@Composable
private fun MoreRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    /**
     * Optional right-aligned value, e.g. a download percentage. Null leaves the row as it was,
     * so every other row is unaffected.
     */
    trailing: String? = null,
    /**
     * Optional replacement for the plain [icon]. Used by the download row, which needs to draw a
     * progress ring around its glyph rather than a bare vector.
     */
    leading: (@Composable () -> Unit)? = null,
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.padding(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            // Weighted so the trailing value is pushed to the far edge instead of sitting
            // immediately after the label, where it would shift as the label's length changed.
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                // Tabular-ish fixed width so the label does not jitter as the digits change
                // width while the percentage counts up.
                modifier = Modifier.widthIn(min = 44.dp),
                textAlign = TextAlign.End,
            )
        }
    }
}

/**
 * The Download row of the three-dot sheet.
 *
 * Collects the progress flow *here* rather than at the top of the screen, deliberately. Progress
 * ticks arrive continuously while a transfer runs, and a read at the top of `NowPlayingScreen`
 * would recompose the whole player — artwork, lyrics, transport bar — on every tick. Confining
 * the read to this one row keeps a moving animation off the player's critical path, which is
 * what lets the ring stay smooth without the transport controls stuttering.
 */
@Composable
private fun DownloadMenuRow(
    state: DownloadState,
    progressFlow: StateFlow<Float?>,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val fraction by progressFlow.collectAsStateWithLifecycle()
    val downloading = state == DownloadState.DOWNLOADING
    // Interpolated once, then shared by the ring and the number, so the two cannot disagree.
    val animated = animatedDownloadFraction(fraction)

    MoreRow(
        icon = DownloadControlVisuals.icon(state),
        label = DownloadControlVisuals.label(state),
        enabled = enabled,
        // The percentage belongs to the in-flight state; once it is done the checkmark says
        // everything a trailing "100%" would, without the noise.
        trailing = if (downloading && animated != null) {
            DownloadControlVisuals.percentLabel(animated)
        } else {
            null
        },
        leading = {
            DownloadProgressIcon(
                state = state,
                fraction = animated,
                // Accent while it is doing something, ordinary row colour when it is not.
                tint = if (downloading) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        },
        onClick = onClick,
    )
}

/** Large borderless play/pause glyph with a subtle scale bounce on every tap. */
@Composable
private fun ApplePlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.88f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "play_scale"
    )
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(76.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = Color.White,
            modifier = Modifier.size(60.dp)
        )
    }
}

/** Thin white scrubber over the gradient; thumb grows slightly while touched. */
@Composable
private fun AppleScrubber(
    progress: Float,
    onSeek: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { androidx.compose.runtime.mutableFloatStateOf(progress) }
    val displayProgress = if (isDragging) dragProgress else progress

    val trackColor = Color.White.copy(alpha = 0.3f)
    val playedColor = Color.White.copy(alpha = 0.9f)
    val thumbRadius by animateFloatAsState(
        targetValue = if (isDragging) 7f else 4f,
        animationSpec = tween(150),
        label = "thumb"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        onDragStart()
                        dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        isDragging = false
                        onDragEnd()
                        onSeek(dragProgress)
                    },
                    onDragCancel = {
                        isDragging = false
                        onDragEnd()
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        dragProgress = (dragProgress + dragAmount / size.width).coerceIn(0f, 1f)
                    }
                )
            }
    ) {
        val trackHeight = 4.dp.toPx()
        val centerY = size.height / 2
        val radius = CornerRadius(trackHeight / 2)

        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, centerY - trackHeight / 2),
            size = androidx.compose.ui.geometry.Size(size.width, trackHeight),
            cornerRadius = radius
        )

        if (displayProgress > 0f) {
            drawRoundRect(
                color = playedColor,
                topLeft = Offset(0f, centerY - trackHeight / 2),
                size = androidx.compose.ui.geometry.Size(size.width * displayProgress, trackHeight),
                cornerRadius = radius
            )
        }

        val handleX = (displayProgress * size.width).coerceIn(0f, size.width)
        drawCircle(
            color = playedColor,
            radius = thumbRadius.dp.toPx(),
            center = Offset(handleX, centerY)
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

private fun formatRemaining(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "-%d:%02d", minutes, seconds)
}
