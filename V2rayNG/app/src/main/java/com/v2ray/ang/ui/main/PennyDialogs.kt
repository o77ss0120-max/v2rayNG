package com.v2ray.ang.ui.main

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * تمام دیالوگ‌ها و بنر آپدیت Penny VPN (جداشده از MainActivity.kt برای کاهش پیچیدگی و تست‌پذیری)
 */

@Composable
internal fun PennyAppUpdateBanner(
    updateInfo: PennyServerScanner.AppUpdateInfo,
    isDownloading: Boolean,
    progress: Float,
    statusNote: String?,
    onUpdateClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = Color(0xFF1B1D26),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .border(1.dp, Color(0xFFE6CFAF).copy(alpha = 0.55f), RoundedCornerShape(14.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color(0xFFE6CFAF).copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        UpdateArrowIcon(size = 16.dp, color = Color(0xFFE6CFAF))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Update Available • v${updateInfo.latestVersion}",
                            color = Color(0xFFF2F4F8),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when {
                                isDownloading -> "Downloading APK ${(progress * 100).toInt()}%..."
                                !statusNote.isNullOrBlank() -> statusNote
                                updateInfo.releaseNotes.isNotBlank() -> updateInfo.releaseNotes.replace('\n', ' ')
                                else -> "Tap Update Now for automatic in-app install"
                            },
                            color = Color(0xFFB5BAC9),
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onUpdateClick,
                        enabled = !isDownloading,
                        shape = RoundedCornerShape(9.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE6CFAF),
                            contentColor = Color(0xFF14151B),
                            disabledContainerColor = Color(0xFF2B2E3A),
                            disabledContentColor = Color(0xFFE6CFAF)
                        ),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = if (isDownloading) "${(progress * 100).toInt()}%" else "Update",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    if (!isDownloading) {
                        Text(
                            text = "×",
                            color = Color(0xFF9EA2B0),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onDismiss() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (isDownloading) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0.02f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFFE6CFAF),
                    trackColor = Color(0xFF2B2E3A)
                )
            }
        }
    }
}

