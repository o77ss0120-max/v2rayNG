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

// ═════════════════════════ زبان (فارسی / انگلیسی) ═════════════════════════

private enum class AppLang(val code: String) { FA("fa"), EN("en") }

private object Strings {
    fun subtitle(l: AppLang, vip: Boolean) =
        if (l == AppLang.FA) {
            if (vip) "👑 اشتراک ۱ ماه ۵۰ گیگ فعال • سرورهای اختصاصی" else "انتخابگر هوشمند سریع‌ترین سرور"
        } else {
            if (vip) "👑 1 Month 50 GB Active • Dedicated Servers" else "Smart Fastest Server Selector"
        }

    fun splashTagline(l: AppLang) =
        if (l == AppLang.FA) "اتصال امن، هوشمند و پرسرعت" else "Fast, Smart & Secure Protection"

    fun ready(l: AppLang) = if (l == AppLang.FA) "آماده اتصال" else "Ready to Connect"
    fun connectedBadge(l: AppLang, vip: Boolean) =
        if (l == AppLang.FA) {
            if (vip) "متصل به سرور ویژه (۱ ماه ۵۰ گیگ) 👑" else "متصل ✅"
        } else {
            if (vip) "Connected to VIP (1M / 50GB) 👑" else "Connected ✅"
        }

    fun smartConnect(l: AppLang, vip: Boolean) =
        if (l == AppLang.FA) {
            if (vip) "اتصال اشتراک ۱ ماه ۵۰ گیگ" else "اتصال هوشمند"
        } else {
            if (vip) "VIP Connect" else "Smart Connect"
        }

    fun tapToCancel(l: AppLang) = if (l == AppLang.FA) "ضربه = لغو" else "Tap to Cancel"
    fun disconnect(l: AppLang) = if (l == AppLang.FA) "قطع اتصال" else "Disconnect"

    fun download(l: AppLang) = if (l == AppLang.FA) "دانلود" else "Download"
    fun upload(l: AppLang) = if (l == AppLang.FA) "آپلود" else "Upload"
    fun duration(l: AppLang) = if (l == AppLang.FA) "مدت اتصال" else "Duration"
    fun totalData(l: AppLang) = if (l == AppLang.FA) "حجم مصرفی" else "Total Data"

    fun noServerYet(l: AppLang, vip: Boolean) =
        if (l == AppLang.FA) {
            if (vip) "هنوز سرور اشتراک ۱ ماه ۵۰ گیگ اسکن نشده؛ دکمه اسکن یا اتصال را بزنید."
            else "هنوز سروری اسکن نشده؛ دکمه اسکن یا اتصال را بزنید."
        } else {
            if (vip) "No VIP servers scanned yet; tap Scan or Connect."
            else "No servers scanned yet; tap Scan or Connect."
        }

    fun scanServersBtn(l: AppLang, vip: Boolean) =
        if (l == AppLang.FA) {
            if (vip) "👑 اسکن سرورهای ویژه" else "🔍 اسکن سرورها"
        } else {
            if (vip) "👑 Scan VIP" else "🔍 Scan Servers"
        }

    fun nextServerBtn(l: AppLang) = if (l == AppLang.FA) "⏭ سرور بعدی" else "⏭ Next Server"

    fun scanInfo(l: AppLang, min: Long, count: Int, vip: Boolean): String {
        val tag = if (vip) "VIP " else ""
        return if (l == AppLang.FA) {
            val ago = when {
                min < 1 -> "چند لحظه پیش"
                min < 60 -> "$min دقیقه پیش"
                else -> "${min / 60} ساعت پیش"
            }
            "آخرین اسکن $tag: $ago  •  $count سرور آماده"
        } else {
            val ago = when {
                min < 1 -> "just now"
                min < 60 -> "${min}m ago"
                else -> "${min / 60}h ago"
            }
            "Last ${tag}scan: $ago  •  $count servers ready"
        }
    }

    fun noScanYet(l: AppLang, vip: Boolean) =
        if (l == AppLang.FA) {
            if (vip) "هنوز اسکن سرورهای ویژه انجام نشده" else "هنوز اسکنی انجام نشده"
        } else {
            if (vip) "No VIP scan performed yet" else "No scan performed yet"
        }

    fun menuBestConfigs(l: AppLang, vip: Boolean) =
        if (l == AppLang.FA) {
            if (vip) "👑 لیست سرورهای اشتراک ۱ ماه ۵۰ گیگ" else "لیست بهترین سرورها"
        } else {
            if (vip) "👑 1M 50GB Dedicated Servers" else "Best Servers List"
        }

