package com.gowaist.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("UPDATE runs SET shoeId = NULL WHERE shoeId = :shoeId")
    suspend fun clearShoe(shoeId: Long)

    @Query("SELECT COUNT(*) FROM runs")
    suspend fun count(): Int
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY CASE status WHEN 'ACTIVE' THEN 0 ELSE 1 END, createdAt DESC")
    fun observeAll(): Flow<List<GoalEntity>>

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllGoals(items: List<GoalEntity>)
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

    @Query("DELETE FROM personal_records WHERE runId = :runId")
    suspend fun deleteForRun(runId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAll(items: List<PersonalRecordEntity>)
}

@Dao
interface ShoeDao {
    @Query("SELECT * FROM shoes ORDER BY retired, isMain DESC, createdAt DESC")
    fun observeAll(): Flow<List<ShoeEntity>>

    @Query("SELECT * FROM shoes")
    suspend fun getAll(): List<ShoeEntity>

    @Query("SELECT * FROM shoes WHERE id = :id")
    suspend fun get(id: Long): ShoeEntity?

    @Query("SELECT * FROM shoes WHERE isMain = 1 AND retired = 0 LIMIT 1")
    suspend fun main(): ShoeEntity?

    @Insert
    suspend fun insert(s: ShoeEntity): Long

    @Update
    suspend fun update(s: ShoeEntity)

    @Query("UPDATE shoes SET isMain = (id = :id)")
    suspend fun setMain(id: Long)

    @Query("DELETE FROM shoes WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ShoeEntity>)
}
