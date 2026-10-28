package com.thanhmich.veil.vpn.features.settings

import com.thanhmich.veil.vpn.data.domain.AccentTheme

data class SettingsUiState(
    val accent: AccentTheme = AccentTheme.Default,
    val motionEnabled: Boolean = true,
    val languageName: String = "",
    val versionName: String = "",
)

sealed interface SettingsEvent {
    data object OpenLanguage : SettingsEvent
    data object ShareApp : SettingsEvent
    data class OpenUrl(val url: String) : SettingsEvent
}
