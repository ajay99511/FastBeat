package com.local.offlinemediaplayer.ui.screens.image

import android.app.Activity
import android.content.Intent
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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.local.offlinemediaplayer.viewmodel.LibraryViewModel
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
    libraryViewModel: LibraryViewModel = hiltViewModel(),
) {
    val images by libraryViewModel.imageList.collectAsStateWithLifecycle()
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
        images = images,
        isRefreshing = isRefreshing,
        isSearchVisible = isSearchVisible,
        bottomPadding = bottomPadding,
        selection = ImageSelection(isActive = isSelectionMode, selectedIds = selectedIds),
        selectionActions =
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
        onRefresh = { libraryViewModel.scanMedia() },
        onDeleteImage = { image -> viewModel.deleteImage(image) },
        onShareImage = { image ->
            context.startActivity(Intent.createChooser(shareIntentFor(image), null))
        },
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
    onShareImage: (MediaFile) -> Unit = {},
    bottomPadding: Dp = 16.dp,
    selection: ImageSelection = ImageSelection(),
    selectionActions: ImageSelectionActions = ImageSelectionActions(),
) {
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
        selectionActions.clear()
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
            onDelete = onDeleteImage,
            onShare = onShareImage,
        )
    } else {
        if (showDeleteSelectedDialog) {
            DeleteConfirmationDialog(
                count = selection.count,
                onConfirm = selectionActions.deleteSelected,
                onDismiss = { showDeleteSelectedDialog = false },
            )
        }

        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (selection.isActive) {
                SelectionBar(
                    count = selection.count,
                    onSelectAll = selectionActions.selectAll,
                    onDelete = { showDeleteSelectedDialog = true },
                    onClose = selectionActions.clear,
                )
            } else {
                CollapsibleSearchBox(
                    isVisible = isSearchVisible,
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholderText = "Search images...",
                )
            }

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
                        bottomPadding = bottomPadding,
                        selection = selection,
                        onImageClick = { index ->
                            val image = filteredImages[index]
                            when (tapIntent(selection)) {
                                TapIntent.OPEN_VIEWER -> selectedImageIndex = index
                                TapIntent.TOGGLE_SELECTION -> selectionActions.toggle(image.id)
                            }
                        },
                        onImageLongClick = { index -> selectionActions.start(filteredImages[index].id) },
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

/**
 * One full-screen photo: pinch, double-tap and pan.
 *
 * The gesture handlers are keyed on [page] so a recycled page starts with fresh detectors rather
 * than ones still tracking the previous photo's pointers.
 *
 * The tap that toggles the viewer's chrome lives here rather than on an outer `clickable`, because
 * a parent click and a child `detectTapGestures` cannot both have the tap — and only the child can
 * tell a single tap from the double tap that zooms.
 */
@Composable
private fun ZoomablePage(
    image: MediaFile,
    page: Int,
    total: Int,
    zoom: ZoomState,
    onZoomChange: (ZoomState) -> Unit,
    onToggleControls: () -> Unit,
) {
    var viewport by remember { mutableStateOf(Size.Zero) }
    val description = imageDescription(image.title, page + 1, total)
    val zoomLabel = zoomActionLabel(zoom.isZoomed)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = description
                    // `detectTapGestures` contributes no semantics at all, so without these the
                    // whole viewer is an unlabelled black rectangle to a screen reader.
                    onClick(label = TOGGLE_CONTROLS_LABEL) {
                        onToggleControls()
                        true
                    }
                    customActions =
                        listOf(
                            CustomAccessibilityAction(zoomLabel) {
                                onZoomChange(ImageZoom.toggle(zoom))
                                true
                            },
                        )
                }.onSizeChanged { viewport = Size(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(page) {
                    detectTapGestures(
                        onTap = { onToggleControls() },
                        onDoubleTap = { onZoomChange(ImageZoom.toggle(zoom)) },
                    )
                }.pointerInput(page) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        onZoomChange(ImageZoom.transform(zoom, gestureZoom, pan, viewport))
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = image.uri,
            // Described by the page above.
            contentDescription = null,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom.scale
                        scaleY = zoom.scale
                        translationX = zoom.offset.x
                        translationY = zoom.offset.y
                    },
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * What the viewer's info button shows.
 *
 * Rows come from [imageDetails], which drops whatever MediaStore did not report, so a photo missing
 * its dimensions shows a shorter sheet rather than "0 × 0".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageDetailsSheet(
    image: MediaFile,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Details",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(16.dp))

            imageDetails(image).forEach { detail ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = detail.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = detail.value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

/**
 * The viewer's top chrome: back, the photo's name, share and delete.
 *
 * Extracted because `ImageViewer` crossed detekt's length limit once the share control and the
 * details sheet arrived — and because a bar of buttons is a coherent thing on its own.
 *
 * [title] is nullable rather than defaulted: the pager can momentarily report a page the list no
 * longer has while a deletion settles, and an empty bar is honest where a stale name would not be.
 */
@Composable
private fun ViewerTopBar(
    title: String?,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
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

        Column(modifier = Modifier.weight(1f)) {
            if (title != null) {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        IconButton(onClick = onShare, enabled = title != null) {
            Icon(Icons.Outlined.Share, contentDescription = "Share", tint = Color.White)
        }

        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Color.White)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageViewer(
    images: List<MediaFile>,
    initialIndex: Int,
    onBack: () -> Unit,
    onDelete: (MediaFile) -> Unit,
    onShare: (MediaFile) -> Unit = {},
) {
    val pagerState =
        rememberPagerState(
            initialPage = initialIndex,
            pageCount = { images.size },
        )
    var showControls by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(ZoomState()) }

    // A photo left magnified would otherwise hand the next one a scale and an offset it never
    // earned, and — since paging is disabled while zoomed — strand the user on it.
    LaunchedEffect(pagerState.currentPage) { zoom = ZoomState() }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 16.dp,
            // While magnified a horizontal drag has to pan the photo. Paging is turned off rather
            // than handed off at the edges: edge hand-off is what a full gallery does, but it needs
            // the drawn bitmap's bounds and fails confusingly when it is a pixel out. Disabling is
            // unambiguous — zoom out and swiping works again.
            userScrollEnabled = !zoom.isZoomed,
        ) { page ->
            ZoomablePage(
                image = images[page],
                page = page,
                total = images.size,
                zoom = zoom,
                onZoomChange = { zoom = it },
                onToggleControls = { showControls = !showControls },
            )
        }

        // Top Bar Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ViewerTopBar(
                title = images.getOrNull(pagerState.currentPage)?.title,
                onBack = onBack,
                onShare = { images.getOrNull(pagerState.currentPage)?.let(onShare) },
                onDelete = { showDeleteDialog = true },
            )
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
                IconButton(onClick = { showInfo = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Details",
                        tint = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }

    if (showInfo) {
        images.getOrNull(pagerState.currentPage)?.let { image ->
            ImageDetailsSheet(image = image, onDismiss = { showInfo = false })
        }
    }

    if (showDeleteDialog) {
        val currentImage = images.getOrNull(pagerState.currentPage)
        if (currentImage != null) {
            DeleteConfirmationDialog(
                count = 1,
                onConfirm = { onDelete(currentImage) },
                onDismiss = { showDeleteDialog = false },
            )
        }
    }
}
