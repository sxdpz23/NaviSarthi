package com.example.navisarthi.dash.nav

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches a road route from the public OSRM demo server. Called at planning time
 * (destination shared) while the phone still has internet — the result is cached
 * so a route can be followed during a temporary loss of mobile data.
 */
enum class RouteMode { Car, Bike }

object Router {
    private const val TAG = "Router"
    private const val BASE = "https://router.project-osrm.org/route/v1/driving"
    private const val UA = "NaviSarthi/1.0 (personal motorcycle nav; single user)"

    suspend fun route(from: GeoPoint, to: GeoPoint, mode: RouteMode = RouteMode.Car): Route? = withContext(Dispatchers.IO) {
        if (mode == RouteMode.Bike) return@withContext motorcycleRoute(from, to)
        val url = "$BASE/${from.lng},${from.lat};${to.lng},${to.lat}" +
                "?overview=full&geometries=polyline&steps=true&annotations=false"
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", UA)
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            conn.disconnect()
            parse(body)
        } catch (e: Exception) {
            Log.w(TAG, "route() failed: ${e.message}")
            null
        }
    }

    private fun motorcycleRoute(from: GeoPoint, to: GeoPoint): Route? {
        val request = JSONObject()
            .put("costing", "motorcycle")
            .put("units", "kilometers")
            .put("locations", org.json.JSONArray(listOf(
                JSONObject().put("lat", from.lat).put("lon", from.lng),
                JSONObject().put("lat", to.lat).put("lon", to.lng),
            )))
        val query = java.net.URLEncoder.encode(request.toString(), "UTF-8")
        return try {
            val conn = (URL("https://valhalla1.openstreetmap.de/route?json=$query").openConnection() as HttpURLConnection)
            try {
                conn.setRequestProperty("User-Agent", UA)
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                parseMotorcycle(response)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Motorcycle routing failed", e)
            null
        }
    }

    private fun parseMotorcycle(response: String): Route? {
        val trip = JSONObject(response).getJSONObject("trip")
        val leg = trip.getJSONArray("legs").getJSONObject(0)
        val geometry = PolylineCodec.decode(leg.getString("shape"), precision = 6)
        if (geometry.size < 2) return null
        val cumulative = DoubleArray(geometry.size)
        for (i in 1 until geometry.size) {
            cumulative[i] = cumulative[i - 1] + GeoPoint.distMeters(geometry[i - 1], geometry[i])
        }
        val steps = leg.getJSONArray("maneuvers")
        val maneuvers = (0 until steps.length()).mapNotNull { i ->
            val step = steps.getJSONObject(i)
            val index = step.optInt("begin_shape_index", -1)
            val point = geometry.getOrNull(index) ?: return@mapNotNull null
            val code = step.getInt("type")
            val type = when (code) {
                in 1..3 -> ManeuverType.DEPART
                in 4..6 -> ManeuverType.ARRIVE
                9, 18, 20, 23, 37 -> ManeuverType.SLIGHT_RIGHT
                10 -> ManeuverType.TURN_RIGHT
                11 -> ManeuverType.SHARP_RIGHT
                12, 13 -> ManeuverType.UTURN
                14 -> ManeuverType.SHARP_LEFT
                15 -> ManeuverType.TURN_LEFT
                16, 19, 21, 24, 38 -> ManeuverType.SLIGHT_LEFT
                26, 27 -> ManeuverType.ROUNDABOUT
                else -> ManeuverType.CONTINUE
            }
            Maneuver(type, step.optString("instruction"), point, cumulative[index],
                step.optInt("roundabout_exit_count").takeIf { it > 0 }, rightUTurn = type == ManeuverType.UTURN)
        }
        return Route(geometry, maneuvers, cumulative.last(), trip.getJSONObject("summary").getDouble("time"), cumulative)
    }

    private fun parse(json: String): Route? {
        val root = JSONObject(json)
        if (root.optString("code") != "Ok") {
            Log.w(TAG, "OSRM code=${root.optString("code")}")
            return null
        }
        val routes = root.optJSONArray("routes") ?: return null
        if (routes.length() == 0) return null
        val r0 = routes.getJSONObject(0)

        val geometry = PolylineCodec.decode(r0.getString("geometry"))
        if (geometry.size < 2) return null

        // Cumulative distance at each vertex
        val cum = DoubleArray(geometry.size)
        for (i in 1 until geometry.size) {
            cum[i] = cum[i - 1] + GeoPoint.distMeters(geometry[i - 1], geometry[i])
        }

        // Maneuvers from steps
        val maneuvers = ArrayList<Maneuver>()
        val legs = r0.optJSONArray("legs")
        if (legs != null) {
            for (li in 0 until legs.length()) {
                val steps = legs.getJSONObject(li).optJSONArray("steps") ?: continue
                for (si in 0 until steps.length()) {
                    val step = steps.getJSONObject(si)
                    val man = step.optJSONObject("maneuver") ?: continue
                    val loc = man.optJSONArray("location") ?: continue
                    val p = GeoPoint(loc.getDouble(1), loc.getDouble(0))
                    val type = ManeuverType.fromOsrm(man.optString("type"), man.optString("modifier"))
                    val name = step.optString("name").ifBlank { "road" }
                    val exit = man.optInt("exit").takeIf { type == ManeuverType.ROUNDABOUT && it > 0 }
                    maneuvers.add(
                        Maneuver(
                            type = type,
                            instruction = exit?.let { "At the roundabout, take exit $it onto $name" } ?: buildInstruction(type, name),
                            roundaboutExit = exit,
                            rightUTurn = type == ManeuverType.UTURN,
                            location = p,
                            cumulativeMeters = nearestCumulative(p, geometry, cum),
                        )
                    )
                }
            }
        }

        return Route(
            geometry = geometry,
            maneuvers = maneuvers,
            totalMeters = r0.optDouble("distance", cum.last()),
            totalSeconds = r0.optDouble("duration", 0.0),
            cumulative = cum,
        )
    }

    private fun buildInstruction(type: ManeuverType, road: String): String = when (type) {
        ManeuverType.DEPART       -> "Head out on $road"
        ManeuverType.ARRIVE       -> "Arrive at destination"
        ManeuverType.TURN_LEFT    -> "Turn left onto $road"
        ManeuverType.TURN_RIGHT   -> "Turn right onto $road"
        ManeuverType.SLIGHT_LEFT  -> "Slight left onto $road"
        ManeuverType.SLIGHT_RIGHT -> "Slight right onto $road"
        ManeuverType.SHARP_LEFT   -> "Sharp left onto $road"
        ManeuverType.SHARP_RIGHT  -> "Sharp right onto $road"
        ManeuverType.UTURN        -> "Make a U-turn"
        ManeuverType.ROUNDABOUT   -> "At the roundabout, take $road"
        ManeuverType.CONTINUE     -> "Continue on $road"
    }

    /** Cumulative distance of the geometry vertex nearest to a maneuver location. */
    private fun nearestCumulative(p: GeoPoint, geom: List<GeoPoint>, cum: DoubleArray): Double {
        var best = 0.0
        var bestD = Double.MAX_VALUE
        for (i in geom.indices) {
            val d = GeoPoint.distMeters(p, geom[i])
            if (d < bestD) { bestD = d; best = cum[i] }
        }
        return best
    }
}
