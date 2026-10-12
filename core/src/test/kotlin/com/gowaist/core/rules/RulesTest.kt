package com.gowaist.core.rules

import com.gowaist.core.goals.BodyPoint
import com.gowaist.core.goals.GoalData
import com.gowaist.core.goals.GoalEvaluator
import com.gowaist.core.goals.GoalSpec
import com.gowaist.core.model.GoalType
import com.gowaist.core.run.RunFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RulesTest {
    private val today = LocalDate.of(2026, 10, 8)
    private val emptyData = GoalData(emptyList(), emptySet(), emptyList())

    @Test
    fun `run distance goal`() {
        val runs = listOf(RunFacts(1, 0, today, 6000.0, 2000), RunFacts(2, 0, today.minusDays(30), 9000.0, 3000))
        val g = GoalSpec(GoalType.RUN_DISTANCE, 10_000.0, today.minusDays(6), today)
        val p = GoalEvaluator.progress(g, emptyData.copy(runs = runs))
        assertEquals(0.6, p.fraction, 1e-9)
        assertFalse(p.achieved)
    }

    @Test
    fun `five k time goal`() {
        val runs = listOf(RunFacts(1, 0, today, 5000.0, 1750))
        val g = GoalSpec(GoalType.RUN_5K_TIME, 1800.0, today.minusDays(6), today)
        assertTrue(GoalEvaluator.progress(g, emptyData.copy(runs = runs)).achieved)
    }

    @Test
    fun `streak goal counts consecutive running days`() {
        val days = setOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(5))
        val g = GoalSpec(GoalType.STREAK_DAYS, 3.0, today.minusDays(10), today)
        assertTrue(GoalEvaluator.progress(g, emptyData.copy(activeDays = days)).achieved)
    }

    @Test
    fun `body weight goal works for losing weight`() {
        val body = listOf(BodyPoint(today.minusDays(5), 80.0, null), BodyPoint(today, 78.0, null))
        val g = GoalSpec(GoalType.BODY_WEIGHT, 76.0, today.minusDays(10), today.plusDays(20), baseline = 80.0)
        val p = GoalEvaluator.progress(g, emptyData.copy(body = body))
        assertEquals(0.5, p.fraction, 1e-9)
        assertEquals(78.0, p.current!!, 0.0)
    }
}
