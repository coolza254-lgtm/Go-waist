package com.gowaist.app.data

import com.gowaist.app.data.db.RunEntity
import com.gowaist.core.run.RunFacts
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
