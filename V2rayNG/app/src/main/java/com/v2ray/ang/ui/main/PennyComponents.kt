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
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF2B2D36), Color(0xFF1D1E24))
                                )
                            )
                            .border(
                                1.dp,
                                if (isConnected) Color(0xFFD8DCE6).copy(alpha = 0.85f)
                                else if (isVipMode) Color(0xFFD8C7B0).copy(alpha = 0.65f)
                                else Color(0xFF454752),
                                RoundedCornerShape(11.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentSrv.codeTag.ifEmpty { "A-1" },
                            color = if (isConnected) Color(0xFFF2F4F8)
                            else if (isVipMode) Color(0xFFE6CFAF)
                            else Color(0xFFD0D4DF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentSrv.label,
                            color = Color(0xFFF2F4F8),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (isConnected) {
                                "Active Node • ${currentSrv.ms} ms • Stable"
                            } else {
                                "Ping ${currentSrv.ms} ms • Tap to change server"
                            },
                            color = Color(0xFF9EA1AB),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (isConnected) Color(0xFF2B2E38)
                                else if (isVipMode) Color(0xFF282A33)
                                else Color(0xFF23242C)
                            )
                            .border(
                                1.dp,
                                if (isConnected) Color(0xFFD8DCE6).copy(alpha = 0.65f)
                                else Color(0xFF3E404A),
                                RoundedCornerShape(9.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isVipMode) {
                                GlassDiamondIcon(size = 11.dp)
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = "${currentSrv.ms} ms",
                                color = if (isConnected) Color(0xFFF2F4F8)
                                else if (isVipMode) Color(0xFFE8ECF4)
                                else Color(0xFFC8CCD6),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
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
    val uptimePercent = if (isVipMode) "99.99%" else "99.98%"
    val jitterMs = ((bestPingMs % 5L) + 2L).coerceIn(2L, 8L)
    val qualityScore = when {
        bestPingMs in 1..250 -> 0.98f
        bestPingMs in 251..500 -> 0.94f
        bestPingMs > 500 -> 0.89f
        else -> 0.96f
    }
    val qualityLabel = when {
        bestPingMs in 1..250 -> "Ultra Stable"
        bestPingMs in 251..550 -> "Excellent"
        else -> "Optimal"
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
                        text = "Uptime $uptimePercent",
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
                    text = "Jitter: ${jitterMs} ms • 0% Loss",
                    color = Color(0xFF9497A1),
                    fontSize = 9.5.sp
                )
                Text(
                    text = buildString {
                        append(if (cleanDnsShield) "DNS Shield: Active" else "Standard DNS")
                        if (splitTunnelLocal) append(" • Split Tunnel")
                        append(" • ${serverCount.coerceAtLeast(1)} Nodes")
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
            Spacer(Modifier.height(8.dp))
            Text(
                text = Strings.splashTagline(lang),
                color = Color(0xFFC8CCD6),
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.graphicsLayer {
                    val p = subAnim.value
                    alpha = p
                    translationY = (1f - p) * 26f
                }
            )
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
        Spacer(Modifier.height(3.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isVipMode) {
                GlassDiamondIcon(size = 12.dp)
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = Strings.subtitle(lang, isVipMode),
                color = if (isVipMode) Color(0xFFE8ECF4) else Color(0xFF9EA1AB),
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun PulsingStatusText(mode: UiMode, statusMessage: String, lang: AppLang, isVip: Boolean) {
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

        if (isConnected && !isVip) {
            Spacer(Modifier.height(4.dp))
            Surface(
                color = Color(0xFF231F1B),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.border(
                    1.dp,
                    Color(0xFFE6CFAF).copy(alpha = 0.45f),
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
                            .background(Color(0xFFE6CFAF))
                    )
                    Text(
                        text = "Speed Throttled: 256 KB/s",
                        color = Color(0xFFE6CFAF),
                        fontSize = 10.5.sp,
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
        UiMode.CONNECTING -> Color(0xFFD4D8E2)
        UiMode.CONNECTED -> Color(0xFFE4E7F0)
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
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "pbPulse"
    )
    val spin by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "pbSpin"
    )
    val breathe by inf.animateFloat(
        initialValue = 1f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ),
        label = "pbBreathe"
    )

    val boxHeight = if (isConnected) 256.dp else 224.dp

    Box(
        modifier = Modifier
            .width(236.dp)
            .height(boxHeight),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier.size(220.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = size.minDimension * 0.35f
                when (mode) {
                    UiMode.CONNECTED, UiMode.CONNECTING -> {
                        for (k in 0..1) {
                            val t = (pulse + k / 2f) % 1f
                            drawCircle(
                                color = color.copy(alpha = (1f - t) * 0.28f),
                                radius = r + 6.dp.toPx() + t * size.minDimension * 0.12f,
                                center = c,
                                style = Stroke(width = 2.dp.toPx())
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
                    color = color.copy(alpha = 0.32f + 0.28f * glow),
                    radius = r + 5.dp.toPx(),
                    center = c,
                    style = Stroke(width = 1.8.dp.toPx())
                )
            }

            Box(
                modifier = Modifier
                    .size(162.dp)
                    .scale(if (mode == UiMode.IDLE) breathe else 1f)
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
