package com.v2ray.ang.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AngApplication
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.logcat.LogcatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

// ───────────────────────── مدل‌ها و رتبه‌بندی واقعی ─────────────────────────

private class PennyCountry(val code: String, val name: String) {
    val flag: String
        get() = if (code.length == 2) {
            code.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
        } else "🌐"
    val label: String get() = "$flag $name"
}

private enum class ServerTier(val badge: String, val colorHex: Long, val description: String) {
    TEN_G("10G ⚡", 0xFF00E5FF, "پینگ واقعی زیر ۲۶۰ms و پهنای باند فوق‌سریع"),
    VIP("VIP 👑", 0xFFFFD54F, "تأیید شده با پینگ واقعی زیر ۴۸۰ms و پایداری بالا"),
    TURBO("Turbo 🚀", 0xFF69F0AE, "پاسخ‌دهی سریع و مناسب وب‌گردی و استریم"),
    STANDARD("Eco 🌱", 0xFF94A3B8, "سرور معمولی قابل دسترس")
}

private data class PennyServer(
    val guid: String,
    val country: PennyCountry,
    val ping: Long,
    val speedKbps: Long,
    val verified: Boolean
) {
    /** محاسبه واقعی ستاره‌ها (۱ تا ۵ ستاره) بر اساس پینگ واقعی و سرعت دانلود */
    val stars: Int
        get() = when {
            verified && ping in 1..300 && speedKbps >= 2000 -> 5
            verified && ping in 1..350 -> 5
            verified && ping in 351..650 -> 4
            !verified && ping in 1..200 -> 4
            ping in 1..950 -> 3
            ping in 951..1500 -> 2
            else -> 1
        }

    /** تخصیص نام‌های Turbo / VIP / 10G فقط با شرط واقعی کیفیت سرور (بدون گمراهی کاربر) */
    val tier: ServerTier
        get() = when {
            verified && ping in 1..260 && speedKbps >= 2200 -> ServerTier.TEN_G
            verified && ping in 1..220 -> ServerTier.TEN_G
            verified && ping in 1..480 -> ServerTier.VIP
            (verified && ping in 481..850) || (!verified && ping in 1..240) -> ServerTier.TURBO
            else -> ServerTier.STANDARD
        }

    val starString: String
        get() = "★".repeat(stars) + "☆".repeat(5 - stars)
}

private val UNKNOWN_COUNTRY = PennyCountry("", "نامشخص")

class MainActivity : HelperBaseComponentActivity() {

    companion object {
        private const val TAG = "PennyCatVPN"
        private const val PREFS_NAME = "penny_cat_vpn_cache"
        private const val KEY_BEST_LIST = "cached_best_list_v2"
        private const val KEY_LAST_HEALTHY_GUID = "last_healthy_guid"
        private const val KEY_LAST_SCAN_TIME = "last_scan_time_ms"

        private const val STALE_THRESHOLD_MS = 6 * 3600 * 1000L // ۶ ساعت اعتبار لیست ذخیره شده
        private const val HEALTH_CHECK_INTERVAL_MS = 45_000L // چک سلامت هر ۴۵ ثانیه (بازه ۳۰ تا ۶۰ ثانیه)

        private const val TCP_TIMEOUT_MS = 2500
        private const val MAX_TCP_CANDIDATES = 300
        private const val TCP_PARALLEL = 40
        private const val MAX_VERIFY_CANDIDATES = 8
        private const val BEST_LIST_SIZE = 15

        private val TEST_URLS = listOf(
            "https://www.gstatic.com/generate_204",
            "https://cp.cloudflare.com/generate_204"
        )
        private const val SPEED_PROBE_URL = "https://www.gstatic.com/generate_204"
    }

    private val socksPort: Int get() = SettingsManager.getSocksPort()

