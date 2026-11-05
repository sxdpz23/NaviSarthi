package com.example.navisarthi.data

/** Integrates validated GPS speeds; track-point thinning must never affect statistics. */
class RideMotionStats(private val startElapsedMs: Long) {
    var distanceMeters = 0.0
        private set
    var maxSpeedMps = 0.0
        private set
    private var previousMs: Long? = null
    private var previousSpeed = 0.0

    fun add(elapsedMs: Long, speedMps: Double) {
        if (elapsedMs < startElapsedMs || !speedMps.isFinite() || speedMps !in 0.0..100.0) return
        val previous = previousMs
        if (previous != null && elapsedMs <= previous) return
        val speed = if (speedMps < 0.7) 0.0 else speedMps
        if (previous != null && elapsedMs - previous <= 5_000L) {
            distanceMeters += (previousSpeed + speed) * 0.5 * (elapsedMs - previous) / 1000.0
        }
        maxSpeedMps = maxOf(maxSpeedMps, speed)
        previousMs = elapsedMs
        previousSpeed = speed
    }

    fun averageSpeedMps(endElapsedMs: Long): Double =
        if (endElapsedMs > startElapsedMs) distanceMeters * 1000.0 / (endElapsedMs - startElapsedMs) else 0.0
}
