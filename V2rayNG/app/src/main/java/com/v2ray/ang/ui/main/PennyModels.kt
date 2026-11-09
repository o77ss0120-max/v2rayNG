package com.v2ray.ang.ui.main

import android.util.Log
import com.v2ray.ang.handler.MmkvManager
import java.util.Locale

internal enum class AppLang(val code: String) { EN("en") }

internal enum class Phase { IDLE, SCANNING, CONNECTING }

internal enum class UiMode { IDLE, SCANNING, CONNECTING, CONNECTED }

internal enum class ConnectionProfile(val label: String, val tag: String) {
    SMART("Smart Auto", "Balanced"),
    GAMING("Gaming", "Low Ping"),
    STREAMING("Streaming", "High Speed")
}

/**
 * ۳ حالت اصلی سرویس طبق معماری جدید:
 * 1) PUBLIC: سرورهای عمومی رایگان (بدون محدودیت حجم اختصاصی)
 * 2) DEDICATED: سرورهای اختصاصی پرسرعت با سهمیه ۱ گیگابایت روزانه به ازای هر شناسه سخت‌افزاری (یا ۵۰ گیگ VIP)
 * 3) GHOST: حالت عبور از فیلترینگ شدید و اختلال سراسری (مخصوص پیام‌رسان و اتصال اضطراری با مصرف بسیار کم)
 */
internal enum class ServiceTierMode(
    val title: String,
    val shortTitle: String,
    val badge: String,
    val description: String
) {
    PUBLIC(
        title = "Public Mode",
        shortTitle = "Public",
        badge = "FREE • UNLIMITED",
        description = "Community public nodes • Free general browsing"
    ),
    DEDICATED(
        title = "Dedicated Pro",
        shortTitle = "Dedicated",
        badge = "1 GB / DAY • PRO",
        description = "Private high-speed nodes • 1 GB/day per hardware UUID"
    ),
    GHOST(
        title = "Ghost Mode",
        shortTitle = "Ghost Mode",
        badge = "CHAT ONLY • LOW DATA",
        description = "Emergency anti-DPI • Restricted to messaging protocols"
    )
}

/**
 * ۴ تب اصلی ناوبری پایین صفحه (Home • Servers • Doctor • Profile)
 */
internal enum class BottomNavTab(val title: String) {
    HOME("Home"),
    SERVERS("Servers"),
    DOCTOR("Doctor"),
    PROFILE("Profile")
}

/**
 * اطلاعات کشور سرور برای نمایش روی کره زمین سه‌بعدی و رادار چشمک‌زن (بدون نمایش مختصات عددی در رابط کاربری)
 */
internal data class CountryGeoInfo(
    val isoCode: String,
    val countryName: String,
    val cityName: String,
    val latitude: Float,
    val longitude: Float
)

internal fun getCountryGeoInfo(codeTag: String): CountryGeoInfo {
    return when (codeTag) {
        "DED-NL" -> CountryGeoInfo(
            isoCode = "NL",
            countryName = "NETHERLANDS",
            cityName = "Amsterdam",
            latitude = 52.37f,
            longitude = 4.90f
        )
        "DED-PL" -> CountryGeoInfo(
            isoCode = "PL",
            countryName = "POLAND",
            cityName = "Warsaw",
            latitude = 52.23f,
            longitude = 21.01f
        )
        "VPS-1" -> CountryGeoInfo(
            isoCode = "DE",
            countryName = "GERMANY",
            cityName = "Frankfurt",
            latitude = 50.11f,
            longitude = 8.68f
        )
        "GHOST" -> CountryGeoInfo(
            isoCode = "NL",
            countryName = "NETHERLANDS • GHOST",
            cityName = "Stealth Relay",
            latitude = 52.37f,
            longitude = 4.90f
        )
        "CF-1", "CF-2", "BPB" -> CountryGeoInfo(
            isoCode = "DE",
            countryName = "GERMANY",
            cityName = "Frankfurt",
            latitude = 50.11f,
            longitude = 8.68f
        )
        else -> CountryGeoInfo(
            isoCode = "FR",
            countryName = "FRANCE",
            cityName = "Paris",
            latitude = 48.85f,
            longitude = 2.35f
        )
    }
}

