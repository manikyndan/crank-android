package com.crank.music.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.crank.music.ui.components.PlaybackControlsSheet
import com.crank.music.ui.components.SleepTimerSheet
import com.crank.music.domain.model.DownloadState
import com.crank.music.ui.theme.HeartRed
import com.crank.music.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playerViewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    onEqualizerClick: () -> Unit = {},
    onCrankAiClick: () -> Unit = {},
    onQueueClick: () -> Unit = {},
    onLyricsClick: () -> Unit = {}
) {
    val playerState by playerViewModel.playerState.collectAsState()
    val song = playerState.currentSong

    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var isLiked by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }

    val view = LocalView.current

    val albumArtScale = remember { Animatable(1f) }
    LaunchedEffect(playerState.isPlaying) {
        if (playerState.isPlaying) {
            while (true) {
                albumArtScale.animateTo(
                    targetValue = 1.02f,
                    animationSpec = tween(3000, easing = LinearEasing)
                )
                albumArtScale.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(3000, easing = LinearEasing)
                )
            }
        }
    }

    val progressFloat = if (playerState.duration > 0) {
        (playerState.progress.toFloat() / playerState.duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // Blurred artwork backdrop. Tinting it with the theme background instead of a fixed
    // colour is what lets this screen survive the switch to light mode.
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (song?.artworkUrl.orEmpty().isNotBlank()) {
            AsyncImage(
                model = song?.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(80.dp)
                    .graphicsLayer { alpha = 0.35f }
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Now Playing",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = { showSleepTimerSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = "Sleep Timer",
                            tint = if (playerState.sleepTimerMinutes > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(onClick = { showSpeedSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Speed",
                            tint = if (playerState.playbackSpeed != 1.0f) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Artwork. Straight-on, large, and softly breathing while playing. The previous
            // version tilted it in 3D under the finger and stamped a LOSSLESS badge on the
            // corner: the tilt fought the drag-to-seek gesture below, and the badge claimed a
            // codec guarantee the stream layer never makes.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .aspectRatio(1f)
                    .graphicsLayer {
                        scaleX = albumArtScale.value
                        scaleY = albumArtScale.value
                    }
                    .shadow(
                        elevation = 28.dp,
                        shape = RoundedCornerShape(12.dp),
                        spotColor = Color.Black.copy(alpha = 0.45f),
                        ambientColor = Color.Black.copy(alpha = 0.2f)
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (song?.artworkUrl.orEmpty().isNotBlank()) {
                    AsyncImage(
                        model = song?.artworkUrl,
                        contentDescription = "Album Artwork",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(72.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title left-aligned and bold, artist underneath in a muted tone. The accent is
            // spent on controls, not on the artist name.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song?.title ?: "No Song Selected",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = song?.artistName ?: "Unknown Artist",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                LikeButton(
                    isLiked = isLiked,
                    onClick = {
                        isLiked = !isLiked
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Scrubber(
                progress = progressFloat,
                onSeek = { percent ->
                    val newPos = (percent * playerState.duration).toLong()
                    playerViewModel.seekTo(newPos)
                },
                onDragStart = { isDragging = true },
                onDragEnd = { isDragging = false },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatMs(playerState.progress),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    // Counting down is how every streaming player shows the tail of a track,
                    // and it makes the number change as fast as the elapsed one.
                    text = "-" + formatMs((playerState.duration - playerState.progress).coerceAtLeast(0L)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Transport controls. Secondary controls stay muted; the accent is reserved for
            // the play button and for the toggles when they are on.
            Row(
                modifier = Modifier.fillMaxWidth(0.9f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedToggleButton(
                    isActive = playerState.shuffleModeEnabled,
                    onClick = { playerViewModel.toggleShuffle() },
                    activeColor = MaterialTheme.colorScheme.primary,
                    inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) { isActive, color ->
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = color,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = { playerViewModel.playPrevious() },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(34.dp)
                    )
                }

                MorphingPlayPauseButton(
                    isPlaying = playerState.isPlaying,
                    onClick = { playerViewModel.togglePlayPause() }
                )

                IconButton(
                    onClick = { playerViewModel.playNext() },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(34.dp)
                    )
                }

                AnimatedToggleButton(
                    isActive = playerState.repeatMode != Player.REPEAT_MODE_OFF,
                    onClick = { playerViewModel.toggleRepeatMode() },
                    activeColor = MaterialTheme.colorScheme.primary,
                    inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) { isActive, color ->
                    Icon(
                        imageVector = if (playerState.repeatMode == Player.REPEAT_MODE_ONE)
                            Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = color,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Secondary actions, muted so they don't compete with the transport row.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp, top = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onEqualizerClick) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Equalizer",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = onQueueClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Queue",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = onLyricsClick) {
                    Icon(
                        imageVector = Icons.Default.FormatQuote,
                        contentDescription = "Lyrics",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = { playerViewModel.downloadCurrentSong() }) {
                    val downloadState by playerViewModel.currentSongDownloadState.collectAsState()
                    Icon(
                        imageVector = when (downloadState) {
                            DownloadState.COMPLETED -> Icons.Default.CheckCircle
                            DownloadState.DOWNLOADING -> Icons.Default.Downloading
                            else -> Icons.Default.Download
                        },
                        contentDescription = "Download",
                        tint = when (downloadState) {
                            DownloadState.COMPLETED -> MaterialTheme.colorScheme.primary
                            DownloadState.DOWNLOADING ->
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = onCrankAiClick) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Crank AI",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

    if (showSleepTimerSheet) {
        SleepTimerSheet(
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

@Composable
private fun MorphingPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "morph"
    )

    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    // A flat accent disc. The old button layered a radial gold gradient, an embossed ring
    // and two shadows on top of each other; at 72dp that reads as noise, and the gradient
    // clashed with the flat surfaces everywhere else in the app.
    Box(
        modifier = modifier
            .size(76.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(
                if (isPlaying) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onBackground
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = if (isPlaying) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.background
                },
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

@Composable
private fun LikeButton(
    isLiked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = remember { Animatable(1f) }
    val particles = remember { mutableStateListOf<Particle>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isLiked) {
        if (isLiked) {
            scope.launch {
                scale.animateTo(1.3f, tween(100))
                scale.animateTo(1f, spring(dampingRatio = 0.3f))
            }
            particles.clear()
            repeat(12) { i ->
                val angle = (i * 30f) * (Math.PI / 180f).toFloat()
                particles.add(
                    Particle(
                        angle = angle,
                        distance = 0f,
                        maxDistance = (30f + (i % 3) * 15f),
                        alpha = 1f
                    )
                )
            }
            launch {
                repeat(20) { frame ->
                    particles.forEachIndexed { index, particle ->
                        particles[index] = particle.copy(
                            distance = particle.maxDistance * (frame / 20f),
                            alpha = 1f - (frame / 20f)
                        )
                    }
                    delay(16)
                }
                particles.clear()
            }
        }
    }

    Box(modifier = modifier.size(40.dp), contentAlignment = Alignment.Center) {
        particles.forEach { particle ->
            val px = cos(particle.angle.toDouble()).toFloat() * particle.distance
            val py = sin(particle.angle.toDouble()).toFloat() * particle.distance
            Canvas(
                modifier = Modifier
                    .size(4.dp)
                    .offset {
                        IntOffset(x = px.roundToInt(), y = py.roundToInt())
                    }
            ) {
                drawCircle(
                    color = HeartRed.copy(alpha = particle.alpha),
                    radius = size.minDimension / 2
                )
            }
        }

        IconButton(onClick = onClick) {
            Icon(
                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Like",
                tint = if (isLiked) HeartRed else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    }
            )
        }
    }
}

private data class Particle(
    val angle: Float,
    val distance: Float,
    val maxDistance: Float,
    val alpha: Float
)

@Composable
private fun AnimatedToggleButton(
    isActive: Boolean,
    onClick: () -> Unit,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
    icon: @Composable (isActive: Boolean, color: Color) -> Unit
) {
    val animatedColor by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(300),
        label = "color"
    )

    val bgColor = lerpColor(inactiveColor.copy(alpha = 0.1f), activeColor.copy(alpha = 0.2f), animatedColor)
    val contentColor = lerpColor(inactiveColor, activeColor, animatedColor)
    val borderWidth by animateFloatAsState(
        targetValue = if (isActive) 2f else 0f,
        animationSpec = tween(300),
        label = "border"
    )

    Box(modifier = modifier) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(44.dp)
                .then(
                    if (isActive) Modifier.border(
                        borderWidth.dp,
                        activeColor.copy(alpha = 0.4f),
                        CircleShape
                    ) else Modifier
                )
                .background(bgColor, CircleShape)
        ) {
            icon(isActive, contentColor)
        }
    }
}

@Composable
private fun Scrubber(
    progress: Float,
    onSeek: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(progress) }
    val displayProgress = if (isDragging) dragProgress else progress

    // Read the colours outside the Canvas: its draw lambda is not a @Composable scope.
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val playedColor = MaterialTheme.colorScheme.onBackground

    // The previous control drew a bar per 80ms tick from a list of Math.random() amplitudes,
    // so the bar heights had no relationship to the audio. This scrubs the real position.
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
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
            val trackHeight = if (isDragging) 8.dp.toPx() else 6.dp.toPx()
            val centerY = size.height / 2
            val radius = CornerRadius(trackHeight / 2)

            // Unplayed portion
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, centerY - trackHeight / 2),
                size = Size(size.width, trackHeight),
                cornerRadius = radius
            )

            // Played portion
            if (displayProgress > 0f) {
                drawRoundRect(
                    color = playedColor,
                    topLeft = Offset(0f, centerY - trackHeight / 2),
                    size = Size(size.width * displayProgress, trackHeight),
                    cornerRadius = radius
                )
            }

            // Handle, visible only while the user is actually scrubbing
            if (isDragging) {
                val handleX = (displayProgress * size.width).coerceIn(0f, size.width)
                drawCircle(
                    color = Color.White,
                    radius = 7.dp.toPx(),
                    center = Offset(handleX, centerY)
                )
                drawCircle(
                    color = playedColor,
                    radius = 3.dp.toPx(),
                    center = Offset(handleX, centerY)
                )
            }
        }
    }
}

private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    return Color(
        red = start.red + (end.red - start.red) * fraction,
        green = start.green + (end.green - start.green) * fraction,
        blue = start.blue + (end.blue - start.blue) * fraction,
        alpha = start.alpha + (end.alpha - start.alpha) * fraction
    )
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
