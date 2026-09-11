package com.local.offlinemediaplayer.ui.screens.me

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The three-swatch curated-theme picker at the top of the Me tab.
 *
 * The swatch colours are the literals `MeScreen` already used; they mirror `ThemeViewModel.themes`
 * and are left as-is here so this move changes nothing on screen. Unifying the two sources is
 * tracked separately — see the `me` package README note in the decomposition plan.
 */
@Composable
internal fun ThemeSwitcherRow(
    activeThemeId: String,
    onThemeSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeButton(
            icon = Icons.Filled.Bolt,
            color = Color(0xFFFF5500),
            isActive = activeThemeId == "orange",
            onClick = { onThemeSelected("orange") },
        )
        Spacer(modifier = Modifier.width(24.dp))
        ThemeButton(
            icon = Icons.Filled.Star,
            color = Color(0xFF00E5FF),
            isActive = activeThemeId == "blue",
            onClick = { onThemeSelected("blue") },
        )
        Spacer(modifier = Modifier.width(24.dp))
        ThemeButton(
            icon = Icons.Filled.OpenWith,
            color = Color(0xFF22C55E),
            isActive = activeThemeId == "green",
            onClick = { onThemeSelected("green") },
        )
    }
}

@Composable
private fun ThemeButton(
    icon: ImageVector,
    color: Color,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .shadow(
                    elevation = if (isActive) 16.dp else 0.dp,
                    spotColor = if (isActive) color else Color.Transparent,
                    shape = CircleShape,
                ).background(
                    if (isActive) color else MaterialTheme.colorScheme.surface,
                    CircleShape,
                ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint =
                if (isActive) {
                    Color.White
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}
