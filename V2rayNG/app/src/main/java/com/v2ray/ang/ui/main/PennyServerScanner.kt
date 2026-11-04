package com.v2ray.ang.ui.main

import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.nio.charset.StandardCharsets

internal object PennyServerScanner {
    const val MAX_DOWNLOAD_BYTES = 4 * 1024 * 1024 // سقف ۴ مگابایت برای امنیت دانلود
    const val TCP_TIMEOUT_MS = 2500
    const val MAX_TCP_CANDIDATES = 300
    const val TCP_PARALLEL = 40

    // تمام منابع لیست سرورها (عادی و VIP) صرفاً به منابع امضاشده با کلید ECDSA P-256 (#PENNY-SIG:) محدود شده‌اند.
    // منابع عمومی بدون امضا (مانند barry-far) به‌طور کامل حذف شدند.
    val SIGNED_STANDARD_SUBSCRIPTION_SOURCES = listOf(
        "https://gist.githubusercontent.com/o77ss0120-max/5e592995c7b0f82b924cec572c0422a9/raw/penny_configs.txt"
    )

    val SIGNED_VIP_SUBSCRIPTION_SOURCES = listOf(
        "https://gist.githubusercontent.com/o77ss0120-max/5e592995c7b0f82b924cec572c0422a9/raw/penny_configs.txt"
    )

    val TEST_URLS = listOf(
        "https://www.gstatic.com/generate_204",
        "https://cp.cloudflare.com/generate_204"
    )

    fun downloadHttpsUrl(urlStr: String): String {
        val cleanUrl = urlStr.trim()
        require(cleanUrl.startsWith("https://", ignoreCase = true)) {
            "Only HTTPS URLs are permitted"
        }
        val sep = if ('?' in cleanUrl) '&' else '?'
        val url = URL("${cleanUrl}${sep}t=${System.currentTimeMillis()}")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 7000
            conn.readTimeout = 7000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "PennyVPN/2.4")
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
     * دریافت لیست سرورها فقط در صورتی که دارای امضای معتبر ECDSA P-256 (#PENNY-SIG:) باشد.
     * هر لیست بدون امضا یا دستکاری‌شده رد می‌شود.
     */
    fun fetchSignedListWithFallback(urlStr: String, onLog: (String) -> Unit): String {
        val mirrors = listOf(
            urlStr,
            "https://ghproxy.net/$urlStr"
        )
        for ((i, mirror) in mirrors.withIndex()) {
            try {
                val rawContent = downloadHttpsUrl(mirror)
                if (rawContent.isNotEmpty()) {
                    val (sigOk, verifiedBody) = VipLicenseManager.verifySignedServerList(rawContent)
                    if (!sigOk) {
                        onLog("Rejected unsigned or tampered server list from source ${i + 1}")
                        continue
                    }
                    onLog("Verified ECDSA-signed server list (source ${i + 1}): ${verifiedBody.length} chars")
                    return verifiedBody
                }
            } catch (e: Exception) {
                onLog("Download failed (source ${i + 1}): ${e.message}")
            }
        }
        return ""
    }

    fun testServerTcpPing(guid: String): Long {
        val profile = MmkvManager.decodeServerConfig(guid) ?: return -1L
        val host = profile.server?.trim()?.takeIf { it.isNotEmpty() } ?: return -1L
        val port = profile.serverPort?.toString()?.trim()?.toIntOrNull() ?: return -1L
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
