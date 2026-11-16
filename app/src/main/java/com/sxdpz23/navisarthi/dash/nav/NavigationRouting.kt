package com.sxdpz23.navisarthi.dash.nav

import android.content.Context
import com.sxdpz23.navisarthi.navigation.core.data.MapDataSource
import com.sxdpz23.navisarthi.navigation.core.data.RoutingPrefs
import com.sxdpz23.navisarthi.navigation.core.model.LatLng
import com.sxdpz23.navisarthi.navigation.core.model.TravelMode
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ConsoleRoutingEntryPoint { fun mapDataSource(): MapDataSource }

/** Adapt Navigation geometry and turn instructions without asking a second router for a different line. */
object NavigationRouting {
    fun install(context: Context) {
        Router.externalDriveRouter = { from, to, heading ->
            try {
                EntryPointAccessors.fromApplication(context, ConsoleRoutingEntryPoint::class.java)
                    .mapDataSource().directions(LatLng(from.lat, from.lng), LatLng(to.lat, to.lng),
                        TravelMode.DRIVE, avoidTolls = RoutingPrefs.avoidTolls,
                        avoidHighways = RoutingPrefs.avoidHighways, avoidFerries = RoutingPrefs.avoidFerries,
                        departBearingDeg = heading?.toDouble(), budgetMs = 20_000L)
                    .firstOrNull()?.let(::consoleRoute)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        }
    }

    fun consoleRoute(source: com.sxdpz23.navisarthi.navigation.core.model.Route): Route {
        val points = source.polyline.map { GeoPoint(it.lat, it.lng) }
        require(points.size >= 2) { "A console route needs road geometry" }
        val cumulative = DoubleArray(points.size)
        for (i in 1..points.lastIndex) cumulative[i] = cumulative[i - 1] + GeoPoint.distMeters(points[i - 1], points[i])
        var previous = 0.0
        val maneuvers = source.legs.flatMap { it.maneuvers }.map { step ->
            val location = GeoPoint(step.location.lat, step.location.lng)
            var bestDistance = Double.POSITIVE_INFINITY
            var along = previous
            for (i in 0 until points.lastIndex) {
                val (projection, fraction) = GeoPoint.projectOnSegment(location, points[i], points[i + 1])
                val candidate = cumulative[i] + fraction * (cumulative[i + 1] - cumulative[i])
                if (candidate + 2 < previous) continue
                val distance = GeoPoint.distMeters(location, projection)
                if (distance < bestDistance) { bestDistance = distance; along = candidate }
            }
            previous = along.coerceAtLeast(previous)
            val type = runCatching { ManeuverType.valueOf(step.type.name) }.getOrElse {
                when (step.type) {
                    com.sxdpz23.navisarthi.navigation.core.model.ManeuverType.FORK_LEFT, com.sxdpz23.navisarthi.navigation.core.model.ManeuverType.RAMP_LEFT,
                    com.sxdpz23.navisarthi.navigation.core.model.ManeuverType.KEEP_LEFT -> ManeuverType.SLIGHT_LEFT
                    com.sxdpz23.navisarthi.navigation.core.model.ManeuverType.FORK_RIGHT, com.sxdpz23.navisarthi.navigation.core.model.ManeuverType.RAMP_RIGHT,
                    com.sxdpz23.navisarthi.navigation.core.model.ManeuverType.KEEP_RIGHT -> ManeuverType.SLIGHT_RIGHT
                    else -> ManeuverType.CONTINUE
                }
            }
            Maneuver(type, step.instruction, location, previous, step.roundaboutExit)
        }
        return Route(points, maneuvers, cumulative.last(), source.durationInTrafficSeconds ?: source.durationSeconds, cumulative)
    }

    fun consoleLegs(source: com.sxdpz23.navisarthi.navigation.core.model.Route): List<Route> {
        if (source.legs.size <= 1) return listOf(consoleRoute(source))
        val points = source.polyline.map { GeoPoint(it.lat, it.lng) }
        val cumulative = DoubleArray(points.size)
        for (i in 1..points.lastIndex) cumulative[i] = cumulative[i - 1] + GeoPoint.distMeters(points[i - 1], points[i])
        var start = 0
        var expected = 0.0
        return source.legs.mapIndexed { index, leg ->
            expected += leg.distanceMeters
            val last = leg.maneuvers.lastOrNull()?.location
            val end = if (index == source.legs.lastIndex || last == null) points.lastIndex else {
                val target = GeoPoint(last.lat, last.lng)
                val expectedAlong = expected / source.distanceMeters.coerceAtLeast(1.0) * cumulative.last()
                ((start + 1)..points.lastIndex).minByOrNull { i ->
                    GeoPoint.distMeters(points[i], target) + kotlin.math.abs(cumulative[i] - expectedAlong) * .01
                } ?: points.lastIndex
            }
            require(end > start) { "The selected route contains an empty console leg" }
            val result = consoleRoute(source.copy(polyline = source.polyline.subList(start, end + 1),
                legs = listOf(leg), distanceMeters = leg.distanceMeters, durationSeconds = leg.durationSeconds,
                durationInTrafficSeconds = leg.durationInTrafficSeconds))
            start = end
            result
        }
    }
}
