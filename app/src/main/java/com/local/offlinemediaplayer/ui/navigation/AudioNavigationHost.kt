package com.local.offlinemediaplayer.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.local.offlinemediaplayer.ui.screens.*
import com.local.offlinemediaplayer.viewmodel.PlaybackViewModel

@Composable
fun AudioNavigationHost(
        viewModel: PlaybackViewModel,
        navController: NavHostController,
        isSearchVisible: Boolean
) {
    val navigateToPlayer by viewModel.navigateToPlayer.collectAsStateWithLifecycle()

    LaunchedEffect(navigateToPlayer) {
        if (navigateToPlayer) {
            navController.navigate("now_playing") { launchSingleTop = true }
            viewModel.onPlayerNavigationConsumed()
        }
    }

    NavHost(
            navController = navController,
            startDestination = "audio_library",
            enterTransition = {
                slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(300)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(300)
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(300)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(300)
                )
            }
    ) {
        composable("audio_library") {
            AudioLibraryScreen(
                    viewModel = viewModel,
                    onNavigateToPlayer = { navController.navigate("now_playing") },
                    onNavigateToPlaylist = { id -> navController.navigate("playlist_detail/$id") },
                    onNavigateToAlbum = { id -> navController.navigate("album_detail/$id") },
                    isSearchVisible = isSearchVisible
            )
        }
        composable("now_playing") {
            NowPlayingScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("playlist_detail/{playlistId}") { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId") ?: return@composable
            PlaylistDetailScreen(
                    playlistId = playlistId,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToPlayer = { navController.navigate("now_playing") }
            )
        }
        composable("album_detail/{albumId}") { backStackEntry ->
            val albumId =
                    backStackEntry.arguments?.getString("albumId")?.toLongOrNull()
                            ?: return@composable
            AlbumDetailScreen(
                    albumId = albumId,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToPlayer = { navController.navigate("now_playing") }
            )
        }
    }
}
