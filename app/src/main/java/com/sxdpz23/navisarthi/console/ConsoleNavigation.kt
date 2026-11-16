package com.sxdpz23.navisarthi.console

import com.sxdpz23.navisarthi.data.SharedLocation
import com.sxdpz23.navisarthi.dash.nav.Route
import com.sxdpz23.navisarthi.dash.nav.RouteMode

/** A journey is independent of its display transport and never implicitly connects a device. */
interface ConsoleNavigation {
    fun startJourney(stops: List<SharedLocation>, mode: RouteMode, previews: List<Route>, offline: Boolean = false)
    fun remainingJourney(): List<SharedLocation>?
    fun exitNavigation()
}
