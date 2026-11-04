package com.example.navisarthi.dash.nav

enum class ManeuverType { CONTINUE, TURN_LEFT, TURN_RIGHT, SLIGHT_LEFT, SLIGHT_RIGHT,
    SHARP_LEFT, SHARP_RIGHT, UTURN, ROUNDABOUT, DEPART, ARRIVE;

    companion object {
        /** Map an OSRM step maneuver (type + modifier) to our enum. */
        fun fromOsrm(type: String?, modifier: String?): ManeuverType = when (type) {
            "depart"   -> DEPART
            "arrive"   -> ARRIVE
            "roundabout", "rotary" -> ROUNDABOUT
            "fork", "end of road", "turn", "new name", "continue", "merge", "on ramp", "off ramp" ->
                when (modifier) {
                    "left"         -> TURN_LEFT
                    "right"        -> TURN_RIGHT
                    "slight left"  -> SLIGHT_LEFT
                    "slight right" -> SLIGHT_RIGHT
                    "sharp left"   -> SHARP_LEFT
                    "sharp right"  -> SHARP_RIGHT
                    "uturn"        -> UTURN
                    else           -> CONTINUE
                }
            else -> CONTINUE
        }
    }
}

/** One routing instruction located at a point along the geometry. */
data class Maneuver(
    val type: ManeuverType,
    val instruction: String,
    val location: GeoPoint,
    /** Cumulative distance (m) from the route start to this maneuver's location. */
    val cumulativeMeters: Double,
    val roundaboutExit: Int? = null,
    val rightUTurn: Boolean = false,
) {
    val cue: String get() = when (type) {
        ManeuverType.TURN_LEFT -> "Turn left"
        ManeuverType.TURN_RIGHT -> "Turn right"
        ManeuverType.SLIGHT_LEFT -> "Slight left"
        ManeuverType.SLIGHT_RIGHT -> "Slight right"
        ManeuverType.SHARP_LEFT -> "Sharp left"
        ManeuverType.SHARP_RIGHT -> "Sharp right"
        ManeuverType.UTURN -> "U-turn"
        ManeuverType.ROUNDABOUT -> roundaboutExit?.let { "Roundabout: exit $it" } ?: "Roundabout"
        ManeuverType.ARRIVE -> "Destination ahead"
        else -> "Continue straight"
    }

    // Empirical K1G catalog: TripperDashPlusPlus/docs/maneuver-glyphs.
    val dashCode: Int get() = when (type) {
        ManeuverType.TURN_LEFT -> 0x14
        ManeuverType.TURN_RIGHT -> 0x15
        ManeuverType.SHARP_LEFT -> 0x16
        ManeuverType.SHARP_RIGHT -> 0x17
        ManeuverType.SLIGHT_LEFT -> 0x18
        ManeuverType.SLIGHT_RIGHT -> 0x19
        ManeuverType.UTURN -> if (rightUTurn) 0x1A else 0x3D
        ManeuverType.ARRIVE -> 0x3C
        ManeuverType.ROUNDABOUT -> when (val exit = roundaboutExit ?: 0) {
            in 1..9 -> 0x31 + exit
            in 10..19 -> 0x46 + exit - 10
            else -> 0x31
        }
        else -> 0x09
    }
}

/** A computed road route from origin to destination. */
data class Route(
    val geometry: List<GeoPoint>,
    val maneuvers: List<Maneuver>,
    val totalMeters: Double,
    val totalSeconds: Double,
    /** Cumulative distance (m) at each geometry vertex — same length as [geometry]. */
    val cumulative: DoubleArray,
) {
    val destination: GeoPoint? get() = geometry.lastOrNull()
}
