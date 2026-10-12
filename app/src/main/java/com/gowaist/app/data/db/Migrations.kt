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

    /** v3: run programs (Cooper, interval, free, long run) with effort and interval details. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `runs` ADD COLUMN `runType` TEXT NOT NULL DEFAULT 'FREE'")
            db.execSQL("ALTER TABLE `runs` ADD COLUMN `rpe` REAL")
            db.execSQL("ALTER TABLE `runs` ADD COLUMN `intervalJson` TEXT")
        }
    }

    /**
     * v4: the app becomes a running tracker. Workout, plan, reward, badge and suggestion data are
     * removed, goals and records keep only running/body entries, and shoes are added.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            listOf(
                "rewards", "badges", "suggestion_states", "plan_days", "plans", "sets", "session_exercises",
                "sessions", "template_items", "templates", "chain_steps", "chains",
            ).forEach { db.execSQL("DROP TABLE IF EXISTS `$it`") }

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `goals_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, `target` REAL NOT NULL, `period` TEXT NOT NULL, `startDate` TEXT NOT NULL, " +
                    "`endDate` TEXT NOT NULL, `baseline` REAL, `status` TEXT NOT NULL, `progress` REAL NOT NULL, " +
                    "`achievedAt` INTEGER, `createdAt` INTEGER NOT NULL)",
            )
            db.execSQL(
                "INSERT INTO `goals_new` SELECT `id`, `type`, `title`, `target`, `period`, `startDate`, `endDate`, `baseline`, " +
                    "`status`, `progress`, `achievedAt`, `createdAt` FROM `goals` " +
                    "WHERE `type` NOT IN ('BW_SESSIONS', 'EXERCISE_MAX_REPS', 'EXERCISE_HOLD', 'EXERCISE_TOTAL_REPS')",
            )
            db.execSQL("DROP TABLE `goals`")
            db.execSQL("ALTER TABLE `goals_new` RENAME TO `goals`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_goals_status` ON `goals` (`status`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `personal_records_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`type` TEXT NOT NULL, `value` REAL NOT NULL, `previous` REAL, `achievedAt` INTEGER NOT NULL, " +
                    "`localDate` TEXT NOT NULL, `runId` INTEGER, " +
                    "FOREIGN KEY(`runId`) REFERENCES `runs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "INSERT INTO `personal_records_new` SELECT `id`, `type`, `value`, `previous`, `achievedAt`, `localDate`, `runId` " +
                    "FROM `personal_records` WHERE `runId` IS NOT NULL",
            )
            db.execSQL("DROP TABLE `personal_records`")
            db.execSQL("ALTER TABLE `personal_records_new` RENAME TO `personal_records`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_personal_records_runId` ON `personal_records` (`runId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_personal_records_achievedAt` ON `personal_records` (`achievedAt`)")

            db.execSQL("DROP TABLE IF EXISTS `exercises`")

            db.execSQL("ALTER TABLE `runs` ADD COLUMN `shoeId` INTEGER")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_runs_shoeId` ON `runs` (`shoeId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `shoes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                    "`priceBaht` REAL, `purchaseDate` TEXT, `imagePath` TEXT, `durabilityPct` INTEGER, " +
                    "`isMain` INTEGER NOT NULL, `retired` INTEGER NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
