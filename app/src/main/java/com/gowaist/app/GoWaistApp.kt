package com.gowaist.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.gowaist.app.data.Seeder
import com.gowaist.app.data.settings.SettingsRepository
import com.gowaist.app.di.ApplicationScope
import com.gowaist.app.notify.Notifications
import com.gowaist.app.notify.ReminderWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GoWaistApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var seeder: Seeder
    @Inject lateinit var settings: SettingsRepository

    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        appScope.launch {
            seeder.ensureSeeded()
            val s = settings.current()
            ReminderWorker.schedule(this@GoWaistApp, s.reminderHour, s.reminderMinute)
        }
    }
}
