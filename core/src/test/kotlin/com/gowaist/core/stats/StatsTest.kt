package com.gowaist.core.stats

import com.gowaist.core.Format
import com.gowaist.core.model.MovementPattern
import com.gowaist.core.model.Muscle
import com.gowaist.core.model.PrType
import com.gowaist.core.model.SetType
import com.gowaist.core.model.TrackingType
import com.gowaist.core.run.RunChecks
import com.gowaist.core.run.RunFacts
import com.gowaist.core.run.RunWarning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatsTest {
    private val d0 = LocalDate.of(2026, 10, 1)

    private val pushup = ExerciseFacts(1, MovementPattern.PUSH, setOf(Muscle.CHEST), setOf(Muscle.TRICEPS), TrackingType.REPS, 0.64)
    private val wPull = ExerciseFacts(2, MovementPattern.PULL, setOf(Muscle.BACK), setOf(Muscle.BICEPS), TrackingType.WEIGHTED, 1.0)
    private val plank = ExerciseFacts(3, MovementPattern.CORE, setOf(Muscle.ABS), emptySet(), TrackingType.HOLD, 0.0)
    private val squat = ExerciseFacts(4, MovementPattern.LEGS, setOf(Muscle.LEGS, Muscle.GLUTES), emptySet(), TrackingType.REPS, 0.7)

    private fun set(ex: Long, session: Long, reps: Int? = null, sec: Int? = null, kg: Double? = null, type: SetType = SetType.NORMAL, date: LocalDate = d0, rpe: Double? = null) =
        SetFacts(ex, session, date, type, reps, sec, kg, null, rpe)

    @Test
    fun `epley formula`() {
        assertEquals(100.0, WorkoutMath.epley1Rm(100.0, 1), 1e-9)
        assertEquals(100.0 * (1 + 10 / 30.0), WorkoutMath.epley1Rm(100.0, 10), 1e-9)
        assertEquals(0.0, WorkoutMath.epley1Rm(100.0, 0), 1e-9)
    }

    @Test
    fun `volume uses body weight factor and added load and skips warmups`() {
        val sets = listOf(set(1, 1, reps = 10), set(1, 1, reps = 10, type = SetType.WARMUP), set(2, 1, reps = 5, kg = 10.0))
        val s = WorkoutMath.summarize(sets, mapOf(1L to pushup, 2L to wPull), bodyweightKg = 70.0)
        assertEquals(2, s.sets)
        assertEquals(15, s.reps)
        assertEquals(10 * 70 * 0.64 + 5 * 80.0, s.volumeKg, 1e-6)
    }

    @Test
    fun `new records only after a baseline exists`() {
        val first = listOf(set(1, 1, reps = 10))
        assertTrue(WorkoutMath.newRecords(first, emptyList(), pushup, 70.0).isEmpty())
        val second = listOf(set(1, 2, reps = 12), set(1, 2, reps = 8))
        val recs = WorkoutMath.newRecords(second, first, pushup, 70.0).map { it.type }
        assertTrue(PrType.MAX_REPS in recs)
        assertTrue(PrType.MAX_VOLUME_SESSION in recs)
    }

    @Test
    fun `weighted records include estimated 1RM`() {
        val before = listOf(set(2, 1, reps = 5, kg = 5.0))
        val now = listOf(set(2, 2, reps = 5, kg = 10.0))
        val recs = WorkoutMath.newRecords(now, before, wPull, 70.0).map { it.type }
        assertTrue(PrType.EST_1RM in recs)
        assertTrue(PrType.MAX_ADDED_WEIGHT in recs)
    }

    @Test
    fun `hold record`() {
        val recs = WorkoutMath.newRecords(listOf(set(3, 2, sec = 75)), listOf(set(3, 1, sec = 60)), plank, 70.0)
        assertEquals(PrType.LONGEST_HOLD, recs.single().type)
        assertEquals(75.0, recs.single().value, 0.0)
    }

    private fun run(id: Long, km: Double, sec: Long, date: LocalDate = d0) = RunFacts(id, id * 1000, date, km * 1000, sec)

    @Test
    fun `run personal bests are estimated from average pace`() {
        val runs = listOf(run(1, 5.0, 1800), run(2, 10.0, 3300), run(3, 3.0, 900))
        val (best5, t5) = RunStats.bestTimeFor(runs, 5000.0)!!
        assertEquals(2L, best5.id)
        assertEquals(1650L, t5)
        assertNull(RunStats.bestTimeFor(runs, 21097.5))
        assertEquals(3L, RunStats.fastestPace(runs)!!.first.id)
    }

    @Test
    fun `run records against history`() {
        val history = listOf(run(1, 5.0, 1800))
        val recs = RunStats.newRecords(run(2, 6.0, 2040), history).map { it.type }
        assertTrue(PrType.RUN_LONGEST in recs)
        assertTrue(PrType.RUN_5K in recs)
        assertTrue(RunStats.newRecords(run(1, 5.0, 1800), emptyList()).isEmpty())
    }

    @Test
    fun `weekly distance buckets by monday`() {
        val today = LocalDate.of(2026, 10, 8) // Thursday
        val runs = listOf(run(1, 5.0, 1800, LocalDate.of(2026, 10, 5)), run(2, 3.0, 1000, LocalDate.of(2026, 10, 4)))
        val weeks = RunStats.weeklyDistance(runs, today, 2)
        assertEquals(LocalDate.of(2026, 9, 28), weeks[0].first)
        assertEquals(3000.0, weeks[0].second, 0.0)
        assertEquals(5000.0, weeks[1].second, 0.0)
    }

    @Test
    fun `streak keeps alive through planned rest days and yesterday`() {
        val today = LocalDate.of(2026, 10, 8)
        val active = setOf(today.minusDays(1), today.minusDays(2), today.minusDays(4))
        val rest = setOf(today.minusDays(3))
        assertEquals(3, Streaks.current(active, rest, today))
        assertEquals(2, Streaks.current(active, emptySet(), today))
        assertEquals(0, Streaks.current(setOf(today.minusDays(3)), emptySet(), today))
        assertEquals(3, Streaks.longest(active, rest))
    }

    @Test
    fun `balance ratios flag push heavy training`() {
        val sets = (1..9).map { set(1, 1, reps = 10) } + (1..3).map { set(4, 1, reps = 10) }
        val r = Balance.ratios(sets, mapOf(1L to pushup, 4L to squat))
        assertEquals(9, r.pushSets)
        assertEquals(0, r.pullSets)
        assertTrue(r.pushPullImbalanced)
        assertTrue(r.upperLowerImbalanced)
        val muscles = Balance.muscleSets(sets, mapOf(1L to pushup, 4L to squat))
        assertEquals(9.0, muscles.getValue(Muscle.CHEST), 0.0)
        assertEquals(4.5, muscles.getValue(Muscle.TRICEPS), 0.0)
    }

    @Test
    fun `moving average smooths daily weight`() {
        val pts = listOf(d0 to 70.0, d0.plusDays(1) to 72.0, d0.plusDays(2) to 71.0)
        val ma = Smoothing.movingAverage(pts, 7)
        assertEquals(71.0, ma.last().second, 1e-9)
    }

    @Test
    fun `formatting`() {
        assertEquals("31:24", Format.duration(1884))
        assertEquals("1:02:47", Format.duration(3767))
        assertEquals("6'15\"", Format.pace(375.0))
        assertEquals("5.02", Format.decimal(5.02))
        assertEquals("5", Format.decimal(5.0))
    }

    @Test
    fun `run checks warn on pace mismatch and implausible values`() {
        val w = RunChecks.validate(5000.0, 1800, 300.0, 300, null, 0)
        assertTrue(RunWarning.PACE_MISMATCH in w)
        assertTrue(RunWarning.HR_IMPLAUSIBLE in w)
        assertTrue(RunChecks.validate(5000.0, 1800, 360.0, 150, null, 0).isEmpty())
    }

    @Test
    fun `duplicate detection by time and distance`() {
        val existing = listOf(RunFacts(1, 1_000_000, d0, 5000.0, 1800))
        assertNotNull(RunChecks.findDuplicate(1_000_000 + 10 * 60_000, d0, 5050.0, true, existing))
        assertNull(RunChecks.findDuplicate(1_000_000 + 60 * 60_000, d0, 5050.0, true, existing))
        assertNotNull(RunChecks.findDuplicate(0, d0, 4990.0, false, existing))
        assertNull(RunChecks.findDuplicate(0, d0, 4990.0, false, existing, excludeId = 1))
        assertFalse(RunChecks.findDuplicate(0, d0, 6000.0, false, existing) != null)
    }
}
