package com.example.navisarthi.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import com.example.navisarthi.dash.map.FixFreshness
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.navisarthi.dash.DashKeepAliveService
import com.example.navisarthi.dash.DashSession
import com.example.navisarthi.dash.DashState
import com.example.navisarthi.dash.DashWifiManager
import com.example.navisarthi.dash.WifiConnStatus
import com.example.navisarthi.dash.map.LocationTracker
import com.example.navisarthi.dash.map.MapRenderer
import com.example.navisarthi.dash.map.Mercator
import com.example.navisarthi.dash.map.TileProvider
import com.example.navisarthi.dash.nav.GeoPoint
import com.example.navisarthi.dash.nav.Route
import com.example.navisarthi.dash.nav.RouteMode
import com.example.navisarthi.dash.nav.Router
import com.example.navisarthi.dash.protocol.DashCommands
import com.example.navisarthi.dash.video.DashEncoder
import com.example.navisarthi.dash.video.NalProcessor
import com.example.navisarthi.dash.video.RtpPacketizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ConnStage { OFFLINE, WIFI, AUTH, STREAMING, ERROR }

/**
 * GPS health shown to the rider so a bad signal degrades *visibly* instead of the marker just
 * freezing. GOOD = fresh, accurate. WEAK = fresh but imprecise (or fixes coming slowly) → the
 * map eases gently toward truth, no jitter. LOST = no fresh fix for a few seconds → marker holds
 * its last known spot and the dash says so.
 */
enum class GpsStatus { GOOD, WEAK, LOST }

data class DashUiState(
    val stage: ConnStage = ConnStage.OFFLINE,
    val frameCount: Int = 0,
    val lastButton: String? = null,
    val ssid: String = "",            // empty until a dash is discovered/paired (see DashConfig)
    val wifiPassword: String = "12345678",  // RE Tripper factory passphrase; rider-overridable
    val destinationName: String? = null,
    val stopNumber: Int = 0,
    val stopCount: Int = 0,
    val errorMessage: String? = null,
    val retryAttempt: Int = 0,        // >0 while auto-retrying the flaky auth handshake
    val mapZoom: Int = 19,
    val remainingKm: Double? = null,
    val etaMinutes: Int? = null,
    val maneuver: String? = null,
    val hasGps: Boolean = false,
    val gpsStatus: GpsStatus = GpsStatus.LOST,
    val hasRoute: Boolean = false,
    val offRoute: Boolean = false,
    val recalculating: Boolean = false, // off-route long enough / actively rerouting → ETA frozen, pill shows "Recalculating"
    val headingUp: Boolean = true,
    val perspective: Boolean = false,
    val goldenRoute: Boolean = false,
    val trafficEnabled: Boolean = false,
    val trafficConfigured: Boolean = false,
    val mapStyle: MapRenderer.Style = MapRenderer.Style.Light,
    val followMode: Boolean = true,
    val thermal: String = "OK",
    val needsWifiOn: Boolean = false,   // Wi‑Fi radio is off → UI prompts to enable it
    val needsLocationOn: Boolean = false, // Location services (master toggle) off → can't resolve dash SSID for auth
    // For the in-app Google Map view
    val riderLat: Double? = null,
    val riderLng: Double? = null,
    val riderBearing: Float = 0f,
    val destLatLng: Pair<Double, Double>? = null,
    val routePoints: List<GeoPoint> = emptyList(),
) {
    val trafficAvailable: Boolean get() = mapStyle != MapRenderer.Style.Black || !goldenRoute
}

class DashViewModel(app: Application) : AndroidViewModel(app) {
    private val _ui = MutableStateFlow(DashUiState())
    val ui = _ui.asStateFlow()

    // The EXACT frame being H.264-encoded to the dash, published for the in-app "Dash view"
    // so the phone preview is pixel-identical to the bike screen (same renderer, not a second
    // map engine). A fresh immutable snapshot per redraw — the encoder's own bitmap is mutated
    // on a background thread and recycled on teardown, so handing that out would tear/crash.
    private val _previewFrame = MutableStateFlow<Bitmap?>(null)
    val previewFrame = _previewFrame.asStateFlow()

    private val session     = DashSession(viewModelScope)
    private val wifiManager = DashWifiManager(app, viewModelScope)
    private val dashConfig  = com.example.navisarthi.dash.DashConfig.get(app)
    private val voice        = com.example.navisarthi.dash.nav.VoiceManager.get(app)
    private val repo         = com.example.navisarthi.data.SyncRepository.get(app)
    private val recorder     = com.example.navisarthi.data.RideRecorder()
    private var recordJob: Job? = null
    private val tiles       = TileProvider(app, viewModelScope)
    private val location    = LocationTracker(app)
    private val trafficTiles = com.example.navisarthi.dash.map.TrafficTiles(app, viewModelScope)
    private val mapRenderer = MapRenderer(tiles, trafficTiles)

    init {
        _ui.update { it.copy(trafficConfigured = trafficTiles.configured, trafficEnabled = trafficTiles.enabled) }
    }

    fun configureTraffic(key: String) {
        trafficTiles.configure(key)
        if (!trafficTiles.configured) trafficTiles.setEnabled(false)
        _ui.update { it.copy(trafficConfigured = trafficTiles.configured, trafficEnabled = trafficTiles.enabled) }
        lastSignature = ""
    }

    fun setTrafficEnabled(enabled: Boolean) {
        trafficTiles.setEnabled(enabled && trafficTiles.configured && _ui.value.trafficAvailable)
        _ui.update { it.copy(trafficEnabled = trafficTiles.enabled) }
        lastSignature = ""
    }
    private val accessoryBattery = com.example.navisarthi.media.BluetoothAccessoryBattery(app)
    val accessoryBatteryEnabled = accessoryBattery.enabled
    val accessoryBatteryStatus = accessoryBattery.status
    val accessoryIconsOnly = accessoryBattery.iconsOnly
    fun setAccessoryIconsOnly(enabled: Boolean) = accessoryBattery.setIconsOnly(enabled)
    fun hasAccessoryBluetoothAccess() = accessoryBattery.hasAccess()
    fun setAccessoryBatteryEnabled(enabled: Boolean) = accessoryBattery.setEnabled(enabled)
    fun refreshAccessoryBattery() = accessoryBattery.refresh()
    private fun currentBatteryPillReadings() = if (accessoryBattery.enabled.value)
        com.example.navisarthi.media.combinedBatteryReadings(accessoryBattery.mobilePercent.value, accessoryBattery.readings.value)
        else emptyList()
    private fun currentAccessoryBatteryLabel(): String? = com.example.navisarthi.media.accessoryBatteryLabel(
        currentBatteryPillReadings(), android.os.SystemClock.elapsedRealtime(), accessoryBattery.iconsOnly.value)
    private fun currentAccessoryBatteryIcon(): com.example.navisarthi.media.AccessoryIcon? =
        if (accessoryBattery.iconsOnly.value) com.example.navisarthi.media.selectedAccessoryBattery(
            currentBatteryPillReadings(), android.os.SystemClock.elapsedRealtime())?.icon else null

    private val mediaInfo   = com.example.navisarthi.media.MediaInfoProvider(app)
    val musicEnabled = mediaInfo.enabled
    val nowPlaying = mediaInfo.nowPlaying
    val musicStatus = mediaInfo.status
    fun setMusicEnabled(enabled: Boolean) = mediaInfo.setEnabled(enabled)
    fun refreshMusic() = mediaInfo.refresh()
    fun musicDiagnostics(): String =
        "NaviSarthi ${com.example.navisarthi.BuildConfig.VERSION_NAME} (${com.example.navisarthi.BuildConfig.VERSION_CODE})\n" +
            mediaInfo.diagnostics() + "\n" + session.musicDiagnostics()
    fun startMusicScreen() {
        mediaInfo.start(com.example.navisarthi.media.MusicObservationOwner.SCREEN)
        accessoryBattery.setScreenVisible(true)
    }
    fun stopMusicScreen() {
        mediaInfo.stop(com.example.navisarthi.media.MusicObservationOwner.SCREEN)
        accessoryBattery.setScreenVisible(false)
    }
    fun musicNext() = mediaInfo.skipNext()
    fun musicPrevious() = mediaInfo.skipPrevious()
    fun musicPlayPause() = mediaInfo.togglePlayback()

    private val consoleHistory = com.example.navisarthi.media.ConsoleCallHistory(app)
    val callHistoryEnabled = consoleHistory.enabled
    val callHistoryStatus = consoleHistory.status
    val favoritesEnabled = consoleHistory.favoritesEnabled
    val consoleDialingEnabled = consoleHistory.dialingEnabled
    val missedBadgeEnabled = consoleHistory.badgeEnabled
    val consoleDialStatus = consoleHistory.dialStatus
    private val dialSnapshot = com.example.navisarthi.media.ConsoleDialSnapshot()
    private var historyCategory: com.example.navisarthi.dash.protocol.CallHistoryCategory? = null
    private var historyRequestJob: Job? = null
    private var historyAuditJob: Job? = null
    private var missedBadgeJob: Job? = null
    private var lastHistoryRows: List<com.example.navisarthi.media.ConsoleCallEntry>? = null
    fun hasCallHistoryAccess() = consoleHistory.hasAccess()
    fun hasFavoriteContactsAccess() = consoleHistory.hasContactsAccess()
    fun hasConsoleDialAccess() = consoleHistory.hasDialAccess()
    fun refreshCallHistoryAccess() {
        consoleHistory.refreshAccess()
        historyCategory?.let {
            if (!consoleHistory.categoryAllowed(it)) { dialSnapshot.clear(); requestCallHistory(it) }
        }
        refreshMissedBadge()
    }
    fun setCallHistoryEnabled(enabled: Boolean) {
        consoleHistory.setEnabled(enabled)
        dialSnapshot.clear()
        historyCategory?.let { requestCallHistory(it) }
    }
    fun setFavoritesEnabled(enabled: Boolean) {
        consoleHistory.setFavorites(enabled)
        dialSnapshot.clear()
        historyCategory?.let { requestCallHistory(it) }
    }
    fun setConsoleDialingEnabled(enabled: Boolean) { consoleHistory.setDialing(enabled) }
    fun setMissedBadgeEnabled(enabled: Boolean) {
        consoleHistory.setBadge(enabled)
        refreshMissedBadge()
    }
    private fun refreshMissedBadge() {
        val previous = missedBadgeJob
        missedBadgeJob = viewModelScope.launch {
            previous?.cancelAndJoin()
            val count = consoleHistory.missedCount()
            session.updateMissedCallCount(if (consoleHistory.badgeEnabled.value && consoleHistory.hasAccess()) count else 0)
        }
    }
    private fun requestCallHistory(category: com.example.navisarthi.dash.protocol.CallHistoryCategory, force: Boolean = true) {
        val previous = historyRequestJob
        if (force) dialSnapshot.clear()
        historyRequestJob = viewModelScope.launch {
            previous?.cancelAndJoin()
            if (historyCategory != category) lastHistoryRows = null
            historyCategory = category
            val rows = consoleHistory.entries(category)
            if (force || rows != lastHistoryRows) {
                dialSnapshot.clear()
                val transferred = session.sendCallHistory(category, rows.map { it.label }) { consoleHistory.categoryAllowed(category) }
                if (transferred && consoleHistory.categoryAllowed(category)) dialSnapshot.displayed(category, rows)
                lastHistoryRows = rows
            }
        }
    }
    private fun dialFromConsole(selection: com.example.navisarthi.dash.protocol.ConsoleDialSelection) {
        if (_ui.value.stage != ConnStage.STREAMING || !consoleHistory.dialingEnabled.value ||
            !consoleHistory.hasDialAccess() || !consoleHistory.categoryAllowed(selection.category) ||
            com.example.navisarthi.media.CallInfoProvider.incomingCall.value != null) return
        val now = android.os.SystemClock.elapsedRealtime()
        val number = dialSnapshot.number(selection.category, selection.index, now) ?: return
        if (consoleHistory.dial(number)) dialSnapshot.dialRequested(now)
    }

