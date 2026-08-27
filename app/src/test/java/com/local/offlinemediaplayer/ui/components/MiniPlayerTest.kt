package com.local.offlinemediaplayer.ui.components

import android.net.Uri
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.ui.theme.OfflineMediaPlayerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P5-G.1 — `MiniPlayer` renders the title, the play/pause control toggles, and a tap navigates.
 *
 * Drives [MiniPlayerContent], the stateless half extracted for this test. The ViewModel-bound
 * `MiniPlayer` wrapper cannot be driven from a unit test at all — constructing a
 * `PlaybackViewModel` pulls in Hilt, Room and a MediaController — which is exactly the coupling
 * F-7 predicted would block P5-G.
 *
 * Runs on Robolectric in `src/test` rather than in `androidTest`, so it runs on a CI runner with no
 * emulator. Same trade-off OQ-8 settled for the DAO and repository tests.
 *
 * **Why [click] invokes the semantics action instead of `performClick()`.** The mini player draws
 * album art through Coil, and under Robolectric an `AsyncImage` leaves the composition in a state
 * where a synthesised touch never reaches the gesture detector — the callback simply never fires.
 * That was measured, not guessed: adding a single `AsyncImage` to an otherwise identical Surface
 * took a passing click assertion from 1 to 0, and a fake Coil loader did not restore it (F-44).
 * Invoking the `OnClick` semantics action runs the same lambda the modifier was given, so the
 * wiring is genuinely covered; what is *not* covered here is hit-testing and overlap, which needs
 * a real device. `PermissionScreensTest` draws no artwork and does use `performClick()`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MiniPlayerTest {
    @get:Rule
    val compose = createComposeRule()

    private fun track(
        title: String = "Bravo Tone",
        artist: String? = "Test Artist",
        isVideo: Boolean = false,
    ) = MediaFile(
        id = 1,
        uri = Uri.EMPTY,
        title = title,
        duration = 60_000,
        isVideo = isVideo,
        artist = artist,
    )

    private fun SemanticsNodeInteraction.click() = performSemanticsAction(SemanticsActions.OnClick)

    /** Records every callback the mini player can fire, so each test asserts on one of them. */
    private class Taps {
        var tap = 0
        var playPause = 0
        var previous = 0
        var next = 0
    }

    private fun setContent(
        media: MediaFile? = track(),
        isPlaying: Boolean = false,
        hasPrevious: Boolean = true,
        hasNext: Boolean = true,
        taps: Taps = Taps(),
    ): Taps {
        compose.setContent {
            OfflineMediaPlayerTheme {
                MiniPlayerContent(
                    track = media,
                    isPlaying = isPlaying,
                    duration = 60_000,
                    positionFlow = MutableStateFlow(0L),
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                    onTap = { taps.tap++ },
                    onPlayPause = { taps.playPause++ },
                    onPrevious = { taps.previous++ },
                    onNext = { taps.next++ },
                )
            }
        }
        return taps
    }

    // ------------------------------------------------------------------ rendering

    @Test
    fun rendersTheTrackTitleAndArtist() {
        setContent()

        compose.onNodeWithText("Bravo Tone").assertIsDisplayed()
        compose.onNodeWithText("Test Artist").assertIsDisplayed()
    }

    @Test
    fun fallsBackToUnknownArtistRatherThanRenderingNothing() {
        setContent(media = track(artist = null))

        compose.onNodeWithText("Unknown Artist").assertIsDisplayed()
    }

    /** Nothing is playing, so there is no mini player to show. */
    @Test
    fun rendersNothingWithoutATrack() {
        setContent(media = null)

        compose.onNodeWithText("Bravo Tone").assertDoesNotExist()
    }

    /**
     * The mini player is an audio surface. A video track reaching it would render a second set of
     * transport controls underneath the video player's own — the comment in the source calls this
     * out as an "Architecture Fix", so it is worth a test that would catch its removal.
     */
    @Test
    fun rendersNothingForAVideoTrack() {
        setContent(media = track(title = "A Movie", isVideo = true))

        compose.onNodeWithText("A Movie").assertDoesNotExist()
    }

    // ------------------------------------------------------------------ play/pause toggle

    @Test
    fun showsAPlayControlWhilePaused() {
        setContent(isPlaying = false)

        compose.onNodeWithContentDescription("Play").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pause").assertDoesNotExist()
    }

    @Test
    fun showsAPauseControlWhilePlaying() {
        setContent(isPlaying = true)

        compose.onNodeWithContentDescription("Pause").assertIsDisplayed()
        compose.onNodeWithContentDescription("Play").assertDoesNotExist()
    }

    @Test
    fun tappingPlayPauseFiresTheCallbackExactlyOnce() {
        val taps = setContent(isPlaying = false)

        compose.onNodeWithContentDescription("Play").click()

        assertEquals(1, taps.playPause)
        assertEquals("only the transport control was tapped", 0, taps.tap)
    }

    // ------------------------------------------------------------------ navigation

    /** Tapping the row is what opens Now Playing — the whole surface is the target, not a button. */
    @Test
    fun tappingTheRowRequestsNavigation() {
        val taps = setContent()

        compose.onNodeWithText("Bravo Tone").click()

        assertEquals(1, taps.tap)
        assertEquals("navigating must not also toggle playback", 0, taps.playPause)
    }

    // ------------------------------------------------------------------ skip controls

    @Test
    fun skipControlsFireTheirOwnCallbacks() {
        val taps = setContent()

        compose.onNodeWithContentDescription("Next").click()
        compose.onNodeWithContentDescription("Previous").click()

        assertEquals(1, taps.next)
        assertEquals(1, taps.previous)
    }

    /**
     * At the end of a queue there is nothing to skip to, and the control must be disabled rather
     * than merely inert — a button that looks live and does nothing is worse than a greyed-out one.
     */
    @Test
    fun theSkipControlIsDisabledWhenThereIsNothingToSkipTo() {
        setContent(hasNext = false)

        compose.onNodeWithContentDescription("Next").assertIsNotEnabled()
    }
}
