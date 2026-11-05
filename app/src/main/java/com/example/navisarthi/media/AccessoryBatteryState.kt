package com.example.navisarthi.media

enum class AccessoryIcon { MOBILE, HEADPHONES, SPEAKER, WATCH, KEYBOARD, BLUETOOTH }

internal data class AccessoryBattery(val id: String, val name: String, val percent: Int,
    val icon: AccessoryIcon = AccessoryIcon.BLUETOOTH)

/** Only a live connection plus a valid reported level is eligible for display. */
internal class AccessoryBatteryState {
    private val levels = mutableMapOf<String, Int>()
    private var connected = emptyMap<String, String>()
    private var icons = emptyMap<String, AccessoryIcon>()
    fun connections(devices: Map<String, String>, icons: Map<String, AccessoryIcon> = emptyMap()) {
        // Pending reports may arrive before profile callbacks; explicit disconnect clears them.
        connected = devices.toMap()
        this.icons = icons.toMap()
    }
    fun report(id: String, percent: Int) {
        if (percent in 0..100) levels[id] = percent else levels.remove(id)
    }
    fun disconnect(id: String) { connected = connected - id; icons = icons - id; levels.remove(id) }
    fun clear() { connected = emptyMap(); icons = emptyMap(); levels.clear() }
    fun readings(): List<AccessoryBattery> = connected.mapNotNull { (id, name) ->
        levels[id]?.let { AccessoryBattery(id, name.ifBlank { "Accessory" }, it, icons[id] ?: AccessoryIcon.BLUETOOTH) }
    }.sortedWith(compareBy<AccessoryBattery> { it.name.lowercase() }.thenBy { it.id })
}

internal fun selectedAccessoryBattery(readings: List<AccessoryBattery>, nowMs: Long): AccessoryBattery? =
    if (readings.isEmpty()) null else readings[((nowMs.coerceAtLeast(0) / 5000) % readings.size).toInt()]

internal fun accessoryBatteryLabel(readings: List<AccessoryBattery>, nowMs: Long, iconsOnly: Boolean = false): String? {
    val item = selectedAccessoryBattery(readings, nowMs) ?: return null
    if (iconsOnly) return "${item.percent}%"
    val name = item.name.filter { !it.isISOControl() }.trim().ifBlank { "Accessory" }
    val short = if (name.length > 10) name.take(9) + "…" else name
    return if (item.icon == AccessoryIcon.MOBILE) "Mobile ${item.percent}%" else "BT $short ${item.percent}%"
}

internal fun mobileBatteryPercent(level: Int, scale: Int): Int? =
    if (scale <= 0 || level !in 0..scale) null else ((level.toLong() * 100 + scale / 2) / scale).toInt()

internal fun combinedBatteryReadings(mobilePercent: Int?, accessories: List<AccessoryBattery>): List<AccessoryBattery> =
    buildList {
        if (mobilePercent != null && mobilePercent in 0..100)
            add(AccessoryBattery("local-phone", "Mobile", mobilePercent, AccessoryIcon.MOBILE))
        addAll(accessories)
    }
