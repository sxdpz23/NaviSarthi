package com.sxdpz23.navisarthi.console

enum class ConsoleType(val label: String, val tabLabel: String) {
    TRIPPER_POD("Tripper Pod", "Pod"),
    TRIPPER_DASH("Tripper Dash", "Dash");

    val navigationPermissions: List<String> get() = listOf("android.permission.ACCESS_FINE_LOCATION") + when (this) {
        TRIPPER_POD -> listOf("android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT")
        TRIPPER_DASH -> listOf("android.permission.NEARBY_WIFI_DEVICES")
    }
}

/** Invalidates callbacks and suspended work belonging to a previous connection. */
class SessionEpoch {
    private var value = 0L
    @Synchronized fun next(): Long = ++value
    @Synchronized fun current(token: Long): Boolean = token == value
}
