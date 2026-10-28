package com.thanhmich.veil.vpn.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.thanhmich.veil.vpn.data.domain.AccentTheme

/** 4 token accent đổi theo [AccentTheme] (Setting → Màu chủ đạo). */
@Immutable
data class AccentPalette(
    val accent: Color,
    val accentStrong: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
)

fun AccentTheme.palette(): AccentPalette = when (this) {
    AccentTheme.Indigo -> AccentPalette(Color(0xFF4C5BF5), Color(0xFF3A47D6), Color(0xFFECEEFE), Color(0xFF2B36A8))
    AccentTheme.Teal -> AccentPalette(Color(0xFF0F766E), Color(0xFF0B5E57), Color(0xFFE0F4F1), Color(0xFF0B5E57))
    AccentTheme.Violet -> AccentPalette(Color(0xFF7C3AED), Color(0xFF6425D0), Color(0xFFF1EAFE), Color(0xFF5B21B6))
    AccentTheme.Rose -> AccentPalette(Color(0xFFE11D48), Color(0xFFBE123C), Color(0xFFFDE8EE), Color(0xFF9F1239))
    AccentTheme.Ocean -> AccentPalette(Color(0xFF0369A1), Color(0xFF075985), Color(0xFFE3F1FA), Color(0xFF075985))
}

/** Toàn bộ màu của app (chỉ light). Neutral cố định, accent lấy từ [AccentPalette]. */
@Immutable
data class AppColors(
    val bg: Color = Color(0xFFF5F6FA),
    val surface: Color = Color(0xFFFFFFFF),
    val surfaceMuted: Color = Color(0xFFEEF0F5),
    val stroke: Color = Color(0xFFE3E6ED),
    val textPrimary: Color = Color(0xFF0F172A),
    val textSecondary: Color = Color(0xFF5B6475),
    val textTertiary: Color = Color(0xFF8A93A6),
    val onAccent: Color = Color(0xFFFFFFFF),
    val good: Color = Color(0xFF15803D),
    val goodContainer: Color = Color(0xFFDCFCE7),
    val fair: Color = Color(0xFFB45309),
    val fairContainer: Color = Color(0xFFFEF3C7),
    val poor: Color = Color(0xFFB91C1C),
    val poorContainer: Color = Color(0xFFFEE2E2),
    val scrim: Color = Color(0x660F172A),
    val accent: Color,
    val accentStrong: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
)

fun appColorsOf(palette: AccentPalette) = AppColors(
    accent = palette.accent,
    accentStrong = palette.accentStrong,
    accentContainer = palette.accentContainer,
    onAccentContainer = palette.onAccentContainer,
)

val LocalAppColors = staticCompositionLocalOf { appColorsOf(AccentTheme.Default.palette()) }
