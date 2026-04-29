package com.pitwall.paddock

import android.app.Application
import com.pitwall.paddock.data.NetworkModule

class PaddockApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NetworkModule.init(
            baseUrl = BuildConfig.PITWALL_API_BASE_URL,
        )
    }
}