    private val callController = com.example.navisarthi.media.CallController(app)
    private var mediaObserveJob: Job? = null
    private val powerManager = app.getSystemService(Application.POWER_SERVICE) as PowerManager

    private var encoder: DashEncoder? = null
    private var streamJob: Job? = null
    // Even presentation clock (ms) feeding the RTP 90 kHz timestamp — advances by exactly one
    // frame interval per encoded frame so the dash gets an evenly-spaced timeline (NOT wall clock,
    // which inherited the loop's jitter). Paired with absolute-grid send pacing below.
    @Volatile private var videoPtsMs = 0L
    private var sendPaceMs = 0.0    // EMA of the real frame-send interval — exposes cadence jitter
    private var sendPaceMaxMs = 0L  // worst interval since the last diag line
    // Backstop so a dropped/never-reached connection can't drain the battery forever: if we don't
    // reach STREAMING within this window, give up completely (stop the Wi‑Fi reconnect loop, the
    // foreground service, GPS and the encoder). Cancelled the moment streaming starts.
    private var giveupJob: Job? = null
    private var connLostAlertJob: Job? = null
    private var connLostAlerted = false

    private var userWantsConnection = false
    private var authAttempts = 0       // bounded auto-retry of the (flaky) auth handshake

    // ── Navigation/map state read by the 4 fps frame loop ──
    @Volatile private var destLat: Double? = null
    @Volatile private var destLng: Double? = null
    @Volatile private var route: Route? = null
    @Volatile private var panX = 0f
    @Volatile private var panY = 0f
    @Volatile private var zoom = 19          // nav-level zoom on the dash (street-level default)
    @Volatile private var headingUp = true
    @Volatile private var displayRoute: List<GeoPoint> = emptyList()
    @Volatile private var arrived = false
    private var arrivalMini = false
    private var navigationStartedMs = 0L
    private var arrivalDuration: String? = null
    private var arrivalAverageKmh: Double? = null
    private var arrivalMaxKmh: Double? = null

    fun setGoldenRoute(enabled: Boolean) {
        _ui.update { it.copy(goldenRoute = enabled) }
        lastSignature = ""
    }

    fun setPerspective(enabled: Boolean) {
        _ui.update { it.copy(perspective = enabled) }
        lastSignature = ""
    }

    fun setMapStyle(style: MapRenderer.Style) {
        _ui.update { it.copy(mapStyle = style) }
        lastSignature = ""
    }
    @Volatile private var followMode = true
    @Volatile private var lastManualPanAt = 0L

    // Smoothed camera — eased toward the latest GPS target every frame so the map
    // glides at 8 fps instead of jumping once per 1 Hz fix (the "cheap"/laggy feel).
    private var camLat = 0.0
    private var camLng = 0.0
    private var camHdg = 0f
    private var camInit = false
    private var lastTickNs = 0L          // for framerate-independent smoothing

    // Dead-reckoning: last GPS fix + its velocity, so the camera keeps gliding forward
    // between the 1 Hz fixes instead of easing to a stop (the "laggy" feel at speed).
    private var fixLat = 0.0
    private var fixLng = 0.0
    private var fixBearing = 0f
    private var fixSpeed = 0f             // m/s
    private var fixElapsedMs = 0L
    private var lastFixElapsedNs = 0L
    private var gpsFixIntervalMs = 0.0   // EMA of the gap between fixes — exposes screen-off GPS throttling

    // GPS health surfaced to the dash + phone (computed each tick from accuracy + fix age).
    @Volatile private var gpsStatus = GpsStatus.LOST
    // Held marker position while stationary — deadbands sub-accuracy GPS wander so a parked bike's
    // dot doesn't drift/jitter. Updated to the live fix while moving; reset on teardown.
    private var heldRider: Pair<Double, Double>? = null

    // Smoothed rider position shown on the dash frame (locked to the camera centre so the
    // marker stays put and the map slides under it). null = no GPS.
    @Volatile private var frameRiderLat: Double? = null
    @Volatile private var frameRiderLng: Double? = null

    // Stable ETA (Google-like): the raw estimate jitters with instantaneous speed, so we
    // smooth it and only recompute the absolute arrival clock occasionally — no per-frame churn.
    private var smoothEtaSec = 0.0
    @Volatile private var etaArrivalMs = 0L
    private var lastArrivalCalcMs = 0L
    // The minutes-remaining shown to the rider, refreshed on the same 5 s cadence as the arrival
    // clock so it stops ticking/bouncing every second ("tacky ETA"). null = no active ETA.
    @Volatile private var displayedEtaMin: Int? = null

    // Off-route → reroute, debounced. Now feasible because the app keeps cellular
    // internet while bound to the dash (per-socket binding), so Router can run mid-ride.
    @Volatile private var offRouteSince = 0L
    @Volatile private var lastRerouteAt = 0L
    @Volatile private var rerouting = false
    // Debounce for the "Recalculating" state (ignore 1-frame off-route GPS blips).
    @Volatile private var offRouteStreakStart = 0L
    // Keep "Recalculating" on screen a beat after it clears, so a fast reroute is still seen.
    @Volatile private var recalcHoldUntil = 0L
    // Last lateral distance from the route (m) — logged so flyover/off-route thresholds are tunable.
    @Volatile private var lastSnapDist = -1.0
    // Rolling average of actual moving speed (m/s), held through stops — drives a STEADY ETA
    // instead of one that swings with instantaneous speed at every brake/light.
    @Volatile private var navigationMotion: com.example.navisarthi.data.RideMotionStats? = null

    // Map-matched rider position: snapped onto the route while on it (kills the GPS
    // lane/road jitter), raw GPS when genuinely off-route. Drives the marker + camera.
    @Volatile private var matchedLat: Double? = null
    @Volatile private var matchedLng: Double? = null

    // Frame cache (avoid the expensive redraw when nothing changed)
    private var frameBitmap: Bitmap? = null
    private var lastSignature = ""
    private var lastRedrawAt = 0L

    // Ride diagnostics: one-shot "first frame" marker + a throttle for the periodic ride line.
    @Volatile private var loggedFirstFrame = false
    private var lastDiagMs = 0L

    companion object {
        private const val MANUAL_IDLE_MS = 8_000L
        private const val FORCE_REDRAW_MS = 2_000L
        // How long to keep trying to (re)connect before giving up and freeing all background
        // resources. Covers the flaky initial handshake AND a mid-ride drop; if the dash is truly
        // gone (parked / powered off / out of range) we stop draining instead of retrying forever.
        // 2 min (was 45 s): if the dash AP hangs mid-ride it only comes back after a bike power-cycle,
        // and 45 s wasn't enough to safely pull over + restart before NaviSarthi gave up. With the
        // WiFi layer now retrying through an AP-missing window, this keeps trying long enough that a
        // power-cycle auto-rejoins with no manual step. Still bounded so a truly-gone dash frees power.
        private const val RECONNECT_GIVEUP_MS = 120_000L
        // Alert the rider after this long of sustained (non-transient) link loss — long enough to skip
        // a brief blip that auto-recovers, short enough that they're not riding blind for minutes.
        private const val CONN_LOST_ALERT_MS = 12_000L
        private const val SMOOTH_TAU = 0.20      // camera smoothing time constant (s) — tighter so the map trails the bike less
        // GPS bearing is only meaningful while genuinely moving; below this it's noise that
        // would spin the heading-up map (the "turns to the opposite direction when I stop" bug).
        private const val BEARING_MIN_SPEED = 2.0f   // m/s (~7 km/h)
        // Lead the displayed position by the pipeline latency (camera ease + H.264 encode + RTP +
        // dash decode/display ≈ 0.5 s) so the dash shows where the bike IS, not where it was.
        private const val DISPLAY_LEAD_S = 0.4
        // Cap on how far ahead the constant-velocity predictor projects (s of travel). Bounds the
        // over-shoot if a fix is missed across a real stop / a long dropout; below this it tracks
        // the rider's actual motion so the camera never freezes between sparse (screen-off) fixes.
        private const val MAX_PREDICT_S = 3.0
        // Motion-adaptive frame cadence — the heart of the RE-style power/stability model.
        // Measuringthe dash showed it projects the map at just 2–4 fps with two
        // profiles (q2g: 4 fps/200 kbps "frameToSend"=250 ms, and 2 fps/100 kbps =500 ms) and switches
        // between them. RE gates on battery; we gate on MOTION instead (smarter for our screen-off
        // power goal, and the user explicitly didn't want a battery gate): stream the fast profile
        // while the map is actually changing (driving / panning / zooming), drop to the slow profile
        // when it's static. A few fps is plenty for a map AND never overruns the dash decoder — which
        // is what caused the blink/stutter at our old fixed 24 fps. ACTIVE is the knob to raise if
        // joystick panning feels choppy (at the cost of more decoder load).
        // ACTIVE was 250 ms (4 fps, matched) but that 250 ms quantum IS the ~0.25 s position lag
        // ("catch-up") felt while moving. Raised to 100 ms (10 fps) — comfortably under the 12 fps the
        // field log proved stable on this dash, so position tracks ~2.5× tighter with no overrun risk.
        // IDLE stays low: a static map has no lag to fix and 2 fps saves power.
        // Back to 4 fps to MATCH the dash exactly (measured at ~3.7 fps). The
        // 12 fps experiment was smoother but ~3× the 2.4 GHz WiFi load, which stresses the dash AP and
        // its BT coexistence; 4 fps is the proven-stable envelope the dash decoder never overruns.
        private const val FRAME_MS_ACTIVE = 250L   // 4 fps while moving — matched
        private const val FRAME_MS_IDLE   = 500L   // 2 fps when static — RE low profile
        // Stay on the fast profile this long after the last visible change, so brief pauses (e.g.
        // between joystick nudges, or a GPS fix gap) don't flap the cadence back and forth.
        private const val MOTION_HOLD_MS  = 1_500L
        private const val MAX_AUTH_ATTEMPTS = 4   // the fw 11.63 handshake often needs a couple of tries
        // Dash joystick codes for call control while a call card is showing. UP = answer, DOWN =
        // reject (intuitive accept/decline; verified on the bike that UP is the natural answer press,
        // not IN). The HOME button is left alone — it does the dash's own exit-to-home.
        private const val BTN_ANSWER = 0x06       // joystick UP
        private const val BTN_REJECT = 0x07       // joystick DOWN
        // MAP-mode joystick codes (the dash forwards these directly while projecting, no media-control
        // mode needed) — confirmed in the ride log: 0x13/0x14 with callRinging=false during the map.
        // These give DIRECT zoom like the dash. In/out guess; swap if the ride feels wrong.
        private const val BTN_MAP_ZOOM_IN  = 0x14
        private const val BTN_MAP_ZOOM_OUT = 0x13
        // MEDIA-mode codes (after pressing IN to enter the dash's audio controls): L/R skip tracks on
        // the phone's player, NOT map zoom. Map zoom stays on the direct map-mode codes above.
        // Media codes live in ConsoleMusicInput, including capture-verified 0x05 play/pause.
        // 0x18 opens the native media overlay; it must not also toggle the phone player.
    }

