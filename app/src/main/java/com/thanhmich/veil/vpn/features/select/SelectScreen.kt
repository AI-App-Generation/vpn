package com.thanhmich.veil.vpn.features.select

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thanhmich.veil.vpn.LogScreenView
import com.thanhmich.veil.vpn.R
import com.thanhmich.veil.vpn.ui.components.PrimaryButton
import com.thanhmich.veil.vpn.ui.components.SelectedMark
import com.thanhmich.veil.vpn.ui.theme.AppShapes
import com.thanhmich.veil.vpn.ui.theme.AppTheme
import com.thanhmich.veil.vpn.ui.theme.LocalMotionEnabled
import com.thanhmich.veil.vpn.ui.theme.Motion
import com.thanhmich.veil.vpn.ui.theme.Sizes
import com.thanhmich.veil.vpn.ui.theme.Spacing
import com.thanhmich.veil.vpn.ui.theme.motionFast
import com.thanhmich.veil.vpn.ui.theme.motionStd
import com.thanhmich.veil.vpn.ui.theme.regular
import com.thanhmich.veil.vpn.ui.theme.semiBold
import com.thanhmich.veil.vpn.ui.theme.shadowCard
import kotlinx.coroutines.delay

/** One reason for using a VPN, with its clay sticker (generated at build time). */
private enum class UseCase(@StringRes val label: Int, @DrawableRes val sticker: Int) {
    PUBLIC_WIFI(R.string.select_opt_public_wifi, R.drawable.select_public_wifi),
    PRIVACY(R.string.select_opt_privacy, R.drawable.select_privacy),
    STREAMING(R.string.select_opt_streaming, R.drawable.select_streaming),
    GAMING(R.string.select_opt_gaming, R.drawable.select_gaming),
    REMOTE_WORK(R.string.select_opt_remote_work, R.drawable.select_remote_work),
    TRAVEL(R.string.select_opt_travel, R.drawable.select_travel),
}

/**
 * "What will you use Veil VPN for?" — multi-select step between Language and Onboarding.
 * Purely a UX step: answers stay on the device and are never sent anywhere. At least one
 * option must be ticked to continue.
 */
@Composable
fun SelectScreen(onNext: () -> Unit = {}) {
    LogScreenView("Select")

    val c = AppTheme.colors
    // Enum names, so the selection survives process death.
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.screenHorizontal),
            ) {
                Column(Modifier.padding(top = Spacing.space24, bottom = Spacing.space20)) {
                    Text(
                        text = stringResource(R.string.select_title),
                        style = AppTheme.type.title,
                        color = c.textPrimary,
                    )
                    Spacer(Modifier.height(Spacing.space8))
                    Text(
                        text = stringResource(R.string.select_subtitle),
                        style = AppTheme.type.body.regular,
                        color = c.textSecondary,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.space12)) {
                    UseCase.entries.chunked(2).forEachIndexed { rowIndex, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space12)) {
                            row.forEachIndexed { columnIndex, option ->
                                val isSelected = option.name in selected
                                UseCaseCard(
                                    modifier = Modifier.weight(1f),
                                    option = option,
                                    selected = isSelected,
                                    appearIndex = rowIndex * 2 + columnIndex,
                                    onClick = {
                                        selected = if (isSelected) selected - option.name else selected + option.name
                                    },
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.space24))
            }

            // Soft edge where the cards scroll under the CTA, instead of a hard cut.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.dp)
                    .wrapContentHeight(Alignment.Bottom, unbounded = true)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(Spacing.space24)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, c.bg)))
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.bg)
                    .navigationBarsPadding()
                    .padding(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, bottom = Spacing.space16),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val counterSpec = motionFast<Float>()
                AnimatedContent(
                    targetState = selected.size,
                    transitionSpec = { fadeIn(counterSpec) togetherWith fadeOut(counterSpec) },
                    label = "selectCounter",
                ) { count ->
                    Text(
                        text = if (count == 0) {
                            stringResource(R.string.select_hint)
                        } else {
                            stringResource(R.string.select_selected_count, count)
                        },
                        style = AppTheme.type.caption.semiBold,
                        color = if (count == 0) c.textTertiary else c.accent,
                    )
                }
                Spacer(Modifier.height(Spacing.space12))
                PrimaryButton(
                    text = stringResource(R.string.select_continue),
                    enabled = selected.isNotEmpty(),
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun UseCaseCard(
    modifier: Modifier,
    option: UseCase,
    selected: Boolean,
    appearIndex: Int,
    onClick: () -> Unit,
) {
    val c = AppTheme.colors
    val motion = LocalMotionEnabled.current

    // Staggered entrance (stagger_step), played once.
    val appear = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!motion) return@LaunchedEffect
        delay(appearIndex.coerceAtMost(Motion.STAGGER_MAX) * Motion.STAGGER_STEP * 2L)
        appear.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
    }

    // Sticker pops a little when picked.
    val stickerScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = if (motion) spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium) else snap(),
        label = "stickerScale",
    )
    val container by animateColorAsState(if (selected) c.accentContainer else c.surface, motionStd(), label = "cardBg")
    val border by animateColorAsState(if (selected) c.accent else Color.Transparent, motionFast(), label = "cardBorder")

    Box(
        modifier = modifier
            .graphicsLayer {
                // ModulateAlpha: no offscreen buffer, so the glow/shadow outside the bounds isn't clipped while fading.
                alpha = appear.value
                compositingStrategy = CompositingStrategy.ModulateAlpha
                translationY = (1f - appear.value) * 24.dp.toPx()
            }
            .shadowCard(AppShapes.lg)
            .clip(AppShapes.lg)
            .background(container)
            .border(Sizes.borderSelected, border, AppShapes.lg)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.space12),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(option.sticker),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .padding(top = Spacing.space8)
                    .size(96.dp)
                    .graphicsLayer {
                        scaleX = stickerScale
                        scaleY = stickerScale
                    },
            )
            Spacer(Modifier.height(Spacing.space8))
            Text(
                text = stringResource(option.label),
                style = AppTheme.type.body.semiBold,
                color = if (selected) c.onAccentContainer else c.textPrimary,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SelectedMark(
            selected = selected,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Spacing.space12),
        )
    }
}
