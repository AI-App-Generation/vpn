package com.thanhmich.veil.vpn.features.language

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.thanhmich.veil.vpn.FadeSlideHorizontal
import com.thanhmich.veil.vpn.LogScreenView
import com.thanhmich.veil.vpn.R
import com.thanhmich.veil.vpn.ads.AdsPlacementHolder
import com.thanhmich.veil.vpn.core.SettingManager
import com.thanhmich.veil.vpn.ui.components.AppIconButton
import com.thanhmich.veil.vpn.ui.components.FlagAvatar
import com.thanhmich.veil.vpn.ui.components.SelectedMark
import com.thanhmich.veil.vpn.ui.components.SurfaceCard
import com.thanhmich.veil.vpn.ui.components.ads.ComposeNativeAdFrame
import com.thanhmich.veil.vpn.ui.theme.AppTheme
import com.thanhmich.veil.vpn.ui.theme.Sizes
import com.thanhmich.veil.vpn.ui.theme.Spacing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.thanhmich.veil.vpn.ui.theme.motionStd
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.thanhmich.veil.vpn.ui.theme.AppShapes
import com.thanhmich.veil.vpn.ui.theme.semiBold
import kotlinx.coroutines.delay

enum class LanguageStep {
    Language,
    Region,
}

/**
 * S06 Language.
 * - Luồng đầu (BaseProject): 2 bước ngôn ngữ → region (nếu có), native ad đáy, nút "Next" hiện sau khi chọn.
 * - [fromSettings] = true: chỉ danh sách ngôn ngữ, không ad, nút back; chọn = áp dụng + [onBack].
 */
@Composable
fun LanguageScreen(
    selectRegion: Boolean = true,
    fromSettings: Boolean = false,
    onNext: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    LogScreenView("Language")

    var currentStep by rememberSaveable { mutableStateOf(LanguageStep.Language) }
    val currentLocale by SettingManager.appLocale.collectAsState()
    var selectedLanguageLocaleId by rememberSaveable {
        mutableStateOf(if (fromSettings) currentLocale.substringBefore('-') else null)
    }
    var selectedRegionIndex by rememberSaveable { mutableIntStateOf(-1) }

    val selectedLanguage = remember(selectedLanguageLocaleId) {
        supportedLanguages.find { it.localeId == selectedLanguageLocaleId }
    }

    BackHandler(currentStep == LanguageStep.Region) {
        selectedRegionIndex = -1
        currentStep = LanguageStep.Language
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(AppTheme.colors.bg)
    ) {
        AnimatedContent(
            targetState = currentStep,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                when {
                    initialState == LanguageStep.Language && targetState == LanguageStep.Region ->
                        FadeSlideHorizontal.first togetherWith ExitTransition.KeepUntilTransitionsFinished
                    else -> EnterTransition.None togetherWith ExitTransition.None
                }
            },
            label = "LanguageStepAnimation",
        ) { step ->
            when (step) {
                LanguageStep.Language -> SelectionList(
                    title = stringResource(if (fromSettings) R.string.language else R.string.language_title),
                    subtitle = stringResource(R.string.language_subtitle),
                    items = supportedLanguages.map {
                        val native = nativeLanguageName(it.localeId)
                        // English name as a hint, unless it just repeats the native one.
                        SelectionItem(it.localeId, native, it.resId, it.text.takeIf { en -> !en.equals(native, ignoreCase = true) })
                    },
                    selectedIndex = supportedLanguages.indexOfFirst { it.localeId == selectedLanguageLocaleId },
                    showAd = !fromSettings,
                    showNext = !fromSettings && selectedLanguageLocaleId != null,
                    onBack = if (fromSettings) onBack else null,
                    onSelected = { index ->
                        val localeId = supportedLanguages[index].localeId
                        selectedLanguageLocaleId = localeId
                        SettingManager.setAppLocale(localeId)
                        if (fromSettings) onBack()
                    },
                    onNext = {
                        val language = selectedLanguage
                        if (selectRegion && language != null && language.regions.isNotEmpty()) {
                            currentStep = LanguageStep.Region
                        } else {
                            onNext()
                        }
                    },
                )

                LanguageStep.Region -> {
                    val language = selectedLanguage ?: return@AnimatedContent
                    SelectionList(
                        title = stringResource(R.string.select_region),
                        subtitle = stringResource(R.string.region_subtitle),
                        items = language.regions.mapIndexed { i, r -> SelectionItem("$i", r.text, r.resId, null) },
                        selectedIndex = selectedRegionIndex,
                        showAd = true,
                        showNext = selectedRegionIndex >= 0,
                        onBack = null,
                        onSelected = { selectedRegionIndex = it },
                        onNext = {
                            val region = language.regions.getOrNull(selectedRegionIndex)
                            SettingManager.setAppLocale(region?.localeId ?: language.localeId)
                            onNext()
                        },
                    )
                }
            }
        }
    }
}

