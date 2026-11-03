package com.v2ray.ang.ui.main

import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

class MainActivity : HelperBaseComponentActivity() {

    // لیست منابع کانفیگ: اول پنل شخصی شما، سپس لینک‌های ۱۵ دقیقه‌ای barry-far
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

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                startV2Ray()
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
                isFetching -> Color(0xFFFFA000) // نارنجی: در حال تست و پیدا کردن بهترین سرور
                isRunning -> Color(0xFF00E676)  // سبز: متصل
                else -> Color(0xFF2979FF)       // آبی: آماده اتصال
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
            // هدر بالای صفحه
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

            // دکمه بزرگ تک‌کلیکه وسط صفحه
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

            // پنل وضعیت و دکمه‌های کنترلی پایین صفحه
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
                            text = if (isRunning) "وضعیت: متصل به بهترین سرور ✅" else statusMessage,
                            color = if (isRunning) Color(0xFF00E676) else Color.White,
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
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
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

    private fun handleOneClickAction() {
        if (mainViewModel.uiState.value.isRunning) {
            LauncherManager.stopService(this)
            statusMessage = "اتصال قطع شد"
            activeServerInfo = ""
        } else {
            fetchAndConnectBestServer()
        }
    }

    private fun downloadUrl(urlStr: String): String {
        val url = URL("$urlStr?t=${System.currentTimeMillis()}")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 7000
        conn.readTimeout = 7000
        conn.setRequestProperty("User-Agent", "v2rayNG/1.8.0")
        return conn.inputStream.bufferedReader().use { it.readText() }.trim()
    }

    private fun fetchWithFallback(urlStr: String): String {
        // تلاش اول: مستقیم، تلاش دوم و سوم: پروکسی‌های ضد فیلتر گیت‌هاب
        val mirrors = listOf(
            urlStr,
            "https://ghproxy.net/$urlStr",
            "https://mirror.ghproxy.com/$urlStr"
        )
        for (mirror in mirrors) {
            try {
                val content = downloadUrl(mirror)
                if (content.isNotEmpty()) return content
            } catch (_: Exception) {}
        }
        return ""
    }

