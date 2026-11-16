package com.sxdpz23.navisarthi.pod

import com.sxdpz23.navisarthi.dash.nav.Maneuver
import com.sxdpz23.navisarthi.dash.nav.ManeuverType
import java.util.UUID
import kotlin.math.roundToInt

enum class PodBottomLine { DISTANCE, TIME_REMAINING, ARRIVAL_TIME }

/** Independent implementation of the Pod UART protocol; no K1G/Dash packets. */
object PodProtocol {
    val SERVICE: UUID = UUID.fromString("01ff0100-ba5e-f4ee-5ca1-eb1e5e4b1ce0")
    val CHARACTERISTIC: UUID = UUID.fromString("01ff0101-ba5e-f4ee-5ca1-eb1e5e4b1ce0")

    fun crc(bytes: ByteArray, length: Int = bytes.size): Int {
        var crc = 0xffff
        for (i in 0 until length) {
            crc = crc xor ((bytes[i].toInt() and 255) shl 8)
            repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1 }
            crc = crc and 0xffff
        }
        return crc
    }

    fun packet(vararg payload: Int): ByteArray {
        require(payload.size <= 18 && payload.all { it in 0..255 })
        val result = ByteArray(20)
        payload.forEachIndexed { i, b -> result[i] = b.toByte() }
        val crc = crc(result, 18)
        result[18] = (crc ushr 8).toByte(); result[19] = crc.toByte()
        return result
    }

    fun valid(bytes: ByteArray): Boolean = bytes.size == 20 &&
        crc(bytes, 18) == (((bytes[18].toInt() and 255) shl 8) or (bytes[19].toInt() and 255))

    enum class Authorization { ACCEPTED, REJECTED }
    fun authorization(bytes: ByteArray): Authorization? {
        if (!valid(bytes) || bytes[0].toInt() != 0x20) return null
        return when (bytes[1].toInt() and 255) {
            1 -> Authorization.ACCEPTED
            else -> Authorization.REJECTED
        }
    }

    fun handshake(known: Boolean) = packet(0x21, if (known) 0 else 1)
    fun pin(pin: String): ByteArray {
        require(pin.length == 6 && pin.all { it in '0'..'9' })
        return packet(0x20, *pin.map { it.code }.toIntArray())
    }
    fun firmware() = packet(0x03)
    fun serial() = packet(0x30)
    fun clock(hour: Int, minute: Int, use24h: Boolean): ByteArray {
        require(hour in 0..23 && minute in 0..59)
        val encoded = if (use24h) hour else (if (hour % 12 == 0) 12 else hour % 12) or
            (if (hour < 12) 0x40 else 0x80)
        return packet(0x50, encoded, minute)
    }

    /** Pod arrival glyph differs from the Dash's arrival glyph. */
    fun icon(maneuver: Maneuver?): Int = when (maneuver?.type) {
        null -> 0xff
        ManeuverType.ARRIVE -> 0x00
        ManeuverType.DEPART -> 0x3c
        ManeuverType.ROUNDABOUT -> {
            // India: clockwise circulation, exits 1..9. Do not fabricate unsupported exits.
            val exit = maneuver.roundaboutExit
            if (exit != null && exit in 1..9) 0x0a + exit else 0x0a
        }
        else -> maneuver.dashCode
    }

    private fun distance(meters: Double): IntArray {
        require(meters.isFinite() && meters >= 0)
        val unit = if (meters <= 999) 1 else 2
        val value = (if (unit == 1) meters else meters / 100).roundToInt().coerceIn(0, 65535)
        return intArrayOf(value ushr 8, value and 255, unit)
    }

    fun guidance(current: Maneuver?, next: Maneuver?, turnMeters: Double, remainingMeters: Double,
                 etaSeconds: Double, night: Boolean, bottom: PodBottomLine, hour: Int, minute: Int): ByteArray {
        require(etaSeconds.isFinite() && etaSeconds >= 0)
        val turn = distance(turnMeters)
        val info = when (bottom) {
            PodBottomLine.DISTANCE -> distance(remainingMeters)
            PodBottomLine.TIME_REMAINING -> {
                val minutes = (etaSeconds / 60).roundToInt().coerceIn(0, 255 * 60 + 59)
                intArrayOf(minutes / 60, minutes % 60, 0)
            }
            PodBottomLine.ARRIVAL_TIME -> {
                require(hour in 0..23 && minute in 0..59)
                intArrayOf(hour, minute, 0)
            }
        }
        return packet(0x10, 0x11, icon(current), *turn, if (night) 1 else 0, icon(next),
            0xff, 0xff, 0x41, *info)
    }

    fun recalculating(night: Boolean) = packet(0x10, 0x11, 0x1c, 0, 0, 0, if (night) 1 else 0)
}
