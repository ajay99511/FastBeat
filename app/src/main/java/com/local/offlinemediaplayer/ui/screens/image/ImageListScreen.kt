package com.local.offlinemediaplayer.ui.screens.image

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.ui.adaptive.LocalWindowSizeClass
import com.local.offlinemediaplayer.ui.adaptive.adaptiveImageCellSize
import com.local.offlinemediaplayer.ui.components.CollapsibleSearchBox
import com.local.offlinemediaplayer.ui.components.DeleteConfirmationDialog
import com.local.offlinemediaplayer.ui.components.SortDropdownMenu
import com.local.offlinemediaplayer.viewmodel.ImageSortField
import com.local.offlinemediaplayer.viewmodel.LibraryViewModel
import com.local.offlinemediaplayer.viewmodel.PlaybackViewModel
import com.local.offlinemediaplayer.viewmodel.SortState

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
 * **On the two ViewModels (DS-7.1, as resolved in I-7.9).** `LibraryViewModel` owns library data,
 * sorting, selection and the batch delete; `PlaybackViewModel` is kept for the single-image delete
 * and for whether the mini player is on screen. Taking both is the convention `AudioListScreen` and
 * `VideoListScreen` already follow, not a compromise — the original decision record said *move*, and
 * was simply wrong about what the codebase does. The single-image delete stays on its existing
 * scoped-storage path rather than being rewritten onto the batch one, because that is an
 * irreversible operation and consolidating it buys nothing either path needs.
 */
