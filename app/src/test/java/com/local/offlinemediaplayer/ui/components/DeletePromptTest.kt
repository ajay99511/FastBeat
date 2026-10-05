package com.local.offlinemediaplayer.ui.components

import com.local.offlinemediaplayer.playback.supportsTrash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * That the confirmation says what will actually happen.
 *
 * The invariant I-7.6 exists to hold is that **the copy and the behaviour cannot disagree**, and
 * the way that is achieved is that one boolean picks both. These tests pin the two halves of it:
 * the wording changes with the flag, and the flag itself is the API-level rule.
 *
 * Getting this wrong in either direction costs the user something real. Promising a trash that does
 * not exist on their Android 9 phone sends them looking for a file that is gone. Warning "cannot be
 * undone" over a recoverable action teaches them to ignore the warning that one day matters.
 */
class DeletePromptTest {
    // ------------------------------------------------------------------ permanent

    @Test
    fun aPermanentDeleteSaysItCannotBeUndone() {
        val prompt = deletePrompt(count = 1, movesToTrash = false)

        assertTrue(prompt.body, prompt.body.contains("permanently"))
        assertTrue(prompt.body, prompt.body.contains("cannot be undone"))
        assertEquals("Delete", prompt.confirmLabel)
    }

    @Test
    fun aPermanentDeleteOfSeveralFilesCountsThem() {
        val prompt = deletePrompt(count = 7, movesToTrash = false)

        assertEquals("Delete Files?", prompt.title)
        assertTrue(prompt.body, prompt.body.contains("7 files"))
    }

    // ------------------------------------------------------------------ trash

    /** The whole point: a recoverable action must not be described as irreversible. */
    @Test
    fun aTrashedDeleteNeverClaimsToBePermanent() {
        val prompt = deletePrompt(count = 1, movesToTrash = true)

        assertFalse(prompt.body, prompt.body.contains("permanently"))
        assertFalse(prompt.body, prompt.body.contains("cannot be undone"))
        assertTrue(prompt.body, prompt.body.contains("restore"))
    }

    @Test
    fun aTrashedDeleteSaysWhereTheFileWentAndWhatToDoAboutIt() {
        val prompt = deletePrompt(count = 1, movesToTrash = true)

        assertEquals("Move to Trash?", prompt.title)
        assertEquals("Move to Trash", prompt.confirmLabel)
        assertTrue(prompt.body, prompt.body.contains("trash"))
    }

    @Test
    fun aTrashedDeleteOfSeveralFilesCountsThem() {
        val prompt = deletePrompt(count = 4, movesToTrash = true)

        assertEquals("Move 4 files to Trash?", prompt.title)
        assertTrue(prompt.body, prompt.body.contains("4 files"))
        assertTrue(prompt.body, prompt.body.contains("them"))
    }

    /**
     * Retention is the system's to decide and OEMs vary, so the copy must not name a number. A
     * promise of thirty days is one the app cannot keep.
     */
    @Test
    fun theTrashCopyPromisesNoParticularRetentionPeriod() {
        val body = deletePrompt(count = 1, movesToTrash = true).body

        assertFalse(body, body.contains("30"))
        assertFalse(body, body.contains("thirty"))
        assertFalse(body, body.contains("day"))
    }

    // ------------------------------------------------------------------ the flag itself

    /**
     * `createTrashRequest` arrived in API 30. Below it a delete is permanent whatever was asked
     * for, so this is the single rule both the copy and the request are chosen by — the only way
     * they are guaranteed to agree.
     */
    @Test
    fun trashIsOfferedOnlyWhereThePlatformHasOne() {
        assertTrue("API 30 is where createTrashRequest arrived", supportsTrash(sdkInt = 30))
        assertTrue(supportsTrash(sdkInt = 34))
        assertFalse("Android 10 has no trash", supportsTrash(sdkInt = 29))
        assertFalse("minSdk is 26, which has no trash either", supportsTrash(sdkInt = 26))
    }
}
