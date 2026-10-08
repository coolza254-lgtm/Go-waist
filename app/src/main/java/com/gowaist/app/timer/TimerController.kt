package com.gowaist.app.timer

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gowaist.app.R
import com.gowaist.app.data.settings.SettingsRepository
import com.gowaist.app.di.ApplicationScope
import com.gowaist.app.notify.Notifications
import com.gowaist.core.timer.IntervalProgram
import com.gowaist.core.timer.Phase
import com.gowaist.core.timer.PhaseKind
import com.gowaist.core.timer.TimerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** All clocks use [SystemClock.elapsedRealtime] so they survive wall-clock changes and doze. */
sealed interface TimerState {
    data object Idle : TimerState

    data class Rest(val endAt: Long, val totalSec: Int, val label: String) : TimerState {
        fun remainingMs(now: Long) = (endAt - now).coerceAtLeast(0)
    }

    data class Hold(val startAt: Long, val targetSec: Int?, val label: String, val entryId: Long) : TimerState {
        fun elapsedMs(now: Long) = (now - startAt).coerceAtLeast(0)
    }

    data class Interval(
        val config: TimerConfig,
        val phases: List<Phase>,
        val startAt: Long,
        val pausedAt: Long? = null,
        val pausedTotalMs: Long = 0,
        val finished: Boolean = false,
    ) : TimerState {
        fun elapsedMs(now: Long) = ((pausedAt ?: now) - startAt - pausedTotalMs).coerceAtLeast(0)
    }

    /** Guided running program (Cooper test, interval run) made of timed phases. */
    data class Program(
        val tag: String,
        val phases: List<Phase>,
        val startAt: Long,
        val wallStart: Long,
        val pausedAt: Long? = null,
        val pausedTotalMs: Long = 0,
        val finished: Boolean = false,
    ) : TimerState {
        fun elapsedMs(now: Long) = ((pausedAt ?: now) - startAt - pausedTotalMs).coerceAtLeast(0)
    }

    /** Count-up timer for free runs, with an optional target that triggers an alert. */
    data class Stopwatch(
        val tag: String,
        val startAt: Long,
        val wallStart: Long,
        val targetSec: Int? = null,
        val pausedAt: Long? = null,
        val pausedTotalMs: Long = 0,
    ) : TimerState {
        fun elapsedMs(now: Long) = ((pausedAt ?: now) - startAt - pausedTotalMs).coerceAtLeast(0)
    }
}

/**
 * Single source of truth for rest, hold and interval timers. A foreground service keeps the
 * process alive and shows the countdown while any timer runs, so timers keep working with the
 * screen off or the app in the background; this controller does the timing and alerts.
 */
