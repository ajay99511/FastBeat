package com.local.offlinemediaplayer.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.local.offlinemediaplayer.ui.theme.OfflineMediaPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P5-G.2 — the permission flow: the rationale shows, granting triggers the request, and a
 * permanent denial redirects to Settings.
 *
 * These three screens are the entire permission UI, and the distinction between them matters more
 * than it looks. Android stops showing the system dialog once a user has denied twice; from that
 * point "Try Again" is a dead end and only Settings can recover the app. Getting the wrong screen
 * in front of the user is therefore not cosmetic — it is the difference between a recoverable state
 * and an app that appears permanently broken.
 *
 * Runs on Robolectric in `src/test` so it runs in CI without an emulator (OQ-8).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PermissionScreensTest {
    @get:Rule
    val compose = createComposeRule()

    // ------------------------------------------------------------------ first ask

    @Test
    fun theFirstAskExplainsWhyAndOffersToRequest() {
        var requests = 0
        compose.setContent {
            OfflineMediaPlayerTheme { PermissionRequestScreen(onRequestPermission = { requests++ }) }
        }

        compose.onNodeWithText("Storage Permission Required").assertIsDisplayed()
        compose
            .onNodeWithText("This app needs access to your media files to play videos and music.")
            .assertIsDisplayed()

        compose.onNodeWithText("Grant Permission").performClick()

        assertEquals("the button must actually launch the request", 1, requests)
    }

    /** The first ask has no Settings escape hatch — the system dialog has not been spent yet. */
    @Test
    fun theFirstAskDoesNotOfferSettings() {
        compose.setContent {
            OfflineMediaPlayerTheme { PermissionRequestScreen(onRequestPermission = {}) }
        }

        compose.onNodeWithText("Open App Settings").assertDoesNotExist()
    }

    // ------------------------------------------------------------------ rationale (can ask again)

    @Test
    fun theRationaleShowsAfterADenialAndCanAskAgain() {
        compose.setContent {
            OfflineMediaPlayerTheme {
                PermissionRationaleScreen(onRequestPermission = {}, onOpenSettings = {})
            }
        }

        compose.onNodeWithText("Permission Denied").assertIsDisplayed()
        compose.onNodeWithText("Try Again").assertIsDisplayed()
    }

    @Test
    fun theRationaleRetryButtonRequestsThePermissionAgain() {
        var requests = 0
        var settings = 0
        compose.setContent {
            OfflineMediaPlayerTheme {
                PermissionRationaleScreen(
                    onRequestPermission = { requests++ },
                    onOpenSettings = { settings++ },
                )
            }
        }

        compose.onNodeWithText("Try Again").performClick()

        assertEquals(1, requests)
        assertEquals("retrying must not bounce the user out to Settings", 0, settings)
    }

    /**
     * The rationale offers both routes. A user who has denied once can still be asked by the
     * system, but may prefer to go straight to Settings — and the two buttons must not be wired to
     * the same callback, which a copy-paste edit could easily do.
     */
    @Test
    fun theRationaleAlsoOffersSettingsOnASeparateCallback() {
        var requests = 0
        var settings = 0
        compose.setContent {
            OfflineMediaPlayerTheme {
                PermissionRationaleScreen(
                    onRequestPermission = { requests++ },
                    onOpenSettings = { settings++ },
                )
            }
        }

        compose.onNodeWithText("Open App Settings").performClick()

        assertEquals(1, settings)
        assertEquals("the two buttons are not the same action", 0, requests)
    }

    // ------------------------------------------------------------------ permanent denial

    /**
     * Once the denial is permanent, the retry route must be gone. Leaving a "Try Again" button here
     * would give the user a button that silently does nothing, because the system will no longer
     * show the dialog.
     */
    @Test
    fun thePermanentDenialScreenOffersOnlySettings() {
        compose.setContent {
            OfflineMediaPlayerTheme { PermissionSettingsScreen(onOpenSettings = {}) }
        }

        compose.onNodeWithText("Media Access Needed").assertIsDisplayed()
        compose.onNodeWithText("Open App Settings").assertIsDisplayed()
        compose.onNodeWithText("Try Again").assertDoesNotExist()
    }

    @Test
    fun thePermanentDenialScreenOpensSettings() {
        var settings = 0
        compose.setContent {
            OfflineMediaPlayerTheme { PermissionSettingsScreen(onOpenSettings = { settings++ }) }
        }

        compose.onNodeWithText("Open App Settings").performClick()

        assertEquals(1, settings)
    }
}
