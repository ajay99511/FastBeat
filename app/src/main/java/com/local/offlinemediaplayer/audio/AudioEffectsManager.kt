package com.local.offlinemediaplayer.audio

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Immutable description of a single equalizer band, used to render the UI sliders. */
data class EqBand(
    val centerFreqHz: Int,
    val minLevelMb: Int,
    val maxLevelMb: Int
)

/**
 * Owns the lifecycle of the system audio effects (Equalizer / Bass Boost / Virtualizer) attached to
 * the ExoPlayer audio session.
 *
 * App-scoped singleton so the effect chain survives UI recreation and configuration changes, and is
 * shared by two collaborators that live in the same process:
 *  - [com.local.offlinemediaplayer.service.PlaybackService] supplies the ExoPlayer audio session id
 *    via [onAudioSessionIdChanged].
 *  - [com.local.offlinemediaplayer.viewmodel.PlaybackViewModel] drives the UI via the exposed flows
 *    and the mutating actions.
 *
 * SAFETY CONTRACT: the effect chain stays fully bypassed (every AudioEffect has enabled = false)
 * unless the user explicitly turns the equalizer on. This guarantees that for anyone who never opens
 * the equalizer, playback is byte-for-byte identical to before this feature existed.
 */
// Virtualizer is marked deprecated (superseded by Spatializer) but remains the standard, universally
// supported way to apply a stereo-widening effect on minSdk 26+, so the deprecation is suppressed.
@Suppress("DEPRECATION")
@Singleton
class AudioEffectsManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private companion object {
        const val TAG = "AudioEffectsManager"
        const val PREFS = "audio_effects"
        const val KEY_ENABLED = "eq_enabled"
        const val KEY_PRESET = "eq_preset"
        const val KEY_BAND_PREFIX = "eq_band_"
        const val KEY_BASS = "bass_strength"
        const val KEY_VIRT = "virt_strength"
        const val PRESET_CUSTOM = -1
        // Effects layered on top of playback use the highest priority so our settings win.
        const val EFFECT_PRIORITY = 1
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // --- Live effect instances (all null until an audio session is attached) ---
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    private var sessionId: Int = 0
    /** Guards against attaching effects on a device/emulator that cannot create them. */
    private var effectsUsable = true

    // --- Exposed state ---
    private val _isAvailable = MutableStateFlow(false)
    /** True once we have confirmed the platform can create an Equalizer on this device. */
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _bands = MutableStateFlow<List<EqBand>>(emptyList())
    val bands: StateFlow<List<EqBand>> = _bands.asStateFlow()

    private val _bandLevels = MutableStateFlow<List<Int>>(emptyList())
    /** Current per-band gain in millibels, index-aligned with [bands]. */
    val bandLevels: StateFlow<List<Int>> = _bandLevels.asStateFlow()

    private val _presetNames = MutableStateFlow<List<String>>(emptyList())
    val presetNames: StateFlow<List<String>> = _presetNames.asStateFlow()

    private val _currentPreset = MutableStateFlow(prefs.getInt(KEY_PRESET, PRESET_CUSTOM))
    /** Index into [presetNames], or [PRESET_CUSTOM] when the user has hand-tuned bands. */
    val currentPreset: StateFlow<Int> = _currentPreset.asStateFlow()

    private val _bassBoostSupported = MutableStateFlow(false)
    val bassBoostSupported: StateFlow<Boolean> = _bassBoostSupported.asStateFlow()

    private val _bassBoostStrength = MutableStateFlow(prefs.getInt(KEY_BASS, 0))
    /** 0..1000 (Android's normalised strength scale). 0 == off. */
    val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()

    private val _virtualizerSupported = MutableStateFlow(false)
    val virtualizerSupported: StateFlow<Boolean> = _virtualizerSupported.asStateFlow()

    private val _virtualizerStrength = MutableStateFlow(prefs.getInt(KEY_VIRT, 0))
    val virtualizerStrength: StateFlow<Int> = _virtualizerStrength.asStateFlow()

