package com.local.offlinemediaplayer.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.local.offlinemediaplayer.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [UserMessage].
 *
 * The formatting path is the part worth pinning: a message that takes arguments will crash or
 * render `%1$s` literally if the resource and the call site disagree, and that only shows up when
 * the specific action is performed.
 */
@RunWith(RobolectricTestRunner::class)
class UserMessageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun aMessageWithoutArgumentsResolvesDirectly() {
        assertEquals("Added to queue", UserMessage.of(R.string.msg_added_to_queue).resolve(context))
    }

    @Test
    fun anIntegerArgumentIsSubstituted() {
        assertEquals(
            "Sleep timer set for 20 min",
            UserMessage.of(R.string.msg_sleep_timer_set, 20).resolve(context),
        )
    }

    @Test
    fun aStringArgumentIsSubstituted() {
        assertEquals(
            "Saved queue as \"Road trip\"",
            UserMessage.of(R.string.msg_saved_queue_as, "Road trip").resolve(context),
        )
    }

    @Test
    fun wordingIsPreservedFromTheHardcodedOriginals() {
        // P4-F.2 must change where a message comes from, not what the user reads.
        assertEquals("Will play next", UserMessage.of(R.string.msg_will_play_next).resolve(context))
        assertEquals(
            "Turn off shuffle to reorder the queue",
            UserMessage.of(R.string.msg_turn_off_shuffle_to_reorder).resolve(context),
        )
        assertEquals(
            "Sleep timer ended — playback paused",
            UserMessage.of(R.string.msg_sleep_timer_ended).resolve(context),
        )
    }

    @Test
    fun messagesWithTheSameContentCompareEqual() {
        assertEquals(
            UserMessage.of(R.string.msg_sleep_timer_set, 20),
            UserMessage.of(R.string.msg_sleep_timer_set, 20),
        )
    }
}
