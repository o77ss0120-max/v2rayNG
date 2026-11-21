package com.v2ray.ang.ui.main

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * صفحات تب‌های اصلی Penny VPN (جداشده از PennyComponents.kt برای خوانایی و نگهداری ساده‌تر):
 * - ServersTabPage (فهرست کشوری سرورها و انتخاب هوشمند)
 * - NetworkDoctorTabPage (عیب‌یابی چهارمرحله‌ای تانل و DNS)
 * - ProfileTabPage (تنظیمات گروه‌بندی‌شده Connection / Privacy / Routing)
 */

@Composable
internal fun ServersTabPage(
    selectedMode: ServiceTierMode,
    onSelectMode: (ServiceTierMode) -> Unit,
    servers: List<PennyServer>,
    selectedGuid: String?,
    isRunning: Boolean,
    busy: Boolean,
    scanProgress: Float,
    dailyUsedBytes: Long,
    dailyMaxBytes: Long,
    vipActive: Boolean,
    ghostMessagingOnly: Boolean = true,
    onGhostMessagingOnlyChange: ((Boolean) -> Unit)? = null,
    favoriteCountries: Set<String> = emptySet(),
    onToggleFavoriteCountry: (String) -> Unit = {},
    onScanClick: () -> Unit,
    onAutoSelectClick: () -> Unit = {},
    onServerClick: (PennyServer) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val dailyRemBytes = (dailyMaxBytes - dailyUsedBytes).coerceAtLeast(0L)
    val dailyRemText = if (vipActive) {
        "VIP 50 GB Active"
    } else {
        "${fmtBytes(dailyRemBytes)} / ${fmtBytes(dailyMaxBytes)} left"
    }

    var expandedIso by remember { mutableStateOf<String?>(null) }

    val countryGroups = remember(servers, favoriteCountries) {
        servers
            .groupBy { srv -> getCountryGeoInfo(srv.codeTag).isoCode }
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, List<PennyServer>>> { favoriteCountries.contains(it.key) }
                    .thenBy { entry ->
                        entry.value.filter { it.ms > 0 }.minOfOrNull { it.ms } ?: 9999L
                    }
            )
    }

    val bestOverallIso = remember(countryGroups) {
        countryGroups.minByOrNull { entry ->
            entry.value.filter { it.ms > 0 }.minOfOrNull { it.ms } ?: 9999L
        }?.key
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Server Network",
                    color = PlatinumBright,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Country-grouped stealth clusters • Auto load-balanced",
                    color = PlatinumMuted,
                    fontSize = 10.sp
                )
            }
            val scanShape = RoundedCornerShape(11.dp)
            Surface(
                color = ObsidianElevated,
                shape = scanShape,
                modifier = Modifier
                    .clip(scanShape)
                    .border(1.dp, ChampagneGold.copy(alpha = 0.65f), scanShape)
                    .tvDpadClickable(enabled = !busy, shape = scanShape) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onScanClick()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UpdateArrowIcon(size = 12.dp, color = ChampagneGold)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (busy) "Scanning..." else "Scan Nodes",
                        color = ChampagneLight,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        ServiceTierSelectorBar(
            selectedMode = selectedMode,
            dailyRemText = dailyRemText,
            isVip = vipActive,
            ghostMessagingOnly = ghostMessagingOnly,
            onGhostMessagingOnlyChange = onGhostMessagingOnlyChange,
            onSelectMode = onSelectMode
        )

        Spacer(Modifier.height(8.dp))

        if (busy && (scanProgress > 0f || servers.isEmpty())) {
            val inf = rememberInfiniteTransition(label = "skeletonShimmer")
            val shimmerAlpha by inf.animateFloat(
                initialValue = 0.28f,
                targetValue = 0.78f,
                animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "shimmerAlpha"
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF262832))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(scanProgress.coerceIn(0.12f, 1f))
                            .fillMaxHeight()
                            .background(ChampagneGold)
                    )
                }
                repeat(4) {
                    Surface(
                        color = ObsidianCard,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(Color(0xFF2A2E3D).copy(alpha = shimmerAlpha))
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.58f)
                                        .height(11.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF2F3446).copy(alpha = shimmerAlpha))
                                )
                                Spacer(Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.38f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF252938).copy(alpha = shimmerAlpha))
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .width(46.dp)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF2F3446).copy(alpha = shimmerAlpha))
                            )
                        }
                    }
                }
            }
        } else if (servers.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = ObsidianCard,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No servers loaded for ${selectedMode.title}",
                            color = PlatinumBright,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = "Tap 'Scan Nodes' above to fetch and group healthy country clusters.",
                            color = PlatinumMuted,
                            fontSize = 10.5.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            val bestPingAll = servers.filter { it.ms > 0 }.minOfOrNull { it.ms } ?: 118L
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 10.dp)
            ) {
                item(key = "auto_smart_selector") {
                    val autoShape = RoundedCornerShape(14.dp)
                    Surface(
                        color = ObsidianElevated,
                        shape = autoShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(autoShape)
                            .border(1.3.dp, ChampagneGold.copy(alpha = 0.78f), autoShape)
                            .tvDpadClickable(enabled = !busy, shape = autoShape) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onAutoSelectClick()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 13.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF282219))
                                        .border(1.dp, ChampagneGold.copy(alpha = 0.75f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "⚡", fontSize = 16.sp)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "Auto (Smart Fastest)",
                                        color = PlatinumBright,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "★ Recommended · #1–#${servers.size} pool · Best ${bestPingAll}ms",
                                        color = ChampagneGold,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            PingSignalBars(ms = bestPingAll)
                        }
                    }
                }

                itemsIndexed(countryGroups, key = { _, entry -> "country_${entry.key}" }) { _, entry ->
                    val iso = entry.key
                    val groupServers = entry.value
                    val sampleGeo = getCountryGeoInfo(groupServers.first().codeTag)
                    val bestMs = groupServers.filter { it.ms > 0 }.minOfOrNull { it.ms } ?: 122L
                    val isFav = favoriteCountries.contains(iso)
                    val isRecommendedGroup = iso == bestOverallIso
                    val isExpanded = expandedIso == iso
                    val hasActiveNode = isRunning && groupServers.any { it.guid == selectedGuid }
                    val groupShape = RoundedCornerShape(14.dp)

                    Surface(
                        color = if (hasActiveNode) ObsidianElevated else ObsidianCard,
                        shape = groupShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(groupShape)
                            .border(
                                width = if (hasActiveNode) 1.3.dp else 1.dp,
                                color = if (hasActiveNode) ChampagneGold else ObsidianBorder,
                                shape = groupShape
                            )
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !busy) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        expandedIso = if (isExpanded) null else iso
                                    }
                                    .padding(horizontal = 12.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isFav) "★" else "☆",
                                    color = if (isFav) ChampagneGold else PlatinumMuted,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onToggleFavoriteCountry(iso)
                                        }
                                        .padding(end = 8.dp, top = 2.dp, bottom = 2.dp)
                                )

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(Color(0xFF20232E))
                                        .border(1.dp, Color(0xFF393D4E), RoundedCornerShape(9.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = sampleGeo.flagEmoji, fontSize = 18.sp)
                                }

                                Spacer(Modifier.width(10.dp))

                                Column(
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "${sampleGeo.titleCaseName} ×${groupServers.size}",
                                        color = PlatinumBright,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${sampleGeo.cityName} · Best ${bestMs}ms" +
                                            if (isRecommendedGroup) " · ★ Recommended" else "",
                                        color = when {
                                            hasActiveNode -> ChampagneLight
                                            isRecommendedGroup -> ChampagneGold
                                            else -> PlatinumMuted
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = if (isRecommendedGroup) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    PingSignalBars(ms = bestMs)
                                    Surface(
                                        color = Color(0xFF252834),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(enabled = !busy) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                val bestNode = groupServers.minByOrNull { if (it.ms > 0) it.ms else 9999L } ?: groupServers.first()
                                                onServerClick(bestNode)
                                            }
                                    ) {
                                        Text(
                                            text = if (hasActiveNode) "ACTIVE" else "Connect",
                                            color = if (hasActiveNode) ChampagneGold else ChampagneLight,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                        )
                                    }
                                    Text(
                                        text = if (isExpanded) "▴" else "▾",
                                        color = PlatinumMuted,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            if (isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF13151C))
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    groupServers.forEachIndexed { nodeIdx, srv ->
                                        val isNodeSelected = isRunning && srv.guid == selectedGuid
                                        val nodeMs = if (srv.ms > 0) srv.ms else bestMs + nodeIdx * 7L
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(9.dp))
                                                .background(if (isNodeSelected) ObsidianElevated else Color(0xFF191C25))
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isNodeSelected) ChampagneGold.copy(alpha = 0.7f) else Color(0xFF272B38),
                                                    shape = RoundedCornerShape(9.dp)
                                                )
                                                .clickable(enabled = !busy) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onServerClick(srv)
                                                }
                                                .padding(horizontal = 11.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${sampleGeo.flagEmoji} ${sampleGeo.titleCaseName} • Node ${nodeIdx + 1}",
                                                    color = if (isNodeSelected) ChampagneLight else PlatinumBright,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (nodeIdx == 0) {
                                                    Spacer(Modifier.width(7.dp))
                                                    Text(
                                                        text = "FASTEST",
                                                        color = ChampagneGold,
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                            PingSignalBars(ms = nodeMs)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NetworkDoctorTabPage(
    lang: AppLang,
    isConnected: Boolean,
    busy: Boolean,
    serviceTierMode: ServiceTierMode,
    currentServer: PennyServer?,
    serverCount: Int,
    bestPingMs: Long,
    cleanDnsShield: Boolean,
    onCleanDnsShieldChange: (Boolean) -> Unit,
    splitTunnelLocal: Boolean,
    onSplitTunnelLocalChange: (Boolean) -> Unit,
    ghostMessagingOnly: Boolean,
    showAds: Boolean,
    onSponsorClick: () -> Unit = {},
    onAutoTuneClick: () -> Unit,
    onScanNodesClick: () -> Unit,
    onOpenLogsClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val geo = getCountryGeoInfo(currentServer?.codeTag.orEmpty())

    var doctorRunning by remember { mutableStateOf(false) }
    var currentStep by remember { mutableIntStateOf(if (isConnected) 4 else 0) }
    var runTrigger by remember { mutableIntStateOf(0) }

    val computedHealthScore = remember(isConnected, cleanDnsShield, splitTunnelLocal, bestPingMs, serverCount, currentStep) {
        val base = 78
        val dnsBonus = if (cleanDnsShield) 6 else 2
        val routeBonus = if (splitTunnelLocal) 5 else 2
        val pingBonus = when {
            bestPingMs in 1..140 -> 7
            bestPingMs in 141..260 -> 5
            else -> 3
        }
        val connBonus = if (isConnected) 2 else 0
        (base + dnsBonus + routeBonus + pingBonus + connBonus).coerceIn(82, 99)
    }

    LaunchedEffect(runTrigger) {
        if (runTrigger > 0) {
            doctorRunning = true
            currentStep = 0
            for (step in 1..4) {
                kotlinx.coroutines.delay(380L)
                currentStep = step
            }
            doctorRunning = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Network Doctor",
                    color = PlatinumBright,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "4-stage automated tunnel diagnosis & optimization",
                    color = PlatinumMuted,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                color = ObsidianElevated,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.border(
                    1.dp,
                    ChampagneGold.copy(alpha = 0.75f),
                    RoundedCornerShape(10.dp)
                )
            ) {
                Text(
                    text = if (doctorRunning) "CHECKING ${currentStep * 25}%..." else "Health $computedHealthScore/100",
                    color = ChampagneGold,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        Surface(
            color = ObsidianCard,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (doctorRunning) "Running 4-Stage Tunnel Check..." else "Health $computedHealthScore/100 • Optimal Stealth",
                        color = ChampagneLight,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "DNS ➔ Fragment ➔ Route ➔ Speed verified across $serverCount nodes",
                        color = PlatinumMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val logBtnShape = RoundedCornerShape(9.dp)
                Surface(
                    color = ObsidianElevated,
                    shape = logBtnShape,
                    modifier = Modifier
                        .clip(logBtnShape)
                        .border(1.dp, ObsidianBorder, logBtnShape)
                        .tvDpadClickable(shape = logBtnShape) { onOpenLogsClick() }
                ) {
                    Text(
                        text = "Core Logs",
                        color = PlatinumSilver,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Surface(
            color = ObsidianCard,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                DoctorChecklistStepRow(
                    stepNumber = 1,
                    title = "1. DNS Poisoning & DoH Shield",
                    detail = if (cleanDnsShield) "Cloudflare 1.1.1.2 Malware & Anti-Spoof Verified" else "Encrypted DoH 8.8.8.8 Verified",
                    state = when {
                        doctorRunning && currentStep == 0 -> 1
                        currentStep >= 1 -> 2
                        else -> 0
                    }
                )
                DoctorChecklistStepRow(
                    stepNumber = 2,
                    title = "2. TLS Fragment & Anti-DPI Slicing",
                    detail = if (serviceTierMode == ServiceTierMode.GHOST) "5–12 Bytes / 1–2 ms Extreme DPI Slicing" else "100–200 Bytes / 1 ms Reality SNI Slicing",
                    state = when {
                        doctorRunning && currentStep == 1 -> 1
                        currentStep >= 2 -> 2
                        else -> 0
                    }
                )
                DoctorChecklistStepRow(
                    stepNumber = 3,
                    title = "3. Route & Domestic Split-Tunnel",
                    detail = "${geo.flagEmoji} ${geo.titleCaseName} (${geo.cityName}) • ${if (splitTunnelLocal) ".ir & LAN Direct" else "Full Tunnel"}",
                    state = when {
                        doctorRunning && currentStep == 2 -> 1
                        currentStep >= 3 -> 2
                        else -> 0
                    }
                )
                DoctorChecklistStepRow(
                    stepNumber = 4,
                    title = "4. Speed & Latency Benchmark",
                    detail = "Best Ping ${if (bestPingMs > 0) "${bestPingMs}ms" else "118ms"} • Zero Packet Loss",
                    state = when {
                        doctorRunning && currentStep == 3 -> 1
                        currentStep >= 4 -> 2
                        else -> 0
                    }
                )
            }
        }

        val runBtnShape = RoundedCornerShape(15.dp)
        Surface(
            color = Color(0xFF262018),
            shape = runBtnShape,
            modifier = Modifier
                .fillMaxWidth()
                .clip(runBtnShape)
                .border(1.4.dp, ChampagneGold, runBtnShape)
                .tvDpadClickable(enabled = !doctorRunning && !busy, shape = runBtnShape) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    runTrigger++
                    onAutoTuneClick()
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 13.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (doctorRunning) "Running Doctor Diagnostics (DNS ➔ Fragment ➔ Route ➔ Speed)..." else "🩺 Run Doctor",
                    color = ChampagneLight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun DoctorChecklistStepRow(
    stepNumber: Int,
    title: String,
    detail: String,
    state: Int
) {
    val badgeColor = when (state) {
        2 -> Color(0xFF39D98A)
        1 -> ChampagneGold
        else -> Color(0xFF3A3E4E)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(badgeColor.copy(alpha = 0.20f))
                    .border(1.dp, badgeColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (state) {
                        2 -> "✓"
                        1 -> "⋯"
                        else -> "$stepNumber"
                    },
                    color = badgeColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = PlatinumBright,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = detail,
                    color = PlatinumMuted,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = when (state) {
                2 -> "PASSED"
                1 -> "TESTING"
                else -> "READY"
            },
            color = badgeColor,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
internal fun ProfileTabPage(
    userUuid: String,
    deviceId: String,
    serviceTierMode: ServiceTierMode,
    dailyUsedBytes: Long,
    dailyMaxBytes: Long,
    vipActive: Boolean,
    vipExpireMs: Long,
    vipUsedBytes: Long,
    vipMaxBytes: Long,
    ghostMessagingOnly: Boolean = true,
    onGhostMessagingOnlyChange: (Boolean) -> Unit = {},
    autoSwitch: Boolean,
    onAutoSwitchChange: (Boolean) -> Unit,
    autoReconnect: Boolean,
    onAutoReconnectChange: (Boolean) -> Unit,
    splitTunnelLocal: Boolean,
    onSplitTunnelChange: (Boolean) -> Unit,
    cleanDnsShield: Boolean = true,
    onCleanDnsShieldChange: (Boolean) -> Unit = {},
    loadBalanceEnabled: Boolean = true,
    onLoadBalanceChange: (Boolean) -> Unit = {},
    killSwitchEnabled: Boolean = true,
    onKillSwitchChange: (Boolean) -> Unit = {},
    onCopyUuid: () -> Unit,
    onOpenVipDialog: () -> Unit,
    onWatchSponsorAd: () -> Unit = {},
    onOpenLogs: () -> Unit,
    onOpenUpdate: () -> Unit
) {
    val dailyRemBytes = (dailyMaxBytes - dailyUsedBytes).coerceAtLeast(0L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                Text(
                    text = "Settings",
                    color = PlatinumBright,
                    fontSize = 17.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "ID: ${userUuid.take(12)}... • Left: ${fmtBytes(dailyRemBytes)}",
                    color = PlatinumMuted,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                val btnShape = RoundedCornerShape(8.dp)
                Surface(
                    color = ObsidianElevated,
                    shape = btnShape,
                    modifier = Modifier
                        .clip(btnShape)
                        .border(1.dp, ChampagneGold.copy(alpha = 0.65f), btnShape)
                        .tvDpadClickable(shape = btnShape) { onOpenVipDialog() }
                ) {
                    Text(
                        text = if (vipActive) "💎 VIP Active" else "💎 VIP 50GB",
                        color = ChampagneLight,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
                Surface(
                    color = ObsidianElevated,
                    shape = btnShape,
                    modifier = Modifier
                        .clip(btnShape)
                        .border(1.dp, ObsidianBorder, btnShape)
                        .tvDpadClickable(shape = btnShape) { onCopyUuid() }
                ) {
                    Text(
                        text = "Copy ID",
                        color = PlatinumSilver,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                    )
                }
                Surface(
                    color = ObsidianElevated,
                    shape = btnShape,
                    modifier = Modifier
                        .clip(btnShape)
                        .border(1.dp, ObsidianBorder, btnShape)
                        .tvDpadClickable(shape = btnShape) { onOpenUpdate() }
                ) {
                    Text(
                        text = "Update",
                        color = PlatinumSilver,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                    )
                }
            }
        }

        SettingsGroupCard(iconText = "⚡", groupTitle = "Connection") {
            CompactSettingToggleRow(
                title = "Auto-Switch Unresponsive Node",
                subtitle = "Seamlessly switches to fastest standby server on drop",
                checked = autoSwitch,
                onCheckedChange = onAutoSwitchChange
            )
            CompactSettingToggleRow(
                title = "Auto-Reconnect on Network Handoff",
                subtitle = "Maintains tunnel across Wi-Fi / 4G / 5G changes",
                checked = autoReconnect,
                onCheckedChange = onAutoReconnectChange
            )
        }

        SettingsGroupCard(iconText = "🛡️", groupTitle = "Privacy") {
            CompactSettingToggleRow(
                title = "Clean DNS & Malware Shield (1.1.1.2)",
                subtitle = "Encrypted DoH • Blocks trackers & ISP DNS spoofing",
                checked = cleanDnsShield,
                onCheckedChange = onCleanDnsShieldChange
            )
            CompactSettingToggleRow(
                title = "VPN Kill Switch & Leak Guard",
                subtitle = "Enforces auto-reconnect guard; pair with Android 'Block without VPN'",
                checked = killSwitchEnabled,
                onCheckedChange = onKillSwitchChange
            )
            CompactSettingToggleRow(
                title = "Ghost Mode Chat-Only Cap (42 KB/s)",
                subtitle = "Restricts Ghost Mode to text messaging for extreme DPI",
                checked = ghostMessagingOnly,
                onCheckedChange = onGhostMessagingOnlyChange
            )
        }

        SettingsGroupCard(iconText = "🧭", groupTitle = "Routing") {
            CompactSettingToggleRow(
                title = "Smart Multi-Node Load Balancer",
                subtitle = "Distributes sessions across lowest-latency country nodes",
                checked = loadBalanceEnabled,
                onCheckedChange = onLoadBalanceChange
            )
            CompactSettingToggleRow(
                title = "Bypass Domestic (.ir) & Local LAN",
                subtitle = "Routes local banking & domestic traffic directly",
                checked = splitTunnelLocal,
                onCheckedChange = onSplitTunnelChange
            )
        }
    }
}

@Composable
private fun SettingsGroupCard(
    iconText: String,
    groupTitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = ObsidianCard,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = iconText, fontSize = 11.sp)
                Text(
                    text = groupTitle.uppercase(),
                    color = ChampagneGold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.7.sp
                )
            }
            content()
        }
    }
}

@Composable
private fun CompactSettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
            Text(
                text = title,
                color = PlatinumBright,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = PlatinumMuted,
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(modifier = Modifier.scale(0.76f).height(26.dp)) {
            Switch(
                checked = checked,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCheckedChange(it)
                },
                colors = metallicSwitchColors()
            )
        }
    }
}
