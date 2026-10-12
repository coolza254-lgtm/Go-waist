package com.gowaist.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        RunEntity::class,
        GoalEntity::class,
        BodyMetricEntity::class,
        PersonalRecordEntity::class,
        ShoeEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class GoWaistDatabase : RoomDatabase() {
    abstract fun runDao(): RunDao
    abstract fun goalDao(): GoalDao
    abstract fun bodyDao(): BodyDao
    abstract fun recordDao(): RecordDao
    abstract fun shoeDao(): ShoeDao
}
