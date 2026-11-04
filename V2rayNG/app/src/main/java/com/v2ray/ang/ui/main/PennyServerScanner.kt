package com.v2ray.ang.ui.main

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
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * محدودکننده واقعی پهنای باند (Token-Bucket Speed Limiter) برای کاربران عادی (رایگان):
 * - کاربران عادی (Free Tier): سقف سرعت دانلود 256 KB/s (معادل 2.0 Mbps) و آپلود 128 KB/s (معادل 1.0 Mbps)
 * - کاربران VIP (اشتراک ۱ ماه ۵۰ گیگ): بدون هیچ‌گونه محدودیت سرعت (Full Unlimited Speed)
 */
object PennySpeedLimiter {
    const val FREE_MAX_DOWN_BYTES_PER_SEC = 256 * 1024L // 256 KB/s (~2.0 Mbps)
    const val FREE_MAX_UP_BYTES_PER_SEC = 128 * 1024L   // 128 KB/s (~1.0 Mbps)

    @Volatile
    var isVipUnrestricted: Boolean = false

    private var serverSocket: ServerSocket? = null
    private var activeListenPort: Int = -1
    private var activeTargetPort: Int = -1
    private val running = AtomicBoolean(false)
    private val pool = Executors.newCachedThreadPool()

    private class TokenBucket(private val rateBytesPerSec: Long) {
        private var availableTokens: Double = rateBytesPerSec.toDouble()
        private var lastRefillNs: Long = System.nanoTime()

        @Synchronized
        fun consume(bytes: Int) {
            if (isVipUnrestricted || bytes <= 0 || rateBytesPerSec <= 0L) return
            val now = System.nanoTime()
            val elapsedSec = (now - lastRefillNs) / 1_000_000_000.0
            if (elapsedSec > 0) {
                availableTokens = min(
                    rateBytesPerSec.toDouble(),
                    availableTokens + elapsedSec * rateBytesPerSec.toDouble()
                )
                lastRefillNs = now
            }
            availableTokens -= bytes.toDouble()
            if (availableTokens < 0) {
                val waitMs = ((-availableTokens / rateBytesPerSec.toDouble()) * 1000.0).toLong()
                    .coerceIn(5L, 250L)
                try {
                    Thread.sleep(waitMs)
                } catch (_: InterruptedException) {
                }
            }
        }
    }

    private val downBucket = TokenBucket(FREE_MAX_DOWN_BYTES_PER_SEC)
    private val upBucket = TokenBucket(FREE_MAX_UP_BYTES_PER_SEC)

