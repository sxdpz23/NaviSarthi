package com.sxdpz23.navisarthi.navigation.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.sxdpz23.navisarthi.navigation.core.nav.SpokenDetail.Mode

/**
 * How much the voice says while navigating (issue #718): everything, one short line a maneuver,
 * or only the highway exits. Settings, Voice. What each choice speaks is defined in the `:core`
 * object of the same name, which is what `NavEngine` reads; this holder keeps the preference and
 * mirrors it down, because `:core` never reaches up into an app holder.
 */
object SpokenDetail {
    val mode = mutableStateOf(Mode.FULL)

    private const val KEY = "spoken_detail"

    private fun prefs(context: Context) =
        context.getSharedPreferences("navigation_settings", Context.MODE_PRIVATE)

    fun init(context: Context) {
        val saved = prefs(context).getString(KEY, null)
        mode.value = Mode.entries.firstOrNull { it.name == saved } ?: Mode.FULL
        com.sxdpz23.navisarthi.navigation.core.nav.SpokenDetail.mode = mode.value
    }

    fun set(context: Context, value: Mode) {
        mode.value = value
        com.sxdpz23.navisarthi.navigation.core.nav.SpokenDetail.mode = value
        prefs(context).edit().putString(KEY, value.name).apply()
    }
}
