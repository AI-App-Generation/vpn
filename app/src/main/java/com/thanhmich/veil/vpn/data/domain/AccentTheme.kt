package com.thanhmich.veil.vpn.data.domain

enum class AccentTheme {
    Indigo, Teal, Violet, Rose, Ocean;

    companion object {
        val Default = Indigo

        /** Persist theo [name]; không khớp → [Default]. */
        fun fromName(name: String?): AccentTheme =
            entries.firstOrNull { it.name == name } ?: Default
    }
}
