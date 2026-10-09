package com.local.offlinemediaplayer.ui.screens.image

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The transform arithmetic behind pinch, double-tap and pan.
 *
 * Plain JVM — `Offset` and `Size` are value classes over floats, with no Android behind them. This
 * is the half of zoom that can be wrong: clamping, and what happens to an offset that was legal at
 * the old scale once the scale changes. Gestures themselves cannot be driven reliably under
 * Robolectric (F-44), so this is also the only level at which the behaviour *can* be pinned.
 */
class ImageZoomTest {
    private val viewport = Size(1000f, 2000f)

    private fun zoomed(
        scale: Float,
        offset: Offset = Offset.Zero,
    ) = ZoomState(scale, offset)

    // ------------------------------------------------------------------ scale

    @Test
    fun aPinchMultipliesTheCurrentScale() {
        val result = ImageZoom.transform(zoomed(2f), zoomChange = 1.5f, panChange = Offset.Zero, viewport = viewport)

        assertEquals(3f, result.scale, TOLERANCE)
    }

    @Test
    fun scaleNeverGoesBelowFittingTheScreen() {
        val result = ImageZoom.transform(zoomed(1.2f), zoomChange = 0.1f, panChange = Offset.Zero, viewport = viewport)

        assertEquals(ImageZoom.MIN_SCALE, result.scale, TOLERANCE)
    }

    @Test
    fun scaleIsCappedSoTheImageCannotBeZoomedIntoNothing() {
        val result = ImageZoom.transform(zoomed(4f), zoomChange = 10f, panChange = Offset.Zero, viewport = viewport)

        assertEquals(ImageZoom.MAX_SCALE, result.scale, TOLERANCE)
    }

    // ------------------------------------------------------------------ offset clamping

    /**
     * The bug this prevents is a photo flung into empty space with no way back except guessing
     * which direction it went.
     */
    @Test
    fun panIsBoundedByHowMuchImageIsOffScreen() {
        // At 2x, half the width hangs off each side, so the centre can travel 500px either way.
        val result =
            ImageZoom.transform(zoomed(2f), zoomChange = 1f, panChange = Offset(9_000f, 9_000f), viewport = viewport)

        assertEquals(500f, result.offset.x, TOLERANCE)
        assertEquals(1_000f, result.offset.y, TOLERANCE)
    }

    @Test
    fun panIsBoundedInBothDirections() {
        val result =
            ImageZoom.transform(zoomed(2f), zoomChange = 1f, panChange = Offset(-9_000f, -9_000f), viewport = viewport)

        assertEquals(-500f, result.offset.x, TOLERANCE)
        assertEquals(-1_000f, result.offset.y, TOLERANCE)
    }

    @Test
    fun panWithinBoundsIsLeftAlone() {
        val result =
            ImageZoom.transform(zoomed(2f), zoomChange = 1f, panChange = Offset(100f, -200f), viewport = viewport)

        assertEquals(100f, result.offset.x, TOLERANCE)
        assertEquals(-200f, result.offset.y, TOLERANCE)
    }

    /** Nothing hangs off the edges at 1x, so there is nowhere to pan to. */
    @Test
    fun anUnzoomedImageCannotBePanned() {
        val result =
            ImageZoom.transform(ZoomState(), zoomChange = 1f, panChange = Offset(400f, 400f), viewport = viewport)

        assertEquals(Offset.Zero, result.offset)
    }

    /**
     * The ordering rule. Zooming out with the image dragged to one side has to pull it back into
     * view; clamping the offset against the *old* scale would strand it off-screen until the user
     * panned it back by hand.
     */
    @Test
    fun zoomingOutPullsAStrandedImageBackIntoView() {
        val draggedToTheEdge = zoomed(4f, Offset(1_500f, 3_000f))

        val result =
            ImageZoom.transform(draggedToTheEdge, zoomChange = 0.5f, panChange = Offset.Zero, viewport = viewport)

        assertEquals(2f, result.scale, TOLERANCE)
        assertEquals(500f, result.offset.x, TOLERANCE)
        assertEquals(1_000f, result.offset.y, TOLERANCE)
    }

    @Test
    fun zoomingFullyOutRecentresTheImage() {
        val strandedAndMagnified = zoomed(3f, Offset(400f, 400f))

        val result =
            ImageZoom.transform(strandedAndMagnified, zoomChange = 0.01f, panChange = Offset.Zero, viewport = viewport)

        assertEquals(ImageZoom.MIN_SCALE, result.scale, TOLERANCE)
        assertEquals(Offset.Zero, result.offset)
    }

    @Test
    fun aZeroSizedViewportDoesNotProduceNonsense() {
        val result =
            ImageZoom.transform(zoomed(3f), zoomChange = 1f, panChange = Offset(50f, 50f), viewport = Size.Zero)

        assertEquals(Offset.Zero, result.offset)
    }

    // ------------------------------------------------------------------ double tap

    @Test
    fun doubleTappingAnUnzoomedImageZoomsIn() {
        assertEquals(ImageZoom.DOUBLE_TAP_SCALE, ImageZoom.toggle(ZoomState()).scale, TOLERANCE)
    }

    @Test
    fun doubleTappingAZoomedImageReturnsItToFitting() {
        val result = ImageZoom.toggle(zoomed(4f, Offset(300f, 300f)))

        assertEquals(ImageZoom.MIN_SCALE, result.scale, TOLERANCE)
        assertEquals("returning to fit must also recentre", Offset.Zero, result.offset)
    }

    // ------------------------------------------------------------------ the paging gate

    /**
     * `isZoomed` decides whether a horizontal drag pans the photo or moves to the next one, so its
     * tolerance is not cosmetic: a pinch settles a hair off 1.0, and an exact `!= 1f` test would
     * leave paging dead after the user had visibly zoomed back out.
     */
    @Test
    fun aScaleAHairAboveOneStillCountsAsNotZoomed() {
        assertFalse(ZoomState(scale = 1f + ImageZoom.SCALE_EPSILON / 2f).isZoomed)
    }

    @Test
    fun aRestingImageIsNotZoomed() {
        assertFalse(ZoomState().isZoomed)
    }

    @Test
    fun aMagnifiedImageIsZoomed() {
        assertTrue(zoomed(1.5f).isZoomed)
    }

    private companion object {
        const val TOLERANCE = 0.001f
    }
}
