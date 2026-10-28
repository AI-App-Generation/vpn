package com.thanhmich.veil.vpn.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Thang spacing 4/8/12/16/20/24/32/48. */
object Spacing {
    val space4 = 4.dp
    val space8 = 8.dp
    val space12 = 12.dp
    val space16 = 16.dp
    val space20 = 20.dp
    val space24 = 24.dp
    val space32 = 32.dp
    val space48 = 48.dp

    /** Lề ngang chuẩn của màn. */
    val screenHorizontal = space20

    /** Khoảng đệm cuối vùng cuộn. */
    val endSpacer = 240.dp

    /** Vùng chạm tối thiểu. */
    val minTouch = 48.dp
}

object AppShapes {
    val xs = RoundedCornerShape(8.dp)
    val md = RoundedCornerShape(16.dp)
    val lg = RoundedCornerShape(24.dp)
    val pill = RoundedCornerShape(percent = 50)
    val sheetTop = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
}

/** Kích thước cố định dùng chung. */
object Sizes {
    val topBar = 64.dp
    val bottomNav = 72.dp
    val iconSm = 18.dp
    val icon = 20.dp
    val iconLg = 24.dp
    val flag = 40.dp
    val flagSm = 28.dp
    val iconContainer = 36.dp
    val primaryButton = 56.dp
    val secondaryButton = 48.dp
    val textButton = 36.dp
    val borderSelected = 1.5.dp
    val hairline = 1.dp
}
