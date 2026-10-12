package com.gowaist.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gowaist.core.model.GoalPeriod
import com.gowaist.core.model.GoalStatus
import com.gowaist.core.model.GoalType
import com.gowaist.core.model.PrType
import com.gowaist.core.perf.RunType
import kotlinx.serialization.Serializable

/*
 * Dates that matter to the user ("which day did I run") are stored as ISO local dates
 * (yyyy-MM-dd) captured when the record is made, so statistics do not shift when the phone's
 * time zone changes. Exact instants are kept separately as epoch millis.
 */

@Serializable
@Entity(tableName = "runs", indices = [Index("localDate"), Index("startAt"), Index("shoeId")])
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
    /** Shoe worn on this run (v4); null = not recorded. */
    val shoeId: Long? = null,
)

@Serializable
@Entity(tableName = "goals", indices = [Index("status")])
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: GoalType,
    val title: String,
    val target: Double,
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
    foreignKeys = [ForeignKey(entity = RunEntity::class, parentColumns = ["id"], childColumns = ["runId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("runId"), Index("achievedAt")],
)
data class PersonalRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: PrType,
    val value: Double,
    val previous: Double? = null,
    val achievedAt: Long,
    val localDate: String,
    val runId: Long? = null,
)

/** A pair of running shoes (v4). Durability is the user's own estimate in percent. */
@Serializable
@Entity(tableName = "shoes")
data class ShoeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val priceBaht: Double? = null,
    /** ISO local date of purchase. */
    val purchaseDate: String? = null,
    val imagePath: String? = null,
    val durabilityPct: Int? = null,
    /** New imported runs are assigned to the main pair. */
    val isMain: Boolean = false,
    val retired: Boolean = false,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
