package com.example.navisarthi.dash.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

/** Live tiles are memory-only and expire even when the network drops. */
internal class TrafficTiles(context: Context, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("traffic", Context.MODE_PRIVATE)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private data class Tile(val bitmap: Bitmap?, val expiresAt: Long)
    private val cache = LruCache<String, Tile>(96)
    private var loading = false
    private var generation = 0
    private var backoffUntil = 0L
    @Volatile var status = ""
        private set
    @Volatile var enabled = prefs.getBoolean("enabled", false)
        private set
    private var key = prefs.getString("api_key", "").orEmpty()
    val configured: Boolean @Synchronized get() = key.isNotBlank()

    @Synchronized
    fun configure(value: String) {
        key = value.trim()
        generation++
        cache.evictAll()
        backoffUntil = 0L
        status = ""
        prefs.edit().putString("api_key", key).apply()
    }

    @Synchronized
    fun setEnabled(value: Boolean) {
        enabled = value
        generation++
        status = ""
        cache.evictAll()
        prefs.edit().putBoolean("enabled", value).apply()
    }

    @Synchronized
    fun get(z: Int, x: Int, y: Int): Bitmap? {
        if (!enabled || key.isBlank() || y !in 0 until (1 shl z)) return null
        val wrappedX = Math.floorMod(x, 1 shl z)
        val id = "$z/$wrappedX/$y"
        val now = SystemClock.elapsedRealtime()
        cache.get(id)?.takeIf { now < it.expiresAt }?.let { return it.bitmap }
        if (loading || now < backoffUntil) return null
        val date = LocalDate.now().toString()
        val used = if (prefs.getString("day", null) == date) prefs.getInt("calls", 0) else 0
        if (used >= 180) {
            status = "Daily traffic limit reached"
            return null
        }
        val network = connectivity.allNetworks.firstOrNull {
            connectivity.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        }
        if (network == null) {
            status = "Traffic offline"
            backoffUntil = now + 10_000
            return null
        }
        prefs.edit().putString("day", date).putInt("calls", used + 1).apply()
        loading = true
        val version = generation
        val requestKey = key
        scope.launch(Dispatchers.IO) {
            var result: Bitmap? = null
            var message = "Traffic unavailable"
            try {
                val url = URL("https://api.tomtom.com/traffic/map/4/tile/flow/relative0/$z/$wrappedX/$y.png" +
                    "?tileSize=256&key=${URLEncoder.encode(requestKey, "UTF-8")}")
                val connection = network.openConnection(url) as HttpURLConnection
                try {
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 7_000
                    connection.readTimeout = 7_000
                    when (connection.responseCode) {
                        200 -> result = connection.inputStream.use { BitmapFactory.decodeStream(it) }
                        401, 403 -> message = "Check TomTom traffic key"
                        429 -> message = "Traffic provider limit reached"
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (_: java.io.IOException) {
                // Leave the regular route visible when traffic cannot be loaded.
            } finally {
                synchronized(this@TrafficTiles) {
                    loading = false
                    if (generation == version) {
                        val finished = SystemClock.elapsedRealtime()
                        cache.put(id, Tile(result, finished + 180_000))
                        status = if (result != null) "" else message
                        if (result == null) backoffUntil = finished + 60_000
                    }
                }
            }
        }
        return null
    }
}
