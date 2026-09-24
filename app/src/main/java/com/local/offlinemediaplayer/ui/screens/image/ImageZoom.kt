package com.local.offlinemediaplayer.ui.screens.image

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * How far the viewer is zoomed in, and how far the image has been dragged.
 *
 * [offset] is in screen pixels and is applied as a `graphicsLayer` translation *after* the scale, so
 * a pan of one finger-pixel moves the image one pixel whatever the zoom level.
 */
internal data class ZoomState(
    val scale: Float = ImageZoom.MIN_SCALE,
    val offset: Offset = Offset.Zero,
) {
    /**
     * Whether the image is magnified at all.
     *
     * The viewer disables paging on this, so it decides whether a horizontal drag pans the photo or
     * moves to the next one. Comparing against a tolerance rather than `!= 1f` because pinch gestures
     * arrive as a stream of floats and settle a hair off 1.0 — without it the pager would stay dead
     * after the user had visibly zoomed back out.
     */
    val isZoomed: Boolean get() = scale > ImageZoom.MIN_SCALE + ImageZoom.SCALE_EPSILON
}

/**
 * The transform arithmetic behind pinch, double-tap and pan.
 *
 * Separated from the composable because it is the part that can be *wrong* — clamping, and the
 * interaction between a scale change and an offset that was valid at the previous scale — and
 * because gestures cannot be driven reliably under Robolectric (F-44), so a Compose test could not
 * cover it anyway.
 *
 * **Hand-rolled rather than taken from a library.** It is this file, it has no dependencies, and the
 * project's dependency posture is deliberate and low-touch. A runtime dependency on the most-used
 * interaction of the screen is not warranted for arithmetic this small.
 */
internal object ImageZoom {
    const val MIN_SCALE = 1f
    const val MAX_SCALE = 5f

    /** Where a double-tap lands. Far enough to be useful, near enough to still see context. */
    const val DOUBLE_TAP_SCALE = 2.5f

    /** Pinches settle a hair off 1.0; below this a scale counts as "not zoomed". */
    const val SCALE_EPSILON = 0.01f

    /**
     * Applies one gesture frame: a relative zoom and a pan, clamped to stay usable.
     *
     * The order matters. The new scale is clamped first, then the offset is clamped *against that
     * new scale* — zooming out with the image dragged to one side has to pull it back into view, and
     * clamping in the other order would leave it stranded off-screen until the user panned it back.
     */
    fun transform(
        current: ZoomState,
        zoomChange: Float,
        panChange: Offset,
        viewport: Size,
    ): ZoomState {
        val scale = (current.scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
        return ZoomState(scale = scale, offset = clampOffset(current.offset + panChange, scale, viewport))
    }

    /** Double-tap: jump in if the image is at rest, otherwise return it to fitting the screen. */
    fun toggle(current: ZoomState): ZoomState =
        if (current.isZoomed) ZoomState() else ZoomState(scale = DOUBLE_TAP_SCALE)

    /**
     * Keeps the magnified image covering the viewport, so it can never be dragged into empty space.
     *
     * The bound is derived from the viewport rather than the bitmap's own drawn size. The image is
     * letterboxed by `ContentScale.Fit`, so a precise bound would need its intrinsic dimensions
     * threaded through from the loader; against that, this permits a little travel into the
     * letterbox bars on an image whose aspect ratio differs sharply from the screen's. That is a
     * visual nicety. Being able to fling a photo entirely off-screen is not, and this prevents it.
     *
     * At [MIN_SCALE] the bound is zero, which is what snaps a zoomed-out image back to centre
     * instead of leaving it wherever the last pan left it.
     */
    fun clampOffset(
        offset: Offset,
        scale: Float,
        viewport: Size,
    ): Offset {
        val maxX = (viewport.width * (scale - MIN_SCALE) / 2f).coerceAtLeast(0f)
        val maxY = (viewport.height * (scale - MIN_SCALE) / 2f).coerceAtLeast(0f)
        return Offset(
            x = offset.x.coerceIn(-maxX, maxX),
            y = offset.y.coerceIn(-maxY, maxY),
        )
    }
}
