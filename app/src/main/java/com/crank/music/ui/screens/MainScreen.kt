package com.crank.music.ui.screens

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: NavItem.Home.route
    val playerState by playerViewModel.playerState.collectAsState()
    val song = playerState.currentSong

    val isFullscreenRoute = currentRoute == "now_playing" || currentRoute == "settings" || currentRoute == "appearance" || currentRoute == "privacy_security" || currentRoute == "offline_music" || currentRoute == "update_checker" || currentRoute == "playback_settings" || currentRoute == "audio_quality" || currentRoute == "crank_ai" || currentRoute == "equalizer" || currentRoute == "music_dna" || currentRoute == "downloads" || currentRoute == "queue" || currentRoute == "lyrics" || currentRoute == "recognition" || currentRoute.startsWith("playlist_detail") || currentRoute.startsWith("album_detail") || currentRoute.startsWith("artist_detail")

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ObsidianBlack,
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!isFullscreenRoute) {
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
                        }
                    )
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
                        navController.navigate(NavItem.You.route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(NavItem.Explore.route) {
                ExploreScreen(
                    onSongSelect = { selectedSong ->
                        playerViewModel.playSong(selectedSong)
                    },
                    onSongSelectWithContext = { selectedSong, contextList ->
                        playerViewModel.playSongWithContext(selectedSong, contextList)
                    },
                    onArtistClick = { artistName ->
                        navController.navigate("search?q=$artistName")
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
            composable(NavItem.Create.route) {
                CrankAIScreen(
                    onSongSelect = { selectedSong ->
                        playerViewModel.playSong(selectedSong)
                    },
                    onBackClick = {
                        navController.popBackStack()
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
                        // There is no Search tab in the bottom bar; Explore is where the
                        // search field lives. Sending an empty library to Explore is the
                        // closest real destination, so the "Find Music" button takes you
                        // somewhere that can actually help.
                        navController.navigate(NavItem.Explore.route) {
                            popUpTo(NavItem.Home.route)
                        }
                    }
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
                route = "album_detail/{albumTitle}?albumArtist={albumArtist}&browseId={browseId}",
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
                    }
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
                    }
                )
            }
            composable(NavItem.You.route) {
                ProfileScreen(
                    onSettingsClick = {
                        navController.navigate("settings")
                    },
                    onDownloadsClick = {
                        navController.navigate("downloads")
                    },
                    onStatsClick = {
                        navController.navigate("music_dna")
                    },
                    onRecognizeClick = {
                        navController.navigate("recognition")
                    }
                )
            }
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
    return "album_detail/$title?albumArtist=$artist&browseId=$browseId"
}
