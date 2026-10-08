package com.gowaist.core.ocr

import com.gowaist.core.DistanceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class RunTextParserTest {

    private val today = LocalDate.of(2026, 10, 8)
    private val parser = RunTextParser(today)

    @Test
    fun `samsung health english summary`() {
        val text = """
            Running
            Sun, Oct 5 6:32 AM
            5.02 km
            Distance
            Workout time
            00:31:24
            Avg. pace
            6'15" /km
            Calories
            352 kcal
            Avg. heart rate
            148 bpm
            Max. heart rate
            171 bpm
        """.trimIndent()
        val r = parser.parse(text)
        assertEquals(5020.0, r.distanceM!!.value, 0.5)
        assertEquals(Confidence.HIGH, r.distanceM!!.confidence)
        assertEquals(31 * 60 + 24L, r.durationSec!!.value)
        assertEquals(375.0, r.paceSecPerKm!!.value, 0.5)
        assertEquals(Confidence.HIGH, r.paceSecPerKm!!.confidence)
        assertEquals(352, r.calories!!.value)
        assertEquals(148, r.avgHr!!.value)
        assertEquals(LocalDate.of(2026, 10, 5), r.date!!.value)
        assertEquals(LocalTime.of(6, 32), r.time!!.value)
    }

    @Test
    fun `thai screenshot where only numbers and units survive OCR`() {
        // Thai labels are dropped or garbled by the Latin recognizer.
        val text = """
            การวิ่ง
            5 ต.ค. 2569 06:32
            ระยะทาง
            10.15 กม.
            ระยะเวลา
            01:02:47
            เพซเฉลี่ย
            6'11"
            148 ครั้ง/นาที
            612 กิโลแคลอรี
        """.trimIndent()
        val r = parser.parse(text)
        assertEquals(10150.0, r.distanceM!!.value, 0.5)
        assertEquals(3767L, r.durationSec!!.value)
        assertEquals(371.0, r.paceSecPerKm!!.value, 0.5)
        assertEquals(148, r.avgHr!!.value)
        assertEquals(612, r.calories!!.value)
        assertEquals(LocalDate.of(2026, 10, 5), r.date!!.value)
        assertEquals(LocalTime.of(6, 32), r.time!!.value)
    }

    @Test
    fun `garbled thai labels keep numeric values`() {
        val text = """
            nn5 ss:
            3.21 km
            ssa1
            21:05
            6'34"/km
            o 201 kcal
        """.trimIndent()
        val r = parser.parse(text)
        assertEquals(3210.0, r.distanceM!!.value, 0.5)
        // 21:05 without a label is only accepted as a derived/low confidence value.
        assertEquals(394.0, r.paceSecPerKm!!.value, 0.5)
        assertNotNull(r.durationSec)
        assertEquals(Confidence.LOW, r.durationSec!!.confidence)
        assertEquals(201, r.calories!!.value)
    }

    @Test
    fun `miles are converted to metres and pace per mile to per km`() {
        val text = """
            3.11 mi
            Duration 00:30:00
            Avg pace 9'39" /mi
        """.trimIndent()
        val r = parser.parse(text)
        assertEquals(DistanceUnit.MI, r.unit)
        assertEquals(3.11 * 1609.344, r.distanceM!!.value, 1.0)
        assertEquals(579 * 1000.0 / 1609.344, r.paceSecPerKm!!.value, 0.5)
        assertEquals(1800L, r.durationSec!!.value)
    }

    @Test
    fun `split table does not override total distance`() {
        val text = """
            8.40 km
            1 km 6'02"
            2 km 6'10"
            3 km 6'05"
            Total time 00:51:30
            Avg pace 6'08"
        """.trimIndent()
        val r = parser.parse(text)
        assertEquals(8400.0, r.distanceM!!.value, 0.5)
        assertEquals(368.0, r.paceSecPerKm!!.value, 0.5)
    }

    @Test
    fun `text durations and comma decimals`() {
        val text = "Distance 7,5 km\nTime 45 min 30 s\nCalories 1,020 kcal"
        val r = parser.parse(text)
        assertEquals(7500.0, r.distanceM!!.value, 0.5)
        assertEquals(45 * 60 + 30L, r.durationSec!!.value)
        assertEquals(1020, r.calories!!.value)
    }

    @Test
    fun `hours and minutes text form`() {
        val r = parser.parse("21.1 km\n2h 05m 10s")
        assertEquals(2 * 3600 + 5 * 60 + 10L, r.durationSec!!.value)
    }

    @Test
    fun `missing duration is derived from distance and pace with low confidence`() {
        val r = parser.parse("5.00 km\nAvg pace 6'00\"")
        assertEquals(1800L, r.durationSec!!.value)
        assertEquals(Confidence.LOW, r.durationSec!!.confidence)
    }

    @Test
    fun `missing pace is computed`() {
        val r = parser.parse("Distance 4.00 km\nDuration 00:24:00")
        assertEquals(360.0, r.paceSecPerKm!!.value, 0.01)
        assertEquals(Confidence.LOW, r.paceSecPerKm!!.confidence)
    }

    @Test
    fun `speed in km per hour becomes pace`() {
        val r = parser.parse("10.00 km\n00:50:00\n12.0 km/h")
        assertEquals(300.0, r.paceSecPerKm!!.value, 0.5)
        assertEquals(10000.0, r.distanceM!!.value, 0.5)
    }

    @Test
    fun `max heart rate alone is ignored`() {
        val r = parser.parse("5 km\nMax heart rate\n175 bpm")
        assertNull(r.avgHr)
    }

    @Test
    fun `numeric dates support day first and buddhist years`() {
        assertEquals(LocalDate.of(2026, 3, 4), parser.parse("04/03/2026 07:00\n5.0 km").date!!.value)
        assertEquals(LocalDate.of(2026, 3, 4), parser.parse("04/03/2569\n5.0 km").date!!.value)
        assertEquals(LocalDate.of(2026, 3, 14), parser.parse("03/14/2026\n5.0 km").date!!.value)
        assertEquals(LocalDate.of(2026, 9, 30), parser.parse("2026-09-30 18:05\n5.0 km").date!!.value)
    }

    @Test
    fun `date without year in the future belongs to last year`() {
        val r = parser.parse("Dec 24 7:00 PM\n5.0 km")
        assertEquals(LocalDate.of(2025, 12, 24), r.date!!.value)
        assertEquals(LocalTime.of(19, 0), r.time!!.value)
    }

    @Test
    fun `day month name format`() {
        val r = parser.parse("5 October 2026\n3.0 km")
        assertEquals(LocalDate.of(2026, 10, 5), r.date!!.value)
    }

    @Test
    fun `today and yesterday`() {
        assertEquals(today, parser.parse("Today 6:00 AM\n5 km").date!!.value)
        assertEquals(today.minusDays(1), parser.parse("Yesterday\n5 km").date!!.value)
    }

    @Test
    fun `timestamp on date line is not taken as duration`() {
        val r = parser.parse("2026-10-05 06:30:12\n5.00 km\nDuration 00:28:10")
        assertEquals(28 * 60 + 10L, r.durationSec!!.value)
    }

    @Test
    fun `ocr letter o inside numbers is fixed`() {
        val r = parser.parse("5.O2 km\nDuration 0O:31:24")
        assertEquals(5020.0, r.distanceM!!.value, 0.5)
        assertEquals(1884L, r.durationSec!!.value)
    }

    @Test
    fun `curly quotes in pace`() {
        val r = parser.parse("5.00 km\nAvg pace 5’45”")
        assertEquals(345.0, r.paceSecPerKm!!.value, 0.5)
    }

    @Test
    fun `empty or unrelated text gives empty result`() {
        assertTrue(parser.parse("").isEmpty)
        assertTrue(parser.parse("Hello world\nSettings").isEmpty)
    }

    @Test
    fun `pace with colon and unit`() {
        val r = parser.parse("6.20 km\n5:50 /km")
        assertEquals(350.0, r.paceSecPerKm!!.value, 0.5)
    }

    /** Samsung Health share card (Thai UI): pace/HR chart axes, mm:ss duration, Thai month lost by OCR. */
    @Test
    fun `samsung health thai share card with charts`() {
        val merged = """
            Samsung Health  5 a.A. 17:36
            O ทำว @ อัตราการเต้นของหัวใจ
            /km  bpm
            04'53"  197
            06'37"  181
            08'21"  165
            10'05"  149
            11'50"  133
            13'34"  117
            15'18"  101
            00:00  08:53  17:46  26:39
            35:34  4.14 km  148 bpm
        """.trimIndent()
        val r = parser.parse(merged)
        assertEquals(4140.0, r.distanceM!!.value, 0.5)
        assertEquals(35 * 60 + 34L, r.durationSec!!.value)
        assertEquals(Confidence.HIGH, r.durationSec!!.confidence)
        assertEquals(148, r.avgHr!!.value)
        // Chart ticks are ignored; pace comes from distance and time.
        assertEquals(2134 / 4.14, r.paceSecPerKm!!.value, 0.5)
        assertEquals(Confidence.LOW, r.paceSecPerKm!!.confidence)
        assertEquals(LocalDate.of(2026, 10, 5), r.date!!.value)
        assertEquals(LocalTime.of(17, 36), r.time!!.value)

        // Same card when OCR returns every label on its own line.
        val split = merged.lines().flatMap { it.split("  ") }.joinToString("\n")
        val r2 = parser.parse(split)
        assertEquals(4140.0, r2.distanceM!!.value, 0.5)
        assertEquals(2134L, r2.durationSec!!.value)
        assertEquals(148, r2.avgHr!!.value)
        assertEquals(LocalDate.of(2026, 10, 5), r2.date!!.value)
    }
}
