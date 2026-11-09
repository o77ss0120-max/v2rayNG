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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.v2ray.ang.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
                    "GHOST" -> "GH"
                    "PUB" -> "PUB"
                    else -> geo.isoCode
                }
                val tierLabel = when (currentSrv.codeTag) {
                    "GHOST" -> "GHOST STEALTH"
                    "PUB" -> "PUBLIC"
                    else -> "DEDICATED"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 11.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // باکس استاندارد تک‌خطی کد کشور (NL / PL / DE / GH) بدون هیچ‌گونه بریدگی عمودی
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
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
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = shortBadgeTop,
                            color = if (isOwn) ChampagneLight else PlatinumBright,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentSrv.label,
                                color = PlatinumBright,
                                fontSize = 12.sp,
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
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${geo.countryName} (${geo.cityName}) • Tap to switch server",
                            color = if (isOwn) ChampagneGold.copy(alpha = 0.9f) else PlatinumMuted,
                            fontSize = 9.5.sp,
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

    var adLoaded by remember { mutableStateOf(false) }

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
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (adLoaded) 42.dp else 0.dp),
                    factory = { ctx ->
                        AdView(ctx).apply {
                            setAdSize(AdSize.BANNER)
                            this.adUnitId = adUnitId
                            adListener = object : com.google.android.gms.ads.AdListener() {
                                override fun onAdLoaded() {
                                    adLoaded = true
                                }
                                override fun onAdFailedToLoad(error: LoadAdError) {
                                    adLoaded = false
                                }
                            }
                            loadAd(AdRequest.Builder().build())
                        }
                    }
                )
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
                text = if (isVipMode) "PENNY VIP" else "PENNY VPN",
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
            if (isVipMode) {
                GlassDiamondIcon(size = 11.dp)
                Spacer(Modifier.width(5.dp))
            }
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
        // نوار رادار کشور متصل‌شده (چشمک‌زن روی کشور سرور مثل هلند، لهستان یا آلمان)
        if (countryGeo != null) {
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
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
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
                        text = "${countryGeo.countryName} (${countryGeo.cityName.uppercase()}) • ${countryGeo.coordLabel}",
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
                fontSize = 11.5.sp,
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
 * دکمه اتصال مرکزی مجهز به کره زمین سه‌بعدی (3D Orthographic World Globe) و رادار چشمک‌زن موقعیت کشور سرور (مثلاً هلند NL یا لهستان PL)
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
    val target = when (mode) {
        UiMode.IDLE -> if (isVipMode) ChampagneGold else Color(0xFF9EA2B0)
        UiMode.SCANNING -> PlatinumSilver
        UiMode.CONNECTING -> ChampagneLight
        UiMode.CONNECTED -> ChampagneGold
    }
    val color by animateColorAsState(target, tween(700), label = "pbColor")
    val animProgress by animateFloatAsState(progress, tween(400), label = "pbProgress")

    // تمرکز دوربین سه‌بعدی کره زمین روی میانه مسیر ایران (35.69N, 51.39E) و کشور مقصد (مثلاً هلند یا لهستان)
    val targetCamLon = ((countryGeo.longitude + 51.39f) * 0.45f).coerceIn(-10f, 45f)
    val targetCamLat = ((countryGeo.latitude + 35.69f) * 0.46f).coerceIn(18f, 46f)

    val baseLonDeg by animateFloatAsState(
        targetValue = targetCamLon,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "globeLon"
    )
    val centerLatDeg by animateFloatAsState(
        targetValue = targetCamLat,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "globeLat"
    )

    val catPresence by animateFloatAsState(
        targetValue = if (isConnected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "catPresence"
    )

    val inf = rememberInfiniteTransition(label = "pbInf")
    val pulse by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "pbPulse"
    )
    val globeSway by inf.animateFloat(
        initialValue = -4.5f, targetValue = 4.5f,
        animationSpec = infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "globeSway"
    )
    val beaconBlink by inf.animateFloat(
        initialValue = 0.20f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "beaconBlink"
    )
    val spin by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "pbSpin"
    )
    val breathe by inf.animateFloat(
        initialValue = 1f,
        targetValue = if (isConnected) 1.025f else 1.012f,
        animationSpec = infiniteRepeatable(
            tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ),
        label = "pbBreathe"
    )

    val centerLonDeg = baseLonDeg + globeSway

    Box(
        modifier = Modifier
            .width(236.dp)
            .height(216.dp),
        contentAlignment = Alignment.Center
    ) {
        // ── کره زمین سه‌بعدی کامل در مرکز صفحه (بدون دایره مات وسط)؛ قابل لمس برای اتصال و قطع اتصال ──
        Box(
            modifier = Modifier
                .offset(y = 6.dp)
                .size(188.dp)
                .scale(breathe)
                .clip(CircleShape)
                .tvDpadClickable(shape = CircleShape, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val globeR = size.minDimension * 0.46f

                fun project3D(latDeg: Float, lonDeg: Float): Pair<Offset, Boolean> {
                    val lat = latDeg * (PI.toFloat() / 180f)
                    val lon = (lonDeg - centerLonDeg) * (PI.toFloat() / 180f)
                    val cLat = centerLatDeg * (PI.toFloat() / 180f)
                    val x = globeR * cos(lat) * sin(lon)
                    val y = -globeR * (sin(lat) * cos(cLat) - cos(lat) * sin(cLat) * cos(lon))
                    val z = sin(lat) * sin(cLat) + cos(lat) * cos(cLat) * cos(lon)
                    return Offset(c.x + x, c.y + y) to (z >= -0.02f)
                }

                // ۱) هاله بیرونی اتمسفر کره زمین
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ChampagneGold.copy(alpha = if (isConnected) 0.28f else 0.14f),
                            PlatinumSilver.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        center = c,
                        radius = globeR * 1.15f
                    ),
                    radius = globeR * 1.15f,
                    center = c
                )

                // ۲) بدنه سه‌بعدی کره زمین (اقیانوس‌ها با نورپردازی کروی سه‌بعدی)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = if (isConnected) {
                            listOf(
                                Color(0xFF2B2F3E),
                                Color(0xFF181B26),
                                Color(0xFF0B0D13)
                            )
                        } else {
                            listOf(
                                Color(0xFF232633),
                                Color(0xFF14161F),
                                Color(0xFF090A0F)
                            )
                        },
                        center = Offset(c.x - globeR * 0.28f, c.y - globeR * 0.28f),
                        radius = globeR * 1.35f
                    ),
                    radius = globeR,
                    center = c
                )

                // ۳) مدارهای عرض جغرافیایی (Parallels) هر ۱۵ درجه
                for (latDeg in listOf(-45f, -30f, -15f, 0f, 15f, 30f, 45f, 60f, 75f)) {
                    val path = Path()
                    var started = false
                    for (lonStep in -90..90 step 5) {
                        val (pt, vis) = project3D(latDeg, centerLonDeg + lonStep)
                        if (vis) {
                            if (!started) {
                                path.moveTo(pt.x, pt.y)
                                started = true
                            } else {
                                path.lineTo(pt.x, pt.y)
                            }
                        } else {
                            started = false
                        }
                    }
                    val isEquator = latDeg == 0f
                    drawPath(
                        path = path,
                        color = if (isEquator) ChampagneGold.copy(alpha = 0.26f)
                        else PlatinumSilver.copy(alpha = 0.15f),
                        style = Stroke(width = if (isEquator) 1.1.dp.toPx() else 0.75.dp.toPx())
                    )
                }

                // ۴) نصف‌النهارهای طول جغرافیایی (Meridians) هر ۱۵ درجه
                for (lonOffset in -75..75 step 15) {
                    val path = Path()
                    var started = false
                    for (latStep in -75..75 step 5) {
                        val (pt, vis) = project3D(latStep.toFloat(), centerLonDeg + lonOffset)
                        if (vis) {
                            if (!started) {
                                path.moveTo(pt.x, pt.y)
                                started = true
                            } else {
                                path.lineTo(pt.x, pt.y)
                            }
                        } else {
                            started = false
                        }
                    }
                    drawPath(
                        path = path,
                        color = PlatinumSilver.copy(alpha = 0.15f),
                        style = Stroke(width = 0.75.dp.toPx())
                    )
                }

                // ۵) ترسیم قاره‌های کره زمین (اروپا، ایران و خاورمیانه، اسکاندیناوی و شمال آفریقا)
                fun drawContinentPolygon(coords: List<Pair<Float, Float>>, fillAlpha: Float, strokeAlpha: Float) {
                    val poly = Path()
                    var count = 0
                    for ((lat, lon) in coords) {
                        val (pt, vis) = project3D(lat, lon)
                        if (vis) {
                            if (count == 0) poly.moveTo(pt.x, pt.y)
                            else poly.lineTo(pt.x, pt.y)
                            count++
                        }
                    }
                    if (count >= 3) {
                        poly.close()
                        drawPath(
                            path = poly,
                            color = ChampagneGold.copy(alpha = fillAlpha)
                        )
                        drawPath(
                            path = poly,
                            color = ChampagneLight.copy(alpha = strokeAlpha),
                            style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // قاره اروپا (پرتغال/اسپانیا، فرانسه، هلند، آلمان، لهستان، بالتیک، ایتالیا، بالکان)
                val europeOutline = listOf(
                    36.5f to -9.0f, 43.3f to -8.4f, 48.4f to -4.5f, 51.0f to 1.8f,
                    52.4f to 4.9f, 54.5f to 8.5f, 54.8f to 14.0f, 54.7f to 20.5f,
                    59.4f to 24.7f, 55.7f to 37.6f, 47.2f to 39.7f, 46.4f to 30.7f,
                    41.0f to 28.9f, 38.0f to 23.7f, 40.8f to 14.2f, 44.4f to 8.9f,
                    43.3f to 5.4f, 41.4f to 2.1f
                )
                // شبه‌جزیره اسکاندیناوی و بریتانیا
                val scandinaviaOutline = listOf(
                    55.6f to 12.6f, 58.0f to 7.0f, 62.0f to 5.5f, 68.0f to 15.0f,
                    70.0f to 25.0f, 65.8f to 24.1f, 60.1f to 24.9f, 59.3f to 18.0f
                )
                val ukOutline = listOf(
                    50.1f to -5.2f, 54.6f to -5.9f, 58.6f to -3.1f, 52.6f to 1.7f, 51.1f to 1.3f
                )
                // ایران، ترکیه و خاورمیانه
                val iranMiddleEastOutline = listOf(
                    41.0f to 29.0f, 41.6f to 41.6f, 38.4f to 48.8f, 37.0f to 54.0f,
                    36.2f to 59.6f, 31.3f to 61.5f, 25.3f to 60.6f, 26.6f to 56.2f,
                    29.9f to 48.5f, 24.5f to 54.4f, 21.5f to 39.2f, 30.0f to 32.5f,
                    36.8f to 36.2f
                )
                // شمال آفریقا
                val northAfricaOutline = listOf(
                    35.8f to -5.8f, 36.8f to 10.2f, 32.9f to 13.2f, 31.2f to 29.9f,
                    24.0f to 32.9f, 22.0f to 15.0f, 28.0f to -10.0f
                )

                val landFill = if (isConnected) 0.24f else 0.16f
                val landStroke = if (isConnected) 0.55f else 0.36f
                drawContinentPolygon(europeOutline, landFill, landStroke)
                drawContinentPolygon(scandinaviaOutline, landFill, landStroke)
                drawContinentPolygon(ukOutline, landFill, landStroke)
                drawContinentPolygon(iranMiddleEastOutline, landFill, landStroke)
                drawContinentPolygon(northAfricaOutline, landFill * 0.8f, landStroke * 0.8f)

                // شبکه نقاط شهرها و مراکز داده روی قاره‌ها
                val worldDataPoints = listOf(
                    52.37f to 4.90f, 52.23f to 21.01f, 50.11f to 8.68f, 48.85f to 2.35f,
                    51.50f to -0.12f, 41.90f to 12.49f, 40.41f to -3.70f, 59.32f to 18.06f,
                    55.67f to 12.56f, 47.37f to 8.54f, 48.20f to 16.37f, 50.07f to 14.43f,
                    60.17f to 24.94f, 35.69f to 51.39f, 32.65f to 51.66f, 29.59f to 52.58f,
                    38.08f to 46.29f, 36.29f to 59.60f, 41.00f to 28.97f, 25.20f to 55.27f
                )
                for ((lat, lon) in worldDataPoints) {
                    val (pt, vis) = project3D(lat, lon)
                    if (vis) {
                        drawCircle(
                            color = PlatinumBright.copy(alpha = 0.42f),
                            radius = 1.6.dp.toPx(),
                            center = pt
                        )
                    }
                }

                // ۶) کمان متحرک مسیر اتصال از ایران (تهران 35.69N, 51.39E) به کشور سرور مقصد (مثلاً هلند یا لهستان)
                val (originPt, originVis) = project3D(35.69f, 51.39f)
                val (targetPt, targetVis) = project3D(countryGeo.latitude, countryGeo.longitude)
                if (originVis && targetVis) {
                    val midX = (originPt.x + targetPt.x) / 2f
                    val midY = minOf(originPt.y, targetPt.y) - 24.dp.toPx()
                    val arcPath = Path().apply {
                        moveTo(originPt.x, originPt.y)
                        quadraticBezierTo(midX, midY, targetPt.x, targetPt.y)
                    }
                    // هاله زیرین کمان مسیر
                    drawPath(
                        path = arcPath,
                        color = ChampagneGold.copy(alpha = if (isConnected) 0.32f else 0.18f),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // خط کمان متحرک نقطه‌چین
                    drawPath(
                        path = arcPath,
                        color = if (isConnected) ChampagneLight else ChampagneGold.copy(alpha = 0.78f),
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 6f), phase = -pulse * 30f)
                        )
                    )

                    // دو بسته داده نورانی متحرک روی کمان از ایران به سمت کشور مقصد
                    for (pktIdx in 0..1) {
                        val tPkt = (pulse + pktIdx * 0.5f) % 1f
                        val oneMinusT = 1f - tPkt
                        val pktX = oneMinusT * oneMinusT * originPt.x +
                            2f * oneMinusT * tPkt * midX +
                            tPkt * tPkt * targetPt.x
                        val pktY = oneMinusT * oneMinusT * originPt.y +
                            2f * oneMinusT * tPkt * midY +
                            tPkt * tPkt * targetPt.y
                        drawCircle(
                            color = ChampagneGold.copy(alpha = 0.45f),
                            radius = 5.dp.toPx(),
                            center = Offset(pktX, pktY)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.2.dp.toPx(),
                            center = Offset(pktX, pktY)
                        )
                    }

                    // نقطه مبدأ (ایران)
                    drawCircle(
                        color = PlatinumSilver.copy(alpha = 0.35f),
                        radius = 6.dp.toPx(),
                        center = originPt
                    )
                    drawCircle(
                        color = PlatinumBright,
                        radius = 3.2.dp.toPx(),
                        center = originPt
                    )
                }

                // ۷) نقطه رادار چشمک‌زن روی موقعیت دقیق کشور سرور (هلند / لهستان / آلمان)
                if (targetVis) {
                    for (ringIdx in 0..1) {
                        val rPhase = (pulse + ringIdx * 0.5f) % 1f
                        val pulseRingR = 5.dp.toPx() + rPhase * 22.dp.toPx()
                        drawCircle(
                            color = ChampagneGold.copy(alpha = (1f - rPhase) * 0.90f),
                            radius = pulseRingR,
                            center = targetPt,
                            style = Stroke(width = (2.0f - rPhase).dp.toPx())
                        )
                    }
                    // هاله چشمک‌زن مرکزی رادار
                    drawCircle(
                        color = ChampagneGold.copy(alpha = 0.48f * beaconBlink),
                        radius = 10.dp.toPx(),
                        center = targetPt
                    )
                    // هسته درخشان چشمک‌زن کشور سرور
                    drawCircle(
                        color = Color.White,
                        radius = 4.8.dp.toPx() * (0.85f + 0.30f * beaconBlink),
                        center = targetPt
                    )
                    drawCircle(
                        color = ChampagneGold,
                        radius = 2.6.dp.toPx(),
                        center = targetPt
                    )
                }

                // ۸) حلقه محیطی کروی و حلقه وضعیت اتصال/اسکن دور کره زمین
                drawCircle(
                    brush = Brush.linearGradient(
                        if (isConnected) {
                            listOf(Color.White, ChampagneGold, ChampagneBronze)
                        } else {
                            listOf(ChampagneGold.copy(alpha = 0.78f), PlatinumSilver.copy(alpha = 0.45f), Color(0xFF3A3D4A))
                        }
                    ),
                    radius = globeR,
                    center = c,
                    style = Stroke(width = if (isConnected) 2.4.dp.toPx() else 1.6.dp.toPx())
                )

                when (mode) {
                    UiMode.CONNECTED, UiMode.CONNECTING -> {
                        val orbitR = globeR + 6.dp.toPx()
                        val tl = Offset(c.x - orbitR, c.y - orbitR)
                        val sz = Size(orbitR * 2f, orbitR * 2f)
                        drawArc(
                            color = ChampagneGold.copy(alpha = 0.75f),
                            startAngle = spin,
                            sweepAngle = 65f,
                            useCenter = false,
                            topLeft = tl,
                            size = sz,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = PlatinumSilver.copy(alpha = 0.65f),
                            startAngle = spin + 180f,
                            sweepAngle = 65f,
                            useCenter = false,
                            topLeft = tl,
                            size = sz,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    UiMode.SCANNING -> {
                        val rr = globeR + 5.dp.toPx()
                        val tl = Offset(c.x - rr, c.y - rr)
                        val sz = Size(rr * 2f, rr * 2f)
                        drawArc(
                            color = color.copy(alpha = 0.22f), startAngle = 0f, sweepAngle = 360f,
                            useCenter = false, topLeft = tl, size = sz, style = Stroke(3.dp.toPx())
                        )
                        drawArc(
                            color = ChampagneGold, startAngle = -90f, sweepAngle = 360f * animProgress,
                            useCenter = false, topLeft = tl, size = sz,
                            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = PlatinumBright, startAngle = spin, sweepAngle = 45f,
                            useCenter = false, topLeft = tl, size = sz,
                            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    else -> {}
                }
            }
        }

        // کپسول شیشه‌ای باریک در لبه پایینی کره زمین (بدون پوشاندن نقشه، مدارها، کمان اتصال و رادار چشمک‌زن)
        Surface(
            color = if (isConnected) Color(0xFF1E1B16).copy(alpha = 0.92f)
            else Color(0xFF15171F).copy(alpha = 0.90f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-2).dp)
                .border(
                    1.dp,
                    if (isConnected) ChampagneGold.copy(alpha = 0.88f)
                    else PlatinumSilver.copy(alpha = 0.45f),
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
                        .clip(CircleShape)
                        .background(
                            if (isConnected) ChampagneGold.copy(alpha = beaconBlink)
                            else PlatinumSilver
                        )
                )
                Text(
                    text = when (mode) {
                        UiMode.IDLE -> if (isVipMode) "TAP GLOBE • VIP CONNECT" else "TAP GLOBE • CONNECT"
                        UiMode.SCANNING -> "SCANNING NODES..."
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

        if (catPresence > 0.01f) {
            Image(
                painter = painterResource(id = R.drawable.penny_cat_peeking),
                contentDescription = "Peeking Cat on 3D Earth Globe",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(68.dp)
                    .height(78.dp)
                    .offset(y = (-8).dp)
                    .graphicsLayer {
                        alpha = catPresence.coerceIn(0f, 1f)
                        translationY = (1f - catPresence) * 20.dp.toPx()
                        val sc = 0.82f + 0.18f * catPresence
                        scaleX = sc
                        scaleY = sc
                    }
                    .clickable(onClick = onClick)
            )
        }
    }
}

/**
 * انتخاب‌گر ۳ حالت سرویس (Public / Dedicated / Ghost Mode) با تم یکپارچه طلایی-شامپاینی و پلاتینیوم
 * همراه با نشانگر اختصاصی وضعیت Ghost Mode و سوییچ محدودکننده ترافیک به پیام‌رسان‌های اضطراری
 */
@Composable
internal fun ServiceTierSelectorBar(
    selectedMode: ServiceTierMode,
    dailyRemText: String,
    isVip: Boolean,
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

    Surface(
        color = ObsidianCard.copy(alpha = 0.96f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
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
                                    "Dedicated Pro • $dailyRemText"
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
}

@Composable
internal fun PennyBottomNavBar(
    selectedTab: BottomNavTab,
    serverCount: Int,
    onSelectTab: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF121319).copy(alpha = 0.98f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = ObsidianBorder,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavTab.values().forEach { tab ->
                val active = selectedTab == tab
                val fg = if (active) ChampagneGold else PlatinumMuted
                val shape = RoundedCornerShape(12.dp)
                Surface(
                    color = if (active) ObsidianElevated else Color.Transparent,
                    shape = shape,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .clip(shape)
                        .then(
                            if (active) Modifier.border(1.dp, ChampagneGold.copy(alpha = 0.45f), shape)
                            else Modifier
                        )
                        .tvDpadClickable(shape = shape) { onSelectTab(tab) }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Canvas(modifier = Modifier.size(15.dp)) {
                            val w = size.width
                            val h = size.height
                            val st = 1.65.dp.toPx()
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
                                BottomNavTab.PROFILE -> {
                                    drawCircle(
                                        color = fg,
                                        radius = w * 0.22f,
                                        center = Offset(w * 0.5f, h * 0.32f),
                                        style = Stroke(width = st)
                                    )
                                    drawArc(
                                        color = fg,
                                        startAngle = 200f,
                                        sweepAngle = 140f,
                                        useCenter = false,
                                        topLeft = Offset(w * 0.18f, h * 0.54f),
                                        size = Size(w * 0.64f, h * 0.44f),
                                        style = Stroke(width = st, cap = StrokeCap.Round)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (tab == BottomNavTab.SERVERS && serverCount > 0) {
                                "${tab.title} ($serverCount)"
                            } else {
                                tab.title
                            },
                            color = if (active) PlatinumBright else PlatinumMuted,
                            fontSize = 11.5.sp,
                            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * تب سرورها: هدر و انتخاب‌گر حالت ثابت هستند و فقط لیست سرورها اسکرول می‌شود
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
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Verified stealth nodes • Zero config leak",
                    color = PlatinumMuted,
                    fontSize = 10.5.sp
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
                        fontSize = 11.sp,
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
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = "Tap 'Scan Mode' above to fetch and verify healthy nodes.",
                            color = PlatinumMuted,
                            fontSize = 11.sp,
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
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                itemsIndexed(servers, key = { _, s -> s.guid }) { idx, srv ->
                    val isSelected = isRunning && srv.guid == selectedGuid
                    val geo = getCountryGeoInfo(srv.codeTag)
                    val tierBadge = when (srv.codeTag) {
                        "DED-NL" -> "REALITY • NL"
                        "DED-PL" -> "STEALTH • PL"
                        "GHOST" -> "GHOST • CHAT"
                        "VPS-1" -> "DEDICATED • DE"
                        else -> "PUBLIC • FREE"
                    }
                    val isOwn = isDedicatedServerTag(srv.codeTag)
                    val itemShape = RoundedCornerShape(14.dp)

                    Surface(
                        color = if (isSelected) ObsidianElevated else ObsidianCard,
                        shape = itemShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(itemShape)
                            .border(
                                width = if (isSelected) 1.4.dp else 1.dp,
                                color = if (isSelected) ChampagneGold else ObsidianBorder,
                                shape = itemShape
                            )
                            .tvDpadClickable(enabled = !busy, shape = itemShape) { onServerClick(srv) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(42.dp)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(
                                        if (isOwn) Color(0xFF2D2720) else Color(0xFF22242E)
                                    )
                                    .border(
                                        1.dp,
                                        if (isOwn) ChampagneGold.copy(alpha = 0.7f) else Color(0xFF454857),
                                        RoundedCornerShape(9.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (srv.codeTag == "GHOST") "GH" else geo.isoCode,
                                    color = if (isOwn) ChampagneLight else PlatinumBright,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Spacer(Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = srv.label.ifBlank { "${selectedMode.shortTitle} Node #${idx + 1}" },
                                        color = PlatinumBright,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(ChampagneGold.copy(alpha = if (isOwn) 0.18f else 0.08f))
                                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                                    ) {
                                        Text(
                                            text = tierBadge,
                                            color = if (isOwn) ChampagneGold else PlatinumSilver,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (isSelected) {
                                        "Active Tunnel • ${geo.countryName} (${geo.coordLabel})"
                                    } else {
                                        "${geo.countryName} • ${geo.cityName} • Tap to connect"
                                    },
                                    color = if (isSelected) ChampagneGold else PlatinumMuted,
                                    fontSize = 9.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (srv.ms > 0) "${srv.ms} ms" else "READY",
                                    color = if (isOwn) ChampagneGold else PlatinumSilver,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = if (isSelected) "ACTIVE" else "CONNECT ›",
                                    color = if (isSelected) ChampagneLight else PlatinumMuted,
                                    fontSize = 8.5.sp,
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
 * تب پروفایل: بدون اسکرول عمودی (Zero-Scroll) و با رفع کامل مشکل فشرده شدن دکمه ACTIVATE ›
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
    onCopyUuid: () -> Unit,
    onOpenVipDialog: () -> Unit,
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
                text = "User Profile & Quota",
                color = PlatinumBright,
                fontSize = 18.sp,
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
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // 1) کارت شناسه اختصاصی کاربر (UUID + Hardware Fingerprint)
        Surface(
            color = ObsidianCard,
            shape = RoundedCornerShape(15.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ObsidianBorder, RoundedCornerShape(15.dp))
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dedicated User Identity",
                            color = ChampagneGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                        Text(
                            text = "HW ID: $deviceId • Anti-Reset Bound",
                            color = PlatinumMuted,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                    val copyShape = RoundedCornerShape(9.dp)
                    Surface(
                        color = ObsidianElevated,
                        shape = copyShape,
                        modifier = Modifier
                            .clip(copyShape)
                            .border(1.dp, ChampagneGold.copy(alpha = 0.55f), copyShape)
                            .tvDpadClickable(shape = copyShape) { onCopyUuid() }
                    ) {
                        Text(
                            text = "Copy UUID",
                            color = ChampagneLight,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = ObsidianDeep,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF282A34), RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = userUuid,
                        color = PlatinumSilver,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 2) کارت سهمیه ۱ گیگابایت روزانه سرویس اختصاصی + دکمه اصلاح‌شده ACTIVATE VIP
        Surface(
            color = ObsidianCard,
            shape = RoundedCornerShape(15.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ObsidianBorder, RoundedCornerShape(15.dp))
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dedicated Pro Daily Quota",
                        color = PlatinumBright,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Resets at 00:00",
                        color = ChampagneGold,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Used: ${fmtBytes(dailyUsedBytes)}",
                        color = PlatinumMuted,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "Left: ${fmtBytes(dailyRemBytes)} / ${fmtBytes(dailyMaxBytes)}",
                        color = ChampagneLight,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
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

                Spacer(Modifier.height(8.dp))
                val vipShape = RoundedCornerShape(11.dp)
                Surface(
                    color = ObsidianElevated,
                    shape = vipShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(vipShape)
                        .border(1.dp, ChampagneGold.copy(alpha = 0.65f), vipShape)
                        .tvDpadClickable(shape = vipShape) { onOpenVipDialog() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // استفاده از Modifier.weight(1f) تا متن سمت چپ هرگز دکمه ACTIVATE › را فشرده نکند
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlassDiamondIcon(size = 14.dp)
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (vipActive) "VIP 50 GB Plan Active" else "Upgrade to VIP (50 GB / Month)",
                                    color = PlatinumBright,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val vipSub = if (vipActive) {
                                    val remD = ((vipExpireMs - System.currentTimeMillis()) / (24L * 3600_000L)).coerceAtLeast(0L)
                                    val remB = (vipMaxBytes - vipUsedBytes).coerceAtLeast(0L)
                                    "$remD days • ${fmtBytes(remB)} remaining"
                                } else {
                                    "50 GB dedicated traffic & zero ads"
                                }
                                Text(
                                    text = vipSub,
                                    color = PlatinumMuted,
                                    fontSize = 9.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(ChampagneGold.copy(alpha = 0.18f))
                                .border(1.dp, ChampagneGold.copy(alpha = 0.65f), RoundedCornerShape(7.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (vipActive) "MANAGE" else "ACTIVATE ›",
                                color = ChampagneLight,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // 3) تنظیمات Ghost Mode و حفاظت شبکه (فشرده و بدون اسکرول)
        Surface(
            color = ObsidianCard,
            shape = RoundedCornerShape(15.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ObsidianBorder, RoundedCornerShape(15.dp))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Ghost Protocol & Protection",
                    color = PlatinumBright,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                CompactSettingToggleRow(
                    title = "Ghost Mode Messaging-Only Lock",
                    subtitle = "Caps Ghost speed to 42 KB/s & blocks video/updates",
                    checked = ghostMessagingOnly,
                    onCheckedChange = onGhostMessagingOnlyChange
                )

                CompactSettingToggleRow(
                    title = "Auto-Switch Unresponsive Server",
                    subtitle = "Switches node if tunnel download drops",
                    checked = autoSwitch,
                    onCheckedChange = onAutoSwitchChange
                )

                CompactSettingToggleRow(
                    title = "Auto-Reconnect & Bypass Domestic (.ir)",
                    subtitle = "Keeps VPN alive & routes banks directly",
                    checked = autoReconnect && splitTunnelLocal,
                    onCheckedChange = {
                        onAutoReconnectChange(it)
                        onSplitTunnelChange(it)
                    }
                )

                Spacer(Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val btnShape = RoundedCornerShape(10.dp)
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
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 7.dp)
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
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 7.dp)
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
