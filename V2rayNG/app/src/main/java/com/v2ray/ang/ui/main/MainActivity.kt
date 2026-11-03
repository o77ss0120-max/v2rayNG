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
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.handler.V2rayConfigManager
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
import java.net.Socket
import java.net.URL

class MainActivity : HelperBaseComponentActivity() {

    companion object {
        private const val TAG = "PennyVPN"
        private const val TCP_TIMEOUT_MS = 2500
        private const val MAX_TCP_CANDIDATES = 300   // حداکثر سرور برای تست TCP
        private const val REAL_PING_CANDIDATES = 30  // تعداد سرور برای تست واقعی
        private const val TCP_PARALLEL = 40
        private const val REAL_PARALLEL = 5
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

    // سرورهایی که در "سرور بعدی" قبلاً امتحان شده‌اند
    private val triedGuids = mutableSetOf<String>()

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                startV2RayService()
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
                            color = if (isRunning) Color.Black else Color.White,
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
                            color = if (isRunning) Color(0xFF1E293B) else Color(0xFFE2E8F0),
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

                        if (activeServerInfo.isNotEmpty() && isRunning) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = activeServerInfo,
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        if (isRunning) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    enabled = !isFetching,
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
        } else {
            fetchAndConnectBestServer()
        }
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

    /** تست واقعی با هسته v2ray (ترافیک واقعاً از سرور رد می‌شود) */
    private fun testServerRealPing(guid: String): Long {
        return try {
            val result = V2rayConfigManager.getV2rayConfig(this, guid)
            if (!result.status) return -1L
            SpeedtestManager.realPing(result.content)
        } catch (e: Throwable) {
            Log.w(TAG, "realPing خطا برای $guid: ${e.message}")
            -1L
        }
    }

    /** مرحله ۱: TCP روی تعداد زیادی سرور؛ خروجی: سرورهای قابل دسترس به ترتیب سرعت */
    private suspend fun tcpFilter(guids: List<String>): List<Pair<String, Long>> {
        val sem = Semaphore(TCP_PARALLEL)
        return withContext(Dispatchers.IO) {
            guids.map { guid ->
                async { sem.withPermit { guid to testServerTcpPing(guid) } }
            }.awaitAll()
        }.filter { it.second > 0 }.sortedBy { it.second }
    }

    /** مرحله ۲: تست واقعی؛ خروجی: سرورهای سالم به ترتیب سرعت */
    private suspend fun realPingFilter(guids: List<String>): List<Pair<String, Long>> {
        val sem = Semaphore(REAL_PARALLEL)
        return withContext(Dispatchers.IO) {
            guids.map { guid ->
                async { sem.withPermit { guid to testServerRealPing(guid) } }
            }.awaitAll()
        }.filter { it.second > 0 }.sortedBy { it.second }
    }

    // ───────────────────────── دریافت و اتصال ─────────────────────────

    private fun fetchAndConnectBestServer() {
        isFetching = true
        triedGuids.clear()
        statusMessage = "در حال دریافت لیست سرورها..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // ۱) دانلود هر منبع به‌صورت جداگانه
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

                // ۲) فقط بعد از دانلود موفق، لیست قدیمی پاک می‌شود
                MmkvManager.removeAllServer()

                // هر منبع جداگانه و با append=true تا base64 و متن ساده قاطی نشوند
                var importedTotal = 0
                for (content in contents) {
                    try {
                        val r = AngConfigManager.importBatchConfig(content, "", true)
                        Log.d(TAG, "importBatchConfig نتیجه: $r")
                        importedTotal++
                    } catch (e: Exception) {
                        Log.e(TAG, "import خطا: ${e.message}", e)
                    }
                }

                val allGuids = MmkvManager.decodeServerList("")
                Log.d(TAG, "منابع import شده: $importedTotal | تعداد سرورها: ${allGuids.size}")

                if (allGuids.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        isFetching = false
                        statusMessage = "هیچ کانفیگ معتبری یافت نشد!"
                    }
                    return@launch
                }

                // ۳) فیلتر اولیه TCP روی تعداد زیاد
                withContext(Dispatchers.Main) {
                    statusMessage = "تست اولیه ${minOf(allGuids.size, MAX_TCP_CANDIDATES)} سرور..."
                }
                val tcpOk = tcpFilter(allGuids.shuffled().take(MAX_TCP_CANDIDATES))
                Log.d(TAG, "TCP موفق: ${tcpOk.size}")

                val realCandidates = if (tcpOk.isNotEmpty()) {
                    tcpOk.take(REAL_PING_CANDIDATES).map { it.first }
                } else {
                    allGuids.shuffled().take(REAL_PING_CANDIDATES)
                }

