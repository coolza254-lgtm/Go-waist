package com.gowaist.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gowaist.core.model.Equipment
import com.gowaist.core.model.GoalPeriod
import com.gowaist.core.model.GoalStatus
import com.gowaist.core.model.GoalType
import com.gowaist.core.model.MovementPattern
import com.gowaist.core.model.Muscle
import com.gowaist.core.model.PlanDayStatus
import com.gowaist.core.model.PlanDayType
import com.gowaist.core.model.PrType
import com.gowaist.core.model.SetType
import com.gowaist.core.model.TrackingType
import com.gowaist.core.perf.RunType
import kotlinx.serialization.Serializable

/*
 * Dates that matter to the user ("which day did I train") are stored as ISO local dates
 * (yyyy-MM-dd) captured when the record is made, so statistics do not shift when the phone's
 * time zone changes. Exact instants are kept separately as epoch millis.
 */

@Serializable
@Entity(tableName = "runs", indices = [Index("localDate"), Index("startAt")])
data class RunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startAt: Long,
    val localDate: String,
    val distanceM: Double,
    val durationSec: Long,
    val avgPaceSecPerKm: Double?,
    val calories: Int? = null,
    val avgHr: Int? = null,
    val note: String = "",
    val tags: List<String> = emptyList(),
    val sourceImagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Program that recorded the run (v3). */
    @ColumnInfo(defaultValue = "FREE") val runType: RunType = RunType.FREE,
    /** Perceived effort 1–10 (v3). */
    val rpe: Double? = null,
    /** Interval session details as JSON [com.gowaist.core.perf.IntervalResult] (v3). */
    val intervalJson: String? = null,
)

@Serializable
@Entity(tableName = "exercises", indices = [Index(value = ["seedKey"], unique = true)])
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable key of built-in exercises; null for user-created ones. */
    val seedKey: String? = null,
    val nameTh: String,
    val nameEn: String,
    val pattern: MovementPattern,
    val primaryMuscles: Set<Muscle>,
    val secondaryMuscles: Set<Muscle>,
    val trackingType: TrackingType,
    val difficulty: Int,
    val description: String = "",
    val caution: String = "",
    val equipment: Set<Equipment> = setOf(Equipment.NONE),
    val bodyweightFactor: Double = 0.65,
    val defaultRestSec: Int = 90,
    val isCustom: Boolean = false,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
)

@Serializable
@Entity(tableName = "chains", indices = [Index(value = ["seedKey"], unique = true)])
data class ChainEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seedKey: String? = null,
    val name: String,
    /** stepOrder of the step the user is currently training. */
    val currentStep: Int = 0,
    val isCustom: Boolean = false,
)

