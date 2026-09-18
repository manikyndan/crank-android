package com.crank.music.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.crank.music.ui.theme.ChampagneGold
import com.crank.music.ui.theme.CharcoalElevated
import com.crank.music.ui.theme.CharcoalSurface
import com.crank.music.ui.theme.GoldDark
import com.crank.music.ui.theme.ObsidianBlack
import com.crank.music.ui.theme.TextSecondary
import com.crank.music.ui.theme.TextTertiary
import com.crank.music.ui.theme.WarmWhite
import com.crank.music.ui.viewmodel.MaxQueueSize
import com.crank.music.ui.viewmodel.NormalizationMode
import com.crank.music.ui.viewmodel.PlaybackSettingsViewModel
import com.crank.music.ui.viewmodel.SleepTimerPreset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSettingsScreen(
    viewModel: PlaybackSettingsViewModel = hiltViewModel(),
    onBackClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val view = LocalView.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
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
                text = "Playback Settings",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = WarmWhite
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                CrossfadeSection(
                    enabled = uiState.crossfadeEnabled,
                    duration = uiState.crossfadeDuration,
                    isPreviewPlaying = uiState.isPreviewPlaying,
                    onToggle = {
                        viewModel.setCrossfadeEnabled(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onDurationChange = { viewModel.setCrossfadeDuration(it) },
                    onPreviewToggle = {
                        viewModel.togglePreview()
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                )
            }

            item {
                GaplessSection(
                    enabled = uiState.gaplessEnabled,
                    onToggle = {
                        viewModel.setGaplessEnabled(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                )
            }

            item {
                NormalizationSection(
                    enabled = uiState.normalizationEnabled,
                    mode = uiState.normalizationMode,
                    targetLoudness = uiState.targetLoudness,
                    onToggle = {
                        viewModel.setNormalizationEnabled(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onModeSelect = { viewModel.setNormalizationMode(it) },
                    onLoudnessChange = { viewModel.setTargetLoudness(it) }
                )
            }

            item {
                PlaybackSpeedSection(
                    speed = uiState.playbackSpeed,
                    pitchPreserved = uiState.pitchPreserved,
                    onSpeedChange = { viewModel.setPlaybackSpeed(it) },
                    onPitchToggle = {
                        viewModel.setPitchPreserved(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                )
            }

            item {
                QueueManagementSection(
                    autoPlaySimilar = uiState.autoPlaySimilar,
                    addToHistory = uiState.addToHistoryQueue,
                    maxQueueSize = uiState.maxQueueSize,
                    onAutoPlayToggle = {
                        viewModel.setAutoPlaySimilar(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onHistoryToggle = {
                        viewModel.setAddToHistoryQueue(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onMaxSizeSelect = {
                        viewModel.setMaxQueueSize(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                )
            }

            item {
                SleepTimerSection(
                    isActive = uiState.sleepTimerActive,
                    activePreset = uiState.sleepTimerPreset,
                    fadeOutVolume = uiState.fadeOutVolume,
                    endOfSong = uiState.endOfSongOption,
                    onSelectPreset = {
                        viewModel.setSleepTimerPreset(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onCancel = {
                        viewModel.cancelSleepTimer()
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onFadeOutToggle = {
                        viewModel.setFadeOutVolume(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onEndOfSongToggle = {
                        viewModel.setEndOfSongOption(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                )
            }

            item {
                CarModeSection(
                    enabled = uiState.carModeEnabled,
                    autoEnable = uiState.carModeAutoEnable,
                    onToggle = {
                        viewModel.setCarModeEnabled(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onAutoEnableToggle = {
                        viewModel.setCarModeAutoEnable(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                )
            }
        }
    }
}

@Composable
private fun CrossfadeSection(
    enabled: Boolean,
    duration: Float,
    isPreviewPlaying: Boolean,
    onToggle: (Boolean) -> Unit,
    onDurationChange: (Float) -> Unit,
    onPreviewToggle: () -> Unit
) {
    val waveOffset = remember { Animatable(0f) }

    LaunchedEffect(isPreviewPlaying) {
        if (isPreviewPlaying) {
            while (true) {
                waveOffset.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(2000, easing = LinearEasing)
                )
                waveOffset.snapTo(0f)
            }
        }
    }

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        SectionHeader(icon = Icons.Default.MusicNote, title = "Crossfade")
        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CharcoalSurface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Crossfade",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = WarmWhite
                        )
                        Text(
                            text = "Smooth transition between songs",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBlack,
                            checkedTrackColor = ChampagneGold,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = CharcoalElevated
                        )
                    )
                }

                AnimatedVisibility(
                    visible = enabled,
                    enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                    exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(CharcoalElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val waveHeight = 20f
                                val baseY = size.height / 2

                                for (i in 0..40) {
                                    val x = (i * size.width / 40)
                                    val alpha1 = ((x / size.width) - waveOffset.value).coerceIn(0f, 1f)
                                    val alpha2 = 1f - alpha1

                                    if (alpha1 > 0f) {
                                        drawLine(
                                            color = ChampagneGold.copy(alpha = alpha1 * 0.8f),
                                            start = Offset(x, baseY - waveHeight * alpha1),
                                            end = Offset(x, baseY + waveHeight * alpha1),
                                            strokeWidth = 3.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                    }
                                    if (alpha2 > 0f) {
                                        drawLine(
                                            color = GoldDark.copy(alpha = alpha2 * 0.8f),
                                            start = Offset(x, baseY - waveHeight * alpha2 * 0.7f),
                                            end = Offset(x, baseY + waveHeight * alpha2 * 0.7f),
                                            strokeWidth = 2.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Song A",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = ChampagneGold,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                                Text(
                                    text = "${duration.toInt()}s",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = WarmWhite
                                )
                                Text(
                                    text = "Song B",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = GoldDark,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Slider(
                            value = duration,
                            onValueChange = onDurationChange,
                            valueRange = 0f..12f,
                            steps = 11,
                            colors = SliderDefaults.colors(
                                thumbColor = ChampagneGold,
                                activeTrackColor = ChampagneGold,
                                inactiveTrackColor = CharcoalElevated
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("0s", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                            Text("12s", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onPreviewToggle() },
                            color = if (isPreviewPlaying) ChampagneGold else CharcoalElevated,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isPreviewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = if (isPreviewPlaying) ObsidianBlack else ChampagneGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isPreviewPlaying) "Stop Preview" else "Preview Crossfade",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = if (isPreviewPlaying) ObsidianBlack else ChampagneGold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GaplessSection(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        SectionHeader(icon = Icons.AutoMirrored.Filled.QueueMusic, title = "Gapless Playback")
        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CharcoalSurface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    color = CharcoalElevated,
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.size(24.dp)) {
                            drawLine(
                                color = if (enabled) ChampagneGold else TextSecondary,
                                start = Offset(2f, size.height / 2),
                                end = Offset(size.width - 2f, size.height / 2),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            drawCircle(
                                color = if (enabled) ChampagneGold else TextSecondary,
                                radius = 2.dp.toPx(),
                                center = Offset(size.width / 2, size.height / 2)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Gapless Playback",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = WarmWhite
                    )
                    Text(
                        text = "Seamless transitions for albums (FLAC, ALAC)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianBlack,
                        checkedTrackColor = ChampagneGold,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = CharcoalElevated
                    )
                )
            }
        }
    }
}

@Composable
private fun NormalizationSection(
    enabled: Boolean,
    mode: NormalizationMode,
    targetLoudness: Float,
    onToggle: (Boolean) -> Unit,
    onModeSelect: (NormalizationMode) -> Unit,
    onLoudnessChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        SectionHeader(icon = Icons.Default.Speed, title = "Audio Normalization")
        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CharcoalSurface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Normalize Volume",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = WarmWhite
                        )
                        Text(
                            text = "Consistent volume across all songs",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBlack,
                            checkedTrackColor = ChampagneGold,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = CharcoalElevated
                        )
                    )
                }

                AnimatedVisibility(
                    visible = enabled,
                    enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                    exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Mode",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(NormalizationMode.entries.toList()) { modeOption ->
                                val isSelected = modeOption == mode
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onModeSelect(modeOption) },
                                    color = if (isSelected) ChampagneGold else CharcoalElevated,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = modeOption.label,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isSelected) ObsidianBlack else TextSecondary,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Target Loudness",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = "${targetLoudness.toInt()} LUFS",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = ChampagneGold
                            )
                        }

                        Slider(
                            value = targetLoudness,
                            onValueChange = onLoudnessChange,
                            valueRange = -14f..-8f,
                            steps = 5,
                            colors = SliderDefaults.colors(
                                thumbColor = ChampagneGold,
                                activeTrackColor = ChampagneGold,
                                inactiveTrackColor = CharcoalElevated
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("-14 LUFS", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                            Text("-8 LUFS", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackSpeedSection(
    speed: Float,
    pitchPreserved: Boolean,
    onSpeedChange: (Float) -> Unit,
    onPitchToggle: (Boolean) -> Unit
) {
    val quickSpeeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        SectionHeader(icon = Icons.Default.Speed, title = "Playback Speed")
        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CharcoalSurface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Speed",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = String.format("%.2fx", speed),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ChampagneGold
                    )
                }

                Slider(
                    value = speed,
                    onValueChange = onSpeedChange,
                    valueRange = 0.5f..2.0f,
                    steps = 29,
                    colors = SliderDefaults.colors(
                        thumbColor = ChampagneGold,
                        activeTrackColor = ChampagneGold,
                        inactiveTrackColor = CharcoalElevated
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0.5x", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    Text("2.0x", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(quickSpeeds) { quickSpeed ->
                        val isSelected = kotlin.math.abs(speed - quickSpeed) < 0.01f
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSpeedChange(quickSpeed) },
                            color = if (isSelected) ChampagneGold else CharcoalElevated,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "${quickSpeed}x",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) ObsidianBlack else TextSecondary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPitchToggle(!pitchPreserved) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Preserve Pitch",
                            style = MaterialTheme.typography.titleSmall,
                            color = WarmWhite
                        )
                        Text(
                            text = "Maintain original pitch at different speeds",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary
                        )
                    }
                    Switch(
                        checked = pitchPreserved,
                        onCheckedChange = onPitchToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBlack,
                            checkedTrackColor = ChampagneGold,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = CharcoalElevated
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueManagementSection(
    autoPlaySimilar: Boolean,
    addToHistory: Boolean,
    maxQueueSize: MaxQueueSize,
    onAutoPlayToggle: (Boolean) -> Unit,
    onHistoryToggle: (Boolean) -> Unit,
    onMaxSizeSelect: (MaxQueueSize) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        SectionHeader(icon = Icons.AutoMirrored.Filled.QueueMusic, title = "Queue Management")
        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CharcoalSurface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column {
                QueueToggleRow(
                    title = "Auto-play similar songs",
                    subtitle = "Keep the vibe going with AI suggestions",
                    isEnabled = autoPlaySimilar,
                    onToggle = onAutoPlayToggle
                )
                QueueToggleRow(
                    title = "Add played songs to history",
                    subtitle = "Track your listening history",
                    isEnabled = addToHistory,
                    onToggle = onHistoryToggle,
                    showDivider = false
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CharcoalSurface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Max Queue Size",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = WarmWhite
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(MaxQueueSize.entries.toList()) { size ->
                        val isSelected = size == maxQueueSize
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onMaxSizeSelect(size) },
                            color = if (isSelected) ChampagneGold else CharcoalElevated,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = size.label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) ObsidianBlack else TextSecondary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueToggleRow(
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    showDivider: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = WarmWhite
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianBlack,
                checkedTrackColor = ChampagneGold,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = CharcoalElevated
            )
        )
    }
}

@Composable
private fun SleepTimerSection(
    isActive: Boolean,
    activePreset: SleepTimerPreset?,
    fadeOutVolume: Boolean,
    endOfSong: Boolean,
    onSelectPreset: (SleepTimerPreset) -> Unit,
    onCancel: () -> Unit,
    onFadeOutToggle: (Boolean) -> Unit,
    onEndOfSongToggle: (Boolean) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        SectionHeader(icon = Icons.Default.Timer, title = "Sleep Timer")
        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CharcoalSurface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (isActive && activePreset != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = ChampagneGold.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = ChampagneGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Timer Active",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = ChampagneGold
                                )
                                Text(
                                    text = "${activePreset.label} remaining",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextTertiary
                                )
                            }
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onCancel() },
                                color = Color(0xFFFF5252).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Cancel",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFFF5252),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(SleepTimerPreset.entries.toList()) { preset ->
                        val isSelected = preset == activePreset
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelectPreset(preset) },
                            color = if (isSelected) ChampagneGold else CharcoalElevated,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) ObsidianBlack else TextSecondary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                SleepToggleRow(
                    title = "Fade out volume",
                    isEnabled = fadeOutVolume,
                    onToggle = onFadeOutToggle
                )
                SleepToggleRow(
                    title = "At end of current song",
                    isEnabled = endOfSong,
                    onToggle = onEndOfSongToggle,
                    showDivider = false
                )
            }
        }
    }
}

@Composable
private fun SleepToggleRow(
    title: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    showDivider: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isEnabled) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = WarmWhite,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianBlack,
                checkedTrackColor = ChampagneGold,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = CharcoalElevated
            )
        )
    }
}

@Composable
private fun CarModeSection(
    enabled: Boolean,
    autoEnable: Boolean,
    onToggle: (Boolean) -> Unit,
    onAutoEnableToggle: (Boolean) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        SectionHeader(icon = Icons.Default.Bluetooth, title = "Car Mode")
        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CharcoalSurface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        color = CharcoalElevated,
                        shape = CircleShape
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = if (enabled) ChampagneGold else TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Car Mode",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = WarmWhite
                        )
                        Text(
                            text = "Simplified UI with larger buttons",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary
                        )
                    }

                    Switch(
                        checked = enabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBlack,
                            checkedTrackColor = ChampagneGold,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = CharcoalElevated
                        )
                    )
                }

                AnimatedVisibility(
                    visible = enabled,
                    enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                    exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAutoEnableToggle(!autoEnable) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-enable on Bluetooth",
                                style = MaterialTheme.typography.titleSmall,
                                color = WarmWhite
                            )
                            Text(
                                text = "Detect car Bluetooth connection",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary
                            )
                        }
                        Switch(
                            checked = autoEnable,
                            onCheckedChange = onAutoEnableToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ObsidianBlack,
                                checkedTrackColor = ChampagneGold,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = CharcoalElevated
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ChampagneGold,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = WarmWhite
        )
    }
}
