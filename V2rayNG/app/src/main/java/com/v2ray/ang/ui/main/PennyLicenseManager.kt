package com.v2ray.ang.ui.main

import android.content.Context
import android.media.MediaDrm
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Locale
import java.util.UUID

internal data class VerifiedLicense(
    val serial: String,
    val deviceId: String,
    val durationMs: Long,
    val maxBytes: Long
)

internal data class ServerQuotaState(
    val ok: Boolean,
    val active: Boolean,
    val serial: String,
    val deviceId: String,
    val expiresAtMs: Long,
    val maxBytes: Long,
    val usedBytes: Long,
    val message: String
)

internal data class DailyQuotaSyncResult(
    val usedBytes: Long,
    val maxBytes: Long,
    val blocked: Boolean
)

internal object VipLicenseManager {
    const val VIP_DURATION_MS = 30L * 24L * 60L * 60L * 1000L // ۱ ماه (۳۰ روز)
    const val VIP_MAX_BYTES = 50L * 1024L * 1024L * 1024L      // ۵۰ گیگابایت

    const val ROTATED_PUBLIC_KEY_B64 =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEf1YXmwm/4ojudflyQNF470nc0kSAIdSM+K5OUIWqHHhxNCusYcvAygnbn9OFs6CGEpxTRB7AK6xfyyStgOE11Q=="

    const val DEFAULT_LICENSE_API_BASE = "https://ais-pre-luudtt26z2k4ltbojg23wu-178499689419.europe-west2.run.app"

