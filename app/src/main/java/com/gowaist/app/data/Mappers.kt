package com.gowaist.app.data

import com.gowaist.app.data.db.ExerciseEntity
import com.gowaist.app.data.db.PlanDayEntity
import com.gowaist.app.data.db.RunEntity
import com.gowaist.app.data.db.SetRow
import com.gowaist.core.plan.PlannedDay
import com.gowaist.core.run.RunFacts
import com.gowaist.core.stats.ExerciseFacts
import com.gowaist.core.stats.SetFacts
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

fun String.toLocalDate(): LocalDate = LocalDate.parse(this)

fun LocalDate.key(): String = toString()

fun today(): LocalDate = LocalDate.now()

fun Long.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())

fun LocalDateTime.toEpochMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun RunEntity.toFacts() = RunFacts(id, startAt, localDate.toLocalDate(), distanceM, durationSec)

fun ExerciseEntity.toFacts() = ExerciseFacts(id, pattern, primaryMuscles, secondaryMuscles, trackingType, bodyweightFactor)

fun SetRow.toFacts() = SetFacts(
    exerciseId = exerciseId,
    sessionId = sessionId,
    date = localDate.toLocalDate(),
    setType = set.setType,
    reps = set.reps,
    durationSec = set.durationSec,
    addedKg = set.addedWeightKg,
    assistKg = set.assistKg,
    rpe = set.rpe,
    distanceM = set.distanceM,
)

fun PlanDayEntity.toPlanned() = PlannedDay(id, date.toLocalDate(), type, targetDistanceM, targetDurationMin, targetPaceSecPerKm)
