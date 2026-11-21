package com.v2ray.ang.ui.main

import android.content.Context
import android.media.MediaDrm
import android.os.Build
import android.provider.Settings
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

internal data class ServerQuotaState(
    val ok: Boolean,
    val active: Boolean,
    val serial: String,
    val deviceId: String,
    val expiresAtMs: Long,
    val maxBytes: Long,
    val usedBytes: Long,
    val message: String,
    val serverNowMs: Long = 0L
)

internal data class DailyQuotaSyncResult(
    val usedBytes: Long,
    val maxBytes: Long,
    val blocked: Boolean,
    val serverNowMs: Long = 0L
)

internal object VipLicenseManager {
    const val VIP_DURATION_MS = PennyCryptoCodec.VIP_DURATION_MS // ۱ ماه (۳۰ روز)
    const val VIP_MAX_BYTES = PennyCryptoCodec.VIP_MAX_BYTES     // ۵۰ گیگابایت

    const val ROTATED_PUBLIC_KEY_B64 = PennyCryptoCodec.ROTATED_PUBLIC_KEY_B64

    // نکته استقرار: در نسخه نهایی حتماً دامنه دائمی Cloudflare Worker یا API اختصاصی را تنظیم کنید
    // (و کاربران همچنین می‌توانند آدرس سرور لایسنس HTTPS دلخواه را از منوی VIP تغییر دهند).
    const val DEFAULT_LICENSE_API_BASE = "https://ais-pre-luudtt26z2k4ltbojg23wu-178499689419.europe-west2.run.app"

    private val WIDEVINE_UUID = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

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

        var fallbackId = ""
        if (androidId.isEmpty() && widevineHex.isEmpty()) {
            fallbackId = runCatching {
                val f = File(context.noBackupFilesDir, ".pny_hw_fallback")
                if (f.exists()) {
                    f.readText(StandardCharsets.UTF_8).trim()
                } else {
                    UUID.randomUUID().toString().also {
                        f.writeText(it, StandardCharsets.UTF_8)
                    }
                }
            }.getOrDefault("")
        }

