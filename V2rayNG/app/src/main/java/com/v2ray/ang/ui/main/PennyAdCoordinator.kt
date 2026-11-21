package com.v2ray.ang.ui.main

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.v2ray.ang.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * هماهنگ‌کننده تبلیغات Penny VPN (Start.io + AADS Round-Robin):
 * - راه‌اندازی Start.io SDK تنها پس از رضایت صریح کاربر انجام می‌شود.
 * - اجرای SDK اختصاصی در نسخه F-Droid غیرفعال است.
 * - چرخش متوازن بین دو واحد بیت‌کوینی AADS (2462374 و 2462387).
 */
internal class PennyAdCoordinator(
    private val context: Context,
    private val store: PennyPreferencesStore,
    private val scope: CoroutineScope,
    private val isVipActive: () -> Boolean,
    private val isVpnUiRunning: () -> Boolean,
    private val pauseVpnForDirectIpAd: suspend () -> Boolean,
    private val onResumeVpnAfterAd: (Boolean) -> Unit,
    private val onShowConsentDialog: () -> Unit,
    private val onOpenAadsSponsorDialog: (unitId: String, shouldResumeVpn: Boolean) -> Unit,
    private val onStatusMessage: (String) -> Unit,
    private val onLog: (String) -> Unit
) {
    companion object {
        const val STARTIO_APP_ID = "210814549"
        const val AADS_AD_UNIT_ID = "2462374"
        const val AADS_SECONDARY_UNIT_ID = "2462387"
        val AADS_AD_UNITS = listOf(AADS_AD_UNIT_ID, AADS_SECONDARY_UNIT_ID)
        const val AADS_SPONSOR_PAGE_URL = "https://o77ss0120-max.github.io/v2rayNG/"
        const val AADS_SPONSOR_WAIT_SEC = 8
        const val INTERSTITIAL_COOLDOWN_MS = 10 * 60 * 1000L
    }

    private var startIoInterstitialAd: StartAppAd? = null
    private var startIoInitialized = false

    private val isProprietarySdkAllowed: Boolean
        get() = !BuildConfig.DISTRIBUTION.equals("F-Droid", ignoreCase = true)

    fun hasUserConsent(): Boolean = store.getBoolean("startio_ads_consent", false)

    fun setUserConsent(granted: Boolean) {
        store.putBoolean("startio_ads_consent", granted)
        if (granted) {
            initSdkIfConsented()
            runCatching {
                if (isProprietarySdkAllowed) {
                    StartAppSDK.setUserConsent(context, "pas", System.currentTimeMillis(), true)
                }
            }
        }
    }

    fun initSdkIfConsented() {
        if (isVipActive() || !isProprietarySdkAllowed) return
        if (!hasUserConsent() || startIoInitialized) return
        runCatching {
            StartAppSDK.initParams(context, STARTIO_APP_ID)
                .setReturnAdsEnabled(false)
                .init()
            StartAppSDK.setUserConsent(context, "pas", System.currentTimeMillis(), true)
            StartAppAd.disableSplash()
            startIoInitialized = true
            loadInterstitialIfIdle()
            onLog("Start.io SDK initialized after explicit consent (ReturnAds=false)")
        }.onFailure { e ->
            onLog("Start.io init warning: ${e.message}")
        }
    }

    fun isVpnTransportActive(): Boolean {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val activeNet = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNet) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }.getOrDefault(false)
    }

    fun loadInterstitialIfIdle() {
        if (isVipActive() || !isProprietarySdkAllowed || !hasUserConsent()) return
        if (!startIoInitialized || isVpnUiRunning() || isVpnTransportActive()) return
        runCatching {
            val ad = StartAppAd(context)
            ad.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
                override fun onReceiveAd(receivedAd: Ad) {
                    startIoInterstitialAd = ad
                    onLog("Start.io Interstitial preloaded on direct user IP")
                }

                override fun onFailedToReceiveAd(failedAd: Ad?) {
                    startIoInterstitialAd = null
                    onLog("Start.io preload note: ${failedAd?.errorMessage ?: "waiting for inventory"}")
                }
            })
        }
    }

    fun nextRotatedAadsUnitId(): String {
        val idx = (store.getInt("aads_unit_rot_idx", 0) % AADS_AD_UNITS.size + AADS_AD_UNITS.size) % AADS_AD_UNITS.size
        val chosen = AADS_AD_UNITS[idx]
        store.putInt("aads_unit_rot_idx", (idx + 1) % AADS_AD_UNITS.size)
        return chosen
    }

    fun openSponsorQuotaFlow(adsClaimedToday: Int, maxDailyAds: Int) {
        if (isVipActive()) {
            onStatusMessage("VIP Plan active (Ad-Free)")
            return
        }
        if (adsClaimedToday >= maxDailyAds) {
            onStatusMessage("Daily limit reached ($maxDailyAds of $maxDailyAds claimed today)")
            return
        }
        if (!hasUserConsent()) {
            onShowConsentDialog()
            return
        }
        initSdkIfConsented()
        scope.launch(Dispatchers.Main) {
            val chosenUnit = nextRotatedAadsUnitId()
            val wasVpnRunning = pauseVpnForDirectIpAd()
            onStatusMessage("Loading AADS Sponsor Banner #$chosenUnit (+100 MB)...")
            onLog("Opening AADS Sponsor View (Rotating Unit: $chosenUnit)")
            onOpenAadsSponsorDialog(chosenUnit, wasVpnRunning)
        }
    }

    fun showInterstitialIfReady(
        lastShownMs: Long,
        onUpdateLastShownMs: (Long) -> Unit,
        allowResumeVpn: Boolean = false
    ) {
        if (isVipActive() || !isProprietarySdkAllowed || !hasUserConsent()) return
        val now = System.currentTimeMillis()
        if (now - lastShownMs < INTERSTITIAL_COOLDOWN_MS) {
            val waitSec = ((INTERSTITIAL_COOLDOWN_MS - (now - lastShownMs)) / 1000L).coerceAtLeast(1L)
            onLog("Interstitial skipped by 10m Frequency Cap (${waitSec}s remaining)")
            return
        }
        initSdkIfConsented()
        scope.launch(Dispatchers.Main) {
            val wasVpnRunning = pauseVpnForDirectIpAd()
            val shouldResumeAfter = allowResumeVpn && wasVpnRunning
            val startAd = startIoInterstitialAd
            if (startAd != null && startAd.isReady) {
                onUpdateLastShownMs(now)
                store.putLong("last_interstitial_ms", now)
                startIoInterstitialAd = null
                startAd.showAd(object : AdDisplayListener {
                    override fun adHidden(ad: Ad?) {
                        loadInterstitialIfIdle()
                        onResumeVpnAfterAd(shouldResumeAfter)
                    }

                    override fun adDisplayed(ad: Ad?) {
                        onLog("Start.io Interstitial displayed on user's real IP")
                    }

                    override fun adClicked(ad: Ad?) {}

                    override fun adNotDisplayed(ad: Ad?) {
                        loadInterstitialIfIdle()
                        onResumeVpnAfterAd(shouldResumeAfter)
                    }
                })
                return@launch
            }
            loadInterstitialIfIdle()
            if (shouldResumeAfter) {
                delay(450L)
                onResumeVpnAfterAd(true)
            }
        }
    }
}
