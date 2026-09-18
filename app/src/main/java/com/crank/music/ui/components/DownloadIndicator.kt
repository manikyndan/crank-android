package com.crank.music.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.crank.music.domain.model.DownloadState
import com.crank.music.ui.theme.ChampagneGold
import com.crank.music.ui.theme.TextSecondary

@Composable
fun DownloadIndicator(
    state: DownloadState,
    modifier: Modifier = Modifier
) {
    when (state) {
        DownloadState.DOWNLOADING -> {
            val infiniteTransition = rememberInfiniteTransition(label = "downloading")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = LinearEasing)
                ),
                label = "rotation"
            )
            Icon(
                imageVector = Icons.Default.Downloading,
                contentDescription = "Downloading",
                tint = ChampagneGold,
                modifier = modifier
                    .size(22.dp)
                    .rotate(rotation)
            )
        }
        DownloadState.COMPLETED -> {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Downloaded",
                tint = ChampagneGold,
                modifier = modifier.size(22.dp)
            )
        }
        DownloadState.IDLE, DownloadState.FAILED -> {
            Icon(
                imageVector = Icons.Default.DownloadForOffline,
                contentDescription = "Not Downloaded",
                tint = TextSecondary,
                modifier = modifier.size(22.dp)
            )
        }
    }
}
