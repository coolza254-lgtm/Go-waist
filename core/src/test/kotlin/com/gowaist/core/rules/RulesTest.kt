package com.gowaist.core.rules

import com.gowaist.core.goals.BodyPoint
import com.gowaist.core.goals.GoalData
import com.gowaist.core.goals.GoalEvaluator
import com.gowaist.core.goals.GoalSpec
import com.gowaist.core.mascot.MascotBrain
import com.gowaist.core.mascot.MascotContext
import com.gowaist.core.mascot.MascotLine
import com.gowaist.core.model.GoalType
import com.gowaist.core.model.MovementPattern
import com.gowaist.core.model.Muscle
import com.gowaist.core.model.PlanDayStatus
import com.gowaist.core.model.PlanDayType
import com.gowaist.core.model.SetType
import com.gowaist.core.model.TrackingType
import com.gowaist.core.plan.AdaptiveRules
import com.gowaist.core.plan.AdjustmentType
import com.gowaist.core.plan.DayActuals
import com.gowaist.core.plan.LoadRules
import com.gowaist.core.plan.LoadWarningType
import com.gowaist.core.plan.PlanMatcher
import com.gowaist.core.plan.PlannedDay
import com.gowaist.core.progression.ExerciseHistory
import com.gowaist.core.progression.ProgressionAdvisor
import com.gowaist.core.progression.ProgressionTarget
import com.gowaist.core.progression.SuggestionType
import com.gowaist.core.run.RunFacts
import com.gowaist.core.seed.ChainSeed
import com.gowaist.core.seed.ExerciseSeed
import com.gowaist.core.seed.PlanSeed
import com.gowaist.core.seed.TemplateSeed
import com.gowaist.core.stats.ExerciseFacts
import com.gowaist.core.stats.SetFacts
import com.gowaist.core.timer.IntervalProgram
import com.gowaist.core.timer.PhaseKind
import com.gowaist.core.timer.TimerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RulesTest {
    private val today = LocalDate.of(2026, 10, 8) // Thursday
    private val pushup = ExerciseFacts(1, MovementPattern.PUSH, setOf(Muscle.CHEST), emptySet(), TrackingType.REPS, 0.64)
    private val plank = ExerciseFacts(3, MovementPattern.CORE, setOf(Muscle.ABS), emptySet(), TrackingType.HOLD, 0.0)

    private fun session(ex: Long, id: Long, daysAgo: Long, reps: List<Int>, rpe: Double? = 7.0, hold: Boolean = false) =
        reps.map { r ->
            SetFacts(ex, id, today.minusDays(daysAgo), SetType.NORMAL, if (hold) null else r, if (hold) r else null, null, null, rpe)
        }

    // ------------------------------------------------------------ progression

    @Test
    fun `double progression suggests promotion in chain after two good sessions`() {
        val sets = session(1, 1, 4, listOf(12, 12, 12)) + session(1, 2, 2, listOf(12, 12, 13))
        val s = ProgressionAdvisor(70.0).suggest(listOf(ExerciseHistory(pushup, sets, ProgressionTarget(3, 12), nextExerciseId = 5)), today)
        val promote = s.single { it.type == SuggestionType.PROMOTE }
        assertEquals(5L, promote.nextExerciseId)
        assertEquals(2, promote.evidence.sessions)
    }

    @Test
    fun `no progression when rpe too high or reps missed`() {
        val hard = session(1, 1, 4, listOf(12, 12, 12), rpe = 9.5) + session(1, 2, 2, listOf(12, 12, 12), rpe = 9.5)
        assertTrue(ProgressionAdvisor(70.0).suggest(listOf(ExerciseHistory(pushup, hard, ProgressionTarget(3, 12), null)), today).none { it.type == SuggestionType.INCREASE_REPS })
        val missed = session(1, 1, 4, listOf(12, 12, 12)) + session(1, 2, 2, listOf(12, 10, 9))
        assertTrue(ProgressionAdvisor(70.0).suggest(listOf(ExerciseHistory(pushup, missed, ProgressionTarget(3, 12), null)), today).isEmpty())
    }

    @Test
    fun `end of chain increases reps or hold`() {
        val sets = session(1, 1, 4, listOf(12, 12, 12)) + session(1, 2, 2, listOf(12, 12, 12))
        val s = ProgressionAdvisor(70.0).suggest(listOf(ExerciseHistory(pushup, sets, ProgressionTarget(3, 12), null)), today).single()
        assertEquals(SuggestionType.INCREASE_REPS, s.type)
        assertEquals(14.0, s.evidence.suggestedValue, 0.0)
        val holds = session(3, 1, 4, listOf(60, 60, 60), hold = true) + session(3, 2, 2, listOf(61, 60, 60), hold = true)
        val h = ProgressionAdvisor(70.0).suggest(listOf(ExerciseHistory(plank, holds, ProgressionTarget(3, 60), null)), today).single()
        assertEquals(SuggestionType.INCREASE_HOLD, h.type)
    }

    @Test
    fun `plateau after three sessions without improvement`() {
        val sets = session(1, 1, 20, listOf(10, 9)) + session(1, 2, 16, listOf(11, 9)) +
            session(1, 3, 12, listOf(10, 9)) + session(1, 4, 8, listOf(11, 8)) + session(1, 5, 4, listOf(10, 10))
        val s = ProgressionAdvisor(70.0).suggest(listOf(ExerciseHistory(pushup, sets, ProgressionTarget(3, 12), null)), today)
        assertEquals(SuggestionType.PLATEAU, s.single().type)
    }

    @Test
    fun `deload when rpe stays high`() {
        val sets = (0 until 4).flatMap { i -> session(1, i.toLong(), (i * 3).toLong(), listOf(8, 8), rpe = 9.0) }
        val s = ProgressionAdvisor(70.0).suggest(listOf(ExerciseHistory(pushup, sets, ProgressionTarget(3, 12), null)), today)
        assertTrue(s.any { it.type == SuggestionType.DELOAD })
    }

    // ------------------------------------------------------------ plan

    private fun pd(id: Long, date: LocalDate, type: PlanDayType, km: Double? = null) = PlannedDay(id, date, type, km?.times(1000))

    @Test
    fun `plan matching statuses`() {
        val past = today.minusDays(1)
        assertEquals(PlanDayStatus.DONE, PlanMatcher.status(pd(1, past, PlanDayType.RUN_EASY, 5.0), DayActuals(4500.0, 1), today))
        assertEquals(PlanDayStatus.PARTIAL, PlanMatcher.status(pd(1, past, PlanDayType.RUN_EASY, 5.0), DayActuals(2000.0, 1), today))
        assertEquals(PlanDayStatus.OVER, PlanMatcher.status(pd(1, past, PlanDayType.RUN_EASY, 5.0), DayActuals(8000.0, 1), today))
        assertEquals(PlanDayStatus.MISSED, PlanMatcher.status(pd(1, past, PlanDayType.RUN_EASY, 5.0), DayActuals(), today))
        assertEquals(PlanDayStatus.PENDING, PlanMatcher.status(pd(1, today, PlanDayType.BW_PUSH), DayActuals(), today))
        assertEquals(PlanDayStatus.DONE, PlanMatcher.status(pd(1, today, PlanDayType.BW_PUSH), DayActuals(bwSessions = 1), today))
        assertEquals(PlanDayStatus.OVER, PlanMatcher.status(pd(1, past, PlanDayType.REST), DayActuals(bwSessions = 1), today))
        assertEquals(PlanDayStatus.REST, PlanMatcher.status(pd(1, past, PlanDayType.REST), DayActuals(), today))
    }

    @Test
    fun `load rules`() {
        val mon = LocalDate.of(2026, 10, 5)
        val days = listOf(
            pd(1, mon, PlanDayType.RUN_EASY, 5.0), pd(2, mon.plusDays(1), PlanDayType.RUN_INTERVAL, 5.0),
            pd(3, mon.plusDays(2), PlanDayType.BW_PUSH), pd(4, mon.plusDays(3), PlanDayType.RUN_EASY, 5.0),
            pd(5, mon.plusDays(4), PlanDayType.BW_PULL), pd(6, mon.plusDays(5), PlanDayType.BW_LEGS),
            pd(7, mon.plusDays(6), PlanDayType.RUN_LONG, 12.0),
        )
        val w = LoadRules.check(days).map { it.type }.toSet()
        assertTrue(LoadWarningType.HEAVY_LEGS_BEFORE_LONG in w)
        assertTrue(LoadWarningType.NO_REST_DAY in w)
        val spike = days + listOf(
            pd(8, mon.plusDays(7), PlanDayType.RUN_LONG, 60.0),
        )
        assertTrue(LoadRules.check(spike).any { it.type == LoadWarningType.LOAD_SPIKE })
    }

    @Test
    fun `seeded plans avoid legs before long runs and keep a rest day`() {
        PlanSeed.all.forEach { plan ->
            val start = LocalDate.of(2026, 10, 5)
            var id = 0L
            val days = plan.weeks.flatMapIndexed { w, week ->
                week.mapIndexed { d, def -> PlannedDay(id++, start.plusDays(w * 7L + d), def.type, def.distanceKm?.times(1000)) }
            }
            val warnings = LoadRules.check(days).map { it.type }
            assertFalse(plan.key, LoadWarningType.HEAVY_LEGS_BEFORE_LONG in warnings)
            assertFalse(plan.key, LoadWarningType.NO_REST_DAY in warnings)
        }
    }

    @Test
    fun `adaptive plan increases volume after two strong weeks and reduces after a weak one`() {
        val lastMon = LocalDate.of(2026, 9, 28)
        fun week(mon: LocalDate, status: PlanDayStatus) =
            (0L..3L).map { pd(it, mon.plusDays(it * 2), PlanDayType.RUN_EASY, 5.0) to status }
        val good = week(lastMon, PlanDayStatus.DONE) + week(lastMon.minusWeeks(1), PlanDayStatus.DONE)
        val adj = AdaptiveRules.evaluate(good, today, emptyList(), emptyList(), null)
        assertEquals(AdjustmentType.INCREASE_VOLUME, adj.single().type)
        assertEquals(1.10, adj.single().value, 1e-9)
        val bad = week(lastMon, PlanDayStatus.MISSED) + week(lastMon.minusWeeks(1), PlanDayStatus.DONE)
        assertEquals(AdjustmentType.REDUCE_VOLUME, AdaptiveRules.evaluate(bad, today, emptyList(), emptyList(), null).single().type)
        val pace = AdaptiveRules.evaluate(emptyList(), today, listOf(350.0, 352.0), listOf(370.0, 372.0), 365.0)
        assertEquals(AdjustmentType.NEW_TARGET_PACE, pace.single().type)
        assertEquals(5500.0, AdaptiveRules.scaleDistance(5000.0, 1.3), 0.0)
    }

    // ------------------------------------------------------------ goals

    private val emptyData = GoalData(emptyList(), emptyList(), emptyMap(), emptySet(), emptySet(), emptyList())

    @Test
    fun `run distance goal`() {
        val runs = listOf(RunFacts(1, 0, today, 6000.0, 2000), RunFacts(2, 0, today.minusDays(30), 9000.0, 3000))
        val g = GoalSpec(GoalType.RUN_DISTANCE, 10_000.0, today.minusDays(6), today)
        val p = GoalEvaluator.progress(g, emptyData.copy(runs = runs))
        assertEquals(0.6, p.fraction, 1e-9)
        assertFalse(p.achieved)
    }

    @Test
    fun `five k time goal and max reps goal`() {
        val runs = listOf(RunFacts(1, 0, today, 5000.0, 1750))
        val g = GoalSpec(GoalType.RUN_5K_TIME, 1800.0, today.minusDays(6), today)
        assertTrue(GoalEvaluator.progress(g, emptyData.copy(runs = runs)).achieved)
        val sets = session(10, 1, 0, listOf(8, 6))
        val pull = GoalSpec(GoalType.EXERCISE_MAX_REPS, 10.0, today.minusDays(6), today, exerciseId = 10)
        assertEquals(0.8, GoalEvaluator.progress(pull, emptyData.copy(sets = sets)).fraction, 1e-9)
    }

    @Test
    fun `body weight goal works for losing weight`() {
        val body = listOf(BodyPoint(today.minusDays(5), 80.0, null), BodyPoint(today, 78.0, null))
        val g = GoalSpec(GoalType.BODY_WEIGHT, 76.0, today.minusDays(10), today.plusDays(20), baseline = 80.0)
        val p = GoalEvaluator.progress(g, emptyData.copy(body = body))
        assertEquals(0.5, p.fraction, 1e-9)
        assertEquals(78.0, p.current!!, 0.0)
    }

    // ------------------------------------------------------------ mascot & timer & seeds

    @Test
    fun `mascot priorities`() {
        val base = MascotContext(true, 0, null, PlanDayType.RUN_EASY, PlanDayStatus.PENDING, 4, 1, 1, 0)
        assertEquals(MascotLine.TODAY_RUN, MascotBrain.line(base))
        assertEquals(MascotLine.NEW_PB, MascotBrain.line(base.copy(pbWithinDays = 1)))
        assertEquals(MascotLine.LONG_BREAK, MascotBrain.line(base.copy(daysSinceLastActivity = 7)))
        assertEquals(MascotLine.WELCOME, MascotBrain.line(base.copy(hasAnyActivity = false)))
    }

    @Test
    fun `interval programs`() {
        val tabata = IntervalProgram.phases(TimerConfig.tabata())
        assertEquals(1 + 8 + 7, tabata.size)
        assertEquals(10 + 8 * 20 + 7 * 10, tabata.sumOf { it.durationSec })
        val pos = IntervalProgram.locate(tabata, 10_000 + 25_000)
        assertEquals(PhaseKind.REST, pos.phase.kind)
        assertEquals(5_000L, pos.remainingMs)
        assertTrue(IntervalProgram.locate(tabata, 999_999).finished)
        assertEquals(10 + 600, IntervalProgram.totalSec(TimerConfig.emom(10)))
    }

    @Test
    fun `seed data references valid exercises`() {
        val keys = ExerciseSeed.byKey.keys
        assertEquals(ExerciseSeed.all.size, keys.size)
        ChainSeed.all.forEach { c -> c.steps.forEach { assertTrue(it.exerciseKey, it.exerciseKey in keys) } }
        TemplateSeed.all.forEach { t -> t.items.forEach { assertTrue(it.exerciseKey, it.exerciseKey in keys) } }
        PlanSeed.all.forEach { p ->
            p.weeks.forEach { w ->
                assertEquals(7, w.size)
                w.forEach { d -> d.templateKey?.let { assertTrue(it, it in TemplateSeed.byKey) } }
            }
        }
        ExerciseSeed.all.forEach { assertTrue(it.key, it.difficulty in 1..5) }
        assertNull(ExerciseSeed.byKey["nope"])
    }
}