    /** Project a lat/lng forward [distM] metres along [bearingDeg] (great-circle). */
    private fun project(lat: Double, lng: Double, bearingDeg: Double, distM: Double): Pair<Double, Double> {
        val r = 6_371_000.0
        val br = Math.toRadians(bearingDeg)
        val dr = distM / r
        val lat1 = Math.toRadians(lat); val lng1 = Math.toRadians(lng)
        val lat2 = Math.asin(Math.sin(lat1) * Math.cos(dr) + Math.cos(lat1) * Math.sin(dr) * Math.cos(br))
        val lng2 = lng1 + Math.atan2(
            Math.sin(br) * Math.sin(dr) * Math.cos(lat1),
            Math.cos(dr) - Math.sin(lat1) * Math.sin(lat2),
        )
        return Math.toDegrees(lat2) to Math.toDegrees(lng2)
    }

    /**
     * Distance (m) to dead-reckon ahead of the last GPS fix, given the time since it arrived.
     *
     * CONSTANT velocity (distance = speed × time), capped at [MAX_PREDICT_S]. The old version
     * tapered off after ~1 s, so the camera DECELERATED to a near-stop between fixes and then
     * jumped when the next one landed — the "stuck then teleport" feel, especially with the screen
     * off when GPS callbacks slow to multi-second gaps. Projecting at the true speed keeps the
     * camera (and the constant [DISPLAY_LEAD_S] lead) moving smoothly through the gap, so each new
     * fix lands where the camera already is — no jump. The cap bounds over-shoot if the rider
     * stops/turns mid-gap; the next fix (with speed ~0 → predictor off) then eases it back.
     */
    private fun predictedDistance(speedMps: Float, elapsedSec: Double): Double =
        speedMps * elapsedSec.coerceIn(0.0, MAX_PREDICT_S)

    init {
        // Reflect the rider's stored dash WiFi config (SSID may be blank until discovered).
        _ui.update { it.copy(ssid = dashConfig.ssid, wifiPassword = dashConfig.password) }

        // When we connect to a previously-unknown dash by prefix, learn + persist its exact
        // SSID so subsequent connects target it directly (no system picker again).
        wifiManager.onSsidResolved = { learned ->
            dashConfig.ssid = learned
            _ui.update { it.copy(ssid = learned) }
        }

        viewModelScope.launch {
            wifiManager.state.collect { ws ->
                com.example.navisarthi.data.RideDiagnostics.log(
                    "wifi",
                    ws.status.toString() +
                        (ws.ssid.takeIf { it.isNotBlank() }?.let { " ssid=$it" } ?: "") +
                        (ws.error?.let { " err=$it" } ?: ""),
                )
                when (ws.status) {
                    WifiConnStatus.CONNECTED -> {
                        refreshStage()
                        if (userWantsConnection &&
                            session.state.value in listOf(DashState.IDLE, DashState.ERROR)
                        ) {
                            delay(1_200)
                            // Re-check: the user may have hit Disconnect during the delay.
                            if (userWantsConnection &&
                                wifiManager.state.value.status == WifiConnStatus.CONNECTED
                            ) session.connect(_ui.value.ssid, wifiManager.network)
                        }
                    }
                    WifiConnStatus.ERROR -> {
                        // Dash is gone for good (bike powered off / out of range) → the ride is
                        // over. Save it even though the user never tapped Disconnect.
                        stopRecording()
                        // …and release everything connect() acquired (wake/wifi locks, GPS,
                        // encoder loop). These used to leak when a connection ENDED on failure
                        // rather than via the Disconnect button, draining the battery in the
                        // background until the app was force-closed.
                        userWantsConnection = false
                        releaseBackgroundResources()
                        session.disconnect()   // wifi status stays ERROR → stage remains ERROR
                        _ui.update { it.copy(errorMessage = ws.error) }; refreshStage()
                        com.example.navisarthi.data.RideDiagnostics.stop("wifi error / dash gone")
                    }
                    else -> refreshStage()
                }
            }
        }

        viewModelScope.launch {
            session.state.collect { state ->
                com.example.navisarthi.data.RideDiagnostics.log("session", "→ $state")
                refreshStage()
                // Battery backstop: count down to a full give-up unless/until we're actually
                // streaming. Reaching STREAMING cancels it; any non-streaming state (re)arms it.
                if (state == DashState.STREAMING) { cancelReconnectGiveup(); cancelConnLostAlert() }
                else if (userWantsConnection) { armReconnectGiveup(); armConnLostAlert() }
                when (state) {
                    DashState.READY -> { authAttempts = 0; _ui.update { it.copy(retryAttempt = 0) }; startStream() }
                    DashState.STREAMING -> {
                        authAttempts = 0; _ui.update { it.copy(retryAttempt = 0, errorMessage = null) }
                        // Usage: the dash actually started showing the map — the core feature working.
                        com.example.navisarthi.util.Telemetry.logEvent("dash_streaming")
                    }
                    // The handshake to fw 11.63 is flaky — a single failed attempt is normal.
                    // Auto-retry (WiFi is already up) instead of making the rider re-tap Connect.
                    DashState.ERROR -> {
                        if (userWantsConnection &&
                            wifiManager.state.value.status == WifiConnStatus.CONNECTED &&
                            authAttempts < MAX_AUTH_ATTEMPTS
                        ) {
                            authAttempts++
                            _ui.update { it.copy(retryAttempt = authAttempts, errorMessage = null) }
                            delay(1_500)
                            if (userWantsConnection && wifiManager.state.value.status == WifiConnStatus.CONNECTED)
                                session.connect(_ui.value.ssid, wifiManager.network)
                        } else {
                            _ui.update { it.copy(retryAttempt = 0) }
                            // Retries exhausted while the WiFi link is still up → give up fully now
                            // (don't wait out the timer). giveUp() also stops the Wi‑Fi reconnect loop.
                            if (userWantsConnection && authAttempts >= MAX_AUTH_ATTEMPTS &&
                                wifiManager.state.value.status == WifiConnStatus.CONNECTED
                            ) {
                                giveUp("auth exhausted after $authAttempts attempts")
                            }
                        }
                    }
                    else -> {}
                }
            }
        }

        session.onError = { msg ->
            com.example.navisarthi.data.RideDiagnostics.log("error", msg)
            _ui.update { it.copy(errorMessage = msg) }; refreshStage()
        }
        // Joystick handling. While a call card is on the dash, the joystick answers/rejects the call
        // (IN = answer, DOWN = reject) instead of zooming; otherwise RIGHT/LEFT zoom the map. The
        // separate HOME button is intentionally untouched — it does the dash's native exit; its code
        // just falls through to the logged-else branch. Codes verified fw 11.63: IN=0x18, RIGHT=0x09,
        // LEFT=0x0A, DOWN=0x07. Every unmatched code is shown in ui.lastButton so we can retune.
        session.onConsoleDial = { selection -> dialFromConsole(selection) }
        session.onCallHistoryRequest = { category -> requestCallHistory(category) }
        session.onButton = { btn ->
            val code = btn.toInt() and 0xFF
            val call = com.example.navisarthi.media.CallInfoProvider.incomingCall.value
            val ringing = call?.incoming == true
            val active  = call != null && !call.incoming
            val musicAction = com.example.navisarthi.media.ConsoleMusicInput.actionFor(
                code, mediaInfo.enabled.value, ringing, active)
            val label = when {
                // Ringing: IN answers, DOWN rejects. Active call: DOWN hangs up (fixes "can't end").
                ringing && code == BTN_ANSWER -> { answerCall(call!!); "Call answered" }
                ringing && code == BTN_REJECT -> { endCall(call!!);    "Call rejected" }
                active  && code == BTN_REJECT -> { endCall(call!!);    "Call ended" }
                // Map/media controls run whenever a call ISN'T ringing (still usable during an active
                // call — only DOWN is reserved for hang-up, and that's not a map/media code anyway).
                !ringing && code == BTN_MAP_ZOOM_IN  -> { zoomOut(); "Zoom out (map)" }
                !ringing && code == BTN_MAP_ZOOM_OUT -> { zoomIn(); "Zoom in (map)" }
                musicAction != null -> {
                    val dispatched = when (musicAction) {
                        com.example.navisarthi.media.ConsoleMusicAction.NEXT -> mediaInfo.skipNext()
                        com.example.navisarthi.media.ConsoleMusicAction.PREVIOUS -> mediaInfo.skipPrevious()
                        com.example.navisarthi.media.ConsoleMusicAction.TOGGLE -> mediaInfo.togglePlayback()
                        com.example.navisarthi.media.ConsoleMusicAction.VOLUME_UP -> mediaInfo.adjustMusicVolume(true)
                        com.example.navisarthi.media.ConsoleMusicAction.VOLUME_DOWN -> mediaInfo.adjustMusicVolume(false)
                    }
                    if (dispatched) "Music $musicAction requested" else "Music: ${mediaInfo.status.value}"
                }
                else -> "code 0x${code.toString(16).uppercase()}"
            }
            // Persist every button code so untethered tests are diagnosable from the remote pull
            // (the on-screen readout is invisible with the phone screen off / projecting).
            com.example.navisarthi.data.RideDiagnostics.log("button",
                "0x%02X (ringing=%b active=%b) → %s".format(code, ringing, active, label))
            _ui.update { it.copy(lastButton = label) }
        }
    }

    private fun refreshStage() {
        val wifi = wifiManager.state.value.status
        val dash = session.state.value
        val stage = when {
            dash == DashState.STREAMING -> ConnStage.STREAMING
            dash == DashState.ERROR || wifi == WifiConnStatus.ERROR -> ConnStage.ERROR
            dash == DashState.AUTHENTICATING || dash == DashState.CONNECTING || dash == DashState.READY -> ConnStage.AUTH
            wifi == WifiConnStatus.REQUESTING || wifi == WifiConnStatus.CONNECTED -> ConnStage.WIFI
            else -> ConnStage.OFFLINE
        }
        _ui.update { it.copy(stage = stage) }
    }

    // ── Connection ─────────────────────────────────────────────────────────

