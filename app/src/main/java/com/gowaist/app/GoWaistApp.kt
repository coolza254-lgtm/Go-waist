package com.gowaist.app

import android.app.Application
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GoWaistApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        cleanUpOldVersion()
    }

    /** Version 1.x had workout reminders and timers; remove their scheduled work and channels. */
    private fun cleanUpOldVersion() {
        runCatching { WorkManager.getInstance(this).cancelUniqueWork("daily_reminder") }
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            listOf("reminders", "timer", "timer_alerts").forEach { runCatching { nm?.deleteNotificationChannel(it) } }
        }
    }
}
