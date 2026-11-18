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

data class CommsMapRider(val id: String, val nickname: String, val position: CommsPosition, val stale: Boolean)
fun commsMapRiders(state: CommsState, now: Long = System.currentTimeMillis() + state.serverOffsetMs): List<CommsMapRider> {
    val snapshot = state.snapshot ?: return emptyList()
    if (snapshot.group.expiresAt <= now) return emptyList()
    return snapshot.group.members.mapNotNull { member ->
        if (member.id == snapshot.memberId) return@mapNotNull null
        val position = snapshot.positions[member.id] ?: return@mapNotNull null
        val age = now - position.receivedAt
        if (age >= 120_000) null else CommsMapRider(member.id, member.nickname, position, age >= 30_000)
    }
}

/** Only an overlay on Nav's existing style; never changes routes, cameras or guidance. Main thread only. */
fun applyCommsMapOverlay(style: Style, riders: List<CommsMapRider>) {
    val sourceId = "comms-riders"
    if (riders.isEmpty() && style.getSource(sourceId) == null) return
    if (style.getSource(sourceId) == null) {
        style.addSource(GeoJsonSource(sourceId))
        style.addLayer(CircleLayer("comms-rider-dot", sourceId).withProperties(
            circleRadius(7f), circleColor("#3EAFA4"), circleStrokeColor("#FFFFFF"), circleStrokeWidth(2f),
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
            addNumberProperty("opacity", if (rider.stale) 0.45 else 1.0)
        }
    }
    style.getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(FeatureCollection.fromFeatures(features))
}
