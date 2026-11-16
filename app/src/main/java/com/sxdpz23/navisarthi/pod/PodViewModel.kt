package com.sxdpz23.navisarthi.pod

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sxdpz23.navisarthi.console.ConsoleNavigation
import com.sxdpz23.navisarthi.console.SessionEpoch
import com.sxdpz23.navisarthi.dash.map.LocationTracker
import com.sxdpz23.navisarthi.dash.nav.*
import com.sxdpz23.navisarthi.data.SharedLocation
import com.sxdpz23.navisarthi.data.RideRecorder
import com.sxdpz23.navisarthi.data.SyncRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class PodNavigation(
    val destination: String? = null,
    val cue: String = "Choose a route to start navigation",
    val turnMeters: Double? = null,
    val remainingMeters: Double? = null,
    val etaMinutes: Int? = null,
    val stopNumber: Int = 0,
    val stopCount: Int = 0,
    val gpsReady: Boolean = false,
    val error: String? = null,
    val arrived: Boolean = false,
)

/** Headless navigation uses the shared routing, progress, GPS, voice and recording components. */
class PodViewModel(app: Application) : AndroidViewModel(app), ConsoleNavigation {
    val ble = PodBleClient(app)
    private val prefs = app.getSharedPreferences("tripper_pod", Application.MODE_PRIVATE)
    private val mutable = MutableStateFlow(PodNavigation())
    val navigation = mutable.asStateFlow()
    private val nightState = MutableStateFlow(prefs.getBoolean("night", false))
    val night = nightState.asStateFlow()
    private val bottomState = MutableStateFlow(PodBottomLine.entries.firstOrNull {
        it.name == prefs.getString("bottom", null)
    } ?: PodBottomLine.DISTANCE)
    val bottom = bottomState.asStateFlow()
    private val clockState = MutableStateFlow(prefs.getBoolean("clock24", true))
    val clock24 = clockState.asStateFlow()
    private val autoState = MutableStateFlow(ble.autoConnect)
    val autoConnect = autoState.asStateFlow()
    private var enabled = false
    private var autoConnectAttempted = false
    private val epoch = SessionEpoch()
    private val location = LocationTracker(app)
    private val voice = VoiceManager.get(app)
    private val recorder = RideRecorder()
    private val repo = SyncRepository.get(app)
    private val direction = RerouteDirection()
    private var stops = emptyList<SharedLocation>()
    private var previews = emptyList<Route>()
    private var index = 0
    private var mode = RouteMode.Car
    private var offline = false
    private var route: Route? = null
    private var loop: Job? = null
    private var fetch: Job? = null
    private var lastFetch = 0L
    private var offRouteSince = 0L
    private var lastRecordedFix = 0L

    init {
        viewModelScope.launch {
            ble.state.map { it.stage }.distinctUntilChanged().collect { stage ->
                if (enabled && stage in listOf(PodStage.CONNECTING, PodStage.RECONNECTING, PodStage.AUTHORIZING, PodStage.PIN_REQUIRED, PodStage.READY)) {
                    runCatching { PodKeepAliveService.start(app) }.onFailure {
                        mutable.update { nav -> nav.copy(error = "Background connection unavailable. Keep the app open and review battery settings.") }
                    }
                } else if (stage in listOf(PodStage.OFFLINE, PodStage.ERROR)) {
                    // A retry can run without a foreground service for at most the bounded BLE window.
                    if (stops.isEmpty()) PodKeepAliveService.stop(app)
                }
            }
        }
    }

