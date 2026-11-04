package com.v2ray.ang.fmt

import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.JsonUtil
import org.json.JSONObject

object CustomFmt : FmtBase() {
    /**
     * Parses a JSON string into a ProfileItem object.
     * Supports both flat Xray v26 settings and nested vnext/servers settings (e.g. BPB Panel).
     */
    fun parse(str: String): ProfileItem {
        val config = ProfileItem.create(EConfigType.CUSTOM)
        val fullConfig = JsonUtil.fromJson(str, V2rayConfig::class.java)
        val outbound = fullConfig?.getProxyOutbound()

        config.remarks = fullConfig?.remarks ?: System.currentTimeMillis().toString()
        config.server = outbound?.getServerAddress()
        config.serverPort = outbound?.getServerPort()?.toString()

        if (config.server.isNullOrBlank() || config.serverPort.isNullOrBlank()) {
            try {
                val root = JSONObject(str)
                val outbounds = root.optJSONArray("outbounds")
                if (outbounds != null) {
                    for (i in 0 until outbounds.length()) {
                        val ob = outbounds.optJSONObject(i) ?: continue
                        val settings = ob.optJSONObject("settings") ?: continue
                        val vnext = settings.optJSONArray("vnext")?.optJSONObject(0)
                        val srv = settings.optJSONArray("servers")?.optJSONObject(0)
                        val host = vnext?.optString("address", "")?.takeIf { it.isNotBlank() }
                            ?: srv?.optString("address", "")?.takeIf { it.isNotBlank() }
                            ?: settings.optString("address", "").takeIf { it.isNotBlank() }
                        val port = vnext?.optInt("port", 0)?.takeIf { it > 0 }
                            ?: srv?.optInt("port", 0)?.takeIf { it > 0 }
                            ?: settings.optInt("port", 0).takeIf { it > 0 }
                        if (!host.isNullOrBlank() && port != null) {
                            config.server = host
                            config.serverPort = port.toString()
                            break
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return config
    }
}
