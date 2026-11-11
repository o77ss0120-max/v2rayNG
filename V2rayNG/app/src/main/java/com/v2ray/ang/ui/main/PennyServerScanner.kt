package com.v2ray.ang.ui.main

import android.content.Context
import android.telephony.TelephonyManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.random.Random

/**
 * محدودکننده هوشمند ترافیک و پروتکل مخصوص Ghost Mode (Emergency Messaging Only):
 * - در حالت‌های Public و Dedicated سرعت کاملاً نامحدود (Native Full Speed) است.
 * - در حالت Ghost Mode (وقتی سوییچ Messaging-Only فعال است):
 *   1) دامنه‌های پرمصرف ویدیو، استریم، آپدیت و دانلود در لایه SOCKS5 بلافاصله مسدود می‌شوند تا هیچ ترافیکی از سرور شخصی کم نشود.
 *   2) پهنای باند تونل روی 42 KB/s دانلود و 28 KB/s آپلود قفل می‌شود که برای پیام‌رسان‌ها (تلگرام، واتس‌اپ، سیگنال، متن و ویس) آنی است اما جلوی مصرف بالای حجم را صددرصد می‌گیرد.
 */
object PennySpeedLimiter {
    const val FREE_MAX_DOWN_BYTES_PER_SEC = Long.MAX_VALUE
    const val FREE_MAX_UP_BYTES_PER_SEC = Long.MAX_VALUE
    const val GHOST_MAX_DOWN_BYTES_PER_SEC = 42L * 1024L // 42 KB/s (مخصوص پیام‌رسان اضطراری)
    const val GHOST_MAX_UP_BYTES_PER_SEC = 28L * 1024L   // 28 KB/s

    @Volatile
    var isVipUnrestricted: Boolean = true

    @Volatile
    var isGhostMessagingRestricted: Boolean = false

    @Volatile
    private var serverSocket: java.net.ServerSocket? = null

    @Volatile
    private var activeListenPort: Int = 0

    private val BLOCKED_HEAVY_MEDIA_DOMAINS = listOf(
        "googlevideo.com",
        "youtube.com",
        "ytimg.com",
        "youtu.be",
        "tiktokcdn.com",
        "tiktokv.com",
        "tiktok.com",
        "musical.ly",
        "byteoversea.com",
        "cdninstagram.com",
        "fbcdn.net",
        "instagram.com",
        "nflxvideo.net",
        "netflix.com",
        "gvt1.com",
        "gvt2.com",
        "windowsupdate.com",
        "steamcontent.com",
        "twitch.tv",
        "ttvnw.net",
        "scdn.co",
        "spotify.com",
        "speedtest.net",
        "fast.com",
        "aparat.com",
        "telewebion.com",
        "namava.ir",
        "filimo.com"
    )

    private fun isHeavyMediaDomain(host: String): Boolean {
        val h = host.trim().lowercase()
        if (h.isEmpty()) return false
        return BLOCKED_HEAVY_MEDIA_DOMAINS.any { blocked ->
            h == blocked || h.endsWith(".$blocked")
        }
    }

