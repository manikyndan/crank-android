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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.crank.music.ui.components.PlaybackControlsSheet
import com.crank.music.ui.components.SleepTimerSheet
import com.crank.music.domain.model.DownloadState
import com.crank.music.ui.theme.CrankGold
import com.crank.music.ui.theme.CrankGoldBright
import com.crank.music.ui.theme.DeepSpaceNavy
import com.crank.music.ui.theme.HeartRed
import com.crank.music.ui.theme.MetallicGoldStart
import com.crank.music.ui.theme.NavyBlue
import com.crank.music.ui.theme.TextPrimary
import com.crank.music.ui.theme.TextSecondarySoft
import com.crank.music.ui.theme.WaveformActive
import com.crank.music.ui.theme.WaveformInactive
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

    val pointerOffsetX = remember { Animatable(0f) }
    val pointerOffsetY = remember { Animatable(0f) }

    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current

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

    val waveformAmplitudes = remember { mutableStateListOf<Float>() }
    LaunchedEffect(Unit) {
        while (true) {
            if (waveformAmplitudes.size > 60) waveformAmplitudes.removeAt(0)
            waveformAmplitudes.add(
                if (playerState.isPlaying) (0.2f + Math.random().toFloat() * 0.8f) else 0.15f
            )
            delay(80)
        }
    }

    // Pure Black background
    Box(modifier = Modifier.fillMaxSize().background(DeepSpaceNavy)) {
        // Blurred album art backdrop with navy overlay
        if (song?.artworkUrl.orEmpty().isNotBlank()) {
            AsyncImage(
                model = song?.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(80.dp)
                    .graphicsLayer { alpha = 0.3f }
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                DeepSpaceNavy.copy(alpha = 0.5f),
                                DeepSpaceNavy.copy(alpha = 0.9f),
                                DeepSpaceNavy
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
                        tint = TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Now Playing",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondarySoft
                    )
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(CrankGold)
                    )
                }
                Row {
                    IconButton(onClick = { showSleepTimerSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = "Sleep Timer",
                            tint = if (playerState.sleepTimerMinutes > 0) CrankGold else TextSecondarySoft,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(onClick = { showSpeedSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Speed",
                            tint = if (playerState.playbackSpeed != 1.0f) CrankGold else TextSecondarySoft,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Album Art with Navy Ambient Glow
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .graphicsLayer {
                        val rotX = (pointerOffsetY.value / 3f).coerceIn(-12f, 12f)
                        val rotY = (pointerOffsetX.value / 3f).coerceIn(-12f, 12f)
                        rotationX = rotX
                        rotationY = rotY
                        cameraDistance = 1200f * density.density
                        val scale = albumArtScale.value
                        scaleX = scale
                        scaleY = scale
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                pointerOffsetX.snapTo(
                                    (pointerOffsetX.value + dragAmount.x) * 0.9f
                                )
                                pointerOffsetY.snapTo(
                                    (pointerOffsetY.value + dragAmount.y) * 0.9f
                                )
                            }
                        }
                    }
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(20.dp),
                        spotColor = NavyBlue.copy(alpha = 0.5f),
                        ambientColor = CrankGold.copy(alpha = 0.15f)
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .background(NavyBlue),
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
                        tint = CrankGold,
                        modifier = Modifier.size(80.dp)
                    )
                }

                // LOSSLESS badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MetallicGoldStart.copy(alpha = 0.9f),
                                    CrankGold.copy(alpha = 0.9f)
                                )
                            ),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "LOSSLESS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.1.em,
                            color = DeepSpaceNavy
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Song Title (White Serif) + Artist (Gold Sans-Serif)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = song?.title ?: "No Song Selected",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    LikeButton(
                        isLiked = isLiked,
                        onClick = {
                            isLiked = !isLiked
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = song?.artistName ?: "Unknown Artist",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = MetallicGoldStart
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Waveform progress with gold active bars
            WaveformProgressBar(
                progress = progressFloat,
                amplitudes = waveformAmplitudes.toList(),
                onSeek = { percent ->
                    val newPos = (percent * playerState.duration).toLong()
                    playerViewModel.seekTo(newPos)
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatMs(playerState.progress),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondarySoft
                )
                Text(
                    text = formatMs(playerState.duration),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondarySoft
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Playback Controls — Large metallic gold
            Row(
                modifier = Modifier.fillMaxWidth(0.85f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedToggleButton(
                    isActive = playerState.shuffleModeEnabled,
                    onClick = { playerViewModel.toggleShuffle() },
                    activeColor = CrankGold,
                    inactiveColor = TextSecondarySoft
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
                        tint = CrankGold,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // 3D Embossed Metallic Gold Play/Pause
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
                        tint = CrankGold,
                        modifier = Modifier.size(36.dp)
                    )
                }

                AnimatedToggleButton(
                    isActive = playerState.repeatMode != Player.REPEAT_MODE_OFF,
                    onClick = { playerViewModel.toggleRepeatMode() },
                    activeColor = CrankGold,
                    inactiveColor = TextSecondarySoft
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

            // Bottom action icons (gold)
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
                        tint = CrankGold,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = onQueueClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Queue",
                        tint = CrankGold,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = onLyricsClick) {
                    Icon(
                        imageVector = Icons.Default.FormatQuote,
                        contentDescription = "Lyrics",
                        tint = CrankGold,
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
                            DownloadState.COMPLETED -> CrankGold
                            DownloadState.DOWNLOADING -> CrankGold.copy(alpha = 0.7f)
                            else -> CrankGold
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = onCrankAiClick) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Crank AI",
                        tint = CrankGold,
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

    // 3D Embossed Metallic Gold Play/Pause Button
    Box(
        modifier = modifier
            .size(72.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = 12.dp,
                shape = CircleShape,
                spotColor = CrankGold.copy(alpha = 0.4f),
                ambientColor = NavyBlue.copy(alpha = 0.3f)
            )
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        CrankGoldBright,
                        CrankGold,
                        MetallicGoldStart
                    )
                )
            )
            .drawBehind {
                // Embossed ring effect
                val stroke = 3.dp.toPx()
                val radius = (size.minDimension - stroke) / 2
                drawCircle(
                    color = Color.White.copy(alpha = 0.2f * animatedProgress),
                    radius = radius,
                    style = Stroke(stroke)
                )
                // Inner shadow
                drawCircle(
                    color = DeepSpaceNavy.copy(alpha = 0.15f * (1f - animatedProgress)),
                    radius = radius - stroke * 2,
                    style = Stroke(stroke * 0.5f)
                )
            },
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = DeepSpaceNavy,
                modifier = Modifier.size(38.dp)
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
                tint = if (isLiked) HeartRed else TextSecondarySoft,
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
private fun WaveformProgressBar(
    progress: Float,
    amplitudes: List<Float>,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(progress) }
    val displayProgress = if (isDragging) dragProgress else progress

    Canvas(
        modifier = modifier
            .height(48.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        isDragging = false
                        onSeek(dragProgress)
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        dragProgress = (dragProgress + dragAmount / size.width).coerceIn(0f, 1f)
                    }
                )
            }
    ) {
        val barWidth = 3.dp.toPx()
        val barGap = 2.dp.toPx()
        val totalBarWidth = barWidth + barGap
        val numBars = (size.width / totalBarWidth).toInt().coerceAtLeast(1)
        val centerY = size.height / 2

        for (i in 0 until numBars) {
            val x = i * totalBarWidth
            val normalizedIndex = i.toFloat() / numBars
            val isPlayed = normalizedIndex <= displayProgress

            val amplitude = if (i < amplitudes.size) amplitudes[i] else 0.3f
            val barHeight = amplitude * size.height * 0.85f

            val color = if (isPlayed) WaveformActive else WaveformInactive
            val alpha = if (isPlayed) 1f else 0.6f

            drawRoundRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(x, centerY - barHeight / 2),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2)
            )
        }

        if (isDragging) {
            val handleX = displayProgress * size.width
            drawCircle(
                color = CrankGold,
                radius = 6.dp.toPx(),
                center = Offset(handleX, centerY)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.3f),
                radius = 10.dp.toPx(),
                center = Offset(handleX, centerY)
            )
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
