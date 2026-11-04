package com.example.navisarthi.data

import android.content.Context
import android.util.AtomicFile
import com.example.navisarthi.dash.nav.GeoPoint
import com.example.navisarthi.dash.nav.Maneuver
import com.example.navisarthi.dash.nav.ManeuverType
import com.example.navisarthi.dash.nav.PolylineCodec
import com.example.navisarthi.dash.nav.Route
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal class OfflineRoutes(context: Context) {
    private val directory = File(context.filesDir, "offline-routes")

    private fun file(id: String) = AtomicFile(File(directory, "${UUID.fromString(id)}.json"))

    fun exists(id: String) = file(id).baseFile.isFile

    fun delete(id: String) = file(id).delete()

    fun save(id: String, legs: List<Route>) {
        require(legs.isNotEmpty())
        val json = JSONArray()
        for (leg in legs) {
            val steps = JSONArray()
            for (step in leg.maneuvers) {
                steps.put(JSONObject().put("type", step.type.name).put("instruction", step.instruction)
                    .put("lat", step.location.lat).put("lng", step.location.lng)
                    .put("distance", step.cumulativeMeters).put("exit", step.roundaboutExit ?: 0)
                    .put("rightUTurn", step.rightUTurn))
            }
            json.put(JSONObject().put("geometry", PolylineCodec.encode(leg.geometry, 6))
                .put("meters", leg.totalMeters).put("seconds", leg.totalSeconds).put("steps", steps))
        }
        check(directory.isDirectory || directory.mkdirs())
        val payload = JSONObject().put("version", 1).put("legs", json).toString().toByteArray(Charsets.UTF_8)
        val target = file(id)
        val output = target.startWrite()
        try {
            output.write(payload)
            target.finishWrite(output)
        } catch (error: Throwable) {
            target.failWrite(output)
            throw error
        }
    }

    fun load(id: String): List<Route> {
        val payload = JSONObject(file(id).readFully().toString(Charsets.UTF_8))
        require(payload.getInt("version") == 1)
        val legs = payload.getJSONArray("legs")
        return List(legs.length()) { index ->
            val leg = legs.getJSONObject(index)
            val geometry = PolylineCodec.decode(leg.getString("geometry"), 6)
            require(geometry.size >= 2)
            val cumulative = DoubleArray(geometry.size)
            for (i in 1 until geometry.size) {
                cumulative[i] = cumulative[i - 1] + GeoPoint.distMeters(geometry[i - 1], geometry[i])
            }
            val steps = leg.getJSONArray("steps")
            val maneuvers = List(steps.length()) { i ->
                val step = steps.getJSONObject(i)
                Maneuver(ManeuverType.valueOf(step.getString("type")), step.getString("instruction"),
                    GeoPoint(step.getDouble("lat"), step.getDouble("lng")), step.getDouble("distance"),
                    step.getInt("exit").takeIf { it > 0 }, step.getBoolean("rightUTurn"))
            }
            Route(geometry, maneuvers, leg.getDouble("meters"), leg.getDouble("seconds"), cumulative)
        }
    }
}
