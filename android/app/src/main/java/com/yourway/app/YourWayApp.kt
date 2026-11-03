package com.yourway.app

import android.app.Application
import com.yourway.app.data.AppContainer

class YourWayApp : Application() {
    /** Replaceable so tests can use stand-ins for network and location. */
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        container = AppContainer.create(this)
    }
}
