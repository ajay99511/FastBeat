package com.local.offlinemediaplayer.ui.adaptive

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.DrawerState

data class AppNavigationDestination(
    val tabIndex: Int,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val contentDescription: String
)

val APP_DESTINATIONS: List<AppNavigationDestination> = listOf(
    AppNavigationDestination(0, "Videos", Icons.Filled.PlayArrow, Icons.Outlined.PlayArrow, "Videos"),
    AppNavigationDestination(1, "Music", Icons.Filled.MusicNote, Icons.Outlined.MusicNote, "Music"),
    AppNavigationDestination(2, "Images", Icons.Filled.Image, Icons.Outlined.Image, "Images"),
    AppNavigationDestination(3, "Stats", Icons.Filled.Analytics, Icons.Outlined.Analytics, "Stats"),
)

enum class NavigationComponentType {
    BottomBar, Rail, Drawer, Hidden
}

fun navigationComponentFor(widthClass: AppWidthClass, isFullscreen: Boolean): NavigationComponentType {
    if (isFullscreen) return NavigationComponentType.Hidden
    return when (widthClass) {
        AppWidthClass.Compact -> NavigationComponentType.BottomBar
        AppWidthClass.Medium -> NavigationComponentType.Rail
        AppWidthClass.Expanded -> NavigationComponentType.Drawer
    }
}

fun showFastBeatHeader(widthClass: AppWidthClass, hasAdaptiveNav: Boolean, isExistingConditionMet: Boolean): Boolean {
    if (widthClass == AppWidthClass.Compact) return isExistingConditionMet
    if (hasAdaptiveNav) return false
    return isExistingConditionMet
}

@Composable
fun FastBeatNavigationRail(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    themeColor: Color
) {
    NavigationRail(
        containerColor = Color.Transparent,
    ) {
        APP_DESTINATIONS.forEach { destination ->
            NavigationRailItem(
                selected = selectedTab == destination.tabIndex,
                onClick = { onTabSelected(destination.tabIndex) },
                icon = {
                    Icon(
                        imageVector = if (selectedTab == destination.tabIndex) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = destination.contentDescription
                    )
                },
                label = { Text(destination.label) }
            )
        }
    }
}

@Composable
fun FastBeatNavigationDrawer(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    themeColor: Color,
    content: @Composable () -> Unit
) {
    ModalNavigationDrawer(
        drawerState = DrawerState(initialValue = DrawerValue.Open),
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color.Transparent
            ) {
                APP_DESTINATIONS.forEach { destination ->
                    NavigationDrawerItem(
                        label = { Text(destination.label) },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == destination.tabIndex) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = destination.contentDescription
                            )
                        },
                        selected = selectedTab == destination.tabIndex,
                        onClick = { onTabSelected(destination.tabIndex) },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = themeColor.copy(alpha = 0.12f),
                            unselectedContainerColor = Color.Transparent
                        )
                    )
                }
            }
        },
        content = content
    )
}
