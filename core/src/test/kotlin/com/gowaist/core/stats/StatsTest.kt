package com.gowaist.core.stats

import com.gowaist.core.Format
import com.gowaist.core.model.PrType
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
