package com.local.offlinemediaplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.local.offlinemediaplayer.model.MediaFile
import com.local.offlinemediaplayer.ui.common.FormatUtils
import com.local.offlinemediaplayer.ui.components.CollapsibleSearchBox
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme
import com.local.offlinemediaplayer.viewmodel.MainViewModel

@Composable
fun MeScreen(
        viewModel: MainViewModel,
        onPlayMedia: (MediaFile) -> Unit,
        onNavigateToAccessibilityGuide: () -> Unit,
        isSearchVisible: Boolean
) {
        val theme = LocalAppTheme.current
        val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()
        val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
        val audioList by viewModel.audioList.collectAsStateWithLifecycle()

        // Realtime Analytics
        val analytics by viewModel.realtimeAnalytics.collectAsStateWithLifecycle()

        // Continue Watching Data
        val continueWatchingList by viewModel.continueWatchingList.collectAsStateWithLifecycle()

        // Simple local search state for MeScreen
        var searchQuery by remember { mutableStateOf("") }

        val randomSongs =
                remember(audioList) {
                        if (audioList.isNotEmpty()) {
                                audioList.asSequence().shuffled().take(50).toList()
                        } else {
                                emptyList()
                        }
                }

        var isExpanded by remember { mutableStateOf(false) }

        // Filter displayed songs based on search query
        val filteredSongs =
                if (searchQuery.isNotEmpty()) {
                        randomSongs.filter {
                                it.title.contains(searchQuery, ignoreCase = true) ||
                                        it.artist?.contains(searchQuery, ignoreCase = true) == true
                        }
                } else {
                        randomSongs
                }

        val displayedSongs =
                if (isExpanded || searchQuery.isNotEmpty()) filteredSongs else filteredSongs.take(5)

        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = 120.dp) // Space for MiniPlayer
        ) {

                // 1. Theme Switcher (Now at the very top of content)
                Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        ThemeButton(
                                icon = Icons.Filled.Bolt,
                                color = Color(0xFFFF5500),
                                isActive = currentTheme.id == "orange",
                                onClick = { viewModel.updateTheme("orange") }
                        )
                        Spacer(modifier = Modifier.width(24.dp))
                        ThemeButton(
                                icon = Icons.Filled.Star,
                                color = Color(0xFF00E5FF),
                                isActive = currentTheme.id == "blue",
                                onClick = { viewModel.updateTheme("blue") }
                        )
                        Spacer(modifier = Modifier.width(24.dp))
                        ThemeButton(
                                icon = Icons.Filled.OpenWith,
                                color = Color(0xFF22C55E),
                                isActive = currentTheme.id == "green",
                                onClick = { viewModel.updateTheme("green") }
                        )
                }

                // 2. Collapsible Search Box
                CollapsibleSearchBox(
                        isVisible = isSearchVisible,
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholderText = "Search in suggested..."
                )

                Spacer(modifier = Modifier.height(12.dp))

                // --- CONTINUE WATCHING SHELF ---
                if (continueWatchingList.isNotEmpty()) {
                        Row(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Icon(
                                        Icons.Filled.History,
                                        null,
                                        tint = theme.primaryColor,
                                        modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                        text = "CONTINUE WATCHING",
                                        style =
                                                MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Bold
                                                ),
                                        color = MaterialTheme.colorScheme.onBackground
                                )
                        }

                        LazyRow(
                                contentPadding = PaddingValues(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth().height(140.dp)
                        ) {
                                items(continueWatchingList) { (video, history) ->
                                        val progress =
                                                if (history.duration > 0)
                                                        history.position.toFloat() /
                                                                history.duration.toFloat()
                                                else 0f

                                        Card(
                                                modifier =
                                                        Modifier.width(180.dp)
                                                                .height(130.dp)
                                                                .clickable { onPlayMedia(video) },
                                                shape = RoundedCornerShape(12.dp),
                                                colors =
                                                        CardDefaults.cardColors(
                                                                containerColor =
                                                                        MaterialTheme.colorScheme
                                                                                .surface
                                                        )
                                        ) {
                                                Box(modifier = Modifier.fillMaxSize()) {
                                                        AsyncImage(
                                                                model = video.uri,
                                                                contentDescription = null,
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentScale = ContentScale.Crop
                                                        )
                                                        Box(
                                                                modifier =
                                                                        Modifier.fillMaxSize()
                                                                                .background(
                                                                                        Color.Black
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.3f
                                                                                                )
                                                                                )
                                                        )

                                                        // Play Icon Center
                                                        Icon(
                                                                imageVector =
                                                                        Icons.Default.PlayArrow,
                                                                contentDescription = null,
                                                                tint = Color.White,
                                                                modifier =
                                                                        Modifier.align(
                                                                                        Alignment
                                                                                                .Center
                                                                                )
                                                                                .size(32.dp)
                                                        )

                                                        // Progress Bar Bottom
                                                        LinearProgressIndicator(
                                                                progress = { progress },
                                                                modifier =
                                                                        Modifier.align(
                                                                                        Alignment
                                                                                                .BottomCenter
                                                                                )
                                                                                .fillMaxWidth()
                                                                                .height(4.dp),
                                                                color = theme.primaryColor,
                                                                trackColor =
                                                                        Color.White.copy(
                                                                                alpha = 0.3f
                                                                        )
                                                        )

                                                        // Title Overlay
                                                        Box(
                                                                modifier =
                                                                        Modifier.align(
                                                                                        Alignment
                                                                                                .BottomStart
                                                                                )
                                                                                .fillMaxWidth()
                                                                                .background(
                                                                                        Brush.verticalGradient(
                                                                                                listOf(
                                                                                                        Color.Transparent,
                                                                                                        Color.Black
                                                                                                )
                                                                                        )
                                                                                )
                                                                                .padding(
                                                                                        bottom =
                                                                                                8.dp,
                                                                                        start =
                                                                                                8.dp,
                                                                                        top = 20.dp,
                                                                                        end = 8.dp
                                                                                )
                                                        ) {
                                                                Text(
                                                                        text = video.title,
                                                                        color = Color.White,
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .labelSmall,
                                                                        maxLines = 1,
                                                                        overflow =
                                                                                TextOverflow
                                                                                        .Ellipsis
                                                                )
                                                        }
                                                }
                                        }
                                }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                }

                // 3. Analytics Dashboard (Real-time)
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                        Icons.Outlined.Speed,
                                        null,
                                        tint = theme.primaryColor,
                                        modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                        text = "LISTENING ACTIVITY",
                                        color = MaterialTheme.colorScheme.onBackground,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Row 1: Today & Streak
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                AnalyticsCard(
                                        modifier = Modifier.weight(1f),
                                        icon = Icons.Outlined.Today,
                                        iconColor = theme.primaryColor,
                                        label = "Today",
                                        value =
                                                FormatUtils.formatMinutesToHours(
                                                        analytics.todayPlaytimeMinutes
                                                ),
                                        subtext = "Active Listening"
                                )

                                AnalyticsCard(
                                        modifier = Modifier.weight(1f),
                                        icon = Icons.Outlined.LocalFireDepartment,
                                        iconColor = Color(0xFFFF5500), // Fire Orange
                                        label = "Streak",
                                        value =
                                                "${analytics.streakDays} Day${if(analytics.streakDays != 1) "s" else ""}",
                                        subtext = "Consecutive"
                                )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Row 2: Last Week & Average
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                AnalyticsCard(
                                        modifier = Modifier.weight(1f),
                                        icon = Icons.Outlined.DateRange,
                                        iconColor = Color(0xFF22C55E), // Green
                                        label = "Last 7 Days",
                                        value =
                                                FormatUtils.formatMinutesToHours(
                                                        analytics.weekPlaytimeMinutes
                                                ),
                                        subtext = "Total Playtime"
                                )

                                AnalyticsCard(
                                        modifier = Modifier.weight(1f),
                                        icon = Icons.AutoMirrored.Outlined.ShowChart,
                                        iconColor = Color(0xFF00E5FF), // Blue
                                        label = "Daily Avg",
                                        value =
                                                FormatUtils.formatMinutesToHours(
                                                        analytics.avgDailyMinutes
                                                ),
                                        subtext = "Last 30 Days"
                                )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Favorites Section
                        Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface
                                        ),
                                shape = RoundedCornerShape(16.dp),
                        ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                        val currentTrack by
                                                viewModel.currentTrack.collectAsStateWithLifecycle()
                                        val lastPlayedAudio by
                                                viewModel.lastPlayedAudio
                                                        .collectAsStateWithLifecycle()

                                        // Logic: Show Current Track if active AND AUDIO, else show
                                        // Last Played from DB
                                        // The user explicitly wants "Last Played Song Alone", so we
                                        // must filter out any
                                        // active video.
                                        val activeAudio = currentTrack?.takeIf { !it.isVideo }
                                        val displayTrack = activeAudio ?: lastPlayedAudio

                                        if (displayTrack != null) {
                                                Row(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .clip(
                                                                                RoundedCornerShape(
                                                                                        12.dp
                                                                                )
                                                                        )
                                                                        .clickable {
                                                                                // If clicking "Last
                                                                                // Played" (not
                                                                                // active), we
                                                                                // need to play it.
                                                                                // If clicking
                                                                                // "Current", we
                                                                                // just open player
                                                                                // (or toggle play).
                                                                                // Simplest: just
                                                                                // call
                                                                                // onPlayMedia(track) which
                                                                                // handles both.
//                                                                                displayTrack.let {
//                                                                                        onPlayMedia(
//                                                                                                it
//                                                                                        )
//                                                                                }
                                                                                onPlayMedia(
                                                                                        displayTrack
                                                                                )
                                                                        }
                                                                        .padding(8.dp),
                                                        verticalAlignment =
                                                                Alignment.CenterVertically
                                                ) {
                                                        Box(
                                                                modifier =
                                                                        Modifier.size(40.dp)
                                                                                .background(
                                                                                        theme.primaryColor
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.1f
                                                                                                ),
                                                                                        RoundedCornerShape(
                                                                                                8.dp
                                                                                        )
                                                                                ),
                                                                contentAlignment = Alignment.Center
                                                        ) {
                                                                Icon(
                                                                        Icons.Default.MusicNote,
                                                                        null,
                                                                        tint = theme.primaryColor,
                                                                        modifier =
                                                                                Modifier.size(20.dp)
                                                                )
                                                        }
                                                        Spacer(modifier = Modifier.width(16.dp))
                                                        Column {
                                                                Text(
                                                                        if (currentTrack != null)
                                                                                "Current Playing"
                                                                        else "Last Played",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .labelMedium,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurfaceVariant
                                                                )
                                                                Text(
                                                                        text = displayTrack.title,
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .titleMedium,
                                                                        fontWeight =
                                                                                FontWeight.Bold,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface,
                                                                        maxLines = 1,
                                                                        overflow =
                                                                                TextOverflow
                                                                                        .Ellipsis
                                                                )
                                                                Text(
                                                                        text = displayTrack.artist
                                                                                        ?: "Unknown Artist",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .bodySmall,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurfaceVariant
                                                                )
                                                        }
                                                }

                                                HorizontalDivider(
                                                        modifier =
                                                                Modifier.padding(vertical = 12.dp),
                                                        color =
                                                                MaterialTheme.colorScheme.onSurface
                                                                        .copy(alpha = 0.1f)
                                                )
                                        }

                                        val currentFav = analytics.currentFavorite
                                        // Current Favorite (Last 30 days)
                                        Row(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .clickable(
                                                                        enabled = currentFav != null
                                                                ) {
                                                                        currentFav?.let {
                                                                                onPlayMedia(it)
                                                                        }
                                                                }
                                                                .padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Box(
                                                        modifier =
                                                                Modifier.size(40.dp)
                                                                        .background(
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface
                                                                                        .copy(
                                                                                                alpha =
                                                                                                        0.05f
                                                                                        ),
                                                                                RoundedCornerShape(
                                                                                        8.dp
                                                                                )
                                                                        ),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        Icon(
                                                                Icons.Outlined.FavoriteBorder,
                                                                null,
                                                                tint = theme.primaryColor,
                                                                modifier = Modifier.size(20.dp)
                                                        )
                                                }
                                                Spacer(modifier = Modifier.width(16.dp))
                                                Column {
                                                        Text(
                                                                "Current Obsession",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelMedium,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                        )
                                                        Text(
                                                                text = currentFav?.title
                                                                                ?: "Keep Listening...",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .titleMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurface,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                        )
                                                        if (currentFav != null) {
                                                                Text(
                                                                        text = currentFav.artist
                                                                                        ?: "Unknown",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .bodySmall,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurfaceVariant
                                                                )
                                                        }
                                                }
                                        }

                                        HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 12.dp),
                                                color =
                                                        MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.1f
                                                        )
                                        )

                                        val allTimeFav = analytics.allTimeFavorite
                                        // All Time Favorite
                                        Row(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .clickable(
                                                                        enabled = allTimeFav != null
                                                                ) {
                                                                        allTimeFav?.let {
                                                                                onPlayMedia(it)
                                                                        }
                                                                }
                                                                .padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Box(
                                                        modifier =
                                                                Modifier.size(40.dp)
                                                                        .background(
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface
                                                                                        .copy(
                                                                                                alpha =
                                                                                                        0.05f
                                                                                        ),
                                                                                RoundedCornerShape(
                                                                                        8.dp
                                                                                )
                                                                        ),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        Icon(
                                                                Icons.Filled.Star,
                                                                null,
                                                                tint = Color(0xFFFFD700),
                                                                modifier = Modifier.size(20.dp)
                                                        )
                                                }
                                                Spacer(modifier = Modifier.width(16.dp))
                                                Column {
                                                        Text(
                                                                "All Time #1",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelMedium,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                        )
                                                        Text(
                                                                text = allTimeFav?.title
                                                                                ?: "Keep Listening...",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .titleMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurface,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                        )
                                                        if (allTimeFav != null) {
                                                                Text(
                                                                        text = allTimeFav.artist
                                                                                        ?: "Unknown",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .bodySmall,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurfaceVariant
                                                                )
                                                        }
                                                }
                                        }
                                }
                        }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 4. Suggested Section Header
                if (randomSongs.isNotEmpty()) {
                        Row(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Icon(
                                        Icons.Default.Star,
                                        null,
                                        tint = theme.primaryColor,
                                        modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                        text = theme.curatedTitle,
                                        style =
                                                MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Bold
                                                ),
                                        color = MaterialTheme.colorScheme.onBackground
                                )
                        }

                        // 5. Active Stream Card
                        if (searchQuery.isEmpty()) {
                                Card(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .aspectRatio(16f / 9f)
                                                        .padding(horizontal = 16.dp),
                                        shape = RoundedCornerShape(24.dp),
                                        colors =
                                                CardDefaults.cardColors(
                                                        containerColor =
                                                                MaterialTheme.colorScheme.surface
                                                )
                                ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                                val featuredSong = randomSongs.firstOrNull()
                                                if (featuredSong?.albumArtUri != null) {
                                                        AsyncImage(
                                                                model = featuredSong.albumArtUri,
                                                                contentDescription = null,
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentScale = ContentScale.Crop
                                                        )
                                                } else {
                                                        Box(
                                                                modifier =
                                                                        Modifier.fillMaxSize()
                                                                                .background(
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .surface
                                                                                )
                                                        )
                                                }

                                                Box(
                                                        modifier =
                                                                Modifier.fillMaxSize()
                                                                        .background(
                                                                                Brush.verticalGradient(
                                                                                        colors =
                                                                                                listOf(
                                                                                                        Color.Transparent,
                                                                                                        Color.Black
                                                                                                                .copy(
                                                                                                                        alpha =
                                                                                                                                0.7f
                                                                                                                )
                                                                                                )
                                                                                )
                                                                        )
                                                )

                                                Column(
                                                        modifier =
                                                                Modifier.align(
                                                                                Alignment
                                                                                        .BottomStart
                                                                        )
                                                                        .padding(24.dp)
                                                ) {
                                                        Row(
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically
                                                        ) {
                                                                Box(
                                                                        modifier =
                                                                                Modifier.size(8.dp)
                                                                                        .background(
                                                                                                theme.primaryColor,
                                                                                                CircleShape
                                                                                        )
                                                                )
                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.width(8.dp)
                                                                )
                                                                Text(
                                                                        text = "SUGGESTED",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .labelSmall
                                                                                        .copy(
                                                                                                fontWeight =
                                                                                                        FontWeight
                                                                                                                .Bold,
                                                                                                letterSpacing =
                                                                                                        2.sp,
                                                                                                color =
                                                                                                        theme.primaryColor
                                                                                        )
                                                                )
                                                        }
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                                text = featuredSong?.title ?: "Mix",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .headlineSmall.copy(
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold
                                                                        ),
                                                                color = Color.White,
                                                                maxLines = 1
                                                        )
                                                }
                                        }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // 6. Play Actions
                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                        Button(
                                                onClick = { viewModel.playAll(false) },
                                                modifier = Modifier.weight(1f).height(48.dp),
                                                colors =
                                                        ButtonDefaults.buttonColors(
                                                                containerColor = theme.primaryColor
                                                        ),
                                                shape = RoundedCornerShape(50)
                                        ) {
                                                Icon(
                                                        Icons.Default.PlayArrow,
                                                        null,
                                                        tint = Color.Black
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                        "PLAY ALL",
                                                        color = Color.Black,
                                                        fontWeight = FontWeight.Bold
                                                )
                                        }

                                        Button(
                                                onClick = { viewModel.playAll(true) },
                                                modifier =
                                                        Modifier.weight(1f)
                                                                .height(48.dp)
                                                                .border(
                                                                        1.dp,
                                                                        MaterialTheme.colorScheme
                                                                                .onBackground.copy(
                                                                                alpha = 0.2f
                                                                        ),
                                                                        RoundedCornerShape(50)
                                                                ),
                                                colors =
                                                        ButtonDefaults.buttonColors(
                                                                containerColor =
                                                                        MaterialTheme.colorScheme
                                                                                .onBackground.copy(
                                                                                alpha = 0.05f
                                                                        )
                                                        ),
                                                shape = RoundedCornerShape(50)
                                        ) {
                                                Icon(
                                                        Icons.Default.Shuffle,
                                                        null,
                                                        tint = theme.primaryColor
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                        "SHUFFLE",
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onBackground,
                                                        fontWeight = FontWeight.Bold
                                                )
                                        }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // Divider with Gradient
                                Box(
                                        modifier =
                                                Modifier.padding(horizontal = 24.dp)
                                                        .width(120.dp)
                                                        .height(3.dp)
                                                        .background(
                                                                Brush.horizontalGradient(
                                                                        colors =
                                                                                listOf(
                                                                                        theme.primaryColor,
                                                                                        Color.Transparent
                                                                                )
                                                                ),
                                                                RoundedCornerShape(2.dp)
                                                        )
                                )

                                Spacer(modifier = Modifier.height(24.dp))
                        }

                        // 7. Collapsible List
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                if (displayedSongs.isEmpty()) {
                                        Text(
                                                "No matches found.",
                                                color = Color.Gray,
                                                modifier = Modifier.padding(16.dp)
                                        )
                                } else {
                                        displayedSongs.forEachIndexed { index, song ->
                                                Row(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .clip(
                                                                                RoundedCornerShape(
                                                                                        12.dp
                                                                                )
                                                                        )
                                                                        .clickable {
                                                                                onPlayMedia(song)
                                                                        }
                                                                        .padding(
                                                                                vertical = 8.dp,
                                                                                horizontal = 8.dp
                                                                        ),
                                                        verticalAlignment =
                                                                Alignment.CenterVertically
                                                ) {
                                                        Box(
                                                                modifier =
                                                                        Modifier.size(48.dp)
                                                                                .background(
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .surfaceContainer,
                                                                                        RoundedCornerShape(
                                                                                                8.dp
                                                                                        )
                                                                                ),
                                                                contentAlignment = Alignment.Center
                                                        ) {
                                                                if (song.albumArtUri != null) {
                                                                        AsyncImage(
                                                                                model =
                                                                                        song.albumArtUri,
                                                                                contentDescription =
                                                                                        null,
                                                                                modifier =
                                                                                        Modifier.fillMaxSize()
                                                                                                .clip(
                                                                                                        RoundedCornerShape(
                                                                                                                8.dp
                                                                                                        )
                                                                                                ),
                                                                                contentScale =
                                                                                        ContentScale
                                                                                                .Crop
                                                                        )
                                                                } else {
                                                                        Text(
                                                                                text =
                                                                                        "${index + 1}",
                                                                                color =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .onSurfaceVariant,
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .labelMedium,
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold
                                                                        )
                                                                }
                                                        }

                                                        Spacer(modifier = Modifier.width(16.dp))

                                                        Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                        text = song.title,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onBackground,
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .bodyMedium,
                                                                        fontWeight =
                                                                                FontWeight.Bold,
                                                                        maxLines = 1
                                                                )
                                                                Text(
                                                                        text = song.artist
                                                                                        ?: "Unknown",
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurfaceVariant,
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .labelSmall
                                                                )
                                                        }

                                                        Box(
                                                                modifier =
                                                                        Modifier.size(32.dp)
                                                                                .background(
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .onBackground
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.1f
                                                                                                ),
                                                                                        CircleShape
                                                                                ),
                                                                contentAlignment = Alignment.Center
                                                        ) {
                                                                Icon(
                                                                        Icons.Default.PlayArrow,
                                                                        null,
                                                                        tint =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onBackground,
                                                                        modifier =
                                                                                Modifier.size(16.dp)
                                                                )
                                                        }
                                                }
                                        }
                                }

                                if (!isExpanded && searchQuery.isEmpty() && randomSongs.size > 5) {
                                        TextButton(
                                                onClick = { isExpanded = true },
                                                modifier =
                                                        Modifier.fillMaxWidth().padding(top = 8.dp)
                                        ) {
                                                Text(
                                                        "VIEW ALL TRACKS",
                                                        color = theme.primaryColor,
                                                        style =
                                                                MaterialTheme.typography
                                                                        .labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = 2.sp
                                                )
                                        }
                                }
                        }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 8. Dark Mode Switch
                Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                ),
                        shape = RoundedCornerShape(16.dp),
                ) {
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                                imageVector =
                                                        if (isDarkTheme) Icons.Outlined.DarkMode
                                                        else Icons.Outlined.LightMode,
                                                contentDescription = null,
                                                tint = theme.primaryColor
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                                text =
                                                        if (isDarkTheme) "Dark Mode"
                                                        else "Light Mode",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                        )
                                }

                                Switch(
                                        checked = isDarkTheme,
                                        onCheckedChange = { viewModel.toggleThemeMode() },
                                        colors =
                                                SwitchDefaults.colors(
                                                        checkedThumbColor = theme.primaryColor,
                                                        checkedTrackColor =
                                                                theme.primaryColor.copy(
                                                                        alpha = 0.3f
                                                                ),
                                                        uncheckedThumbColor = Color.Gray,
                                                        uncheckedTrackColor = Color.Transparent
                                                )
                                )
                        }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 9. Accessibility Guide Button
                Card(
                        modifier =
                                Modifier.fillMaxWidth().padding(horizontal = 24.dp).clickable {
                                        onNavigateToAccessibilityGuide()
                                },
                        colors =
                                CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                ),
                        shape = RoundedCornerShape(16.dp),
                ) {
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                                imageVector = Icons.Outlined.Accessibility,
                                                contentDescription = null,
                                                tint = theme.primaryColor
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                                Text(
                                                        text = "Accessibility Guide",
                                                        style =
                                                                MaterialTheme.typography
                                                                        .titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                        text = "Learn about app features",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )
                                        }
                                }

                                Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }
                }
        }
}

