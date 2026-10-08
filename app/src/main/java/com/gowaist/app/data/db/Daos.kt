package com.gowaist.app.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {
    @Query("SELECT * FROM runs ORDER BY startAt DESC")
    fun observeAll(): Flow<List<RunEntity>>

    @Query("SELECT * FROM runs ORDER BY startAt DESC")
    suspend fun getAll(): List<RunEntity>

    @Query("SELECT * FROM runs WHERE id = :id")
    fun observe(id: Long): Flow<RunEntity?>

    @Query("SELECT * FROM runs WHERE id = :id")
    suspend fun get(id: Long): RunEntity?

    @Query("SELECT * FROM runs WHERE localDate BETWEEN :from AND :to ORDER BY startAt")
    suspend fun inRange(from: String, to: String): List<RunEntity>

    @Insert
    suspend fun insert(run: RunEntity): Long

    @Update
    suspend fun update(run: RunEntity)

    @Query("DELETE FROM runs WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RunEntity>)
}

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY difficulty, nameTh")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises")
    suspend fun getAll(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observe(id: Long): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun get(id: Long): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE seedKey = :key")
    suspend fun bySeedKey(key: String): ExerciseEntity?

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Insert
    suspend fun insert(item: ExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ExerciseEntity>): List<Long>

    @Update
    suspend fun update(item: ExerciseEntity)

    @Query("UPDATE exercises SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("UPDATE exercises SET isHidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)
}

data class ChainWithSteps(
    @Embedded val chain: ChainEntity,
    @Relation(parentColumn = "id", entityColumn = "chainId") val steps: List<ChainStepEntity>,
)

@Dao
interface ChainDao {
    @Transaction
    @Query("SELECT * FROM chains ORDER BY id")
    fun observeAll(): Flow<List<ChainWithSteps>>

    @Transaction
    @Query("SELECT * FROM chains ORDER BY id")
    suspend fun getAll(): List<ChainWithSteps>

    @Query("SELECT * FROM chain_steps")
    suspend fun allSteps(): List<ChainStepEntity>

    @Insert
    suspend fun insert(chain: ChainEntity): Long

    @Update
    suspend fun update(chain: ChainEntity)

    @Query("UPDATE chains SET currentStep = :step WHERE id = :id")
    suspend fun setCurrentStep(id: Long, step: Int)