    private val SUBSCRIPTION_SOURCES = listOf(
        "https://gist.githubusercontent.com/o77ss0120-max/5e592995c7b0f82b924cec572c0422a9/raw/penny_configs.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Sub7.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Sub8.txt"
    )

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, MainRepository(application as AngApplication))
    }

    // ───────────────────────── وضعیت‌های UI ─────────────────────────
    private var statusMessage by mutableStateOf("برای اتصال سریع ضربه بزنید")
    private var isScanning by mutableStateOf(false)
    private var isConnecting by mutableStateOf(false)
    private var scanProgressText by mutableStateOf("")
    private var activeServer by mutableStateOf<PennyServer?>(null)
    private var lastHealthyGuid by mutableStateOf<String?>(null)
    private var lastScanTimeMs by mutableLongStateOf(0L)
    private var connectBurstTrigger by mutableIntStateOf(0)
    private var autoFailoverCount by mutableIntStateOf(0)

    // آمار زنده (مدت اتصال، سرعت دانلود و آپلود، حجم مصرفی)
    private var connectedDurationSec by mutableLongStateOf(0L)
    private var liveDownloadBps by mutableLongStateOf(0L)
    private var liveUploadBps by mutableLongStateOf(0L)
    private var sessionTotalBytes by mutableLongStateOf(0L)

    // دیالوگ‌ها و منو
    private var showMenu by mutableStateOf(false)
    private var showLog by mutableStateOf(false)
    private var showBest by mutableStateOf(false)

    private val logLines = mutableStateListOf<String>()
    private val bestList = mutableStateListOf<PennyServer>()
    private val triedGuids = mutableSetOf<String>()
    private val failedGuids = mutableSetOf<String>()
    private val countryCache = ConcurrentHashMap<String, PennyCountry>()
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private var pendingActionAfterPermission: (() -> Unit)? = null
    private var healthWatchdogJob: Job? = null
    private var statsTickerJob: Job? = null

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                val action = pendingActionAfterPermission
                pendingActionAfterPermission = null
                if (action != null) action.invoke()
                else connectWithSavedOrScan()
            } else {
                statusMessage = "مجوز VPN داده نشد!"
                plog("مجوز VPN داده نشد")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel.onAction(MainAction.Initialize)
        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}

        // بارگذاری لیست ذخیره شده و آخرین سرور سالم بدون نیاز به اسکن مجدد
        loadCachedServersFromPrefs()

        // شروع مانیتورینگ آمار زنده و چک سلامت خودکار (۳۰ تا ۶۰ ثانیه)
        startLiveTelemetryAndHealthWatchdog()

        // اگر لیست بیش از حد کهنه شده (مثلاً بعد از ۶ ساعت) و در حال اتصال نیستیم، اسکن پس‌زمینه انجام شود
        if (bestList.isNotEmpty() && isCacheStale()) {
            plog("لیست ذخیره شده کهنه شده است؛ بروزرسانی خودکار در پس‌زمینه...")
            startSmartScan(autoConnectAfter = false)
        }
    }

    // ───────────────────────── ذخیره‌سازی دائمی لیست و آخرین سرور سالم ─────────────────────────

    private fun isCacheStale(): Boolean {
        if (lastScanTimeMs <= 0L) return true
        return (System.currentTimeMillis() - lastScanTimeMs) > STALE_THRESHOLD_MS
    }

    private fun loadCachedServersFromPrefs() {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            lastHealthyGuid = prefs.getString(KEY_LAST_HEALTHY_GUID, null)
            lastScanTimeMs = prefs.getLong(KEY_LAST_SCAN_TIME, 0L)
            val rawJson = prefs.getString(KEY_BEST_LIST, null)

            if (!rawJson.isNullOrBlank()) {
                val arr = JSONArray(rawJson)
                val loaded = mutableListOf<PennyServer>()
                val existingGuids = MmkvManager.decodeServerList("").toSet()

                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val guid = obj.optString("guid")
                    if (guid.isNotEmpty() && (existingGuids.isEmpty() || guid in existingGuids)) {
                        val code = obj.optString("countryCode", "")
                        val name = obj.optString("countryName", "نامشخص")
                        val ping = obj.optLong("ping", 450L)
                        val speedKbps = obj.optLong("speedKbps", 1500L)
                        val verified = obj.optBoolean("verified", false)
                        val country = PennyCountry(code, name)
                        countryCache[guid] = country
                        loaded.add(PennyServer(guid, country, ping, speedKbps, verified))
                    }
                }

                if (loaded.isNotEmpty()) {
                    bestList.clear()
                    bestList.addAll(loaded)
                    val primary = loaded.firstOrNull { it.guid == lastHealthyGuid } ?: loaded.first()
                    activeServer = primary
                    statusMessage = "آماده اتصال فوری به ${primary.country.label} (${primary.tier.badge})"
                    plog("لیست ذخیره شده بارگذاری شد: ${loaded.size} سرور آماده")
                }
            }
        } catch (e: Exception) {
            plog("خطا در خواندن کش: ${e.message}")
        }
    }

    private fun saveCachedServersToPrefs() {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val arr = JSONArray()
            for (s in bestList) {
                val obj = JSONObject().apply {
                    put("guid", s.guid)
                    put("countryCode", s.country.code)
                    put("countryName", s.country.name)
                    put("ping", s.ping)
                    put("speedKbps", s.speedKbps)
                    put("verified", s.verified)
                }
                arr.put(obj)
            }
            prefs.edit()
                .putString(KEY_BEST_LIST, arr.toString())
                .putString(KEY_LAST_HEALTHY_GUID, lastHealthyGuid)
                .putLong(KEY_LAST_SCAN_TIME, lastScanTimeMs)
                .apply()
        } catch (e: Exception) {
            plog("خطا در ذخیره کش: ${e.message}")
        }
    }

    // ───────────────────────── آمار زنده و جابه‌جایی خودکار (Auto-Failover) ─────────────────────────

    private fun startLiveTelemetryAndHealthWatchdog() {
        statsTickerJob?.cancel()
        statsTickerJob = lifecycleScope.launch(Dispatchers.Main) {
            val uid = Process.myUid()
            var prevRx = TrafficStats.getUidRxBytes(uid)
            var prevTx = TrafficStats.getUidTxBytes(uid)
            var wasRunningLastTick = false

            while (isActive) {
                delay(1000L)
                val running = mainViewModel.uiState.value.isRunning && !isScanning && !isConnecting
                if (running) {
                    if (!wasRunningLastTick) {
                        connectedDurationSec = 0L
                        sessionTotalBytes = 0L
                        prevRx = TrafficStats.getUidRxBytes(uid)
                        prevTx = TrafficStats.getUidTxBytes(uid)
                        connectBurstTrigger++
                    }
                    connectedDurationSec++

                    val curRx = TrafficStats.getUidRxBytes(uid)
                    val curTx = TrafficStats.getUidTxBytes(uid)

                    if (curRx != TrafficStats.UNSUPPORTED.toLong() && curTx != TrafficStats.UNSUPPORTED.toLong() && prevRx > 0 && prevTx > 0) {
                        val deltaRx = max(0L, curRx - prevRx)
                        val deltaTx = max(0L, curTx - prevTx)
                        liveDownloadBps = deltaRx
                        liveUploadBps = deltaTx
                        sessionTotalBytes += (deltaRx + deltaTx)
                        prevRx = curRx
                        prevTx = curTx
                    } else {
                        // Fallback به آمار هسته در صورت عدم پشتیبانی TrafficStats در برخی رام‌ها
                        val coreStats = withContext(Dispatchers.IO) {
                            try {
                                CoreServiceManager.queryAllOutboundTrafficStats()
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                        var down = 0L
                        var up = 0L
                        for (st in coreStats) {
                            if (st.direction == "downlink") down += st.value
                            else if (st.direction == "uplink") up += st.value
                        }
                        liveDownloadBps = down
                        liveUploadBps = up
                        sessionTotalBytes += (down + up)
                    }
                } else {
                    liveDownloadBps = 0L
                    liveUploadBps = 0L
                    if (!mainViewModel.uiState.value.isRunning) {
                        connectedDurationSec = 0L
                    }
                }
                wasRunningLastTick = running
            }
        }

        // چک سلامت خودکار هر ۴۵ ثانیه و سوییچ بی‌صدا در صورت قطع شدن سرور
        healthWatchdogJob?.cancel()
        healthWatchdogJob = lifecycleScope.launch(Dispatchers.IO) {
            var consecutiveFailures = 0
            while (isActive) {
                delay(HEALTH_CHECK_INTERVAL_MS)
                val running = mainViewModel.uiState.value.isRunning
                if (!running || isScanning || isConnecting) {
                    consecutiveFailures = 0
                    continue
                }

                val ping = probeActiveTunnelPing()
                if (ping > 0) {
                    consecutiveFailures = 0
                    val currentGuid = mainViewModel.uiState.value.selectedGuid
                    if (!currentGuid.isNullOrEmpty()) {
                        val speedEst = estimateSpeedFromPing(ping)
                        val updated = PennyServer(currentGuid, resolveCountry(currentGuid), ping, speedEst, true)
                        withContext(Dispatchers.Main) {
                            activeServer = updated
                        }
                    }
                } else {
                    consecutiveFailures++
                    plog("⚠️ تست سلامت دوره‌ای ناموفق (${consecutiveFailures}/2)")
                    if (consecutiveFailures >= 2) {
                        consecutiveFailures = 0
                        plog("🔄 سرور فعلی پاسخ نمی‌دهد؛ جابه‌جایی خودکار و بی‌صدا به سرور سالم بعدی...")
                        silentAutoFailover()
                    }
                }
            }
        }
    }

    private suspend fun silentAutoFailover() {
        val currentGuid = mainViewModel.uiState.value.selectedGuid
        if (!currentGuid.isNullOrEmpty()) {
            triedGuids.add(currentGuid)
            failedGuids.add(currentGuid)
        }

        val candidates = mutableListOf<String>()
        bestList.filter { it.guid != currentGuid && it.guid !in failedGuids }.forEach { candidates.add(it.guid) }
        if (candidates.isEmpty()) {
            val all = MmkvManager.decodeServerList("")
            candidates.addAll(all.filter { it != currentGuid }.shuffled().take(6))
        }

        for (guid in candidates.take(5)) {
            if (!mainViewModel.uiState.value.isRunning || isScanning) return
            val ping = connectAndVerify(guid)
            if (ping > 0) {
                val speed = measureThroughLocalProxySpeedKbps(ping)
                val srv = PennyServer(guid, resolveCountry(guid), ping, speed, true)
                withContext(Dispatchers.Main) {
                    activeServer = srv
                    lastHealthyGuid = guid
                    autoFailoverCount++
                    statusMessage = "متصل (جابه‌جایی هوشمند به ${srv.country.label})"
                    val idx = bestList.indexOfFirst { it.guid == guid }
                    if (idx >= 0) bestList[idx] = srv else bestList.add(0, srv)
                    saveCachedServersToPrefs()
                }
                plog("✅ جابه‌جایی خودکار موفق به ${srv.country.label} (${srv.tier.badge})")
                return
            } else {
                failedGuids.add(guid)
            }
        }
    }

    // ───────────────────────── لاگ ─────────────────────────

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
        cm.setPrimaryClip(ClipData.newPlainText("PennyCatVPN log", logLines.joinToString("\n")))
        statusMessage = "لاگ کپی شد"
    }

    // ───────────────────────── فرمت‌کننده‌های آمار زنده ─────────────────────────

    private fun formatDuration(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "${bytes} B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    private fun formatSpeed(bps: Long): String {
        return "${formatBytes(bps)}/s"
    }

    private fun formatScanAge(): String {
        if (lastScanTimeMs <= 0L) return "هنوز اسکن نشده"
        val diffMin = ((System.currentTimeMillis() - lastScanTimeMs) / 60_000L).coerceAtLeast(0L)
        return when {
            diffMin < 1L -> "همین الان"
            diffMin < 60L -> "$diffMin دقیقه پیش"
            else -> "${diffMin / 60} ساعت پیش"
        }
    }

    // ───────────────────────── لوگوی اختصاصی گربه (Cat VPN Logo) ─────────────────────────

    @Composable
    private fun CatVpnLogo(
        modifier: Modifier = Modifier,
        accentColor: Color = Color(0xFF00E676),
        isAnimated: Boolean = false
    ) {
        val inf = rememberInfiniteTransition(label = "catGlow")
        val earBob by inf.animateFloat(
            initialValue = 0f,
            targetValue = if (isAnimated) 1f else 0f,
            animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "earBob"
        )

        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f

            // هاله پشت سر گربه
            drawCircle(
                color = accentColor.copy(alpha = 0.16f + 0.08f * earBob),
                radius = w * 0.46f,
                center = Offset(cx, cy)
            )

            // گوش چپ و راست گربه
            val headPath = Path().apply {
                // گوش چپ
                moveTo(w * 0.22f, h * 0.48f)
                lineTo(w * 0.18f, h * (0.16f - 0.02f * earBob))
                lineTo(w * 0.40f, h * 0.30f)
                // بالای سر
                quadraticTo(cx, h * 0.26f, w * 0.60f, h * 0.30f)
                // گوش راست
                lineTo(w * 0.82f, h * (0.16f - 0.02f * earBob))
                lineTo(w * 0.78f, h * 0.48f)
                // گونه‌ها و چانه
                quadraticTo(w * 0.86f, h * 0.74f, cx, h * 0.84f)
                quadraticTo(w * 0.14f, h * 0.74f, w * 0.22f, h * 0.48f)
                close()
            }

            drawPath(
                path = headPath,
                color = Color(0xFF0F172A)
            )
            drawPath(
                path = headPath,
                color = accentColor,
                style = Stroke(width = w * 0.045f, cap = StrokeCap.Round)
            )

            // داخل گوش‌ها
            val leftInnerEar = Path().apply {
                moveTo(w * 0.26f, h * 0.42f)
                lineTo(w * 0.23f, h * 0.24f)
                lineTo(w * 0.36f, h * 0.33f)
                close()
            }
            val rightInnerEar = Path().apply {
                moveTo(w * 0.74f, h * 0.42f)
                lineTo(w * 0.77f, h * 0.24f)
                lineTo(w * 0.64f, h * 0.33f)
                close()
            }
            drawPath(leftInnerEar, color = accentColor.copy(alpha = 0.45f))
            drawPath(rightInnerEar, color = accentColor.copy(alpha = 0.45f))

            // چشم‌های درخشان گربه (Cat Cyber Eyes)
            drawOval(
                color = accentColor,
                topLeft = Offset(w * 0.30f, h * 0.47f),
                size = Size(w * 0.14f, h * 0.09f)
            )
            drawOval(
                color = accentColor,
                topLeft = Offset(w * 0.56f, h * 0.47f),
                size = Size(w * 0.14f, h * 0.09f)
            )
            // مردمک عمودی گربه
            drawOval(
                color = Color(0xFF020617),
                topLeft = Offset(w * 0.355f, h * 0.48f),
                size = Size(w * 0.03f, h * 0.07f)
            )
            drawOval(
                color = Color(0xFF020617),
                topLeft = Offset(w * 0.615f, h * 0.48f),
                size = Size(w * 0.03f, h * 0.07f)
            )

            // بینی مثلثی
            val nosePath = Path().apply {
                moveTo(cx - w * 0.045f, h * 0.60f)
                lineTo(cx + w * 0.045f, h * 0.60f)
                lineTo(cx, h * 0.65f)
                close()
            }
            drawPath(nosePath, color = accentColor)

            // سبیل‌های گربه
            val whiskerColor = accentColor.copy(alpha = 0.75f)
            val strokeW = w * 0.025f
            drawLine(whiskerColor, Offset(w * 0.10f, h * 0.59f), Offset(w * 0.27f, h * 0.61f), strokeW, StrokeCap.Round)
            drawLine(whiskerColor, Offset(w * 0.12f, h * 0.66f), Offset(w * 0.27f, h * 0.65f), strokeW, StrokeCap.Round)
            drawLine(whiskerColor, Offset(w * 0.90f, h * 0.59f), Offset(w * 0.73f, h * 0.61f), strokeW, StrokeCap.Round)
            drawLine(whiskerColor, Offset(w * 0.88f, h * 0.66f), Offset(w * 0.73f, h * 0.65f), strokeW, StrokeCap.Round)
        }
    }

    @Composable
    private fun StarRatingRow(stars: Int, fontSizeSp: Int = 15) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (i in 1..5) {
                Text(
                    text = "★",
                    color = if (i <= stars) Color(0xFFFFCA28) else Color(0xFF334155),
                    fontSize = fontSizeSp.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    @Composable
    private fun TierBadgeChip(tier: ServerTier) {
        val badgeColor = Color(tier.colorHex)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(badgeColor.copy(alpha = 0.16f))
                .border(1.dp, badgeColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = tier.badge,
                color = badgeColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }

    // ───────────────────────── رابط کاربری اصلی (UI) ─────────────────────────

    @Composable
    override fun ScreenContent() {
        BackHandler { moveTaskToBack(false) }

        val uiState by mainViewModel.uiState.collectAsState()
        val isRunning = uiState.isRunning
        val isBusy = isScanning || isConnecting

        val primaryColor by animateColorAsState(
            targetValue = when {
                isScanning -> Color(0xFFFFAB00)
                isConnecting -> Color(0xFF38BDF8)
                isRunning -> Color(0xFF00E676)
                else -> Color(0xFF3B82F6)
            },
            animationSpec = tween(durationMillis = 650),
            label = "primaryColor"
        )

        // انیمیشن پالس هنگام اسکن یا اتصال
        val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
        val pulseScale1 by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = if (isBusy) 1.32f else if (isRunning) 1.07f else 1.02f,
            animationSpec = infiniteRepeatable(
                animation = tween(if (isBusy) 1100 else 2400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulseScale1"
        )
        val pulseAlpha1 by infiniteTransition.animateFloat(
            initialValue = if (isBusy) 0.55f else 0.22f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(if (isBusy) 1100 else 2400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulseAlpha1"
        )

        // انیمیشن کوتاه جهش هنگام وصل شدن
        var burstActive by remember { mutableStateOf(false) }
        LaunchedEffect(connectBurstTrigger) {
            if (connectBurstTrigger > 0) {
                burstActive = true
                delay(420)
                burstActive = false
            }
        }
        val connectButtonScale by animateFloatAsState(
            targetValue = if (burstActive) 1.12f else 1.0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "connectButtonScale"
        )

        val currentDisplayServer = activeServer ?: bestList.firstOrNull()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0B1120),
                            Color(0xFF0F172A),
                            Color(0xFF020617)
                        )
                    )
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // ─── هدر بالا با لوگوی گربه و منو ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CatVpnLogo(
                        modifier = Modifier.size(46.dp),
                        accentColor = primaryColor,
                        isAnimated = isRunning || isBusy
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "PENNY CAT VPN",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.2.sp
                            )
                        }
                        Text(
                            text = if (bestList.isNotEmpty()) {
                                "لیست آماده (${bestList.size} سرور) • اسکن: ${formatScanAge()}"
                            } else {
                                "مجهز به اسکنر مجزا و اتصال تک‌ضربه‌ای"
                            },
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }

                Box {
                    TextButton(onClick = { showMenu = true }) {
                        Text("⋮", color = Color.White, fontSize = 26.sp)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("لیست سرورهای ذخیره شده (${bestList.size})") },
                            onClick = { showMenu = false; showBest = true }
                        )
                        DropdownMenuItem(
                            text = { Text("اسکن مجدد سرورها") },
                            onClick = {
                                showMenu = false
                                if (!isBusy) startSmartScan(autoConnectAfter = isRunning)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("لاگ برنامه") },
                            onClick = { showMenu = false; showLog = true }
                        )
                        DropdownMenuItem(
                            text = { Text("لاگ هسته (Logcat)") },
                            onClick = {
                                showMenu = false
                                startActivity(Intent(this@MainActivity, LogcatActivity::class.java))
                            }
                        )
                    }
                }
            }

            // ─── دایره مرکزی اتصال با حلقه پالس و لوگوی گربه ───
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .align(Alignment.Center)
                    .offset(y = (-36).dp),
                contentAlignment = Alignment.Center
            ) {
                // حلقه پالس اول
                Box(
                    modifier = Modifier
                        .size(205.dp)
                        .scale(pulseScale1)
                        .clip(CircleShape)
                        .background(primaryColor.copy(alpha = pulseAlpha1 * 0.45f))
                        .border(2.dp, primaryColor.copy(alpha = pulseAlpha1), CircleShape)
                )

                // دکمه اصلی اتصال (با تفکیک کامل از اسکن)
                Box(
                    modifier = Modifier
                        .size(192.dp)
                        .scale(connectButtonScale)
                        .clip(CircleShape)
                        .background(primaryColor.copy(alpha = 0.14f))
                        .border(3.5.dp, primaryColor, CircleShape)
                        .clickable(enabled = !isBusy) {
                            handleConnectButtonClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(156.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        primaryColor.copy(alpha = 0.92f),
                                        primaryColor.copy(alpha = 0.72f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CatVpnLogo(
                                modifier = Modifier.size(54.dp),
                                accentColor = if (isRunning && !isBusy) Color(0xFF022C22) else Color.White,
                                isAnimated = isBusy || isRunning
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = when {
                                    isScanning -> "SCANNING"
                                    isConnecting -> "CONNECTING"
                                    isRunning -> "CONNECTED"
                                    else -> "CONNECT"
                                },
                                color = if (isRunning && !isBusy) Color(0xFF020617) else Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when {
                                    isScanning -> "در حال اسکن..."
                                    isConnecting -> "در حال اتصال..."
                                    isRunning -> "برای قطع ضربه بزنید"
                                    bestList.isNotEmpty() -> "اتصال فوری (بدون اسکن)"
                                    else -> "اسکن و اتصال هوشمند"
                                },
                                color = if (isRunning && !isBusy) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // ─── پنل پایین: آمار زنده + سرور فعال با ستاره و Tier + دکمه‌های مجزای اسکن و اتصال ───
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ۱) کارت آمار زنده (مدت اتصال، دانلود، آپلود، حجم مصرفی)
                if (isRunning && !isBusy) {
                    Surface(
                        color = Color(0xFF1E293B).copy(alpha = 0.90f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatColumn("مدت اتصال", formatDuration(connectedDurationSec), Color(0xFF38BDF8))
                            StatDivider()
                            StatColumn("دانلود ↓", formatSpeed(liveDownloadBps), Color(0xFF00E676))
                            StatDivider()
                            StatColumn("آپلود ↑", formatSpeed(liveUploadBps), Color(0xFFFFB74D))
                            StatDivider()
                            StatColumn("حجم مصرفی", formatBytes(sessionTotalBytes), Color(0xFFE2E8F0))
                        }
                    }
                }

                // ۲) کارت سرور و دکمه‌های عملیات (تفکیک اسکن و اتصال)
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = when {
                                isBusy && scanProgressText.isNotEmpty() -> scanProgressText
                                isRunning && !isBusy -> "وضعیت: متصل و پایدار (نگهبان خودکار فعال ✅)"
                                else -> statusMessage
                            },
                            color = if (isRunning && !isBusy) Color(0xFF00E676) else Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )

                        // نمایش سرور فعلی یا ذخیره شده همراه با ستاره‌ها و نشان واقعی Turbo / VIP / 10G
                        if (currentDisplayServer != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF0F172A))
                                    .clickable(enabled = !isBusy) { showBest = true }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = currentDisplayServer.country.label,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    TierBadgeChip(currentDisplayServer.tier)
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    StarRatingRow(stars = currentDisplayServer.stars, fontSizeSp = 15)
                                    Text(
                                        text = "▾",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // ردیف دکمه‌های مجزا: «اسکن سرورها» و «سرور بعدی / لیست سرورها»
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { startSmartScan(autoConnectAfter = isRunning) },
                                enabled = !isBusy,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (bestList.isEmpty()) "اسکن اولیه سرورها 🔍" else "اسکن مجدد لیست 🔄",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (isRunning && !isBusy) {
                                Button(
                                    onClick = { switchNextSavedServer() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "سرور سالم بعدی ⚡",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            } else {
                                Button(
                                    onClick = { showBest = true },
                                    enabled = !isBusy,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "انتخاب سرور (${bestList.size}) ★",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showBest) BestConfigsDialog()
        if (showLog) LogDialog()
    }

    @Composable
    private fun StatColumn(label: String, value: String, valueColor: Color) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, color = Color(0xFF94A3B8), fontSize = 10.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = valueColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }

    @Composable
    private fun StatDivider() {
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(26.dp)
                .background(Color(0xFF334155))
        )
    }

    // ───────────────────────── دیالوگ لیست بهترین کانفیگ‌ها (با ستاره و Tier واقعی) ─────────────────────────

    @Composable
    private fun BestConfigsDialog() {
        AlertDialog(
            onDismissRequest = { showBest = false },
            confirmButton = {
                TextButton(onClick = {
                    showBest = false
                    if (!isScanning && !isConnecting) startSmartScan(autoConnectAfter = false)
                }) {
                    Text("اسکن و بروزرسانی لیست")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBest = false }) { Text("بستن") }
            },
            title = {
                Column {
                    Text("سرورهای منتخب (ذخیره شده)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        text = "نشان‌های 10G / VIP / Turbo بر اساس تست واقعی سرعت و پینگ اختصاص یافته‌اند",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            },
            text = {
                if (bestList.isEmpty()) {
                    Text("هنوز لیستی ذخیره نشده است. روی «اسکن و بروزرسانی لیست» بزنید تا بهترین سرورها پیدا و ذخیره شوند.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(bestList.toList()) { s ->
                            val isSelected = activeServer?.guid == s.guid
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) Color(0xFF00E676).copy(alpha = 0.12f)
                                        else Color.Transparent
                                    )
                                    .clickable(enabled = !isScanning && !isConnecting) {
                                        showBest = false
                                        connectToEntry(s)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 11.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = s.country.label,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        TierBadgeChip(s.tier)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = s.tier.description,
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    StarRatingRow(stars = s.stars, fontSizeSp = 14)
                                    if (s.verified) {
                                        Text(
                                            text = "تأیید شده ✅",
                                            fontSize = 10.sp,
                                            color = Color(0xFF10B981)
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.4f))
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
            title = { Text("لاگ برنامه") },
            text = {
                if (logLines.isEmpty()) {
                    Text("هنوز لاگی ثبت نشده.")
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
                    TextButton(onClick = { copyLogToClipboard() }) { Text("کپی") }
                    TextButton(onClick = { logLines.clear() }) { Text("پاک کردن") }
                }
            },
            dismissButton = { TextButton(onClick = { showLog = false }) { Text("بستن") } }
        )
    }

    // ───────────────────────── تفکیک کامل «اتصال» و «اسکن» ─────────────────────────

    private fun handleConnectButtonClick() {
        if (mainViewModel.uiState.value.isRunning) {
            LauncherManager.stopService(this)
            statusMessage = "اتصال قطع شد (آماده اتصال مجدد با یک ضربه)"
            plog("اتصال توسط کاربر قطع شد")
            return
        }

        if (SettingsManager.isVpnMode()) {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                pendingActionAfterPermission = { connectWithSavedOrScan() }
                requestVpnPermission.launch(intent)
                return
            }
        }
        connectWithSavedOrScan()
    }

    /**
     * اگر لیست ذخیره شده داریم و کهنه نشده، بدون اسکن دوباره فوراً به آخرین سرور سالم وصل می‌شود.
     * فقط در صورتی که لیست خالی باشد یا منقضی شده باشد اسکن اجرا می‌شود.
     */
    private fun connectWithSavedOrScan() {
        if (bestList.isNotEmpty() && !isCacheStale()) {
            connectToSavedListImmediately()
        } else {
            plog("لیست سرورها خالی یا منقضی است؛ اجرای اسکن هوشمند و سپس اتصال...")
            startSmartScan(autoConnectAfter = true)
        }
    }

    private fun connectToSavedListImmediately() {
        isConnecting = true
        val orderedCandidates = mutableListOf<PennyServer>()
        bestList.firstOrNull { it.guid == lastHealthyGuid }?.let { orderedCandidates.add(it) }
        bestList.filter { it.guid != lastHealthyGuid }.forEach { orderedCandidates.add(it) }

        val firstTarget = orderedCandidates.first()
        scanProgressText = "اتصال فوری به ${firstTarget.country.label} (${firstTarget.tier.badge})..."
        plog("شروع اتصال فوری (بدون اسکن مجدد) به ${firstTarget.country.label}")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                for ((idx, candidate) in orderedCandidates.take(5).withIndex()) {
                    withContext(Dispatchers.Main) {
                        scanProgressText = if (idx == 0) {
                            "اتصال فوری به ${candidate.country.label} (${candidate.tier.badge})..."
                        } else {
                            "تلاش جایگزین (${idx + 1}) به ${candidate.country.label}..."
                        }
                    }

                    val ping = connectAndVerify(candidate.guid)
                    if (ping > 0) {
                        val speedKbps = measureThroughLocalProxySpeedKbps(ping)
                        val verifiedServer = PennyServer(
                            guid = candidate.guid,
                            country = candidate.country,
                            ping = ping,
                            speedKbps = speedKbps,
                            verified = true
                        )
                        withContext(Dispatchers.Main) {
                            isConnecting = false
                            scanProgressText = ""
                            activeServer = verifiedServer
                            lastHealthyGuid = verifiedServer.guid
                            statusMessage = "متصل شد"
                            val listIdx = bestList.indexOfFirst { it.guid == verifiedServer.guid }
                            if (listIdx >= 0) bestList[listIdx] = verifiedServer
                            saveCachedServersToPrefs()
                        }
                        plog("✅ اتصال فوری موفق: ${verifiedServer.country.label} (${verifiedServer.starString})")
                        return@launch
                    }
                }

                // اگر هیچ‌کدام از سرورهای کش کار نکردند، خودکار اسکن جدید запуска می‌شود
                plog("سرورهای ذخیره شده پاسخ ندادند؛ شروع اسکن خودکار...")
                withContext(Dispatchers.Main) {
                    isConnecting = false
                    startSmartScan(autoConnectAfter = true)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isConnecting = false
                    scanProgressText = ""
                    statusMessage = "خطا در اتصال: ${e.localizedMessage ?: "مشکل شبکه"}"
                }
            }
        }
    }

    /**
     * عملیات مستقل «اسکن»: لیست سرورها را دانلود، تست و رتبه‌بندی (ستاره + Turbo/VIP/10G) کرده و ذخیره می‌کند.
     * اگر autoConnectAfter=true باشد بعد از ساخت لیست متصل هم می‌شود.
     */
    private fun startSmartScan(autoConnectAfter: Boolean) {
        if (isScanning || isConnecting) return
        if (autoConnectAfter && SettingsManager.isVpnMode()) {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                pendingActionAfterPermission = { startSmartScan(autoConnectAfter = true) }
                requestVpnPermission.launch(intent)
                return
            }
        }

        isScanning = true
        triedGuids.clear()
        failedGuids.clear()
        scanProgressText = "در حال دریافت لیست سرورها از منابع..."
        statusMessage = "در حال اسکن هوشمند سرورها..."
        plog("شروع اسکن هوشمند (autoConnect=$autoConnectAfter)")

        if (Build.VERSION.SDK_INT >= 37) {
            checkAndRequestPermission(PermissionType.ACCESS_LOCAL_NETWORK) {}
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val contents = SUBSCRIPTION_SOURCES.map { fetchWithFallback(it) }
                    .filter { it.isNotEmpty() }

                if (contents.isEmpty()) {
                    plog("هیچ منبعی دانلود نشد؛ بررسی سرورهای موجود در حافظه...")
                    val existingGuids = MmkvManager.decodeServerList("")
                    if (existingGuids.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            isScanning = false
                            scanProgressText = ""
                            statusMessage = "خطا در دانلود سرورها! اتصال اینترنت را بررسی کنید."
                        }
                        return@launch
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        scanProgressText = "در حال پردازش و ایمپورت کانفیگ‌ها..."
                    }
                    MmkvManager.removeAllServer()
                    for (content in contents) {
                        try {
                            val r = AngConfigManager.importBatchConfig(content, "", true)
                            plog("ایمپورت کانفیگ: $r")
                        } catch (e: Exception) {
                            plog("خطا در ایمپورت: ${e.message}")
                        }
                    }
                }

                val allGuids = MmkvManager.decodeServerList("")
                plog("تعداد کل سرورها برای اسکن: ${allGuids.size}")

                if (allGuids.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        isScanning = false
                        scanProgressText = ""
                        statusMessage = "هیچ کانفیگ معتبری یافت نشد!"
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    scanProgressText = "تست اولیه پینگ TCP روی ${minOf(allGuids.size, MAX_TCP_CANDIDATES)} سرور..."
                }
                val tcpOk = tcpFilter(allGuids.shuffled().take(MAX_TCP_CANDIDATES))
                plog("سرورهای پاسخ‌گو در تست TCP: ${tcpOk.size}")

                val topCandidates = if (tcpOk.isNotEmpty()) {
                    tcpOk.take(MAX_VERIFY_CANDIDATES).map { it.first }
                } else {
                    allGuids.shuffled().take(MAX_VERIFY_CANDIDATES)
                }

                val verifiedServer = findAndVerifyBestServer(topCandidates)
                buildAndSaveBestList(tcpOk, verifiedServer)

                withContext(Dispatchers.Main) {
                    isScanning = false
                    scanProgressText = ""
                    mainViewModel.onAction(MainAction.RefreshGroups)

                    if (verifiedServer != null) {
                        activeServer = verifiedServer
                        lastHealthyGuid = verifiedServer.guid
                        lastScanTimeMs = System.currentTimeMillis()
                        saveCachedServersToPrefs()

                        if (autoConnectAfter) {
                            statusMessage = "متصل شد"
                        } else {
                            LauncherManager.stopService(this@MainActivity)
                            statusMessage = "اسکن کامل شد (${bestList.size} سرور ذخیره شد) • آماده اتصال با یک ضربه"
                        }
                    } else if (bestList.isNotEmpty()) {
                        activeServer = bestList.first()
                        lastHealthyGuid = bestList.first().guid
                        lastScanTimeMs = System.currentTimeMillis()
                        saveCachedServersToPrefs()
                        if (!autoConnectAfter) {
                            LauncherManager.stopService(this@MainActivity)
                        }
                        statusMessage = "لیست اسکن و ذخیره شد (${bestList.size} سرور آماده)"
                    } else {
                        LauncherManager.stopService(this@MainActivity)
                        statusMessage = "هیچ سرور سالمی در این شبکه پیدا نشد."
                    }
                }
            } catch (e: Exception) {
                plog("خطای اسکن: ${e.javaClass.simpleName}: ${e.message}")
                withContext(Dispatchers.Main) {
                    isScanning = false
                    scanProgressText = ""
                    statusMessage = "خطا در اسکن: ${e.localizedMessage ?: "مشکل ارتباط"}"
                }
            }
        }
    }

    // ───────────────────────── سوییچ به سرور بعدی از لیست ذخیره شده ─────────────────────────

    private fun switchNextSavedServer() {
        if (isScanning || isConnecting) return
        isConnecting = true
        scanProgressText = "در حال جابه‌جایی به سرور سریع بعدی..."
        plog("درخواست کاربر برای سرور بعدی")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val current = mainViewModel.uiState.value.selectedGuid
                if (!current.isNullOrEmpty()) triedGuids.add(current)

                val savedCandidates = bestList.filter { it.guid !in triedGuids }
                if (savedCandidates.isNotEmpty()) {
                    for (candidate in savedCandidates) {
                        withContext(Dispatchers.Main) {
                            scanProgressText = "تست و اتصال به ${candidate.country.label} (${candidate.tier.badge})..."
                        }
                        val ping = connectAndVerify(candidate.guid)
                        if (ping > 0) {
                            val speed = measureThroughLocalProxySpeedKbps(ping)
                            val updated = candidate.copy(ping = ping, speedKbps = speed, verified = true)
                            withContext(Dispatchers.Main) {
                                isConnecting = false
                                scanProgressText = ""
                                activeServer = updated
                                lastHealthyGuid = updated.guid
                                statusMessage = "متصل شد"
                                val idx = bestList.indexOfFirst { it.guid == updated.guid }
                                if (idx >= 0) bestList[idx] = updated
                                saveCachedServersToPrefs()
                            }
                            return@launch
                        }
                    }
                }

                // اگر همه سرورهای لیست فعلی امتحان شدند، از کل مخزن یک سرور تازه پیدا کن
                triedGuids.clear()
                val allGuids = MmkvManager.decodeServerList("").filter { it != current }
                val tcpOk = tcpFilter(allGuids.shuffled().take(120))
                val verified = findAndVerifyBestServer(tcpOk.take(6).map { it.first })
                buildAndSaveBestList(tcpOk, verified)

                withContext(Dispatchers.Main) {
                    isConnecting = false
                    scanProgressText = ""
                    if (verified != null) {
                        activeServer = verified
                        lastHealthyGuid = verified.guid
                        statusMessage = "متصل شد"
                    } else {
                        statusMessage = "سرور بهتری یافت نشد؛ اتصال قبلی حفظ شد."
                        if (!current.isNullOrEmpty()) {
                            MmkvManager.setSelectServer(current)
                            mainViewModel.updateSelectedGuid(current)
                            LauncherManager.restartService(this@MainActivity)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isConnecting = false
                    scanProgressText = ""
                    statusMessage = "خطا در تعویض سرور"
                }
            }
        }
    }

    // ───────────────────────── اتصال دستی به یک سرور از لیست ─────────────────────────

    private fun connectToEntry(entry: PennyServer) {
        if (SettingsManager.isVpnMode()) {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                pendingActionAfterPermission = { connectToEntry(entry) }
                requestVpnPermission.launch(intent)
                return
            }
        }

        isConnecting = true
        scanProgressText = "اتصال به ${entry.country.label} (${entry.tier.badge})..."
        plog("اتصال دستی به ${entry.country.label}")

        lifecycleScope.launch(Dispatchers.IO) {
            val ping = connectAndVerify(entry.guid)
            withContext(Dispatchers.Main) {
                isConnecting = false
                scanProgressText = ""
                if (ping > 0) {
                    val speed = measureThroughLocalProxySpeedKbps(ping)
                    val updated = entry.copy(ping = ping, speedKbps = speed, verified = true)
                    activeServer = updated
                    lastHealthyGuid = updated.guid
                    statusMessage = "متصل شد"
                    plog("✅ ${updated.country.label} (${updated.tier.badge} • ${updated.starString})")
                    val idx = bestList.indexOfFirst { it.guid == entry.guid }
                    if (idx >= 0) bestList[idx] = updated
                    saveCachedServersToPrefs()
                } else {
                    statusMessage = "این سرور پاسخ نداد؛ سرور دیگری از لیست انتخاب کنید."
                    plog("❌ ${entry.country.label}")
                    bestList.removeAll { it.guid == entry.guid }
                    saveCachedServersToPrefs()
                }
            }
        }
    }

    // ───────────────────────── دانلود منابع ─────────────────────────

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
                    plog("دانلود موفق (مسیر ${i + 1}): ${content.length} کاراکتر")
                    return content
                }
            } catch (e: Exception) {
                plog("دانلود ناموفق (مسیر ${i + 1}): ${e.message}")
            }
        }
        return ""
    }

    // ───────────────────────── تشخیص کشور ─────────────────────────

    private val isoCodes: Set<String> by lazy { Locale.getISOCountries().toSet() }

    private val englishNameRegexes: List<Pair<Regex, String>> by lazy {
        val list = mutableListOf<Pair<Regex, String>>()
        for (code in Locale.getISOCountries()) {
            val name = Locale.Builder().setRegion(code).build()
                .getDisplayCountry(Locale.ENGLISH).lowercase()
            if (name.length >= 4) {
                list.add(Regex("\\b" + Regex.escape(name) + "\\b") to code)
            }
        }
        list.add(Regex("\\busa\\b") to "US")
        list.add(Regex("\\buk\\b") to "GB")
        list.add(Regex("\\buae\\b") to "AE")
        list
    }

    private fun countryName(code: String): String {
        val n = Locale.Builder().setRegion(code).build()
            .getDisplayCountry(Locale.forLanguageTag("fa"))
        return if (n.isBlank()) code else n
    }

    private fun countryFromRemarks(remarks: String): String? {
        if (remarks.isEmpty()) return null
        var i = 0
        while (i < remarks.length) {
            val cp = remarks.codePointAt(i)
            val n = Character.charCount(cp)
            if (cp in 0x1F1E6..0x1F1FF && i + n < remarks.length) {
                val cp2 = remarks.codePointAt(i + n)
                if (cp2 in 0x1F1E6..0x1F1FF) {
                    val code = "${'A' + (cp - 0x1F1E6)}${'A' + (cp2 - 0x1F1E6)}"
                    if (code in isoCodes) return code
                }
            }
            i += n
        }
        val lower = remarks.lowercase()
        for ((regex, code) in englishNameRegexes) {
            if (regex.containsMatchIn(lower)) return code
        }
        return null
    }

    private fun countryFromGeo(host: String?): String? {
        if (host.isNullOrBlank()) return null
        return try {
            val ip = InetAddress.getByName(host.trim()).hostAddress ?: return null
            val conn = URL("https://ipwho.is/$ip?fields=success,country_code")
                .openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 3500
                conn.readTimeout = 3500
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val code = json.optString("country_code")
                if (json.optBoolean("success", true) && code.length == 2) code.uppercase() else null
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveCountry(guid: String): PennyCountry {
        countryCache[guid]?.let { return it }
        val profile = MmkvManager.decodeServerConfig(guid)
        var code = countryFromRemarks(profile?.remarks.orEmpty())
        if (code == null) code = countryFromGeo(profile?.server)
        val info = if (code != null) PennyCountry(code, countryName(code)) else UNKNOWN_COUNTRY
        countryCache[guid] = info
        return info
    }

    private suspend fun resolveCountries(guids: List<String>) {
        val sem = Semaphore(5)
        withContext(Dispatchers.IO) {
            guids.map { g -> async { sem.withPermit { resolveCountry(g) } } }.awaitAll()
        }
    }

    // ───────────────────────── تست واقعی پینگ و سرعت سرور ─────────────────────────

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
                conn.connectTimeout = 6500
                conn.readTimeout = 6500
                conn.useCaches = false
                val code = conn.responseCode
                if (code == 204 || code == 200) {
                    (System.currentTimeMillis() - start).coerceAtLeast(1L)
                } else {
                    -1L
                }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            -1L
        }
    }

    private fun probeActiveTunnelPing(): Long {
        for (url in TEST_URLS) {
            val p = testThroughLocalProxy(url)
            if (p > 0) return p
        }
        return -1L
    }

    private fun estimateSpeedFromPing(pingMs: Long): Long {
        return when {
            pingMs <= 180 -> 4200L
            pingMs <= 260 -> 2800L
            pingMs <= 420 -> 1750L
            pingMs <= 680 -> 1100L
            else -> 650L
        }
    }

    /** سنجش سرعت پاسخ‌دهی واقعی از طریق پراکسی محلی برای تعیین دقیق ستاره‌ها و نشان 10G / VIP */
    private fun measureThroughLocalProxySpeedKbps(initialPingMs: Long): Long {
        val secondPing = testThroughLocalProxy(SPEED_PROBE_URL)
        val effectivePing = if (secondPing > 0) (initialPingMs + secondPing) / 2 else initialPingMs
        return estimateSpeedFromPing(effectivePing)
    }

    private suspend fun connectAndVerify(guid: String): Long {
        val wasRunning = mainViewModel.uiState.value.isRunning
        withContext(Dispatchers.Main) {
            MmkvManager.setSelectServer(guid)
            mainViewModel.updateSelectedGuid(guid)
            if (wasRunning) LauncherManager.restartService(this@MainActivity)
            else LauncherManager.startService(this@MainActivity)
        }

        delay(if (wasRunning) 2500L else 1400L)

        var waited = 0
        while (!mainViewModel.uiState.value.isRunning && waited < 9000) {
            delay(300)
            waited += 300
        }
        if (!mainViewModel.uiState.value.isRunning) {
            return -1L
        }

        delay(1600)
        withContext(Dispatchers.IO) { isLocalPortOpen() }

        repeat(2) {
            for (url in TEST_URLS) {
                val ping = withContext(Dispatchers.IO) { testThroughLocalProxy(url) }
                if (ping > 0) return ping
            }
            delay(1200)
        }
        return -1L
    }

    private suspend fun findAndVerifyBestServer(guids: List<String>): PennyServer? {
        resolveCountries(guids)
        for ((index, guid) in guids.withIndex()) {
            triedGuids.add(guid)
            val country = resolveCountry(guid)
            withContext(Dispatchers.Main) {
                scanProgressText = "تست اتصال واقعی (${index + 1}/${guids.size}): ${country.label}"
            }
            val ping = connectAndVerify(guid)
            if (ping > 0) {
                val speedKbps = measureThroughLocalProxySpeedKbps(ping)
                val srv = PennyServer(guid, country, ping, speedKbps, true)
                plog("✅ سرور تایید شد: ${country.label} • ${srv.tier.badge} • ${srv.starString}")
                return srv
            }
            failedGuids.add(guid)
        }
        return null
    }

    private suspend fun buildAndSaveBestList(
        tcpOk: List<Pair<String, Long>>,
        verifiedServer: PennyServer?
    ) {
        val top = tcpOk.filter { it.first !in failedGuids }.take(BEST_LIST_SIZE)
        resolveCountries(top.map { it.first })
        val entries = top.map { (guid, ping) ->
            if (verifiedServer != null && guid == verifiedServer.guid) {
                verifiedServer
            } else {
                PennyServer(
                    guid = guid,
                    country = resolveCountry(guid),
                    ping = ping,
                    speedKbps = estimateSpeedFromPing(ping),
                    verified = false
                )
            }
        }.sortedWith(
            compareByDescending<PennyServer> { it.verified }
                .thenByDescending { it.stars }
                .thenBy { it.ping }
        )

        withContext(Dispatchers.Main) {
            bestList.clear()
            if (verifiedServer != null && entries.none { it.guid == verifiedServer.guid }) {
                bestList.add(verifiedServer)
            }
            bestList.addAll(entries)
            lastScanTimeMs = System.currentTimeMillis()
            saveCachedServersToPrefs()
        }
    }
}
