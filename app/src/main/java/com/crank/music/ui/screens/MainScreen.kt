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
    libraryViewModel: com.crank.music.ui.viewmodel.LibraryViewModel = hiltViewModel()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: NavItem.Home.route
    val playerState by playerViewModel.playerState.collectAsState()
    val song = playerState.currentSong
    val isCurrentLiked by playerViewModel.isCurrentLiked.collectAsState()

    // Raised from the mini player's "+" button. Held here, at the shell, so the sheet survives
    // tab switches instead of being torn down with whichever screen is on top.
    var showAddToPlaylist by remember { mutableStateOf(false) }

    val isFullscreenRoute = currentRoute == "now_playing" || currentRoute == "settings" || currentRoute == "appearance" || currentRoute == "privacy_security" || currentRoute == "offline_music" || currentRoute == "update_checker" || currentRoute == "playback_settings" || currentRoute == "audio_quality" || currentRoute == "crank_ai" || currentRoute == "equalizer" || currentRoute == "music_dna" || currentRoute == "downloads" || currentRoute == "queue" || currentRoute == "lyrics" || currentRoute == "recognition" || currentRoute == "liked_music" || currentRoute.startsWith("playlist_detail") || currentRoute.startsWith("album_detail") || currentRoute.startsWith("artist_detail")

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
