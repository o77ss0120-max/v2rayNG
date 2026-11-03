package com.v2ray.ang.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.logcat.LogcatActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// ═════════════════════════ زبان (فقط انگلیسی) ═════════════════════════

private enum class AppLang(val code: String) { EN("en") }

private object Strings {
    fun subtitle(l: AppLang, vip: Boolean) =
        if (vip) "👑 1 Month 50 GB Active • Dedicated Servers" else "Smart Fastest Server Selector"

    fun splashTagline(l: AppLang) = "Fast, Smart & Secure Protection"

    fun ready(l: AppLang) = "Ready to Connect"
    fun connectedBadge(l: AppLang, vip: Boolean) =
        if (vip) "Connected to VIP (1M / 50GB) 👑" else "Connected ✅"

    fun smartConnect(l: AppLang, vip: Boolean) =
        if (vip) "VIP Connect" else "Smart Connect"

    fun tapToCancel(l: AppLang) = "Tap to Cancel"
    fun disconnect(l: AppLang) = "Disconnect"

    fun download(l: AppLang) = "Download"
    fun upload(l: AppLang) = "Upload"
    fun duration(l: AppLang) = "Duration"
    fun totalData(l: AppLang) = "Total Data"

    fun noServerYet(l: AppLang, vip: Boolean) =
        if (vip) "No VIP servers scanned yet; tap Scan or Connect."
        else "No servers scanned yet; tap Scan or Connect."

    fun scanServersBtn(l: AppLang, vip: Boolean) =
        if (vip) "👑 Scan VIP" else "🔍 Scan Servers"

    fun nextServerBtn(l: AppLang) = "⏭ Next Server"

    fun scanInfo(l: AppLang, min: Long, count: Int, vip: Boolean): String {
        val tag = if (vip) "VIP " else ""
        val ago = when {
            min < 1 -> "just now"
            min < 60 -> "${min}m ago"
            else -> "${min / 60}h ago"
        }
        return "Last ${tag}scan: $ago  •  $count servers ready"
    }

    fun noScanYet(l: AppLang, vip: Boolean) =
        if (vip) "No VIP scan performed yet" else "No scan performed yet"

    fun menuBestConfigs(l: AppLang, vip: Boolean) =
        if (vip) "👑 1M 50GB Dedicated Servers" else "Best Servers List"

    fun menuAppLog(l: AppLang) = "App Activity Logs"
    fun menuCoreLog(l: AppLang) = "Core Logs (Logcat)"
    fun close(l: AppLang) = "Close"
    fun copy(l: AppLang) = "Copy"
    fun clear(l: AppLang) = "Clear"
    fun emptyBestList(l: AppLang) = "No server list yet. Please scan first."
    fun emptyLogs(l: AppLang) = "No logs recorded yet."
}

// ═════════════════════════ اعتبارسنجی کد متغیر اشتراک ۱ ماه ۵۰ گیگ ═════════════════════════

private object VipLicenseManager {
    const val VIP_DURATION_MS = 30L * 24L * 60L * 60L * 1000L // ۱ ماه (۳۰ روز)
    const val VIP_MAX_BYTES = 50L * 1024L * 1024L * 1024L      // ۵۰ گیگابایت
    private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    fun computeSignature(serial4: String): String {
        val clean = serial4.uppercase(Locale.US).trim()
        var h = 2166136261L
        val input = "$clean:PENNY_VIP_MASTER_2026"
        for (ch in input) {
            h = (h xor ch.code.toLong()) * 16777619L and 0xFFFFFFFFL
        }
        val sb = StringBuilder()
        var v = h
        for (i in 0 until 4) {
            val idx = ((v + i * 7L) % ALPHABET.length).toInt().let { if (it < 0) -it else it }
            sb.append(ALPHABET[idx])
            v = (v shr 4) xor ((v * 37L) and 0xFFFFFFFFL)
        }
        return sb.toString()
    }

    fun verifyCode(rawCode: String): String? {
        val normalized = rawCode.uppercase(Locale.US).trim().replace(" ", "")
        val parts = normalized.split("-")
        if (parts.size != 3) return null
        val prefix = parts[0]
        val serial = parts[1]
        val sig = parts[2]
        if (prefix != "PVIP" && prefix != "VIP") return null
        if (serial.length != 4 || sig.length != 4) return null
        val expected = computeSignature(serial)
        return if (sig == expected) serial else null
    }
}

// ═════════════════════════ مدل سرورها (بدون پرچم کشور، با نام‌های Data center A-1 / Server A-1) ═════════════════════════

private enum class Phase { IDLE, SCANNING, CONNECTING }

private enum class UiMode { IDLE, SCANNING, CONNECTING, CONNECTED }

private enum class Tier(val title: String, val badgeText: String) {
    ULTRA("Ultra", "10G ULTRA"),
    TURBO("Turbo", "TURBO"),
    VIP("VIP", "HIGH SPEED"),
    STANDARD("Standard", "STABLE"),
    BASIC("Basic", "ONLINE")
}

private fun starsFor(ms: Long): Int = when {
    ms <= 350 -> 5
    ms <= 600 -> 4
    ms <= 1000 -> 3
    ms <= 1800 -> 2
    else -> 1
}

private fun tierFor(stars: Int): Tier = when (stars) {
    5 -> Tier.ULTRA
    4 -> Tier.TURBO
    3 -> Tier.VIP
    2 -> Tier.STANDARD
    else -> Tier.BASIC
}

private data class PennyServer(
    val guid: String,
    val codeTag: String,
    val ms: Long,
    val label: String,
    val isVipServer: Boolean = false
) {
    val stars: Int get() = starsFor(ms)
    val tier: Tier get() = tierFor(stars)
}

/**
 * نام‌گذاری سرورها بدون پرچم کشور و مرتب‌شده بر اساس کمترین پینگ (ms):
 * Data center A-1, Data center A-2, Server A-1, Server A-2, Data center B-1, ...
 */
private fun assignLabels(list: List<PennyServer>, isVipList: Boolean = false): List<PennyServer> {
    return list.sortedBy { it.ms }.mapIndexed { index, s ->
        val groupChar = ('A'.code + (index / 4).coerceAtMost(5)).toChar()
        val num = (index % 2) + 1
        val isDataCenter = (index % 4) < 2
        val codeTag = "$groupChar-$num"
        val baseLabel = if (isDataCenter) "Data center $codeTag" else "Server $codeTag"
        // حذف نام اصلی سرور از کانفیگ ذخیره شده تا در نوتیفیکیشن سیستم نمایش داده نشود
        try {
            val cfg = MmkvManager.decodeServerConfig(s.guid)
            if (cfg != null && cfg.remarks != "Penny VPN") {
                cfg.remarks = "Penny VPN"
                MmkvManager.encodeServerConfig(s.guid, cfg)
            }
        } catch (_: Exception) {}
        s.copy(
            codeTag = codeTag,
            label = baseLabel,
            isVipServer = isVipList
        )
    }
}

// ═════════════════════════ فرمت‌ها ═════════════════════════

private fun fmtBytes(b: Long): String {
    if (b < 0) return "—"
    val kb = b / 1024.0
    return when {
        kb < 1024.0 -> String.format(Locale.US, "%.0f KB", kb)
        kb < 1024.0 * 1024.0 -> String.format(Locale.US, "%.1f MB", kb / 1024.0)
        else -> String.format(Locale.US, "%.2f GB", kb / 1024.0 / 1024.0)
    }
}

private fun fmtSpeed(bps: Long): String = if (bps < 0) "—" else fmtBytes(bps) + "/s"

private fun fmtDuration(sec: Long): String {
    val s = sec.coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

// ═════════════════════════ کامپوننت‌های گرافیکی مدرن (تم نقره‌ای متالیک، خاکستری و نود شاینی) ═════════════════════════

@Composable
private fun StarsRow(stars: Int, size: TextUnit = 11.sp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) {
            Text(
                text = "★",
                color = if (i <= stars) Color(0xFFD8C7B0) else Color(0xFF3E3D46),
                fontSize = size
            )
        }
    }
}

