package com.crank.music.ui.screens

import java.util.Locale

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crank.music.ui.components.SectionHeader
import com.crank.music.ui.theme.ChampagneGold
import com.crank.music.ui.theme.CharcoalElevated
import com.crank.music.ui.theme.CharcoalSurface
import com.crank.music.ui.theme.ObsidianBlack
import com.crank.music.ui.theme.TextSecondary
import com.crank.music.ui.theme.TextTertiary
import com.crank.music.ui.theme.WarmWhite
import com.crank.music.ui.viewmodel.PlayerViewModel

/**
 * Playback settings that actually drive the player.
 *
 * ## What was wrong before
 *
 * This screen was backed by `PlaybackSettingsViewModel`, a second, completely disconnected copy of
 * the player: its own speed value, its own sleep timer and its own queue limits, with no reference to
 * [PlayerViewModel] or to ExoPlayer. Nothing it changed could reach playback, so every switch on the
 * screen — crossfade, gapless, normalization, target loudness, queue size, auto-play, car mode — was
 * cosmetic. Nothing on this screen was ever read by the audio engine.
 *
 * ## What is left, and why
 *
 * Only the two settings the engine can honour are here, both bound to the real player:
 *
 * - **Speed** → [PlayerViewModel.setPlaybackSpeed] (ExoPlayer's `PlaybackParameters`). ExoPlayer
 *   always time-stretches without changing pitch, so the old "Preserve Pitch" switch — which implied
 *   the alternative was chipmunk playback at 2x — has been removed rather than inverted into
 *   something meaningless.
 * - **Sleep timer** → [PlayerViewModel.setSleepTimer]. The countdown shown here is the player's own
 *   `remainingSleepTimeMs`, the same value Now Playing displays, so the two screens cannot disagree.
 *
 * Everything else was removed: crossfade needs a custom audio sink ExoPlayer does not provide,
 * gapless is already the default (so it is stated as fixed rather than offered as a switch), queue
 * size and auto-play-similar have no implementation in the queue, and car mode is not a mode this app
 * has. Normalization was driving the same `LoudnessEnhancer` as the equalizer's preamp and the two
 * overwrote each other; the preamp in the equalizer is the single surviving gain control.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSettingsScreen(
    playerViewModel: PlayerViewModel,
    onBackClick: () -> Unit = {}
) {
    val playerState by playerViewModel.playerState.collectAsStateWithLifecycle()
    val view = LocalView.current
    val buzz = { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }

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
                PlaybackSpeedSection(
                    speed = playerState.playbackSpeed,
                    onSpeedChange = {
                        playerViewModel.setPlaybackSpeed(it)
                        buzz()
                    }
                )
            }

            item {
                SleepTimerSection(
                    activeMinutes = playerState.sleepTimerMinutes,
                    remainingMs = playerState.remainingSleepTimeMs,
                    onSelectMinutes = {
                        playerViewModel.setSleepTimer(it)
                        buzz()
                    },
                    onCancel = {
                        playerViewModel.cancelSleepTimer()
                        buzz()
                    }
                )
            }

            item {
                FixedBehaviourSection()
            }
        }
    }
}

@Composable
private fun PlaybackSpeedSection(
    speed: Float,
    onSpeedChange: (Float) -> Unit
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
                        text = String.format(Locale.US, "%.2fx", speed),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ChampagneGold
                    )
                }

                Slider(
                    value = speed,
                    onValueChange = onSpeedChange,
                    valueRange = 0.5f..2.0f,
                    steps = 29,
                    colors = sliderColors()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0.5x", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    Text("2.0x", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(quickSpeeds) { quickSpeed ->
                        val isSelected = kotlin.math.abs(speed - quickSpeed) < 0.01f
                        ChoiceChip(
                            label = "${quickSpeed}x",
                            isSelected = isSelected,
                            onClick = { onSpeedChange(quickSpeed) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepTimerSection(
    activeMinutes: Int,
    remainingMs: Long,
    onSelectMinutes: (Int) -> Unit,
    onCancel: () -> Unit
) {
    // The player stores a minute count, not a named preset, so the chips are just shortcuts to
    // setSleepTimer(minutes) and the remaining time is reported by the timer itself.
    val presets = listOf("15 min" to 15, "30 min" to 30, "45 min" to 45, "1 hour" to 60, "2 hours" to 120)
    val isActive = activeMinutes > 0

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        SectionHeader(icon = Icons.Default.Timer, title = "Sleep Timer")
        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CharcoalSurface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (isActive) {
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
                                    text = "Timer active",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = ChampagneGold
                                )
                                Text(
                                    text = "${formatRemaining(remainingMs)} remaining",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextTertiary
                                )
                            }
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onCancel() },
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Cancel",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets) { (label, minutes) ->
                        ChoiceChip(
                            label = label,
                            isSelected = minutes == activeMinutes,
                            onClick = { onSelectMinutes(minutes) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Playback pauses when the timer runs out. It is not resumed automatically.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }
    }
}

/**
 * Behaviour that is not adjustable, stated as fact instead of as a switch.
 *
 * These rows used to be toggles that wrote to nothing. Gapless playback has no switch because it is
 * how ExoPlayer already behaves; crossfade has none because it is genuinely unavailable.
 */
@Composable
private fun FixedBehaviourSection() {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        SectionHeader(icon = Icons.Default.Info, title = "Fixed Behaviour")
        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CharcoalSurface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                FixedRow(title = "Gapless playback", value = "Always on")
                Spacer(modifier = Modifier.height(10.dp))
                FixedRow(title = "Crossfade", value = "Not supported")
                Spacer(modifier = Modifier.height(10.dp))
                FixedRow(title = "Queue length", value = "No limit")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Crossfade needs a custom audio sink the player does not use, so it cannot be " +
                "offered. Gapless playback is the default and cannot be turned off.",
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
        )
    }
}

@Composable
private fun FixedRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = WarmWhite
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = TextSecondary
        )
    }
}

@Composable
private fun ChoiceChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        color = if (isSelected) ChampagneGold else CharcoalElevated,
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (isSelected) ObsidianBlack else TextSecondary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = ChampagneGold,
    activeTrackColor = ChampagneGold,
    inactiveTrackColor = CharcoalElevated
)

private fun formatRemaining(remainingMs: Long): String {
    val totalSeconds = (remainingMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

// The private SectionHeader that lived here was one of five per-screen copies of the same
// Icon + title row. It is now the shared com.crank.music.ui.components.SectionHeader, so an
// icon-size or typography change lands on every settings screen at once instead of drifting.
