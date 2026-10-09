package com.local.offlinemediaplayer.ui.screens.me

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.offlinemediaplayer.model.MediaFile

/** The full-width "Shuffle All Audio" call to action on the Me tab. Moved unchanged. */
@Composable
internal fun ShuffleAllButton(
    audioList: List<MediaFile>,
    primaryColor: Color,
    onShuffle: (List<MediaFile>) -> Unit,
) {
    Button(
        onClick = { onShuffle(audioList) },
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                // A minimum, not a fixed height. At a 200% font setting the label needs more than
                // 56 dp and a fixed height crops it; a minimum keeps the button identical at the
                // default scale and lets it grow where it has to.
                .heightIn(min = 56.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = primaryColor,
            ),
        shape = RoundedCornerShape(16.dp),
        enabled = audioList.isNotEmpty(),
    ) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            "Shuffle All Audio",
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
    }
}
