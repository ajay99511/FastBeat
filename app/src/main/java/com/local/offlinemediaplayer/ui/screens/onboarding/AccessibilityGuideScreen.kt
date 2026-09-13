package com.local.offlinemediaplayer.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PictureInPicture
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.offlinemediaplayer.R
import com.local.offlinemediaplayer.ui.theme.LocalAppTheme

/**
 * User-facing walkthrough of the app, reachable from the Me tab.
 *
 * This screen has two jobs, and the second one constrains how it is built. It documents what
 * FastBeat can do — and it is the screen a disabled user is most likely to open first, so it has to
 * be the most accessible screen in the app rather than merely an average one. Three things follow
 * from that, and should survive any future edit:
 *
 *  1. **Every string comes from `strings.xml`.** Hardcoded text cannot be translated, and a screen
 *     reader feeding English into a non-English TTS engine produces gibberish.
 *  2. **Section titles are marked with `heading()`**, so screen-reader users can jump between
 *     sections instead of swiping through every row in between.
 *  3. **Label/detail rows merge their semantics** into one announcement. Left alone, Compose
 *     announces "Swipe left or right" and "Seek backward or forward" as two unrelated stops, and
 *     the pairing — the only thing that makes the row mean anything — is lost.
 *
 * Icons here are decorative: each one sits beside text that already says the same thing, so they
 * carry `contentDescription = null` deliberately. Labelling them would double the length of every
 * announcement without adding information. See `docs/ENGINEERING_PLAYBOOK.md` §7.
 *
 * When a feature gains a gesture or any non-obvious interaction, update this screen in the same PR.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilityGuideScreen(onBack: () -> Unit) {
    val theme = LocalAppTheme.current
    val accent = theme.primaryColor

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.guide_title),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.guide_back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.guide_welcome_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.guide_welcome_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Deliberately first: a user who opened this screen because they need assistive
            // technology should not have to scroll past four feature cards to reach it.
            DetailCard(
                icon = Icons.Outlined.Accessibility,
                title = stringResource(R.string.guide_a11y_section_title),
                accentColor = accent,
            ) {
                DetailRow(
                    label = stringResource(R.string.guide_a11y_labels_label),
                    detail = stringResource(R.string.guide_a11y_labels_detail),
                )
                DetailRow(
                    label = stringResource(R.string.guide_a11y_headings_label),
                    detail = stringResource(R.string.guide_a11y_headings_detail),
                )
                DetailRow(
                    label = stringResource(R.string.guide_a11y_text_size_label),
                    detail = stringResource(R.string.guide_a11y_text_size_detail),
                )
                DetailRow(
                    label = stringResource(R.string.guide_a11y_theme_label),
                    detail = stringResource(R.string.guide_a11y_theme_detail),
                )
                DetailRow(
                    label = stringResource(R.string.guide_a11y_gestures_label),
                    detail = stringResource(R.string.guide_a11y_gestures_detail),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            GuideSection(
                icon = Icons.Filled.PlayArrow,
                title = stringResource(R.string.guide_videos_title),
                description = stringResource(R.string.guide_videos_body),
                accentColor = accent,
            )
            GuideSection(
                icon = Icons.Filled.MusicNote,
                title = stringResource(R.string.guide_music_title),
                description = stringResource(R.string.guide_music_body),
                accentColor = accent,
            )
            GuideSection(
                icon = Icons.Filled.Image,
                title = stringResource(R.string.guide_images_title),
                description = stringResource(R.string.guide_images_body),
                accentColor = accent,
            )
            GuideSection(
                icon = Icons.Filled.Analytics,
                title = stringResource(R.string.guide_me_title),
                description = stringResource(R.string.guide_me_body),
                accentColor = accent,
            )

            Spacer(modifier = Modifier.height(16.dp))

            DetailCard(
                icon = Icons.Outlined.PlayCircleOutline,
                title = stringResource(R.string.guide_now_playing_title),
                accentColor = accent,
                intro = stringResource(R.string.guide_now_playing_intro),
            ) {
                DetailRow(
                    label = stringResource(R.string.guide_now_playing_shuffle_label),
                    detail = stringResource(R.string.guide_now_playing_shuffle_detail),
                )
                DetailRow(
                    label = stringResource(R.string.guide_now_playing_favorite_label),
                    detail = stringResource(R.string.guide_now_playing_favorite_detail),
                )
                DetailRow(
                    label = stringResource(R.string.guide_now_playing_speed_label),
                    detail = stringResource(R.string.guide_now_playing_speed_detail),
                )
                DetailRow(
                    label = stringResource(R.string.guide_now_playing_eq_label),
                    detail = stringResource(R.string.guide_now_playing_eq_detail),
                )
                DetailRow(
                    label = stringResource(R.string.guide_now_playing_sleep_label),
                    detail = stringResource(R.string.guide_now_playing_sleep_detail),
                )
                DetailRow(
                    label = stringResource(R.string.guide_now_playing_playlist_label),
                    detail = stringResource(R.string.guide_now_playing_playlist_detail),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            DetailCard(
                icon = Icons.AutoMirrored.Outlined.QueueMusic,
                title = stringResource(R.string.guide_queue_title),
                accentColor = accent,
                intro = stringResource(R.string.guide_queue_intro),
            ) {
                DetailRow(
                    label = stringResource(R.string.guide_queue_reorder_label),
                    detail = stringResource(R.string.guide_queue_reorder_detail),
                )
                DetailRow(
                    label = stringResource(R.string.guide_queue_remove_label),
                    detail = stringResource(R.string.guide_queue_remove_detail),
                )
                DetailRow(
                    label = stringResource(R.string.guide_queue_save_label),
                    detail = stringResource(R.string.guide_queue_save_detail),
                )
                DetailRow(
                    label = stringResource(R.string.guide_queue_next_label),
                    detail = stringResource(R.string.guide_queue_next_detail),
                )
                DetailRow(
                    label = stringResource(R.string.guide_queue_persist_label),
                    detail = stringResource(R.string.guide_queue_persist_detail),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            DetailCard(
                icon = Icons.Outlined.TouchApp,
                title = stringResource(R.string.guide_gestures_title),
                accentColor = accent,
                intro = stringResource(R.string.guide_gestures_intro),
            ) {
                DetailRow(
                    label = stringResource(R.string.guide_gesture_brightness_label),
                    detail = stringResource(R.string.guide_gesture_brightness_action),
                )
                DetailRow(
                    label = stringResource(R.string.guide_gesture_volume_label),
                    detail = stringResource(R.string.guide_gesture_volume_action),
                )
                DetailRow(
                    label = stringResource(R.string.guide_gesture_seek_label),
                    detail = stringResource(R.string.guide_gesture_seek_action),
                )
                DetailRow(
                    label = stringResource(R.string.guide_gesture_double_left_label),
                    detail = stringResource(R.string.guide_gesture_double_left_action),
                )
                DetailRow(
                    label = stringResource(R.string.guide_gesture_double_right_label),
                    detail = stringResource(R.string.guide_gesture_double_right_action),
                )
                DetailRow(
                    label = stringResource(R.string.guide_gesture_tap_label),
                    detail = stringResource(R.string.guide_gesture_tap_action),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SimpleCard(
                icon = Icons.Outlined.Lock,
                title = stringResource(R.string.guide_gesture_lock_label),
                body = stringResource(R.string.guide_gesture_lock_action),
                accentColor = accent,
            )

            Spacer(modifier = Modifier.height(16.dp))

            SimpleCard(
                icon = Icons.Outlined.PictureInPicture,
                title = stringResource(R.string.guide_miniplayer_title),
                body = stringResource(R.string.guide_miniplayer_body),
                accentColor = accent,
            )

            Spacer(modifier = Modifier.height(16.dp))

            DetailCard(
                icon = Icons.Outlined.Search,
                title = stringResource(R.string.guide_library_title),
                accentColor = accent,
            ) {
                DetailRow(
                    label = stringResource(R.string.guide_library_search_label),
                    detail = stringResource(R.string.guide_library_search_detail),
                )
                DetailRow(
                    label = stringResource(R.string.guide_library_sort_label),
                    detail = stringResource(R.string.guide_library_sort_detail),
                )
                DetailRow(
                    label = stringResource(R.string.guide_library_refresh_label),
                    detail = stringResource(R.string.guide_library_refresh_detail),
                )
                DetailRow(
                    label = stringResource(R.string.guide_library_rename_label),
                    detail = stringResource(R.string.guide_library_rename_detail),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SimpleCard(
                icon = Icons.Outlined.AutoAwesome,
                title = stringResource(R.string.guide_smart_title),
                body = stringResource(R.string.guide_smart_body),
                accentColor = accent,
            )

            Spacer(modifier = Modifier.height(16.dp))

            SimpleCard(
                icon = Icons.Outlined.PrivacyTip,
                title = stringResource(R.string.guide_privacy_title),
                body = stringResource(R.string.guide_privacy_body),
                accentColor = accent,
            )

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

/** One of the four top-level areas of the app: an accent-tinted icon, a heading, and a paragraph. */
@Composable
private fun GuideSection(
    icon: ImageVector,
    title: String,
    description: String,
    accentColor: Color,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            AccentIcon(icon = icon, accentColor = accentColor)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A titled card holding [DetailRow]s, with an optional sentence of context above them. */
@Composable
private fun DetailCard(
    icon: ImageVector,
    title: String,
    accentColor: Color,
    intro: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            CardHeader(icon = icon, title = title, accentColor = accentColor)

            if (intro != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = intro,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

/** A titled card holding a single paragraph. */
@Composable
private fun SimpleCard(
    icon: ImageVector,
    title: String,
    body: String,
    accentColor: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            CardHeader(icon = icon, title = title, accentColor = accentColor)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CardHeader(
    icon: ImageVector,
    title: String,
    accentColor: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            // Decorative: the title beside it already carries the meaning.
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun AccentIcon(
    icon: ImageVector,
    accentColor: Color,
) {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .background(accentColor.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            // Decorative: the section title beside it already carries the meaning.
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * A label paired with what it does.
 *
 * The two halves are merged into a single semantics node with an explicit description, because the
 * pairing is the whole point: read separately, "Swipe left or right" and "Seek backward or forward"
 * are two unrelated announcements and the reader has to hold them together themselves.
 */
@Composable
private fun DetailRow(
    label: String,
    detail: String,
) {
    val spoken = stringResource(R.string.guide_row_description, label, detail)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
