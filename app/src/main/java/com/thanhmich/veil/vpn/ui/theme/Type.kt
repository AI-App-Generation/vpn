package com.thanhmich.veil.vpn.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 5 bậc chữ của app, cách nhau ≥ 2sp. Mỗi màn dùng ≤ 4 bậc. */
@Immutable
data class AppTypography(
    val display: TextStyle = TextStyle(fontFamily = FontFamily.Default, fontSize = 44.sp, lineHeight = 52.sp, fontWeight = FontWeight.SemiBold),
    val title: TextStyle = TextStyle(fontFamily = FontFamily.Default, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    val headline: TextStyle = TextStyle(fontFamily = FontFamily.Default, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    val body: TextStyle = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    val caption: TextStyle = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

/** Đoạn văn (cùng bậc, weight Normal). */
val TextStyle.regular: TextStyle get() = copy(fontWeight = FontWeight.Normal)

/** Nhãn nút / nhấn mạnh (cùng bậc, weight SemiBold). */
val TextStyle.semiBold: TextStyle get() = copy(fontWeight = FontWeight.SemiBold)

/** Badge / pill / nav / SectionHeader. */
val TextStyle.strongLabel: TextStyle get() = copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp)

/** Số liệu thay đổi liên tục: chữ số đều bề ngang. */
val TextStyle.tnum: TextStyle get() = copy(fontFeatureSettings = "tnum")

val LocalAppTypography = staticCompositionLocalOf { AppTypography() }

/** Cho các màn BaseProject còn dùng MaterialTheme.typography. */
internal fun AppTypography.toMaterial() = Typography(
    displayLarge = display,
    headlineLarge = title,
    titleLarge = title,
    titleMedium = headline,
    bodyLarge = body,
    bodyMedium = body,
    labelLarge = body.semiBold,
    bodySmall = caption,
    labelSmall = caption,
)
