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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * MainActivity ماژولار شده Penny VPN:
 * - رمزنگاری و اعتبارسنجی خالص: PennyCryptoCodec.kt (قابل تست با JUnit 5)
 * - ذخیره‌سازی MMKV و مهاجرت خودکار: PennyStorage.kt
 * - لایسنس و همگام‌سازی سهمیه (OkHttp): PennyLicenseManager.kt
 * - اسکنر و امضای لیست‌ها: PennyServerScanner.kt
 * - هماهنگ‌کننده تبلیغات با رضایت صریح: PennyAdCoordinator.kt
 * - هماهنگ‌کننده آپدیت امن APK: PennyUpdateCoordinator.kt
 * - صفحات و دیالوگ‌ها: PennyComponents.kt، PennyPages.kt، PennyDialogs.kt
 */
class MainActivity : HelperBaseComponentActivity() {

    companion object {
        private const val TAG = "PennyVPN"

        private const val BEST_LIST_SIZE = 15
        private const val MAX_CONNECT_ATTEMPTS = 10

        private const val STALE_MS = 6 * 60 * 60 * 1000L
        private const val MONITOR_INTERVAL_MS = 40_000L

        const val STARTIO_APP_ID = PennyAdCoordinator.STARTIO_APP_ID
        const val AADS_AD_UNIT_ID = PennyAdCoordinator.AADS_AD_UNIT_ID
        const val AADS_SECONDARY_UNIT_ID = PennyAdCoordinator.AADS_SECONDARY_UNIT_ID
        const val AADS_SPONSOR_PAGE_URL = PennyAdCoordinator.AADS_SPONSOR_PAGE_URL

        private const val BASE_FREE_SESSION_SEC = 3600L
        private const val REWARD_ADD_TIME_SEC = 2 * 3600L
        private const val BASE_DAILY_FREE_BYTES = 1L * 1024L * 1024L * 1024L
        private const val STARTIO_REWARD_BONUS_BYTES = 100L * 1024L * 1024L
        private const val MAX_DAILY_SPONSORED_ADS = 3
    }

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, MainRepository(application as AngApplication))
    }

    private val socksPort: Int get() = SettingsManager.getSocksPort()
    private val store: PennyPreferencesStore by lazy { PennyPreferencesStore(this) }

    // ── وضعیت سهمیه روزانه و تبلیغات ──
    private var lastInterstitialShownMs by mutableStateOf(0L)
    private var freeSessionRemainingSec by mutableStateOf(BASE_FREE_SESSION_SEC)
    private var dailyQuotaDayKey by mutableStateOf("")
    private var dailyUsedBytes by mutableStateOf(0L)
    private var dailyMaxBytes by mutableStateOf(BASE_DAILY_FREE_BYTES)
    private var pendingDailyUnsyncedBytes by mutableStateOf(0L)
    @Volatile
    private var isVipSyncInFlight: Boolean = false
    @Volatile
    private var isDailySyncInFlight: Boolean = false
    private var adsClaimedToday by mutableStateOf(0)
    private var favoriteCountries by mutableStateOf(setOf("NL", "DE"))
    @Volatile
    private var disconnectEpoch: Long = 0L
    private var monitorActionJob: Job? = null

    // ── همگام‌سازی ساعت تاییدشده سرور (جلوگیری از قفل دائمی expiry در پرش ساعت دستگاه) ──
    @Volatile
    private var lastServerNowMs: Long = 0L
    @Volatile
    private var elapsedAtServerSyncMs: Long = 0L

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

    // ── وضعیت اشتراک VIP ──
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
    private var showAdConsentDialog by mutableStateOf(false)
    private var showAadsSponsorDialog by mutableStateOf(false)
    private var aadsShouldResumeVpn by mutableStateOf(false)
    private var currentAadsUnitId by mutableStateOf(AADS_AD_UNIT_ID)
    private var bpbFragmentMode by mutableStateOf("NORMAL")
    private var availableUpdate by mutableStateOf<PennyServerScanner.AppUpdateInfo?>(null)
    private var updateBannerDismissed by mutableStateOf(false)
    private var updateDownloading by mutableStateOf(false)
    private var updateDownloadProgress by mutableStateOf(0f)
    private var updateDownloadStatus by mutableStateOf<String?>(null)
    private var pendingInstallApkFile: File? = null

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

    private val adCoordinator: PennyAdCoordinator by lazy {
        PennyAdCoordinator(
            context = this,
            store = store,
            scope = lifecycleScope,
            isVipActive = { vipActive },
            isVpnUiRunning = { mainViewModel.uiState.value.isRunning },
            pauseVpnForDirectIpAd = { pauseVpnForDirectIpAdIfNeeded() },
            onResumeVpnAfterAd = { shouldResume -> resumeVpnAfterAdIfNeeded(shouldResume) },
            onShowConsentDialog = { showAdConsentDialog = true },
            onOpenAadsSponsorDialog = { unitId, shouldResume ->
                currentAadsUnitId = unitId
                aadsShouldResumeVpn = shouldResume
                showAadsSponsorDialog = true
            },
            onStatusMessage = { statusMessage = it },
            onLog = ::plog
        )
    }

    private val updateCoordinator: PennyUpdateCoordinator by lazy {
        PennyUpdateCoordinator(
            context = this,
            scope = lifecycleScope,
            onDownloadingChange = { updateDownloading = it },
            onProgressChange = { updateDownloadProgress = it },
            onShowBanner = { updateBannerDismissed = false },
            onDownloadStatus = { updateDownloadStatus = it },
            onStatusMessage = { statusMessage = it },
            onRequestInstallPermission = { permIntent, apkFile ->
                pendingInstallApkFile = apkFile
                runCatching { installApkPermissionLauncher.launch(permIntent) }
                    .onFailure { updateDownloadStatus = "Unable to open install permission settings" }
            },
            onLog = ::plog
        )
    }

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                startSmartConnect()
            } else {
                statusMessage = "VPN permission denied!"
                plog("VPN permission denied")
            }
        }

    private val installApkPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val pending = pendingInstallApkFile
            if (pending != null && pending.exists()) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()) {
                    pendingInstallApkFile = null
                    updateCoordinator.launchApkInstaller(pending)
                } else {
                    updateDownloadStatus = "Allow 'Install unknown apps' to finish updating"
                    statusMessage = "Allow 'Install unknown apps' to complete app update"
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel.onAction(MainAction.Initialize)
        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
        loadSaved()
        checkVipExpiration()
        lifecycleScope.launch(Dispatchers.IO) {
            PennyServerScanner.fetchRemoteEdgeConfig(licenseServerUrl, ::plog)
            PennyServerScanner.selectBestGhostTransport(serviceTierMode, ::plog)
            val updateInfo = runCatching {
                PennyServerScanner.checkAppUpdateOnGitHub(BuildConfig.VERSION_NAME)
            }.getOrNull()
            if (updateInfo != null && updateInfo.hasUpdate) {
                withContext(Dispatchers.Main) {
                    availableUpdate = updateInfo
                    plog("New Penny VPN update found: v${updateInfo.latestVersion}")
                }
            }
        }
        syncServerQuotaInBackground(pendingUnsyncedBytes)
        syncDailyDedicatedQuotaInBackground(pendingDailyUnsyncedBytes)
        adCoordinator.initSdkIfConsented()
        startMonitor()
        registerNetworkChangeAutoReconnect()
    }

    override fun onStop() {
        val now = effectiveNowMs()
        if (vipActive) {
            store.putLong("vip_used_bytes", vipUsedBytes)
            store.putLong("pending_unsynced_bytes", pendingUnsyncedBytes)
            store.putLong("vip_last_seen_ms", now)
            if (pendingUnsyncedBytes > 0L) {
                syncServerQuotaInBackground(pendingUnsyncedBytes)
            }
        } else {
            ensureDailyDeviceQuotaCurrent()
            store.putLong("daily_used_bytes_$deviceId", dailyUsedBytes)
            store.putLong("pending_daily_unsynced_bytes_$deviceId", pendingDailyUnsyncedBytes)
            store.putLong("free_session_rem_sec", freeSessionRemainingSec)
            VipLicenseManager.writeTamperResistantDailyUsed(this, deviceId, dailyQuotaDayKey, dailyUsedBytes)
            if (pendingDailyUnsyncedBytes > 0L) {
                syncDailyDedicatedQuotaInBackground(pendingDailyUnsyncedBytes)
            }
        }
        super.onStop()
    }

    override fun onDestroy() {
        unregisterNetworkChangeCallback()
        super.onDestroy()
    }

    private fun recordServerClockAnchor(serverNowMs: Long) {
        if (serverNowMs > 1_700_000_000_000L) {
            lastServerNowMs = serverNowMs
            elapsedAtServerSyncMs = SystemClock.elapsedRealtime()
            vipLastSeenMs = serverNowMs
            store.putLong("vip_last_seen_ms", serverNowMs)
        }
    }

    private fun effectiveNowMs(): Long {
        val sysNow = System.currentTimeMillis()
        val resolved = PennyCryptoCodec.resolveEffectiveNowMs(
            systemWallMs = sysNow,
            storedLastSeenMs = vipLastSeenMs,
            lastServerNowMs = lastServerNowMs,
            elapsedAtServerSyncMs = elapsedAtServerSyncMs,
            currentElapsedMs = SystemClock.elapsedRealtime()
        )
        if (resolved != vipLastSeenMs) {
            vipLastSeenMs = resolved
            store.putLong("vip_last_seen_ms", resolved)
        }
        return resolved
    }

    // ═════════════════════════ همگام‌سازی سهمیه و لایسنس با سرور ═════════════════════════

    private fun syncServerQuotaInBackground(deltaToReport: Long) {
        if (!vipActive || vipSerial.isBlank() || isVipSyncInFlight) return
        val clampedDelta = deltaToReport.coerceAtLeast(0L)
        isVipSyncInFlight = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val state = VipLicenseManager.syncTrafficWithServer(
                    apiBaseUrl = licenseServerUrl,
                    serial = vipSerial,
                    deviceId = deviceId,
                    deltaBytes = clampedDelta,
                    code = vipCode
                ) ?: return@launch

                withContext(Dispatchers.Main) {
                    if (state.serverNowMs > 0L) recordServerClockAnchor(state.serverNowMs)
                    pendingUnsyncedBytes = (pendingUnsyncedBytes - clampedDelta).coerceAtLeast(0L)
                    vipUsedBytes = maxOf(vipUsedBytes, state.usedBytes)
                    vipMaxBytes = state.maxBytes
                    if (state.expiresAtMs > 0L) {
                        vipExpireMs = state.expiresAtMs
                    }
                    store.putLong("vip_used_bytes", vipUsedBytes)
                    store.putLong("pending_unsynced_bytes", pendingUnsyncedBytes)
                    store.putLong("vip_max_bytes", vipMaxBytes)
                    store.putLong("vip_expire_ms", vipExpireMs)
                    if (!state.active || vipUsedBytes >= vipMaxBytes) {
                        checkVipExpiration()
                    }
                }
            } finally {
                isVipSyncInFlight = false
            }
        }
    }

    // ═════════════════════════ هماهنگی تبلیغات با قطع موقت VPN ═════════════════════════

    private suspend fun pauseVpnForDirectIpAdIfNeeded(): Boolean {
        val wasRunning = mainViewModel.uiState.value.isRunning ||
            adCoordinator.isVpnTransportActive() ||
            phase != Phase.IDLE
        if (wasRunning) {
            withContext(Dispatchers.Main) {
                disconnectEpoch++
                workJob?.cancel()
                monitorActionJob?.cancel()
                userWantsConnected = false
                phase = Phase.IDLE
                scanProgress = 0f
                statusMessage = "Disconnecting VPN to load ad on your direct IP..."
                plog("Stopping VPN before ad request so sponsor loads from user's direct IP")
                runCatching { PennySpeedLimiter.stop() }
                runCatching { LauncherManager.stopService(this@MainActivity) }
            }
            var waitedMs = 0L
            while ((mainViewModel.uiState.value.isRunning || adCoordinator.isVpnTransportActive()) && waitedMs < 3200L) {
                delay(150L)
                waitedMs += 150L
            }
            delay(1100L)
        }
        return wasRunning
    }

    private fun resumeVpnAfterAdIfNeeded(shouldResume: Boolean) {
        if (!shouldResume) return
        lifecycleScope.launch(Dispatchers.Main) {
            delay(450L)
            if (!mainViewModel.uiState.value.isRunning && phase == Phase.IDLE) {
                plog("Ad closed; auto-reconnecting VPN tunnel...")
                startSmartConnect()
            }
        }
    }

    private fun claimStartIoRewardBonus() {
        ensureDailyDeviceQuotaCurrent()
        if (adsClaimedToday >= MAX_DAILY_SPONSORED_ADS) {
            statusMessage = "Daily limit reached ($MAX_DAILY_SPONSORED_ADS of $MAX_DAILY_SPONSORED_ADS claimed today)"
            return
        }
        val today = todayDateKey()
        adsClaimedToday = (adsClaimedToday + 1).coerceAtMost(MAX_DAILY_SPONSORED_ADS)
        dailyMaxBytes += STARTIO_REWARD_BONUS_BYTES
        freeSessionRemainingSec += REWARD_ADD_TIME_SEC
        store.putLong("daily_max_bytes_$deviceId", dailyMaxBytes)
        store.putLong("free_session_rem_sec", freeSessionRemainingSec)
        store.putInt("ads_claimed_${deviceId}_$today", adsClaimedToday)
        VipLicenseManager.writeTamperResistantDailyAllowance(
            this,
            deviceId,
            today,
            dailyMaxBytes,
            adsClaimedToday
        )
        val remBytes = (dailyMaxBytes - dailyUsedBytes).coerceAtLeast(0L)
        statusMessage = "+100 MB Added ($adsClaimedToday of $MAX_DAILY_SPONSORED_ADS claimed today • ${fmtBytes(remBytes)} left)"
        plog("Sponsored Ad completed: +100 MB ($adsClaimedToday/$MAX_DAILY_SPONSORED_ADS claimed today)")
    }

    // ═════════════════════════ سهمیه روزانه ۱ گیگابایت ═════════════════════════

    private fun todayDateKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(effectiveNowMs()))

    private fun ensureDailyDeviceQuotaCurrent() {
        val today = todayDateKey()
        val storedDay = store.getString("daily_quota_day_$deviceId", "") ?: ""
        val maxCap = BASE_DAILY_FREE_BYTES + MAX_DAILY_SPONSORED_ADS * STARTIO_REWARD_BONUS_BYTES
        if (storedDay != today || dailyQuotaDayKey != today) {
            dailyQuotaDayKey = today
            val recovered = VipLicenseManager.readTamperResistantDailyUsed(this, deviceId, today, 0L)
            val (verifiedMax, verifiedAds) = VipLicenseManager.readTamperResistantDailyAllowance(
                this, deviceId, today, BASE_DAILY_FREE_BYTES, 0
            )
            dailyUsedBytes = recovered
            dailyMaxBytes = verifiedMax.coerceIn(BASE_DAILY_FREE_BYTES, maxCap)
            pendingDailyUnsyncedBytes = 0L
            adsClaimedToday = verifiedAds.coerceIn(0, MAX_DAILY_SPONSORED_ADS)
            store.putString("daily_quota_day_$deviceId", today)
            store.putLong("daily_used_bytes_$deviceId", dailyUsedBytes)
            store.putLong("pending_daily_unsynced_bytes_$deviceId", 0L)
            store.putLong("daily_max_bytes_$deviceId", dailyMaxBytes)
            store.putInt("ads_claimed_${deviceId}_$today", adsClaimedToday)
            VipLicenseManager.writeTamperResistantDailyUsed(this, deviceId, today, dailyUsedBytes)
            VipLicenseManager.writeTamperResistantDailyAllowance(this, deviceId, today, dailyMaxBytes, adsClaimedToday)
        } else {
            val (verifiedMax, verifiedAds) = VipLicenseManager.readTamperResistantDailyAllowance(
                this, deviceId, today, BASE_DAILY_FREE_BYTES, 0
            )
            adsClaimedToday = verifiedAds.coerceIn(0, MAX_DAILY_SPONSORED_ADS)
            dailyMaxBytes = verifiedMax.coerceIn(BASE_DAILY_FREE_BYTES, maxCap)
            val recovered = VipLicenseManager.readTamperResistantDailyUsed(this, deviceId, today, dailyUsedBytes)
            if (recovered > dailyUsedBytes) {
                dailyUsedBytes = recovered
                store.putLong("daily_used_bytes_$deviceId", dailyUsedBytes)
            }
        }
    }

    private fun syncDailyDedicatedQuotaInBackground(deltaBytes: Long) {
        if (isDailySyncInFlight) return
        val clampedDelta = deltaBytes.coerceAtLeast(0L)
        val today = todayDateKey()
        val curLabel = activeList.firstOrNull { it.guid == lastConnectedGuid }?.label.orEmpty()
        isDailySyncInFlight = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val res = VipLicenseManager.syncDailyQuotaAndTelemetry(
                    apiBaseUrl = licenseServerUrl,
                    deviceId = deviceId,
                    userUuid = userUuid,
                    dayKey = today,
                    localUsedBytes = dailyUsedBytes,
                    deltaBytes = clampedDelta,
                    activeMode = serviceTierMode.name,
                    connectedServerLabel = curLabel,
                    activeEdgeId = PennyServerScanner.activeEdgeId,
                    activeTransport = PennyServerScanner.activeTransport,
                    edgeLatencyMs = PennyServerScanner.activeEdgeLatencyMs,
                    transportHealth = PennyServerScanner.activeTransportHealth
                ) ?: return@launch
                withContext(Dispatchers.Main) {
                    if (res.serverNowMs > 0L) recordServerClockAnchor(res.serverNowMs)
                    pendingDailyUnsyncedBytes = (pendingDailyUnsyncedBytes - clampedDelta).coerceAtLeast(0L)
                    if (res.usedBytes > dailyUsedBytes) {
                        dailyUsedBytes = res.usedBytes
                    }
                    store.putLong("daily_used_bytes_$deviceId", dailyUsedBytes)
                    store.putLong("pending_daily_unsynced_bytes_$deviceId", pendingDailyUnsyncedBytes)
                    VipLicenseManager.writeTamperResistantDailyUsed(this@MainActivity, deviceId, today, dailyUsedBytes)

                    if (res.blocked && !vipActive && (serviceTierMode == ServiceTierMode.DEDICATED || serviceTierMode == ServiceTierMode.GHOST)) {
                        dailyUsedBytes = maxOf(dailyUsedBytes, dailyMaxBytes)
                        store.putLong("daily_used_bytes_$deviceId", dailyUsedBytes)
                        store.putString("service_tier_mode", ServiceTierMode.PUBLIC.name)
                        VipLicenseManager.writeTamperResistantDailyUsed(this@MainActivity, deviceId, today, dailyUsedBytes)
                        serviceTierMode = ServiceTierMode.PUBLIC
                        PennyServerScanner.syncGhostModeKernelRules(ServiceTierMode.PUBLIC, ghostMessagingOnly, splitTunnelLocal)
                        statusMessage = "1 GB Daily Quota Reached • Switched to Public Mode"
                        plog("Server enforced 1 GB daily quota block; switching to Public Mode")
                        if (mainViewModel.uiState.value.isRunning) {
                            LauncherManager.stopService(this@MainActivity)
                        }
                    }
                }
            } finally {
                isDailySyncInFlight = false
            }
        }
    }

    // ═════════════════════════ مدیریت اشتراک VIP ═════════════════════════

    private fun getSubscriptionSources(mode: ServiceTierMode): List<String> {
        val custom = customVipSubUrl.trim()
        val baseList = PennyServerScanner.getSubscriptionSourcesForMode(mode)
        return (if (mode != ServiceTierMode.PUBLIC && custom.isNotEmpty() && !custom.contains(".pages.dev", ignoreCase = true)) {
            custom.split("\n", ",")
                .map { it.trim() }
                .filter {
                    VipLicenseManager.isTrustedHttpsEndpoint(it) &&
                        PennyServerScanner.isSafeNestedSubscriptionUrl(it)
                } + baseList
        } else {
            baseList
        }).distinct()
    }

    private fun checkVipExpiration(): Boolean {
        if (vipActive) {
            val (sigVerified, _) = VipLicenseManager.verifyLicenseOfflineSignature(vipCode, deviceId, 0L)
            if (sigVerified == null || vipSerial.isBlank() || !sigVerified.serial.equals(vipSerial, ignoreCase = true)) {
                vipActive = false
                useVipServers = false
                vipCode = ""
                vipSerial = ""
                PennySpeedLimiter.isVipUnrestricted = false
                store.putBoolean("vip_active", false)
                store.putBoolean("use_vip_servers", false)
                store.putString("vip_code", "")
                store.putString("vip_serial", "")
                plog("Cleared unverified local VIP state")
                adCoordinator.initSdkIfConsented()
                return true
            }
        }
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
            store.putBoolean("vip_active", false)
            store.putBoolean("use_vip_servers", false)
            store.putString("vip_code", "")
            store.putLong("vip_last_seen_ms", now)
            statusMessage = if (trafficExpired) {
                "Your VIP traffic quota has been reached."
            } else {
                "Your VIP subscription has expired."
            }
            plog("VIP expired: timeExpired=$timeExpired, trafficExpired=$trafficExpired")
            adCoordinator.initSdkIfConsented()
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
                if (serverState.serverNowMs > 0L) recordServerClockAnchor(serverState.serverNowMs)
                val now = effectiveNowMs()
                vipActive = true
                useVipServers = true
                vipCode = inputCode.trim()
                vipSerial = serverState.serial
                vipExpireMs = serverState.expiresAtMs
                vipMaxBytes = serverState.maxBytes
                vipUsedBytes = serverState.usedBytes
                vipLastSeenMs = now

                store.putBoolean("vip_active", true)
                store.putBoolean("use_vip_servers", true)
                store.putString("vip_code", vipCode)
                store.putString("vip_serial", vipSerial)
                store.putLong("vip_expire_ms", vipExpireMs)
                store.putLong("vip_max_bytes", vipMaxBytes)
                store.putLong("vip_used_bytes", vipUsedBytes)
                store.putLong("vip_last_seen_ms", now)

                plog("Server-verified VIP Plan Activated (${VipLicenseManager.maskCode(vipCode)}, used=${fmtBytes(vipUsedBytes)})")
                statusMessage = "VIP Plan Activated (Server Verified)"
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
                    val capturedEpoch = disconnectEpoch
                    if (!autoReconnect || !userWantsConnected) return
                    monitorActionJob = lifecycleScope.launch(Dispatchers.IO) {
                        delay(1800)
                        if (!autoReconnect || !userWantsConnected || disconnectEpoch != capturedEpoch || phase != Phase.IDLE) return@launch
                        val isRunning = mainViewModel.uiState.value.isRunning
                        val tunnelAlive = isRunning &&
                            PennyServerScanner.testThroughLocalProxy(
                                PennyServerScanner.TEST_URLS.first(),
                                socksPort,
                                ::plog
                            ) > 0
                        if (!tunnelAlive && userWantsConnected && disconnectEpoch == capturedEpoch) {
                            plog("Network change detected; auto-reconnecting...")
                            triggerAutoReconnect()
                        }
                    }
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    super.onCapabilitiesChanged(network, networkCapabilities)
                    val capturedEpoch = disconnectEpoch
                    if (!autoReconnect || !userWantsConnected || phase != Phase.IDLE) return
                    if (!mainViewModel.uiState.value.isRunning) {
                        monitorActionJob = lifecycleScope.launch(Dispatchers.IO) {
                            delay(1500)
                            if (autoReconnect && userWantsConnected && disconnectEpoch == capturedEpoch && phase == Phase.IDLE && !mainViewModel.uiState.value.isRunning) {
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
        val capturedEpoch = disconnectEpoch
        if (!userWantsConnected || phase != Phase.IDLE || activeList.isEmpty()) return
        if (SettingsManager.isVpnMode() && VpnService.prepare(this@MainActivity) != null) return

        ui {
            if (!userWantsConnected || disconnectEpoch != capturedEpoch) return@ui
            phase = Phase.CONNECTING
            statusMessage = "Auto-reconnecting to last healthy server..."
        }
        try {
            if (!userWantsConnected || disconnectEpoch != capturedEpoch) return
            failedGuids.clear()
            val connected = connectFromList(startAfter = null)
            ui {
                if (userWantsConnected && disconnectEpoch == capturedEpoch) {
                    statusMessage = if (connected != null) "Auto-reconnected" else "Auto-reconnect failed"
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Main) {
                phase = Phase.IDLE
            }
        }
    }

    // ═════════════════════════ ذخیره / بازیابی (MMKV) ═════════════════════════

    private fun loadSaved() {
        appLang = AppLang.EN
        statusMessage = Strings.ready(appLang)
        PennyServerScanner.applyBpbCoreOptimizations()
        autoSwitch = store.getBoolean("auto_switch", true)
        autoReconnect = store.getBoolean("auto_reconnect", true)
        splitTunnelLocal = store.getBoolean("split_tunnel_local", true)
        cleanDnsShield = store.getBoolean("clean_dns_shield", true)
        loadBalanceEnabled = store.getBoolean("load_balance_enabled", true)
        killSwitchEnabled = store.getBoolean("kill_switch_enabled", true)
        favoriteCountries = store.getStringSet("fav_countries", setOf("NL", "DE"))
        val savedProf = store.getString("conn_profile", ConnectionProfile.SMART.name) ?: ConnectionProfile.SMART.name
        connectionProfile = runCatching { ConnectionProfile.valueOf(savedProf) }.getOrDefault(ConnectionProfile.SMART)
        val savedTier = store.getString("service_tier_mode", ServiceTierMode.DEDICATED.name) ?: ServiceTierMode.DEDICATED.name
        serviceTierMode = runCatching { ServiceTierMode.valueOf(savedTier) }.getOrDefault(ServiceTierMode.DEDICATED)
        ghostMessagingOnly = store.getBoolean("ghost_messaging_only", true)
        bpbFragmentMode = store.getString("bpb_frag_mode", "NORMAL") ?: "NORMAL"
        PennyServerScanner.syncGhostModeKernelRules(serviceTierMode, ghostMessagingOnly, splitTunnelLocal)
        userWantsConnected = store.getBoolean("user_connected", false)
        lastScanTime = store.getLong("scan_time", 0L)
        vipLastScanTime = store.getLong("vip_scan_time", 0L)
        ghostLastScanTime = store.getLong("ghost_scan_time", 0L)
        connectStartMs = store.getLong("connect_time", 0L)
        lastConnectedGuid = store.getString("last_guid", null)
        lastInterstitialShownMs = store.getLong("last_interstitial_ms", 0L)
        freeSessionRemainingSec = store.getLong("free_session_rem_sec", BASE_FREE_SESSION_SEC).coerceAtLeast(60L)

        vipLastSeenMs = store.getLong("vip_last_seen_ms", 0L)
        effectiveNowMs()

        val today = todayDateKey()
        val savedDay = store.getString("daily_quota_day_$deviceId", "") ?: ""
        val maxCap = BASE_DAILY_FREE_BYTES + MAX_DAILY_SPONSORED_ADS * STARTIO_REWARD_BONUS_BYTES
        dailyQuotaDayKey = today
        val prefUsed = if (savedDay == today) store.getLong("daily_used_bytes_$deviceId", 0L).coerceAtLeast(0L) else 0L
        dailyUsedBytes = VipLicenseManager.readTamperResistantDailyUsed(this, deviceId, today, prefUsed)
        val (verifiedMax, verifiedAds) = VipLicenseManager.readTamperResistantDailyAllowance(
            this, deviceId, today, BASE_DAILY_FREE_BYTES, 0
        )
        dailyMaxBytes = verifiedMax.coerceIn(BASE_DAILY_FREE_BYTES, maxCap)
        adsClaimedToday = verifiedAds.coerceIn(0, MAX_DAILY_SPONSORED_ADS)
        if (savedDay != today) {
            store.putString("daily_quota_day_$deviceId", today)
            store.putLong("daily_used_bytes_$deviceId", dailyUsedBytes)
            store.putLong("daily_max_bytes_$deviceId", dailyMaxBytes)
            store.putInt("ads_claimed_${deviceId}_$today", adsClaimedToday)
            VipLicenseManager.writeTamperResistantDailyAllowance(this, deviceId, today, dailyMaxBytes, adsClaimedToday)
        }

        val rawVipActive = store.getBoolean("vip_active", false)
        vipCode = store.getString("vip_code", "") ?: ""
        vipSerial = store.getString("vip_serial", "") ?: ""
        vipExpireMs = store.getLong("vip_expire_ms", 0L)
        vipMaxBytes = store.getLong("vip_max_bytes", VipLicenseManager.VIP_MAX_BYTES)
        vipUsedBytes = store.getLong("vip_used_bytes", 0L)
        if (rawVipActive) {
            val (verifiedLic, _) = VipLicenseManager.verifyLicenseOfflineSignature(vipCode, deviceId, 0L)
            if (verifiedLic != null && vipSerial.isNotBlank() && verifiedLic.serial.equals(vipSerial, ignoreCase = true)) {
                vipActive = true
                useVipServers = store.getBoolean("use_vip_servers", false)
            } else {
                vipActive = false
                useVipServers = false
                vipCode = ""
                vipSerial = ""
                store.putBoolean("vip_active", false)
                store.putBoolean("use_vip_servers", false)
                store.putString("vip_code", "")
                store.putString("vip_serial", "")
                plog("Rejected tampered or unverified saved VIP flag")
            }
        } else {
            vipActive = false
            useVipServers = false
        }
        PennySpeedLimiter.isVipUnrestricted = vipActive
        pendingUnsyncedBytes = store.getLong("pending_unsynced_bytes", 0L).coerceAtLeast(0L)
        pendingDailyUnsyncedBytes = store.getLong("pending_daily_unsynced_bytes_$deviceId", 0L).coerceAtLeast(0L)

        customVipSubUrl = store.getString("custom_vip_sub_url", PennyServerScanner.DEDICATED_VPS_SUB_URL)
            ?.trim()
            ?.takeIf {
                it.isNotBlank() &&
                    !it.contains(".pages.dev", ignoreCase = true) &&
                    VipLicenseManager.isTrustedHttpsEndpoint(it) &&
                    PennyServerScanner.isSafeNestedSubscriptionUrl(it)
            }
            ?: PennyServerScanner.DEDICATED_VPS_SUB_URL
        licenseServerUrl = store.getString("license_server_url", VipLicenseManager.DEFAULT_LICENSE_API_BASE)
            ?.trim()
            ?.takeIf { VipLicenseManager.isTrustedHttpsEndpoint(it) }
            ?: VipLicenseManager.DEFAULT_LICENSE_API_BASE

        val lbSeed = if (loadBalanceEnabled) kotlin.math.abs(deviceId.hashCode()) else 0
        bestList.clear()
        bestList.addAll(store.loadCachedServerList(ServiceTierMode.PUBLIC, lbSeed))
        vipBestList.clear()
        vipBestList.addAll(store.loadCachedServerList(ServiceTierMode.DEDICATED, lbSeed))
        ghostBestList.clear()
        ghostBestList.addAll(store.loadCachedServerList(ServiceTierMode.GHOST, lbSeed))
    }

    private fun saveList(list: List<PennyServer>, scanTime: Long, mode: ServiceTierMode) {
        store.saveCachedServerList(list, scanTime, mode)
    }

    private fun plog(msg: String) {
        val sanitized = msg
            .replace(PennyServerScanner.DED_NL_HOST, "[DED-NL-NODE]", ignoreCase = true)
            .replace(PennyServerScanner.DED_PL_HOST, "[DED-PL-NODE]", ignoreCase = true)
            .replace(PennyServerScanner.VPS1_HOST, "[DED-DE-NODE]", ignoreCase = true)
            .replace(PennyServerScanner.BPB_HOST, "[DED-CF1-NODE]", ignoreCase = true)
            .replace(PennyServerScanner.BPB2_HOST, "[DED-CF2-NODE]", ignoreCase = true)
            .replace(userUuid, "[PROTECTED-UUID]", ignoreCase = true)
            .replace(deviceId, "[PROTECTED-HWID]", ignoreCase = true)
            .replace(Regex("""PL1\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""", RegexOption.IGNORE_CASE), "[PROTECTED-LICENSE]")
            .replace(Regex("""(vless|vmess|trojan|ss|hysteria2)://[^\s]+""", RegexOption.IGNORE_CASE), "$1://[REDACTED]")
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

    private fun copyDeviceIdToClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("PennyVPN Device ID", deviceId))
        statusMessage = "Device ID copied: $deviceId"
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
        val downHistory = remember { mutableStateListOf<Float>() }
        val upHistory = remember { mutableStateListOf<Float>() }

        LaunchedEffect(isRunning, vipActive, useVipServers) {
            if (!isRunning) {
                downSpeed = 0L; upSpeed = 0L; totalDown = 0L; totalUp = 0L; elapsedSec = 0L
                downHistory.clear()
                upHistory.clear()
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
                        runCatching { CoreServiceManager.queryAllOutboundTrafficStats() }.getOrDefault(emptyList())
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
                downHistory.add(downSpeed.toFloat())
                if (downHistory.size > 24) downHistory.removeAt(0)
                upHistory.add(upSpeed.toFloat())
                if (upHistory.size > 24) upHistory.removeAt(0)

                if (!vipActive && (serviceTierMode == ServiceTierMode.DEDICATED || serviceTierMode == ServiceTierMode.GHOST)) {
                    ensureDailyDeviceQuotaCurrent()
                    if (stepDeltaBytes > 0L) {
                        dailyUsedBytes += stepDeltaBytes
                        pendingDailyUnsyncedBytes += stepDeltaBytes
                        if (tickCount % 5 == 0) {
                            store.putLong("daily_used_bytes_$deviceId", dailyUsedBytes)
                            store.putLong("pending_daily_unsynced_bytes_$deviceId", pendingDailyUnsyncedBytes)
                            VipLicenseManager.writeTamperResistantDailyUsed(
                                this@MainActivity, deviceId, dailyQuotaDayKey, dailyUsedBytes
                            )
                        }
                        if (tickCount % 15 == 0 && pendingDailyUnsyncedBytes > 0L) {
                            syncDailyDedicatedQuotaInBackground(pendingDailyUnsyncedBytes)
                        }
                    }
                    if (dailyUsedBytes >= dailyMaxBytes) {
                        store.putLong("daily_used_bytes_$deviceId", dailyUsedBytes)
                        store.putLong("pending_daily_unsynced_bytes_$deviceId", pendingDailyUnsyncedBytes)
                        VipLicenseManager.writeTamperResistantDailyUsed(
                            this@MainActivity, deviceId, dailyQuotaDayKey, dailyUsedBytes
                        )
                        plog("Daily 1 GB quota (${fmtBytes(dailyMaxBytes)}) reached; switching to Public Mode")
                        serviceTierMode = ServiceTierMode.PUBLIC
                        store.putString("service_tier_mode", ServiceTierMode.PUBLIC.name)
                        PennyServerScanner.syncGhostModeKernelRules(serviceTierMode, ghostMessagingOnly, splitTunnelLocal)
                        statusMessage = "1 GB Daily Quota Reached • Switched to Public Mode"
                        LauncherManager.stopService(this@MainActivity)
                        continue
                    }

                    if (freeSessionRemainingSec > 0L) {
                        freeSessionRemainingSec -= 1L
                        if (tickCount % 10 == 0) {
                            store.putLong("free_session_rem_sec", freeSessionRemainingSec)
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
                        store.putLong("vip_used_bytes", vipUsedBytes)
                        store.putLong("pending_unsynced_bytes", pendingUnsyncedBytes)
                    }
                    if (tickCount % 15 == 0 && pendingUnsyncedBytes > 0L) {
                        syncServerQuotaInBackground(pendingUnsyncedBytes)
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
                if (vipActive) "VIP Active"
                else "${fmtBytes(dailyRemBytes)} / ${fmtBytes(dailyMaxBytes)} left"
            ServiceTierMode.GHOST ->
                if (ghostMessagingOnly) "Chat-Only Lock (42 KB/s)"
                else "Ghost Anti-DPI"
            ServiceTierMode.PUBLIC -> "Free Unlimited"
        }

        val onGhostToggleChanged: (Boolean) -> Unit = { enabled ->
            ghostMessagingOnly = enabled
            store.putBoolean("ghost_messaging_only", enabled)
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
                store.putString("service_tier_mode", newMode.name)
                if (newMode == ServiceTierMode.GHOST && !ghostMessagingOnly) {
                    ghostMessagingOnly = true
                    store.putBoolean("ghost_messaging_only", true)
                }
                PennyServerScanner.syncGhostModeKernelRules(newMode, ghostMessagingOnly, splitTunnelLocal)
                PennyServerScanner.selectBestGhostTransport(newMode, ::plog)
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

        val onSplitTunnelChange: (Boolean) -> Unit = { enabled ->
            splitTunnelLocal = enabled
            store.putBoolean("split_tunnel_local", enabled)
            MmkvManager.encodeSettings(AppConfig.PREF_VPN_BYPASS_LAN, if (enabled) "1" else "2")
            PennyServerScanner.syncGhostModeKernelRules(serviceTierMode, ghostMessagingOnly, enabled)
            statusMessage = if (enabled) "Domestic (.ir) & LAN Direct Bypass Enabled" else "All Traffic Routed in Tunnel"
            plog("Smart Split Tunneling set to $enabled")
        }

        val onCleanDnsChange: (Boolean) -> Unit = { enabled ->
            cleanDnsShield = enabled
            store.putBoolean("clean_dns_shield", enabled)
            val dnsServer = if (enabled) "1.1.1.2" else "1.1.1.1"
            MmkvManager.encodeSettings(AppConfig.PREF_REMOTE_DNS, dnsServer)
            statusMessage = "Encrypted DNS set to $dnsServer"
            plog("Clean DNS Shield set to $dnsServer")
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(bgTopColor, bgMidColor, bgBottomColor)))
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

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    val activeUpdate = availableUpdate
                    AnimatedVisibility(
                        visible = activeUpdate != null && activeUpdate.hasUpdate && (!updateBannerDismissed || updateDownloading),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        if (activeUpdate != null) {
                            PennyAppUpdateBanner(
                                updateInfo = activeUpdate,
                                isDownloading = updateDownloading,
                                progress = updateDownloadProgress,
                                statusNote = updateDownloadStatus,
                                onUpdateClick = { updateCoordinator.startFullAppUpdate(activeUpdate.downloadUrl) },
                                onDismiss = { updateBannerDismissed = true }
                            )
                        }
                    }

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
                                        verticalArrangement = Arrangement.spacedBy(7.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        LiveConnectionTelemetryCard(
                                            mode = mode,
                                            elapsedSec = elapsedSec,
                                            downSpeed = downSpeed,
                                            upSpeed = upSpeed,
                                            downHistory = downHistory,
                                            upHistory = upHistory,
                                            dailyUsedBytes = dailyUsedBytes,
                                            dailyMaxBytes = dailyMaxBytes,
                                            countryGeo = currentCountryGeo,
                                            server = current,
                                            onServerSwitchClick = { currentNavTab = BottomNavTab.SERVERS },
                                            activeEdgeId = PennyServerScanner.activeEdgeId,
                                            activeTransport = PennyServerScanner.activeTransport
                                        )

                                        if (!vipActive) {
                                            SponsoredQuotaCard(
                                                adsClaimedToday = adsClaimedToday,
                                                maxDailyAds = MAX_DAILY_SPONSORED_ADS,
                                                isConnected = mode == UiMode.CONNECTED,
                                                onWatchSponsorAd = {
                                                    adCoordinator.openSponsorQuotaFlow(
                                                        adsClaimedToday = adsClaimedToday,
                                                        maxDailyAds = MAX_DAILY_SPONSORED_ADS
                                                    )
                                                }
                                            )
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
                                    favoriteCountries = favoriteCountries,
                                    onToggleFavoriteCountry = { iso ->
                                        val next = favoriteCountries.toMutableSet()
                                        if (next.contains(iso)) next.remove(iso) else next.add(iso)
                                        favoriteCountries = next.toSet()
                                        store.putStringSet("fav_countries", favoriteCountries)
                                    },
                                    onScanClick = { startScanOnly() },
                                    onAutoSelectClick = {
                                        val bestAuto = currentList.minByOrNull { if (it.ms > 0) it.ms else 9999L }
                                            ?: currentList.firstOrNull()
                                        if (bestAuto != null) connectToEntry(bestAuto) else startSmartConnect()
                                    },
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
                                    onCleanDnsShieldChange = onCleanDnsChange,
                                    splitTunnelLocal = splitTunnelLocal,
                                    onSplitTunnelLocalChange = onSplitTunnelChange,
                                    ghostMessagingOnly = ghostMessagingOnly,
                                    showAds = !vipActive,
                                    onSponsorClick = {
                                        adCoordinator.openSponsorQuotaFlow(
                                            adsClaimedToday = adsClaimedToday,
                                            maxDailyAds = MAX_DAILY_SPONSORED_ADS
                                        )
                                    },
                                    onAutoTuneClick = {
                                        val tuned = PennyServerScanner.detectCarrierAndTuneFragment(this@MainActivity, ::plog)
                                        statusMessage = "ISP Auto-Tuned: $tuned"
                                        if (isRunning) safeRestartVpnOnMainThread()
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
                                        store.putBoolean("auto_switch", it)
                                    },
                                    autoReconnect = autoReconnect,
                                    onAutoReconnectChange = {
                                        autoReconnect = it
                                        store.putBoolean("auto_reconnect", it)
                                    },
                                    splitTunnelLocal = splitTunnelLocal,
                                    onSplitTunnelChange = onSplitTunnelChange,
                                    cleanDnsShield = cleanDnsShield,
                                    onCleanDnsShieldChange = onCleanDnsChange,
                                    loadBalanceEnabled = loadBalanceEnabled,
                                    onLoadBalanceChange = {
                                        loadBalanceEnabled = it
                                        store.putBoolean("load_balance_enabled", it)
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
                                        if (it && !autoReconnect) {
                                            autoReconnect = true
                                            store.putBoolean("auto_reconnect", true)
                                        }
                                        store.putBoolean("kill_switch_enabled", it)
                                        statusMessage = if (it) "VPN Kill Switch & Auto-Reconnect Guard Active" else "VPN Kill Switch Guard Disabled"
                                    },
                                    onCopyUuid = { copyUuidToClipboard() },
                                    onOpenVipDialog = { showVipDialog = true },
                                    onWatchSponsorAd = {
                                        adCoordinator.openSponsorQuotaFlow(
                                            adsClaimedToday = adsClaimedToday,
                                            maxDailyAds = MAX_DAILY_SPONSORED_ADS
                                        )
                                    },
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

            if (showMenu) {
                PennyModernMenuDialog(
                    appLang = appLang,
                    isVipMode = isVipMode,
                    activeServerCount = activeList.size,
                    autoSwitch = autoSwitch,
                    onAutoSwitchChange = {
                        autoSwitch = it
                        store.putBoolean("auto_switch", it)
                    },
                    autoReconnect = autoReconnect,
                    onAutoReconnectChange = {
                        autoReconnect = it
                        store.putBoolean("auto_reconnect", it)
                    },
                    splitTunnelLocal = splitTunnelLocal,
                    onSplitTunnelChange = onSplitTunnelChange,
                    cleanDnsShield = cleanDnsShield,
                    onCleanDnsShieldChange = onCleanDnsChange,
                    onOpenUpdate = { showMenu = false; showUpdateDialog = true },
                    onOpenVip = { showMenu = false; showVipDialog = true },
                    onOpenBest = { showMenu = false; showBest = true },
                    onOpenAppLogs = { showMenu = false; showLog = true },
                    onOpenCoreLogs = {
                        showMenu = false
                        startActivity(Intent(this@MainActivity, LogcatActivity::class.java))
                    },
                    onDismiss = { showMenu = false }
                )
            }

            if (showBest) {
                PennyBestConfigsDialog(
                    appLang = appLang,
                    servers = activeList.toList(),
                    selectedGuid = uiState.selectedGuid,
                    isRunning = isRunning,
                    isVipMode = isVipMode,
                    busy = busy,
                    onSelectServer = { entry ->
                        showBest = false
                        connectToEntry(entry)
                    },
                    onDismiss = { showBest = false }
                )
            }

            if (showVipDialog) {
                PennyVipSubscriptionDialog(
                    appLang = appLang,
                    deviceId = deviceId,
                    vipActive = vipActive,
                    vipCode = vipCode,
                    vipExpireMs = vipExpireMs,
                    vipUsedBytes = vipUsedBytes,
                    vipMaxBytes = vipMaxBytes,
                    effectiveNowMs = effectiveNowMs(),
                    useVipServers = useVipServers,
                    customVipSubUrl = customVipSubUrl,
                    onCopyDeviceId = { copyDeviceIdToClipboard() },
                    onUseVipServersChange = { enabled ->
                        useVipServers = enabled
                        store.putBoolean("use_vip_servers", enabled)
                        if (enabled) {
                            serviceTierMode = ServiceTierMode.DEDICATED
                            store.putString("service_tier_mode", ServiceTierMode.DEDICATED.name)
                        }
                    },
                    onCustomVipSubUrlChange = { clean ->
                        if (clean.isBlank()) {
                            customVipSubUrl = PennyServerScanner.DEDICATED_VPS_SUB_URL
                            store.putString("custom_vip_sub_url", customVipSubUrl)
                        } else if (VipLicenseManager.isTrustedHttpsEndpoint(clean) &&
                            PennyServerScanner.isSafeNestedSubscriptionUrl(clean)
                        ) {
                            customVipSubUrl = clean
                            store.putString("custom_vip_sub_url", clean)
                        } else {
                            statusMessage = "Only trusted public HTTPS subscription URLs are allowed"
                        }
                    },
                    onActivateLicense = { code, cb -> activateVipWithServerVerification(code, cb) },
                    onDismiss = { showVipDialog = false }
                )
            }

            if (showUpdateDialog) {
                PennyUpdateCenterDialog(
                    appLang = appLang,
                    availableUpdate = availableUpdate,
                    bpbFragmentMode = bpbFragmentMode,
                    busy = busy,
                    updateDownloading = updateDownloading,
                    updateDownloadProgress = updateDownloadProgress,
                    updateDownloadStatus = updateDownloadStatus,
                    onUpdateInfoDiscovered = { info -> availableUpdate = info },
                    onSelectFragmentMode = { modeName ->
                        bpbFragmentMode = modeName
                        store.putString("bpb_frag_mode", modeName)
                        if (modeName == "SMART") {
                            PennyServerScanner.applyBpbCoreOptimizations(fragLength = "10-30", fragInterval = "1")
                        } else {
                            PennyServerScanner.applyBpbCoreOptimizations(fragLength = "100-200", fragInterval = "1")
                        }
                    },
                    onRunNetworkDoctor = {
                        PennyServerScanner.runNetworkDoctor(this@MainActivity, ::plog)
                    },
                    onScanServers = {
                        showUpdateDialog = false
                        startScanOnly()
                    },
                    onStartAppUpdate = { url -> updateCoordinator.startFullAppUpdate(url) },
                    onDismiss = { showUpdateDialog = false }
                )
            }

            if (showAdConsentDialog) {
                PennyAdConsentDialog(
                    onAccept = {
                        showAdConsentDialog = false
                        adCoordinator.setUserConsent(true)
                        adCoordinator.openSponsorQuotaFlow(
                            adsClaimedToday = adsClaimedToday,
                            maxDailyAds = MAX_DAILY_SPONSORED_ADS
                        )
                    },
                    onDismiss = { showAdConsentDialog = false }
                )
            }

            if (showAadsSponsorDialog) {
                PennyAadsSponsorDialog(
                    activeUnitId = currentAadsUnitId,
                    waitSec = PennyAdCoordinator.AADS_SPONSOR_WAIT_SEC,
                    onRotateUnit = {
                        val nextUnit = adCoordinator.nextRotatedAadsUnitId()
                        currentAadsUnitId = nextUnit
                        nextUnit
                    },
                    onClose = { claimBonus ->
                        showAadsSponsorDialog = false
                        if (claimBonus) {
                            claimStartIoRewardBonus()
                        } else {
                            statusMessage = "Sponsor banner closed before reward completed"
                        }
                        val resume = aadsShouldResumeVpn
                        aadsShouldResumeVpn = false
                        resumeVpnAfterAdIfNeeded(resume)
                    }
                )
            }

            if (showLog) {
                PennyLogDialog(
                    appLang = appLang,
                    logLines = logLines.toList(),
                    onCopyLogs = { copyLogToClipboard() },
                    onClearLogs = { logLines.clear() },
                    onDismiss = { showLog = false }
                )
            }
        }
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
                store.putString("service_tier_mode", ServiceTierMode.PUBLIC.name)
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

    private fun flushQuotaStateNow() {
        ensureDailyDeviceQuotaCurrent()
        store.putLong("daily_used_bytes_$deviceId", dailyUsedBytes)
        store.putLong("pending_daily_unsynced_bytes_$deviceId", pendingDailyUnsyncedBytes)
        store.putLong("free_session_rem_sec", freeSessionRemainingSec)
        store.putLong("vip_used_bytes", vipUsedBytes)
        store.putLong("pending_unsynced_bytes", pendingUnsyncedBytes)
        VipLicenseManager.writeTamperResistantDailyUsed(this, deviceId, dailyQuotaDayKey, dailyUsedBytes)
        if (vipActive && pendingUnsyncedBytes > 0L) {
            syncServerQuotaInBackground(pendingUnsyncedBytes)
        } else if (!vipActive && pendingDailyUnsyncedBytes > 0L) {
            syncDailyDedicatedQuotaInBackground(pendingDailyUnsyncedBytes)
        }
    }

    private fun disconnect() {
        disconnectEpoch++
        workJob?.cancel()
        monitorActionJob?.cancel()
        userWantsConnected = false
        phase = Phase.IDLE
        scanProgress = 0f
        store.putBoolean("user_connected", false)
        store.remove("connect_time")
        flushQuotaStateNow()
        runCatching { PennySpeedLimiter.stop() }
        runCatching { LauncherManager.stopService(this) }
        statusMessage = "Disconnected"
        connectStartMs = 0L
        plog("Disconnected by user (auto-reconnect locked off)")
        adCoordinator.showInterstitialIfReady(
            lastShownMs = lastInterstitialShownMs,
            onUpdateLastShownMs = { lastInterstitialShownMs = it },
            allowResumeVpn = false
        )
    }

    private fun cancelWork() {
        disconnectEpoch++
        workJob?.cancel()
        monitorActionJob?.cancel()
        userWantsConnected = false
        store.putBoolean("user_connected", false)
        store.remove("connect_time")
        flushQuotaStateNow()
        MessageHelper.sendMsg2TestService(
            this, TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_CANCEL)
        )
        runCatching { LauncherManager.stopService(this) }
        phase = Phase.IDLE
        scanProgress = 0f
        statusMessage = "Cancelled"
        plog("Operation cancelled by user")
    }

    private fun startSmartConnect() {
        if (phase != Phase.IDLE) return
        userWantsConnected = true
        store.putBoolean("user_connected", true)
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
                            store.putLong("free_session_rem_sec", freeSessionRemainingSec)
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

    private fun safeRestartVpnOnMainThread() {
        val capturedEpoch = disconnectEpoch
        lifecycleScope.launch(Dispatchers.Main) {
            runCatching {
                if (!userWantsConnected || disconnectEpoch != capturedEpoch) return@runCatching
                if (mainViewModel.uiState.value.isRunning) {
                    LauncherManager.stopService(this@MainActivity)
                    delay(650L)
                }
                if (!userWantsConnected || disconnectEpoch != capturedEpoch) return@runCatching
                if (SettingsManager.isVpnMode() && VpnService.prepare(this@MainActivity) != null) {
                    return@runCatching
                }
                LauncherManager.startService(this@MainActivity)
            }.onFailure { e ->
                plog("Safe restart warning: ${e.message}")
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
        userWantsConnected = true
        store.putBoolean("user_connected", true)
        phase = Phase.CONNECTING
        statusMessage = "Switching to ${entry.label}..."
        workJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ping = connectAndVerify(entry.guid)
                if (ping > 0 && userWantsConnected) {
                    markConnected(entry.guid, ping)
                    ui { statusMessage = "Connected • ${entry.label}" }
                } else if (userWantsConnected) {
                    ui { statusMessage = "Server did not respond; try another one." }
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
                if (!userWantsConnected) continue
                if (checkVipExpiration()) continue

                val running = mainViewModel.uiState.value.isRunning
                if (!running && userWantsConnected && (autoReconnect || killSwitchEnabled) && activeList.isNotEmpty()) {
                    plog("Unexpected VPN drop detected; running auto-reconnect...")
                    monitorActionJob = lifecycleScope.launch(Dispatchers.IO) {
                        triggerAutoReconnect()
                    }
                    monitorActionJob?.join()
                    continue
                }

                if (!autoSwitch || !running || !userWantsConnected) continue
                try {
                    monitorActionJob = lifecycleScope.launch(Dispatchers.IO) {
                        healthCheckAndSwitch()
                    }
                    monitorActionJob?.join()
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
        val capturedEpoch = disconnectEpoch
        if (!userWantsConnected) return
        if (connectStartMs > 0L && System.currentTimeMillis() - connectStartMs < 35_000L) return
        val currentRx = TrafficStats.getUidRxBytes(Process.myUid())
        if (currentRx > 0L) {
            val deltaRx = currentRx - lastHealthCheckRxBytes
            lastHealthCheckRxBytes = currentRx
            if (deltaRx > 16_384L) return
        }

        var ok = false
        for (i in 0 until 3) {
            if (!userWantsConnected || disconnectEpoch != capturedEpoch) return
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
        if (ok || !userWantsConnected || disconnectEpoch != capturedEpoch) return

        val current = mainViewModel.uiState.value.selectedGuid
        if (current != null) {
            val nativePing = withContext(Dispatchers.IO) {
                runCatching {
                    CoreNativeManager.initCoreEnv(this@MainActivity)
                    val cfg = CoreConfigManager.getV2rayConfig4Speedtest(this@MainActivity, current)
                    if (cfg.status) CoreNativeManager.measureOutboundDelay(cfg.content, SettingsManager.getDelayTestUrl()) else -1L
                }.getOrDefault(-1L)
            }
            if (nativePing > 0L || !userWantsConnected || disconnectEpoch != capturedEpoch) return
        }

        if (!userWantsConnected || disconnectEpoch != capturedEpoch) return
        plog("Active tunnel unresponsive; auto-switching...")
        if (current != null) failedGuids.add(current)
        ui {
            if (userWantsConnected && disconnectEpoch == capturedEpoch) {
                phase = Phase.CONNECTING
                statusMessage = "Connection dropped; auto-switching..."
            }
        }
        try {
            if (!userWantsConnected || disconnectEpoch != capturedEpoch) return
            val r = connectFromList(startAfter = current)
            ui {
                if (userWantsConnected && disconnectEpoch == capturedEpoch) {
                    statusMessage = if (r != null) "Switched to healthier server" else "No healthy server found; please scan"
                }
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
            runCatching { PennyServerScanner.fetchRemoteEdgeConfig(licenseServerUrl, ::plog) }
        }
        PennyServerScanner.selectBestGhostTransport(currentMode, ::plog)
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
                    val isRemoteVault = url == PennyServerScanner.STEALTH_VAULT_GITHUB_URL
                    val fetched = PennyServerScanner.fetchSignedListWithFallback(
                        urlStr = url,
                        onLog = ::plog,
                        requireSigned = isRemoteVault
                    )
                    if (fetched.isNotEmpty()) {
                        fetched
                    } else if (isRemoteVault) {
                        val bundled = PennyServerScanner.readBundledStealthVaultAsset(this@MainActivity)
                        if (bundled.isNotEmpty()) {
                            plog("Using APK-signed bundled stealth vault fallback")
                            PennyServerScanner.extractPlayableConfigs(bundled, ::plog)
                        } else ""
                    } else {
                        ""
                    }
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
        if (!userWantsConnected) {
            runCatching { LauncherManager.stopService(this@MainActivity) }
            return
        }
        val now = System.currentTimeMillis()
        val currentMode = serviceTierMode
        store.putString("last_guid", guid)
        store.putLong("connect_time", now)
        store.putBoolean("user_connected", true)
        ui {
            if (!userWantsConnected) return@ui
            connectStartMs = now
            lastConnectedGuid = guid
            userWantsConnected = true
            if (verifiedPingMs > 0L) {
                val targetList = activeList
                val idx = targetList.indexOfFirst { it.guid == guid }
                if (idx in targetList.indices) {
                    targetList[idx] = targetList[idx].copy(ms = verifiedPingMs)
                    saveList(targetList.toList(), activeScanTime, mode = currentMode)
                }
            }
        }
        syncDailyDedicatedQuotaInBackground(0L)
    }

    private suspend fun connectAndVerify(guid: String): Long {
        val epochAtStart = disconnectEpoch
        if (!userWantsConnected) return -1L
        if (MmkvManager.decodeServerConfig(guid) == null) {
            plog("Skipped missing server config for $guid")
            return -1L
        }
        PennySpeedLimiter.isVipUnrestricted = vipActive
        PennyServerScanner.syncGhostModeKernelRules(serviceTierMode, ghostMessagingOnly, splitTunnelLocal)
        PennyServerScanner.selectBestGhostTransport(serviceTierMode, ::plog)
        val wasRunning = mainViewModel.uiState.value.isRunning

        if (wasRunning) {
            withContext(Dispatchers.Main) {
                runCatching { LauncherManager.stopService(this@MainActivity) }
            }
            delay(650L)
        }

        if (!userWantsConnected || disconnectEpoch != epochAtStart) {
            return -1L
        }

        withContext(Dispatchers.Main) {
            if (!userWantsConnected || disconnectEpoch != epochAtStart) return@withContext
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
            if (!userWantsConnected || disconnectEpoch != epochAtStart) {
                withContext(Dispatchers.Main) { runCatching { LauncherManager.stopService(this@MainActivity) } }
                return -1L
            }
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
            if (!userWantsConnected || disconnectEpoch != epochAtStart) {
                withContext(Dispatchers.Main) { runCatching { LauncherManager.stopService(this@MainActivity) } }
                return -1L
            }
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

        val probeUrls = PennyServerScanner.TEST_URLS.take(2)
        for (url in probeUrls) {
            if (!userWantsConnected || disconnectEpoch != epochAtStart) {
                withContext(Dispatchers.Main) { runCatching { LauncherManager.stopService(this@MainActivity) } }
                return -1L
            }
            val dlMs = withContext(Dispatchers.IO) {
                PennyServerScanner.testThroughLocalProxy(url, socksPort, ::plog)
            }
            if (dlMs > 0L) {
                if (!userWantsConnected || disconnectEpoch != epochAtStart) {
                    withContext(Dispatchers.Main) { runCatching { LauncherManager.stopService(this@MainActivity) } }
                    return -1L
                }
                MmkvManager.encodeServerTestDelayMillis(guid, dlMs)
                return dlMs
            }
        }
        return -1L
    }
}
