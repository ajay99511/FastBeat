package com.local.offlinemediaplayer.model

import android.content.Context
import androidx.annotation.StringRes

/**
 * A short informational message for the user — "Added to queue", "Subtitle added", "Sleep timer set
 * for 20 min".
 *
 * Introduced by P4-F.2 alongside [AppError], and deliberately a **separate type**. The card
 * described migrating `MutableSharedFlow<String>` onto `AppError`, but most of what that flow
 * carries is not an error: modelling "Added to queue" as an `AppError` would misreport a success as
 * a failure, and would rule out ever styling the two differently. Errors get [AppError];
 * confirmations get this.
 *
 * Holding a resource id plus its format arguments — rather than a finished `String` — is what makes
 * the message translatable. The ViewModel says *what happened*; the UI, which has a `Context` and
 * therefore a locale, decides how to phrase it.
 */
data class UserMessage(
    @param:StringRes val messageRes: Int,
    val args: List<Any> = emptyList(),
) {
    /**
     * `SpreadOperator` is suppressed rather than baselined: `Context.getString(int, vararg Object)`
     * can only be called with a spread, and the array is at most a couple of elements built once
     * per message. The alternative is dropping format arguments entirely, which would put
     * translation back out of reach.
     */
    @Suppress("SpreadOperator")
    fun resolve(context: Context): String =
        if (args.isEmpty()) {
            context.getString(messageRes)
        } else {
            context.getString(messageRes, *args.toTypedArray())
        }

    companion object {
        fun of(
            @StringRes messageRes: Int,
            vararg args: Any,
        ): UserMessage = UserMessage(messageRes, args.toList())
    }
}
