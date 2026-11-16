package com.sxdpz23.navisarthi.navigation.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.sxdpz23.navisarthi.navigation.core.data.tiles.MapStyle

/**
 * Settings > Appearance > Map look (pref `map_look`, default the Navigation look on Liberty): which
 * OpenFreeMap style the basemap loads (issue #750). A process-wide holder like [MapColors]. A
 * change reloads the map through `styleKey`, and asks `MapFonts` for that look's patched style.
 */
object MapLook {
    val style = mutableStateOf(MapStyle.DEFAULT)

    fun init(context: Context) {
        style.value = MapStyle.fromKey(prefs(context).getString(KEY, null)) ?: MapStyle.DEFAULT
    }

    fun set(context: Context, look: MapStyle) {
        style.value = look
        prefs(context).edit().putString(KEY, look.key).apply()
        com.sxdpz23.navisarthi.navigation.ui.map.MapFonts.ensure(context, look)
    }

    private fun prefs(c: Context) = c.getSharedPreferences("navigation_settings", Context.MODE_PRIVATE)
    private const val KEY = "map_look"
}
