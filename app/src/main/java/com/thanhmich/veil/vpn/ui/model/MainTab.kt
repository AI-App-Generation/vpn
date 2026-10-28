package com.thanhmich.veil.vpn.ui.model

/** 4 tab của MainShell; [route] là route trong NavHost tab. */
enum class MainTab(val route: String) {
    Vpn("tab/vpn"),
    Locations("tab/locations"),
    Speed("tab/speed"),
    Settings("tab/settings"),
}
