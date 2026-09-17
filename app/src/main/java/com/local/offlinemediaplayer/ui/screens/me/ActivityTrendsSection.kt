package com.local.offlinemediaplayer.ui.screens.me

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.offlinemediaplayer.domain.ActivityBucket
import com.local.offlinemediaplayer.domain.StatsRange
import com.local.offlinemediaplayer.ui.common.FormatUtils

/**
 * "ACTIVITY TRENDS" — the playtime bar chart on the Me tab, over the selected [StatsRange].
 *
 * The chart is deliberately ignorant of what a bar *is*. It draws whatever [ActivityBucket]s it is
 * handed — seven days, thirty days or twelve months — which is what let the range selector be added
 * without touching the Canvas drawing at all. Deciding what a bucket covers and what it is called
 * belongs to `ActivityChart`, where it can be tested without a renderer.
 *
 * [ActivityBarChart] does the Canvas drawing and is the one genuinely long composable here; its
 * pre-existing detekt baseline entries moved with it out of `MeScreen.kt`.
 */
@Composable
internal fun ActivityTrendsSection(
    buckets: List<ActivityBucket>,
    selectedRange: StatsRange,
    onRangeSelected: (StatsRange) -> Unit,
    primaryColor: Color,
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                Icons.Outlined.Speed,
                null,
                tint = primaryColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "ACTIVITY TRENDS",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        RangeSelector(
            selected = selectedRange,
            onSelect = onRangeSelected,
            primaryColor = primaryColor,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Chart Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                // Bar Chart
                ActivityBarChart(
                    data = buckets,
                    primaryColor = primaryColor,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp), // Adjusted height for tooltips and grid
                )

                Spacer(modifier = Modifier.height(12.dp))

                // One weighted cell per bar, whether or not it carries a label. Rendering only the
                // labelled ones would space them evenly among themselves and detach every label
                // from the bar it names — the failure gets worse the more bars there are, which is
                // exactly when labels are sparse.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    buckets.forEach { bucket ->
                        Text(
                            text = bucket.label,
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false,
                            fontWeight = if (bucket.isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color =
                                if (bucket.isCurrent) {
                                    primaryColor
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The Week / Month / Year switch.
 *
 * Three buttons rather than a dropdown: with this few options the cost of showing them all is one
 * row, and it makes the fact that other ranges exist discoverable — which was the actual problem,
 * since the chart previously gave no hint that anything but this week was available.
 */
@Composable
private fun RangeSelector(
    selected: StatsRange,
    onSelect: (StatsRange) -> Unit,
    primaryColor: Color,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatsRange.entries.forEach { range ->
            val isSelected = range == selected
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) {
                                primaryColor.copy(alpha = 0.15f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ).selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onSelect(range) },
                        ).padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = range.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color =
                        if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActivityBarChart(
    data: List<ActivityBucket>,
    primaryColor: Color,
    modifier: Modifier = Modifier,
) {
    val maxMinutes = data.maxOfOrNull { it.playtimeMinutes } ?: 0
    val emptyBarColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val gridLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tooltipBgColor = MaterialTheme.colorScheme.surfaceVariant
    val tooltipTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Animation for bar heights
    var animationPlayed by remember { mutableStateOf(false) }
    val animationProgress by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec =
            tween(
                durationMillis = 1000,
                easing = FastOutSlowInEasing,
            ),
        label = "barAnimation",
    )

    LaunchedEffect(data) {
        animationPlayed = true
    }

    // Interactive tooltips
    var selectedBarIndex by remember { mutableStateOf<Int?>(null) }

    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier =
            modifier.pointerInput(data) {
                detectTapGestures { offset ->
                    val barCount = data.size
                    if (barCount == 0) return@detectTapGestures

                    val totalSpacing = size.width * 0.4f
                    val barWidth = (size.width - totalSpacing) / barCount
                    val spacing = totalSpacing / (barCount + 1)

                    // Find which bar was clicked
                    for (i in 0 until barCount) {
                        val xStart = spacing + i * (barWidth + spacing)
                        val xEnd = xStart + barWidth

                        // Allow a bit of leeway in tap target
                        if (offset.x >= xStart - spacing / 2 && offset.x <= xEnd + spacing / 2) {
                            selectedBarIndex = if (selectedBarIndex == i) null else i
                            return@detectTapGestures
                        }
                    }
                    selectedBarIndex = null // Clicked outside any bar
                }
            },
    ) {
        val barCount = data.size
        if (barCount == 0) return@Canvas

        // Reserving space at top for tooltip
        val topPadding = 40.dp.toPx()
        val bottomPadding = 10.dp.toPx()
        val chartHeight = size.height - topPadding - bottomPadding

        val totalSpacing = size.width * 0.4f
        val barWidth = (size.width - totalSpacing) / barCount
        val spacing = totalSpacing / (barCount + 1)
        val cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
        val minBarHeight = 4.dp.toPx()

        // Draw Grid Lines (Y-Axis references)
        val gridLines = 3 // 0, max/2, max
        for (i in 0 until gridLines) {
            val y =
                topPadding + chartHeight - (chartHeight * (i.toFloat() / (gridLines - 1).coerceAtLeast(1)))
            drawLine(
                color = gridLineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f),
            )

            // Draw grid labels if there's data
            if (maxMinutes > 0 && i > 0) {
                val labelValue = (maxMinutes * (i.toFloat() / (gridLines - 1))).toInt()
                val labelText = FormatUtils.formatMinutesToHours(labelValue)
                val textLayoutResult =
                    textMeasurer.measure(
                        text = AnnotatedString(labelText),
                        style =
                            TextStyle(
                                color = textColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                    )
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft =
                        Offset(
                            0f, // Left aligned
                            y - textLayoutResult.size.height - 4.dp.toPx(),
                        ),
                )
            }
        }

        // Draw Bars
        data.forEachIndexed { index, bucket ->
            val x = spacing + index * (barWidth + spacing)

            val targetHeight =
                if (maxMinutes > 0) {
                    val ratio = bucket.playtimeMinutes.toFloat() / maxMinutes
                    (ratio * (chartHeight - minBarHeight)) + minBarHeight
                } else {
                    minBarHeight
                }

            val animatedHeight = targetHeight * animationProgress
            val yOffset = topPadding + chartHeight - animatedHeight

            // Determine colors based on selection
            val isSelected = selectedBarIndex == index
            val hasSelection = selectedBarIndex != null

            // Dim unselected bars if something is selected
            val alphaMultiplier = if (hasSelection && !isSelected) 0.3f else 1.0f

            val brush =
                if (bucket.playtimeMinutes > 0) {
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                primaryColor.copy(alpha = alphaMultiplier),
                                primaryColor.copy(alpha = alphaMultiplier * 0.5f),
                            ),
                        startY = yOffset,
                        endY = yOffset + animatedHeight,
                    )
                } else {
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                emptyBarColor.copy(alpha = alphaMultiplier),
                                emptyBarColor.copy(alpha = alphaMultiplier),
                            ),
                    )
                }

            // Draw the bar
            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, yOffset),
                size = Size(barWidth, animatedHeight),
                cornerRadius = cornerRadius,
            )

            // Draw Tooltip for selected bar OR always show value if it's today
            if (isSelected ||
                (bucket.isCurrent && !hasSelection && bucket.playtimeMinutes > 0 && animationProgress > 0.9f)
            ) {
                val text = FormatUtils.formatMinutesToHours(bucket.playtimeMinutes)
                val textLayoutResult =
                    textMeasurer.measure(
                        text = AnnotatedString(text),
                        style =
                            TextStyle(
                                color = if (isSelected) primaryColor else tooltipTextColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                    )

                val textWidth = textLayoutResult.size.width
                val textHeight = textLayoutResult.size.height

                // Center tooltip above bar
                val tooltipX = x + (barWidth / 2) - (textWidth / 2)
                val tooltipY = yOffset - textHeight - 8.dp.toPx()

                // Draw background pill for selected
                if (isSelected) {
                    val paddingParam = 6.dp.toPx()
                    drawRoundRect(
                        color = tooltipBgColor,
                        topLeft = Offset(tooltipX - paddingParam, tooltipY - paddingParam),
                        size =
                            Size(
                                textWidth.toFloat() + (paddingParam * 2),
                                textHeight.toFloat() + (paddingParam * 2),
                            ),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                    )
                }

                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(tooltipX, tooltipY),
                )
            }
        }
    }
}
