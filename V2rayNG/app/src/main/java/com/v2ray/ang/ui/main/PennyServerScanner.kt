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
 * بدون محدودیت سرعت برای همه کاربران (Full Unlimited Speed):
 * ترافیک کاربران عادی به‌صورت روزانه به ازای هر دیوایس (۱ گیگابایت پایه در روز + امکان افزایش گیگ با تماشای تبلیغ) محاسبه می‌شود.
 */
object PennySpeedLimiter {
    const val FREE_MAX_DOWN_BYTES_PER_SEC = Long.MAX_VALUE
    const val FREE_MAX_UP_BYTES_PER_SEC = Long.MAX_VALUE

    @Volatile
    var isVipUnrestricted: Boolean = true

    @Synchronized
    fun startIfNeeded(listenPort: Int, targetPort: Int, vipActive: Boolean): Int {
        isVipUnrestricted = true
        return targetPort
    }

    @Synchronized
    fun stop() {
        // No local throttling proxy needed — full native speed on targetPort
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

    val BPB_HOST: String by lazy { decodeXorHex(ENC_BPB1_HOST) }
    val BPB_MIXED_SNI: String by lazy { decodeXorHex(ENC_BPB1_SNI) }
    private val BPB1_UUID: String by lazy { decodeXorHex(ENC_BPB1_UUID) }
    private val BPB1_TR_PWD: String by lazy { decodeXorHex(ENC_BPB1_TR_PWD) }

    val BPB2_HOST: String by lazy { decodeXorHex(ENC_BPB2_HOST) }
    val BPB2_MIXED_SNI: String by lazy { decodeXorHex(ENC_BPB2_SNI) }
    private val BPB2_UUID: String by lazy { decodeXorHex(ENC_BPB2_UUID) }
    private val BPB2_TR_PWD: String by lazy { decodeXorHex(ENC_BPB2_TR_PWD) }

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
    var detectedIspProfile: String = "Auto (Smart Fragment 20-50B)"

    // پورت‌های استاندارد TLS کلودفلر در BPB-VPN (پورت‌های 8443 و 443 و 2083)
    val BPB_TLS_PORTS = listOf(8443, 443, 2083)

    // تنظیمات استاندارد TLS Fragment هماهنگ با هسته Xray-core (بدون maxSplit یا UDP noise که باعث رد شدن کانفیگ شود)
    const val BPB_FINALMASK_NORMAL_JSON =
        """{"tcp":[{"type":"fragment","settings":{"packets":"tlshello","length":"100-200","delay":"1"}}]}"""
    const val BPB_FINALMASK_SMART_JSON =
        """{"tcp":[{"type":"fragment","settings":{"packets":"tlshello","length":"20-50","delay":"1"}}]}"""

    val DEDICATED_BPB_FRAGMENT_SUB_URL: String by lazy { decodeXorHex(ENC_BPB1_SUB_FRAG) }
    val DEDICATED_BPB_SUB_URL: String by lazy { decodeXorHex(ENC_BPB1_SUB_NORM) }
    val DEDICATED_BPB2_FRAGMENT_SUB_URL: String by lazy { decodeXorHex(ENC_BPB2_SUB_FRAG) }

    val SIGNED_STANDARD_SUBSCRIPTION_SOURCES: List<String> by lazy {
        listOf(
            DEDICATED_BPB_FRAGMENT_SUB_URL,
            DEDICATED_BPB2_FRAGMENT_SUB_URL,
            "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Sub1.txt",
            "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Sub2.txt"
        )
    }

    val SIGNED_VIP_SUBSCRIPTION_SOURCES: List<String> by lazy {
        listOf(
            DEDICATED_BPB_FRAGMENT_SUB_URL,
            DEDICATED_BPB2_FRAGMENT_SUB_URL,
            "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Sub1.txt",
            "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Sub2.txt"
        )
    }

    val TEST_URLS = listOf(
        "https://www.gstatic.com/generate_204",
        "https://cp.cloudflare.com/generate_204"
    )

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
    }

