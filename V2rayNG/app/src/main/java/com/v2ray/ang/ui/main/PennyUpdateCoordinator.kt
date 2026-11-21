package com.v2ray.ang.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * هماهنگ‌کننده دانلود امن و نصب خودکار نسخه جدید APK از گیت‌هاب
 */
internal class PennyUpdateCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onDownloadingChange: (Boolean) -> Unit,
    private val onProgressChange: (Float) -> Unit,
    private val onShowBanner: () -> Unit,
    private val onDownloadStatus: (String?) -> Unit,
    private val onStatusMessage: (String) -> Unit,
    private val onRequestInstallPermission: (Intent, File) -> Unit,
    private val onLog: (String) -> Unit
) {
    private var downloading = false

    fun startFullAppUpdate(downloadUrl: String) {
        if (downloading) return
        val cleanUrl = downloadUrl.trim()
        if (!cleanUrl.substringBefore('?').endsWith(".apk", ignoreCase = true)) {
            onDownloadStatus("Opening GitHub release page...")
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
            return
        }

        downloading = true
        onDownloadingChange(true)
        onProgressChange(0f)
        onShowBanner()
        onDownloadStatus("Downloading update package...")
        onStatusMessage("Downloading Penny VPN update...")
        onLog("Starting in-app APK download: $cleanUrl")

        scope.launch {
            val apkFile = File(context.cacheDir, "penny_vpn_update.apk")
            val ok = withContext(Dispatchers.IO) {
                PennyServerScanner.downloadApkWithProgress(cleanUrl, apkFile) { progress ->
                    scope.launch(Dispatchers.Main) {
                        onProgressChange(progress)
                    }
                }
            }
            downloading = false
            onDownloadingChange(false)

            if (!ok || !apkFile.exists()) {
                onDownloadStatus("Download failed. Check connection and try again.")
                onStatusMessage("Update download failed")
                onLog("In-app APK download failed")
                return@launch
            }

            val archivePkg = withContext(Dispatchers.IO) {
                runCatching {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)?.packageName
                }.getOrNull()
            }
            if (archivePkg != null && archivePkg != context.packageName) {
                runCatching { apkFile.delete() }
                onDownloadStatus("Package mismatch ($archivePkg). Update aborted.")
                onStatusMessage("Downloaded APK does not match installed app flavor")
                onLog("Aborted APK install due to package mismatch: $archivePkg vs ${context.packageName}")
                return@launch
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                onDownloadStatus("Permission required: allow 'Install unknown apps'")
                onStatusMessage("Grant install permission to finish update")
                val permIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                )
                onRequestInstallPermission(permIntent, apkFile)
                return@launch
            }

            launchApkInstaller(apkFile)
        }
    }

    fun launchApkInstaller(apkFile: File) {
        if (!apkFile.exists()) {
            onDownloadStatus("Installer package missing; please retry.")
            return
        }
        runCatching {
            val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.cache", apkFile)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            onDownloadStatus("Launching Android installer...")
            onStatusMessage("Installing Penny VPN update...")
            context.startActivity(installIntent)
        }.onFailure { e ->
            onDownloadStatus("Installer launch error: ${e.message}")
            onLog("APK installer launch failed: ${e.message}")
        }
    }
}
