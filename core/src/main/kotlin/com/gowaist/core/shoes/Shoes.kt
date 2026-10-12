package com.gowaist.core.shoes

/** Running shoe numbers derived from the runs it was used for. */
data class ShoeStats(
    val distanceM: Double,
    val runs: Int,
    /** Price per kilometre run so far; null before the first kilometre. */
    val costPerKm: Double?,
)

object ShoeMath {
    fun stats(priceBaht: Double?, runDistancesM: List<Double>): ShoeStats {
        val m = runDistancesM.sum()
        val cost = if (priceBaht != null && priceBaht > 0 && m >= 1000) priceBaht / (m / 1000) else null
        return ShoeStats(m, runDistancesM.size, cost)
    }

    /**
     * Kilometres left before the shoe is worn out, extrapolated from the durability the user
     * entered: if 28% has been used over 312 km, 72% lasts about 802 km more.
     */
    fun kmLeft(distanceM: Double, durabilityPct: Int?): Double? {
        val pct = durabilityPct ?: return null
        val used = 100 - pct.coerceIn(0, 100)
        if (used <= 0 || distanceM < 1000) return null
        return distanceM / 1000 / used * pct
    }
}
