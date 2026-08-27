package com.local.offlinemediaplayer.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates `baseline-prof.txt` for FastBeat (P5-E).
 *
 * A baseline profile lists the classes and methods ART compiles ahead of time at install time
 * instead of interpreting on first use. It is worth exactly what the journeys below touch: code
 * these journeys never reach stays un-precompiled. **The journeys are the deliverable** — the
 * plugin wiring is only how they get recorded.
 *
 * Collection is split in two — a startup-only pass and a journeys pass — because the two feed
 * different files with different ART priorities. The plugin merges the results into the single
 * `baseline-prof.txt` / `startup-prof.txt` pair that `:app` consumes and ships.
 *
 * Selectors are text and content-description based, never resource ids: this is a Compose UI, so
 * there are no view ids, and the labels used here (`Music`, `Play All`, the mini player's
 * `Play`/`Pause` description) are the app's own accessibility surface, which is more stable than
 * pixel coordinates and is checked by other tests too.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    /**
     * The **startup** profile: cold launch and nothing else.
     *
     * This is deliberately a separate collection from [journeys]. `includeInStartupProfile` marks
     * everything the block touches as startup-critical, and ART gives the startup profile priority
     * over the general one — so folding the journeys in here would flag ~19 000 of ~26 000 entries
     * as startup, including video-folder browsing, and there would be nothing left for the
     * prioritisation to prioritise. That is not hypothetical: the first version of this file used a
     * single block and produced a `startup-prof.txt` byte-identical to `baseline-prof.txt`.
     */
    @Test
    fun startup() =
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = true,
        ) {
            grantMediaPermissions(device)
            pressHome()
            startActivityAndWait()
        }

    /** The two journeys the P5-E card names. Not startup — see [startup]. */
    @Test
    fun journeys() =
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = false,
        ) {
            // A freshly installed release variant holds no runtime permissions, so without this the
            // app would sit on its permission screen and the profile would cover nothing but that
            // screen. Granting is idempotent, so it is safe to repeat every iteration.
            grantMediaPermissions(device)

            pressHome()
            startActivityAndWait()
            device.awaitIdle()

            audioLibraryJourney(device)
            videoLibraryJourney(device)
        }

    /** Journey 1 — launch → audio library → play. */
    private fun audioLibraryJourney(device: UiDevice) {
        if (!device.waitAndClick(By.text("Music"))) return
        device.awaitIdle()

        // Scroll before playing: this pulls LazyColumn item composition and the thumbnail path
        // into the profile, which is what a cold launch onto this screen actually pays for.
        device.scrollFirstList()

        // "Play All" binds the MediaController, builds the queue and starts ExoPlayer — the most
        // expensive first-use path in the app.
        if (!device.waitAndClick(By.text("Play All"))) return
        device.awaitIdle()

        openNowPlayingFromMiniPlayer(device)
    }

    /** Journey 2 — launch → video list → play. */
    private fun videoLibraryJourney(device: UiDevice) {
        if (!device.waitAndClick(By.text("Videos"))) return
        device.awaitIdle()

        // Both video tabs, because each hosts a different list implementation.
        device.waitAndClick(By.text("MOVIES"))
        device.awaitIdle()
        device.scrollFirstList()

        device.waitAndClick(By.text("FOLDERS"))
        device.awaitIdle()
        device.scrollFirstList()
    }

    /**
     * Opens Now Playing by tapping the mini player's title area.
     *
     * The mini player has no id and its title is whatever happens to be playing, so the anchor is
     * its play/pause button — the one element with a fixed content description. The tap then lands
     * on the title area to its left, which is part of the same clickable row. Tapping the button
     * itself would toggle playback instead of navigating.
     */
    private fun openNowPlayingFromMiniPlayer(device: UiDevice) {
        val transport =
            device.wait(Until.findObject(By.desc("Pause")), UI_TIMEOUT_MS)
                ?: device.wait(Until.findObject(By.desc("Play")), UI_TIMEOUT_MS)
                ?: return

        val bounds = transport.visibleBounds
        device.click(bounds.left / 3, bounds.centerY())
        device.awaitIdle()
        device.pressBack()
        device.awaitIdle()
    }

    private companion object {
        const val TARGET_PACKAGE = "com.local.offlinemediaplayer"
        const val UI_TIMEOUT_MS = 5_000L

        val MEDIA_PERMISSIONS =
            listOf(
                "android.permission.READ_MEDIA_AUDIO",
                "android.permission.READ_MEDIA_VIDEO",
                "android.permission.READ_MEDIA_IMAGES",
                // Pre-API-33 devices; `pm grant` simply fails for a permission the platform does
                // not know, which is why each grant is issued separately and unchecked.
                "android.permission.READ_EXTERNAL_STORAGE",
            )

        fun grantMediaPermissions(device: UiDevice) {
            MEDIA_PERMISSIONS.forEach { permission ->
                runCatching { device.executeShellCommand("pm grant $TARGET_PACKAGE $permission") }
            }
        }

        /**
         * Clicks the first element matching [selector], or returns false if it never appears.
         *
         * Returning false rather than throwing is deliberate. Profile generation runs against
         * whatever media library the generating machine happens to have, and a device with no
         * videos cannot reach a video screen. Failing the build there would mean the profile could
         * only ever be regenerated on one particular machine. A journey that covers less yields a
         * smaller profile — a performance shortfall, not a broken build.
         */
        fun UiDevice.waitAndClick(selector: BySelector): Boolean {
            val found = wait(Until.findObject(selector), UI_TIMEOUT_MS) ?: return false
            found.click()
            return true
        }

        fun UiDevice.scrollFirstList() {
            findObject(By.scrollable(true))?.fling(Direction.DOWN)
            awaitIdle()
        }

        fun UiDevice.awaitIdle() {
            waitForIdle(UI_TIMEOUT_MS)
        }
    }
}
