package com.thanhmich.veil.vpn.features.speed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thanhmich.veil.vpn.core.AppGraph
import com.thanhmich.veil.vpn.core.SettingManager
import com.thanhmich.veil.vpn.corefeatures.speedtest.SpeedTestException
import com.thanhmich.veil.vpn.corefeatures.vpn.ConnectivityNetworkChecker
import com.thanhmich.veil.vpn.data.domain.ConnectionState
import com.thanhmich.veil.vpn.data.domain.SpeedError
import com.thanhmich.veil.vpn.data.domain.SpeedPhase
import com.thanhmich.veil.vpn.data.domain.SpeedProgress
import com.thanhmich.veil.vpn.data.domain.SpeedResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SpeedViewModel(
    private val runTest: () -> Flow<SpeedProgress> = { AppGraph.speedTestEngine.run() },
    connectionState: StateFlow<ConnectionState> = AppGraph.connectionManager.state,
    history: StateFlow<List<SpeedResult>> = SettingManager.speedHistory,
    private val saveResult: (SpeedResult) -> Unit = SettingManager::addSpeedResult,
    private val isOnline: () -> Boolean = { ConnectivityNetworkChecker(AppGraph.app).isOnline() },
    private val wallClock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val mutableState = MutableStateFlow(initialState(history.value, connectionState.value, isOnline()))
    val uiState = mutableState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), mutableState.value)
    private var testJob: Job? = null
    private var offlineMonitorJob: Job? = null

    init {
        viewModelScope.launch {
            history.collect { values ->
                mutableState.value = mutableState.value.copy(history = values.take(10))
            }
        }
        viewModelScope.launch {
            connectionState.collect { state ->
                mutableState.value = mutableState.value.copy(route = routeInfo(state))
            }
        }
        viewModelScope.launch {
            connectionState.map(::connectionKind).distinctUntilChanged().drop(1).collect {
                if (testJob?.isActive == true) fail(SpeedError.VpnChanged)
            }
        }
        if (mutableState.value.offline) monitorUntilOnline()
    }

    fun start() {
        if (testJob?.isActive == true) return
        if (!isOnline()) {
            mutableState.value = idleFromHistory(mutableState.value).copy(
                offline = true,
                statusLine = StatusLineModel.offline(),
            )
            monitorUntilOnline()
            return
        }
        offlineMonitorJob?.cancel()
        mutableState.value = mutableState.value.copy(
            status = SpeedStatus.Running(SpeedPhase.Ping),
            gaugeValue = 0.0,
            gaugeUnit = GaugeUnit.Ms,
            ping = null,
            down = null,
            up = null,
            statusLine = StatusLineModel.running(SpeedPhase.Ping),
            offline = false,
        )
        testJob = viewModelScope.launch {
            try {
                runTest().collect(::onProgress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SpeedTestException) {
                fail(e.error)
            } catch (_: Exception) {
                fail(SpeedError.Unknown)
            }
        }
    }

    fun stop() {
        testJob?.cancel()
        testJob = null
        mutableState.value = idleFromHistory(mutableState.value)
    }

    private fun onProgress(progress: SpeedProgress) {
        when (progress) {
            is SpeedProgress.Sample -> applyValue(progress.phase, progress.value)
            is SpeedProgress.PhaseDone -> {
                applyValue(progress.phase, progress.value)
                val next = when (progress.phase) {
                    SpeedPhase.Ping -> SpeedPhase.Download
                    SpeedPhase.Download -> SpeedPhase.Upload
                    SpeedPhase.Upload -> null
                }
                if (next != null) mutableState.value = mutableState.value.copy(
                    status = SpeedStatus.Running(next),
                    gaugeValue = 0.0,
                    gaugeUnit = GaugeUnit.Mbps,
                    statusLine = StatusLineModel.running(next),
                )
            }
            is SpeedProgress.Finished -> {
                saveResult(progress.result)
                mutableState.value = mutableState.value.copy(
                    status = SpeedStatus.Done,
                    gaugeValue = progress.result.downMbps,
                    gaugeUnit = GaugeUnit.Mbps,
                    ping = progress.result.pingMs.toDouble(),
                    down = progress.result.downMbps,
                    up = progress.result.upMbps,
                    history = (listOf(progress.result) + mutableState.value.history)
                        .distinctBy { it.timestamp }.take(10),
                    statusLine = StatusLineModel.done(formatTime(progress.result.timestamp)),
                )
                testJob = null
            }
        }
    }

    private fun applyValue(phase: SpeedPhase, value: Double) {
        val s = mutableState.value
        mutableState.value = when (phase) {
            SpeedPhase.Ping -> s.copy(gaugeValue = value, gaugeUnit = GaugeUnit.Ms, ping = value)
            SpeedPhase.Download -> s.copy(gaugeValue = value, gaugeUnit = GaugeUnit.Mbps, down = value)
            SpeedPhase.Upload -> s.copy(gaugeValue = value, gaugeUnit = GaugeUnit.Mbps, up = value)
        }
    }

    private fun fail(error: SpeedError) {
        testJob?.cancel()
        testJob = null
        mutableState.value = idleFromHistory(mutableState.value).copy(
            status = SpeedStatus.Error(error),
            statusLine = StatusLineModel.error(error),
            offline = error == SpeedError.NoNetwork,
        )
        if (error == SpeedError.NoNetwork) monitorUntilOnline()
    }

    private fun monitorUntilOnline() {
        if (offlineMonitorJob?.isActive == true) return
        offlineMonitorJob = viewModelScope.launch {
            while (!isOnline()) delay(1_000)
            mutableState.value = idleFromHistory(mutableState.value)
        }
    }

    private fun formatTime(timestamp: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}

internal enum class ConnectionKind { Disconnected, Connecting, Connected, Disconnecting }

internal fun connectionKind(state: ConnectionState): ConnectionKind = when (state) {
    is ConnectionState.Disconnected -> ConnectionKind.Disconnected
    is ConnectionState.Connecting -> ConnectionKind.Connecting
    is ConnectionState.Connected -> ConnectionKind.Connected
    ConnectionState.Disconnecting -> ConnectionKind.Disconnecting
}

internal fun routeInfo(state: ConnectionState): RouteInfo = when (state) {
    is ConnectionState.Connected -> RouteInfo(viaVpn = true, country = state.server.country)
    is ConnectionState.Connecting -> RouteInfo(connecting = true)
    else -> RouteInfo()
}

private fun initialState(history: List<SpeedResult>, connection: ConnectionState, online: Boolean): SpeedUiState =
    SpeedUiState(history = history.take(10), route = routeInfo(connection), offline = !online).let {
        idleFromHistory(it).copy(statusLine = if (online) StatusLineModel.idle() else StatusLineModel.offline())
    }

private fun idleFromHistory(state: SpeedUiState): SpeedUiState {
    val last = state.history.firstOrNull()
    return state.copy(
        status = SpeedStatus.Idle,
        gaugeValue = 0.0,
        gaugeUnit = GaugeUnit.Mbps,
        ping = last?.pingMs?.toDouble(),
        down = last?.downMbps,
        up = last?.upMbps,
        statusLine = StatusLineModel.idle(),
        offline = false,
    )
}
