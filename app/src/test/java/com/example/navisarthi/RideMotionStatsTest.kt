package com.example.navisarthi

import com.example.navisarthi.data.RideMotionStats
import org.junit.Assert.*
import org.junit.Test

class RideMotionStatsTest {
    @Test fun constantSpeedDistanceAndAverageAgreeWithMaximum() {
        val stats = RideMotionStats(1000)
        for (second in 0..120) stats.add(1000L + second * 1000, 10.0)
        assertEquals(1200.0, stats.distanceMeters, 0.001)
        assertEquals(10.0, stats.averageSpeedMps(121000), 0.001)
        assertEquals(10.0, stats.maxSpeedMps, 0.001)
    }

    @Test fun shortSpeedPeakIsNotLostByTrackThinningAndStopsLowerAverage() {
        val stats = RideMotionStats(0)
        stats.add(0, 0.0)
        stats.add(1000, 4.0)
        stats.add(2000, 16.0)
        stats.add(3000, 0.0)
        stats.add(4000, 0.0)
        assertEquals(16.0, stats.maxSpeedMps, 0.001)
        assertEquals(5.0, stats.averageSpeedMps(4000), 0.001)
    }

    @Test fun staleDuplicateInvalidAndLongGapSamplesCannotInflateDistance() {
        val stats = RideMotionStats(1000)
        stats.add(0, 90.0)
        stats.add(1000, 4.0)
        stats.add(1000, 90.0)
        stats.add(900, 90.0)
        stats.add(2000, Double.NaN)
        stats.add(2000, -1.0)
        stats.add(2000, 101.0)
        stats.add(2000, 4.0)
        stats.add(22000, 4.0)
        assertEquals(4.0, stats.distanceMeters, 0.001)
        assertEquals(4.0, stats.maxSpeedMps, 0.001)
        assertTrue(stats.averageSpeedMps(22000) <= stats.maxSpeedMps)
    }

    @Test fun stationaryNoiseDoesNotRecordTravel() {
        val stats = RideMotionStats(0)
        for (second in 0..120) stats.add(second * 1000L, 0.3)
        assertEquals(0.0, stats.distanceMeters, 0.001)
    }
}
