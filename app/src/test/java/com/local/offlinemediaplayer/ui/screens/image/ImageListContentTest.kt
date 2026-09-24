package com.local.offlinemediaplayer.ui.screens.image

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.ui.theme.OfflineMediaPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * I-7.2 — the first tests this screen has ever had.
 *
 * They exist because of the split, not despite it: `ImageListScreen`'s only parameter was a
 * `PlaybackViewModel`, which constructs a `MediaController` and pulls in Hilt and Room, so no
 * assertion could be made about this screen at all. [ImageListContent] takes a list and callbacks.
 *
 * **Why [click] invokes the semantics action rather than `performClick()`.** Every grid cell and
 * every viewer page is a Coil `AsyncImage`, and F-44 measured that under Robolectric an
 * `AsyncImage` leaves the composition in a state where a synthesised touch never reaches the
 * gesture detector — the callback simply never fires. Invoking `OnClick` runs the same lambda the
 * modifier was given, so the wiring is genuinely covered; what is not covered is hit-testing and
 * overlap, which needs a device. This is the same trade-off `MiniPlayerTest` documents.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImageListContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun image(
        id: Long,
        title: String,
    ) = MediaFile(
        id = id,
        uri = Uri.EMPTY,
        title = title,
        duration = 0,
        isVideo = false,
        isImage = true,
    )

    private val threeImages =
        listOf(
            image(1, "beach.jpg"),
            image(2, "mountain.png"),
            image(3, "beach-sunset.jpg"),
        )

    private fun SemanticsNodeInteraction.click() = performSemanticsAction(SemanticsActions.OnClick)

    private fun setContent(
        images: List<MediaFile> = threeImages,
        isSearchVisible: Boolean = false,
        onRefresh: () -> Unit = {},
        onDeleteImage: (MediaFile) -> Unit = {},
    ) {
        composeRule.setContent {
            OfflineMediaPlayerTheme {
                ImageListContent(
                    images = images,
                    isRefreshing = false,
                    isSearchVisible = isSearchVisible,
                    onRefresh = onRefresh,
                    onDeleteImage = onDeleteImage,
                )
            }
        }
    }

    /**
     * Renders against a list the test can change afterwards.
     *
     * The distinction this exists to draw is the whole of I-7.4: asking to delete and the file
     * actually going away are separate events, separated by a system consent dialog the app does
     * not control. Only a list that changes on the test's command can tell the two apart.
     */
    private fun setMutableContent(
        initial: List<MediaFile> = threeImages,
        onDeleteImage: (MediaFile) -> Unit = {},
    ): (List<MediaFile>) -> Unit {
        var images by mutableStateOf(initial)
        composeRule.setContent {
            OfflineMediaPlayerTheme {
                ImageListContent(
                    images = images,
                    isRefreshing = false,
                    isSearchVisible = false,
                    onRefresh = {},
                    onDeleteImage = onDeleteImage,
                )
            }
        }
        return { updated -> composeRule.runOnIdle { images = updated } }
    }

    // ------------------------------------------------------------------ grid

    @Test
    fun everyImageGetsACell() {
        setContent()

        threeImages.forEach { composeRule.onNodeWithContentDescription(it.title).assertIsDisplayed() }
    }

    @Test
    fun anEmptyLibrarySaysSo() {
        setContent(images = emptyList())

        composeRule.onNodeWithText("No images found on device").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ viewer

    @Test
    fun tappingAnImageOpensTheViewer() {
        setContent()

        composeRule.onNodeWithContentDescription("mountain.png").click()

        // The viewer replaces the grid, so the title appears in the top bar as text rather than
        // only as a cell's content description.
        composeRule.onNodeWithText("mountain.png").assertIsDisplayed()
    }

    @Test
    fun theViewerOpensOnTheImageThatWasTapped() {
        setContent()

        composeRule.onNodeWithContentDescription("beach-sunset.jpg").click()

        composeRule.onNodeWithText("beach-sunset.jpg").assertIsDisplayed()
    }

    @Test
    fun backReturnsToTheGrid() {
        setContent()

        composeRule.onNodeWithContentDescription("beach.jpg").click()
        composeRule.onNodeWithContentDescription("Back").click()

        // The viewer's chrome is gone and every cell is back — the viewer shows one image at a
        // time, so all three being present is only true of the grid.
        composeRule.onNodeWithContentDescription("Back").assertIsNotDisplayed()
        threeImages.forEach { composeRule.onNodeWithContentDescription(it.title).assertIsDisplayed() }
    }

    // ------------------------------------------------------------------ deletion wiring

    /**
     * Pins that confirming the dialog reports the image *currently shown*, not the one the viewer
     * was opened on. The two differ as soon as the user swipes, and the list the callback receives
     * is the filtered one.
     */
    @Test
    fun confirmingTheDialogReportsTheImageOnScreen() {
        var deleted: MediaFile? = null
        setContent(onDeleteImage = { deleted = it })

        composeRule.onNodeWithContentDescription("mountain.png").click()
        composeRule.onNodeWithContentDescription("Delete").click()
        composeRule.onNodeWithText("Delete").click()

        assertEquals(threeImages[1], deleted)
    }

    /**
     * The version of the above that can actually fail.
     *
     * Opening on an image and deleting it without moving cannot distinguish "the image on screen"
     * from "the image the viewer opened on" — they are the same page. Swiping first is what makes
     * the assertion mean something, and a mutation that deletes `images[initialIndex]` survives the
     * test above and dies here.
     *
     * The pager is driven through its scroll-to-index semantics rather than a swipe gesture,
     * because the pages are `AsyncImage`s and synthesised touches do not reach them under
     * Robolectric (F-44).
     */
    @Test
    fun deletingAfterSwipingReportsTheNewPageNotTheOpeningOne() {
        var deleted: MediaFile? = null
        setContent(onDeleteImage = { deleted = it })

        composeRule.onNodeWithContentDescription("beach.jpg").click()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        composeRule.onNodeWithContentDescription("Delete").click()
        composeRule.onNodeWithText("Delete").click()

        assertEquals(threeImages[2], deleted)
    }

    @Test
    fun dismissingTheDialogDeletesNothing() {
        var deleted: MediaFile? = null
        setContent(onDeleteImage = { deleted = it })

        composeRule.onNodeWithContentDescription("mountain.png").click()
        composeRule.onNodeWithContentDescription("Delete").click()
        composeRule.onNodeWithText("Cancel").click()

        assertEquals(null, deleted)
    }
    // ------------------------------------------------------------------ deletion is list-driven

    /**
     * The regression this task exists for.
     *
     * `createDeleteRequest` only emits an IntentSender; the file survives until the user confirms a
     * system dialog. The viewer used to adjust or close itself the instant the app's own dialog was
     * confirmed, so deleting your only photo dropped you back to the grid before you had agreed to
     * anything — and cancelling left you there having done nothing.
     */
    @Test
    fun confirmingDeletionDoesNotCloseTheViewerBeforeTheFileIsGone() {
        setContent(images = listOf(threeImages[0]))

        composeRule.onNodeWithContentDescription("beach.jpg").click()
        composeRule.onNodeWithContentDescription("Delete").click()
        composeRule.onNodeWithText("Delete").click()

        // Still in the viewer: nothing has been removed from the list yet.
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun theViewerClosesOnceTheLastImageIsActuallyRemoved() {
        val setImages = setMutableContent(initial = listOf(threeImages[0]))

        composeRule.onNodeWithContentDescription("beach.jpg").click()
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()

        setImages(emptyList())

        composeRule.onNodeWithContentDescription("Back").assertDoesNotExist()
        composeRule.onNodeWithText("No images found on device").assertIsDisplayed()
    }

    /** Removing some other photo must not eject the user from the one they are looking at. */
    @Test
    fun theViewerStaysOpenWhenADifferentImageIsRemoved() {
        val setImages = setMutableContent()

        composeRule.onNodeWithContentDescription("beach.jpg").click()
        setImages(listOf(threeImages[0], threeImages[1]))

        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeRule.onNodeWithText("beach.jpg").assertIsDisplayed()
    }
    // ------------------------------------------------------------------ zoom gates paging

    /**
     * While an image is magnified a horizontal drag must pan it, not move to the next photo, so the
     * pager's own scrolling is switched off. Asserted through the pager's scroll semantics, which
     * `userScrollEnabled = false` removes.
     */
    @Test
    fun pagingIsDisabledWhileTheImageIsZoomed() {
        setContent()

        composeRule.onNodeWithContentDescription("beach.jpg").click()
        composeRule.onNode(hasScrollToIndexAction()).assertExists()

        composeRule.onNodeWithContentDescription("beach.jpg").performTouchInput { doubleClick() }

        composeRule.onNode(hasScrollToIndexAction()).assertDoesNotExist()
    }
}
