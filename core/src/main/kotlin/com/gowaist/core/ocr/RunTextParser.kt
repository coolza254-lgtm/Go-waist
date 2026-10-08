package com.gowaist.core.ocr

import com.gowaist.core.DistanceUnit
import com.gowaist.core.Pace
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

enum class Confidence { HIGH, LOW }

data class Parsed<T>(val value: T, val confidence: Confidence)

data class ParsedRun(
    val distanceM: Parsed<Double>? = null,
    val durationSec: Parsed<Long>? = null,
    val paceSecPerKm: Parsed<Double>? = null,
    val calories: Parsed<Int>? = null,
    val avgHr: Parsed<Int>? = null,
    val date: Parsed<LocalDate>? = null,
    val time: Parsed<LocalTime>? = null,
    val unit: DistanceUnit = DistanceUnit.KM,
) {
    val isEmpty: Boolean
        get() = distanceM == null && durationSec == null && paceSecPerKm == null && calories == null && avgHr == null
}

/**
 * Extracts run metrics from OCR text of a workout screenshot (Samsung Health and similar).
 *
 * ML Kit's Latin recognizer does not read Thai, so the parser never relies on words: it looks
 * for number + unit patterns (`5.02 km`, `00:31:24`, `6'15"`, `320 kcal`, `148 bpm`). Labels are
 * only used as hints to break ties (e.g. "avg" vs "max" heart rate), in both English and Thai
 * for screenshots where the Thai label happens to survive recognition.
 *
 * Fields matched with an explicit unit or label get [Confidence.HIGH]; values that had to be
 * guessed or derived from other fields get [Confidence.LOW] so the review screen can flag them.
 */
class RunTextParser(private val today: LocalDate = LocalDate.now()) {

    private data class Line(val raw: String, val text: String, val index: Int)

    private data class Candidate<T>(val value: T, val line: Int, val labeled: Boolean, val avg: Boolean, val max: Boolean)

    fun parse(text: String): ParsedRun = parseLines(text.lines())

    fun parseLines(rawLines: List<String>): ParsedRun {
        val lines = rawLines.map { it.trim() }.filter { it.isNotEmpty() }
            .mapIndexed { i, raw -> Line(raw, normalize(raw), i) }
        if (lines.isEmpty()) return ParsedRun()

        val unit = detectUnit(lines)
        val distance = pickDistance(lines)
        val duration = pickDuration(lines)
        val computedPace = if (distance != null && duration != null) Pace.secPerKm(distance.value, duration.value) else null
        val pace = pickPace(lines, unit, computedPace)
        val calories = pickCalories(lines)
        val hr = pickHeartRate(lines)
        val (date, time) = pickDateTime(lines)

        // Derive missing core values from the other two when possible.
        var dist = distance
        var dur = duration
        var pc = pace
        if (dist == null && dur != null && pc != null) {
            dist = Parsed(dur.value / pc.value * 1000.0, Confidence.LOW)
        }
        if (dur == null && dist != null && pc != null) {
            dur = Parsed((pc.value * dist.value / 1000.0).toLong(), Confidence.LOW)
        }
        if (pc == null && dist != null && dur != null) {
            Pace.secPerKm(dist.value, dur.value)?.let { pc = Parsed(it, Confidence.LOW) }
        }

        return ParsedRun(
            distanceM = dist,
            durationSec = dur,
            paceSecPerKm = pc,
            calories = calories,
            avgHr = hr,
            date = date,
            time = time,
            unit = unit,
        )
    }

    // ---------------------------------------------------------------- normalisation

    private fun normalize(s: String): String {
        var t = s.lowercase()
            .replace('’', '\'').replace('‘', '\'').replace('′', '\'').replace('`', '\'').replace('´', '\'')
            .replace('”', '"').replace('“', '"').replace('″', '"')
            .replace("''", "\"")
            .replace('：', ':')
        // Common OCR confusions inside numbers: 5.O2 -> 5.02, l2:3O -> 12:30
        t = OCR_DIGIT_FIX.replace(t) { m -> m.value.replace('o', '0').replace('l', '1').replace('i', '1') }
        return t
    }

    private fun detectUnit(lines: List<Line>): DistanceUnit {
        val mi = lines.count { MI_UNIT.containsMatchIn(it.text) }
        val km = lines.count { KM_UNIT.containsMatchIn(it.text) }
        return if (mi > km) DistanceUnit.MI else DistanceUnit.KM
    }

