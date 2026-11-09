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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
                    Color(0xFFE4E9F2).copy(alpha = 0.78f),
                    Color(0xFFB4C3D8).copy(alpha = 0.55f)
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

        val leftLowerFacet = Path().apply {
            moveTo(w * 0.04f, h * 0.38f)
            lineTo(w * 0.32f, h * 0.38f)
            lineTo(w * 0.50f, h * 0.92f)
            close()
        }
        drawPath(path = leftLowerFacet, color = Color(0xFFD0D8E8).copy(alpha = 0.42f))

        drawLine(
            color = Color(0xFFFFFFFF).copy(alpha = 0.85f),
            start = Offset(w * 0.04f, h * 0.38f),
            end = Offset(w * 0.96f, h * 0.38f),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = Color(0xFFFFFFFF).copy(alpha = 0.75f),
            start = Offset(w * 0.24f, h * 0.14f),
            end = Offset(w * 0.32f, h * 0.38f),
            strokeWidth = 0.9.dp.toPx()
        )
        drawLine(
            color = Color(0xFFFFFFFF).copy(alpha = 0.75f),
            start = Offset(w * 0.76f, h * 0.14f),
            end = Offset(w * 0.68f, h * 0.38f),
            strokeWidth = 0.9.dp.toPx()
        )
        drawLine(
            color = Color(0xFFEFF3FA).copy(alpha = 0.70f),
            start = Offset(w * 0.32f, h * 0.38f),
            end = Offset(w * 0.50f, h * 0.92f),
            strokeWidth = 0.9.dp.toPx()
        )
        drawLine(
            color = Color(0xFFEFF3FA).copy(alpha = 0.70f),
            start = Offset(w * 0.68f, h * 0.38f),
            end = Offset(w * 0.50f, h * 0.92f),
            strokeWidth = 0.9.dp.toPx()
        )

        drawPath(
            path = outerPath,
            color = Color(0xFFFFFFFF).copy(alpha = 0.92f),
            style = Stroke(width = 1.15.dp.toPx())
        )
    }
}

@Composable
internal fun MenuLinesIcon(
    size: Dp = 15.dp,
    color: Color = Color(0xFFE8ECF4),
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
    color: Color = Color(0xFFE2E5EE),
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
    checkedThumbColor = Color(0xFFE4E7F0),
    checkedTrackColor = Color(0xFF3B3E48),
    checkedBorderColor = Color(0xFF5A5E6B),
    uncheckedThumbColor = Color(0xFF7E828E),
    uncheckedTrackColor = Color(0xFF121317),
    uncheckedBorderColor = Color(0xFF2C2E36)
)

@Composable
internal fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color(0xFFF2F4F8), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = Color(0xFF9EA1AB), fontSize = 10.sp)
    }
}

