package com.sxdpz23.navisarthi.comms

import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

data class CommsMapRider(val id: String, val nickname: String, val position: CommsPosition, val stale: Boolean, val color: Int)

internal object RiderColors {
    private val palette = intArrayOf(
        0xFF55D6BE.toInt(), 0xFFFF9B71.toInt(), 0xFFA78BFA.toInt(), 0xFF7DD3FC.toInt(),
        0xFFF472B6.toInt(), 0xFFD4E157.toInt(), 0xFFFFC857.toInt(), 0xFF81C784.toInt(),
        0xFFEF9A9A.toInt(), 0xFF90A4E8.toInt(), 0xFFD7B899.toInt(), 0xFFB0BEC5.toInt(),
    )
    private var groupId: String? = null
    private val assignments = mutableMapOf<String, Int>()

    @Synchronized
    fun forMembers(group: CommsGroup): Map<String, Int> {
        if (groupId != group.id) {
            assignments.clear()
            groupId = group.id
        }
        val active = group.members.map { it.id }.toSet()
        assignments.keys.retainAll(active)
        group.members.sortedBy { it.id }.forEach { member ->
            if (member.id !in assignments) {
                assignments[member.id] = palette.firstOrNull { it !in assignments.values } ?: palette[0]
            }
        }
        return assignments.toMap()
    }
}
fun commsMapRiders(state: CommsState, now: Long = System.currentTimeMillis() + state.serverOffsetMs): List<CommsMapRider> {
    val snapshot = state.snapshot ?: return emptyList()
    if (snapshot.group.expiresAt <= now) return emptyList()
    val colors = RiderColors.forMembers(snapshot.group)
    return snapshot.group.members.mapNotNull { member ->
        if (member.id == snapshot.memberId) return@mapNotNull null
        val position = snapshot.positions[member.id] ?: return@mapNotNull null
        val age = now - position.receivedAt
        if (age >= 120_000) null else CommsMapRider(member.id, member.nickname, position, age >= 30_000, colors.getValue(member.id))
    }
}

/** Only an overlay on Nav's existing style; never changes routes, cameras or guidance. Main thread only. */
fun applyCommsMapOverlay(style: Style, riders: List<CommsMapRider>) {
    val sourceId = "comms-riders"
    if (riders.isEmpty() && style.getSource(sourceId) == null) return
    if (style.getSource(sourceId) == null) {
        style.addSource(GeoJsonSource(sourceId))
        style.addLayer(CircleLayer("comms-rider-dot", sourceId).withProperties(
            circleRadius(7f), circleColor(Expression.get("color")), circleStrokeColor("#FFFFFF"), circleStrokeWidth(2f),
            circleOpacity(Expression.get("opacity")),
        ))
        style.addLayer(SymbolLayer("comms-rider-name", sourceId).withProperties(
            textField(Expression.get("nickname")), textFont(arrayOf("Noto Sans Regular")), textSize(12f), textOffset(arrayOf(0f, -1.7f)),
            textColor("#FFFFFF"), textHaloColor("#101416"), textHaloWidth(2f), textAllowOverlap(false),
            textOpacity(Expression.get("opacity")),
        ))
    }
    val features = riders.map { rider ->
        Feature.fromGeometry(Point.fromLngLat(rider.position.lng, rider.position.lat)).apply {
            addStringProperty("nickname", rider.nickname)
            addStringProperty("color", String.format(java.util.Locale.ROOT, "#%06X", rider.color and 0xFFFFFF))
            addNumberProperty("opacity", if (rider.stale) 0.45 else 1.0)
        }
    }
    style.getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(FeatureCollection.fromFeatures(features))
}
