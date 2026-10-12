package com.gowaist.core.shoes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShoesTest {
    @Test
    fun `cost per km and remaining distance`() {
        val s = ShoeMath.stats(4200.0, listOf(100_000.0, 212_000.0))
        assertEquals(312_000.0, s.distanceM, 0.0)
        assertEquals(2, s.runs)
        assertEquals(13.46, s.costPerKm!!, 0.01)
        assertEquals(802.3, ShoeMath.kmLeft(312_000.0, 72)!!, 0.1)
    }

    @Test
    fun `no numbers before enough data`() {
        assertNull(ShoeMath.stats(4200.0, listOf(500.0)).costPerKm)
        assertNull(ShoeMath.stats(null, listOf(5000.0)).costPerKm)
        assertNull(ShoeMath.kmLeft(312_000.0, 100))
        assertNull(ShoeMath.kmLeft(312_000.0, null))
    }
}
