package com.example.navisarthi.dash.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Network
import android.util.LruCache
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/** Paint only line features from OpenMapTiles transportation. Vector overzoom keeps roads thin. */
internal class BlackRoadTiles {
    private data class Source(val template: String, val maxZoom: Int)
    private data class Roads(val extent: Int, val lines: List<IntArray>)
    private val cache = LruCache<String, Roads>(24)
    private var source: Source? = null

    @Synchronized
    fun fetch(z: Int, x: Int, y: Int, network: Network?): Bitmap {
        val config = source ?: JSONObject(read("https://tiles.openfreemap.org/planet/latest", network)
            .toString(Charsets.UTF_8)).let {
            Source(it.getJSONArray("tiles").getString(0), it.optInt("maxzoom", 14).coerceIn(0, 22))
                .also { source = it }
        }
        val base = minOf(z, config.maxZoom)
        val shift = z - base
        val factor = 1 shl shift
        val bx = x shr shift
        val by = y shr shift
        val key = "$base/$bx/$by"
        val roads = cache.get(key) ?: decode(read(config.template
            .replace("{z}", base.toString()).replace("{x}", bx.toString()).replace("{y}", by.toString()),
            network)).also { cache.put(key, it) }
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val path = Path()
        val scale = 256f * factor / roads.extent
        val offsetX = (x and (factor - 1)) * 256f
        val offsetY = (y and (factor - 1)) * 256f
        for (line in roads.lines) {
            path.moveTo(line[0] * scale - offsetX, line[1] * scale - offsetY)
            for (i in 2 until line.size step 2) {
                path.lineTo(line[i] * scale - offsetX, line[i + 1] * scale - offsetY)
            }
        }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(77, 81, 88)
            style = Paint.Style.STROKE
            strokeWidth = if (z >= 16) 2.5f else 1.5f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        })
        return bitmap
    }

    private fun read(url: String, network: Network?): ByteArray {
        val address = URL(url)
        require(address.protocol == "https" && address.host == "tiles.openfreemap.org")
        val conn = (network?.openConnection(address) ?: address.openConnection()) as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.instanceFollowRedirects = false
            check(conn.responseCode == 200)
            return conn.inputStream.use { raw ->
                val input = if (conn.contentEncoding.equals("gzip", true)) GZIPInputStream(raw) else raw
                input.readNBytes(4 * 1024 * 1024 + 1).also { require(it.size <= 4 * 1024 * 1024) }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun decode(bytes: ByteArray): Roads {
        val tile = Proto(bytes)
        while (tile.more) {
            val field = tile.number()
            if (field != 26) { tile.skip(field); continue }
            val layer = Proto(tile.blob())
            var name = ""
            var extent = 4096
            val features = mutableListOf<ByteArray>()
            val keys = mutableListOf<String>()
            val values = mutableListOf<String>()
            while (layer.more) {
                when (val tag = layer.number()) {
                    10 -> name = layer.blob().toString(Charsets.UTF_8)
                    18 -> features.add(layer.blob())
                    26 -> keys.add(layer.blob().toString(Charsets.UTF_8))
                    34 -> {
                        val value = Proto(layer.blob())
                        values.add(if (value.more && value.number() == 10) value.blob().toString(Charsets.UTF_8) else "")
                    }
                    40 -> extent = layer.number()
                    else -> layer.skip(tag)
                }
            }
            if (name != "transportation") continue
            require(extent > 0)
            val classKey = keys.indexOf("class")
            val lines = mutableListOf<IntArray>()
            for (feature in features) {
                val data = Proto(feature)
                var geometry: ByteArray? = null
                var type = 0
                var roadClass: String? = null
                while (data.more) {
                    when (val tag = data.number()) {
                        18 -> {
                            val tags = Proto(data.blob())
                            while (tags.more) {
                                val key = tags.number()
                                val value = tags.number()
                                if (key == classKey) roadClass = values.getOrNull(value)
                            }
                        }
                        24 -> type = data.number()
                        34 -> geometry = data.blob()
                        else -> data.skip(tag)
                    }
                }
                if (type != 2 || geometry == null || roadClass in setOf("rail", "transit", "ferry", "aerialway", "path")) continue
                val commands = Proto(geometry)
                var x = 0
                var y = 0
                var line = mutableListOf<Int>()
                while (commands.more) {
                    val header = commands.number()
                    val kind = header and 7
                    require(kind == 1 || kind == 2)
                    repeat(header ushr 3) {
                        if (kind == 1) {
                            if (line.size >= 4) lines.add(line.toIntArray())
                            line = mutableListOf()
                        }
                        x += commands.signed()
                        y += commands.signed()
                        line.add(x)
                        line.add(y)
                    }
                }
                if (line.size >= 4) lines.add(line.toIntArray())
            }
            return Roads(extent, lines)
        }
        return Roads(4096, emptyList())
    }

    private class Proto(private val bytes: ByteArray) {
        private var offset = 0
        val more get() = offset < bytes.size

        fun number(): Int {
            var value = 0
            for (shift in 0..63 step 7) {
                require(more)
                val byte = bytes[offset++].toInt() and 255
                if (shift < 32) value = value or ((byte and 127) shl shift)
                if (byte < 128) return value
            }
            error("Invalid vector tile integer")
        }

        fun signed(): Int = number().let { (it ushr 1) xor -(it and 1) }

        fun blob(): ByteArray {
            val length = number()
            require(length in 0..bytes.size - offset)
            val result = bytes.copyOfRange(offset, offset + length)
            offset += length
            return result
        }

        fun skip(tag: Int) {
            when (tag and 7) {
                0 -> number()
                1 -> offset += 8
                2 -> { val size = number(); require(size in 0..bytes.size - offset); offset += size }
                5 -> offset += 4
                else -> error("Invalid vector tile field")
            }
            require(offset <= bytes.size)
        }
    }
}
