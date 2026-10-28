package com.thanhmich.veil.vpn.features.language

import com.thanhmich.veil.vpn.R

data class Region(
    val localeId: String,
    val text: String,
    val resId: Int
)

data class Language(
    val localeId: String,
    val text: String,
    val resId: Int,
    val regions: List<Region> = emptyList()
)

val supportedLanguages = listOf(
    Language(
        localeId = "es",
        text = "Spanish",
        resId = R.drawable.flag_es,
        regions = listOf(
            Region("es", "Spain", R.drawable.flag_es),
            Region("es", "Mexico", R.drawable.flag_es),
            Region("es", "Argentina", R.drawable.flag_es),
            Region("es", "United States", R.drawable.flag_es)
        )
    ),

    Language(
        localeId = "hi",
        text = "Hindi",
        resId = R.drawable.flag_hi,
    ),

    Language(
        localeId = "ru",
        text = "Russian",
        resId = R.drawable.flag_ru,
        regions = listOf(
            Region("ru", "Russia", R.drawable.flag_ru),
            Region("ru", "Belarus", R.drawable.flag_ru),
            Region("ru", "Kazakhstan", R.drawable.flag_ru)
        )
    ),

    Language(
        localeId = "en",
        text = "English",
        resId = R.drawable.flag_en_us,
        regions = listOf(
            Region("en", "United States", R.drawable.flag_en_us),
            Region("en", "United Kingdom", R.drawable.flag_en_gb),
            Region("en", "Canada", R.drawable.flag_en_ca),
            Region("en", "Australia", R.drawable.flag_en_au),
            Region("en", "India", R.drawable.flag_en_in)
        )
    ),

    Language(
        localeId = "ar",
        text = "Arabic",
        resId = R.drawable.flag_ar
    ),

    Language(
        localeId = "fr",
        text = "French",
        resId = R.drawable.flag_fr
    ),

    Language(
        localeId = "pt",
        text = "Portuguese",
        resId = R.drawable.flag_pt
    ),

    Language(
        localeId = "de",
        text = "German",
        resId = R.drawable.flag_de
    ),

    Language(
        localeId = "ja",
        text = "Japanese",
        resId = R.drawable.flag_ja
    ),

    Language(
        localeId = "ko",
        text = "Korean",
        resId = R.drawable.flag_ko
    ),

    Language(
        localeId = "vi",
        text = "Vietnamese",
        resId = R.drawable.flag_vi
    ),

    Language(
        localeId = "zh",
        text = "Chinese",
        resId = R.drawable.flag_zh
    )
)

/** The language's name written in itself ("Español", "日本語"), capitalised for list display. */
fun nativeLanguageName(localeId: String): String {
    val locale = java.util.Locale.forLanguageTag(localeId)
    return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
}
