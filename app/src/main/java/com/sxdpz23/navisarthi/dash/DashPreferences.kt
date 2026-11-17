package com.sxdpz23.navisarthi.dash

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object DashPreferences {
    private lateinit var prefs: android.content.SharedPreferences
    private val auto = MutableStateFlow(true)
    private val screen = MutableStateFlow(true)
    private val awake = MutableStateFlow(true)
    val autoConnect = auto.asStateFlow()
    val screenOff = screen.asStateFlow()
    val keepAwake = awake.asStateFlow()
    fun init(context: Context) {
        prefs = context.getSharedPreferences("dash_preferences", Context.MODE_PRIVATE)
        auto.value = prefs.getBoolean("auto_connect", true)
        screen.value = prefs.getBoolean("screen_off", true)
        awake.value = prefs.getBoolean("keep_awake", true)
    }
    fun setAutoConnect(value: Boolean) { prefs.edit().putBoolean("auto_connect", value).apply(); auto.value = value }
    fun setScreenOff(value: Boolean) { prefs.edit().putBoolean("screen_off", value).apply(); screen.value = value }
    fun setKeepAwake(value: Boolean) { prefs.edit().putBoolean("keep_awake", value).apply(); awake.value = value }
}
