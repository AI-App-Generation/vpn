package com.thanhmich.veil.vpn.features.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Lớp phủ cấp shell: nội dung đăng ký ở đây được MainShell vẽ trên cùng (sau BottomNavBar),
 * để scrim/sheet phủ cả thanh tab. Màn tab đăng ký bằng DisposableEffect và gỡ khi dispose.
 */
class ShellOverlayHost {
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)
}

val LocalShellOverlay = staticCompositionLocalOf<ShellOverlayHost> { error("no shell") }