@Composable
internal fun StaggeredServerItem(
    index: Int,
    content: @Composable () -> Unit
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(index) {
        delay((index * 55L).coerceAtMost(550L))
        anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = anim.value
                translationY = (1f - anim.value) * 34f
                val sc = 0.95f + 0.05f * anim.value
                scaleX = sc
                scaleY = sc
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
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            tween(1100, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "pingDotScale"
    )

    // رنگ‌بندی هماهنگ با بخش Service Quality (نقره‌ای پلاتینی و شامپاینی درخشان، بدون رنگ سبز)
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
                width = 1.1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        Color(0xFF8E93A2).copy(alpha = 0.65f + 0.30f * glowAlpha),
                        Color(0xFFD8DCE6).copy(alpha = 0.95f),
                        Color(0xFFE6CFAF).copy(alpha = 0.65f + 0.30f * glowAlpha)
                    )
                ),
                shape = RoundedCornerShape(9.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (showDiamond) {
                GlassDiamondIcon(size = 11.dp)
            }
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .scale(dotScale)
                    .clip(CircleShape)
                    .background(Color(0xFFE6CFAF).copy(alpha = glowAlpha))
            )
            Text(
                text = "$ms ms",
                color = Color(0xFFECEEF4),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color(0xFFD8DCE6).copy(alpha = 0.75f * glowAlpha),
                        offset = Offset(0f, 0f),
                        blurRadius = 9f
                    )
                )
            )
        }
    }
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
    val borderColor = when {
        isConnected -> Color(0xFFD8DCE6).copy(alpha = 0.85f)
        isVipMode -> Color(0xFFD8C7B0).copy(alpha = 0.65f)
        else -> Color(0xFF3A3B44).copy(alpha = 0.85f)
    }

    Surface(
        color = Color(0xFF1A1B20).copy(alpha = 0.94f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled) { onClick() }
    ) {
        Crossfade(
            targetState = server,
            animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
            label = "serverCardFade"
        ) { currentSrv ->
            if (currentSrv == null) {
                Text(
                    text = Strings.noServerYet(lang, isVipMode),
                    color = Color(0xFF9EA1AB),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isOwn = isDedicatedServerTag(currentSrv.codeTag)
                    val tagText = currentSrv.codeTag.ifEmpty { "CF-1" }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(
                                Brush.linearGradient(
                                    if (isOwn) {
                                        listOf(Color(0xFF383024), Color(0xFF221E18))
                                    } else {
                                        listOf(Color(0xFF282A34), Color(0xFF1B1C23))
                                    }
                                )
                            )
                            .border(
                                1.2.dp,
                                if (isOwn) Color(0xFFE6CFAF).copy(alpha = 0.88f)
                                else Color(0xFF565A68),
                                RoundedCornerShape(11.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tagText,
                            color = if (isOwn) Color(0xFFF5E6D3) else Color(0xFFB8BDCB),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = currentSrv.label,
                                color = Color(0xFFF2F4F8),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(
                                        if (isOwn) Color(0xFFE6CFAF).copy(alpha = 0.22f)
                                        else Color(0xFF323542)
                                    )
                                    .border(
                                        0.8.dp,
                                        if (isOwn) Color(0xFFE6CFAF).copy(alpha = 0.75f)
                                        else Color(0xFF5A5E6E),
                                        RoundedCornerShape(5.dp)
                                    )
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = if (isOwn) "DEDICATED" else "PUBLIC",
                                    color = if (isOwn) Color(0xFFF5E6D3) else Color(0xFFB4B9C7),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = when {
                                isOwn && isConnected -> "Dedicated Cloudflare ($tagText) • High-Speed Stream"
                                isOwn -> "Our Dedicated Cloudflare Edge ($tagText) • Tap to switch"
                                isConnected -> "Public Shared Community Node • Tap to switch to OWN"
                                else -> "Public Community Server • Shared Bandwidth"
                            },
                            color = if (isOwn) Color(0xFFD8C7B0) else Color(0xFF9EA1AB),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(6.dp))

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
        color = Color(0xFF17181E).copy(alpha = 0.95f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF32343E), RoundedCornerShape(14.dp))
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
                    color = if (active) Color(0xFF2B2E38) else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .then(
                            if (active) Modifier.border(
                                1.dp,
                                Color(0xFFD8DCE6).copy(alpha = 0.55f),
                                RoundedCornerShape(10.dp)
                            ) else Modifier
                        )
                        .clickable { onSelect(prof) }
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = prof.label,
                            color = if (active) Color(0xFFF2F4F8) else Color(0xFF9EA1AB),
                            fontSize = 11.sp,
                            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Text(
                            text = prof.tag,
                            color = if (active) Color(0xFFD8C7B0) else Color(0xFF6E717C),
                            fontSize = 8.5.sp,
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
        color = Color(0xFF18191F).copy(alpha = 0.95f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isConnected) Color(0xFF4A4E5C) else Color(0xFF32343E),
                RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 10.dp)
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
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) Color(0xFFD8DCE6) else Color(0xFFC8B8A2))
                    )
                    Text(
                        text = "Service Quality: $qualityLabel",
                        color = Color(0xFFECEEF4),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF252730))
                        .border(1.dp, Color(0xFF484B57), RoundedCornerShape(7.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = liveBadgeText,
                        color = Color(0xFFE2E5EE),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.height(7.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF262830))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(qualityScore)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF8E93A2), Color(0xFFD8DCE6), Color(0xFFE6CFAF))
                            )
                        )
                )
            }

            Spacer(Modifier.height(5.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Fragment: Active • Iran Direct",
                    color = Color(0xFF9497A1),
                    fontSize = 9.5.sp
                )
                Text(
                    text = buildString {
                        append(if (cleanDnsShield) "DoH 1.1.1.1" else "Standard DNS")
                        if (splitTunnelLocal) append(" • Bypass LAN")
                        append(" • ${serverCount.coerceAtLeast(0)} Nodes")
                    },
                    color = Color(0xFFC8CCD6),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (showAds) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (adLoaded) 50.dp else 0.dp),
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
            delay(180)
            titleAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing)
            )
        }
        launch {
            delay(360)
            subAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing)
            )
        }
        launch {
            delay(540)
            footerAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing)
            )
        }
        delay(2450)
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
                        Color(0xFF0B0B0E)
                    )
                )
            )
            .clickable { onFinished() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(252.dp)
                    .graphicsLayer {
                        val p = logoAnim.value
                        alpha = p.coerceIn(0f, 1f)
                        val sc = 0.42f + 0.58f * p
                        scaleX = sc
                        scaleY = sc
                        translationY = (1f - p) * -42f
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val arcR = size.minDimension * 0.43f
                    val tl = Offset(center.x - arcR, center.y - arcR)
                    val sz = Size(arcR * 2f, arcR * 2f)
                    drawArc(
                        color = Color(0xFFD8DCE6).copy(alpha = 0.78f),
                        startAngle = orbitAngle,
                        sweepAngle = 95f,
                        useCenter = false,
                        topLeft = tl,
                        size = sz,
                        style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Color(0xFFE6CFAF).copy(alpha = 0.62f),
                        startAngle = orbitAngle + 180f,
                        sweepAngle = 95f,
                        useCenter = false,
                        topLeft = tl,
                        size = sz,
                        style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Image(
                    painter = painterResource(id = R.drawable.penny_cat_logo),
                    contentDescription = "Penny VPN Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(198.dp)
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = "PENNY VPN",
                color = Color(0xFFF2F4F8),
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 4.sp,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color(0xFFD0D4DF),
                        offset = Offset(0f, 0f),
                        blurRadius = glowPulse
                    )
                ),
                modifier = Modifier.graphicsLayer {
                    val p = titleAnim.value
                    alpha = p
                    translationY = (1f - p) * 32f
                    val sc = 0.86f + 0.14f * p
                    scaleX = sc
                    scaleY = sc
                }
            )
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.graphicsLayer {
                    val p = subAnim.value
                    alpha = p
                    translationY = (1f - p) * 26f
                }
            ) {
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(1.5.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color(0xFFE6CFAF), Color(0xFFFFFFFF))
                            )
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = Strings.splashTagline(lang),
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontStyle = FontStyle.Italic,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 1.4.sp,
                    style = TextStyle(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFFFFFF),
                                Color(0xFFF7E8D2),
                                Color(0xFFE6CFAF),
                                Color(0xFFFFFFFF)
                            )
                        ),
                        shadow = Shadow(
                            color = Color(0xFFE6CFAF).copy(alpha = 0.95f),
                            offset = Offset(0f, 0f),
                            blurRadius = glowPulse * 0.9f
                        )
                    )
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(1.5.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFFFFFFF), Color(0xFFE6CFAF), Color.Transparent)
                            )
                        )
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "powered by Omidss",
                color = Color(0xFFD8C7B0),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                modifier = Modifier.graphicsLayer {
                    val p = footerAnim.value
                    alpha = p
                    translationY = (1f - p) * 20f
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
        isVipMode && mode == UiMode.IDLE -> Color(0xFFE6CFAF)
        mode == UiMode.IDLE -> Color(0xFFB8BDC8)
        mode == UiMode.SCANNING -> Color(0xFFD0D4DF)
        mode == UiMode.CONNECTING -> Color(0xFFD8DCE6)
        else -> Color(0xFFEEF1F7)
    }
    val neonColor by animateColorAsState(glowTarget, tween(700), label = "neonColor")
    val inf = rememberInfiniteTransition(label = "headerNeon")
    val blurRadius by inf.animateFloat(
        initialValue = 10f,
        targetValue = 22f,
        animationSpec = infiniteRepeatable(
            tween(1600, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "neonBlur"
    )
    val sloganGlowAlpha by inf.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            tween(1350, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "sloganGlowAlpha"
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
                    .size(38.dp)
                    .clip(CircleShape)
                    .border(1.2.dp, neonColor.copy(alpha = 0.75f), CircleShape)
                    .clickable { onLogoClick() }
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = if (isVipMode) "PENNY VIP" else "PENNY VPN",
                color = Color(0xFFF2F4F8),
                fontSize = 23.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.5.sp,
                style = TextStyle(
                    shadow = Shadow(
                        color = neonColor,
                        offset = Offset(0f, 0f),
                        blurRadius = blurRadius
                    )
                )
            )
        }
        Spacer(Modifier.height(5.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(1.3.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color(0xFFE6CFAF).copy(alpha = sloganGlowAlpha),
                                Color(0xFFFFFFFF).copy(alpha = sloganGlowAlpha)
                            )
                        )
                    )
            )
            Spacer(Modifier.width(7.dp))
            if (isVipMode) {
                GlassDiamondIcon(size = 12.dp)
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = Strings.subtitle(lang, isVipMode),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                fontStyle = FontStyle.Italic,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 1.25.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFFFFFF),
                            Color(0xFFF7E8D2),
                            Color(0xFFE6CFAF),
                            Color(0xFFFFFFFF)
                        )
                    ),
                    shadow = Shadow(
                        color = Color(0xFFE6CFAF).copy(alpha = 0.92f * sloganGlowAlpha),
                        offset = Offset(0f, 0f),
                        blurRadius = blurRadius * 0.95f
                    )
                )
            )
            Spacer(Modifier.width(7.dp))
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(1.3.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFFFFFFFF).copy(alpha = sloganGlowAlpha),
                                Color(0xFFE6CFAF).copy(alpha = sloganGlowAlpha),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}

