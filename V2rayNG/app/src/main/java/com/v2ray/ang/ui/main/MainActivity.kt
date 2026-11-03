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
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
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
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// ═════════════════════════ مدل‌ها ═════════════════════════

private enum class Phase { IDLE, SCANNING, CONNECTING }

private enum class UiMode { IDLE, SCANNING, CONNECTING, CONNECTED }

private enum class Tier(val title: String, val color: Color, val icon: String) {
    ULTRA("Ultra", Color(0xFFE040FB), "💎"),
    TURBO("Turbo", Color(0xFFFFB300), "⚡"),
    VIP("VIP", Color(0xFF40C4FF), "👑"),
    STANDARD("Standard", Color(0xFF69F0AE), "🚀"),
    BASIC("Basic", Color(0xFF90A4AE), "🌐")
}

/** ستاره از روی پینگ واقعی هسته (میلی‌ثانیه) */
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

private fun flagOf(code: String): String =
    if (code.length == 2) {
        code.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
    } else "🌐"

private data class PennyServer(
    val guid: String,
    val cc: String,
    val ms: Long,
    val label: String
) {
    val stars: Int get() = starsFor(ms)
    val tier: Tier get() = tierFor(stars)
    val flag: String get() = flagOf(cc)
}

private fun assignLabels(list: List<PennyServer>): List<PennyServer> {
    val counts = HashMap<Tier, Int>()
    return list.map { s ->
        val n = (counts[s.tier] ?: 0) + 1
        counts[s.tier] = n
        s.copy(label = if (n == 1) "${s.tier.title} Server" else "${s.tier.title} Server $n")
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

// ═════════════════════════ کامپوننت‌های گرافیکی ═════════════════════════

@Composable
private fun StarsRow(stars: Int, size: TextUnit = 16.sp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) {
            Text(
                text = "★",
                color = if (i <= stars) Color(0xFFFFC107) else Color(0xFF475569),
                fontSize = size
            )
        }
    }
}

@Composable
private fun StatCell(icon: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$icon $value", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = Color(0xFF94A3B8), fontSize = 11.sp)
    }
}

