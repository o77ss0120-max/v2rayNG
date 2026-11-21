package com.v2ray.ang.ui.main

import android.content.Context
import android.content.SharedPreferences
import com.tencent.mmkv.MMKV
import com.v2ray.ang.handler.MmkvManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * لایه یکپارچه ذخیره‌سازی Penny VPN بر پایه MMKV (مطابق معماری اصلی v2rayNG)
 * همراه با مهاجرت خودکار یک‌باره از SharedPreferences قدیمی «penny_prefs».
 */
internal class PennyPreferencesStore(private val context: Context) {

    private val mmkv: MMKV by lazy {
        MMKV.mmkvWithID("penny_state", MMKV.MULTI_PROCESS_MODE)
    }

    init {
        migrateLegacyPrefsIfNeeded()
    }

    private fun migrateLegacyPrefsIfNeeded() {
        if (mmkv.decodeBool("migrated_from_legacy_prefs_v1", false)) return
        val legacy: SharedPreferences = runCatching {
            context.getSharedPreferences("penny_prefs", Context.MODE_PRIVATE)
        }.getOrNull() ?: run {
            mmkv.encode("migrated_from_legacy_prefs_v1", true)
            return
        }
        val all = legacy.all
        if (all.isNotEmpty()) {
            for ((key, value) in all) {
                when (value) {
                    is Boolean -> mmkv.encode(key, value)
                    is Int -> mmkv.encode(key, value)
                    is Long -> mmkv.encode(key, value)
                    is Float -> mmkv.encode(key, value)
                    is String -> mmkv.encode(key, value)
                    is Set<*> -> {
                        val strSet = value.filterIsInstance<String>().toSet()
                        mmkv.encode(key, strSet)
                    }
                }
            }
        }
        mmkv.encode("migrated_from_legacy_prefs_v1", true)
    }

    fun getBoolean(key: String, default: Boolean): Boolean = mmkv.decodeBool(key, default)
    fun putBoolean(key: String, value: Boolean) { mmkv.encode(key, value) }

    fun getInt(key: String, default: Int): Int = mmkv.decodeInt(key, default)
    fun putInt(key: String, value: Int) { mmkv.encode(key, value) }

    fun getLong(key: String, default: Long): Long = mmkv.decodeLong(key, default)
    fun putLong(key: String, value: Long) { mmkv.encode(key, value) }

    fun getString(key: String, default: String? = null): String? = mmkv.decodeString(key, default)
    fun putString(key: String, value: String) { mmkv.encode(key, value) }

    fun getStringSet(key: String, default: Set<String>): Set<String> =
        mmkv.decodeStringSet(key, default) ?: default

    fun putStringSet(key: String, value: Set<String>) { mmkv.encode(key, value) }

    fun remove(key: String) { mmkv.removeValueForKey(key) }

    fun loadCachedServerList(mode: ServiceTierMode, loadBalanceSeed: Int): List<PennyServer> {
        val key = when (mode) {
            ServiceTierMode.PUBLIC -> "list"
            ServiceTierMode.DEDICATED -> "vip_list"
            ServiceTierMode.GHOST -> "ghost_list"
        }
        val isVip = mode != ServiceTierMode.PUBLIC
        val json = mmkv.decodeString(key, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val out = mutableListOf<PennyServer>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val g = o.getString("g")
                if (MmkvManager.decodeServerConfig(g) != null) {
                    out.add(PennyServer(g, o.optString("c", ""), o.optLong("m"), "", isVip))
                }
            }
            assignLabels(out, isVipList = isVip, loadBalanceSeed = loadBalanceSeed)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCachedServerList(list: List<PennyServer>, scanTime: Long, mode: ServiceTierMode) {
        try {
            val arr = JSONArray()
            list.forEach { arr.put(JSONObject().put("g", it.guid).put("c", it.codeTag).put("m", it.ms)) }
            when (mode) {
                ServiceTierMode.PUBLIC -> {
                    mmkv.encode("list", arr.toString())
                    mmkv.encode("scan_time", scanTime)
                }
                ServiceTierMode.DEDICATED -> {
                    mmkv.encode("vip_list", arr.toString())
                    mmkv.encode("vip_scan_time", scanTime)
                }
                ServiceTierMode.GHOST -> {
                    mmkv.encode("ghost_list", arr.toString())
                    mmkv.encode("ghost_scan_time", scanTime)
                }
            }
        } catch (_: Exception) {
        }
    }
}
