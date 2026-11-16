package com.sxdpz23.navisarthi.dash.map

/** Monotonic fix timestamps only: wall clock changes must not revive stale locations. */
object FixFreshness {
    const val MAX_AGE_MS = 4_000L
    const val MAX_ACCURACY_M = 50f

    fun ageMs(fixNs: Long, nowNs: Long): Long =
        if (fixNs <= 0L || fixNs > nowNs) Long.MAX_VALUE else (nowNs - fixNs) / 1_000_000L

    fun usable(fixNs: Long, nowNs: Long, accuracyM: Float): Boolean =
        ageMs(fixNs, nowNs) <= MAX_AGE_MS && accuracyM.isFinite() &&
            accuracyM > 0f && accuracyM <= MAX_ACCURACY_M
}
