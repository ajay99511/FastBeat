package com.local.offlinemediaplayer.ui.screens.image

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.viewmodel.ImageSortField
import com.local.offlinemediaplayer.viewmodel.SortState

/**
 * Which photos are selected, and whether the grid is in selection mode at all.
 *
 * The two are not derivable from each other. An empty selection while [isActive] is true is a real
 * state — the user long-pressed and then deselected — and it must keep the selection bar on screen
 * rather than silently dropping back to browsing under their finger.
 */
internal data class ImageSelection(
    val isActive: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
) {
    val count: Int get() = selectedIds.size

    fun contains(id: Long): Boolean = id in selectedIds
}

/**
 * What the grid can ask the selection to do.
 *
 * Bundled rather than passed as five separate lambdas because `ImageListContent` was already at
 * seven parameters and detekt caps the list at ten — but mostly because these five are one
 * capability, and a caller that supplies three of them has made a mistake the compiler should
 * catch.
 */
internal data class ImageSelectionActions(
    val start: (Long) -> Unit = {},
    val toggle: (Long) -> Unit = {},
    val selectAll: () -> Unit = {},
    val clear: () -> Unit = {},
    val deleteSelected: () -> Unit = {},
)

/**
 * What tapping a photo means right now.
 *
 * Once selection is active a tap toggles rather than opens — the rule every multi-select grid
 * follows, and the one that makes selection usable with one hand. Long-press always starts
 * selection, including on a photo that is already selected, because the alternative is a gesture
 * that does nothing.
 */
internal fun tapIntent(selection: ImageSelection): TapIntent =
    if (selection.isActive) TapIntent.TOGGLE_SELECTION else TapIntent.OPEN_VIEWER

internal enum class TapIntent {
    OPEN_VIEWER,
    TOGGLE_SELECTION,
}

/**
 * Everything the Images grid renders from.
 *
 * Bundled because the signature had grown to eleven parameters, which detekt stops at and which is
 * past the point a reader can hold it in their head. Splitting *state* from *actions* rather than
 * inventing four narrower holders is the shape Compose screens converge on anyway: one thing to
 * render, one thing to call.
 */
internal data class ImageGridState(
    val images: List<MediaFile> = emptyList(),
    val isRefreshing: Boolean = false,
    val isSearchVisible: Boolean = false,
    val bottomPadding: Dp = 16.dp,
    val sort: SortState<ImageSortField> = SortState(ImageSortField.DATE_ADDED),
    val selection: ImageSelection = ImageSelection(),
)

/** Everything the Images grid can ask for. */
internal data class ImageGridActions(
    val refresh: () -> Unit = {},
    val delete: (MediaFile) -> Unit = {},
    val share: (MediaFile) -> Unit = {},
    val sortBy: (SortState<ImageSortField>) -> Unit = {},
    val selection: ImageSelectionActions = ImageSelectionActions(),
)