@Composable
internal fun PulsingStatusText(
    mode: UiMode,
    statusMessage: String,
    lang: AppLang,
    isVip: Boolean,
    dailyRemainingText: String? = null
) {
    val isConnected = mode == UiMode.CONNECTED
    val inf = rememberInfiniteTransition(label = "statusPulse")
    val pulseScale by inf.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isConnected) 1.06f else 1.0f,
        animationSpec = infiniteRepeatable(
            tween(1100, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "statusScale"
    )
    val pulseGlow by inf.animateFloat(
        initialValue = 8f,
        targetValue = if (isConnected) 20f else 8f,
        animationSpec = infiniteRepeatable(
            tween(1100, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "statusGlow"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Crossfade(
            targetState = if (isConnected) Strings.connectedBadge(lang, isVip) else statusMessage,
            label = "status"
        ) { msg ->
            Text(
                text = msg,
                color = if (isConnected) Color(0xFFF2F4F8) else Color(0xFFD0D4DF),
                fontSize = 14.sp,
                fontWeight = if (isConnected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                style = if (isConnected) {
                    TextStyle(
                        shadow = Shadow(
                            color = Color(0xFFD8DCE6).copy(alpha = 0.85f),
                            offset = Offset(0f, 0f),
                            blurRadius = pulseGlow
                        )
                    )
                } else {
                    TextStyle.Default
                },
                modifier = Modifier
                    .scale(if (isConnected) pulseScale else 1f)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }

        if (isConnected && !isVip && !dailyRemainingText.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Surface(
                color = Color(0xFF252730),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.border(
                    1.dp,
                    Color(0xFF484B57),
                    RoundedCornerShape(8.dp)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFD8DCE6))
                    )
                    Text(
                        text = dailyRemainingText,
                        color = Color(0xFFE2E5EE),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
internal fun PowerButton(
    mode: UiMode,
    progress: Float,
    lang: AppLang,
    isVipMode: Boolean,
    onClick: () -> Unit
) {
    val isConnected = mode == UiMode.CONNECTED
    val target = when (mode) {
        UiMode.IDLE -> if (isVipMode) Color(0xFFB8B2A6) else Color(0xFF8A8E99)
        UiMode.SCANNING -> Color(0xFFC4C8D4)
        UiMode.CONNECTING -> Color(0xFFD8DCE6)
        UiMode.CONNECTED -> Color(0xFFECEEF6)
    }
    val color by animateColorAsState(target, tween(700), label = "pbColor")
    val animProgress by animateFloatAsState(progress, tween(400), label = "pbProgress")
    val glow by animateFloatAsState(
        if (isConnected) 1f else 0f, tween(800), label = "pbGlow"
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
        animationSpec = infiniteRepeatable(tween(2100, easing = LinearEasing)),
        label = "pbPulse"
    )
    val spin by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "pbSpin"
    )
    val breathe by inf.animateFloat(
        initialValue = 1f,
        targetValue = if (isConnected) 1.055f else 1.035f,
        animationSpec = infiniteRepeatable(
            tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ),
        label = "pbBreathe"
    )

    val boxHeight = if (isConnected) 268.dp else 236.dp

    Box(
        modifier = Modifier
            .width(268.dp)
            .height(boxHeight),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier.size(252.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = 81.dp.toPx()
                when (mode) {
                    UiMode.CONNECTED, UiMode.CONNECTING -> {
                        // هاله نورانی وسیع در پس‌زمینه دکمه هنگام اتصال
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFE6CFAF).copy(alpha = 0.24f),
                                    Color(0xFFD8DCE6).copy(alpha = 0.12f),
                                    Color.Transparent
                                ),
                                center = c,
                                radius = r * 1.65f
                            ),
                            radius = r * 1.65f,
                            center = c
                        )
                        // ۳ موج پالس وسیع و پویا که تا لبه بیرونی پخش می‌شوند
                        for (k in 0..2) {
                            val t = (pulse + k / 3f) % 1f
                            val waveRadius = r + 4.dp.toPx() + t * (size.minDimension * 0.22f)
                            val waveAlpha = (1f - t) * (if (isConnected) 0.52f else 0.36f)
                            drawCircle(
                                color = if (k % 2 == 0) {
                                    color.copy(alpha = waveAlpha)
                                } else {
                                    Color(0xFFE6CFAF).copy(alpha = waveAlpha * 0.85f)
                                },
                                radius = waveRadius,
                                center = c,
                                style = Stroke(width = (3.2f - t * 1.8f).dp.toPx())
                            )
                        }
                    }
                    UiMode.SCANNING -> {
                        val rr = r + 12.dp.toPx()
                        val tl = Offset(c.x - rr, c.y - rr)
                        val sz = Size(rr * 2f, rr * 2f)
                        drawArc(
                            color = color.copy(alpha = 0.20f), startAngle = 0f, sweepAngle = 360f,
                            useCenter = false, topLeft = tl, size = sz, style = Stroke(4.dp.toPx())
                        )
                        drawArc(
                            color = color, startAngle = -90f, sweepAngle = 360f * animProgress,
                            useCenter = false, topLeft = tl, size = sz,
                            style = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = Color(0xFFF2F4F8).copy(alpha = 0.9f), startAngle = spin, sweepAngle = 40f,
                            useCenter = false, topLeft = tl, size = sz,
                            style = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    else -> {}
                }
                drawCircle(
                    color = color.copy(alpha = 0.35f + 0.35f * glow),
                    radius = r + 5.dp.toPx(),
                    center = c,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            Box(
                modifier = Modifier
                    .size(162.dp)
                    .scale(if (mode == UiMode.IDLE || mode == UiMode.CONNECTED) breathe else 1f)
                    .clip(CircleShape)
                    .background(
                        if (isConnected) {
                            Brush.radialGradient(
                                listOf(
                                    Color(0xFFF4F6FA),
                                    Color(0xFFC8CCD6),
                                    Color(0xFF7D8290)
                                )
                            )
                        } else {
                            Brush.radialGradient(
                                listOf(
                                    Color(0xFF343640),
                                    Color(0xFF22232A),
                                    Color(0xFF16171C)
                                )
                            )
                        }
                    )
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            if (isConnected) {
                                listOf(Color(0xFFFFFFFF), Color(0xFFD8DCE6), Color(0xFF6E7380))
                            } else {
                                listOf(Color(0xFFD0D4DF), Color(0xFF6B6E7B), Color(0xFF363842))
                            }
                        ),
                        shape = CircleShape
                    )
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(targetState = mode, label = "pbText") { m ->
                    val fg = if (m == UiMode.CONNECTED) Color(0xFF16171C) else Color(0xFFF2F4F8)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = if (m == UiMode.CONNECTED) 16.dp else 0.dp)
                    ) {
                        Text(
                            text = when (m) {
                                UiMode.IDLE -> if (isVipMode) "VIP CONNECT" else "CONNECT"
                                UiMode.SCANNING -> "SCANNING"
                                UiMode.CONNECTING -> "CONNECTING"
                                UiMode.CONNECTED -> "CONNECTED"
                            },
                            color = fg, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = when (m) {
                                UiMode.IDLE -> Strings.smartConnect(lang, isVipMode)
                                UiMode.SCANNING -> Strings.tapToCancel(lang)
                                UiMode.CONNECTING -> Strings.tapToCancel(lang)
                                UiMode.CONNECTED -> Strings.disconnect(lang)
                            },
                            color = fg.copy(alpha = 0.80f), fontSize = 11.5.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (catPresence > 0.01f) {
            Image(
                painter = painterResource(id = R.drawable.penny_cat_peeking),
                contentDescription = "Peeking Cat on Connect Button",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(88.dp)
                    .height(102.dp)
                    .offset(y = (-12).dp)
                    .graphicsLayer {
                        alpha = catPresence.coerceIn(0f, 1f)
                        translationY = (1f - catPresence) * 28.dp.toPx()
                        val sc = 0.80f + 0.20f * catPresence
                        scaleX = sc
                        scaleY = sc
                    }
                    .clickable(onClick = onClick)
            )
        }
    }
}

@Composable
internal fun ServiceTierSelectorBar(
    selectedMode: ServiceTierMode,
    dailyRemText: String,
    isVip: Boolean,
    onSelectMode: (ServiceTierMode) -> Unit
) {
    Surface(
        color = Color(0xFF181920).copy(alpha = 0.96f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF343642), RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ServiceTierMode.values().forEach { tier ->
                    val isSelected = selectedMode == tier
                    val accentColor = when (tier) {
                        ServiceTierMode.PUBLIC -> Color(0xFF98E2C6)
                        ServiceTierMode.DEDICATED -> Color(0xFFE6CFAF)
                        ServiceTierMode.GHOST -> Color(0xFFC5B3F8)
                    }
                    Surface(
                        color = if (isSelected) Color(0xFF282A35) else Color(0xFF1F2028),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (isSelected) 1.3.dp else 1.dp,
                                color = if (isSelected) accentColor else Color(0xFF2E303B),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectMode(tier) }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = tier.shortTitle,
                                color = if (isSelected) Color(0xFFF5F7FA) else Color(0xFFA0A3B1),
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(2.dp))
                            val subBadge = when (tier) {
                                ServiceTierMode.PUBLIC -> "FREE"
                                ServiceTierMode.DEDICATED -> if (isVip) "VIP 50GB" else "1GB/DAY"
                                ServiceTierMode.GHOST -> "CHAT ONLY"
                            }
                            Text(
                                text = subBadge,
                                color = if (isSelected) accentColor else Color(0xFF757886),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = selectedMode.description,
                    color = Color(0xFFB8BCC8),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (selectedMode == ServiceTierMode.DEDICATED) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = dailyRemText,
                        color = Color(0xFFE6CFAF),
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
internal fun PennyBottomNavBar(
    selectedTab: BottomNavTab,
    serverCount: Int,
    onSelectTab: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF14151B).copy(alpha = 0.98f),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Color(0xFF2E303C),
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavTab.values().forEach { tab ->
                val active = selectedTab == tab
                val fg = if (active) Color(0xFFE6CFAF) else Color(0xFF8E92A0)
                Surface(
                    color = if (active) Color(0xFF232530) else Color.Transparent,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectTab(tab) }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 9.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Canvas(modifier = Modifier.size(16.dp)) {
                            val w = size.width
                            val h = size.height
                            val st = 1.7.dp.toPx()
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
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = if (tab == BottomNavTab.SERVERS && serverCount > 0) {
                                "${tab.title} ($serverCount)"
                            } else {
                                tab.title
                            },
                            color = if (active) Color(0xFFF5F7FA) else Color(0xFF9498A6),
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

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
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Server Network",
                    color = Color(0xFFF5F7FA),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Select service mode & connect to verified nodes",
                    color = Color(0xFF9EA2B0),
                    fontSize = 11.5.sp
                )
            }
            Surface(
                color = Color(0xFF252732),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFE6CFAF).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .clickable(enabled = !busy) { onScanClick() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UpdateArrowIcon(size = 13.dp, color = Color(0xFFE6CFAF))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (busy) "Scanning..." else "Scan Mode",
                        color = Color(0xFFF5E6D3),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        ServiceTierSelectorBar(
            selectedMode = selectedMode,
            dailyRemText = dailyRemText,
            isVip = vipActive,
            onSelectMode = onSelectMode
        )

        Spacer(Modifier.height(10.dp))

        // کارت وضعیت اختصاصی هر مود
        Surface(
            color = Color(0xFF1A1B22).copy(alpha = 0.95f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF323440), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                when (selectedMode) {
                    ServiceTierMode.DEDICATED -> {
                        val ratio = if (vipActive) {
                            1f
                        } else {
                            (dailyRemBytes.toFloat() / dailyMaxBytes.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (vipActive) "Dedicated Pro • VIP 50 GB Plan" else "Dedicated Pro • 1 GB Daily Quota",
                                color = Color(0xFFE6CFAF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (vipActive) "UNRESTRICTED" else "${fmtBytes(dailyRemBytes)} Remaining",
                                color = Color(0xFFF2F4F8),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF282A34))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(ratio)
                                    .fillMaxHeight()
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFF9E8A6E), Color(0xFFE6CFAF))
                                        )
                                    )
                            )
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = "Encrypted Stealth Vault • Protected against config extraction • Resets daily at 00:00",
                            color = Color(0xFF9498A6),
                            fontSize = 9.5.sp
                        )
                    }
                    ServiceTierMode.GHOST -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Ghost Mode • Extreme Censorship Bypass",
                                color = Color(0xFFC5B3F8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "MESSAGING ONLY",
                                color = Color(0xFFC5B3F8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Designed for severe blackouts & heavy DPI filtering. Uses micro-fragmentation (5-12B) to keep Telegram, WhatsApp & text messages connected.",
                            color = Color(0xFFB0B4C2),
                            fontSize = 10.sp
                        )
                    }
                    ServiceTierMode.PUBLIC -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Public Mode • Free Community Servers",
                                color = Color(0xFF98E2C6),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "UNLIMITED FREE",
                                color = Color(0xFF98E2C6),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Public servers verified by real SOCKS5 download test. Free unlimited usage for general browsing.",
                            color = Color(0xFFB0B4C2),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        if (busy && scanProgress > 0f) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF262832))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(scanProgress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(Color(0xFFE6CFAF))
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (servers.isEmpty()) {
            Surface(
                color = Color(0xFF181920),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF2E303C), RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No servers loaded for ${selectedMode.title}",
                        color = Color(0xFFF2F4F8),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Tap 'Scan Mode' above to fetch and verify healthy nodes for this mode.",
                        color = Color(0xFF9498A6),
                        fontSize = 11.5.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            servers.forEachIndexed { idx, srv ->
                val isSelected = isRunning && srv.guid == selectedGuid
                val tierBadge = when (srv.codeTag) {
                    "DED-NL" -> "REALITY • NL"
                    "DED-PL" -> "STEALTH • PL"
                    "GHOST" -> "GHOST • ANTI-DPI"
                    "VPS-1" -> "DEDICATED • DE"
                    else -> "PUBLIC • FREE"
                }
                val badgeColor = when (srv.codeTag) {
                    "DED-NL", "DED-PL", "VPS-1" -> Color(0xFFE6CFAF)
                    "GHOST" -> Color(0xFFC5B3F8)
                    else -> Color(0xFF98E2C6)
                }
                Surface(
                    color = if (isSelected) Color(0xFF272935) else Color(0xFF191A22),
                    shape = RoundedCornerShape(15.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .border(
                            width = if (isSelected) 1.4.dp else 1.dp,
                            color = if (isSelected) badgeColor else Color(0xFF2D2F3B),
                            shape = RoundedCornerShape(15.dp)
                        )
                        .clickable(enabled = !busy) { onServerClick(srv) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = srv.label.ifBlank { "${selectedMode.shortTitle} Node #${idx + 1}" },
                                    color = Color(0xFFF5F7FA),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(badgeColor.copy(alpha = 0.16f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = tierBadge,
                                        color = badgeColor,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = if (isSelected) {
                                    "Connected • Encrypted Tunnel Active (Config Hidden)"
                                } else {
                                    "Download-Verified • Tap to connect immediately"
                                },
                                color = if (isSelected) badgeColor else Color(0xFF8E92A0),
                                fontSize = 10.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (srv.ms > 0) "${srv.ms} ms" else "READY",
                                color = badgeColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (isSelected) "ACTIVE" else "CONNECT ›",
                                color = if (isSelected) Color(0xFFF5F7FA) else Color(0xFF9EA2B0),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

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
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "User Profile & Quota",
            color = Color(0xFFF5F7FA),
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold
        )

        // 1) کارت شناسه اختصاصی کاربر (UUID + Hardware Fingerprint)
        Surface(
            color = Color(0xFF191A22),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF353744), RoundedCornerShape(18.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Dedicated User Identity",
                            color = Color(0xFFE6CFAF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Hardware Fingerprint: $deviceId",
                            color = Color(0xFF9EA2B0),
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Surface(
                        color = Color(0xFF282A36),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFFE6CFAF).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .clickable { onCopyUuid() }
                    ) {
                        Text(
                            text = "Copy UUID",
                            color = Color(0xFFF5E6D3),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = Color(0xFF121318),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF282A34), RoundedCornerShape(10.dp))
                ) {
                    Text(
                        text = userUuid,
                        color = Color(0xFFD8DCE6),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Bound to hardware DRM & signing certificate. Clearing app data or reinstalling will not reset this UUID or daily quota.",
                    color = Color(0xFF8C909E),
                    fontSize = 9.5.sp
                )
            }
        }

        // 2) کارت سهمیه ۱ گیگابایت روزانه سرویس اختصاصی + وضعیت VIP
        Surface(
            color = Color(0xFF191A22),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF353744), RoundedCornerShape(18.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dedicated Pro Daily Quota",
                        color = Color(0xFFF2F4F8),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Active Mode: ${serviceTierMode.shortTitle}",
                        color = Color(0xFFE6CFAF),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Used Today: ${fmtBytes(dailyUsedBytes)}",
                        color = Color(0xFFB8BCC8),
                        fontSize = 11.sp
                    )
                    Text(
                        text = "Remaining: ${fmtBytes(dailyRemBytes)} / ${fmtBytes(dailyMaxBytes)}",
                        color = Color(0xFFF5E6D3),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF262832))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(dailyRatio)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF8E93A2), Color(0xFFE6CFAF))
                                )
                            )
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Quota automatically renews every day at 00:00. After 1 GB is reached, Dedicated Pro pauses and you can continue on Public or Ghost Mode.",
                    color = Color(0xFF8C909E),
                    fontSize = 9.5.sp
                )

                Spacer(Modifier.height(10.dp))
                Surface(
                    color = Color(0xFF242632),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFE6CFAF).copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                        .clickable { onOpenVipDialog() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlassDiamondIcon(size = 16.dp)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (vipActive) "VIP 50 GB Plan Active" else "Upgrade to VIP (50 GB / Month)",
                                    color = Color(0xFFF5F7FA),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                val vipSub = if (vipActive) {
                                    val remD = ((vipExpireMs - System.currentTimeMillis()) / (24L * 3600_000L)).coerceAtLeast(0L)
                                    val remB = (vipMaxBytes - vipUsedBytes).coerceAtLeast(0L)
                                    "$remD days • ${fmtBytes(remB)} remaining"
                                } else {
                                    "Unlock 50 GB high-speed dedicated traffic & zero ads"
                                }
                                Text(
                                    text = vipSub,
                                    color = Color(0xFFB0B4C2),
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Text(
                            text = if (vipActive) "MANAGE" else "ACTIVATE ›",
                            color = Color(0xFFE6CFAF),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 3) تنظیمات ضد فیلتر و ابزارهای سیستم
        Surface(
            color = Color(0xFF191A22),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF353744), RoundedCornerShape(18.dp))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Protection & Diagnostics",
                    color = Color(0xFFF2F4F8),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Switch Unresponsive Server", color = Color(0xFFE8ECF4), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("Automatically switches if download drops", color = Color(0xFF8C909E), fontSize = 10.sp)
                    }
                    androidx.compose.material3.Switch(
                        checked = autoSwitch,
                        onCheckedChange = onAutoSwitchChange,
                        colors = metallicSwitchColors()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Reconnect on Network Change", color = Color(0xFFE8ECF4), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("Keeps VPN alive between Wi-Fi & Mobile Data", color = Color(0xFF8C909E), fontSize = 10.sp)
                    }
                    androidx.compose.material3.Switch(
                        checked = autoReconnect,
                        onCheckedChange = onAutoReconnectChange,
                        colors = metallicSwitchColors()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bypass Iranian Domestic Traffic (.ir)", color = Color(0xFFE8ECF4), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("Direct routing for banks & local apps", color = Color(0xFF8C909E), fontSize = 10.sp)
                    }
                    androidx.compose.material3.Switch(
                        checked = splitTunnelLocal,
                        onCheckedChange = onSplitTunnelChange,
                        colors = metallicSwitchColors()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = Color(0xFF232530),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF383A48), RoundedCornerShape(12.dp))
                            .clickable { onOpenLogs() }
                    ) {
                        Text(
                            text = "Activity Logs",
                            color = Color(0xFFE8ECF4),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }
                    Surface(
                        color = Color(0xFF232530),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF383A48), RoundedCornerShape(12.dp))
                            .clickable { onOpenUpdate() }
                    ) {
                        Text(
                            text = "Check Update",
                            color = Color(0xFFE8ECF4),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}


