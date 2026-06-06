package com.local.offlinemediaplayer.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.ui.screens.VideoFolderScreen
import com.local.offlinemediaplayer.ui.screens.VideoListScreen
import com.local.offlinemediaplayer.viewmodel.LibraryViewModel
import com.local.offlinemediaplayer.viewmodel.PlaybackViewModel

import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class VideoNavigationLayout {
    SinglePane, TwoPane
}

fun videoNavigationLayout(widthClass: AppWidthClass, route: String): VideoNavigationLayout {
    return if (widthClass == AppWidthClass.Expanded && route == "video_folders") {
        VideoNavigationLayout.TwoPane
    } else {
        VideoNavigationLayout.SinglePane
    }
}

data class TwoPaneVideoState(val selectedFolderId: String? = null)

fun TwoPaneVideoState.selectFolder(folderId: String): TwoPaneVideoState {
    return this.copy(selectedFolderId = folderId)
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun TwoPaneVideoNavigationHost(
    viewModel: PlaybackViewModel,
    libraryViewModel: LibraryViewModel,
    onVideoClick: (MediaFile, List<MediaFile>) -> Unit,
    isSearchVisible: Boolean
) {
    var state by remember { mutableStateOf(TwoPaneVideoState()) }
    val navigator = rememberListDetailPaneScaffoldNavigator<Any>()
    val allVideos by libraryViewModel.videoList.collectAsStateWithLifecycle()

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            VideoFolderScreen(
                viewModel = viewModel,
                libraryViewModel = libraryViewModel,
                onFolderClick = { folderId ->
                    state = state.selectFolder(folderId)
                    navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
                },
                onPlaylistClick = { playlistId ->
                    // For MVP we just use existing navigation or do nothing since two pane focuses on folders
                },
                onVideoClick = { mediaFile, list ->
                    onVideoClick(mediaFile, list)
                },
                isSearchVisible = isSearchVisible
            )
        },
        detailPane = {
            if (state.selectedFolderId != null) {
                val folderVideos = allVideos.filter { it.bucketId == state.selectedFolderId }
                VideoListScreen(
                    viewModel = viewModel,
                    libraryViewModel = libraryViewModel,
                    onVideoClick = onVideoClick,
                    videoListOverride = folderVideos,
                    title = "Folder", // Or dynamically lookup the name
                    onBack = {
                        navigator.navigateBack()
                    }
                )
            } else {
                EmptyDetailPane()
            }
        }
    )
}

@Composable
fun EmptyDetailPane() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Select a folder")
    }
}
