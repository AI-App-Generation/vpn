package com.thanhmich.veil.vpn.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thanhmich.veil.vpn.R
import com.thanhmich.veil.vpn.ui.model.MainTab
import com.thanhmich.veil.vpn.ui.theme.AppShapes
import com.thanhmich.veil.vpn.ui.theme.AppTheme
import com.thanhmich.veil.vpn.ui.theme.Sizes
import com.thanhmich.veil.vpn.ui.theme.Spacing
import com.thanhmich.veil.vpn.ui.theme.motionFast
import com.thanhmich.veil.vpn.ui.theme.motionStd
import com.thanhmich.veil.vpn.ui.theme.shadowNav
import com.thanhmich.veil.vpn.ui.theme.strongLabel

private data class NavItem(
    val tab: MainTab,
    @DrawableRes val icon: Int,
    @DrawableRes val iconSelected: Int,
    @StringRes val label: Int,
)

private val NAV_ITEMS = listOf(
    NavItem(MainTab.Vpn, R.drawable.ic_shield, R.drawable.ic_shield_filled, R.string.tab_vpn),
    NavItem(MainTab.Locations, R.drawable.ic_world, R.drawable.ic_world_filled, R.string.tab_locations),
    NavItem(MainTab.Speed, R.drawable.ic_gauge, R.drawable.ic_gauge_filled, R.string.tab_speed),
    NavItem(MainTab.Settings, R.drawable.ic_settings, R.drawable.ic_settings_filled, R.string.tab_settings),
)

private val INDICATOR_WIDTH = 56.dp
private val INDICATOR_HEIGHT = 32.dp

/** Nav bar 4 tab: indicator pill accentContainer trượt giữa các tab. Tự áp navigationBarsPadding. */
@Composable
fun BottomNavBar(
    current: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadowNav(RectangleShape)
            .background(c.surface)
    ) {
        Box(Modifier.fillMaxWidth().height(Sizes.hairline).background(c.stroke))
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(Sizes.bottomNav - Sizes.hairline)
        ) {
            val itemWidth = maxWidth / NAV_ITEMS.size
            val index = NAV_ITEMS.indexOfFirst { it.tab == current }.coerceAtLeast(0)
            val indicatorX by animateDpAsState(
                itemWidth * index + (itemWidth - INDICATOR_WIDTH) / 2,
                motionStd(),
                label = "navIndicator",
            )
            // Indicator: cùng hàng dọc với icon (top 12dp)
            Box(
                Modifier
                    .offset(x = indicatorX, y = Spacing.space12)
                    .size(INDICATOR_WIDTH, INDICATOR_HEIGHT)
                    .clip(AppShapes.pill)
                    .background(c.accentContainer)
            )
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                NAV_ITEMS.forEach { item ->
                    NavBarItem(
                        item = item,
                        selected = item.tab == current,
                        onClick = { onSelect(item.tab) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun NavBarItem(
    item: NavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    val tint by animateColorAsState(if (selected) c.accent else c.textSecondary, motionFast(), label = "navTint")
    Column(
        modifier = modifier
            .fillMaxHeight()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 36.dp),
            )
            .padding(top = Spacing.space12),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(INDICATOR_WIDTH, INDICATOR_HEIGHT), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(if (selected) item.iconSelected else item.icon),
                contentDescription = null,
                modifier = Modifier.size(Sizes.iconLg),
                tint = tint,
            )
        }
        Spacer(Modifier.height(Spacing.space4))
        Text(
            text = stringResource(item.label),
            style = AppTheme.type.caption.strongLabel,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Spacing.space4),
        )
    }
}
