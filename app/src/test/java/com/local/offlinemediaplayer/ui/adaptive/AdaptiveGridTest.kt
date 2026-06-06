package com.local.offlinemediaplayer.ui.adaptive

import androidx.compose.ui.unit.dp
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.checkAll

class AdaptiveGridTest : StringSpec({
    // Property 4: Adaptive Grid Column Count is Monotone and Correct
    "adaptiveGridColumns is monotone and correct" {
        checkAll(Arb.enum<AppWidthClass>()) { widthClass ->
            val cols = adaptiveGridColumns(widthClass)
            when (widthClass) {
                AppWidthClass.Compact -> cols shouldBe 2
                AppWidthClass.Medium -> cols shouldBe 3
                AppWidthClass.Expanded -> cols shouldBe 4
            }
        }
    }

    // Property 5: Adaptive Image Cell Size is Monotone and Correct
    "adaptiveImageCellSize is monotone and correct" {
        checkAll(Arb.enum<AppWidthClass>()) { widthClass ->
            val size = adaptiveImageCellSize(widthClass)
            when (widthClass) {
                AppWidthClass.Compact -> size shouldBe 100.dp
                AppWidthClass.Medium -> size shouldBe 130.dp
                AppWidthClass.Expanded -> size shouldBe 160.dp
            }
        }
    }
})