    @Query("DELETE FROM chains WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertSteps(steps: List<ChainStepEntity>)

    @Update
    suspend fun updateStep(step: ChainStepEntity)

    @Query("DELETE FROM chain_steps WHERE chainId = :chainId")
    suspend fun deleteSteps(chainId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllChains(items: List<ChainEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSteps(items: List<ChainStepEntity>)
}

data class TemplateWithItems(
    @Embedded val template: TemplateEntity,
    @Relation(parentColumn = "id", entityColumn = "templateId") val items: List<TemplateItemEntity>,
)

@Dao
interface TemplateDao {
    @Transaction
    @Query("SELECT * FROM templates ORDER BY isCustom DESC, id")
    fun observeAll(): Flow<List<TemplateWithItems>>

    @Transaction
    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun get(id: Long): TemplateWithItems?

    @Transaction
    @Query("SELECT * FROM templates WHERE id = :id")
    fun observe(id: Long): Flow<TemplateWithItems?>

    @Query("SELECT * FROM templates WHERE seedKey = :key")
    suspend fun bySeedKey(key: String): TemplateEntity?

    @Query("SELECT * FROM templates")
    suspend fun getAllTemplates(): List<TemplateEntity>

    @Query("SELECT * FROM template_items")
    suspend fun getAllItems(): List<TemplateItemEntity>

    @Insert
    suspend fun insert(t: TemplateEntity): Long

    @Update
    suspend fun update(t: TemplateEntity)

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertItems(items: List<TemplateItemEntity>)

    @Query("DELETE FROM template_items WHERE templateId = :templateId")
    suspend fun deleteItems(templateId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTemplates(items: List<TemplateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllItems(items: List<TemplateItemEntity>)
}

/** A logged set joined with its exercise, session and day — the base row for all statistics. */
data class SetRow(
    @Embedded val set: SetEntity,
    val exerciseId: Long,
    val sessionId: Long,
    val localDate: String,
)

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(s: SessionEntity): Long

    @Update
    suspend fun update(s: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observe(id: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE isDraft = 1 ORDER BY startAt DESC LIMIT 1")
    fun observeDraft(): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE isDraft = 1 ORDER BY startAt DESC LIMIT 1")
    suspend fun getDraft(): SessionEntity?

    @Query("SELECT * FROM sessions WHERE isDraft = 0 ORDER BY startAt DESC")
    fun observeFinished(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions")
    suspend fun getAll(): List<SessionEntity>

    @Insert
    suspend fun insertExercise(e: SessionExerciseEntity): Long

    @Update
    suspend fun updateExercise(e: SessionExerciseEntity)

    @Update
    suspend fun updateExercises(e: List<SessionExerciseEntity>)

    @Query("DELETE FROM session_exercises WHERE id = :id")
    suspend fun deleteExercise(id: Long)

    @Query("SELECT * FROM session_exercises WHERE sessionId = :sessionId ORDER BY itemOrder")
    fun observeExercises(sessionId: Long): Flow<List<SessionExerciseEntity>>

    @Query("SELECT * FROM session_exercises WHERE sessionId = :sessionId ORDER BY itemOrder")
    suspend fun getExercises(sessionId: Long): List<SessionExerciseEntity>

    @Query("SELECT * FROM session_exercises")
    suspend fun getAllExercises(): List<SessionExerciseEntity>

    @Insert
    suspend fun insertSet(s: SetEntity): Long

    @Update
    suspend fun updateSet(s: SetEntity)

    @Query("DELETE FROM sets WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("SELECT * FROM sets WHERE sessionExerciseId IN (SELECT id FROM session_exercises WHERE sessionId = :sessionId) ORDER BY setOrder")
    fun observeSets(sessionId: Long): Flow<List<SetEntity>>

    @Query("SELECT * FROM sets")
    suspend fun getAllSets(): List<SetEntity>

    @Query(
        """
        SELECT sets.*, se.exerciseId AS exerciseId, se.sessionId AS sessionId, ss.localDate AS localDate
        FROM sets JOIN session_exercises se ON sets.sessionExerciseId = se.id
        JOIN sessions ss ON se.sessionId = ss.id
        WHERE ss.isDraft = 0
        ORDER BY ss.startAt, se.itemOrder, sets.setOrder
        """,
    )
    fun observeFinishedSetRows(): Flow<List<SetRow>>

    @Query(
        """
        SELECT sets.*, se.exerciseId AS exerciseId, se.sessionId AS sessionId, ss.localDate AS localDate
        FROM sets JOIN session_exercises se ON sets.sessionExerciseId = se.id
        JOIN sessions ss ON se.sessionId = ss.id
        WHERE ss.isDraft = 0
        ORDER BY ss.startAt, se.itemOrder, sets.setOrder
        """,
    )
    suspend fun finishedSetRows(): List<SetRow>

    @Query(
        """
        SELECT sets.*, se.exerciseId AS exerciseId, se.sessionId AS sessionId, ss.localDate AS localDate
        FROM sets JOIN session_exercises se ON sets.sessionExerciseId = se.id
        JOIN sessions ss ON se.sessionId = ss.id
        WHERE se.sessionId = :sessionId
        ORDER BY se.itemOrder, sets.setOrder
        """,
    )
    suspend fun setRowsForSession(sessionId: Long): List<SetRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSessions(items: List<SessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllExercises(items: List<SessionExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSets(items: List<SetEntity>)
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM plans WHERE isActive = 1 ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM plans ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM plans")
    suspend fun getAll(): List<PlanEntity>

    @Query("SELECT * FROM plans WHERE id = :id")
    suspend fun get(id: Long): PlanEntity?

    @Insert
    suspend fun insert(p: PlanEntity): Long

    @Update
    suspend fun update(p: PlanEntity)

    @Query("DELETE FROM plans WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertDays(days: List<PlanDayEntity>)

    @Update
    suspend fun updateDay(day: PlanDayEntity)

    @Update
    suspend fun updateDays(days: List<PlanDayEntity>)

    @Query("DELETE FROM plan_days WHERE id = :id")
    suspend fun deleteDay(id: Long)

    @Query("SELECT * FROM plan_days WHERE id = :id")
    suspend fun getDay(id: Long): PlanDayEntity?

    @Query(
        """
        SELECT d.* FROM plan_days d JOIN plans p ON d.planId = p.id
        WHERE p.isActive = 1 AND d.date BETWEEN :from AND :to ORDER BY d.date
        """,
    )
    fun observeActiveDays(from: String, to: String): Flow<List<PlanDayEntity>>

    @Query(
        """
        SELECT d.* FROM plan_days d JOIN plans p ON d.planId = p.id
        WHERE p.isActive = 1 AND d.date BETWEEN :from AND :to ORDER BY d.date
        """,
    )
    suspend fun activeDays(from: String, to: String): List<PlanDayEntity>

    @Query("SELECT * FROM plan_days WHERE planId = :planId ORDER BY date")
    suspend fun daysOf(planId: Long): List<PlanDayEntity>

    @Query("SELECT * FROM plan_days")
    suspend fun getAllDays(): List<PlanDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPlans(items: List<PlanEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllDays(items: List<PlanDayEntity>)
}

data class GoalWithReward(
    @Embedded val goal: GoalEntity,
    @Relation(parentColumn = "id", entityColumn = "goalId") val rewards: List<RewardEntity>,
) {
    val reward: RewardEntity? get() = rewards.firstOrNull()
}

@Dao
interface GoalDao {
    @Transaction
    @Query("SELECT * FROM goals ORDER BY CASE status WHEN 'ACTIVE' THEN 0 ELSE 1 END, createdAt DESC")
    fun observeAll(): Flow<List<GoalWithReward>>

    @Query("SELECT * FROM goals")
    suspend fun getAll(): List<GoalEntity>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun get(id: Long): GoalEntity?

    @Insert
    suspend fun insert(g: GoalEntity): Long

    @Update
    suspend fun update(g: GoalEntity)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertReward(r: RewardEntity): Long

    @Update
    suspend fun updateReward(r: RewardEntity)

    @Query("SELECT * FROM rewards WHERE goalId = :goalId LIMIT 1")
    suspend fun rewardFor(goalId: Long): RewardEntity?

    @Query("SELECT * FROM rewards")
    suspend fun getAllRewards(): List<RewardEntity>

    @Query("SELECT * FROM rewards ORDER BY unlockedAt DESC")
    fun observeRewards(): Flow<List<RewardEntity>>

    @Query("SELECT * FROM badges ORDER BY unlockedAt DESC")
    fun observeBadges(): Flow<List<BadgeEntity>>

    @Query("SELECT * FROM badges")
    suspend fun getBadges(): List<BadgeEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBadge(b: BadgeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllGoals(items: List<GoalEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllRewards(items: List<RewardEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllBadges(items: List<BadgeEntity>)
}

@Dao
interface BodyDao {
    @Query("SELECT * FROM body_metrics ORDER BY date DESC")
    fun observeAll(): Flow<List<BodyMetricEntity>>

    @Query("SELECT * FROM body_metrics ORDER BY date DESC")
    suspend fun getAll(): List<BodyMetricEntity>

    @Query("SELECT * FROM body_metrics WHERE date = :date")
    suspend fun byDate(date: String): BodyMetricEntity?

    @Query("SELECT * FROM body_metrics WHERE weightKg IS NOT NULL ORDER BY date DESC LIMIT 1")
    fun observeLatestWeight(): Flow<BodyMetricEntity?>

    @Upsert
    suspend fun upsert(m: BodyMetricEntity): Long

    @Query("DELETE FROM body_metrics WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<BodyMetricEntity>)
}

@Dao
interface RecordDao {
    @Query("SELECT * FROM personal_records ORDER BY achievedAt DESC")
    fun observeAll(): Flow<List<PersonalRecordEntity>>

    @Query("SELECT * FROM personal_records")
    suspend fun getAll(): List<PersonalRecordEntity>

    @Insert
    suspend fun insertAll(items: List<PersonalRecordEntity>)

    @Query("DELETE FROM personal_records WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)

    @Query("DELETE FROM personal_records WHERE runId = :runId")
    suspend fun deleteForRun(runId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAll(items: List<PersonalRecordEntity>)
}

@Dao
interface SuggestionDao {
    @Query("SELECT * FROM suggestion_states")
    fun observeAll(): Flow<List<SuggestionStateEntity>>

    @Query("SELECT * FROM suggestion_states")
    suspend fun getAll(): List<SuggestionStateEntity>

    @Upsert
    suspend fun upsert(s: SuggestionStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SuggestionStateEntity>)
}