    fun setConsoleEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        autoConnectAttempted = false
        ble.setEnabled(value)
        if (!value) { exitNavigation(); PodKeepAliveService.stop(getApplication()) }
    }
    fun autoConnectSaved() {
        if (!enabled || autoConnectAttempted) return
        autoConnectAttempted = true
        if (ble.autoConnect && ble.savedAddress != null && ble.hasPermissions() &&
            ble.state.value.stage in listOf(PodStage.OFFLINE, PodStage.ERROR)) ble.connect()
    }
    fun setNight(value: Boolean) { nightState.value = value; prefs.edit().putBoolean("night", value).apply(); updateGuidance() }
    fun setBottom(value: PodBottomLine) { bottomState.value = value; prefs.edit().putString("bottom", value.name).apply(); updateGuidance() }
    fun setClock24(value: Boolean) { clockState.value = value; prefs.edit().putBoolean("clock24", value).apply(); ble.syncClock() }
    fun setAutoConnect(value: Boolean) { autoState.value = value; ble.autoConnect = value }
    fun disconnect() { exitNavigation(); ble.disconnect(); PodKeepAliveService.stop(getApplication()) }
    fun forget() { disconnect(); ble.forget() }

    override fun startJourney(stops: List<SharedLocation>, mode: RouteMode, previews: List<Route>, offline: Boolean) {
        if (!enabled) return
        require(stops.isNotEmpty() && stops.all { it.lat != null && it.lng != null })
        exitNavigation()
        this.stops = stops.toList(); this.previews = previews.toList(); this.mode = mode; this.offline = offline
        index = 0
        location.setUpdateInterval(1_000)
        location.start()
        recorder.start()
        lastRecordedFix = 0L
        runCatching { PodKeepAliveService.start(getApplication()) }.onFailure {
            mutable.update { it.copy(error = "Could not start background navigation. Keep the app open.") }
        }
        activateStop()
        loop = viewModelScope.launch {
            while (isActive && enabled && this@PodViewModel.stops.isNotEmpty()) {
                updateGuidance()
                delay(1_000)
            }
        }
    }

    private fun activateStop() {
        epoch.next(); fetch?.cancel(); fetch = null
        direction.clear(); offRouteSince = 0L; lastFetch = 0L
        route = previews.getOrNull(index)?.takeIf { it.geometry.size >= 2 }
        voice.resetTrip()
        mutable.value = PodNavigation(destination = stops[index].name, cue = "Waiting for GPS",
            stopNumber = index + 1, stopCount = stops.size)
    }

    override fun remainingJourney(): List<SharedLocation>? = stops.drop(index).takeIf {
        it.isNotEmpty() && !navigation.value.arrived
    }

    override fun exitNavigation() {
        epoch.next(); fetch?.cancel(); fetch = null; loop?.cancel(); loop = null
        location.stop(); direction.clear()
        if (recorder.isRecording) recorder.stop()?.let { repo.addRide(it) }
        voice.stopGuidance()
        stops = emptyList(); previews = emptyList(); index = 0; route = null
        ble.screen(null)
        mutable.value = PodNavigation()
        if (ble.state.value.stage != PodStage.READY) PodKeepAliveService.stop(getApplication())
    }

    private fun updateGuidance() {
        if (!enabled || stops.isEmpty() || navigation.value.arrived) return
        val fix = location.location.value ?: location.lastKnown()
        val now = SystemClock.elapsedRealtime()
        if (fix == null || !fix.hasAccuracy() || fix.accuracy > 35f ||
            (SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos) !in 0..5_000_000_000L) {
            mutable.update { it.copy(gpsReady = false, cue = "Waiting for accurate GPS", turnMeters = null) }
            ble.screen(null)
            return
        }
        if (fix.elapsedRealtimeNanos != lastRecordedFix && recorder.isRecording) {
            recorder.add(fix.latitude, fix.longitude, if (fix.hasSpeed()) fix.speed else Float.NaN,
                fix.accuracy, fix.elapsedRealtimeNanos / 1_000_000L)
            lastRecordedFix = fix.elapsedRealtimeNanos
        }
        val heading = direction.heading(RerouteDirection.Fix(GeoPoint(fix.latitude, fix.longitude),
            fix.bearing.takeIf { fix.hasBearing() }, fix.speed, fix.accuracy, fix.elapsedRealtimeNanos / 1_000_000,
            fix.bearingAccuracyDegrees.takeIf { fix.hasBearingAccuracy() }), now)
        val current = route
        if (current == null) {
            mutable.update { it.copy(gpsReady = true, cue = if (offline) "Offline route unavailable" else "Calculating route") }
            if (!offline) requestRoute(GeoPoint(fix.latitude, fix.longitude), heading)
            ble.screen(null)
            return
        }
        val point = GeoPoint(fix.latitude, fix.longitude)
        val progress = NavEngine.progress(current, point, fix.speed)
        if (progress.offRoute) {
            if (offRouteSince == 0L) offRouteSince = now
            mutable.update { it.copy(gpsReady = true, cue = if (offline) "Off route · return to the saved route" else "Off route · checking direction", turnMeters = null) }
            ble.screen(null)
            if (!offline && now - offRouteSince >= 5_000 && heading != null) {
                ble.screen(PodProtocol.recalculating(night.value))
                requestRoute(point, heading)
            }
            return
        }
        offRouteSince = 0L
        val destination = current.destination
        val arrived = progress.arrived && destination != null && GeoPoint.distMeters(point, destination) <= 5.0
        if (arrived && index + 1 < stops.size) { index++; activateStop(); return }
        val maneuver = if (arrived) Maneuver(ManeuverType.ARRIVE, "Arrived", point, current.totalMeters) else progress.nextManeuver
        val next = current.maneuvers.firstOrNull { it.cumulativeMeters > (maneuver?.cumulativeMeters ?: Double.MAX_VALUE) + 1 }
        val arrival = java.time.LocalTime.now().plusSeconds(progress.etaSeconds.toLong().coerceAtLeast(0))
        ble.screen(PodProtocol.guidance(maneuver, next, if (arrived) 0.0 else progress.distanceToManeuverM,
            if (arrived) 0.0 else progress.remainingMeters, if (arrived) 0.0 else progress.etaSeconds,
            night.value, bottom.value, arrival.hour, arrival.minute))
        voice.maybeAnnounce(maneuver, if (arrived) 0.0 else progress.distanceToManeuverM,
            if (arrived) 0.0 else progress.remainingMeters.coerceAtLeast(26.0))
        mutable.update { it.copy(gpsReady = true, arrived = arrived, error = null,
            cue = if (arrived) "You have arrived" else maneuver?.cue ?: "Continue straight",
            turnMeters = if (arrived) 0.0 else progress.distanceToManeuverM,
            remainingMeters = if (arrived) 0.0 else progress.remainingMeters,
            etaMinutes = (progress.etaSeconds / 60).toInt()) }
        if (arrived) {
            loop?.cancel(); loop = null; location.stop()
            if (recorder.isRecording) recorder.stop()?.let { repo.addRide(it) }
        }
    }

    private fun requestRoute(origin: GeoPoint, heading: Int?) {
        val now = SystemClock.elapsedRealtime()
        if (fetch?.isActive == true || (lastFetch != 0L && now - lastFetch < 15_000)) return
        val stop = stops.getOrNull(index) ?: return
        val token = epoch.next()
        lastFetch = now
        fetch = viewModelScope.launch {
            val result = Router.route(origin, GeoPoint(stop.lat!!, stop.lng!!), mode, heading)
            if (!isActive || !enabled || !epoch.current(token)) return@launch
            if (result != null && result.geometry.size >= 2) {
                route = result; offRouteSince = 0L; voice.resetTrip()
            } else mutable.update { it.copy(error = "Route unavailable. Check mobile data; retrying while navigation is active.") }
        }
    }
    override fun onCleared() {
        enabled = false; exitNavigation(); ble.setEnabled(false)
        PodKeepAliveService.stop(getApplication())
        super.onCleared()
    }
}