    private val WIDEVINE_UUID = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)

    @Volatile
    private var cachedHwDigest: ByteArray? = null

    /**
     * استخراج اثرانگشت سخت‌افزاری پایدار (MediaDrm Widevine + Signing-Key ANDROID_ID + SoC Hardware)
     * برای تولید شناسه یکتای دستگاه جهت مدیریت سهمیه روزانه و قفل لایسنس.
     */
    private fun computeHardwareDigest(context: Context): ByteArray {
        cachedHwDigest?.let { return it }
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        }.getOrDefault("")

        val widevineHex = runCatching {
            val drm = MediaDrm(WIDEVINE_UUID)
            try {
                val idBytes = drm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
                idBytes.joinToString("") { String.format(Locale.US, "%02x", it.toInt() and 0xFF) }
            } finally {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    runCatching { drm.close() }
                } else {
                    @Suppress("DEPRECATION")
                    runCatching { drm.release() }
                }
            }
        }.getOrDefault("")

        val hwSpec = "${Build.BOARD}|${Build.BRAND}|${Build.DEVICE}|${Build.HARDWARE}"
        val rawSeed = "PENNY_HW_FINGERPRINT_V3:$androidId:$widevineHex:$hwSpec"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(rawSeed.toByteArray(StandardCharsets.UTF_8))
        cachedHwDigest = digest
        return digest
    }

    /**
     * شناسه ۱۲ کاراکتری سخت‌افزاری دستگاه
     */
    fun getDeviceId(context: Context): String {
        return try {
            val digest = computeHardwareDigest(context)
            val sb = StringBuilder()
            for (i in 0 until 6) {
                if (i == 2 || i == 4) sb.append("-")
                sb.append(String.format(Locale.US, "%02X", digest[i].toInt() and 0xFF))
            }
            sb.toString()
        } catch (e: Exception) {
            Log.w("PennyVPN", "DeviceId fallback: ${e.message}")
            "0000-0000-0000"
        }
    }

    /**
     * تولید UUID اختصاصی و یکتا برای هر کاربر بر اساس اثرانگشت سخت‌افزاری دستگاه:
     * فرمت استاندارد xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx
     */
    fun getUserUuid(context: Context): String {
        return try {
            val d = computeHardwareDigest(context).clone()
            d[6] = ((d[6].toInt() and 0x0F) or 0x40).toByte() // Version 4
            d[8] = ((d[8].toInt() and 0x3F) or 0x80).toByte() // Variant RFC4122
            val sb = StringBuilder(36)
            for (i in 0 until 16) {
                if (i == 4 || i == 6 || i == 8 || i == 10) sb.append('-')
                sb.append(String.format(Locale.US, "%02x", d[i].toInt() and 0xFF))
            }
            sb.toString()
        } catch (_: Exception) {
            "00000000-0000-4000-8000-000000000000"
        }
    }

    /**
     * ذخیره‌سازی محلی سهمیه روزانه ۱ گیگابایت (همراه با همگام‌سازی سمت سرور بر اساس Hardware UUID)
     */
    fun readTamperResistantDailyUsed(context: Context, deviceId: String, dayKey: String, prefsUsed: Long): Long {
        var maxObserved = prefsUsed.coerceAtLeast(0L)
        try {
            val backupFile = File(context.noBackupFilesDir, ".pny_q_${deviceId.hashCode()}.dat")
            if (backupFile.exists()) {
                val text = backupFile.readText(StandardCharsets.UTF_8).trim()
                val parts = text.split("|")
                if (parts.size >= 2 && parts[0] == dayKey) {
                    val v = parts[1].toLongOrNull() ?: 0L
                    if (v > maxObserved) maxObserved = v
                }
            }
        } catch (e: Exception) {
            Log.d("PennyVPN", "Quota file read warning: ${e.message}")
        }
        return maxObserved
    }

    fun writeTamperResistantDailyUsed(context: Context, deviceId: String, dayKey: String, usedBytes: Long) {
        try {
            val backupFile = File(context.noBackupFilesDir, ".pny_q_${deviceId.hashCode()}.dat")
            backupFile.writeText("$dayKey|$usedBytes", StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.d("PennyVPN", "Quota file write warning: ${e.message}")
        }
    }

    /**
     * همگام‌سازی سهمیه ۱ گیگابایت روزانه با سرور (بدون ارسال نام سرور یا اطلاعات اضافی)
     */
    fun syncDailyQuotaAndTelemetry(
        apiBaseUrl: String,
        deviceId: String,
        userUuid: String,
        dayKey: String,
        localUsedBytes: Long,
        deltaBytes: Long,
        activeMode: String,
        connectedServerLabel: String
    ): DailyQuotaSyncResult? {
        val cleanBase = apiBaseUrl.trim().trimEnd('/')
        if (!cleanBase.startsWith("https://", ignoreCase = true)) {
            return null
        }
        return try {
            val conn = URL("$cleanBase/api/v1/quota/daily-sync").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")

                val reqJson = JSONObject()
                    .put("deviceId", deviceId)
                    .put("userUuid", userUuid)
                    .put("dayKey", dayKey)
                    .put("localUsedBytes", localUsedBytes.coerceAtLeast(0L))
                    .put("deltaBytes", deltaBytes.coerceAtLeast(0L))
                    .put("activeMode", activeMode)
                    .toString()

                OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(reqJson)
                }

                if (conn.responseCode !in 200..299) return null
                val respText = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                val obj = JSONObject(respText)
                if (!obj.optBoolean("ok", false)) return null
                DailyQuotaSyncResult(
                    usedBytes = obj.optLong("dailyUsedBytes", obj.optLong("usedBytes", localUsedBytes)),
                    maxBytes = obj.optLong("maxDailyBytes", obj.optLong("maxBytes", 1L * 1024L * 1024L * 1024L)),
                    blocked = !obj.optBoolean("allowed", true) || obj.optBoolean("blocked", false)
                )
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.d("PennyVPN", "Daily quota sync skipped: ${e.message}")
            null
        }
    }

    fun maskCode(code: String): String {
        val clean = code.trim()
        if (clean.length <= 10) return "****"
        return "${clean.take(6)}...${clean.takeLast(4)}"
    }

    private fun verifyWithSingleKey(dataBytes: ByteArray, sigBytes: ByteArray, keyB64: String): Boolean {
        return try {
            val keyBytes = Base64.decode(keyB64, Base64.DEFAULT)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val pubKey = KeyFactory.getInstance("EC").generatePublic(keySpec)
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(pubKey)
            verifier.update(dataBytes)
            verifier.verify(sigBytes)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * بررسی امضای دیجیتال ECDSA P-256 فقط با کلید عمومی چرخانده‌شده جدید (کلید قدیمی به‌طور کامل ابطال و حذف شد)
     */
    fun verifyEcdsaSignature(
        dataBytes: ByteArray,
        sigBytes: ByteArray,
        pubKeyB64: String = ROTATED_PUBLIC_KEY_B64
    ): Boolean {
        return verifyWithSingleKey(dataBytes, sigBytes, pubKeyB64)
    }

    fun verifyLicenseOfflineSignature(
        rawCode: String,
        currentDeviceId: String,
        effectiveNowMs: Long
    ): Pair<VerifiedLicense?, String> {
        val trimmed = rawCode.trim().replace(" ", "")
        if (!trimmed.startsWith("PL1.", ignoreCase = true) && !trimmed.startsWith("PV1.", ignoreCase = true)) {
            return null to "Invalid license format. Please enter a valid ECDSA P-256 license (PL1....)."
        }
        val parts = trimmed.split(".")
        if (parts.size != 3) return null to "Malformed ECDSA license token."
        return try {
            val payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val sigBytes = Base64.decode(parts[2], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            if (!verifyEcdsaSignature(payloadBytes, sigBytes, ROTATED_PUBLIC_KEY_B64)) {
                return null to "Invalid or revoked ECDSA P-256 signature!"
            }
            val payloadStr = String(payloadBytes, StandardCharsets.UTF_8).trim()
            if (payloadStr.startsWith("{")) {
                val json = JSONObject(payloadStr)
                val serial = json.optString("s", "").trim().uppercase(Locale.US)
                if (serial.isEmpty()) return null to "Missing license serial in token."
                val targetDevice = json.optString("d", "ANY").trim().uppercase(Locale.US)
                if (targetDevice != "ANY" && targetDevice != "*" && targetDevice != currentDeviceId.uppercase(Locale.US)) {
                    return null to "License is locked to another Device ID ($targetDevice)."
                }
                val expSec = json.optLong("exp", 0L)
                val maxB = json.optLong("max", VIP_MAX_BYTES).coerceIn(1L * 1024L * 1024L * 1024L, 1000L * 1024L * 1024L * 1024L)
                val durMs = if (expSec > 0L) {
                    val remMs = expSec * 1000L - effectiveNowMs
                    if (remMs <= 0L) {
                        return null to "License token has expired."
                    }
                    remMs
                } else {
                    VIP_DURATION_MS
                }
                return VerifiedLicense(
                    serial = serial,
                    deviceId = targetDevice,
                    durationMs = durMs,
                    maxBytes = maxB
                ) to "OK"
            }
            val delimiter = if ('|' in payloadStr) '|' else ':'
            val fields = payloadStr.split(delimiter)
            if (fields.size < 3) return null to "Corrupted license payload."
            val serial = fields[0].trim().uppercase(Locale.US)
            val targetDevice = fields[1].trim().uppercase(Locale.US)
            val rawDeadline = fields[2].trim().toLongOrNull() ?: 0L
            val deadlineMs = if (rawDeadline in 1..9_999_999_999L) rawDeadline * 1000L else rawDeadline
            val days = fields.getOrNull(3)?.trim()?.toLongOrNull()?.coerceIn(1L, 365L) ?: 30L
            val gb = fields.getOrNull(4)?.trim()?.toLongOrNull()?.coerceIn(1L, 1000L) ?: 50L

            if (targetDevice != "ANY" && targetDevice != "*" && targetDevice != currentDeviceId.uppercase(Locale.US)) {
                return null to "License is locked to another Device ID ($targetDevice)."
            }
            if (deadlineMs > 0L && effectiveNowMs > deadlineMs) {
                return null to "Activation deadline for this license has expired."
            }
            VerifiedLicense(
                serial = serial,
                deviceId = targetDevice,
                durationMs = days * 24L * 3600_000L,
                maxBytes = gb * 1024L * 1024L * 1024L
            ) to "OK"
        } catch (e: Exception) {
            Log.w("PennyVPN", "License parse error: ${e.message}")
            null to "Malformed activation code."
        }
    }

    /**
     * فعال‌سازی سخت‌گیرانه سمت سرور:
     * ابتدا امضای ECDSA P-256 بررسی می‌شود و سپس حتماً باید سرور لایسنس آن را تایید کند (بدون فعال‌سازی آفلاین در صورت خطا).
     */
    fun activateOnServer(
        apiBaseUrl: String,
        rawCode: String,
        deviceId: String
    ): Pair<ServerQuotaState?, String> {
        val (offlineVerified, offlineMsg) = verifyLicenseOfflineSignature(
            rawCode,
            deviceId,
            System.currentTimeMillis()
        )
        if (offlineVerified == null) {
            return null to offlineMsg
        }

        val cleanBase = apiBaseUrl.trim().trimEnd('/')
        if (!cleanBase.startsWith("https://", ignoreCase = true)) {
            return null to "License verification server URL must use HTTPS."
        }

        return try {
            val conn = URL("$cleanBase/api/v1/license/activate").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")

                val reqJson = JSONObject()
                    .put("code", rawCode.trim())
                    .put("deviceId", deviceId)
                    .toString()

                OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(reqJson)
                }

                val status = conn.responseCode
                val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                val respText = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: "{}"
                val obj = JSONObject(respText)
                val ok = obj.optBoolean("ok", false)
                val msg = obj.optString("message", if (ok) "Activated on server" else "Server rejected license")
                if (!ok) {
                    return null to msg
                }
                val state = ServerQuotaState(
                    ok = true,
                    active = obj.optBoolean("active", true),
                    serial = obj.optString("serial", offlineVerified.serial),
                    deviceId = obj.optString("deviceId", deviceId),
                    expiresAtMs = obj.optLong("expiresAtMs", System.currentTimeMillis() + offlineVerified.durationMs),
                    maxBytes = obj.optLong("maxBytes", offlineVerified.maxBytes),
                    usedBytes = obj.optLong("usedBytes", 0L),
                    message = msg
                )
                state to msg
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w("PennyVPN", "Server license activation failed: ${e.message}")
            null to "License server unreachable. Please check your internet connection and try again."
        }
    }

    fun syncTrafficWithServer(
        apiBaseUrl: String,
        serial: String,
        deviceId: String,
        deltaBytes: Long
    ): ServerQuotaState? {
        val cleanBase = apiBaseUrl.trim().trimEnd('/')
        if (!cleanBase.startsWith("https://", ignoreCase = true) || serial.isBlank()) return null

        return try {
            val conn = URL("$cleanBase/api/v1/license/heartbeat").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")

                val reqJson = JSONObject()
                    .put("serial", serial)
                    .put("deviceId", deviceId)
                    .put("deltaBytes", deltaBytes.coerceAtLeast(0L))
                    .toString()

                OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(reqJson)
                }

                val status = conn.responseCode
                val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                val respText = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: return null
                val obj = JSONObject(respText)
                ServerQuotaState(
                    ok = obj.optBoolean("ok", false),
                    active = obj.optBoolean("active", false),
                    serial = obj.optString("serial", serial),
                    deviceId = obj.optString("deviceId", deviceId),
                    expiresAtMs = obj.optLong("expiresAtMs", 0L),
                    maxBytes = obj.optLong("maxBytes", VIP_MAX_BYTES),
                    usedBytes = obj.optLong("usedBytes", 0L),
                    message = obj.optString("message", "")
                )
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w("PennyVPN", "Traffic heartbeat warning: ${e.message}")
            null
        }
    }

    fun verifySignedServerList(rawContent: String): Pair<Boolean, String> {
        val normalized = rawContent.replace("\r\n", "\n").trim()
        val lines = normalized.lines()
        val firstLine = lines.firstOrNull()?.trim() ?: return false to ""
        if (!firstLine.startsWith("#PENNY-SIG:")) {
            Log.w("PennyVPN", "Rejected unsigned server list (missing #PENNY-SIG: header)")
            return false to ""
        }
        val sigB64 = firstLine.removePrefix("#PENNY-SIG:").trim()
        val canonicalLines = lines
            .drop(1)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#PENNY-SIG:") }
        val canonicalBody = canonicalLines.joinToString("\n")
        if (canonicalBody.isEmpty()) return false to ""

        return try {
            val sigBytes = Base64.decode(sigB64, Base64.DEFAULT)
            val ok = verifyEcdsaSignature(
                canonicalBody.toByteArray(StandardCharsets.UTF_8),
                sigBytes,
                ROTATED_PUBLIC_KEY_B64
            )
            if (ok) true to canonicalBody else false to ""
        } catch (e: Exception) {
            Log.w("PennyVPN", "Signed list verify error: ${e.message}")
            false to ""
        }
    }
}
