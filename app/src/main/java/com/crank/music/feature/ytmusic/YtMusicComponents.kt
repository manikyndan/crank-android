package com.crank.music.feature.ytmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.crank.music.domain.model.Album
import com.crank.music.domain.model.Song
import com.crank.music.ui.components.ShimmerBox
import com.crank.music.ui.theme.CrankRadius
import com.crank.music.ui.theme.CrankSpacing

/** Reusable section title with an optional "See all" affordance. YT Music style. */
@Composable
fun YtSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onSeeAllClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CrankSpacing.M),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (onSeeAllClick != null) {
            Text(
                text = "See all",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSeeAllClick() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** Wrapper for horizontal scrolling content: 16px side padding, generous gaps. */
@Composable
fun <T> YtHorizontalScrollSection(
    items: List<T>,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = CrankSpacing.M),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.hashCode() }) { item -> content(item) }
    }
}

@Composable
private fun pressModifier(onClick: () -> Unit): Modifier {
    return Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
    ) { onClick() }
}

@Composable
private fun YtArtwork(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(CrankRadius.Medium),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (url.isNotBlank()) {
                AsyncImage(
                    model = url,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

/** Small square card: artwork + title + artist. Tap plays immediately. */
@Composable
fun YtQuickPickCard(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(120.dp)
            .then(pressModifier(onClick)),
    ) {
        YtArtwork(
            url = song.artworkUrl,
            contentDescription = song.title,
            modifier = Modifier.size(120.dp),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = song.artistName,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Larger mix/playlist card with 2x2 artwork collage. */
@Composable
fun YtMixCard(
    mix: YtMix,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(160.dp)
            .then(pressModifier(onClick)),
    ) {
        Surface(
            modifier = Modifier.size(160.dp),
            shape = RoundedCornerShape(CrankRadius.Large),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp,
            shadowElevation = 3.dp,
        ) {
            if (mix.artworkUrls.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp),
                    )
                }
            } else if (mix.artworkUrls.size == 1) {
                AsyncImage(
                    model = mix.artworkUrls.first(),
                    contentDescription = mix.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f)) {
                        mix.artworkUrls.take(2).forEach { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxSize(),
                            )
                        }
                    }
                    Row(modifier = Modifier.weight(1f)) {
                        mix.artworkUrls.drop(2).take(2).forEach { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = mix.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = mix.subtitle,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            minLines = 2,
        )
    }
}

/** Album recommendation card. */
@Composable
fun YtAlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(140.dp)
            .then(pressModifier(onClick)),
    ) {
        YtArtwork(
            url = album.artworkUrl,
            contentDescription = album.title,
            modifier = Modifier.size(140.dp),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = album.artistName,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Circular artist card. */
@Composable
fun YtArtistCard(
    artist: YtArtistRec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(110.dp)
            .then(pressModifier(onClick)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
        ) {
            if (artist.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = artist.imageUrl,
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "Artist",
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Library song row with play-button overlay. */
@Composable
fun YtLibraryPickCard(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(140.dp)
            .then(pressModifier(onClick)),
    ) {
        Box(modifier = Modifier.size(140.dp)) {
            YtArtwork(
                url = song.artworkUrl,
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(36.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = song.artistName,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Mood/activity category chip. */
@Composable
fun YtCategoryChip(
    category: YtMoodCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.then(pressModifier(onClick)),
        shape = RoundedCornerShape(CrankRadius.Large),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
    ) {
        Text(
            text = category.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}

/** Continue-listening card. [progress] is null when no saved position exists — no bar then. */
@Composable
fun YtContinueCard(
    song: Song,
    progress: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(160.dp)
            .then(pressModifier(onClick)),
    ) {
        Box(modifier = Modifier.size(160.dp)) {
            YtArtwork(
                url = song.artworkUrl,
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
            )
            if (progress != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = song.artistName,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Loading skeleton row reusing the app's ShimmerBox. */@Composable
fun YtLoadingRow(
    cardWidth: Int = 120,
    cardHeight: Int = 120,
    count: Int = 4,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = CrankSpacing.M),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(count) {
            Column(modifier = Modifier.width(cardWidth.dp)) {
                ShimmerBox(
                    modifier = Modifier
                        .size(cardWidth.dp, cardHeight.dp)
                        .clip(RoundedCornerShape(CrankRadius.Medium)),
                )
                Spacer(modifier = Modifier.height(6.dp))
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
            }
        }
    }
}

/** Trending chart row: rank number + artwork + title/artist. */
@Composable
fun YtTrendingCard(
    entry: YtTrendingEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .width(280.dp)
            .then(pressModifier(onClick)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.rank.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
            modifier = Modifier.width(36.dp),
            maxLines = 1,
        )
        YtArtwork(
            url = entry.song.artworkUrl,
            contentDescription = entry.song.title,
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.song.artistName,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Vibrant gradient tile for the mood grid. Colors are presentation only. */
@Composable
fun YtMoodTile(
    category: YtMoodCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MOOD_GRADIENTS[category.id] ?: (Color(0xFF3A3A3C) to Color(0xFF1C1C1E))
    Box(
        modifier = modifier
            .height(76.dp)
            .clip(RoundedCornerShape(CrankRadius.Large))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(colors.first, colors.second)
                )
            )
            .then(pressModifier(onClick))
            .padding(14.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = category.label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val MOOD_GRADIENTS = mapOf(
    "workout" to (Color(0xFFE13300) to Color(0xFF7A1E00)),
    "chill" to (Color(0xFF2E77D0) to Color(0xFF12325E)),
    "focus" to (Color(0xFF6B3FA0) to Color(0xFF2E1A4D)),
    "party" to (Color(0xFFE8115B) to Color(0xFF7A0B32)),
    "commute" to (Color(0xFF1DB954) to Color(0xFF0B4D24)),
    "rock" to (Color(0xFF535353) to Color(0xFF1B1B1B)),
    "latin" to (Color(0xFFF5A623) to Color(0xFF7A4A00)),
    "throwback" to (Color(0xFF8D67AB) to Color(0xFF3E2A55)),
)