private data class SelectionItem(val key: String, val text: String, val flagRes: Int, val subtitle: String?)

@Composable
private fun SelectionList(
    title: String,
    subtitle: String,
    items: List<SelectionItem>,
    selectedIndex: Int,
    showAd: Boolean,
    showNext: Boolean,
    onBack: (() -> Unit)?,
    onSelected: (Int) -> Unit,
    onNext: () -> Unit,
) {
    var nextVisible by remember { mutableStateOf(false) }
    var hasReloadedAd by remember { mutableStateOf(false) }
    var reloadNativeAd by remember { mutableStateOf<(String?) -> Unit>({}) }

    // Giữ hành vi BaseProject: nút Next xuất hiện trễ 500ms sau khi chọn.
    LaunchedEffect(selectedIndex, showNext) {
        nextVisible = false
        if (showNext) {
            delay(500)
            nextVisible = true
        }
    }

    // Opaque on purpose: Language is kept on screen (KeepUntilTransitionsFinished) while Region
    // fades in on top of it, so a transparent step would show both lists at once.
    Column(
        Modifier
            .fillMaxSize()
            .background(AppTheme.colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        PickerHeader(
            title = title,
            subtitle = subtitle,
            onBack = onBack,
            showConfirm = nextVisible,
            onConfirm = onNext,
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(
                start = Spacing.screenHorizontal,
                end = Spacing.screenHorizontal,
                top = Spacing.space8,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.space12),
        ) {
            itemsIndexed(items, key = { _, it -> it.key }) { index, item ->
                SurfaceCard(
                    modifier = Modifier.fillMaxWidth(),
                    selected = index == selectedIndex,
                    onClick = {
                        onSelected(index)
                        if (showAd && !hasReloadedAd) {
                            hasReloadedAd = true
                            reloadNativeAd(AdsPlacementHolder.NATIVE)
                        }
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FlagAvatar(res = item.flagRes, size = Sizes.flag)
                        Spacer(Modifier.width(Spacing.space12))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = item.text,
                                style = AppTheme.type.body,
                                color = AppTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (item.subtitle != null) {
                                Text(
                                    text = item.subtitle,
                                    style = AppTheme.type.caption,
                                    color = AppTheme.colors.textTertiary,
                                    maxLines = 1,
                                )
                            }
                        }
                        Spacer(Modifier.width(Spacing.space8))
                        SelectedMark(selected = index == selectedIndex)
                    }
                }
            }
            item(key = "end") { Spacer(Modifier.height(Spacing.endSpacer)) }
        }

        if (showAd) {
            ComposeNativeAdFrame(
                modifier = Modifier.fillMaxWidth(),
                adPlacement = AdsPlacementHolder.NATIVE,
                onReloadReady = { reloadNativeAd = it },
            )
        }
    }
}


/**
 * Title + one-line hint, optional back arrow, and a compact accent pill ("Next") that confirms
 * the choice. The pill is always laid out (only its alpha changes), so the title never reflows,
 * and it has no glow: a blurred shadow under a fading/scaling layer smears mid-animation.
 */
@Composable
private fun PickerHeader(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)?,
    showConfirm: Boolean,
    onConfirm: () -> Unit,
) {
    val c = AppTheme.colors
    val confirmAlpha by animateFloatAsState(if (showConfirm) 1f else 0f, motionStd(), label = "pickerConfirm")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (onBack != null) Spacing.space8 else Spacing.screenHorizontal,
                end = Spacing.screenHorizontal,
                top = Spacing.space16,
                bottom = Spacing.space16,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            AppIconButton(
                iconRes = R.drawable.ic_arrow_left,
                contentDescription = stringResource(R.string.back),
                onClick = onBack,
                tint = c.textPrimary,
            )
            Spacer(Modifier.width(Spacing.space4))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppTheme.type.title,
                color = c.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.space4))
            Text(
                text = subtitle,
                style = AppTheme.type.caption,
                color = c.textSecondary,
                maxLines = 2,
            )
        }
        Spacer(Modifier.width(Spacing.space12))
        // 36dp pill (TextActionButton height), 48dp touch target via the vertical padding.
        Box(
            modifier = Modifier
                .padding(vertical = 6.dp)
                .graphicsLayer { alpha = confirmAlpha }
                .height(Sizes.textButton)
                .clip(AppShapes.pill)
                .background(c.accent)
                .clickable(enabled = showConfirm, role = Role.Button, onClick = onConfirm)
                .padding(start = Spacing.space16, end = Spacing.space12),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.next),
                    style = AppTheme.type.body.semiBold,
                    color = c.onAccent,
                    maxLines = 1,
                )
                Spacer(Modifier.width(Spacing.space4))
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = c.onAccent,
                    modifier = Modifier.size(Sizes.iconSm),
                )
            }
        }
    }
}
