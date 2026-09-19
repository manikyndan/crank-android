package com.crank.music.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crank.music.ui.theme.AmberGlow
import com.crank.music.ui.theme.CrankGold
import com.crank.music.ui.theme.CrankGoldBright
import com.crank.music.ui.theme.DeepSpaceNavy
import com.crank.music.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ─── Play/Pause Morphing Animation ───
@Composable
fun AnimatedPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 80.dp,
    backgroundColor: Color = CrankGold,
    iconColor: Color = DeepSpaceNavy
) {
    val view = LocalView.current
    val scale = remember { Animatable(1f) }
    val rotation by animateFloatAsState(
        targetValue = if (isPlaying) 0f else 180f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "rotation"
    )

    LaunchedEffect(isPlaying) {
        scale.animateTo(
            targetValue = 0.85f,
            animationSpec = tween(100)
        )
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.4f,
                stiffness = Spring.StiffnessHigh
            )
        )
    }

    Surface(
        modifier = modifier
            .size(size)
            .scale(scale.value)
            .graphicsLayer { this.rotationY = rotation }
            .drawBehind {
                drawCircle(
                    color = backgroundColor.copy(alpha = 0.15f),
                    radius = size.toPx() * 0.6f
                )
            },
        shape = CircleShape,
        color = backgroundColor,
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = iconColor,
                modifier = Modifier
                    .size(size * 0.45f)
                    .graphicsLayer { this.rotationY = rotation }
            )
        }
    }
}

// ─── Like/Heart Button with Particle Burst ───
@Composable
fun AnimatedLikeButton(
    isLiked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 48.dp
) {
    val view = LocalView.current
    val scale = remember { Animatable(1f) }
    var showParticles by remember { mutableStateOf(false) }
    val particleSeed = remember { Random.nextInt() }

    val heartColor by animateColorAsState(
        targetValue = if (isLiked) CrankGoldBright else Color.Transparent,
        animationSpec = tween(200),
        label = "heart_color"
    )

    val outlineColor by animateColorAsState(
        targetValue = if (isLiked) CrankGoldBright else CrankGold.copy(alpha = 0.5f),
        animationSpec = tween(200),
        label = "outline_color"
    )

    LaunchedEffect(isLiked) {
        if (isLiked) {
            showParticles = true
            scale.animateTo(
                targetValue = 1.35f,
                animationSpec = tween(100)
            )
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.3f,
                    stiffness = Spring.StiffnessMedium
                )
            )
            delay(400)
            showParticles = false
        }
    }

    Box(
        modifier = modifier.size(size + 40.dp),
        contentAlignment = Alignment.Center
    ) {
        if (showParticles) {
            ParticleBurst(
                particleCount = 7,
                colors = listOf(CrankGoldBright, CrankGold, AmberGlow),
                modifier = Modifier.size(size * 2f),
                seed = particleSeed
            )
        }

        Surface(
            modifier = Modifier
                .size(size)
                .scale(scale.value),
            shape = CircleShape,
            color = Color.Transparent,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onToggle()
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(size * 0.6f)) {
                    val w = size.toPx() * 0.6f
                    val h = size.toPx() * 0.6f
                    val heartPath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w / 2, h * 0.35f)
                        cubicTo(w * 0.1f, 0f, 0f, h * 0.6f, w / 2, h)
                        cubicTo(w, h * 0.6f, w * 0.9f, 0f, w / 2, h * 0.35f)
                        close()
                    }
                    drawPath(
                        path = heartPath,
                        color = outlineColor,
                        style = Stroke(width = 3.dp.toPx())
                    )
                    drawPath(
                        path = heartPath,
                        color = heartColor
                    )
                }
            }
        }
    }
}

// ─── Shuffle/Repeat Toggle Button ───
@Composable
fun AnimatedToggleButton(
    isActive: Boolean,
    onClick: () -> Unit,
    activeIcon: @Composable () -> Unit,
    inactiveIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 32.dp
) {
    val view = LocalView.current
    val scale = remember { Animatable(1f) }
    val iconAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.5f,
        animationSpec = tween(200),
        label = "alpha"
    )

    LaunchedEffect(isActive) {
        if (isActive) {
            scale.animateTo(
                targetValue = 1.15f,
                animationSpec = tween(100)
            )
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.5f,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    Surface(
        modifier = modifier
            .size(size + 16.dp)
            .scale(scale.value),
        shape = CircleShape,
        color = Color.Transparent,
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.alpha(iconAlpha)
        ) {
            if (isActive) activeIcon() else inactiveIcon()
        }
    }
}

