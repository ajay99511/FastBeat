package com.local.offlinemediaplayer.ui.screens.image

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The wording a screen reader speaks for a photo.
 *
 * Pinned here rather than inside the Compose suite because the grid and the viewer compute it from
 * different lists and must nevertheless say the same thing about the same photo — and because a
 * string a blind user depends on should not be free to drift on a refactor.
 */
class ImageDescriptionTest {
    @Test
    fun aPhotoIsNamedAndPlaced() {
        assertEquals("beach.jpg, image 3 of 240", imageDescription("beach.jpg", 3, 240))
    }

    /** Positions are spoken, so they count from one. An "image 0 of 3" would be nonsense aloud. */
    @Test
    fun thePositionIsTheOneTheUserWouldSay() {
        assertEquals("only.jpg, image 1 of 1", imageDescription("only.jpg", 1, 1))
    }

    /**
     * Double-tap is the gesture TalkBack has already claimed for activation, so zoom is offered as
     * a named custom action instead — and the name has to say which way it will go.
     */
    @Test
    fun theZoomActionSaysWhichDirectionItGoes() {
        assertEquals("Zoom in", zoomActionLabel(isZoomed = false))
        assertEquals("Zoom out", zoomActionLabel(isZoomed = true))
    }
}
