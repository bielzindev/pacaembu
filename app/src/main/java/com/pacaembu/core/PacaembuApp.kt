package com.pacaembu.core

import android.app.Application

class PacaembuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        CrashLog.install(this)
        AppRepository.init(this)
    }
}
