package com.thanhmich.veil.vpn.features.locations

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thanhmich.veil.vpn.R
import com.thanhmich.veil.vpn.data.domain.*
import com.thanhmich.veil.vpn.ui.components.*
import com.thanhmich.veil.vpn.ui.theme.*

@Composable fun LocationSearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors; var focused by remember { mutableStateOf(false) }
    Row(modifier.height(52.dp).clip(AppShapes.md).background(c.surface).border(if (focused) Sizes.borderSelected else Sizes.hairline, if (focused) c.accent else c.stroke, AppShapes.md).padding(start = Spacing.space16, end = Spacing.space4), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_search), null, Modifier.size(Sizes.icon), tint = c.textTertiary); Spacer(Modifier.width(Spacing.space12))
        Box(Modifier.weight(1f)) { if (value.isEmpty()) Text(stringResource(R.string.locations_search_hint), style = AppTheme.type.body, color = c.textTertiary, maxLines = 1)
            BasicTextField(value, onValueChange, Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }, singleLine = true, textStyle = AppTheme.type.body.copy(color = c.textPrimary), cursorBrush = SolidColor(c.accent)) }
        if (value.isNotEmpty()) AppIconButton(R.drawable.ic_x, stringResource(R.string.close), { onValueChange("") }, tint = c.textTertiary) else Spacer(Modifier.size(Spacing.minTouch))
    }
}

@Composable fun CountryGroupCard(group: CountryGroup, expanded: Boolean, selection: ServerSelection, onToggle: () -> Unit, onSelect: (VpnServer) -> Unit) {
    val selectedId = (selection as? ServerSelection.Specific)?.serverId
    SurfaceCard(selected = group.servers.any { it.id == selectedId }, contentPadding = PaddingValues(Spacing.space8)) {
        Box(Modifier.fillMaxWidth().clip(AppShapes.md).clickable(onClick = onToggle).padding(horizontal = Spacing.space8)) { CountryRow(group.country, subtitle = stringResource(R.string.locations_server_count, group.servers.size), leading = { FlagAvatar(url = group.flagUrl) }, trailing = { PingBadge(group.bestPingMs); Spacer(Modifier.width(Spacing.space8)); val rotation by animateFloatAsState(if (expanded) 180f else 0f, motionStd(), label = "expand"); Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(Sizes.icon).rotate(rotation), tint = AppTheme.colors.textTertiary) }) }
        AnimatedVisibility(expanded, enter = expandVertically(animationSpec = motionStd()) + fadeIn(motionStd()), exit = shrinkVertically(animationSpec = motionStd()) + fadeOut(motionStd())) { Column(Modifier.padding(top = Spacing.space4), verticalArrangement = Arrangement.spacedBy(Spacing.space4)) { group.servers.forEach { server -> key(server.id) { ServerItem(server, server.id == selectedId) { onSelect(server) } } } } }
    }
}

@Composable private fun ServerItem(server: VpnServer, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(if (selected) AppTheme.colors.accentContainer else Color.Transparent, motionFast(), label = "serverSelected")
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(AppShapes.md).background(background).clickable(onClick = onClick).padding(horizontal = Spacing.space12, vertical = Spacing.space8), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(Sizes.iconContainer).clip(CircleShape).background(AppTheme.colors.surfaceMuted), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_server), null, Modifier.size(Sizes.iconSm), tint = AppTheme.colors.textSecondary) }
        Spacer(Modifier.width(Spacing.space12)); Column(Modifier.weight(1f)) { Text(server.hostname, style = AppTheme.type.body, color = AppTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(server.ipAddress, style = AppTheme.type.caption, color = AppTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Spacer(Modifier.width(Spacing.space8)); PingBadge(server.pingMs); Spacer(Modifier.width(Spacing.space8)); SelectedMark(selected)
    }
}
