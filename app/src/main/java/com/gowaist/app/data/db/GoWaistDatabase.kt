package com.gowaist.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        RunEntity::class,
        ExerciseEntity::class,
        ChainEntity::class,
        ChainStepEntity::class,
        TemplateEntity::class,
        TemplateItemEntity::class,
        SessionEntity::class,
        SessionExerciseEntity::class,
        SetEntity::class,
        PlanEntity::class,
        PlanDayEntity::class,
        GoalEntity::class,
        RewardEntity::class,
        BadgeEntity::class,
        BodyMetricEntity::class,
        PersonalRecordEntity::class,
        SuggestionStateEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class GoWaistDatabase : RoomDatabase() {
    abstract fun runDao(): RunDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun chainDao(): ChainDao
    abstract fun templateDao(): TemplateDao
    abstract fun sessionDao(): SessionDao
    abstract fun planDao(): PlanDao
    abstract fun goalDao(): GoalDao
    abstract fun bodyDao(): BodyDao
    abstract fun recordDao(): RecordDao
    abstract fun suggestionDao(): SuggestionDao
}
