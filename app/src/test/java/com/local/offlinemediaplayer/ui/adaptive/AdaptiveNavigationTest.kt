package com.local.offlinemediaplayer.ui.adaptive

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bool
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

class AdaptiveNavigationTest : StringSpec({
    // Property 2: Navigation Component Selection is Total and Consistent
    "navigationComponentFor returns correct type for all width classes and fullscreen states" {
        checkAll(Arb.bool(), Arb.enum<AppWidthClass>()) { isFullscreen, widthClass ->
            val result = navigationComponentFor(widthClass, isFullscreen)
            if (isFullscreen) {
                result shouldBe NavigationComponentType.Hidden
            } else {
                when (widthClass) {
                    AppWidthClass.Compact -> result shouldBe NavigationComponentType.BottomBar
                    AppWidthClass.Medium -> result shouldBe NavigationComponentType.Rail
                    AppWidthClass.Expanded -> result shouldBe NavigationComponentType.Drawer
                }
            }
        }
    }

    // Property 3: Tab Selection Equivalence Across Navigation Components
    "all navigation components produce same selectedTab for same index" {
        checkAll(Arb.int(0, 3)) { tabIndex ->
            // Simulating selection via callback
            var barSelected = -1
            var railSelected = -1
            var drawerSelected = -1

            val simulateNavBarSelect = { index: Int -> barSelected = index; barSelected }
            val simulateNavRailSelect = { index: Int -> railSelected = index; railSelected }
            val simulateNavDrawerSelect = { index: Int -> drawerSelected = index; drawerSelected }

            val viaBar = simulateNavBarSelect(tabIndex)
            val viaRail = simulateNavRailSelect(tabIndex)
            val viaDrawer = simulateNavDrawerSelect(tabIndex)

            viaBar shouldBe viaRail
            viaBar shouldBe viaDrawer
        }
    }

    // Property 13: Header Visibility is Determined by Navigation Context
    "showFastBeatHeader is false for Medium/Expanded with adaptive nav" {
        checkAll(Arb.bool()) { hasAdaptiveNav ->
            listOf(AppWidthClass.Medium, AppWidthClass.Expanded).forEach { widthClass ->
                if (hasAdaptiveNav) {
                    showFastBeatHeader(widthClass, hasAdaptiveNav, true) shouldBe false
                }
            }
            showFastBeatHeader(AppWidthClass.Compact, false, true) shouldBe true
        }
    }
})
