package com.local.offlinemediaplayer.domain

/**
 * The window the activity chart covers.
 *
 * Until this existed the chart was hardcoded to the current Monday-to-Sunday week, so the one
 * question it could not answer was whether this week was normal — last week was unreachable, and
 * the months of history sitting in `daily_playtime` were unreachable with it.
 *
 * The three options are not arbitrary. Each answers a different question at the granularity that
 * question deserves: [WEEK] is "which days do I listen on", [MONTH] is "am I keeping this up", and
 * [YEAR] is "which months did I listen most". A fourth option covering all time was considered and
 * left out — the bucket width would have to vary with how long the user has had the app, so the
 * same chart would mean something different on every device.
 */
enum class StatsRange(
    val label: String,
) {
    WEEK("Week"),
    MONTH("Month"),
    YEAR("Year"),
}
