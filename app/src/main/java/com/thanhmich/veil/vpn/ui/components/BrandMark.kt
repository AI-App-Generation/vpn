package com.thanhmich.veil.vpn.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.thanhmich.veil.vpn.R
import com.thanhmich.veil.vpn.ui.theme.AppTheme

/**
 * Logo trong app (fallback vì `logo_veil` chưa sinh được — xem `# Assets`):
 * vòng tròn gradient accent + khiên đặc onAccent 60% kích thước.
 */
@Composable
fun BrandMark(size: Dp, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(c.accent, c.accentStrong))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_shield_filled),
            contentDescription = null,
            modifier = Modifier.size(size * 0.6f),
            tint = c.onAccent,
        )
    }
}
