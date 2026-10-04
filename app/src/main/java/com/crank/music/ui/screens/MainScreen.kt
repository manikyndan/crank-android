package com.crank.music.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.crank.music.domain.model.Album
import com.crank.music.feature.recognition.RecognitionScreen
import com.crank.music.ui.components.BottomNavigationBar
import com.crank.music.ui.components.MiniPlayer
import com.crank.music.ui.components.NavItem
import com.crank.music.ui.theme.ObsidianBlack
import com.crank.music.ui.viewmodel.PlayerViewModel

@Composable
fun MainScreen(
    navController: NavHostController = rememberNavController(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
    libraryViewModel: com.crank.music.ui.viewmodel.LibraryViewModel = hiltViewModel(),
    /** Route to open immediately, e.g. from a tapped notification. Null means a normal start. */
    startRoute: String? = null,
    /** Called once [startRoute] has been navigated, so the caller can clear it. */
    onRouteConsumed: () -> Unit = {},
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: NavItem.Home.route

    // Deep link from a tapped notification. Runs once per distinct non-null route; the caller
    // clears it through onRouteConsumed so a recomposition cannot navigate a second time.
    LaunchedEffect(startRoute) {
        val route = startRoute ?: return@LaunchedEffect

        // This route arrives as an intent extra on an exported activity, so it is untrusted input
        // from any app on the device. It used to be handed straight to `navigate`, which throws
        // `IllegalArgumentException` for a route no destination matches — a one-line crash of the
        // app's main activity, reachable from outside. Only parameterless routes the shell actually
        // registers are accepted, and the navigation itself is wrapped because a malformed route
        // string can still fail deep inside the navigator.
        if (route in KNOWN_START_ROUTES) {
            runCatching {
                navController.navigate(route) { launchSingleTop = true }
            }.onFailure {
                Log.w(TAG, "Could not open start route '$route'", it)
            }
        } else {
            Log.w(TAG, "Ignoring unknown start route '$route'")
        }

        onRouteConsumed()
    }

    val playerState by playerViewModel.playerState.collectAsStateWithLifecycle()
    val song = playerState.currentSong
    val isCurrentLiked by playerViewModel.isCurrentLiked.collectAsStateWithLifecycle()

    // Raised from the mini player's "+" button. Held here, at the shell, so the sheet survives
    // tab switches instead of being torn down with whichever screen is on top.
    var showAddToPlaylist by remember { mutableStateOf(false) }

    // Detail and settings routes render edge-to-edge; the bottom navigation bar belongs to the
    // four tabs only. Expressed as data (see [routeIsFullscreen]) so adding a screen cannot
    // silently miss the rule the way the previous 600-character `||` chain could.
    val isFullscreenRoute = routeIsFullscreen(currentRoute)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ObsidianBlack,
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // The mini player is global, not a tab-screen extra: it sits above the bottom nav
                // on the tab screens and stays put on detail routes (album, playlist, Liked Songs)
                // so the playing track is always reachable without going back. Only Now Playing
                // hides it, because that screen already *is* the full player.
                if (song != null && currentRoute != "now_playing") {
                    MiniPlayer(
                        title = song?.title ?: "No Song Playing",
                        artist = song?.artistName ?: "Unknown Artist",
                        artworkUrl = song?.artworkUrl.orEmpty(),
                        progress = if (playerState.duration > 0) playerState.progress.toFloat() / playerState.duration.toFloat() else 0f,
                        isPlaying = playerState.isPlaying,
                        isLoading = playerState.isLoading,
                        errorMessage = playerState.errorMessage,
                        onPlayPauseClick = { playerViewModel.togglePlayPause() },
                        onPlayerClick = {
                            if (currentRoute != "now_playing") {
                                navController.navigate("now_playing")
                            }
                        },
                        onSwipeUp = {
                            if (currentRoute != "now_playing") {
                                navController.navigate("now_playing")
                            }
                        },
                        onNextClick = { playerViewModel.playNext() },
                        onPreviousClick = { playerViewModel.playPrevious() },
                        isLiked = isCurrentLiked,
                        onLikeClick = { playerViewModel.toggleLikeCurrentSong() },
                        onAddToPlaylistClick = { showAddToPlaylist = true }
                    )
                }

                // The nav bar keeps its original rule: tab screens only. Detail routes stay
                // full-bleed and get the mini player by itself.
                if (!isFullscreenRoute) {
                    BottomNavigationBar(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            if (route != currentRoute) {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars.only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom))
                    )
                }
            }
        }
    ) { paddingValues ->
        // The mini player's playlist picker. Composed in the content slot rather than the bottom
        // bar so it overlays the whole shell instead of being clipped to the bar's height.
        if (showAddToPlaylist) {
            AddToPlaylistSheet(
                libraryViewModel = libraryViewModel,
                onAdd = { playlistId ->
                    song?.let { libraryViewModel.addSongToPlaylist(playlistId, it) }
                    showAddToPlaylist = false
                },
                onDismiss = { showAddToPlaylist = false }
            )
        }

        NavHost(
            navController = navController,
            startDestination = NavItem.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            composable(NavItem.Home.route) {
                HomeScreen(
                    onSongSelect = { selectedSong ->
                        playerViewModel.playSong(selectedSong)
                    },
                    onSongSelectWithContext = { selectedSong, contextList ->
                        playerViewModel.playSongWithContext(selectedSong, contextList)
                    },
                    onPlaylistClick = { collectionSlug ->
                        navController.navigate("playlist_detail/$collectionSlug")
                    },
                    onProfileClick = {
                        // The You tab is gone, so the Home header's profile affordance is now the
                        // only thing that opens Settings — and Settings is the only route to the
                        // equalizer, appearance, privacy, downloads and Music DNA. Pointed at the
                        // settings route directly rather than at a removed tab.
                        navController.navigate("settings")
                    },
                    onSearchClick = {
                        navController.navigate(NavItem.Search.route) {
                            popUpTo(NavItem.Home.route)
                        }
                    },
                    onAlbumClick = { album ->
                        navController.navigate(albumRoute(album))
                    },
                    onArtistClick = { artistName ->
                        navController.navigate("artist_detail/${android.net.Uri.encode(artistName)}")
                    }
                )
            }
            composable(NavItem.Browse.route) {
                ExploreScreen(
                    onSongSelect = { selectedSong ->
                        playerViewModel.playSong(selectedSong)
                    },
                    onSongSelectWithContext = { selectedSong, contextList ->
                        playerViewModel.playSongWithContext(selectedSong, contextList)
                    },
                    onArtistClick = { artistName ->
                        navController.navigate("artist_detail/${android.net.Uri.encode(artistName)}")
                    },
                    onPlaylistClick = { collectionSlug ->
                        navController.navigate("playlist_detail/$collectionSlug")
                    },
                    onAlbumClick = { album ->
                        navController.navigate(albumRoute(album))
                    }
                )
            }
            composable("search") {
                SearchScreen(
                    currentSongId = song?.id,
                    isPlaying = playerState.isPlaying,
                    onSongSelect = { selectedSong ->
                        playerViewModel.playSong(selectedSong)
                    },
                    onSongSelectWithContext = { selectedSong, contextList ->
                        playerViewModel.playSongWithContext(selectedSong, contextList)
                    },
                    onAlbumClick = { album ->
                        navController.navigate(albumRoute(album))
                    }
                )
            }
            composable("search?q={query}") { backStackEntry ->
                val query = backStackEntry.arguments?.getString("query").orEmpty()
                SearchScreen(
                    searchViewModel = hiltViewModel(),
                    initialQuery = query,
                    currentSongId = song?.id,
                    isPlaying = playerState.isPlaying,
                    onSongSelect = { selectedSong ->
                        playerViewModel.playSong(selectedSong)
                    },
                    onSongSelectWithContext = { selectedSong, contextList ->
                        playerViewModel.playSongWithContext(selectedSong, contextList)
                    },
                    onAlbumClick = { album ->
                        navController.navigate(albumRoute(album))
                    }
                )
            }
            composable("crank_ai") {
                CrankAIScreen(
                    onSongSelect = { selectedSong ->
                        playerViewModel.playSong(selectedSong)
                    },
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }
            composable("equalizer") {
                EqualizerScreen(
                    audioSessionId = playerState.audioSessionId,
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }
            composable("music_dna") {
                MusicDNAScreen(
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }
            composable("downloads") {
                DownloadsScreen(
                    onSongSelect = { selectedSong ->
                        playerViewModel.playSong(selectedSong)
                    },
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }
            composable("queue") {
                QueueScreen(
                    playerViewModel = playerViewModel,
                    onSongSelect = { selectedSong ->
                        playerViewModel.playSong(selectedSong)
                    },
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }
            composable("lyrics") {
                LyricsScreen(
                    playerViewModel = playerViewModel,
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }
            composable(NavItem.Library.route) {
                LibraryScreen(
                    onSongSelect = { selectedSong ->
                        playerViewModel.playSong(selectedSong)
                    },
                    onLikedMusicClick = {
                        navController.navigate("liked_music")
                    },
                    onDownloadedMusicClick = {
                        navController.navigate("downloads")
                    },
                    onPlaylistClick = { playlistId ->
                        navController.navigate("playlist_detail/$playlistId")
                    },
                    onArtistClick = { artistName ->
                        navController.navigate("artist_detail/${android.net.Uri.encode(artistName)}")
                    },
                    onAlbumClick = { album ->
                        navController.navigate(albumRoute(album))
                    },
                    onBrowseClick = {
                        // Search is a real top-level tab now, so an empty library can send the
                        // user straight to the search field instead of to Browse as a stand-in.
                        navController.navigate(NavItem.Search.route) {
                            popUpTo(NavItem.Home.route)
                        }
                    }
                )
            }
            // Liked Songs. Reached from the Library's pinned row; the row used to just switch the
            // Library's own section filter, so this is a new destination rather than a rebuilt one.
            composable("liked_music") {
                LikedMusicScreen(
                    onBackClick = { navController.popBackStack() },
                    // The whole visible list becomes the queue, so Next/Previous walk the liked
                    // songs from wherever the user tapped.
                    onPlaySongs = { song, contextList ->
                        playerViewModel.playSongWithContext(song, contextList)
                    },
                    onPlayAll = { contextList ->
                        contextList.firstOrNull()?.let { first ->
                            playerViewModel.playSongWithContext(first, contextList)
                        }
                    },
                    onToggleShuffle = { playerViewModel.toggleShuffle() },
                    onAddToQueue = { playerViewModel.addToQueue(it) },
                    shuffleEnabled = playerState.shuffleModeEnabled,
                    currentSongId = playerState.currentSong?.id,
                    isPlaying = playerState.isPlaying,
                )
            }
            composable("playlist_detail/{playlistId}") {
                PlaylistDetailScreen(
                    onSongSelect = { selectedSong ->
                        playerViewModel.playSong(selectedSong)
                    },
                    onSongSelectWithContext = { selectedSong, contextList ->
                        playerViewModel.playSongWithContext(selectedSong, contextList)
                    },
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }
            // The album route carries the title and artist, not just an opaque id: the app's search
            // layer resolves albums by text, so those two strings are what the destination actually
            // needs. Passing only an id meant the screen searched for the id itself.
            composable(
                route = "album_detail/{albumTitle}?albumArtist={albumArtist}&browseId={browseId}&releaseYear={releaseYear}&artworkUrl={artworkUrl}",
                arguments = listOf(
                    navArgument("albumTitle") { type = NavType.StringType },
                    navArgument("albumArtist") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("browseId") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("releaseYear") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("artworkUrl") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                )
            ) {
                AlbumDetailScreen(
                    onSongSelect = { selectedSong ->
                        playerViewModel.playSong(selectedSong)
                    },
                    onSongSelectWithContext = { selectedSong, contextList ->
                        playerViewModel.playSongWithContext(selectedSong, contextList)
                    },
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onAlbumClick = { album ->
                        navController.navigate(albumRoute(album))
                    },
                    onArtistClick = { artistName ->
                        navController.navigate("artist_detail/${android.net.Uri.encode(artistName)}")
                    },
                    currentSongId = song?.id,
                    isPlaying = playerState.isPlaying
                )
            }
            composable("artist_detail/{artistId}") {
                ArtistDetailScreen(
                    onSongSelect = { selectedSong ->
                        playerViewModel.playSong(selectedSong)
                    },
                    onSongSelectWithContext = { selectedSong, contextList ->
                        playerViewModel.playSongWithContext(selectedSong, contextList)
                    },
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onAlbumClick = { album ->
                        navController.navigate(albumRoute(album))
                    },
                    currentSongId = song?.id,
                    isPlaying = playerState.isPlaying
                )
            }
            // The You destination is removed with its tab. The routes it linked to — settings,
            // downloads, music_dna, recognition — are all still declared below and still
            // reachable; what is gone is the hub that pointed at them. See the NavItem note for
            // why this was deliberate and how to undo it.
            composable("recognition") {
                RecognitionScreen(
                    onPlaySong = { recognized ->
                        playerViewModel.playSong(recognized)
                    },
                    onAddToQueue = { recognized ->
                        playerViewModel.addToQueue(recognized)
                    },
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }
            composable("settings") {
                SettingsScreen(
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onEqualizerClick = {
                        navController.navigate("equalizer")
                    },
                    onCrankAiClick = {
                        navController.navigate("crank_ai")
                    },
                    onAppearanceClick = {
                        navController.navigate("appearance")
                    },
                    onPrivacyClick = {
                        navController.navigate("privacy_security")
                    },
                    onDownloadsClick = {
                        navController.navigate("offline_music")
                    },
                    onUpdateClick = {
                        navController.navigate("update_checker")
                    },
                    onPlaybackClick = {
                        navController.navigate("playback_settings")
                    },
                    onAudioQualityClick = {
                        navController.navigate("audio_quality")
                    },
                    onSourcesClick = {
                        navController.navigate("source_settings")
                    },
                    showUpdateBadge = false
                )
            }
            composable("appearance") {
                AppearanceSettingsScreen(
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }
            composable("privacy_security") {
                PrivacySecurityScreen(
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }
            composable("offline_music") {
                OfflineMusicScreen(
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }
            composable("update_checker") {
                UpdateCheckerScreen(
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }
            composable("playback_settings") {
                PlaybackSettingsScreen(
                    playerViewModel = playerViewModel,
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }
            composable("audio_quality") {
                AudioQualityScreen(
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }
            composable("source_settings") {
                SourceSettingsScreen(
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }
            composable("now_playing") {
                NowPlayingScreen(
                    playerViewModel = playerViewModel,
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onEqualizerClick = {
                        navController.navigate("equalizer")
                    },
                    onCrankAiClick = {
                        navController.navigate("crank_ai")
                    },
                    onQueueClick = {
                        navController.navigate("queue")
                    },
                    onLyricsClick = {
                        navController.navigate("lyrics")
                    },
                    onArtistClick = { artistName ->
                        navController.navigate("artist_detail/${android.net.Uri.encode(artistName)}")
                    }
                )
            }
        }
    }
}

/**
 * Builds the album route for [album].
 *
 * Both values are percent-encoded because album and artist names routinely contain `/`, `?`, `#`
 * and `&`, any of which would otherwise be read as route syntax and corrupt the argument.
 */
private fun albumRoute(album: Album): String {
    val title = android.net.Uri.encode(album.title)
    val artist = android.net.Uri.encode(album.artistName)
    val browseId = android.net.Uri.encode(album.id)
    val year = android.net.Uri.encode(album.releaseYear)
    val artwork = android.net.Uri.encode(album.artworkUrl)
    return "album_detail/$title?albumArtist=$artist&browseId=$browseId&releaseYear=$year&artworkUrl=$artwork"
}

/**
 * Routes an external launch may open.
 *
 * Deliberately only the parameterless destinations: a route with arguments cannot be reached from a
 * notification extra without also supplying them, and accepting arbitrary `{placeholder}` strings
 * would just move the failure into the argument parsing. Anything outside this set is ignored.
 */
private val KNOWN_START_ROUTES = setOf(
    "settings",
    "appearance",
    "privacy_security",
    "offline_music",
    "update_checker",
    "playback_settings",
    "audio_quality",
    "source_settings",
    "crank_ai",
    "equalizer",
    "music_dna",
    "downloads",
    "queue",
    "lyrics",
    "recognition",
    "liked_music",
    "now_playing",
    "search",
)

/** Tag for the few recoverable failures this shell can hit from outside input. */
private const val TAG = "CRANK_NAV"

/**
 * Routes that hide the bottom navigation bar because they render edge-to-edge.
 *
 * A named set rather than an inline `||` chain: the previous form was a single 600-character
 * expression, so a new fullscreen screen had to be remembered in exactly the right place, and a
 * typo'd route string failed silently (the bar would simply appear). Keeping the routes here
 * makes the list reviewable and testable.
 */
private val FULLSCREEN_ROUTES = setOf(
    "now_playing",
    "settings",
    "appearance",
    "privacy_security",
    "offline_music",
    "update_checker",
    "playback_settings",
    "audio_quality",
    "source_settings",
    "crank_ai",
    "equalizer",
    "music_dna",
    "downloads",
    "queue",
    "lyrics",
    "recognition",
    "liked_music",
)

/**
 * Detail routes, matched by prefix because their route string carries arguments
 * (e.g. `album_detail/{title}?albumArtist=…`), so an equality check would never match.
 */
private val FULLSCREEN_PREFIXES = listOf("playlist_detail", "album_detail", "artist_detail")

/** True when [route] should hide the bottom navigation bar. */
private fun routeIsFullscreen(route: String): Boolean =
    route in FULLSCREEN_ROUTES || FULLSCREEN_PREFIXES.any { route.startsWith(it) }
