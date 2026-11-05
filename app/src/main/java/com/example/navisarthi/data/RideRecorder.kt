package com.example.navisarthi.data

import com.example.navisarthi.dash.nav.GeoPoint
import com.example.navisarthi.dash.nav.PolylineCodec

/**
 * Accumulates GPS fixes for one ride (a connect→disconnect dash session) and produces a
 * [Ride] at the end. Points are thinned to ~every [MIN_MOVE_M] metres so the stored track
 * stays small. Distance integrates validated GPS speed samples; maximum speed uses every
 * accepted speed sample. Average speed includes the whole session, including stops.
 *
 * Not thread-safe — driven from a single coroutine in DashViewModel.
 */
class RideRecorder {

    private val points = ArrayList<GeoPoint>()
    private var startMs = 0L
    private var startElapsedMs = 0L
    private var motion = RideMotionStats(0L)
    private var last: GeoPoint? = null
    private var recording = false

    val isRecording: Boolean get() = recording

    fun start() {
        points.clear(); last = null
        startMs = System.currentTimeMillis()
        startElapsedMs = android.os.SystemClock.elapsedRealtime()
        motion = RideMotionStats(startElapsedMs)
        recording = true
    }

    fun add(lat: Double, lng: Double, speedMps: Float, accuracyM: Float, elapsedMs: Long) {
        if (!recording) return
        // Drop noisy fixes entirely: a fix accurate only to 30 m wanders ~30 m between samples,
        // and that wander gets counted as travel — the main reason the recorded distance ran
        // ~0.2 km over the dash's odometer. Only trust reasonably precise fixes.
        val now = android.os.SystemClock.elapsedRealtime()
        if (!accuracyM.isFinite() || accuracyM <= 0 || accuracyM > ACC_GATE_M ||
            !lat.isFinite() || lat !in -90.0..90.0 || !lng.isFinite() || lng !in -180.0..180.0 ||
            elapsedMs < startElapsedMs || elapsedMs > now || now - elapsedMs > 4_000L ||
            !speedMps.isFinite() || speedMps !in 0f..100f) return
        motion.add(elapsedMs, speedMps.toDouble())
        val p = GeoPoint(lat, lng)
        val prev = last
        if (prev == null) {
            points.add(p); last = p; return
        }
        val step = GeoPoint.distMeters(prev, p)
        if (step < MIN_MOVE_M) return   // thin out jitter / stationary noise
        points.add(p); last = p
    }

    /**
     * Finish the ride. Returns null for a trivial session so we don't litter the history with
     * parking-lot blips. A ride must have COVERED GROUND ([MIN_RIDE_M]); the old rule also kept any
     * session that merely LASTED a while, which saved 0 km "rides" whenever you connected and sat
     * (traffic/parked) for >90 s before disconnecting — distance is the only thing that makes it a ride.
     */
    fun stop(): Ride? {
        recording = false
        val end = System.currentTimeMillis()
        val endElapsedMs = android.os.SystemClock.elapsedRealtime()
        val durationS = ((endElapsedMs - startElapsedMs) / 1000L).coerceAtLeast(0)
        val distanceM = motion.distanceMeters
        if (points.size < 2 || distanceM < MIN_RIDE_M) return null
        val avg = motion.averageSpeedMps(endElapsedMs)
        val first = points.first(); val lastPt = points.last()
        // Usage: a real ride was recorded — how far/long riders actually go.
        com.example.navisarthi.util.Telemetry.logEvent(
            "ride_recorded", "distance_km" to distanceM / 1000.0, "duration_min" to durationS / 60.0,
        )
        return Ride(
            sid = NaviSarthiDb.newSid(),
            startMs = startMs, endMs = end,
            distanceMeters = distanceM, durationSec = durationS,
            avgSpeedMps = avg, maxSpeedMps = motion.maxSpeedMps,
            trackPolyline = PolylineCodec.encode(points),
            startLat = first.lat, startLng = first.lng, endLat = lastPt.lat, endLng = lastPt.lng,
        )
    }

    companion object {
        private const val MIN_MOVE_M = 8.0     // thin track points to ~every 8 m
        private const val MIN_RIDE_M = 150.0   // discard rides that covered less than this (parking-lot blips)
        private const val ACC_GATE_M = 20f     // ignore fixes worse than this (m); noisy fixes inflate distance
    }
}
