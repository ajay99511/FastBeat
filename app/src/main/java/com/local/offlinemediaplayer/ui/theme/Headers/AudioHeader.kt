package com.local.offlinemediaplayer.ui.theme.Headers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AudioHeader() {
    val neonCyan = Color(0xFFC9B458) // Cyan/Teal for VIDEO PLAYER text
    val darkBg = Color(0xFF0B0B0F)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(darkBg)
            .statusBarsPadding()
            .padding(top = 8.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // "FASTBEAT"
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FAST",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = Color.White
                )
//                style=MaterialTheme.typography.titleLarge
            )
            Text(
                text = "BEAT",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = neonCyan
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Line - VIDEO PLAYER - Line
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Left Line
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(2.dp)
                    .background(Color(0xFF1E293B)) // Dark slate line
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "MUSIC PLAYER",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                    color = neonCyan
                )
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Right Line
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(2.dp)
                    .background(Color(0xFF1E293B))
            )
        }
    }
}
