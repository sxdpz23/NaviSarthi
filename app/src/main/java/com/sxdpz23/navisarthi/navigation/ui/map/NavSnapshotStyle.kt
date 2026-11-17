package com.sxdpz23.navisarthi.navigation.ui.map

import android.content.Context
import com.sxdpz23.navisarthi.navigation.car.CarStyle
import com.sxdpz23.navisarthi.navigation.core.data.tiles.MapStyle
import com.sxdpz23.navisarthi.navigation.offline.GlyphPackStore
import com.sxdpz23.navisarthi.navigation.offline.SnapshotBasemapTiles
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Style specification for Nav's offscreen display; no second map provider or palette. */
internal data class NavSnapshotStyle(val json: String?, val uri: String, val painted: Boolean, val layerIds: List<String>) {
    companion object {
        /** Disk reads and PMTiles header reads belong on IO, before constructing the Main-thread snapshotter. */
        fun resolve(context: Context, look: MapStyle, effective: String, archive: String?, satellite: Boolean): NavSnapshotStyle {
            val cached = SnapshotStyleCache(File(context.cacheDir, "nav-snapshot-styles"))
                .load(look.key, effective, look.uri) { uri -> runCatching {
                okhttp3.OkHttpClient.Builder().callTimeout(10, java.util.concurrent.TimeUnit.SECONDS).build()
                    .newCall(okhttp3.Request.Builder().url(uri).build()).execute().use { response ->
                        if (response.isSuccessful) response.body?.string()?.also { JSONObject(it) } else null
                    }
            }.getOrNull() }
            val offline = archive?.let {
                MapFonts.offlineStyle(look, cached) {
                    runCatching { context.assets.open("styles/liberty-roboto.json").bufferedReader().use { it.readText() } }.getOrNull()
                }
            }
            val painted = offline?.painted ?: look.painted
            var json = offline?.json ?: cached
            // A plain URL fallback bypassed satellite/traffic injection entirely. Load the
            // selected specification before snapshotting; retry rather than show the wrong map.
            if (json == null && look == MapStyle.LIBERTY) {
                json = context.assets.open("styles/liberty-roboto.json").bufferedReader().use { it.readText() }
            }
            checkNotNull(json) { "Selected Nav map style is not cached; connect to internet and retry" }
            if (json != null) {
                json = CarStyle.inject(json, TRAFFIC_TILES, twins = painted)
                val root = JSONObject(json)
                if (archive != null) {
                    val local = SnapshotBasemapTiles.source(archive)
                    if (local != null) {
                        val sources = root.getJSONObject("sources")
                        val sourceId = if (sources.has("openmaptiles")) "openmaptiles" else sources.keys().asSequence()
                            .firstOrNull { sources.optJSONObject(it)?.optString("type") == "vector" }
                        if (sourceId != null) sources.put(sourceId, local)
                        GlyphPackStore.glyphUrl(context)?.let { root.put("glyphs", it) }
                        GlyphPackStore.spriteUrl(context)?.let { root.put("sprite", it) }
                        val layers = root.getJSONArray("layers")
                        for (i in 0 until layers.length()) {
                            val layout = layers.getJSONObject(i).optJSONObject("layout") ?: continue
                            val fonts = layout.optJSONArray("text-font") ?: continue
                            layout.put("text-font", JSONArray().apply {
                                for (j in 0 until fonts.length()) put(GlyphPackStore.stackName(fonts.getString(j)))
                            })
                        }
                    }
                }
                if (satellite) {
                    root.getJSONObject("sources").put(SATELLITE_SOURCE, JSONObject()
                        .put("type", "raster").put("tileSize", 256).put("maxzoom", 19)
                        .put("tiles", JSONArray().put("https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}")))
                    val layers = root.getJSONArray("layers")
                    val out = JSONArray()
                    var inserted = false
                    for (i in 0 until layers.length()) {
                        val layer = layers.getJSONObject(i)
                        if (!inserted && layer.optString("type") == "symbol") {
                            out.put(JSONObject().put("id", SATELLITE_LAYER).put("type", "raster").put("source", SATELLITE_SOURCE))
                            inserted = true
                        }
                        out.put(layer)
                    }
                    if (!inserted) out.put(JSONObject().put("id", SATELLITE_LAYER).put("type", "raster").put("source", SATELLITE_SOURCE))
                    root.put("layers", out)
                }
                val ordered = root.getJSONArray("layers")
                val all = (0 until ordered.length()).map { ordered.getJSONObject(it) }
                val base = all.filter { it.optString("type") != "symbol" && it.optString("id") !in listOf(SATELLITE_LAYER, CarStyle.TRAFFIC_LAYER) }
                val imagery = all.filter { it.optString("id") == SATELLITE_LAYER }
                val traffic = all.filter { it.optString("id") == CarStyle.TRAFFIC_LAYER }
                val labels = all.filter { it.optString("type") == "symbol" }
                root.put("layers", JSONArray(base + imagery + traffic + labels))
                json = root.toString()
            }
            val ids = runCatching {
                val raw = json ?: context.assets.open("styles/liberty-roboto.json").bufferedReader().use { it.readText() }
                val layers = JSONObject(raw).getJSONArray("layers")
                (0 until layers.length()).map { layers.getJSONObject(it).getString("id") }
            }.getOrDefault(emptyList())
            return NavSnapshotStyle(json, look.uri, painted, ids)
        }
    }
}

internal const val SATELLITE_SOURCE = "navisarthi-dash-satellite-source"
internal const val SATELLITE_LAYER = "navisarthi-dash-satellite"
