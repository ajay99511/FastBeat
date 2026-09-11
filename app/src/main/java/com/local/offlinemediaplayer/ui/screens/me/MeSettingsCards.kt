package com.local.offlinemediaplayer.ui.screens.me

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The two settings cards at the foot of the Me tab.
 *
 * Both were inline blocks in `MeScreen` and read `theme.primaryColor` and a ViewModel directly.
 * They now take the accent colour and a callback, matching the signature style
 * [LibraryStatsSection] and [ActivityTrendsSection] already use, which is what makes them
 * renderable in isolation.
 */
@Composable
internal fun DarkModeToggleCard(
    isDarkTheme: Boolean,
    primaryColor: Color,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector =
                        if (isDarkTheme) {
                            Icons.Outlined.DarkMode
                        } else {
                            Icons.Outlined.LightMode
                        },
                    contentDescription = null,
                    tint = primaryColor,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text =
                        if (isDarkTheme) {
                            "Dark Mode"
                        } else {
                            "Light Mode"
                        },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Switch(
                checked = isDarkTheme,
                onCheckedChange = { onToggle() },
                colors =
                    SwitchDefaults.colors(
                        checkedThumbColor = primaryColor,
                        checkedTrackColor =
                            primaryColor.copy(
                                alpha = 0.3f,
                            ),
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.Transparent,
                    ),
            )
        }
    }
}

@Composable
internal fun AccessibilityGuideCard(
    primaryColor: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).clickable {
                onClick()
            },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Accessibility,
                    contentDescription = null,
                    tint = primaryColor,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Accessibility Guide",
                        style =
                            MaterialTheme.typography
                                .titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Learn about app features",
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
