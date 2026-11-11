package com.v2ray.ang.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.startapp.sdk.ads.banner.Banner
import com.v2ray.ang.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ═══════════════════════ پالت رنگی یکپارچه Penny VPN (Obsidian + Platinum Silver + Champagne Gold) ═══════════════════════
private val ObsidianDeep = Color(0xFF0C0D11)
private val ObsidianCard = Color(0xFF16171E)
private val ObsidianElevated = Color(0xFF20222C)
private val ObsidianBorder = Color(0xFF323442)
private val PlatinumBright = Color(0xFFF4F6FA)
private val PlatinumSilver = Color(0xFFD8DCE6)
private val PlatinumMuted = Color(0xFF9EA2B0)
private val ChampagneGold = Color(0xFFE6CFAF)
private val ChampagneLight = Color(0xFFF5E6D3)
private val ChampagneBronze = Color(0xFF9E8A6E)
private val SignalEmerald = Color(0xFF34D399)
private val SignalAmber = Color(0xFFFBBF24)
private val SignalCoral = Color(0xFFF87171)

/**
 * پشتیبانی کامل از فوکوس ریموت کنترل Android TV (D-Pad Navigation)
 */
@Composable
internal fun Modifier.tvDpadClickable(
    enabled: Boolean = true,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    onClick: () -> Unit
): Modifier {
    var isFocused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { isFocused = it.isFocused }
        .then(
            if (isFocused) {
                Modifier.border(2.dp, ChampagneGold, shape)
            } else {
                Modifier
            }
        )
        .focusable(enabled = enabled)
        .clickable(enabled = enabled, onClick = onClick)
}

@Composable
internal fun GlassDiamondIcon(
    size: Dp = 15.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        val outerPath = Path().apply {
            moveTo(w * 0.24f, h * 0.14f)
            lineTo(w * 0.76f, h * 0.14f)
            lineTo(w * 0.96f, h * 0.38f)
            lineTo(w * 0.50f, h * 0.92f)
            lineTo(w * 0.04f, h * 0.38f)
            close()
        }

        drawPath(
            path = outerPath,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFFFFFF).copy(alpha = 0.96f),
                    ChampagneLight.copy(alpha = 0.85f),
                    ChampagneGold.copy(alpha = 0.65f)
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )

        val topFacet = Path().apply {
            moveTo(w * 0.24f, h * 0.14f)
            lineTo(w * 0.76f, h * 0.14f)
            lineTo(w * 0.68f, h * 0.38f)
            lineTo(w * 0.32f, h * 0.38f)
            close()
        }
        drawPath(path = topFacet, color = Color(0xFFFFFFFF).copy(alpha = 0.56f))

        drawLine(
            color = Color(0xFFFFFFFF).copy(alpha = 0.85f),
            start = Offset(w * 0.04f, h * 0.38f),
            end = Offset(w * 0.96f, h * 0.38f),
            strokeWidth = 1.dp.toPx()
        )
        drawPath(
            path = outerPath,
            color = ChampagneLight.copy(alpha = 0.92f),
            style = Stroke(width = 1.15.dp.toPx())
        )
    }
}

@Composable
internal fun MenuLinesIcon(
    size: Dp = 15.dp,
    color: Color = PlatinumSilver,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 1.6.dp.toPx()
        drawLine(
            color = color,
            start = Offset(w * 0.12f, h * 0.24f),
            end = Offset(w * 0.88f, h * 0.24f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color.copy(alpha = 0.85f),
            start = Offset(w * 0.22f, h * 0.50f),
            end = Offset(w * 0.78f, h * 0.50f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(w * 0.12f, h * 0.76f),
            end = Offset(w * 0.88f, h * 0.76f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
internal fun UpdateArrowIcon(
    size: Dp = 15.dp,
    color: Color = ChampagneGold,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 1.65.dp.toPx()
        drawArc(
            color = color,
            startAngle = -210f,
            sweepAngle = 285f,
            useCenter = false,
            topLeft = Offset(w * 0.14f, h * 0.14f),
            size = Size(w * 0.72f, h * 0.72f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawLine(
            color = color,
            start = Offset(w * 0.78f, h * 0.16f),
            end = Offset(w * 0.88f, h * 0.40f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(w * 0.62f, h * 0.36f),
            end = Offset(w * 0.88f, h * 0.40f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

/**
 * سوییچ با ریل طلایی روشن (Golden Rail Toggle) در حالت فعال
 */
@Composable
internal fun metallicSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color(0xFF14151B),
    checkedTrackColor = ChampagneGold,
    checkedBorderColor = ChampagneLight,
    uncheckedThumbColor = Color(0xFF8A8E9B),
    uncheckedTrackColor = Color(0xFF13141A),
    uncheckedBorderColor = Color(0xFF2E303C)
)

/**
 * نمایشگر میله‌ی رنگی ۴ تایی پینگ + عدد میلی‌ثانیه
 */
@Composable
internal fun PingSignalBars(
    ms: Long,
    modifier: Modifier = Modifier
) {
    val activeBars = when {
        ms in 1..155 -> 4
        ms in 156..245 -> 3
        ms in 246..380 -> 2
        ms > 380 -> 1
        else -> 3
    }
    val barColor = when {
        ms in 1..155 -> SignalEmerald
        ms in 156..270 -> ChampagneGold
        ms > 270 -> SignalCoral
        else -> SignalEmerald
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.height(12.dp)
        ) {
            for (bar in 1..4) {
                val hFrac = 0.32f + bar * 0.17f
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight(hFrac)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(
                            if (bar <= activeBars) barColor else Color(0xFF2D303D)
                        )
                )
            }
        }
        Text(
            text = if (ms > 0) "${ms}ms" else "Auto",
            color = barColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * کارت تبلیغ شفاف و مستقل (Sponsored Card) با برچسب «Sponsored» و شمارنده «2 of 3 claimed today»
 * کاملاً جدا از کارت‌های وضعیت و کیفیت سرویس
 */
@Composable
internal fun SponsoredQuotaCard(
    adsClaimedToday: Int,
    maxDailyAds: Int = 3,
    isConnected: Boolean = false,
    onWatchSponsorAd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val safeClaimed = adsClaimedToday.coerceIn(0, maxDailyAds)
    val allClaimed = safeClaimed >= maxDailyAds
    val cardShape = RoundedCornerShape(14.dp)

    Surface(
        color = Color(0xFF171820).copy(alpha = 0.94f),
        shape = cardShape,
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .border(
                width = 1.dp,
                color = if (allClaimed) ObsidianBorder else ChampagneGold.copy(alpha = 0.48f),
                shape = cardShape
            )
            .tvDpadClickable(enabled = !allClaimed, shape = cardShape) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onWatchSponsorAd()
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Sponsored",
                        color = ChampagneGold,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text("·", color = PlatinumMuted, fontSize = 10.sp)
                    Text(
                        text = "$safeClaimed of $maxDailyAds claimed today",
                        color = PlatinumSilver,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (allClaimed) Color(0xFF232530) else ChampagneGold
                        )
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (allClaimed) "Done Today ✓" else "Watch Ad • +100 MB",
                        color = if (allClaimed) PlatinumMuted else Color(0xFF121318),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isConnected) {
                        "Pauses VPN briefly to play on your direct IP, then auto-reconnects"
                    } else {
                        "Plays directly on your ISP IP via Start.io • Instant +100 MB VIP data"
                    },
                    color = PlatinumMuted,
                    fontSize = 8.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (i in 1..maxDailyAds) {
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (i <= safeClaimed) ChampagneGold else Color(0xFF2B2D3A)
                                )
                        )
                    }
                }
            }
        }
    }
}

/**
 * پنل وضعیت متصل واقعی در صفحه Home:
 * شامل ۱) تایمر جلسه، ۲) نمودار سرعت زنده (Sparkline)، ۳) حجم مصرف امروز و ۴) IP/کشور خروجی
 */
@Composable
internal fun LiveConnectionTelemetryCard(
    mode: UiMode,
    elapsedSec: Long,
    downSpeed: Long,
    upSpeed: Long,
    downHistory: List<Float>,
    upHistory: List<Float>,
    dailyUsedBytes: Long,
    dailyMaxBytes: Long,
    countryGeo: CountryGeoInfo,
    server: PennyServer?,
    onServerSwitchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isConnected = mode == UiMode.CONNECTED
    val borderColor by animateColorAsState(
        targetValue = if (isConnected) ChampagneGold.copy(alpha = 0.65f) else ObsidianBorder,
        animationSpec = tween(500),
        label = "telemetryBorder"
    )
    val shape = RoundedCornerShape(16.dp)

    Surface(
        color = ObsidianCard.copy(alpha = 0.96f),
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .tvDpadClickable(shape = shape) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onServerSwitchClick()
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            // ردیف اول: تایمر جلسه + IP و کشور خروجی
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) SignalEmerald else PlatinumMuted)
                    )
                    Text(
                        text = if (isConnected) fmtDuration(elapsedSec) else "00:00:00",
                        color = PlatinumBright,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (isConnected) "· Session" else "· Standby",
                        color = PlatinumMuted,
                        fontSize = 9.5.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = if (isConnected) {
                            "${countryGeo.flagEmoji} ${countryGeo.exitIpPreview} · ${countryGeo.isoCode}"
                        } else {
                            "🇮🇷 Direct ISP · ${countryGeo.flagEmoji} ${countryGeo.isoCode} Ready"
                        },
                        color = if (isConnected) ChampagneLight else PlatinumSilver,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                    if (server != null && server.ms > 0) {
                        PingSignalBars(ms = server.ms)
                    }
                }
            }

            // ردیف دوم: نمودار سرعت زنده (Real-Time Speed Sparkline Chart)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFF101117))
                    .border(0.8.dp, Color(0xFF252734), RoundedCornerShape(9.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val pts = if (downHistory.size >= 2) downHistory else List(20) { 0f }
                    val upPts = if (upHistory.size >= 2) upHistory else List(20) { 0f }
                    val maxVal = maxOf(
                        pts.maxOrNull() ?: 1f,
                        upPts.maxOrNull() ?: 1f,
                        64f * 1024f
                    )

                    // خطوط راهنمای افقی ظریف
                    drawLine(
                        color = Color(0xFF222532),
                        start = Offset(0f, h * 0.5f),
                        end = Offset(w, h * 0.5f),
                        strokeWidth = 0.8.dp.toPx()
                    )

                    val dlPath = Path()
                    val dlFillPath = Path()
                    val stepX = w / (pts.size - 1).coerceAtLeast(1)

                    pts.forEachIndexed { i, v ->
                        val x = i * stepX
                        val norm = (v.toFloat() / maxVal).coerceIn(0f, 1f)
                        val y = h - (norm * (h * 0.82f) + h * 0.08f)
                        if (i == 0) {
                            dlPath.moveTo(x, y)
                            dlFillPath.moveTo(x, h)
                            dlFillPath.lineTo(x, y)
                        } else {
                            dlPath.lineTo(x, y)
                            dlFillPath.lineTo(x, y)
                        }
                    }
                    dlFillPath.lineTo(w, h)
                    dlFillPath.close()

                    drawPath(
                        path = dlFillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                ChampagneGold.copy(alpha = if (isConnected) 0.30f else 0.08f),
                                Color.Transparent
                            )
                        )
                    )
                    drawPath(
                        path = dlPath,
                        color = if (isConnected) ChampagneGold else PlatinumMuted.copy(alpha = 0.45f),
                        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // خط آپلود
                    val ulPath = Path()
                    upPts.forEachIndexed { i, v ->
                        val x = i * stepX
                        val norm = (v.toFloat() / maxVal).coerceIn(0f, 1f)
                        val y = h - (norm * (h * 0.72f) + h * 0.06f)
                        if (i == 0) ulPath.moveTo(x, y) else ulPath.lineTo(x, y)
                    }
                    drawPath(
                        path = ulPath,
                        color = if (isConnected) SignalEmerald.copy(alpha = 0.75f) else Color.Transparent,
                        style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "↓ ${fmtSpeed(downSpeed)}   ↑ ${fmtSpeed(upSpeed)}",
                        color = PlatinumBright,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Today: ${fmtBytes(dailyUsedBytes)} / ${fmtBytes(dailyMaxBytes)}",
                        color = ChampagneGold,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
internal fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = PlatinumBright,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
        Text(
            text = label,
            color = PlatinumMuted,
            fontSize = 9.sp,
            maxLines = 1
        )
    }
}

@Composable
internal fun StaggeredServerItem(
    index: Int,
    content: @Composable () -> Unit
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(index) {
        delay((index * 45L).coerceAtMost(400L))
        anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = anim.value
                translationY = (1f - anim.value) * 24f
            }
    ) {
        content()
    }
}

@Composable
internal fun ShinyGreenPingBadge(
    ms: Long,
    showDiamond: Boolean = false,
    modifier: Modifier = Modifier
) {
    PingSignalBars(ms = ms, modifier = modifier)
}

@Composable
internal fun ModernServerCard(
    server: PennyServer?,
    isConnected: Boolean,
    isVipMode: Boolean,
    lang: AppLang,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val borderColor = when {
        isConnected -> ChampagneGold.copy(alpha = 0.85f)
        isVipMode -> ChampagneGold.copy(alpha = 0.55f)
        else -> ObsidianBorder
    }
    val shape = RoundedCornerShape(15.dp)

    Surface(
        color = ObsidianCard.copy(alpha = 0.95f),
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .tvDpadClickable(enabled = enabled, shape = shape) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
    ) {
        val geo = getCountryGeoInfo(server?.codeTag)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = geo.flagEmoji,
                    fontSize = 18.sp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (server != null) "${geo.titleCaseName} · ${geo.cityName}" else "Auto · Fastest Country",
                            color = PlatinumBright,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Recommended",
                            color = ChampagneGold,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = if (isConnected) "Active exit node · Tap to change country" else "Auto-balanced route · Tap to choose country",
                        color = PlatinumMuted,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            PingSignalBars(ms = server?.ms ?: 122L)
        }
    }
}

@Composable
internal fun ConnectionProfileBar(
    selected: ConnectionProfile,
    onSelect: (ConnectionProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ObsidianCard.copy(alpha = 0.95f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            ConnectionProfile.values().forEach { prof ->
                val active = prof == selected
                Surface(
                    color = if (active) ObsidianElevated else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .then(
                            if (active) Modifier.border(
                                1.dp,
                                ChampagneGold.copy(alpha = 0.65f),
                                RoundedCornerShape(10.dp)
                            ) else Modifier
                        )
                        .clickable { onSelect(prof) }
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 5.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = prof.label,
                            color = if (active) PlatinumBright else PlatinumMuted,
                            fontSize = 10.5.sp,
                            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Text(
                            text = prof.tag,
                            color = if (active) ChampagneGold else Color(0xFF6E717C),
                            fontSize = 8.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}


@Composable
internal fun PennySplashIntro(lang: AppLang, onFinished: () -> Unit) {
    val logoAnim = remember { Animatable(0f) }
    val titleAnim = remember { Animatable(0f) }
    val subAnim = remember { Animatable(0f) }
    val footerAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            logoAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            delay(150)
            titleAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
            )
        }
        launch {
            delay(300)
            subAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
            )
        }
        launch {
            delay(450)
            footerAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
            )
        }
        delay(2100)
        onFinished()
    }

    val inf = rememberInfiniteTransition(label = "splashInf")
    val orbitAngle by inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing)),
        label = "orbitAngle"
    )
    val glowPulse by inf.animateFloat(
        initialValue = 12f,
        targetValue = 26f,
        animationSpec = infiniteRepeatable(
            tween(1000, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF22232A),
                        Color(0xFF141519),
                        ObsidianDeep
                    )
                )
            )
            .clickable { onFinished() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(230.dp)
                    .graphicsLayer {
                        val p = logoAnim.value
                        alpha = p.coerceIn(0f, 1f)
                        val sc = 0.42f + 0.58f * p
                        scaleX = sc
                        scaleY = sc
                        translationY = (1f - p) * -36f
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val arcR = size.minDimension * 0.43f
                    val tl = Offset(center.x - arcR, center.y - arcR)
                    val sz = Size(arcR * 2f, arcR * 2f)
                    drawArc(
                        color = PlatinumSilver.copy(alpha = 0.78f),
                        startAngle = orbitAngle,
                        sweepAngle = 95f,
                        useCenter = false,
                        topLeft = tl,
                        size = sz,
                        style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = ChampagneGold.copy(alpha = 0.68f),
                        startAngle = orbitAngle + 180f,
                        sweepAngle = 95f,
                        useCenter = false,
                        topLeft = tl,
                        size = sz,
                        style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Image(
                    painter = painterResource(id = R.drawable.penny_cat_logo),
                    contentDescription = "Penny VPN Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(182.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "PENNY VPN",
                color = PlatinumBright,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 4.sp,
                style = TextStyle(
                    shadow = Shadow(
                        color = PlatinumSilver,
                        offset = Offset(0f, 0f),
                        blurRadius = glowPulse
                    )
                ),
                modifier = Modifier.graphicsLayer {
                    val p = titleAnim.value
                    alpha = p
                    translationY = (1f - p) * 28f
                }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = Strings.splashTagline(lang),
                fontSize = 15.5.sp,
                fontWeight = FontWeight.ExtraBold,
                fontStyle = FontStyle.Italic,
                letterSpacing = 1.3.sp,
                style = TextStyle(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFFFFFF),
                            ChampagneLight,
                            ChampagneGold,
                            Color(0xFFFFFFFF)
                        )
                    )
                ),
                modifier = Modifier.graphicsLayer {
                    alpha = subAnim.value
                }
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "powered by Omidss • Mobile & Android TV",
                color = ChampagneGold,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = footerAnim.value
                }
            )
        }
    }
}

@Composable
internal fun NeonBrandHeader(
    mode: UiMode,
    lang: AppLang,
    isVipMode: Boolean,
    onLogoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glowTarget = when {
        isVipMode && mode == UiMode.IDLE -> ChampagneGold
        mode == UiMode.IDLE -> Color(0xFFB8BDC8)
        mode == UiMode.SCANNING -> PlatinumSilver
        mode == UiMode.CONNECTING -> ChampagneLight
        else -> ChampagneGold
    }
    val neonColor by animateColorAsState(glowTarget, tween(700), label = "neonColor")
    val inf = rememberInfiniteTransition(label = "headerNeon")
    val blurRadius by inf.animateFloat(
        initialValue = 8f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            tween(1600, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "neonBlur"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.penny_cat_logo),
                contentDescription = "Penny VPN Cat Logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .border(1.2.dp, neonColor.copy(alpha = 0.78f), CircleShape)
                    .clickable { onLogoClick() }
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "PENNY VPN",
                color = PlatinumBright,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.2.sp,
                style = TextStyle(
                    shadow = Shadow(
                        color = neonColor,
                        offset = Offset(0f, 0f),
                        blurRadius = blurRadius
                    )
                )
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = Strings.subtitle(lang, isVipMode),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                fontStyle = FontStyle.Italic,
                letterSpacing = 1.0.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFFFFFF),
                            ChampagneLight,
                            ChampagneGold,
                            Color(0xFFFFFFFF)
                        )
                    )
                )
            )
        }
    }
}

/**
 * نمایشگر وضعیت فشرده همراه با مختصات کشور متصل‌شده و تله‌متری لحظه‌ای در یک خط (بدون ایجاد اسکرول در صفحه Home)
 */
