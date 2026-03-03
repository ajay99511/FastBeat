package com.local.offlinemediaplayer.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import com.local.offlinemediaplayer.ui.theme.AppThemeConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class ThemeViewModel @Inject constructor(
    app: Application
) : AndroidViewModel(app) {

    val themes = mapOf(
        "blue" to AppThemeConfig("blue", Color(0xFF00E5FF), "DIGITAL WAVES", "Quick Mix"),
        "green" to AppThemeConfig("green", Color(0xFF22C55E), "ECO FREQUENCY", "Fresh Finds"),
        "orange" to AppThemeConfig("orange", Color(0xFFFF5500), "AMBER HORIZON", "Jump Back In")
    )

    private val sharedPrefs = app.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private val _isDarkTheme = MutableStateFlow(sharedPrefs.getBoolean("is_dark_mode", true))
    val isDarkTheme = _isDarkTheme.asStateFlow()

    private val savedThemeId = sharedPrefs.getString("current_theme_id", "orange") ?: "orange"
    private val _currentTheme = MutableStateFlow(themes[savedThemeId] ?: themes["orange"]!!)
    val currentTheme = _currentTheme.asStateFlow()

    fun updateTheme(themeId: String) {
        _currentTheme.value = themes[themeId] ?: themes["orange"]!!
        sharedPrefs.edit { putString("current_theme_id", themeId) }
    }

    fun toggleThemeMode() {
        val newMode = !_isDarkTheme.value
        _isDarkTheme.value = newMode
        sharedPrefs.edit { putBoolean("is_dark_mode", newMode) }
    }
}
