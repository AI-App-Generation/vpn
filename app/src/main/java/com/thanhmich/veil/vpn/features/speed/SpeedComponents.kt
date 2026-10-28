package com.thanhmich.veil.vpn.features.speed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thanhmich.veil.vpn.R
import com.thanhmich.veil.vpn.data.domain.SpeedPhase
import com.thanhmich.veil.vpn.data.domain.SpeedResult
import com.thanhmich.veil.vpn.ui.components.SurfaceCard
import com.thanhmich.veil.vpn.ui.theme.AppTheme
import com.thanhmich.veil.vpn.ui.theme.Sizes
import com.thanhmich.veil.vpn.ui.theme.Spacing
import com.thanhmich.veil.vpn.ui.theme.motionFast
import com.thanhmich.veil.vpn.ui.theme.motionStd
import com.thanhmich.veil.vpn.ui.theme.tnum
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private val gaugeTicks = doubleArrayOf(0.0, 1.0, 5.0, 10.0, 20.0, 50.0, 100.0, 250.0, 500.0)

internal fun gaugeFraction(value: Double): Float {
    val safe = value.coerceIn(gaugeTicks.first(), gaugeTicks.last())
    val segment = (0 until gaugeTicks.lastIndex).firstOrNull { safe <= gaugeTicks[it + 1] } ?: 7
    val local = (safe - gaugeTicks[segment]) / (gaugeTicks[segment + 1] - gaugeTicks[segment])
    return ((segment + local) / 8.0).toFloat()
}

@Composable
fun SpeedGauge(
    value: Double,
    unit: GaugeUnit,
    phase: SpeedPhase?,
    status: SpeedStatus,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    // Engine emits every 200 ms. A 300 ms tween is restarted by every sample and can
    // therefore remain permanently behind the displayed measurement.
    val shown by animateFloatAsState(value.toFloat(), motionFast(), label = "gaugeValue")
    // Animate one source of truth. The gauge scale is non-linear, so animating the raw
    // value and its mapped fraction separately makes the arc disagree with the number.
    val fraction = gaugeFraction(shown.toDouble())
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = minOf(size.width / 2f - 28.dp.toPx(), size.height * 0.34f)
            val center = Offset(size.width / 2f, size.height * 0.48f)
            val stroke = 14.dp.toPx()
            val arcRect = Rect(center - Offset(radius, radius), Size(radius * 2f, radius * 2f))
            val gaugeStroke = Stroke(width = stroke, cap = StrokeCap.Round)
            val trackPath = Path().apply { addArc(arcRect, 150f, 240f) }
            drawPath(trackPath, c.surfaceMuted, style = gaugeStroke)
            if (unit != GaugeUnit.Ms && fraction > 0f) {
                val progressPath = Path().apply { addArc(arcRect, 150f, 240f * fraction) }
                drawPath(
                    path = progressPath,
                    brush = Brush.sweepGradient(listOf(c.accentContainer, c.accent), center),
                    style = gaugeStroke,
                )
            }
            val paint = android.graphics.Paint().apply {
                color = c.textTertiary.toArgb()
                textSize = 12.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                isAntiAlias = true
            }
            val baselineOffset = -(paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
            gaugeTicks.forEachIndexed { index, tick ->
                val angle = Math.toRadians((150.0 + index * 30.0))
                val labelRadius = radius + 18.dp.toPx()
                drawContext.canvas.nativeCanvas.drawText(
                    tick.toInt().toString(),
                    center.x + cos(angle).toFloat() * labelRadius,
                    center.y + sin(angle).toFloat() * labelRadius + baselineOffset,
                    paint,
                )
            }
            val angle = Math.toRadians((150.0 + 240.0 * fraction))
            val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            val normal = Offset(-direction.y, direction.x)
            val needle = Path().apply {
                moveTo(center.x + normal.x * 4.dp.toPx(), center.y + normal.y * 4.dp.toPx())
                lineTo(center.x + direction.x * radius * 0.36f, center.y + direction.y * radius * 0.36f)
                lineTo(center.x - normal.x * 4.dp.toPx(), center.y - normal.y * 4.dp.toPx())
                close()
            }
            drawPath(needle, c.textPrimary)
            drawCircle(c.surface, 8.dp.toPx(), center)
            drawCircle(c.textPrimary, 8.dp.toPx(), center, style = Stroke(3.dp.toPx()))
        }
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (unit == GaugeUnit.Ms) String.format(Locale.US, "%.0f", shown) else String.format(Locale.US, "%.1f", shown),
                style = AppTheme.type.display.tnum,
                color = if (status == SpeedStatus.Idle || status is SpeedStatus.Error) c.textTertiary else c.textPrimary,
                maxLines = 1,
            )
            Text(if (unit == GaugeUnit.Ms) stringResource(R.string.speed_unit_ms) else stringResource(R.string.speed_unit_mbps), style = AppTheme.type.caption, color = c.textSecondary, maxLines = 1)
            Text(
                text = when (phase) {
                    SpeedPhase.Ping -> stringResource(R.string.speed_phase_ping)
                    SpeedPhase.Download -> stringResource(R.string.speed_phase_download)
                    SpeedPhase.Upload -> stringResource(R.string.speed_phase_upload)
                    null -> if (status is SpeedStatus.Done) stringResource(R.string.speed_phase_done) else stringResource(R.string.speed_phase_ready)
                },
                style = AppTheme.type.caption,
                color = if (status == SpeedStatus.Idle || status is SpeedStatus.Error) c.textTertiary else c.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SpeedHistoryItem(
    result: SpeedResult,
    modifier: Modifier = Modifier,
    animateEntrance: Boolean = false,
) {
    val c = AppTheme.colors
    val formatter = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault())
    val visibility = remember(result.timestamp) { MutableTransitionState(!animateEntrance) }
    LaunchedEffect(result.timestamp, animateEntrance) { visibility.targetState = true }
    AnimatedVisibility(visibleState = visibility, enter = fadeIn(motionStd())) {
        SurfaceCard(modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.space16)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(Sizes.iconContainer).background(if (result.viaVpn) c.accentContainer else c.surfaceMuted, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(if (result.viaVpn) R.drawable.ic_shield_check_filled else R.drawable.ic_world), null, Modifier.size(Sizes.iconSm), tint = if (result.viaVpn) c.accent else c.textSecondary)
                }
                Spacer(Modifier.width(Spacing.space12))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.speed_history_rates, result.downMbps, result.upMbps), style = AppTheme.type.headline.tnum, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.speed_history_route_time, result.vpnCountry ?: stringResource(R.string.speed_no_vpn), formatter.format(Date(result.timestamp))), style = AppTheme.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(Spacing.space8))
                Text(stringResource(R.string.ping_ms, result.pingMs), style = AppTheme.type.caption.tnum, color = c.textSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
fun EmptySpeedHistory(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.speed_history_empty),
        modifier = modifier.fillMaxWidth().padding(Spacing.space24),
        color = AppTheme.colors.textTertiary,
        style = AppTheme.type.caption,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
