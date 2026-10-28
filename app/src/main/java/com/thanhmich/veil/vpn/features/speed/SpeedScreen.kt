package com.thanhmich.veil.vpn.features.speed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thanhmich.veil.vpn.R
import com.thanhmich.veil.vpn.data.domain.SpeedPhase
import com.thanhmich.veil.vpn.ui.components.*
import com.thanhmich.veil.vpn.ui.theme.AppTheme
import com.thanhmich.veil.vpn.ui.theme.Spacing

@Composable
fun SpeedScreen(vm: SpeedViewModel = viewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    SpeedContent(state, vm::start, vm::stop)
}

@Composable
private fun SpeedContent(state: SpeedUiState, onStart: () -> Unit, onStop: () -> Unit) {
    val seenHistory = remember {
        mutableStateMapOf<Long, Unit>().apply {
            state.history.forEach { put(it.timestamp, Unit) }
        }
    }
    Column(Modifier.fillMaxSize().background(AppTheme.colors.bg).statusBarsPadding()) {
        AppTopBar(title = stringResource(R.string.tab_speed))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenHorizontal)) {
            Spacer(Modifier.height(Spacing.space8))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val variant = when {
                    state.route.viaVpn -> StatusPillVariant.Connected
                    state.route.connecting -> StatusPillVariant.Connecting
                    else -> StatusPillVariant.Disconnected
                }
                val text = when {
                    state.route.viaVpn -> stringResource(R.string.speed_route_vpn, state.route.country.orEmpty())
                    state.route.connecting -> stringResource(R.string.speed_route_connecting)
                    else -> stringResource(R.string.speed_route_direct)
                }
                StatusPill(variant, text)
            }
            Spacer(Modifier.height(Spacing.space16))
            SurfaceCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.space20)) {
                val phase = (state.status as? SpeedStatus.Running)?.phase
                SpeedGauge(state.gaugeValue, state.gaugeUnit, phase, state.status, Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(1.3f).align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(Spacing.space12))
                Text(
                    stringResource(state.statusLine.textRes, *state.statusLine.args.toTypedArray()),
                    style = AppTheme.type.caption,
                    color = when (state.statusLine.tone) {
                        StatusTone.Tertiary -> AppTheme.colors.textTertiary
                        StatusTone.Secondary -> AppTheme.colors.textSecondary
                        StatusTone.Poor -> AppTheme.colors.poor
                    },
                    textAlign = TextAlign.Center,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.space16))
                val running = state.status is SpeedStatus.Running
                PrimaryButton(
                    text = stringResource(when { running -> R.string.speed_action_stop; state.status is SpeedStatus.Done || state.status is SpeedStatus.Error -> R.string.speed_action_again; else -> R.string.speed_action_start }),
                    iconRes = when { running -> R.drawable.ic_player_stop_filled; state.status is SpeedStatus.Done || state.status is SpeedStatus.Error -> R.drawable.ic_refresh; else -> R.drawable.ic_player_play_filled },
                    enabled = !state.offline,
                    onClick = if (running) onStop else onStart,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(Spacing.space12))
            val phase = (state.status as? SpeedStatus.Running)?.phase
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.space12)) {
                MetricTile(R.drawable.ic_activity, stringResource(R.string.speed_metric_ping), state.ping, stringResource(R.string.speed_unit_ms), Modifier.weight(1f), 0, phase == SpeedPhase.Ping)
                MetricTile(R.drawable.ic_arrow_down, stringResource(R.string.speed_metric_download), state.down, stringResource(R.string.speed_unit_mbps), Modifier.weight(1f), 1, phase == SpeedPhase.Download)
                MetricTile(R.drawable.ic_arrow_up, stringResource(R.string.speed_metric_upload), state.up, stringResource(R.string.speed_unit_mbps), Modifier.weight(1f), 1, phase == SpeedPhase.Upload)
            }
            Spacer(Modifier.height(Spacing.space8))
            SectionHeader(stringResource(R.string.speed_history_title))
            if (state.history.isEmpty()) EmptySpeedHistory()
            else Column(verticalArrangement = Arrangement.spacedBy(Spacing.space12)) {
                state.history.forEach { result ->
                    key(result.timestamp) {
                        val animateEntrance = result.timestamp !in seenHistory
                        SpeedHistoryItem(result, Modifier.fillMaxWidth(), animateEntrance)
                        SideEffect { seenHistory[result.timestamp] = Unit }
                    }
                }
            }
            EndSpacer()
        }
    }
}