        val hwSpec = "${Build.BOARD}|${Build.BRAND}|${Build.DEVICE}|${Build.HARDWARE}"
        val rawSeed = "PENNY_HW_FINGERPRINT_V3:$androidId:$widevineHex:$fallbackId:$hwSpec"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(rawSeed.toByteArray(StandardCharsets.UTF_8))
        cachedHwDigest = digest
        return digest
    }

    /**
     * شناسه ۶۴ بیتی (۱۶ کاراکتر هگز با قالب XXXX-XXXX-XXXX-XXXX) برای جلوگیری از برخورد در مقیاس بالا
     */
    fun getDeviceId(context: Context): String {
        return try {
            PennyCryptoCodec.computeDeviceIdHex(computeHardwareDigest(context), bytesCount = 8)
        } catch (e: Exception) {
            Log.w("PennyVPN", "DeviceId fallback: ${e.message}")
            "0000-0000-0000-0000"
        }
    }

    /**
     * تولید UUID اختصاصی ۱۲۸ بیتی برای هر کاربر بر اساس اثرانگشت سخت‌افزاری دستگاه
     */
    fun getUserUuid(context: Context): String {
        return try {
            PennyCryptoCodec.computeUserUuidV4(computeHardwareDigest(context))
        } catch (_: Exception) {
            "00000000-0000-4000-8000-000000000000"
        }
    }

    private fun computeQuotaHmac(context: Context, payload: String): String {
        return runCatching {
            PennyCryptoCodec.computeQuotaHmacHex(computeHardwareDigest(context), payload)
        }.getOrDefault("")
    }

    fun isTrustedHttpsEndpoint(rawUrl: String): Boolean {
        val clean = rawUrl.trim()
        if (!clean.startsWith("https://", ignoreCase = true)) return false
        return runCatching {
            val u = URL(clean)
            val h = u.host.trim().lowercase(Locale.US)
            if (h.isEmpty() || h == "localhost" || h.endsWith(".local") || h.endsWith(".internal")) return false
            val ipMatch = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""").matchEntire(h)
            if (ipMatch != null) {
                val a = ipMatch.groupValues[1].toInt()
                val b = ipMatch.groupValues[2].toInt()
                if (a == 0 || a == 10 || a == 127) return false
                if (a == 169 && b == 254) return false
                if (a == 172 && b in 16..31) return false
                if (a == 192 && b == 168) return false
                if (a == 100 && b in 64..127) return false
            }
            true
        }.getOrDefault(false)
    }

    /**
     * ذخیره‌سازی محلی با برچسب یکپارچگی HMAC-SHA256 برای سهمیه روزانه ۱ گیگابایت و پاداش تبلیغ
     */
    fun readTamperResistantDailyAllowance(
        context: Context,
        deviceId: String,
        dayKey: String,
        defaultMaxBytes: Long,
        defaultAdsClaimed: Int
    ): Pair<Long, Int> {
        try {
            val f = File(context.noBackupFilesDir, ".pny_a_${deviceId.hashCode()}.dat")
            if (f.exists()) {
                val parts = f.readText(StandardCharsets.UTF_8).trim().split("|")
                if (parts.size == 4 && parts[0] == dayKey) {
                    val maxB = parts[1].toLongOrNull() ?: defaultMaxBytes
                    val ads = parts[2].toIntOrNull() ?: defaultAdsClaimed
                    val expectedMac = computeQuotaHmac(context, "${parts[0]}|${parts[1]}|${parts[2]}")
                    if (expectedMac.isNotEmpty() && MessageDigest.isEqual(
                            expectedMac.toByteArray(StandardCharsets.UTF_8),
                            parts[3].toByteArray(StandardCharsets.UTF_8)
                        )
                    ) {
                        return maxB.coerceAtLeast(defaultMaxBytes) to ads.coerceIn(0, 10)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return defaultMaxBytes to defaultAdsClaimed.coerceIn(0, 10)
    }

    fun writeTamperResistantDailyAllowance(
        context: Context,
        deviceId: String,
        dayKey: String,
        maxBytes: Long,
        adsClaimed: Int
    ) {
        try {
            val f = File(context.noBackupFilesDir, ".pny_a_${deviceId.hashCode()}.dat")
            val payload = "$dayKey|$maxBytes|$adsClaimed"
            val mac = computeQuotaHmac(context, payload)
            f.writeText("$payload|$mac", StandardCharsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    fun readTamperResistantDailyUsed(context: Context, deviceId: String, dayKey: String, prefsUsed: Long): Long {
        var maxObserved = prefsUsed.coerceAtLeast(0L)
        try {
            val backupFile = File(context.noBackupFilesDir, ".pny_q_${deviceId.hashCode()}.dat")
            if (backupFile.exists()) {
                val text = backupFile.readText(StandardCharsets.UTF_8).trim()
                val parts = text.split("|")
                if (parts.size >= 3 && parts[0] == dayKey) {
                    val v = parts[1].toLongOrNull() ?: 0L
                    val expectedMac = computeQuotaHmac(context, "${parts[0]}|${parts[1]}")
                    if (expectedMac.isNotEmpty() && MessageDigest.isEqual(
                            expectedMac.toByteArray(StandardCharsets.UTF_8),
                            parts[2].toByteArray(StandardCharsets.UTF_8)
                        )
                    ) {
                        if (v > maxObserved) maxObserved = v
                    }
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
            val payload = "$dayKey|$usedBytes"
            val mac = computeQuotaHmac(context, payload)
            backupFile.writeText("$payload|$mac", StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.d("PennyVPN", "Quota file write warning: ${e.message}")
        }
    }

    private fun postJson(url: String, bodyJson: String): Pair<Int, String>? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PennyVPN/${PennyServerScanner.APP_VERSION_NAME}")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            response.code to text
        }
    }

    /**
     * همگام‌سازی سهمیه ۱ گیگابایت روزانه و تله‌متری Edge/Transport از طریق OkHttp
     */
    fun syncDailyQuotaAndTelemetry(
        apiBaseUrl: String,
        deviceId: String,
        userUuid: String,
        dayKey: String,
        localUsedBytes: Long,
        deltaBytes: Long,
        activeMode: String,
        connectedServerLabel: String,
        activeEdgeId: String = "",
        activeTransport: String = "",
        edgeLatencyMs: Long = -1L,
        transportHealth: String = "OPTIMAL"
    ): DailyQuotaSyncResult? {
        val cleanBase = apiBaseUrl.trim().trimEnd('/')
        if (!isTrustedHttpsEndpoint(cleanBase)) return null
        return try {
            val reqObj = JSONObject()
                .put("deviceId", deviceId)
                .put("userUuid", userUuid)
                .put("dayKey", dayKey)
                .put("localUsedBytes", localUsedBytes.coerceAtLeast(0L))
                .put("deltaBytes", deltaBytes.coerceAtLeast(0L))
                .put("activeMode", activeMode)
            if (activeEdgeId.isNotBlank()) {
                reqObj.put("activeEdgeId", activeEdgeId.take(64))
            }
            if (activeTransport.isNotBlank()) {
                reqObj.put("activeTransport", activeTransport.take(32))
            }
            if (edgeLatencyMs in 1L..29999L) {
                reqObj.put("edgeLatencyMs", edgeLatencyMs)
            }
            if (transportHealth.isNotBlank()) {
                reqObj.put("transportHealth", transportHealth.take(24))
            }

            val (status, respText) = postJson("$cleanBase/api/v1/quota/daily-sync", reqObj.toString()) ?: return null
            if (status !in 200..299 || respText.isBlank()) return null
            val obj = JSONObject(respText)
            if (!obj.optBoolean("ok", false)) return null
            DailyQuotaSyncResult(
                usedBytes = obj.optLong("dailyUsedBytes", obj.optLong("usedBytes", localUsedBytes)),
                maxBytes = obj.optLong("maxDailyBytes", obj.optLong("maxBytes", 1L * 1024L * 1024L * 1024L)),
                blocked = !obj.optBoolean("allowed", true) || obj.optBoolean("blocked", false),
                serverNowMs = obj.optLong("serverNowMs", 0L)
            )
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

    fun verifyEcdsaSignature(
        dataBytes: ByteArray,
        sigBytes: ByteArray,
        pubKeyB64: String = ROTATED_PUBLIC_KEY_B64
    ): Boolean {
        return PennyCryptoCodec.verifyEcdsaSignature(dataBytes, sigBytes, pubKeyB64)
    }

    fun verifyLicenseOfflineSignature(
        rawCode: String,
        currentDeviceId: String,
        effectiveNowMs: Long
    ): Pair<VerifiedLicense?, String> {
        return PennyCryptoCodec.verifyLicenseOfflineSignature(
            rawCode = rawCode,
            currentDeviceId = currentDeviceId,
            effectiveNowMs = effectiveNowMs,
            pubKeyB64 = ROTATED_PUBLIC_KEY_B64
        )
    }

    /**
     * فعال‌سازی سخت‌گیرانه سمت سرور از طریق OkHttp:
     * ابتدا امضای ECDSA P-256 بررسی می‌شود و سپس حتماً باید سرور لایسنس آن را تایید کند.
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
        if (!isTrustedHttpsEndpoint(cleanBase)) {
            return null to "License verification server URL must be a trusted public HTTPS endpoint."
        }

        return try {
            val reqJson = JSONObject()
                .put("code", rawCode.trim())
                .put("deviceId", deviceId)
                .toString()

            val (_, respText) = postJson("$cleanBase/api/v1/license/activate", reqJson)
                ?: return null to "Empty response from license server."
            val obj = JSONObject(respText.ifBlank { "{}" })
            val ok = obj.optBoolean("ok", false)
            val msg = obj.optString("message", if (ok) "Activated on server" else "Server rejected license")
            if (!ok) {
                return null to msg
            }
            val serverNow = obj.optLong("serverNowMs", 0L)
            val fallbackBaseNow = if (serverNow > 0L) serverNow else System.currentTimeMillis()
            val state = ServerQuotaState(
                ok = true,
                active = obj.optBoolean("active", true),
                serial = obj.optString("serial", offlineVerified.serial),
                deviceId = obj.optString("deviceId", deviceId),
                expiresAtMs = obj.optLong("expiresAtMs", fallbackBaseNow + offlineVerified.durationMs),
                maxBytes = obj.optLong("maxBytes", offlineVerified.maxBytes),
                usedBytes = obj.optLong("usedBytes", 0L),
                message = msg,
                serverNowMs = serverNow
            )
            state to msg
        } catch (e: Exception) {
            Log.w("PennyVPN", "Server license activation failed: ${e.message}")
            null to "License server unreachable. Please check your internet connection and try again."
        }
    }

    fun syncTrafficWithServer(
        apiBaseUrl: String,
        serial: String,
        deviceId: String,
        deltaBytes: Long,
        code: String = ""
    ): ServerQuotaState? {
        val cleanBase = apiBaseUrl.trim().trimEnd('/')
        if (!isTrustedHttpsEndpoint(cleanBase) || serial.isBlank()) return null

        return try {
            val reqJson = JSONObject()
                .put("serial", serial)
                .put("deviceId", deviceId)
                .put("deltaBytes", deltaBytes.coerceAtLeast(0L))
                .apply { if (code.isNotBlank()) put("code", code.trim()) }
                .toString()

            val (_, respText) = postJson("$cleanBase/api/v1/license/heartbeat", reqJson) ?: return null
            if (respText.isBlank()) return null
            val obj = JSONObject(respText)
            ServerQuotaState(
                ok = obj.optBoolean("ok", false),
                active = obj.optBoolean("active", false),
                serial = obj.optString("serial", serial),
                deviceId = obj.optString("deviceId", deviceId),
                expiresAtMs = obj.optLong("expiresAtMs", 0L),
                maxBytes = obj.optLong("maxBytes", VIP_MAX_BYTES),
                usedBytes = obj.optLong("usedBytes", 0L),
                message = obj.optString("message", ""),
                serverNowMs = obj.optLong("serverNowMs", 0L)
            )
        } catch (e: Exception) {
            Log.w("PennyVPN", "Traffic heartbeat warning: ${e.message}")
            null
        }
    }

    fun verifySignedServerList(rawContent: String): Pair<Boolean, String> {
        val result = PennyCryptoCodec.verifySignedServerList(rawContent, ROTATED_PUBLIC_KEY_B64)
        if (!result.first) {
            Log.w("PennyVPN", "Rejected unsigned or invalid ECDSA server list")
        }
        return result
    }
}
