package com.thanhmich.veil.vpn.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thanhmich.veil.vpn.BuildConfig
import com.thanhmich.veil.vpn.core.SettingManager
import com.thanhmich.veil.vpn.data.domain.AccentTheme
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel : ViewModel() {
    val uiState = combine(SettingManager.accent, SettingManager.motionEnabled, SettingManager.appLocale) { accent, motion, localeId ->
        val language = Locale.forLanguageTag(localeId).getDisplayLanguage(Locale.forLanguageTag(localeId)).replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        SettingsUiState(accent, motion, language, "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(versionName = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"))
    fun setAccent(theme: AccentTheme) = SettingManager.setAccent(theme)
    fun setMotionEnabled(enabled: Boolean) = SettingManager.setMotionEnabled(enabled)
}