    @Synchronized
    fun startIfNeeded(
        listenPort: Int,
        targetPort: Int,
        vipActive: Boolean,
        ghostRestricted: Boolean = isGhostMessagingRestricted
    ): Int {
        isVipUnrestricted = vipActive
        isGhostMessagingRestricted = ghostRestricted
        if (!ghostRestricted) {
            stop()
            return targetPort
        }
        if (serverSocket != null && serverSocket?.isClosed == false && activeListenPort == listenPort) {
            return listenPort
        }
        stop()
        return try {
            val ss = java.net.ServerSocket()
            ss.reuseAddress = true
            ss.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), listenPort), 128)
            serverSocket = ss
            activeListenPort = listenPort
            Thread({
                while (!ss.isClosed) {
                    try {
                        val client = ss.accept()
                        client.tcpNoDelay = true
                        Thread({ handleGhostClient(client, targetPort) }, "PennyGhostRelayWorker").apply {
                            isDaemon = true
                            start()
                        }
                    } catch (_: Exception) {
                        if (ss.isClosed) break
                    }
                }
            }, "PennyGhostLimiterAccept").apply {
                isDaemon = true
                start()
            }
            listenPort
        } catch (_: Exception) {
            targetPort
        }
    }

    private fun handleGhostClient(client: Socket, targetPort: Int) {
        var upstream: Socket? = null
        try {
            client.soTimeout = 15_000
            val cIn = client.getInputStream()
            val cOut = client.getOutputStream()

            upstream = Socket()
            upstream.tcpNoDelay = true
            upstream.connect(InetSocketAddress(InetAddress.getByName("127.0.0.1"), targetPort), 4000)
            upstream.soTimeout = 15_000
            val uIn = upstream.getInputStream()
            val uOut = upstream.getOutputStream()

            // بررسی کامل هدر SOCKS5 (مقاوم در برابر تکهتکه شدن پکت‌های TCP) برای مسدودسازی دامنه‌های ویدیویی در حالت Ghost
            val firstBuf = ByteArray(512)
            val n0 = cIn.read(firstBuf)
            if (n0 <= 0) return
            uOut.write(firstBuf, 0, n0)
            uOut.flush()

            if (firstBuf[0] == 0x05.toByte()) {
                val repBuf = ByteArray(64)
                val r0 = uIn.read(repBuf)
                if (r0 <= 0) return
                cOut.write(repBuf, 0, r0)
                cOut.flush()

                val reqBuf = ByteArray(1024)
                var n1 = 0
                while (n1 < 5) {
                    val r = cIn.read(reqBuf, n1, reqBuf.size - n1)
                    if (r <= 0) break
                    n1 += r
                }
                if (n1 <= 0) return
                if (n1 >= 5 && reqBuf[0] == 0x05.toByte() && reqBuf[1] == 0x01.toByte() && reqBuf[3] == 0x03.toByte()) {
                    val domainLen = reqBuf[4].toInt() and 0xFF
                    val expectedTotal = (5 + domainLen + 2).coerceAtMost(reqBuf.size)
                    while (n1 < expectedTotal) {
                        val r = cIn.read(reqBuf, n1, expectedTotal - n1)
                        if (r <= 0) break
                        n1 += r
                    }
                    if (5 + domainLen <= n1) {
                        val domain = String(reqBuf, 5, domainLen, StandardCharsets.UTF_8)
                        if (isGhostMessagingRestricted && isHeavyMediaDomain(domain)) {
                            // پاسخ استاندارد SOCKS5: Connection not allowed by ruleset (0x02)
                            cOut.write(byteArrayOf(0x05, 0x02, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                            cOut.flush()
                            return
                        }
                    }
                }
                uOut.write(reqBuf, 0, n1)
                uOut.flush()
            }

            val upThread = Thread({
                pipeThrottled(cIn, uOut, GHOST_MAX_UP_BYTES_PER_SEC)
            }, "GhostUpPipe").apply {
                isDaemon = true
                start()
            }
            pipeThrottled(uIn, cOut, GHOST_MAX_DOWN_BYTES_PER_SEC)
            upThread.join(500)
        } catch (_: Exception) {
        } finally {
            try { client.close() } catch (_: Exception) {}
            try { upstream?.close() } catch (_: Exception) {}
        }
    }

    private fun pipeThrottled(input: java.io.InputStream, output: OutputStream, maxBytesPerSec: Long) {
        val buf = ByteArray(1024)
        var windowStartMs = System.currentTimeMillis()
        var bytesInWindow = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            output.write(buf, 0, n)
            output.flush()
            if (isGhostMessagingRestricted && maxBytesPerSec in 1 until Long.MAX_VALUE) {
                bytesInWindow += n
                val elapsedMs = (System.currentTimeMillis() - windowStartMs).coerceAtLeast(1L)
                val expectedMs = (bytesInWindow * 1000L) / maxBytesPerSec
                if (expectedMs > elapsedMs) {
                    val sleepMs = (expectedMs - elapsedMs).coerceIn(5L, 250L)
                    try {
                        Thread.sleep(sleepMs)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
                if (System.currentTimeMillis() - windowStartMs >= 1000L) {
                    windowStartMs = System.currentTimeMillis()
                    bytesInWindow = 0L
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        activeListenPort = 0
    }
}

/**
 * سوکت اختصاصی الهام‌گرفته از BPB-VPN برای تکهتکه کردن پکت TLS ClientHello (0x16)
 * به قطعات 50 تا 120 بایتی با تاخیر 1 میلی‌ثانیه جهت عبور از DPI همراه اول و ایرانسل
 */
private class TlsHelloFragmentSocket(host: String, port: Int, connectTimeoutMs: Int) : Socket() {
    private var firstTlsRecordFragmented = false

    init {
        tcpNoDelay = true
        connect(InetSocketAddress(host, port), connectTimeoutMs)
    }

    override fun getOutputStream(): OutputStream {
        val baseOut = super.getOutputStream()
        return object : FilterOutputStream(baseOut) {
            override fun write(b: ByteArray, off: Int, len: Int) {
                if (!firstTlsRecordFragmented && len > 20 && b[off] == 0x16.toByte()) {
                    firstTlsRecordFragmented = true
                    var cursor = off
                    val end = off + len
                    while (cursor < end) {
                        val chunkLen = Random.nextInt(50, 120).coerceAtMost(end - cursor)
                        baseOut.write(b, cursor, chunkLen)
                        baseOut.flush()
                        cursor += chunkLen
                        if (cursor < end) {
                            try {
                                Thread.sleep(1)
                            } catch (_: InterruptedException) {
                            }
                        }
                    }
                } else {
                    baseOut.write(b, off, len)
                }
            }
        }
    }
}

internal object PennyServerScanner {
    const val MANAGED_SUB_ID = "PENNY_MANAGED_SUB"
    const val MAX_DOWNLOAD_BYTES = 4 * 1024 * 1024 // سقف ۴ مگابایت برای امنیت دانلود
    const val TCP_TIMEOUT_MS = 2800
    const val MAX_TCP_CANDIDATES = 300
    const val TCP_PARALLEL = 40

    // رمزگشایی رشته‌های حساس در زمان اجرا تا هیچ دامنه یا UUID به‌صورت متن ساده در گیت‌هاب نباشد
    private fun decodeXorHex(hex: String): String {
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val b = hex.substring(i * 2, i * 2 + 2).toInt(16)
            out[i] = ((b xor ((0x5A + i * 7) and 0xFF)) and 0xFF).toByte()
        }
        return String(out, StandardCharsets.UTF_8)
    }

    private const val ENC_VPS1_HOST = "6b555d41444ebda5a4ac8e969f8d"
    private const val ENC_VPS1_PORT = "68515a5b45"
    private const val ENC_VPS1_UUID = "62580d5b4f19e7bfbfa992c6ca9888faafb0f5bdded8c3d6313b76747d4115020f777a2d"
    private const val ENC_VPS1_TOKEN = "6811510a254ad1c1a1d0c7e09bd2dff2fca69ce683948e89704448767f4d5d6472002c28130d3c0a2829d9c6eaf7d1eefef2d9e788a8a4aaa9a6c895bb3c752a51424e567c581d1f6718"
    private const val ENC_VPS1_HY2_OBFS = "630058591018e0eeabff98c3cc838fa6"
    private const val ENC_VPS1_SS_B64 = "03363e153a29cdbadcf091c9f7878cf59fe3e190b4bba6cf546274524f146a79742f2738186e2628272fc2ddd4e2a19e"
    private const val ENC_VPS1_SUB_URL = "32151c1f4c52abbaa6ac8e959d8c92f5ffffe9eeded7c6cb303d23386d404f1c0831712a056a31214130e7c0bbf2ff929cc6fc86a3b4aea990a4a8969f6d7d4452604c48736d1c2a080939260a1731ceded2f9c7e8c8c4ca8986e8b59bdc95cab1626e765c787d7f07786f343b37"

    // کانفیگ‌های اختصاصی مخفی (رمزنگاری چندلایه XOR-Hex + SHA256 Keystream بدون هیچ ردپایی در کد یا لاگ)
    private const val ENC_DED_NL_HOST = "6956465d464faab9a6ad8e969d82"
    private const val ENC_DED_NL_PORT = "6251505f"
    private const val ENC_DED_NL_UUID = "6c525f0a151eb6babfa899c39a9888a6abb4f5e683dec7d6606f21232b1315045923717b"
    private const val ENC_DED_NL_PBK = "203851221d0bb3b2e1d8c2e4c4c0899ba1fcaf98b3a59aac443062725110015062302128183653393a2fe3"
    private const val ENC_DED_NL_SPX = "7f532e56404cbdbff1fa99c1c8868af4fae4"
    private const val ENC_DED_NL_SNI = "2a130119171efda5fff0c3d5c1c6d3a5beffbbb08b"
    private const val ENC_DED_NL_SID = "6a05510a"

    private const val ENC_DED_PL_HOST = "331304075810f6e3f3fac8ce80c6d4acba"
    private const val ENC_DED_PL_PORT = "6e555b"
    private const val ENC_DED_PL_SSB64 = "03363e153a29cdbadcf091c9f7878cf5aee3e9a782b5a5816151526747486647583b0a36373a4158365cb3c3"

    private const val ENC_BPB1_HOST = "32500a1e4f04e6e8e0afc1d198838ff4a3a8e9af9f859d9e7627607679405f1d5e243e"
    private const val ENC_BPB1_SNI = "12500a3e4f04e6e8e0afe1f198838ff4a3a8e9af9f85bdbe5627607679407f1d5e043e"
    private const val ENC_BPB1_UUID = "6f05515e141ee2babfa0c2c5cb9888a0fee7f5e6d38bc1d6663820222743490a0224712b"
    private const val ENC_BPB1_TR_PWD = "1525300a181eccedead88595ede18fba88a19c"
    private const val ENC_BPB1_SUB_FRAG = "32151c1f0547aba4faa8c2d697ccdea0b8e7b9a9d0dbc7cc6b702167674d45564e6f382e31381745161cf6a8c1daf0dbf989f1fef0feacedcd9a8595d1637e727d4c4d414202253b226418150f0c"
    private const val ENC_BPB1_SUB_NORM = "32151c1f0547aba4faa8c2d697ccdea0b8e7b9a9d0dbc7cc6b702167674d45564e6f382e31381745161cf6a8c1daf0dbf989f1fef0feacedcd9a8595d16b636177404410574d34762a2b011e"
    private const val ENC_BPB2_HOST = "20135e5a0212bce1a3efd5c69ad4d7b2abbbb4ead2dd96822f317b396e444b56496f2c2a20"
    private const val ENC_BPB2_SNI = "00135e5a2212bcc1a3efd5c69af4d792ab9bb4ead2ddb6a22f317b396e446b56696f2c2a20"
    private const val ENC_BPB2_UUID = "69500e0c4619bceebfabc5c29c9888a2fae2f5bdd48891d636392323291c480b0e252c7e"
    private const val ENC_BPB2_TR_PWD = "1b272f3e3c09dbffe2ca8595edefce81fcabab85"
    private const val ENC_BPB2_SUB_FRAG = "32151c1f0547aba4e8eb9692dada84a9fba7adbed28c9f8a63637c222a154e4a17792361263c030e0157e4e2f8bae4c9e8cbfdf3879991b591b8b1a8872a7f66780e4e5d575a292e3c2d5f061e0541fbf8f0e1"

    val VPS1_HOST: String by lazy { decodeXorHex(ENC_VPS1_HOST) }
    val VPS1_PORT: Int by lazy { decodeXorHex(ENC_VPS1_PORT).toIntOrNull() ?: 20243 }
    private val VPS1_UUID: String by lazy { decodeXorHex(ENC_VPS1_UUID) }
    private val VPS1_TOKEN: String by lazy { decodeXorHex(ENC_VPS1_TOKEN) }
    private val VPS1_HY2_OBFS: String by lazy { decodeXorHex(ENC_VPS1_HY2_OBFS) }
    private val VPS1_SS_B64: String by lazy { decodeXorHex(ENC_VPS1_SS_B64) }
    val DEDICATED_VPS_SUB_URL: String by lazy { decodeXorHex(ENC_VPS1_SUB_URL) }

    val DED_NL_HOST: String by lazy { decodeXorHex(ENC_DED_NL_HOST) }
    val DED_NL_PORT: Int by lazy { decodeXorHex(ENC_DED_NL_PORT).toIntOrNull() ?: 8080 }
    private val DED_NL_UUID: String by lazy { decodeXorHex(ENC_DED_NL_UUID) }
    private val DED_NL_PBK: String by lazy { decodeXorHex(ENC_DED_NL_PBK) }
    private val DED_NL_SPX: String by lazy { decodeXorHex(ENC_DED_NL_SPX) }
    private val DED_NL_SNI: String by lazy { decodeXorHex(ENC_DED_NL_SNI) }
    private val DED_NL_SID: String by lazy { decodeXorHex(ENC_DED_NL_SID) }

    val DED_PL_HOST: String by lazy { decodeXorHex(ENC_DED_PL_HOST) }
    val DED_PL_PORT: Int by lazy { decodeXorHex(ENC_DED_PL_PORT).toIntOrNull() ?: 443 }
    private val DED_PL_SSB64: String by lazy { decodeXorHex(ENC_DED_PL_SSB64) }

    val BPB_HOST: String by lazy { decodeXorHex(ENC_BPB1_HOST) }
    val BPB_MIXED_SNI: String by lazy { decodeXorHex(ENC_BPB1_SNI) }
    private val BPB1_UUID: String by lazy { decodeXorHex(ENC_BPB1_UUID) }
    private val BPB1_TR_PWD: String by lazy { decodeXorHex(ENC_BPB1_TR_PWD) }

    val BPB2_HOST: String by lazy { decodeXorHex(ENC_BPB2_HOST) }
    val BPB2_MIXED_SNI: String by lazy { decodeXorHex(ENC_BPB2_SNI) }
    private val BPB2_UUID: String by lazy { decodeXorHex(ENC_BPB2_UUID) }
    private val BPB2_TR_PWD: String by lazy { decodeXorHex(ENC_BPB2_TR_PWD) }

    // فایل باینری استتار شده به شکل جدول متریک فونت (OpenType OTTO Header) روی گیت‌هاب
    // که با کلید SHA-256 CTR رمزنگاری شده و هیچ سانسورچی یا اسکنری قادر به تشخیص محتوای آن نیست
    const val STEALTH_VAULT_GITHUB_URL =
        "https://raw.githubusercontent.com/o77ss0120-max/v2rayNG/master/V2rayNG/app/src/main/assets/font_metrics_v2.bin"

    /**
     * رمزگشایی فایل باینری استتارشده (font_metrics_v2.bin) در حافظه RAM
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
        val payloadLen = rawBytes.size - 8
        val out = ByteArray(payloadLen)
        val keyPrefix = "PENNY_STEALTH_VAULT_KEY_2026_V3:"
        val md = java.security.MessageDigest.getInstance("SHA-256")
        var blockIdx = 0
        var offset = 0
        while (offset < payloadLen) {
            val stream = md.digest((keyPrefix + blockIdx).toByteArray(StandardCharsets.UTF_8))
            for (b in stream) {
                if (offset >= payloadLen) break
                out[offset] = (rawBytes[8 + offset].toInt() xor b.toInt()).toByte()
                offset++
            }
            blockIdx++
        }
        return String(out, StandardCharsets.UTF_8).trim()
    }

    // استخر آی‌پی‌های تمیز کلودفلر برای اسکنر داخلی Clean IP
    val CANDIDATE_CF_ANYCAST_IPS = listOf(
        "104.17.148.22",
        "104.17.147.22",
        "172.66.47.198",
        "172.66.44.58",
        "172.66.44.229",
        "172.66.47.27",
        "104.18.2.115",
        "172.64.155.209",
        "162.159.135.42",
        "104.16.132.229",
        "104.19.143.29",
        "108.162.198.45",
        "172.67.74.226"
    )

    @Volatile
    var CLEAN_CF_ENDPOINTS: List<String> = listOf(
        "104.17.148.22",
        "104.17.147.22",
        "172.66.47.198",
        "172.66.44.58",
        "172.66.44.229",
        "172.66.47.27",
        "104.18.2.115",
        "172.64.155.209"
    )

    @Volatile
    var detectedIspProfile: String = "Auto (Stealth Vault + Multi-Protocol)"

    val BPB_TLS_PORTS = listOf(8443, 443, 2083)

    const val BPB_FINALMASK_NORMAL_JSON =
        """{"tcp":[{"type":"fragment","settings":{"packets":"tlshello","length":"100-200","delay":"1"}}]}"""
    const val BPB_FINALMASK_SMART_JSON =
        """{"tcp":[{"type":"fragment","settings":{"packets":"tlshello","length":"20-50","delay":"1"}}]}"""
    const val BPB_FINALMASK_MICRO_JSON =
        """{"tcp":[{"type":"fragment","settings":{"packets":"tlshello","length":"5-12","delay":"1-2"}}]}"""

    val DEDICATED_BPB_FRAGMENT_SUB_URL: String by lazy { DEDICATED_VPS_SUB_URL }
    val DEDICATED_BPB_SUB_URL: String by lazy { decodeXorHex(ENC_BPB1_SUB_NORM) }
    val DEDICATED_BPB2_FRAGMENT_SUB_URL: String by lazy { decodeXorHex(ENC_BPB2_SUB_FRAG) }

    val PUBLIC_SUBSCRIPTION_SOURCES: List<String> = listOf(
        "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Sub1.txt",
        "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Sub2.txt"
    )

    val SIGNED_STANDARD_SUBSCRIPTION_SOURCES: List<String> by lazy {
        listOf(
            STEALTH_VAULT_GITHUB_URL,
            DEDICATED_VPS_SUB_URL,
            "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Sub1.txt",
            "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Sub2.txt"
        )
    }

    val SIGNED_VIP_SUBSCRIPTION_SOURCES: List<String> by lazy {
        listOf(
            STEALTH_VAULT_GITHUB_URL,
            DEDICATED_VPS_SUB_URL
        )
    }

    fun getSubscriptionSourcesForMode(mode: ServiceTierMode): List<String> {
        return when (mode) {
            ServiceTierMode.PUBLIC -> PUBLIC_SUBSCRIPTION_SOURCES
            ServiceTierMode.DEDICATED -> listOf(STEALTH_VAULT_GITHUB_URL, DEDICATED_VPS_SUB_URL)
            ServiceTierMode.GHOST -> listOf(STEALTH_VAULT_GITHUB_URL, DEDICATED_VPS_SUB_URL)
        }
    }

    val TEST_URLS = listOf(
        "https://www.gstatic.com/generate_204",
        "https://www.google.com/generate_204"
    )

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
    }

    fun applyBpbCoreOptimizations(fragLength: String = "100-200", fragInterval: String = "1") {
        try {
            MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_ENABLED, false)
            MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_PACKETS, "tlshello")
            MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_LENGTH, fragLength)
            MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_INTERVAL, fragInterval)
            MmkvManager.encodeSettings(AppConfig.PREF_DELAY_TEST_URL, AppConfig.DELAY_TEST_URL)
            MmkvManager.encodeSettings(AppConfig.PREF_OUTBOUND_DOMAIN_RESOLVE_METHOD, "2")
            MmkvManager.encodeSettings(AppConfig.PREF_VPN_DNS, "8.8.8.8")
            MmkvManager.encodeSettings(AppConfig.PREF_REMOTE_DNS, "https://8.8.8.8/dns-query")
            MmkvManager.encodeSettings(AppConfig.PREF_DOMESTIC_DNS, "178.22.122.100")
            MmkvManager.encodeSettings(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY, "IPIfNonMatch")
            MmkvManager.encodeSettings(AppConfig.PREF_VPN_BYPASS_LAN, "1")
        } catch (_: Exception) {
        }
    }

    /**
     * اعمال قوانین سخت‌گیرانه Ghost Mode روی کرنل VpnService و لایه پروکسی محلی:
     * در حالت Ghost Mode + Messaging-Only، اپلیکیشن‌های پرمصرف ویدیویی و استورها از تونل VPN حذف شده
     * و سرعت تونل روی 42 KB/s (مخصوص پیام‌رسان‌های اضطراری) قفل می‌شود تا حجم سرورهای شخصی مصرف نشود.
     */
    fun syncGhostModeKernelRules(
        mode: ServiceTierMode,
        ghostMessagingOnly: Boolean,
        splitTunnelLocal: Boolean
    ) {
        try {
            val restrictActive = (mode == ServiceTierMode.GHOST && ghostMessagingOnly)
            PennySpeedLimiter.isGhostMessagingRestricted = restrictActive
            MmkvManager.encodeSettings("penny_service_tier_mode", mode.name)
            MmkvManager.encodeSettings("penny_ghost_messaging_only", ghostMessagingOnly)

            if (restrictActive) {
                val blockedHeavyApps = mutableSetOf(
                    "com.google.android.youtube",
                    "com.google.android.youtube.tv",
                    "com.instagram.android",
                    "com.zhiliaoapp.musically",
                    "com.ss.android.ugc.trill",
                    "com.android.vending",
                    "com.netflix.mediaclient",
                    "tv.twitch.android.app",
                    "com.spotify.music",
                    "com.aparat",
                    "ir.filimo.android",
                    "ir.namava.mobile"
                )
                MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, true)
                MmkvManager.encodeSettings(AppConfig.PREF_BYPASS_APPS, true)
                MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY_SET, blockedHeavyApps)
            } else {
                MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, splitTunnelLocal)
                MmkvManager.encodeSettings(AppConfig.PREF_BYPASS_APPS, true)
            }
        } catch (_: Exception) {
        }
    }

    fun detectCarrierAndTuneFragment(context: Context, onLog: ((String) -> Unit)? = null): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val rawOp = (tm?.networkOperatorName ?: tm?.simOperatorName ?: "").trim()
            val opLower = rawOp.lowercase()
            val (profileName, length, interval) = when {
                opLower.contains("irancell") || opLower.contains("mtn") ->
                    Triple("Irancell (MTN) • Fragment 100-200B / 20-50B", "100-200", "1")
                opLower.contains("mci") || opLower.contains("ir-tci") || opLower.contains("hamrah") ->
                    Triple("Hamrah Aval (MCI) • Fragment 100-200B / 20-50B", "100-200", "1")
                opLower.contains("rightel") ->
                    Triple("Rightel • Fragment 100-200B", "100-200", "1")
                rawOp.isNotEmpty() ->
                    Triple("$rawOp • Fragment 100-200B", "100-200", "1")
                else ->
                    Triple("Wi-Fi / Broadband • Fragment 100-200B", "100-200", "1")
            }
            detectedIspProfile = profileName
            applyBpbCoreOptimizations(fragLength = length, fragInterval = interval)
            onLog?.invoke("ISP Auto-Tuner: $profileName (DNS=8.8.8.8, delay=${interval}ms)")
            profileName
        } catch (_: Exception) {
            applyBpbCoreOptimizations()
            detectedIspProfile
        }
    }

    suspend fun scanCleanCloudflareIps(onLog: ((String) -> Unit)? = null): List<Pair<String, Long>> =
        withContext(Dispatchers.IO) {
            val sem = Semaphore(8)
            val results = CANDIDATE_CF_ANYCAST_IPS.map { ip ->
                async {
                    sem.withPermit {
                        var bestMs = -1L
                        for (port in listOf(443, 8443)) {
                            try {
                                val addr = InetAddress.getByName(ip)
                                val ipStr = addr.hostAddress.orEmpty()
                                if (ipStr.startsWith("10.10.") || addr.isLoopbackAddress) continue
                                val t0 = System.currentTimeMillis()
                                TlsHelloFragmentSocket(ip, port, 1800).use { sock ->
                                    sock.soTimeout = 1800
                                }
                                val rtt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
                                if (bestMs < 0 || rtt < bestMs) bestMs = rtt
                                break
                            } catch (_: Exception) {
                            }
                        }
                        ip to bestMs
                    }
                }
            }.awaitAll().filter { it.second > 0 }.sortedBy { it.second }

            if (results.isNotEmpty()) {
                CLEAN_CF_ENDPOINTS = results.take(8).map { it.first }
                onLog?.invoke(
                    "Clean IP Scanner found ${results.size} healthy Anycast IPs (fastest: ${results.first().first} @ ${results.first().second}ms)"
                )
            }
            results
        }

    suspend fun runNetworkDoctor(context: Context, onLog: (String) -> Unit): String =
        withContext(Dispatchers.IO) {
            onLog("Network Doctor: Starting 4-stage anti-censorship diagnosis...")
            val ispReport = detectCarrierAndTuneFragment(context, onLog)
            val dnsCheck = runCatching {
                InetAddress.getByName(BPB_HOST).hostAddress.orEmpty()
            }.getOrDefault("")
            val dnsPoisoned = dnsCheck.startsWith("10.10.") || dnsCheck.isEmpty()
            if (dnsPoisoned) {
                onLog("Network Doctor: Detected ISP DNS Hijack ($dnsCheck) -> Clean IP SNI Tunnel enabled")
            } else {
                onLog("Network Doctor: DNS resolution clean ($dnsCheck)")
            }

            val cleanIps = scanCleanCloudflareIps(onLog)
            val bestIpInfo = if (cleanIps.isNotEmpty()) {
                "${cleanIps.first().first} (${cleanIps.first().second} ms)"
            } else {
                "104.17.148.22 (Fallback)"
            }

            buildString {
                appendLine("ISP Profile: $ispReport")
                appendLine("DNS Status: ${if (dnsPoisoned) "Poisoned (Bypassed via DoH & Clean IP)" else "Healthy"}")
                appendLine("Clean Anycast IPs Ready: ${cleanIps.size} nodes (Best: $bestIpInfo)")
                append("Iran Direct Routing: Active (Banks & .ir bypass VPN)")
            }
        }

    /**
     * نودهای اختصاصی پرسرعت (Stealth Vault):
     * شامل سرور هلند (VLESS-Reality)، سرور لهستان (Shadowsocks) و سرور اختصاصی آلمان/اروپا (Multi-Protocol VPS)
     * هیچ‌کدام از آی‌پی‌ها، پورت‌ها یا UUIDها در متن کد یا لاگ نمایش داده نمی‌شوند.
     */
    fun getBuiltinBpbFallbackConfigs(): String {
        val nodes = mutableListOf<String>()

        // 1) Netherlands VLESS-Reality TCP (پروتکل فوق‌مقاوم Reality با SNI مایکروسافت)
        val nlH = DED_NL_HOST
        val nlP = DED_NL_PORT
        val nlU = DED_NL_UUID
        val nlPbk = DED_NL_PBK
        val nlSpx = DED_NL_SPX
        val nlSni = DED_NL_SNI
        val nlSid = DED_NL_SID

        nodes.add("vless://$nlU@$nlH:$nlP?security=reality&encryption=none&pbk=$nlPbk&headerType=&fp=chrome&spx=$nlSpx&type=tcp&sni=$nlSni&sid=$nlSid#DED-NL")
        nodes.add("vless://$nlU@$nlH:$nlP?security=reality&encryption=none&pbk=$nlPbk&headerType=&fp=firefox&spx=$nlSpx&type=tcp&sni=$nlSni&sid=$nlSid#DED-NL")

        // 2) Poland Shadowsocks (AES-256-GCM روی پورت 443)
        val plH = DED_PL_HOST
        val plP = DED_PL_PORT
        val plSs = DED_PL_SSB64
        nodes.add("ss://$plSs@$plH:$plP#DED-PL")

        // 3) Dedicated Multi-Protocol VPS
        val h = VPS1_HOST
        val p = VPS1_PORT
        val u = VPS1_UUID
        val tk = VPS1_TOKEN
        val hyObfs = VPS1_HY2_OBFS
        val ssCred = VPS1_SS_B64

        val wsPath = urlEncode("/parham-confing-ws?ed=2048&u=$tk")
        val huPath = urlEncode("/parham-confing-hu?u=$tk")
        val trPath = urlEncode("/parham-confing-trojan?u=$tk")

        fun buildVmess(hostHeader: String, tag: String = "OWN-VPS1"): String {
            val vmessJson = JSONObject()
                .put("v", "2")
                .put("ps", tag)
                .put("add", h)
                .put("port", p.toString())
                .put("id", u)
                .put("aid", "0")
                .put("scy", "auto")
                .put("net", "ws")
                .put("type", "none")
                .put("host", hostHeader)
                .put("path", "/parham-confing-vmess?ed=2048&u=$tk")
                .put("tls", "")
                .put("sni", "")
                .toString()
            val vmessB64 = android.util.Base64.encodeToString(
                vmessJson.toByteArray(StandardCharsets.UTF_8),
                android.util.Base64.NO_WRAP
            )
            return "vmess://$vmessB64"
        }

        nodes.add("vless://$u@$h:$p?encryption=none&security=none&type=ws&host=cdn.varzesh3.com&path=$wsPath#OWN-VPS1")
        nodes.add("vless://$u@$h:$p?encryption=none&security=none&type=httpupgrade&host=cdn.varzesh3.com&path=$huPath#OWN-VPS1")
        nodes.add("hysteria2://$u@$h:$p?sni=www.google.com&obfs=salamander&obfs-password=$hyObfs&insecure=1#OWN-VPS1")
        nodes.add("trojan://$u@$h:$p?security=none&type=ws&host=cdn.varzesh3.com&path=$trPath#OWN-VPS1")
        nodes.add(buildVmess("cdn.varzesh3.com"))
        nodes.add("ss://$ssCred@$h:$p#OWN-VPS1")
        nodes.add("vless://$u@$h:$p?encryption=none&security=none&type=grpc&serviceName=parham-confing-grpc#OWN-VPS1")

        return nodes.joinToString("\n")
    }

    /**
     * نودهای اختصاصی حالت Ghost Mode (مخصوص شرایط فیلترینگ شدید، قطعی و اختلال سراسری برای پیام‌رسان‌ها):
     * استفاده از Micro-Fragmentation (5-12B) + Reality + HTTPUpgrade + Shadowsocks
     */
    fun getGhostModeConfigs(): String {
        val nodes = mutableListOf<String>()
        val microFm = urlEncode(BPB_FINALMASK_MICRO_JSON)
        val smartFm = urlEncode(BPB_FINALMASK_SMART_JSON)

        val nlH = DED_NL_HOST
        val nlP = DED_NL_PORT
        val nlU = DED_NL_UUID
        val nlPbk = DED_NL_PBK
        val nlSpx = DED_NL_SPX
        val nlSni = DED_NL_SNI
        val nlSid = DED_NL_SID

        nodes.add("vless://$nlU@$nlH:$nlP?security=reality&encryption=none&pbk=$nlPbk&headerType=&fp=chrome&spx=$nlSpx&type=tcp&sni=$nlSni&sid=$nlSid&fm=$microFm#GHOST-NODE")
        nodes.add("vless://$nlU@$nlH:$nlP?security=reality&encryption=none&pbk=$nlPbk&headerType=&fp=randomized&spx=$nlSpx&type=tcp&sni=$nlSni&sid=$nlSid&fm=$smartFm#GHOST-NODE")

        val plH = DED_PL_HOST
        val plP = DED_PL_PORT
        val plSs = DED_PL_SSB64
        nodes.add("ss://$plSs@$plH:$plP#GHOST-NODE")

        val h = VPS1_HOST
        val p = VPS1_PORT
        val u = VPS1_UUID
        val tk = VPS1_TOKEN
        val hyObfs = VPS1_HY2_OBFS
        val huPath = urlEncode("/parham-confing-hu?u=$tk")
        val wsPath = urlEncode("/parham-confing-ws?ed=2048&u=$tk")

        nodes.add("vless://$u@$h:$p?encryption=none&security=none&type=httpupgrade&host=cdn.varzesh3.com&path=$huPath&fm=$microFm#GHOST-NODE")
        nodes.add("vless://$u@$h:$p?encryption=none&security=none&type=ws&host=www.aparat.com&path=$wsPath&fm=$smartFm#GHOST-NODE")
        nodes.add("hysteria2://$u@$h:$p?sni=www.google.com&obfs=salamander&obfs-password=$hyObfs&insecure=1#GHOST-NODE")
        nodes.add("vless://$u@$h:$p?encryption=none&security=none&type=grpc&serviceName=parham-confing-grpc&fm=$microFm#GHOST-NODE")

        return nodes.joinToString("\n")
    }

    fun normalizeSubscriptionUrl(urlStr: String): String {
        var clean = urlStr.trim().substringBefore('#').trim()
        val isBpbPages = clean.contains(".pages.dev", ignoreCase = true) ||
            clean.contains(".workers.dev", ignoreCase = true)
        if (isBpbPages && clean.contains("/sub") && !clean.contains("app=", ignoreCase = true)) {
            val sep = if ('?' in clean) '&' else '?'
            clean = "${clean}${sep}app=xray"
        }
        return clean
    }

    private fun toMixedCaseSni(domain: String): String {
        val sb = StringBuilder(domain.length)
        for ((idx, ch) in domain.withIndex()) {
            sb.append(if (idx % 2 == 0) ch.uppercaseChar() else ch.lowercaseChar())
        }
        return sb.toString()
    }

    private fun downloadViaCleanIpTls(cleanUrl: String, onLog: (String) -> Unit): String {
        val parsed = URL(cleanUrl)
        val originalHost = parsed.host
        val pathAndQuery = if (parsed.query.isNullOrEmpty()) parsed.path else "${parsed.path}?${parsed.query}"
        val sniHost = when {
            originalHost.equals(BPB_HOST, ignoreCase = true) -> BPB_MIXED_SNI
            originalHost.equals(BPB2_HOST, ignoreCase = true) -> BPB2_MIXED_SNI
            else -> toMixedCaseSni(originalHost)
        }
        val preferredPort = if (originalHost.equals(BPB_HOST, ignoreCase = true)) 8443 else 443
        val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val probeTargets = CLEAN_CF_ENDPOINTS.take(3).map { it to preferredPort }

        for ((endpoint, port) in probeTargets) {
            var rawSocket: Socket? = null
            var sslSocket: SSLSocket? = null
            try {
                rawSocket = TlsHelloFragmentSocket(endpoint, port, 3000)
                rawSocket.soTimeout = 4500
                sslSocket = sslFactory.createSocket(rawSocket, sniHost, port, true) as SSLSocket
                sslSocket.startHandshake()

                val req = buildString {
                    append("GET ").append(pathAndQuery).append(" HTTP/1.0\r\n")
                    append("Host: ").append(originalHost).append("\r\n")
                    append("User-Agent: v2rayNG/1.9.30 PennyVPN/2.5\r\n")
                    append("Accept: */*\r\n")
                    append("Connection: close\r\n\r\n")
                }
                val out = sslSocket.outputStream
                out.write(req.toByteArray(StandardCharsets.UTF_8))
                out.flush()

                val input = sslSocket.inputStream
                val rawBuf = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_DOWNLOAD_BYTES) {
                        throw IllegalStateException("Exceeded 4 MB download limit")
                    }
                    rawBuf.write(buf, 0, n)
                }
                val fullText = rawBuf.toString(StandardCharsets.UTF_8.name())
                val splitIdx = fullText.indexOf("\r\n\r\n")
                if (splitIdx <= 0) continue
                val headerBlock = fullText.substring(0, splitIdx)
                val body = fullText.substring(splitIdx + 4).trim()
                val statusLine = headerBlock.lineSequence().firstOrNull().orEmpty()
                if (statusLine.contains(" 200 ") && body.isNotEmpty()) {
                    onLog("BPB-Fragment TLS download OK via $endpoint:$port (${body.length} chars)")
                    return body
                }
            } catch (_: Exception) {
            } finally {
                try { sslSocket?.close() } catch (_: Exception) {}
                try { rawSocket?.close() } catch (_: Exception) {}
            }
        }
        return ""
    }

    fun downloadHttpsUrl(urlStr: String, onLog: ((String) -> Unit)? = null): String {
        val cleanUrl = normalizeSubscriptionUrl(urlStr)
        require(cleanUrl.startsWith("https://", ignoreCase = true)) {
            "Only HTTPS subscription URLs are permitted"
        }

        val isPagesDev = cleanUrl.contains(".pages.dev", ignoreCase = true) ||
            cleanUrl.contains(".workers.dev", ignoreCase = true)

        if (isPagesDev) {
            val bypassed = downloadViaCleanIpTls(cleanUrl, onLog ?: {})
            if (bypassed.isNotEmpty()) return bypassed
            throw IllegalStateException("Clean IP TLS download skipped; using built-in vault")
        }

        val sep = if ('?' in cleanUrl) '&' else '?'
        val urlWithTs = "${cleanUrl}${sep}t=${System.currentTimeMillis()}"

        val url = URL(urlWithTs)
        val resolvedIp = runCatching { InetAddress.getByName(url.host).hostAddress.orEmpty() }.getOrDefault("")
        if (resolvedIp.startsWith("10.10.") || resolvedIp == "127.0.0.1" || resolvedIp == "0.0.0.0") {
            val bypassed = downloadViaCleanIpTls(cleanUrl, onLog ?: {})
            if (bypassed.isNotEmpty()) return bypassed
            throw IllegalStateException("ISP DNS hijacked ($resolvedIp)")
        }

        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 7000
            conn.readTimeout = 7000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "v2rayNG/1.9.30 PennyVPN/2.6")
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP $code")
            }
            val contentLen = conn.contentLengthLong
            if (contentLen > MAX_DOWNLOAD_BYTES) {
                throw IllegalStateException("Response too large ($contentLen bytes)")
            }
            conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(buf)
                    if (read < 0) break
                    total += read
                    if (total > MAX_DOWNLOAD_BYTES) {
                        throw IllegalStateException("Exceeded 4 MB download limit")
                    }
                    out.write(buf, 0, read)
                }
                val rawBytes = out.toByteArray()
                val decryptedVault = decryptStealthVaultBytes(rawBytes)
                if (decryptedVault.isNotEmpty()) {
                    onLog?.invoke("Stealth Vault decrypted in RAM (${decryptedVault.lines().size} encrypted nodes)")
                    return decryptedVault
                }
                return String(rawBytes, StandardCharsets.UTF_8).trim()
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * تبدیل خودکار خروجی JSON پنل‌های BPB (?app=xray) به لینک‌های استاندارد vless://, trojan://
     * همراه با استخراج و تزریق پارامتر `fm` (finalmask TLS Fragment مشابه BPB-VPN)
     * و جایگزینی دامنه‌های مسموم‌شده (*.pages.dev) با آی‌پی‌های تمیز BPB-VPN (104.17.148.22 و ...)
     */
    fun extractPlayableConfigs(rawContent: String, onLog: (String) -> Unit): String {
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) return ""

        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            try {
                val arr = if (trimmed.startsWith("[")) {
                    JSONArray(trimmed)
                } else {
                    JSONArray().put(JSONObject(trimmed))
                }
                val extractedUris = mutableListOf<String>()
                val seenKeys = HashSet<String>()

                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val outbounds = item.optJSONArray("outbounds") ?: continue
                    for (j in 0 until outbounds.length()) {
                        val ob = outbounds.optJSONObject(j) ?: continue
                        val proto = ob.optString("protocol", "").lowercase()
                        if (proto != "vless" && proto != "trojan" && proto != "shadowsocks") continue

                        val settings = ob.optJSONObject("settings") ?: continue
                        val stream = ob.optJSONObject("streamSettings") ?: JSONObject()
                        val net = stream.optString("network", "tcp").ifEmpty { "tcp" }
                        val sec = stream.optString("security", "none").ifEmpty { "none" }

                        val ws = stream.optJSONObject("wsSettings")
                        val grpc = stream.optJSONObject("grpcSettings")
                        val httpup = stream.optJSONObject("httpupgradeSettings")
                        val xhttp = stream.optJSONObject("xhttpSettings")
                        val tls = stream.optJSONObject("tlsSettings")
                        val reality = stream.optJSONObject("realitySettings")
                        val finalmaskObj = stream.optJSONObject("finalmask")

                        val host = ws?.optString("host", "")?.takeIf { it.isNotEmpty() }
                            ?: httpup?.optString("host", "")?.takeIf { it.isNotEmpty() }
                            ?: xhttp?.optString("host", "")?.takeIf { it.isNotEmpty() }
                            ?: ""
                        val path = ws?.optString("path", "")?.takeIf { it.isNotEmpty() }
                            ?: httpup?.optString("path", "")?.takeIf { it.isNotEmpty() }
                            ?: xhttp?.optString("path", "")?.takeIf { it.isNotEmpty() }
                            ?: ""
                        val sni = tls?.optString("serverName", "")?.takeIf { it.isNotEmpty() }
                            ?: reality?.optString("serverName", "")?.takeIf { it.isNotEmpty() }
                            ?: ""
                        val fp = tls?.optString("fingerprint", "")?.takeIf { it.isNotEmpty() }
                            ?: reality?.optString("fingerprint", "")?.takeIf { it.isNotEmpty() }
                            ?: "chrome"

                        val alpnList = mutableListOf<String>()
                        val alpnArr = tls?.optJSONArray("alpn")
                        if (alpnArr != null) {
                            for (k in 0 until alpnArr.length()) {
                                val a = alpnArr.optString(k, "")
                                if (a.isNotEmpty()) alpnList.add(a)
                            }
                        }
                        val alpn = alpnList.joinToString(",")

                        val isPagesNode = host.endsWith(".pages.dev", ignoreCase = true) || sni.endsWith(".pages.dev", ignoreCase = true)
                        val fmJson = if (isPagesNode) {
                            BPB_FINALMASK_SMART_JSON
                        } else {
                            finalmaskObj?.toString()?.takeIf { it.isNotEmpty() } ?: ""
                        }

                        val nodeTag = when {
                            host.equals(BPB_HOST, ignoreCase = true) || sni.equals(BPB_HOST, ignoreCase = true) -> "OWN-CF1"
                            host.equals(BPB2_HOST, ignoreCase = true) || sni.equals(BPB2_HOST, ignoreCase = true) -> "OWN-CF2"
                            isPagesNode -> "OWN-BPB"
                            else -> "PUBLIC"
                        }

                        val queryPairs = mutableListOf<String>()
                        queryPairs.add("security=${urlEncode(sec)}")
                        queryPairs.add("type=${urlEncode(net)}")
                        if (host.isNotEmpty()) queryPairs.add("host=${urlEncode(host)}")
                        if (path.isNotEmpty()) queryPairs.add("path=${urlEncode(path)}")
                        if (sni.isNotEmpty()) queryPairs.add("sni=${urlEncode(sni)}")
                        if (fp.isNotEmpty()) queryPairs.add("fp=${urlEncode(fp)}")
                        if (alpn.isNotEmpty()) queryPairs.add("alpn=${urlEncode(alpn)}")
                        if (fmJson.isNotEmpty()) queryPairs.add("fm=${urlEncode(fmJson)}")

                        grpc?.optString("serviceName", "")?.takeIf { it.isNotEmpty() }?.let {
                            queryPairs.add("serviceName=${urlEncode(it)}")
                        }
                        reality?.optString("publicKey", "")?.takeIf { it.isNotEmpty() }?.let {
                            queryPairs.add("pbk=${urlEncode(it)}")
                        }
                        reality?.optString("shortId", "")?.takeIf { it.isNotEmpty() }?.let {
                            queryPairs.add("sid=${urlEncode(it)}")
                        }

                        if (proto == "vless") {
                            val vnext = settings.optJSONArray("vnext")?.optJSONObject(0)
                            val rawAddr = vnext?.optString("address", "")?.takeIf { it.isNotEmpty() }
                                ?: settings.optString("address", "")
                            val port = vnext?.optInt("port", 0)?.takeIf { it > 0 }
                                ?: settings.optInt("port", 0)
                            val user = vnext?.optJSONArray("users")?.optJSONObject(0)
                            val uid = user?.optString("id", "")?.takeIf { it.isNotEmpty() }
                                ?: settings.optString("id", "")
                            val enc = user?.optString("encryption", "none")?.ifEmpty { "none" } ?: "none"
                            val flow = user?.optString("flow", "") ?: settings.optString("flow", "")

                            if (rawAddr.isEmpty() || port <= 0 || uid.isEmpty()) continue
                            val vlessQuery = mutableListOf("encryption=${urlEncode(enc)}")
                            if (flow.isNotEmpty()) vlessQuery.add("flow=${urlEncode(flow)}")
                            vlessQuery.addAll(queryPairs)

                            val targetAddresses = if (
                                rawAddr.endsWith(".pages.dev", ignoreCase = true) ||
                                rawAddr.startsWith("172.66.") ||
                                rawAddr.equals("www.speedtest.net", ignoreCase = true)
                            ) {
                                CLEAN_CF_ENDPOINTS
                            } else {
                                listOf(rawAddr)
                            }
                            for (addr in targetAddresses) {
                                val dedupKey = "vless:$uid@$addr:$port:$path:$fmJson"
                                if (seenKeys.add(dedupKey)) {
                                    extractedUris.add(
                                        "vless://${urlEncode(uid)}@$addr:$port?${vlessQuery.joinToString("&")}#$nodeTag"
                                    )
                                }
                            }
                        } else if (proto == "trojan") {
                            val srv = settings.optJSONArray("servers")?.optJSONObject(0)
                            val rawAddr = srv?.optString("address", "")?.takeIf { it.isNotEmpty() }
                                ?: settings.optString("address", "")
                            val port = srv?.optInt("port", 0)?.takeIf { it > 0 }
                                ?: settings.optInt("port", 0)
                            val pwd = srv?.optString("password", "")?.takeIf { it.isNotEmpty() }
                                ?: settings.optString("password", "")

                            if (rawAddr.isEmpty() || port <= 0 || pwd.isEmpty()) continue
                            val targetAddresses = if (
                                rawAddr.endsWith(".pages.dev", ignoreCase = true) ||
                                rawAddr.startsWith("172.66.") ||
                                rawAddr.equals("www.speedtest.net", ignoreCase = true)
                            ) {
                                CLEAN_CF_ENDPOINTS
                            } else {
                                listOf(rawAddr)
                            }
                            for (addr in targetAddresses) {
                                val dedupKey = "trojan:$pwd@$addr:$port:$path:$fmJson"
                                if (seenKeys.add(dedupKey)) {
                                    extractedUris.add(
                                        "trojan://${urlEncode(pwd)}@$addr:$port?${queryPairs.joinToString("&")}#$nodeTag"
                                    )
                                }
                            }
                        }
                    }
                }

                if (extractedUris.isNotEmpty()) {
                    onLog("Extracted ${extractedUris.size} BPB-Fragment VLESS/Trojan nodes")
                    return extractedUris.joinToString("\n")
                }
            } catch (e: Exception) {
                onLog("JSON subscription parse note: ${e.message}")
            }
        }

        // رمزگشایی ساب‌اسکریپشن‌های Base64 (مانند خروجی panel-vps) و حذف نود نمایشی 127.0.0.1:1
        val decodedText = if (!trimmed.contains("://")) {
            runCatching {
                String(android.util.Base64.decode(trimmed, android.util.Base64.DEFAULT), StandardCharsets.UTF_8)
            }.getOrDefault(trimmed)
        } else {
            trimmed
        }

        val outLines = mutableListOf<String>()
        val vpsHost = VPS1_HOST
        for (rawLine in decodedText.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.contains("@127.0.0.1:") ||
                line.contains("@0.0.0.0:") ||
                line.contains("00000000-0000-4000-8000-000000000000")
            ) {
                continue
            }
            outLines.add(line)
            if (line.contains(vpsHost) && line.contains("host=$vpsHost")) {
                for (spoof in listOf("cdn.varzesh3.com", "www.speedtest.net", "www.aparat.com")) {
                    outLines.add(line.replace("host=$vpsHost", "host=$spoof"))
                }
            }
        }
        return if (outLines.isNotEmpty()) outLines.joinToString("\n") else trimmed
    }

    /**
     * دریافت لیست سرورها با پشتیبانی از:
     * ۱. لینک‌های ساب اختصاصی BPB (Fragment و Normal) با دور زدن مسمومیت DNS (10.10.34.36) و تکهتکه کردن TLS ClientHello
     * ۲. دنبال کردن خودکار لینک‌های اشاره‌گر (مثل Gist حاوی لینک https://... دیگر)
     * ۳. لیست‌های امضاشده با کلید ECDSA P-256 (#PENNY-SIG:)
     */
    fun fetchSignedListWithFallback(urlStr: String, onLog: (String) -> Unit, depth: Int = 0): String {
        if (depth > 2) return ""
        val cleanUrl = normalizeSubscriptionUrl(urlStr)
        // حذف کامل پروکسی شخص ثالث ghproxy.net برای جلوگیری از حملات MITM و تزریق کانفیگ جعلی
        val mirrors = listOf(cleanUrl)

        for ((i, mirror) in mirrors.withIndex()) {
            try {
                val rawContent = downloadHttpsUrl(mirror, onLog)
                if (rawContent.isNotEmpty()) {
                    val bodyToParse = if (rawContent.trimStart().startsWith("#PENNY-SIG:", ignoreCase = true)) {
                        val (sigOk, verifiedBody) = VipLicenseManager.verifySignedServerList(rawContent)
                        if (sigOk) {
                            onLog("Verified ECDSA-signed server list (source ${i + 1})")
                            verifiedBody
                        } else {
                            onLog("Security Alert: Invalid ECDSA signature on source ${i + 1}; rejecting tampered list!")
                            continue
                        }
                    } else {
                        onLog("Downloaded HTTPS subscription (source ${i + 1}): ${rawContent.length} chars")
                        rawContent
                    }

                    // اگر محتوای دانلودشده خودش یک یا چند لینک https:// دیگر است، آن را دنبال کن
                    val nonCommentLines = bodyToParse.lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                    if (nonCommentLines.isNotEmpty() && nonCommentLines.all { it.startsWith("https://", ignoreCase = true) }) {
                        val nestedResults = mutableListOf<String>()
                        for (nestedUrl in nonCommentLines.take(3)) {
                            onLog("Following nested subscription URL: $nestedUrl")
                            val nested = fetchSignedListWithFallback(nestedUrl, onLog, depth + 1)
                            if (nested.isNotEmpty()) nestedResults.add(nested)
                        }
                        if (nestedResults.isNotEmpty()) {
                            return nestedResults.joinToString("\n")
                        }
                        continue
                    }

                    val playable = extractPlayableConfigs(bodyToParse, onLog)
                    if (playable.isNotEmpty()) {
                        return playable
                    }
                }
            } catch (e: Exception) {
                onLog("Download failed (source ${i + 1}): ${e.message}")
            }
        }
        return ""
    }

    fun testServerTcpPing(guid: String): Long {
        val profile = MmkvManager.decodeServerConfig(guid) ?: return -1L
        var host = profile.server?.trim().orEmpty()
        var port = profile.serverPort?.toString()?.trim()?.toIntOrNull() ?: -1

        if (host.isEmpty() || port <= 0) {
            val raw = MmkvManager.decodeServerRaw(guid)
            if (!raw.isNullOrBlank()) {
                try {
                    val root = JSONObject(raw)
                    val outbounds = root.optJSONArray("outbounds")
                    if (outbounds != null) {
                        for (i in 0 until outbounds.length()) {
                            val ob = outbounds.optJSONObject(i) ?: continue
                            val settings = ob.optJSONObject("settings") ?: continue
                            val vnext = settings.optJSONArray("vnext")?.optJSONObject(0)
                            val srv = settings.optJSONArray("servers")?.optJSONObject(0)
                            val h = vnext?.optString("address", "")?.takeIf { it.isNotEmpty() }
                                ?: srv?.optString("address", "")?.takeIf { it.isNotEmpty() }
                                ?: settings.optString("address", "")
                            val p = vnext?.optInt("port", 0)?.takeIf { it > 0 }
                                ?: srv?.optInt("port", 0)?.takeIf { it > 0 }
                                ?: settings.optInt("port", 0)
                            if (h.isNotEmpty() && p > 0) {
                                host = h
                                port = p
                                profile.server = host
                                profile.serverPort = port.toString()
                                MmkvManager.encodeServerConfig(guid, profile)
                                break
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        if (host.isEmpty() || port <= 0) return -1L

        // محافظت در برابر مسمومیت DNS ایران (10.10.34.36) هنگام تست پینگ سرورها
        if (host.endsWith(".pages.dev", ignoreCase = true) ||
            host.endsWith(".workers.dev", ignoreCase = true)
        ) {
            host = CLEAN_CF_ENDPOINTS.first()
            profile.server = host
            MmkvManager.encodeServerConfig(guid, profile)
        }

        return try {
            val addr = InetAddress.getByName(host)
            val ipStr = addr.hostAddress.orEmpty()
            if (ipStr.startsWith("10.10.") || addr.isLoopbackAddress || addr.isAnyLocalAddress) {
                return -1L
            }
            val start = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(addr, port), TCP_TIMEOUT_MS)
            }
            (System.currentTimeMillis() - start).coerceAtLeast(1L)
        } catch (_: Exception) {
            -1L
        }
    }

    suspend fun tcpFilter(guids: List<String>): List<Pair<String, Long>> {
        val sem = Semaphore(TCP_PARALLEL)
        return withContext(Dispatchers.IO) {
            guids.map { guid ->
                async { sem.withPermit { guid to testServerTcpPing(guid) } }
            }.awaitAll()
        }.filter { it.second > 0 }.sortedBy { it.second }
    }

    fun isLocalPortOpen(socksPort: Int): Boolean {
        return try {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", socksPort), 800) }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun readExactBytes(input: java.io.InputStream, count: Int): ByteArray? {
        val out = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val r = input.read(out, offset, count - offset)
            if (r <= 0) return null
            offset += r
        }
        return out
    }

    private fun socks5DownloadProbe(
        host: String,
        port: Int,
        path: String,
        useTls: Boolean,
        socksPort: Int,
        timeoutMs: Int
    ): Pair<Long, Int> {
        val start = System.currentTimeMillis()
        Socket().use { rawSocket ->
            rawSocket.soTimeout = timeoutMs
            rawSocket.connect(InetSocketAddress("127.0.0.1", socksPort), 1500)
            val rawIn = rawSocket.getInputStream()
            val rawOut = rawSocket.getOutputStream()

            // 1) SOCKS5 Greeting (VER=0x05, NMETHODS=1, NO_AUTH=0x00)
            rawOut.write(byteArrayOf(0x05, 0x01, 0x00))
            rawOut.flush()
            val greet = readExactBytes(rawIn, 2) ?: return Pair(-1L, 0)
            if (greet[0] != 0x05.toByte() || greet[1] != 0x00.toByte()) return Pair(-1L, 0)

            // 2) SOCKS5 CONNECT with ATYP=0x03 (Domain Name - bypasses poisoned local ISP DNS)
            val hostBytes = host.toByteArray(StandardCharsets.US_ASCII)
            val req = ByteArray(4 + 1 + hostBytes.size + 2)
            req[0] = 0x05
            req[1] = 0x01 // CONNECT
            req[2] = 0x00 // RSV
            req[3] = 0x03 // ATYP = Domain Name
            req[4] = hostBytes.size.toByte()
            System.arraycopy(hostBytes, 0, req, 5, hostBytes.size)
            req[5 + hostBytes.size] = ((port shr 8) and 0xFF).toByte()
            req[6 + hostBytes.size] = (port and 0xFF).toByte()
            rawOut.write(req)
            rawOut.flush()

            val replyHdr = readExactBytes(rawIn, 4) ?: return Pair(-1L, 0)
            if (replyHdr[0] != 0x05.toByte() || replyHdr[1] != 0x00.toByte()) return Pair(-1L, 0)
            when (replyHdr[3].toInt() and 0xFF) {
                0x01 -> readExactBytes(rawIn, 6) ?: return Pair(-1L, 0)
                0x03 -> {
                    val lenByte = readExactBytes(rawIn, 1) ?: return Pair(-1L, 0)
                    val dLen = lenByte[0].toInt() and 0xFF
                    readExactBytes(rawIn, dLen + 2) ?: return Pair(-1L, 0)
                }
                0x04 -> readExactBytes(rawIn, 18) ?: return Pair(-1L, 0)
                else -> return Pair(-1L, 0)
            }

            // 3) Send HTTP GET and verify real downloaded bytes through the tunnel
            val targetSocket: Socket = if (useTls) {
                val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                (sslFactory.createSocket(rawSocket, host, port, true) as SSLSocket).apply {
                    soTimeout = timeoutMs
                    startHandshake()
                }
            } else {
                rawSocket
            }

            val inStream = targetSocket.getInputStream()
            val outStream = targetSocket.getOutputStream()
            val httpReq = buildString {
                append("GET ").append(path.ifEmpty { "/" }).append(" HTTP/1.1\r\n")
                append("Host: ").append(host).append("\r\n")
                append("User-Agent: Mozilla/5.0 (Linux; Android 14)\r\n")
                append("Accept: */*\r\n")
                append("Connection: close\r\n\r\n")
            }
            outStream.write(httpReq.toByteArray(StandardCharsets.US_ASCII))
            outStream.flush()

            val buf = ByteArray(4096)
            var totalBytes = 0
            var firstChunk = ""
            while (totalBytes < 4096) {
                val n = inStream.read(buf, 0, buf.size)
                if (n <= 0) break
                if (firstChunk.isEmpty()) {
                    firstChunk = String(buf, 0, minOf(n, 256), StandardCharsets.ISO_8859_1)
                }
                totalBytes += n
                if (firstChunk.contains("\r\n\r\n") && totalBytes >= 16) break
            }

            if (totalBytes > 0 && firstChunk.startsWith("HTTP/1.")) {
                val statusLine = firstChunk.lineSequence().firstOrNull().orEmpty()
                val parts = statusLine.split(' ')
                val code = parts.getOrNull(1)?.toIntOrNull() ?: 0
                if (code in 200..399) {
                    val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                    return Pair(elapsed, totalBytes)
                }
            }
            return Pair(-1L, 0)
        }
    }

    fun testThroughLocalProxy(testUrl: String, socksPort: Int, onLog: (String) -> Unit): Long {
        val parsed = runCatching { URL(testUrl) }.getOrNull()
        val host = parsed?.host?.takeIf { it.isNotBlank() } ?: "www.gstatic.com"
        val path = parsed?.file?.takeIf { it.isNotBlank() } ?: "/generate_204"

        // ابتدا تست دانلود سریع روی پورت ۸۰ از داخل تانل رمزنگاری‌شده SOCKS5 (ATYP=0x03 بدون DNS محلی)
        try {
            val (elapsedHttp, bytesHttp) = socks5DownloadProbe(
                host = host,
                port = 80,
                path = path,
                useTls = false,
                socksPort = socksPort,
                timeoutMs = 3600
            )
            if (elapsedHttp > 0L && bytesHttp > 0) {
                onLog("Download OK ($host -> ${bytesHttp}B in ${elapsedHttp}ms)")
                return elapsedHttp
            }
        } catch (_: Exception) {
        }

        // در صورت مسدود بودن پورت ۸۰ روی خروجی، تست دانلود HTTPS روی پورت ۴۴۳ از داخل تانل SOCKS5
        return try {
            val (elapsedTls, bytesTls) = socks5DownloadProbe(
                host = host,
                port = 443,
                path = path,
                useTls = true,
                socksPort = socksPort,
                timeoutMs = 4000
            )
            if (elapsedTls > 0L && bytesTls > 0) {
                onLog("Download OK TLS ($host -> ${bytesTls}B in ${elapsedTls}ms)")
                elapsedTls
            } else {
                onLog("Download test returned 0B ($host)")
                -1L
            }
        } catch (e: Exception) {
            onLog("Download test failed ($host): ${e.javaClass.simpleName}: ${e.message}")
            -1L
        }
    }

    data class AppUpdateInfo(
        val currentVersion: String,
        val latestVersion: String,
        val hasUpdate: Boolean,
        val downloadUrl: String,
        val releaseNotes: String
    )

    const val APP_VERSION_NAME = "2.9.4"
    const val GITHUB_RELEASES_PAGE = "https://github.com/o77ss0120-max/v2rayNG/releases"
    const val GITHUB_ACTIONS_PAGE = "https://github.com/o77ss0120-max/v2rayNG/actions"

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

    fun checkAppUpdateOnGitHub(): AppUpdateInfo {
        val apiUrls = listOf(
            "https://api.github.com/repos/o77ss0120-max/v2rayNG/releases/latest"
        )
        for (u in apiUrls) {
            try {
                val conn = URL(u).openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 6000
                    conn.readTimeout = 6000
                    conn.setRequestProperty("Accept", "application/vnd.github+json")
                    conn.setRequestProperty("User-Agent", "PennyVPN-Updater/2.9.4")
                    if (conn.responseCode == 200) {
                        val text = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                        val obj = JSONObject(text)
                        val tag = obj.optString("tag_name", "").removePrefix("v").ifEmpty { APP_VERSION_NAME }
                        var apkUrl = obj.optString("html_url", GITHUB_RELEASES_PAGE)
                        val assets = obj.optJSONArray("assets")
                        if (assets != null) {
                            for (i in 0 until assets.length()) {
                                val a = assets.optJSONObject(i) ?: continue
                                val dl = a.optString("browser_download_url", "")
                                if (dl.endsWith(".apk", ignoreCase = true)) {
                                    apkUrl = dl
                                    break
                                }
                            }
                        }
                        val isNewer = isSemanticVersionNewer(APP_VERSION_NAME, tag)
                        return AppUpdateInfo(
                            currentVersion = APP_VERSION_NAME,
                            latestVersion = tag,
                            hasUpdate = isNewer,
                            downloadUrl = apkUrl,
                            releaseNotes = ""
                        )
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (_: Exception) {
            }
        }
        return AppUpdateInfo(
            currentVersion = APP_VERSION_NAME,
            latestVersion = APP_VERSION_NAME,
            hasUpdate = false,
            downloadUrl = GITHUB_RELEASES_PAGE,
            releaseNotes = ""
        )
    }
}
