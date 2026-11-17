package com.sxdpz23.navisarthi.navigation.offline

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject

/** The Nav-selected local archive, served to a headless MapLibre snapshot through the same reader.
 * Snapshotter cannot add a vector source after loading a style. Tile templates avoid its PMTiles
 * metadata/cache path while keeping all geometry in Nav's downloaded basemap and glyph stores. */
object SnapshotBasemapTiles : Interceptor {
    private const val HOST = "snapshot-basemap.navisarthi.invalid"
    private val archives = ConcurrentHashMap<String, File>()
    private val tile = Regex("^/([0-9a-f]+)/(\\d+)/(\\d+)/(\\d+)\\.pbf$")

    fun source(uri: String): JSONObject? {
        val path = uri.removePrefix("pmtiles://").removePrefix("file://")
        val file = File(path).takeIf { it.isFile } ?: return null
        val header = PmtilesReader.header(file) ?: return null
        val key = MessageDigest.getInstance("SHA-256").digest(file.absolutePath.toByteArray())
            .take(12).joinToString("") { "%02x".format(it) }
        archives[key] = file
        return JSONObject().put("type", "vector")
            .put("tiles", JSONArray().put("https://$HOST/$key/{z}/{x}/{y}.pbf"))
            .put("minzoom", header.minZoom).put("maxzoom", header.maxZoom)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != HOST) return chain.proceed(request)
        val match = tile.matchEntire(request.url.encodedPath)
        val file = match?.let { archives[it.groupValues[1]] }
        val bytes = if (file == null || match == null) null else PmtilesReader.tileBytes(
            file, match.groupValues[2].toInt(), match.groupValues[3].toInt(), match.groupValues[4].toInt(),
        )
        return Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(if (bytes == null) 204 else 200).message(if (bytes == null) "No Content" else "OK")
            .body((bytes ?: ByteArray(0)).toResponseBody("application/x-protobuf".toMediaType())).build()
    }
}
