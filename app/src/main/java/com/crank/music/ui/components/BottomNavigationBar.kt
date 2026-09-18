package com.crank.music.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.crank.music.ui.theme.AmberGlow
import com.crank.music.ui.theme.BronzeDark
import com.crank.music.ui.theme.BronzeShadow
import com.crank.music.ui.theme.CrankGold
import com.crank.music.ui.theme.CrankGoldBright
import com.crank.music.ui.theme.DeepSpaceNavy
import com.crank.music.ui.theme.DarkerNavy
import com.crank.music.ui.theme.MidnightBlue
import com.crank.music.ui.theme.NavyBlue
import com.crank.music.ui.theme.MetallicGoldStart
import com.crank.music.ui.theme.NavyBlue
import com.crank.music.ui.theme.TextPrimary
import com.crank.music.ui.theme.TextSecondarySoft

sealed class NavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : NavItem("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    object Explore : NavItem("explore", "Explore", Icons.Filled.Explore, Icons.Outlined.Explore)
    object Create : NavItem("create", "Crank AI", Icons.Filled.Star, Icons.Filled.Star)
    object Library : NavItem("library", "Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic)
    object You : NavItem("you", "You", Icons.Filled.Person, Icons.Outlined.Person)
}

@Composable
fun BottomNavigationBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        NavItem.Home,
        NavItem.Explore,
        NavItem.Create,
        NavItem.Library,
        NavItem.You
    )

    val view = LocalView.current
    val density = LocalDensity.current

    // ─── FAB glow animation ───
    val infiniteTransition = rememberInfiniteTransition(label = "fab_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    val glowRadius by infiniteTransition.animateFloat(
        initialValue = 20f,
        targetValue = 32f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_radius"
    )

    // ─── FAB press animation ───
    var fabPressed by remember { mutableStateOf(false) }
    val fabScale by animateFloatAsState(
        targetValue = if (fabPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = 0.4f,
            stiffness = Spring.StiffnessHigh
        ),
        label = "fab_scale"
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // ─── Pure Black Nav Bar with Navy Top Border ───
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            color = Color.Transparent
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Pure black background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DeepSpaceNavy)
                )

                // Subtle top border in Deep Navy Blue
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    NavyBlue.copy(alpha = 0.8f),
                                    CrankGold.copy(alpha = 0.3f),
                                    NavyBlue.copy(alpha = 0.8f),
                                    Color.Transparent
                                )
                            )
                        )
                        .align(Alignment.TopCenter)
                )

                // ─── Tab Row ───
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.Top
                ) {
                    items.forEach { item ->
                        if (item is NavItem.Create) {
                            Spacer(modifier = Modifier.weight(1f))
                        } else {
                            val selected = currentRoute == item.route
                            NavTabItem(
                                item = item,
                                isSelected = selected,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    onNavigate(item.route)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // ─── Center Crank AI FAB: 3D Metallic Gold with Navy Core ───
        Box(
            modifier = Modifier
                .size(56.dp)
                .offset(y = (-28).dp)
                .graphicsLayer {
                    scaleX = fabScale
                    scaleY = fabScale
                }
                .shadow(
                    elevation = 10.dp,
                    shape = CircleShape,
                    ambientColor = CrankGold.copy(alpha = 0.3f),
                    spotColor = AmberGlow.copy(alpha = 0.2f)
                )
                .drawBehind {
                    // Outer gold glow
                    drawCircle(
                        color = CrankGold.copy(alpha = glowAlpha * 0.3f),
                        radius = with(density) { glowRadius.dp.toPx() } + size.minDimension / 2
                    )
                    // Inner amber glow
                    drawCircle(
                        color = AmberGlow.copy(alpha = glowAlpha * 0.15f),
                        radius = size.minDimension / 2 + with(density) { 8.dp.toPx() }
                    )
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    fabPressed = true
                    onNavigate(NavItem.Create.route)
                },
            contentAlignment = Alignment.Center
        ) {
            // 3D metallic gold gradient background
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MetallicGoldStart,
                                CrankGold,
                                CrankGoldBright,
                                CrankGold,
                                MetallicGoldStart
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Navy blue core (3D effect)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    NavyBlue,
                                    DeepSpaceNavy
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Sparkles icon
                    SparklesIcon(
                        modifier = Modifier.size(20.dp),
                        tint = CrankGold
                    )
                }
            }
        }
    }
}