@Composable
private fun NeonBrandHeader(mode: UiMode) {
    val glowTarget = when (mode) {
        UiMode.IDLE -> Color(0xFF38BDF8)
        UiMode.SCANNING -> Color(0xFFFFAB00)
        UiMode.CONNECTING -> Color(0xFFB388FF)
        UiMode.CONNECTED -> Color(0xFF00E676)
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

    Column {
        Text(
            text = "PENNY VPN",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp,
            style = TextStyle(
                shadow = Shadow(
                    color = neonColor,
                    offset = Offset(0f, 0f),
                    blurRadius = blurRadius
                )
            )
        )
        Text(
            text = "انتخابگر هوشمند سریع‌ترین سرور",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun PowerButton(mode: UiMode, progress: Float, onClick: () -> Unit) {
    val target = when (mode) {
        UiMode.IDLE -> Color(0xFF2979FF)
        UiMode.SCANNING -> Color(0xFFFFA000)
        UiMode.CONNECTING -> Color(0xFF7C4DFF)
        UiMode.CONNECTED -> Color(0xFF00E676)
    }
    val color by animateColorAsState(target, tween(700), label = "pbColor")
    val animProgress by animateFloatAsState(progress, tween(400), label = "pbProgress")
    val glow by animateFloatAsState(
        if (mode == UiMode.CONNECTED) 1f else 0f, tween(800), label = "pbGlow"
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

    Box(modifier = Modifier.size(270.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension * 0.33f
            when (mode) {
                UiMode.CONNECTED, UiMode.CONNECTING -> {
                    for (k in 0..2) {
                        val t = (pulse + k / 3f) % 1f
                        drawCircle(
                            color = color.copy(alpha = (1f - t) * 0.30f),
                            radius = r + 8.dp.toPx() + t * size.minDimension * 0.15f,
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
                .size(180.dp)
                .scale(if (mode == UiMode.IDLE) breathe else 1f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.70f)))
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(targetState = mode, label = "pbText") { m ->
                val fg = if (m == UiMode.CONNECTED) Color.Black else Color.White
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = when (m) {
                            UiMode.IDLE -> "CONNECT"
                            UiMode.SCANNING -> "SCANNING"
                            UiMode.CONNECTING -> "CONNECTING"
                            UiMode.CONNECTED -> "CONNECTED"
                        },
                        color = fg, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when (m) {
                            UiMode.IDLE -> "اتصال هوشمند"
                            UiMode.SCANNING -> "ضربه = لغو"
                            UiMode.CONNECTING -> "ضربه = لغو"
                            UiMode.CONNECTED -> "قطع اتصال"
                        },
                        color = fg.copy(alpha = 0.8f), fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// ═════════════════════════ Activity ═════════════════════════

class MainActivity : HelperBaseComponentActivity() {

    companion object {
        private const val TAG = "PennyVPN"

        private const val TCP_TIMEOUT_MS = 2500
        private const val MAX_TCP_CANDIDATES = 300
        private const val TCP_PARALLEL = 40

        private const val REAL_TEST_COUNT = 60          // تعداد سرور برای تست واقعی هسته
        private const val REAL_TEST_TIMEOUT_MS = 45_000L
        private const val BEST_LIST_SIZE = 15
        private const val MAX_CONNECT_ATTEMPTS = 6

        private const val STALE_MS = 6 * 60 * 60 * 1000L // لیست قدیمی‌تر از ۶ ساعت
        private const val MONITOR_INTERVAL_MS = 40_000L

        // فقط https (http ساده در اندروید جدید مسدود است)
        private val TEST_URLS = listOf(
            "https://www.gstatic.com/generate_204",
            "https://cp.cloudflare.com/generate_204"
        )
    }

    private val SUBSCRIPTION_SOURCES = listOf(
        "https://gist.githubusercontent.com/o77ss0120-max/5e592995c7b0f82b924cec572c0422a9/raw/penny_configs.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Sub7.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Sub8.txt"
    )

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, MainRepository(application as AngApplication))
    }

    /** پورت SOCKS واقعی هسته از تنظیمات خود برنامه */
    private val socksPort: Int get() = SettingsManager.getSocksPort()

    // ── وضعیت UI ──
    private var phase by mutableStateOf(Phase.IDLE)
    private var statusMessage by mutableStateOf("آماده")
    private var scanProgress by mutableStateOf(0f)
    private var lastScanTime by mutableStateOf(0L)
    private var connectStartMs by mutableStateOf(0L)
    private var lastConnectedGuid by mutableStateOf<String?>(null)
    private var autoSwitch by mutableStateOf(true)

    private var showMenu by mutableStateOf(false)
    private var showLog by mutableStateOf(false)
    private var showBest by mutableStateOf(false)

    private val logLines = mutableStateListOf<String>()
    private val bestList = mutableStateListOf<PennyServer>()

    private val failedGuids = mutableSetOf<String>()
    private val countryCache = ConcurrentHashMap<String, String>()
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    private var workJob: Job? = null

    private val prefs by lazy { getSharedPreferences("penny_prefs", Context.MODE_PRIVATE) }

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                startSmartConnect()
            } else {
                statusMessage = "مجوز VPN داده نشد!"
                plog("مجوز VPN داده نشد")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel.onAction(MainAction.Initialize)
        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
        loadSaved()
        startMonitor()
    }

    // ═════════════════════════ ذخیره / بازیابی ═════════════════════════

    private fun loadSaved() {
        autoSwitch = prefs.getBoolean("auto_switch", true)
        lastScanTime = prefs.getLong("scan_time", 0L)
        connectStartMs = prefs.getLong("connect_time", 0L)
        lastConnectedGuid = prefs.getString("last_guid", null)
        val json = prefs.getString("list", null) ?: return
        try {
            val arr = JSONArray(json)
            val out = mutableListOf<PennyServer>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val g = o.getString("g")
                if (MmkvManager.decodeServerConfig(g) != null) {
                    out.add(PennyServer(g, o.optString("c"), o.optLong("m"), ""))
                }
            }
            bestList.clear()
            bestList.addAll(assignLabels(out))
            plog("لیست ذخیره‌شده بارگذاری شد: ${out.size} سرور")
        } catch (e: Exception) {
            plog("خطا در بارگذاری لیست: ${e.message}")
        }
    }

    private fun saveList(list: List<PennyServer>, scanTime: Long) {
        try {
            val arr = JSONArray()
            list.forEach { arr.put(JSONObject().put("g", it.guid).put("c", it.cc).put("m", it.ms)) }
            prefs.edit().putString("list", arr.toString()).putLong("scan_time", scanTime).apply()
        } catch (e: Exception) {
            plog("خطا در ذخیره لیست: ${e.message}")
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
        statusMessage = "لاگ کپی شد"
    }

    private suspend fun ui(block: () -> Unit) = withContext(Dispatchers.Main) { block() }

    // ═════════════════════════ UI ═════════════════════════

    @Composable
    override fun ScreenContent() {
        BackHandler { moveTaskToBack(false) }
        val uiState by mainViewModel.uiState.collectAsState()
        val isRunning = uiState.isRunning
        val busy = phase != Phase.IDLE

        val mode = when {
            phase == Phase.SCANNING -> UiMode.SCANNING
            phase == Phase.CONNECTING -> UiMode.CONNECTING
            isRunning -> UiMode.CONNECTED
            else -> UiMode.IDLE
        }

        // ── آمار زنده (با پشتیبانی از TrafficStats + Fallback هسته) ──
        var downSpeed by remember { mutableStateOf(0L) }
        var upSpeed by remember { mutableStateOf(0L) }
        var totalDown by remember { mutableStateOf(0L) }
        var totalUp by remember { mutableStateOf(0L) }
        var elapsedSec by remember { mutableStateOf(0L) }

        LaunchedEffect(isRunning) {
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
            while (true) {
                delay(1000)
                val now = SystemClock.elapsedRealtime()
                val dt = (now - lastT).coerceAtLeast(1L)
                if (uidSupported) {
                    val rx = TrafficStats.getUidRxBytes(uid)
                    val tx = TrafficStats.getUidTxBytes(uid)
                    downSpeed = ((rx - lastRx) * 1000L / dt).coerceAtLeast(0L)
                    upSpeed = ((tx - lastTx) * 1000L / dt).coerceAtLeast(0L)
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
                    downSpeed = (d * 1000L / dt).coerceAtLeast(0L)
                    upSpeed = (u * 1000L / dt).coerceAtLeast(0L)
                    totalDown += d
                    totalUp += u
                }
                lastT = now
                elapsedSec = (System.currentTimeMillis() - connectStartMs) / 1000L
            }
        }

        val current: PennyServer? =
            (if (isRunning) bestList.firstOrNull { it.guid == uiState.selectedGuid } else null)
                ?: bestList.firstOrNull { it.guid == lastConnectedGuid }
                ?: bestList.firstOrNull()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF020617)))
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 28.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── هدر نئونی درخشان + منو ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NeonBrandHeader(mode = mode)
                    Box {
                        TextButton(onClick = { showMenu = true }) {
                            Text("⋮", color = Color.White, fontSize = 28.sp)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("بهترین کانفیگ‌ها") },
                                onClick = { showMenu = false; showBest = true }
                            )
                            DropdownMenuItem(
                                text = { Text(if (autoSwitch) "✓ تعویض خودکار هنگام قطعی" else "تعویض خودکار هنگام قطعی") },
                                onClick = {
                                    showMenu = false
                                    autoSwitch = !autoSwitch
                                    prefs.edit().putBoolean("auto_switch", autoSwitch).apply()
                                    plog("تعویض خودکار: $autoSwitch")
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

                Spacer(Modifier.height(8.dp))

                // ── دکمه اصلی انیمیشنی ──
                PowerButton(mode = mode, progress = scanProgress, onClick = { onPowerClick() })

                // ── پیام وضعیت ──
                Crossfade(
                    targetState = if (mode == UiMode.CONNECTED) "متصل ✅" else statusMessage,
                    label = "status"
                ) { msg ->
                    Text(
                        text = msg,
                        color = if (mode == UiMode.CONNECTED) Color(0xFF00E676) else Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }

                // ── آمار زنده ──
                AnimatedVisibility(
                    visible = mode == UiMode.CONNECTED,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        color = Color(0xFF111C33),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                StatCell(
                                    "⬇", fmtSpeed(downSpeed), "دانلود",
                                    Modifier.weight(1f)
                                )
                                StatCell(
                                    "⬆", fmtSpeed(upSpeed), "آپلود",
                                    Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                StatCell("⏱", fmtDuration(elapsedSec), "مدت اتصال", Modifier.weight(1f))
                                StatCell(
                                    "📊",
                                    fmtBytes(totalDown + totalUp),
                                    "حجم مصرفی",
                                    Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ── کارت سرور (ستاره‌ها دقیقاً جلوی اسم سرور در یک ردیف، نه زیر آن) ──
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = !busy && bestList.isNotEmpty()) {
                            showBest = true
                        }
                ) {
                    if (current == null) {
                        Text(
                            "هنوز سروری اسکن نشده؛ دکمه اسکن یا اتصال را بزنید.",
                            color = Color(0xFF94A3B8), fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(current.flag, fontSize = 30.sp)
                            Spacer(Modifier.width(12.dp))
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (mode == UiMode.CONNECTED) current.label else "پیشنهاد: ${current.label}",
                                    color = current.tier.color,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                StarsRow(current.stars, 16.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(current.tier.icon, fontSize = 24.sp)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ── نوار پیشرفت اسکن ──
                AnimatedVisibility(visible = phase == Phase.SCANNING) {
                    val p by animateFloatAsState(scanProgress, tween(400), label = "bar")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
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

                // ── دکمه‌های جدا: اسکن / سرور بعدی ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { startScanOnly() },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("🔍 اسکن سرورها", fontSize = 13.sp) }
                    Button(
                        onClick = { switchNext() },
                        enabled = !busy && isRunning && bestList.size > 1,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) { Text("⏭ سرور بعدی", fontSize = 13.sp, color = Color.White) }
                }

                Spacer(Modifier.height(10.dp))
                val scanInfo = if (lastScanTime > 0L) {
                    val min = (System.currentTimeMillis() - lastScanTime) / 60000L
                    val ago = if (min < 1) "چند لحظه پیش" else if (min < 60) "$min دقیقه پیش" else "${min / 60} ساعت پیش"
                    "آخرین اسکن: $ago  •  ${bestList.size} سرور آماده"
                } else "هنوز اسکنی انجام نشده"
                Text(scanInfo, color = Color(0xFF64748B), fontSize = 12.sp)
            }
        }

        if (showBest) BestConfigsDialog(uiState.selectedGuid, isRunning)
        if (showLog) LogDialog()
    }

    @Composable
    private fun BestConfigsDialog(selectedGuid: String?, isRunning: Boolean) {
        AlertDialog(
            onDismissRequest = { showBest = false },
            confirmButton = { TextButton(onClick = { showBest = false }) { Text("بستن") } },
            title = { Text("بهترین کانفیگ‌ها") },
            text = {
                if (bestList.isEmpty()) {
                    Text("هنوز لیستی ساخته نشده. ابتدا اسکن کنید.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(bestList.toList()) { s ->
                            val active = isRunning && s.guid == selectedGuid
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = phase == Phase.IDLE) {
                                        showBest = false
                                        connectToEntry(s)
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(s.flag, fontSize = 24.sp)
                                Spacer(Modifier.width(10.dp))
                                // ستاره‌ها دقیقاً جلوی اسم سرور قرار گرفته‌اند، نه زیر آن
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = s.label,
                                        color = s.tier.color,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    StarsRow(s.stars, 14.sp)
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(s.tier.icon, fontSize = 18.sp)
                                if (active) {
                                    Spacer(Modifier.width(6.dp))
                                    Text("✅", fontSize = 18.sp)
                                }
                            }
                            HorizontalDivider()
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
        LauncherManager.stopService(this)
        statusMessage = "اتصال قطع شد"
        connectStartMs = 0L
        prefs.edit().remove("connect_time").apply()
        plog("اتصال توسط کاربر قطع شد")
    }

    private fun cancelWork() {
        workJob?.cancel()
        MessageHelper.sendMsg2TestService(
            this, TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_CANCEL)
        )
        phase = Phase.IDLE
        scanProgress = 0f
        statusMessage = "لغو شد"
        plog("عملیات توسط کاربر لغو شد")
    }

    /** دکمه اتصال: اگر لیست خالی/قدیمی است اول اسکن، بعد وصل */
    private fun startSmartConnect() {
        if (phase != Phase.IDLE) return
        phase = Phase.CONNECTING
        statusMessage = "در حال آماده‌سازی..."
        failedGuids.clear()
        if (Build.VERSION.SDK_INT >= 37) {
            checkAndRequestPermission(PermissionType.ACCESS_LOCAL_NETWORK) {}
        }

        workJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                var scanned = false
                val stale = System.currentTimeMillis() - lastScanTime > STALE_MS
                if (bestList.isEmpty() || stale) {
                    scanned = runScan()
                    if (!scanned && bestList.isEmpty()) return@launch
                }
                var result = connectFromList()
                if (result == null && !scanned) {
                    plog("همه‌ی سرورهای لیست ناموفق بودند؛ اسکن مجدد")
                    failedGuids.clear()
                    if (runScan()) result = connectFromList()
                }
                ui {
                    statusMessage =
                        if (result != null) "متصل شد" else "هیچ سرور سالمی پیدا نشد. لاگ برنامه را ببینید."
                }
                if (result == null) LauncherManager.stopService(this@MainActivity)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                plog("خطای کلی: ${e.javaClass.simpleName}: ${e.message}")
                ui { statusMessage = "خطا: ${e.localizedMessage ?: "مشکل در ارتباط"}" }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    phase = Phase.IDLE
                    scanProgress = 0f
                }
            }
        }
    }

    /** دکمه‌ی جدای اسکن: فقط لیست را می‌سازد، وصل نمی‌شود */
    private fun startScanOnly() {
        if (phase != Phase.IDLE) return
        phase = Phase.SCANNING
        workJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ok = runScan()
                ui {
                    statusMessage =
                        if (ok) "اسکن تمام شد: ${bestList.size} سرور آماده" else statusMessage
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                plog("خطا در اسکن: ${e.javaClass.simpleName}: ${e.message}")
                ui { statusMessage = "خطا: ${e.localizedMessage ?: "مشکل در ارتباط"}" }
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
        statusMessage = "در حال جستجوی سرور بعدی..."
        val current = mainViewModel.uiState.value.selectedGuid
        workJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (current != null) failedGuids.add(current)
                val res = connectFromList(startAfter = current)
                if (res == null && current != null) {
                    plog("سرور بعدی پیدا نشد؛ بازگشت به سرور قبلی")
                    failedGuids.remove(current)
                    connectAndVerify(current)
                }
                ui { statusMessage = if (res != null) "سرور جدید وصل شد" else "سرور سالم دیگری پیدا نشد." }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                plog("خطا در سرور بعدی: ${e.javaClass.simpleName}: ${e.message}")
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
            statusMessage = "ابتدا یک بار روی دکمه اتصال بزنید تا مجوز VPN داده شود."
            return
        }
        phase = Phase.CONNECTING
        statusMessage = "اتصال به ${entry.label}..."
        workJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ping = connectAndVerify(entry.guid)
                if (ping > 0) {
                    markConnected(entry.guid, ping)
                    ui { statusMessage = "متصل شد" }
                } else {
                    ui { statusMessage = "این سرور کار نکرد؛ سرور دیگری انتخاب کنید." }
                    plog("❌ ${entry.label} پاسخ نداد")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                plog("خطا: ${e.message}")
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
                if (!autoSwitch || phase != Phase.IDLE || !mainViewModel.uiState.value.isRunning) continue
                try {
                    healthCheckAndSwitch()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    plog("خطا در مانیتور: ${e.message}")
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

        plog("⚠ اتصال فعلی پاسخ نمی‌دهد؛ تعویض خودکار")
        val current = mainViewModel.uiState.value.selectedGuid
        if (current != null) failedGuids.add(current)
        ui { phase = Phase.CONNECTING; statusMessage = "اتصال قطع شد؛ تعویض خودکار..." }
        try {
            val r = connectFromList(startAfter = current)
            ui {
                statusMessage =
                    if (r != null) "به سرور بهتر تعویض شد" else "سرور سالمی پیدا نشد؛ اسکن کنید"
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Main) { phase = Phase.IDLE }
        }
    }

    // ═════════════════════════ اسکن ═════════════════════════

    private suspend fun runScan(): Boolean {
        ui { phase = Phase.SCANNING; scanProgress = 0.03f; statusMessage = "در حال دریافت لیست سرورها..." }
        plog("شروع اسکن")

        // دانلود موازی منابع برای افزایش چشمگیر سرعت اسکن
        val contents = coroutineScope {
            SUBSCRIPTION_SOURCES.map { url ->
                async(Dispatchers.IO) { fetchWithFallback(url) }
            }.awaitAll().filter { it.isNotEmpty() }
        }
        if (contents.isEmpty()) {
            plog("هیچ منبعی دانلود نشد")
            ui { statusMessage = "خطا در دانلود سرورها! اینترنت یا فیلترشکن را بررسی کنید." }
            return false
        }

        ui { scanProgress = 0.15f; statusMessage = "در حال پردازش کانفیگ‌ها..." }
        val before = MmkvManager.decodeServerList("").toSet()
        for (content in contents) {
            try {
                val r = AngConfigManager.importBatchConfig(content, "", true)
                plog("import نتیجه: $r")
            } catch (e: Exception) {
                plog("import خطا: ${e.message}")
            }
        }
        val newGuids = MmkvManager.decodeServerList("").filter { it !in before }
        plog("کانفیگ جدید: ${newGuids.size}")
        if (newGuids.isEmpty()) {
            ui { statusMessage = "هیچ کانفیگ معتبری یافت نشد!" }
            return false
        }

        // مرحله ۱: فیلتر سریع TCP
        ui { scanProgress = 0.25f; statusMessage = "تست اولیه ${minOf(newGuids.size, MAX_TCP_CANDIDATES)} سرور..." }
        val tcpOk = tcpFilter(newGuids.shuffled().take(MAX_TCP_CANDIDATES))
        plog("قابل دسترس (TCP): ${tcpOk.size}")
        if (tcpOk.isEmpty()) {
            MmkvManager.removeServers(newGuids, "")
            ui { statusMessage = "هیچ سروری در دسترس نبود." }
            return false
        }

        val keep = tcpOk.take(REAL_TEST_COUNT).map { it.first }
        val keepSet = keep.toHashSet()
        val oldBest = bestList.map { it.guid }.toHashSet()
        val selected = mainViewModel.uiState.value.selectedGuid
        val protect = HashSet<String>().apply {
            addAll(keepSet); addAll(oldBest); selected?.let { add(it) }
        }
        // حذف بقیه‌ی کانفیگ‌ها (لیست سبک و تست سریع می‌ماند)
        val toRemove = MmkvManager.decodeServerList("").filter { it !in protect }
        MmkvManager.removeServers(toRemove, "")

        // مرحله ۲: تست واقعی با هسته‌ی خود برنامه (بدون وصل شدن، موازی)
        MmkvManager.clearAllTestDelayResults(keep)
        ui { scanProgress = 0.35f; statusMessage = "تست واقعی سرورها (0/${keep.size})..." }
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
                statusMessage = "تست واقعی سرورها ($d/${keep.size})..."
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
        plog("سالم در تست واقعی: ${valid.size} از ${keep.size}")

        if (valid.isEmpty()) {
            MmkvManager.removeServers(keep.filter { it != selected }, "")
            ui { statusMessage = "هیچ سرور سالمی در تست واقعی پیدا نشد." }
            return false
        }

        // ساخت لیست نهایی
        ui { scanProgress = 0.93f; statusMessage = "در حال ساخت لیست بهترین‌ها..." }
        val top = valid.take(BEST_LIST_SIZE)
        resolveCountries(top.map { it.first })
        val entries = assignLabels(top.map { (g, d) -> PennyServer(g, resolveCountry(g), d, "") })

        // پاکسازی: هر چیزی که در لیست نهایی نیست (جز سرور انتخاب‌شده)
        val finalSet = entries.map { it.guid }.toHashSet()
        val cleanup = (keepSet + oldBest).filter { it !in finalSet && it != selected }
        MmkvManager.removeServers(cleanup, "")

        val now = System.currentTimeMillis()
        saveList(entries, now)
        ui {
            bestList.clear()
            bestList.addAll(entries)
            lastScanTime = now
            scanProgress = 1f
        }
        failedGuids.clear()
        plog("اسکن کامل شد: ${entries.size} سرور (بهترین: ${entries.first().label} ${'★'.toString().repeat(entries.first().stars)})")
        return true
    }

    // ═════════════════════════ اتصال ═════════════════════════

    private suspend fun connectFromList(startAfter: String? = null): PennyServer? {
        val snapshot = bestList.toList()
        val rotated = if (startAfter != null) {
            val startIdx = snapshot.indexOfFirst { it.guid == startAfter }
            if (startIdx >= 0) snapshot.drop(startIdx + 1) + snapshot.take(startIdx + 1) else snapshot
        } else {
            // اولویت با آخرین سرور سالم متصل‌شده (برای اتصال فوری در باز شدن بعدی اپ)
            val preferred = snapshot.firstOrNull { it.guid == lastConnectedGuid }
            if (preferred != null) listOf(preferred) + snapshot.filter { it.guid != preferred.guid }
            else snapshot
        }
        val order = rotated.filter { it.guid !in failedGuids && it.guid != startAfter }

        var attempts = 0
        for (s in order) {
            if (attempts >= MAX_CONNECT_ATTEMPTS) break
            attempts++
            ui { phase = Phase.CONNECTING; statusMessage = "در حال اتصال به ${s.label}..." }
            plog("تلاش $attempts: ${s.label}")
            val ping = connectAndVerify(s.guid)
            if (ping > 0) {
                plog("✅ وصل شد: ${s.label} (${ping}ms)")
                markConnected(s.guid, ping)
                return s
            }
            failedGuids.add(s.guid)
            plog("❌ ناموفق: ${s.label}")
        }
        return null
    }

    private suspend fun markConnected(guid: String, verifiedPingMs: Long = -1L) {
        val now = System.currentTimeMillis()
        prefs.edit().putString("last_guid", guid).putLong("connect_time", now).apply()
        ui {
            connectStartMs = now
            lastConnectedGuid = guid
            if (verifiedPingMs > 0L) {
                val idx = bestList.indexOfFirst { it.guid == guid }
                if (idx >= 0) {
                    val updated = bestList.toMutableList()
                    updated[idx] = updated[idx].copy(ms = verifiedPingMs)
                    val relabeled = assignLabels(updated.sortedBy { it.ms })
                    bestList.clear()
                    bestList.addAll(relabeled)
                    saveList(relabeled, lastScanTime)
                }
            }
        }
    }

    /** اتصال واقعی + تأیید سلامت از طریق پراکسی SOCKS محلی. خروجی: میلی‌ثانیه یا -1 */
    private suspend fun connectAndVerify(guid: String): Long {
        val wasRunning = mainViewModel.uiState.value.isRunning
        withContext(Dispatchers.Main) {
            MmkvManager.setSelectServer(guid)
            mainViewModel.updateSelectedGuid(guid)
            // restartService فقط daemon فعال را ری‌استارت می‌کند؛ برای سرویس خاموش باید startService زد
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
            plog("سرویس بالا نیامد (بعد از ${waited}ms)")
            return -1L
        }
        delay(1500)

        if (!withContext(Dispatchers.IO) { isLocalPortOpen() }) {
            plog("هشدار: پورت SOCKS $socksPort باز نیست")
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
                    plog("دانلود موفق (مسیر ${i + 1}): ${content.length} کاراکتر")
                    return content
                }
            } catch (e: Exception) {
                plog("دانلود ناموفق (مسیر ${i + 1}): ${e.message}")
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

    /** درخواست https از طریق پراکسی SOCKS محلی هسته؛ 204/200 یعنی سرور واقعاً کار می‌کند */
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
                    plog("تست پراکسی: کد پاسخ $code")
                    -1L
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            plog("تست پراکسی ناموفق: ${e.javaClass.simpleName}: ${e.message}")
            -1L
        }
    }

    // ═════════════════════════ تشخیص کشور (فقط برای پرچم) ═════════════════════════

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
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val code = json.optString("country_code")
                if (json.optBoolean("success", true) && code.length == 2) code.uppercase() else null
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** کد دو حرفی کشور؛ رشته‌ی خالی یعنی نامشخص */
    private fun resolveCountry(guid: String): String {
        countryCache[guid]?.let { return it }
        val profile = MmkvManager.decodeServerConfig(guid)
        val code = countryFromRemarks(profile?.remarks.orEmpty()) ?: countryFromGeo(profile?.server) ?: ""
        countryCache[guid] = code
        return code
    }

    private suspend fun resolveCountries(guids: List<String>) {
        val sem = Semaphore(5)
        withContext(Dispatchers.IO) {
            guids.map { g -> async { sem.withPermit { resolveCountry(g) } } }.awaitAll()
        }
    }
}
