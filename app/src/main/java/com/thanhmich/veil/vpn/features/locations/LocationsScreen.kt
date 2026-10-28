package com.thanhmich.veil.vpn.features.locations

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thanhmich.veil.vpn.R
import com.thanhmich.veil.vpn.data.domain.CatalogState
import com.thanhmich.veil.vpn.data.domain.ServerSelection
import com.thanhmich.veil.vpn.ui.components.*
import com.thanhmich.veil.vpn.ui.model.BannerVariant
import com.thanhmich.veil.vpn.ui.model.MainTab
import com.thanhmich.veil.vpn.ui.theme.*

@Composable
fun LocationsScreen(onNavigateTab: (MainTab) -> Unit, vm: LocationsViewModel = viewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.events.collect { if (it is LocationsEvent.NavigateTab) onNavigateTab(it.tab) } }
    LaunchedEffect(state.catalog, state.animateEntrance) {
        if (state.animateEntrance && state.catalog is CatalogState.Ready) {
            kotlinx.coroutines.delay(Motion.STD + Motion.STAGGER_MAX * Motion.STAGGER_STEP.toLong())
            vm.markEntranceShown()
        }
    }
    Column(Modifier.fillMaxSize().background(AppTheme.colors.bg).statusBarsPadding()) {
        AppTopBar(stringResource(R.string.tab_locations), trailing = { RefreshButton((state.catalog as? CatalogState.Ready)?.refreshing == true, vm::refresh) })
        LocationSearchField(state.query, vm::onQuery, Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal))
        Spacer(Modifier.height(Spacing.space8))
        LocationsBody(state, vm)
    }
}

@Composable
private fun ColumnScope.LocationsBody(state: LocationsUiState, vm: LocationsViewModel) {
    val catalog = when (val s = state.catalog) { is CatalogState.Ready -> s.catalog; is CatalogState.Failed -> s.cached; else -> null }
    if (catalog == null && state.catalog is CatalogState.Loading) { SkeletonList(); return }
    if (catalog == null && state.catalog is CatalogState.Failed) {
        EmptyState(stringResource(R.string.locations_load_error), stringResource(R.string.error_generic), Modifier.fillMaxWidth().weight(1f).padding(top = Spacing.space48), iconRes = R.drawable.ic_cloud_off, actionText = stringResource(R.string.retry), onAction = vm::refresh); return
    }
    if (catalog != null && catalog.servers.isEmpty()) {
        EmptyState(stringResource(R.string.locations_empty_title), stringResource(R.string.locations_empty_body), Modifier.fillMaxWidth().weight(1f).padding(top = Spacing.space48), iconRes = R.drawable.ic_world, actionText = stringResource(R.string.retry), onAction = vm::refresh); return
    }
    val groups = state.tier1 + state.tier2
    if (state.query.isNotBlank() && groups.isEmpty()) {
        EmptyState(stringResource(R.string.locations_no_results, state.query), stringResource(R.string.locations_no_results_body), Modifier.fillMaxWidth().weight(1f).padding(top = Spacing.space48), iconRes = R.drawable.ic_search); return
    }
    Text(if (state.updatedMinutesAgo == null) stringResource(R.string.locations_total, state.totalServers) else stringResource(R.string.locations_updated, state.updatedMinutesAgo, state.totalServers), style = AppTheme.type.caption, color = AppTheme.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = Spacing.screenHorizontal))
    LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, top = Spacing.space12, bottom = Spacing.endSpacer), verticalArrangement = Arrangement.spacedBy(Spacing.space12)) {
        if (catalog?.fromCache == true || (state.catalog as? CatalogState.Failed)?.cached != null) item("banner") { InlineBanner(BannerVariant.Offline, stringResource(R.string.locations_cached), actionText = stringResource(R.string.retry), onAction = vm::refresh) }
        item("auto") { SurfaceCard(selected = state.selection is ServerSelection.Auto, onClick = vm::selectAuto) { CountryRow(stringResource(R.string.vpn_server_auto), subtitle = stringResource(R.string.locations_auto_subtitle), leading = { Box(Modifier.size(40.dp).background(AppTheme.colors.accentContainer, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_bolt_filled), null, Modifier.size(Sizes.icon), tint = AppTheme.colors.accent) } }, trailing = { SelectedMark(state.selection is ServerSelection.Auto) }) } }
        if (state.query.isBlank() && state.tier1.isNotEmpty()) item("h1") { SectionHeader(stringResource(R.string.locations_priority), first = true) }
        items(state.tier1, key = { "p_${it.country}" }) { group -> StaggeredLocationItem(state.tier1.indexOf(group), state.animateEntrance) { CountryGroupCard(group, group.country in state.expanded, state.selection, { vm.toggleExpand(group.country) }, vm::selectServer) } }
        if (state.query.isBlank() && state.tier2.isNotEmpty()) item("h2") { SectionHeader(stringResource(R.string.locations_all), first = state.tier1.isEmpty()) }
        items(state.tier2, key = { "a_${it.country}" }) { group -> StaggeredLocationItem(state.tier1.size + state.tier2.indexOf(group), state.animateEntrance) { CountryGroupCard(group, group.country in state.expanded, state.selection, { vm.toggleExpand(group.country) }, vm::selectServer) } }
    }
}

@Composable private fun StaggeredLocationItem(index: Int, animateEntrance: Boolean, content: @Composable () -> Unit) {
    var entered by rememberSaveable { mutableStateOf(!animateEntrance) }
    val motionEnabled = AppTheme.motionEnabled
    LaunchedEffect(Unit) { if (animateEntrance && motionEnabled) kotlinx.coroutines.delay(index.coerceAtMost(Motion.STAGGER_MAX) * Motion.STAGGER_STEP.toLong()); entered = true }
    val alpha by animateFloatAsState(if (entered || !motionEnabled) 1f else 0f, motionStd(), label = "locationAlpha")
    val offset by animateFloatAsState(if (entered || !motionEnabled) 0f else 12f, motionStd(), label = "locationOffset")
    Box(Modifier.graphicsLayer { this.alpha = alpha; translationY = offset * density }) { content() }
}

@Composable private fun RefreshButton(refreshing: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "refresh")
    val rotation = if (refreshing && AppTheme.motionEnabled) transition.animateFloat(0f, 360f, infiniteRepeatable(tween(800, easing = LinearEasing)), label = "rotation").value else 0f
    AppIconButton(R.drawable.ic_refresh, stringResource(R.string.retry), onClick, enabled = !refreshing, iconModifier = Modifier.rotate(rotation).alpha(if (refreshing && !AppTheme.motionEnabled) .4f else 1f))
}

@Composable private fun ColumnScope.SkeletonList() {
    Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.space12), verticalArrangement = Arrangement.spacedBy(Spacing.space12)) {
        repeat(6) { SurfaceCard(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) { SkeletonBlock(Modifier.size(40.dp), androidx.compose.foundation.shape.CircleShape); Spacer(Modifier.width(Spacing.space12)); Column { SkeletonBlock(Modifier.width(140.dp).height(12.dp)); Spacer(Modifier.height(Spacing.space8)); SkeletonBlock(Modifier.width(90.dp).height(8.dp)) } } } }
    }
}
