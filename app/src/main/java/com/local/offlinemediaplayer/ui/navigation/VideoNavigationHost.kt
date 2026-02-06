package com.local.offlinemediaplayer.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.ui.screens.VideoFolderScreen
import com.local.offlinemediaplayer.ui.screens.VideoListScreen
import com.local.offlinemediaplayer.ui.screens.VideoPlaylistDetailScreen
import com.local.offlinemediaplayer.viewmodel.MainViewModel

@Composable
fun VideoNavigationHost(
    viewModel: MainViewModel,
    navController: NavHostController,
    onVideoClick: (MediaFile) -> Unit,
    isSearchVisible: Boolean
) {
    NavHost(
        navController = navController,
        startDestination = "video_folders",
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300))
        },
        exitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300))
        },
        popEnterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300))
        }
    ) {
        composable("video_folders") {
            VideoFolderScreen(
                viewModel = viewModel,
                onFolderClick = { folderId ->
                    navController.navigate("video_list/$folderId")
                },
                onPlaylistClick = { playlistId ->
                    navController.navigate("video_playlist_detail/$playlistId")
                },
                onVideoClick = onVideoClick,
                isSearchVisible = isSearchVisible
            )
        }

        composable("video_list/{bucketId}") { backStackEntry ->
            val bucketId = backStackEntry.arguments?.getString("bucketId") ?: ""
            val allVideos by viewModel.videoList.collectAsState()
            val folderVideos = allVideos.filter { it.bucketId == bucketId }

            // Video List has its own header with search toggle, but we can pass initial state if needed.
            // Since VideoList hides the main FastBeat header, `isSearchVisible` from MainScreen
            // won't toggle via the main header. We will handle local toggle in VideoListScreen.

            VideoListScreen(
                viewModel = viewModel,
                onVideoClick = onVideoClick,
                videoListOverride = folderVideos,
                title = folderVideos.firstOrNull()?.bucketName ?: "Videos",
                onBack = { navController.popBackStack() }
            )
        }

        composable("video_playlist_detail/{playlistId}") { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId") ?: return@composable

            VideoPlaylistDetailScreen(
                playlistId = playlistId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateToPlayer = onVideoClick
            )
        }
    }
}