    fun menuAppLog(l: AppLang) = if (l == AppLang.FA) "گزارش و لاگ برنامه" else "App Activity Logs"
    fun menuCoreLog(l: AppLang) = if (l == AppLang.FA) "لاگ هسته (Logcat)" else "Core Logs (Logcat)"
    fun close(l: AppLang) = if (l == AppLang.FA) "بستن" else "Close"
    fun copy(l: AppLang) = if (l == AppLang.FA) "کپی" else "Copy"
    fun clear(l: AppLang) = if (l == AppLang.FA) "پاک کردن" else "Clear"
    fun emptyBestList(l: AppLang) =
        if (l == AppLang.FA) "هنوز لیستی ساخته نشده. ابتدا اسکن کنید."
        else "No server list yet. Please scan first."

    fun emptyLogs(l: AppLang) =
        if (l == AppLang.FA) "هنوز لاگی ثبت نشده." else "No logs recorded yet."
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
 * نام‌گذاری سرورها بدون پرچم کشور به فرمت استاندارد:
 * Data center A-1, Data center A-2, Server A-1, Server A-2, Data center B-1, ...
 */
private fun assignLabels(list: List<PennyServer>, isVipList: Boolean = false): List<PennyServer> {
    return list.mapIndexed { index, s ->
        val groupChar = ('A'.code + (index / 4).coerceAtMost(5)).toChar()
        val num = (index % 2) + 1
        val isDataCenter = (index % 4) < 2
        val codeTag = "$groupChar-$num"
        val baseLabel = if (isDataCenter) "Data center $codeTag" else "Server $codeTag"
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

// ═════════════════════════ کامپوننت‌های گرافیکی مدرن ═════════════════════════

@Composable
private fun StarsRow(stars: Int, size: TextUnit = 14.sp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) {
            Text(
                text = "★",
                color = if (i <= stars) Color(0xFFFBBF24) else Color(0xFF334155),
                fontSize = size
            )
        }
    }
}

@Composable
private fun StatCell(icon: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$icon $value", color = Color(0xFFF8FAFC), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = Color(0xFF94A3B8), fontSize = 11.sp)
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
 * کارت مدرن نمایش سرور بدون پرچم کشور (با کد نود A-1، نام سفید یکدست Data center A-1 / Server A-1 و ستاره‌ها روبه‌روی نام)
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
        isConnected -> Color(0xFF10B981).copy(alpha = 0.65f)
        isVipMode -> Color(0xFFF59E0B).copy(alpha = 0.45f)
        else -> Color(0xFF334155).copy(alpha = 0.7f)
    }

    Surface(
        color = Color(0xFF0F172A).copy(alpha = 0.88f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(enabled = enabled) { onClick() }
    ) {
        if (server == null) {
            Text(
                text = Strings.noServerYet(lang, isVipMode),
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // باکس مدرن کد نود (بدون پرچم کشور)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color(0xFF1E293B))
                        .border(
                            1.dp,
                            if (isConnected) Color(0xFF10B981).copy(alpha = 0.5f)
                            else if (isVipMode) Color(0xFFF59E0B).copy(alpha = 0.5f)
                            else Color(0xFF334155),
                            RoundedCornerShape(13.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = server.codeTag.ifEmpty { "A-1" },
                        color = if (isConnected) Color(0xFF34D399)
                        else if (isVipMode) Color(0xFFFBBF24)
                        else Color(0xFF38BDF8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // ردیف افقی: نام سرور (Data center A-1 / Server A-1) با رنگ سفید یکدست + ستاره‌ها دقیقاً جلوی نام
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = server.label,
                            color = Color(0xFFF8FAFC),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        StarsRow(server.stars, 13.sp)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (isConnected) {
                            if (lang == AppLang.FA) "نود فعال • اتصال پایدار" else "Active Node • Stable Tunnel"
                        } else {
                            if (lang == AppLang.FA) "آماده اتصال • برای تغییر سرور ضربه بزنید" else "Ready • Tap to change server"
                        },
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }

                Spacer(Modifier.width(8.dp))

                // بج مدرن وضعیت / کیفیت
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isConnected) Color(0xFF064E3B)
                            else if (isVipMode) Color(0xFF451A03)
                            else Color(0xFF1E293B)
                        )
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (isVipMode) "👑 VIP" else server.tier.badgeText,
                        color = if (isConnected) Color(0xFF34D399)
                        else if (isVipMode) Color(0xFFFBBF24)
                        else Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

/**
 * بنر تبلیغاتی AdMob (برای مشترکین ۱ ماه ۵۰ گیگ به صورت خودکار مخفی می‌شود)
 */
@Composable
private fun AdmobBannerCard(adUnitId: String, modifier: Modifier = Modifier) {
    Surface(
        color = Color(0xFF0F172A).copy(alpha = 0.85f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                factory = { ctx ->
                    AdView(ctx).apply {
                        setAdSize(AdSize.BANNER)
                        this.adUnitId = adUnitId
                        loadAd(AdRequest.Builder().build())
                    }
                }
            )
        }
    }
}

/**
 * اسپلش و انیمیشن خلاقانه ورود با لوگوی گربه VPN
 */
@Composable
private fun PennySplashIntro(lang: AppLang, onFinished: () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        entered = true
        delay(2300)
        onFinished()
    }

    val logoScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.35f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "splashScale"
    )

    val inf = rememberInfiniteTransition(label = "splashInf")
    val orbitAngle by inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "orbitAngle"
    )
    val ringWave by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "ringWave"
    )
    val glowPulse by inf.animateFloat(
        initialValue = 14f,
        targetValue = 32f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = FastOutSlowInEasing),
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
                        Color(0xFF0B2558),
                        Color(0xFF0F172A),
                        Color(0xFF020617)
                    )
                )
            )
            .clickable { onFinished() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(250.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val baseR = size.minDimension * 0.36f

                    for (k in 0..1) {
                        val t = (ringWave + k * 0.5f) % 1f
                        drawCircle(
                            color = Color(0xFF38BDF8).copy(alpha = (1f - t) * 0.38f),
                            radius = baseR + t * size.minDimension * 0.16f,
                            center = center,
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }

                    val arcR = baseR + 10.dp.toPx()
                    val tl = Offset(center.x - arcR, center.y - arcR)
                    val sz = Size(arcR * 2f, arcR * 2f)
                    drawArc(
                        color = Color(0xFF00E676),
                        startAngle = orbitAngle,
                        sweepAngle = 100f,
                        useCenter = false,
                        topLeft = tl,
                        size = sz,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Color(0xFF38BDF8),
                        startAngle = orbitAngle + 180f,
                        sweepAngle = 100f,
                        useCenter = false,
                        topLeft = tl,
                        size = sz,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Image(
                    painter = painterResource(id = R.drawable.penny_cat_logo),
                    contentDescription = "Penny VPN Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(182.dp)
                        .scale(logoScale)
                        .clip(CircleShape)
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "PENNY VPN",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 3.sp,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color(0xFF38BDF8),
                        offset = Offset(0f, 0f),
                        blurRadius = glowPulse
                    )
                )
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = Strings.splashTagline(lang),
                color = Color(0xFF94A3B8),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "powered by Omidss",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
        }
    }
}

