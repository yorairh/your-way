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
        // Fetch a newer schedule in the background; it is used from the next launch. Tests must not touch the network.
        if (android.os.Build.FINGERPRINT != "robolectric") {
            val updater = container.scheduleUpdater
            if (updater != null) Thread { updater.updateIfNeeded() }.apply { isDaemon = true; start() }
        }
    }
}
