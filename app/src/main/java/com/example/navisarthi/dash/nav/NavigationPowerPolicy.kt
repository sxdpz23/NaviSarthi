package com.example.navisarthi.dash.nav

/** Keep the console's proven 2–4 fps envelope; save work where guidance is predictable. */
internal class NavigationPowerPolicy {
    data class Profile(val frameMs: Long, val locationMs: Long, val prefetch: Boolean, val reason: String)
    private var stationarySinceMs: Long? = null
    private var resumeUntilMs = 0L

    fun reset() { stationarySinceMs = null; resumeUntilMs = 0L }

    fun select(nowMs: Long, speedMps: Float, preciseFix: Boolean, matched: Boolean,
        nextTurnMeters: Double?, recalculating: Boolean, interacting: Boolean,
        thermalStatus: Int, batterySaver: Boolean): Profile {
        val speed = speedMps.takeIf { it.isFinite() && it >= 0f } ?: 0f
        if (speed >= .5f && stationarySinceMs?.let { nowMs - it >= 15_000L } == true)
            resumeUntilMs = nowMs + 3_000L
        if (preciseFix && speed < .5f) {
            if (stationarySinceMs == null) stationarySinceMs = nowMs
        } else stationarySinceMs = null
        val parked = stationarySinceMs?.let { nowMs - it >= 15_000L } == true
        val nearTurn = nextTurnMeters?.let { it <= maxOf(80.0, speed * 12.0) } == true
        val resuming = nowMs < resumeUntilMs
        val demanding = interacting || resuming || recalculating || !preciseFix || (!matched && !parked) || nearTurn
        val constrained = thermalStatus >= 2 || batterySaver // Android MODERATE and above.
        val frameMs = if (constrained || parked || !demanding) 500L else 250L
        val locationMs = when {
            !preciseFix || recalculating || interacting || resuming -> 500L
            parked -> 2_000L
            nearTurn || !matched -> 500L
            else -> 1_000L
        }
        val reason = when {
            thermalStatus >= 2 -> "Cooling"
            batterySaver -> "Battery saver"
            parked -> "Stationary"
            demanding -> "Responsive"
            else -> "Cruising"
        }
        return Profile(frameMs, locationMs, !constrained && !parked, reason)
    }
}