@Singleton
class TimerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<TimerState>(TimerState.Idle)
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private val _now = MutableStateFlow(SystemClock.elapsedRealtime())
    /** Ticks ~4×/s while a timer is active; UIs derive remaining time from it. */
    val now: StateFlow<Long> = _now.asStateFlow()

    private var ticker: Job? = null
    private var lastPhaseIndex = -1
    private var holdTargetAlerted = false

    fun startRest(seconds: Int, label: String) {
        if (seconds <= 0) return
        Notifications.cancel(context, Notifications.ID_REST_DONE)
        set(TimerState.Rest(SystemClock.elapsedRealtime() + seconds * 1000L, seconds, label))
    }

    fun adjustRest(deltaSec: Int) {
        val s = _state.value as? TimerState.Rest ?: return
        val newEnd = (s.endAt + deltaSec * 1000L).coerceAtLeast(SystemClock.elapsedRealtime() + 1000)
        set(s.copy(endAt = newEnd, totalSec = (s.totalSec + deltaSec).coerceAtLeast(1)))
    }

    fun startHold(label: String, targetSec: Int?, entryId: Long) {
        holdTargetAlerted = false
        set(TimerState.Hold(SystemClock.elapsedRealtime(), targetSec, label, entryId))
    }

    /** Stops the hold timer and returns the held seconds. */
    fun stopHold(): Int {
        val s = _state.value as? TimerState.Hold ?: return 0
        val sec = (s.elapsedMs(SystemClock.elapsedRealtime()) / 1000).toInt()
        set(TimerState.Idle)
        return sec
    }

    fun startInterval(config: TimerConfig) {
        lastPhaseIndex = -1
        set(TimerState.Interval(config, IntervalProgram.phases(config), SystemClock.elapsedRealtime()))
    }

    fun startProgram(tag: String, phases: List<Phase>) {
        lastPhaseIndex = -1
        set(TimerState.Program(tag, phases, SystemClock.elapsedRealtime(), System.currentTimeMillis()))
    }

    fun startStopwatch(tag: String, targetSec: Int?) {
        holdTargetAlerted = false
        set(TimerState.Stopwatch(tag, SystemClock.elapsedRealtime(), System.currentTimeMillis(), targetSec))
    }

    /** Ends a stopwatch or program early and returns the active (unpaused) seconds. */
    fun finishActive(): Long {
        val now = SystemClock.elapsedRealtime()
        val sec = when (val s = _state.value) {
            is TimerState.Stopwatch -> s.elapsedMs(now) / 1000
            is TimerState.Program -> s.elapsedMs(now) / 1000
            else -> 0
        }
        set(TimerState.Idle)
        return sec
    }

    fun togglePause() {
        val now = SystemClock.elapsedRealtime()
        when (val s = _state.value) {
            is TimerState.Interval -> set(if (s.pausedAt == null) s.copy(pausedAt = now) else s.copy(pausedAt = null, pausedTotalMs = s.pausedTotalMs + (now - s.pausedAt)))
            is TimerState.Program -> set(if (s.pausedAt == null) s.copy(pausedAt = now) else s.copy(pausedAt = null, pausedTotalMs = s.pausedTotalMs + (now - s.pausedAt)))
            is TimerState.Stopwatch -> set(if (s.pausedAt == null) s.copy(pausedAt = now) else s.copy(pausedAt = null, pausedTotalMs = s.pausedTotalMs + (now - s.pausedAt)))
            else -> Unit
        }
    }

    fun stop() {
        set(TimerState.Idle)
    }

    private fun set(s: TimerState) {
        _state.value = s
        _now.value = SystemClock.elapsedRealtime()
        if (s is TimerState.Idle || (s is TimerState.Interval && s.finished) || (s is TimerState.Program && s.finished)) {
            ticker?.cancel()
            ticker = null
            context.stopService(Intent(context, WorkoutTimerService::class.java))
        } else {
            ContextCompat.startForegroundService(context, Intent(context, WorkoutTimerService::class.java))
            if (ticker?.isActive != true) ticker = scope.launch { tick() }
        }
    }

    private suspend fun tick() {
        while (scope.isActive) {
            val now = SystemClock.elapsedRealtime()
            _now.value = now
            when (val s = _state.value) {
                is TimerState.Rest -> if (now >= s.endAt) {
                    alert(long = true)
                    postRestDone(s.label)
                    set(TimerState.Idle)
                    return
                }
                is TimerState.Hold -> if (s.targetSec != null && !holdTargetAlerted && s.elapsedMs(now) >= s.targetSec * 1000L) {
                    holdTargetAlerted = true
                    alert(long = false)
                }
                is TimerState.Interval -> if (s.pausedAt == null && phaseTick(s.phases, s.elapsedMs(now))) {
                    set(s.copy(finished = true))
                    return
                }
                is TimerState.Program -> if (s.pausedAt == null && phaseTick(s.phases, s.elapsedMs(now))) {
                    set(s.copy(finished = true))
                    return
                }
                is TimerState.Stopwatch -> if (s.targetSec != null && !holdTargetAlerted && s.elapsedMs(now) >= s.targetSec * 1000L) {
                    holdTargetAlerted = true
                    alert(long = true)
                }
                TimerState.Idle -> return
            }
            delay(250)
        }
    }

    /** Alerts on phase changes and the last seconds of a phase; returns true when all phases are done. */
    private fun phaseTick(phases: List<Phase>, elapsedMs: Long): Boolean {
        val pos = IntervalProgram.locate(phases, elapsedMs)
        if (pos.finished) {
            alert(long = true)
            return true
        }
        if (pos.phaseIndex != lastPhaseIndex) {
            if (lastPhaseIndex >= 0) alert(long = pos.phase.kind == PhaseKind.WORK)
            lastPhaseIndex = pos.phaseIndex
        } else if (pos.remainingMs in 2_500..3_000 && pos.phase.durationSec > 5) {
            beep(short = true) // 3-2-1 warning
        } else if (pos.phase.durationSec >= 300 && pos.remainingMs in 59_750..60_000) {
            beep(short = true) // one minute left in long phases (Cooper test)
        }
        return false
    }

    private fun postRestDone(label: String) {
        val b = NotificationCompat.Builder(context, Notifications.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_gowaist)
            .setContentTitle(context.getString(R.string.timer_rest_done_title))
            .setContentText(context.getString(R.string.timer_rest_done_text, label))
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(Notifications.openAppIntent(context))
        Notifications.notify(context, Notifications.ID_REST_DONE, b)
    }

    private var lastBeep = 0L

    private fun beep(short: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBeep < 900) return
        lastBeep = now
        scope.launch {
            if (!settings.current().soundEnabled) return@launch
            runCatching {
                val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
                tg.startTone(ToneGenerator.TONE_PROP_BEEP, if (short) 120 else 450)
                delay(600)
                tg.release()
            }
        }
    }

    private fun alert(long: Boolean) {
        scope.launch {
            val s = settings.current()
            if (s.hapticsEnabled) vibrate(if (long) longArrayOf(0, 350, 150, 350) else longArrayOf(0, 120))
            if (s.soundEnabled) beep(short = !long)
        }
    }

    private fun vibrate(pattern: LongArray) {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}
