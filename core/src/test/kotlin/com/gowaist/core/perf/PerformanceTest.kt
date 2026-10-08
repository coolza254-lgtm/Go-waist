package com.gowaist.core.perf

import com.gowaist.core.timer.PhaseKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PerformanceTest {
    private val today = LocalDate.of(2026, 10, 8)
    private val profile = Profile(age = 30, sex = Sex.MALE, restHr = 60)

    @Test
    fun `cooper formula`() {
        assertEquals(42.37, Vo2Max.cooper(2400.0), 0.01)
    }

    @Test
    fun `vdot matches daniels table for a 25 minute 5k`() {
        assertEquals(38.3, Vo2Max.vdot(5000.0, 1500), 0.15)
        assertEquals(1500.0, RunPaces.predictSeconds(38.3, 5000.0).toDouble(), 6.0)
        assertEquals(250.0, Vo2Max.velocityForVo2(Vo2Max.vo2AtVelocity(250.0)), 1e-6)
    }

    @Test
    fun `heart rate estimate uses heart rate reserve`() {
        // 10 km in 50 min at HR 160 with max 187 (Tanaka for age 30) and rest 60.
        assertEquals(187, profile.effectiveMaxHr)
        assertEquals(54.3, Vo2Max.fromHeartRate(10_000.0, 3000, 160, profile)!!, 0.2)
        assertNull(Vo2Max.fromHeartRate(10_000.0, 3000, 90, profile))
    }

    @Test
    fun `cooper run gives full weight estimate and intervals give none`() {
        val cooper = RunSample(1, today, 2600.0, 720, null, RunType.COOPER, 10.0)
        val e = Vo2Max.estimates(cooper, profile)
        assertEquals(Vo2Method.COOPER, e.single().method)
        assertEquals(1.0, e.single().weight, 0.0)
        val interval = RunSample(2, today, 6000.0, 2400, 165, RunType.INTERVAL, 8.0)
        assertTrue(Vo2Max.estimates(interval, profile).isEmpty())
        val easy = RunSample(3, today, 8000.0, 2880, 150, RunType.FREE, null)
        assertEquals(Vo2Method.HEART_RATE, Vo2Max.estimates(easy, profile).single().method)
    }

    @Test
    fun `daily series covers every day and moves towards new estimates`() {
        val e = listOf(
            Vo2Estimate(1, today.minusDays(20), 40.0, Vo2Method.COOPER, 1.0),
            Vo2Estimate(2, today.minusDays(2), 44.0, Vo2Method.COOPER, 1.0),
        )
        val d = Vo2Max.daily(e, today)
        assertEquals(21, d.size)
        assertEquals(40.0, d.first().trend, 1e-9)
        assertEquals(40.0, d[17].trend, 1e-9) // before the second test
        assertTrue(d.last().trend in 42.0..44.0)
        assertNotNull(d[18].measured)
        assertNull(d.last().measured)
    }

    @Test
    fun `fitness levels by age and sex`() {
        assertEquals(FitnessLevel.GOOD, Vo2Max.level(42.0, profile))
        assertEquals(FitnessLevel.SUPERIOR, Vo2Max.level(42.0, Profile(age = 30, sex = Sex.FEMALE)))
        assertEquals(FitnessLevel.VERY_POOR, Vo2Max.level(25.0, profile))
    }

    @Test
    fun `training paces are ordered`() {
        val easy = RunPaces.paceSecPerKm(45.0, RunPaces.Zone.EASY)
        val threshold = RunPaces.paceSecPerKm(45.0, RunPaces.Zone.THRESHOLD)
        val interval = RunPaces.paceSecPerKm(45.0, RunPaces.Zone.INTERVAL)
        assertTrue(easy > threshold && threshold > interval)
        assertEquals(6, RunPaces.hrZones(profile).size)
    }

    @Test
    fun `training load series converges and flags spikes`() {
        val loads = (0L until 100L).associate { today.minusDays(99 - it) to 50.0 }
        val s = TrainingLoad.series(loads, today.minusDays(29), today)
        assertEquals(30, s.size)
        assertTrue(s.last().fitness in 40.0..50.0)
        assertTrue(kotlin.math.abs(s.last().form) < 10)
        val spike = loads + (today to 400.0)
        val p = TrainingLoad.series(spike, today, today).last()
        assertEquals(FormState.OVERREACHING, TrainingLoad.state(p))
        assertEquals(0.0, TrainingLoad.load(0, null, null, profile), 0.0)
        assertTrue(TrainingLoad.load(2700, null, 6.0, profile) in 60.0..90.0)
    }

    @Test
    fun `weight trend rate bmi and goal date`() {
        val pts = (0L..27L).map { today.minusDays(27 - it) to 80.0 - it * 0.1 }
        val s = WeightTrend.stats(pts, heightCm = 175.0, goalKg = 75.0, today = today)!!
        assertEquals(-0.7, s.weeklyRate!!, 0.01)
        assertEquals(77.3 / (1.75 * 1.75), s.bmi!!, 0.01)
        assertNotNull(s.goalDate)
        assertTrue(s.goalDate!!.isAfter(today))
        assertEquals(WeightTrend.BmiClass.OBESE, WeightTrend.bmiClass(s.bmi!!))
        assertEquals(WeightTrend.BmiClass.OVER, WeightTrend.bmiClass(24.0))
    }

    @Test
    fun `interval program phases and adaptation`() {
        val c = IntervalConfig(warmupMin = 10, workSec = 60, restSec = 90, reps = 6, cooldownMin = 5)
        val phases = RunProgramRules.intervalPhases(c)
        assertEquals(1 + 1 + 6 + 5 + 1, phases.size)
        assertEquals(PhaseKind.COOLDOWN, phases.last().kind)
        assertEquals(7, RunProgramRules.adaptInterval(IntervalResult(c, 6), 5.0).next.reps)
        assertEquals(AdjustReason.ON_TARGET, RunProgramRules.adaptInterval(IntervalResult(c, 6), 7.5).reason)
        assertEquals(5, RunProgramRules.adaptInterval(IntervalResult(c, 4), 9.0).next.reps)
        assertEquals(105, RunProgramRules.adaptInterval(IntervalResult(c, 6), 9.5).next.restSec)
        assertEquals(75, RunProgramRules.adaptInterval(IntervalResult(c.copy(reps = 12), 12), 5.0).next.workSec)
    }

    @Test
    fun `long run target progression and cooper schedule`() {
        assertEquals(9.0, RunProgramRules.adaptLongRun(LongRunConfig(8.0), 8.1).next.targetKm, 0.0)
        assertEquals(8.0, RunProgramRules.adaptLongRun(LongRunConfig(8.0), 7.0).next.targetKm, 0.0)
        assertEquals(AdjustReason.SHORT_OF_TARGET, RunProgramRules.adaptLongRun(LongRunConfig(10.0), 5.0).reason)
        assertTrue(RunProgramRules.cooperDue(CooperConfig(null), today))
        assertTrue(!RunProgramRules.cooperDue(CooperConfig(today.minusDays(5).toString()), today))
        assertEquals(23, RunProgramRules.daysUntilCooper(CooperConfig(today.minusDays(5).toString()), today))
    }
}
