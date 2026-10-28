package com.thanhmich.veil.vpn.features.locations

import com.thanhmich.veil.vpn.data.domain.CatalogState
import com.thanhmich.veil.vpn.data.domain.CountryGroup
import com.thanhmich.veil.vpn.data.domain.ServerSelection
import com.thanhmich.veil.vpn.ui.model.MainTab

data class LocationsUiState(
    val catalog: CatalogState = CatalogState.Loading,
    val query: String = "",
    val tier1: List<CountryGroup> = emptyList(),
    val tier2: List<CountryGroup> = emptyList(),
    /** Tập country đang mở rộng. */
    val expanded: Set<String> = emptySet(),
    val selection: ServerSelection = ServerSelection.Auto,
    val updatedMinutesAgo: Int? = null,
    val totalServers: Int = 0,
    val animateEntrance: Boolean = true,
)

sealed interface LocationsEvent {
    data class NavigateTab(val tab: MainTab) : LocationsEvent
}