    private fun hasLabel(lines: List<Line>, line: Int, words: List<String>): Boolean {
        val here = lines[line].text
        val prev = if (line > 0) lines[line - 1].text else ""
        return words.any { here.contains(it) } || (prev.length < 40 && words.any { prev.contains(it) } && !DIGIT.containsMatchIn(prev))
    }

    private fun avgLabel(lines: List<Line>, i: Int) = hasLabel(lines, i, AVG_WORDS)
    private fun maxLabel(lines: List<Line>, i: Int) = hasLabel(lines, i, MAX_WORDS)

    // ---------------------------------------------------------------- distance

    private fun pickDistance(lines: List<Line>): Parsed<Double>? {
        val candidates = mutableListOf<Candidate<Double>>()
        lines.forEach { line ->
            DISTANCE_WITH_UNIT.findAll(line.text).forEach { m ->
                val v = m.groupValues[1].toDecimal() ?: return@forEach
                val unitText = m.groupValues[2]
                val meters = if (unitText.startsWith("mi")) v * DistanceUnit.MI.meters else v * 1000.0
                if (meters in 10.0..300_000.0) {
                    candidates += Candidate(meters, line.index, hasLabel(lines, line.index, DISTANCE_WORDS), false, false)
                }
            }
        }
        if (candidates.isNotEmpty()) {
            val labeled = candidates.firstOrNull { it.labeled }
            if (labeled != null) return Parsed(labeled.value, Confidence.HIGH)
            // Split tables repeat "1 km", "2 km"…; the total is the largest value.
            val best = candidates.maxBy { it.value }
            val distinct = candidates.map { it.value }.distinct().size
            val decimalTotal = DECIMAL_DISTANCE.containsMatchIn(lines[best.line].text)
            return Parsed(best.value, if (distinct == 1 || decimalTotal) Confidence.HIGH else Confidence.LOW)
        }
        // No unit: a decimal number on a line labelled "distance".
        lines.forEachIndexed { i, line ->
            if (hasLabel(lines, i, DISTANCE_WORDS)) {
                BARE_DECIMAL.find(line.text)?.groupValues?.get(1)?.toDecimal()?.let { v ->
                    if (v in 0.05..300.0) return Parsed(v * 1000.0, Confidence.LOW)
                }
                if (i + 1 < lines.size) {
                    BARE_DECIMAL.matchEntire(lines[i + 1].text.trim())?.groupValues?.get(1)?.toDecimal()?.let { v ->
                        if (v in 0.05..300.0) return Parsed(v * 1000.0, Confidence.LOW)
                    }
                }
            }
        }
        return null
    }

    // ---------------------------------------------------------------- duration

