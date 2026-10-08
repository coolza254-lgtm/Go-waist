package com.gowaist.app.notify

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gowaist.app.R
import com.gowaist.app.data.repo.PlanRepository
import com.gowaist.app.data.settings.SettingsRepository
import com.gowaist.app.widget.WidgetUpdater
import com.gowaist.app.ui.components.labelRes
import com.gowaist.core.Format
import com.gowaist.core.model.PlanDayStatus
import com.gowaist.core.model.PlanDayType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Daily reminder at the user's chosen time. Only nags when today has a planned workout that is
 * not done yet and the user has not switched reminders off for that day. Reschedules itself.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val plans: PlanRepository,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val s = settings.current()
        if (s.remindersEnabled) {
            plans.syncStatuses()
            val today = LocalDate.now()
            val day = plans.daysNow(today, today).firstOrNull {
                it.type != PlanDayType.REST && it.status == PlanDayStatus.PENDING && !it.reminderOff
            }
            if (day != null) {
                val ctx = applicationContext
                val what = ctx.getString(day.type.labelRes()) + (day.targetDistanceM?.let { " " + Format.decimal(it / 1000.0, 1) + " " + ctx.getString(R.string.unit_km) } ?: "")
                val b = NotificationCompat.Builder(ctx, Notifications.CHANNEL_REMINDERS)
                    .setSmallIcon(R.drawable.ic_stat_gowaist)
                    .setContentTitle(ctx.getString(R.string.reminder_title))
                    .setContentText(ctx.getString(R.string.reminder_text, what))
                    .setAutoCancel(true)
                    .setContentIntent(Notifications.openAppIntent(ctx))
                Notifications.notify(ctx, Notifications.ID_REMINDER, b)
            }
        }
        WidgetUpdater.update(applicationContext)
        schedule(applicationContext, s.reminderHour, s.reminderMinute)
        return Result.success()
    }


    companion object {
        private const val WORK = "daily_reminder"

        fun schedule(context: Context, hour: Int, minute: Int) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(hour, minute)
            if (!next.isAfter(now.plusMinutes(1))) next = next.plusDays(1)
            val delay = Duration.between(now, next).toMillis()
            val req = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, req)
        }
    }
}
