package com.sxdpz23.navisarthi.console

/** Commit a vehicle change only after the previous session has been fully stopped. */
class VehicleSessionCoordinator(
    initialVehicle: String?,
    private val stop: () -> Unit,
    private val persist: (String) -> Unit,
    private val activate: (ConsoleType) -> Unit,
) {
    private var selected = initialVehicle
    fun select(vehicle: String, type: ConsoleType): Boolean {
        if (selected == vehicle) return false
        stop()
        persist(vehicle)
        activate(type)
        selected = vehicle
        return true
    }
}