    @Synchronized
    fun startIfNeeded(listenPort: Int, targetPort: Int, vipActive: Boolean): Int {
        isVipUnrestricted = vipActive
        if (vipActive) {
            return targetPort
        }
        if (running.get() && activeListenPort == listenPort && activeTargetPort == targetPort && serverSocket?.isClosed == false) {
            return listenPort
        }
        stop()
        return try {
            val srv = ServerSocket()
            srv.reuseAddress = true
            srv.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), listenPort), 128)
            serverSocket = srv
            activeListenPort = listenPort
            activeTargetPort = targetPort
            running.set(true)
            pool.execute {
                while (running.get() && !srv.isClosed) {
                    try {
                        val client = srv.accept()
                        client.tcpNoDelay = true
                        pool.execute { handleConnection(client, targetPort) }
                    } catch (_: Exception) {
                        if (!running.get()) break
                    }
                }
            }
            listenPort
        } catch (_: Exception) {
            targetPort
        }
    }

    private fun handleConnection(client: Socket, targetPort: Int) {
        var upstream: Socket? = null
        try {
            upstream = Socket()
            upstream.tcpNoDelay = true
            upstream.connect(InetSocketAddress("127.0.0.1", targetPort), 3000)
            val upSock = upstream
            val tUp = pool.submit {
                pipeWithLimit(client.getInputStream(), upSock.getOutputStream(), upBucket)
                try { upSock.shutdownOutput() } catch (_: Exception) {}
            }
            val tDown = pool.submit {
                pipeWithLimit(upSock.getInputStream(), client.getOutputStream(), downBucket)
                try { client.shutdownOutput() } catch (_: Exception) {}
            }
            tUp.get()
            tDown.get()
        } catch (_: Exception) {
        } finally {
            try { upstream?.close() } catch (_: Exception) {}
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun pipeWithLimit(input: InputStream, output: OutputStream, bucket: TokenBucket) {
        val buf = ByteArray(4096)
        try {
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                bucket.consume(n)
                output.write(buf, 0, n)
                output.flush()
            }
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun stop() {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        activeListenPort = -1
        activeTargetPort = -1
    }
}

internal object PennyServerScanner {
    const val MAX_DOWNLOAD_BYTES = 4 * 1024 * 1024 // سقف ۴ مگابایت برای امنیت دانلود
    const val TCP_TIMEOUT_MS = 2800
    const val MAX_TCP_CANDIDATES = 300
    const val TCP_PARALLEL = 40

    // لینک ساب اختصاصی BPB شما به عنوان منبع اصلی سرورها + منبع پشتیبان Gist
    const val DEDICATED_BPB_SUB_URL =
        "https://h1bq9ybcr6av6637iy1pyhiet.pages.dev/OOlxS8IA63x6/sub/normal?app=xray#%F0%9F%92%A6%20BPB%20Normal"

    val SIGNED_STANDARD_SUBSCRIPTION_SOURCES = listOf(
        DEDICATED_BPB_SUB_URL,
        "https://gist.githubusercontent.com/o77ss0120-max/5e592995c7b0f82b924cec572c0422a9/raw/penny_configs.txt"
    )

    val SIGNED_VIP_SUBSCRIPTION_SOURCES = listOf(
        DEDICATED_BPB_SUB_URL,
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

    fun downloadHttpsUrl(urlStr: String): String {
        val cleanUrl = normalizeSubscriptionUrl(urlStr)
        require(cleanUrl.startsWith("https://", ignoreCase = true)) {
            "Only HTTPS URLs are permitted"
        }
        val sep = if ('?' in cleanUrl) '&' else '?'
        val url = URL("${cleanUrl}${sep}t=${System.currentTimeMillis()}")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
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
     * تبدیل خودکار خروجی JSON پنل‌های BPB (?app=xray) به لینک‌های استاندارد vless://, trojan://, ss://
     * تا هسته v2rayNG آدرس، پورت، SNI، Host و Path تمام سرورها را بدون خطا استخراج و تست پینگ کند.
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

                        val queryPairs = mutableListOf<String>()
                        queryPairs.add("security=${urlEncode(sec)}")
                        queryPairs.add("type=${urlEncode(net)}")
                        if (host.isNotEmpty()) queryPairs.add("host=${urlEncode(host)}")
                        if (path.isNotEmpty()) queryPairs.add("path=${urlEncode(path)}")
                        if (sni.isNotEmpty()) queryPairs.add("sni=${urlEncode(sni)}")
                        if (fp.isNotEmpty()) queryPairs.add("fp=${urlEncode(fp)}")
                        if (alpn.isNotEmpty()) queryPairs.add("alpn=${urlEncode(alpn)}")

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
                            val addr = vnext?.optString("address", "")?.takeIf { it.isNotEmpty() }
                                ?: settings.optString("address", "")
                            val port = vnext?.optInt("port", 0)?.takeIf { it > 0 }
                                ?: settings.optInt("port", 0)
                            val user = vnext?.optJSONArray("users")?.optJSONObject(0)
                            val uid = user?.optString("id", "")?.takeIf { it.isNotEmpty() }
                                ?: settings.optString("id", "")
                            val enc = user?.optString("encryption", "none")?.ifEmpty { "none" } ?: "none"
                            val flow = user?.optString("flow", "") ?: settings.optString("flow", "")

                            if (addr.isEmpty() || port <= 0 || uid.isEmpty()) continue
                            val vlessQuery = mutableListOf("encryption=${urlEncode(enc)}")
                            if (flow.isNotEmpty()) vlessQuery.add("flow=${urlEncode(flow)}")
                            vlessQuery.addAll(queryPairs)

                            val dedupKey = "vless:$uid@$addr:$port:$path:$sni"
                            if (seenKeys.add(dedupKey)) {
                                extractedUris.add(
                                    "vless://${urlEncode(uid)}@$addr:$port?${vlessQuery.joinToString("&")}#PennyVPN"
                                )
                            }
                        } else if (proto == "trojan") {
                            val srv = settings.optJSONArray("servers")?.optJSONObject(0)
                            val addr = srv?.optString("address", "")?.takeIf { it.isNotEmpty() }
                                ?: settings.optString("address", "")
                            val port = srv?.optInt("port", 0)?.takeIf { it > 0 }
                                ?: settings.optInt("port", 0)
                            val pwd = srv?.optString("password", "")?.takeIf { it.isNotEmpty() }
                                ?: settings.optString("password", "")

                            if (addr.isEmpty() || port <= 0 || pwd.isEmpty()) continue
                            val dedupKey = "trojan:$pwd@$addr:$port:$path:$sni"
                            if (seenKeys.add(dedupKey)) {
                                extractedUris.add(
                                    "trojan://${urlEncode(pwd)}@$addr:$port?${queryPairs.joinToString("&")}#PennyVPN"
                                )
                            }
                        }
                    }
                }

                if (extractedUris.isNotEmpty()) {
                    onLog("Extracted ${extractedUris.size} VLESS/Trojan nodes from BPB Xray JSON subscription")
                    return extractedUris.joinToString("\n")
                }
            } catch (e: Exception) {
                onLog("JSON subscription parse note: ${e.message}")
            }
        }
        return trimmed
    }

    /**
     * دریافت لیست سرورها با پشتیبانی هم‌زمان از:
     * ۱. لینک ساب اختصاصی BPB (خروجی JSON آرایه Xray و لینک‌های استاندارد)
     * ۲. لیست‌های امضاشده با کلید ECDSA P-256 (#PENNY-SIG:)
     */
    fun fetchSignedListWithFallback(urlStr: String, onLog: (String) -> Unit): String {
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
                val rawContent = downloadHttpsUrl(mirror)
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
        return try {
            val start = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), TCP_TIMEOUT_MS)
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
}