    // تست پینگ واقعی سرور قبل از اتصال (TCP Ping)
    private fun testServerTcpPing(guid: String): Long {
        val profile = MmkvManager.decodeServerConfig(guid) ?: return -1L
        val host = profile.server?.trim() ?: return -1L
        val port = profile.serverPort?.toString()?.trim()?.toIntOrNull() ?: return -1L
        return try {
            val start = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 1200) // حداکثر ۱.۲ ثانیه منتظر پاسخ می‌ماند
            }
            System.currentTimeMillis() - start
        } catch (_: Exception) {
            -1L
        }
    }

    private fun fetchAndConnectBestServer() {
        isFetching = true
        statusMessage = "در حال دریافت لیست سرورها..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val combinedConfigs = StringBuilder()

                // ۱. دانلود کانفیگ‌ها از پنل شما و Sub7 و Sub8
                for (sourceUrl in SUBSCRIPTION_SOURCES) {
                    val content = fetchWithFallback(sourceUrl)
                    if (content.isNotEmpty()) {
                        combinedConfigs.append(content).append("\n")
                    }
                }

                val allConfigText = combinedConfigs.toString().trim()
                if (allConfigText.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        isFetching = false
                        statusMessage = "خطا در دانلود سرورها! اینترنت خود را بررسی کنید."
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    statusMessage = "در حال پردازش و تست پینگ سرورها..."
                }

                // ۲. ایمپورت کانفیگ‌ها به دیتابیس
                MmkvManager.removeAllServer()
                AngConfigManager.importBatchConfig(allConfigText, "", false)

                val allGuids = MmkvManager.decodeServerList("")
                if (allGuids.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        isFetching = false
                        statusMessage = "هیچ کانفیگ معتبری یافت نشد!"
                    }
                    return@launch
                }

                // ۳. انتخاب تصادفی ۲۵ سرور و تست پینگ هم‌زمان برای پیدا کردن سریع‌ترین سرور
                val candidates = allGuids.shuffled().take(25)
                val pingResults = candidates.map { guid ->
                    async(Dispatchers.IO) {
                        val ping = testServerTcpPing(guid)
                        Pair(guid, ping)
                    }
                }.awaitAll()

                val bestServer = pingResults
                    .filter { it.second in 1..1199 }
                    .minByOrNull { it.second }

                val selectedGuid = bestServer?.first ?: allGuids.first()
                val bestPing = bestServer?.second ?: -1L
                val selectedProfile = MmkvManager.decodeServerConfig(selectedGuid)

                withContext(Dispatchers.Main) {
                    isFetching = false
                    MmkvManager.setSelectServer(selectedGuid)
                    mainViewModel.updateSelectedGuid(selectedGuid)
                    mainViewModel.onAction(MainAction.RefreshGroups)

                    activeServerInfo = if (bestPing > 0) {
                        "سرور منتخب: ${selectedProfile?.remarks ?: "Fast Server"} (پینگ: ${bestPing}ms)"
                    } else {
                        "سرور منتخب: ${selectedProfile?.remarks ?: "Auto Server"}"
                    }
                    statusMessage = "بهترین سرور انتخاب شد (${bestPing}ms). در حال اتصال..."
                    requestServiceStart()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isFetching = false
                    statusMessage = "خطا: ${e.localizedMessage ?: "مشکل در ارتباط"}"
                }
            }
        }
    }

    // تعویض سریع به سرور سالم بعدی بدون نیاز به دانلود مجدد
    private fun switchNextBestServer() {
        isFetching = true
        statusMessage = "در حال جستجوی سرور پرسرعت بعدی..."
        lifecycleScope.launch(Dispatchers.IO) {
            val allGuids = MmkvManager.decodeServerList("")
            val candidates = allGuids.shuffled().take(20)
            val pingResults = candidates.map { guid ->
                async(Dispatchers.IO) {
                    Pair(guid, testServerTcpPing(guid))
                }
            }.awaitAll()

            val bestServer = pingResults
                .filter { it.second in 1..1199 }
                .minByOrNull { it.second }

            val selectedGuid = bestServer?.first ?: allGuids.randomOrNull()
            val bestPing = bestServer?.second ?: -1L

            withContext(Dispatchers.Main) {
                isFetching = false
                if (selectedGuid != null) {
                    val profile = MmkvManager.decodeServerConfig(selectedGuid)
                    MmkvManager.setSelectServer(selectedGuid)
                    mainViewModel.updateSelectedGuid(selectedGuid)
                    activeServerInfo = if (bestPing > 0) {
                        "سرور جدید: ${profile?.remarks ?: "Fast Server"} (پینگ: ${bestPing}ms)"
                    } else {
                        "سرور جدید: ${profile?.remarks ?: "Auto Server"}"
                    }
                    LauncherManager.restartService(this@MainActivity)
                }
            }
        }
    }

    private fun requestServiceStart() {
        if (!SettingsManager.isVpnMode()) {
            startV2Ray()
            return
        }
        val intent = VpnService.prepare(this)
        if (intent == null) startV2Ray() else requestVpnPermission.launch(intent)
    }

    private fun startV2Ray() {
        val currentGuid = mainViewModel.uiState.value.selectedGuid
        if (currentGuid.isNullOrEmpty()) {
            val firstGuid = MmkvManager.decodeServerList("").firstOrNull()
            if (firstGuid != null) {
                MmkvManager.setSelectServer(firstGuid)
                mainViewModel.updateSelectedGuid(firstGuid)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            checkAndRequestPermission(PermissionType.ACCESS_LOCAL_NETWORK) {}
        }
        LauncherManager.startService(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown
