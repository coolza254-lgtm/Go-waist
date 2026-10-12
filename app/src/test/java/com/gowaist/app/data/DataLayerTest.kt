package com.gowaist.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gowaist.app.data.db.GoWaistDatabase
import com.gowaist.app.data.db.GoalEntity
import com.gowaist.app.data.db.RunEntity
import com.gowaist.app.data.db.ShoeEntity
import com.gowaist.app.data.repo.BackupRepository
import com.gowaist.app.data.repo.GoalRepository
import com.gowaist.app.data.repo.RunRepository
import com.gowaist.app.data.repo.ShoeRepository
import com.gowaist.app.data.settings.SettingsRepository
import com.gowaist.core.model.GoalPeriod
import com.gowaist.core.model.GoalStatus
import com.gowaist.core.model.GoalType
import com.gowaist.core.model.PrType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DataLayerTest {
    private lateinit var context: Context
    private lateinit var db: GoWaistDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var runs: RunRepository
    private lateinit var goals: GoalRepository
    private lateinit var shoes: ShoeRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, GoWaistDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(context)
        runs = RunRepository(context, db, db.runDao())
        goals = GoalRepository(db, db.goalDao(), db.runDao(), db.bodyDao())
        shoes = ShoeRepository(db, db.shoeDao(), db.runDao(), runs)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun runRecordsAndDuplicateDetection() = runTest {
        val today = LocalDate.now()
        val start = today.minusDays(2).atTime(6, 0).toEpochMillis()
        runs.save(RunEntity(startAt = start, localDate = today.minusDays(2).key(), distanceM = 5000.0, durationSec = 1800, avgPaceSecPerKm = 360.0))
        val (_, recs) = runs.save(RunEntity(startAt = start + 86_400_000, localDate = today.minusDays(1).key(), distanceM = 6000.0, durationSec = 2100, avgPaceSecPerKm = 350.0))
        assertTrue(recs.any { it.type == PrType.RUN_LONGEST })
        assertNotNull(runs.findDuplicate(start + 5 * 60_000, today.minusDays(2).key(), 5050.0, true, null))
        assertNull(runs.findDuplicate(start + 5 * 60_000, today.minusDays(2).key(), 8000.0, true, null))
    }

    @Test
    fun goalsAreAchievedByRuns() = runTest {
        val today = LocalDate.now()
        goals.create(GoalEntity(type = GoalType.RUN_DISTANCE, title = "วิ่ง 5 กม.", target = 5000.0, period = GoalPeriod.WEEK, startDate = today.minusDays(1).key(), endDate = today.plusDays(5).key()))
        runs.save(RunEntity(startAt = System.currentTimeMillis(), localDate = today.key(), distanceM = 5200.0, durationSec = 1800, avgPaceSecPerKm = null))
        assertEquals(1, goals.evaluate().size)
        assertEquals(GoalStatus.ACHIEVED, goals.goals.first().single().status)
        assertTrue(goals.evaluate().isEmpty())
    }

    @Test
    fun shoesTrackDistanceCostAndMainPair() = runTest {
        val a = shoes.save(ShoeEntity(name = "Pegasus", priceBaht = 4200.0, durabilityPct = 72), null)
        val b = shoes.save(ShoeEntity(name = "Novablast", priceBaht = 3900.0), null)
        // The first pair becomes the main one automatically.
        assertEquals(a, shoes.main()!!.id)
        runs.save(RunEntity(startAt = 1_000, localDate = "2026-10-01", distanceM = 100_000.0, durationSec = 36_000, avgPaceSecPerKm = 360.0, shoeId = a))
        runs.save(RunEntity(startAt = 2_000, localDate = "2026-10-02", distanceM = 212_000.0, durationSec = 76_000, avgPaceSecPerKm = 360.0, shoeId = a))
        val list = shoes.shoes.first()
        val peg = list.first { it.shoe.id == a }
        assertEquals(312_000.0, peg.stats.distanceM, 0.0)
        assertEquals(13.46, peg.stats.costPerKm!!, 0.01)
        assertEquals(802.3, peg.kmLeft!!, 0.1)

        shoes.setMain(b)
        assertEquals(b, shoes.main()!!.id)
        shoes.delete(b)
        assertEquals(a, shoes.main()!!.id)

        shoes.delete(a)
        assertTrue(db.runDao().getAll().all { it.shoeId == null })
    }

    @Test
    fun backupRoundTripRestoresEverything() = runTest {
        val backup = BackupRepository(context, db, settings)
        val shoe = shoes.save(ShoeEntity(name = "Pegasus", priceBaht = 4200.0), null)
        runs.save(RunEntity(startAt = 1_000, localDate = "2026-10-01", distanceM = 4200.0, durationSec = 1500, avgPaceSecPerKm = 357.0, tags = listOf("เช้า", "ฝนตก"), shoeId = shoe))
        runs.save(RunEntity(startAt = 90_000_000, localDate = "2026-10-02", distanceM = 6000.0, durationSec = 2100, avgPaceSecPerKm = 350.0))
        goals.create(GoalEntity(type = GoalType.RUN_COUNT, title = "วิ่ง 3 ครั้ง", target = 3.0, period = GoalPeriod.MONTH, startDate = "2026-10-01", endDate = "2026-10-31"))

        val before = backup.snapshot()
        val text = backup.encode(before)
        backup.wipeAll()
        assertTrue(db.runDao().getAll().isEmpty())

        backup.restore(backup.decode(text))
        val after = backup.snapshot()
        assertEquals(before.runs, after.runs)
        assertEquals(before.goals, after.goals)
        assertEquals(before.shoes, after.shoes)
        assertEquals(before.records, after.records)
        assertTrue(backup.runsCsv().contains("เช้า|ฝนตก"))
        assertTrue(backup.runsCsv().contains("Pegasus"))
    }

    @Test
    fun oldBackupWithWorkoutDataStillImports() = runTest {
        val backup = BackupRepository(context, db, settings)
        val v1 = """{"format":"gowaist-backup","version":1,"runs":[{"id":1,"startAt":1000,"localDate":"2026-10-01","distanceM":5000.0,"durationSec":1800,"avgPaceSecPerKm":360.0}],
            "exercises":[{"id":1,"nameTh":"วิดพื้น"}],"goals":[{"id":1,"type":"BW_SESSIONS","title":"x","target":3.0,"exerciseId":null,"period":"WEEK","startDate":"2026-10-01","endDate":"2026-10-07"},
            {"id":2,"type":"RUN_COUNT","title":"y","target":3.0,"period":"WEEK","startDate":"2026-10-01","endDate":"2026-10-07"}],
            "records":[{"id":1,"exerciseId":1,"type":"MAX_REPS","value":20.0,"achievedAt":1,"localDate":"2026-10-01","sessionId":3}]}"""
        backup.restore(backup.decode(v1))
        assertEquals(1, db.runDao().getAll().size)
        assertEquals(listOf(GoalType.RUN_COUNT), db.goalDao().getAll().map { it.type })
        assertTrue(db.recordDao().getAll().isEmpty())
    }
}
