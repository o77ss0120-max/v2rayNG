package com.v2ray.ang.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.Uri
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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
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
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * MainActivity ماژولار شده Penny VPN:
 * منطق مدل‌ها در PennyModels.kt، اعتبارسنجی سرورمحور لایسنس ECDSA در PennyLicenseManager.kt،
 * اسکنر امن و امضاشده در PennyServerScanner.kt و کامپوننت‌های گرافیکی در PennyComponents.kt تفکیک شده‌اند.
 */
class MainActivity : HelperBaseComponentActivity() {

    companion object {
        private const val TAG = "PennyVPN"

        // ── شناسه‌های رسمی تست اندروید AdMob ──
        const val ADMOB_APP_ID = "ca-app-pub-3940256099942544~3347511713"
        private const val ADMOB_BANNER_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
        private const val ADMOB_INTERSTITIAL_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
        const val ADMOB_REWARDED_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
        const val ADMOB_NATIVE_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"
        const val ADMOB_APP_OPEN_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"

        private const val REAL_TEST_COUNT = 60
        private const val REAL_TEST_TIMEOUT_MS = 45_000L
        private const val BEST_LIST_SIZE = 15
        private const val MAX_CONNECT_ATTEMPTS = 6

        private const val STALE_MS = 6 * 60 * 60 * 1000L
        private const val MONITOR_INTERVAL_MS = 40_000L

        // ── مدل درآمدزایی Time-Boxed + محدودیت نمایش (Frequency Capping) ──
        private const val INTERSTITIAL_COOLDOWN_MS = 10 * 60 * 1000L // حداکثر ۱ تبلیغ تمام‌صفحه در هر ۱۰ دقیقه
        private const val BASE_FREE_SESSION_SEC = 3600L              // ۱ ساعت اتصال پایه با هر Connect
        private const val REWARD_ADD_TIME_SEC = 2 * 3600L            // +۲ ساعت افزایش زمان با تماشای ویدیو جایزه‌دار
    }

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, MainRepository(application as AngApplication))
    }

    private val socksPort: Int get() = SettingsManager.getSocksPort()

    // ── وضعیت AdMob ──
    private var interstitialAd: InterstitialAd? = null
    private var isAdLoading = false
    private var lastInterstitialShownMs by mutableStateOf(0L)
    private var rewardedAd: RewardedAd? = null
    private var isRewardedLoading = false
    private var freeSessionRemainingSec by mutableStateOf(BASE_FREE_SESSION_SEC)

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
    private var splitTunnelLocal by mutableStateOf(true)
    private var cleanDnsShield by mutableStateOf(true)
    private var connectionProfile by mutableStateOf(ConnectionProfile.SMART)
    private var userWantsConnected by mutableStateOf(false)

    // ── وضعیت اشتراک ۱ ماه ۵۰ گیگ (تأیید و شمارش حجم سمت سرور) ──
    private var vipActive by mutableStateOf(false)
    private var useVipServers by mutableStateOf(false)
    private var vipCode by mutableStateOf("")
    private var vipSerial by mutableStateOf("")
    private var vipExpireMs by mutableStateOf(0L)
    private var vipUsedBytes by mutableStateOf(0L)
    private var vipMaxBytes by mutableStateOf(VipLicenseManager.VIP_MAX_BYTES)
    private var vipLastSeenMs by mutableStateOf(0L)
    private var pendingUnsyncedBytes by mutableStateOf(0L)
    private var customVipSubUrl by mutableStateOf("")
    private var licenseServerUrl by mutableStateOf(VipLicenseManager.DEFAULT_LICENSE_API_BASE)
    private var showVipDialog by mutableStateOf(false)
    private val deviceId: String by lazy { VipLicenseManager.getDeviceId(this) }

    private var showMenu by mutableStateOf(false)
    private var showLog by mutableStateOf(false)
    private var showBest by mutableStateOf(false)
    private var showUpdateDialog by mutableStateOf(false)
    private var bpbFragmentMode by mutableStateOf("NORMAL")

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
        syncServerQuotaInBackground(0L)
        initAdMob()
        startMonitor()
        registerNetworkChangeAutoReconnect()
    }

    override fun onStop() {
        if (vipActive) {
            val now = effectiveNowMs()
            prefs.edit()
                .putLong("vip_used_bytes", vipUsedBytes)
                .putLong("vip_last_seen_ms", now)
                .apply()
            syncServerQuotaInBackground(pendingUnsyncedBytes)
            pendingUnsyncedBytes = 0L
        }
        super.onStop()
    }

    override fun onDestroy() {
        unregisterNetworkChangeCallback()
        super.onDestroy()
    }

    private fun effectiveNowMs(): Long {
        val sysNow = System.currentTimeMillis()
        if (sysNow > vipLastSeenMs) {
            vipLastSeenMs = sysNow
        }
        return maxOf(sysNow, vipLastSeenMs)
    }

    // ═════════════════════════ همگام‌سازی حجم ۵۰ گیگ و اعتبار لایسنس با سرور ═════════════════════════

    private fun syncServerQuotaInBackground(deltaToReport: Long) {
        if (!vipActive || vipSerial.isBlank()) return
        lifecycleScope.launch(Dispatchers.IO) {
            val state = VipLicenseManager.syncTrafficWithServer(
                apiBaseUrl = licenseServerUrl,
                serial = vipSerial,
                deviceId = deviceId,
                deltaBytes = deltaToReport
            ) ?: return@launch

            withContext(Dispatchers.Main) {
                vipUsedBytes = maxOf(vipUsedBytes, state.usedBytes)
                vipMaxBytes = state.maxBytes
                if (state.expiresAtMs > 0L) {
                    vipExpireMs = state.expiresAtMs
                }
                prefs.edit()
                    .putLong("vip_used_bytes", vipUsedBytes)
                    .putLong("vip_max_bytes", vipMaxBytes)
                    .putLong("vip_expire_ms", vipExpireMs)
                    .apply()
                if (!state.active || vipUsedBytes >= vipMaxBytes) {
                    checkVipExpiration()
                }
            }
        }
    }

    // ═════════════════════════ AdMob (مدل Time-Boxed + کول‌داون ۱۰ دقیقه‌ای) ═════════════════════════

    private fun initAdMob() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                MobileAds.initialize(this@MainActivity) {
                    plog("AdMob initialized (Banner + Interstitial + Rewarded ready)")
                }
                withContext(Dispatchers.Main) {
                    loadInterstitialAd()
                    loadRewardedAd()
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
        if (vipActive) return
        val now = System.currentTimeMillis()
        if (now - lastInterstitialShownMs < INTERSTITIAL_COOLDOWN_MS) {
            val waitSec = ((INTERSTITIAL_COOLDOWN_MS - (now - lastInterstitialShownMs)) / 1000L).coerceAtLeast(1L)
            plog("Interstitial skipped by 10m Frequency Cap (${waitSec}s remaining)")
            return
        }
        val ad = interstitialAd
        if (ad != null) {
            lastInterstitialShownMs = now
            prefs.edit().putLong("last_interstitial_ms", now).apply()
            ad.show(this)
        } else {
            loadInterstitialAd()
        }
    }

    private fun loadRewardedAd() {
        if (isRewardedLoading || rewardedAd != null) return
        isRewardedLoading = true
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            this,
            ADMOB_REWARDED_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isRewardedLoading = false
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            rewardedAd = null
                            loadRewardedAd()
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            rewardedAd = null
                        }
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    rewardedAd = null
                    isRewardedLoading = false
                }
            }
        )
    }

    private fun addTwoHoursToSession() {
        freeSessionRemainingSec += REWARD_ADD_TIME_SEC
        prefs.edit().putLong("free_session_rem_sec", freeSessionRemainingSec).apply()
        statusMessage = "+2 Hours Added • Remaining: ${fmtDuration(freeSessionRemainingSec)}"
        plog("Rewarded Video watched: +2 Hours added to session (Total left: ${fmtDuration(freeSessionRemainingSec)})")
    }

    private fun showRewardedAdForAddTime() {
        val ad = rewardedAd
        if (ad != null) {
            ad.show(this, OnUserEarnedRewardListener {
                addTwoHoursToSession()
            })
        } else {
            loadRewardedAd()
            addTwoHoursToSession()
        }
    }

    // ═════════════════════════ مدیریت اشتراک ۱ ماه ۵۰ گیگ (سرورمحور + ECDSA چرخانده‌شده) ═════════════════════════

    private fun getSubscriptionSources(isVip: Boolean): List<String> {
        val custom = customVipSubUrl.trim()
        val baseList = if (isVip) {
            PennyServerScanner.SIGNED_VIP_SUBSCRIPTION_SOURCES
        } else {
            PennyServerScanner.SIGNED_STANDARD_SUBSCRIPTION_SOURCES
        }
        return (if (custom.isNotEmpty()) {
            custom.split("\n", ",")
                .map { it.trim() }
                .filter { it.startsWith("https://", ignoreCase = true) } + baseList
        } else {
            baseList
        }).distinct()
    }

    private fun checkVipExpiration(): Boolean {
        PennySpeedLimiter.isVipUnrestricted = vipActive
        if (!vipActive) return false
        val now = effectiveNowMs()
        val timeExpired = now >= vipExpireMs
        val trafficExpired = vipUsedBytes >= vipMaxBytes
        if (timeExpired || trafficExpired) {
            vipActive = false
            useVipServers = false
            vipCode = ""
            PennySpeedLimiter.isVipUnrestricted = false
            prefs.edit()
                .putBoolean("vip_active", false)
                .putBoolean("use_vip_servers", false)
                .putString("vip_code", "")
                .putLong("vip_last_seen_ms", now)
                .apply()
            statusMessage = if (trafficExpired) {
                "Your 50 GB server traffic quota has been reached."
            } else {
                "Your 1-month subscription has expired."
            }
            plog("VIP expired: timeExpired=$timeExpired, trafficExpired=$trafficExpired")
            if (mainViewModel.uiState.value.isRunning) {
                LauncherManager.stopService(this)
            }
            return true
        }
        return false
    }

    private fun activateVipWithServerVerification(
        inputCode: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            val (serverState, msg) = VipLicenseManager.activateOnServer(
                apiBaseUrl = licenseServerUrl,
                rawCode = inputCode,
                deviceId = deviceId
            )
            withContext(Dispatchers.Main) {
                if (serverState == null || !serverState.ok || !serverState.active) {
                    onComplete(false, msg)
                    return@withContext
                }
                val now = effectiveNowMs()
                vipActive = true
                useVipServers = true
                vipCode = inputCode.trim()
                vipSerial = serverState.serial
                vipExpireMs = serverState.expiresAtMs
                vipMaxBytes = serverState.maxBytes
                vipUsedBytes = serverState.usedBytes
                vipLastSeenMs = now

                prefs.edit()
                    .putBoolean("vip_active", true)
                    .putBoolean("use_vip_servers", true)
                    .putString("vip_code", vipCode)
                    .putString("vip_serial", vipSerial)
                    .putLong("vip_expire_ms", vipExpireMs)
                    .putLong("vip_max_bytes", vipMaxBytes)
                    .putLong("vip_used_bytes", vipUsedBytes)
                    .putLong("vip_last_seen_ms", now)
                    .apply()

                plog("Server-verified 1M/50GB Plan Activated (${VipLicenseManager.maskCode(vipCode)}, used=${fmtBytes(vipUsedBytes)})")
                statusMessage = "1 Month 50 GB Plan Activated (Server Verified)"
                onComplete(true, statusMessage)
            }
        }
    }

    // ═════════════════════════ اتصال مجدد خودکار در تغییر شبکه ═════════════════════════

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
                        val tunnelAlive = isRunning &&
                            PennyServerScanner.testThroughLocalProxy(
                                PennyServerScanner.TEST_URLS.first(),
                                socksPort,
                                ::plog
                            ) > 0
                        if (!tunnelAlive) {
                            plog("Network change detected; auto-reconnecting...")
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
        } catch (e: Exception) {
            Log.w(TAG, "unregisterNetworkCallback warning: ${e.message}")
        }
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
                statusMessage = if (connected != null) "Auto-reconnected" else "Auto-reconnect failed"
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
        PennyServerScanner.applyBpbCoreOptimizations()
        autoSwitch = prefs.getBoolean("auto_switch", true)
        autoReconnect = prefs.getBoolean("auto_reconnect", true)
        splitTunnelLocal = prefs.getBoolean("split_tunnel_local", true)
        cleanDnsShield = prefs.getBoolean("clean_dns_shield", true)
        val savedProf = prefs.getString("conn_profile", ConnectionProfile.SMART.name) ?: ConnectionProfile.SMART.name
        connectionProfile = runCatching { ConnectionProfile.valueOf(savedProf) }.getOrDefault(ConnectionProfile.SMART)
        userWantsConnected = prefs.getBoolean("user_connected", false)
        lastScanTime = prefs.getLong("scan_time", 0L)
        vipLastScanTime = prefs.getLong("vip_scan_time", 0L)
        connectStartMs = prefs.getLong("connect_time", 0L)
        lastConnectedGuid = prefs.getString("last_guid", null)
        lastInterstitialShownMs = prefs.getLong("last_interstitial_ms", 0L)
        freeSessionRemainingSec = prefs.getLong("free_session_rem_sec", BASE_FREE_SESSION_SEC).coerceAtLeast(60L)

        vipActive = prefs.getBoolean("vip_active", false)
        PennySpeedLimiter.isVipUnrestricted = vipActive
        useVipServers = prefs.getBoolean("use_vip_servers", vipActive)
        vipCode = prefs.getString("vip_code", "") ?: ""
        vipSerial = prefs.getString("vip_serial", "") ?: ""
        vipExpireMs = prefs.getLong("vip_expire_ms", 0L)
        vipMaxBytes = prefs.getLong("vip_max_bytes", VipLicenseManager.VIP_MAX_BYTES)
        vipUsedBytes = prefs.getLong("vip_used_bytes", 0L)
        vipLastSeenMs = prefs.getLong("vip_last_seen_ms", 0L)
        effectiveNowMs()
        customVipSubUrl = prefs.getString("custom_vip_sub_url", PennyServerScanner.DEDICATED_BPB_FRAGMENT_SUB_URL)
            ?.takeIf { it.isNotBlank() } ?: PennyServerScanner.DEDICATED_BPB_FRAGMENT_SUB_URL
        licenseServerUrl = prefs.getString("license_server_url", VipLicenseManager.DEFAULT_LICENSE_API_BASE)
            ?: VipLicenseManager.DEFAULT_LICENSE_API_BASE

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
                plog("Error loading cache: ${e.message}")
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
                    val rawDown = (dRx * 1000L / dt).coerceAtLeast(0L)
                    val rawUp = (dTx * 1000L / dt).coerceAtLeast(0L)
                    downSpeed = if (vipActive) rawDown else rawDown.coerceAtMost(PennySpeedLimiter.FREE_MAX_DOWN_BYTES_PER_SEC)
                    upSpeed = if (vipActive) rawUp else rawUp.coerceAtMost(PennySpeedLimiter.FREE_MAX_UP_BYTES_PER_SEC)
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
                    val rawDown = (d * 1000L / dt).coerceAtLeast(0L)
                    val rawUp = (u * 1000L / dt).coerceAtLeast(0L)
                    downSpeed = if (vipActive) rawDown else rawDown.coerceAtMost(PennySpeedLimiter.FREE_MAX_DOWN_BYTES_PER_SEC)
                    upSpeed = if (vipActive) rawUp else rawUp.coerceAtMost(PennySpeedLimiter.FREE_MAX_UP_BYTES_PER_SEC)
                    totalDown += d
                    totalUp += u
                }
                lastT = now
                elapsedSec = (System.currentTimeMillis() - connectStartMs) / 1000L

                if (!vipActive) {
                    if (freeSessionRemainingSec > 0L) {
                        freeSessionRemainingSec -= 1L
                        if (tickCount % 10 == 0) {
                            prefs.edit().putLong("free_session_rem_sec", freeSessionRemainingSec).apply()
                        }
                    } else {
                        plog("1-hour free session timer ended; disconnecting")
                        statusMessage = "Session time ended • Tap Connect or + Add Time"
                        LauncherManager.stopService(this@MainActivity)
                    }
                }

                if (vipActive && useVipServers && stepDeltaBytes > 0L) {
                    vipUsedBytes += stepDeltaBytes
                    pendingUnsyncedBytes += stepDeltaBytes
                    if (tickCount % 5 == 0) {
                        prefs.edit().putLong("vip_used_bytes", vipUsedBytes).apply()
                    }
                    // هر ۱۵ ثانیه مصرف حجم ۵۰ گیگ را با سرور لایسنس همگام‌سازی می‌کنیم
                    if (tickCount % 15 == 0 && pendingUnsyncedBytes > 0L) {
                        val toSync = pendingUnsyncedBytes
                        pendingUnsyncedBytes = 0L
                        syncServerQuotaInBackground(toSync)
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
                    NeonBrandHeader(
                        mode = mode,
                        lang = appLang,
                        isVipMode = isVipMode,
                        onLogoClick = { showSplash = true }
                    )

                    Spacer(Modifier.height(10.dp))

                    PowerButton(
                        mode = mode,
                        progress = scanProgress,
                        lang = appLang,
                        isVipMode = isVipMode,
                        onClick = { onPowerClick() }
                    )

                    PulsingStatusText(
                        mode = mode,
                        statusMessage = statusMessage,
                        lang = appLang,
                        isVip = isVipMode
                    )

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
                                    StatCell(fmtSpeed(downSpeed), Strings.download(appLang), Modifier.weight(1f))
                                    StatCell(fmtSpeed(upSpeed), Strings.upload(appLang), Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    StatCell(
                                        if (vipActive) fmtDuration(elapsedSec) else fmtDuration(freeSessionRemainingSec),
                                        if (vipActive) Strings.duration(appLang) else "Session Time Left",
                                        Modifier.weight(1f)
                                    )
                                    StatCell(
                                        fmtBytes(totalDown + totalUp),
                                        Strings.totalData(appLang),
                                        Modifier.weight(1f)
                                    )
                                }

                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    color = if (vipActive) Color(0xFF20252E) else Color(0xFF231F1B),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp)
                                        .border(
                                            1.dp,
                                            if (vipActive) Color(0xFFD8DCE6).copy(alpha = 0.4f) else Color(0xFFE6CFAF).copy(alpha = 0.35f),
                                            RoundedCornerShape(10.dp)
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (vipActive) {
                                                "VIP Speed: Unlimited Full Bandwidth"
                                            } else {
                                                "Speed Throttled: 256 KB/s"
                                            },
                                            color = if (vipActive) Color(0xFFE4E9F2) else Color(0xFFE6CFAF),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (vipActive) "MAX SPEED" else "LIMITED",
                                            color = if (vipActive) Color(0xFFD8DCE6) else Color(0xFFB8B4AE),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                if (!vipActive) {
                                    Spacer(Modifier.height(8.dp))
                                    val btnPulse = rememberInfiniteTransition(label = "addTimePulse")
                                    val btnScale by btnPulse.animateFloat(
                                        initialValue = 1.0f,
                                        targetValue = 1.028f,
                                        animationSpec = infiniteRepeatable(
                                            tween(1100, easing = FastOutSlowInEasing),
                                            RepeatMode.Reverse
                                        ),
                                        label = "addTimeScale"
                                    )
                                    Surface(
                                        color = Color(0xFF282520),
                                        shape = RoundedCornerShape(13.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp)
                                            .scale(btnScale)
                                            .clip(RoundedCornerShape(13.dp))
                                            .border(
                                                1.2.dp,
                                                Brush.horizontalGradient(
                                                    listOf(Color(0xFFE6CFAF), Color(0xFFD8DCE6), Color(0xFFE6CFAF))
                                                ),
                                                RoundedCornerShape(13.dp)
                                            )
                                            .clickable { showRewardedAdForAddTime() }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(
                                                    text = "+ Add Time (+2 Hours)",
                                                    color = Color(0xFFF5E6D3),
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                                Text(
                                                    text = "Extend connection by watching a short video",
                                                    color = Color(0xFFB8B4AE),
                                                    fontSize = 9.5.sp
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFFE6CFAF))
                                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "+2h FREE",
                                                    color = Color(0xFF171512),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    ConnectionProfileBar(
                        selected = connectionProfile,
                        onSelect = { prof ->
                            connectionProfile = prof
                            prefs.edit().putString("conn_profile", prof.name).apply()
                            statusMessage = "${prof.label} profile active (${prof.tag})"
                            plog("Switched routing profile to ${prof.label}")
                        }
                    )

                    Spacer(Modifier.height(8.dp))

                    ModernServerCard(
                        server = current,
                        isConnected = mode == UiMode.CONNECTED,
                        isVipMode = isVipMode,
                        lang = appLang,
                        enabled = !busy && currentList.isNotEmpty(),
                        onClick = { showBest = true }
                    )

                    Spacer(Modifier.height(10.dp))

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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isVipMode) {
                                    GlassDiamondIcon(size = 13.dp)
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    Strings.scanServersBtn(appLang, isVipMode),
                                    fontSize = 13.sp,
                                    color = Color(0xFFE8ECF4)
                                )
                            }
                        }

                        Button(
                            onClick = { switchNext() },
                            enabled = !busy && isRunning && currentList.size > 1,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isVipMode) Color(0xFFD0D4DF) else Color(0xFF36353E)
                            )
                        ) {
                            Text(
                                Strings.nextServerBtn(appLang),
                                fontSize = 13.sp,
                                color = if (isVipMode) Color(0xFF15161B) else Color(0xFFF5EFE6),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

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
                                Surface(
                                    color = if (vipActive) Color(0xFF282A33) else Color(0xFF23242B),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(
                                            1.dp,
                                            if (vipActive) Color(0xFFE4E9F2).copy(alpha = 0.65f) else Color(0xFF3E3D46),
                                            RoundedCornerShape(14.dp)
                                        )
                                        .clickable { showVipDialog = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            GlassDiamondIcon(size = 18.dp)
                                            Spacer(Modifier.width(9.dp))
                                            Column {
                                                Text(
                                                    text = if (vipActive) "1 Month 50 GB (Active)" else "1 Month 50 GB Plan",
                                                    color = Color(0xFFF2F4F8),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                                val subVipText = if (vipActive) {
                                                    val remDays = ((vipExpireMs - System.currentTimeMillis()) / (24L * 3600_000L)).coerceAtLeast(0L)
                                                    val remBytes = (vipMaxBytes - vipUsedBytes).coerceAtLeast(0L)
                                                    "${remDays}d • ${fmtBytes(remBytes)} left"
                                                } else {
                                                    "Server-verified ECDSA license"
                                                }
                                                Text(
                                                    text = subVipText,
                                                    color = if (vipActive) Color(0xFFD8DCE6) else Color(0xFFA6A39E),
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Text(
                                            text = if (vipActive) "ACTIVE" else "›",
                                            color = if (vipActive) Color(0xFFE4E9F2) else Color(0xFFA6A39E),
                                            fontSize = if (vipActive) 10.sp else 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Surface(
                                    color = Color(0xFF17271F),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(1.dp, Color(0xFF39FF88).copy(alpha = 0.50f), RoundedCornerShape(14.dp))
                                        .clickable { showUpdateDialog = true }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        UpdateArrowIcon(size = 15.dp, color = Color(0xFF5CFF9D))
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = "Update",
                                            color = Color(0xFF5CFF9D),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }

                                Surface(
                                    color = Color(0xFF23242B),
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
                                        MenuLinesIcon(size = 15.dp)
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = "Menu",
                                            color = Color(0xFFA6A39E),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            if (vipActive) {
                                val progressRatio = (vipUsedBytes.toFloat() / vipMaxBytes.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                                Spacer(Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0xFF262830))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth((1f - progressRatio).coerceIn(0f, 1f))
                                            .fillMaxHeight()
                                            .background(Color(0xFFE4E9F2))
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    ServiceQualityUptimeCard(
                        lang = appLang,
                        isConnected = mode == UiMode.CONNECTED,
                        isVipMode = isVipMode,
                        serverCount = currentList.size,
                        bestPingMs = current?.ms ?: 180L,
                        cleanDnsShield = cleanDnsShield,
                        splitTunnelLocal = splitTunnelLocal,
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
            if (showUpdateDialog) UpdateCenterDialog()
            if (showLog) LogDialog()
        }
    }

    @Composable
    private fun ModernMenuDialog(isVipMode: Boolean) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            containerColor = Color(0xFF191A20),
            confirmButton = {
                TextButton(onClick = { showMenu = false }) {
                    Text(Strings.close(appLang), color = Color(0xFFD8DCE6))
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MenuLinesIcon(size = 16.dp)
                    Text(
                        text = "Settings & Control Center",
                        color = Color(0xFFF2F4F8),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = Color(0xFF15261E),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, Color(0xFF39FF88).copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                            .clickable {
                                showMenu = false
                                showUpdateDialog = true
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                UpdateArrowIcon(size = 15.dp, color = Color(0xFF5CFF9D))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Check for App & Server Update",
                                        color = Color(0xFF5CFF9D),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Version ${PennyServerScanner.APP_VERSION_NAME} • BPB Fragment Engine",
                                        color = Color(0xFF9EA1AB),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Text("›", color = Color(0xFF5CFF9D), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Surface(
                        color = Color(0xFF23252D),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, Color(0xFFD8DCE6).copy(alpha = 0.35f), RoundedCornerShape(14.dp))
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GlassDiamondIcon(size = 15.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "1 Month 50 GB Plan",
                                    color = Color(0xFFF2F4F8),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Text("›", color = Color(0xFFD0D4DF), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Surface(
                        color = Color(0xFF23252D),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, Color(0xFF3A3C46), RoundedCornerShape(14.dp))
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
                                text = Strings.menuBestConfigs(appLang, isVipMode),
                                color = Color(0xFFF2F4F8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text("${activeList.size}", color = Color(0xFFD8DCE6), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Surface(
                        color = Color(0xFF23252D),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF3A3C46), RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Auto-Switch on Drop", fontSize = 13.sp, color = Color(0xFFF2F4F8), modifier = Modifier.weight(1f))
                            Switch(
                                checked = autoSwitch,
                                onCheckedChange = {
                                    autoSwitch = it
                                    prefs.edit().putBoolean("auto_switch", it).apply()
                                },
                                colors = metallicSwitchColors()
                            )
                        }
                    }

                    Surface(
                        color = Color(0xFF23252D),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF3A3C46), RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Auto-Reconnect on Net Change", fontSize = 13.sp, color = Color(0xFFF2F4F8), modifier = Modifier.weight(1f))
                            Switch(
                                checked = autoReconnect,
                                onCheckedChange = {
                                    autoReconnect = it
                                    prefs.edit().putBoolean("auto_reconnect", it).apply()
                                },
                                colors = metallicSwitchColors()
                            )
                        }
                    }

                    Surface(
                        color = Color(0xFF23252D),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF3A3C46), RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Smart Split Tunneling", fontSize = 13.sp, color = Color(0xFFF2F4F8), fontWeight = FontWeight.SemiBold)
                                Text("Bypass local & banking apps automatically", fontSize = 10.sp, color = Color(0xFF9EA1AB))
                            }
                            Switch(
                                checked = splitTunnelLocal,
                                onCheckedChange = {
                                    splitTunnelLocal = it
                                    prefs.edit().putBoolean("split_tunnel_local", it).apply()
                                    MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, it)
                                    MmkvManager.encodeSettings(AppConfig.PREF_BYPASS_APPS, true)
                                    plog("Smart Split Tunneling: ${if (it) "Enabled" else "Disabled"}")
                                },
                                colors = metallicSwitchColors()
                            )
                        }
                    }

                    Surface(
                        color = Color(0xFF23252D),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF3A3C46), RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Clean DNS & Tracker Shield", fontSize = 13.sp, color = Color(0xFFF2F4F8), fontWeight = FontWeight.SemiBold)
                                Text("Encrypted DNS (1.1.1.2) • Malware & tracker block", fontSize = 10.sp, color = Color(0xFF9EA1AB))
                            }
                            Switch(
                                checked = cleanDnsShield,
                                onCheckedChange = {
                                    cleanDnsShield = it
                                    prefs.edit().putBoolean("clean_dns_shield", it).apply()
                                    val dnsServer = if (it) "1.1.1.2" else "1.1.1.1"
                                    MmkvManager.encodeSettings(AppConfig.PREF_REMOTE_DNS, dnsServer)
                                    plog("Clean DNS Shield set to $dnsServer")
                                },
                                colors = metallicSwitchColors()
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showMenu = false; showLog = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(Strings.menuAppLog(appLang), fontSize = 11.sp, maxLines = 1, color = Color(0xFFD8DCE6))
                        }
                        OutlinedButton(
                            onClick = {
                                showMenu = false
                                startActivity(Intent(this@MainActivity, LogcatActivity::class.java))
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(Strings.menuCoreLog(appLang), fontSize = 11.sp, maxLines = 1, color = Color(0xFFD8DCE6))
                        }
                    }
                }
            }
        )
    }

    @Composable
    private fun VipSubscriptionDialog() {
        var inputCode by remember { mutableStateOf("") }
        var subUrlInput by remember { mutableStateOf(customVipSubUrl) }
        var feedback by remember { mutableStateOf<String?>(null) }
        var isSuccess by remember { mutableStateOf(false) }
        var isVerifying by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showVipDialog = false },
            containerColor = Color(0xFF191A20),
            confirmButton = {
                TextButton(onClick = { showVipDialog = false }) {
                    Text(Strings.close(appLang), color = Color(0xFFD8DCE6))
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassDiamondIcon(size = 18.dp)
                    Text(
                        text = "1 Month 50 GB Plan",
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFF2F4F8)
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = Color(0xFF20222A),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF363844), RoundedCornerShape(10.dp))
                            .clickable {
                                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("PennyVPN Device ID", deviceId))
                                feedback = "Device ID ($deviceId) copied to clipboard"
                                isSuccess = true
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Device ID: $deviceId",
                                color = Color(0xFFE4E7F0),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Copy ID",
                                color = Color(0xFFD8DCE6),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (vipActive) {
                        val now = effectiveNowMs()
                        val remDays = ((vipExpireMs - now) / (24L * 3600_000L)).coerceAtLeast(0L)
                        val remHours = (((vipExpireMs - now) % (24L * 3600_000L)) / 3600_000L).coerceAtLeast(0L)
                        val remBytes = (vipMaxBytes - vipUsedBytes).coerceAtLeast(0L)

                        Surface(
                            color = Color(0xFF23252D),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFD8DCE6).copy(alpha = 0.40f), RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Status: Active (Server-Verified • Ad-Free)", color = Color(0xFFF2F4F8), fontWeight = FontWeight.Bold)
                                Text("Active License: ${VipLicenseManager.maskCode(vipCode)}", fontSize = 12.sp, color = Color(0xFFD0D4DF), fontFamily = FontFamily.Monospace)
                                Text("Time Remaining: ${remDays}d ${remHours}h", color = Color(0xFFD0D4DF), fontSize = 12.sp)
                                Text("Server Quota Left: ${fmtBytes(remBytes)} (Used: ${fmtBytes(vipUsedBytes)} / ${fmtBytes(vipMaxBytes)})", color = Color(0xFFE4E7F0), fontSize = 12.sp)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Use VIP Subscription Servers", color = Color(0xFFF2F4F8), fontSize = 13.sp)
                            Switch(
                                checked = useVipServers,
                                onCheckedChange = {
                                    useVipServers = it
                                    prefs.edit().putBoolean("use_vip_servers", it).apply()
                                },
                                colors = metallicSwitchColors()
                            )
                        }
                    } else {
                        Text(
                            text = "Enter your ECDSA P-256 license (PL1....) to verify on server and unlock 1 Month / 50 GB:",
                            color = Color(0xFFD0D4DF),
                            fontSize = 12.5.sp
                        )

                        OutlinedTextField(
                            value = inputCode,
                            onValueChange = { inputCode = it.trim() },
                            placeholder = { Text("PL1.<payload>.<signature>") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                if (isVerifying) return@Button
                                if (subUrlInput.trim() != customVipSubUrl) {
                                    customVipSubUrl = subUrlInput.trim()
                                    prefs.edit().putString("custom_vip_sub_url", customVipSubUrl).apply()
                                }
                                isVerifying = true
                                activateVipWithServerVerification(inputCode) { ok, msg ->
                                    isVerifying = false
                                    isSuccess = ok
                                    feedback = msg
                                    if (ok && vipBestList.isEmpty()) {
                                        startScanOnly()
                                    }
                                }
                            },
                            enabled = !isVerifying,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF383B46))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GlassDiamondIcon(size = 15.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (isVerifying) "Verifying on Server..." else "Activate 1 Month 50 GB",
                                    color = Color(0xFFF2F4F8),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        feedback?.let { msg ->
                            Text(
                                text = msg,
                                color = if (isSuccess) Color(0xFFE4E9F2) else Color(0xFFE57373),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedTextField(
                        value = subUrlInput,
                        onValueChange = {
                            subUrlInput = it
                            customVipSubUrl = it.trim()
                            prefs.edit().putString("custom_vip_sub_url", customVipSubUrl).apply()
                        },
                        label = { Text("Signed VIP Subscription URL (HTTPS + #PENNY-SIG)", fontSize = 11.sp) },
                        placeholder = { Text("https://.../vip-sub.txt", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }

    @Composable
    private fun BestConfigsDialog(selectedGuid: String?, isRunning: Boolean, isVipMode: Boolean) {
        val listToShow = activeList.sortedBy { it.ms }
        var isGlobalBubbleView by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showBest = false },
            containerColor = Color(0xFF191A20),
            confirmButton = {
                TextButton(onClick = { showBest = false }) {
                    Text(Strings.close(appLang), color = Color(0xFFE6CFAF))
                }
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isVipMode) {
                            GlassDiamondIcon(size = 16.dp)
                        }
                        Text(
                            text = Strings.menuBestConfigs(appLang, isVipMode),
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFF2F4F8),
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        color = if (isGlobalBubbleView) Color(0xFF2D2820) else Color(0xFF25262E),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                1.dp,
                                if (isGlobalBubbleView) Color(0xFFE6CFAF) else Color(0xFF454752),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { isGlobalBubbleView = !isGlobalBubbleView }
                    ) {
                        Text(
                            text = if (isGlobalBubbleView) "List View" else "Global View",
                            color = if (isGlobalBubbleView) Color(0xFFF5E6D3) else Color(0xFFD8DCE6),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            },
            text = {
                if (listToShow.isEmpty()) {
                    Text(Strings.emptyBestList(appLang))
                } else if (isGlobalBubbleView) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Interactive Latency Bubble Map • Larger bubble = Faster ping",
                            color = Color(0xFFA6A39E),
                            fontSize = 10.5.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF121318))
                                .border(1.dp, Color(0xFF2E303A), RoundedCornerShape(16.dp))
                        ) {
                            val w = maxWidth
                            val h = maxHeight
                            val positions = listOf(
                                0.50f to 0.48f,
                                0.25f to 0.30f,
                                0.75f to 0.32f,
                                0.27f to 0.72f,
                                0.74f to 0.70f,
                                0.50f to 0.18f,
                                0.50f to 0.82f,
                                0.16f to 0.52f,
                                0.84f to 0.52f
                            )
                            listToShow.take(positions.size).forEachIndexed { idx, s ->
                                val active = isRunning && s.guid == selectedGuid
                                val (fx, fy) = positions[idx]
                                val bubbleSize = (82 - idx * 5).coerceIn(50, 82).dp
                                val xOffset = (w * fx) - (bubbleSize / 2)
                                val yOffset = (h * fy) - (bubbleSize / 2)

                                Box(
                                    modifier = Modifier
                                        .offset(x = xOffset, y = yOffset)
                                        .size(bubbleSize)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.radialGradient(
                                                if (active) {
                                                    listOf(Color(0xFFE6CFAF), Color(0xFF5A4E3C))
                                                } else if (s.ms < 220) {
                                                    listOf(Color(0xFF3E4250), Color(0xFF22242C))
                                                } else {
                                                    listOf(Color(0xFF2D2E38), Color(0xFF191A20))
                                                }
                                            )
                                        )
                                        .border(
                                            width = if (active) 2.dp else 1.2.dp,
                                            color = if (active) Color(0xFFF5E6D3)
                                            else if (s.ms < 220) Color(0xFFD8DCE6).copy(alpha = 0.75f)
                                            else Color(0xFF525564),
                                            shape = CircleShape
                                        )
                                        .clickable(enabled = phase == Phase.IDLE) {
                                            showBest = false
                                            connectToEntry(s)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = s.codeTag.ifEmpty { "A-1" },
                                            color = if (active) Color(0xFF161512) else Color(0xFFF2F4F8),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "${s.ms}ms",
                                            color = if (active) Color(0xFF0B2918) else Color(0xFF5CFF9D),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.Monospace,
                                            style = TextStyle(
                                                shadow = Shadow(
                                                    color = Color(0xFF00FF7F).copy(alpha = if (active) 0.35f else 0.75f),
                                                    offset = Offset(0f, 0f),
                                                    blurRadius = 8f
                                                )
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
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
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            ShinyGreenPingBadge(
                                                ms = s.ms,
                                                showDiamond = s.isVip
                                            )
                                            if (active) {
                                                Text(
                                                    text = "ACTIVE",
                                                    color = Color(0xFFF2F4F8),
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 9.5.sp
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
    private fun UpdateCenterDialog() {
        var checking by remember { mutableStateOf(true) }
        var updateInfo by remember {
            mutableStateOf(
                PennyServerScanner.AppUpdateInfo(
                    currentVersion = PennyServerScanner.APP_VERSION_NAME,
                    latestVersion = PennyServerScanner.APP_VERSION_NAME,
                    hasUpdate = false,
                    downloadUrl = PennyServerScanner.GITHUB_RELEASES_PAGE,
                    releaseNotes = "Checking GitHub Releases & BPB Fragment status..."
                )
            )
        }
        var statusNote by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            checking = true
            val info = withContext(Dispatchers.IO) {
                PennyServerScanner.checkAppUpdateOnGitHub()
            }
            updateInfo = info
            checking = false
        }

        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            containerColor = Color(0xFF181A20),
            confirmButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text(Strings.close(appLang), color = Color(0xFFD8DCE6))
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UpdateArrowIcon(size = 18.dp, color = Color(0xFF5CFF9D))
                    Text(
                        text = "Penny VPN Update Center",
                        color = Color(0xFFF2F4F8),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = Color(0xFF12231B),
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF39FF88).copy(alpha = 0.50f), RoundedCornerShape(13.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Current Build: v${updateInfo.currentVersion}",
                                    color = Color(0xFFF2F4F8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = if (checking) "CHECKING..." else if (updateInfo.hasUpdate) "UPDATE READY" else "UP TO DATE",
                                    color = Color(0xFF5CFF9D),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = "Latest Release: v${updateInfo.latestVersion}",
                                color = Color(0xFFD0D4DF),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = updateInfo.releaseNotes,
                                color = Color(0xFFA8B0BE),
                                fontSize = 10.5.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // انتخاب حالت فرگمنت BPB-VPN (Normal 100-200ms یا Smart 1-5ms)
                    Surface(
                        color = Color(0xFF21232B),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF383A46), RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "BPB-VPN Anti-DPI Fragment Engine",
                                color = Color(0xFFF2F4F8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val isNormal = bpbFragmentMode == "NORMAL"
                                OutlinedButton(
                                    onClick = {
                                        bpbFragmentMode = "NORMAL"
                                        prefs.edit().putString("bpb_frag_mode", "NORMAL").apply()
                                        MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_ENABLED, true)
                                        MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_PACKETS, "tlshello")
                                        MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_LENGTH, "100-200")
                                        MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_INTERVAL, "1")
                                        statusNote = "Switched to BPB Normal Fragment (100-200 bytes / 1ms)"
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Normal (100-200)",
                                        fontSize = 10.5.sp,
                                        color = if (isNormal) Color(0xFF5CFF9D) else Color(0xFFB8BCC8),
                                        fontWeight = if (isNormal) FontWeight.ExtraBold else FontWeight.Medium
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        bpbFragmentMode = "SMART"
                                        prefs.edit().putString("bpb_frag_mode", "SMART").apply()
                                        MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_ENABLED, true)
                                        MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_PACKETS, "tlshello")
                                        MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_LENGTH, "1-5")
                                        MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_INTERVAL, "1")
                                        statusNote = "Switched to BPB Smart Fragment (1-5 bytes / 1ms)"
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Smart (1-5)",
                                        fontSize = 10.5.sp,
                                        color = if (!isNormal) Color(0xFF5CFF9D) else Color(0xFFB8BCC8),
                                        fontWeight = if (!isNormal) FontWeight.ExtraBold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            showUpdateDialog = false
                            startScanOnly()
                        },
                        enabled = phase == Phase.IDLE,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F402E)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF39FF88).copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                    ) {
                        Text(
                            text = "Update BPB Servers & Clean IPs Now",
                            color = Color(0xFF5CFF9D),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.5.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.downloadUrl))
                                    startActivity(intent)
                                }
                            },
                            shape = RoundedCornerShape(11.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Download APK",
                                color = Color(0xFFF2F4F8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PennyServerScanner.GITHUB_ACTIONS_PAGE))
                                    startActivity(intent)
                                }
                            },
                            shape = RoundedCornerShape(11.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "GitHub Builds",
                                color = Color(0xFFD8DCE6),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    statusNote?.let { note ->
                        Text(
                            text = note,
                            color = Color(0xFF5CFF9D),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
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
                        if (!vipActive && freeSessionRemainingSec < BASE_FREE_SESSION_SEC) {
                            freeSessionRemainingSec = BASE_FREE_SESSION_SEC
                            prefs.edit().putLong("free_session_rem_sec", freeSessionRemainingSec).apply()
                        }
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
                    plog("${entry.label} failed")
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

    private fun startMonitor() {
        lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                delay(MONITOR_INTERVAL_MS)
                if (phase != Phase.IDLE) continue
                if (checkVipExpiration()) continue

                val running = mainViewModel.uiState.value.isRunning
                if (!running && userWantsConnected && autoReconnect && activeList.isNotEmpty()) {
                    plog("Unexpected VPN drop detected; running auto-reconnect...")
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
            if (PennyServerScanner.testThroughLocalProxy(
                    PennyServerScanner.TEST_URLS[i % PennyServerScanner.TEST_URLS.size],
                    socksPort,
                    ::plog
                ) > 0
            ) {
                ok = true
                break
            }
            delay(2000)
        }
        if (ok) return

        plog("Active tunnel unresponsive; auto-switching...")
        val current = mainViewModel.uiState.value.selectedGuid
        if (current != null) failedGuids.add(current)
        ui {
            phase = Phase.CONNECTING
            statusMessage = "Connection dropped; auto-switching..."
        }
        try {
            val r = connectFromList(startAfter = current)
            ui {
                statusMessage = if (r != null) "Switched to healthier server" else "No healthy server found; please scan"
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Main) { phase = Phase.IDLE }
        }
    }

    private suspend fun runScan(): Boolean {
        PennyServerScanner.applyBpbCoreOptimizations()
        val scanningVip = vipActive && useVipServers
        val sources = getSubscriptionSources(scanningVip)

        ui {
            phase = Phase.SCANNING
            scanProgress = 0.05f
            statusMessage = if (scanningVip) "Fetching VIP subscription servers..." else "Fetching subscription servers..."
        }
        plog("Scan started (VIP=$scanningVip, sources=${sources.size})")

        val downloadedContents = coroutineScope {
            sources.map { url ->
                async(Dispatchers.IO) {
                    PennyServerScanner.fetchSignedListWithFallback(url, ::plog)
                }
            }.awaitAll().filter { it.isNotEmpty() }
        }
        val contents = if (downloadedContents.isNotEmpty()) {
            downloadedContents + listOf(PennyServerScanner.getBuiltinBpbFallbackConfigs())
        } else {
            plog("External download blocked by ISP; using built-in Clean-IP BPB nodes")
            listOf(PennyServerScanner.getBuiltinBpbFallbackConfigs())
        }

        ui {
            scanProgress = 0.15f
            statusMessage = "Importing subscription servers..."
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
        val allAfter = MmkvManager.decodeServerList("")
        val newGuids = allAfter.filter { it !in before }.ifEmpty { allAfter }
        newGuids.forEach { g ->
            try {
                val cfg = MmkvManager.decodeServerConfig(g)
                if (cfg != null) {
                    if (cfg.server?.endsWith(".pages.dev", ignoreCase = true) == true) {
                        cfg.server = PennyServerScanner.CLEAN_CF_ENDPOINTS.first()
                    }
                    if (cfg.finalMask.isNullOrBlank() &&
                        (cfg.host?.endsWith(".pages.dev", ignoreCase = true) == true ||
                            cfg.sni?.endsWith(".pages.dev", ignoreCase = true) == true)
                    ) {
                        cfg.finalMask = PennyServerScanner.BPB_FINALMASK_NORMAL_JSON
                    }
                    cfg.remarks = "Penny VPN"
                    MmkvManager.encodeServerConfig(g, cfg)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sanitize remarks warning for $g: ${e.message}")
            }
        }
        if (newGuids.isEmpty()) {
            ui { statusMessage = "No valid configs found in subscription!" }
            return false
        }

        val tcpSampleCount = minOf(newGuids.size, PennyServerScanner.MAX_TCP_CANDIDATES)
        ui {
            scanProgress = 0.28f
            statusMessage = "TCP probing $tcpSampleCount servers..."
        }
        val tcpOk = PennyServerScanner.tcpFilter(newGuids.shuffled().take(PennyServerScanner.MAX_TCP_CANDIDATES))
        val keep = if (tcpOk.isNotEmpty()) {
            tcpOk.take(REAL_TEST_COUNT).map { it.first }
        } else {
            plog("TCP probe returned 0 direct hits; testing ${minOf(newGuids.size, 20)} servers via Xray core")
            newGuids.take(minOf(newGuids.size, 20))
        }

        val keepSet = keep.toHashSet()
        val oldFree = bestList.map { it.guid }.toHashSet()
        val oldVip = vipBestList.map { it.guid }.toHashSet()
        val selected = mainViewModel.uiState.value.selectedGuid
        val protect = HashSet<String>().apply {
            addAll(keepSet); addAll(oldFree); addAll(oldVip); selected?.let { add(it) }
        }
        val toRemove = MmkvManager.decodeServerList("").filter { it !in protect }
        if (toRemove.isNotEmpty()) {
            MmkvManager.removeServers(toRemove, "")
        }

        MmkvManager.clearAllTestDelayResults(keep)
        ui {
            scanProgress = 0.40f
            statusMessage = "Real ping test (0/${keep.size})..."
        }
        withContext(Dispatchers.Main) {
            MessageHelper.sendMsg2TestService(
                this@MainActivity,
                TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_START, serverGuids = keep),
                UUID.randomUUID().toString()
            )
        }

        val timeoutMs = if (keep.size <= 16) 14_000L else REAL_TEST_TIMEOUT_MS
        val startT = System.currentTimeMillis()
        var done = 0
        while (System.currentTimeMillis() - startT < timeoutMs) {
            delay(450)
            done = keep.count { (MmkvManager.decodeServerAffiliationInfo(it)?.testDelayMillis ?: 0L) != 0L }
            val d = done
            ui {
                scanProgress = 0.40f + 0.50f * d / keep.size.coerceAtLeast(1)
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

        val realValid = keep.mapNotNull { g ->
            val d = MmkvManager.decodeServerAffiliationInfo(g)?.testDelayMillis ?: 0L
            if (d > 0L) g to d else null
        }.sortedBy { it.second }

        val tcpMap = tcpOk.toMap()
        val valid = if (realValid.isNotEmpty()) {
            realValid
        } else {
            plog("Real ping returned no positive delays; falling back to verified TCP ping (${tcpOk.size} servers)")
            keep.mapNotNull { g ->
                val ms = tcpMap[g] ?: PennyServerScanner.testServerTcpPing(g)
                if (ms > 0L) g to ms else null
            }.sortedBy { it.second }
        }

        if (valid.isEmpty()) {
            ui { statusMessage = "No reachable servers found on current network." }
            return false
        }

        ui {
            scanProgress = 0.94f
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
        plog("Scan complete (VIP=$scanningVip): ${entries.size} servers ready")
        return true
    }

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
                plog("Connected: ${s.label} (${ping}ms)")
                markConnected(s.guid, ping)
                return s
            }
            failedGuids.add(s.guid)
            plog("Failed: ${s.label}")
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
        PennySpeedLimiter.isVipUnrestricted = vipActive
        val wasRunning = mainViewModel.uiState.value.isRunning
        withContext(Dispatchers.Main) {
            MmkvManager.setSelectServer(guid)
            mainViewModel.updateSelectedGuid(guid)
            if (wasRunning) LauncherManager.restartService(this@MainActivity)
            else LauncherManager.startService(this@MainActivity)
        }

        delay(if (wasRunning) 2200L else 1400L)
        var waited = 0
        while (!mainViewModel.uiState.value.isRunning && waited < 10000) {
            delay(300)
            waited += 300
        }
        if (!mainViewModel.uiState.value.isRunning) {
            plog("Service did not start (${waited}ms)")
            return -1L
        }
        delay(1200)

        val localPortReady = withContext(Dispatchers.IO) { PennyServerScanner.isLocalPortOpen(socksPort) }
        if (!localPortReady) {
            plog("Warning: SOCKS port $socksPort not open yet")
        }
        repeat(2) {
            for (url in PennyServerScanner.TEST_URLS) {
                val ping = withContext(Dispatchers.IO) {
                    PennyServerScanner.testThroughLocalProxy(url, socksPort, ::plog)
                }
                if (ping > 0) return ping
            }
            delay(1000)
        }
        if (mainViewModel.uiState.value.isRunning && localPortReady) {
            val tcpPing = withContext(Dispatchers.IO) { PennyServerScanner.testServerTcpPing(guid) }
            if (tcpPing > 0L) {
                plog("Local proxy active & TCP handshake ok (${tcpPing}ms); accepting connection")
                return tcpPing
            }
        }
        return -1L
    }
}
