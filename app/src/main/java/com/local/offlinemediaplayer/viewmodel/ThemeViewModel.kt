package com.local.offlinemediaplayer.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.offlinemediaplayer.data.AppPreferencesManager
import com.local.offlinemediaplayer.ui.theme.AppThemeConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel
    @Inject
    constructor(
        private val appPrefs: AppPreferencesManager,
    ) : ViewModel() {
        val themes =
            mapOf(
                "blue" to AppThemeConfig("blue", Color(0xFF00E5FF), "DIGITAL WAVES", "Quick Mix"),
                "green" to AppThemeConfig("green", Color(0xFF22C55E), "ECO FREQUENCY", "Fresh Finds"),
                "orange" to AppThemeConfig("orange", Color(0xFFFF5500), "AMBER HORIZON", "Jump Back In"),
            )

        private val defaultTheme = themes.getValue(AppPreferencesManager.DEFAULT_THEME_ID)

        private val _isDarkTheme = MutableStateFlow(true)
        val isDarkTheme = _isDarkTheme.asStateFlow()

        private val _currentTheme = MutableStateFlow(defaultTheme)
        val currentTheme = _currentTheme.asStateFlow()

        /**
         * False until the stored theme has been read back.
         *
         * P5-C.3 moved these two values from SharedPreferences to DataStore, which cannot be read
         * synchronously, so the flows above start on the defaults and settle a few milliseconds
         * later. For a *theme* that difference is visible: a user on light mode would have seen the
         * app open dark and snap to light. `MainActivity` therefore withholds the first composition
         * until this turns true, which keeps the launch background on screen for those frames
         * exactly as it already is during activity start — no new dependency, and no flash.
         */
        private val _isLoaded = MutableStateFlow(false)
        val isLoaded = _isLoaded.asStateFlow()

        init {
            viewModelScope.launch {
                _isDarkTheme.value = appPrefs.getDarkTheme()
                _currentTheme.value = themes[appPrefs.getThemeId()] ?: defaultTheme
                _isLoaded.value = true
            }
        }

        fun updateTheme(themeId: String) {
            _currentTheme.value = themes[themeId] ?: defaultTheme
            viewModelScope.launch { appPrefs.setThemeId(themeId) }
        }

        fun toggleThemeMode() {
            val newMode = !_isDarkTheme.value
            _isDarkTheme.value = newMode
            viewModelScope.launch { appPrefs.setDarkTheme(newMode) }
        }
    }
