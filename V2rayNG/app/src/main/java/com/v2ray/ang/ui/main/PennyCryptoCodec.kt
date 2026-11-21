package com.v2ray.ang.ui.main

import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal data class VerifiedLicense(
    val serial: String,
    val deviceId: String,
    val durationMs: Long,
    val maxBytes: Long
)

/**
 * لایه رمزنگاری و اعتبارسنجی خالص بدون وابستگی به چارچوب اندروید
 * تا دقیقاً همین کد در تست‌های واحد JUnit 5 روی JVM قابل اجرا و راستی‌آزمایی باشد.
 */
internal object PennyCryptoCodec {

    const val VIP_DURATION_MS = 30L * 24L * 60L * 60L * 1000L
    const val VIP_MAX_BYTES = 50L * 1024L * 1024L * 1024L

    const val ROTATED_PUBLIC_KEY_B64 =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEf1YXmwm/4ojudflyQNF470nc0kSAIdSM+K5OUIWqHHhxNCusYcvAygnbn9OFs6CGEpxTRB7AK6xfyyStgOE11Q=="

    private const val B64_STD = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private const val STEALTH_VAULT_KEY_PREFIX = "PENNY_STEALTH_VAULT_KEY_2026_V3:"
    private const val STEALTH_VAULT_MAC_KEY = "PENNY_STEALTH_VAULT_MAC_2026_V3"
    private const val MAX_LOCAL_CLOCK_DRIFT_MS = 7L * 24L * 60L * 60L * 1000L

    fun decodeBase64Flexible(input: String): ByteArray {
        val cleaned = input.trim()
            .replace("\r", "")
            .replace("\n", "")
            .replace(" ", "")
            .replace('-', '+')
            .replace('_', '/')
        if (cleaned.isEmpty()) return ByteArray(0)
        val out = ByteArray((cleaned.length * 3) / 4)
        var outLen = 0
        var buffer = 0
        var bitsCollected = 0
        for (ch in cleaned) {
            if (ch == '=') break
            val idx = B64_STD.indexOf(ch)
            if (idx < 0) continue
            buffer = (buffer shl 6) or idx
            bitsCollected += 6
            if (bitsCollected >= 8) {
                bitsCollected -= 8
                out[outLen++] = ((buffer shr bitsCollected) and 0xFF).toByte()
            }
        }
        return out.copyOf(outLen)
    }

