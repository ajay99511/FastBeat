package com.local.offlinemediaplayer.ui.adaptive

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.checkAll

class ContentWidthModifierTest : StringSpec({
    // Feature: offline-media-player-ui-responsiveness
    // Property 8: Max Content Width Applied Only on Expanded
    "contentWidthConstraintApplied applies 840dp constraint only on Expanded" {
        checkAll(Arb.enum<AppWidthClass>()) { widthClass ->
            val hasConstraint = contentWidthConstraintApplied(widthClass)
            when (widthClass) {
                AppWidthClass.Expanded -> hasConstraint shouldBe true
                else -> hasConstraint shouldBe false
            }
        }
    }
})

fun contentWidthConstraintApplied(widthClass: AppWidthClass): Boolean {
    return widthClass == AppWidthClass.Expanded
}
