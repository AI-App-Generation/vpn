package com.thanhmich.veil.vpn.features.locations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thanhmich.veil.vpn.core.AppGraph
import com.thanhmich.veil.vpn.core.SettingManager
import com.thanhmich.veil.vpn.data.domain.*
import com.thanhmich.veil.vpn.ui.model.MainTab
import java.text.Normalizer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

@OptIn(kotlinx.coroutines.FlowPreview::class)
class LocationsViewModel : ViewModel() {
    private val query = MutableStateFlow("")
    private val filteredQuery = query.debounce(150)
    private val expanded = MutableStateFlow<Set<String>?>(null)
    private val animateEntrance = MutableStateFlow(true)
    private val eventsChannel = Channel<LocationsEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    private val displayState = combine(expanded, animateEntrance) { open, entrance -> open to entrance }
    val uiState = combine(AppGraph.serverRepo.state, SettingManager.selection, query, filteredQuery, displayState) { catalogState, selection, rawQuery, debouncedQuery, display ->
        val (open, entrance) = display
        val catalog = when (catalogState) { is CatalogState.Ready -> catalogState.catalog; is CatalogState.Failed -> catalogState.cached; CatalogState.Loading -> null }
        val normalized = normalize(debouncedQuery)
        val groups = catalog?.groups.orEmpty().filter { normalized.isBlank() || normalize(it.country).contains(normalized) }
        val selectedCountry = (selection as? ServerSelection.Specific)?.country
        LocationsUiState(catalogState, rawQuery, groups.filter { it.tier == 1 }, groups.filter { it.tier != 1 }, open ?: selectedCountry?.let(::setOf).orEmpty(), selection,
            catalog?.crawledAt?.let { ((System.currentTimeMillis() - it).coerceAtLeast(0) / 60_000).toInt() }, catalog?.servers?.size ?: 0, entrance)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocationsUiState())

    fun onQuery(value: String) { query.value = value }
    fun refresh() = AppGraph.serverRepo.refresh()
    fun toggleExpand(country: String) {
        val selected = (SettingManager.selection.value as? ServerSelection.Specific)?.country
        expanded.value = (expanded.value ?: selected?.let(::setOf).orEmpty()).toMutableSet().apply { if (!add(country)) remove(country) }
    }
    fun markEntranceShown() { animateEntrance.value = false }
    fun selectAuto() = select(ServerSelection.Auto)
    fun selectServer(server: VpnServer) = select(ServerSelection.Specific(server.id, server.country))
    private fun select(selection: ServerSelection) { SettingManager.setSelection(selection); eventsChannel.trySend(LocationsEvent.NavigateTab(MainTab.Vpn)) }
    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD).replace("\\p{M}+".toRegex(), "").lowercase()
}
