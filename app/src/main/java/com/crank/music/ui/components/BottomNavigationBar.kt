package com.crank.music.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

sealed class NavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : NavItem("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)

    /**
     * Browse. Same route and destination as the previous `Explore` item — only the label and the
     * icon changed, so nothing that navigates by route had to move.
     */
    object Browse : NavItem("explore", "Browse", Icons.Filled.Explore, Icons.Outlined.Explore)

    object Library : NavItem("library", "Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic)

    /**
     * Search is now a top-level destination.
     *
     * It was previously reachable only through `composable("search")`, while `MainScreen` carried
     * a comment noting there was no Search tab and that Explore was standing in for it.
     */
    object Search : NavItem("search", "Search", Icons.Filled.Search, Icons.Outlined.Search)

    /**
     * NEW additive destination: YouTube Music-style home feed.
     *
     * Added alongside the existing four tabs; none of the existing routes,
     * labels or icons were touched. Remove this object + its MainScreen
     * composable to revert without affecting anything else.
     */
    object Discover : NavItem("ytmusic_home", "Discover", Icons.Filled.MusicNote, Icons.Outlined.MusicNote)

    /**
     * Removed: `Create` ("Crank AI") and `You`.
     *
     * `You` routed to `ProfileScreen`, which was the only entry point for Settings, Downloads,
     * Music DNA and Recognition; Crank AI was reachable only from `SettingsScreen.onCrankAiClick`.
     * Dropping the two tabs therefore also removed the only path to those four screens. That was
     * raised and confirmed before removal, and it is confined to a single commit so `git revert`
     * brings the destinations and their entry points back together.
     */
}

@Composable
fun BottomNavigationBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        NavItem.Home,
        NavItem.Browse,
        NavItem.Discover,
        NavItem.Library,
        NavItem.Search
    )

    val view = LocalView.current

    // The centre FAB and its pulsing glow are gone with the Create tab. A nav bar whose tallest
    // element is a raised button reads as "there is a fifth, more important destination" — which
    // is exactly what it meant, and no longer what is true. Four equal tabs is also what makes
    // the bar read as a single row rather than a row plus an exception.
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // A plain hairline. The old border was a five-stop horizontal gradient that faded to
            // transparent at both ends and carried the accent colour through the centre, which
            // drew the eye to the middle of the bar — the FAB's old position. A uniform divider
            // separates the bar from content without competing with it.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    NavTabItem(
                        item = item,
                        isSelected = currentRoute == item.route,
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

@Composable
private fun NavTabItem(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "icon_scale"
    )

    // Unselected sits at full opacity in the muted text colour rather than being faded out.
    // Fading and tinting at once made unselected tabs look disabled rather than simply inactive,
    // and it pushed contrast below a comfortable level on the light scheme.
    val tint by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(200),
        label = "tab_tint"
    )

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
        Icon(
            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
            contentDescription = item.title,
            tint = tint,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = item.title,
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            style = MaterialTheme.typography.labelSmall
        )
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
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Hairline progress. Deliberately thin and uncoloured: the mini-player already has a
            // coloured control (the play button) and a coloured progress bar as well made the bar
            // read as two competing actions. It is information, not a control.
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                trackColor = Color.Transparent,
                drawStopIndicator = {}
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // No border. The artwork is the identity of the track, and a coloured ring
                    // around it competes with whatever palette the cover actually has.
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            // Loading keeps the artwork slot so the row never reflows when the
                            // cover arrives; a spinner replacing the box would shift the title.
                            if (artworkUrl.isNotBlank()) {
                                AsyncImage(
                                    model = artworkUrl,
                                    contentDescription = "Album Art",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = if (errorMessage != null) {
                                        Icons.Default.Error
                                    } else {
                                        Icons.Default.MusicNote
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 14.sp
                        )
                        val subtitle = when {
                            isLoading -> "Loading…"
                            errorMessage != null -> errorMessage
                            else -> artist
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (errorMessage != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // The one coloured element in the bar, so it reads as the primary action
                    // without needing a label. Sized up from 36dp: it is the control people reach
                    // for most and it sits at the very bottom of the screen.
                    if (!isLoading) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = Color.Transparent,
                            onClick = onPlayPauseClick
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