@Composable
fun ThemeButton(icon: ImageVector, color: Color, isActive: Boolean, onClick: () -> Unit) {
        Box(
                modifier =
                        Modifier.size(48.dp)
                                .shadow(
                                        elevation = if (isActive) 16.dp else 0.dp,
                                        spotColor = if (isActive) color else Color.Transparent,
                                        shape = CircleShape
                                )
                                .background(
                                        if (isActive) color else MaterialTheme.colorScheme.surface,
                                        CircleShape
                                )
                                .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
        ) {
                Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint =
                                if (isActive) Color.White
                                else MaterialTheme.colorScheme.onSurfaceVariant
                )
        }
}

@Composable
fun AnalyticsCard(
        modifier: Modifier = Modifier,
        icon: ImageVector,
        iconColor: Color,
        label: String,
        value: String,
        subtext: String
) {
        Card(
                modifier = modifier,
                colors =
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = null
        ) {
                Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                                Box(
                                        modifier =
                                                Modifier.size(32.dp)
                                                        .background(
                                                                MaterialTheme.colorScheme.onSurface
                                                                        .copy(alpha = 0.05f),
                                                                RoundedCornerShape(8.dp)
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Icon(
                                                icon,
                                                null,
                                                tint = iconColor,
                                                modifier = Modifier.size(18.dp)
                                        )
                                }
                                Text(
                                        text = label.uppercase(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                                text = value,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                        )
                        Text(
                                text = subtext,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                        )
                }
        }
}
