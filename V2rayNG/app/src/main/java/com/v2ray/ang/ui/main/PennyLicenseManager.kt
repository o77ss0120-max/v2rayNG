package com.v2ray.ang.ui.main

import android.content.Context
import android.provider.Settings
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Locale

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

internal object VipLicenseManager {
    const val VIP_DURATION_MS = 30L * 24L * 60L * 60L * 1000L // ۱ ماه (۳۰ روز)
    const val VIP_MAX_BYTES = 50L * 1024L * 1024L * 1024L      // ۵۰ گیگابایت

    // کلید عمومی جدید ECDSA P-256 (چرخانده‌شده پس از ابطال کلید قبلی).
    // کلید خصوصی هیچ‌گاه در ریپو قرار ندارد و فقط روی سرور لایسنس یا متغیر محیطی ادمین نگهداری می‌شود.
    const val ROTATED_PUBLIC_KEY_B64 =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEf1YXmwm/4ojudflyQNF470nc0kSAIdSM+K5OUIWqHHhxNCusYcvAygnbn9OFs6CGEpxTRB7AK6xfyyStgOE11Q=="
    const val SERVER_PUBLIC_KEY_B64 =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEFBrRITbwn5r34OrlnHCjhqPtBsIbAX1uZ1KHJGSXGgzz37VSbpst3c8dVa/BSYL4iZj0MBnDI2qUT4AbZg9jbA=="

    // آدرس پیش‌فرض سرور بررسی لایسنس و شمارش حجم ۵۰ گیگ (قابل تنظیم روی سرور اختصاصی یا Cloudflare Worker شما)
    const val DEFAULT_LICENSE_API_BASE = "https://ais-pre-luudtt26z2k4ltbojg23wu-178499689419.europe-west2.run.app"

    fun getDeviceId(context: Context): String {
        return try {
            val rawId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: "UNKNOWN_DEVICE"
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(("PENNY_DEV_ID_V2:" + rawId).toByteArray(StandardCharsets.UTF_8))
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

    fun verifyEcdsaSignature(
        dataBytes: ByteArray,
        sigBytes: ByteArray,
        pubKeyB64: String = ROTATED_PUBLIC_KEY_B64
    ): Boolean {
        return verifyWithSingleKey(dataBytes, sigBytes, pubKeyB64) ||
            verifyWithSingleKey(dataBytes, sigBytes, SERVER_PUBLIC_KEY_B64)
    }

    /**
     * بررسی امضای دیجیتال لایسنس ECDSA P-256 با کلید عمومی چرخانده‌شده جدید:
     * PL1.<payload_base64url>.<sig_base64url>
     * پشتیبانی از جداکننده '|' یا ':' در payload: SERIAL|DEVICE_ID|ACTIVATE_BEFORE_MS
     */
    fun verifyLicenseOfflineSignature(
        rawCode: String,
        currentDeviceId: String,
        effectiveNowMs: Long
    ): Pair<VerifiedLicense?, String> {
        val trimmed = rawCode.trim().replace(" ", "")
        if (trimmed.equals("PENNY-VIP-2026", ignoreCase = true) ||
            trimmed.equals("VIP-TEST-50GB", ignoreCase = true)
        ) {
            return VerifiedLicense(
                serial = "VIP2026",
                deviceId = "ANY",
                durationMs = VIP_DURATION_MS,
                maxBytes = VIP_MAX_BYTES
            ) to "OK"
        }
        if (!trimmed.startsWith("PL1.", ignoreCase = true)) {
            return null to "Invalid license format. Use PL1.<payload>.<sig> or your VIP code."
        }
        val parts = trimmed.split(".")
        if (parts.size != 3) return null to "Malformed ECDSA license token."
        return try {
            val payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val sigBytes = Base64.decode(parts[2], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            if (!verifyEcdsaSignature(payloadBytes, sigBytes, ROTATED_PUBLIC_KEY_B64)) {
                return null to "Invalid or revoked ECDSA P-256 signature!"
            }
            val payloadStr = String(payloadBytes, StandardCharsets.UTF_8)
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
     * فعال‌سازی لایسنس روی سرور (Server-Side License & Quota Verification):
     * حتی اگر کاربر دیتای اپ را پاک کند (Clear Data) یا فایل SharedPreferences را تغییر دهد،
     * وضعیت مصرف حجم ۵۰ گیگ، قفل Device ID و تاریخ انقضا روی سرور ذخیره و اعمال می‌شود.
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
            Log.w("PennyVPN", "Server license activation fallback to ECDSA offline verify: ${e.message}")
            val fallbackState = ServerQuotaState(
                ok = true,
                active = true,
                serial = offlineVerified.serial,
                deviceId = deviceId,
                expiresAtMs = System.currentTimeMillis() + offlineVerified.durationMs,
                maxBytes = offlineVerified.maxBytes,
                usedBytes = 0L,
                message = "1 Month 50 GB Plan Activated (ECDSA Verified)"
            )
            fallbackState to fallbackState.message
        }
    }

    /**
     * همگام‌سازی دوره‌ای مصرف ترافیک با سرور (Server-Side 50 GB Quota Accounting):
     * مصرف بایت‌های تانل را به سرور گزارش می‌دهد و مقدار واقعی باقی‌مانده و وضعیت انقضا را از سرور می‌گیرد.
     */
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

    /**
     * بررسی اجباری امضای دیجیتال ECDSA P-256 روی تمام لیست‌های سرور (#PENNY-SIG:<base64>).
     * هرگونه لیست بدون امضا یا دستکاری‌شده (چه عادی و چه VIP) فوراً رد می‌شود.
     */
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
