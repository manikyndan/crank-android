package com.crank.music.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.crank.music.domain.model.Song
import com.crank.music.ui.viewmodel.BackgroundMood
import com.crank.music.ui.viewmodel.ChatMessage
import com.crank.music.ui.theme.ChampagneGold
import com.crank.music.ui.theme.CharcoalElevated
import com.crank.music.ui.theme.CharcoalSurface
import com.crank.music.ui.theme.GoldDark
import com.crank.music.ui.theme.GoldMuted
import com.crank.music.ui.theme.ObsidianBlack
import com.crank.music.ui.theme.TextSecondary
import com.crank.music.ui.theme.TextTertiary
import com.crank.music.ui.theme.WarmWhite
import com.crank.music.ui.viewmodel.CrankAiViewModel
import com.crank.music.ui.viewmodel.Sender
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CrankAIScreen(
    crankAiViewModel: CrankAiViewModel = hiltViewModel(),
    onSongSelect: (Song) -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    val uiState by crankAiViewModel.uiState.collectAsState()
    val view = LocalView.current
    // Read the accent here: a drawBehind lambda is not a @Composable scope.
    val accentColor = ChampagneGold
    val accentDark = GoldDark
    val listState = rememberLazyListState()
    var showInput by remember { mutableStateOf(true) }

    val backgroundBrush = when (uiState.backgroundMood) {
        BackgroundMood.RAIN_NIGHT -> Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0A1628),
                Color(0xFF0D1F3C),
                Color(0xFF060E1A),
                ObsidianBlack
            )
        )
        BackgroundMood.WORKOUT -> Brush.verticalGradient(
            colors = listOf(
                Color(0xFF2A0A0A),
                Color(0xFF1A0505),
                Color(0xFF0A0202),
                ObsidianBlack
            )
        )
        BackgroundMood.PARTY -> Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1A0A2A),
                Color(0xFF100520),
                Color(0xFF050210),
                ObsidianBlack
            )
        )
        BackgroundMood.FOCUS -> Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0A1A1A),
                Color(0xFF051010),
                ObsidianBlack
            )
        )
        BackgroundMood.SLEEP -> Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0A0A1A),
                Color(0xFF050510),
                ObsidianBlack
            )
        )
        else -> Brush.verticalGradient(
            colors = listOf(ObsidianBlack, ObsidianBlack)
        )
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
        )

        if (uiState.backgroundMood == BackgroundMood.RAIN_NIGHT) {
            RainOverlay()
        }
        if (uiState.backgroundMood == BackgroundMood.WORKOUT) {
            PulseOverlay()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = WarmWhite,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "CRANK AI",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = WarmWhite
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = ChampagneGold,
                    modifier = Modifier.size(22.dp)
                )
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(crankAiViewModel.quickActions) { action ->
                    QuickActionChip(
                        action = action,
                        onClick = {
                            crankAiViewModel.onQuickAction(action)
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Chill",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    modifier = Modifier.width(32.dp)
                )
                Slider(
                    value = uiState.moodValue,
                    onValueChange = { crankAiViewModel.onMoodChanged(it) },
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = ChampagneGold,
                        activeTrackColor = ChampagneGold,
                        inactiveTrackColor = CharcoalElevated
                    )
                )
                Text(
                    text = "Hype",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    modifier = Modifier.width(36.dp)
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = uiState.messages,
                    key = { it.id }
                ) { msg ->
                    AnimatedChatBubble(
                        message = msg,
                        onSongSelect = onSongSelect,
                        isPlaying = false,
                        onPlayPause = { onSongSelect(it) }
                    )
                }

                if (uiState.isThinking) {
                    item {
                        TypingIndicator()
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    ),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            crankAiViewModel.toggleRecording()
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Input",
                            tint = if (uiState.isRecording) Color.Red else TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(GlassSurface)
                            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (uiState.currentInput.isEmpty()) {
                            Text(
                                text = "Mood, genre, or vibe...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextTertiary
                            )
                        }
                        BasicTextField(
                            value = uiState.currentInput,
                            onValueChange = { crankAiViewModel.onInputChanged(it) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = WarmWhite),
                            cursorBrush = SolidColor(ChampagneGold),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    FloatingActionButton(
                        onClick = {
                            crankAiViewModel.sendMessage()
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        },
                        shape = CircleShape,
                        containerColor = Color.Transparent,
                        contentColor = ObsidianBlack,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .size(48.dp)
                            .drawBehind {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFFFFC107),
                                            accentColor,
                                            accentDark
                                        )
                                    )
                                )
                            }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = ObsidianBlack,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