@Composable
private fun StatCell(icon: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$icon $value", color = Color(0xFFF2F4F8), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = Color(0xFF9EA1AB), fontSize = 10.sp)
    }
}

/**
 * انیمیشن ورود پلکانی (Staggered Entrance Animation) برای آیتم‌های لیست بهترین کانفیگ‌ها
 */
@Composable
private fun StaggeredServerItem(
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

/**
 * کارت مدرن نمایش سرور بدون پرچم کشور و بدون ستاره/10G ULTRA
 * همراه با افکت محو شدن (Fade / Crossfade) ملایم هنگام تغییر سرور
 */
@Composable
private fun ModernServerCard(
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
                    // باکس مدرن کد نود (بدون پرچم کشور - استایل نقره‌ای متالیک)
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

                    // بج مدرن پینگ با ترکیب نقره‌ای متالیک
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (isConnected) Color(0xFF2B2E38)
                                else if (isVipMode) Color(0xFF2D2720)
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
                        Text(
                            text = if (isVipMode) "👑 ${currentSrv.ms} ms" else "${currentSrv.ms} ms",
                            color = if (isConnected) Color(0xFFF2F4F8)
                            else if (isVipMode) Color(0xFFE6CFAF)
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

/**
 * کارت نمایش کیفیت کل سرویس و درصد آپ‌تایم (جایگزین کادر خالی پایین صفحه)
 * همراه با بارگذاری هوشمند تبلیغ بنری فقط در صورت آماده بودن واقعی
 */
@Composable
private fun ServiceQualityUptimeCard(
    lang: AppLang,
    isConnected: Boolean,
    isVipMode: Boolean,
    serverCount: Int,
    bestPingMs: Long,
    adUnitId: String,
    showAds: Boolean,
    modifier: Modifier = Modifier
) {
    val uptimePercent = if (isVipMode) "99.99%" else "99.98%"
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

            // نوار شاینی کیفیت کل شبکه
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
                    text = "Tunnel Stability: 0% Packet Loss",
                    color = Color(0xFF9497A1),
                    fontSize = 9.5.sp
                )
                Text(
                    text = "${serverCount.coerceAtLeast(1)} Active Nodes",
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

/**
 * اسپلش و انیمیشن ورود بزرگ‌تر و پویا برای لوگو و نوشته‌ها در شروع اپ
 */
@Composable
private fun PennySplashIntro(lang: AppLang, onFinished: () -> Unit) {
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

/**
 * هدر براق (Shiny Metallic Silver) متمرکز و خلوت
 */
@Composable
private fun NeonBrandHeader(
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
        Text(
            text = Strings.subtitle(lang, isVipMode),
            color = if (isVipMode) Color(0xFFE6CFAF) else Color(0xFF9EA1AB),
            fontSize = 11.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * انیمیشن پالس زنده روی متن وضعیت اتصال در حالت CONNECTED با درخشش نقره‌ای متالیک
 */
@Composable
private fun PulsingStatusText(mode: UiMode, statusMessage: String, lang: AppLang, isVip: Boolean) {
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
}

/**
 * دکمه اتصال با رنگ‌بندی نقره‌ای و خاکستری متالیک شاینی
 * همراه با گربه کوچک‌تر که فقط سرِ پنجه‌هایش کمی روی لبه بالایی دایره قرار می‌گیرد
 */
@Composable
private fun PowerButton(
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
                            // نقره‌ای و خاکستری متالیک شاینی (جایگزین رنگ طلایی وسط صفحه)
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

        // گربه کوچک‌تر شده که سرش بالای دایره است و فقط سرِ دستانش کمی وارد لبه بالایی دایره می‌شود
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

// ═════════════════════════ Activity ═════════════════════════

class MainActivity : HelperBaseComponentActivity() {

    companion object {
        private const val TAG = "PennyVPN"

        // ── شناسه‌های AdMob (آماده تست؛ هنگام انتشار در گوگل پلی با Unit IDهای پنل AdMob خود جایگزین کنید) ──
        private const val ADMOB_BANNER_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
        private const val ADMOB_INTERSTITIAL_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

        private const val TCP_TIMEOUT_MS = 2500
        private const val MAX_TCP_CANDIDATES = 300
        private const val TCP_PARALLEL = 40

        private const val REAL_TEST_COUNT = 60
        private const val REAL_TEST_TIMEOUT_MS = 45_000L
        private const val BEST_LIST_SIZE = 15
        private const val MAX_CONNECT_ATTEMPTS = 6

        private const val STALE_MS = 6 * 60 * 60 * 1000L
        private const val MONITOR_INTERVAL_MS = 40_000L

        private val TEST_URLS = listOf(
            "https://www.gstatic.com/generate_204",
            "https://cp.cloudflare.com/generate_204"
        )
    }

    // ── منابع سرورهای عادی ──
    private val SUBSCRIPTION_SOURCES = listOf(
        "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Sub7.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Sub8.txt"
    )

    // ── محل وارد کردن سرورهای مشترکین ویژه (اشتراک ۱ ماه ۵۰ گیگ) ──
    // هر لینک سابسکریپشن یا فایل متنی خام (Raw) گیت‌هاب / سرور شخصی خود را در این لیست قرار دهید:
    private val DEFAULT_VIP_SUBSCRIPTION_SOURCES = listOf(
        "https://gist.githubusercontent.com/o77ss0120-max/5e592995c7b0f82b924cec572c0422a9/raw/penny_configs.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Sub1.txt"
    )

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, MainRepository(application as AngApplication))
    }

    private val socksPort: Int get() = SettingsManager.getSocksPort()

    // ── وضعیت AdMob ──
    private var interstitialAd: InterstitialAd? = null
    private var isAdLoading = false

    // ── وضعیت UI ──
    private var showSplash by mutableStateOf(true)
    private var appLang by mutableStateOf(AppLang.EN)
    private var phase by mutableStateOf(Phase.IDLE)
    private var statusMessage by mutableStateOf("Ready to Connect")
    private var scanProgress by mutableStateOf(0f)
    private var lastScanTime by mutableStateOf(0L)
    private var vipLastScanTime by mutableStateOf(0L)
    private var connectStartMs by mutableStateOf(0L)
    private var lastConnectedGuid by mutableStateOf<String?>(null)
    private var autoSwitch by mutableStateOf(true)
    private var autoReconnect by mutableStateOf(true)
    private var userWantsConnected by mutableStateOf(false)

    // ── وضعیت اشتراک ۱ ماه ۵۰ گیگ (VIP) ──
    private var vipActive by mutableStateOf(false)
    private var useVipServers by mutableStateOf(false)
    private var vipCode by mutableStateOf("")
    private var vipExpireMs by mutableStateOf(0L)
    private var vipUsedBytes by mutableStateOf(0L)
    private var customVipSubUrl by mutableStateOf("")
    private var showVipDialog by mutableStateOf(false)

    private var showMenu by mutableStateOf(false)
    private var showLog by mutableStateOf(false)
    private var showBest by mutableStateOf(false)

    private val logLines = mutableStateListOf<String>()
    private val bestList = mutableStateListOf<PennyServer>()
    private val vipBestList = mutableStateListOf<PennyServer>()

    private val activeList: MutableList<PennyServer>
        get() = if (vipActive && useVipServers) vipBestList else bestList

    private val activeScanTime: Long
        get() = if (vipActive && useVipServers) vipLastScanTime else lastScanTime

    private val failedGuids = mutableSetOf<String>()
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    private var workJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val prefs by lazy { getSharedPreferences("penny_prefs", Context.MODE_PRIVATE) }

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                startSmartConnect()
            } else {
                statusMessage = "VPN permission denied!"
                plog("VPN permission denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel.onAction(MainAction.Initialize)
        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
        loadSaved()
        checkVipExpiration()
        initAdMob()
        startMonitor()
        registerNetworkChangeAutoReconnect()
    }

    override fun onDestroy() {
        unregisterNetworkChangeCallback()
        super.onDestroy()
    }

    // ═════════════════════════ راه‌اندازی سرویس تبلیغات AdMob (بنری + بینابینی) ═════════════════════════

    private fun initAdMob() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                MobileAds.initialize(this@MainActivity) {
                    plog("AdMob initialized successfully")
                }
                withContext(Dispatchers.Main) {
                    loadInterstitialAd()
                }
            } catch (e: Exception) {
                plog("AdMob init warning: ${e.message}")
            }
        }
    }

    private fun loadInterstitialAd() {
        if (vipActive || isAdLoading || interstitialAd != null) return
        isAdLoading = true
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            ADMOB_INTERSTITIAL_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isAdLoading = false
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitialAd = null
                            loadInterstitialAd()
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            interstitialAd = null
                        }
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    isAdLoading = false
                }
            }
        )
    }

    private fun showInterstitialAdIfReady() {
        if (vipActive) return // مشترکین ۱ ماه ۵۰ گیگ بدون تبلیغ هستند
        val ad = interstitialAd
        if (ad != null) {
            ad.show(this)
        } else {
            loadInterstitialAd()
        }
    }

    // ═════════════════════════ مدیریت اشتراک ۱ ماه ۵۰ گیگ ═════════════════════════

    private fun getVipSubscriptionSources(): List<String> {
        val custom = customVipSubUrl.trim()
        return if (custom.isNotEmpty()) {
            custom.split("\n", ",").map { it.trim() }.filter { it.startsWith("http") } + DEFAULT_VIP_SUBSCRIPTION_SOURCES
        } else {
            DEFAULT_VIP_SUBSCRIPTION_SOURCES
        }
    }

    private fun checkVipExpiration(): Boolean {
        if (!vipActive) return false
        val now = System.currentTimeMillis()
        val timeExpired = now >= vipExpireMs
        val trafficExpired = vipUsedBytes >= VipLicenseManager.VIP_MAX_BYTES
        if (timeExpired || trafficExpired) {
            vipActive = false
            useVipServers = false
            prefs.edit()
                .putBoolean("vip_active", false)
                .putBoolean("use_vip_servers", false)
                .apply()
            val msg = if (trafficExpired) {
                "Your 50 GB traffic quota has been reached."
            } else {
                "Your 1-month subscription has expired."
            }
            statusMessage = msg
            plog("VIP expired: timeExpired=$timeExpired, trafficExpired=$trafficExpired")
            if (mainViewModel.uiState.value.isRunning) {
                LauncherManager.stopService(this)
            }
            return true
        }
        return false
    }

    private fun activateVipWithCode(inputCode: String): Pair<Boolean, String> {
        val serial = VipLicenseManager.verifyCode(inputCode)
            ?: return false to "Invalid activation code!"

        val usedSet = prefs.getStringSet("used_vip_serials", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (serial in usedSet && vipCode != inputCode.uppercase(Locale.US).trim()) {
            return false to "This code has already been used."
        }

        val now = System.currentTimeMillis()
        val normalized = inputCode.uppercase(Locale.US).trim()
        usedSet.add(serial)

        vipActive = true
        useVipServers = true
        vipCode = normalized
        vipExpireMs = now + VipLicenseManager.VIP_DURATION_MS
        vipUsedBytes = 0L

        prefs.edit()
            .putBoolean("vip_active", true)
            .putBoolean("use_vip_servers", true)
            .putString("vip_code", vipCode)
            .putLong("vip_expire_ms", vipExpireMs)
            .putLong("vip_used_bytes", 0L)
            .putStringSet("used_vip_serials", usedSet)
            .apply()

        plog("👑 1M/50GB Plan Activated with code $vipCode")
        statusMessage = "1 Month 50 GB Plan Activated 👑"

        return true to statusMessage
    }

    // ═════════════════════════ تشخیص تغییر شبکه و اتصال مجدد خودکار (Auto-Reconnect) ═════════════════════════

    private fun registerNetworkChangeAutoReconnect() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    if (!autoReconnect || !userWantsConnected) return
                    lifecycleScope.launch(Dispatchers.IO) {
                        delay(1800)
                        if (!autoReconnect || !userWantsConnected || phase != Phase.IDLE) return@launch
                        val isRunning = mainViewModel.uiState.value.isRunning
                        val tunnelAlive = isRunning && testThroughLocalProxy(TEST_URLS.first()) > 0
                        if (!tunnelAlive) {
                            plog("🌐 Network change / drop detected; auto-reconnecting to last healthy server...")
                            triggerAutoReconnect()
                        }
                    }
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    super.onCapabilitiesChanged(network, networkCapabilities)
                    if (!autoReconnect || !userWantsConnected || phase != Phase.IDLE) return
                    if (!mainViewModel.uiState.value.isRunning) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            delay(1500)
                            if (autoReconnect && userWantsConnected && phase == Phase.IDLE && !mainViewModel.uiState.value.isRunning) {
                                plog("🔄 Auto-Reconnect triggered on network capability recovery")
                                triggerAutoReconnect()
                            }
                        }
                    }
                }
            }
            cm.registerDefaultNetworkCallback(cb)
            networkCallback = cb
        } catch (e: Exception) {
            plog("NetworkCallback error: ${e.message}")
        }
    }

    private fun unregisterNetworkChangeCallback() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            networkCallback?.let { cm.unregisterNetworkCallback(it) }
            networkCallback = null
        } catch (_: Exception) {}
    }

    private suspend fun triggerAutoReconnect() {
        if (phase != Phase.IDLE || activeList.isEmpty()) return
        if (SettingsManager.isVpnMode() && VpnService.prepare(this@MainActivity) != null) return

        ui {
            phase = Phase.CONNECTING
            statusMessage = "Auto-reconnecting to last healthy server..."
        }
        try {
            failedGuids.clear()
            val connected = connectFromList(startAfter = null)
            ui {
                statusMessage = if (connected != null) "Auto-reconnected ✅" else "Auto-reconnect failed"
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Main) {
                phase = Phase.IDLE
            }
        }
    }

    // ═════════════════════════ ذخیره / بازیابی ═════════════════════════

    private fun loadSaved() {
        appLang = AppLang.EN
        statusMessage = Strings.ready(appLang)
        autoSwitch = prefs.getBoolean("auto_switch", true)
        autoReconnect = prefs.getBoolean("auto_reconnect", true)
        userWantsConnected = prefs.getBoolean("user_connected", false)
        lastScanTime = prefs.getLong("scan_time", 0L)
        vipLastScanTime = prefs.getLong("vip_scan_time", 0L)
        connectStartMs = prefs.getLong("connect_time", 0L)
        lastConnectedGuid = prefs.getString("last_guid", null)

        vipActive = prefs.getBoolean("vip_active", false)
        useVipServers = prefs.getBoolean("use_vip_servers", vipActive)
        vipCode = prefs.getString("vip_code", "") ?: ""
        vipExpireMs = prefs.getLong("vip_expire_ms", 0L)
        vipUsedBytes = prefs.getLong("vip_used_bytes", 0L)
        customVipSubUrl = prefs.getString("custom_vip_sub_url", "") ?: ""

        prefs.getString("list", null)?.let { json ->
            try {
                val arr = JSONArray(json)
                val out = mutableListOf<PennyServer>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val g = o.getString("g")
                    if (MmkvManager.decodeServerConfig(g) != null) {
                        out.add(PennyServer(g, "", o.optLong("m"), "", false))
                    }
                }
                bestList.clear()
                bestList.addAll(assignLabels(out, isVipList = false))
            } catch (e: Exception) {
                plog("Error loading free cache: ${e.message}")
            }
        }

        prefs.getString("vip_list", null)?.let { json ->
            try {
                val arr = JSONArray(json)
                val out = mutableListOf<PennyServer>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val g = o.getString("g")
                    if (MmkvManager.decodeServerConfig(g) != null) {
                        out.add(PennyServer(g, "", o.optLong("m"), "", true))
                    }
                }
                vipBestList.clear()
                vipBestList.addAll(assignLabels(out, isVipList = true))
            } catch (e: Exception) {
                plog("Error loading VIP cache: ${e.message}")
            }
        }
    }

    private fun saveList(list: List<PennyServer>, scanTime: Long, isVip: Boolean) {
        try {
            val arr = JSONArray()
            list.forEach { arr.put(JSONObject().put("g", it.guid).put("c", it.codeTag).put("m", it.ms)) }
            if (isVip) {
                prefs.edit().putString("vip_list", arr.toString()).putLong("vip_scan_time", scanTime).apply()
            } else {
                prefs.edit().putString("list", arr.toString()).putLong("scan_time", scanTime).apply()
            }
        } catch (e: Exception) {
            plog("Error saving cache: ${e.message}")
        }
    }

    // ═════════════════════════ لاگ ═════════════════════════

    private fun plog(msg: String) {
        Log.d(TAG, msg)
        val line = "${timeFmt.format(Date())}  $msg"
        runOnUiThread {
            logLines.add(line)
            if (logLines.size > 400) logLines.removeAt(0)
        }
    }

    private fun copyLogToClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("PennyVPN log", logLines.joinToString("\n")))
        statusMessage = "Logs copied"
    }

    private suspend fun ui(block: () -> Unit) = withContext(Dispatchers.Main) { block() }

    // ═════════════════════════ UI ═════════════════════════

    @Composable
    override fun ScreenContent() {
        BackHandler { moveTaskToBack(false) }
        val uiState by mainViewModel.uiState.collectAsState()
        val isRunning = uiState.isRunning
        val busy = phase != Phase.IDLE
        val isVipMode = vipActive && useVipServers

        val mode = when {
            phase == Phase.SCANNING -> UiMode.SCANNING
            phase == Phase.CONNECTING -> UiMode.CONNECTING
            isRunning -> UiMode.CONNECTED
            else -> UiMode.IDLE
        }

        // ── آمار زنده + کسر حجم از اعتبار ۵۰ گیگ در حالت VIP ──
        var downSpeed by remember { mutableStateOf(0L) }
        var upSpeed by remember { mutableStateOf(0L) }
        var totalDown by remember { mutableStateOf(0L) }
        var totalUp by remember { mutableStateOf(0L) }
        var elapsedSec by remember { mutableStateOf(0L) }

        LaunchedEffect(isRunning, vipActive, useVipServers) {
            if (!isRunning) {
                downSpeed = 0L; upSpeed = 0L; totalDown = 0L; totalUp = 0L; elapsedSec = 0L
                return@LaunchedEffect
            }
            if (connectStartMs == 0L) connectStartMs = System.currentTimeMillis()
            val uid = Process.myUid()
            val rx0 = TrafficStats.getUidRxBytes(uid)
            val tx0 = TrafficStats.getUidTxBytes(uid)
            val uidSupported = rx0 >= 0 && tx0 >= 0
            var lastRx = rx0
            var lastTx = tx0
            var lastT = SystemClock.elapsedRealtime()
            var tickCount = 0
            while (true) {
                delay(1000)
                tickCount++
                val now = SystemClock.elapsedRealtime()
                val dt = (now - lastT).coerceAtLeast(1L)
                var stepDeltaBytes = 0L
                if (uidSupported) {
                    val rx = TrafficStats.getUidRxBytes(uid)
                    val tx = TrafficStats.getUidTxBytes(uid)
                    val dRx = (rx - lastRx).coerceAtLeast(0L)
                    val dTx = (tx - lastTx).coerceAtLeast(0L)
                    stepDeltaBytes = dRx + dTx
                    downSpeed = (dRx * 1000L / dt).coerceAtLeast(0L)
                    upSpeed = (dTx * 1000L / dt).coerceAtLeast(0L)
                    totalDown = (rx - rx0).coerceAtLeast(0L)
                    totalUp = (tx - tx0).coerceAtLeast(0L)
                    lastRx = rx
                    lastTx = tx
                } else {
                    val coreStats = withContext(Dispatchers.IO) {
                        try {
                            CoreServiceManager.queryAllOutboundTrafficStats()
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                    var d = 0L
                    var u = 0L
                    for (st in coreStats) {
                        if (st.direction == AppConfig.DOWNLINK) d += st.value
                        else if (st.direction == AppConfig.UPLINK) u += st.value
                    }
                    stepDeltaBytes = (d + u).coerceAtLeast(0L)
                    downSpeed = (d * 1000L / dt).coerceAtLeast(0L)
                    upSpeed = (u * 1000L / dt).coerceAtLeast(0L)
                    totalDown += d
                    totalUp += u
                }
                lastT = now
                elapsedSec = (System.currentTimeMillis() - connectStartMs) / 1000L

                if (vipActive && useVipServers && stepDeltaBytes > 0L) {
                    vipUsedBytes += stepDeltaBytes
                    if (tickCount % 5 == 0) {
                        prefs.edit().putLong("vip_used_bytes", vipUsedBytes).apply()
                    }
                    checkVipExpiration()
                }
            }
        }

        val currentList = activeList
        val current: PennyServer? =
            (if (isRunning) currentList.firstOrNull { it.guid == uiState.selectedGuid } else null)
                ?: currentList.firstOrNull { it.guid == lastConnectedGuid }
                ?: currentList.firstOrNull()

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF232127),
                                Color(0xFF151519),
                                Color(0xFF0B0B0D)
                            )
                        )
                    )
            ) {
                // ── لوگوی کمرنگ (Watermark) جدید در پس‌زمینه ──
                Image(
                    painter = painterResource(id = R.drawable.penny_cat_logo),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(260.dp)
                        .align(Alignment.Center)
                        .alpha(0.04f)
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 38.dp, bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ── هدر براق متمرکز و پایین‌تر از لبه بالایی ──
                    NeonBrandHeader(
                        mode = mode,
                        lang = appLang,
                        isVipMode = isVipMode,
                        onLogoClick = { showSplash = true }
                    )

                    Spacer(Modifier.height(10.dp))

                    // ── دکمه اصلی همراه با گربه لوگو که هنگام اتصال روی لبه دایره می‌نشیند ──
                    PowerButton(
                        mode = mode,
                        progress = scanProgress,
                        lang = appLang,
                        isVipMode = isVipMode,
                        onClick = { onPowerClick() }
                    )

                    // ── پیام وضعیت با انیمیشن پالس در حالت CONNECTED ──
                    PulsingStatusText(
                        mode = mode,
                        statusMessage = statusMessage,
                        lang = appLang,
                        isVip = isVipMode
                    )

                    // ── آمار زنده ──
                    AnimatedVisibility(
                        visible = mode == UiMode.CONNECTED,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Surface(
                            color = Color(0xFF1B1B20).copy(alpha = 0.92f),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .border(1.dp, Color(0xFF3A3942), RoundedCornerShape(18.dp))
                        ) {
                            Column(modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    StatCell(
                                        "⬇", fmtSpeed(downSpeed), Strings.download(appLang),
                                        Modifier.weight(1f)
                                    )
                                    StatCell(
                                        "⬆", fmtSpeed(upSpeed), Strings.upload(appLang),
                                        Modifier.weight(1f)
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    StatCell(
                                        "⏱", fmtDuration(elapsedSec), Strings.duration(appLang),
                                        Modifier.weight(1f)
                                    )
                                    StatCell(
                                        "📊",
                                        fmtBytes(totalDown + totalUp),
                                        Strings.totalData(appLang),
                                        Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // ── کارت سرور مدرن با افکت Fade هنگام تغییر سرور ──
                    ModernServerCard(
                        server = current,
                        isConnected = mode == UiMode.CONNECTED,
                        isVipMode = isVipMode,
                        lang = appLang,
                        enabled = !busy && currentList.isNotEmpty(),
                        onClick = { showBest = true }
                    )

                    Spacer(Modifier.height(10.dp))

                    // ── نوار پیشرفت اسکن ──
                    AnimatedVisibility(visible = phase == Phase.SCANNING) {
                        val p by animateFloatAsState(scanProgress, tween(400), label = "bar")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF28272E))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(p.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .background(Color(0xFFE6CFAF))
                            )
                        }
                    }

                    // ── دکمه‌های اسکن و سرور بعدی با تم نود و خاکستری شاینی ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { startScanOnly() },
                            enabled = !busy,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                Strings.scanServersBtn(appLang, isVipMode),
                                fontSize = 13.sp,
                                color = Color(0xFFE8D3B5)
                            )
                        }

                        Button(
                            onClick = { switchNext() },
                            enabled = !busy && isRunning && currentList.size > 1,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isVipMode) Color(0xFFC8AD8D) else Color(0xFF36353E)
                            )
                        ) {
                            Text(
                                Strings.nextServerBtn(appLang),
                                fontSize = 13.sp,
                                color = if (isVipMode) Color(0xFF181512) else Color(0xFFF5EFE6),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // ── داک مدرن پایین صفحه (اشتراک ۱ ماه ۵۰ گیگ و منوی مدرن؛ فقط انگلیسی) ──
                    Surface(
                        color = Color(0xFF1A1A1F).copy(alpha = 0.94f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF383740), RoundedCornerShape(20.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // کارت/دکمه اشتراک ۱ ماه ۵۰ گیگ
                                Surface(
                                    color = if (vipActive) Color(0xFF2D261E) else Color(0xFF25242B),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(
                                            1.dp,
                                            if (vipActive) Color(0xFFE6CFAF).copy(alpha = 0.7f) else Color(0xFF3E3D46),
                                            RoundedCornerShape(14.dp)
                                        )
                                        .clickable { showVipDialog = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = if (vipActive) "👑 1 Month 50 GB (Active)" else "👑 1 Month 50 GB Plan",
                                                color = if (vipActive) Color(0xFFF5E6D3) else Color(0xFFF5EFE6),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            val subVipText = if (vipActive) {
                                                val remDays = ((vipExpireMs - System.currentTimeMillis()) / (24L * 3600_000L)).coerceAtLeast(0L)
                                                val remBytes = (VipLicenseManager.VIP_MAX_BYTES - vipUsedBytes).coerceAtLeast(0L)
                                                "${remDays}d • ${fmtBytes(remBytes)} left"
                                            } else {
                                                "Activate dedicated servers with code"
                                            }
                                            Text(
                                                text = subVipText,
                                                color = if (vipActive) Color(0xFFE6CFAF) else Color(0xFFA6A39E),
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = if (vipActive) "✓" else "❯",
                                            color = if (vipActive) Color(0xFFE8D3B5) else Color(0xFFA6A39E),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // دکمه منوی مدرن در داک پایین
                                Surface(
                                    color = Color(0xFF25242B),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(1.dp, Color(0xFF3E3D46), RoundedCornerShape(14.dp))
                                        .clickable { showMenu = true }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "⚙",
                                            color = Color(0xFFF5EFE6),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Menu",
                                            color = Color(0xFFA6A39E),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }

                            // نوار پیشرفت حجم ۵۰ گیگ در صورت فعال بودن اشتراک ۱ ماه ۵۰ گیگ
                            if (vipActive) {
                                val progressRatio = (vipUsedBytes.toFloat() / VipLicenseManager.VIP_MAX_BYTES.toFloat()).coerceIn(0f, 1f)
                                Spacer(Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0xFF2A2622))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth((1f - progressRatio).coerceIn(0f, 1f))
                                            .fillMaxHeight()
                                            .background(Color(0xFFE6CFAF))
                                    )
                                }
                            }
                        }
                    }

                    // ── کارت نمایش کیفیت کل سرویس و درصد آپ‌تایم ──
                    Spacer(Modifier.height(10.dp))
                    ServiceQualityUptimeCard(
                        lang = appLang,
                        isConnected = mode == UiMode.CONNECTED,
                        isVipMode = isVipMode,
                        serverCount = currentList.size,
                        bestPingMs = current?.ms ?: 180L,
                        adUnitId = ADMOB_BANNER_UNIT_ID,
                        showAds = !vipActive
                    )

                    Spacer(Modifier.height(8.dp))
                    val scanInfo = if (activeScanTime > 0L) {
                        val min = (System.currentTimeMillis() - activeScanTime) / 60000L
                        Strings.scanInfo(appLang, min, currentList.size, isVipMode)
                    } else Strings.noScanYet(appLang, isVipMode)
                    Text(scanInfo, color = Color(0xFF8C8984), fontSize = 11.sp)

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "powered by Omidss",
                        color = Color(0xFFD8C3A5).copy(alpha = 0.78f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp
                    )
                }

                // ── اسپلش بزرگ‌تر و متحرک ابتدای برنامه با لوگوی اختصاصی Penny VPN ──
                AnimatedVisibility(
                    visible = showSplash,
                    enter = fadeIn(tween(300)) + scaleIn(tween(300)),
                    exit = fadeOut(tween(500)) + scaleOut(tween(500))
                ) {
                    PennySplashIntro(
                        lang = appLang,
                        onFinished = { showSplash = false }
                    )
                }
            }

            if (showMenu) ModernMenuDialog(isVipMode)
            if (showBest) BestConfigsDialog(uiState.selectedGuid, isRunning, isVipMode)
            if (showVipDialog) VipSubscriptionDialog()
            if (showLog) LogDialog()
        }
    }

    /**
     * منوی مدرن کارتی با تم نود و خاکستری شاینی (English Only)
     */
    @Composable
    private fun ModernMenuDialog(isVipMode: Boolean) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            confirmButton = {
                TextButton(onClick = { showMenu = false }) {
                    Text(Strings.close(appLang), color = Color(0xFFE6CFAF))
                }
            },
            title = {
                Text(
                    text = "⚙ Settings & Control Center",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // ۱. کارت اشتراک ۱ ماه ۵۰ گیگ
                    Surface(
                        color = Color(0xFF25242B),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, Color(0xFF3E3D46), RoundedCornerShape(14.dp))
                            .clickable {
                                showMenu = false
                                showVipDialog = true
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "👑 1 Month 50 GB Plan",
                                color = Color(0xFFE6CFAF),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text("❯", color = Color(0xFFA6A39E))
                        }
                    }

                    // ۲. کارت لیست بهترین سرورها
                    Surface(
                        color = Color(0xFF25242B),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, Color(0xFF3E3D46), RoundedCornerShape(14.dp))
                            .clickable {
                                showMenu = false
                                showBest = true
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "⚡ " + Strings.menuBestConfigs(appLang, isVipMode),
                                color = Color(0xFFF5EFE6),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text("${activeList.size}", color = Color(0xFFE6CFAF), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    // ۳. سوییچ تعویض خودکار هنگام قطعی
                    Surface(
                        color = Color(0xFF25242B),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF3E3D46), RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "🔄 Auto-Switch on Drop",
                                fontSize = 13.sp,
                                color = Color(0xFFF5EFE6),
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = autoSwitch,
                                onCheckedChange = {
                                    autoSwitch = it
                                    prefs.edit().putBoolean("auto_switch", it).apply()
                                }
                            )
                        }
                    }

                    // ۴. سوییچ اتصال خودکار در تغییر شبکه
                    Surface(
                        color = Color(0xFF25242B),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF3E3D46), RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "🌐 Auto-Reconnect on Net Change",
                                fontSize = 13.sp,
                                color = Color(0xFFF5EFE6),
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = autoReconnect,
                                onCheckedChange = {
                                    autoReconnect = it
                                    prefs.edit().putBoolean("auto_reconnect", it).apply()
                                }
                            )
                        }
                    }

                    // ۵. ردیف لاگ برنامه و لاگ هسته
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showMenu = false; showLog = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(Strings.menuAppLog(appLang), fontSize = 11.sp, maxLines = 1, color = Color(0xFFE6CFAF))
                        }
                        OutlinedButton(
                            onClick = {
                                showMenu = false
                                startActivity(Intent(this@MainActivity, LogcatActivity::class.java))
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(Strings.menuCoreLog(appLang), fontSize = 11.sp, maxLines = 1, color = Color(0xFFE6CFAF))
                        }
                    }
                }
            }
        )
    }

    /**
     * دیالوگ فعال‌سازی و مدیریت اشتراک ۱ ماه ۵۰ گیگ
     */
    @Composable
    private fun VipSubscriptionDialog() {
        var inputCode by remember { mutableStateOf("") }
        var subUrlInput by remember { mutableStateOf(customVipSubUrl) }
        var feedback by remember { mutableStateOf<String?>(null) }
        var isSuccess by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showVipDialog = false },
            confirmButton = {
                TextButton(onClick = { showVipDialog = false }) {
                    Text(Strings.close(appLang), color = Color(0xFFE6CFAF))
                }
            },
            title = {
                Text(
                    text = "👑 1 Month 50 GB Plan",
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFE6CFAF)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (vipActive) {
                        val remDays = ((vipExpireMs - System.currentTimeMillis()) / (24L * 3600_000L)).coerceAtLeast(0L)
                        val remHours = (((vipExpireMs - System.currentTimeMillis()) % (24L * 3600_000L)) / 3600_000L).coerceAtLeast(0L)
                        val remBytes = (VipLicenseManager.VIP_MAX_BYTES - vipUsedBytes).coerceAtLeast(0L)

                        Surface(
                            color = Color(0xFF25242B),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFE6CFAF).copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Status: Active ✅ (Ad-Free)",
                                    color = Color(0xFFF5E6D3),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Active Code: $vipCode",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "Time Remaining: ${remDays}d ${remHours}h (of 30 days)",
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "Traffic Remaining: ${fmtBytes(remBytes)} (Used: ${fmtBytes(vipUsedBytes)} / 50 GB)",
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Use Dedicated VIP Servers",
                                fontSize = 13.sp
                            )
                            Switch(
                                checked = useVipServers,
                                onCheckedChange = {
                                    useVipServers = it
                                    prefs.edit().putBoolean("use_vip_servers", it).apply()
                                }
                            )
                        }
                    } else {
                        Text(
                            text = "Enter your variable activation code to unlock 1 Month / 50 GB dedicated servers and Ad-Free mode:",
                            fontSize = 13.sp
                        )

                        OutlinedTextField(
                            value = inputCode,
                            onValueChange = { inputCode = it.uppercase(Locale.US) },
                            placeholder = { Text("PVIP-XXXX-YYYY") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                if (subUrlInput.trim() != customVipSubUrl) {
                                    customVipSubUrl = subUrlInput.trim()
                                    prefs.edit().putString("custom_vip_sub_url", customVipSubUrl).apply()
                                }
                                val (ok, msg) = activateVipWithCode(inputCode)
                                isSuccess = ok
                                feedback = msg
                                if (ok && vipBestList.isEmpty()) {
                                    startScanOnly()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD8BE9B))
                        ) {
                            Text(
                                text = "Activate 1 Month 50 GB 👑",
                                color = Color(0xFF1A1714),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        feedback?.let { msg ->
                            Text(
                                text = msg,
                                color = if (isSuccess) Color(0xFFF5E6D3) else Color(0xFFE57373),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // بخش اختیاری لینک سابسکریپشن اختصاصی سرورهای ویژه
                    OutlinedTextField(
                        value = subUrlInput,
                        onValueChange = {
                            subUrlInput = it
                            customVipSubUrl = it.trim()
                            prefs.edit().putString("custom_vip_sub_url", customVipSubUrl).apply()
                        },
                        label = {
                            Text(
                                "Custom VIP Subscription URL (Optional)",
                                fontSize = 11.sp
                            )
                        },
                        placeholder = { Text("https://.../vip-sub.txt", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }

    /**
     * دیالوگ مدرن بهترین کانفیگ‌ها مرتب‌شده بر اساس پینگ، بدون ستاره و بدون عبارات 10G ULTRA
     */
    @Composable
    private fun BestConfigsDialog(selectedGuid: String?, isRunning: Boolean, isVipMode: Boolean) {
        val listToShow = activeList.sortedBy { it.ms }
        AlertDialog(
            onDismissRequest = { showBest = false },
            confirmButton = {
                TextButton(onClick = { showBest = false }) {
                    Text(Strings.close(appLang), color = Color(0xFFE6CFAF))
                }
            },
            title = {
                Text(
                    text = Strings.menuBestConfigs(appLang, isVipMode),
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                if (listToShow.isEmpty()) {
                    Text(Strings.emptyBestList(appLang))
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(listToShow, key = { _, item -> item.guid }) { index, s ->
                            StaggeredServerItem(index = index) {
                                val active = isRunning && s.guid == selectedGuid
                                Surface(
                                    color = if (active) Color(0xFF332C24) else Color(0xFF222127),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(
                                            1.dp,
                                            if (active) Color(0xFFE6CFAF) else Color(0xFF3A3942),
                                            RoundedCornerShape(14.dp)
                                        )
                                        .clickable(enabled = phase == Phase.IDLE) {
                                            showBest = false
                                            connectToEntry(s)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 11.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // باکس کد نود (بدون پرچم کشور)
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(9.dp))
                                                .background(Color(0xFF17181D))
                                                .border(
                                                    1.dp,
                                                    if (active) Color(0xFFD8DCE6).copy(alpha = 0.8f) else Color(0xFF3E404A),
                                                    RoundedCornerShape(9.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = s.codeTag.ifEmpty { "A-1" },
                                                color = if (active) Color(0xFFF2F4F8) else Color(0xFFD0D4DF),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        Spacer(Modifier.width(9.dp))
                                        // نام سرور بدون ستاره
                                        Text(
                                            text = s.label,
                                            color = Color(0xFFF2F4F8),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        // نمایش پینگ بدون عبارات 10G ULTRA
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "${s.ms} ms",
                                                color = if (active) Color(0xFFE6CFAF) else Color(0xFFC8CCD6),
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            if (active) {
                                                Text(
                                                    text = "✓",
                                                    color = Color(0xFFF2F4F8),
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    @Composable
    private fun LogDialog() {
        AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text(Strings.menuAppLog(appLang)) },
            text = {
                if (logLines.isEmpty()) {
                    Text(Strings.emptyLogs(appLang))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                        items(logLines.toList().asReversed()) { line ->
                            Text(
                                text = line,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { copyLogToClipboard() }) { Text(Strings.copy(appLang)) }
                    TextButton(onClick = { logLines.clear() }) { Text(Strings.clear(appLang)) }
                }
            },
            dismissButton = { TextButton(onClick = { showLog = false }) { Text(Strings.close(appLang)) } }
        )
    }

    // ═════════════════════════ اکشن‌ها ═════════════════════════

    private fun onPowerClick() {
        if (phase != Phase.IDLE) {
            cancelWork()
            return
        }
        if (mainViewModel.uiState.value.isRunning) {
            disconnect()
            return
        }
        if (checkVipExpiration()) return

        if (SettingsManager.isVpnMode()) {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                requestVpnPermission.launch(intent)
                return
            }
        }
        startSmartConnect()
    }

    private fun disconnect() {
        userWantsConnected = false
        prefs.edit().putBoolean("user_connected", false).remove("connect_time").apply()
        LauncherManager.stopService(this)
        statusMessage = "Disconnected"
        connectStartMs = 0L
        plog("Disconnected by user")
        showInterstitialAdIfReady()
    }

    private fun cancelWork() {
        workJob?.cancel()
        MessageHelper.sendMsg2TestService(
            this, TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_CANCEL)
        )
        phase = Phase.IDLE
        scanProgress = 0f
        statusMessage = "Cancelled"
        plog("Operation cancelled by user")
    }

    private fun startSmartConnect() {
        if (phase != Phase.IDLE) return
        phase = Phase.CONNECTING
        statusMessage = "Preparing..."
        failedGuids.clear()
        if (Build.VERSION.SDK_INT >= 37) {
            checkAndRequestPermission(PermissionType.ACCESS_LOCAL_NETWORK) {}
        }

        workJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                var scanned = false
                val stale = System.currentTimeMillis() - activeScanTime > STALE_MS
                if (activeList.isEmpty() || stale) {
                    scanned = runScan()
                    if (!scanned && activeList.isEmpty()) return@launch
                }
                var result = connectFromList()
                if (result == null && !scanned) {
                    plog("All cached servers failed; rescanning...")
                    failedGuids.clear()
                    if (runScan()) result = connectFromList()
                }
                ui {
                    statusMessage = if (result != null) {
                        showInterstitialAdIfReady()
                        "Connected"
                    } else {
                        "No working server found. Check App Logs."
                    }
                }
                if (result == null) LauncherManager.stopService(this@MainActivity)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                plog("Error: ${e.javaClass.simpleName}: ${e.message}")
                ui { statusMessage = "Error: ${e.localizedMessage ?: "Network issue"}" }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    phase = Phase.IDLE
                    scanProgress = 0f
                }
            }
        }
    }

    private fun startScanOnly() {
        if (phase != Phase.IDLE) return
        phase = Phase.SCANNING
        workJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ok = runScan()
                ui {
                    statusMessage = if (ok) {
                        "Scan complete: ${activeList.size} servers ready"
                    } else statusMessage
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                plog("Scan error: ${e.javaClass.simpleName}: ${e.message}")
                ui { statusMessage = "Error: ${e.localizedMessage ?: "Connection failed"}" }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    phase = Phase.IDLE
                    scanProgress = 0f
                }
            }
        }
    }

    private fun switchNext() {
        if (phase != Phase.IDLE) return
        phase = Phase.CONNECTING
        statusMessage = "Switching to next server..."
        val current = mainViewModel.uiState.value.selectedGuid
        workJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (current != null) failedGuids.add(current)
                val res = connectFromList(startAfter = current)
                if (res == null && current != null) {
                    plog("Next server not found; reverting to previous")
                    failedGuids.remove(current)
                    connectAndVerify(current)
                }
                ui {
                    statusMessage = if (res != null) {
                        "Switched to new server"
                    } else {
                        "No other healthy server found."
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                plog("Switch error: ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                withContext(NonCancellable + Dispatchers.Main) { phase = Phase.IDLE }
            }
        }
    }

    private fun connectToEntry(entry: PennyServer) {
        if (phase != Phase.IDLE) return
        if (!mainViewModel.uiState.value.isRunning && SettingsManager.isVpnMode() &&
            VpnService.prepare(this) != null
        ) {
            statusMessage = "Tap the main Connect button once to grant VPN permission."
            return
        }
        phase = Phase.CONNECTING
        statusMessage = "Connecting to ${entry.label}..."
        workJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ping = connectAndVerify(entry.guid)
                if (ping > 0) {
                    markConnected(entry.guid, ping)
                    ui {
                        showInterstitialAdIfReady()
                        statusMessage = "Connected"
                    }
                } else {
                    ui {
                        statusMessage = "Server did not respond; try another one."
                    }
                    plog("❌ ${entry.label} failed")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                plog("Error: ${e.message}")
            } finally {
                withContext(NonCancellable + Dispatchers.Main) { phase = Phase.IDLE }
            }
        }
    }

    // ═════════════════════════ مانیتور و تعویض خودکار ═════════════════════════

    private fun startMonitor() {
        lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                delay(MONITOR_INTERVAL_MS)
                if (phase != Phase.IDLE) continue
                if (checkVipExpiration()) continue

                val running = mainViewModel.uiState.value.isRunning
                if (!running && userWantsConnected && autoReconnect && activeList.isNotEmpty()) {
                    plog("⚠ Unexpected VPN drop detected; running auto-reconnect...")
                    triggerAutoReconnect()
                    continue
                }

                if (!autoSwitch || !running) continue
                try {
                    healthCheckAndSwitch()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    plog("Monitor error: ${e.message}")
                }
            }
        }
    }

    private suspend fun healthCheckAndSwitch() {
        var ok = false
        for (i in 0 until 2) {
            if (testThroughLocalProxy(TEST_URLS[i % TEST_URLS.size]) > 0) {
                ok = true
                break
            }
            delay(2000)
        }
        if (ok) return

        plog("⚠ Active tunnel unresponsive; auto-switching...")
        val current = mainViewModel.uiState.value.selectedGuid
        if (current != null) failedGuids.add(current)
        ui {
            phase = Phase.CONNECTING
            statusMessage = "Connection dropped; auto-switching..."
        }
        try {
            val r = connectFromList(startAfter = current)
            ui {
                statusMessage = if (r != null) {
                    "Switched to healthier server"
                } else {
                    "No healthy server found; please scan"
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Main) { phase = Phase.IDLE }
        }
    }

    // ═════════════════════════ اسکن (جداگانه برای عادی و اشتراک ۱ ماه ۵۰ گیگ) ═════════════════════════

    private suspend fun runScan(): Boolean {
        val scanningVip = vipActive && useVipServers
        val sources = if (scanningVip) getVipSubscriptionSources() else SUBSCRIPTION_SOURCES

        ui {
            phase = Phase.SCANNING
            scanProgress = 0.03f
            statusMessage = if (scanningVip) "Fetching dedicated VIP servers..." else "Fetching server lists..."
        }
        plog("Scan started (VIP=$scanningVip)")

        val contents = coroutineScope {
            sources.map { url ->
                async(Dispatchers.IO) { fetchWithFallback(url) }
            }.awaitAll().filter { it.isNotEmpty() }
        }
        if (contents.isEmpty()) {
            plog("No subscription sources downloaded")
            ui {
                statusMessage = "Failed to download servers! Check your connection."
            }
            return false
        }

        ui {
            scanProgress = 0.15f
            statusMessage = "Processing configs..."
        }
        val before = MmkvManager.decodeServerList("").toSet()
        for (content in contents) {
            try {
                val r = AngConfigManager.importBatchConfig(content, "", true)
                plog("import result: $r")
            } catch (e: Exception) {
                plog("import error: ${e.message}")
            }
        }
        val newGuids = MmkvManager.decodeServerList("").filter { it !in before }
        // پاک‌سازی فوری نام اصلی سرورهای ایمپورت شده تا در هیچ بخشی از سیستم یا نوتیفیکیشن دیده نشود
        newGuids.forEach { g ->
            try {
                val cfg = MmkvManager.decodeServerConfig(g)
                if (cfg != null && cfg.remarks != "Penny VPN") {
                    cfg.remarks = "Penny VPN"
                    MmkvManager.encodeServerConfig(g, cfg)
                }
            } catch (_: Exception) {}
        }
        plog("New configs: ${newGuids.size}")
        if (newGuids.isEmpty()) {
            ui {
                statusMessage = "No valid configs found!"
            }
            return false
        }

        val tcpSampleCount = minOf(newGuids.size, MAX_TCP_CANDIDATES)
        ui {
            scanProgress = 0.25f
            statusMessage = "TCP probing $tcpSampleCount servers..."
        }
        val tcpOk = tcpFilter(newGuids.shuffled().take(MAX_TCP_CANDIDATES))
        plog("Reachable (TCP): ${tcpOk.size}")
        if (tcpOk.isEmpty()) {
            MmkvManager.removeServers(newGuids, "")
            ui {
                statusMessage = "No reachable servers found."
            }
            return false
        }

        val keep = tcpOk.take(REAL_TEST_COUNT).map { it.first }
        val keepSet = keep.toHashSet()
        val oldFree = bestList.map { it.guid }.toHashSet()
        val oldVip = vipBestList.map { it.guid }.toHashSet()
        val selected = mainViewModel.uiState.value.selectedGuid
        val protect = HashSet<String>().apply {
            addAll(keepSet); addAll(oldFree); addAll(oldVip); selected?.let { add(it) }
        }
        val toRemove = MmkvManager.decodeServerList("").filter { it !in protect }
        MmkvManager.removeServers(toRemove, "")

        MmkvManager.clearAllTestDelayResults(keep)
        ui {
            scanProgress = 0.35f
            statusMessage = "Real ping test (0/${keep.size})..."
        }
        withContext(Dispatchers.Main) {
            MessageHelper.sendMsg2TestService(
                this@MainActivity,
                TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_START, serverGuids = keep),
                UUID.randomUUID().toString()
            )
        }

        val startT = System.currentTimeMillis()
        var done = 0
        while (System.currentTimeMillis() - startT < REAL_TEST_TIMEOUT_MS) {
            delay(700)
            done = keep.count { (MmkvManager.decodeServerAffiliationInfo(it)?.testDelayMillis ?: 0L) != 0L }
            val d = done
            ui {
                scanProgress = 0.35f + 0.55f * d / keep.size
                statusMessage = "Real ping test ($d/${keep.size})..."
            }
            if (done >= keep.size) break
        }
        if (done < keep.size) {
            withContext(Dispatchers.Main) {
                MessageHelper.sendMsg2TestService(
                    this@MainActivity, TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_CANCEL)
                )
            }
        }

        val valid = keep.mapNotNull { g ->
            val d = MmkvManager.decodeServerAffiliationInfo(g)?.testDelayMillis ?: 0L
            if (d > 0L) g to d else null
        }.sortedBy { it.second }
        plog("Healthy in real test: ${valid.size}/${keep.size}")

        if (valid.isEmpty()) {
            MmkvManager.removeServers(keep.filter { it != selected }, "")
            ui {
                statusMessage = "No healthy servers passed verification."
            }
            return false
        }

        ui {
            scanProgress = 0.93f
            statusMessage = "Building best servers list..."
        }
        val top = valid.take(BEST_LIST_SIZE)
        val entries = assignLabels(
            top.map { (g, d) -> PennyServer(g, "", d, "", scanningVip) },
            isVipList = scanningVip
        )

        val now = System.currentTimeMillis()
        saveList(entries, now, isVip = scanningVip)
        ui {
            if (scanningVip) {
                vipBestList.clear()
                vipBestList.addAll(entries)
                vipLastScanTime = now
            } else {
                bestList.clear()
                bestList.addAll(entries)
                lastScanTime = now
            }
            scanProgress = 1f
        }
        failedGuids.clear()
        plog("Scan complete (VIP=$scanningVip): ${entries.size} servers")
        return true
    }

    // ═════════════════════════ اتصال ═════════════════════════

    private suspend fun connectFromList(startAfter: String? = null): PennyServer? {
        val snapshot = activeList.toList()
        val rotated = if (startAfter != null) {
            val startIdx = snapshot.indexOfFirst { it.guid == startAfter }
            if (startIdx >= 0) snapshot.drop(startIdx + 1) + snapshot.take(startIdx + 1) else snapshot
        } else {
            val preferred = snapshot.firstOrNull { it.guid == lastConnectedGuid }
            if (preferred != null) listOf(preferred) + snapshot.filter { it.guid != preferred.guid }
            else snapshot
        }
        val order = rotated.filter { it.guid !in failedGuids && it.guid != startAfter }

        var attempts = 0
        for (s in order) {
            if (attempts >= MAX_CONNECT_ATTEMPTS) break
            attempts++
            ui {
                phase = Phase.CONNECTING
                statusMessage = "Connecting to ${s.label}..."
            }
            plog("Attempt $attempts: ${s.label}")
            val ping = connectAndVerify(s.guid)
            if (ping > 0) {
                plog("✅ Connected: ${s.label} (${ping}ms)")
                markConnected(s.guid, ping)
                return s
            }
            failedGuids.add(s.guid)
            plog("❌ Failed: ${s.label}")
        }
        return null
    }

    private suspend fun markConnected(guid: String, verifiedPingMs: Long = -1L) {
        val now = System.currentTimeMillis()
        val isVip = vipActive && useVipServers
        prefs.edit()
            .putString("last_guid", guid)
            .putLong("connect_time", now)
            .putBoolean("user_connected", true)
            .apply()
        ui {
            connectStartMs = now
            lastConnectedGuid = guid
            userWantsConnected = true
            if (verifiedPingMs > 0L) {
                val targetList = if (isVip) vipBestList else bestList
                val idx = targetList.indexOfFirst { it.guid == guid }
                if (idx >= 0) {
                    val updated = targetList.toMutableList()
                    updated[idx] = updated[idx].copy(ms = verifiedPingMs)
                    val relabeled = assignLabels(updated.sortedBy { it.ms }, isVipList = isVip)
                    targetList.clear()
                    targetList.addAll(relabeled)
                    saveList(relabeled, if (isVip) vipLastScanTime else lastScanTime, isVip = isVip)
                }
            }
        }
    }

    private suspend fun connectAndVerify(guid: String): Long {
        val wasRunning = mainViewModel.uiState.value.isRunning
        withContext(Dispatchers.Main) {
            MmkvManager.setSelectServer(guid)
            mainViewModel.updateSelectedGuid(guid)
            if (wasRunning) LauncherManager.restartService(this@MainActivity)
            else LauncherManager.startService(this@MainActivity)
        }
        plog(if (wasRunning) "restartService" else "startService")

        delay(if (wasRunning) 2500L else 1500L)
        var waited = 0
        while (!mainViewModel.uiState.value.isRunning && waited < 10000) {
            delay(300)
            waited += 300
        }
        if (!mainViewModel.uiState.value.isRunning) {
            plog("Service did not start (${waited}ms)")
            return -1L
        }
        delay(1500)

        if (!withContext(Dispatchers.IO) { isLocalPortOpen() }) {
            plog("Warning: SOCKS port $socksPort not open yet")
        }
        repeat(2) {
            for (url in TEST_URLS) {
                val ping = withContext(Dispatchers.IO) { testThroughLocalProxy(url) }
                if (ping > 0) return ping
            }
            delay(1200)
        }
        return -1L
    }

    // ═════════════════════════ دانلود ═════════════════════════

    private fun downloadUrl(urlStr: String): String {
        val url = URL("$urlStr?t=${System.currentTimeMillis()}")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 7000
            conn.readTimeout = 7000
            conn.setRequestProperty("User-Agent", "v2rayNG/1.8.0")
            return conn.inputStream.bufferedReader().use { it.readText() }.trim()
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchWithFallback(urlStr: String): String {
        val mirrors = listOf(
            urlStr,
            "https://ghproxy.net/$urlStr",
            "https://mirror.ghproxy.com/$urlStr"
        )
        for ((i, mirror) in mirrors.withIndex()) {
            try {
                val content = downloadUrl(mirror)
                if (content.isNotEmpty()) {
                    plog("Download OK (mirror ${i + 1}): ${content.length} chars")
                    return content
                }
            } catch (e: Exception) {
                plog("Download failed (mirror ${i + 1}): ${e.message}")
            }
        }
        return ""
    }

    // ═════════════════════════ تست شبکه ═════════════════════════

    private fun testServerTcpPing(guid: String): Long {
        val profile = MmkvManager.decodeServerConfig(guid) ?: return -1L
        val host = profile.server?.trim()?.takeIf { it.isNotEmpty() } ?: return -1L
        val port = profile.serverPort?.toString()?.trim()?.toIntOrNull() ?: return -1L
        return try {
            val start = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), TCP_TIMEOUT_MS)
            }
            (System.currentTimeMillis() - start).coerceAtLeast(1L)
        } catch (_: Exception) {
            -1L
        }
    }

    private suspend fun tcpFilter(guids: List<String>): List<Pair<String, Long>> {
        val sem = Semaphore(TCP_PARALLEL)
        return withContext(Dispatchers.IO) {
            guids.map { guid ->
                async { sem.withPermit { guid to testServerTcpPing(guid) } }
            }.awaitAll()
        }.filter { it.second > 0 }.sortedBy { it.second }
    }

    private fun isLocalPortOpen(): Boolean {
        return try {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", socksPort), 800) }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun testThroughLocalProxy(testUrl: String): Long {
        return try {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
            val start = System.currentTimeMillis()
            val conn = URL(testUrl).openConnection(proxy) as HttpURLConnection
            try {
                conn.connectTimeout = 7000
                conn.readTimeout = 7000
                conn.useCaches = false
                val code = conn.responseCode
                if (code == 204 || code == 200) {
                    (System.currentTimeMillis() - start).coerceAtLeast(1L)
                } else {
                    plog("Proxy test HTTP $code")
                    -1L
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            plog("Proxy test failed: ${e.javaClass.simpleName}: ${e.message}")
            -1L
        }
    }
}