@Composable
internal fun PennyAadsSponsorDialog(
    activeUnitId: String,
    waitSec: Int = PennyAdCoordinator.AADS_SPONSOR_WAIT_SEC,
    onRotateUnit: () -> String,
    onClose: (claimBonus: Boolean) -> Unit
) {
    val context = LocalContext.current
    var currentUnit by remember(activeUnitId) { mutableStateOf(activeUnitId) }
    var pageLoaded by remember { mutableStateOf(false) }
    var usedInlineFallback by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableStateOf(waitSec) }
    var reloadToken by remember { mutableStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    fun buildInlineFallbackHtml(unitId: String): String {
        val frameSnippet = if (unitId == PennyAdCoordinator.AADS_SECONDARY_UNIT_ID) {
            """
            <div id="frame" style="width:100%; max-width:468px; margin:auto; z-index:99998; height:auto;">
              <iframe data-aa='2462387' src='https://ad.a-ads.com/2462387/?size=468x60'
                      style='border:0; padding:0; width:100%; max-width:468px; height:60px; overflow:hidden; display:block; margin:auto'></iframe>
              <div style="width:100%; margin:auto; text-align:center;">
                <a target="_blank" style="display:inline-block;font-size:12px;color:#263238;padding:3px 10px;background:#F8F8F9;text-decoration:none;border-radius:0 0 4px 4px;" id="frame-link" href="https://aads.com/campaigns/new/?source_id=2462387&source_type=ad_unit&partner=2462387">Advertise here</a>
              </div>
            </div>
            """.trimIndent()
        } else {
            """
            <div id="frame" style="width: 100%;margin: auto;position: relative; z-index: 99998;">
              <iframe data-aa='2462374' src='https://acceptable.a-ads.com/2462374/?size=Adaptive'
                      style='border:0; padding:0; width:100%; min-height:220px; height:auto; overflow:hidden; display:block; margin:auto'></iframe>
            </div>
            """.trimIndent()
        }
        return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0" />
          <style>
            * { box-sizing: border-box; }
            html, body {
              margin: 0;
              padding: 8px;
              width: 100%;
              min-height: 100%;
              background: #0f1118;
              color: #f5efe6;
              font-family: -apple-system, BlinkMacSystemFont, sans-serif;
              display: flex;
              flex-direction: column;
              align-items: center;
              justify-content: center;
              text-align: center;
            }
            .tag {
              font-size: 11px;
              color: #b5bac9;
              margin-bottom: 10px;
              letter-spacing: 0.4px;
            }
            #frame iframe {
              border: 0 !important;
              border-radius: 10px;
              display: block !important;
              margin: 0 auto !important;
            }
          </style>
        </head>
        <body>
          <div class="tag">Sponsored Content · AADS Unit #$unitId</div>
          $frameSnippet
        </body>
        </html>
        """.trimIndent()
    }

    LaunchedEffect(pageLoaded, reloadToken) {
        if (!pageLoaded) {
            delay(6000L)
            pageLoaded = true
        }
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft -= 1
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { webViewRef?.destroy() }
            webViewRef = null
        }
    }

    val elapsedRatio = ((waitSec - secondsLeft).toFloat() / waitSec.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

    Dialog(
        onDismissRequest = { onClose(false) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color(0xFF151720),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .border(1.dp, Color(0xFFE6CFAF).copy(alpha = 0.35f), RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Sponsor Banner • #$currentUnit (+100 MB)",
                            color = Color(0xFFF2F4F8),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (secondsLeft > 0) {
                                "Displaying sponsor partner • ${secondsLeft}s remaining"
                            } else {
                                "Sponsor session ready • Claim +100 MB & +2h"
                            },
                            color = if (secondsLeft > 0) Color(0xFFB5BAC9) else Color(0xFF6EE7B7),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            color = Color(0xFF232634),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val nextUnit = onRotateUnit()
                                    currentUnit = nextUnit
                                    pageLoaded = false
                                    usedInlineFallback = false
                                    reloadToken += 1
                                    webViewRef?.loadUrl(
                                        "${PennyAdCoordinator.AADS_SPONSOR_PAGE_URL}?inapp=1&unit=$nextUnit&_r=${System.currentTimeMillis()}"
                                    )
                                }
                        ) {
                            Text(
                                text = "↻",
                                color = Color(0xFFD8DCE6),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }

                        Surface(
                            color = Color(0xFF232634),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("${PennyAdCoordinator.AADS_SPONSOR_PAGE_URL}?unit=$currentUnit")
                                            )
                                        )
                                    }
                                }
                        ) {
                            Text(
                                text = "↗",
                                color = Color(0xFFE6CFAF),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                LinearProgressIndicator(
                    progress = { elapsedRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (secondsLeft <= 0) Color(0xFF6EE7B7) else Color(0xFFE6CFAF),
                    trackColor = Color(0xFF272A38)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Color(0xFF323646), RoundedCornerShape(14.dp))
                        .background(Color(0xFF0F1118))
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setBackgroundColor(android.graphics.Color.parseColor("#0F1118"))
                                isVerticalScrollBarEnabled = false
                                isHorizontalScrollBarEnabled = false
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    allowFileAccess = false
                                    allowContentAccess = false
                                    @Suppress("DEPRECATION")
                                    allowFileAccessFromFileURLs = false
                                    @Suppress("DEPRECATION")
                                    allowUniversalAccessFromFileURLs = false
                                    mediaPlaybackRequiresUserGesture = true
                                }
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        pageLoaded = true
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val target = request?.url ?: return true
                                        val scheme = target.scheme?.lowercase(Locale.US) ?: ""
                                        val host = target.host?.lowercase(Locale.US) ?: ""
                                        if (scheme != "https") return true
                                        if (host.endsWith("github.io") || host.endsWith("a-ads.com")) {
                                            return false
                                        }
                                        runCatching {
                                            ctx.startActivity(Intent(Intent.ACTION_VIEW, target))
                                        }
                                        return true
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?
                                    ) {
                                        if (request?.isForMainFrame == true && !usedInlineFallback) {
                                            usedInlineFallback = true
                                            view?.loadDataWithBaseURL(
                                                PennyAdCoordinator.AADS_SPONSOR_PAGE_URL,
                                                buildInlineFallbackHtml(currentUnit),
                                                "text/html",
                                                "UTF-8",
                                                null
                                            )
                                        }
                                    }

                                    override fun onReceivedHttpError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        errorResponse: WebResourceResponse?
                                    ) {
                                        if (request?.isForMainFrame == true &&
                                            (errorResponse?.statusCode ?: 0) >= 400 &&
                                            !usedInlineFallback
                                        ) {
                                            usedInlineFallback = true
                                            view?.loadDataWithBaseURL(
                                                PennyAdCoordinator.AADS_SPONSOR_PAGE_URL,
                                                buildInlineFallbackHtml(currentUnit),
                                                "text/html",
                                                "UTF-8",
                                                null
                                            )
                                        }
                                    }
                                }
                                loadUrl("${PennyAdCoordinator.AADS_SPONSOR_PAGE_URL}?inapp=1&unit=$currentUnit")
                                webViewRef = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (!pageLoaded) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xCC0F1118)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFFE6CFAF),
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = "Loading sponsor banner...",
                                    color = Color(0xFFB5BAC9),
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { onClose(false) },
                        shape = RoundedCornerShape(11.dp),
                        modifier = Modifier.weight(0.38f)
                    ) {
                        Text("Close", color = Color(0xFF9EA2B0), fontSize = 12.sp)
                    }

                    Button(
                        enabled = secondsLeft <= 0,
                        onClick = { onClose(true) },
                        shape = RoundedCornerShape(11.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE6CFAF),
                            contentColor = Color(0xFF14151B),
                            disabledContainerColor = Color(0xFF262936),
                            disabledContentColor = Color(0xFF7D818E)
                        ),
                        modifier = Modifier.weight(0.62f)
                    ) {
                        Text(
                            text = if (secondsLeft > 0) "Wait (${secondsLeft}s)" else "Claim +100 MB & Reconnect",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PennyAdConsentDialog(
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF191A20),
        title = {
            Text(
                text = "Sponsored Ad Privacy Notice (+100 MB)",
                color = Color(0xFFF2F4F8),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
        },
        text = {
            Text(
                text = "Sponsored banners rotate across AADS units (${PennyAdCoordinator.AADS_AD_UNIT_ID} & ${PennyAdCoordinator.AADS_SECONDARY_UNIT_ID}). Important privacy notice: to load ads, the VPN tunnel pauses briefly so your device's direct network IP reaches the ad partner, then reconnects automatically. Only agree if you are comfortable exposing your direct IP to the ad network.",
                color = Color(0xFFB5BAC9),
                fontSize = 12.5.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text("Agree & View Sponsor", color = Color(0xFFE6CFAF), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF9EA2B0))
            }
        }
    )
}

@Composable
internal fun PennyModernMenuDialog(
    appLang: AppLang,
    isVipMode: Boolean,
    activeServerCount: Int,
    autoSwitch: Boolean,
    onAutoSwitchChange: (Boolean) -> Unit,
    autoReconnect: Boolean,
    onAutoReconnectChange: (Boolean) -> Unit,
    splitTunnelLocal: Boolean,
    onSplitTunnelChange: (Boolean) -> Unit,
    cleanDnsShield: Boolean,
    onCleanDnsShieldChange: (Boolean) -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenVip: () -> Unit,
    onOpenBest: () -> Unit,
    onOpenAppLogs: () -> Unit,
    onOpenCoreLogs: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF191A20),
        confirmButton = {
            TextButton(onClick = onDismiss) {
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
                        .clickable { onOpenUpdate() }
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
                                text = "App & Server Update (v${com.v2ray.ang.BuildConfig.VERSION_NAME})",
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
                        .clickable { onOpenVip() }
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
                        .clickable { onOpenBest() }
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
                        Text("$activeServerCount", color = Color(0xFFD8DCE6), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                MenuToggleCard("Auto-Switch on Drop", null, autoSwitch, onAutoSwitchChange)
                MenuToggleCard("Auto-Reconnect on Net Change", null, autoReconnect, onAutoReconnectChange)
                MenuToggleCard(
                    "Smart Split Tunneling",
                    "Bypass local & banking apps automatically",
                    splitTunnelLocal,
                    onSplitTunnelChange
                )
                MenuToggleCard(
                    "Clean DNS & Tracker Shield",
                    "Encrypted DNS (1.1.1.2) • Malware & tracker block",
                    cleanDnsShield,
                    onCleanDnsShieldChange
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenAppLogs,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(Strings.menuAppLog(appLang), fontSize = 11.sp, maxLines = 1, color = Color(0xFFD8DCE6))
                    }
                    OutlinedButton(
                        onClick = onOpenCoreLogs,
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
private fun MenuToggleCard(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
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
                Text(title, fontSize = 13.sp, color = Color(0xFFF2F4F8), fontWeight = FontWeight.SemiBold)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, fontSize = 10.sp, color = Color(0xFF9EA1AB))
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = metallicSwitchColors()
            )
        }
    }
}

@Composable
internal fun PennyVipSubscriptionDialog(
    appLang: AppLang,
    deviceId: String,
    vipActive: Boolean,
    vipCode: String,
    vipExpireMs: Long,
    vipUsedBytes: Long,
    vipMaxBytes: Long,
    effectiveNowMs: Long,
    useVipServers: Boolean,
    customVipSubUrl: String,
    onCopyDeviceId: () -> Unit,
    onUseVipServersChange: (Boolean) -> Unit,
    onCustomVipSubUrlChange: (String) -> Unit,
    onActivateLicense: (code: String, onComplete: (Boolean, String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var inputCode by remember { mutableStateOf("") }
    var subUrlInput by remember(customVipSubUrl) { mutableStateOf(customVipSubUrl) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF191A20),
        confirmButton = {
            TextButton(onClick = onDismiss) {
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
                            onCopyDeviceId()
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
                            fontSize = 11.sp,
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
                    val remDays = ((vipExpireMs - effectiveNowMs) / (24L * 3600_000L)).coerceAtLeast(0L)
                    val remHours = (((vipExpireMs - effectiveNowMs) % (24L * 3600_000L)) / 3600_000L).coerceAtLeast(0L)
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
                            onCheckedChange = onUseVipServersChange,
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
                            onCustomVipSubUrlChange(subUrlInput.trim())
                            isVerifying = true
                            onActivateLicense(inputCode) { ok, msg ->
                                isVerifying = false
                                isSuccess = ok
                                feedback = msg
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
                        onCustomVipSubUrlChange(it.trim())
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
internal fun PennyBestConfigsDialog(
    appLang: AppLang,
    servers: List<PennyServer>,
    selectedGuid: String?,
    isRunning: Boolean,
    isVipMode: Boolean,
    busy: Boolean,
    onSelectServer: (PennyServer) -> Unit,
    onDismiss: () -> Unit
) {
    val listToShow = remember(servers) { servers.sortedBy { it.ms } }
    var isGlobalBubbleView by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF191A20),
        confirmButton = {
            TextButton(onClick = onDismiss) {
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
                                    .clickable(enabled = !busy) { onSelectServer(s) },
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
                                    .clickable(enabled = !busy) { onSelectServer(s) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
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
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = s.label,
                                            color = Color(0xFFF2F4F8),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (isOwn) "★ VIP MODE · Balanced ($tagText)" else "PUBLIC · Community Node",
                                            color = if (isOwn) Color(0xFFE6CFAF) else Color(0xFF9EA2AE),
                                            fontSize = 9.5.sp,
                                            fontWeight = if (isOwn) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        ShinyGreenPingBadge(ms = s.ms, showDiamond = s.isVipServer)
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
internal fun PennyUpdateCenterDialog(
    appLang: AppLang,
    availableUpdate: PennyServerScanner.AppUpdateInfo?,
    bpbFragmentMode: String,
    busy: Boolean,
    updateDownloading: Boolean,
    updateDownloadProgress: Float,
    updateDownloadStatus: String?,
    onUpdateInfoDiscovered: (PennyServerScanner.AppUpdateInfo) -> Unit,
    onSelectFragmentMode: (String) -> Unit,
    onRunNetworkDoctor: suspend () -> String,
    onScanServers: () -> Unit,
    onStartAppUpdate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val installedVer = com.v2ray.ang.BuildConfig.VERSION_NAME
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(true) }
    var updateInfo by remember {
        mutableStateOf(
            availableUpdate ?: PennyServerScanner.AppUpdateInfo(
                currentVersion = installedVer,
                latestVersion = installedVer,
                hasUpdate = false,
                downloadUrl = PennyServerScanner.GITHUB_RELEASES_PAGE,
                releaseNotes = ""
            )
        )
    }
    var statusNote by remember { mutableStateOf<String?>(null) }
    var diagnosing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        checking = true
        val info = withContext(Dispatchers.IO) {
            PennyServerScanner.checkAppUpdateOnGitHub(installedVer)
        }
        updateInfo = info
        if (info.hasUpdate) {
            onUpdateInfoDiscovered(info)
        }
        checking = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF191A20),
        confirmButton = {
            TextButton(onClick = onDismiss) {
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
                            onSelectFragmentMode("NORMAL")
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
                            onSelectFragmentMode("SMART")
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
                        scope.launch {
                            statusNote = onRunNetworkDoctor()
                            diagnosing = false
                        }
                    },
                    enabled = !diagnosing && !busy,
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
                    onClick = onScanServers,
                    enabled = !busy,
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

                Button(
                    onClick = { onStartAppUpdate(updateInfo.downloadUrl) },
                    enabled = !updateDownloading,
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (updateInfo.hasUpdate) Color(0xFFE6CFAF) else Color(0xFF2B2E3A),
                        contentColor = if (updateInfo.hasUpdate) Color(0xFF14151B) else Color(0xFFD8DCE6)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = when {
                            updateDownloading -> "Downloading Update (${(updateDownloadProgress * 100).toInt()}%)..."
                            updateInfo.hasUpdate -> "Update App Now (v${updateInfo.latestVersion})"
                            else -> "Install Latest Release APK"
                        },
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                if (updateDownloading) {
                    LinearProgressIndicator(
                        progress = { updateDownloadProgress.coerceIn(0.02f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFFE6CFAF),
                        trackColor = Color(0xFF2B2E3A)
                    )
                }

                val combinedNote = updateDownloadStatus ?: statusNote
                combinedNote?.let { note ->
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
internal fun PennyLogDialog(
    appLang: AppLang,
    logLines: List<String>,
    onCopyLogs: () -> Unit,
    onClearLogs: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.menuAppLog(appLang)) },
        text = {
            if (logLines.isEmpty()) {
                Text(Strings.emptyLogs(appLang))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(logLines.asReversed()) { line ->
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
                TextButton(onClick = onCopyLogs) { Text(Strings.copy(appLang)) }
                TextButton(onClick = onClearLogs) { Text(Strings.clear(appLang)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.close(appLang)) } }
    )
}
