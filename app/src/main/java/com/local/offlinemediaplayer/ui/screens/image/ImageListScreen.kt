package com.local.offlinemediaplayer.ui.screens.image

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.ui.adaptive.LocalWindowSizeClass
import com.local.offlinemediaplayer.ui.adaptive.adaptiveImageCellSize
import com.local.offlinemediaplayer.ui.components.CollapsibleSearchBox
import com.local.offlinemediaplayer.ui.components.DeleteConfirmationDialog
import com.local.offlinemediaplayer.viewmodel.PlaybackViewModel

/**
 * The Images tab, bound to its ViewModel.
 *
 * This half owns only what needs a ViewModel: the scoped-storage consent round-trip and the
 * user-message toasts. Everything that draws is [ImageListContent], which takes data and callbacks
 * and can therefore be driven from a test.
 *
 * That split is the point. Until it existed there was no way to assert anything about this screen —
 * its single parameter was a 2 000-line ViewModel that constructs a `MediaController`, which is the
 * coupling F-7 predicted and F-44 confirmed. `MiniPlayer` was split the same way and for the same
 * reason; this follows that precedent rather than inventing a new one.
 *
 * **Deviation from DS-7.1, deliberate.** That record says the screen moves onto `LibraryViewModel`,
 * which already exposes `imageList`, `isRefreshing`, `scanMedia`, the delete-intent stream and the
 * shared selection API — everything except a single-image delete. Moving *that* means swapping one
 * working scoped-storage delete path for another, on an irreversible operation, for no gain this
 * task needs. The migration is deferred to I-7.9, where multi-select needs the selection API and
 * the two delete paths get consolidated on purpose rather than in passing.
 */
@Composable
fun ImageListScreen(
    viewModel: PlaybackViewModel,
    isSearchVisible: Boolean,
) {
    val images by viewModel.imageList.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Deletion Flow (for Android 11+ scoped storage)
    val intentLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                viewModel.onImageDeleteSuccess()
            } else {
                viewModel.onDeleteCancelled()
            }
        }

    LaunchedEffect(Unit) {
        viewModel.deleteIntentEvent.collect { intentSender ->
            intentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        }
    }

    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { msg ->
            Toast.makeText(context, msg.resolve(context), Toast.LENGTH_SHORT).show()
        }
    }

    ImageListContent(
        images = images,
        isRefreshing = isRefreshing,
        isSearchVisible = isSearchVisible,
        onRefresh = { viewModel.scanMedia() },
        onDeleteImage = { image -> viewModel.deleteImage(image) },
    )
}

/**
 * Everything the Images tab draws: the grid, the search box, the empty states and the full-screen
 * viewer.
 *
 * Stateless with respect to the app — it takes a list and reports intent through callbacks. The
 * viewer's open/closed state and the search query stay here because they are view state, not
 * application state, and nothing outside this screen has any use for them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImageListContent(
    images: List<MediaFile>,
    isRefreshing: Boolean,
    isSearchVisible: Boolean,
    onRefresh: () -> Unit,
    onDeleteImage: (MediaFile) -> Unit,
) {
    var selectedImageIndex by remember { mutableStateOf<Int?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Handle Back Press to close viewer
    BackHandler(enabled = selectedImageIndex != null) {
        selectedImageIndex = null
    }

    val filteredImages =
        if (searchQuery.isNotEmpty()) {
            images.filter { it.title.contains(searchQuery, ignoreCase = true) }
        } else {
            images
        }

    if (selectedImageIndex != null && filteredImages.isNotEmpty()) {
        ImageViewer(
            images = filteredImages,
            initialIndex = selectedImageIndex!!,
            onBack = { selectedImageIndex = null },
            onDelete = onDeleteImage,
            onDeleted = { deletedIndex ->
                // After deletion: navigate to next image, or prev, or close viewer
                if (filteredImages.size <= 1) {
                    // Was the last image, close viewer
                    selectedImageIndex = null
                } else if (deletedIndex >= filteredImages.size - 1) {
                    // Was the last item in list, go to previous
                    selectedImageIndex = deletedIndex - 1
                }
                // Otherwise stay at same index (next image slides in)
            },
        )
    } else {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            CollapsibleSearchBox(
                isVisible = isSearchVisible,
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholderText = "Search images...",
            )

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (filteredImages.isEmpty()) {
                    EmptyImages(isSearching = searchQuery.isNotEmpty())
                } else {
                    ImageGrid(
                        images = filteredImages,
                        onImageClick = { index -> selectedImageIndex = index },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyImages(isSearching: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (isSearching) "No images match search" else "No images found on device",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ImageGrid(
    images: List<MediaFile>,
    onImageClick: (Int) -> Unit,
) {
    val widthClass = LocalWindowSizeClass.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = adaptiveImageCellSize(widthClass)),
        contentPadding = PaddingValues(bottom = 80.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(images) { index, image ->
            ImageItem(image, onClick = { onImageClick(index) })
        }
        // Bottom padding to avoid navigation bar overlap if any
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
fun ImageItem(
    image: MediaFile,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = image.uri,
            contentDescription = image.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageViewer(
    images: List<MediaFile>,
    initialIndex: Int,
    onBack: () -> Unit,
    onDelete: (MediaFile) -> Unit,
    onDeleted: (Int) -> Unit,
) {
    val pagerState =
        rememberPagerState(
            initialPage = initialIndex,
            pageCount = { images.size },
        )
    var showControls by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { showControls = !showControls },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 16.dp,
        ) { page ->
            val image = images[page]
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = image.uri,
                    contentDescription = image.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        // Top Bar Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .statusBarsPadding()
                        .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }

                Spacer(modifier = Modifier.width(8.dp))

                val currentImage = images.getOrNull(pagerState.currentPage)
                if (currentImage != null) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentImage.title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        tint = Color.White,
                    )
                }
            }
        }

        // Bottom Info Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .navigationBarsPadding()
                        .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Info",
                    tint = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }

    if (showDeleteDialog) {
        val currentPage = pagerState.currentPage
        val currentImage = images.getOrNull(currentPage)
        if (currentImage != null) {
            DeleteConfirmationDialog(
                count = 1,
                onConfirm = {
                    onDelete(currentImage)
                    onDeleted(currentPage)
                },
                onDismiss = { showDeleteDialog = false },
            )
        }
    }
}
