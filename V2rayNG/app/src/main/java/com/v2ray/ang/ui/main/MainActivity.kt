package com.v2ray.ang.ui.main

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL

class MainActivity : HelperBaseComponentActivity() {

    companion object {
        private const val TAG = "PennyVPN"

        // فیلتر اولیه TCP
        private const val TCP_TIMEOUT_MS = 2500
        private const val MAX_TCP_CANDIDATES = 300
        private const val TCP_PARALLEL = 40

        // تعداد سروری که به‌ترتیب واقعاً وصل می‌شویم و تست می‌کنیم
        private const val MAX_CONNECT_ATTEMPTS = 10

        // پورت SOCKS محلی v2rayNG (پیش‌فرض 10808). اگر در تنظیمات تغییر داده‌اید اینجا هم عوض کنید.
        private const val SOCKS_PORT = 10808
        private const val TEST_URL = "http://cp.cloudflare.com/generate_204"
    }

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

    private val triedGuids = mutableSetOf<String>()

    // بعد از گرفتن مجوز VPN، جستجوی سرور شروع می‌شود
    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                fetchAndConnectBestServer()
            } else {
                statusMessage = "مجوز VPN داده نشد!"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel.onAction(MainAction.Initialize)
        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
    }

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
                                fontSize = 13.sp,
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
    }

    // ───────────────────────── اکشن اصلی ─────────────────────────

    private fun handleOneClickAction() {
        if (mainViewModel.uiState.value.isRunning) {
            LauncherManager.stopService(this)
            statusMessage = "اتصال قطع شد"
            activeServerInfo = ""
            return
        }
        // اول مجوز VPN، بعد جستجوی سرور
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
        for (mirror in mirrors) {
            try {
                val content = downloadUrl(mirror)
                if (content.isNotEmpty()) {
                    Log.d(TAG, "دانلود موفق: $mirror (${content.length} کاراکتر)")
                    return content
                }
            } catch (e: Exception) {
                Log.w(TAG, "دانلود ناموفق: $mirror -> ${e.message}")
            }
        }
        return ""
    }

    // ───────────────────────── تست سرور ─────────────────────────

    /** تست سریع TCP (فقط فیلتر اولیه؛ معیار سلامت نیست) */
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

    /**
     * تست واقعی: ترافیک HTTP از طریق پراکسی SOCKS محلی هسته (که روی سرور فعلی وصل است) عبور داده می‌شود.
     * اگر پاسخ 204/200 برگشت یعنی سرور واقعاً کار می‌کند.
     */
    private fun testThroughLocalProxy(): Long {
        return try {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", SOCKS_PORT))
            val start = System.currentTimeMillis()
            val conn = URL(TEST_URL).openConnection(proxy) as HttpURLConnection
            try {
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.useCaches = false
                val code = conn.responseCode
                if (code == 204 || code == 200) {
                    (System.currentTimeMillis() - start).coerceAtLeast(1L)
                } else -1L
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.d(TAG, "تست از طریق پراکسی ناموفق: ${e.message}")
            -1L
        }
    }

    /** به سرور وصل می‌شود، منتظر بالا آمدن هسته می‌ماند و سلامت را تست می‌کند. خروجی: پینگ یا -1 */
    private suspend fun connectAndVerify(guid: String): Long {
        withContext(Dispatchers.Main) {
            MmkvManager.setSelectServer(guid)
            mainViewModel.updateSelectedGuid(guid)
            LauncherManager.restartService(this@MainActivity)
        }
        delay(1200)

        var waited = 0
        while (!mainViewModel.uiState.value.isRunning && waited < 8000) {
            delay(300)
            waited += 300
        }
        if (!mainViewModel.uiState.value.isRunning) {
            Log.d(TAG, "سرویس بالا نیامد برای $guid")
            return -1L
        }
        delay(1500) // فرصت برای آماده شدن هسته

        repeat(2) {
            val ping = withContext(Dispatchers.IO) { testThroughLocalProxy() }
            if (ping > 0) return ping
            delay(800)
        }
        return -1L
    }

    /** به‌ترتیب کاندیداها را امتحان می‌کند تا اولین سرور سالم. خروجی: (guid, ping) یا null */
    private suspend fun connectFirstWorking(guids: List<String>): Pair<String, Long>? {
        for ((index, guid) in guids.withIndex()) {
            triedGuids.add(guid)
            val name = MmkvManager.decodeServerConfig(guid)?.remarks ?: "Server"
            withContext(Dispatchers.Main) {
                statusMessage = "تست اتصال واقعی (${index + 1}/${guids.size}): $name"
            }
            val ping = connectAndVerify(guid)
            Log.d(TAG, "تلاش ${index + 1}: $name -> ping=$ping")
            if (ping > 0) return guid to ping
        }
        return null
    }

    // ───────────────────────── دریافت و اتصال ─────────────────────────

    private fun fetchAndConnectBestServer() {
        isFetching = true
        triedGuids.clear()
        statusMessage = "در حال دریافت لیست سرورها..."

        // مجوز شبکه محلی در اندروید جدید
        if (Build.VERSION.SDK_INT >= 37) {
            checkAndRequestPermission(PermissionType.ACCESS_LOCAL_NETWORK) {}
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // ۱) دانلود هر منبع جداگانه
                val contents = SUBSCRIPTION_SOURCES.map { fetchWithFallback(it) }
                    .filter { it.isNotEmpty() }

                if (contents.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        isFetching = false
                        statusMessage = "خطا در دانلود سرورها! اینترنت یا فیلترشکن خود را بررسی کنید."
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) { statusMessage = "در حال پردازش کانفیگ‌ها..." }

                // ۲) پاک کردن لیست قدیمی فقط بعد از دانلود موفق
                MmkvManager.removeAllServer()

                for (content in contents) {
                    try {
                        val r = AngConfigManager.importBatchConfig(content, "", true)
                        Log.d(TAG, "importBatchConfig نتیجه: $r")
                    } catch (e: Exception) {
                        Log.e(TAG, "import خطا: ${e.message}", e)
                    }
                }

                val allGuids = MmkvManager.decodeServerList("")
                Log.d(TAG, "تعداد سرورها: ${allGuids.size}")

                if (allGuids.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        isFetching = false
                        statusMessage = "هیچ کانفیگ معتبری یافت نشد!"
                    }
                    return@launch
                }

                // ۳) فیلتر اولیه TCP
                withContext(Dispatchers.Main) {
                    statusMessage = "تست اولیه ${minOf(allGuids.size, MAX_TCP_CANDIDATES)} سرور..."
                }
                val tcpOk = tcpFilter(allGuids.shuffled().take(MAX_TCP_CANDIDATES))
                Log.d(TAG, "TCP موفق: ${tcpOk.size}")

                val candidates = if (tcpOk.isNotEmpty()) {
                    tcpOk.take(MAX_CONNECT_ATTEMPTS).map { it.first }
                } else {
                    allGuids.shuffled().take(MAX_CONNECT_ATTEMPTS)
                }

                // ۴) اتصال واقعی و تأیید، به‌ترتیب سرعت
                val working = connectFirstWorking(candidates)

                withContext(Dispatchers.Main) {
                    isFetching = false
                    if (working != null) {
                        val (guid, ping) = working
                        val profile = MmkvManager.decodeServerConfig(guid)
                        mainViewModel.onAction(MainAction.RefreshGroups)
                        activeServerInfo =
                            "سرور منتخب: ${profile?.remarks ?: "Fast Server"} (پینگ واقعی: ${ping}ms)"
                        statusMessage = "متصل شد"
                    } else {
                        LauncherManager.stopService(this@MainActivity)
                        statusMessage = "هیچ سرور سالمی پیدا نشد. دوباره تلاش کنید."
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchAndConnectBestServer خطا", e)
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

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val allGuids = MmkvManager.decodeServerList("")
                val current = mainViewModel.uiState.value.selectedGuid
                if (!current.isNullOrEmpty()) triedGuids.add(current)

                var pool = allGuids.filter { it !in triedGuids }
                if (pool.isEmpty()) {
                    triedGuids.clear()
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

                withContext(Dispatchers.Main) {
                    isFetching = false
                    if (working != null) {
                        val (guid, ping) = working
                        val profile = MmkvManager.decodeServerConfig(guid)
                        activeServerInfo =
                            "سرور جدید: ${profile?.remarks ?: "Fast Server"} (پینگ واقعی: ${ping}ms)"
                        statusMessage = "متصل شد"
                    } else {
                        statusMessage = "سرور سالم دیگری پیدا نشد."
                        // برگرد به سرور قبلی
                        if (!current.isNullOrEmpty()) {
                            MmkvManager.setSelectServer(current)
                            mainViewModel.updateSelectedGuid(current)
                            LauncherManager.restartService(this@MainActivity)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "switchNextBestServer خطا", e)
                withContext(Dispatchers.Main) {
                    isFetching = false
                    statusMessage = "خطا: ${e.localizedMessage ?: "مشکل در ارتباط"}"
                }
            }
        }
    }
}