    fun encodeBase64UrlNoPadding(bytes: ByteArray): String {
        val sb = StringBuilder((bytes.size * 4) / 3 + 4)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val hasB1 = i + 1 < bytes.size
            val hasB2 = i + 2 < bytes.size
            val b1 = if (hasB1) bytes[i + 1].toInt() and 0xFF else 0
            val b2 = if (hasB2) bytes[i + 2].toInt() and 0xFF else 0
            val triple = (b0 shl 16) or (b1 shl 8) or b2
            sb.append(B64_STD[(triple shr 18) and 0x3F])
            sb.append(B64_STD[(triple shr 12) and 0x3F])
            if (hasB1) sb.append(B64_STD[(triple shr 6) and 0x3F])
            if (hasB2) sb.append(B64_STD[triple and 0x3F])
            i += 3
        }
        return sb.toString()
            .replace('+', '-')
            .replace('/', '_')
    }

    fun computeDeviceIdHex(hwDigest: ByteArray, bytesCount: Int = 8): String {
        val count = bytesCount.coerceIn(6, 16)
        if (hwDigest.size < count) return "0000-0000-0000-0000"
        val sb = StringBuilder()
        for (i in 0 until count) {
            if (i > 0 && i % 2 == 0) sb.append('-')
            sb.append(String.format(Locale.US, "%02X", hwDigest[i].toInt() and 0xFF))
        }
        return sb.toString()
    }

    fun computeUserUuidV4(hwDigest: ByteArray): String {
        if (hwDigest.size < 16) return "00000000-0000-4000-8000-000000000000"
        val d = hwDigest.clone()
        d[6] = ((d[6].toInt() and 0x0F) or 0x40).toByte()
        d[8] = ((d[8].toInt() and 0x3F) or 0x80).toByte()
        val sb = StringBuilder(36)
        for (i in 0 until 16) {
            if (i == 4 || i == 6 || i == 8 || i == 10) sb.append('-')
            sb.append(String.format(Locale.US, "%02x", d[i].toInt() and 0xFF))
        }
        return sb.toString()
    }

    fun computeQuotaHmacHex(hwDigest: ByteArray, payload: String): String {
        return try {
            val keySpec = SecretKeySpec(hwDigest, "HmacSHA256")
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(keySpec)
            mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { String.format(Locale.US, "%02x", it.toInt() and 0xFF) }
                .take(16)
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * همگام‌سازی ساعت مقاوم در برابر دستکاری و جهش‌های تصادفی ساعت گوشی (جلوگیری از قفل دائمی expiry):
     * - در صورت در دسترس بودن ساعت تاییدشده سرور (serverSyncEpochMs)، از آن مبنا استفاده می‌کند.
     * - ratchet محلی حداکثر تا ۷ روز اختلاف با ساعت سیستم را نگه می‌دارد تا پرش ناگهانی ساعت کاربر
     *   باعث انقضای دائمی اشتراک نشود.
     */
    fun resolveEffectiveNowMs(
        systemWallMs: Long,
        storedLastSeenMs: Long,
        lastServerNowMs: Long = 0L,
        elapsedAtServerSyncMs: Long = 0L,
        currentElapsedMs: Long = 0L
    ): Long {
        if (lastServerNowMs > 0L && elapsedAtServerSyncMs > 0L && currentElapsedMs >= elapsedAtServerSyncMs) {
            val serverDerivedNow = lastServerNowMs + (currentElapsedMs - elapsedAtServerSyncMs)
            return maxOf(serverDerivedNow, minOf(systemWallMs, serverDerivedNow + MAX_LOCAL_CLOCK_DRIFT_MS))
        }
        return if (storedLastSeenMs > systemWallMs && (storedLastSeenMs - systemWallMs) <= MAX_LOCAL_CLOCK_DRIFT_MS) {
            storedLastSeenMs
        } else {
            systemWallMs
        }
    }

    fun verifyEcdsaSignature(
        dataBytes: ByteArray,
        sigBytes: ByteArray,
        pubKeyB64: String = ROTATED_PUBLIC_KEY_B64
    ): Boolean {
        return try {
            val keyBytes = decodeBase64Flexible(pubKeyB64)
            if (keyBytes.isEmpty()) return false
            val keySpec = X509EncodedKeySpec(keyBytes)
            val pubKey = KeyFactory.getInstance("EC").generatePublic(keySpec)
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(pubKey)
            verifier.update(dataBytes)
            verifier.verify(sigBytes)
        } catch (_: Exception) {
            false
        }
    }

    private fun extractFlatJsonString(json: String, key: String): String? {
        val m = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"([^\"]*)\"").find(json)
        return m?.groupValues?.getOrNull(1)
    }

    private fun extractFlatJsonLong(json: String, key: String): Long? {
        val m = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(-?\\d+)").find(json)
        return m?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    fun verifyLicenseOfflineSignature(
        rawCode: String,
        currentDeviceId: String,
        effectiveNowMs: Long,
        pubKeyB64: String = ROTATED_PUBLIC_KEY_B64
    ): Pair<VerifiedLicense?, String> {
        val trimmed = rawCode.trim().replace(" ", "")
        if (!trimmed.startsWith("PL1.", ignoreCase = true) && !trimmed.startsWith("PV1.", ignoreCase = true)) {
            return null to "Invalid license format. Please enter a valid ECDSA P-256 license (PL1....)."
        }
        val parts = trimmed.split(".")
        if (parts.size != 3) return null to "Malformed ECDSA license token."
        return try {
            val payloadBytes = decodeBase64Flexible(parts[1])
            val sigBytes = decodeBase64Flexible(parts[2])
            if (payloadBytes.isEmpty() || sigBytes.isEmpty()) {
                return null to "Malformed ECDSA license token."
            }
            if (!verifyEcdsaSignature(payloadBytes, sigBytes, pubKeyB64)) {
                return null to "Invalid or revoked ECDSA P-256 signature!"
            }
            val payloadStr = String(payloadBytes, StandardCharsets.UTF_8).trim()
            if (payloadStr.startsWith("{")) {
                val serial = extractFlatJsonString(payloadStr, "s")?.trim()?.uppercase(Locale.US).orEmpty()
                if (serial.isEmpty()) return null to "Missing license serial in token."
                val targetDevice = (extractFlatJsonString(payloadStr, "d") ?: "ANY").trim().uppercase(Locale.US)
                if (targetDevice != "ANY" && targetDevice != "*" && targetDevice != currentDeviceId.uppercase(Locale.US)) {
                    return null to "License is locked to a different Device ID."
                }
                val expRaw = extractFlatJsonLong(payloadStr, "exp") ?: 0L
                val expMs = if (expRaw in 1..9_999_999_999L) expRaw * 1000L else expRaw
                val maxB = (extractFlatJsonLong(payloadStr, "max") ?: VIP_MAX_BYTES)
                    .coerceIn(1L * 1024L * 1024L * 1024L, 1000L * 1024L * 1024L * 1024L)
                val durMs = if (expMs > 0L) {
                    val remMs = expMs - effectiveNowMs
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
            if (serial.isEmpty()) return null to "Missing license serial in token."
            val targetDevice = fields[1].trim().uppercase(Locale.US)
            val rawDeadline = fields[2].trim().toLongOrNull() ?: 0L
            val deadlineMs = if (rawDeadline in 1..9_999_999_999L) rawDeadline * 1000L else rawDeadline
            val days = fields.getOrNull(3)?.trim()?.toLongOrNull()?.coerceIn(1L, 365L) ?: 30L
            val gb = fields.getOrNull(4)?.trim()?.toLongOrNull()?.coerceIn(1L, 1000L) ?: 50L

            if (targetDevice != "ANY" && targetDevice != "*" && targetDevice != currentDeviceId.uppercase(Locale.US)) {
                return null to "License is locked to a different Device ID."
            }
            if (deadlineMs > 0L && effectiveNowMs > 0L && effectiveNowMs > deadlineMs) {
                return null to "Activation deadline for this license has expired."
            }
            VerifiedLicense(
                serial = serial,
                deviceId = targetDevice,
                durationMs = days * 24L * 3600_000L,
                maxBytes = gb * 1024L * 1024L * 1024L
            ) to "OK"
        } catch (_: Exception) {
            null to "Malformed activation code."
        }
    }

    fun verifySignedServerList(
        rawContent: String,
        pubKeyB64: String = ROTATED_PUBLIC_KEY_B64
    ): Pair<Boolean, String> {
        val normalized = rawContent.replace("\r\n", "\n").trim()
        val lines = normalized.lines()
        val firstLine = lines.firstOrNull()?.trim() ?: return false to ""
        if (!firstLine.startsWith("#PENNY-SIG:")) {
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
            val sigBytes = decodeBase64Flexible(sigB64)
            if (sigBytes.isEmpty()) return false to ""
            val ok = verifyEcdsaSignature(
                canonicalBody.toByteArray(StandardCharsets.UTF_8),
                sigBytes,
                pubKeyB64
            )
            if (ok) true to canonicalBody else false to ""
        } catch (_: Exception) {
            false to ""
        }
    }

    /**
     * رمزگشایی فایل استتارشده font_metrics_v2.bin:
     * - پشتیبانی از نسخه احراز هویت‌شده OTTO v3 (همراه با HMAC-SHA256 32 بایتی برای یکپارچگی باینری)
     * - پشتیبانی از نسخه OTTO v2 (که در استفاده از منبع از راه دور ملزم به داشتن #PENNY-SIG: است)
     */
    fun decryptStealthVaultBytes(rawBytes: ByteArray): String {
        if (rawBytes.size <= 8) return ""
        if (rawBytes[0] != 'O'.code.toByte() ||
            rawBytes[1] != 'T'.code.toByte() ||
            rawBytes[2] != 'T'.code.toByte() ||
            rawBytes[3] != 'O'.code.toByte()
        ) {
            return ""
        }
        val versionMajor = rawBytes[5].toInt() and 0xFF
        val cipherOffset = if (versionMajor >= 3) {
            if (rawBytes.size <= 40) return ""
            val providedMac = rawBytes.copyOfRange(8, 40)
            val cipherBody = rawBytes.copyOfRange(40, rawBytes.size)
            val expectedMac = try {
                val mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec(STEALTH_VAULT_MAC_KEY.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
                mac.update(rawBytes, 0, 8)
                mac.update(cipherBody)
                mac.doFinal()
            } catch (_: Exception) {
                return ""
            }
            if (!MessageDigest.isEqual(providedMac, expectedMac)) return ""
            40
        } else {
            8
        }

        val payloadLen = rawBytes.size - cipherOffset
        val out = ByteArray(payloadLen)
        val md = MessageDigest.getInstance("SHA-256")
        var blockIdx = 0
        var offset = 0
        while (offset < payloadLen) {
            val stream = md.digest((STEALTH_VAULT_KEY_PREFIX + blockIdx).toByteArray(StandardCharsets.UTF_8))
            for (b in stream) {
                if (offset >= payloadLen) break
                out[offset] = (rawBytes[cipherOffset + offset].toInt() xor b.toInt()).toByte()
                offset++
            }
            blockIdx++
        }
        return String(out, StandardCharsets.UTF_8).trim()
    }

    fun isSemanticVersionNewer(currentVersion: String, remoteTag: String): Boolean {
        val cleanCurrent = currentVersion.trim().removePrefix("v").substringBefore('-')
        val cleanRemote = remoteTag.trim().removePrefix("v").substringBefore('-')
        if (cleanRemote.isBlank() || cleanCurrent == cleanRemote) return false
        val currParts = cleanCurrent.split('.').map { it.toIntOrNull() ?: 0 }
        val remParts = cleanRemote.split('.').map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(currParts.size, remParts.size)
        for (i in 0 until maxLen) {
            val c = currParts.getOrElse(i) { 0 }
            val r = remParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
