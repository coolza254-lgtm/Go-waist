package com.gowaist.app.timer

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.gowaist.app.R
import com.gowaist.app.notify.Notifications
import com.gowaist.core.Format
import com.gowaist.core.timer.IntervalProgram
import com.gowaist.core.timer.PhaseKind
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps workout timers alive while the screen is off or the app is in the background and shows
 * the live countdown in an ongoing notification.
 */
@AndroidEntryPoint
class WorkoutTimerService : LifecycleService() {

    @Inject lateinit var controller: TimerController

    private var observing = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notifications.ID_TIMER, build(controller.state.value), type)
        if (!observing) {
            observing = true
            lifecycleScope.launch {
                // Re-render at most once per second (the text only shows whole seconds).
                combine(controller.state, controller.now) { s, now -> s to now / 1000 }
                    .distinctUntilChanged()
                    .collect { (s, _) ->
                        if (s is TimerState.Idle) {
                            ServiceCompat.stopForeground(this@WorkoutTimerService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        } else {
                            Notifications.notify(this@WorkoutTimerService, Notifications.ID_TIMER, builder(s))
                        }
                    }
            }
        }
        return START_NOT_STICKY
    }

    private fun build(s: TimerState) = builder(s).build()

    private fun builder(s: TimerState): NotificationCompat.Builder {
        val now = SystemClock.elapsedRealtime()
        val b = NotificationCompat.Builder(this, Notifications.CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_stat_gowaist)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(Notifications.openAppIntent(this))
        when (s) {
            is TimerState.Rest -> {
                val rem = s.remainingMs(now)
                b.setContentTitle(getString(R.string.timer_rest_title, Format.duration((rem + 999) / 1000)))
                    .setContentText(getString(R.string.timer_rest_next, s.label))
                    .setProgress(s.totalSec * 1000, (s.totalSec * 1000 - rem).toInt().coerceAtLeast(0), false)
            }
            is TimerState.Hold -> {
                val el = s.elapsedMs(now) / 1000
                b.setContentTitle(getString(R.string.timer_hold_title, Format.duration(el)))
                    .setContentText(s.label)
            }
            is TimerState.Interval -> {
                val pos = IntervalProgram.locate(s.phases, s.elapsedMs(now))
                val phaseName = getString(
                    when (pos.phase.kind) {
                        PhaseKind.PREP -> R.string.timer_phase_prep
                        PhaseKind.WORK -> R.string.timer_phase_work
                        PhaseKind.REST -> R.string.timer_phase_rest
                    },
                )
                b.setContentTitle("$phaseName ${Format.duration((pos.remainingMs + 999) / 1000)}")
                    .setContentText(getString(R.string.timer_round, pos.phase.round.coerceAtLeast(1), pos.phase.totalRounds) + if (s.pausedAt != null) " · " + getString(R.string.timer_paused) else "")
            }
            TimerState.Idle -> b.setContentTitle(getString(R.string.app_name))
        }
        return b
    }
}
