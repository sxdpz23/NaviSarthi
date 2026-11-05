package com.example.navisarthi.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Phone battery observation is independent of Bluetooth availability and permissions. */
internal class MobileBatteryMonitor(private val context: Context) {
    private val _percent = MutableStateFlow<Int?>(null)
    val percent = _percent.asStateFlow()
    private var registered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) { update(intent) }
    }
    private fun update(intent: Intent) {
        _percent.value = mobileBatteryPercent(intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
            intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1))
    }
    fun setActive(active: Boolean) {
        if (!active) { close(); return }
        if (registered) return
        runCatching {
            val sticky = context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
            registered = true
            sticky?.let(::update)
        }.onFailure { _percent.value = null }
    }
    fun close() {
        if (registered) { runCatching { context.unregisterReceiver(receiver) }; registered = false }
        _percent.value = null
    }
}
