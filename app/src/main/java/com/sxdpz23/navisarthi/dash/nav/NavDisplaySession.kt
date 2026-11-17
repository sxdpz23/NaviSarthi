package com.sxdpz23.navisarthi.dash.nav

import android.content.Context
import android.location.Location
import com.sxdpz23.navisarthi.console.ConsoleType
import com.sxdpz23.navisarthi.data.SharedLocation
import com.sxdpz23.navisarthi.navigation.core.nav.NavSession
import com.sxdpz23.navisarthi.navigation.ui.map.MapUiState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class DashRouteColor { BLUE, VEHICLE_ACCENT, GOLDEN_LINE }

/** A display of Nav's session, never another navigation engine. */
data class NavDisplayState(
    val active: Boolean = false,
    val target: ConsoleType? = null,
    val phoneVisible: Boolean = false,
    val editing: Boolean = false,
    val perspective: Boolean = false,
    val headingUp: Boolean = true,
    val locationIntervalMs: Long = 1_000L,
    val showAlternatives: Boolean = false,
    val routeColor: DashRouteColor = DashRouteColor.BLUE,
    val nav: NavSession.State = NavSession.State(),
    val fix: Location? = null,
    val alternatives: List<com.sxdpz23.navisarthi.navigation.core.model.Route> = emptyList(),
    val basemapArchive: String? = null,
    val remainingStops: List<SharedLocation> = emptyList(),
)

object NavDisplaySession {
    private val mutable = MutableStateFlow(NavDisplayState())
    val state = mutable.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var phoneTimeout: Job? = null
    private var binding: Job? = null
    private var session: NavSession? = null
    private var stopNavigation: (() -> Unit)? = null
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("nav_display", Context.MODE_PRIVATE)
        mutable.update { it.copy(
            perspective = prefs?.getBoolean("perspective", false) ?: false,
            showAlternatives = prefs?.getBoolean("alternatives", false) ?: false,
            routeColor = runCatching { DashRouteColor.valueOf(prefs?.getString("route_color", null) ?: "BLUE") }.getOrDefault(DashRouteColor.BLUE),
        ) }
    }

    fun setTarget(target: ConsoleType?) {
        if (state.value.target == target) return
        if (state.value.target != null) requestStop() // stop the old console, not a cold-start link
        mutable.update { it.copy(target = target, active = false, phoneVisible = false, editing = false) }
    }

    fun bind(context: Context, owner: CoroutineScope, map: StateFlow<MapUiState>, nav: NavSession, onStop: () -> Unit) {
        if (prefs == null) init(context)
        binding?.cancel()
        session = nav
        stopNavigation = onStop
        binding = owner.launch {
            combine(map, nav.state) { view, progress -> view to progress }.collect { (view, progress) ->
                val active = progress.navigating && state.value.target != null
                val stops = nav.remainingStops().map { SharedLocation(it.label, it.location.lat, it.location.lng) } +
                    listOfNotNull(progress.route?.polyline?.lastOrNull()?.let { SharedLocation(progress.destinationLabel, it.lat, it.lng) })
                mutable.update { previous -> previous.copy(
                    active = active, nav = progress, basemapArchive = view.basemapArchive, headingUp = !view.navNorthUp,
                    alternatives = view.routes.filter { it.polyline != progress.route?.polyline }, remainingStops = stops,
                    phoneVisible = active && previous.active && previous.phoneVisible,
                    editing = active && previous.active && previous.editing,
                ) }
            }
        }
    }

    fun publishFix(location: Location) { mutable.update { it.copy(fix = Location(location)) } }
    fun setLocationInterval(intervalMs: Long) {
        mutable.update { it.copy(locationIntervalMs = intervalMs.coerceIn(500L, 5_000L)) }
    }
    fun setPerspective(enabled: Boolean) {
        prefs?.edit()?.putBoolean("perspective", enabled)?.apply()
        mutable.update { it.copy(perspective = enabled) }
    }
    fun setAlternatives(enabled: Boolean) {
        prefs?.edit()?.putBoolean("alternatives", enabled)?.apply()
        mutable.update { it.copy(showAlternatives = enabled) }
    }
    fun setRouteColor(color: DashRouteColor) {
        prefs?.edit()?.putString("route_color", color.name)?.apply()
        mutable.update { it.copy(routeColor = color) }
    }
    /** Viewing is bounded; editing stays open until the rider explicitly returns to Dash/Pod. */
    fun showPhone(edit: Boolean = false) {
        phoneTimeout?.cancel()
        mutable.update { it.copy(phoneVisible = true, editing = edit) }
        if (!edit) phoneTimeout = scope.launch { delay(120_000); hidePhone() }
    }
    fun hidePhone() {
        phoneTimeout?.cancel(); phoneTimeout = null
        mutable.update { it.copy(phoneVisible = false, editing = false) }
    }
    fun requestStop() {
        stopNavigation?.invoke() ?: session?.stop()
        hidePhone()
    }
    fun skipStop() {
        val current = session ?: return
        val fix = state.value.fix ?: return
        val stops = current.remainingStops()
        if (stops.isNotEmpty()) current.setStops(stops.drop(1),
            com.sxdpz23.navisarthi.navigation.core.model.LatLng(fix.latitude, fix.longitude), "skip stop")
    }
}
