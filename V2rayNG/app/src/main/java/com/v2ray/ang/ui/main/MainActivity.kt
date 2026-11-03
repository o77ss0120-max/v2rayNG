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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : HelperBaseComponentActivity() {

    // لینک پنل گیت‌هاب شما
    private val PENNY_PANEL_URL = "https://gist.githubusercontent.com/o77ss0120-max/5e592995c7b0f82b924cec572c0422a9/raw/penny_configs.txt"

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, MainRepository(application as AngApplication))
    }

    private var statusMessage by mutableStateOf("برای اتصال روی دکمه ضربه بزنید")
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
                isFetching -> Color(0xFFFFA000) // نارنجی در حال دریافت سرور
                isRunning -> Color(0xFF00E676)  // سبز در حالت متصل
                else -> Color(0xFF2979FF)       // آبی در حالت قطع
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
                    .padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "PENNY VPN",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "اتصال امن، پرسرعت و رایگان",
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
                                isFetching -> "WAIT..."
                                isRunning -> "CONNECTED"
                                else -> "CONNECT"
                            },
                            color = if (isRunning) Color.Black else Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isRunning) "قطع اتصال" else "شروع اتصال",
                            color = if (isRunning) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // بخش وضعیت و تست پینگ در پایین صفحه
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp),
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
                            text = if (isRunning) "وضعیت: متصل و ایمن ✅" else statusMessage,
                            color = if (isRunning) Color(0xFF00E676) else Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )

                        if (isRunning) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { mainViewModel.testCurrentServerRealPing() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                            ) {
                                Text("تست پینگ سرور", color = Color.White)
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
        } else {
            fetchPennyConfigsAndStart()
        }
    }

    private fun downloadTextWithTimeout(urlStr: String): String {
        val url = URL("$urlStr?t=${System.currentTimeMillis()}") // جلوگیری از کش شدن لینک قدیمی
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", "v2rayNG/1.8.0")
        return conn.inputStream.bufferedReader().use { it.readText() }.trim()
    }

    private fun fetchPennyConfigsAndStart() {
        isFetching = true
        statusMessage = "در حال دریافت جدیدترین سرور از پنل..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // ۱. دریافت محتوای پنل گیت‌هاب (با اضافه کردن ضد کش)
                var configText = try {
                    downloadTextWithTimeout(PENNY_PANEL_URL)
                } catch (e: Exception) {
                    // مسیر کمکی ضد فیلتر در صورتی که گیت‌هاب روی اینترنت کاربر بسته باشد
                    downloadTextWithTimeout("https://ghproxy.net/$PENNY_PANEL_URL")
                }

                // ۲. اگر داخل فایل Gist یک لینک سابسکریپشن (http...) گذاشته باشید، محتوای آن را هم باز می‌کند
                if (configText.startsWith("http://") || configText.startsWith("https://")) {
                    val subUrl = configText.lines().first().trim()
                    configText = downloadTextWithTimeout(subUrl)
                }

                if (configText.isNotEmpty()) {
                    // ۳. پاک کردن سرورهای قبلی و ایمپورت مستقیم و آنی در دیتابیس
                    MmkvManager.removeAllServer()
                    val (count, _) = AngConfigManager.importBatchConfig(configText, "", false)

                    withContext(Dispatchers.Main) {
                        isFetching = false
                        if (count > 0) {
                            // ۴. انتخاب اولین سرور از دیتابیس و روشن کردن هسته
                            mainViewModel.onAction(MainAction.RefreshGroups)
                            val serverList = MmkvManager.decodeServerList("")
                            val firstGuid = serverList.firstOrNull()

                            if (firstGuid != null) {
                                MmkvManager.setSelectServer(firstGuid)
                                mainViewModel.updateSelectedGuid(firstGuid)
                                statusMessage = "$count سرور دریافت شد. در حال اتصال..."
                                requestServiceStart()
                            } else {
                                statusMessage = "خطا: لیست سرورها خالی است!"
                            }
                        } else {
                            statusMessage = "خطا: فرمت کانفیگ داخل پنل معتبر نیست!"
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isFetching = false
                        statusMessage = "خطا: فایل پنل گیت‌هاب شما خالی است!"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isFetching = false
                    statusMessage = "خطا در ارتباط با پنل: ${e.localizedMessage ?: "اینترنت را بررسی کنید"}"
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
        val firstGuid = MmkvManager.decodeServerList("").firstOrNull()
        if (firstGuid != null) {
            MmkvManager.setSelectServer(firstGuid)
            mainViewModel.updateSelectedGuid(firstGuid)
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
        return super.onKeyDown(keyCode, event)
    }
}
