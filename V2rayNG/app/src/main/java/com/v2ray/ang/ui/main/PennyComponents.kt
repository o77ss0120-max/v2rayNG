package com.v2ray.ang.ui.main

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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.layout.ContentScale
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

@Composable
internal fun metallicSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = ChampagneLight,
    checkedTrackColor = Color(0xFF3E3830),
    checkedBorderColor = ChampagneGold.copy(alpha = 0.78f),
    uncheckedThumbColor = Color(0xFF7E828E),
    uncheckedTrackColor = Color(0xFF121317),
    uncheckedBorderColor = Color(0xFF2C2E36)
)

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
    val inf = rememberInfiniteTransition(label = "pingGlow")
    val glowAlpha by inf.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            tween(1100, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "pingGlowAlpha"
    )
    val dotScale by inf.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            tween(1100, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "pingDotScale"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF22242D),
                        Color(0xFF2B2E3A),
                        Color(0xFF22242D)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        Color(0xFF8E93A2).copy(alpha = 0.65f + 0.30f * glowAlpha),
                        PlatinumSilver.copy(alpha = 0.95f),
                        ChampagneGold.copy(alpha = 0.65f + 0.30f * glowAlpha)
                    )
                ),
                shape = RoundedCornerShape(9.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (showDiamond) {
                GlassDiamondIcon(size = 10.dp)
            }
            Box(
                modifier = Modifier
                    .size(5.5.dp)
                    .scale(dotScale)
                    .clip(CircleShape)
                    .background(ChampagneGold.copy(alpha = glowAlpha))
            )
            Text(
                text = if (ms > 0) "$ms ms" else "READY",
                color = PlatinumBright,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * کارت سرور اصلاح‌شده در صفحه Home:
 * - بدون بیرون‌زدگی متن (DED-NL به کد کشور تمیز ۲ حرفی مثل NL / PL / DE تبدیل شده)
 * - بدون شکستن تگ DEDICATED به دو خط (DEDICA / TED)
 */
@Composable
internal fun ModernServerCard(
    server: PennyServer?,
    isConnected: Boolean,
    isVipMode: Boolean,
    lang: AppLang,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val borderColor = when {
        isConnected -> ChampagneGold.copy(alpha = 0.85f)
        isVipMode -> ChampagneGold.copy(alpha = 0.55f)
        else -> ObsidianBorder
    }
    val shape = RoundedCornerShape(16.dp)

    Surface(
        color = ObsidianCard.copy(alpha = 0.95f),
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .tvDpadClickable(enabled = enabled, shape = shape, onClick = onClick)
    ) {
        Crossfade(
            targetState = server,
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            label = "serverCardFade"
        ) { currentSrv ->
            if (currentSrv == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = Strings.noServerYet(lang, isVipMode),
                        color = PlatinumMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "SELECT ›",
                        color = ChampagneGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            } else {
                val isOwn = isDedicatedServerTag(currentSrv.codeTag)
                val geo = getCountryGeoInfo(currentSrv.codeTag)
                val shortBadgeTop = when (currentSrv.codeTag) {
                    "GHOST" -> "NL"
                    else -> geo.isoCode
                }
                val tierLabel = when {
                    currentSrv.codeTag == "GHOST" -> "GHOST STEALTH"
                    isOwn -> "VIP MODE"
                    else -> "PUBLIC • ${geo.isoCode}"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // باکس استاندارد تک‌خطی کد کشور (NL / PL / DE / GH) بدون هیچ‌گونه بریدگی عمودی
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                Brush.linearGradient(
                                    if (isOwn) {
                                        listOf(Color(0xFF352E24), Color(0xFF1F1B16))
                                    } else {
                                        listOf(Color(0xFF262832), Color(0xFF191A22))
                                    }
                                )
                            )
                            .border(
                                1.dp,
                                if (isOwn) ChampagneGold.copy(alpha = 0.85f) else Color(0xFF4C505E),
                                RoundedCornerShape(9.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = shortBadgeTop,
                            color = if (isOwn) ChampagneLight else PlatinumBright,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Spacer(Modifier.width(9.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentSrv.label,
                                color = PlatinumBright,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(
                                        if (isOwn) ChampagneGold.copy(alpha = 0.18f)
                                        else Color(0xFF2B2D38)
                                    )
                                    .border(
                                        0.8.dp,
                                        if (isOwn) ChampagneGold.copy(alpha = 0.72f)
                                        else Color(0xFF525666),
                                        RoundedCornerShape(5.dp)
                                    )
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = tierLabel,
                                    color = if (isOwn) ChampagneLight else PlatinumSilver,
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        Spacer(Modifier.height(1.dp))
                        Text(
                            text = "${geo.countryName} (${geo.cityName}) • Tap to switch server",
                            color = if (isOwn) ChampagneGold.copy(alpha = 0.9f) else PlatinumMuted,
                            fontSize = 8.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    ShinyGreenPingBadge(
                        ms = currentSrv.ms,
                        showDiamond = isVipMode
                    )
                }
            }
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
internal fun ServiceQualityUptimeCard(
    lang: AppLang,
    isConnected: Boolean,
    isVipMode: Boolean,
    serverCount: Int,
    bestPingMs: Long,
    cleanDnsShield: Boolean,
    splitTunnelLocal: Boolean,
    adUnitId: String,
    showAds: Boolean,
    onSponsorClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val liveBadgeText = when {
        isConnected && bestPingMs > 0 -> "Verified • ${bestPingMs} ms"
        bestPingMs > 0 -> "Best Ping: ${bestPingMs} ms"
        else -> "Ready to Scan"
    }
    val qualityScore = when {
        bestPingMs in 1..250 -> 0.96f
        bestPingMs in 251..500 -> 0.88f
        bestPingMs > 500 -> 0.76f
        else -> 0.50f
    }
    val qualityLabel = when {
        isConnected && bestPingMs in 1..250 -> "Low Latency"
        isConnected -> "Connected"
        bestPingMs > 0 -> "Nodes Verified"
        else -> "Standby"
    }

    Surface(
        color = ObsidianCard.copy(alpha = 0.95f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isConnected) ChampagneGold.copy(alpha = 0.45f) else ObsidianBorder,
                RoundedCornerShape(14.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 7.dp)
        ) {
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
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) ChampagneGold else PlatinumSilver)
                    )
                    Text(
                        text = "Service Quality: $qualityLabel",
                        color = PlatinumBright,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ObsidianElevated)
                        .border(1.dp, Color(0xFF484B57), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 1.5.dp)
                ) {
                    Text(
                        text = liveBadgeText,
                        color = ChampagneLight,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(Modifier.height(5.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF262830))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(qualityScore)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF8E93A2), PlatinumSilver, ChampagneGold)
                            )
                        )
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Fragment: Active • Iran Direct",
                    color = PlatinumMuted,
                    fontSize = 9.sp,
                    maxLines = 1
                )
                Text(
                    text = buildString {
                        append(if (cleanDnsShield) "DoH 8.8.8.8" else "Standard DNS")
                        if (splitTunnelLocal) append(" • Bypass LAN")
                        append(" • ${serverCount.coerceAtLeast(0)} Nodes")
                    },
                    color = PlatinumSilver,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            if (showAds) {
                Spacer(Modifier.height(6.dp))
                val sponsorShape = RoundedCornerShape(9.dp)
                Surface(
                    color = Color(0xFF1E1D24),
                    shape = sponsorShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(sponsorShape)
                        .border(1.dp, ChampagneGold.copy(alpha = 0.55f), sponsorShape)
                        .tvDpadClickable(shape = sponsorShape) { onSponsorClick() }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(ChampagneGold.copy(alpha = 0.22f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "START.IO",
                                        color = ChampagneLight,
                                        fontSize = 7.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Text(
                                    text = "Rewarded Video • Watch on Direct IP for +100 MB",
                                    color = PlatinumBright,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "+100 MB ›",
                                color = ChampagneGold,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        // بنر Start.io فقط در حالت قطع بودن VPN بارگذاری می‌شود تا از آی‌پی واقعی خود کاربر (نه سرور VPN) پخش شود
                        if (!isConnected) {
                            AndroidView(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                factory = { ctx ->
                                    Banner(ctx)
                                }
                            )
                        }
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
 * کره زمین سه‌بعدی با رفتار هوشمند بر اساس وضعیت:
 * 1) در حالت دیسکانکت (IDLE): کاملاً ثابت، متمرکز روی ایران و فقط نقشه کشور مبدأ (ایران) روشن است.
 * 2) در حالت اسکن/اتصال (SCANNING / CONNECTING): کره زمین به صورت سه‌بعدی به دور محور خود می‌چرخد.
 * 3) در حالت متصل (CONNECTED): دوربین با انیمیشن نرم روی کشور مقصد می‌چرخد، نقشه کشور مقصد روشن می‌شود
 *    و پالس دور کره زمین به حالت تپش قلب واقعی (Lub-Dub Heartbeat) می‌تپد.
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

    // هدف دوربین و زوم بر اساس وضعیت:
    // - در حالت IDLE (دیسکانکت): دقیقاً روی ایران (32.4N, 53.7E)، کاملاً ثابت با زوم 1.85x و فقط نقشه ایران روشن
    // - در حالت SCANNING: زوم باز 1.12x و چرخش ۳۶۰ درجه کره زمین
    // - در حالت CONNECTED: تمرکز ۱۰۰٪ و مستقیم روی کشور مقصد (هلند، لهستان، آلمان و...) با زوم اختصاصی آن کشور تا نقشه کشور مقصد کاملاً واضح و بزرگ در مرکز کره دیده شود
    val targetCamLat = when (mode) {
        UiMode.IDLE -> originLatDeg
        UiMode.SCANNING -> 34.0f
        UiMode.CONNECTING, UiMode.CONNECTED -> countryGeo.latitude
    }
    val targetCamLon = when (mode) {
        UiMode.IDLE -> originLonDeg
        UiMode.SCANNING -> originLonDeg
        UiMode.CONNECTING, UiMode.CONNECTED -> countryGeo.longitude
    }
    val targetZoom = when (mode) {
        UiMode.IDLE -> 1.85f
        UiMode.SCANNING -> 1.12f
        UiMode.CONNECTING -> (countryGeo.globeZoom * 0.72f).coerceAtLeast(1.45f)
        UiMode.CONNECTED -> countryGeo.globeZoom
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

    // انیمیشن روشن بودن نقشه مبدأ (ایران) فقط در حالت دیسکانکت و خاموش شدن آن در حالت متصل
    val irHighlightAlpha by animateFloatAsState(
        targetValue = when (mode) {
            UiMode.IDLE -> 1f
            UiMode.SCANNING -> 0.45f
            else -> 0f
        },
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "irHighlightAlpha"
    )

    // انیمیشن روشن شدن اختصاصی نقشه کشور مقصد در حالت اتصال/متصل
    val destHighlightAlpha by animateFloatAsState(
        targetValue = when (mode) {
            UiMode.CONNECTED -> 1f
            UiMode.CONNECTING -> 0.75f
            else -> 0f
        },
        animationSpec = tween(750, easing = FastOutSlowInEasing),
        label = "destHighlightAlpha"
    )

    val inf = rememberInfiniteTransition(label = "pbInf")
    val pulse by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "pbPulse"
    )
    // چرخش ۳۶۰ درجه کره زمین در حالت اسکنینگ
    val globeSpinLon by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3400, easing = LinearEasing)),
        label = "globeSpinLon"
    )
    // فاز تپش قلب (Lub-Dub Heartbeat) با دوره ۱۲۵۰ میلی‌ثانیه (~۴۸ ضربان در دقیقه)
    val heartbeatPhase by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1250, easing = LinearEasing)),
        label = "heartbeatPhase"
    )
    val beaconBlink by inf.animateFloat(
        initialValue = 0.22f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "beaconBlink"
    )
    val spin by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "pbSpin"
    )

    // محاسبه منحنی واقعی تپش قلب دوضربه‌ای (Lub-Dub): ضربه اول قوی در t=0.12 و ضربه دوم در t=0.28
    val d1 = (heartbeatPhase - 0.12f) / 0.048f
    val d2 = (heartbeatPhase - 0.28f) / 0.055f
    val lubPeak = kotlin.math.exp((-d1 * d1).toDouble()).toFloat()
    val dubPeak = 0.72f * kotlin.math.exp((-d2 * d2).toDouble()).toFloat()
    val heartbeatAmp = if (isConnected) (lubPeak + dubPeak).coerceIn(0f, 1.15f) else 0f

    // مقیاس کره زمین: در حالت دیسکانکت کاملاً ثابت (1f)، در حالت متصل با ریتم تپش قلب می‌تپد
    val globeScale = when (mode) {
        UiMode.IDLE -> 1f
        UiMode.CONNECTED -> 1f + 0.026f * heartbeatAmp
        else -> 1.006f
    }

    // طول جغرافیایی دوربین: در حالت IDLE و CONNECTED کاملاً متمرکز، در حالت SCANNING چرخش ۳۶۰ درجه
    val centerLonDeg = when (mode) {
        UiMode.IDLE -> baseLonDeg
        UiMode.SCANNING -> (baseLonDeg + globeSpinLon) % 360f
        UiMode.CONNECTING -> (baseLonDeg + globeSpinLon * 0.25f) % 360f
        UiMode.CONNECTED -> baseLonDeg
    }

    Box(
        modifier = Modifier
            .width(264.dp)
            .height(242.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(234.dp)
                .scale(globeScale)
                .tvDpadClickable(shape = CircleShape, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val globeR = size.minDimension * 0.445f
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

                // ۱) پالس تپش قلب دور کره زمین در حالت متصل (Lub-Dub Heartbeat Shockwave Rings)
                if (isConnected) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ChampagneGold.copy(alpha = (0.14f + 0.24f * heartbeatAmp).coerceIn(0f, 1f)),
                                ChampagneLight.copy(alpha = (0.06f + 0.12f * heartbeatAmp).coerceIn(0f, 1f)),
                                Color.Transparent
                            ),
                            center = c,
                            radius = globeR * (1.14f + 0.08f * heartbeatAmp)
                        ),
                        radius = globeR * (1.14f + 0.08f * heartbeatAmp),
                        center = c
                    )

                    // موج اول تپش قلب (Systolic Lub Wave)
                    val wave1Progress = ((heartbeatPhase - 0.08f).coerceAtLeast(0f) / 0.75f).coerceIn(0f, 1f)
                    if (wave1Progress > 0f && wave1Progress < 1f) {
                        val wave1R = globeR + wave1Progress * 15.dp.toPx()
                        val wave1Alpha = (1f - wave1Progress) * (0.45f + 0.45f * heartbeatAmp)
                        drawCircle(
                            color = ChampagneGold.copy(alpha = wave1Alpha.coerceIn(0f, 1f)),
                            radius = wave1R.coerceAtLeast(1f),
                            center = c,
                            style = Stroke(width = (2.6f * (1f - wave1Progress * 0.6f)).coerceAtLeast(0.5f).dp.toPx())
                        )
                    }

                    // موج دوم تپش قلب (Diastolic Dub Wave)
                    val wave2Progress = ((heartbeatPhase - 0.25f).coerceAtLeast(0f) / 0.68f).coerceIn(0f, 1f)
                    if (wave2Progress > 0f && wave2Progress < 1f) {
                        val wave2R = globeR + wave2Progress * 11.dp.toPx()
                        val wave2Alpha = (1f - wave2Progress) * 0.62f
                        drawCircle(
                            color = ChampagneLight.copy(alpha = wave2Alpha.coerceIn(0f, 1f)),
                            radius = wave2R.coerceAtLeast(1f),
                            center = c,
                            style = Stroke(width = (1.8f * (1f - wave2Progress * 0.5f)).coerceAtLeast(0.5f).dp.toPx())
                        )
                    }
                } else {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                PlatinumSilver.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            center = c,
                            radius = globeR * 1.08f
                        ),
                        radius = globeR * 1.08f,
                        center = c
                    )
                }

                // ۲) بدنه سه‌بعدی کره زمین (اقیانوس تاریک ابسیدین)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF151924),
                            Color(0xFF0C0F17),
                            Color(0xFF06080D)
                        ),
                        center = Offset(c.x - globeR * 0.26f, c.y - globeR * 0.26f),
                        radius = globeR * 1.32f
                    ),
                    radius = globeR,
                    center = c
                )

                // ۳) کلیپ دقیق کروی (انطباق ۱۰۰٪ لبه‌های نقشه با دایره کره زمین)
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

                    // مرحله الف: تمام کشورهای غیرهدف خاموش (تاریک و مات) باشند تا فقط کشور فعال بدرخشد
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

                    // مرحله ب: دریاچه‌ها و دریاهای داخلی (خزر، ارومیه و...)
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

                    // مرحله ج: نقشه مبدأ (ایران IR) فقط در حالت دیسکانکت/اسکن روشن است و در حالت متصل خاموش می‌شود
                    if (safeIrAlpha > 0.02f) {
                        for (poly in allPolys) {
                            if (poly.iso == "IR") {
                                drawRealCountryRing(
                                    latLonPairs = poly.latLonPairs,
                                    fillColor = ChampagneGold.copy(alpha = (0.76f * safeIrAlpha).coerceIn(0f, 1f)),
                                    strokeColor = Color.White.copy(alpha = safeIrAlpha),
                                    strokeWidthPx = 1.65.dp.toPx(),
                                    glowColor = ChampagneGold.copy(alpha = (0.44f * safeIrAlpha).coerceIn(0f, 1f)),
                                    glowWidthPx = 4.0.dp.toPx()
                                )
                            }
                        }
                    }

                    // مرحله د: نقشه کشور مقصد (هلند NL، لهستان PL، آلمان DE و...) در حالت متصل در مرکز کره زمین روشن و بزرگ نمایش داده می‌شود
                    if (safeDestAlpha > 0.02f) {
                        for (poly in allPolys) {
                            if (poly.iso == countryGeo.isoCode) {
                                drawRealCountryRing(
                                    latLonPairs = poly.latLonPairs,
                                    fillColor = ChampagneGold.copy(alpha = ((0.86f + 0.12f * heartbeatAmp) * safeDestAlpha).coerceIn(0f, 1f)),
                                    strokeColor = Color.White.copy(alpha = safeDestAlpha),
                                    strokeWidthPx = (1.85f + 0.45f * heartbeatAmp).dp.toPx(),
                                    glowColor = ChampagneGold.copy(alpha = ((0.52f + 0.28f * heartbeatAmp) * safeDestAlpha).coerceIn(0f, 1f)),
                                    glowWidthPx = (5.2f + 2.2f * heartbeatAmp).dp.toPx()
                                )
                            }
                        }
                    }

                    // سایه سه بعدی لبه کروی (Vignette داخلی کره زمین برای عمق سه‌بعدی واقعی و انطباق لبه‌ها)
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

                // ۴) نقطه ظریف پایتخت روی نقشه (بدون پوشاندن شکل نقشه کشور مقصد با دایره توپر بزرگ)
                val (originPt, originVis) = project3D(originLatDeg, originLonDeg, 1f)
                val (targetPt, targetVis) = project3D(countryGeo.latitude, countryGeo.longitude, 1f)
                val safeIrAlpha = irHighlightAlpha.coerceIn(0f, 1f)
                val safeDestAlpha = destHighlightAlpha.coerceIn(0f, 1f)

                if (safeIrAlpha > 0.05f && originVis) {
                    val distFromCenter = sqrt((originPt.x - c.x) * (originPt.x - c.x) + (originPt.y - c.y) * (originPt.y - c.y))
                    if (distFromCenter < globeR * 0.92f) {
                        drawCircle(
                            color = ChampagneGold.copy(alpha = (0.38f * safeIrAlpha).coerceIn(0f, 1f)),
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

                // ۵) حلقه ظریف تپنده ضربان قلب دور پایتخت کشور مقصد (شفاف در مرکز تا نقشه کشور مقصد ۱۰۰٪ دیده شود)
                if (safeDestAlpha > 0.05f && targetVis) {
                    val distFromCenter = sqrt((targetPt.x - c.x) * (targetPt.x - c.x) + (targetPt.y - c.y) * (targetPt.y - c.y))
                    if (distFromCenter < globeR * 0.92f) {
                        val beatRingR = (6.5f + 5.5f * heartbeatAmp).dp.toPx()
                        drawCircle(
                            color = Color.White.copy(alpha = ((0.35f + 0.40f * beaconBlink) * safeDestAlpha).coerceIn(0f, 1f)),
                            radius = beatRingR,
                            center = targetPt,
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = safeDestAlpha),
                            radius = 2.0.dp.toPx(),
                            center = targetPt
                        )
                    }
                }

                // ۶) حلقه محیطی کروی با ضربان قلب در حالت متصل و حلقه چرخان در حالت اسکنینگ
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
                    style = Stroke(width = if (isConnected) (2.0f + 1.2f * heartbeatAmp).dp.toPx() else 1.6.dp.toPx())
                )

                if (isScanningOrConnecting) {
                    val rr = globeR + 5.dp.toPx()
                    val tl = Offset(c.x - rr, c.y - rr)
                    val sz = Size(rr * 2f, rr * 2f)
                    drawArc(
                        color = color.copy(alpha = 0.22f), startAngle = 0f, sweepAngle = 360f,
                        useCenter = false, topLeft = tl, size = sz, style = Stroke(2.4.dp.toPx())
                    )
                    if (mode == UiMode.SCANNING) {
                        drawArc(
                            color = ChampagneGold, startAngle = -90f, sweepAngle = 360f * animProgress.coerceIn(0f, 1f),
                            useCenter = false, topLeft = tl, size = sz,
                            style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    drawArc(
                        color = PlatinumBright, startAngle = spin, sweepAngle = 55f,
                        useCenter = false, topLeft = tl, size = sz,
                        style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }

        // بج ظریف نام کشور فعال در بالای کره زمین (ایران در حالت دیسکانکت، کشور مقصد در حالت متصل)
        Surface(
            color = Color(0xFF11131A).copy(alpha = 0.86f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 8.dp)
                .border(
                    width = 0.8.dp,
                    color = if (isConnected) ChampagneGold.copy(alpha = 0.65f) else PlatinumSilver.copy(alpha = 0.30f),
                    shape = RoundedCornerShape(14.dp)
                )
        ) {
            Text(
                text = when (mode) {
                    UiMode.IDLE -> "ORIGIN MAP • IRAN (IR)"
                    UiMode.SCANNING -> "WORLD SCAN • 360°"
                    UiMode.CONNECTING -> "TARGET • ${countryGeo.countryName} (${countryGeo.isoCode})"
                    UiMode.CONNECTED -> "${countryGeo.countryName} (${countryGeo.isoCode}) • ${countryGeo.cityName.uppercase()}"
                },
                color = if (isConnected) ChampagneLight else PlatinumSilver,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }

        // کپسول شیشه‌ای باریک در لبه پایینی کره زمین (بدون پوشاندن نقشه کشورها)
        Surface(
            color = if (isConnected) Color(0xFF1A1815).copy(alpha = 0.92f)
            else Color(0xFF13151C).copy(alpha = 0.90f),
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
                .clickable(onClick = onClick)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .scale(if (isConnected) 0.9f + 0.35f * heartbeatAmp else 1f)
                        .clip(CircleShape)
                        .background(
                            if (isConnected) ChampagneGold
                            else PlatinumSilver
                        )
                )
                Text(
                    text = when (mode) {
                        UiMode.IDLE -> "TAP GLOBE • CONNECT"
                        UiMode.SCANNING -> "ROTATING & SCANNING..."
                        UiMode.CONNECTING -> "CONNECTING TO ${countryGeo.isoCode}..."
                        UiMode.CONNECTED -> "CONNECTED • ${countryGeo.isoCode} (TAP TO STOP)"
                    },
                    color = if (isConnected) ChampagneLight else PlatinumBright,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.6.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/**
 * انتخاب‌گر ۳ حالت سرویس (Public / VIP Mode / Ghost Mode) با تم یکپارچه طلایی-شامپاینی و پلاتینیوم
 * همراه با عکس گربه بالای کادر در زمان اتصال (Connected) و سوییچ محدودکننده ترافیک Ghost Mode
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
    val inf = rememberInfiniteTransition(label = "ghostIndicator")
    val ghostBlink by inf.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ghostBlink"
    )
    // استفاده از tween به جای فنر نوسانی (LowBouncy) + محدودسازی (coerceIn) برای جلوگیری ۱۰۰٪ از
    // منفی شدن padding هنگام دیسکانکت (IllegalArgumentException: Padding must be non-negative)
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
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = (22f * safeCatPresence).coerceAtLeast(0f).dp)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ServiceTierMode.values().forEach { tier ->
                    val isSelected = selectedMode == tier
                    val borderBrush = when {
                        isSelected && tier == ServiceTierMode.DEDICATED -> ChampagneGold
                        isSelected && tier == ServiceTierMode.GHOST -> ChampagneLight
                        isSelected -> PlatinumSilver
                        else -> Color(0xFF2B2D38)
                    }
                    val shape = RoundedCornerShape(11.dp)
                    Surface(
                        color = if (isSelected) ObsidianElevated else Color(0xFF1A1B22),
                        shape = shape,
                        modifier = Modifier
                            .weight(1f)
                            .clip(shape)
                            .border(
                                width = if (isSelected) 1.3.dp else 1.dp,
                                color = borderBrush,
                                shape = shape
                            )
                            .tvDpadClickable(shape = shape) { onSelectMode(tier) }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = tier.shortTitle,
                                color = if (isSelected) PlatinumBright else PlatinumMuted,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                maxLines = 1
                            )
                            val subBadge = when (tier) {
                                ServiceTierMode.PUBLIC -> "FREE"
                                ServiceTierMode.DEDICATED -> if (isVip) "VIP 50GB" else "1GB/DAY"
                                ServiceTierMode.GHOST -> "CHAT ONLY"
                            }
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

            Spacer(Modifier.height(6.dp))

            // ردیف اختصاصی وضعیت Ghost Mode و سوییچ قفل ترافیک روی پیام‌رسان‌های اضطراری
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF121319))
                    .border(
                        width = 1.dp,
                        color = if (selectedMode == ServiceTierMode.GHOST && ghostMessagingOnly) {
                            ChampagneGold.copy(alpha = 0.65f)
                        } else {
                            Color(0xFF272934)
                        },
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (selectedMode == ServiceTierMode.GHOST && ghostMessagingOnly) {
                                    ChampagneGold.copy(alpha = ghostBlink)
                                } else if (selectedMode == ServiceTierMode.DEDICATED) {
                                    ChampagneGold
                                } else {
                                    PlatinumSilver
                                }
                            )
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (selectedMode) {
                                ServiceTierMode.GHOST ->
                                    if (ghostMessagingOnly) "GHOST SHIELD ACTIVE • Messaging-Only (42 KB/s Cap)"
                                    else "Ghost Mode • Toggle switch to restrict to chat only"
                                ServiceTierMode.DEDICATED ->
                                    "VIP Mode • $dailyRemText"
                                ServiceTierMode.PUBLIC ->
                                    "Public Free • Toggle Ghost Lock for emergency chat"
                            },
                            color = if (selectedMode == ServiceTierMode.GHOST) ChampagneLight else PlatinumBright,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (onGhostMessagingOnlyChange != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (ghostMessagingOnly && selectedMode == ServiceTierMode.GHOST) "CHAT LOCK" else "GHOST",
                            color = if (ghostMessagingOnly && selectedMode == ServiceTierMode.GHOST) ChampagneGold else PlatinumMuted,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                        Box(modifier = Modifier.scale(0.68f).height(24.dp)) {
                            Switch(
                                checked = selectedMode == ServiceTierMode.GHOST && ghostMessagingOnly,
                                onCheckedChange = { checked ->
                                    if (checked && selectedMode != ServiceTierMode.GHOST) {
                                        onSelectMode(ServiceTierMode.GHOST)
                                    }
                                    onGhostMessagingOnlyChange(checked)
                                },
                                colors = metallicSwitchColors()
                            )
                        }
                    }
                }
            }
        }
        }

        // عکس گربه در زمان کانکت شدن بالای کادر Public / VIP Mode / Ghost Mode قرار می‌گیرد
        if (safeCatPresence > 0.01f) {
            Image(
                painter = painterResource(id = R.drawable.penny_cat_peeking),
                contentDescription = "Peeking Cat Above Service Tier Card",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(52.dp)
                    .height(44.dp)
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
    // حذف کامل کادر دور ۴ دکمه پایین طبق درخواست کاربر
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
                        .tvDpadClickable(shape = shape) { onSelectTab(tab) }
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
                                    // آیکون چرخ‌دنده تنظیمات (Settings Gear)
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
 * تب سرورها: هدر و انتخاب‌گر حالت ثابت هستند و فقط لیست سرورها اسکرول می‌شود
 * (با سایز فونت کوچکتر و متوازن برای نام سرورها + کلید یکتای امن برای جلوگیری از کرش در زمان تغییر سرور)
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
    onScanClick: () -> Unit,
    onServerClick: (PennyServer) -> Unit
) {
    val dailyRemBytes = (dailyMaxBytes - dailyUsedBytes).coerceAtLeast(0L)
    val dailyRemText = if (vipActive) {
        "VIP 50 GB Active"
    } else {
        "${fmtBytes(dailyRemBytes)} / ${fmtBytes(dailyMaxBytes)} left"
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
                    text = "Load-balanced stealth nodes • Zero config leak",
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
                    .tvDpadClickable(enabled = !busy, shape = scanShape) { onScanClick() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UpdateArrowIcon(size = 12.dp, color = ChampagneGold)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (busy) "Scanning..." else "Scan Mode",
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

        if (busy && scanProgress > 0f) {
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF262832))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(scanProgress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(ChampagneGold)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (servers.isEmpty()) {
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
                            text = "Tap 'Scan Mode' above to fetch and load-balance healthy nodes.",
                            color = PlatinumMuted,
                            fontSize = 10.5.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            // تنها بخشی از کل اپلیکیشن که اسکرول می‌شود: لیست سرورها
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                itemsIndexed(servers, key = { idx, s -> "${s.guid}_$idx" }) { idx, srv ->
                    val isSelected = isRunning && srv.guid == selectedGuid
                    val geo = getCountryGeoInfo(srv.codeTag)
                    val isOwn = isDedicatedServerTag(srv.codeTag)
                    val tierBadge = when {
                        srv.codeTag == "DED-NL" -> "REALITY • NL"
                        srv.codeTag == "DED-PL" -> "STEALTH • PL"
                        srv.codeTag == "GHOST" -> "GHOST • NL"
                        srv.codeTag == "VPS-1" -> "VIP • DE"
                        isOwn -> "VIP • ${geo.isoCode}"
                        else -> "PUBLIC • ${geo.isoCode}"
                    }
                    val itemShape = RoundedCornerShape(13.dp)

                    Surface(
                        color = if (isSelected) ObsidianElevated else ObsidianCard,
                        shape = itemShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(itemShape)
                            .border(
                                width = if (isSelected) 1.3.dp else 1.dp,
                                color = if (isSelected) ChampagneGold else ObsidianBorder,
                                shape = itemShape
                            )
                            .tvDpadClickable(enabled = !busy, shape = itemShape) { onServerClick(srv) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 11.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(38.dp)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isOwn) Color(0xFF2D2720) else Color(0xFF22242E)
                                    )
                                    .border(
                                        1.dp,
                                        if (isOwn) ChampagneGold.copy(alpha = 0.7f) else Color(0xFF454857),
                                        RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (srv.codeTag == "GHOST") "GH" else geo.isoCode,
                                    color = if (isOwn) ChampagneLight else PlatinumBright,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Spacer(Modifier.width(9.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = srv.label.ifBlank { "${selectedMode.shortTitle} Node #${idx + 1}" },
                                        color = PlatinumBright,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(ChampagneGold.copy(alpha = if (isOwn) 0.18f else 0.08f))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = tierBadge,
                                            color = if (isOwn) ChampagneGold else PlatinumSilver,
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                                Spacer(Modifier.height(1.dp))
                                Text(
                                    text = if (isSelected) {
                                        "Active Tunnel • ${geo.countryName} • ${geo.cityName}"
                                    } else {
                                        "${geo.countryName} • ${geo.cityName} • Balanced Node"
                                    },
                                    color = if (isSelected) ChampagneGold else PlatinumMuted,
                                    fontSize = 8.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (srv.ms > 0) "${srv.ms} ms" else "READY",
                                    color = if (isOwn) ChampagneGold else PlatinumSilver,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = if (isSelected) "ACTIVE" else "CONNECT ›",
                                    color = if (isSelected) ChampagneLight else PlatinumMuted,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * تب Network Doctor: شامل کارت Service Quality منتقل‌شده از صفحه اصلی + عیب‌یاب زنده Anti-DPI، Fragment و DNS (بدون اسکرول)
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
    val geo = getCountryGeoInfo(currentServer?.codeTag.orEmpty())
    val fragLabel = when (serviceTierMode) {
        ServiceTierMode.GHOST -> "5-12 B / 1-2 ms (Extreme Anti-DPI)"
        ServiceTierMode.DEDICATED -> "100-200 B / 1 ms (Reality Tuned)"
        ServiceTierMode.PUBLIC -> "100-200 B / 1 ms (Standard TLS)"
    }
    val dnsLabel = if (cleanDnsShield) "1.1.1.2 (Malware & Tracker Shield)" else "8.8.8.8 / 1.1.1.1 (Encrypted DoH)"

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1) هدر تب Network Doctor
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Network Doctor & Quality",
                    color = PlatinumBright,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Real-time tunnel diagnostics, Anti-DPI & Service Quality",
                    color = PlatinumMuted,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianElevated)
                    .border(
                        1.dp,
                        if (isConnected) ChampagneGold.copy(alpha = 0.65f) else ObsidianBorder,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = when {
                        busy -> "DIAGNOSING..."
                        isConnected -> "TUNNEL HEALTHY"
                        else -> "STANDBY"
                    },
                    color = if (isConnected) ChampagneGold else PlatinumSilver,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // 2) کارت Service Quality منتقل‌شده از صفحه اصلی به تب Network Doctor
        ServiceQualityUptimeCard(
            lang = lang,
            isConnected = isConnected,
            isVipMode = serviceTierMode != ServiceTierMode.PUBLIC,
            serverCount = serverCount,
            bestPingMs = bestPingMs,
            cleanDnsShield = cleanDnsShield,
            splitTunnelLocal = splitTunnelLocal,
            adUnitId = adUnitId,
            showAds = showAds,
            onSponsorClick = onSponsorClick
        )

        // 3) ماتریس عیب‌یابی زنده شبکه (۲×۲ کارت فشرده)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                DoctorMetricCard(
                    title = "TLS FRAGMENT ENGINE",
                    value = if (serviceTierMode == ServiceTierMode.GHOST) "5-12 Bytes" else "100-200 Bytes",
                    subtitle = fragLabel,
                    highlight = true,
                    modifier = Modifier.weight(1f)
                )
                DoctorMetricCard(
                    title = "ACTIVE ROUTE NODE",
                    value = if (currentServer != null) "${geo.countryName} (${geo.isoCode})" else "Auto-Select",
                    subtitle = if (currentServer != null) "${geo.cityName} • ${if (bestPingMs > 0) "${bestPingMs} ms" else "Verified"}" else "$serverCount verified nodes ready",
                    highlight = isConnected,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                DoctorMetricCard(
                    title = "ENCRYPTED DNS (DoH)",
                    value = if (cleanDnsShield) "Shield 1.1.1.2" else "DoH 8.8.8.8",
                    subtitle = dnsLabel,
                    highlight = cleanDnsShield,
                    modifier = Modifier.weight(1f)
                )
                DoctorMetricCard(
                    title = "TRAFFICSHAPER & LAN",
                    value = if (serviceTierMode == ServiceTierMode.GHOST && ghostMessagingOnly) "42 KB/s Cap" else "Full Speed",
                    subtitle = if (splitTunnelLocal) "Iran (.ir) & LAN Direct Bypass" else "All Traffic Routed in Tunnel",
                    highlight = splitTunnelLocal,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 4) کنترل‌های تعمیر سریع شبکه و سپر DNS
        Surface(
            color = ObsidianCard,
            shape = RoundedCornerShape(15.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ObsidianBorder, RoundedCornerShape(15.dp))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "Auto-Repair & Shield Controls",
                    color = PlatinumBright,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                CompactSettingToggleRow(
                    title = "Clean DNS & Malware Shield (1.1.1.2)",
                    subtitle = "Blocks DNS poisoning, ads & trackers inside tunnel",
                    checked = cleanDnsShield,
                    onCheckedChange = onCleanDnsShieldChange
                )

                CompactSettingToggleRow(
                    title = "Smart Domestic (.ir) & LAN Direct Route",
                    subtitle = "Bypasses local banking apps & prevents IP blocks",
                    checked = splitTunnelLocal,
                    onCheckedChange = onSplitTunnelLocalChange
                )

                Spacer(Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    val btnShape = RoundedCornerShape(10.dp)
                    Surface(
                        color = ObsidianElevated,
                        shape = btnShape,
                        modifier = Modifier
                            .weight(1f)
                            .clip(btnShape)
                            .border(1.dp, ChampagneGold.copy(alpha = 0.55f), btnShape)
                            .tvDpadClickable(enabled = !busy, shape = btnShape) { onAutoTuneClick() }
                    ) {
                        Text(
                            text = "Auto-Tune ISP",
                            color = ChampagneLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp)
                        )
                    }
                    Surface(
                        color = ObsidianElevated,
                        shape = btnShape,
                        modifier = Modifier
                            .weight(1f)
                            .clip(btnShape)
                            .border(1.dp, ObsidianBorder, btnShape)
                            .tvDpadClickable(enabled = !busy, shape = btnShape) { onScanNodesClick() }
                    ) {
                        Text(
                            text = if (busy) "Testing..." else "Re-Test Nodes",
                            color = PlatinumBright,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp)
                        )
                    }
                    Surface(
                        color = ObsidianElevated,
                        shape = btnShape,
                        modifier = Modifier
                            .weight(0.85f)
                            .clip(btnShape)
                            .border(1.dp, ObsidianBorder, btnShape)
                            .tvDpadClickable(shape = btnShape) { onOpenLogsClick() }
                    ) {
                        Text(
                            text = "Core Logs",
                            color = PlatinumSilver,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DoctorMetricCard(
    title: String,
    value: String,
    subtitle: String,
    highlight: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ObsidianCard,
        shape = RoundedCornerShape(13.dp),
        modifier = modifier.border(
            width = 1.dp,
            color = if (highlight) ChampagneGold.copy(alpha = 0.45f) else ObsidianBorder,
            shape = RoundedCornerShape(13.dp)
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = title,
                color = PlatinumMuted,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                color = if (highlight) ChampagneLight else PlatinumBright,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                color = PlatinumMuted,
                fontSize = 8.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * تب تنظیمات (Settings): بدون اسکرول عمودی (Zero-Scroll) و شامل آیتم‌های کامل تنظیمات VPN، تعادل بار و سهمیه VIP
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
    val dailyRatio = (dailyRemBytes.toFloat() / dailyMaxBytes.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Settings & VIP Quota",
                color = PlatinumBright,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianElevated)
                    .border(1.dp, ChampagneGold.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "MODE: ${serviceTierMode.shortTitle.uppercase()}",
                    color = ChampagneGold,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // 1) کارت فشرده شناسه سخت‌افزاری + سهمیه روزانه VIP Mode + دکمه جایزه +100MB Start.io و ارتقا به VIP 50GB
        Surface(
            color = ObsidianCard,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "VIP Mode Daily Quota & HW ID",
                            color = ChampagneGold,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                        Text(
                            text = "UUID: ${userUuid.take(16)}... • Left: ${fmtBytes(dailyRemBytes)} / ${fmtBytes(dailyMaxBytes)}",
                            color = PlatinumMuted,
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (!vipActive) {
                            val bonusShape = RoundedCornerShape(8.dp)
                            Surface(
                                color = Color(0xFF28231C),
                                shape = bonusShape,
                                modifier = Modifier
                                    .clip(bonusShape)
                                    .border(1.dp, ChampagneGold.copy(alpha = 0.75f), bonusShape)
                                    .tvDpadClickable(shape = bonusShape) { onWatchSponsorAd() }
                            ) {
                                Text(
                                    text = "+100 MB Free",
                                    color = ChampagneLight,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                )
                            }
                        }
                        val copyShape = RoundedCornerShape(8.dp)
                        Surface(
                            color = ObsidianElevated,
                            shape = copyShape,
                            modifier = Modifier
                                .clip(copyShape)
                                .border(1.dp, ChampagneGold.copy(alpha = 0.55f), copyShape)
                                .tvDpadClickable(shape = copyShape) { onCopyUuid() }
                        ) {
                            Text(
                                text = "Copy ID",
                                color = ChampagneLight,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF262832))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(dailyRatio)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF8E93A2), ChampagneGold)
                                )
                            )
                    )
                }

                Spacer(Modifier.height(5.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val vipShape = RoundedCornerShape(10.dp)
                    Surface(
                        color = ObsidianElevated,
                        shape = vipShape,
                        modifier = Modifier
                            .weight(1.35f)
                            .clip(vipShape)
                            .border(1.dp, ChampagneGold.copy(alpha = 0.65f), vipShape)
                            .tvDpadClickable(shape = vipShape) { onOpenVipDialog() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GlassDiamondIcon(size = 12.dp)
                                Spacer(Modifier.width(5.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (vipActive) "VIP 50 GB Active" else "VIP 50 GB Plan",
                                        color = PlatinumBright,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (vipActive) "Manage license" else "Zero ads • 50GB/mo",
                                        color = PlatinumMuted,
                                        fontSize = 8.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Text(
                                text = "VIP ›",
                                color = ChampagneLight,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    if (!vipActive) {
                        val sponsorBtnShape = RoundedCornerShape(10.dp)
                        Surface(
                            color = Color(0xFF231F1B),
                            shape = sponsorBtnShape,
                            modifier = Modifier
                                .weight(1f)
                                .clip(sponsorBtnShape)
                                .border(1.dp, ChampagneGold.copy(alpha = 0.65f), sponsorBtnShape)
                                .tvDpadClickable(shape = sponsorBtnShape) { onWatchSponsorAd() }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "🎁 +500 MB Quota",
                                    color = ChampagneLight,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Sponsor Bonus (+2h)",
                                    color = PlatinumMuted,
                                    fontSize = 8.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2) آیتم‌های جامع تنظیمات شبکه، تعادل بار و امنیت (۶ سوییچ کلیدی بدون نیاز به اسکرول)
        Surface(
            color = ObsidianCard,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Core VPN & Load Balancer Settings",
                    color = PlatinumBright,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                CompactSettingToggleRow(
                    title = "Smart Server Load Balancer",
                    subtitle = "Distributes users evenly across healthy nodes",
                    checked = loadBalanceEnabled,
                    onCheckedChange = onLoadBalanceChange
                )

                CompactSettingToggleRow(
                    title = "VPN Kill Switch & Leak Guard",
                    subtitle = "Prevents unencrypted traffic leaks on drop",
                    checked = killSwitchEnabled,
                    onCheckedChange = onKillSwitchChange
                )

                CompactSettingToggleRow(
                    title = "Clean DNS & Malware Shield (1.1.1.2)",
                    subtitle = "Encrypted DoH • Blocks trackers & DNS spoofing",
                    checked = cleanDnsShield,
                    onCheckedChange = onCleanDnsShieldChange
                )

                CompactSettingToggleRow(
                    title = "Ghost Mode Messaging-Only Lock",
                    subtitle = "Caps Ghost to 42 KB/s & blocks heavy media",
                    checked = ghostMessagingOnly,
                    onCheckedChange = onGhostMessagingOnlyChange
                )

                CompactSettingToggleRow(
                    title = "Auto-Switch Unresponsive Server",
                    subtitle = "Switches to next balanced node if tunnel drops",
                    checked = autoSwitch,
                    onCheckedChange = onAutoSwitchChange
                )

                CompactSettingToggleRow(
                    title = "Auto-Reconnect & Bypass Domestic (.ir)",
                    subtitle = "Keeps VPN alive & routes local banks directly",
                    checked = autoReconnect && splitTunnelLocal,
                    onCheckedChange = {
                        onAutoReconnectChange(it)
                        onSplitTunnelChange(it)
                    }
                )

                Spacer(Modifier.height(3.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val btnShape = RoundedCornerShape(9.dp)
                    Surface(
                        color = ObsidianElevated,
                        shape = btnShape,
                        modifier = Modifier
                            .weight(1f)
                            .clip(btnShape)
                            .border(1.dp, ObsidianBorder, btnShape)
                            .tvDpadClickable(shape = btnShape) { onOpenLogs() }
                    ) {
                        Text(
                            text = "Activity Logs",
                            color = PlatinumBright,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                    Surface(
                        color = ObsidianElevated,
                        shape = btnShape,
                        modifier = Modifier
                            .weight(1f)
                            .clip(btnShape)
                            .border(1.dp, ChampagneGold.copy(alpha = 0.45f), btnShape)
                            .tvDpadClickable(shape = btnShape) { onOpenUpdate() }
                    ) {
                        Text(
                            text = "Check Update",
                            color = ChampagneLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
            }
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
            Text(
                text = title,
                color = PlatinumBright,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = PlatinumMuted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(modifier = Modifier.scale(0.76f).height(28.dp)) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = metallicSwitchColors()
            )
        }
    }
}
