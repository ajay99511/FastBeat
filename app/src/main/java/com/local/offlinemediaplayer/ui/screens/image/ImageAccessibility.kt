package com.local.offlinemediaplayer.ui.screens.image

/**
 * What a screen reader is told about one photo.
 *
 * The grid used to hand TalkBack the bare filename, so a camera roll announced itself as
 * "I M G underscore 2 0 2 4 0 7 1 2 …" one cell at a time, with no sense of where in the library
 * the user was. A position turns that from noise into navigation: it is the only way to tell,
 * without sight, whether swiping has moved one cell or twenty.
 *
 * Kept as a pure function so the wording is pinned by a test rather than by reading a composable —
 * and because the grid and the viewer must say the same thing about the same photo. They compute it
 * from different lists, so agreeing by construction is worth more than agreeing by inspection.
 *
 * @param position 1-based, as spoken, not as indexed.
 */
internal fun imageDescription(
    title: String,
    position: Int,
    total: Int,
): String = "$title, image $position of $total"

/** Announced for the tap that shows and hides the viewer's chrome, which otherwise has no name. */
internal const val TOGGLE_CONTROLS_LABEL = "Show or hide controls"

/** Announced for the grid cell's tap. "View" rather than "open" — this is a viewer, not an editor. */
internal const val VIEW_IMAGE_LABEL = "View"

/**
 * Names the zoom action offered to a screen reader.
 *
 * Double-tap is how zoom is reached by touch, and double-tap is exactly the gesture TalkBack
 * consumes to activate the focused element — so without an explicit action, zoom is not merely
 * awkward for a screen-reader user, it is unreachable. It is offered as a custom action, which
 * TalkBack surfaces through its own menu rather than through the gesture it has already claimed.
 */
internal fun zoomActionLabel(isZoomed: Boolean): String = if (isZoomed) "Zoom out" else "Zoom in"

/** Announced for the tap that adds or removes a photo from the selection. */
internal const val TOGGLE_SELECTION_LABEL = "Select or deselect"

/** Announced for the long-press that starts selection. */
internal const val SELECT_IMAGE_LABEL = "Select"

/**
 * Spoken state for a cell in selection mode.
 *
 * The tick in the corner is a purely visual signal, so without a state description a screen-reader
 * user can move through a grid toggling photos with no way to hear which ones they have chosen.
 */
internal const val SELECTED_LABEL = "Selected"
internal const val NOT_SELECTED_LABEL = "Not selected"
