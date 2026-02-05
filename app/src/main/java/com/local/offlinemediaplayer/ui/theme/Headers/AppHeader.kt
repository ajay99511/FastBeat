
package com.local.offlinemediaplayer.ui.theme.Headers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppHeader() {
    val neonRed = Color(0xFFFF1F48) // Vibrant Red for the brand

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0B0B0F)) // Deep dark background to match theme
            .statusBarsPadding()
            .padding(top = 20.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo Text Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "FAST",
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black,
                    fontSize = 32.sp,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
            )
            Text(
                text = "BEAT",
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black,
                    fontSize = 32.sp,
                    color = neonRed,
                    letterSpacing = 1.sp,
                    shadow = Shadow(
                        color = neonRed.copy(alpha = 0.5f),
                        blurRadius = 35f,
                        offset = Offset(0f, 0f)
                    )
                )
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Glowing Underline Gradient
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            neonRed,
                            Color.Transparent
                        )
                    )
                )
        )
    }
}
