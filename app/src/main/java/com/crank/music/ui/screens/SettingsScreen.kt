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
import androidx.compose.material.icons.filled.LibraryMusic
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
import com.crank.music.ui.components.GroupedSettingsSection
import com.crank.music.ui.components.SettingsNavRow
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
    onSourcesClick: () -> Unit = {},
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
                GroupedSettingsSection(title = "Audio") {
                    SettingsNavRow(
                        icon = Icons.Default.GraphicEq,
                        title = "Equalizer & DSP",
                        subtitle = "5-Band EQ, Bass Boost & Custom Presets",
                        onClick = onEqualizerClick,
                    )
                    SettingsNavRow(
                        icon = Icons.Default.AudioFile,
                        title = "Audio Quality",
                        subtitle = "Hi-Res Lossless, Streaming & Equalizer",
                        onClick = onAudioQualityClick,
                    )
                    SettingsNavRow(
                        icon = Icons.Default.PlayCircle,
                        title = "Playback",
                        subtitle = "Crossfade, Gapless & Normalization",
                        onClick = onPlaybackClick,
                    )
                    SettingsNavRow(
                        // Core-set icon, guaranteed to exist: this screen is about where music comes from.
                        icon = Icons.Default.LibraryMusic,
                        title = "Music Sources",
                        subtitle = "Optional self-hosted Gaana catalogue",
                        onClick = onSourcesClick,
                        showDivider = false,
                    )
                }
            }
            item {
                GroupedSettingsSection(title = "Library") {
                    SettingsNavRow(
                        icon = Icons.Default.Download,
                        title = "Downloads",
                        subtitle = "Offline Storage & Download Quality",
                        onClick = onDownloadsClick,
                    )
                    SettingsNavRow(
                        icon = Icons.Default.AutoAwesome,
                        title = "CRANK AI Discovery",
                        subtitle = "Conversational Mood & Playlist Assistant",
                        onClick = onCrankAiClick,
                        showDivider = false,
                    )
                }
            }
            item {
                GroupedSettingsSection(title = "Account & Privacy") {
                    SettingsNavRow(
                        icon = Icons.Default.Person,
                        title = "Account",
                        subtitle = "Profile & Settings",
                        onClick = { },
                    )
                    SettingsNavRow(
                        icon = Icons.Default.Lock,
                        title = "Privacy",
                        subtitle = "Listening Activity & Analytics",
                        onClick = onPrivacyClick,
                        showDivider = false,
                    )
                }
            }
            item {
                GroupedSettingsSection(title = "Appearance & Alerts") {
                    SettingsNavRow(
                        icon = Icons.Default.DarkMode,
                        title = "Appearance",
                        subtitle = "Obsidian Dark & Custom Accent Themes",
                        onClick = onAppearanceClick,
                    )
                    SettingsNavRow(
                        icon = Icons.Default.Notifications,
                        title = "Notifications",
                        subtitle = "New Releases, Recommended & Alerts",
                        onClick = { },
                        showDivider = false,
                    )
                }
            }
            item {
                // The update badge becomes a trailing label rather than a floating dot: same
                // information, but it sits on the row's baseline instead of hovering beside it.
                GroupedSettingsSection(title = "About") {
                    SettingsNavRow(
                        icon = Icons.Default.Info,
                        title = "About Crank Music",
                        subtitle = "Version 1.0.0 (Build 1) - Check for Updates",
                        trailingValue = if (showUpdateBadge) "Update available" else null,
                        onClick = onUpdateClick,
                        showDivider = false,
                    )
                }
            }
        }
    }
}
