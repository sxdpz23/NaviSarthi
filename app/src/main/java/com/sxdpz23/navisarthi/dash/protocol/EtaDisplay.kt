package com.sxdpz23.navisarthi.dash.protocol

enum class EtaDisplayMode(val label: String) { RIBBON("Ribbon"), PILL("Below cursor") }

internal fun etaRibbonText(arrivalClock: String?, minutes: Int?, recalculating: Boolean): String {
    if (recalculating) return "Recalculating ETA"
    val remaining = when {
        minutes == null -> "--m"
        minutes <= 0 -> "<1m"
        minutes < 60 -> "${minutes}m"
        minutes % 60 == 0 -> "${minutes / 60}h"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
    return "ETA ${arrivalClock ?: "--:--"} | $remaining"
}
