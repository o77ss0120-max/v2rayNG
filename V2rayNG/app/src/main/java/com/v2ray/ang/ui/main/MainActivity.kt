package com.v2ray.ang.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AngApplication
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.logcat.LogcatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
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

// ───────────────────────── مدل‌ها ─────────────────────────

private class PennyCountry(val code: String, val name: String) {
    val flag: String
        get() = if (code.length == 2) {
            code.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
        } else "🌐"
    val label: String get() = "$flag $name"
}

private data class PennyServer(
    val guid: String,
    val country: PennyCountry,
    val ping: Long,
    val verified: Boolean
)

private val UNKNOWN_COUNTRY = PennyCountry("", "نامشخص")

class MainActivity : HelperBaseComponentActivity() {

    companion object {
        private const val TAG = "PennyVPN"

        private const val TCP_TIMEOUT_MS = 2500
        private const val MAX_TCP_CANDIDATES = 300
        private const val TCP_PARALLEL = 40
        private const val MAX_CONNECT_ATTEMPTS = 10
        private const val BEST_LIST_SIZE = 15

        // مهم: فقط https (http ساده در اندروید جدید مسدود است و تست همیشه شکست می‌خورد)
        private val TEST_URLS = listOf(
            "https://www.gstatic.com/generate_204",
            "https://cp.cloudflare.com/generate_204"
        )
    }

    /** پورت SOCKS واقعی هسته از تنظیمات خود برنامه (پشتیبانی از پورت پویا) */
    private val socksPort: Int get() = SettingsManager.getSocksPort()

