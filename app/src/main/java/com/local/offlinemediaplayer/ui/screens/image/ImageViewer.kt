package com.local.offlinemediaplayer.ui.screens.image

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.playback.supportsTrash
import com.local.offlinemediaplayer.ui.components.DeleteConfirmationDialog

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
                movesToTrash = supportsTrash(),
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
