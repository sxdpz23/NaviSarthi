package com.sxdpz23.navisarthi.navigation.ui.map

internal data class OverlayLayer(val id: String, val symbol: Boolean)

/** Map looks interleave arrows, roads and labels differently; never anchor on one guessed layer name. */
internal object MapOverlayOrder {
    private fun baseGeometry(layer: OverlayLayer) = !layer.symbol && !layer.id.startsWith("navisarthi-")

    fun geometryTop(layers: List<OverlayLayer>, extraGeometry: Set<String> = emptySet()): String? =
        layers.lastOrNull { baseGeometry(it) || (!it.symbol && it.id in extraGeometry) }?.id

    fun routeLabelAnchor(layers: List<OverlayLayer>): String? {
        val top = layers.indexOfLast(::baseGeometry)
        return layers.drop(top + 1).firstOrNull { it.symbol && !it.id.startsWith("navisarthi-") }?.id
    }
}