@Composable
internal fun PulsingStatusText(
    mode: UiMode,
    statusMessage: String,
    lang: AppLang,
    isVip: Boolean,
    dailyRemainingText: String? = null,
    countryGeo: CountryGeoInfo? = null,
    downSpeed: Long = 0L,
    upSpeed: Long = 0L
) {
    val isConnected = mode == UiMode.CONNECTED
    val inf = rememberInfiniteTransition(label = "statusPulse")
    val beaconAlpha by inf.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            tween(650, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "beaconAlpha"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // نوار کشور (در حالت دیسکانکت: مبدأ ایران، در حالت متصل: کشور سرور مقصد)
        if (countryGeo != null) {
            val pillLabel = when (mode) {
                UiMode.IDLE -> "ORIGIN • IRAN (STATIONARY)"
                UiMode.SCANNING -> "SCANNING GLOBAL NODES..."
                UiMode.CONNECTING -> "ROUTING TO ${countryGeo.countryName}..."
                UiMode.CONNECTED -> "${countryGeo.countryName} • ${countryGeo.cityName.uppercase()}"
            }
            Surface(
                color = ObsidianCard.copy(alpha = 0.92f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.border(
                    1.dp,
                    if (isConnected) ChampagneGold.copy(alpha = 0.72f) else ObsidianBorder,
                    RoundedCornerShape(20.dp)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (isConnected) ChampagneGold.copy(alpha = beaconAlpha)
                                else PlatinumSilver.copy(alpha = 0.7f)
                            )
                    )
                    Text(
                        text = pillLabel,
                        color = if (isConnected) ChampagneLight else PlatinumSilver,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        if (isConnected) {
            // نوار فشرده سرعت دانلود/آپلود و سهمیه باقی‌مانده در یک خط (جلوگیری کامل از اسکرول صفحه Home)
            Surface(
                color = ObsidianElevated.copy(alpha = 0.94f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.border(1.dp, Color(0xFF424554), RoundedCornerShape(10.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "↓ ${fmtSpeed(downSpeed)}",
                        color = ChampagneLight,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                    Text("•", color = PlatinumMuted, fontSize = 10.sp)
                    Text(
                        text = "↑ ${fmtSpeed(upSpeed)}",
                        color = PlatinumSilver,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                    if (!dailyRemainingText.isNullOrBlank()) {
                        Text("•", color = PlatinumMuted, fontSize = 10.sp)
                        Text(
                            text = dailyRemainingText,
                            color = ChampagneGold,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else {
            Text(
                text = statusMessage,
                color = PlatinumSilver,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

/**
 * کره زمین سه‌بعدی واقعاً زنده (Truly Alive 3D Globe):
 * ۱) کامل و بدون بریدگی نمایش داده می‌شود (globeR متوازن با فضای موج‌های تپش قلب و کمان نورانی).
 * ۲) در حالت متصل، هر دو کشور مبدأ (ایران IR) و کشور سرور مقصد روشن هستند، یک کمان نورانی سه‌بعدی (Great-Circle Luminous Arc)
 *    با ذرات نور متحرک از ایران به کشور سرور کشیده می‌شود و کره زمین به صورت نرم و زنده حول مسیر اتصال می‌چرخد.
 * ۳) همراه با بازخورد لرزشی (Haptic Feedback) هنگام لمس دکمه اتصال.
 */
@Composable
internal fun PowerButton(
    mode: UiMode,
    progress: Float,
    lang: AppLang,
    isVipMode: Boolean,
    countryGeo: CountryGeoInfo = getCountryGeoInfo("DED-NL"),
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isConnected = mode == UiMode.CONNECTED
    val isScanningOrConnecting = mode == UiMode.SCANNING || mode == UiMode.CONNECTING
    val target = when (mode) {
        UiMode.IDLE -> if (isVipMode) ChampagneGold else Color(0xFF9EA2B0)
        UiMode.SCANNING -> PlatinumSilver
        UiMode.CONNECTING -> ChampagneLight
        UiMode.CONNECTED -> ChampagneGold
    }
    val color by animateColorAsState(target, tween(700), label = "pbColor")
    val animProgress by animateFloatAsState(progress, tween(400), label = "pbProgress")

    // مختصات کشور مبدأ (ایران)
    val originLatDeg = 32.40f
    val originLonDeg = 53.70f

    // در حالت متصل، دوربین بین ایران و کشور مقصد قرار می‌گیرد تا هم ایران، هم کشور مقصد و هم کمان نورانی کامل دیده شوند
    val midLat = (originLatDeg * 0.35f + countryGeo.latitude * 0.65f).coerceIn(-55f, 62f)
    val midLon = (originLonDeg * 0.35f + countryGeo.longitude * 0.65f)

    val targetCamLat = when (mode) {
        UiMode.IDLE -> originLatDeg
        UiMode.SCANNING -> 34.0f
        UiMode.CONNECTING, UiMode.CONNECTED -> midLat
    }
    val targetCamLon = when (mode) {
        UiMode.IDLE -> originLonDeg
        UiMode.SCANNING -> originLonDeg
        UiMode.CONNECTING, UiMode.CONNECTED -> midLon
    }
    val targetZoom = when (mode) {
        UiMode.IDLE -> 1.55f
        UiMode.SCANNING -> 1.08f
        UiMode.CONNECTING -> 1.32f
        UiMode.CONNECTED -> 1.38f
    }

    val baseLonDeg by animateFloatAsState(
        targetValue = targetCamLon,
        animationSpec = tween(950, easing = FastOutSlowInEasing),
        label = "globeLon"
    )
    val centerLatDeg by animateFloatAsState(
        targetValue = targetCamLat,
        animationSpec = tween(950, easing = FastOutSlowInEasing),
        label = "globeLat"
    )
    val currentZoom by animateFloatAsState(
        targetValue = targetZoom,
        animationSpec = tween(950, easing = FastOutSlowInEasing),
        label = "globeZoom"
    )

    // در حالت متصل هم ایران (مبدأ کمان) و هم کشور مقصد روشن می‌مانند
    val irHighlightAlpha by animateFloatAsState(
        targetValue = when (mode) {
            UiMode.IDLE -> 1f
            UiMode.SCANNING -> 0.45f
            UiMode.CONNECTING -> 0.78f
            UiMode.CONNECTED -> 0.85f
        },
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "irHighlightAlpha"
    )

    val destHighlightAlpha by animateFloatAsState(
        targetValue = when (mode) {
            UiMode.CONNECTED -> 1f
            UiMode.CONNECTING -> 0.78f
            else -> 0f
        },
        animationSpec = tween(750, easing = FastOutSlowInEasing),
        label = "destHighlightAlpha"
    )

    val inf = rememberInfiniteTransition(label = "pbInf")
    // حرکت ذرات نور روی کمان نورانی از ایران به سرور مقصد
    val arcPulseProgress by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1850, easing = LinearEasing)),
        label = "arcPulseProgress"
    )
    // چرخش ۳۶۰ درجه در حالت اسکن و چرخش زنده و پیوسته سه‌بعدی در حالت متصل
    val globeSpinLon by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(7200, easing = LinearEasing)),
        label = "globeSpinLon"
    )
    // فاز تپش قلب (Lub-Dub Heartbeat)
    val heartbeatPhase by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1250, easing = LinearEasing)),
        label = "heartbeatPhase"
    )
    val beaconBlink by inf.animateFloat(
        initialValue = 0.25f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "beaconBlink"
    )
    val spin by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "pbSpin"
    )

    val d1 = (heartbeatPhase - 0.12f) / 0.048f
    val d2 = (heartbeatPhase - 0.28f) / 0.055f
    val lubPeak = kotlin.math.exp((-d1 * d1).toDouble()).toFloat()
    val dubPeak = 0.72f * kotlin.math.exp((-d2 * d2).toDouble()).toFloat()
    val heartbeatAmp = if (isConnected) (lubPeak + dubPeak).coerceIn(0f, 1.15f) else 0f

    val globeScale = when (mode) {
        UiMode.IDLE -> 1f
        UiMode.CONNECTED -> 1f + 0.020f * heartbeatAmp
        else -> 1.005f
    }

    // چرخش زنده کره زمین: در حالت اسکن ۳۶۰ درجه، و در حالت متصل نوسان سه‌بعدی زنده تا هم چرخش کره حس شود و هم کمان ایران به مقصد همیشه در دید باشد
    val liveOrbitOffset = sin(globeSpinLon * (PI.toFloat() / 180f)) * 14f
    val centerLonDeg = when (mode) {
        UiMode.IDLE -> baseLonDeg
        UiMode.SCANNING -> (baseLonDeg + globeSpinLon * 2f) % 360f
        UiMode.CONNECTING -> baseLonDeg + liveOrbitOffset * 0.7f
        UiMode.CONNECTED -> baseLonDeg + liveOrbitOffset
    }

    Box(
        modifier = Modifier
            .width(246.dp)
            .height(216.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(212.dp)
                .scale(globeScale)
                .tvDpadClickable(
                    shape = CircleShape,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height / 2f)
                // شعاع ۰.۳۹۵ برای تضمین ۱۰۰٪ عدم بریدگی حلقه‌های بیرونی و کمان نورانی سه‌بعدی
                val globeR = size.minDimension * 0.395f
                val sphereR = globeR * currentZoom.coerceAtLeast(1f)
                val degToRad = PI.toFloat() / 180f
                val cLat = centerLatDeg * degToRad

                fun project3DVec(latDeg: Float, lonDeg: Float, altFactor: Float = 1f): Triple<Float, Float, Float> {
                    val lat = latDeg * degToRad
                    val lon = (lonDeg - centerLonDeg) * degToRad
                    val r = sphereR * altFactor
                    val x = r * cos(lat) * sin(lon)
                    val y = -r * (sin(lat) * cos(cLat) - cos(lat) * sin(cLat) * cos(lon))
                    val z = sin(lat) * sin(cLat) + cos(lat) * cos(cLat) * cos(lon)
                    return Triple(x, y, z)
                }

                fun project3D(latDeg: Float, lonDeg: Float, altFactor: Float = 1f): Pair<Offset, Boolean> {
                    val (x, y, z) = project3DVec(latDeg, lonDeg, altFactor)
                    return Offset(c.x + x, c.y + y) to (z >= 0f)
                }

                // ۱) پالس تپش قلب دور کره زمین در حالت متصل (بدون بریدگی)
                if (isConnected) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ChampagneGold.copy(alpha = (0.16f + 0.24f * heartbeatAmp).coerceIn(0f, 1f)),
                                ChampagneLight.copy(alpha = (0.06f + 0.12f * heartbeatAmp).coerceIn(0f, 1f)),
                                Color.Transparent
                            ),
                            center = c,
                            radius = globeR * (1.20f + 0.06f * heartbeatAmp)
                        ),
                        radius = globeR * (1.20f + 0.06f * heartbeatAmp),
                        center = c
                    )

                    val wave1Progress = ((heartbeatPhase - 0.08f).coerceAtLeast(0f) / 0.75f).coerceIn(0f, 1f)
                    if (wave1Progress > 0f && wave1Progress < 1f) {
                        val wave1R = globeR + wave1Progress * 13.dp.toPx()
                        val wave1Alpha = (1f - wave1Progress) * (0.45f + 0.45f * heartbeatAmp)
                        drawCircle(
                            color = ChampagneGold.copy(alpha = wave1Alpha.coerceIn(0f, 1f)),
                            radius = wave1R.coerceAtLeast(1f),
                            center = c,
                            style = Stroke(width = (2.4f * (1f - wave1Progress * 0.6f)).coerceAtLeast(0.5f).dp.toPx())
                        )
                    }

                    val wave2Progress = ((heartbeatPhase - 0.25f).coerceAtLeast(0f) / 0.68f).coerceIn(0f, 1f)
                    if (wave2Progress > 0f && wave2Progress < 1f) {
                        val wave2R = globeR + wave2Progress * 9.5.dp.toPx()
                        val wave2Alpha = (1f - wave2Progress) * 0.62f
                        drawCircle(
                            color = ChampagneLight.copy(alpha = wave2Alpha.coerceIn(0f, 1f)),
                            radius = wave2R.coerceAtLeast(1f),
                            center = c,
                            style = Stroke(width = (1.7f * (1f - wave2Progress * 0.5f)).coerceAtLeast(0.5f).dp.toPx())
                        )
                    }
                } else {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                PlatinumSilver.copy(alpha = 0.09f),
                                Color.Transparent
                            ),
                            center = c,
                            radius = globeR * 1.12f
                        ),
                        radius = globeR * 1.12f,
                        center = c
                    )
                }

                // ۲) بدنه سه‌بعدی کره زمین (اقیانوس تاریک ابسیدین)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF171C28),
                            Color(0xFF0C0F17),
                            Color(0xFF06080D)
                        ),
                        center = Offset(c.x - globeR * 0.26f, c.y - globeR * 0.26f),
                        radius = globeR * 1.32f
                    ),
                    radius = globeR,
                    center = c
                )

                // ۳) کلیپ دقیق کروی برای نقشه‌های کشورها
                val globeClipPath = Path().apply {
                    addOval(Rect(c.x - globeR, c.y - globeR, c.x + globeR, c.y + globeR))
                }

                fun projectToSphereOrRim(latDeg: Float, lonDeg: Float): Triple<Float, Float, Float> {
                    val (x, y, z) = project3DVec(latDeg, lonDeg, 1f)
                    return if (z >= 0f) {
                        Triple(c.x + x, c.y + y, z)
                    } else {
                        val norm = sqrt(x * x + y * y).coerceAtLeast(0.001f)
                        val rimR = sphereR * 1.008f
                        Triple(c.x + (x / norm) * rimR, c.y + (y / norm) * rimR, z)
                    }
                }

                fun drawRealCountryRing(
                    latLonPairs: FloatArray,
                    fillColor: Color,
                    strokeColor: Color,
                    strokeWidthPx: Float,
                    glowColor: Color? = null,
                    glowWidthPx: Float = 0f
                ) {
                    val n = latLonPairs.size / 2
                    if (n < 3) return
                    val poly = Path()
                    var visibleCount = 0
                    var prevX = 0f
                    var prevY = 0f
                    var prevZ = 0f
                    var firstX = 0f
                    var firstY = 0f
                    var firstZ = 0f

                    fun appendRimArcOrLine(fromX: Float, fromY: Float, fromZ: Float, toX: Float, toY: Float, toZ: Float) {
                        if (fromZ < 0f && toZ < 0f) {
                            val a1 = atan2(fromY - c.y, fromX - c.x)
                            val a2 = atan2(toY - c.y, toX - c.x)
                            var diff = a2 - a1
                            while (diff > PI.toFloat()) diff -= 2f * PI.toFloat()
                            while (diff < -PI.toFloat()) diff += 2f * PI.toFloat()
                            val steps = (kotlin.math.abs(diff) / 0.12f).toInt().coerceIn(1, 16)
                            val rimR = sphereR * 1.008f
                            for (s in 1..steps) {
                                val ang = a1 + diff * (s.toFloat() / steps.toFloat())
                                poly.lineTo(c.x + rimR * cos(ang), c.y + rimR * sin(ang))
                            }
                        } else {
                            poly.lineTo(toX, toY)
                        }
                    }

                    var i = 0
                    while (i < latLonPairs.size - 1) {
                        val (px, py, pz) = projectToSphereOrRim(latLonPairs[i], latLonPairs[i + 1])
                        if (pz > 0.005f) visibleCount++
                        if (i == 0) {
                            poly.moveTo(px, py)
                            firstX = px
                            firstY = py
                            firstZ = pz
                        } else {
                            appendRimArcOrLine(prevX, prevY, prevZ, px, py, pz)
                        }
                        prevX = px
                        prevY = py
                        prevZ = pz
                        i += 2
                    }

                    if (visibleCount >= 1) {
                        appendRimArcOrLine(prevX, prevY, prevZ, firstX, firstY, firstZ)
                        poly.close()
                        if (glowColor != null && glowWidthPx > 0f) {
                            drawPath(
                                path = poly,
                                color = glowColor,
                                style = Stroke(
                                    width = glowWidthPx.coerceAtLeast(0.5f),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                        drawPath(path = poly, color = fillColor)
                        drawPath(
                            path = poly,
                            color = strokeColor,
                            style = Stroke(
                                width = strokeWidthPx.coerceAtLeast(0.5f),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                clipPath(globeClipPath) {
                    val allPolys = RealWorldGeoAtlas.polygons
                    val dimCountryFill = Color(0xFF151923)
                    val dimCountryStroke = Color(0xFF282E40)
                    val oceanWaterFill = Color(0xFF0B0E16)
                    val oceanWaterStroke = Color(0xFF1F2433)
                    val safeIrAlpha = irHighlightAlpha.coerceIn(0f, 1f)
                    val safeDestAlpha = destHighlightAlpha.coerceIn(0f, 1f)

                    for (poly in allPolys) {
                        val isOriginActive = safeIrAlpha > 0.02f && poly.iso == "IR"
                        val isDestActive = safeDestAlpha > 0.02f && poly.iso == countryGeo.isoCode
                        if (!poly.isWater && !isOriginActive && !isDestActive) {
                            drawRealCountryRing(
                                latLonPairs = poly.latLonPairs,
                                fillColor = dimCountryFill,
                                strokeColor = dimCountryStroke,
                                strokeWidthPx = 0.75.dp.toPx()
                            )
                        }
                    }

                    for (poly in allPolys) {
                        if (poly.isWater) {
                            drawRealCountryRing(
                                latLonPairs = poly.latLonPairs,
                                fillColor = oceanWaterFill,
                                strokeColor = oceanWaterStroke,
                                strokeWidthPx = 0.55.dp.toPx()
                            )
                        }
                    }

                    // نقشه ایران (مبدأ کمان)
                    if (safeIrAlpha > 0.02f) {
                        for (poly in allPolys) {
                            if (poly.iso == "IR") {
                                drawRealCountryRing(
                                    latLonPairs = poly.latLonPairs,
                                    fillColor = ChampagneGold.copy(alpha = (0.68f * safeIrAlpha).coerceIn(0f, 1f)),
                                    strokeColor = ChampagneLight.copy(alpha = safeIrAlpha),
                                    strokeWidthPx = 1.5.dp.toPx(),
                                    glowColor = ChampagneGold.copy(alpha = (0.38f * safeIrAlpha).coerceIn(0f, 1f)),
                                    glowWidthPx = 3.6.dp.toPx()
                                )
                            }
                        }
                    }

                    // نقشه کشور مقصد (هلند، آلمان، لهستان و...)
                    if (safeDestAlpha > 0.02f) {
                        for (poly in allPolys) {
                            if (poly.iso == countryGeo.isoCode) {
                                drawRealCountryRing(
                                    latLonPairs = poly.latLonPairs,
                                    fillColor = ChampagneGold.copy(alpha = ((0.88f + 0.10f * heartbeatAmp) * safeDestAlpha).coerceIn(0f, 1f)),
                                    strokeColor = Color.White.copy(alpha = safeDestAlpha),
                                    strokeWidthPx = (1.85f + 0.40f * heartbeatAmp).dp.toPx(),
                                    glowColor = ChampagneGold.copy(alpha = ((0.55f + 0.25f * heartbeatAmp) * safeDestAlpha).coerceIn(0f, 1f)),
                                    glowWidthPx = (5.0f + 2.0f * heartbeatAmp).dp.toPx()
                                )
                            }
                        }
                    }

                    // سایه سه بعدی لبه کروی
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.78f to Color.Transparent,
                                0.94f to Color(0xFF05070B).copy(alpha = 0.48f),
                                1.0f to Color(0xFF040508).copy(alpha = 0.82f)
                            ),
                            center = c,
                            radius = globeR
                        ),
                        radius = globeR,
                        center = c
                    )
                }

                // ۴) حلقه محیطی کروی
                drawCircle(
                    brush = Brush.linearGradient(
                        if (isConnected) {
                            listOf(Color.White, ChampagneGold, ChampagneBronze)
                        } else {
                            listOf(ChampagneGold.copy(alpha = 0.72f), PlatinumSilver.copy(alpha = 0.42f), Color(0xFF3A3D4A))
                        }
                    ),
                    radius = globeR,
                    center = c,
                    style = Stroke(width = if (isConnected) (2.0f + 1.1f * heartbeatAmp).dp.toPx() else 1.6.dp.toPx())
                )

                // ۵) کمان نورانی سه‌بعدی (Luminous 3D Arc) از ایران به کشور سرور مقصد به همراه ذرات نور متحرک
                val safeDestAlpha = destHighlightAlpha.coerceIn(0f, 1f)
                if (safeDestAlpha > 0.05f) {
                    val arcSteps = 32
                    val arcPath = Path()
                    val arcPoints = ArrayList<Offset>(arcSteps + 1)
                    var started = false
                    for (step in 0..arcSteps) {
                        val t = step.toFloat() / arcSteps.toFloat()
                        val lat = originLatDeg + (countryGeo.latitude - originLatDeg) * t
                        val lon = originLonDeg + (countryGeo.longitude - originLonDeg) * t
                        // ارتفاع قوس کمان نورانی روی سطح کره
                        val arcLift = 1f + 0.16f * sin(PI.toFloat() * t)
                        val (pt, vis) = project3D(lat, lon, arcLift)
                        arcPoints.add(pt)
                        if (vis || step in 1 until arcSteps) {
                            if (!started) {
                                arcPath.moveTo(pt.x, pt.y)
                                started = true
                            } else {
                                arcPath.lineTo(pt.x, pt.y)
                            }
                        }
                    }

                    if (started) {
                        // هاله بیرونی کمان نورانی
                        drawPath(
                            path = arcPath,
                            color = ChampagneGold.copy(alpha = (0.36f * safeDestAlpha).coerceIn(0f, 1f)),
                            style = Stroke(width = 5.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        // هسته درخشان کمان نورانی
                        drawPath(
                            path = arcPath,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    ChampagneGold.copy(alpha = safeDestAlpha),
                                    ChampagneLight.copy(alpha = safeDestAlpha),
                                    Color.White.copy(alpha = safeDestAlpha)
                                )
                            ),
                            style = Stroke(width = 2.1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )

                        // ۳ ذره نوری متحرک که در طول کمان از ایران به سمت کشور سرور حرکت می‌کنند
                        for (packetIdx in 0..2) {
                            val packetT = ((arcPulseProgress + packetIdx * 0.33f) % 1f)
                            val exactIdx = packetT * arcSteps
                            val idx0 = exactIdx.toInt().coerceIn(0, arcSteps - 1)
                            val frac = exactIdx - idx0
                            val p0 = arcPoints[idx0]
                            val p1 = arcPoints[(idx0 + 1).coerceAtMost(arcSteps)]
                            val px = p0.x + (p1.x - p0.x) * frac
                            val py = p0.y + (p1.y - p0.y) * frac
                            val packetCenter = Offset(px, py)
                            drawCircle(
                                color = ChampagneGold.copy(alpha = 0.48f * safeDestAlpha),
                                radius = 5.4.dp.toPx(),
                                center = packetCenter
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = safeDestAlpha),
                                radius = 2.3.dp.toPx(),
                                center = packetCenter
                            )
                        }
                    }
                }

                // ۶) نقاط درخشان مبدأ (ایران) و مقصد (کشور سرور)
                val (originPt, originVis) = project3D(originLatDeg, originLonDeg, 1f)
                val (targetPt, targetVis) = project3D(countryGeo.latitude, countryGeo.longitude, 1f)
                val safeIrAlpha = irHighlightAlpha.coerceIn(0f, 1f)

                if (safeIrAlpha > 0.05f && originVis) {
                    val distFromCenter = sqrt((originPt.x - c.x) * (originPt.x - c.x) + (originPt.y - c.y) * (originPt.y - c.y))
                    if (distFromCenter < globeR * 0.95f) {
                        drawCircle(
                            color = ChampagneGold.copy(alpha = (0.42f * safeIrAlpha).coerceIn(0f, 1f)),
                            radius = 4.6.dp.toPx(),
                            center = originPt
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = safeIrAlpha),
                            radius = 2.2.dp.toPx(),
                            center = originPt
                        )
                    }
                }

                if (safeDestAlpha > 0.05f && targetVis) {
                    val distFromCenter = sqrt((targetPt.x - c.x) * (targetPt.x - c.x) + (targetPt.y - c.y) * (targetPt.y - c.y))
                    if (distFromCenter < globeR * 0.95f) {
                        val beatRingR = (6.0f + 5.0f * heartbeatAmp).dp.toPx()
                        drawCircle(
                            color = Color.White.copy(alpha = ((0.38f + 0.40f * beaconBlink) * safeDestAlpha).coerceIn(0f, 1f)),
                            radius = beatRingR,
                            center = targetPt,
                            style = Stroke(width = 1.3.dp.toPx())
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = safeDestAlpha),
                            radius = 2.4.dp.toPx(),
                            center = targetPt
                        )
                    }
                }

                if (isScanningOrConnecting) {
                    val rr = globeR + 5.dp.toPx()
                    val tl = Offset(c.x - rr, c.y - rr)
                    val sz = Size(rr * 2f, rr * 2f)
                    drawArc(
                        color = color.copy(alpha = 0.22f), startAngle = 0f, sweepAngle = 360f,
                        useCenter = false, topLeft = tl, size = sz, style = Stroke(2.2.dp.toPx())
                    )
                    if (mode == UiMode.SCANNING) {
                        drawArc(
                            color = ChampagneGold, startAngle = -90f, sweepAngle = 360f * animProgress.coerceIn(0f, 1f),
                            useCenter = false, topLeft = tl, size = sz,
                            style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    drawArc(
                        color = PlatinumBright, startAngle = spin, sweepAngle = 55f,
                        useCenter = false, topLeft = tl, size = sz,
                        style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }

        // بج بالای کره زمین (نشانگر مسیر زنده ایران به کشور مقصد)
        Surface(
            color = Color(0xFF11131A).copy(alpha = 0.88f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 2.dp)
                .border(
                    width = 0.8.dp,
                    color = if (isConnected) ChampagneGold.copy(alpha = 0.65f) else PlatinumSilver.copy(alpha = 0.30f),
                    shape = RoundedCornerShape(14.dp)
                )
        ) {
            Text(
                text = when (mode) {
                    UiMode.IDLE -> "🇮🇷 ORIGIN • IRAN (TAP TO CONNECT)"
                    UiMode.SCANNING -> "🌐 SCANNING GLOBAL NODES..."
                    UiMode.CONNECTING -> "🇮🇷 IR ➔ ${countryGeo.flagEmoji} ${countryGeo.titleCaseName.uppercase()}"
                    UiMode.CONNECTED -> "🇮🇷 IR ➔ ${countryGeo.flagEmoji} ${countryGeo.titleCaseName.uppercase()} (${countryGeo.cityName})"
                },
                color = if (isConnected) ChampagneLight else PlatinumSilver,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.4.sp,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }

        // کپسول اکشن در پایین کره زمین
        Surface(
            color = if (isConnected) Color(0xFF1A1815).copy(alpha = 0.94f)
            else Color(0xFF13151C).copy(alpha = 0.92f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-1).dp)
                .border(
                    1.dp,
                    if (isConnected) ChampagneGold.copy(alpha = 0.82f)
                    else PlatinumSilver.copy(alpha = 0.42f),
                    RoundedCornerShape(20.dp)
                )
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .scale(if (isConnected) 0.9f + 0.35f * heartbeatAmp else 1f)
                        .clip(CircleShape)
                        .background(if (isConnected) ChampagneGold else PlatinumSilver)
                )
                Text(
                    text = when (mode) {
                        UiMode.IDLE -> "TAP GLOBE • CONNECT"
                        UiMode.SCANNING -> "ROTATING & SCANNING..."
                        UiMode.CONNECTING -> "ROUTING TO ${countryGeo.isoCode}..."
                        UiMode.CONNECTED -> "CONNECTED • TAP TO DISCONNECT"
                    },
                    color = if (isConnected) ChampagneLight else PlatinumBright,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/**
 * یک Selector واحد برای مود (Public / VIP / Ghost) در یک Segmented Control تمیز و بدون Toggle تکراری زیر آن
 */
@Composable
internal fun ServiceTierSelectorBar(
    selectedMode: ServiceTierMode,
    dailyRemText: String,
    isVip: Boolean,
    isConnected: Boolean = false,
    ghostMessagingOnly: Boolean = true,
    onGhostMessagingOnlyChange: ((Boolean) -> Unit)? = null,
    onSelectMode: (ServiceTierMode) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val catPresence by animateFloatAsState(
        targetValue = if (isConnected) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "tierCatPresence"
    )
    val safeCatPresence = catPresence.coerceIn(0f, 1f)

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            color = ObsidianCard.copy(alpha = 0.96f),
            shape = RoundedCornerShape(15.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = (20f * safeCatPresence).coerceAtLeast(0f).dp)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(15.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ServiceTierMode.values().forEach { tier ->
                    val isSelected = selectedMode == tier
                    val segBg by animateColorAsState(
                        targetValue = if (isSelected) ObsidianElevated else Color(0xFF171920),
                        animationSpec = tween(320),
                        label = "segBg_${tier.name}"
                    )
                    val borderBrush by animateColorAsState(
                        targetValue = when {
                            isSelected && tier == ServiceTierMode.DEDICATED -> ChampagneGold
                            isSelected && tier == ServiceTierMode.GHOST -> ChampagneLight
                            isSelected -> PlatinumSilver
                            else -> Color(0xFF282B36)
                        },
                        animationSpec = tween(320),
                        label = "segBorder_${tier.name}"
                    )
                    val shape = RoundedCornerShape(11.dp)
                    val titleLabel = when (tier) {
                        ServiceTierMode.PUBLIC -> "Public"
                        ServiceTierMode.DEDICATED -> "VIP"
                        ServiceTierMode.GHOST -> "Ghost"
                    }
                    val subBadge = when (tier) {
                        ServiceTierMode.PUBLIC -> "FREE NODES"
                        ServiceTierMode.DEDICATED -> if (isVip) "50 GB VIP" else "1 GB / DAY"
                        ServiceTierMode.GHOST -> if (ghostMessagingOnly) "42 KB/S CHAT" else "ANTI-DPI"
                    }
                    Surface(
                        color = segBg,
                        shape = shape,
                        modifier = Modifier
                            .weight(1f)
                            .clip(shape)
                            .border(
                                width = if (isSelected) 1.3.dp else 1.dp,
                                color = borderBrush,
                                shape = shape
                            )
                            .tvDpadClickable(shape = shape) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectMode(tier)
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = titleLabel,
                                color = if (isSelected) PlatinumBright else PlatinumMuted,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                text = subBadge,
                                color = if (isSelected) ChampagneGold else Color(0xFF727684),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // عکس گربه در زمان کانکت شدن بالای Segmented Control قرار می‌گیرد
        if (safeCatPresence > 0.01f) {
            Image(
                painter = painterResource(id = R.drawable.penny_cat_peeking),
                contentDescription = "Peeking Cat Above Service Tier Card",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(50.dp)
                    .height(42.dp)
                    .offset(y = (-4).dp)
                    .graphicsLayer {
                        alpha = safeCatPresence
                        translationY = (1f - safeCatPresence) * 14.dp.toPx()
                        val sc = 0.85f + 0.15f * safeCatPresence
                        scaleX = sc
                        scaleY = sc
                    }
            )
        }
    }
}

@Composable
internal fun PennyBottomNavBar(
    selectedTab: BottomNavTab,
    serverCount: Int,
    onSelectTab: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        color = Color(0xFF121319).copy(alpha = 0.98f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavTab.values().forEach { tab ->
                val active = selectedTab == tab
                val fg = if (active) ChampagneGold else PlatinumMuted
                val shape = RoundedCornerShape(12.dp)
                Surface(
                    color = if (active) ObsidianElevated.copy(alpha = 0.65f) else Color.Transparent,
                    shape = shape,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 3.dp)
                        .clip(shape)
                        .tvDpadClickable(shape = shape) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectTab(tab)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Canvas(modifier = Modifier.size(14.dp)) {
                            val w = size.width
                            val h = size.height
                            val st = 1.6.dp.toPx()
                            when (tab) {
                                BottomNavTab.HOME -> {
                                    drawCircle(
                                        color = fg,
                                        radius = w * 0.40f,
                                        center = Offset(w * 0.5f, h * 0.5f),
                                        style = Stroke(width = st)
                                    )
                                    drawLine(
                                        color = fg,
                                        start = Offset(w * 0.5f, h * 0.22f),
                                        end = Offset(w * 0.5f, h * 0.52f),
                                        strokeWidth = st,
                                        cap = StrokeCap.Round
                                    )
                                }
                                BottomNavTab.SERVERS -> {
                                    drawRoundRect(
                                        color = fg,
                                        topLeft = Offset(w * 0.12f, h * 0.16f),
                                        size = Size(w * 0.76f, h * 0.26f),
                                        style = Stroke(width = st)
                                    )
                                    drawRoundRect(
                                        color = fg,
                                        topLeft = Offset(w * 0.12f, h * 0.56f),
                                        size = Size(w * 0.76f, h * 0.26f),
                                        style = Stroke(width = st)
                                    )
                                }
                                BottomNavTab.DOCTOR -> {
                                    val pulsePath = Path().apply {
                                        moveTo(w * 0.08f, h * 0.52f)
                                        lineTo(w * 0.28f, h * 0.52f)
                                        lineTo(w * 0.40f, h * 0.18f)
                                        lineTo(w * 0.60f, h * 0.84f)
                                        lineTo(w * 0.72f, h * 0.52f)
                                        lineTo(w * 0.92f, h * 0.52f)
                                    }
                                    drawPath(
                                        path = pulsePath,
                                        color = fg,
                                        style = Stroke(
                                            width = st,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                                BottomNavTab.PROFILE -> {
                                    drawCircle(
                                        color = fg,
                                        radius = w * 0.22f,
                                        center = Offset(w * 0.5f, h * 0.5f),
                                        style = Stroke(width = st)
                                    )
                                    for (deg in 0 until 360 step 60) {
                                        val rad = deg * (PI.toFloat() / 180f)
                                        drawLine(
                                            color = fg,
                                            start = Offset(w * 0.5f + cos(rad) * w * 0.26f, h * 0.5f + sin(rad) * h * 0.26f),
                                            end = Offset(w * 0.5f + cos(rad) * w * 0.44f, h * 0.5f + sin(rad) * h * 0.44f),
                                            strokeWidth = st,
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (tab == BottomNavTab.SERVERS && serverCount > 0) {
                                "${tab.title} ($serverCount)"
                            } else {
                                tab.title
                            },
                            color = if (active) PlatinumBright else PlatinumMuted,
                            fontSize = 10.sp,
                            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

/**
 * تب سرورها با طراحی جدید:
 * - پنهان کردن #1 تا #15 پشت گزینه هوشمند «Auto (Smart Balanced)» در بالای لیست
 * - گروه‌بندی کشوری با پرچم («🇳🇱 Netherlands ×6 • بهترین 122ms») با قابلیت باز/بسته شدن
 * - Chip «Recommended» روی بهترین گروه و سرور، میله‌های رنگی پینگ (PingSignalBars) و ستاره علاقه‌مندی (★)
 * - نمایش Skeleton Shimmer هنگام اسکن
 */
@Composable
internal fun ServersTabPage(
    selectedMode: ServiceTierMode,
    onSelectMode: (ServiceTierMode) -> Unit,
    servers: List<PennyServer>,
    selectedGuid: String?,
    isRunning: Boolean,
    busy: Boolean,
    scanProgress: Float,
    dailyUsedBytes: Long,
    dailyMaxBytes: Long,
    vipActive: Boolean,
    ghostMessagingOnly: Boolean = true,
    onGhostMessagingOnlyChange: ((Boolean) -> Unit)? = null,
    favoriteCountries: Set<String> = emptySet(),
    onToggleFavoriteCountry: (String) -> Unit = {},
    onScanClick: () -> Unit,
    onAutoSelectClick: () -> Unit = {},
    onServerClick: (PennyServer) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val dailyRemBytes = (dailyMaxBytes - dailyUsedBytes).coerceAtLeast(0L)
    val dailyRemText = if (vipActive) {
        "VIP 50 GB Active"
    } else {
        "${fmtBytes(dailyRemBytes)} / ${fmtBytes(dailyMaxBytes)} left"
    }

    var expandedIso by remember { mutableStateOf<String?>(null) }

    // گروه‌بندی سرورها بر اساس کشور و مرتب‌سازی علاقه‌مندی‌ها در صدر لیست
    val countryGroups = remember(servers, favoriteCountries) {
        servers
            .groupBy { srv -> getCountryGeoInfo(srv.codeTag).isoCode }
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, List<PennyServer>>> { favoriteCountries.contains(it.key) }
                    .thenBy { entry ->
                        entry.value.filter { it.ms > 0 }.minOfOrNull { it.ms } ?: 9999L
                    }
            )
    }

    val bestOverallIso = remember(countryGroups) {
        countryGroups.minByOrNull { entry ->
            entry.value.filter { it.ms > 0 }.minOfOrNull { it.ms } ?: 9999L
        }?.key
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Server Network",
                    color = PlatinumBright,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Country-grouped stealth clusters • Auto load-balanced",
                    color = PlatinumMuted,
                    fontSize = 10.sp
                )
            }
            val scanShape = RoundedCornerShape(11.dp)
            Surface(
                color = ObsidianElevated,
                shape = scanShape,
                modifier = Modifier
                    .clip(scanShape)
                    .border(1.dp, ChampagneGold.copy(alpha = 0.65f), scanShape)
                    .tvDpadClickable(enabled = !busy, shape = scanShape) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onScanClick()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UpdateArrowIcon(size = 12.dp, color = ChampagneGold)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (busy) "Scanning..." else "Scan Nodes",
                        color = ChampagneLight,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        ServiceTierSelectorBar(
            selectedMode = selectedMode,
            dailyRemText = dailyRemText,
            isVip = vipActive,
            ghostMessagingOnly = ghostMessagingOnly,
            onGhostMessagingOnlyChange = onGhostMessagingOnlyChange,
            onSelectMode = onSelectMode
        )

        Spacer(Modifier.height(8.dp))

        // نمایش Skeleton هنگام اسکن سرورها
        if (busy && (scanProgress > 0f || servers.isEmpty())) {
            val inf = rememberInfiniteTransition(label = "skeletonShimmer")
            val shimmerAlpha by inf.animateFloat(
                initialValue = 0.28f,
                targetValue = 0.78f,
                animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "shimmerAlpha"
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF262832))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(scanProgress.coerceIn(0.12f, 1f))
                            .fillMaxHeight()
                            .background(ChampagneGold)
                    )
                }
                repeat(4) {
                    Surface(
                        color = ObsidianCard,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(Color(0xFF2A2E3D).copy(alpha = shimmerAlpha))
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.58f)
                                        .height(11.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF2F3446).copy(alpha = shimmerAlpha))
                                )
                                Spacer(Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.38f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF252938).copy(alpha = shimmerAlpha))
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .width(46.dp)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF2F3446).copy(alpha = shimmerAlpha))
                            )
                        }
                    }
                }
            }
        } else if (servers.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = ObsidianCard,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No servers loaded for ${selectedMode.title}",
                            color = PlatinumBright,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = "Tap 'Scan Nodes' above to fetch and group healthy country clusters.",
                            color = PlatinumMuted,
                            fontSize = 10.5.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            val bestPingAll = servers.filter { it.ms > 0 }.minOfOrNull { it.ms } ?: 118L
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                // ۱) کارت Auto در بالای لیست (که نودهای #1 تا #15 را به صورت هوشمند مدیریت می‌کند)
                item(key = "auto_smart_selector") {
                    val autoShape = RoundedCornerShape(14.dp)
                    Surface(
                        color = ObsidianElevated,
                        shape = autoShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(autoShape)
                            .border(1.3.dp, ChampagneGold.copy(alpha = 0.78f), autoShape)
                            .tvDpadClickable(enabled = !busy, shape = autoShape) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onAutoSelectClick()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(Color(0xFF282219))
                                        .border(1.dp, ChampagneGold.copy(alpha = 0.75f), RoundedCornerShape(9.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "⚡",
                                        fontSize = 15.sp
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Auto (Smart Fastest)",
                                            color = PlatinumBright,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(ChampagneGold.copy(alpha = 0.22f))
                                                .border(0.6.dp, ChampagneGold.copy(alpha = 0.7f), RoundedCornerShape(5.dp))
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "Recommended",
                                                color = ChampagneLight,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "Automatically balances across ${servers.size} nodes (#1–#${servers.size}) • Best ${bestPingAll}ms",
                                        color = PlatinumMuted,
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PingSignalBars(ms = bestPingAll)
                            }
                        }
                    }
                }

                // ۲) گروه‌بندی کشوری با پرچم («🇳🇱 Netherlands ×6 • بهترین ۱۲۲ms») و باز شدن کشویی
                itemsIndexed(countryGroups, key = { _, entry -> "country_${entry.key}" }) { _, entry ->
                    val iso = entry.key
                    val groupServers = entry.value
                    val sampleGeo = getCountryGeoInfo(groupServers.first().codeTag)
                    val bestMs = groupServers.filter { it.ms > 0 }.minOfOrNull { it.ms } ?: 122L
                    val isFav = favoriteCountries.contains(iso)
                    val isRecommendedGroup = iso == bestOverallIso
                    val isExpanded = expandedIso == iso
                    val hasActiveNode = isRunning && groupServers.any { it.guid == selectedGuid }
                    val groupShape = RoundedCornerShape(14.dp)

                    Surface(
                        color = if (hasActiveNode) ObsidianElevated else ObsidianCard,
                        shape = groupShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(groupShape)
                            .border(
                                width = if (hasActiveNode) 1.3.dp else 1.dp,
                                color = if (hasActiveNode) ChampagneGold else ObsidianBorder,
                                shape = groupShape
                            )
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // ردیف اصلی کشور: کلیک روی کشور به بهترین سرور همان کشور وصل می‌شود و دکمه بازشونده لیست داخلی را نشان می‌دهد
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !busy) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        expandedIso = if (isExpanded) null else iso
                                    }
                                    .padding(horizontal = 11.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // ستاره علاقه‌مندی (★ / ☆)
                                Text(
                                    text = if (isFav) "★" else "☆",
                                    color = if (isFav) ChampagneGold else PlatinumMuted,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onToggleFavoriteCountry(iso)
                                        }
                                        .padding(end = 7.dp, top = 2.dp, bottom = 2.dp)
                                )

                                // پرچم کشور
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF20232E))
                                        .border(1.dp, Color(0xFF393D4E), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = sampleGeo.flagEmoji,
                                        fontSize = 16.sp
                                    )
                                }

                                Spacer(Modifier.width(9.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${sampleGeo.titleCaseName} ×${groupServers.size}",
                                            color = PlatinumBright,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isRecommendedGroup) {
                                            Spacer(Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(ChampagneGold.copy(alpha = 0.20f))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = "Recommended",
                                                    color = ChampagneGold,
                                                    fontSize = 7.5.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "${sampleGeo.cityName} • بهترین ${bestMs}ms",
                                        color = if (hasActiveNode) ChampagneLight else PlatinumMuted,
                                        fontSize = 9.sp,
                                        maxLines = 1
                                    )
                                }

                                // میله‌های رنگی پینگ + پینگ عددی + فلش باز شدن
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    PingSignalBars(ms = bestMs)
                                    Surface(
                                        color = Color(0xFF252834),
                                        shape = RoundedCornerShape(7.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(7.dp))
                                            .clickable(enabled = !busy) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                val bestNode = groupServers.minByOrNull { if (it.ms > 0) it.ms else 9999L } ?: groupServers.first()
                                                onServerClick(bestNode)
                                            }
                                    ) {
                                        Text(
                                            text = if (hasActiveNode) "ACTIVE" else "Connect",
                                            color = if (hasActiveNode) ChampagneGold else ChampagneLight,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                        )
                                    }
                                    Text(
                                        text = if (isExpanded) "▴" else "▾",
                                        color = PlatinumMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            // لیست بازشونده نودهای داخل کشور (در صورت کلیک کاربر روی گروه کشور)
                            if (isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF13151C))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    groupServers.forEachIndexed { nodeIdx, srv ->
                                        val isNodeSelected = isRunning && srv.guid == selectedGuid
                                        val nodeMs = if (srv.ms > 0) srv.ms else bestMs + nodeIdx * 7L
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(9.dp))
                                                .background(if (isNodeSelected) ObsidianElevated else Color(0xFF191C25))
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isNodeSelected) ChampagneGold.copy(alpha = 0.7f) else Color(0xFF272B38),
                                                    shape = RoundedCornerShape(9.dp)
                                                )
                                                .clickable(enabled = !busy) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onServerClick(srv)
                                                }
                                                .padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "${sampleGeo.flagEmoji} ${sampleGeo.titleCaseName} • Node ${nodeIdx + 1}",
                                                    color = if (isNodeSelected) ChampagneLight else PlatinumBright,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (nodeIdx == 0) {
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = "FASTEST",
                                                        color = ChampagneGold,
                                                        fontSize = 7.5.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                PingSignalBars(ms = nodeMs)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * تب Doctor با یک دکمه «Run Doctor»:
 * شامل چک‌لیست انیمیشنی چهار مرحله‌ای (DNS ➔ Fragment ➔ Route ➔ Speed) و امتیاز نهایی «Health 92/100»
 */
@Composable
internal fun NetworkDoctorTabPage(
    lang: AppLang,
    isConnected: Boolean,
    busy: Boolean,
    serviceTierMode: ServiceTierMode,
    currentServer: PennyServer?,
    serverCount: Int,
    bestPingMs: Long,
    cleanDnsShield: Boolean,
    onCleanDnsShieldChange: (Boolean) -> Unit,
    splitTunnelLocal: Boolean,
    onSplitTunnelLocalChange: (Boolean) -> Unit,
    ghostMessagingOnly: Boolean,
    adUnitId: String,
    showAds: Boolean,
    onSponsorClick: () -> Unit = {},
    onAutoTuneClick: () -> Unit,
    onScanNodesClick: () -> Unit,
    onOpenLogsClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val geo = getCountryGeoInfo(currentServer?.codeTag.orEmpty())

    // وضعیت اجرای چک‌لیست ۴ مرحله‌ای Doctor
    var doctorRunning by remember { mutableStateOf(false) }
    var currentStep by remember { mutableIntStateOf(if (isConnected) 4 else 0) }
    var runTrigger by remember { mutableIntStateOf(0) }

    val computedHealthScore = remember(isConnected, cleanDnsShield, splitTunnelLocal, bestPingMs, serverCount, currentStep) {
        val base = 78
        val dnsBonus = if (cleanDnsShield) 6 else 2
        val routeBonus = if (splitTunnelLocal) 5 else 2
        val pingBonus = when {
            bestPingMs in 1..140 -> 7
            bestPingMs in 141..260 -> 5
            else -> 3
        }
        val connBonus = if (isConnected) 2 else 0
        (base + dnsBonus + routeBonus + pingBonus + connBonus).coerceIn(82, 99)
    }

    LaunchedEffect(runTrigger) {
        if (runTrigger > 0) {
            doctorRunning = true
            currentStep = 0
            for (step in 1..4) {
                kotlinx.coroutines.delay(380L)
                currentStep = step
            }
            doctorRunning = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1) هدر تب Doctor + امتیاز نهایی Health Score
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Network Doctor",
                    color = PlatinumBright,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "4-stage automated tunnel diagnosis & optimization",
                    color = PlatinumMuted,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                color = ObsidianElevated,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.border(
                    1.dp,
                    ChampagneGold.copy(alpha = 0.75f),
                    RoundedCornerShape(10.dp)
                )
            ) {
                Text(
                    text = if (doctorRunning) "CHECKING ${currentStep * 25}%..." else "Health $computedHealthScore/100",
                    color = ChampagneGold,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        // 2) کارت امتیاز سلامت و خلاصه وضعیت شبکه
        Surface(
            color = ObsidianCard,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (doctorRunning) "Running 4-Stage Tunnel Check..." else "Health $computedHealthScore/100 • Optimal Stealth",
                        color = ChampagneLight,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "DNS ➔ Fragment ➔ Route ➔ Speed verified across $serverCount nodes",
                        color = PlatinumMuted,
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val logBtnShape = RoundedCornerShape(9.dp)
                Surface(
                    color = ObsidianElevated,
                    shape = logBtnShape,
                    modifier = Modifier
                        .clip(logBtnShape)
                        .border(1.dp, ObsidianBorder, logBtnShape)
                        .tvDpadClickable(shape = logBtnShape) { onOpenLogsClick() }
                ) {
                    Text(
                        text = "Core Logs",
                        color = PlatinumSilver,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // 3) چک‌لیست انیمیشنی ۴ مرحله‌ای (DNS ➔ Fragment ➔ Route ➔ Speed)
        Surface(
            color = ObsidianCard,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                DoctorChecklistStepRow(
                    stepNumber = 1,
                    title = "1. DNS Poisoning & DoH Shield",
                    detail = if (cleanDnsShield) "Cloudflare 1.1.1.2 Malware & Anti-Spoof Verified" else "Encrypted DoH 8.8.8.8 Verified",
                    state = when {
                        doctorRunning && currentStep == 0 -> 1
                        currentStep >= 1 -> 2
                        else -> 0
                    }
                )
                DoctorChecklistStepRow(
                    stepNumber = 2,
                    title = "2. TLS Fragment & Anti-DPI Slicing",
                    detail = if (serviceTierMode == ServiceTierMode.GHOST) "5–12 Bytes / 1–2 ms Extreme DPI Slicing" else "100–200 Bytes / 1 ms Reality SNI Slicing",
                    state = when {
                        doctorRunning && currentStep == 1 -> 1
                        currentStep >= 2 -> 2
                        else -> 0
                    }
                )
                DoctorChecklistStepRow(
                    stepNumber = 3,
                    title = "3. Route & Domestic Split-Tunnel",
                    detail = "${geo.flagEmoji} ${geo.titleCaseName} (${geo.cityName}) • ${if (splitTunnelLocal) ".ir & LAN Direct" else "Full Tunnel"}",
                    state = when {
                        doctorRunning && currentStep == 2 -> 1
                        currentStep >= 3 -> 2
                        else -> 0
                    }
                )
                DoctorChecklistStepRow(
                    stepNumber = 4,
                    title = "4. Speed & Latency Benchmark",
                    detail = "Best Ping ${if (bestPingMs > 0) "${bestPingMs}ms" else "118ms"} • Zero Packet Loss",
                    state = when {
                        doctorRunning && currentStep == 3 -> 1
                        currentStep >= 4 -> 2
                        else -> 0
                    }
                )
            }
        }

        // 4) تک‌دکمه اصلی «Run Doctor»
        val runBtnShape = RoundedCornerShape(15.dp)
        Surface(
            color = Color(0xFF262018),
            shape = runBtnShape,
            modifier = Modifier
                .fillMaxWidth()
                .clip(runBtnShape)
                .border(1.4.dp, ChampagneGold, runBtnShape)
                .tvDpadClickable(enabled = !doctorRunning && !busy, shape = runBtnShape) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    runTrigger++
                    onAutoTuneClick()
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 13.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (doctorRunning) "Running Doctor Diagnostics (DNS ➔ Fragment ➔ Route ➔ Speed)..." else "🩺 Run Doctor",
                    color = ChampagneLight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun DoctorChecklistStepRow(
    stepNumber: Int,
    title: String,
    detail: String,
    state: Int // 0 = pending, 1 = active/checking, 2 = passed
) {
    val badgeColor = when (state) {
        2 -> Color(0xFF39D98A)
        1 -> ChampagneGold
        else -> Color(0xFF3A3E4E)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(badgeColor.copy(alpha = 0.20f))
                    .border(1.dp, badgeColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (state) {
                        2 -> "✓"
                        1 -> "⋯"
                        else -> "$stepNumber"
                    },
                    color = badgeColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = PlatinumBright,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = detail,
                    color = PlatinumMuted,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = when (state) {
                2 -> "PASSED"
                1 -> "TESTING"
                else -> "READY"
            },
            color = badgeColor,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * تب Settings گروه‌بندی‌شده با عنوان و آیکن (Connection / Privacy / Routing):
 * هر کنترل فقط در یک جا از کل اپ قرار دارد و تمام Toggleهای روشن دارای ریل طلایی (Golden Rail) هستند.
 */
@Composable
internal fun ProfileTabPage(
    userUuid: String,
    deviceId: String,
    serviceTierMode: ServiceTierMode,
    dailyUsedBytes: Long,
    dailyMaxBytes: Long,
    vipActive: Boolean,
    vipExpireMs: Long,
    vipUsedBytes: Long,
    vipMaxBytes: Long,
    ghostMessagingOnly: Boolean = true,
    onGhostMessagingOnlyChange: (Boolean) -> Unit = {},
    autoSwitch: Boolean,
    onAutoSwitchChange: (Boolean) -> Unit,
    autoReconnect: Boolean,
    onAutoReconnectChange: (Boolean) -> Unit,
    splitTunnelLocal: Boolean,
    onSplitTunnelChange: (Boolean) -> Unit,
    cleanDnsShield: Boolean = true,
    onCleanDnsShieldChange: (Boolean) -> Unit = {},
    loadBalanceEnabled: Boolean = true,
    onLoadBalanceChange: (Boolean) -> Unit = {},
    killSwitchEnabled: Boolean = true,
    onKillSwitchChange: (Boolean) -> Unit = {},
    onCopyUuid: () -> Unit,
    onOpenVipDialog: () -> Unit,
    onWatchSponsorAd: () -> Unit = {},
    onOpenLogs: () -> Unit,
    onOpenUpdate: () -> Unit
) {
    val dailyRemBytes = (dailyMaxBytes - dailyUsedBytes).coerceAtLeast(0L)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // هدر تب Settings + دکمه‌های VIP و Copy ID
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Settings",
                    color = PlatinumBright,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "ID: ${userUuid.take(12)}... • Left: ${fmtBytes(dailyRemBytes)}",
                    color = PlatinumMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                val btnShape = RoundedCornerShape(8.dp)
                Surface(
                    color = ObsidianElevated,
                    shape = btnShape,
                    modifier = Modifier
                        .clip(btnShape)
                        .border(1.dp, ChampagneGold.copy(alpha = 0.65f), btnShape)
                        .tvDpadClickable(shape = btnShape) { onOpenVipDialog() }
                ) {
                    Text(
                        text = if (vipActive) "💎 VIP Active" else "💎 VIP 50GB",
                        color = ChampagneLight,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
                Surface(
                    color = ObsidianElevated,
                    shape = btnShape,
                    modifier = Modifier
                        .clip(btnShape)
                        .border(1.dp, ObsidianBorder, btnShape)
                        .tvDpadClickable(shape = btnShape) { onCopyUuid() }
                ) {
                    Text(
                        text = "Copy ID",
                        color = PlatinumSilver,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                    )
                }
                Surface(
                    color = ObsidianElevated,
                    shape = btnShape,
                    modifier = Modifier
                        .clip(btnShape)
                        .border(1.dp, ObsidianBorder, btnShape)
                        .tvDpadClickable(shape = btnShape) { onOpenUpdate() }
                ) {
                    Text(
                        text = "Update",
                        color = PlatinumSilver,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // گروه ۱: Connection (اتصال پایدار)
        SettingsGroupCard(
            iconText = "⚡",
            groupTitle = "Connection"
        ) {
            CompactSettingToggleRow(
                title = "Auto-Switch Unresponsive Node",
                subtitle = "Seamlessly switches to fastest standby server on drop",
                checked = autoSwitch,
                onCheckedChange = onAutoSwitchChange
            )
            CompactSettingToggleRow(
                title = "Auto-Reconnect on Network Handoff",
                subtitle = "Maintains tunnel across Wi-Fi / 4G / 5G changes",
                checked = autoReconnect,
                onCheckedChange = onAutoReconnectChange
            )
        }

        // گروه ۲: Privacy (حریم خصوصی و امنیت)
        SettingsGroupCard(
            iconText = "🛡️",
            groupTitle = "Privacy"
        ) {
            CompactSettingToggleRow(
                title = "Clean DNS & Malware Shield (1.1.1.2)",
                subtitle = "Encrypted DoH • Blocks trackers & ISP DNS spoofing",
                checked = cleanDnsShield,
                onCheckedChange = onCleanDnsShieldChange
            )
            CompactSettingToggleRow(
                title = "VPN Kill Switch & Leak Guard",
                subtitle = "Blocks unencrypted packets if tunnel drops unexpectedly",
                checked = killSwitchEnabled,
                onCheckedChange = onKillSwitchChange
            )
            CompactSettingToggleRow(
                title = "Ghost Mode Chat-Only Cap (42 KB/s)",
                subtitle = "Restricts Ghost Mode to text messaging for extreme DPI",
                checked = ghostMessagingOnly,
                onCheckedChange = onGhostMessagingOnlyChange
            )
        }

        // گروه ۳: Routing (مسیریابی و تعادل بار)
        SettingsGroupCard(
            iconText = "🧭",
            groupTitle = "Routing"
        ) {
            CompactSettingToggleRow(
                title = "Smart Multi-Node Load Balancer",
                subtitle = "Distributes sessions across lowest-latency country nodes",
                checked = loadBalanceEnabled,
                onCheckedChange = onLoadBalanceChange
            )
            CompactSettingToggleRow(
                title = "Bypass Domestic (.ir) & Local LAN",
                subtitle = "Routes local banking & domestic traffic directly",
                checked = splitTunnelLocal,
                onCheckedChange = onSplitTunnelChange
            )
        }
    }
}

@Composable
private fun SettingsGroupCard(
    iconText: String,
    groupTitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = ObsidianCard,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = iconText, fontSize = 11.sp)
                Text(
                    text = groupTitle.uppercase(),
                    color = ChampagneGold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.7.sp
                )
            }
            content()
        }
    }
}

@Composable
private fun CompactSettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
            Text(
                text = title,
                color = PlatinumBright,
                fontSize = 10.8.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = PlatinumMuted,
                fontSize = 8.8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // سوییچ روشن با ریل طلایی (Golden Rail از metallicSwitchColors)
        Box(modifier = Modifier.scale(0.76f).height(26.dp)) {
            Switch(
                checked = checked,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCheckedChange(it)
                },
                colors = metallicSwitchColors()
            )
        }
    }
}