    fun connect() {
        userWantsConnection = true
        authAttempts = 0
        _ui.update { it.copy(errorMessage = null, retryAttempt = 0, needsWifiOn = false, needsLocationOn = false) }

        // The dash link needs the Wi‑Fi radio on (WifiNetworkSpecifier joins it as a separate
        // per-app network — the phone's current Wi‑Fi/cellular is untouched). Android 10+ won't
        // let us enable Wi‑Fi for the user, so if it's off, surface it immediately and let the UI
        // open the Wi‑Fi panel — far better than a silent 30s timeout.
        if (!wifiManager.isWifiEnabled()) {
            userWantsConnection = false
            _ui.update { it.copy(stage = ConnStage.ERROR, needsWifiOn = true,
                errorMessage = "Turn on Wi‑Fi to connect to the dash") }
            return
        }

        // First pairing needs the EXACT dash SSID. The OS only reveals it (both WiFi scan results AND
        // the connected network's WifiInfo.ssid) when Location Services — the master toggle, NOT just
        // the runtime permission — is ON. With it off the SSID stays empty and the dash silently
        // rejects the encrypted auth handshake (looks like a generic "auth timed out"). Surface the
        // real cause. Only gates FIRST pairing; once the SSID is learned + stored, scanning isn't needed.
        if (dashConfig.needsDiscovery &&
            !com.example.navisarthi.util.DeviceReadiness.locationServicesEnabled(getApplication())) {
            userWantsConnection = false
            _ui.update { it.copy(stage = ConnStage.ERROR, needsLocationOn = true,
                errorMessage = "Turn on Location to find your dash (needed once, to pair)") }
            return
        }

        com.example.navisarthi.data.RideDiagnostics.init(getApplication())
        com.example.navisarthi.data.RideDiagnostics.start("connect")
        // Stamp the running build's identity into the log so a pulled ride is matched 1:1 to the
        // pushed APK — never again analyse a ride and not know which build produced it.
        com.example.navisarthi.data.RideDiagnostics.log(
            "build", "apk=${com.example.navisarthi.util.BuildId.sha12(getApplication())}" +
                " vc=${com.example.navisarthi.BuildConfig.VERSION_CODE} name=${com.example.navisarthi.BuildConfig.VERSION_NAME}",
        )
        com.example.navisarthi.data.RideDiagnostics.log(
            "connect", "ssid='${dashConfig.ssid}' needsDiscovery=${dashConfig.needsDiscovery} dest=${_ui.value.destinationName}",
        )
        loggedFirstFrame = false
        DashKeepAliveService.start(getApplication())
        location.start()
        startRecording()

        // We must know the EXACT dash SSID before connecting — the dash validates it inside
        // the encrypted auth handshake (DashAuth), and Android redacts the SSID of a network
        // we've already joined. So if we don't have it stored, find it from a WiFi scan.
        if (dashConfig.needsDiscovery) {
            wifiManager.findDashSsid(dashConfig.ssidPrefix)?.let { found ->
                dashConfig.ssid = found
                _ui.update { it.copy(ssid = found) }
            }
        }

        when {
            wifiManager.state.value.status == WifiConnStatus.CONNECTED ->
                session.connect(_ui.value.ssid, wifiManager.network)
            // Known SSID (stored or just found by scan) → exact connect + correct auth.
            dashConfig.ssid.isNotBlank() ->
                wifiManager.connect(dashConfig.ssid, dashConfig.password)
            // Couldn't find it in scan results — fall back to prefix discovery so we at
            // least associate (auth may still need the SSID; a rescan usually fixes it).
            else ->
                wifiManager.connect(dashConfig.ssidPrefix, dashConfig.password, prefixMatch = true)
        }
    }

    fun disconnect() {
        userWantsConnection = false
        cancelReconnectGiveup()
        com.example.navisarthi.data.RideDiagnostics.log("connect", "user disconnect")
        stopRecording()        // a connect→disconnect session = one saved ride
        session.disconnect()
        wifiManager.disconnect()
        releaseBackgroundResources()
        refreshStage()
        com.example.navisarthi.data.RideDiagnostics.stop("disconnect")
        // Ride just ended and internet is back (dash WiFi released) — flush the log to Firestore
        // now so it's retrievable even if the app isn't reopened. Test-channel + Firebase-gated.
        com.example.navisarthi.data.DiagnosticsUploader.uploadPending(getApplication())
    }

    /**
     * Release everything that keeps the CPU/GPS/WiFi awake: the foreground service
     * (PARTIAL_WAKE_LOCK + WifiLock), GPS updates, and the encoder/stream loop. Called
     * whenever the connection has ended so the app stops draining the battery in the
     * background — on an explicit disconnect, on a terminal connection failure (dash
     * unreachable / handshake exhausted), and when the ViewModel is cleared.
     */
    private fun releaseBackgroundResources() {
        teardown()                                   // stream loop + encoder + frame bitmap
        location.stop()                              // stop GPS updates
        DashKeepAliveService.stop(getApplication())  // release wake + wifi locks
    }

    /**
     * Complete stop of all background work — including the Wi‑Fi manager's reconnect loop, which
     * [releaseBackgroundResources] alone does NOT stop (that loop re-joins the dash AP every few
     * seconds and was the main source of drain after the dash went away). The session stays in
     * ERROR so the Dash screen still shows "Couldn't connect" + Try again.
     */
    private fun giveUp(reason: String) {
        cancelReconnectGiveup()
        userWantsConnection = false
        session.disconnect()
        wifiManager.disconnect()       // stops the indefinite AP reconnect loop
        releaseBackgroundResources()
        refreshStage()
        com.example.navisarthi.data.RideDiagnostics.log("error", "gave up: $reason — background released")
        com.example.navisarthi.data.RideDiagnostics.stop(reason)
    }

    /** Start the give-up countdown if it isn't already running (idempotent). */
    private fun armReconnectGiveup() {
        if (giveupJob?.isActive == true) return
        giveupJob = viewModelScope.launch {
            delay(RECONNECT_GIVEUP_MS)
            if (session.state.value != DashState.STREAMING) giveUp("reconnect window elapsed")
        }
    }

    private fun cancelReconnectGiveup() { giveupJob?.cancel(); giveupJob = null }