                // ۴) تست واقعی هسته
                withContext(Dispatchers.Main) {
                    statusMessage = "تست واقعی اتصال ${realCandidates.size} سرور..."
                }
                var realOk = realPingFilter(realCandidates)
                Log.d(TAG, "Real ping موفق: ${realOk.size} -> $realOk")

                // اگر هیچ‌کدام پاس نشد، بقیه سرورهای TCP-سالم را هم امتحان کن
                if (realOk.isEmpty() && tcpOk.size > REAL_PING_CANDIDATES) {
                    withContext(Dispatchers.Main) { statusMessage = "در حال جستجوی سرورهای بیشتر..." }
                    val more = tcpOk.drop(REAL_PING_CANDIDATES).take(60).map { it.first }
                    realOk = realPingFilter(more)
                    Log.d(TAG, "Real ping مرحله دوم: ${realOk.size}")
                }

                val selectedGuid: String
                val bestPing: Long
                val verified: Boolean
                if (realOk.isNotEmpty()) {
                    selectedGuid = realOk.first().first
                    bestPing = realOk.first().second
                    verified = true
                } else if (tcpOk.isNotEmpty()) {
                    selectedGuid = tcpOk.first().first
                    bestPing = tcpOk.first().second
                    verified = false
                } else {
                    withContext(Dispatchers.Main) {
                        isFetching = false
                        statusMessage = "هیچ سرور سالمی پیدا نشد. فیلترشکن/اینترنت را بررسی و دوباره تلاش کنید."
                    }
                    return@launch
                }

                triedGuids.add(selectedGuid)
                val selectedProfile = MmkvManager.decodeServerConfig(selectedGuid)

                withContext(Dispatchers.Main) {
                    isFetching = false
                    MmkvManager.setSelectServer(selectedGuid)
                    mainViewModel.updateSelectedGuid(selectedGuid)
                    mainViewModel.onAction(MainAction.RefreshGroups)

                    val name = selectedProfile?.remarks ?: "Fast Server"
                    activeServerInfo = if (verified) {
                        "سرور منتخب: $name (پینگ واقعی: ${bestPing}ms)"
                    } else {
                        "سرور منتخب: $name (تست واقعی انجام نشد)"
                    }
                    statusMessage = "بهترین سرور انتخاب شد. در حال اتصال..."
                    checkAndStartVpn()
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
                val current = MmkvManager.getSelectServer()
                if (current != null) triedGuids.add(current)

                var pool = allGuids.filter { it !in triedGuids }
                if (pool.isEmpty()) {          // همه امتحان شدند؛ از اول
                    triedGuids.clear()
                    current?.let { triedGuids.add(it) }
                    pool = allGuids.filter { it != current }
                }

                val tcpOk = tcpFilter(pool.shuffled().take(MAX_TCP_CANDIDATES))
                val candidates = if (tcpOk.isNotEmpty()) {
                    tcpOk.take(REAL_PING_CANDIDATES).map { it.first }
                } else {
                    pool.shuffled().take(REAL_PING_CANDIDATES)
                }
                val realOk = realPingFilter(candidates)
                Log.d(TAG, "سرور بعدی: real ok=${realOk.size}")

                val best = realOk.firstOrNull()

                withContext(Dispatchers.Main) {
                    isFetching = false
                    if (best == null) {
                        statusMessage = "سرور سالم دیگری پیدا نشد."
                        return@withContext
                    }
                    val (guid, ping) = best
                    triedGuids.add(guid)
                    val profile = MmkvManager.decodeServerConfig(guid)

                    MmkvManager.setSelectServer(guid)
                    mainViewModel.updateSelectedGuid(guid)
                    activeServerInfo =
                        "سرور جدید: ${profile?.remarks ?: "Fast Server"} (پینگ واقعی: ${ping}ms)"
                    statusMessage = "در حال اتصال به سرور جدید..."

                    // ابتدا توقف کامل، سپس استارت
                    lifecycleScope.launch {
                        LauncherManager.stopService(this@MainActivity)
                        delay(1000)
                        LauncherManager.restartService(this@MainActivity)
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

    // ───────────────────────── VPN ─────────────────────────

    private fun checkAndStartVpn() {
        if (!SettingsManager.isVpnMode()) {
            startV2RayService()
            return
        }
        val intent = VpnService.prepare(this)
        if (intent == null) {
            startV2RayService()
        } else {
            requestVpnPermission.launch(intent)
        }
    }

    private fun startV2RayService() {
        // ACCESS_LOCAL_NETWORK مربوط به اندروید جدید (API 37). اگر در SDK شما نیست، این بلوک را حذف کنید.
        if (Build.VERSION.SDK_INT >= 37) {
            checkAndRequestPermission(PermissionType.ACCESS_LOCAL_NETWORK) {}
        }
        LauncherManager.restartService(this)
    }
}