    /**
     * Called by the playback service whenever ExoPlayer reports a (new) audio session id. Rebuilds
     * the effect chain against the new session and re-applies the persisted configuration. A value
     * of 0 (C.AUDIO_SESSION_ID_UNSET) tears the chain down.
     */
    @Synchronized
    fun onAudioSessionIdChanged(newSessionId: Int) {
        if (newSessionId == sessionId && equalizer != null) return
        sessionId = newSessionId
        releaseEffects()
        if (newSessionId != 0 && effectsUsable) {
            buildEffects(newSessionId)
        }
    }

    /**
     * Called from the UI layer when the equalizer screen is opened. If effects have not been built
     * yet (e.g. the session id has not arrived) this attempts a build so band/preset metadata is
     * available for rendering; it never changes what is audible.
     */
    @Synchronized
    fun initializeIfNeeded() {
        if (equalizer == null && sessionId != 0 && effectsUsable) {
            buildEffects(sessionId)
        }
    }

    // --- Mutations (each updates the live effect, the exposed flow and persisted prefs) ---

    @Synchronized
    fun setEnabled(value: Boolean) {
        _enabled.value = value
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        applyEnabledState()
    }

    @Synchronized
    fun setBandLevel(band: Int, levelMb: Int) {
        val eq = equalizer ?: return
        val info = _bands.value.getOrNull(band) ?: return
        val clamped = levelMb.coerceIn(info.minLevelMb, info.maxLevelMb)
        try {
            eq.setBandLevel(band.toShort(), clamped.toShort())
        } catch (e: Exception) {
            Log.w(TAG, "setBandLevel failed", e)
            return
        }
        _bandLevels.value = _bandLevels.value.toMutableList().also { it[band] = clamped }
        // A manual edit means the levels no longer correspond to a named preset.
        _currentPreset.value = PRESET_CUSTOM
        prefs.edit()
            .putInt("$KEY_BAND_PREFIX$band", clamped)
            .putInt(KEY_PRESET, PRESET_CUSTOM)
            .apply()
    }

    @Synchronized
    fun applyPreset(index: Int) {
        val eq = equalizer ?: return
        try {
            eq.usePreset(index.toShort())
        } catch (e: Exception) {
            Log.w(TAG, "usePreset failed", e)
            return
        }
        _currentPreset.value = index
        // Read the resulting levels back so the sliders reflect the preset and we can persist them.
        val levels = readBandLevels(eq)
        _bandLevels.value = levels
        prefs.edit().apply {
            putInt(KEY_PRESET, index)
            levels.forEachIndexed { i, lvl -> putInt("$KEY_BAND_PREFIX$i", lvl) }
            apply()
        }
    }

    @Synchronized
    fun setBassBoost(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        _bassBoostStrength.value = clamped
        prefs.edit().putInt(KEY_BASS, clamped).apply()
        bassBoost?.let { fx ->
            try {
                fx.setStrength(clamped.toShort())
                fx.enabled = _enabled.value && clamped > 0
            } catch (e: Exception) {
                Log.w(TAG, "setBassBoost failed", e)
            }
        }
    }

    @Synchronized
    fun setVirtualizer(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        _virtualizerStrength.value = clamped
        prefs.edit().putInt(KEY_VIRT, clamped).apply()
        virtualizer?.let { fx ->
            try {
                fx.setStrength(clamped.toShort())
                fx.enabled = _enabled.value && clamped > 0
            } catch (e: Exception) {
                Log.w(TAG, "setVirtualizer failed", e)
            }
        }
    }

    // --- Internals ---