    private fun pickDuration(lines: List<Line>): Parsed<Long>? {
        val candidates = mutableListOf<Candidate<Long>>()
        lines.forEach { line ->
            val t = line.text
            val dateLine = ISO_DATE.containsMatchIn(t) || NUMERIC_DATE.containsMatchIn(t)
            HMS.findAll(t).forEach { m ->
                if (dateLine) return@forEach
                val (h, mi, s) = m.destructured
                val sec = h.toLong() * 3600 + mi.toLong() * 60 + s.toLong()
                if (sec > 0 && !isClockTime(t, m.range.last)) {
                    candidates += Candidate(sec, line.index, hasLabel(lines, line.index, DURATION_WORDS), false, false)
                }
            }
            HM_TEXT.findAll(t).forEach { m ->
                val h = m.groupValues[1].toLong()
                val mi = m.groupValues[2].toLong()
                val s = m.groupValues[3].toLongOrNull() ?: 0
                candidates += Candidate(h * 3600 + mi * 60 + s, line.index, true, false, false)
            }
            MS_TEXT.findAll(t).forEach { m ->
                val mi = m.groupValues[1].toLong()
                val s = m.groupValues[2].toLong()
                candidates += Candidate(mi * 60 + s, line.index, hasLabel(lines, line.index, DURATION_WORDS), false, false)
            }
        }
        if (candidates.isNotEmpty()) {
            val labeled = candidates.filter { it.labeled }
            if (labeled.isNotEmpty()) return Parsed(labeled.maxBy { it.value }.value, Confidence.HIGH)
            val best = candidates.maxBy { it.value }
            return Parsed(best.value, if (candidates.size == 1) Confidence.HIGH else Confidence.LOW)
        }
        // mm:ss without hours (e.g. Samsung Health "35:34"). Skip clock times, pace values, the
        // date/time header and chart axes (rows with several time ticks).
        val ms = mutableListOf<Candidate<Long>>()
        lines.forEachIndexed { i, line ->
            val t = line.text
            if (isHeaderClockLine(lines, i)) return@forEachIndexed
            val matches = MS_COLON.findAll(t).filter { !isPaceContext(t, it.range.last) && !isClockTime(t, it.range.last) }.toList()
            if (matches.size >= 3) return@forEachIndexed // time axis of a chart
            matches.forEach { m ->
                val sec = m.groupValues[1].toLong() * 60 + m.groupValues[2].toLong()
                if (sec > 0) {
                    val strong = hasLabel(lines, i, DURATION_WORDS) || DISTANCE_WITH_UNIT.containsMatchIn(t) || BPM.containsMatchIn(t)
                    ms += Candidate(sec, i, strong, false, false)
                }
            }
            // A label alone on its line with the value on the next line.
            if (matches.isEmpty() && hasLabel(lines, i, DURATION_WORDS)) {
                lines.getOrNull(i + 1)?.let { next -> MS_COLON.matchEntire(next.text.trim()) }?.let { m ->
                    val sec = m.groupValues[1].toLong() * 60 + m.groupValues[2].toLong()
                    if (sec > 0) ms += Candidate(sec, i + 1, true, false, false)
                }
            }
        }
        ms.filter { it.labeled }.maxByOrNull { it.value }?.let { return Parsed(it.value, Confidence.HIGH) }
        ms.maxByOrNull { it.value }?.let { return Parsed(it.value, Confidence.LOW) }
        return null
    }

    /** "5 ต.ค. 17:36" style header near the top: a day number, a word and a clock time. */
    private fun isHeaderClockLine(lines: List<Line>, i: Int): Boolean {
        val t = lines[i].text
        if (ISO_DATE.containsMatchIn(t) || NUMERIC_DATE.containsMatchIn(t)) return true
        return i <= 3 && HEADER_DATE_TIME.containsMatchIn(t)
    }

    private fun isClockTime(text: String, endIndex: Int): Boolean {
        val rest = text.substring((endIndex + 1).coerceAtMost(text.length)).trimStart()
        return rest.startsWith("am") || rest.startsWith("pm") || rest.startsWith("a.m") || rest.startsWith("p.m") || rest.startsWith("น.")
    }

    private fun isPaceContext(text: String, endIndex: Int): Boolean {
        val rest = text.substring((endIndex + 1).coerceAtMost(text.length)).trimStart()
        return rest.startsWith("/") || rest.startsWith("min/") || rest.startsWith("'")
    }

    // ---------------------------------------------------------------- pace

    private fun pickPace(lines: List<Line>, unit: DistanceUnit, computed: Double?): Parsed<Double>? {
        val candidates = mutableListOf<Candidate<Double>>()
        lines.forEach { line ->
            val t = line.text
            val perMile = PER_MILE.containsMatchIn(t) || (unit == DistanceUnit.MI && !PER_KM.containsMatchIn(t))
            val factor = if (perMile) 1000.0 / DistanceUnit.MI.meters else 1.0
            PACE_QUOTE.findAll(t).forEach { m ->
                val sec = m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
                if (sec in 90..1800) candidates += Candidate(sec * factor, line.index, true, avgLabel(lines, line.index), maxLabel(lines, line.index))
            }
            PACE_COLON.findAll(t).forEach { m ->
                val sec = m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
                val f = if (m.groupValues[3].startsWith("mi")) 1000.0 / DistanceUnit.MI.meters else 1.0
                if (sec in 90..1800) candidates += Candidate(sec * f, line.index, true, avgLabel(lines, line.index), maxLabel(lines, line.index))
            }
            SPEED.findAll(t).forEach { m ->
                val speed = m.groupValues[1].toDecimal() ?: return@forEach
                if (speed in 2.0..30.0) {
                    val perKm = 3600.0 / (if (m.groupValues[2].startsWith("mph")) speed * DistanceUnit.MI.meters / 1000.0 else speed)
                    candidates += Candidate(perKm, line.index, false, avgLabel(lines, line.index), maxLabel(lines, line.index))
                }
            }
        }
        candidates.firstOrNull { it.avg && !it.max && it.labeled }?.let { return Parsed(it.value, Confidence.HIGH) }
        // Three or more unlabelled paces on separate lines are the tick labels of a pace chart.
        val unlabelled = candidates.filter { !it.avg }
        if (unlabelled.map { it.line }.distinct().size >= 3) candidates.removeAll(unlabelled)
        if (candidates.isEmpty()) return null
        val pool = candidates.filter { !it.max }.ifEmpty { candidates }
        if (computed != null) {
            val closest = pool.minBy { abs(it.value - computed) }
            val conf = if (Pace.matches(closest.value, computed)) Confidence.HIGH else Confidence.LOW
            return Parsed(closest.value, conf)
        }
        val first = pool.first()
        return Parsed(first.value, if (pool.size == 1 && first.labeled) Confidence.HIGH else Confidence.LOW)
    }

