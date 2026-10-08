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

    private companion object {
        const val DB = "migration-test"
    }
}