internal data class PennyServer(
    val guid: String,
    val codeTag: String,
    val ms: Long,
    val label: String,
    val isVipServer: Boolean = false
) {
    val isVip: Boolean get() = isVipServer
}

internal object Strings {
    fun subtitle(l: AppLang, vip: Boolean) =
        if (vip) "Connects When Others Can't • VIP" else "Connects When Others Can't"

    fun splashTagline(l: AppLang) = "Connects When Others Can't"
    fun ready(l: AppLang) = "Ready to Connect"
    fun connectedBadge(l: AppLang, vip: Boolean) =
        if (vip) "Connected • Encrypted Tunnel" else "Connected"

    fun smartConnect(l: AppLang, vip: Boolean) =
        if (vip) "VIP Connect" else "Smart Connect"

    fun tapToCancel(l: AppLang) = "Tap to Cancel"
    fun disconnect(l: AppLang) = "Disconnect"
    fun download(l: AppLang) = "Download"
    fun upload(l: AppLang) = "Upload"
    fun duration(l: AppLang) = "Duration"
    fun totalData(l: AppLang) = "Total Data"

    fun noServerYet(l: AppLang, vip: Boolean) =
        if (vip) "Tap Scan or Connect to initialize Dedicated nodes"
        else "Tap Scan or Connect to initialize Public nodes"

    fun scanServersBtn(l: AppLang, vip: Boolean) =
        if (vip) "Scan Dedicated" else "Scan Servers"

    fun nextServerBtn(l: AppLang) = "Next Server"

    fun scanInfo(l: AppLang, min: Long, count: Int, vip: Boolean): String {
        val tag = if (vip) "Dedicated " else ""
        val ago = when {
            min < 1 -> "just now"
            min < 60 -> "${min}m ago"
            else -> "${min / 60}h ago"
        }
        return "Last ${tag}scan: $ago  •  $count nodes ready"
    }

    fun noScanYet(l: AppLang, vip: Boolean) =
        if (vip) "Dedicated Vault Ready • Tap Connect" else "Tap Scan or Connect to start"

    fun menuBestConfigs(l: AppLang, vip: Boolean) =
        if (vip) "Dedicated Pro Servers" else "Best Servers List"

    fun menuAppLog(l: AppLang) = "App Activity Logs"
    fun menuCoreLog(l: AppLang) = "Core Logs (Logcat)"
    fun close(l: AppLang) = "Close"
    fun copy(l: AppLang) = "Copy"
    fun clear(l: AppLang) = "Clear"
    fun emptyBestList(l: AppLang) = "No server list yet. Please scan first."
    fun emptyLogs(l: AppLang) = "No logs recorded yet."
}

internal fun detectServerOriginTag(guid: String): String {
    return try {
        val cfg = MmkvManager.decodeServerConfig(guid) ?: return "PUB"
        val combined = "${cfg.host.orEmpty()} ${cfg.sni.orEmpty()} ${cfg.server.orEmpty()} ${cfg.remarks}".lowercase(Locale.US)
        val nlHost = PennyServerScanner.DED_NL_HOST.lowercase(Locale.US)
        val plHost = PennyServerScanner.DED_PL_HOST.lowercase(Locale.US)
        val vps1 = PennyServerScanner.VPS1_HOST.lowercase(Locale.US)
        val bpb1 = PennyServerScanner.BPB_HOST.lowercase(Locale.US)
        val bpb2 = PennyServerScanner.BPB2_HOST.lowercase(Locale.US)
        when {
            combined.contains("ghost-node") || combined.contains("pennyvpn ghost") -> "GHOST"
            combined.contains(nlHost) || combined.contains("ded-nl") -> "DED-NL"
            combined.contains(plHost) || combined.contains("ded-pl") -> "DED-PL"
            combined.contains(vps1) || combined.contains("own-vps") || combined.contains("vps-1") || combined.contains("zenith") || combined.contains("parham-confing") -> "VPS-1"
            combined.contains(bpb1) || combined.contains("own-cf1") || combined.contains("cf-1") -> "CF-1"
            combined.contains(bpb2) || combined.contains("own-cf2") || combined.contains("cf-2") -> "CF-2"
            combined.contains(".pages.dev") || combined.contains("own-bpb") || combined.contains("bpb") -> "BPB"
            combined.contains(".workers.dev") || combined.contains("own-cf") -> "CF-1"
            else -> "PUB"
        }
    } catch (_: Exception) {
        "PUB"
    }
}

