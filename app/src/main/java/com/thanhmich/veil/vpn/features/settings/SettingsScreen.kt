package com.thanhmich.veil.vpn.features.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thanhmich.veil.vpn.BuildConfig
import com.thanhmich.veil.vpn.R
import com.thanhmich.veil.vpn.data.domain.AccentTheme
import com.thanhmich.veil.vpn.ui.components.*
import com.thanhmich.veil.vpn.ui.theme.*
import com.thanhmich.veil.vpn.utils.IntentUtils

@Composable
fun SettingsScreen(onOpenLanguage: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(AppTheme.colors.bg).statusBarsPadding()) {
        AppTopBar(stringResource(R.string.settings))
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenHorizontal)) {
            SectionHeader(stringResource(R.string.settings_appearance), first = true)
            SurfaceCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_palette), null, Modifier.size(Sizes.icon), tint = AppTheme.colors.textSecondary)
                    Spacer(Modifier.width(Spacing.space12)); Text(stringResource(R.string.settings_accent), style = AppTheme.type.body, color = AppTheme.colors.textPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(accentName(state.accent), style = AppTheme.type.caption, color = AppTheme.colors.textSecondary, maxLines = 1)
                }
                Spacer(Modifier.height(Spacing.space16))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { AccentTheme.entries.forEach { AccentSwatch(it, it == state.accent, vm::setAccent) } }
                Spacer(Modifier.height(Spacing.space8))
                SettingRow(R.drawable.ic_sparkles, stringResource(R.string.settings_motion), trailing = { AppSwitch(state.motionEnabled, vm::setMotionEnabled) })
            }
            SectionHeader(stringResource(R.string.settings_general))
            SurfaceCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.space8)) {
                SettingRow(R.drawable.ic_language, stringResource(R.string.language), value = state.languageName, showChevron = true, onClick = onOpenLanguage)
                SettingRow(R.drawable.ic_share, stringResource(R.string.settings_share), showChevron = true, onClick = {
                    val text = context.getString(R.string.settings_share_text, context.getString(R.string.app_name), context.packageName)
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), context.getString(R.string.settings_share)))
                })
                SettingRow(R.drawable.ic_info_circle, stringResource(R.string.settings_version), value = state.versionName)
            }
            SectionHeader(stringResource(R.string.settings_servers))
            SurfaceCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.space20)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).background(AppTheme.colors.fairContainer, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_info_circle_filled), null, Modifier.size(Sizes.icon), tint = AppTheme.colors.fair) }
                    Spacer(Modifier.width(Spacing.space12)); Text(stringResource(R.string.settings_community_title), style = AppTheme.type.headline, color = AppTheme.colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(Spacing.space12)); Text(stringResource(R.string.community_notice_full), style = AppTheme.type.body.copy(fontWeight = FontWeight.Normal), color = AppTheme.colors.textSecondary)
                Spacer(Modifier.height(Spacing.space8)); TextActionButton(stringResource(R.string.settings_learn_vpngate), { IntentUtils.openUrl(context, "https://www.vpngate.net") }, Modifier.offset(x = -Spacing.space12), R.drawable.ic_external_link)
            }
            EndSpacer()
        }
    }
}

@Composable private fun accentName(theme: AccentTheme) = stringResource(when (theme) { AccentTheme.Indigo -> R.string.accent_indigo; AccentTheme.Teal -> R.string.accent_teal; AccentTheme.Violet -> R.string.accent_violet; AccentTheme.Rose -> R.string.accent_rose; AccentTheme.Ocean -> R.string.accent_ocean })
