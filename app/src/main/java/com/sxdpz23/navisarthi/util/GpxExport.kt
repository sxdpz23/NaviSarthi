package com.sxdpz23.navisarthi.util

import android.content.Context
import com.sxdpz23.navisarthi.data.Ride
import com.sxdpz23.navisarthi.dash.nav.PolylineCodec
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Exports a recorded [Ride] as a GPX 1.1 track file for other nav/ride tools. */
object GpxExport {
    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /** Writes `cacheDir/shared/<sid>.gpx` and returns the file. */
    fun write(context: Context, ride: Ride): File {
        val track = if (ride.trackPolyline.isBlank()) emptyList() else PolylineCodec.decode(ride.trackPolyline)
        val name = "Ride ${iso.format(Date(ride.startMs))}"

        val xml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""" + "\n")
            append("""<gpx version="1.1" creator="NaviSarthi" xmlns="http://www.topografix.com/GPX/1/1">""" + "\n")
            append("  <trk>\n")
            append("    <name>${escape(name)}</name>\n")
            append("    <trkseg>\n")
            track.forEach { p ->
                append("      <trkpt lat=\"${p.lat}\" lon=\"${p.lng}\">")
                append("</trkpt>\n")
            }
            append("    </trkseg>\n")
            append("  </trk>\n")
            append("</gpx>\n")
        }

        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "${ride.sid.ifBlank { "ride" }}.gpx")
        file.writeText(xml)
        return file
    }

    private fun escape(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")
}