/**
 * هدر نئونی متمرکز و پایین‌تر از لبه بالایی صفحه
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
        isVipMode && mode == UiMode.IDLE -> Color(0xFFFFD54F)
        mode == UiMode.IDLE -> Color(0xFF38BDF8)
        mode == UiMode.SCANNING -> Color(0xFFFFAB00)
        mode == UiMode.CONNECTING -> Color(0xFFB388FF)
        else -> Color(0xFF00E676)
    }
    val neonColor by animateColorAsState(glowTarget, tween(700), label = "neonColor")
    val inf = rememberInfiniteTransition(label = "headerNeon")
    val blurRadius by inf.animateFloat(
        initialValue = 14f,
        targetValue = 28f,
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
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, neonColor.copy(alpha = 0.75f), CircleShape)
                    .clickable { onLogoClick() }
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (isVipMode) "PENNY VIP" else "PENNY VPN",
                color = Color.White,
                fontSize = 25.sp,
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
        Spacer(Modifier.height(4.dp))
        Text(
            text = Strings.subtitle(lang, isVipMode),
            color = if (isVipMode) Color(0xFFFFD54F) else Color(0xFF94A3B8),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * انیمیشن پالس زنده روی متن وضعیت اتصال در حالت CONNECTED
 */
@Composable
private fun PulsingStatusText(mode: UiMode, statusMessage: String, lang: AppLang, isVip: Boolean) {
    val isConnected = mode == UiMode.CONNECTED
    val inf = rememberInfiniteTransition(label = "statusPulse")
    val pulseScale by inf.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isConnected) 1.08f else 1.0f,
        animationSpec = infiniteRepeatable(
            tween(1100, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "statusScale"
    )
    val pulseGlow by inf.animateFloat(
        initialValue = 8f,
        targetValue = if (isConnected) 24f else 8f,
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
            color = if (isConnected) Color(0xFF00E676) else Color.White,
            fontSize = 15.sp,
            fontWeight = if (isConnected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            style = if (isConnected) {
                TextStyle(
                    shadow = Shadow(
                        color = Color(0xFF00E676).copy(alpha = 0.85f),
                        offset = Offset(0f, 0f),
                        blurRadius = pulseGlow
                    )
                )
            } else {
                TextStyle.Default
            },
            modifier = Modifier
                .scale(if (isConnected) pulseScale else 1f)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * دکمه اتصال همراه با انیمیشن طبیعی قرار گرفتن گربه روی دایره کانکت (سر بالای دایره و دست‌ها روی لبه دایره)
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
        UiMode.IDLE -> if (isVipMode) Color(0xFFD97706) else Color(0xFF2979FF)
        UiMode.SCANNING -> Color(0xFFFFA000)
        UiMode.CONNECTING -> Color(0xFF7C4DFF)
        UiMode.CONNECTED -> Color(0xFF00E676)
    }
    val color by animateColorAsState(target, tween(700), label = "pbColor")
    val animProgress by animateFloatAsState(progress, tween(400), label = "pbProgress")
    val glow by animateFloatAsState(
        if (isConnected) 1f else 0f, tween(800), label = "pbGlow"
    )

    // انیمیشن طبیعی بالا آمدن گربه و قرار گرفتن دست‌هایش روی لبه بالایی دایره کانکت
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
        initialValue = 1f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ),
        label = "pbBreathe"
    )

    // ارتفاع کانتینر وقتی متصل می‌شود کمی بازتر است تا سر گربه بالای دایره کاملاً جا بگیرد
    val boxHeight = if (isConnected) 288.dp else 236.dp

    Box(
        modifier = Modifier
            .width(245.dp)
            .height(boxHeight),
        contentAlignment = Alignment.BottomCenter
    ) {
        // بخش دایره کانکت و حلقه‌های نئونی در پایین/مرکز
        Box(
            modifier = Modifier.size(230.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = size.minDimension * 0.35f
                when (mode) {
                    UiMode.CONNECTED, UiMode.CONNECTING -> {
                        for (k in 0..2) {
                            val t = (pulse + k / 3f) % 1f
                            drawCircle(
                                color = color.copy(alpha = (1f - t) * 0.30f),
                                radius = r + 8.dp.toPx() + t * size.minDimension * 0.14f,
                                center = c,
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                    }
                    UiMode.SCANNING -> {
                        val rr = r + 14.dp.toPx()
                        val tl = Offset(c.x - rr, c.y - rr)
                        val sz = Size(rr * 2f, rr * 2f)
                        drawArc(
                            color = color.copy(alpha = 0.18f), startAngle = 0f, sweepAngle = 360f,
                            useCenter = false, topLeft = tl, size = sz, style = Stroke(5.dp.toPx())
                        )
                        drawArc(
                            color = color, startAngle = -90f, sweepAngle = 360f * animProgress,
                            useCenter = false, topLeft = tl, size = sz,
                            style = Stroke(5.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = Color.White.copy(alpha = 0.85f), startAngle = spin, sweepAngle = 40f,
                            useCenter = false, topLeft = tl, size = sz,
                            style = Stroke(5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    else -> {}
                }
                drawCircle(
                    color = color.copy(alpha = 0.25f + 0.20f * glow),
                    radius = r + 6.dp.toPx(),
                    center = c,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            Box(
                modifier = Modifier
                    .size(166.dp)
                    .scale(if (mode == UiMode.IDLE) breathe else 1f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.70f)))
                    )
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(targetState = mode, label = "pbText") { m ->
                    val fg = if (m == UiMode.CONNECTED) Color(0xFF022C22) else Color.White
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = if (m == UiMode.CONNECTED) 32.dp else 0.dp)
                    ) {
                        Text(
                            text = when (m) {
                                UiMode.IDLE -> if (isVipMode) "VIP CONNECT" else "CONNECT"
                                UiMode.SCANNING -> "SCANNING"
                                UiMode.CONNECTING -> "CONNECTING"
                                UiMode.CONNECTED -> "CONNECTED"
                            },
                            color = fg, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = when (m) {
                                UiMode.IDLE -> Strings.smartConnect(lang, isVipMode)
                                UiMode.SCANNING -> Strings.tapToCancel(lang)
                                UiMode.CONNECTING -> Strings.tapToCancel(lang)
                                UiMode.CONNECTED -> Strings.disconnect(lang)
                            },
                            color = fg.copy(alpha = 0.82f), fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // گربه لوگو که هنگام اتصال سرش بالای دایره قرار می‌گیرد و دستانش به صورت طبیعی روی لبه دایره می‌نشیند
        if (catPresence > 0.01f) {
            Image(
                painter = painterResource(id = R.drawable.penny_cat_peeking),
                contentDescription = "Peeking Cat on Connect Button",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(136.dp)
                    .height(172.dp)
                    .offset(y = (-6).dp)
                    .graphicsLayer {
                        alpha = catPresence.coerceIn(0f, 1f)
                        translationY = (1f - catPresence) * 42.dp.toPx()
                        val sc = 0.75f + 0.25f * catPresence
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
    private var appLang by mutableStateOf(AppLang.FA)
    private var phase by mutableStateOf(Phase.IDLE)
    private var statusMessage by mutableStateOf("آماده اتصال")
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
                statusMessage = if (appLang == AppLang.FA) "مجوز VPN داده نشد!" else "VPN permission denied!"
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
            val msg = if (appLang == AppLang.FA) {
                if (trafficExpired) "حجم ۵۰ گیگابایت اشتراک شما به پایان رسید."
                else "مهلت ۱ ماهه اشتراک شما به پایان رسید."
            } else {
                if (trafficExpired) "Your 50 GB traffic quota has been reached."
                else "Your 1-month subscription has expired."
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
            ?: return false to (if (appLang == AppLang.FA) "کد واردشده معتبر نیست!" else "Invalid activation code!")

        val usedSet = prefs.getStringSet("used_vip_serials", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (serial in usedSet && vipCode != inputCode.uppercase(Locale.US).trim()) {
            return false to (if (appLang == AppLang.FA) "این کد قبلاً استفاده شده و منقضی گردیده است." else "This code has already been used.")
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
        statusMessage = if (appLang == AppLang.FA)
            "اشتراک ۱ ماه ۵۰ گیگ فعال شد 👑"
        else
            "1 Month 50 GB Plan Activated 👑"

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
                            plog(
                                if (appLang == AppLang.FA)
                                    "🌐 تغییر شبکه یا افت ناگهانی تشخیص داده شد؛ اتصال مجدد خودکار به آخرین سرور سالم..."
                                else
                                    "🌐 Network change / drop detected; auto-reconnecting to last healthy server..."
                            )
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
            statusMessage = if (appLang == AppLang.FA)
                "در حال اتصال مجدد خودکار به آخرین سرور سالم..."
            else
                "Auto-reconnecting to last healthy server..."
        }
        try {
            failedGuids.clear()
            val connected = connectFromList(startAfter = null)
            ui {
                statusMessage = if (connected != null) {
                    if (appLang == AppLang.FA) "اتصال مجدد خودکار برقرار شد ✅" else "Auto-reconnected ✅"
                } else {
                    if (appLang == AppLang.FA) "اتصال مجدد ناموفق بود" else "Auto-reconnect failed"
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Main) {
                phase = Phase.IDLE
            }
        }
    }

    // ═════════════════════════ ذخیره / بازیابی ═════════════════════════

    private fun toggleLanguage() {
        appLang = if (appLang == AppLang.FA) AppLang.EN else AppLang.FA
        prefs.edit().putString("app_lang", appLang.code).apply()
        statusMessage = Strings.ready(appLang)
    }

    private fun loadSaved() {
        val savedLang = prefs.getString("app_lang", "fa")
        appLang = if (savedLang == "en") AppLang.EN else AppLang.FA
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
        statusMessage = if (appLang == AppLang.FA) "لاگ کپی شد" else "Logs copied"
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

        val layoutDir = if (appLang == AppLang.FA) LayoutDirection.Rtl else LayoutDirection.Ltr

        CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF0B132B), Color(0xFF020617)))
                    )
            ) {
                // ── لوگوی کمرنگ (Watermark) در پس‌زمینه ──
                Image(
                    painter = painterResource(id = R.drawable.penny_cat_logo),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(340.dp)
                        .align(Alignment.Center)
                        .alpha(0.06f)
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
                    // ── هدر نئونی متمرکز و پایین‌تر از لبه بالایی ──
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
                            color = Color(0xFF0F172A).copy(alpha = 0.9f),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(18.dp))
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

                    // ── کارت سرور مدرن بدون پرچم کشور (Data center A-1 / Server A-1) ──
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
                                .background(Color(0xFF1E293B))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(p.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .background(Color(0xFFFFA000))
                            )
                        }
                    }

                    // ── دکمه‌های اسکن و سرور بعدی ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { startScanOnly() },
                            enabled = !busy,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text(Strings.scanServersBtn(appLang, isVipMode), fontSize = 13.sp) }

                        Button(
                            onClick = { switchNext() },
                            enabled = !busy && isRunning && currentList.size > 1,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isVipMode) Color(0xFFD97706) else Color(0xFF2563EB)
                            )
                        ) { Text(Strings.nextServerBtn(appLang), fontSize = 13.sp, color = Color.White) }
                    }

                    Spacer(Modifier.height(10.dp))

                    // ── داک مدرن پایین صفحه (اشتراک ۱ ماه ۵۰ گیگ، تغییر زبان و منوی مدرن) ──
                    Surface(
                        color = Color(0xFF0F172A).copy(alpha = 0.92f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // کارت/دکمه اشتراک ۱ ماه ۵۰ گیگ
                                Surface(
                                    color = if (vipActive) Color(0xFF271705) else Color(0xFF1E293B),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(
                                            1.dp,
                                            if (vipActive) Color(0xFFF59E0B).copy(alpha = 0.6f) else Color(0xFF334155),
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
                                                text = if (vipActive) {
                                                    if (appLang == AppLang.FA) "👑 اشتراک ۱ ماه ۵۰ گیگ (فعال)" else "👑 1 Month 50 GB (Active)"
                                                } else {
                                                    if (appLang == AppLang.FA) "👑 اشتراک ۱ ماه ۵۰ گیگ" else "👑 1 Month 50 GB Plan"
                                                },
                                                color = if (vipActive) Color(0xFFFBBF24) else Color(0xFFF8FAFC),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            val subVipText = if (vipActive) {
                                                val remDays = ((vipExpireMs - System.currentTimeMillis()) / (24L * 3600_000L)).coerceAtLeast(0L)
                                                val remBytes = (VipLicenseManager.VIP_MAX_BYTES - vipUsedBytes).coerceAtLeast(0L)
                                                if (appLang == AppLang.FA) "$remDays روز • ${fmtBytes(remBytes)} مانده"
                                                else "${remDays}d • ${fmtBytes(remBytes)} left"
                                            } else {
                                                if (appLang == AppLang.FA) "فعال‌سازی سرورهای ویژه با کد متغیر"
                                                else "Activate dedicated servers with code"
                                            }
                                            Text(
                                                text = subVipText,
                                                color = if (vipActive) Color(0xFFFDE68A) else Color(0xFF94A3B8),
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = if (vipActive) "✓" else "❯",
                                            color = if (vipActive) Color(0xFF34D399) else Color(0xFF94A3B8),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // دکمه تغییر زبان در داک پایین
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(14.dp))
                                        .clickable { toggleLanguage() }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = if (appLang == AppLang.FA) "EN" else "FA",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            text = if (appLang == AppLang.FA) "زبان" else "Lang",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                // دکمه منوی مدرن در داک پایین
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(14.dp))
                                        .clickable { showMenu = true }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "⚙",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (appLang == AppLang.FA) "منو" else "Menu",
                                            color = Color(0xFF94A3B8),
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
                                        .background(Color(0xFF451A03))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth((1f - progressRatio).coerceIn(0f, 1f))
                                            .fillMaxHeight()
                                            .background(Color(0xFFF59E0B))
                                    )
                                }
                            }
                        }
                    }

                    // ── تبلیغ بنری AdMob برای کاربران عادی (در حالت اشتراک ۱ ماه ۵۰ گیگ مخفی است) ──
                    if (!vipActive) {
                        Spacer(Modifier.height(10.dp))
                        AdmobBannerCard(adUnitId = ADMOB_BANNER_UNIT_ID)
                    }

                    Spacer(Modifier.height(8.dp))
                    val scanInfo = if (activeScanTime > 0L) {
                        val min = (System.currentTimeMillis() - activeScanTime) / 60000L
                        Strings.scanInfo(appLang, min, currentList.size, isVipMode)
                    } else Strings.noScanYet(appLang, isVipMode)
                    Text(scanInfo, color = Color(0xFF64748B), fontSize = 11.sp)

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "powered by Omidss",
                        color = Color(0xFF94A3B8).copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp
                    )
                }

                // ── اسپلش خلاقانه ابتدای برنامه با لوگوی گربه VPN ──
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
     * منوی مدرن کارتی
     */
    @Composable
    private fun ModernMenuDialog(isVipMode: Boolean) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            confirmButton = {
                TextButton(onClick = { showMenu = false }) {
                    Text(Strings.close(appLang))
                }
            },
            title = {
                Text(
                    text = if (appLang == AppLang.FA) "⚙ منوی تنظیمات و امکانات" else "⚙ Settings & Control Center",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // ۱. کارت اشتراک ۱ ماه ۵۰ گیگ
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
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
                                text = if (appLang == AppLang.FA) "👑 اشتراک ۱ ماه ۵۰ گیگ" else "👑 1 Month 50 GB Plan",
                                color = Color(0xFFFBBF24),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text("❯", color = Color(0xFF94A3B8))
                        }
                    }

                    // ۲. کارت لیست بهترین سرورها
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
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
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text("${activeList.size}", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    // ۳. سوییچ تعویض خودکار هنگام قطعی
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (appLang == AppLang.FA) "🔄 تعویض خودکار هنگام قطعی" else "🔄 Auto-Switch on Drop",
                                fontSize = 13.sp,
                                color = Color.White,
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
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (appLang == AppLang.FA) "🌐 اتصال خودکار در تغییر شبکه" else "🌐 Auto-Reconnect on Net Change",
                                fontSize = 13.sp,
                                color = Color.White,
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
                            Text(Strings.menuAppLog(appLang), fontSize = 11.sp, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                showMenu = false
                                startActivity(Intent(this@MainActivity, LogcatActivity::class.java))
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(Strings.menuCoreLog(appLang), fontSize = 11.sp, maxLines = 1)
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
                    Text(Strings.close(appLang))
                }
            },
            title = {
                Text(
                    text = if (appLang == AppLang.FA) "👑 اشتراک ۱ ماه ۵۰ گیگ" else "👑 1 Month 50 GB Plan",
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFBBF24)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (vipActive) {
                        val remDays = ((vipExpireMs - System.currentTimeMillis()) / (24L * 3600_000L)).coerceAtLeast(0L)
                        val remHours = (((vipExpireMs - System.currentTimeMillis()) % (24L * 3600_000L)) / 3600_000L).coerceAtLeast(0L)
                        val remBytes = (VipLicenseManager.VIP_MAX_BYTES - vipUsedBytes).coerceAtLeast(0L)

                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (appLang == AppLang.FA) "وضعیت: فعال ✅ (بدون تبلیغات)" else "Status: Active ✅ (Ad-Free)",
                                    color = Color(0xFF00E676),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (appLang == AppLang.FA) "کد فعال: $vipCode" else "Active Code: $vipCode",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = if (appLang == AppLang.FA)
                                        "زمان باقی‌مانده: $remDays روز و $remHours ساعت (از ۳۰ روز)"
                                    else
                                        "Time Remaining: ${remDays}d ${remHours}h (of 30 days)",
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = if (appLang == AppLang.FA)
                                        "حجم باقی‌مانده: ${fmtBytes(remBytes)} (مصرف‌شده: ${fmtBytes(vipUsedBytes)} از 50 GB)"
                                    else
                                        "Traffic Remaining: ${fmtBytes(remBytes)} (Used: ${fmtBytes(vipUsedBytes)} / 50 GB)",
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
                                text = if (appLang == AppLang.FA) "استفاده از سرورهای ویژه (۱ ماه ۵۰ گیگ)" else "Use Dedicated VIP Servers",
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
                            text = if (appLang == AppLang.FA)
                                "کد متغیر اشتراک ۱ ماه ۵۰ گیگ خود را وارد کنید تا سرورهای اختصاصی پرسرعت و حالت بدون تبلیغ فعال شود:"
                            else
                                "Enter your variable activation code to unlock 1 Month / 50 GB dedicated servers and Ad-Free mode:",
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                        ) {
                            Text(
                                text = if (appLang == AppLang.FA) "فعال‌سازی اشتراک ۱ ماه ۵۰ گیگ 👑" else "Activate 1 Month 50 GB 👑",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        feedback?.let { msg ->
                            Text(
                                text = msg,
                                color = if (isSuccess) Color(0xFF00E676) else Color(0xFFEF4444),
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
                                if (appLang == AppLang.FA) "لینک سابسکریپشن سرورهای ویژه (اختیاری)"
                                else "Custom VIP Subscription URL (Optional)",
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
     * دیالوگ مدرن بهترین کانفیگ‌ها بدون پرچم کشور (Data center A-1 / Server A-1) + ستاره‌ها جلوی نام سرور
     */
    @Composable
    private fun BestConfigsDialog(selectedGuid: String?, isRunning: Boolean, isVipMode: Boolean) {
        val listToShow = activeList.toList()
        AlertDialog(
            onDismissRequest = { showBest = false },
            confirmButton = { TextButton(onClick = { showBest = false }) { Text(Strings.close(appLang)) } },
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
                                    color = if (active) Color(0xFF064E3B).copy(alpha = 0.45f) else Color(0xFF1E293B),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(
                                            1.dp,
                                            if (active) Color(0xFF10B981) else Color(0xFF334155),
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
                                            .padding(horizontal = 12.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // باکس کد نود (بدون پرچم کشور)
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF0F172A))
                                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = s.codeTag.ifEmpty { "A-1" },
                                                color = if (active) Color(0xFF34D399) else Color(0xFF38BDF8),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        // نام سرور (Data center A-1 / Server A-1) با رنگ سفید یکدست + ستاره‌ها دقیقاً جلوی نام
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = s.label,
                                                color = Color(0xFFF8FAFC),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            StarsRow(s.stars, 13.sp)
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        if (active) {
                                            Text("✅", fontSize = 16.sp)
                                        } else {
                                            Text(
                                                text = s.tier.badgeText,
                                                color = Color(0xFF94A3B8),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
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
        statusMessage = if (appLang == AppLang.FA) "اتصال قطع شد" else "Disconnected"
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
        statusMessage = if (appLang == AppLang.FA) "لغو شد" else "Cancelled"
        plog("Operation cancelled by user")
    }

    private fun startSmartConnect() {
        if (phase != Phase.IDLE) return
        phase = Phase.CONNECTING
        statusMessage = if (appLang == AppLang.FA) "در حال آماده‌سازی..." else "Preparing..."
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
                        if (appLang == AppLang.FA) "متصل شد" else "Connected"
                    } else {
                        if (appLang == AppLang.FA) "هیچ سرور سالمی پیدا نشد. لاگ برنامه را ببینید."
                        else "No working server found. Check App Logs."
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
                        if (appLang == AppLang.FA) "اسکن تمام شد: ${activeList.size} سرور آماده"
                        else "Scan complete: ${activeList.size} servers ready"
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
        statusMessage = if (appLang == AppLang.FA) "در حال جستجوی سرور بعدی..." else "Switching to next server..."
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
                        if (appLang == AppLang.FA) "سرور جدید وصل شد" else "Switched to new server"
                    } else {
                        if (appLang == AppLang.FA) "سرور سالم دیگری پیدا نشد." else "No other healthy server found."
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
            statusMessage = if (appLang == AppLang.FA)
                "ابتدا یک بار روی دکمه اتصال بزنید تا مجوز VPN داده شود."
            else
                "Tap the main Connect button once to grant VPN permission."
            return
        }
        phase = Phase.CONNECTING
        statusMessage = if (appLang == AppLang.FA) "اتصال به ${entry.label}..." else "Connecting to ${entry.label}..."
        workJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ping = connectAndVerify(entry.guid)
                if (ping > 0) {
                    markConnected(entry.guid, ping)
                    ui {
                        showInterstitialAdIfReady()
                        statusMessage = if (appLang == AppLang.FA) "متصل شد" else "Connected"
                    }
                } else {
                    ui {
                        statusMessage = if (appLang == AppLang.FA)
                            "این سرور کار نکرد؛ سرور دیگری انتخاب کنید."
                        else
                            "Server did not respond; try another one."
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
            statusMessage = if (appLang == AppLang.FA) "اتصال قطع شد؛ تعویض خودکار..." else "Connection dropped; auto-switching..."
        }
        try {
            val r = connectFromList(startAfter = current)
            ui {
                statusMessage = if (r != null) {
                    if (appLang == AppLang.FA) "به سرور بهتر تعویض شد" else "Switched to healthier server"
                } else {
                    if (appLang == AppLang.FA) "سرور سالمی پیدا نشد؛ اسکن کنید" else "No healthy server found; please scan"
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
            statusMessage = if (appLang == AppLang.FA) {
                if (scanningVip) "در حال دریافت سرورهای ویژه (۱ ماه ۵۰ گیگ)..." else "در حال دریافت لیست سرورها..."
            } else {
                if (scanningVip) "Fetching dedicated VIP servers..." else "Fetching server lists..."
            }
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
                statusMessage = if (appLang == AppLang.FA)
                    "خطا در دانلود سرورها! اینترنت یا فیلترشکن را بررسی کنید."
                else
                    "Failed to download servers! Check your connection."
            }
            return false
        }

        ui {
            scanProgress = 0.15f
            statusMessage = if (appLang == AppLang.FA) "در حال پردازش کانفیگ‌ها..." else "Processing configs..."
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
        plog("New configs: ${newGuids.size}")
        if (newGuids.isEmpty()) {
            ui {
                statusMessage = if (appLang == AppLang.FA) "هیچ کانفیگ معتبری یافت نشد!" else "No valid configs found!"
            }
            return false
        }

        val tcpSampleCount = minOf(newGuids.size, MAX_TCP_CANDIDATES)
        ui {
            scanProgress = 0.25f
            statusMessage = if (appLang == AppLang.FA) "تست اولیه $tcpSampleCount سرور..." else "TCP probing $tcpSampleCount servers..."
        }
        val tcpOk = tcpFilter(newGuids.shuffled().take(MAX_TCP_CANDIDATES))
        plog("Reachable (TCP): ${tcpOk.size}")
        if (tcpOk.isEmpty()) {
            MmkvManager.removeServers(newGuids, "")
            ui {
                statusMessage = if (appLang == AppLang.FA) "هیچ سروری در دسترس نبود." else "No reachable servers found."
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
            statusMessage = if (appLang == AppLang.FA) "تست واقعی سرورها (0/${keep.size})..." else "Real ping test (0/${keep.size})..."
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
                statusMessage = if (appLang == AppLang.FA) "تست واقعی سرورها ($d/${keep.size})..." else "Real ping test ($d/${keep.size})..."
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
                statusMessage = if (appLang == AppLang.FA) "هیچ سرور سالمی در تست واقعی پیدا نشد." else "No healthy servers passed verification."
            }
            return false
        }

        ui {
            scanProgress = 0.93f
            statusMessage = if (appLang == AppLang.FA) "در حال ساخت لیست بهترین‌ها..." else "Building best servers list..."
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
                statusMessage = if (appLang == AppLang.FA) "در حال اتصال به ${s.label}..." else "Connecting to ${s.label}..."
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
