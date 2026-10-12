package com.gowaist.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gowaist.app.data.db.GoWaistDatabase
import com.gowaist.app.data.db.Migrations
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GoWaistDatabase::class.java,
    )

    @Test
    fun migrate1To2KeepsData() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO runs (id, startAt, localDate, distanceM, durationSec, avgPaceSecPerKm, note, tags, createdAt) " +
                    "VALUES (1, 1000, '2026-10-05', 5020.0, 1884, 375.3, 'เช้า', '[\"เช้า\"]', 1000)",
            )
        }
        val db = helper.runMigrationsAndValidate(DB, 2, true, *Migrations.ALL)
        db.query("SELECT distanceM FROM runs WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals(5020.0, c.getDouble(0), 0.0)
        }
        db.execSQL("INSERT INTO suggestion_states (`key`, status, updatedAt) VALUES ('k', 'ACCEPTED', 1)")
        db.close()
    }

    @Test
    fun migrate2To3AddsRunProgramColumns() {
        helper.createDatabase(DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO runs (id, startAt, localDate, distanceM, durationSec, avgPaceSecPerKm, note, tags, createdAt) " +
                    "VALUES (1, 1000, '2026-10-05', 2400.0, 720, 300.0, '', '[]', 1000)",
            )
        }
        val db = helper.runMigrationsAndValidate(DB, 3, true, *Migrations.ALL)
        db.query("SELECT runType, rpe FROM runs WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals("FREE", c.getString(0))
            assertEquals(true, c.isNull(1))
        }
        db.close()
    }

    @Test
    fun migrate3To4KeepsRunsGoalsAndRecordsAndAddsShoes() {
        helper.createDatabase(DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO runs (id, startAt, localDate, distanceM, durationSec, avgPaceSecPerKm, note, tags, createdAt, runType) " +
                    "VALUES (1, 1000, '2026-10-05', 4140.0, 2134, 515.5, '', '[]', 1000, 'FREE')",
            )
            db.execSQL(
                "INSERT INTO goals (id, type, title, target, period, startDate, endDate, status, progress, createdAt) " +
                    "VALUES (1, 'RUN_DISTANCE', 'run', 60000.0, 'MONTH', '2026-10-01', '2026-10-31', 'ACTIVE', 0.0, 1)",
            )
            db.execSQL(
                "INSERT INTO goals (id, type, title, target, period, startDate, endDate, status, progress, createdAt) " +
                    "VALUES (2, 'BW_SESSIONS', 'bw', 3.0, 'WEEK', '2026-10-01', '2026-10-07', 'ACTIVE', 0.0, 1)",
            )
            db.execSQL("INSERT INTO personal_records (id, type, value, achievedAt, localDate, runId) VALUES (1, 'RUN_LONGEST', 4140.0, 1000, '2026-10-05', 1)")
        }
        val db = helper.runMigrationsAndValidate(DB, 4, true, *Migrations.ALL)
        db.query("SELECT distanceM, shoeId FROM runs WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals(4140.0, c.getDouble(0), 0.0)
            assertEquals(true, c.isNull(1))
        }
        db.query("SELECT COUNT(*) FROM goals").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        db.query("SELECT COUNT(*) FROM personal_records").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        db.execSQL("INSERT INTO shoes (name, isMain, retired, note, createdAt) VALUES ('Pegasus', 1, 0, '', 1)")
        db.close()
    }

    private companion object {
        const val DB = "migration-test"
    }
}
