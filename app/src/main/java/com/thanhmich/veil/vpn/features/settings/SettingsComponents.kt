package com.thanhmich.veil.vpn.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.thanhmich.veil.vpn.R
import com.thanhmich.veil.vpn.data.domain.AccentTheme
import com.thanhmich.veil.vpn.ui.theme.*

@Composable fun AccentSwatch(theme: AccentTheme, selected: Boolean, onSelect: (AccentTheme) -> Unit) {
    Box(Modifier.size(Spacing.minTouch).clip(CircleShape)
        .border(if (selected) 2.dp else 0.dp, if (selected) AppTheme.colors.textPrimary else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
        .clickable { onSelect(theme) }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(theme.palette().accent), contentAlignment = Alignment.Center) {
            if (selected) Icon(painterResource(R.drawable.ic_check), null, Modifier.size(Sizes.icon), tint = AppTheme.colors.onAccent)
        }
    }
}
