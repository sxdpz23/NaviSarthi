package com.sxdpz23.navisarthi.navigation.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * "Use Navigation without Google" (Settings > Privacy, pref `google_free`, off by default): the one
 * switch that stops every request to a Google host. Mirrored into the `:core` flag
 * [com.sxdpz23.navisarthi.navigation.core.data.NoGoogle], which gates the data source at the network edge; the app gates
 * its own Google surfaces (hidden WebViews, the traffic raster, the satellite fallback, the tap
 * lookup, Street View) on this holder. What it costs is in the settings hint and in docs/FAQ.md.
 */
object GoogleFree {
    val on = mutableStateOf(false)

    /** While [on]: may a shared SHORT Google Maps link (maps.app.goo.gl) still be opened by asking
     *  Google's shortener where it points (one cookieless request, see core ShortLinks)? ON by
     *  default (user 2026-09-22): the alternative is not being able to view a link someone sent.
     *  Off, such a link is refused with a toast. Full google.com/maps links never need it. */
    val resolveLinks = mutableStateOf(true)

    fun setResolveLinks(context: Context, value: Boolean) {
        resolveLinks.value = value
        prefs(context).edit().putBoolean(KEY_LINKS, value).apply()
    }

    fun init(context: Context) {
        resolveLinks.value = prefs(context).getBoolean(KEY_LINKS, true)
        on.value = prefs(context).getBoolean(KEY, false)
        com.sxdpz23.navisarthi.navigation.core.data.NoGoogle.enabled = on.value
    }

    fun set(context: Context, value: Boolean) {
        on.value = value
        com.sxdpz23.navisarthi.navigation.core.data.NoGoogle.enabled = value
        prefs(context).edit().putBoolean(KEY, value).apply()
    }

    private fun prefs(c: Context) = c.getSharedPreferences("navigation_settings", Context.MODE_PRIVATE)
    private const val KEY = "google_free"
    private const val KEY_LINKS = "google_free_resolve_links"
}
