package com.gowaist.app.data.repo

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.gowaist.app.data.db.BodyMetricEntity
import com.gowaist.app.data.db.GoWaistDatabase
import com.gowaist.app.data.db.GoalEntity
import com.gowaist.app.data.db.PersonalRecordEntity
import com.gowaist.app.data.db.RunEntity
import com.gowaist.app.data.db.ShoeEntity
import com.gowaist.app.data.settings.AppSettings
import com.gowaist.app.data.settings.SettingsRepository
import com.gowaist.app.data.toLocalDateTime
import com.gowaist.core.Format
import com.gowaist.core.model.GoalType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything the app stores, in one versioned JSON document. Version 1 files (with workout data)
 * still import: their extra fields are ignored and only running and body data is kept.
 */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val settings: AppSettings = AppSettings(),
    val runs: List<RunEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val bodyMetrics: List<BodyMetricEntity> = emptyList(),
    val records: List<PersonalRecordEntity> = emptyList(),
    val shoes: List<ShoeEntity> = emptyList(),
) {
    companion object {
        const val FORMAT = "gowaist-backup"
        const val CURRENT_VERSION = 2
    }
}

class BackupException(message: String) : Exception(message)

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: GoWaistDatabase,
    private val settings: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    suspend fun snapshot(): BackupFile = db.withTransaction {
        BackupFile(
            settings = settings.current(),
            runs = db.runDao().getAll(),
            goals = db.goalDao().getAll(),
            bodyMetrics = db.bodyDao().getAll(),
            records = db.recordDao().getAll(),
            shoes = db.shoeDao().getAll(),
        )
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun decode(text: String): BackupFile {
        val file = runCatching { json.decodeFromString(BackupFile.serializer(), text) }
            .getOrElse { throw BackupException("ไฟล์ไม่ใช่ข้อมูลสำรองของ Go waist") }
        if (file.format != BackupFile.FORMAT) throw BackupException("ไฟล์ไม่ใช่ข้อมูลสำรองของ Go waist")
        if (file.version > BackupFile.CURRENT_VERSION) throw BackupException("ไฟล์มาจากแอปเวอร์ชันใหม่กว่า กรุณาอัปเดตแอปก่อน")
        return file
    }

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val text = encode(snapshot())
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: throw BackupException("เขียนไฟล์ไม่ได้")
    }

    suspend fun importFrom(uri: Uri): BackupFile = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw BackupException("อ่านไฟล์ไม่ได้")
        val file = decode(text)
        restore(file)
        file
    }

    /**
     * Replaces all data with the backup content in a single transaction (all or nothing).
     * The current data is first saved to app storage as a safety copy.
     */
    suspend fun restore(file: BackupFile) = withContext(Dispatchers.IO) {
        runCatching { File(context.filesDir, "before_restore.json").writeText(encode(snapshot())) }
        db.withTransaction {
            deleteAllRows()
            db.shoeDao().insertAll(file.shoes.map { sh -> sh.copy(imagePath = sh.imagePath?.takeIf { File(it).exists() }) })
            val shoeIds = file.shoes.map { it.id }.toSet()
            db.runDao().insertAll(
                file.runs.map { r -> r.copy(sourceImagePath = r.sourceImagePath?.takeIf { File(it).exists() }, shoeId = r.shoeId?.takeIf { it in shoeIds }) },
            )
            db.goalDao().insertAllGoals(file.goals.filter { it.type !in WORKOUT_GOALS })
            db.bodyDao().insertAll(file.bodyMetrics)
            val runIds = file.runs.map { it.id }.toSet()
            db.recordDao().replaceAll(file.records.filter { it.runId != null && it.runId in runIds })
        }
        settings.replace(file.settings.copy(onboardingDone = true))
    }

    /** Deletes every record and stored image. */
    suspend fun wipeAll() = withContext(Dispatchers.IO) {
        db.withTransaction { deleteAllRows() }
        File(context.filesDir, "run_images").deleteRecursively()
    }

    private fun deleteAllRows() {
        val sql = db.openHelper.writableDatabase
        listOf("personal_records", "goals", "runs", "body_metrics", "shoes").forEach { sql.execSQL("DELETE FROM `$it`") }
    }

    // ------------------------------------------------------------------ CSV

    private fun csv(vararg values: Any?): String = values.joinToString(",") { v ->
        val s = v?.toString() ?: ""
        if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    suspend fun runsCsv(): String {
        val dateFmt = DateTimeFormatter.ofPattern("HH:mm")
        val sb = StringBuilder("﻿")
        sb.appendLine(csv("date", "start_time", "distance_km", "duration", "duration_sec", "pace_per_km", "calories", "avg_hr", "shoe", "tags", "note"))
        val shoes = db.shoeDao().getAll().associate { it.id to it.name }
        db.runDao().getAll().sortedBy { it.startAt }.forEach { r ->
            sb.appendLine(
                csv(
                    r.localDate, r.startAt.toLocalDateTime().format(dateFmt), Format.decimal(r.distanceM / 1000.0, 3),
                    Format.duration(r.durationSec), r.durationSec, Format.pace(r.avgPaceSecPerKm), r.calories, r.avgHr,
                    r.shoeId?.let { shoes[it] }, r.tags.joinToString("|"), r.note,
                ),
            )
        }
        return sb.toString()
    }

    suspend fun writeText(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: throw BackupException("เขียนไฟล์ไม่ได้")
    }
}

private val WORKOUT_GOALS = setOf(GoalType.BW_SESSIONS, GoalType.EXERCISE_MAX_REPS, GoalType.EXERCISE_HOLD, GoalType.EXERCISE_TOTAL_REPS)
