package com.local.offlinemediaplayer.ui.components

/** The words a delete confirmation shows. */
internal data class DeletePrompt(
    val title: String,
    val body: String,
    val confirmLabel: String,
)

/**
 * Builds the confirmation copy for a delete, telling the truth about whether it can be undone.
 *
 * The dialog promised *"permanently delete… This action cannot be undone"* whatever actually
 * happened. Once images move to the system trash on API 30+, that sentence becomes a lie on most
 * devices — and the one below it, on an Android 9 phone where there is no trash, stays true. A
 * single hardcoded string cannot be right in both places.
 *
 * **The same [movesToTrash] flag chooses the copy and the request**, which is the only way the two
 * can be guaranteed to agree. A dialog that says "cannot be undone" over a recoverable action
 * teaches the user to distrust the warning; one that promises a trash that does not exist is worse,
 * because they will go looking for the file.
 */
internal fun deletePrompt(
    count: Int,
    movesToTrash: Boolean,
): DeletePrompt {
    val plural = count > 1
    val these = if (plural) "$count files" else "this file"
    val them = if (plural) "them" else "it"

    return if (movesToTrash) {
        DeletePrompt(
            title = if (plural) "Move $count files to Trash?" else "Move to Trash?",
            // No promise of thirty days: the retention period is the system's to decide and OEMs
            // vary. What is reliably true is that the file goes somewhere recoverable.
            body =
                "This moves $these to your device's trash. You can restore $them from your " +
                    "gallery until the system clears it.",
            confirmLabel = "Move to Trash",
        )
    } else {
        DeletePrompt(
            title = if (plural) "Delete Files?" else "Delete File?",
            body =
                "Are you sure you want to permanently delete $these from your device? " +
                    "This action cannot be undone.",
            confirmLabel = "Delete",
        )
    }
}