@Composable
fun ImageListScreen(
    viewModel: PlaybackViewModel,
    isSearchVisible: Boolean,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
) {
    val images by libraryViewModel.sortedImageList.collectAsStateWithLifecycle()
    val sortState by libraryViewModel.imageSortState.collectAsStateWithLifecycle()
    val isRefreshing by libraryViewModel.isRefreshing.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isSelectionMode by libraryViewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedIds by libraryViewModel.selectedMediaIds.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Same rule every other list in the app uses, rather than this screen's own magic number.
    val isMiniPlayerVisible = currentTrack != null && !currentTrack!!.isVideo
    val bottomPadding = if (isMiniPlayerVisible) 100.dp else 16.dp

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

    // The batch delete lives on LibraryViewModel and emits its own consent intents, so it needs a
    // launcher of its own. Two streams rather than one is the convention AudioListScreen and
    // VideoListScreen already follow.
    val selectionIntentLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                libraryViewModel.onDeleteSuccess()
            } else {
                libraryViewModel.onDeleteCancelled()
            }
        }

    LaunchedEffect(Unit) {
        libraryViewModel.deleteIntentEvent.collect { intentSender ->
            selectionIntentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        }
    }

    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { msg ->
            Toast.makeText(context, msg.resolve(context), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        libraryViewModel.userMessage.collect { msg ->
            Toast.makeText(context, msg.resolve(context), Toast.LENGTH_SHORT).show()
        }
    }

    ImageListContent(
        state =
            ImageGridState(
                images = images,
                isRefreshing = isRefreshing,
                isSearchVisible = isSearchVisible,
                bottomPadding = bottomPadding,
                sort = sortState,
                selection = ImageSelection(isActive = isSelectionMode, selectedIds = selectedIds),
            ),
        actions =
            ImageGridActions(
                refresh = { libraryViewModel.scanMedia() },
                delete = { image -> viewModel.deleteImage(image) },
                share = { image ->
                    context.startActivity(Intent.createChooser(shareIntentFor(image), null))
                },
                sortBy = libraryViewModel::updateImageSort,
                selection =
                    ImageSelectionActions(
                        start = { id ->
                            libraryViewModel.toggleSelectionMode(true)
                            libraryViewModel.toggleSelection(id)
                        },
                        toggle = libraryViewModel::toggleSelection,
                        selectAll = { libraryViewModel.selectAll(images.map { it.id }) },
                        clear = { libraryViewModel.toggleSelectionMode(false) },
                        deleteSelected = libraryViewModel::deleteSelectedMedia,
                    ),
            ),
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
    state: ImageGridState,
    actions: ImageGridActions = ImageGridActions(),
) {
    val images = state.images
    val selection = state.selection
    // Saveable, not remembered: rotating while looking at a photo used to drop the viewer and the
    // search query on the floor.
    var selectedImageIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showDeleteSelectedDialog by rememberSaveable { mutableStateOf(false) }

    // Handle Back Press to close viewer
    BackHandler(enabled = selectedImageIndex != null) {
        selectedImageIndex = null
    }

    BackHandler(enabled = selectedImageIndex == null && selection.isActive) {
        actions.selection.clear()
    }

    // Keyed, so a library of several thousand photos is not re-filtered on every recomposition —
    // and toggling the viewer's controls recomposes plenty.
    val filteredImages =
        remember(images, searchQuery) {
            if (searchQuery.isEmpty()) {
                images
            } else {
                images.filter { it.title.contains(searchQuery, ignoreCase = true) }
            }
        }

    if (selectedImageIndex != null && filteredImages.isNotEmpty()) {
        ImageViewer(
            images = filteredImages,
            initialIndex = selectedImageIndex!!,
            onBack = { selectedImageIndex = null },
            onDelete = actions.delete,
            onShare = actions.share,
        )
    } else {
        if (showDeleteSelectedDialog) {
            DeleteConfirmationDialog(
                count = selection.count,
                onConfirm = actions.selection.deleteSelected,
                onDismiss = { showDeleteSelectedDialog = false },
            )
        }

        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (selection.isActive) {
                SelectionBar(
                    count = selection.count,
                    onSelectAll = actions.selection.selectAll,
                    onDelete = { showDeleteSelectedDialog = true },
                    onClose = actions.selection.clear,
                )
            } else {
                CollapsibleSearchBox(
                    isVisible = state.isSearchVisible,
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholderText = "Search images...",
                )
            }

            if (!selection.isActive && filteredImages.isNotEmpty()) {
                ImageListHeader(
                    count = filteredImages.size,
                    sortState = state.sort,
                    onSortChange = actions.sortBy,
                )
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = actions.refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (filteredImages.isEmpty()) {
                    EmptyImages(isSearching = searchQuery.isNotEmpty())
                } else {
                    ImageGrid(
                        images = filteredImages,
                        bottomPadding = state.bottomPadding,
                        selection = selection,
                        onImageClick = { index ->
                            val image = filteredImages[index]
                            when (tapIntent(selection)) {
                                TapIntent.OPEN_VIEWER -> selectedImageIndex = index
                                TapIntent.TOGGLE_SELECTION -> actions.selection.toggle(image.id)
                            }
                        },
                        onImageLongClick = { index -> actions.selection.start(filteredImages[index].id) },
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
    bottomPadding: Dp,
    selection: ImageSelection,
    onImageClick: (Int) -> Unit,
    onImageLongClick: (Int) -> Unit,
) {
    val widthClass = LocalWindowSizeClass.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = adaptiveImageCellSize(widthClass)),
        contentPadding = PaddingValues(bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // Keyed by id so deleting a photo removes that cell rather than shifting every image one
        // place left and reusing the wrong bitmap for the rest of the grid.
        itemsIndexed(images, key = { _, image -> image.id }) { index, image ->
            ImageItem(
                image = image,
                position = index + 1,
                total = images.size,
                isSelectionMode = selection.isActive,
                isSelected = selection.contains(image.id),
                onClick = { onImageClick(index) },
                onLongClick = { onImageLongClick(index) },
            )
        }
    }
}

/**
 * The count-and-sort strip above the grid.
 *
 * The Images tab had neither. A count is the cheapest thing a gallery can tell you about itself, and
 * the sort control is what makes a long roll navigable — every other media list in the app has had
 * one; this was the exception.
 *
 * Hidden during selection, because the strip's job is to describe the library and selection mode is
 * about a subset of it.
 */
@Composable
private fun ImageListHeader(
    count: Int,
    sortState: SortState<ImageSortField>,
    onSortChange: (SortState<ImageSortField>) -> Unit,
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = if (count == 1) "1 photo" else "$count photos",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box {
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClickLabel = "Change sort order") { showSortMenu = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (sortState.ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = if (sortState.ascending) "Sorted ascending" else "Sorted descending",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Sort: ${sortState.field.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SortDropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false },
                fields = ImageSortField.entries,
                sortState = sortState,
                onSortChange = onSortChange,
            )
        }
    }
}

/**
 * One grid cell.
 *
 * The description lives on the cell, not on the `AsyncImage`, so the photo and its tap target are a
 * single thing to a screen reader instead of an unlabelled button wrapped around a filename. In
 * selection mode it also reports whether the photo is selected, because the tick is otherwise a
 * purely visual signal — a screen-reader user could toggle their way through a grid with no way to
 * hear what they had chosen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageItem(
    image: MediaFile,
    position: Int,
    total: Int,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val description = imageDescription(image.title, position, total)
    val stateLabel = if (isSelected) SELECTED_LABEL else NOT_SELECTED_LABEL
    Box(
        modifier =
            Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .combinedClickable(
                    onClickLabel = if (isSelectionMode) TOGGLE_SELECTION_LABEL else VIEW_IMAGE_LABEL,
                    onLongClickLabel = SELECT_IMAGE_LABEL,
                    onClick = onClick,
                    onLongClick = onLongClick,
                ).semantics {
                    contentDescription = description
                    if (isSelectionMode) stateDescription = stateLabel
                },
    ) {
        AsyncImage(
            model = image.uri,
            // Described by the cell above; repeating it here would have TalkBack say it twice.
            contentDescription = null,
            modifier =
                Modifier
                    .fillMaxSize()
                    // A selected photo shrinks rather than gaining a border, so the tick has
                    // somewhere to sit that is not on top of the picture.
                    .padding(if (isSelected) 8.dp else 0.dp),
            contentScale = ContentScale.Crop,
        )

        if (isSelectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp),
            )
        }
    }
}

/**
 * The bar that replaces the search box while photos are being selected.
 *
 * It replaces rather than stacks: searching and selecting are different modes, and a search box
 * sitting above a selection count invites typing into a list that is about to be deleted from.
 */
@Composable
private fun SelectionBar(
    count: Int,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Cancel selection")
        }
        Text(
            text = if (count == 1) "1 selected" else "$count selected",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onSelectAll) {
            Text("Select all")
        }
        // An empty selection is a real state — long-press then deselect — and deleting nothing is
        // not an action, so the control says so rather than quietly doing nothing.
        IconButton(onClick = onDelete, enabled = count > 0) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Delete selected",
                tint =
                    if (count > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}