    // Non-silent disconnect alert: this dash-AP-hang failure only recovers after the rider
    // power-cycles the bike, so they must KNOW the link dropped. Fire once after a sustained loss
    // (not a transient blip) via vibration + a BT-routed voice cue, so it carries through the helmet
    // with the phone screen off. Re-armed each non-streaming state; cancelled (and reset) on recovery.
    private fun armConnLostAlert() {
        if (connLostAlertJob?.isActive == true || connLostAlerted) return
        connLostAlertJob = viewModelScope.launch {
            delay(CONN_LOST_ALERT_MS)
            if (session.state.value != DashState.STREAMING && userWantsConnection) {
                connLostAlerted = true
                com.example.navisarthi.data.RideDiagnostics.log("connect", "sustained link loss → rider alert (vibrate + voice)")
                runCatching {
                    val v = getApplication<Application>().getSystemService(android.os.Vibrator::class.java)
                    v?.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300, 200, 300), -1))
                }
                voice.alert("Dash disconnected. Restart the dash to reconnect.")
            }
        }
    }

    private fun cancelConnLostAlert() {
        connLostAlertJob?.cancel(); connLostAlertJob = null
        if (connLostAlerted) {
            connLostAlerted = false
            voice.alert("Dash reconnected")
        }
    }

    // ── Ride recording (the connected session) ───────────────────────────────
    private fun startRecording() {
        if (recorder.isRecording) return
        recorder.start()
        recordJob = viewModelScope.launch {
            location.location.collect { loc ->
                if (loc != null) recorder.add(loc.latitude, loc.longitude,
                    if (loc.hasSpeed()) loc.speed else Float.NaN, loc.accuracy, loc.elapsedRealtimeNanos / 1_000_000L)
            }
        }
    }

    private fun stopRecording() {
        recordJob?.cancel(); recordJob = null
        if (!recorder.isRecording) return
        val ride = recorder.stop() ?: return   // null = trivial session, don't save
        repo.addRide(ride)   // self-scoped on the repo (survives ViewModel teardown)
        android.util.Log.i("DashViewModel", "Ride saved: ${"%.1f".format(ride.distanceKm)} km, ${ride.durationSec}s")
    }

    // ── Dash WiFi config (Settings) ──────────────────────────────────────────
    fun setSsid(s: String) { dashConfig.ssid = s.trim(); _ui.update { it.copy(ssid = s.trim()) } }
    fun setWifiPassword(p: String) { dashConfig.password = p; _ui.update { it.copy(wifiPassword = p) } }
    /** Forget the paired dash so the next connect rediscovers any RE_* dash by prefix. */
    fun forgetDash() { dashConfig.forgetDash(); _ui.update { it.copy(ssid = "") } }

    // ── Destination + routing ───────────────────────────────────────────────

    fun prefetchTiles(lat: Double, lng: Double) {
        val loc = location.location.value
        tiles.prefetch(lat, lng, loc?.latitude, loc?.longitude)
    }

    private var routeFetchJob: Job? = null
    private var lastRouteAttemptMs = 0L

    @Volatile private var routeMode = RouteMode.Car

    private data class JourneyStop(val place: com.example.navisarthi.data.SharedLocation, val preview: Route?)
    @Volatile private var journey = emptyList<JourneyStop>()
    @Volatile private var journeyIndex = 0
    @Volatile private var offlineJourney = false

    @Synchronized
    fun startJourney(stops: List<com.example.navisarthi.data.SharedLocation>, mode: RouteMode, previews: List<Route>, offline: Boolean = false) {
        require(stops.isNotEmpty() && stops.all { it.lat != null && it.lng != null })
        offlineJourney = offline
        journey = stops.mapIndexed { index, place -> JourneyStop(place, previews.getOrNull(index)) }
        journeyIndex = 0
        navigationStartedMs = SystemClock.elapsedRealtime()
        navigationMotion = com.example.navisarthi.data.RideMotionStats(navigationStartedMs)
        activateStop(journey.first(), mode)
    }

    @Synchronized
    fun remainingJourney(): List<com.example.navisarthi.data.SharedLocation>? =
        if (journey.isEmpty() || arrived) null else journey.drop(journeyIndex).map { it.place }

    @Synchronized
    fun skipStop() {
        advanceStop(journey, journeyIndex, usePreview = false)
    }

    @Synchronized
    private fun advanceStop(expected: List<JourneyStop>, index: Int, usePreview: Boolean): Boolean {
        if (journey !== expected || index != journeyIndex || index + 1 !in expected.indices) return false
        journeyIndex++
        val next = expected[journeyIndex]
        activateStop(if (usePreview || offlineJourney) next else next.copy(preview = null), routeMode)
        return true
    }

    private fun activateStop(stop: JourneyStop, mode: RouteMode) {
        val name = stop.place.name.ifBlank { "Dropped pin" }
        val lat = requireNotNull(stop.place.lat)
        val lng = requireNotNull(stop.place.lng)
        val previewRoute = stop.preview
        routeMode = mode
        routeFetchJob?.cancel()
        lastRouteAttemptMs = 0L
        _ui.update { it.copy(
            destinationName = name, hasRoute = false,
            stopNumber = journeyIndex + 1, stopCount = journey.size,
            destLatLng = lat to lng,
            routePoints = emptyList(),
        ) }
        destLat = lat
        destLng = lng
        route = previewRoute
        arrived = false
        arrivalMini = false
        arrivalDuration = null
        arrivalAverageKmh = null
        arrivalMaxKmh = null
        displayRoute = emptyList()
        progressM = 0.0
        offRouteSince = 0L
        offRouteStreakStart = 0L
        recalcHoldUntil = 0L
        smoothEtaSec = 0.0; etaArrivalMs = 0L; displayedEtaMin = null   // fresh ETA for the new route
        voice.resetTrip()   // fresh announcements for the new route
        session.updateRouteCard(name)
        lastSignature = ""
        val loc = location.lastKnown()
        tiles.prefetch(lat, lng, loc?.latitude, loc?.longitude)
        if (previewRoute == null) {
            if (!offlineJourney) fetchRoute(lat, lng)
        } else {
            if (!offlineJourney) tiles.prefetchRoute(previewRoute.geometry)
            _ui.update { it.copy(hasRoute = true, routePoints = previewRoute.geometry) }
        }
    }

    /** Drop the destination/route → free roam. The map keeps streaming and follows the rider. */
    @Synchronized
    fun exitNavigation() {
        navigationMotion = null
        journey = emptyList()
        journeyIndex = 0
        routeFetchJob?.cancel()
        destLat = null
        destLng = null
        route = null
        arrived = false
        arrivalMini = false
        arrivalDuration = null
        arrivalAverageKmh = null
        arrivalMaxKmh = null
        displayRoute = emptyList()
        progressM = 0.0
        offRouteSince = 0L
        panX = 0f; panY = 0f; followMode = true
        smoothEtaSec = 0.0; etaArrivalMs = 0L; displayedEtaMin = null
        voice.resetTrip()
        lastSignature = ""   // force a redraw with no route line
        _ui.update { it.copy(
            destinationName = null,
            stopNumber = 0, stopCount = 0,
            hasRoute = false,
            destLatLng = null,
            routePoints = emptyList(),
            remainingKm = null,
            etaMinutes = null,
            maneuver = null,
            offRoute = false,
            followMode = true,
        ) }
        session.updateRouteCard("NaviSarthi")   // dash card → name + 0.0 km, nav off
    }

    /** Compute the road route now (while internet is reachable) and cache it. */
    private fun fetchRoute(destLatV: Double, destLngV: Double) {
        val loc = location.lastKnown()
        if (loc == null) {
            android.util.Log.w("DashViewModel", "fetchRoute: no origin location yet")
            return
        }
        routeFetchJob?.cancel()
        lastRouteAttemptMs = SystemClock.elapsedRealtime()
        routeFetchJob = viewModelScope.launch {
            val r = Router.route(GeoPoint(loc.latitude, loc.longitude), GeoPoint(destLatV, destLngV), routeMode)
            ensureActive()
            if (r != null && !arrived && destLat == destLatV && destLng == destLngV) {
                route = r
                tiles.prefetchRoute(r.geometry)
                _ui.update { it.copy(hasRoute = true, routePoints = r.geometry) }
                android.util.Log.i("DashViewModel", "Route ready: ${r.geometry.size} pts, ${r.totalMeters.toInt()} m")
            } else {
                android.util.Log.w("DashViewModel", "Router returned null")
            }
        }
    }

    // ── Map controls ────────────────────────────────────────────────────────

    fun zoomIn()  = setZoom(zoom + 1)
    fun zoomOut() = setZoom(zoom - 1)
    private fun setZoom(z: Int) {
        val clamped = z.coerceIn(11, 20)
        if (clamped == zoom) return
        zoom = clamped
        session.updateZoom(zoom)
        _ui.update { it.copy(mapZoom = zoom) }
        // Make zoom feel instant: force a redraw on the very next tick (the renderer bridges the
        // gap with scaled neighbouring-zoom tiles), and warm the real tiles for the new level so
        // the scaled view sharpens in quickly.
        lastSignature = ""
        if (camInit) tiles.prefetchZoom(camLat, camLng, zoom)
    }
    fun panBy(dx: Float, dy: Float) = manualPan(dx, dy)
    fun recenter() {
        panX = 0f; panY = 0f; followMode = true
        _ui.update { it.copy(followMode = true) }
    }
    fun toggleHeadingUp() {
        headingUp = !headingUp
        _ui.update { it.copy(headingUp = headingUp) }
    }

    private fun manualPan(dx: Float, dy: Float) {
        panX += dx; panY += dy
        followMode = false
        lastManualPanAt = System.currentTimeMillis()
        _ui.update { it.copy(followMode = false) }
    }

    // ── Video + nav loop ────────────────────────────────────────────────────

    private var renderDurationMs = 0L
    private var encodeSendDurationMs = 0L

    private fun startStream() {
        com.example.navisarthi.data.RideDiagnostics.log("stream", "startStream — encoder up, RTP→dash beginning")
        videoPtsMs = 0L
        val packetizer = RtpPacketizer { rtpPkt -> session.sendRtp(rtpPkt) }
        val nalProc    = NalProcessor { nal, _ ->
            // Preserve codec capture timing, including real stalls. All NALs of an output
            // buffer share its timestamp; the RTP clock never compresses elapsed time.
            packetizer.packetize(nal, endOfAU = true, wallClockMs = videoPtsMs)
        }
        var encoderEpochMs = 0L
        var lastVideoPtsMs = 0L
        val onEncoded: (ByteArray, Boolean, Long) -> Unit = { annexB, isKey, ptsMs ->
            videoPtsMs = encoderEpochMs + ptsMs
            lastVideoPtsMs = maxOf(lastVideoPtsMs, videoPtsMs)
            nalProc.process(annexB)
            // First encoded frame leaving the phone — its delay after READY is the prime suspect
            // for the dash's "Timeout!" (the dash waits for video and gives up if it's late).
            if (!loggedFirstFrame) {
                loggedFirstFrame = true
                com.example.navisarthi.data.RideDiagnostics.log("stream", "first video frame sent (key=$isKey, ${annexB.size}B)")
            }
            // Atomic update: this runs on the encoder's callback thread, concurrent with the
            // frame loop's _ui writes — a plain copy() read-modify-write would drop updates.
            _ui.update { it.copy(frameCount = it.frameCount + 1) }
        }
        encoder?.release()
        encoder = DashEncoder(onEncoded).also { it.prepare() }

        frameBitmap = Bitmap.createBitmap(DashEncoder.WIDTH, DashEncoder.HEIGHT, Bitmap.Config.ARGB_8888)
        lastSignature = ""
        // Fresh camera so it snaps to the first fix instead of gliding from a stale spot.
        camInit = false; lastTickNs = 0L; lastFixElapsedNs = 0L; heldRider = null

        session.startStreaming()
        startMediaForwarding()
        location.location.value?.let { tiles.prefetch(it.latitude, it.longitude) }

        streamJob = viewModelScope.launch(Dispatchers.Default) {
            var lastPrefetch = 0L
            var failures = 0
            // Motion-adaptive, absolute-grid pacing (RE-style). Each iteration tick() tells us whether
            // the map actually changed; we hold the fast profile (FRAME_MS_ACTIVE) for MOTION_HOLD_MS
            // after the last change, then settle to the slow profile (FRAME_MS_IDLE). The grid sleeps
            // only the REMAINDER to the next frame so render+encode work doesn't make the cadence
            // wobble — uneven arrival is what makes the dash decoder jitter/teleport. videoPtsMs
            // advances by the interval actually used so RTP timestamps stay proportional to real time.
            var lastChangeMs = SystemClock.elapsedRealtime()
            var idleBitrate = false   // track the live profile so we only poke the encoder on a switch
            var frameMs = FRAME_MS_ACTIVE
            var nextFrameMs = SystemClock.elapsedRealtime()
            var lastSendMs = SystemClock.elapsedRealtime()
            // The loop must NEVER die silently: the session's heartbeats keep the dash
            // connected, so a dead frame loop = frozen map with the connection "up".
            while (isActive && session.state.value == DashState.STREAMING) {
                try {
                    val tickStart = SystemClock.elapsedRealtime()
                    val changed = tick()
                    renderDurationMs = SystemClock.elapsedRealtime() - tickStart
                    val nowMs = SystemClock.elapsedRealtime()
                    if (changed) lastChangeMs = nowMs
                    // Active while the map changed recently; idle once it's been still for MOTION_HOLD_MS.
                    val active = nowMs - lastChangeMs < MOTION_HOLD_MS
                    frameMs = if (active) FRAME_MS_ACTIVE else FRAME_MS_IDLE
                    val bmp = frameBitmap
                    val enc = encoder
                    if (bmp != null && enc != null) {
                        // Switch the encoder bitrate to match the profile, but only on a transition.
                        if (active && idleBitrate) { enc.requestBitrate(DashEncoder.BITRATE); idleBitrate = false }
                        else if (!active && !idleBitrate) { enc.requestBitrate(DashEncoder.BITRATE_IDLE); idleBitrate = true }
                        enc.drain()
                        enc.checkProgress()
                        val encodeStart = SystemClock.elapsedRealtime()
                        enc.renderFrame { canvas -> canvas.drawBitmap(bmp, 0f, 0f, null) }
                        // Drain immediately; output timestamps belong to their own captured frames.
                        enc.drain()
                        encodeSendDurationMs = SystemClock.elapsedRealtime() - encodeStart
                    }
                    failures = 0
                    // Track the real send cadence (EMA + worst) so the ride log proves it's even.
                    val sendNow = SystemClock.elapsedRealtime()
                    val gap = sendNow - lastSendMs
                    lastSendMs = sendNow
                    sendPaceMs = if (sendPaceMs <= 0.0) gap.toDouble() else sendPaceMs + (gap - sendPaceMs) * 0.1
                    if (gap > sendPaceMaxMs) sendPaceMaxMs = gap
                    // Warm the tile cache ahead of the rider every ~20 s.
                    if (sendNow - lastPrefetch > 20_000) {
                        lastPrefetch = sendNow
                        location.location.value?.let { tiles.prefetch(it.latitude, it.longitude) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures++
                    android.util.Log.e("DashViewModel", "Frame loop error #$failures", e)
                    if (failures >= 3) {
                        com.example.navisarthi.data.RideDiagnostics.log("stream", "encoder recovery: ${e.javaClass.simpleName}; pending=${encoder?.pendingFrames ?: 0}")
                        // MediaCodec is in an error state — rebuild the encoder so the
                        // stream recovers. The fresh encoder re-emits SPS/PPS, which the
                        // NAL processor bundles into the next IDR for the dash decoder.
                        runCatching { encoder?.release() }
                        encoderEpochMs = lastVideoPtsMs + frameMs
                        encoder = runCatching { DashEncoder(onEncoded).also { it.prepare() } }
                            .onFailure { android.util.Log.e("DashViewModel", "Encoder rebuild failed", it) }
                            .getOrNull()
                        lastSignature = "" // force a full redraw on the next tick
                        failures = 0
                    }
                }
                // Sleep only the remaining time to the next grid point.
                nextFrameMs += frameMs
                val sleepMs = nextFrameMs - SystemClock.elapsedRealtime()
                if (sleepMs > 1) {
                    delay(sleepMs)
                } else if (sleepMs < -frameMs) {
                    // Fell more than a frame behind (render spike) — re-anchor so we don't fire a
                    // burst of catch-up frames (which would itself look like a jump).
                    nextFrameMs = SystemClock.elapsedRealtime()
                }
            }
        }
    }

    /**
     * Compute nav state, push nav-info to the dash, and redraw the frame only if it changed.
     * @return true if the visible map content changed this tick (drives the motion-adaptive cadence).
     */
    private fun tick(): Boolean {
        // Revert to follow mode after the rider stops nudging the joystick.
        if (!followMode && System.currentTimeMillis() - lastManualPanAt > MANUAL_IDLE_MS) {
            panX = 0f; panY = 0f; followMode = true
            _ui.update { it.copy(followMode = true) }
        }

        val loc = location.location.value
        val fixAgeMs = loc?.let { FixFreshness.ageMs(it.elapsedRealtimeNanos, SystemClock.elapsedRealtimeNanos()) } ?: Long.MAX_VALUE
        val navigationFixValid = loc != null && loc.hasAccuracy() &&
            FixFreshness.usable(loc.elapsedRealtimeNanos, SystemClock.elapsedRealtimeNanos(), loc.accuracy)
        if (!arrived && navigationFixValid && loc != null && loc.hasSpeed() && loc.accuracy <= 20f) {
            navigationMotion?.add(loc.elapsedRealtimeNanos / 1_000_000L, loc.speed.toDouble())
        }
        val activeJourney = journey
        val activeIndex = journeyIndex
        val r = route
        val dLat = destLat; val dLng = destLng
        // A cached origin may correctly have been rejected while GPS warmed up.
        // Retry from a fresh fix rather than leaving the destination permanently unrouted.
        if (!offlineJourney && !arrived && r == null && navigationFixValid && dLat != null && dLng != null &&
            routeFetchJob?.isActive != true && SystemClock.elapsedRealtime() - lastRouteAttemptMs >= 6_000L) {
            fetchRoute(dLat, dLng)
        }

        // Default to raw GPS; map-matching below snaps it onto the route when on it.
        matchedLat = loc?.latitude
        matchedLng = loc?.longitude

        displayRoute = if (arrived) emptyList() else r?.geometry.orEmpty()
        var routeMatch: GeoPoint? = null
        if (navigationFixValid && loc != null && dLat != null && dLng != null) {
            val nearby = GeoPoint.distMeters(GeoPoint(loc.latitude, loc.longitude), GeoPoint(dLat, dLng)) <= 15.0
            if (!arrived && nearby) {
                if (advanceStop(activeJourney, activeIndex, usePreview = true)) return true
                if (journey !== activeJourney || journeyIndex != activeIndex) return false
                arrived = true
                arrivalMini = true
                val arrivalElapsedMs = SystemClock.elapsedRealtime()
                arrivalAverageKmh = navigationMotion?.averageSpeedMps(arrivalElapsedMs)?.times(3.6)
                arrivalMaxKmh = navigationMotion?.maxSpeedMps?.times(3.6)
                val seconds = (SystemClock.elapsedRealtime() - navigationStartedMs) / 1000
                arrivalDuration = if (seconds >= 3600) "%dh %02dm".format(seconds / 3600, seconds / 60 % 60)
                    else "%dm %02ds".format(seconds / 60, seconds % 60)
                displayRoute = emptyList()
                routeFetchJob?.cancel()
                session.updateNavInfo(0xFF, 0, DashCommands.NAV_UNIT_METERS, 0, DashCommands.NAV_UNIT_METERS)
            }
            if (!nearby) arrivalMini = false
        }
        var maneuverCue: String? = null
        var remainingM: Double? = null
        var etaSec: Double? = null
        // Heading-up direction. GPS bearing is trustworthy ONLY while genuinely moving — at a
        // standstill (or a near-zero crawl) it's noise and would rotate the map to a random or
        // opposite direction the instant you stop. So adopt it only above BEARING_MIN_SPEED (and
        // when the fix actually carries a bearing); otherwise HOLD the last heading shown while
        // moving. Also keeps the last heading through a GPS dropout (tunnels).
        var heading = when {
            loc != null && loc.hasBearing() && loc.speed >= BEARING_MIN_SPEED -> loc.bearing
            camInit -> camHdg
            else -> 0f
        }
        var offRoute = false
        var recalculating = false

        if (r != null && loc != null && navigationFixValid && !arrived) {
            val ns = trackProgress(r, GeoPoint(loc.latitude, loc.longitude))
            remainingM = ns.remainingM
            maneuverCue = ns.nextManeuver?.let {
                val distance = if (ns.nextTurnM < 1000) "${ns.nextTurnM.toInt()} m"
                    else "%.1f km".format(ns.nextTurnM / 1000)
                "${it.cue}  /  $distance"
            }
            // Heading-aware off-route. A flyover (or its service road) runs PARALLEL to and almost
            // on top of the route, so flat GPS can sit well past the lateral threshold while the
            // rider is still effectively on the line. Distance alone then cries "off-route" and
            // triggers a sudden reroute. So in the ambiguous band we require the heading to ALSO
            // disagree (a genuine divergence / wrong turn); a clearly different road (very far) is
            // off-route regardless. This is the main flyover-confusion mitigation.
            lastSnapDist = ns.snapDist
            val headingKnown = loc.hasBearing() && loc.speed >= BEARING_MIN_SPEED
            val headingOff = headingKnown && angleDelta(loc.bearing, ns.heading) > 50f
            offRoute = when {
                ns.snapDist > 150.0 -> true       // unmistakably a different road → off regardless
                ns.snapDist > 70.0  -> headingOff // parallel/flyover band: off ONLY if heading diverges
                else -> false                     // (stopped here → not off-route; decide once moving)
            }
            val directionAgrees = if (headingKnown) angleDelta(loc.bearing, ns.heading) <= 40f
                else loc.speed < BEARING_MIN_SPEED
            if (!offRoute && directionAgrees && loc.accuracy <= 25f &&
                ns.snapDist <= loc.accuracy.toDouble().coerceIn(6.0, 15.0)) {
                routeMatch = ns.snapped
                matchedLat = ns.snapped.lat
                matchedLng = ns.snapped.lng
                val next = r.cumulative.binarySearch(ns.matchedMeters).let { if (it >= 0) it + 1 else -it - 1 }
                displayRoute = object : AbstractList<GeoPoint>() {
                    override val size = 1 + r.geometry.size - next
                    override fun get(index: Int) = if (index == 0) ns.snapped else r.geometry[next + index - 1]
                }
            }
            val nowMs = System.currentTimeMillis()

            // "Recalculating" = off the line for >0.8 s (ignores a 1-frame GPS blip), OR a reroute
            // request is in flight. While true the distance is measured against a route we've left,
            // so the ETA is meaningless — we FREEZE it (and the pill says "Recalculating") rather
            // than let the number lurch until the fresh route lands. recalcHoldUntil keeps the pill
            // up a beat after the new route lands so a fast reroute is still visible to the rider.
            if (offRoute) { if (offRouteStreakStart == 0L) offRouteStreakStart = nowMs }
            else offRouteStreakStart = 0L
            val rawRecalc = rerouting || (offRouteStreakStart != 0L && nowMs - offRouteStreakStart > 800)
            if (rawRecalc) recalcHoldUntil = nowMs + 1_500
            recalculating = rawRecalc || nowMs < recalcHoldUntil

            if (!recalculating) {
                // Whole-navigation average includes stopped time, and survives stop changes/reroutes.
                // No speed floor or preset planning speed: wait until movement has been measured.
                val average = navigationMotion?.averageSpeedMps(SystemClock.elapsedRealtime()) ?: 0.0
                smoothEtaSec = if (average > 0.0) ns.remainingM / average else 0.0
                // Refresh the displayed minutes + arrival clock only every 5 s so they read steady
                // (like Google Maps) instead of flickering every second.
                if (smoothEtaSec > 0.0 && (etaArrivalMs == 0L || nowMs - lastArrivalCalcMs > 5_000)) {
                    etaArrivalMs = nowMs + (smoothEtaSec * 1000).toLong()
                    displayedEtaMin = Math.round(smoothEtaSec / 60.0).toInt()
                    lastArrivalCalcMs = nowMs
                }
            }
            etaSec = if (smoothEtaSec > 0.0) smoothEtaSec else null
            // Feed the dash's own turn-by-turn widget with CORRECT distances (next-turn
            // + total remaining) and the held arrival time.
            val (pv, pu) = toDashDistance(ns.nextTurnM)
            val (tv, tu) = toDashDistance(ns.remainingM)
            val etaHHMM = etaSec?.let {
                val arrival = java.util.Calendar.getInstance().apply { add(java.util.Calendar.SECOND, it.toInt()) }
                "%02d%02d".format(arrival.get(java.util.Calendar.HOUR_OF_DAY), arrival.get(java.util.Calendar.MINUTE))
            }
            session.updateNavInfo(ns.nextManeuver?.dashCode ?: DashCommands.NAV_MANEUVER_CONTINUE, pv, pu, tv, tu, etaHHMM)
            // Spoken/chime turn guidance (no-op when voice mode is OFF).
            voice.maybeAnnounce(ns.nextManeuver, ns.nextTurnM, ns.remainingM)
        } else if (!arrived && loc != null && dLat != null && dLng != null) {
            remainingM = GeoPoint.distMeters(
                GeoPoint(loc.latitude, loc.longitude), GeoPoint(dLat, dLng)
            )
        }

        // Recompute the route if the rider has clearly left it for a few seconds.
        if (offlineJourney && offRoute) maneuverCue = "Return to downloaded route"
        else if (!offlineJourney) maybeReroute(offRoute, if (navigationFixValid) loc else null)

        // GPS health: LOST if no fresh fix for a few seconds (or none at all), WEAK if the fix is
        // imprecise or coming in slowly, else GOOD. Drives the on-dash indicator and marker colour.
        gpsStatus = when {
            loc == null || fixAgeMs > 4_000 -> GpsStatus.LOST
            loc.accuracy > 25f || gpsFixIntervalMs > 2_500 -> GpsStatus.WEAK
            else -> GpsStatus.GOOD
        }

        // Publish nav figures to the phone UI.
        _ui.update { it.copy(
            hasGps = navigationFixValid,
            gpsStatus = gpsStatus,
            routePoints = if (arrived) emptyList() else it.routePoints,
            riderLat = matchedLat,
            riderLng = matchedLng,
            riderBearing = heading,
            remainingKm = remainingM?.let { d -> d / 1000.0 },
            etaMinutes = if (etaSec != null) displayedEtaMin else null,
            maneuver = maneuverCue,
            offRoute = offRoute,
            recalculating = recalculating,
        ) }

        updateThermal()

        // Periodic ride snapshot (~15 s) — GPS quality, motion, frame throughput and thermal,
        // so a post-ride read shows where the map stuttered (weak GPS) or the dash struggled.
        val nowD = System.currentTimeMillis()
        if (nowD - lastDiagMs > 15_000) {
            lastDiagMs = nowD
            com.example.navisarthi.data.RideDiagnostics.log(
                "ride",
                "gps=" + (loc?.let { "acc=%.0fm spd=%.1fm/s".format(it.accuracy, it.speed) } ?: "none") +
                    " fixAge=${fixAgeMs}ms fixGap=%.0fms".format(gpsFixIntervalMs) +
                    " render=${renderDurationMs}ms encodeSend=${encodeSendDurationMs}ms pending=${encoder?.pendingFrames ?: 0}" +
                    " frames=${_ui.value.frameCount} thermal=${_ui.value.thermal}" +
                    " pace=%.0fms/max=%dms".format(sendPaceMs, sendPaceMaxMs) +  // send cadence (≈250ms active / 500ms idle)
                    " remaining=" + (remainingM?.let { "%.1fkm".format(it / 1000.0) } ?: "-") +
                    " offRoute=$offRoute" +
                    (if (lastSnapDist >= 0) " snap=%.0fm".format(lastSnapDist) else ""),
            )
            sendPaceMaxMs = 0L  // reset the worst-case window each diag line
        }

        // ── Predictive, framerate-independent camera (CarPlay-smooth) ──
        // Capture each fresh GPS fix + its velocity for dead-reckoning.
        if (loc != null && loc.elapsedRealtimeNanos != lastFixElapsedNs) {
            lastFixElapsedNs = loc.elapsedRealtimeNanos
            // Track the GAP between fixes (EMA): if this balloons past ~1 s with the screen off,
            // GPS callbacks are being throttled — that's what the predictor has to bridge and the
            // root of any residual "stuck then teleport". Logged in the periodic ride line.
            val nowWall = SystemClock.elapsedRealtime()
            if (fixElapsedMs > 0L) {
                val gap = (nowWall - fixElapsedMs).toDouble()
                gpsFixIntervalMs = if (gpsFixIntervalMs <= 0.0) gap else gpsFixIntervalMs + (gap - gpsFixIntervalMs) * 0.3
            }
            fixLat = loc.latitude; fixLng = loc.longitude
            fixBearing = loc.bearing; fixSpeed = loc.speed
            fixElapsedMs = loc.elapsedRealtimeNanos / 1_000_000L
        }
        // Extrapolate where the rider IS NOW (last fix + velocity·elapsed), so the camera
        // doesn't trail the bike between 1 Hz fixes. Only predict while genuinely moving.
        val rider: Pair<Double, Double>? = when {
            loc == null -> null
            routeMatch != null -> {
                val confidence = ((25f - loc.accuracy) / 15f).coerceIn(0f, 1f)
                val distance = predictedDistance(loc.speed, (fixAgeMs / 1000.0).coerceAtMost(1.5)) * confidence
                if (loc.hasBearing() && loc.speed >= BEARING_MIN_SPEED) {
                    project(routeMatch.lat, routeMatch.lng, loc.bearing.toDouble(), distance)
                } else routeMatch.lat to routeMatch.lng
            }
            navigationFixValid && loc.hasBearing() && fixSpeed > 0.5f -> {
                // Dead-reckon forward from the last fix so the camera keeps GLIDING through GPS
                // gaps instead of freezing then snapping ("stuck then jump"). predictedDistance
                // runs at full speed for the first ~1 s (the normal 1 Hz fix gap) then tapers, so
                // a long dropout — or a brake/stop the predictor hasn't heard about yet — can't
                // fling the camera far ahead; the next fix then needs only a tiny eased correction.
                // elapsed = time since the fix arrived; + DISPLAY_LEAD_S leads the marker past
                // "now" to cancel the camera-ease + encode + RTP + dash-decode/display latency, so
                // the dash tracks the bike instead of trailing ~0.5 s behind. The predictor's taper
                // still caps how far a long dropout / unannounced brake can fling it.
                val elapsed = fixAgeMs / 1000.0
                // Weak-GPS guard: a noisy fix has an unreliable position AND bearing/speed, so
                // extrapolating from it flings the view ("teleport"). Scale the prediction down as
                // accuracy worsens — but only for GENUINELY poor fixes: full up to 25 m, none above
                // 50 m. The old 12 m cutoff scaled prediction DOWN at the moderate accuracy that's
                // normal at speed, so the predictor undershot → the camera lagged → each new fix
                // snap-corrected forward = the "jumping when moving fast". Widening it keeps the
                // dead-reckon gliding through the 1 Hz gaps at speed while still guarding bad fixes.
                val conf = ((50f - (loc.accuracy)) / 25f).toDouble().coerceIn(0.0, 1.0)
                val dist = predictedDistance(fixSpeed, elapsed + DISPLAY_LEAD_S) * conf
                // Remember where the bike actually is, so that when it stops the marker holds the
                // real fix (not the lead-projected point ahead of it).
                heldRider = fixLat to fixLng
                project(fixLat, fixLng, fixBearing.toDouble(), dist)
            }
            else -> {
                // Stopped: hold position and IGNORE sub-accuracy GPS wander, so a parked bike's dot
                // doesn't drift or jitter. Only move the held point if the fix steps clearly outside
                // its own error radius (i.e. the bike really moved a little / GPS re-converged).
                val cand = matchedLat!! to matchedLng!!
                val h = heldRider
                val band = loc.accuracy.coerceIn(8f, 30f).toDouble()
                if (h == null || GeoPoint.distMeters(
                        GeoPoint(h.first, h.second), GeoPoint(cand.first, cand.second)
                    ) > band
                ) { heldRider = cand; cand } else h
            }
        }

        val haveTarget = rider != null || (dLat != null && dLng != null)
        val targetLat = rider?.first ?: dLat ?: camLat
        val targetLng = rider?.second ?: dLng ?: camLng

        // Time-based smoothing: alpha derived from the real frame interval + a time
        // constant, so motion is equally smooth at any (dynamic) frame rate.
        val nowNs = System.nanoTime()
        val dt = if (lastTickNs == 0L) 0.042 else ((nowNs - lastTickNs) / 1e9).coerceIn(0.0, 0.5)
        lastTickNs = nowNs
        // Adaptive smoothing: ease HARDER (bigger time constant) when the fix is noisy so weak-GPS
        // jitter doesn't reach the camera, and stay snappy when the signal is clean. This is the
        // other half of the "stuck then jump" fix — the predictor bridges gaps, this absorbs the
        // lateral noise that makes a poor signal jitter.
        val acc = loc?.accuracy ?: 12f
        val tau = when {
            acc > 25f -> SMOOTH_TAU * 2.0   // poor signal → heavy smoothing
            acc > 12f -> SMOOTH_TAU * 1.5   // fair
            else      -> SMOOTH_TAU         // good → responsive
        }
        val a = if (camInit) (1.0 - Math.exp(-dt / tau)) else 1.0

        if (haveTarget) {
            if (!camInit) { camLat = targetLat; camLng = targetLng; camHdg = heading; camInit = true }
            else {
                camLat += (targetLat - camLat) * a
                camLng += (targetLng - camLng) * a
                val dh = (((heading - camHdg) % 360f) + 540f) % 360f - 180f  // shortest arc
                camHdg += dh * a.toFloat()
            }
        }
        // Lock the dash marker to the smoothed centre (map slides under it).
        frameRiderLat = if (rider != null) camLat else null
        frameRiderLng = if (rider != null) camLng else null

        val centerLat = if (haveTarget) camLat else 0.0
        val centerLng = if (haveTarget) camLng else 0.0
        val camHeading = if (haveTarget) camHdg else heading

        val sig = buildString {
            // High resolution (6 dp ≈ 0.1 m, 0.1° heading) so every smoothed step redraws
            // for buttery motion. Safe from standstill jitter because the camera is fed the
            // SMOOTHED position (which settles and stops), not raw GPS.
            append("%.6f".format(centerLat)); append("%.6f".format(centerLng))
            append(currentAccessoryBatteryLabel()); append(currentAccessoryBatteryIcon())
            append(maneuverCue); append(arrived); append(arrivalMini); append(_ui.value.goldenRoute); append(routeMatch); append(displayRoute.size)
            append(_ui.value.mapStyle); append(_ui.value.perspective)
            append(zoom); append(panX.toInt()); append(panY.toInt())
            append(if (headingUp) (camHeading * 10).toInt() else 0)
            append(remainingM?.let { (it / 100).toInt() } ?: -1) // 100 m resolution to avoid jitter
            append(if (r != null) r.geometry.size else 0)
            // Include the incoming-call caller so a call arriving forces an immediate redraw of the
            // banner even when the map is static (otherwise it'd wait for the 2 s forced refresh).
            append(com.example.navisarthi.media.CallInfoProvider.incomingCall.value?.takeIf { it.incoming }?.caller ?: "")
        }
        val now = System.currentTimeMillis()
        // A genuine content change (not the periodic forced refresh) is what marks the map as
        // "moving" for the adaptive cadence — a forced redraw of identical content stays "idle".
        val contentChanged = sig != lastSignature
        if (contentChanged || now - lastRedrawAt > FORCE_REDRAW_MS) {
            lastSignature = sig
            lastRedrawAt = now
            redrawFrame(centerLat, centerLng, camHeading, remainingM)
        }
        return contentChanged
    }

    /**
     * Reroute when off the line for >5 s (12 s cooldown between attempts). Routes from
     * the live GPS position to the saved destination and swaps the polyline in. Needs
     * internet — available now because only the dash sockets are bound to the dash WiFi.
     */
    private fun maybeReroute(offRoute: Boolean, loc: android.location.Location?) {
        val dLat = destLat; val dLng = destLng
        if (!offRoute || loc == null || dLat == null || dLng == null) { offRouteSince = 0L; return }
        val now = System.currentTimeMillis()
        if (offRouteSince == 0L) offRouteSince = now
        // Reroute faster: 2 s off-line confirms a real miss (not a GPS wobble), 6 s cooldown
        // between attempts. Was 4 s / 12 s, which felt sluggish after a missed turn.
        if (now - offRouteSince < 2_000 || now - lastRerouteAt < 6_000 || rerouting) return
        lastRerouteAt = now
        rerouting = true
        val offSec = (now - offRouteSince) / 1000
        android.util.Log.i("DashViewModel", "Off-route ${offSec}s → rerouting")
        com.example.navisarthi.data.RideDiagnostics.log("reroute", "off-route ${offSec}s → requesting new route")
        val startMs = System.currentTimeMillis()
        viewModelScope.launch {
            val requestedMode = routeMode
            val r = Router.route(GeoPoint(loc.latitude, loc.longitude), GeoPoint(dLat, dLng), requestedMode)
            val took = System.currentTimeMillis() - startMs
            if (r != null && !arrived && destLat == dLat && destLng == dLng && routeMode == requestedMode) {
                route = r
                progressM = 0.0
                offRouteSince = 0L
                tiles.prefetchRoute(r.geometry)
                _ui.update { it.copy(hasRoute = true, routePoints = r.geometry) }
                android.util.Log.i("DashViewModel", "Reroute ok: ${r.geometry.size} pts, ${r.totalMeters.toInt()} m")
                com.example.navisarthi.data.RideDiagnostics.log("reroute", "new route in ${took}ms (${r.geometry.size} pts, ${r.totalMeters.toInt()} m)")
            } else {
                android.util.Log.w("DashViewModel", "Reroute failed (no internet?)")
                com.example.navisarthi.data.RideDiagnostics.log("reroute", "FAILED after ${took}ms (no internet?)")
            }
            rerouting = false
        }
    }

    private fun redrawFrame(
        centerLat: Double, centerLng: Double, heading: Float, remainingM: Double?,
    ) {
        val bmp = frameBitmap ?: return
        val loc = location.location.value
        // Glanceable ETA — minutes remaining + a stable 12-hour arrival clock. Both come
        // from the smoothed estimate so they don't flicker every second.
        val mins = _ui.value.etaMinutes
        val arriving = mins != null && mins <= 0
        val recalculating = _ui.value.recalculating
        // Off-route / rerouting: tell the rider the ETA is being recomputed instead of showing a
        // frozen or wildly-swinging number.
        val etaPrimary = when {
            recalculating && route != null -> "Recalculating"
            mins != null && etaArrivalMs > 0L -> when {
                arriving   -> "< 1 min"
                mins >= 60 -> "${mins / 60}h ${mins % 60}m"
                else       -> "$mins min"
            }
            else -> null
        }
        // 12-hour arrival clock; hidden once arriving or while recalculating.
        val etaSecondary = if (etaPrimary != null && !arriving && !recalculating)
            java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(etaArrivalMs))
        else null
        val frame = MapRenderer.Frame(
            centerLat = centerLat,
            centerLng = centerLng,
            zoom = zoom,
            panX = panX,
            panY = panY,
            headingUp = (headingUp || _ui.value.perspective) && loc != null,
            heading = heading,
            riderLat = frameRiderLat,
            riderLng = frameRiderLng,
            destLat = destLat,
            destLng = destLng,
            destName = _ui.value.destinationName,
            route = if (arrived) emptyList() else displayRoute,
            maneuverText = _ui.value.maneuver,
            remainingText = remainingM?.let { fmtDist(it) },
            tilt3d = _ui.value.perspective && destLat != null && !arrived,
            style = _ui.value.mapStyle,
            arrived = arrivalMini,
            arrivalDuration = arrivalDuration,
            journeyAverageKmh = arrivalAverageKmh,
            journeyMaxKmh = arrivalMaxKmh,
            goldenRoute = _ui.value.goldenRoute,
            traffic = trafficTiles.enabled && _ui.value.trafficAvailable && !offlineJourney && !arrived && displayRoute.size >= 2,
            etaPrimary = etaPrimary,
            etaSecondary = etaSecondary,
            gpsWeak = gpsStatus == GpsStatus.WEAK,
            accessoryBatteryLabel = currentAccessoryBatteryLabel(),
            accessoryBatteryIcon = currentAccessoryBatteryIcon(),
            gpsLost = gpsStatus == GpsStatus.LOST,
        )
        val canvas = Canvas(bmp)
        mapRenderer.draw(canvas, frame)
        // The dash shows ONLY our video frame while projecting — it won't overlay its native
        // now-playing/call cards (confirmed: 05 0d/05 22 are sent fine but never displayed mid-map).
        // So we draw them INTO the frame ourselves, as the CLAUDE.md design always intended.
        drawDashOverlays(canvas, bmp.width, bmp.height)
        // Publish a snapshot for the in-app preview (immutable copy — see _previewFrame).
        _previewFrame.value = bmp.copy(Bitmap.Config.ARGB_8888, false)
    }

    // Overlay paints — created once (never per-frame). ~50% smaller than the first cut, per feedback.
    private val ovBg by lazy { android.graphics.Paint().apply { color = 0xCC0B0D0E.toInt(); isAntiAlias = true } }
    private val ovTitle by lazy { android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE; isAntiAlias = true; textSize = 15f
        typeface = android.graphics.Typeface.DEFAULT_BOLD; textAlign = android.graphics.Paint.Align.CENTER } }
    private val ovSub by lazy { android.graphics.Paint().apply {
        color = 0xFFCFCFCF.toInt(); isAntiAlias = true; textSize = 10f; textAlign = android.graphics.Paint.Align.CENTER } }
    private val ovAccent by lazy { android.graphics.Paint().apply {
        color = 0xFFF2A93B.toInt(); isAntiAlias = true; textSize = 10f; textAlign = android.graphics.Paint.Align.CENTER } }

    /**
     * Draw the INCOMING-call banner onto the projected frame (the dash shows only our video while
     * projecting, so this is the only way it's visible mid-ride). Incoming only — outgoing/active
     * calls don't set CallInfoProvider, so this never fires for them. Now-playing has no overlay; it
     * goes to the dash's audio screen via the 05 0d TLV instead. Centred in the round display's safe zone.
     */
    private fun drawDashOverlays(canvas: Canvas, w: Int, h: Int) {
        val call = com.example.navisarthi.media.CallInfoProvider.incomingCall.value
        if (call == null || !call.incoming) return   // overlay only for a ringing call, not active/outgoing
        val cx = w / 2f; val cy = h / 2f
        val half = h * 0.28f   // ~half the previous width; stays inside the round display
        canvas.drawRoundRect(cx - half, cy - 26f, cx + half, cy + 26f, 10f, 10f, ovBg)
        canvas.drawText("Incoming call", cx, cy - 11f, ovSub)
        canvas.drawText(ellipsizeOverlay(call.caller, 16), cx, cy + 5f, ovTitle)
        canvas.drawText("UP answer · DOWN reject", cx, cy + 19f, ovAccent)
    }

    private fun ellipsizeOverlay(s: String, max: Int) = if (s.length > max) s.take(max - 1) + "…" else s

    /** Answer via the dialer's own notification action (most reliable); fall back to TelecomManager. */
    private fun answerCall(c: com.example.navisarthi.media.IncomingCall) {
        val sent = c.answerIntent?.let { runCatching { it.send() }.isSuccess } ?: false
        if (!sent) callController.answer()
        com.example.navisarthi.data.RideDiagnostics.log("media", "answer (notifAction=$sent)")
    }

    /** Reject (ringing) or hang up (active) via the dialer's action; fall back to TelecomManager. */
    private fun endCall(c: com.example.navisarthi.media.IncomingCall) {
        val sent = c.declineIntent?.let { runCatching { it.send() }.isSuccess } ?: false
        if (!sent) callController.hangup()
        com.example.navisarthi.data.RideDiagnostics.log("media", "end/reject (notifAction=$sent)")
    }

    // ── Monotonic route-progress tracker ────────────────────────────────────
    // Snapping to the GLOBALLY nearest segment makes the remaining distance flicker
    // (a winding route can pass near the start again, so it jumps to ~straight-line).
    // Instead we advance progress along the route, searching a window AHEAD of where
    // we already are, only re-acquiring globally if we're clearly off-route.
    @Volatile private var progressM = 0.0

    private data class NavState(
        val remainingM: Double, val nextTurnM: Double, val heading: Float, val offRoute: Boolean,
        val snapped: GeoPoint, val snapDist: Double, val matchedMeters: Double,
        val nextManeuver: com.example.navisarthi.dash.nav.Maneuver?,
    )

    private data class Match(val cum: Double, val dist: Double, val bearing: Float, val proj: GeoPoint)

    private fun trackProgress(r: Route, pos: GeoPoint): NavState {
        val geom = r.geometry
        val cum = r.cumulative

        fun search(lo: Double, hi: Double): Match {
            var bestDist = Double.MAX_VALUE; var bestCum = progressM; var bestBearing = 0f; var bestProj = pos
            for (i in 0 until geom.size - 1) {
                if (cum[i + 1] < lo || cum[i] > hi) continue
                val (proj, t) = GeoPoint.projectOnSegment(pos, geom[i], geom[i + 1])
                val d = GeoPoint.distMeters(pos, proj)
                if (d < bestDist) {
                    bestDist = d
                    bestCum = cum[i] + GeoPoint.distMeters(geom[i], geom[i + 1]) * t
                    bestBearing = GeoPoint.bearing(geom[i], geom[i + 1]).toFloat()
                    bestProj = proj
                }
            }
            return Match(bestCum, bestDist, bestBearing, bestProj)
        }

        var m = search(progressM - 60.0, progressM + 1000.0)
        if (m.dist > 80.0) {
            val g = search(0.0, r.totalMeters) // off-window → re-acquire globally
            if (g.dist < m.dist) m = g
        }
        progressM = maxOf(progressM - 25.0, m.cum) // mostly forward, tolerate small GPS slide

        val remaining = (r.totalMeters - progressM).coerceAtLeast(0.0)
        val nextMan = r.maneuvers.firstOrNull {
            it.cumulativeMeters > progressM + 1.0 && it.type != com.example.navisarthi.dash.nav.ManeuverType.DEPART
        }
        val nextTurn = nextMan?.let { (it.cumulativeMeters - progressM).coerceAtLeast(0.0) } ?: remaining
        return NavState(remaining, nextTurn, m.bearing, m.dist > 70.0, m.proj, m.dist, m.cum, nextMan)
    }

    private fun updateThermal() {
        val status = runCatching { powerManager.currentThermalStatus }.getOrDefault(PowerManager.THERMAL_STATUS_NONE)
        val label = when (status) {
            PowerManager.THERMAL_STATUS_NONE, PowerManager.THERMAL_STATUS_LIGHT -> "OK"
            PowerManager.THERMAL_STATUS_MODERATE -> "Warm"
            PowerManager.THERMAL_STATUS_SEVERE, PowerManager.THERMAL_STATUS_CRITICAL -> "Hot"
            else -> "Throttling"
        }
        if (label != _ui.value.thermal) _ui.update { it.copy(thermal = label) }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** metres → (value, dash unit). <1 km → metres (0x30); else km×10 (0x10). */
    private fun toDashDistance(meters: Double): Pair<Int, Int> =
        if (meters < 1000.0) meters.toInt().coerceIn(0, 0xFFFF) to DashCommands.NAV_UNIT_METERS
        else (meters / 100.0).toInt().coerceIn(0, 0xFFFF) to DashCommands.NAV_UNIT_KM_TENTHS

    private fun fmtDist(m: Double): String =
        if (m < 1000) "${m.toInt()} m" else "%.1f km".format(m / 1000.0)

    /** Smallest absolute angle (0..180°) between two compass bearings. */
    private fun angleDelta(a: Float, b: Float): Float {
        val d = (((a - b) % 360f) + 540f) % 360f - 180f
        return kotlin.math.abs(d)
    }

    private fun teardown() {
        streamJob?.cancel(); streamJob = null
        stopMediaForwarding()
        encoder?.release(); encoder = null
        frameBitmap?.recycle(); frameBitmap = null
        _previewFrame.value = null   // drop the stale dash frame so the preview falls back to the live map
    }

    /**
     * Bridge the phone's now-playing session + incoming-call notification onto the dash (TLV 05 0d /
     * 05 22), matching the dash. Needs notification access; without it MediaInfoProvider
     * stays silent and the dash simply shows no media card. Artwork uses the verified OGRE 05 40
     * fragmentation with owned thumbnail bitmaps; native call-history requests are handled separately.
     */
    private fun startMediaForwarding() {
        val granted = com.example.navisarthi.media.MediaInfoProvider.isAccessGranted(getApplication())
        com.example.navisarthi.data.RideDiagnostics.log("media", "forwarding start — notification access=$granted")
        mediaInfo.start()
        accessoryBattery.setStreaming(true)
        historyAuditJob?.cancel()
        historyAuditJob = viewModelScope.launch {
            while (isActive) {
                consoleHistory.refreshAccess()
                refreshMissedBadge()
                historyCategory?.let {
                    if (!consoleHistory.categoryAllowed(it)) dialSnapshot.clear()
                    requestCallHistory(it, force = false)
                }
                delay(5000)
            }
        }
        mediaObserveJob?.cancel()
        mediaObserveJob = viewModelScope.launch {
            launch {
                mediaInfo.nowPlaying.collectLatest { np ->
                    val jpeg = withContext(Dispatchers.Default) {
                        runCatching { com.example.navisarthi.media.ConsoleAlbumArt.encode(np?.art) }
                            .getOrElse { com.example.navisarthi.media.ConsoleAlbumArt.encode(null) }
                    }
                    session.updateAlbumArt(jpeg)
                }
            }
            launch {
                mediaInfo.nowPlaying.collect { np ->
                    com.example.navisarthi.data.RideDiagnostics.log("media", "now-playing observed: ${np?.title ?: "(none)"}")
                    session.updateNowPlaying(np?.title, np?.album.orEmpty(), np?.artist.orEmpty(),
                        when (np?.playbackState) {
                            android.media.session.PlaybackState.STATE_PLAYING -> true
                            android.media.session.PlaybackState.STATE_PAUSED -> false
                            else -> null // OGRE sends no play/pause flag for buffering/unknown states.
                        })
                }
            }
            launch {
                com.example.navisarthi.media.CallInfoProvider.incomingCall.collect { call ->
                    com.example.navisarthi.data.RideDiagnostics.log("media", "call observed: ${call?.caller ?: "(none)"}")
                    session.updateCall(call?.caller)
                }
            }
        }
    }

    private fun stopMediaForwarding() {
        accessoryBattery.setStreaming(false)
        historyAuditJob?.cancel(); historyAuditJob = null
        missedBadgeJob?.cancel(); missedBadgeJob = null
        historyRequestJob?.cancel(); historyRequestJob = null
        historyCategory = null; lastHistoryRows = null
        dialSnapshot.clear()
        session.updateAlbumArt(null)
        mediaObserveJob?.cancel(); mediaObserveJob = null
        mediaInfo.stop()
        session.updateNowPlaying(null, "", "")
        session.updateCall(null)
    }

    override fun onCleared() {
        super.onCleared()
        stopRecording()        // save the in-progress ride if the app is closed mid-session
        session.disconnect()
        wifiManager.disconnect()
        // Streaming can't continue once the ViewModel is gone, so make sure the foreground
        // service + wake/wifi locks + GPS don't outlive it (otherwise they'd hold the CPU/WiFi
        // awake with nothing producing frames).
        releaseBackgroundResources()
        mediaInfo.close()
        accessoryBattery.close()
        voice.shutdown()
        com.example.navisarthi.data.RideDiagnostics.stop("app closed")
    }
}
