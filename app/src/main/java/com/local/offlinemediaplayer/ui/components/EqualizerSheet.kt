package com.local.offlinemediaplayer.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.offlinemediaplayer.R
import com.local.offlinemediaplayer.viewmodel.PlaybackViewModel
import kotlin.math.roundToInt

/**
 * Equalizer / audio-effects control sheet. Reads and mutates state through
 * [PlaybackViewModel.audioEffects]; it owns no audio logic of its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit,
) {
    val fx = viewModel.audioEffects
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Build effect handles lazily the first time the sheet is opened so band/preset metadata exists.
    LaunchedEffect(Unit) { fx.initializeIfNeeded() }

    val isAvailable by fx.isAvailable.collectAsStateWithLifecycle()
    val enabled by fx.enabled.collectAsStateWithLifecycle()
    val bands by fx.bands.collectAsStateWithLifecycle()
    val bandLevels by fx.bandLevels.collectAsStateWithLifecycle()
    val presetNames by fx.presetNames.collectAsStateWithLifecycle()
    val currentPreset by fx.currentPreset.collectAsStateWithLifecycle()
    val bassSupported by fx.bassBoostSupported.collectAsStateWithLifecycle()
    val bassStrength by fx.bassBoostStrength.collectAsStateWithLifecycle()
    val virtSupported by fx.virtualizerSupported.collectAsStateWithLifecycle()
    val virtStrength by fx.virtualizerStrength.collectAsStateWithLifecycle()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
        ) {
            // Header + master switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.eq_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = { fx.setEnabled(it) },
                    enabled = isAvailable,
                )
            }

            Spacer(Modifier.height(8.dp))

            if (!isAvailable) {
                Text(
                    text = stringResource(R.string.eq_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            val controlsAlpha = if (enabled) 1f else 0.4f

            // Presets
            if (presetNames.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.eq_presets),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .alpha(controlsAlpha),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presetNames.forEachIndexed { index, name ->
                        FilterChip(
                            selected = currentPreset == index,
                            onClick = { if (enabled) fx.applyPreset(index) },
                            label = { Text(name) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
            }

            // Band sliders
            bands.forEachIndexed { index, band ->
                val levelMb = bandLevels.getOrNull(index) ?: 0
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .alpha(controlsAlpha),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatFrequency(band.centerFreqHz),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(56.dp),
                    )
                    Slider(
                        value = levelMb.toFloat(),
                        onValueChange = { fx.setBandLevel(index, it.roundToInt()) },
                        valueRange = band.minLevelMb.toFloat()..band.maxLevelMb.toFloat(),
                        enabled = enabled,
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                    )
                    Text(
                        text = formatGain(levelMb),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(48.dp),
                    )
                }
            }

            // Bass Boost / Virtualizer
            if (bassSupported || virtSupported) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
            }
            if (bassSupported) {
                EffectStrengthSlider(
                    label = stringResource(R.string.eq_bass_boost),
                    strength = bassStrength,
                    enabled = enabled,
                    onChange = { fx.setBassBoost(it) },
                )
            }
            if (virtSupported) {
                Spacer(Modifier.height(8.dp))
                EffectStrengthSlider(
                    label = stringResource(R.string.eq_virtualizer),
                    strength = virtStrength,
                    enabled = enabled,
                    onChange = { fx.setVirtualizer(it) },
                )
            }
        }
    }
}

@Composable
private fun EffectStrengthSlider(
    label: String,
    strength: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    val alpha = if (enabled) 1f else 0.4f
    Column(modifier = Modifier.fillMaxWidth().alpha(alpha)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.eq_strength_percent, strength / 10),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = strength.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..1000f,
            enabled = enabled,
        )
    }
}

private fun formatFrequency(hz: Int): String =
    if (hz >= 1000) {
        val k = hz / 1000f
        if (k % 1f == 0f) "${k.toInt()}kHz" else "${(Math.round(k * 10) / 10f)}kHz"
    } else {
        "${hz}Hz"
    }

private fun formatGain(levelMb: Int): String {
    val db = levelMb / 100
    return if (db > 0) "+${db}dB" else "${db}dB"
}
