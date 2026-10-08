package com.gowaist.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.gowaist.app.data.db.GoWaistDatabase
import com.gowaist.app.data.db.GoalEntity
import com.gowaist.app.data.db.RunEntity
import com.gowaist.app.data.db.SetEntity
import com.gowaist.app.data.repo.BackupRepository
import com.gowaist.app.data.repo.BodyRepository
import com.gowaist.app.data.repo.GoalRepository
import com.gowaist.app.data.repo.LibraryRepository
import com.gowaist.app.data.repo.PlanRepository
import com.gowaist.app.data.repo.RunRepository
import com.gowaist.app.data.repo.WorkoutRepository
import com.gowaist.app.data.settings.SettingsRepository
import com.gowaist.core.model.GoalPeriod
import com.gowaist.core.model.GoalStatus
import com.gowaist.core.model.GoalType
import com.gowaist.core.model.PlanDayStatus
import com.gowaist.core.model.PrType
import com.gowaist.core.seed.ExerciseSeed
import com.gowaist.core.seed.TemplateSeed
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
    private lateinit var seeder: Seeder
    private lateinit var library: LibraryRepository
    private lateinit var body: BodyRepository
    private lateinit var workouts: WorkoutRepository
    private lateinit var runs: RunRepository
    private lateinit var plans: PlanRepository
    private lateinit var goals: GoalRepository

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, GoWaistDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(context)
        seeder = Seeder(db)
        library = LibraryRepository(db, db.exerciseDao(), db.chainDao(), db.templateDao(), db.suggestionDao())
        body = BodyRepository(db.bodyDao(), settings)
        workouts = WorkoutRepository(db, db.sessionDao(), db.exerciseDao(), library, body)
        runs = RunRepository(context, db, db.runDao())
        plans = PlanRepository(db, db.planDao(), db.runDao(), db.sessionDao(), library)
        goals = GoalRepository(db, db.goalDao(), db.runDao(), db.sessionDao(), db.planDao(), db.bodyDao(), db.recordDao())
        seeder.ensureSeeded()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun seedingIsIdempotent() = runTest {
        seeder.ensureSeeded()
        assertEquals(ExerciseSeed.all.size, db.exerciseDao().count())
        assertEquals(TemplateSeed.all.size, db.templateDao().getAllTemplates().size)
    }

    private suspend fun logSession(daysAgo: Long, reps: List<Int>): Long {
        val tpl = library.templateBySeedKey("push_day")!!
        val start = LocalDate.now().minusDays(daysAgo).atTime(7, 0).toEpochMillis()
        val id = workouts.start(templateId = tpl.id, startAt = start)
        val entry = db.sessionDao().getExercises(id).first()
        reps.forEachIndexed { i, r -> workouts.logSet(SetEntity(sessionExerciseId = entry.id, setOrder = i, reps = r, rpe = 7.0)) }
        return id
    }

    @Test
    fun finishingSessionsTracksRecordsAndDropsEmptyExercises() = runTest {
        val first = logSession(3, listOf(10, 10))
        val r1 = workouts.finish(first, feeling = 4, note = "")
        assertTrue(r1.records.isEmpty())
        assertEquals(1, db.sessionDao().getExercises(first).size)
        assertEquals(20, r1.summary.reps)

        val second = logSession(1, listOf(12, 11))
        val r2 = workouts.finish(second, feeling = 5, note = "ดีมาก")
        assertTrue(r2.records.any { it.type == PrType.MAX_REPS && it.value == 12.0 })
        assertNull(workouts.currentDraft())

        workouts.history.test {
            val h = awaitItem()
            assertEquals(2, h.size)
            assertEquals(second, h.first().session.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

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
    fun planStatusesFollowRealActivity() = runTest {
        val monday = LocalDate.now().minusWeeks(1).with(java.time.DayOfWeek.MONDAY)
        val planId = plans.createFromSeed("run_5k", monday, adaptive = true)
        val days = plans.daysOf(planId)
        assertTrue(days.isNotEmpty())
        val tuesday = days.first { it.type.isRun }
        runs.save(
            RunEntity(
                startAt = tuesday.date.toLocalDate().atTime(6, 0).toEpochMillis(), localDate = tuesday.date,
                distanceM = tuesday.targetDistanceM!!, durationSec = 1200, avgPaceSecPerKm = null,
            ),
        )
        plans.syncStatuses()
        assertEquals(PlanDayStatus.DONE, plans.day(tuesday.id)!!.status)
        val restDay = plans.daysOf(planId).first { !it.type.isRun && it.date.toLocalDate().isBefore(LocalDate.now()) }
        assertEquals(PlanDayStatus.REST, restDay.status)
    }

    @Test
    fun goalsUnlockRewardsAndBadges() = runTest {
        val today = LocalDate.now()
        goals.create(
            GoalEntity(type = GoalType.RUN_DISTANCE, title = "วิ่ง 5 กม.", target = 5000.0, period = GoalPeriod.WEEK, startDate = today.minusDays(1).key(), endDate = today.plusDays(5).key()),
            rewardText = "กินบุฟเฟ่ต์",
        )
        runs.save(RunEntity(startAt = System.currentTimeMillis(), localDate = today.key(), distanceM = 5200.0, durationSec = 1800, avgPaceSecPerKm = null))
        val a = goals.evaluate()
        assertEquals(1, a.goals.size)
        assertTrue(a.badges.any { it.name == "FIRST_RUN" })
        val g = goals.goals.first().single()
        assertEquals(GoalStatus.ACHIEVED, g.goal.status)
        assertNotNull(g.reward!!.unlockedAt)
        // Evaluating again does not report the same achievements twice.
        val again = goals.evaluate()
        assertTrue(again.goals.isEmpty() && again.badges.isEmpty())
    }

    @Test
    fun backupRoundTripRestoresEverything() = runTest {
        val backup = BackupRepository(context, db, settings, seeder)
        val session = logSession(1, listOf(8, 9))
        workouts.finish(session, 3, "x")
        runs.save(RunEntity(startAt = 1_000, localDate = "2026-10-01", distanceM = 4200.0, durationSec = 1500, avgPaceSecPerKm = 357.0, tags = listOf("เช้า", "ฝนตก")))
        plans.createFromSeed("combo", LocalDate.now(), adaptive = false)
        goals.create(GoalEntity(type = GoalType.BW_SESSIONS, title = "ฝึก 3 ครั้ง", target = 3.0, period = GoalPeriod.WEEK, startDate = "2026-10-01", endDate = "2026-10-31"), "หนังใหม่")

        val before = backup.snapshot()
        val text = backup.encode(before)
        backup.wipeAll()
        assertTrue(db.runDao().getAll().isEmpty())
        assertEquals(ExerciseSeed.all.size, db.exerciseDao().count())

        backup.restore(backup.decode(text))
        val after = backup.snapshot()
        assertEquals(before.runs, after.runs)
        assertEquals(before.sets, after.sets)
        assertEquals(before.sessions, after.sessions)
        assertEquals(before.planDays, after.planDays)
        assertEquals(before.goals, after.goals)
        assertEquals(before.rewards, after.rewards)
        assertEquals(before.exercises, after.exercises)
        assertEquals(before.records, after.records)
        assertTrue(backup.runsCsv().contains("เช้า|ฝนตก"))
        assertTrue(backup.setsCsv().lines().size > 2)
    }
}
