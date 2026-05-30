package com.statussaver.app

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager

class StatusSaverApp : Application(), Configuration.Provider {
    
    companion object {
        const val TAG = "StatusSaver"
        var instance: StatusSaverApp? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "StatusSaverApp initialized")
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()
}
