package com.v2ray.ang.ui.main

import android.content.Context
import android.telephony.TelephonyManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
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
 * به قطعات 80 تا 160 بایتی با تاخیر 1 میلی‌ثانیه جهت عبور از DPI مخابرات/ایرانسل/همراه‌اول
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
                        val chunkLen = Random.nextInt(80, 160).coerceAtMost(end - cursor)
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

    const val BPB_HOST = "h1bq9ybcr6av6637iy1pyhiet.pages.dev"
    const val BPB_MIXED_SNI = "H1bQ9ybcr6AV6637iy1pyhIET.pageS.dEv"

    // استخر آی‌پی‌های تمیز کلودفلر در رنج‌های متنوع Anycast برای اسکنر داخلی Clean IP
    val CANDIDATE_CF_ANYCAST_IPS = listOf(
        "104.17.148.22",
        "104.17.147.22",
        "172.66.44.58",
        "172.66.47.198",
        "172.64.155.209",
        "104.18.2.115",
        "162.159.135.42",
        "104.16.132.229",
        "104.19.143.29",
        "108.162.198.45",
        "172.67.74.226",
        "188.114.96.3",
        "www.speedtest.net",
        "www.visa.com"
    )

    @Volatile
    var CLEAN_CF_ENDPOINTS: List<String> = listOf(
        "104.17.148.22",
        "104.17.147.22",
        "172.66.44.58",
        "172.66.47.198",
        "172.64.155.209",
        "104.18.2.115",
        "162.159.135.42",
        "www.speedtest.net"
    )

    @Volatile
    var detectedIspProfile: String = "Auto (Smart Fragment 80-160B)"

    // پورت‌های استاندارد TLS کلودفلر در BPB-VPN (پورت‌های 8443 و 2083 روی ایرانسل و همراه اول عملکرد عالی دارند)
    val BPB_TLS_PORTS = listOf(443, 8443, 2083)

    // تنظیمات استاندارد TLS Fragment و Smart Fragment مشابه BPB-VPN برای هسته Xray v26 (finalmask)
    const val BPB_FINALMASK_NORMAL_JSON =
        """{"tcp":[{"type":"fragment","settings":{"packets":"tlshello","length":"100-200","delay":"1"}}]}"""
    const val BPB_FINALMASK_SMART_JSON =
        """{"tcp":[{"type":"fragment","settings":{"packets":"tlshello","length":"1-5","delay":"1-1"}}]}"""

    // لینک‌های ساب اختصاصی BPB شما (هر دو حالت Fragment و Normal) + منابع پشتیبان
    const val DEDICATED_BPB_FRAGMENT_SUB_URL =
        "https://h1bq9ybcr6av6637iy1pyhiet.pages.dev/OOlxS8IA63x6/sub/fragment?app=xray#BPB-Fragment"
    const val DEDICATED_BPB_SUB_URL =
        "https://h1bq9ybcr6av6637iy1pyhiet.pages.dev/OOlxS8IA63x6/sub/normal?app=xray#%F0%9F%92%A6%20BPB%20Normal"

    val SIGNED_STANDARD_SUBSCRIPTION_SOURCES = listOf(
        DEDICATED_BPB_FRAGMENT_SUB_URL,
        DEDICATED_BPB_SUB_URL,
        "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Sub1.txt",
        "https://gist.githubusercontent.com/o77ss0120-max/5e592995c7b0f82b924cec572c0422a9/raw/penny_configs.txt"
    )

    val SIGNED_VIP_SUBSCRIPTION_SOURCES = listOf(
        DEDICATED_BPB_FRAGMENT_SUB_URL,
        DEDICATED_BPB_SUB_URL,
        "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Sub1.txt",
        "https://gist.githubusercontent.com/o77ss0120-max/5e592995c7b0f82b924cec572c0422a9/raw/penny_configs.txt"
    )

    val TEST_URLS = listOf(
        "https://www.gstatic.com/generate_204",
        "https://cp.cloudflare.com/generate_204"
    )

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
    }

    /**
     * فعال‌سازی خودکار تنظیمات TLS Fragment، DNS ضدتحریم و مسیریابی مستقیم سایت‌ها و بانک‌های داخل ایران (Iran Direct Routing)
     */
    fun applyBpbCoreOptimizations(fragLength: String = "100-200", fragInterval: String = "1") {
        try {
            MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_ENABLED, true)
            MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_PACKETS, "tlshello")
            MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_LENGTH, fragLength)
            MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_INTERVAL, fragInterval)
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
                    Triple("Irancell (MTN) • Deep Fragment 10-30B", "10-30", "2")
                opLower.contains("mci") || opLower.contains("ir-tci") || opLower.contains("hamrah") ->
                    Triple("Hamrah Aval (MCI) • Smart Fragment 20-50B", "20-50", "1")
                opLower.contains("rightel") ->
                    Triple("Rightel • Fast Fragment 40-90B", "40-90", "1")
                rawOp.isNotEmpty() ->
                    Triple("$rawOp • Balanced Fragment 80-160B", "80-160", "1")
                else ->
                    Triple("Wi-Fi / Broadband • Fragment 80-160B", "80-160", "1")
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
     * نودهای آماده BPB-VPN با آی‌پی‌های تمیز کلودفلر (104.17.148.22, 104.17.147.22, 172.66.44.58 و ...)
     * به همراه پورت‌های 443، 8443 و 2083 و پارامتر فرگمنت (fm) برای اتصال تضمینی در ایران
     */
    fun getBuiltinBpbFallbackConfigs(): String {
        val nodes = mutableListOf<String>()
        val fmNormal = urlEncode(BPB_FINALMASK_NORMAL_JSON)
        val fmSmart = urlEncode(BPB_FINALMASK_SMART_JSON)

        for (ip in CLEAN_CF_ENDPOINTS) {
            for (port in BPB_TLS_PORTS) {
                // 1. VLESS + BPB TLS Fragment (100-200ms)
                nodes.add(
                    "vless://5d91bcf1-9bbe-4c46-95f5-d1059fe98e9d@$ip:$port?encryption=none&security=tls&type=ws&host=$BPB_HOST&path=%2Fvl%2Fp08StKN7UT3vN1ToQFedY5JYlt%3Fed%3D2560&sni=$BPB_MIXED_SNI&fp=chrome&alpn=http%2F1.1&fm=$fmNormal#PennyVPN"
                )
                // 2. VLESS + BPB Smart Fragment (1-5ms)
                if (port == 443 || port == 8443) {
                    nodes.add(
                        "vless://5d91bcf1-9bbe-4c46-95f5-d1059fe98e9d@$ip:$port?encryption=none&security=tls&type=ws&host=$BPB_HOST&path=%2Fvl%2FNGmVOkqMU67fFMyJQSgbXbR5AzgvF3%3Fed%3D2560&sni=$BPB_MIXED_SNI&fp=chrome&alpn=http%2F1.1&fm=$fmSmart#PennyVPN"
                    )
                }
                // 3. Trojan + BPB TLS Fragment (100-200ms)
                nodes.add(
                    "trojan://ODXencHfxA%2CT3yBpD@$ip:$port?security=tls&type=ws&host=$BPB_HOST&path=%2Ftr%2FtpHAPr4mwS7BN4F4%3Fed%3D2560&sni=$BPB_MIXED_SNI&fp=chrome&alpn=http%2F1.1&fm=$fmNormal#PennyVPN"
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
     * دانلود مستقیم از طریق آی‌پی‌های تمیز BPB-VPN (104.17.148.22 و ...) + پورت‌های 443/8443/2083
     * + تکهتکه کردن TLS ClientHello (TlsHelloFragmentSocket) جهت عبور از مسمومیت DNS (10.10.34.36) و DPI ایران
     */
    private fun downloadViaCleanIpTls(cleanUrl: String, onLog: (String) -> Unit): String {
        val parsed = URL(cleanUrl)
        val originalHost = parsed.host
        val pathAndQuery = if (parsed.query.isNullOrEmpty()) parsed.path else "${parsed.path}?${parsed.query}"
        val sniHost = if (originalHost.equals(BPB_HOST, ignoreCase = true)) {
            BPB_MIXED_SNI
        } else {
            toMixedCaseSni(originalHost)
        }
        val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val probeTargets = listOf(
            "104.17.148.22" to 8443,
            "104.17.147.22" to 8443,
            "172.66.44.58" to 443,
            "172.66.47.198" to 8443,
            "104.17.148.22" to 443,
            "104.17.147.22" to 2083,
            "www.speedtest.net" to 443
        )

        for ((endpoint, port) in probeTargets) {
            var rawSocket: Socket? = null
            var sslSocket: SSLSocket? = null
            try {
                rawSocket = TlsHelloFragmentSocket(endpoint, port, 4500)
                rawSocket.soTimeout = 7000
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
            "Only HTTPS URLs are permitted"
        }

        val isPagesDev = cleanUrl.contains(".pages.dev", ignoreCase = true) ||
            cleanUrl.contains(".workers.dev", ignoreCase = true)

        // برای دامنه‌های pages.dev و workers.dev، ابتدا مستقیماً از تانل Clean IP + TLS Fragment (مشابه BPB-VPN) استفاده کن
        if (isPagesDev) {
            val bypassed = downloadViaCleanIpTls(cleanUrl, onLog ?: {})
            if (bypassed.isNotEmpty()) return bypassed
        }

        val sep = if ('?' in cleanUrl) '&' else '?'
        val urlWithTs = "${cleanUrl}${sep}t=${System.currentTimeMillis()}"

        val url = URL(urlWithTs)
        val resolvedIp = runCatching { InetAddress.getByName(url.host).hostAddress.orEmpty() }.getOrDefault("")
        if (resolvedIp.startsWith("10.10.") || resolvedIp == "127.0.0.1" || resolvedIp == "0.0.0.0") {
            onLog?.invoke("Detected ISP DNS poisoning ($resolvedIp) for ${url.host}; switching to Clean IP TLS")
            val bypassed = downloadViaCleanIpTls(cleanUrl, onLog ?: {})
            if (bypassed.isNotEmpty()) return bypassed
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

                        // استخراج finalmask از JSON پنل BPB یا تزریق خودکار فرگمنت BPB-VPN برای دامنه‌های pages.dev
                        val fmJson = finalmaskObj?.toString()?.takeIf { it.isNotEmpty() }
                            ?: if (host.endsWith(".pages.dev", ignoreCase = true) || sni.endsWith(".pages.dev", ignoreCase = true)) {
                                BPB_FINALMASK_NORMAL_JSON
                            } else {
                                ""
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

                            val targetAddresses = if (rawAddr.endsWith(".pages.dev", ignoreCase = true)) {
                                CLEAN_CF_ENDPOINTS
                            } else {
                                listOf(rawAddr)
                            }
                            for (addr in targetAddresses) {
                                val dedupKey = "vless:$uid@$addr:$port:$path:$fmJson"
                                if (seenKeys.add(dedupKey)) {
                                    extractedUris.add(
                                        "vless://${urlEncode(uid)}@$addr:$port?${vlessQuery.joinToString("&")}#PennyVPN"
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
                            val targetAddresses = if (rawAddr.endsWith(".pages.dev", ignoreCase = true)) {
                                CLEAN_CF_ENDPOINTS
                            } else {
                                listOf(rawAddr)
                            }
                            for (addr in targetAddresses) {
                                val dedupKey = "trojan:$pwd@$addr:$port:$path:$fmJson"
                                if (seenKeys.add(dedupKey)) {
                                    extractedUris.add(
                                        "trojan://${urlEncode(pwd)}@$addr:$port?${queryPairs.joinToString("&")}#PennyVPN"
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
        if (host.endsWith(".pages.dev", ignoreCase = true)) {
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
                conn.connectTimeout = 7000
                conn.readTimeout = 7000
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