// ─── Particle Burst Effect ───
@Composable
fun ParticleBurst(
    particleCount: Int,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    seed: Int = 0
) {
    val particles = remember {
        List(particleCount) { i ->
            val random = Random(seed + i)
            ParticleData(
                angle = random.nextFloat() * 360f,
                speed = 2f + random.nextFloat() * 4f,
                size = 3f + random.nextFloat() * 5f,
                color = colors[random.nextInt(colors.size)],
                delayMs = random.nextLong(100)
            )
        }
    }

    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(500, easing = FastOutSlowInEasing)
        )
    }

    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val maxRadius = size.width / 2

        particles.forEach { particle ->
            val progress = (animProgress.value - particle.delayMs / 1000f).coerceIn(0f, 1f)
            if (progress > 0f) {
                val radius = maxRadius * progress * (particle.speed / 6f)
                val angleRad = Math.toRadians(particle.angle.toDouble())
                val x = centerX + (radius * cos(angleRad)).toFloat()
                val y = centerY + (radius * sin(angleRad)).toFloat()
                val alpha = (1f - progress).coerceIn(0f, 1f)
                val particleScale = (1f - progress * 0.5f).coerceIn(0.3f, 1f)

                drawCircle(
                    color = particle.color.copy(alpha = alpha),
                    radius = particle.size.dp.toPx() * particleScale,
                    center = Offset(x, y)
                )
            }
        }
    }
}

private data class ParticleData(
    val angle: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val delayMs: Long
)

