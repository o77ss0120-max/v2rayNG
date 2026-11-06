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
        if (vip) "Connected to VIP (1M / 50GB)" else "Connected"

    fun smartConnect(l: AppLang, vip: Boolean) =
        if (vip) "VIP Connect" else "Smart Connect"

    fun tapToCancel(l: AppLang) = "Tap to Cancel"
    fun disconnect(l: AppLang) = "Disconnect"
    fun download(l: AppLang) = "Download"
    fun upload(l: AppLang) = "Upload"
    fun duration(l: AppLang) = "Duration"
    fun totalData(l: AppLang) = "Total Data"

    fun noServerYet(l: AppLang, vip: Boolean) =
        if (vip) "No VIP servers scanned yet; tap Scan or Connect."
        else "No servers scanned yet; tap Scan or Connect."

    fun scanServersBtn(l: AppLang, vip: Boolean) =
        if (vip) "Scan VIP" else "Scan Servers"

    fun nextServerBtn(l: AppLang) = "Next Server"

    fun scanInfo(l: AppLang, min: Long, count: Int, vip: Boolean): String {
        val tag = if (vip) "VIP " else ""
        val ago = when {
            min < 1 -> "just now"
            min < 60 -> "${min}m ago"
            else -> "${min / 60}h ago"
        }
        return "Last ${tag}scan: $ago  •  $count servers ready"
    }

    fun noScanYet(l: AppLang, vip: Boolean) =
        if (vip) "No VIP scan performed yet" else "No scan performed yet"

    fun menuBestConfigs(l: AppLang, vip: Boolean) =
        if (vip) "1M 50GB Dedicated Servers" else "Best Servers List"

    fun menuAppLog(l: AppLang) = "App Activity Logs"
    fun menuCoreLog(l: AppLang) = "Core Logs (Logcat)"
    fun close(l: AppLang) = "Close"
    fun copy(l: AppLang) = "Copy"
    fun clear(l: AppLang) = "Clear"
    fun emptyBestList(l: AppLang) = "No server list yet. Please scan first."
    fun emptyLogs(l: AppLang) = "No logs recorded yet."
}

internal fun assignLabels(list: List<PennyServer>, isVipList: Boolean = false): List<PennyServer> {
    return list.sortedBy { it.ms }.mapIndexed { index, s ->
        val groupChar = ('A'.code + (index / 4).coerceAtMost(5)).toChar()
        val num = (index % 4) + 1
        val isNodeGroup = (index % 4) < 2
        val codeTag = "$groupChar-$num"
        var customRemarks: String? = null
        try {
            val cfg = MmkvManager.decodeServerConfig(s.guid)
            if (cfg != null) {
                if (cfg.subscriptionId == "PENNY_MANAGED_SUB" ||
                    cfg.remarks.startsWith("PennyVPN", ignoreCase = true) ||
                    cfg.remarks.contains("BPB", ignoreCase = true)
                ) {
                    if (cfg.remarks != "Penny VPN") {
                        cfg.remarks = "Penny VPN"
                        MmkvManager.encodeServerConfig(s.guid, cfg)
                    }
                } else if (cfg.remarks.isNotBlank() && cfg.remarks != "Penny VPN") {
                    customRemarks = cfg.remarks.take(24)
                }
            }
        } catch (e: Exception) {
            Log.w("PennyVPN", "Failed to read remarks for ${s.guid}: ${e.message}")
        }
        val baseLabel = customRemarks ?: if (isNodeGroup) "Node $codeTag" else "Server $codeTag"
        s.copy(
            codeTag = codeTag,
            label = baseLabel,
            isVipServer = isVipList
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
