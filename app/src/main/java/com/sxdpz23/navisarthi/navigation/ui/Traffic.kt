package com.sxdpz23.navisarthi.navigation.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * Whether Google's live-traffic overlay is drawn on the map. A process-wide
 * reactive holder (like [Units] / [com.sxdpz23.navisarthi.navigation.ui.theme.AppTheme]) so a toggle
 * anywhere flips it everywhere; persisted across launches. Off by default
 * (it's a network overlay).
 */
object Traffic {
    val on = mutableStateOf(false)

    fun init(context: Context) {
        on.value = prefs(context).getBoolean(KEY, false)
    }

    fun set(context: Context, value: Boolean) {
        on.value = value
        prefs(context).edit().putBoolean(KEY, value).apply()
    }

    private fun prefs(c: Context) = c.getSharedPreferences("navigation_settings", Context.MODE_PRIVATE)
    private const val KEY = "traffic_on"
}
