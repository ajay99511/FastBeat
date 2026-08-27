package com.local.offlinemediaplayer.domain

import javax.inject.Inject

/**
 * How many consecutive days, ending today or yesterday, the user has listened on.
 *
 * Extracted from `AnalyticsViewModel` (P5-A.1), where it sat inline inside a six-way `combine`
 * inside a `stateIn` — reachable only by constructing the ViewModel, and therefore never tested.
 * It is the one piece of real arithmetic on that screen and the one most likely to be quietly
 * wrong.
 *
 * **Takes data, not a DAO.** The card sketched every use case as DAO-injected with a `suspend`
 * invoke, but the caller here needs a *Flow* of active days so the streak recomputes as playtime is
 * recorded. Injecting the DAO and reading it once with `first()` would have turned a reactive
 * screen into a stale one — a behaviour change smuggled in by a structural refactor. Taking the
 * already-collected list keeps the ViewModel's `combine` exactly as it was.
 *
 * The rules, all inherited from the code this replaces:
 *  - [activeDays] arrives newest-first, normalised to midnight (`ORDER BY date DESC`), and only
 *    contains days with more than a minute of playtime — that filter is the query's, not this
 *    function's.
 *  - A streak must be *current*: if the newest active day is older than yesterday the streak is 0,
 *    however long the historical run was. Today being absent is not a break, because the day is
 *    still in progress.
 *  - Walking back, days must be exactly one apart; the first gap ends the count.
 *
 * Known limitation, carried over unchanged: a day is a fixed 86 400 000 ms, so a DST transition
 * shifts midnight and can break a streak that the user did not actually break.
 */
class CalculateStreakUseCase
    @Inject
    constructor() {
        operator fun invoke(
            activeDays: List<Long>,
            today: Long,
        ): Int {
            val lastActive = activeDays.firstOrNull() ?: return 0
            if (lastActive != today && lastActive != today - DAY_MS) return 0

            var streak = 1
            var checkDate = lastActive
            for (index in 1 until activeDays.size) {
                val previousDay = activeDays[index]
                if (checkDate - previousDay != DAY_MS) break
                streak++
                checkDate = previousDay
            }
            return streak
        }

        private companion object {
            const val DAY_MS = 86_400_000L
        }
    }
