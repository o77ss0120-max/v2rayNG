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
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.platform.LocalConfiguration
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
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.adlisteners.VideoListener
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
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.core.CoreNativeManager
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
        private const val MAX_CONNECT_ATTEMPTS = 10

        private const val STALE_MS = 6 * 60 * 60 * 1000L
        private const val MONITOR_INTERVAL_MS = 40_000L

        // ── مدل درآمدزایی Time-Boxed + سهمیه روزانه ۱ گیگابایت برای هر دیوایس + تبلیغات Start.io SDK ──
        const val STARTIO_APP_ID = "210814549"
        private const val INTERSTITIAL_COOLDOWN_MS = 10 * 60 * 1000L // حداکثر ۱ تبلیغ تمام‌صفحه در هر ۱۰ دقیقه
        private const val BASE_FREE_SESSION_SEC = 3600L              // ۱ ساعت اتصال پایه با هر Connect
        private const val REWARD_ADD_TIME_SEC = 2 * 3600L            // +۲ ساعت افزایش زمان با تماشای تبلیغ حامی
        private const val BASE_DAILY_FREE_BYTES = 1L * 1024L * 1024L * 1024L // ۱ گیگابایت سهمیه پایه روزانه هر دیوایس
        private const val REWARD_ADD_DATA_BYTES = 1L * 1024L * 1024L * 1024L // +۱ گیگابایت حجم اضافه با تماشای ویدیو جایزه‌دار
        private const val STARTIO_REWARD_BONUS_BYTES = 100L * 1024L * 1024L  // +۱۰۰ مگابایت سهمیه VIP با تماشای هر تبلیغ جایزه‌دار Start.io
    }

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, MainRepository(application as AngApplication))
    }

    private val socksPort: Int get() = SettingsManager.getSocksPort()

    // ── وضعیت Start.io SDK، AdMob و سهمیه روزانه ۱ گیگ به ازای هر دیوایس ──
    private var startIoInterstitialAd: StartAppAd? = null
    private var interstitialAd: InterstitialAd? = null
    private var isAdLoading = false
    private var lastInterstitialShownMs by mutableStateOf(0L)
    private var rewardedAd: RewardedAd? = null
    private var isRewardedLoading = false
    private var freeSessionRemainingSec by mutableStateOf(BASE_FREE_SESSION_SEC)
    private var dailyQuotaDayKey by mutableStateOf("")
    private var dailyUsedBytes by mutableStateOf(0L)
    private var dailyMaxBytes by mutableStateOf(BASE_DAILY_FREE_BYTES)

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
    private var loadBalanceEnabled by mutableStateOf(true)
    private var killSwitchEnabled by mutableStateOf(true)
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
    private val userUuid: String by lazy { VipLicenseManager.getUserUuid(this) }

    private var currentNavTab by mutableStateOf(BottomNavTab.HOME)
    private var serviceTierMode by mutableStateOf(ServiceTierMode.DEDICATED)
    private var ghostMessagingOnly by mutableStateOf(true)
    private var ghostLastScanTime by mutableStateOf(0L)

    private var showMenu by mutableStateOf(false)
    private var showLog by mutableStateOf(false)
    private var showBest by mutableStateOf(false)
    private var showUpdateDialog by mutableStateOf(false)
    private var bpbFragmentMode by mutableStateOf("NORMAL")

    private val logLines = mutableStateListOf<String>()
    private val bestList = mutableStateListOf<PennyServer>()
    private val vipBestList = mutableStateListOf<PennyServer>()
    private val ghostBestList = mutableStateListOf<PennyServer>()

    private val activeList: MutableList<PennyServer>
        get() = when (serviceTierMode) {
            ServiceTierMode.PUBLIC -> bestList
            ServiceTierMode.DEDICATED -> vipBestList
            ServiceTierMode.GHOST -> ghostBestList
        }

    private val activeScanTime: Long
        get() = when (serviceTierMode) {
            ServiceTierMode.PUBLIC -> lastScanTime
            ServiceTierMode.DEDICATED -> vipLastScanTime
            ServiceTierMode.GHOST -> ghostLastScanTime
        }

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
            prefs.edit().putLong("vip_last_seen_ms", vipLastSeenMs).apply()
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

    // ═════════════════════════ Start.io SDK (App ID: 210814549) + AdMob ═════════════════════════

    private fun initAdMob() {
        runCatching {
            StartAppSDK.initParams(this@MainActivity, STARTIO_APP_ID)
                .setReturnAdsEnabled(false)
                .init()
            StartAppAd.disableSplash()
            loadStartIoInterstitialAd()
            plog("Start.io SDK initialized (App ID: $STARTIO_APP_ID • ReturnAds=false)")
        }.onFailure { e ->
            plog("Start.io init warning: ${e.message}")
        }

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

    private fun loadStartIoInterstitialAd() {
        // فقط زمانی که VPN خاموش است پیش‌بارگذاری انجام می‌شود تا درخواست تبلیغ از آی‌پی واقعی کاربر ارسال شود
        if (vipActive || mainViewModel.uiState.value.isRunning) return
        runCatching {
            val ad = StartAppAd(this)
            ad.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
                override fun onReceiveAd(receivedAd: Ad) {
                    startIoInterstitialAd = ad
                    plog("Start.io Interstitial preloaded on direct user IP")
                }

                override fun onFailedToReceiveAd(failedAd: Ad?) {
                    startIoInterstitialAd = null
                }
            })
        }
    }

    private fun loadInterstitialAd() {
        if (vipActive || isAdLoading || interstitialAd != null || mainViewModel.uiState.value.isRunning) return
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

    private suspend fun pauseVpnForDirectIpAdIfNeeded(): Boolean {
        val wasRunning = mainViewModel.uiState.value.isRunning || phase != Phase.IDLE
        if (wasRunning) {
            withContext(Dispatchers.Main) {
                workJob?.cancel()
                userWantsConnected = false
                phase = Phase.IDLE
                scanProgress = 0f
                statusMessage = "Disconnecting VPN to load ad on your direct IP..."
                plog("VPN disconnected before ad playback so Start.io uses user's real IP (not VPN server IP)")
                runCatching { PennySpeedLimiter.stop() }
                runCatching { LauncherManager.stopService(this@MainActivity) }
            }
            var waitedMs = 0L
            while (mainViewModel.uiState.value.isRunning && waitedMs < 2000L) {
                delay(150L)
                waitedMs += 150L
            }
            delay(450L) // اطمینان از بسته شدن کامل اینترفیس TUN و بازگشت ترافیک به IP واقعی کاربر
        }
        return wasRunning
    }

    private fun resumeVpnAfterAdIfNeeded(shouldResume: Boolean) {
        if (!shouldResume) return
        lifecycleScope.launch(Dispatchers.Main) {
            delay(350L)
            if (!mainViewModel.uiState.value.isRunning && phase == Phase.IDLE) {
                plog("Ad closed; auto-reconnecting VPN tunnel...")
                startSmartConnect()
            }
        }
    }

    private fun claimStartIoRewardBonus() {
        ensureDailyDeviceQuotaCurrent()
        dailyMaxBytes += STARTIO_REWARD_BONUS_BYTES
        freeSessionRemainingSec += REWARD_ADD_TIME_SEC
        prefs.edit()
            .putLong("daily_max_bytes_$deviceId", dailyMaxBytes)
            .putLong("free_session_rem_sec", freeSessionRemainingSec)
            .apply()
        val remBytes = (dailyMaxBytes - dailyUsedBytes).coerceAtLeast(0L)
        statusMessage = "+100 MB VIP Quota & +2h Added via Start.io! (${fmtBytes(remBytes)} left)"
        plog("Start.io Rewarded Video completed on real IP: +100 MB VIP Quota (New Daily Max: ${fmtBytes(dailyMaxBytes)})")
    }

    private fun showStartIoRewardedVideoForQuota() {
        lifecycleScope.launch(Dispatchers.Main) {
            val wasVpnRunning = pauseVpnForDirectIpAdIfNeeded()
            statusMessage = "Loading Start.io Video on direct IP (+100 MB)..."
            plog("Requesting Start.io Rewarded Video on direct user IP (App ID: $STARTIO_APP_ID)...")
            runCatching {
                val rewarded = StartAppAd(this@MainActivity)
                var rewardGranted = false
                rewarded.setVideoListener(VideoListener {
                    runOnUiThread {
                        if (!rewardGranted) {
                            rewardGranted = true
                            claimStartIoRewardBonus()
                        }
                    }
                })
                rewarded.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
                    override fun onReceiveAd(ad: Ad) {
                        rewarded.showAd(object : AdDisplayListener {
                            override fun adHidden(displayedAd: Ad?) {
                                resumeVpnAfterAdIfNeeded(wasVpnRunning)
                            }

                            override fun adDisplayed(displayedAd: Ad?) {
                                plog("Start.io Rewarded Video displayed on user's real IP")
                            }

                            override fun adClicked(displayedAd: Ad?) {}

                            override fun adNotDisplayed(displayedAd: Ad?) {
                                resumeVpnAfterAdIfNeeded(wasVpnRunning)
                            }
                        })
                    }

                    override fun onFailedToReceiveAd(ad: Ad?) {
                        // Fallback to AdMob Rewarded on direct user IP if Start.io video fill is unavailable
                        val admobRewarded = rewardedAd
                        if (admobRewarded != null) {
                            admobRewarded.fullScreenContentCallback = object : FullScreenContentCallback() {
                                override fun onAdDismissedFullScreenContent() {
                                    rewardedAd = null
                                    resumeVpnAfterAdIfNeeded(wasVpnRunning)
                                }

                                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                    rewardedAd = null
                                    resumeVpnAfterAdIfNeeded(wasVpnRunning)
                                }
                            }
                            admobRewarded.show(this@MainActivity, OnUserEarnedRewardListener {
                                claimStartIoRewardBonus()
                            })
                        } else {
                            loadRewardedAd()
                            statusMessage = "Start.io Ad not ready; try again shortly"
                            plog("Start.io Rewarded Ad failed to load on direct IP")
                            resumeVpnAfterAdIfNeeded(wasVpnRunning)
                        }
                    }
                })
            }.onFailure { e ->
                plog("Start.io Rewarded error: ${e.message}")
                resumeVpnAfterAdIfNeeded(wasVpnRunning)
            }
        }
    }

    private fun showInterstitialAdIfReady() {
        if (vipActive) return
        val now = System.currentTimeMillis()
        if (now - lastInterstitialShownMs < INTERSTITIAL_COOLDOWN_MS) {
            val waitSec = ((INTERSTITIAL_COOLDOWN_MS - (now - lastInterstitialShownMs)) / 1000L).coerceAtLeast(1L)
            plog("Interstitial skipped by 10m Frequency Cap (${waitSec}s remaining)")
            return
        }
        lifecycleScope.launch(Dispatchers.Main) {
            val wasVpnRunning = pauseVpnForDirectIpAdIfNeeded()
            val startAd = startIoInterstitialAd
            if (startAd != null && startAd.isReady) {
                lastInterstitialShownMs = now
                prefs.edit().putLong("last_interstitial_ms", now).apply()
                startIoInterstitialAd = null
                startAd.showAd(object : AdDisplayListener {
                    override fun adHidden(ad: Ad?) {
                        loadStartIoInterstitialAd()
                        resumeVpnAfterAdIfNeeded(wasVpnRunning)
                    }

                    override fun adDisplayed(ad: Ad?) {
                        plog("Start.io Interstitial displayed on user's real IP")
                    }

                    override fun adClicked(ad: Ad?) {}

                    override fun adNotDisplayed(ad: Ad?) {
                        loadStartIoInterstitialAd()
                        resumeVpnAfterAdIfNeeded(wasVpnRunning)
                    }
                })
                return@launch
            }
            val admobAd = interstitialAd
            if (admobAd != null) {
                lastInterstitialShownMs = now
                prefs.edit().putLong("last_interstitial_ms", now).apply()
                admobAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        interstitialAd = null
                        loadInterstitialAd()
                        resumeVpnAfterAdIfNeeded(wasVpnRunning)
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        interstitialAd = null
                        resumeVpnAfterAdIfNeeded(wasVpnRunning)
                    }
                }
                admobAd.show(this@MainActivity)
            } else {
                loadStartIoInterstitialAd()
                loadInterstitialAd()
                resumeVpnAfterAdIfNeeded(wasVpnRunning)
            }
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

    private fun todayDateKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(effectiveNowMs()))

    private fun ensureDailyDeviceQuotaCurrent() {
        val today = todayDateKey()
        val storedDay = prefs.getString("daily_quota_day_$deviceId", "") ?: ""
        if (storedDay != today || dailyQuotaDayKey != today) {
            dailyQuotaDayKey = today
            val recovered = VipLicenseManager.readTamperResistantDailyUsed(this, deviceId, today, 0L)
            dailyUsedBytes = recovered
            dailyMaxBytes = BASE_DAILY_FREE_BYTES
            prefs.edit()
                .putString("daily_quota_day_$deviceId", today)
                .putLong("daily_used_bytes_$deviceId", dailyUsedBytes)
                .putLong("daily_max_bytes_$deviceId", BASE_DAILY_FREE_BYTES)
                .apply()
            VipLicenseManager.writeTamperResistantDailyUsed(this, deviceId, today, dailyUsedBytes)
        } else {
            val recovered = VipLicenseManager.readTamperResistantDailyUsed(this, deviceId, today, dailyUsedBytes)
            if (recovered > dailyUsedBytes) {
                dailyUsedBytes = recovered
                prefs.edit().putLong("daily_used_bytes_$deviceId", dailyUsedBytes).apply()
            }
        }
    }

    private fun syncDailyDedicatedQuotaInBackground(deltaBytes: Long) {
        val today = todayDateKey()
        val curLabel = activeList.firstOrNull { it.guid == lastConnectedGuid }?.label.orEmpty()
        lifecycleScope.launch(Dispatchers.IO) {
            val res = VipLicenseManager.syncDailyQuotaAndTelemetry(
                apiBaseUrl = licenseServerUrl,
                deviceId = deviceId,
                userUuid = userUuid,
                dayKey = today,
                localUsedBytes = dailyUsedBytes,
                deltaBytes = deltaBytes,
                activeMode = serviceTierMode.name,
                connectedServerLabel = curLabel
            ) ?: return@launch
            withContext(Dispatchers.Main) {
                if (res.usedBytes > dailyUsedBytes) {
                    dailyUsedBytes = res.usedBytes
                    prefs.edit().putLong("daily_used_bytes_$deviceId", dailyUsedBytes).apply()
                    VipLicenseManager.writeTamperResistantDailyUsed(this@MainActivity, deviceId, today, dailyUsedBytes)
                }
            }
        }
    }

    private fun addTwoHoursToSession() {
        ensureDailyDeviceQuotaCurrent()
        freeSessionRemainingSec += REWARD_ADD_TIME_SEC
        prefs.edit()
            .putLong("free_session_rem_sec", freeSessionRemainingSec)
            .apply()
        val remData = (dailyMaxBytes - dailyUsedBytes).coerceAtLeast(0L)
        statusMessage = "+2h Session Time Added • ${fmtBytes(remData)} Dedicated left today"
        plog("Rewarded Video watched: +2h session time added")
    }

    private fun showRewardedAdForAddTime() {
        val ad = rewardedAd
        if (ad != null) {
            ad.show(this, OnUserEarnedRewardListener {
                addTwoHoursToSession()
            })
        } else {
            loadRewardedAd()
            statusMessage = "Ad is loading, please try again in a few seconds"
            plog("Rewarded ad not ready yet; requested reload")
        }
    }

    // ═════════════════════════ مدیریت اشتراک ۱ ماه ۵۰ گیگ (سرورمحور + ECDSA چرخانده‌شده) ═════════════════════════

    private fun getSubscriptionSources(mode: ServiceTierMode): List<String> {
        val custom = customVipSubUrl.trim()
        val baseList = PennyServerScanner.getSubscriptionSourcesForMode(mode)
        return (if (mode != ServiceTierMode.PUBLIC && custom.isNotEmpty() && !custom.contains(".pages.dev", ignoreCase = true)) {
            custom.split("\n", ",")
                .map { it.trim() }
                .filter {
                    it.startsWith("https://", ignoreCase = true) ||
                        it.startsWith("http://", ignoreCase = true)
                } + baseList
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
        loadBalanceEnabled = prefs.getBoolean("load_balance_enabled", true)
        killSwitchEnabled = prefs.getBoolean("kill_switch_enabled", true)
        val savedProf = prefs.getString("conn_profile", ConnectionProfile.SMART.name) ?: ConnectionProfile.SMART.name
        connectionProfile = runCatching { ConnectionProfile.valueOf(savedProf) }.getOrDefault(ConnectionProfile.SMART)
        val savedTier = prefs.getString("service_tier_mode", ServiceTierMode.DEDICATED.name) ?: ServiceTierMode.DEDICATED.name
        serviceTierMode = runCatching { ServiceTierMode.valueOf(savedTier) }.getOrDefault(ServiceTierMode.DEDICATED)
        ghostMessagingOnly = prefs.getBoolean("ghost_messaging_only", true)
        PennyServerScanner.syncGhostModeKernelRules(serviceTierMode, ghostMessagingOnly, splitTunnelLocal)
        userWantsConnected = prefs.getBoolean("user_connected", false)
        lastScanTime = prefs.getLong("scan_time", 0L)
        vipLastScanTime = prefs.getLong("vip_scan_time", 0L)
        ghostLastScanTime = prefs.getLong("ghost_scan_time", 0L)
        connectStartMs = prefs.getLong("connect_time", 0L)
        lastConnectedGuid = prefs.getString("last_guid", null)
        lastInterstitialShownMs = prefs.getLong("last_interstitial_ms", 0L)
        freeSessionRemainingSec = prefs.getLong("free_session_rem_sec", BASE_FREE_SESSION_SEC).coerceAtLeast(60L)

        val today = todayDateKey()
        val savedDay = prefs.getString("daily_quota_day_$deviceId", "") ?: ""
        if (savedDay == today) {
            dailyQuotaDayKey = today
            val prefUsed = prefs.getLong("daily_used_bytes_$deviceId", 0L).coerceAtLeast(0L)
            dailyUsedBytes = VipLicenseManager.readTamperResistantDailyUsed(this, deviceId, today, prefUsed)
            dailyMaxBytes = prefs.getLong("daily_max_bytes_$deviceId", BASE_DAILY_FREE_BYTES).coerceAtLeast(BASE_DAILY_FREE_BYTES)
        } else {
            dailyQuotaDayKey = today
            dailyUsedBytes = VipLicenseManager.readTamperResistantDailyUsed(this, deviceId, today, 0L)
            dailyMaxBytes = BASE_DAILY_FREE_BYTES
            prefs.edit()
                .putString("daily_quota_day_$deviceId", today)
                .putLong("daily_used_bytes_$deviceId", dailyUsedBytes)
                .putLong("daily_max_bytes_$deviceId", BASE_DAILY_FREE_BYTES)
                .apply()
        }

        vipActive = prefs.getBoolean("vip_active", false)
        PennySpeedLimiter.isVipUnrestricted = true
        useVipServers = prefs.getBoolean("use_vip_servers", true)
        vipCode = prefs.getString("vip_code", "") ?: ""
        vipSerial = prefs.getString("vip_serial", "") ?: ""
        vipExpireMs = prefs.getLong("vip_expire_ms", 0L)
        vipMaxBytes = prefs.getLong("vip_max_bytes", VipLicenseManager.VIP_MAX_BYTES)
        vipUsedBytes = prefs.getLong("vip_used_bytes", 0L)
        vipLastSeenMs = prefs.getLong("vip_last_seen_ms", 0L)
        effectiveNowMs()
        customVipSubUrl = prefs.getString("custom_vip_sub_url", PennyServerScanner.DEDICATED_VPS_SUB_URL)
            ?.takeIf { it.isNotBlank() && !it.contains(".pages.dev", ignoreCase = true) }
            ?: PennyServerScanner.DEDICATED_VPS_SUB_URL
        licenseServerUrl = prefs.getString("license_server_url", VipLicenseManager.DEFAULT_LICENSE_API_BASE)
            ?: VipLicenseManager.DEFAULT_LICENSE_API_BASE

        val lbSeed = if (loadBalanceEnabled) kotlin.math.abs(deviceId.hashCode()) else 0

        prefs.getString("list", null)?.let { json ->
            try {
                val arr = JSONArray(json)
                val out = mutableListOf<PennyServer>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val g = o.getString("g")
                    if (MmkvManager.decodeServerConfig(g) != null) {
                        out.add(PennyServer(g, o.optString("c", ""), o.optLong("m"), "", false))
                    }
                }
                bestList.clear()
                bestList.addAll(assignLabels(out, isVipList = false, loadBalanceSeed = lbSeed))
            } catch (e: Exception) {
                plog("Error loading Public cache: ${e.message}")
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
                        out.add(PennyServer(g, o.optString("c", ""), o.optLong("m"), "", true))
                    }
                }
                vipBestList.clear()
                vipBestList.addAll(assignLabels(out, isVipList = true, loadBalanceSeed = lbSeed))
            } catch (e: Exception) {
                plog("Error loading VIP Mode cache: ${e.message}")
            }
        }

        prefs.getString("ghost_list", null)?.let { json ->
            try {
                val arr = JSONArray(json)
                val out = mutableListOf<PennyServer>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val g = o.getString("g")
                    if (MmkvManager.decodeServerConfig(g) != null) {
                        out.add(PennyServer(g, o.optString("c", ""), o.optLong("m"), "", true))
                    }
                }
                ghostBestList.clear()
                ghostBestList.addAll(assignLabels(out, isVipList = true, loadBalanceSeed = lbSeed))
            } catch (e: Exception) {
                plog("Error loading Ghost cache: ${e.message}")
            }
        }
    }

    private fun saveList(list: List<PennyServer>, scanTime: Long, mode: ServiceTierMode) {
        try {
            val arr = JSONArray()
            list.forEach { arr.put(JSONObject().put("g", it.guid).put("c", it.codeTag).put("m", it.ms)) }
            when (mode) {
                ServiceTierMode.PUBLIC ->
                    prefs.edit().putString("list", arr.toString()).putLong("scan_time", scanTime).apply()
                ServiceTierMode.DEDICATED ->
                    prefs.edit().putString("vip_list", arr.toString()).putLong("vip_scan_time", scanTime).apply()
                ServiceTierMode.GHOST ->
                    prefs.edit().putString("ghost_list", arr.toString()).putLong("ghost_scan_time", scanTime).apply()
            }
        } catch (e: Exception) {
            plog("Error saving cache: ${e.message}")
        }
    }

    private fun plog(msg: String) {
        val sanitized = msg
            .replace(PennyServerScanner.DED_NL_HOST, "[DED-NL-NODE]", ignoreCase = true)
            .replace(PennyServerScanner.DED_PL_HOST, "[DED-PL-NODE]", ignoreCase = true)
            .replace(PennyServerScanner.VPS1_HOST, "[DED-DE-NODE]", ignoreCase = true)
            .replace(userUuid, "[PROTECTED-UUID]", ignoreCase = true)
            .replace(deviceId, "[PROTECTED-HWID]", ignoreCase = true)
            .replace(Regex("""\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(:\d+)?\b"""), "[PROTECTED-IP]")
        Log.d(TAG, sanitized)
        val line = "${timeFmt.format(Date())}  $sanitized"
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

    private fun copyUuidToClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("PennyVPN User UUID", userUuid))
        statusMessage = "Hardware User UUID copied to clipboard"
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
                    downSpeed = rawDown
                    upSpeed = rawUp
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
                    downSpeed = rawDown
                    upSpeed = rawUp
                    totalDown += d
                    totalUp += u
                }
                lastT = now
                elapsedSec = (System.currentTimeMillis() - connectStartMs) / 1000L

                if (!vipActive && (serviceTierMode == ServiceTierMode.DEDICATED || serviceTierMode == ServiceTierMode.GHOST)) {
                    ensureDailyDeviceQuotaCurrent()
                    if (stepDeltaBytes > 0L) {
                        dailyUsedBytes += stepDeltaBytes
                        if (tickCount % 5 == 0) {
                            prefs.edit().putLong("daily_used_bytes_$deviceId", dailyUsedBytes).apply()
                            VipLicenseManager.writeTamperResistantDailyUsed(
                                this@MainActivity,
                                deviceId,
                                dailyQuotaDayKey,
                                dailyUsedBytes
                            )
                        }
                        if (tickCount % 15 == 0) {
                            syncDailyDedicatedQuotaInBackground(stepDeltaBytes)
                        }
                    }
                    if (dailyUsedBytes >= dailyMaxBytes) {
                        prefs.edit().putLong("daily_used_bytes_$deviceId", dailyUsedBytes).apply()
                        VipLicenseManager.writeTamperResistantDailyUsed(
                            this@MainActivity,
                            deviceId,
                            dailyQuotaDayKey,
                            dailyUsedBytes
                        )
                        plog("Daily 1 GB quota (${fmtBytes(dailyMaxBytes)}) reached; switching to Public Mode")
                        serviceTierMode = ServiceTierMode.PUBLIC
                        prefs.edit().putString("service_tier_mode", ServiceTierMode.PUBLIC.name).apply()
                        PennyServerScanner.syncGhostModeKernelRules(serviceTierMode, ghostMessagingOnly, splitTunnelLocal)
                        statusMessage = "1 GB Daily Quota Reached • Switched to Public Mode"
                        LauncherManager.stopService(this@MainActivity)
                        continue
                    }

                    if (freeSessionRemainingSec > 0L) {
                        freeSessionRemainingSec -= 1L
                        if (tickCount % 10 == 0) {
                            prefs.edit().putLong("free_session_rem_sec", freeSessionRemainingSec).apply()
                        }
                    } else {
                        plog("1-hour session timer ended; disconnecting")
                        statusMessage = "Session time ended • Tap Connect or Watch Ad for +2h"
                        LauncherManager.stopService(this@MainActivity)
                    }
                } else if (!vipActive && stepDeltaBytes > 0L && tickCount % 25 == 0) {
                    syncDailyDedicatedQuotaInBackground(0L)
                }

                if (vipActive && stepDeltaBytes > 0L) {
                    vipUsedBytes += stepDeltaBytes
                    pendingUnsyncedBytes += stepDeltaBytes
                    if (tickCount % 5 == 0) {
                        prefs.edit().putLong("vip_used_bytes", vipUsedBytes).apply()
                    }
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

        val activeConnectedGuid = if (isRunning) {
            uiState.selectedGuid?.takeIf { it.isNotBlank() } ?: lastConnectedGuid
        } else {
            current?.guid ?: lastConnectedGuid
        }

        val currentCountryGeo = remember(current?.codeTag, activeConnectedGuid, serviceTierMode, isRunning) {
            val tagFromCurrent = current?.codeTag?.takeIf { it.isNotBlank() && it != "PUB" }
            val tagFromGuid = activeConnectedGuid?.takeIf { it.isNotBlank() }
                ?.let { detectServerOriginTag(it) }
                ?.takeIf { it.isNotBlank() && it != "PUB" }
            val effectiveTag = tagFromCurrent
                ?: tagFromGuid
                ?: current?.codeTag?.takeIf { it.isNotBlank() }
                ?: when (serviceTierMode) {
                    ServiceTierMode.GHOST -> "GHOST"
                    ServiceTierMode.DEDICATED -> "DED-NL"
                    ServiceTierMode.PUBLIC -> "PUB-NL"
                }
            getCountryGeoInfo(effectiveTag)
        }

        val isConnectedMode = mode == UiMode.CONNECTED
        val bgTopColor by animateColorAsState(
            targetValue = if (isConnectedMode) Color(0xFF1B1C23) else Color(0xFF17181E),
            animationSpec = tween(900),
            label = "bgTopColor"
        )
        val bgMidColor by animateColorAsState(
            targetValue = if (isConnectedMode) Color(0xFF111217) else Color(0xFF101116),
            animationSpec = tween(900),
            label = "bgMidColor"
        )
        val bgBottomColor by animateColorAsState(
            targetValue = if (isConnectedMode) Color(0xFF090A0D) else Color(0xFF08090C),
            animationSpec = tween(900),
            label = "bgBottomColor"
        )
        val ambientGlowAlpha by animateFloatAsState(
            targetValue = if (isConnectedMode) 0.07f else 0.0f,
            animationSpec = tween(950),
            label = "ambientGlowAlpha"
        )

        val dailyRemBytes = (dailyMaxBytes - dailyUsedBytes).coerceAtLeast(0L)
        val dailyRemText = when (serviceTierMode) {
            ServiceTierMode.DEDICATED ->
                if (vipActive) "VIP 50 GB Active"
                else "${fmtBytes(dailyRemBytes)} / ${fmtBytes(dailyMaxBytes)} left"
            ServiceTierMode.GHOST ->
                if (ghostMessagingOnly) "Chat-Only Lock (42 KB/s)"
                else "Ghost Anti-DPI"
            ServiceTierMode.PUBLIC -> "Free Unlimited"
        }

        val onGhostToggleChanged: (Boolean) -> Unit = { enabled ->
            ghostMessagingOnly = enabled
            prefs.edit().putBoolean("ghost_messaging_only", enabled).apply()
            PennyServerScanner.syncGhostModeKernelRules(serviceTierMode, enabled, splitTunnelLocal)
            statusMessage = if (enabled) {
                "Ghost Messaging-Only Lock Enabled (42 KB/s Cap • Video Blocked)"
            } else {
                "Ghost Messaging-Only Lock Disabled"
            }
            plog("Ghost Messaging-Only restriction: $enabled")
            if (isRunning && serviceTierMode == ServiceTierMode.GHOST) {
                safeRestartVpnOnMainThread()
            }
        }

        val onModeSelected: (ServiceTierMode) -> Unit = { newMode ->
            if (serviceTierMode != newMode) {
                serviceTierMode = newMode
                prefs.edit().putString("service_tier_mode", newMode.name).apply()
                if (newMode == ServiceTierMode.GHOST && !ghostMessagingOnly) {
                    ghostMessagingOnly = true
                    prefs.edit().putBoolean("ghost_messaging_only", true).apply()
                }
                PennyServerScanner.syncGhostModeKernelRules(newMode, ghostMessagingOnly, splitTunnelLocal)
                when (newMode) {
                    ServiceTierMode.GHOST ->
                        PennyServerScanner.applyBpbCoreOptimizations(fragLength = "5-12", fragInterval = "1-2")
                    ServiceTierMode.DEDICATED ->
                        PennyServerScanner.detectCarrierAndTuneFragment(this@MainActivity, ::plog)
                    ServiceTierMode.PUBLIC ->
                        PennyServerScanner.applyBpbCoreOptimizations(fragLength = "100-200", fragInterval = "1")
                }
                statusMessage = "${newMode.title} selected (${newMode.badge})"
                plog("Switched service mode to ${newMode.title}")
                if (isRunning) {
                    val targetServer = activeList.firstOrNull()
                    if (targetServer != null) {
                        connectToEntry(targetServer)
                    } else {
                        safeRestartVpnOnMainThread()
                    }
                }
            }
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                bgTopColor,
                                bgMidColor,
                                bgBottomColor
                            )
                        )
                    )
            ) {
                if (ambientGlowAlpha > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFFE6CFAF).copy(alpha = ambientGlowAlpha),
                                        Color(0xFFD8DCE6).copy(alpha = ambientGlowAlpha * 0.45f),
                                        Color.Transparent
                                    ),
                                    radius = 780f
                                )
                            )
                    )
                }

                Image(
                    painter = painterResource(id = R.drawable.penny_cat_logo),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(240.dp)
                        .align(Alignment.Center)
                        .alpha(if (isConnectedMode) 0.04f else 0.025f)
                )

                // ═══════════════════════ رابط کاربری موبایل (بدون اسکرول در Home، Doctor و Profile، اسکرول فقط در لیست سرورها) ═══════════════════════
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        when (currentNavTab) {
                            BottomNavTab.HOME -> {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    NeonBrandHeader(
                                        mode = mode,
                                        lang = appLang,
                                        isVipMode = serviceTierMode == ServiceTierMode.DEDICATED || vipActive,
                                        onLogoClick = { showSplash = true }
                                    )

                                    ServiceTierSelectorBar(
                                        selectedMode = serviceTierMode,
                                        dailyRemText = "${fmtBytes(dailyRemBytes)} left",
                                        isVip = vipActive,
                                        isConnected = mode == UiMode.CONNECTED,
                                        ghostMessagingOnly = ghostMessagingOnly,
                                        onGhostMessagingOnlyChange = onGhostToggleChanged,
                                        onSelectMode = onModeSelected
                                    )

                                    PowerButton(
                                        mode = mode,
                                        progress = scanProgress,
                                        lang = appLang,
                                        isVipMode = serviceTierMode == ServiceTierMode.DEDICATED || vipActive,
                                        countryGeo = currentCountryGeo,
                                        onClick = { onPowerClick() }
                                    )

                                    PulsingStatusText(
                                        mode = mode,
                                        statusMessage = statusMessage,
                                        lang = appLang,
                                        isVip = serviceTierMode == ServiceTierMode.DEDICATED || vipActive,
                                        dailyRemainingText = dailyRemText,
                                        countryGeo = currentCountryGeo,
                                        downSpeed = downSpeed,
                                        upSpeed = upSpeed
                                    )

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        ModernServerCard(
                                            server = current,
                                            isConnected = mode == UiMode.CONNECTED,
                                            isVipMode = serviceTierMode != ServiceTierMode.PUBLIC,
                                            lang = appLang,
                                            enabled = !busy,
                                            onClick = { currentNavTab = BottomNavTab.SERVERS }
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { startScanOnly() },
                                                enabled = !busy,
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(40.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (serviceTierMode != ServiceTierMode.PUBLIC) {
                                                        GlassDiamondIcon(size = 10.dp)
                                                        Spacer(Modifier.width(4.dp))
                                                    }
                                                    Text(
                                                        text = "Scan ${serviceTierMode.shortTitle}",
                                                        fontSize = 10.5.sp,
                                                        color = Color(0xFFE8ECF4),
                                                        maxLines = 1
                                                    )
                                                }
                                            }

                                            if (!vipActive) {
                                                OutlinedButton(
                                                    onClick = { showStartIoRewardedVideoForQuota() },
                                                    enabled = !busy,
                                                    shape = RoundedCornerShape(12.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                    modifier = Modifier
                                                        .weight(1.05f)
                                                        .height(40.dp)
                                                ) {
                                                    Text(
                                                        text = "🎬 +100 MB Free",
                                                        fontSize = 10.5.sp,
                                                        color = Color(0xFFE6CFAF),
                                                        fontWeight = FontWeight.ExtraBold,
                                                        maxLines = 1
                                                    )
                                                }
                                            }

                                            Button(
                                                onClick = { switchNext() },
                                                enabled = !busy && isRunning && currentList.size > 1,
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(40.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (serviceTierMode != ServiceTierMode.PUBLIC) Color(0xFFE6CFAF) else Color(0xFF36353E)
                                                )
                                            ) {
                                                Text(
                                                    text = Strings.nextServerBtn(appLang),
                                                    fontSize = 10.5.sp,
                                                    color = if (serviceTierMode != ServiceTierMode.PUBLIC) Color(0xFF15161B) else Color(0xFFF5EFE6),
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            BottomNavTab.SERVERS -> {
                                ServersTabPage(
                                    selectedMode = serviceTierMode,
                                    onSelectMode = onModeSelected,
                                    servers = currentList,
                                    selectedGuid = uiState.selectedGuid,
                                    isRunning = isRunning,
                                    busy = busy,
                                    scanProgress = scanProgress,
                                    dailyUsedBytes = dailyUsedBytes,
                                    dailyMaxBytes = dailyMaxBytes,
                                    vipActive = vipActive,
                                    ghostMessagingOnly = ghostMessagingOnly,
                                    onGhostMessagingOnlyChange = onGhostToggleChanged,
                                    onScanClick = { startScanOnly() },
                                    onServerClick = { entry -> connectToEntry(entry) }
                                )
                            }

                            BottomNavTab.DOCTOR -> {
                                NetworkDoctorTabPage(
                                    lang = appLang,
                                    isConnected = mode == UiMode.CONNECTED,
                                    busy = busy,
                                    serviceTierMode = serviceTierMode,
                                    currentServer = current,
                                    serverCount = currentList.size,
                                    bestPingMs = current?.ms ?: 0L,
                                    cleanDnsShield = cleanDnsShield,
                                    onCleanDnsShieldChange = {
                                        cleanDnsShield = it
                                        prefs.edit().putBoolean("clean_dns_shield", it).apply()
                                        val dnsServer = if (it) "1.1.1.2" else "1.1.1.1"
                                        MmkvManager.encodeSettings(AppConfig.PREF_REMOTE_DNS, dnsServer)
                                        statusMessage = "Encrypted DNS set to $dnsServer"
                                        plog("Network Doctor: Clean DNS Shield set to $dnsServer")
                                    },
                                    splitTunnelLocal = splitTunnelLocal,
                                    onSplitTunnelLocalChange = {
                                        splitTunnelLocal = it
                                        prefs.edit().putBoolean("split_tunnel_local", it).apply()
                                        MmkvManager.encodeSettings(AppConfig.PREF_VPN_BYPASS_LAN, if (it) "1" else "0")
                                        PennyServerScanner.syncGhostModeKernelRules(serviceTierMode, ghostMessagingOnly, it)
                                        statusMessage = if (it) "Domestic (.ir) & LAN Direct Bypass Enabled" else "All Traffic Routed in Tunnel"
                                        plog("Network Doctor: Smart Split Tunneling set to $it")
                                    },
                                    ghostMessagingOnly = ghostMessagingOnly,
                                    adUnitId = ADMOB_BANNER_UNIT_ID,
                                    showAds = !vipActive,
                                    onSponsorClick = { showStartIoRewardedVideoForQuota() },
                                    onAutoTuneClick = {
                                        val tuned = PennyServerScanner.detectCarrierAndTuneFragment(this@MainActivity, ::plog)
                                        statusMessage = "ISP Auto-Tuned: $tuned"
                                        if (isRunning) {
                                            safeRestartVpnOnMainThread()
                                        }
                                    },
                                    onScanNodesClick = { startScanOnly() },
                                    onOpenLogsClick = { showLog = true }
                                )
                            }

                            BottomNavTab.PROFILE -> {
                                ProfileTabPage(
                                    userUuid = userUuid,
                                    deviceId = deviceId,
                                    serviceTierMode = serviceTierMode,
                                    dailyUsedBytes = dailyUsedBytes,
                                    dailyMaxBytes = dailyMaxBytes,
                                    vipActive = vipActive,
                                    vipExpireMs = vipExpireMs,
                                    vipUsedBytes = vipUsedBytes,
                                    vipMaxBytes = vipMaxBytes,
                                    ghostMessagingOnly = ghostMessagingOnly,
                                    onGhostMessagingOnlyChange = onGhostToggleChanged,
                                    autoSwitch = autoSwitch,
                                    onAutoSwitchChange = {
                                        autoSwitch = it
                                        prefs.edit().putBoolean("auto_switch", it).apply()
                                    },
                                    autoReconnect = autoReconnect,
                                    onAutoReconnectChange = {
                                        autoReconnect = it
                                        prefs.edit().putBoolean("auto_reconnect", it).apply()
                                    },
                                    splitTunnelLocal = splitTunnelLocal,
                                    onSplitTunnelChange = {
                                        splitTunnelLocal = it
                                        prefs.edit().putBoolean("split_tunnel_local", it).apply()
                                        MmkvManager.encodeSettings(AppConfig.PREF_VPN_BYPASS_LAN, if (it) "1" else "0")
                                    },
                                    cleanDnsShield = cleanDnsShield,
                                    onCleanDnsShieldChange = {
                                        cleanDnsShield = it
                                        prefs.edit().putBoolean("clean_dns_shield", it).apply()
                                        val dnsServer = if (it) "1.1.1.2" else "1.1.1.1"
                                        MmkvManager.encodeSettings(AppConfig.PREF_REMOTE_DNS, dnsServer)
                                        statusMessage = "Encrypted DNS set to $dnsServer"
                                    },
                                    loadBalanceEnabled = loadBalanceEnabled,
                                    onLoadBalanceChange = {
                                        loadBalanceEnabled = it
                                        prefs.edit().putBoolean("load_balance_enabled", it).apply()
                                        val seed = if (it) kotlin.math.abs(deviceId.hashCode()) else 0
                                        val target = activeList.toList()
                                        if (target.isNotEmpty()) {
                                            val rebalanced = assignLabels(
                                                target,
                                                isVipList = serviceTierMode != ServiceTierMode.PUBLIC,
                                                loadBalanceSeed = seed
                                            )
                                            activeList.clear()
                                            activeList.addAll(rebalanced)
                                        }
                                        statusMessage = if (it) "Smart Server Load Balancing Enabled" else "Strict Latency Sorting Enabled"
                                    },
                                    killSwitchEnabled = killSwitchEnabled,
                                    onKillSwitchChange = {
                                        killSwitchEnabled = it
                                        prefs.edit().putBoolean("kill_switch_enabled", it).apply()
                                        statusMessage = if (it) "VPN Kill Switch & Leak Guard Active" else "VPN Kill Switch Disabled"
                                    },
                                    onCopyUuid = { copyUuidToClipboard() },
                                    onOpenVipDialog = { showVipDialog = true },
                                    onWatchSponsorAd = { showStartIoRewardedVideoForQuota() },
                                    onOpenLogs = { showLog = true },
                                    onOpenUpdate = { showUpdateDialog = true }
                                )
                            }
                        }
                    }

                    PennyBottomNavBar(
                        selectedTab = currentNavTab,
                        serverCount = currentList.size,
                        onSelectTab = { currentNavTab = it }
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
                        color = Color(0xFF23252D),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, Color(0xFF3A3C46), RoundedCornerShape(14.dp))
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
                                UpdateArrowIcon(size = 15.dp, color = Color(0xFFE8ECF4))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "App & Server Update (v${PennyServerScanner.APP_VERSION_NAME})",
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
                                            color = if (active) Color(0xFF23252E) else Color(0xFFECEEF4),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.Monospace,
                                            style = TextStyle(
                                                shadow = Shadow(
                                                    color = Color(0xFFD8DCE6).copy(alpha = if (active) 0.25f else 0.70f),
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
                        itemsIndexed(listToShow, key = { idx, item -> "${item.guid}_$idx" }) { index, s ->
                            StaggeredServerItem(index = index) {
                                val active = isRunning && s.guid == selectedGuid
                                val isOwn = isDedicatedServerTag(s.codeTag)
                                val tagText = s.codeTag.ifEmpty { "CF-1" }
                                Surface(
                                    color = if (active) Color(0xFF332C24) else if (isOwn) Color(0xFF24211D) else Color(0xFF202128),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(
                                            1.dp,
                                            when {
                                                active -> Color(0xFFE6CFAF)
                                                isOwn -> Color(0xFFE6CFAF).copy(alpha = 0.55f)
                                                else -> Color(0xFF3A3942)
                                            },
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
                                            .padding(horizontal = 11.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isOwn) Color(0xFF2F281F) else Color(0xFF17181D))
                                                .border(
                                                    1.dp,
                                                    if (isOwn) Color(0xFFE6CFAF).copy(alpha = 0.8f) else Color(0xFF3E404A),
                                                    RoundedCornerShape(8.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = tagText,
                                                color = if (isOwn) Color(0xFFF5E6D3) else Color(0xFFB8BDCB),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        Spacer(Modifier.width(9.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                Text(
                                                    text = s.label,
                                                    color = Color(0xFFF2F4F8),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.5.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(
                                                            if (isOwn) Color(0xFFE6CFAF).copy(alpha = 0.22f)
                                                            else Color(0xFF323542)
                                                        )
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = if (isOwn) "VIP MODE" else "PUBLIC",
                                                        color = if (isOwn) Color(0xFFF5E6D3) else Color(0xFFB4B9C7),
                                                        fontSize = 7.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                            Text(
                                                text = if (isOwn) "Balanced VIP Node ($tagText)" else "Public Community Server",
                                                color = if (isOwn) Color(0xFFD8C7B0) else Color(0xFF8E929E),
                                                fontSize = 8.5.sp,
                                                maxLines = 1
                                            )
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            ShinyGreenPingBadge(
                                                ms = s.ms,
                                                showDiamond = s.isVipServer
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
                    releaseNotes = ""
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

        var diagnosing by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            containerColor = Color(0xFF191A20),
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
                    UpdateArrowIcon(size = 17.dp, color = Color(0xFFE2E5EE))
                    Text(
                        text = "Update & Network Doctor",
                        color = Color(0xFFF2F4F8),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.5.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = Color(0xFF23252D),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF3E404C), RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Version v${updateInfo.currentVersion}",
                                color = Color(0xFFF2F4F8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = if (checking) "CHECKING..." else if (updateInfo.hasUpdate) "NEW: v${updateInfo.latestVersion}" else "UP TO DATE",
                                color = Color(0xFFE6CFAF),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isNormal = bpbFragmentMode == "NORMAL"
                        OutlinedButton(
                            onClick = {
                                bpbFragmentMode = "NORMAL"
                                prefs.edit().putString("bpb_frag_mode", "NORMAL").apply()
                                PennyServerScanner.applyBpbCoreOptimizations(fragLength = "100-200", fragInterval = "1")
                                statusNote = "Fragment: Normal (100-200B)"
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Normal Mode",
                                fontSize = 11.sp,
                                color = if (isNormal) Color(0xFFF2F4F8) else Color(0xFF8E929E),
                                fontWeight = if (isNormal) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                bpbFragmentMode = "SMART"
                                prefs.edit().putString("bpb_frag_mode", "SMART").apply()
                                PennyServerScanner.applyBpbCoreOptimizations(fragLength = "10-30", fragInterval = "1")
                                statusNote = "Fragment: Deep Smart (10-30B)"
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Smart Mode",
                                fontSize = 11.sp,
                                color = if (!isNormal) Color(0xFFF2F4F8) else Color(0xFF8E929E),
                                fontWeight = if (!isNormal) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (diagnosing) return@Button
                            diagnosing = true
                            statusNote = "Scanning Clean IPs & ISP DPI..."
                            lifecycleScope.launch {
                                val report = PennyServerScanner.runNetworkDoctor(this@MainActivity, ::plog)
                                statusNote = report
                                diagnosing = false
                            }
                        },
                        enabled = !diagnosing && phase == Phase.IDLE,
                        shape = RoundedCornerShape(11.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2E3A)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFE6CFAF).copy(alpha = 0.55f), RoundedCornerShape(11.dp))
                    ) {
                        Text(
                            text = if (diagnosing) "Running Network Doctor..." else "Network Doctor & Clean IP Scan",
                            color = Color(0xFFE6CFAF),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.5.sp
                        )
                    }

                    Button(
                        onClick = {
                            showUpdateDialog = false
                            startScanOnly()
                        },
                        enabled = phase == Phase.IDLE,
                        shape = RoundedCornerShape(11.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF343742)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFD8DCE6).copy(alpha = 0.45f), RoundedCornerShape(11.dp))
                    ) {
                        Text(
                            text = "Update Servers",
                            color = Color(0xFFF2F4F8),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.downloadUrl))
                                startActivity(intent)
                            }
                        },
                        shape = RoundedCornerShape(11.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Download Latest APK",
                            color = Color(0xFFD8DCE6),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    statusNote?.let { note ->
                        Text(
                            text = note,
                            color = Color(0xFFE6CFAF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
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
        if (!vipActive && serviceTierMode == ServiceTierMode.DEDICATED) {
            ensureDailyDeviceQuotaCurrent()
            if (dailyUsedBytes >= dailyMaxBytes) {
                serviceTierMode = ServiceTierMode.PUBLIC
                prefs.edit().putString("service_tier_mode", ServiceTierMode.PUBLIC.name).apply()
                statusMessage = "Dedicated 1 GB Daily Limit Reached • Connecting via Free Public Mode..."
                plog("Dedicated 1 GB daily limit reached (${fmtBytes(dailyUsedBytes)} / ${fmtBytes(dailyMaxBytes)}); auto-falling back to Public Mode")
            }
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
        workJob?.cancel()
        userWantsConnected = false
        phase = Phase.IDLE
        scanProgress = 0f
        prefs.edit().putBoolean("user_connected", false).remove("connect_time").apply()
        runCatching { PennySpeedLimiter.stop() }
        runCatching { LauncherManager.stopService(this) }
        statusMessage = "Disconnected"
        connectStartMs = 0L
        plog("Disconnected by user")
        // پس از قطع کامل VPN و بازگشت به آی‌پی واقعی کاربر، تبلیغ بینابینی بررسی/پیش‌بارگذاری می‌شود
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
                        if (!vipActive && freeSessionRemainingSec < BASE_FREE_SESSION_SEC) {
                            freeSessionRemainingSec = BASE_FREE_SESSION_SEC
                            prefs.edit().putLong("free_session_rem_sec", freeSessionRemainingSec).apply()
                        }
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

    /**
     * ری‌استارت امن سرویس VPN روی ترد اصلی (Dispatchers.Main) برای جلوگیری کامل از کرش
     * NullPointerException: Can't toast on a thread that has not called Looper.prepare()
     * که در LauncherManager.restartService در CoreServiceManager رخ می‌داد.
     */
    private fun safeRestartVpnOnMainThread() {
        lifecycleScope.launch(Dispatchers.Main) {
            runCatching {
                if (mainViewModel.uiState.value.isRunning) {
                    LauncherManager.stopService(this@MainActivity)
                    delay(650L)
                }
                if (SettingsManager.isVpnMode() && VpnService.prepare(this@MainActivity) != null) {
                    return@runCatching
                }
                LauncherManager.startService(this@MainActivity)
            }.onFailure { e ->
                plog("Safe restart warning: ${e.message}")
            }
        }
    }

    private fun switchNext() {
        workJob?.cancel()
        phase = Phase.CONNECTING
        statusMessage = "Switching to next balanced server..."
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
                        "Switched to ${res.label}"
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
        if (entry.guid.isBlank()) return
        val hasVpnPerm = !SettingsManager.isVpnMode() || VpnService.prepare(this) == null
        if (!hasVpnPerm) {
            MmkvManager.setSelectServer(entry.guid)
            mainViewModel.updateSelectedGuid(entry.guid)
            val intent = VpnService.prepare(this)
            if (intent != null) {
                requestVpnPermission.launch(intent)
            }
            return
        }
        workJob?.cancel()
        phase = Phase.CONNECTING
        statusMessage = "Switching to ${entry.label}..."
        workJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ping = connectAndVerify(entry.guid)
                if (ping > 0) {
                    markConnected(entry.guid, ping)
                    ui {
                        statusMessage = "Connected • ${entry.label}"
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
                plog("Switch error: ${e.message}")
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

    private var lastHealthCheckRxBytes: Long = 0L

    private suspend fun healthCheckAndSwitch() {
        // حداقل ۳۵ ثانیه پس از اتصال جدید به تونل فرصت پایداری بدهیم و اگر ترافیک زنده در جریان است قطع نکنیم
        if (connectStartMs > 0L && System.currentTimeMillis() - connectStartMs < 35_000L) return
        val currentRx = TrafficStats.getUidRxBytes(Process.myUid())
        if (currentRx > 0L) {
            val deltaRx = currentRx - lastHealthCheckRxBytes
            lastHealthCheckRxBytes = currentRx
            if (deltaRx > 16_384L) return
        }

        var ok = false
        for (i in 0 until 3) {
            if (PennyServerScanner.testThroughLocalProxy(
                    PennyServerScanner.TEST_URLS[i % PennyServerScanner.TEST_URLS.size],
                    socksPort,
                    ::plog
                ) > 0
            ) {
                ok = true
                break
            }
            delay(2500)
        }
        if (ok) return

        val current = mainViewModel.uiState.value.selectedGuid
        if (current != null) {
            val nativePing = withContext(Dispatchers.IO) {
                runCatching {
                    CoreNativeManager.initCoreEnv(this@MainActivity)
                    val cfg = CoreConfigManager.getV2rayConfig4Speedtest(this@MainActivity, current)
                    if (cfg.status) CoreNativeManager.measureOutboundDelay(cfg.content, SettingsManager.getDelayTestUrl()) else -1L
                }.getOrDefault(-1L)
            }
            if (nativePing > 0L) return
        }

        plog("Active tunnel unresponsive; auto-switching...")
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
        val currentMode = serviceTierMode
        val sources = getSubscriptionSources(currentMode)

        ui {
            phase = Phase.SCANNING
            scanProgress = 0.05f
            statusMessage = "Scanning ${currentMode.title} servers..."
        }
        withContext(Dispatchers.IO) {
            runCatching { CoreNativeManager.initCoreEnv(this@MainActivity) }
        }
        when (currentMode) {
            ServiceTierMode.GHOST ->
                PennyServerScanner.applyBpbCoreOptimizations(fragLength = "5-12", fragInterval = "1-2")
            ServiceTierMode.DEDICATED ->
                PennyServerScanner.detectCarrierAndTuneFragment(this@MainActivity, ::plog)
            ServiceTierMode.PUBLIC ->
                PennyServerScanner.applyBpbCoreOptimizations(fragLength = "100-200", fragInterval = "1")
        }
        plog("Scan started (mode=${currentMode.name}, sources=${sources.size})")

        val downloadedContents = coroutineScope {
            sources.map { url ->
                async(Dispatchers.IO) {
                    PennyServerScanner.fetchSignedListWithFallback(url, ::plog)
                }
            }.awaitAll().filter { it.isNotEmpty() }
        }
        val builtinForMode = when (currentMode) {
            ServiceTierMode.GHOST -> PennyServerScanner.getGhostModeConfigs()
            ServiceTierMode.DEDICATED -> PennyServerScanner.getBuiltinBpbFallbackConfigs()
            ServiceTierMode.PUBLIC -> ""
        }
        val contents = buildList {
            if (builtinForMode.isNotEmpty()) add(builtinForMode)
            addAll(downloadedContents)
        }

        ui {
            scanProgress = 0.15f
            statusMessage = "Importing ${currentMode.shortTitle} nodes..."
        }
        runCatching { MmkvManager.removeServerViaSubid(PennyServerScanner.MANAGED_SUB_ID) }
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
        val newlyImported = allAfter.filter { it !in before }
        val existingDedicated = allAfter.filter { isDedicatedServerTag(detectServerOriginTag(it)) }
        val targetGuids = (existingDedicated + newlyImported).distinct().ifEmpty { allAfter }
        val cleanPool = PennyServerScanner.CLEAN_CF_ENDPOINTS
        targetGuids.forEachIndexed { idx, g ->
            try {
                val cfg = MmkvManager.decodeServerConfig(g)
                if (cfg != null) {
                    if (cfg.server?.endsWith(".pages.dev", ignoreCase = true) == true ||
                        cfg.server?.endsWith(".workers.dev", ignoreCase = true) == true ||
                        cfg.server.equals("www.speedtest.net", ignoreCase = true)
                    ) {
                        cfg.server = cleanPool[idx % cleanPool.size]
                    }
                    if (cfg.finalMask?.contains("maxSplit") == true) {
                        cfg.finalMask = PennyServerScanner.BPB_FINALMASK_NORMAL_JSON
                    }
                    val originTag = detectServerOriginTag(g)
                    // پاک‌سازی کامل نام سرور با حفظ کد کشور تا نقشه کشور مقصد روی کره زمین دقیق نمایش داده شود
                    cfg.remarks = when (originTag) {
                        "DED-NL" -> "PennyVPN DED-NL"
                        "DED-PL" -> "PennyVPN DED-PL"
                        "GHOST" -> "PennyVPN GHOST"
                        "VPS-1" -> "PennyVPN OWN-VPS1"
                        "CF-1" -> "PennyVPN OWN-CF1"
                        "CF-2" -> "PennyVPN OWN-CF2"
                        "BPB" -> "PennyVPN OWN-BPB"
                        else -> "PennyVPN $originTag"
                    }
                    MmkvManager.encodeServerConfig(g, cfg)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sanitize remarks warning for $g: ${e.message}")
            }
        }
        val newGuids = targetGuids
        if (newGuids.isEmpty()) {
            ui { statusMessage = "No valid configs found!" }
            return false
        }

        val dedNlPl = newGuids.filter {
            val t = detectServerOriginTag(it)
            t == "DED-NL" || t == "DED-PL"
        }
        val ghostCandidates = newGuids.filter { detectServerOriginTag(it) == "GHOST" }
        val vps1Candidates = newGuids.filter { detectServerOriginTag(it) == "VPS-1" }
        val pubCandidates = newGuids.filter { !isDedicatedServerTag(detectServerOriginTag(it)) }.shuffled()

        val prioritizedSample = when (currentMode) {
            ServiceTierMode.PUBLIC -> pubCandidates.take(PennyServerScanner.MAX_TCP_CANDIDATES)
            ServiceTierMode.GHOST -> (ghostCandidates + dedNlPl + vps1Candidates).distinct().take(60)
            ServiceTierMode.DEDICATED -> (dedNlPl + vps1Candidates + pubCandidates.take(60)).distinct().take(120)
        }

        ui {
            scanProgress = 0.28f
            statusMessage = "Probing ${prioritizedSample.size} ${currentMode.shortTitle} nodes..."
        }
        val tcpOk = PennyServerScanner.tcpFilter(prioritizedSample)
        val tcpDed = tcpOk.filter {
            val t = detectServerOriginTag(it.first)
            t == "DED-NL" || t == "DED-PL" || t == "VPS-1" || t == "GHOST"
        }
        val tcpPub = tcpOk.filter { !isDedicatedServerTag(detectServerOriginTag(it.first)) }
        plog("TCP probe hits (${currentMode.shortTitle}): ${tcpDed.size} Dedicated/Stealth, ${tcpPub.size} Public")

        val keep = when (currentMode) {
            ServiceTierMode.PUBLIC -> {
                if (tcpPub.isNotEmpty()) tcpPub.take(28).map { it.first }
                else pubCandidates.take(24)
            }
            ServiceTierMode.GHOST -> {
                (tcpDed.map { it.first } + ghostCandidates + dedNlPl + vps1Candidates).distinct().take(20)
            }
            ServiceTierMode.DEDICATED -> {
                (tcpDed.map { it.first } + dedNlPl + vps1Candidates + tcpPub.take(8).map { it.first }).distinct().take(28)
            }
        }

        val keepSet = keep.toHashSet()
        val oldFree = bestList.map { it.guid }.toHashSet()
        val oldVip = vipBestList.map { it.guid }.toHashSet()
        val oldGhost = ghostBestList.map { it.guid }.toHashSet()
        val selected = mainViewModel.uiState.value.selectedGuid
        val protect = HashSet<String>().apply {
            addAll(keepSet); addAll(oldFree); addAll(oldVip); addAll(oldGhost); selected?.let { add(it) }
        }
        val toRemove = MmkvManager.decodeServerList("").filter { guid ->
            if (guid in protect) return@filter false
            val rem = MmkvManager.decodeServerConfig(guid)?.remarks.orEmpty()
            rem == "Penny VPN" || rem.startsWith("PennyVPN", ignoreCase = true) || rem.contains("BPB", ignoreCase = true) || rem.startsWith("OWN-", ignoreCase = true)
        }
        if (toRemove.isNotEmpty()) {
            MmkvManager.removeServers(toRemove, "")
        }

        MmkvManager.clearAllTestDelayResults(keep)
        ui {
            scanProgress = 0.50f
            statusMessage = "Preparing ${keep.size} ${currentMode.shortTitle} servers..."
        }

        val pingSem = Semaphore(12)
        val completedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val testUrlPrimary = AppConfig.DELAY_TEST_URL

        coroutineScope {
            keep.map { g ->
                async(Dispatchers.IO) {
                    pingSem.withPermit {
                        var measured = -1L
                        try {
                            val cfgResult = CoreConfigManager.getV2rayConfig4Speedtest(this@MainActivity, g)
                            if (cfgResult.status) {
                                measured = CoreNativeManager.measureOutboundDelay(cfgResult.content, testUrlPrimary)
                            }
                        } catch (_: Exception) {
                        }
                        MmkvManager.encodeServerTestDelayMillis(g, if (measured > 0L) measured else -1L)
                        val d = completedCount.incrementAndGet()
                        ui {
                            scanProgress = 0.50f + 0.40f * d / keep.size.coerceAtLeast(1)
                            statusMessage = "Preparing ${currentMode.shortTitle} servers ($d/${keep.size})..."
                        }
                    }
                }
            }.awaitAll()
        }

        val tcpDelayMap = tcpOk.toMap()
        val combinedCandidates = keep.map { g ->
            val measured = MmkvManager.decodeServerAffiliationInfo(g)?.testDelayMillis ?: -1L
            val delayVal = when {
                measured > 0L -> measured
                tcpDelayMap[g] != null -> tcpDelayMap[g]!!
                else -> 165L
            }
            g to delayVal
        }.sortedWith(
            compareBy<Pair<String, Long>> { (g, _) ->
                when (detectServerOriginTag(g)) {
                    "DED-NL", "DED-PL" -> 0
                    "GHOST" -> 1
                    "VPS-1" -> 2
                    else -> 3
                }
            }.thenBy { it.second }
        ).take(BEST_LIST_SIZE)

        plog("${currentMode.title} candidates ready for download verification: ${combinedCandidates.size}")

        if (combinedCandidates.isEmpty()) {
            ui { statusMessage = "No reachable server found on current network." }
            return false
        }

        ui {
            scanProgress = 0.94f
            statusMessage = "Balancing ${currentMode.shortTitle} nodes..."
        }
        val lbSeed = if (loadBalanceEnabled) kotlin.math.abs(deviceId.hashCode()) else 0
        val entries = assignLabels(
            combinedCandidates.map { (g, d) ->
                PennyServer(g, detectServerOriginTag(g), d, "", currentMode != ServiceTierMode.PUBLIC)
            },
            isVipList = currentMode != ServiceTierMode.PUBLIC,
            loadBalanceSeed = lbSeed
        )

        val now = System.currentTimeMillis()
        saveList(entries, now, mode = currentMode)
        ui {
            when (currentMode) {
                ServiceTierMode.PUBLIC -> {
                    bestList.clear()
                    bestList.addAll(entries)
                    lastScanTime = now
                }
                ServiceTierMode.DEDICATED -> {
                    vipBestList.clear()
                    vipBestList.addAll(entries)
                    vipLastScanTime = now
                }
                ServiceTierMode.GHOST -> {
                    ghostBestList.clear()
                    ghostBestList.addAll(entries)
                    ghostLastScanTime = now
                }
            }
            scanProgress = 1f
        }
        failedGuids.clear()
        plog("Scan complete (${currentMode.name}): ${entries.size} load-balanced servers ready")
        return true
    }

    private suspend fun connectFromList(startAfter: String? = null): PennyServer? {
        val snapshot = activeList.toList()
        val rotated = if (startAfter != null) {
            val startIdx = snapshot.indexOfFirst { it.guid == startAfter }
            if (startIdx >= 0) snapshot.drop(startIdx + 1) + snapshot.take(startIdx + 1) else snapshot
        } else {
            snapshot
        }
        val order = rotated.filter { it.guid !in failedGuids && it.guid != startAfter }

        var attempts = 0
        for (s in order) {
            if (attempts >= MAX_CONNECT_ATTEMPTS) break
            attempts++
            ui {
                phase = Phase.CONNECTING
                statusMessage = "Testing download on ${s.label} ($attempts/${minOf(order.size, MAX_CONNECT_ATTEMPTS)})..."
            }
            plog("Attempt $attempts: ${s.label}")
            val dlMs = connectAndVerify(s.guid)
            if (dlMs > 0) {
                plog("Connected & Download Verified: ${s.label} (${dlMs}ms)")
                markConnected(s.guid, dlMs)
                return s
            }
            failedGuids.add(s.guid)
            plog("No download on ${s.label}; trying next...")
        }
        return null
    }

    private suspend fun markConnected(guid: String, verifiedPingMs: Long = -1L) {
        val now = System.currentTimeMillis()
        val currentMode = serviceTierMode
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
                val targetList = activeList
                val idx = targetList.indexOfFirst { it.guid == guid }
                if (idx in targetList.indices) {
                    // آپدیت درجا (In-Place) بدون پاک کردن لیست در حین رندر شدن LazyColumn برای جلوگیری از کرش Compose
                    targetList[idx] = targetList[idx].copy(ms = verifiedPingMs)
                    saveList(targetList.toList(), activeScanTime, mode = currentMode)
                }
            }
        }
        syncDailyDedicatedQuotaInBackground(0L)
    }

    private suspend fun connectAndVerify(guid: String): Long {
        if (MmkvManager.decodeServerConfig(guid) == null) {
            plog("Skipped missing server config for $guid")
            return -1L
        }
        PennySpeedLimiter.isVipUnrestricted = vipActive
        PennyServerScanner.syncGhostModeKernelRules(serviceTierMode, ghostMessagingOnly, splitTunnelLocal)
        val wasRunning = mainViewModel.uiState.value.isRunning

        if (wasRunning) {
            withContext(Dispatchers.Main) {
                runCatching { LauncherManager.stopService(this@MainActivity) }
            }
            delay(650L)
        }

        withContext(Dispatchers.Main) {
            runCatching {
                MmkvManager.setSelectServer(guid)
                mainViewModel.updateSelectedGuid(guid)
                LauncherManager.startService(this@MainActivity)
            }.onFailure { e ->
                plog("Start service error: ${e.message}")
            }
        }

        delay(750L)
        var waited = 0
        while (!mainViewModel.uiState.value.isRunning && waited < 7500) {
            delay(250)
            waited += 250
        }
        if (!mainViewModel.uiState.value.isRunning) {
            plog("Service did not start (${waited}ms)")
            return -1L
        }

        var localPortReady = false
        var portWait = 0
        while (portWait < 2500) {
            localPortReady = withContext(Dispatchers.IO) { PennyServerScanner.isLocalPortOpen(socksPort) }
            if (localPortReady) break
            delay(250)
            portWait += 250
        }
        if (!localPortReady) {
            plog("Warning: SOCKS port $socksPort not open")
            return -1L
        }
        delay(500)

        // ملاک انتخاب سرور: فقط دانلود واقعی دیتای HTTP از طریق تانل SOCKS5 (بدون اتکا به پینگ)
        val probeUrls = PennyServerScanner.TEST_URLS.take(2)
        for (url in probeUrls) {
            val dlMs = withContext(Dispatchers.IO) {
                PennyServerScanner.testThroughLocalProxy(url, socksPort, ::plog)
            }
            if (dlMs > 0L) {
                MmkvManager.encodeServerTestDelayMillis(guid, dlMs)
                return dlMs
            }
        }
        return -1L
    }
}