    private val SUBSCRIPTION_SOURCES = listOf(
        "https://gist.githubusercontent.com/o77ss0120-max/5e592995c7b0f82b924cec572c0422a9/raw/penny_configs.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Sub7.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Sub8.txt"
    )

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, MainRepository(application as AngApplication))
    }

    private var statusMessage by mutableStateOf("برای اتصال هوشمند ضربه بزنید")
    private var activeServerInfo by mutableStateOf("")
    private var isFetching by mutableStateOf(false)

    private var showMenu by mutableStateOf(false)
    private var showLog by mutableStateOf(false)
    private var showBest by mutableStateOf(false)

    private val logLines = mutableStateListOf<String>()
    private val bestList = mutableStateListOf<PennyServer>()

    private val triedGuids = mutableSetOf<String>()
    private val failedGuids = mutableSetOf<String>()
    private val countryCache = ConcurrentHashMap<String, PennyCountry>()
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                fetchAndConnectBestServer()
            } else {
                statusMessage = "مجوز VPN داده نشد!"
                plog("مجوز VPN داده نشد")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel.onAction(MainAction.Initialize)
        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
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
        cm.setPrimaryClip(ClipData.newPlainText("PennyVPN log", logLines.joinToString("\n")))
        statusMessage = "لاگ کپی شد"
    }

    // ───────────────────────── UI ─────────────────────────

    @Composable
    override fun ScreenContent() {
        BackHandler { moveTaskToBack(false) }
        val uiState by mainViewModel.uiState.collectAsState()
        val isRunning = uiState.isRunning

        val buttonColor by animateColorAsState(
            targetValue = when {
                isFetching -> Color(0xFFFFA000)
                isRunning -> Color(0xFF00E676)
                else -> Color(0xFF2979FF)
            },
            label = "buttonColor"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F172A), Color(0xFF020617))
                    )
                )
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 44.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "PENNY VPN",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "مجهز به انتخابگر هوشمند سریع‌ترین سرور",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp
                )
            }

            // منو
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                TextButton(onClick = { showMenu = true }) {
                    Text("⋮", color = Color.White, fontSize = 28.sp)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("بهترین کانفیگ‌ها") },
                        onClick = { showMenu = false; showBest = true }
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

            Box(
                modifier = Modifier
                    .size(210.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(buttonColor.copy(alpha = 0.15f))
                    .border(4.dp, buttonColor, CircleShape)
                    .clickable(enabled = !isFetching) {
                        handleOneClickAction()
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .clip(CircleShape)
                        .background(buttonColor),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = when {
                                isFetching -> "SCANNING"
                                isRunning -> "CONNECTED"
                                else -> "CONNECT"
                            },
                            color = if (isRunning && !isFetching) Color.Black else Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when {
                                isFetching -> "لطفاً صبر کنید..."
                                isRunning -> "قطع اتصال"
                                else -> "اتصال هوشمند"
                            },
                            color = if (isRunning && !isFetching) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isRunning && !isFetching) "وضعیت: متصل ✅" else statusMessage,
                            color = if (isRunning && !isFetching) Color(0xFF00E676) else Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )

                        if (activeServerInfo.isNotEmpty() && isRunning && !isFetching) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = activeServerInfo,
                                color = Color(0xFF94A3B8),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        if (isRunning && !isFetching) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { switchNextBestServer() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                                ) {
                                    Text("سرور سریع بعدی ⚡", color = Color.White, fontSize = 13.sp)
                                }
                                Button(
                                    onClick = { mainViewModel.testCurrentServerRealPing() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                ) {
                                    Text("تست پینگ", color = Color.White, fontSize = 13.sp)
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
    private fun BestConfigsDialog() {
        AlertDialog(
            onDismissRequest = { showBest = false },
            confirmButton = { TextButton(onClick = { showBest = false }) { Text("بستن") } },
            title = { Text("بهترین کانفیگ‌ها") },
            text = {
                if (bestList.isEmpty()) {
                    Text("هنوز لیستی ساخته نشده. ابتدا یک بار روی دکمه اتصال بزنید.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                        items(bestList.toList()) { s ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isFetching) {
                                        showBest = false
                                        connectToEntry(s)
                                    }
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(s.country.label, fontSize = 16.sp)
                                Text(
                                    if (s.verified) "✅ ${s.ping}ms" else "${s.ping}ms",
                                    fontSize = 14.sp
                                )
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

    // ───────────────────────── اکشن اصلی ─────────────────────────

    private fun handleOneClickAction() {
        if (mainViewModel.uiState.value.isRunning) {
            LauncherManager.stopService(this)
            statusMessage = "اتصال قطع شد"
            activeServerInfo = ""
            plog("اتصال توسط کاربر قطع شد")
            return
        }
        if (SettingsManager.isVpnMode()) {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                requestVpnPermission.launch(intent)
                return
            }
        }
        fetchAndConnectBestServer()
    }

    // ───────────────────────── دانلود ─────────────────────────

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

    // ───────────────────────── کشور ─────────────────────────

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
        // ۱) ایموجی پرچم
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
        // ۲) نام انگلیسی کشور
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
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val code = json.optString("country_code")
                if (json.optBoolean("success", true) && code.length == 2) code.uppercase() else null
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
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

    // ───────────────────────── تست سرور ─────────────────────────

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

    /** ترافیک https را از پراکسی SOCKS محلی عبور می‌دهد؛ اگر 204/200 آمد یعنی سرور واقعاً کار می‌کند */
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

    /** اتصال واقعی به سرور + تأیید سلامت. خروجی: پینگ یا -1 */
    private suspend fun connectAndVerify(guid: String): Long {
        val wasRunning = mainViewModel.uiState.value.isRunning
        withContext(Dispatchers.Main) {
            MmkvManager.setSelectServer(guid)
            mainViewModel.updateSelectedGuid(guid)
            // نکته کلیدی: اگر سرویس روشن نیست باید startService زده شود، نه restartService
            if (wasRunning) LauncherManager.restartService(this@MainActivity)
            else LauncherManager.startService(this@MainActivity)
        }
        plog(if (wasRunning) "restartService صدا زده شد" else "startService صدا زده شد")

        delay(if (wasRunning) 3000L else 1500L)
        var waited = 0
        while (!mainViewModel.uiState.value.isRunning && waited < 10000) {
            delay(300)
            waited += 300
        }
        if (!mainViewModel.uiState.value.isRunning) {
            plog("سرویس بالا نیامد (isRunning=false بعد از ${waited}ms)")
            return -1L
        }
        delay(2000) // آماده شدن هسته

        val portOpen = withContext(Dispatchers.IO) { isLocalPortOpen() }
        if (!portOpen) {
            plog("هشدار: پورت SOCKS $socksPort باز نیست (هسته بالا نیامده است)")
        }

        repeat(2) {
            for (url in TEST_URLS) {
                val ping = withContext(Dispatchers.IO) { testThroughLocalProxy(url) }
                if (ping > 0) return ping
            }
            delay(1500)
        }
        return -1L
    }

    private suspend fun connectFirstWorking(guids: List<String>): Pair<String, Long>? {
        resolveCountries(guids)
        for ((index, guid) in guids.withIndex()) {
            triedGuids.add(guid)
            val country = resolveCountry(guid)
            withContext(Dispatchers.Main) {
                statusMessage = "تست اتصال واقعی (${index + 1}/${guids.size}): ${country.label}"
            }
            plog("تلاش ${index + 1}/${guids.size}: ${country.label}")
            val ping = connectAndVerify(guid)
            if (ping > 0) {
                plog("✅ موفق: ${country.label} (${ping}ms)")
                return guid to ping
            }
            failedGuids.add(guid)
            plog("❌ ناموفق: ${country.label}")
        }
        return null
    }

    private suspend fun buildBestList(
        tcpOk: List<Pair<String, Long>>,
        verifiedGuid: String?,
        verifiedPing: Long
    ) {
        val top = tcpOk.filter { it.first !in failedGuids }.take(BEST_LIST_SIZE)
        resolveCountries(top.map { it.first })
        val entries = top.map { (guid, ping) ->
            if (guid == verifiedGuid) PennyServer(guid, resolveCountry(guid), verifiedPing, true)
            else PennyServer(guid, resolveCountry(guid), ping, false)
        }.sortedWith(compareByDescending<PennyServer> { it.verified }.thenBy { it.ping })
        withContext(Dispatchers.Main) {
            bestList.clear()
            bestList.addAll(entries)
        }
    }

    // ───────────────────────── دریافت و اتصال ─────────────────────────

    private fun fetchAndConnectBestServer() {
        isFetching = true
        triedGuids.clear()
        failedGuids.clear()
        statusMessage = "در حال دریافت لیست سرورها..."
        plog("شروع جستجو")

        if (Build.VERSION.SDK_INT >= 37) {
            checkAndRequestPermission(PermissionType.ACCESS_LOCAL_NETWORK) {}
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val contents = SUBSCRIPTION_SOURCES.map { fetchWithFallback(it) }
                    .filter { it.isNotEmpty() }

                if (contents.isEmpty()) {
                    plog("هیچ منبعی دانلود نشد")
                    withContext(Dispatchers.Main) {
                        isFetching = false
                        statusMessage = "خطا در دانلود سرورها! اینترنت یا فیلترشکن خود را بررسی کنید."
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) { statusMessage = "در حال پردازش کانفیگ‌ها..." }

                MmkvManager.removeAllServer()
                for (content in contents) {
                    try {
                        val r = AngConfigManager.importBatchConfig(content, "", true)
                        plog("import نتیجه: $r")
                    } catch (e: Exception) {
                        plog("import خطا: ${e.message}")
                    }
                }

                val allGuids = MmkvManager.decodeServerList("")
                plog("تعداد کل سرورها: ${allGuids.size}")

                if (allGuids.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        isFetching = false
                        statusMessage = "هیچ کانفیگ معتبری یافت نشد!"
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    statusMessage = "تست اولیه ${minOf(allGuids.size, MAX_TCP_CANDIDATES)} سرور..."
                }
                val tcpOk = tcpFilter(allGuids.shuffled().take(MAX_TCP_CANDIDATES))
                plog("سرورهای قابل دسترس (TCP): ${tcpOk.size}")

                val candidates = if (tcpOk.isNotEmpty()) {
                    tcpOk.take(MAX_CONNECT_ATTEMPTS).map { it.first }
                } else {
                    allGuids.shuffled().take(MAX_CONNECT_ATTEMPTS)
                }

                val working = connectFirstWorking(candidates)
                buildBestList(tcpOk, working?.first, working?.second ?: -1L)

                withContext(Dispatchers.Main) {
                    isFetching = false
                    if (working != null) {
                        val (guid, ping) = working
                        mainViewModel.onAction(MainAction.RefreshGroups)
                        activeServerInfo = "${resolveCountry(guid).label} • ${ping}ms"
                        statusMessage = "متصل شد"
                    } else {
                        LauncherManager.stopService(this@MainActivity)
                        statusMessage = "هیچ سرور سالمی پیدا نشد. لاگ برنامه را از منو ببینید."
                        plog("هیچ سرور سالمی پیدا نشد")
                    }
                }
            } catch (e: Exception) {
                plog("خطای کلی: ${e.javaClass.simpleName}: ${e.message}")
                withContext(Dispatchers.Main) {
                    isFetching = false
                    statusMessage = "خطا: ${e.localizedMessage ?: "مشکل در ارتباط"}"
                }
            }
        }
    }

    // ───────────────────────── سرور بعدی ─────────────────────────

    private fun switchNextBestServer() {
        isFetching = true
        statusMessage = "در حال جستجوی سرور پرسرعت بعدی..."
        plog("جستجوی سرور بعدی")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val allGuids = MmkvManager.decodeServerList("")
                val current = mainViewModel.uiState.value.selectedGuid
                if (!current.isNullOrEmpty()) triedGuids.add(current)

                var pool = allGuids.filter { it !in triedGuids }
                if (pool.isEmpty()) {
                    triedGuids.clear()
                    failedGuids.clear()
                    if (!current.isNullOrEmpty()) triedGuids.add(current)
                    pool = allGuids.filter { it != current }
                }

                val tcpOk = tcpFilter(pool.shuffled().take(MAX_TCP_CANDIDATES))
                val candidates = if (tcpOk.isNotEmpty()) {
                    tcpOk.take(MAX_CONNECT_ATTEMPTS).map { it.first }
                } else {
                    pool.shuffled().take(MAX_CONNECT_ATTEMPTS)
                }

                val working = connectFirstWorking(candidates)
                buildBestList(tcpOk, working?.first, working?.second ?: -1L)

                withContext(Dispatchers.Main) {
                    isFetching = false
                    if (working != null) {
                        val (guid, ping) = working
                        activeServerInfo = "${resolveCountry(guid).label} • ${ping}ms"
                        statusMessage = "متصل شد"
                    } else {
                        statusMessage = "سرور سالم دیگری پیدا نشد."
                        if (!current.isNullOrEmpty()) {
                            MmkvManager.setSelectServer(current)
                            mainViewModel.updateSelectedGuid(current)
                            LauncherManager.restartService(this@MainActivity)
                        }
                    }
                }
            } catch (e: Exception) {
                plog("خطا در سرور بعدی: ${e.javaClass.simpleName}: ${e.message}")
                withContext(Dispatchers.Main) {
                    isFetching = false
                    statusMessage = "خطا: ${e.localizedMessage ?: "مشکل در ارتباط"}"
                }
            }
        }
    }

    // ───────────────────────── اتصال به آیتم لیست «بهترین کانفیگ‌ها» ─────────────────────────

    private fun connectToEntry(entry: PennyServer) {
        if (!mainViewModel.uiState.value.isRunning && SettingsManager.isVpnMode() &&
            VpnService.prepare(this) != null
        ) {
            statusMessage = "ابتدا یک بار روی دکمه اتصال بزنید تا مجوز VPN داده شود."
            return
        }
        isFetching = true
        statusMessage = "اتصال به ${entry.country.label}..."
        plog("اتصال دستی به ${entry.country.label}")

        lifecycleScope.launch(Dispatchers.IO) {
            val ping = connectAndVerify(entry.guid)
            withContext(Dispatchers.Main) {
                isFetching = false
                if (ping > 0) {
                    activeServerInfo = "${entry.country.label} • ${ping}ms"
                    statusMessage = "متصل شد"
                    plog("✅ ${entry.country.label} (${ping}ms)")
                    val idx = bestList.indexOfFirst { it.guid == entry.guid }
                    if (idx >= 0) bestList[idx] = entry.copy(ping = ping, verified = true)
                } else {
                    statusMessage = "این سرور کار نکرد؛ سرور دیگری انتخاب کنید."
                    plog("❌ ${entry.country.label}")
                    bestList.removeAll { it.guid == entry.guid }
                }
            }
        }
    }
}