internal fun isDedicatedServerTag(codeTag: String): Boolean {
    return codeTag == "DED-NL" ||
        codeTag == "DED-PL" ||
        codeTag == "VPS-1" ||
        codeTag == "GHOST" ||
        codeTag == "CF-1" ||
        codeTag == "CF-2" ||
        codeTag == "BPB" ||
        codeTag.startsWith("OWN") ||
        codeTag.startsWith("DED")
}

internal fun assignLabels(list: List<PennyServer>, isVipList: Boolean = false): List<PennyServer> {
    val tagged = list.map { s ->
        val origin = detectServerOriginTag(s.guid)
        val realVerified = (MmkvManager.decodeServerAffiliationInfo(s.guid)?.testDelayMillis ?: 0L) > 0L
        Triple(s, origin, realVerified)
    }

    val sorted = tagged.sortedWith(
        compareBy<Triple<PennyServer, String, Boolean>> { (_, origin, _) ->
            when (origin) {
                "DED-NL", "DED-PL" -> 0
                "GHOST" -> 1
                "VPS-1" -> 2
                else -> 3
            }
        }.thenBy { (_, _, realVerified) ->
            if (realVerified) 0 else 1
        }.thenBy { (s, _, _) ->
            if (s.ms > 0L) s.ms else Long.MAX_VALUE
        }
    )

    return sorted.mapIndexed { index, (s, origin, _) ->
        try {
            val cfg = MmkvManager.decodeServerConfig(s.guid)
            if (cfg != null) {
                val targetRemark = when (origin) {
                    "DED-NL" -> "PennyVPN DED-NL"
                    "DED-PL" -> "PennyVPN DED-PL"
                    "GHOST" -> "PennyVPN GHOST"
                    "VPS-1" -> "PennyVPN OWN-VPS1"
                    "CF-1" -> "PennyVPN OWN-CF1"
                    "CF-2" -> "PennyVPN OWN-CF2"
                    "BPB" -> "PennyVPN OWN-BPB"
                    else -> "PennyVPN PUBLIC"
                }
                if (cfg.remarks != targetRemark) {
                    cfg.remarks = targetRemark
                    MmkvManager.encodeServerConfig(s.guid, cfg)
                }
            }
        } catch (e: Exception) {
            Log.w("PennyVPN", "Failed to tag remarks for ${s.guid}: ${e.message}")
        }

        val displayLabel = when (origin) {
            "DED-NL" -> "Netherlands Pro • #${index + 1}"
            "DED-PL" -> "Poland Pro • #${index + 1}"
            "GHOST" -> "Ghost Messaging • #${index + 1}"
            "VPS-1" -> "Germany Pro • #${index + 1}"
            "CF-1", "CF-2", "BPB" -> "Europe Edge • #${index + 1}"
            else -> "Public Node • #${index + 1}"
        }
        s.copy(
            codeTag = origin,
            label = displayLabel,
            isVipServer = isVipList || isDedicatedServerTag(origin)
        )
    }
}

internal fun fmtBytes(b: Long): String {
    if (b < 0) return "—"
    val kb = b / 1024.0
    return when {
        kb < 1024.0 -> String.format(Locale.US, "%.0f KB", kb)
        kb < 1024.0 * 1024.0 -> String.format(Locale.US, "%.1f MB", kb / 1024.0)
        else -> String.format(Locale.US, "%.2f GB", kb / 1024.0 / 1024.0)
    }
}

internal fun fmtSpeed(bps: Long): String = if (bps < 0) "—" else fmtBytes(bps) + "/s"

internal fun fmtDuration(sec: Long): String {
    val s = sec.coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}
