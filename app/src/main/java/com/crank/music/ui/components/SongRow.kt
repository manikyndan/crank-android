package com.crank.music.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.crank.music.domain.model.Song
import com.crank.music.ui.theme.CrankGold
import com.crank.music.ui.theme.CrankGoldBright
import com.crank.music.ui.theme.WarmWhite
import com.crank.music.ui.theme.TextSecondary
import com.crank.music.ui.theme.CharcoalElevated

@Composable
fun SongRow(
    song: Song,
    modifier: Modifier = Modifier,
    isCurrentlyPlaying: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .then(
                if (isCurrentlyPlaying) Modifier.background(CrankGold.copy(alpha = 0.08f))
                else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(CharcoalElevated),
                contentAlignment = Alignment.Center
            ) {
                if (song.artworkUrl.isNotBlank()) {
                    AsyncImage(
                        model = song.artworkUrl,
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Song Placeholder",
                        tint = CrankGold,
                        modifier = Modifier.size(28.dp)
                    )
                }

                if (isCurrentlyPlaying && isPlaying) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                            .size(16.dp)
                            .background(CrankGold.copy(alpha = 0.9f), RoundedCornerShape(3.dp))
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        MiniEqualizerBars(isPlaying = true)
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isCurrentlyPlaying) CrankGoldBright else WarmWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artistName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isCurrentlyPlaying) CrankGold.copy(alpha = 0.8f) else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        HorizontalDivider(
            color = CharcoalElevated,
            thickness = 0.8.dp
        )
    }
}

@Composable
fun MiniEqualizerBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = CrankGoldBright
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mini_eq")

    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    val bar1Height = if (isPlaying) bar1 else 0.3f
    val bar2Height = if (isPlaying) bar2 else 0.2f
    val bar3Height = if (isPlaying) bar3 else 0.4f

    Canvas(modifier = modifier) {
        val barWidth = size.width / 5f
        val gap = barWidth * 0.3f
        val maxHeight = size.height

        drawRoundRect(
            color = activeColor,
            topLeft = Offset(0f, maxHeight * (1f - bar1Height)),
            size = Size(barWidth, maxHeight * bar1Height),
            cornerRadius = CornerRadius(barWidth / 3)
        )
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(barWidth + gap, maxHeight * (1f - bar2Height)),
            size = Size(barWidth, maxHeight * bar2Height),
            cornerRadius = CornerRadius(barWidth / 3)
        )
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(2 * (barWidth + gap), maxHeight * (1f - bar3Height)),
            size = Size(barWidth, maxHeight * bar3Height),
            cornerRadius = CornerRadius(barWidth / 3)
        )
    }
}
