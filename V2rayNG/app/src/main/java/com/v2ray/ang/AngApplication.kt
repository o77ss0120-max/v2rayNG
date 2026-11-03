package com.v2ray.ang

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.Utils

class AngApplication : Application(), Configuration.Provider {
    companion object {
        lateinit var application: AngApplication
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        application = this
    }

    override val workManagerConfiguration: Configuration by lazy {
        Configuration.Builder()
            .setDefaultProcessName("${ANG_PACKAGE}:bg")
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        MMKV.initialize(this)

        Utils.setNightMode()
        try {
            WorkManager.initialize(this, workManagerConfiguration)
        } catch (_: Exception) {
            // Already initialized via Configuration.Provider by MobileAds or WorkManager
        }

        SettingsManager.initRoutingRulesets(this)
        es.dmoral.toasty.Toasty.Config.getInstance()
            .setGravity(android.view.Gravity.BOTTOM, 0, 200)
            .apply()
    }
}