// ─── Success Checkmark Animation ───
@Composable
fun SuccessCheckmark(
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 80.dp,
    // A default argument is evaluated outside the composition, so it cannot read a
    // theme-aware token. This is the dark-theme success green; callers may override it.
    color: Color = Color(0xFF30D158)
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(500, easing = FastOutSlowInEasing)
            )
        } else {
            progress.snapTo(0f)
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = scaleIn(initialScale = 0.5f) + fadeIn(),
        exit = scaleOut(targetScale = 0.5f) + fadeOut(),
        modifier = modifier
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokeWidth = 4.dp.toPx()
            val padding = size.toPx() * 0.2f

            drawArc(
                color = color.copy(alpha = 0.2f),
                startAngle = 0f,
                sweepAngle = 360f * progress.value,
                useCenter = false,
                style = Stroke(strokeWidth),
                topLeft = Offset(padding, padding),
                size = androidx.compose.ui.geometry.Size(
                    size.toPx() - padding * 2,
                    size.toPx() - padding * 2
                )
            )

            if (progress.value > 0.5f) {
                val checkProgress = ((progress.value - 0.5f) * 2f).coerceIn(0f, 1f)
                val cx = size.toPx() / 2
                val cy = size.toPx() / 2
                val w = size.toPx() * 0.15f

                val p1 = Offset(cx - w, cy)
                val p2 = Offset(cx - w * 0.3f, cy + w * 0.8f)
                val p3 = Offset(cx + w, cy - w * 0.6f)

                drawLine(
                    color = color,
                    start = p1,
                    end = if (checkProgress < 0.5f) {
                        Offset(
                            p1.x + (p2.x - p1.x) * checkProgress * 2,
                            p1.y + (p2.y - p1.y) * checkProgress * 2
                        )
                    } else {
                        p2
                    },
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )

                if (checkProgress > 0.5f) {
                    val line2Progress = (checkProgress - 0.5f) * 2f
                    drawLine(
                        color = color,
                        start = p2,
                        end = Offset(
                            p2.x + (p3.x - p2.x) * line2Progress,
                            p2.y + (p3.y - p2.y) * line2Progress
                        ),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

// ─── Shake Animation (for errors) ───
@Composable
fun ShakeModifier(
    isShaking: Boolean,
    modifier: Modifier = Modifier
): Modifier {
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(isShaking) {
        if (isShaking) {
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 400
                    0f at 0
                    (-12f) at 50
                    12f at 100
                    (-10f) at 150
                    10f at 200
                    (-6f) at 250
                    6f at 300
                    0f at 400
                }
            )
        }
    }

    return modifier.graphicsLayer {
        translationX = shakeOffset.value
    }
}

// ─── Confetti Burst ───
@Composable
fun ConfettiBurst(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val confettiColors = listOf(
        CrankGoldBright, CrankGold, AmberGlow,
        Color(0xFFE53935), Color(0xFF43A047), Color(0xFF1E88E5)
    )

    val particles = remember {
        List(30) { i ->
            val random = Random(i)
            ConfettiParticle(
                x = random.nextFloat(),
                y = -0.1f,
                velocityX = (random.nextFloat() - 0.5f) * 2f,
                velocityY = 1f + random.nextFloat() * 3f,
                rotation = random.nextFloat() * 360f,
                rotationSpeed = (random.nextFloat() - 0.5f) * 720f,
                color = confettiColors[random.nextInt(confettiColors.size)],
                size = 4f + random.nextFloat() * 6f,
                shape = if (random.nextBoolean()) ConfettiShape.CIRCLE else ConfettiShape.RECTANGLE
            )
        }
    }

    var animProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(isActive) {
        if (isActive) {
            animProgress = 0f
            val startTime = System.currentTimeMillis()
            while (animProgress < 1f) {
                delay(16)
                animProgress = ((System.currentTimeMillis() - startTime) / 1500f).coerceIn(0f, 1f)
            }
        }
    }

    if (isActive && animProgress < 1f) {
        Canvas(modifier = modifier.fillMaxSize()) {
            particles.forEach { particle ->
                val t = animProgress
                val x = (particle.x + particle.velocityX * t) * size.width
                val y = (particle.y + particle.velocityY * t + 2f * t * t) * size.height
                val alpha = (1f - t).coerceIn(0f, 1f)

                when (particle.shape) {
                    ConfettiShape.CIRCLE -> {
                        drawCircle(
                            color = particle.color.copy(alpha = alpha),
                            radius = particle.size,
                            center = Offset(x, y)
                        )
                    }
                    ConfettiShape.RECTANGLE -> {
                        drawRect(
                            color = particle.color.copy(alpha = alpha),
                            topLeft = Offset(x - particle.size / 2, y - particle.size / 2),
                            size = androidx.compose.ui.geometry.Size(particle.size, particle.size * 1.5f)
                        )
                    }
                }
            }
        }
    }
}

private enum class ConfettiShape { CIRCLE, RECTANGLE }

private data class ConfettiParticle(
    val x: Float,
    val y: Float,
    val velocityX: Float,
    val velocityY: Float,
    val rotation: Float,
    val rotationSpeed: Float,
    val color: Color,
    val size: Float,
    val shape: ConfettiShape
)

// ─── Toast Notification Slide-Up ───
@Composable
fun SlideUpToast(
    message: String,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = DeepSpaceNavy,
    textColor: Color = TextPrimary,
    accentColor: Color = CrankGold
) {
    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(2500)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            color = backgroundColor,
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(24.dp),
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ─── Pulse Animation (for retry buttons) ───
@Composable
fun PulsingGlow(
    modifier: Modifier = Modifier,
    color: Color = CrankGold,
    maxAlpha: Float = 0.3f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = maxAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier
            .drawBehind {
                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = size.minDimension / 2 + 8.dp.toPx()
                )
            }
    )
}

// ─── Staggered Fade-In ───
@Composable
fun StaggeredFadeIn(
    index: Int,
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(
            durationMillis = 300,
            delayMillis = index * 100
        )) + slideInVertically(
            initialOffsetY = { it / 4 },
            animationSpec = tween(
                durationMillis = 300,
                delayMillis = index * 100
            )
        ),
        exit = fadeOut(),
        modifier = modifier
    ) {
        content()
    }
}

// ─── Overscroll Gold Glow ───
@Composable
fun GoldOverscrollGlow(
    isOverscrolling: Boolean,
    modifier: Modifier = Modifier
) {
    val alpha by animateFloatAsState(
        targetValue = if (isOverscrolling) 0.15f else 0f,
        animationSpec = tween(200),
        label = "overscroll_glow"
    )

    if (alpha > 0f) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            CrankGold.copy(alpha = alpha),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

// ─── Red Flash (for errors) ───
@Composable
fun RedFlashError(
    isError: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var showFlash by remember { mutableStateOf(false) }
    val flashAlpha by animateFloatAsState(
        targetValue = if (showFlash) 0.3f else 0f,
        animationSpec = tween(200),
        label = "flash_alpha"
    )

    LaunchedEffect(isError) {
        if (isError) {
            showFlash = true
            delay(200)
            showFlash = false
        }
    }

    Box(modifier = modifier) {
        // Flashed overlay colour, read before the layout lambda.
        val flashColor = MaterialTheme.colorScheme.error
        content()
        if (flashAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(flashColor.copy(alpha = flashAlpha))
            )
        }
    }
}

// ─── Gradient Shimmer (for loading states) ───
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
