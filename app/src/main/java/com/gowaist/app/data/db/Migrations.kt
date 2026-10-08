package com.gowaist.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema change gets an explicit migration so user data is never wiped on update.
 * Exported schemas live in app/schemas and are checked by MigrationTest.
 */
object Migrations {

    /** v2: remember accepted / rejected / snoozed progression and plan suggestions. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `suggestion_states` (`key` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                    "`snoozeUntil` INTEGER, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
