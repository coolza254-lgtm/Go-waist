package com.gowaist.core

import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class DistanceUnit(val meters: Double, val label: String) {
    KM(1000.0, "km"),
    MI(1609.344, "mi"),
}

enum class WeightUnit(val kg: Double, val label: String) {
    KG(1.0, "kg"),
    LB(0.45359237, "lb"),
}

enum class LengthUnit(val cm: Double, val label: String) {
    CM(1.0, "cm"),
    IN(2.54, "in"),
}

object Units {
    const val METERS_PER_MILE = 1609.344

    fun metersTo(unit: DistanceUnit, meters: Double): Double = meters / unit.meters
    fun toMeters(unit: DistanceUnit, value: Double): Double = value * unit.meters
    fun kgTo(unit: WeightUnit, kg: Double): Double = kg / unit.kg
    fun toKg(unit: WeightUnit, value: Double): Double = value * unit.kg
    fun cmTo(unit: LengthUnit, cm: Double): Double = cm / unit.cm
    fun toCm(unit: LengthUnit, value: Double): Double = value * unit.cm
}

object Format {
    /** 3725 -> "1:02:05", 1925 -> "32:05". */
    fun duration(totalSec: Long): String {
        val s = totalSec.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
    }

    /** Pace in seconds per unit -> 6'15". */
    fun pace(secPerUnit: Double?): String {
        if (secPerUnit == null || secPerUnit.isNaN() || secPerUnit.isInfinite() || secPerUnit <= 0) return "-"
        val total = secPerUnit.roundToLong()
        return String.format(Locale.US, "%d'%02d\"", total / 60, total % 60)
    }

    fun decimal(value: Double, digits: Int = 2): String {
        if (digits == 0) return value.roundToInt().toString()
        val s = String.format(Locale.US, "%.${digits}f", value)
        return s.trimEnd('0').trimEnd('.').ifEmpty { "0" }
    }

    fun distance(meters: Double, unit: DistanceUnit, digits: Int = 2): String =
        decimal(Units.metersTo(unit, meters), digits)
}

object Pace {
    /** Seconds per kilometer from distance and duration; null when not computable. */
    fun secPerKm(distanceM: Double, durationSec: Long): Double? =
        if (distanceM <= 0 || durationSec <= 0) null else durationSec / (distanceM / 1000.0)

    fun secPerUnit(secPerKm: Double, unit: DistanceUnit): Double = secPerKm * unit.meters / 1000.0

    /** True if two paces differ by less than [tolerance] (fraction). */
    fun matches(a: Double, b: Double, tolerance: Double = 0.05): Boolean =
        a > 0 && b > 0 && kotlin.math.abs(a - b) / b <= tolerance
}