    // ---------------------------------------------------------------- calories / heart rate

    private fun pickCalories(lines: List<Line>): Parsed<Int>? {
        val candidates = mutableListOf<Candidate<Int>>()
        lines.forEach { line ->
            CALORIES.findAll(line.text).forEach { m ->
                val v = m.groupValues[1].replace(",", "").replace(".", "").toIntOrNull() ?: return@forEach
                if (v in 1..10_000) candidates += Candidate(v, line.index, true, false, false)
            }
        }
        if (candidates.isEmpty()) return null
        return Parsed(candidates.first().value, if (candidates.map { it.value }.distinct().size == 1) Confidence.HIGH else Confidence.LOW)
    }

    private fun pickHeartRate(lines: List<Line>): Parsed<Int>? {
        val candidates = mutableListOf<Candidate<Int>>()
        lines.forEach { line ->
            BPM.findAll(line.text).forEach { m ->
                val v = m.groupValues[1].toIntOrNull() ?: return@forEach
                if (v in 35..230) {
                    val minLabel = hasLabel(lines, line.index, MIN_WORDS)
                    candidates += Candidate(v, line.index, true, avgLabel(lines, line.index), maxLabel(lines, line.index) || minLabel)
                }
            }
        }
        if (candidates.isEmpty()) return null
        candidates.firstOrNull { it.avg && !it.max }?.let { return Parsed(it.value, Confidence.HIGH) }
        val pool = candidates.filter { !it.max }
        if (pool.isEmpty()) return null
        return Parsed(pool.first().value, if (pool.size == 1) Confidence.HIGH else Confidence.LOW)
    }

    // ---------------------------------------------------------------- date / time

    private fun pickDateTime(lines: List<Line>): Pair<Parsed<LocalDate>?, Parsed<LocalTime>?> {
        var date: Parsed<LocalDate>? = null
        var dateLine = -1
        for (line in lines) {
            val d = parseDate(line.text) ?: continue
            date = Parsed(d.first, if (d.second) Confidence.HIGH else Confidence.LOW)
            dateLine = line.index
            break
        }
        if (date == null) {
            // Month name not readable (e.g. Thai "ต.ค." through the Latin recognizer): keep the
            // day number and assume the most recent such day.
            for (line in lines.take(4)) {
                val m = HEADER_DATE_TIME.find(line.text) ?: continue
                val day = m.groupValues[1].toInt()
                if (day !in 1..31) continue
                var d = today
                var guard = 0
                while (d.dayOfMonth != day && guard < 62) { d = d.minusDays(1); guard++ }
                if (d.dayOfMonth == day) {
                    date = Parsed(d, Confidence.LOW)
                    dateLine = line.index
                }
                break
            }
        }
        var time: Parsed<LocalTime>? = null
        val order = if (dateLine >= 0) listOf(dateLine, dateLine + 1, dateLine - 1).filter { it in lines.indices } else emptyList()
        for (i in order) {
            parseClock(lines[i].text, requireMeridiem = false)?.let { time = Parsed(it, Confidence.HIGH) }
            if (time != null) break
        }
        if (time == null) {
            for (line in lines) {
                parseClock(line.text, requireMeridiem = true)?.let { time = Parsed(it, Confidence.LOW) }
                if (time != null) break
            }
        }
        return date to time
    }