    private fun buildEffects(session: Int) {
        // Equalizer is the anchor effect. If it cannot be created we treat the whole feature as
        // unavailable and never touch the audio path again.
        val eq = try {
            Equalizer(EFFECT_PRIORITY, session)
        } catch (e: Throwable) {
            Log.w(TAG, "Equalizer unavailable on this device", e)
            effectsUsable = false
            _isAvailable.value = false
            return
        }
        equalizer = eq
        _isAvailable.value = true

        // Cache immutable band metadata + preset names on first successful build.
        if (_bands.value.isEmpty()) {
            val range = eq.bandLevelRange // [min, max] in millibels
            val min = range[0].toInt()
            val max = range[1].toInt()
            _bands.value = (0 until eq.numberOfBands).map { b ->
                EqBand(
                    centerFreqHz = eq.getCenterFreq(b.toShort()) / 1000, // µHz -> Hz
                    minLevelMb = min,
                    maxLevelMb = max
                )
            }
            _presetNames.value = (0 until eq.numberOfPresets).map { p ->
                eq.getPresetName(p.toShort())
            }
        }

        // Restore persisted band levels (or the current preset) onto the fresh instance.
        restoreBandConfig(eq)
        _bandLevels.value = readBandLevels(eq)

        // Optional effects — absent on some hardware/emulators, so failures are non-fatal.
        bassBoost = tryCreate("BassBoost") { BassBoost(EFFECT_PRIORITY, session) }?.also {
            _bassBoostSupported.value = it.strengthSupported
            runCatching {
                if (it.strengthSupported) it.setStrength(_bassBoostStrength.value.toShort())
            }.onFailure { e -> Log.w(TAG, "Failed to apply persisted bass boost strength", e) }
        }
        virtualizer = tryCreate("Virtualizer") { Virtualizer(EFFECT_PRIORITY, session) }?.also {
            _virtualizerSupported.value = it.strengthSupported
            runCatching {
                if (it.strengthSupported) it.setStrength(_virtualizerStrength.value.toShort())
            }.onFailure { e -> Log.w(TAG, "Failed to apply persisted virtualizer strength", e) }
        }

        applyEnabledState()
    }

    private fun restoreBandConfig(eq: Equalizer) {
        val preset = _currentPreset.value
        if (preset != PRESET_CUSTOM && preset < eq.numberOfPresets) {
            runCatching { eq.usePreset(preset.toShort()) }
                .onFailure { Log.w(TAG, "Failed to restore preset $preset", it) }
            return
        }
        val range = eq.bandLevelRange
        // Aggregated deliberately: when the effect is in a bad state every band fails, and one log
        // line per band would bury the signal. Report once, with the first failure as the cause.
        var failedBands = 0
        var firstFailure: Throwable? = null
        for (b in 0 until eq.numberOfBands) {
            val stored = prefs.getInt("$KEY_BAND_PREFIX$b", 0)
                .coerceIn(range[0].toInt(), range[1].toInt())
            runCatching { eq.setBandLevel(b.toShort(), stored.toShort()) }
                .onFailure { e ->
                    failedBands++
                    if (firstFailure == null) firstFailure = e
                }
        }
        firstFailure?.let {
            Log.w(TAG, "Failed to restore $failedBands of ${eq.numberOfBands} band levels", it)
        }
    }

    private fun readBandLevels(eq: Equalizer): List<Int> {
        var failedBands = 0
        var firstFailure: Throwable? = null
        val levels = (0 until eq.numberOfBands).map { b ->
            runCatching { eq.getBandLevel(b.toShort()).toInt() }
                .onFailure { e ->
                    failedBands++
                    if (firstFailure == null) firstFailure = e
                }
                .getOrDefault(0)
        }
        firstFailure?.let {
            Log.w(TAG, "Failed to read $failedBands of ${eq.numberOfBands} band levels; " +
                "those bands report 0 mB", it)
        }
        return levels
    }

    /** Push the master on/off state to every effect. Bass/Virtualizer also require a non-zero strength. */
    private fun applyEnabledState() {
        val on = _enabled.value
        runCatching { equalizer?.enabled = on }
            .onFailure { Log.w(TAG, "Failed to set equalizer enabled=$on", it) }
        runCatching { bassBoost?.enabled = on && _bassBoostStrength.value > 0 }
            .onFailure { Log.w(TAG, "Failed to set bass boost enabled state", it) }
        runCatching { virtualizer?.enabled = on && _virtualizerStrength.value > 0 }
            .onFailure { Log.w(TAG, "Failed to set virtualizer enabled state", it) }
    }

    private fun <T> tryCreate(name: String, factory: () -> T): T? =
        try {
            factory()
        } catch (e: Throwable) {
            Log.w(TAG, "$name unavailable on this device", e)
            null
        }

    private fun releaseEffects() {
        runCatching { equalizer?.release() }
            .onFailure { Log.w(TAG, "Failed to release Equalizer", it) }
        runCatching { bassBoost?.release() }
            .onFailure { Log.w(TAG, "Failed to release BassBoost", it) }
        runCatching { virtualizer?.release() }
            .onFailure { Log.w(TAG, "Failed to release Virtualizer", it) }
        equalizer = null
        bassBoost = null
        virtualizer = null
    }
}