private val GlassSurface = Color(0x1AFFFFFF)
private val GlassBorder = Color(0x33FFFFFF)

@Composable
private fun AnimatedChatBubble(
    message: com.crank.music.ui.viewmodel.ChatMessage,
    onSongSelect: (Song) -> Unit,
    isPlaying: Boolean,
    onPlayPause: (Song) -> Unit
) {
    val isUser = message.sender == Sender.USER
    val animatedAlpha = remember { Animatable(0f) }
    val animatedOffsetY = remember { Animatable(20f) }

    LaunchedEffect(message.id) {
        launch {
            animatedAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(300, easing = LinearEasing)
            )
        }
        launch {
            animatedOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = animatedAlpha.value
                translationY = animatedOffsetY.value
            },
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    if (isUser) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
                    else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
                )
                .background(
                    if (isUser) Brush.horizontalGradient(
                        colors = listOf(ChampagneGold, Color(0xFFFFC107))
                    ) else SolidColor(CharcoalSurface)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) ObsidianBlack else WarmWhite
            )
        }

        if (message.songs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                message.songs.forEach { song ->
                    InlineSongCard(
                        song = song,
                        onPlay = {
                            onSongSelect(song)
                            onPlayPause(song)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineSongCard(
    song: Song,
    onPlay: () -> Unit
) {
    Surface(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onPlay() },
        color = CharcoalElevated,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CharcoalSurface),
                contentAlignment = Alignment.Center
            ) {
                if (song.artworkUrl.isNotBlank()) {
                    AsyncImage(
                        model = song.artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = ChampagneGold,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = WarmWhite,
                    maxLines = 1
                )
                Text(
                    text = song.artistName,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1
                )
            }

            FloatingActionButton(
                onClick = onPlay,
                shape = CircleShape,
                containerColor = ChampagneGold,
                contentColor = ObsidianBlack,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickActionChip(
    action: com.crank.music.ui.viewmodel.QuickAction,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        color = CharcoalSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = action.icon, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = action.label,
                style = MaterialTheme.typography.labelLarge,
                color = WarmWhite
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    val note1Y by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "note1"
    )

    val note2Y by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "note2"
    )

    val note3Y by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "note3"
    )

    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot"
    )

    Row(
        modifier = Modifier.padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "♪",
            style = MaterialTheme.typography.titleMedium,
            color = ChampagneGold.copy(alpha = dotAlpha),
            modifier = Modifier.offset(y = note1Y.dp)
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = "♫",
            style = MaterialTheme.typography.titleMedium,
            color = GoldMuted.copy(alpha = dotAlpha),
            modifier = Modifier.offset(y = note2Y.dp)
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = "♪",
            style = MaterialTheme.typography.titleMedium,
            color = ChampagneGold.copy(alpha = dotAlpha),
            modifier = Modifier.offset(y = note3Y.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "CRANK AI is thinking...",
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary
        )
    }
}

@Composable
private fun RainOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "rain")
    val rainOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rain_offset"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .blur(1.dp)
    ) {
        val rainDrops = 40
        for (i in 0 until rainDrops) {
            val x = (i * 31.7f) % size.width
            val baseY = ((rainOffset * size.height * 2 + i * 47.3f) % (size.height * 1.5f)) - size.height * 0.25f
            val length = 8.dp.toPx() + (i % 3) * 4.dp.toPx()

            drawLine(
                color = Color(0xFF4488BB).copy(alpha = 0.15f + (i % 5) * 0.02f),
                start = Offset(x, baseY),
                end = Offset(x - 2.dp.toPx(), baseY + length),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

@Composable
private fun PulseOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFF4444).copy(alpha = pulseAlpha),
                            Color.Transparent
                        ),
                        center = Offset(size.width / 2, size.height * 0.3f),
                        radius = size.width * 0.8f
                    )
                )
            }
    )
}
