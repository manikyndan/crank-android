package com.crank.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crank.music.ui.components.SettingsRow
import com.crank.music.ui.theme.ChampagneGold
import com.crank.music.ui.theme.CharcoalElevated
import com.crank.music.ui.theme.ObsidianBlack
import com.crank.music.ui.theme.TextSecondary
import com.crank.music.ui.theme.WarmWhite

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit = {},
    onEqualizerClick: () -> Unit = {},
    onCrankAiClick: () -> Unit = {},
    onAppearanceClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    onUpdateClick: () -> Unit = {},
    onPlaybackClick: () -> Unit = {},
    onAudioQualityClick: () -> Unit = {},
    showUpdateBadge: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = WarmWhite,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = WarmWhite
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                SettingsRow(
                    icon = Icons.Default.GraphicEq,
                    title = "Equalizer & DSP",
                    subtitle = "5-Band EQ, Bass Boost & Custom Presets",
                    onClick = onEqualizerClick
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Default.AutoAwesome,
                    title = "CRANK AI Discovery",
                    subtitle = "Conversational Mood & Playlist Assistant",
                    onClick = onCrankAiClick
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Default.Person,
                    title = "Account",
                    subtitle = "Profile & Settings",
                    onClick = { }
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Default.PlayCircle,
                    title = "Playback",
                    subtitle = "Crossfade, Gapless & Normalization",
                    onClick = onPlaybackClick
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Default.AudioFile,
                    title = "Audio Quality",
                    subtitle = "Hi-Res Lossless, Streaming & Equalizer",
                    onClick = onAudioQualityClick
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Default.Download,
                    title = "Downloads",
                    subtitle = "Offline Storage & Download Quality",
                    onClick = onDownloadsClick
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Default.Notifications,
                    title = "Notifications",
                    subtitle = "New Releases, Recommended & Alerts",
                    onClick = { }
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Default.Lock,
                    title = "Privacy",
                    subtitle = "Listening Activity & Analytics",
                    onClick = onPrivacyClick
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Default.DarkMode,
                    title = "Appearance",
                    subtitle = "Obsidian Dark & Custom Accent Themes",
                    onClick = onAppearanceClick
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUpdateClick() }
                        .padding(vertical = 10.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "About CRANK",
                        tint = ChampagneGold,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "About CRANK",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                            color = WarmWhite
                        )
                        Text(
                            text = "Version 1.0.0 (Build 1) - Check for Updates",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    if (showUpdateBadge) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = Color(0xFFFF5252),
                                    shape = CircleShape
                                )
                        )
                    }
                }
                HorizontalDivider(
                    color = CharcoalElevated,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(start = 52.dp)
                )
            }
        }
    }
}
