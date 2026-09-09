package com.local.offlinemediaplayer.ui.common

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import java.util.Locale

/**
 * WHY THIS EXISTS — [FormatUtils.formatDuration] renders every duration the user reads (track
 * rows, the Now Playing counters, the album track list), and until now nothing tested it.
 *
 * Three screens had each grown their own private copy instead of calling it, and two of those
 * copies computed `minutes = totalSeconds / 60` with no `% 60` and no hours component. A 65-minute
 * recording therefore rendered as `65:30` on the main player rather than `1:05:30`. Nothing caught
 * it because the duplicates were private, untested, and only wrong past the one-hour mark — which
 * no fixture in the suite crossed.
 *
 * The duplicates are gone and all four call sites now route here, so this spec is what stands
 * between that bug and its return. The hour boundary cases below are the specific regression.
 *
 * The default locale is pinned for the duration of the spec: [FormatUtils.formatDuration] formats
 * through `Locale.getDefault()`, so on a machine set to a locale with non-ASCII digits (ar-EG,
 * fa-IR) the expected strings below would not match — a failure about the test environment rather
 * than about the code.
 */
class FormatUtilsTest :
    StringSpec({
        val originalLocale = Locale.getDefault()
        beforeSpec { Locale.setDefault(Locale.US) }
        afterSpec { Locale.setDefault(originalLocale) }

        "formatDuration omits the hours component below one hour" {
            FormatUtils.formatDuration(0L) shouldBe "0:00"
            FormatUtils.formatDuration(1_000L) shouldBe "0:01"
            FormatUtils.formatDuration(59_000L) shouldBe "0:59"
            FormatUtils.formatDuration(60_000L) shouldBe "1:00"
            FormatUtils.formatDuration(3_599_000L) shouldBe "59:59"
        }

        // The regression. The removed private copies produced "60:00", "65:30" and "600:00" here.
        "formatDuration carries into an hours component at and past one hour" {
            FormatUtils.formatDuration(3_600_000L) shouldBe "1:00:00"
            FormatUtils.formatDuration(3_930_000L) shouldBe "1:05:30"
            FormatUtils.formatDuration(36_000_000L) shouldBe "10:00:00"
        }

        // Sub-second remainders are truncated, not rounded: a position of 1999ms is still "0:01",
        // because rounding up would let the counter reach a value the player has not played yet.
        "formatDuration truncates sub-second remainders" {
            FormatUtils.formatDuration(1_999L) shouldBe "0:01"
            FormatUtils.formatDuration(59_999L) shouldBe "0:59"
        }

        // The property the three duplicates violated, stated directly rather than by example.
        "formatDuration never emits a minutes or seconds field of 60 or more" {
            checkAll(Arb.long(0L..(48L * 60 * 60 * 1000))) { millis ->
                val fields = FormatUtils.formatDuration(millis).split(":")
                fields.size shouldBe if (millis >= 3_600_000L) 3 else 2
                // Every field after the first is a zero-padded 00..59 remainder.
                fields.drop(1).forEach { field ->
                    field.length shouldBe 2
                    (field.toInt() in 0..59) shouldBe true
                }
            }
        }

        "formatSeekTime joins the two durations the player displays" {
            FormatUtils.formatSeekTime(currentMs = 65_000L, totalMs = 3_930_000L) shouldBe
                "1:05 / 1:05:30"
        }

        "formatSize returns a bare zero rather than a computed unit for empty and invalid input" {
            // log10(0) is -Infinity, so the general path would index the units array out of bounds.
            FormatUtils.formatSize(0L) shouldBe "0 B"
            FormatUtils.formatSize(-1L) shouldBe "0 B"
        }

        "formatSize steps up a unit at each 1024 boundary" {
            FormatUtils.formatSize(512L) shouldBe "512 B"
            FormatUtils.formatSize(1_024L) shouldBe "1 KB"
            FormatUtils.formatSize(1_048_576L) shouldBe "1 MB"
            FormatUtils.formatSize(1_073_741_824L) shouldBe "1 GB"
        }

        "formatMinutesToHours drops a zero minutes remainder" {
            FormatUtils.formatMinutesToHours(0) shouldBe "0m"
            FormatUtils.formatMinutesToHours(59) shouldBe "59m"
            FormatUtils.formatMinutesToHours(60) shouldBe "1h"
            FormatUtils.formatMinutesToHours(90) shouldBe "1h 30m"
            FormatUtils.formatMinutesToHours(1_500) shouldBe "25h"
        }

        "formatDate returns empty for the absent-timestamp sentinel MediaStore uses" {
            FormatUtils.formatDate(0L) shouldBe ""
            FormatUtils.formatDate(-1L) shouldBe ""
        }
    })
