package com.thanhmich.veil.vpn.features.speed

import com.thanhmich.veil.vpn.data.domain.ConnectionState
import com.thanhmich.veil.vpn.data.domain.SpeedPhase
import com.thanhmich.veil.vpn.data.domain.SpeedProgress
import com.thanhmich.veil.vpn.data.domain.SpeedResult
import com.thanhmich.veil.vpn.data.domain.VpnServer
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SpeedUiLogicTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun gaugeScale_mapsEachTickToEqualSegment() {
        val ticks = listOf(0.0, 1.0, 5.0, 10.0, 20.0, 50.0, 100.0, 250.0, 500.0)
        ticks.forEachIndexed { index, value -> assertEquals(index / 8f, gaugeFraction(value), 0.0001f) }
        assertEquals(0f, gaugeFraction(-2.0), 0f)
        assertEquals(1f, gaugeFraction(900.0), 0f)
    }

    @Test fun routeInfo_onlyMarksConnectedAsVpn() {
        val server = server()
        val connected = routeInfo(ConnectionState.Connected(server, 1L))
        assertTrue(connected.viaVpn)
        assertEquals("Japan", connected.country)
        val disconnected = routeInfo(ConnectionState.Disconnected(null))
        assertFalse(disconnected.viaVpn)
        assertFalse(disconnected.connecting)
    }

    @Test fun stop_returnsToIdle_withoutSavingPartialResult() = runTest(dispatcher) {
        val connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected(null))
        val history = MutableStateFlow<List<SpeedResult>>(emptyList())
        val saved = mutableListOf<SpeedResult>()
        val vm = SpeedViewModel(
            runTest = { flow { emit(SpeedProgress.Sample(SpeedPhase.Download, 42.0)); awaitCancellation() } },
            connectionState = connection,
            history = history,
            saveResult = saved::add,
            isOnline = { true },
        )
        val collector = launch { vm.uiState.collect {} }
        runCurrent()
        vm.start()
        runCurrent()
        assertTrue(vm.uiState.value.status is SpeedStatus.Running)
        vm.stop()
        runCurrent()
        assertEquals(SpeedStatus.Idle, vm.uiState.value.status)
        assertTrue(saved.isEmpty())
        collector.cancelAndJoin()
        clear(vm)
    }

    @Test fun vpnKindChange_cancelsCollector_andReportsVpnChanged() = runTest(dispatcher) {
        val connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected(null))
        var cancelled = false
        val vm = SpeedViewModel(
            runTest = { flow { try { awaitCancellation() } finally { cancelled = true } } },
            connectionState = connection,
            history = MutableStateFlow(emptyList()),
            saveResult = {},
            isOnline = { true },
        )
        val collector = launch { vm.uiState.collect {} }
        runCurrent()
        vm.start()
        runCurrent()
        connection.value = ConnectionState.Connected(server(), 1L)
        runCurrent()
        assertEquals(SpeedStatus.Error(com.thanhmich.veil.vpn.data.domain.SpeedError.VpnChanged), vm.uiState.value.status)
        assertTrue(cancelled)
        collector.cancelAndJoin()
        clear(vm)
    }

    @Test fun finished_movesToDone_andSavesExactlyOnce() = runTest(dispatcher) {
        val result = SpeedResult(123L, 18, 2, 87.4, 23.1, false, null)
        val saved = mutableListOf<SpeedResult>()
        val vm = SpeedViewModel(
            runTest = { flowOf(SpeedProgress.Finished(result)) },
            connectionState = MutableStateFlow(ConnectionState.Disconnected(null)),
            history = MutableStateFlow(emptyList()),
            saveResult = saved::add,
            isOnline = { true },
        )
        val collector = launch { vm.uiState.collect {} }
        runCurrent()
        vm.start()
        runCurrent()
        assertEquals(SpeedStatus.Done, vm.uiState.value.status)
        assertEquals(listOf(result), saved)
        assertEquals(result.downMbps, vm.uiState.value.down!!, 0.0)
        collector.cancelAndJoin()
        clear(vm)
    }

    private fun server() = VpnServer("id", "Japan", "", 1, "host", "1.2.3.4", "url", 1, 20, null)

    private fun clear(vm: SpeedViewModel) {
        ViewModelStore().also { it.put("speed", vm) }.clear()
    }
}