@Composable
private fun NavTabItem(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "icon_scale"
    )

    val iconAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.5f,
        animationSpec = tween(200),
        label = "icon_alpha"
    )

    val tabDensity = LocalDensity.current
    val glowRadiusPx = with(tabDensity) { 20.dp.toPx() }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
                alpha = iconAlpha
            },
            contentAlignment = Alignment.Center
        ) {
            // Active gold glow behind icon
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    CrankGold.copy(alpha = 0.25f),
                                    Color.Transparent
                                ),
                                radius = glowRadiusPx
                            ),
                            shape = CircleShape
                        )
                )
            }

            Icon(
                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                contentDescription = item.title,
                tint = if (isSelected) CrankGoldBright else NavyBlue,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = item.title,
            color = if (isSelected) CrankGoldBright else NavyBlue.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun SparklesIcon(
    modifier: Modifier = Modifier,
    tint: Color = CrankGold
) {
    val density = LocalDensity.current
    with(density) {
        androidx.compose.foundation.Canvas(modifier = modifier) {
            val strokeWidth = 3.dp.toPx()
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2

            // Center sparkle (4-pointed star)
            drawLine(
                color = tint,
                start = Offset(center.x, center.y - radius * 0.7f),
                end = Offset(center.x, center.y + radius * 0.7f),
                strokeWidth = strokeWidth
            )
            drawLine(
                color = tint,
                start = Offset(center.x - radius * 0.7f, center.y),
                end = Offset(center.x + radius * 0.7f, center.y),
                strokeWidth = strokeWidth
            )

            // Diagonal sparkle (smaller)
            val diagLen = radius * 0.4f
            drawLine(
                color = tint,
                start = Offset(center.x - radius * 0.35f, center.y - radius * 0.35f),
                end = Offset(center.x - radius * 0.35f + diagLen * 0.7f, center.y - radius * 0.35f + diagLen * 0.7f),
                strokeWidth = strokeWidth * 0.8f
            )
            drawLine(
                color = tint,
                start = Offset(center.x - radius * 0.35f, center.y - radius * 0.35f),
                end = Offset(center.x - radius * 0.35f - diagLen * 0.7f, center.y - radius * 0.35f + diagLen * 0.7f),
                strokeWidth = strokeWidth * 0.8f
            )

            // Top-right small sparkle
            val tr = Offset(center.x + radius * 0.45f, center.y - radius * 0.45f)
            drawLine(
                color = tint,
                start = Offset(tr.x, tr.y - diagLen * 0.5f),
                end = Offset(tr.x, tr.y + diagLen * 0.5f),
                strokeWidth = strokeWidth * 0.6f
            )
            drawLine(
                color = tint,
                start = Offset(tr.x - diagLen * 0.5f, tr.y),
                end = Offset(tr.x + diagLen * 0.5f, tr.y),
                strokeWidth = strokeWidth * 0.6f
            )
        }
    }
}

@Composable
fun MiniPlayer(
    modifier: Modifier = Modifier,
    title: String = "No Song Playing",
    artist: String = "Unknown Artist",
    artworkUrl: String = "",
    progress: Float = 0f,
    isPlaying: Boolean = false,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onPlayPauseClick: () -> Unit = {},
    onPlayerClick: () -> Unit = {},
    onSwipeUp: () -> Unit = {}
) {
    var dragOffset by remember { mutableStateOf(0f) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = dragOffset
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffset < -80f) {
                            onSwipeUp()
                        }
                        dragOffset = 0f
                    },
                    onDragCancel = {
                        dragOffset = 0f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (dragAmount < 0) {
                            change.consume()
                            dragOffset = (dragOffset + dragAmount).coerceAtMost(0f)
                        }
                    }
                )
            }
            .clickable { onPlayerClick() },
        color = Color.Transparent,
        shadowElevation = 6.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ─── Gold Progress Bar ───
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = CrankGold,
                trackColor = NavyBlue
            )

            // ─── Player Content ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DeepSpaceNavy)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ─── Album Art with Gold Border ───
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = NavyBlue,
                        border = BorderStroke(1.dp, CrankGold.copy(alpha = 0.4f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = CrankGold,
                                    strokeWidth = 2.dp
                                )
                            } else if (errorMessage != null) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "Error",
                                    tint = Color(0xFFCF6679),
                                    modifier = Modifier.size(20.dp)
                                )
                            } else if (artworkUrl.isNotBlank()) {
                                AsyncImage(
                                    model = artworkUrl,
                                    contentDescription = "Album Art",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = CrankGold.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // ─── Song Info ───
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 13.sp
                        )
                        val subtitle = when {
                            isLoading -> "Loading..."
                            errorMessage != null -> errorMessage
                            else -> artist
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (errorMessage != null) Color(0xFFCF6679) else TextSecondarySoft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // ─── Play/Pause Button (Gold) ───
                    if (!isLoading) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = CrankGold.copy(alpha = 0.15f),
                            onClick = onPlayPauseClick
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = CrankGoldBright,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ─── Gold swipe-up hint ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                CrankGold.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}