@Serializable
@Entity(
    tableName = "chain_steps",
    foreignKeys = [
        ForeignKey(entity = ChainEntity::class, parentColumns = ["id"], childColumns = ["chainId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("chainId"), Index("exerciseId")],
)
data class ChainStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chainId: Long,
    val exerciseId: Long,
    val stepOrder: Int,
    /** Promotion rule: [targetSets] × [targetRepsOrSec] in [sessionsRequired] sessions in a row. */
    val targetSets: Int = 3,
    val targetRepsOrSec: Int = 12,
    val sessionsRequired: Int = 2,
)

@Serializable
@Entity(tableName = "templates", indices = [Index(value = ["seedKey"], unique = true)])
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seedKey: String? = null,
    val name: String,
    val description: String = "",
    val isCustom: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "template_items",
    foreignKeys = [
        ForeignKey(entity = TemplateEntity::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("templateId"), Index("exerciseId")],
)
data class TemplateItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val exerciseId: Long,
    val itemOrder: Int,
    val targetSets: Int,
    val targetRepsOrSec: Int,
    val restSec: Int,
    val supersetGroup: Int? = null,
)

@Serializable
@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(entity = TemplateEntity::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = PlanDayEntity::class, parentColumns = ["id"], childColumns = ["planDayId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("localDate"), Index("templateId"), Index("planDayId"), Index("isDraft")],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startAt: Long,
    val endAt: Long? = null,
    val localDate: String,
    val name: String = "",
    val templateId: Long? = null,
    val planDayId: Long? = null,
    /** 1–5 overall feeling. */
    val feeling: Int? = null,
    val note: String = "",
    val isDraft: Boolean = true,
    /** EMOM / AMRAP / TABATA / CUSTOM when the session was run with an interval timer. */
    val timerMode: String? = null,
    /** e.g. rounds completed in an AMRAP. */
    val timerResult: String? = null,
)

@Serializable
@Entity(
    tableName = "session_exercises",
    foreignKeys = [
        ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SessionExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val itemOrder: Int,
    val supersetGroup: Int? = null,
    val restSec: Int = 90,
    val targetSets: Int? = null,
    val targetRepsOrSec: Int? = null,
    val note: String = "",
)

@Serializable
@Entity(
    tableName = "sets",
    foreignKeys = [
        ForeignKey(entity = SessionExerciseEntity::class, parentColumns = ["id"], childColumns = ["sessionExerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("sessionExerciseId")],
)
data class SetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionExerciseId: Long,
    val setOrder: Int,
    val setType: SetType = SetType.NORMAL,
    val reps: Int? = null,
    val durationSec: Int? = null,
    val addedWeightKg: Double? = null,
    val assistKg: Double? = null,
    val distanceM: Double? = null,
    val rpe: Double? = null,
    val rir: Int? = null,
    val note: String = "",
    val completedAt: Long = System.currentTimeMillis(),
)

@Serializable
enum class PlanMode { PREBUILT, CUSTOM, ADAPTIVE }

@Serializable
@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mode: PlanMode,
    val sourceKey: String? = null,
    /** Monday of the first week. */
    val startDate: String,
    val weeks: Int,
    val isActive: Boolean = true,
    val targetPaceSecPerKm: Double? = null,
    /** Custom weekly pattern as JSON (list of 7 day specs), used to extend custom plans. */
    val weeklyPattern: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "plan_days",
    foreignKeys = [
        ForeignKey(entity = PlanEntity::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TemplateEntity::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("planId"), Index("date"), Index("templateId")],
)
data class PlanDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val date: String,
    val type: PlanDayType,
    val targetDistanceM: Double? = null,
    val targetDurationMin: Int? = null,
    val templateId: Long? = null,
    val targetPaceSecPerKm: Double? = null,
    val note: String = "",
    /** Last computed status (kept for the widget and history; refreshed from real activity). */
    val status: PlanDayStatus = PlanDayStatus.PENDING,
    val reminderOff: Boolean = false,
)

@Serializable
@Entity(
    tableName = "goals",
    foreignKeys = [
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("exerciseId"), Index("status")],
)
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: GoalType,
    val title: String,
    val target: Double,
    val exerciseId: Long? = null,
    val period: GoalPeriod,
    val startDate: String,
    val endDate: String,
    val baseline: Double? = null,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val progress: Double = 0.0,
    val achievedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "rewards",
    foreignKeys = [ForeignKey(entity = GoalEntity::class, parentColumns = ["id"], childColumns = ["goalId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("goalId")],
)
data class RewardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val text: String,
    val unlockedAt: Long? = null,
    val claimedAt: Long? = null,
)

@Serializable
@Entity(tableName = "badges")
data class BadgeEntity(
    @PrimaryKey val key: String,
    val unlockedAt: Long,
)

@Serializable
@Entity(tableName = "body_metrics", indices = [Index(value = ["date"], unique = true)])
data class BodyMetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val weightKg: Double? = null,
    val waistCm: Double? = null,
    val hipCm: Double? = null,
    val chestCm: Double? = null,
    val armCm: Double? = null,
    val thighCm: Double? = null,
    val note: String = "",
)

@Serializable
@Entity(
    tableName = "personal_records",
    foreignKeys = [
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = RunEntity::class, parentColumns = ["id"], childColumns = ["runId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("exerciseId"), Index("sessionId"), Index("runId"), Index("achievedAt")],
)
data class PersonalRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long? = null,
    val type: PrType,
    val value: Double,
    val previous: Double? = null,
    val achievedAt: Long,
    val localDate: String,
    val sessionId: Long? = null,
    val runId: Long? = null,
)

@Serializable
enum class SuggestionStatus { ACCEPTED, REJECTED, SNOOZED }

/** Remembers the user's answer to a rule-based suggestion (added in schema version 2). */
@Serializable
@Entity(tableName = "suggestion_states")
data class SuggestionStateEntity(
    @PrimaryKey val key: String,
    val status: SuggestionStatus,
    val snoozeUntil: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)
