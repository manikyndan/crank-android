package com.crank.music.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.crank.music.ui.theme.CrankGold
import com.crank.music.ui.theme.DeepSpaceNavy

/**
 * Gradient shimmer used for loading skeletons.
 *
 * ## Why this file is now 40 lines instead of 775
 *
 * It used to hold twelve "micro-interaction" composables — an animated play/pause morph, a particle
 * burst, confetti, a shake modifier, a success checkmark, a slide-up toast, a pulsing glow, a
 * staggered fade-in, a gold overscroll glow and a red error flash. None of them had a single caller
 * anywhere in the app: they were written, previewed and abandoned, and the build carried them plus
 * their imports on every compile.
 *
 * `ShimmerBox` is the one that is used (Explore, Library and the YouTube Music skeleton rows), so it
 * is the one that stays. The dead ones were removed rather than left as an obvious-looking toolkit
 * that a future change would wire up without checking whether the animation is actually wanted.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shimmerColor: Color = CrankGold.copy(alpha = 0.1f),
    baseColor: Color = DeepSpaceNavy
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val translateX by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 300f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    Box(
        modifier = modifier
            .background(baseColor)
            .drawBehind {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            baseColor,
                            shimmerColor,
                            baseColor
                        ),
                        startX = translateX - 150f,
                        endX = translateX + 150f
                    )
                )
            }
    )
}