    /** Returns the date and whether the year was explicit. */
    private fun parseDate(t: String): Pair<LocalDate, Boolean>? {
        if (TODAY_WORDS.any { t.contains(it) }) return today to true
        if (YESTERDAY_WORDS.any { t.contains(it) }) return today.minusDays(1) to true

        ISO_DATE.find(t)?.let { m ->
            val (y, mo, d) = m.destructured
            return safeDate(normalizeYear(y.toInt()), mo.toInt(), d.toInt())?.let { it to true }
        }
        NUMERIC_DATE.find(t)?.let { m ->
            var a = m.groupValues[1].toInt()
            var b = m.groupValues[2].toInt()
            val y = normalizeYear(m.groupValues[3].toInt())
            // Default day/month order (Thai & most locales); swap when the second number can't be a month.
            if (b > 12 && a <= 12) a = b.also { b = a }
            return safeDate(y, b, a)?.let { it to true }
        }
        DAY_MONTH_NAME.find(t)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = monthIndex(m.groupValues[2]) ?: return@let
            val y = m.groupValues[3].toIntOrNull()
            return resolveYear(day, month, y)
        }
        MONTH_NAME_DAY.find(t)?.let { m ->
            val month = monthIndex(m.groupValues[1]) ?: return@let
            val day = m.groupValues[2].toInt()
            val y = m.groupValues[3].toIntOrNull()
            return resolveYear(day, month, y)
        }
        THAI_DATE.find(t)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = thaiMonthIndex(m.groupValues[2]) ?: return@let
            val y = m.groupValues[3].toIntOrNull()
            return resolveYear(day, month, y)
        }
        return null
    }

    private fun resolveYear(day: Int, month: Int, year: Int?): Pair<LocalDate, Boolean>? {
        if (year != null) return safeDate(normalizeYear(year), month, day)?.let { it to true }
        val thisYear = safeDate(today.year, month, day) ?: return null
        // A date without a year that lands in the future belongs to last year.
        return (if (thisYear.isAfter(today)) thisYear.minusYears(1) else thisYear) to false
    }

    private fun normalizeYear(y: Int): Int = when {
        y > 2400 -> y - 543 // Buddhist era
        y < 100 -> {
            val ad = 2000 + y
            if (ad > today.year + 1) 2500 + y - 543 else ad
        }
        else -> y
    }

    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? =
        runCatching { LocalDate.of(y, m, d) }.getOrNull()?.takeIf { it.year in 2000..2100 }

    private fun parseClock(t: String, requireMeridiem: Boolean): LocalTime? {
        for (m in CLOCK.findAll(t)) {
            val end = m.range.last + 1
            val after = t.substring(end)
            if (after.startsWith(":") || after.trimStart().startsWith("/") || after.startsWith("'")) continue
            if (m.range.first > 0 && t[m.range.first - 1] == ':') continue
            var h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            val mer = m.groupValues[3]
            if (requireMeridiem && mer.isEmpty()) continue
            if (mer.startsWith("p") && h < 12) h += 12
            if (mer.startsWith("a") && h == 12) h = 0
            if (h in 0..23 && min in 0..59) return LocalTime.of(h, min)
        }
        return null
    }

    private fun monthIndex(s: String): Int? = MONTHS.indexOfFirst { s.startsWith(it) }.takeIf { it >= 0 }?.plus(1)

    private fun thaiMonthIndex(s: String): Int? {
        val clean = s.replace(" ", "")
        THAI_MONTHS_SHORT.forEachIndexed { i, m -> if (clean.startsWith(m)) return i + 1 }
        THAI_MONTHS_LONG.forEachIndexed { i, m -> if (clean.startsWith(m)) return i + 1 }
        return null
    }

    private fun String.toDecimal(): Double? = replace(',', '.').toDoubleOrNull()

    companion object {
        private val DIGIT = Regex("\\d")
        private val OCR_DIGIT_FIX = Regex("(?<=\\d[.:,]?)[oli](?=[\\d.:,])|(?<=\\d[.:,])[oli]\\b|\\b[oli](?=\\d[.:,]\\d)")
        private val KM_UNIT = Regex("\\d\\s*(km|กม|kilomet)")
        private val MI_UNIT = Regex("\\d\\s*(mi\\b|mile)")
        private val PER_MILE = Regex("/\\s*mi")
        private val PER_KM = Regex("/\\s*(km|กม)")

        private val DISTANCE_WITH_UNIT =
            Regex("(?<![\\d:.,'/])(\\d{1,3}(?:[.,]\\d{1,3})?)\\s*(km|kilometers?|kilometres?|กม\\.?|กิโลเมตร|mi(?:les?)?)(?![a-z/])(?!\\s*/\\s*h)")
        private val DECIMAL_DISTANCE = Regex("\\d+[.,]\\d+\\s*(km|mi|กม)")
        private val BARE_DECIMAL = Regex("(?<![\\d:])(\\d{1,3}[.,]\\d{1,3})(?![\\d:])")

        private val HMS = Regex("(?<![\\d:'])(\\d{1,2}):([0-5]\\d):([0-5]\\d)(?![\\d:])")
        private val HM_TEXT = Regex("(\\d{1,2})\\s*(?:h|hr|hrs|ชม\\.?|ชั่วโมง)\\s*(\\d{1,2})\\s*(?:m|min|mins|นาที)(?:\\s*(\\d{1,2})\\s*(?:s|sec|วินาที))?(?![a-z/])")
        private val MS_TEXT = Regex("(?<![\\d.])(\\d{1,3})\\s*(?:m|min|mins|นาที)\\s*(\\d{1,2})\\s*(?:s|sec|secs|วินาที)(?![a-z])")
        private val MS_COLON = Regex("(?<![\\d:'])(\\d{1,3}):([0-5]\\d)(?![\\d:])")

        private val PACE_QUOTE = Regex("(?<![\\d:])(\\d{1,2})\\s*'\\s*(\\d{2})(?:\\s*\")?")
        private val PACE_COLON = Regex("(?<![\\d:])(\\d{1,2}):([0-5]\\d)\\s*(?:min)?\\s*/\\s*(km|mi|กม)")
        private val SPEED = Regex("(\\d{1,2}[.,]\\d{1,2})\\s*(km/h|kmh|kph|mph|กม\\./ชม)")

        private val CALORIES = Regex("(?<![\\d.,])(\\d{1,2}[,.]\\d{3}|\\d{1,4})\\s*(?:kcal|cal\\b|calories|กิโลแคลอรี|แคลอรี)(?!\\s*/)")
        private val BPM = Regex("(?<![\\d.])(\\d{2,3})\\s*(?:bpm|ครั้ง/นาที|ครั้งต่อนาที)")

        private val ISO_DATE = Regex("\\b(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})\\b")
        private val NUMERIC_DATE = Regex("(?<![\\d:])(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{2,4})(?![\\d:])")
        private const val MONTH_RX = "(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?"
        private val DAY_MONTH_NAME = Regex("\\b(\\d{1,2})\\s+$MONTH_RX,?(?:\\s+(\\d{4}))?")
        private val MONTH_NAME_DAY = Regex("\\b$MONTH_RX\\s+(\\d{1,2})(?!\\d)(?:,?\\s+(\\d{4}))?")
        private val THAI_DATE = Regex("(\\d{1,2})\\s*((?:[ก-๙]{1,2}\\.\\s?){2}|[ก-๙]{4,})\\s*(\\d{2,4})?")
        private val HEADER_DATE_TIME = Regex("(?<![\\d:.])(\\d{1,2})\\s+[^\\s\\d:]{1,12}\\s+(\\d{1,2}):([0-5]\\d)(?![\\d:])")
        private val CLOCK = Regex("(?<![\\d.])(\\d{1,2}):([0-5]\\d)\\s*(am|pm|a\\.m\\.|p\\.m\\.)?")

        private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
        private val THAI_MONTHS_SHORT = listOf("ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")
        private val THAI_MONTHS_LONG = listOf(
            "มกรา", "กุมภา", "มีนา", "เมษา", "พฤษภา", "มิถุนา", "กรกฎา", "สิงหา", "กันยา", "ตุลา", "พฤศจิกา", "ธันวา",
        )

        private val DISTANCE_WORDS = listOf("distance", "ระยะทาง", "dist")
        private val DURATION_WORDS = listOf("duration", "workout time", "total time", "elapsed", "time", "ระยะเวลา", "เวลา")
        private val AVG_WORDS = listOf("avg", "average", "เฉลี่ย", "mean")
        private val MAX_WORDS = listOf("max", "best", "fastest", "สูงสุด", "เร็วที่สุด", "peak")
        private val MIN_WORDS = listOf("min hr", "minimum", "ต่ำสุด", "resting")
        private val TODAY_WORDS = listOf("today", "วันนี้")
        private val YESTERDAY_WORDS = listOf("yesterday", "เมื่อวาน")
    }
}
