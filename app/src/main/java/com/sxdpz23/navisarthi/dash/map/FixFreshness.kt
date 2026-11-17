package com.sxdpz23.navisarthi.dash.map

/** Monotonic fix timestamps only: wall clock changes must not revive stale locations. */
object FixFreshness {
    const val MAX_AGE_MS = 4_000L
    const val MAX_ACCURACY_M = 50f

    fun ageMs(fixNs: Long, nowNs: Long): Long =
        if (fixNs <= 0L || fixNs > nowNs) Long.MAX_VALUE else (nowNs - fixNs) / 1_000_000L

    /** Allow two scheduled intervals, including headless power-saving GPS at 5 seconds. */
    fun windowMs(intervalMs: Long): Long = maxOf(MAX_AGE_MS, intervalMs.coerceIn(500L, 5_000L) * 2)

    fun usable(fixNs: Long, nowNs: Long, accuracyM: Float, maxAgeMs: Long = MAX_AGE_MS): Boolean =
        ageMs(fixNs, nowNs) <= maxAgeMs && accuracyM.isFinite() &&
            accuracyM > 0f && accuracyM <= MAX_ACCURACY_M
}