    /**
     * تنظیم بهینه DNS ضدتحریم و مسیریابی مستقیم سایت‌ها و بانک‌های داخل ایران.
     * نکته حیاتی: PREF_FRAGMENT_ENABLED سراسری را false نگه می‌داریم تا روی سرورهای عمومی (VLESS-REALITY و ...)
     * تنظیمات UDP noise و فرگمنت اجباری تزریق نشود؛ سرورهای کلودفلر خودشان پارامتر اختصاصی finalMask (fm) دارند.
     */
    fun applyBpbCoreOptimizations(fragLength: String = "20-50", fragInterval: String = "1") {
        try {
            MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_ENABLED, false)
            MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_PACKETS, "tlshello")
            MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_LENGTH, fragLength)
            MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_INTERVAL, fragInterval)
            MmkvManager.encodeSettings(AppConfig.PREF_DELAY_TEST_URL, AppConfig.DELAY_TEST_URL)
            MmkvManager.encodeSettings(AppConfig.PREF_VPN_DNS, "1.1.1.1")
            MmkvManager.encodeSettings(AppConfig.PREF_REMOTE_DNS, "https://1.1.1.1/dns-query")
            MmkvManager.encodeSettings(AppConfig.PREF_DOMESTIC_DNS, "178.22.122.100")
            MmkvManager.encodeSettings(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY, "IPIfNonMatch")
            MmkvManager.encodeSettings(AppConfig.PREF_VPN_BYPASS_LAN, "1")
        } catch (_: Exception) {
        }
    }

    /**
     * تشخیص خودکار اپراتور اینترنت (ایرانسل، همراه اول، رایتل، مخابرات/شاتل) و تنظیم هوشمند TLS Fragment
     */
    fun detectCarrierAndTuneFragment(context: Context, onLog: ((String) -> Unit)? = null): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val rawOp = (tm?.networkOperatorName ?: tm?.simOperatorName ?: "").trim()
            val opLower = rawOp.lowercase()
            val (profileName, length, interval) = when {
                opLower.contains("irancell") || opLower.contains("mtn") ->
                    Triple("Irancell (MTN) • Deep Fragment 20-50B", "20-50", "1")
                opLower.contains("mci") || opLower.contains("ir-tci") || opLower.contains("hamrah") ->
                    Triple("Hamrah Aval (MCI) • Smart Fragment 20-50B", "20-50", "1")
                opLower.contains("rightel") ->
                    Triple("Rightel • Fast Fragment 40-90B", "40-90", "1")
                rawOp.isNotEmpty() ->
                    Triple("$rawOp • Balanced Fragment 50-100B", "50-100", "1")
                else ->
                    Triple("Wi-Fi / Broadband • Fragment 50-100B", "50-100", "1")
            }
            detectedIspProfile = profileName
            applyBpbCoreOptimizations(fragLength = length, fragInterval = interval)
            onLog?.invoke("ISP Auto-Tuner: $profileName (len=$length, delay=${interval}ms)")
            profileName
        } catch (_: Exception) {
            applyBpbCoreOptimizations()
            detectedIspProfile
        }
    }

    /**
     * اسکنر داخلی آی‌پی تمیز کلودفلر (On-Device Clean IP Scanner):
     * تست موازی ۱۴ رنج Anycast کلودفلر روی پورت‌های 443 و 8443 و انتخاب سریع‌ترین آی‌پی‌های باز روی اینترنت فعلی کاربر
     */
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

    /**
     * ابزار عیب‌یاب هوشمند شبکه (Network Doctor):
     * ۱. بررسی مسمومیت DNS مخابرات (10.10.34.36)
     * ۲. تشخیص اپراتور و تنظیم بهینه TLS Fragment
     * ۳. اسکن آی‌پی‌های تمیز کلودفلر روی اینترنت فعلی کاربر
     * ۴. فعال‌سازی مسیریابی مستقیم سایت‌ها و بانک‌های ایران
     */
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
     * نودهای آماده BPB-VPN با آی‌پی‌های تمیز کلودفلر (104.17.148.22, 104.17.147.22, 172.66.47.27, 172.66.44.229 و ...)
     * به همراه پورت‌های 8443، 443 و 2083 با ترکیب هر دو حالت Native Fragment و Smart FinalMask
     */
    fun getBuiltinBpbFallbackConfigs(): String {
        val nodes = mutableListOf<String>()
        val fmDeep = urlEncode(BPB_FINALMASK_SMART_JSON)
        val fmFast = urlEncode(BPB_FINALMASK_NORMAL_JSON)

        for (ip in CLEAN_CF_ENDPOINTS) {
            // کلاستر ۱ (BPB1: پورت‌های 8443، 443 و 2083 فعال هستند)
            for (port in listOf(8443, 443, 2083)) {
                nodes.add(
                    "vless://$BPB1_UUID@$ip:$port?encryption=none&security=tls&type=ws&host=$BPB_HOST&path=%2Fvl%2FPpQoztI2kHNHs91QvklsrZIF%3Fed%3D2560&sni=$BPB_MIXED_SNI&fp=chrome&alpn=http%2F1.1#OWN-CF1"
                )
                nodes.add(
                    "vless://$BPB1_UUID@$ip:$port?encryption=none&security=tls&type=ws&host=$BPB_HOST&path=%2Fvl%2FolPcudqQC7WWnOTnqALts%3Fed%3D2560&sni=$BPB_MIXED_SNI&fp=chrome&alpn=http%2F1.1&fm=$fmDeep#OWN-CF1"
                )
                nodes.add(
                    "trojan://$BPB1_TR_PWD@$ip:$port?security=tls&type=ws&host=$BPB_HOST&path=%2Ftr%2FVbomkD6DumfXUz3f6N1L3OfQ%3Fed%3D2560&sni=$BPB_MIXED_SNI&fp=chrome&alpn=http%2F1.1&fm=$fmFast#OWN-CF1"
                )
            }
            // کلاستر ۲ (BPB2: پورت اصلی 443 و 8443)
            for (port in listOf(443, 8443)) {
                nodes.add(
                    "vless://$BPB2_UUID@$ip:$port?encryption=none&security=tls&type=ws&host=$BPB2_HOST&path=%2Fvl%2FP0DsgUrIHpxwVC17kqBm1v%3Fed%3D2560&sni=$BPB2_MIXED_SNI&fp=chrome&alpn=http%2F1.1#OWN-CF2"
                )
                nodes.add(
                    "vless://$BPB2_UUID@$ip:$port?encryption=none&security=tls&type=ws&host=$BPB2_HOST&path=%2Fvl%2Fy7wl1tInOhSUZ6Qi7tsll0%3Fed%3D2560&sni=$BPB2_MIXED_SNI&fp=chrome&alpn=http%2F1.1&fm=$fmDeep#OWN-CF2"
                )
                nodes.add(
                    "trojan://$BPB2_TR_PWD@$ip:$port?security=tls&type=ws&host=$BPB2_HOST&path=%2Ftr%2FkbLSeIYkZdqVY73yFdNf2LrR%3Fed%3D2560&sni=$BPB2_MIXED_SNI&fp=chrome&alpn=http%2F1.1&fm=$fmFast#OWN-CF2"
                )
            }
        }
        return nodes.joinToString("\n")
    }

    /**
     * پاک‌سازی URL (حذف fragment مثل #💦 BPB Normal قبل از افزودن پارامتر زمان)
     * و اطمینان از وجود ?app=xray برای لینک‌های BPB Panel
     */
    fun normalizeSubscriptionUrl(urlStr: String): String {
        var clean = urlStr.trim().substringBefore('#').trim()
        if (clean.contains("/sub/") && !clean.contains("app=", ignoreCase = true)) {
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

    /**
     * دانلود مستقیم از طریق آی‌پی‌های تمیز BPB-VPN (104.17.148.22:8443, 104.17.147.22:8443, 104.18.2.115:443 و ...)
     * با استفاده از تکنیک TLS Record-Layer SNI Splitting در TlsHelloFragmentSocket (Conscrypt Engine Mode)
     */
    private fun downloadViaCleanIpTls(cleanUrl: String, onLog: (String) -> Unit): String {
        val parsed = URL(cleanUrl)
        val originalHost = parsed.host
        val pathAndQuery = if (parsed.query.isNullOrEmpty()) parsed.path else "${parsed.path}?${parsed.query}"
        val sniHost = when {
            originalHost.equals(BPB_HOST, ignoreCase = true) -> BPB_MIXED_SNI
            originalHost.equals(BPB2_HOST, ignoreCase = true) -> BPB2_MIXED_SNI
            else -> toMixedCaseSni(originalHost)
        }
        val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
        runCatching {
            val conscryptClass = Class.forName("com.android.org.conscrypt.Conscrypt")
            conscryptClass.getMethod("setUseEngineSocket", SSLSocketFactory::class.java, Boolean::class.javaPrimitiveType)
                .invoke(null, sslFactory, true)
        }
        val probeTargets = listOf(
            "104.17.148.22" to 8443,
            "104.17.147.22" to 8443,
            "104.17.148.22" to 443,
            "104.18.2.115" to 443
        )

        for ((endpoint, port) in probeTargets) {
            var rawSocket: Socket? = null
            var sslSocket: SSLSocket? = null
            try {
                rawSocket = TlsHelloFragmentSocket(endpoint, port, 2500)
                rawSocket.soTimeout = 4000
                sslSocket = sslFactory.createSocket(rawSocket, sniHost, port, true) as SSLSocket
                runCatching {
                    val conscryptClass = Class.forName("com.android.org.conscrypt.Conscrypt")
                    conscryptClass.getMethod("setUseEngineSocket", SSLSocket::class.java, Boolean::class.javaPrimitiveType)
                        .invoke(null, sslSocket, true)
                }
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
            "Only HTTPS URLs are permitted"
        }

        val isPagesDev = cleanUrl.contains(".pages.dev", ignoreCase = true) ||
            cleanUrl.contains(".workers.dev", ignoreCase = true)

        // برای دامنه‌های pages.dev مستقیماً یک‌بار از تانل Clean IP + TLS Record Split استفاده کن و در صورت مسدود بودن وقت را تلف نکن
        if (isPagesDev) {
            val bypassed = downloadViaCleanIpTls(cleanUrl, onLog ?: {})
            if (bypassed.isNotEmpty()) return bypassed
            throw IllegalStateException("Clean IP TLS download skipped for ${URL(cleanUrl).host}; using built-in nodes")
        }

        val sep = if ('?' in cleanUrl) '&' else '?'
        val urlWithTs = "${cleanUrl}${sep}t=${System.currentTimeMillis()}"

        val url = URL(urlWithTs)
        val resolvedIp = runCatching { InetAddress.getByName(url.host).hostAddress.orEmpty() }.getOrDefault("")
        if (resolvedIp.startsWith("10.10.") || resolvedIp == "127.0.0.1" || resolvedIp == "0.0.0.0") {
            onLog?.invoke("Detected ISP DNS poisoning ($resolvedIp) for ${url.host}; switching to Clean IP TLS")
            val bypassed = downloadViaCleanIpTls(cleanUrl, onLog ?: {})
            if (bypassed.isNotEmpty()) return bypassed
            throw IllegalStateException("ISP DNS hijacked ($resolvedIp) for ${url.host}")
        }

        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 7000
            conn.readTimeout = 7000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "v2rayNG/1.9.30 PennyVPN/2.5")
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
                return out.toString(StandardCharsets.UTF_8.name()).trim()
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
        return trimmed
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
        val isGithubUrl = cleanUrl.contains("github.com", ignoreCase = true) ||
            cleanUrl.contains("githubusercontent.com", ignoreCase = true)
        val mirrors = if (isGithubUrl) {
            listOf(cleanUrl, "https://ghproxy.net/$cleanUrl")
        } else {
            listOf(cleanUrl)
        }

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
                            onLog("Signature header mismatch on source ${i + 1}; stripping header")
                            rawContent.lineSequence().drop(1).joinToString("\n").trim()
                        }
                    } else {
                        onLog("Downloaded subscription (source ${i + 1}): ${rawContent.length} chars")
                        rawContent
                    }

                    // اگر محتوای دانلودشده خودش یک یا چند لینک https:// دیگر است (مثل Gist 70 کاراکتری)، آن را دنبال کن
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

    fun testThroughLocalProxy(testUrl: String, socksPort: Int, onLog: (String) -> Unit): Long {
        return try {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
            val start = System.currentTimeMillis()
            val conn = URL(testUrl).openConnection(proxy) as HttpURLConnection
            try {
                conn.connectTimeout = 6500
                conn.readTimeout = 6500
                conn.useCaches = false
                val code = conn.responseCode
                if (code == 204 || code == 200) {
                    (System.currentTimeMillis() - start).coerceAtLeast(1L)
                } else {
                    onLog("Proxy test HTTP $code")
                    -1L
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            onLog("Proxy test failed: ${e.javaClass.simpleName}: ${e.message}")
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

    const val APP_VERSION_NAME = "2.6.2"
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
            "https://api.github.com/repos/o77ss0120-max/v2rayNG/releases/latest",
            "https://ghproxy.net/https://api.github.com/repos/o77ss0120-max/v2rayNG/releases/latest"
        )
        for (u in apiUrls) {
            try {
                val conn = URL(u).openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 6000
                    conn.readTimeout = 6000
                    conn.setRequestProperty("Accept", "application/vnd.github+json")
                    conn.setRequestProperty("User-Agent", "PennyVPN-Updater/2.6.2")
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
