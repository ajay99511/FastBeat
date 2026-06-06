package com.local.offlinemediaplayer.ui.adaptive

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import kotlin.math.abs

class AppWidthClassTest : StringSpec({
    // Feature: offline-media-player-ui-responsiveness
    // Property 1: Width Classification Covers All Values
    "appWidthClassFromDp classifies all widths correctly" {
        checkAll<Float>(PropTestConfig(iterations = 200)) { width ->
            val absWidth = abs(width)
            val result = appWidthClassFromDp(absWidth)
            when {
                absWidth < 600f -> result shouldBe AppWidthClass.Compact
                absWidth < 840f -> result shouldBe AppWidthClass.Medium
                else -> result shouldBe AppWidthClass.Expanded
            }
        }
    }
})
