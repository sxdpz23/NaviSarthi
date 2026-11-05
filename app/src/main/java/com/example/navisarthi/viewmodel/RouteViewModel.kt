package com.example.navisarthi.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.navisarthi.data.SharedLocation
import com.example.navisarthi.dash.nav.GeoPoint
import com.example.navisarthi.dash.nav.Route
import com.example.navisarthi.dash.nav.Router
import com.example.navisarthi.util.Dbg
import com.example.navisarthi.util.LocationParser
import com.example.navisarthi.dash.nav.RouteMode
import com.example.navisarthi.dash.nav.PlanningEstimate
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RouteState(
    val destination: SharedLocation? = null,
    val isResolving: Boolean = false,
    val pendingNavigate: Boolean = false,
    // Real routing results
    val route: Route? = null,
    val stops: List<SharedLocation> = emptyList(),
    val legs: List<Route> = emptyList(),
    val offline: Boolean = false,
    val needsStopChoice: Boolean = false,
    val mode: RouteMode = RouteMode.Car,
    val routingError: Boolean = false,
    val routing: Boolean = false,
    val distanceText: String? = null,   // "218 km"
    val durationText: String? = null,   // "4h 50m"
    val etaText: String? = null,        // "13:32"
)

class RouteViewModel(app: Application) : AndroidViewModel(app) {
    private val TAG = "RouteViewModel"
    private val _state = MutableStateFlow(RouteState())
    val state = _state.asStateFlow()

    data class SavedItinerary(val id: String, val name: String, val mode: RouteMode, val stops: List<SharedLocation>)

    private val itineraryPrefs = app.getSharedPreferences("saved_itineraries", Context.MODE_PRIVATE)
    private val _itineraries = MutableStateFlow(readItineraries())
    val itineraries = _itineraries.asStateFlow()

    private fun readItineraries(): List<SavedItinerary> = runCatching {
        val items = org.json.JSONArray(itineraryPrefs.getString("items", "[]"))
        List(items.length()) { index ->
            val item = items.getJSONObject(index)
            val points = item.getJSONArray("stops")
            SavedItinerary(item.getString("id"), item.getString("name"), RouteMode.valueOf(item.getString("mode")),
                List(points.length()) { position ->
                    val point = points.getJSONObject(position)
                    SharedLocation(point.getString("name"), point.getDouble("lat"), point.getDouble("lng"))
                })
        }.filter { it.stops.isNotEmpty() }
    }.getOrDefault(emptyList())

    private fun writeItineraries(items: List<SavedItinerary>) {
        val json = org.json.JSONArray()
        for (item in items) {
            val points = org.json.JSONArray()
            item.stops.forEach { stop ->
                points.put(org.json.JSONObject().put("name", stop.name).put("lat", stop.lat).put("lng", stop.lng))
            }
            json.put(org.json.JSONObject().put("id", item.id).put("name", item.name)
                .put("mode", item.mode.name).put("stops", points))
        }
        itineraryPrefs.edit().putString("items", json.toString()).apply()
        _itineraries.value = items
    }

    fun saveItinerary(name: String) {
        val plan = _state.value
        if (plan.stops.isEmpty() || plan.needsStopChoice || name.isBlank()) return
        val item = SavedItinerary(java.util.UUID.randomUUID().toString(), name.trim(), plan.mode, plan.stops.toList())
        writeItineraries(listOf(item) + _itineraries.value)
    }

    fun loadItinerary(item: SavedItinerary) {
        _state.value = RouteState(mode = item.mode)
        updateStops(item.stops)
    }

    fun deleteItinerary(id: String) {
        if (_downloadInProgress.value) return
        offlineRoutes.delete(id)
        _downloaded.value = _downloaded.value - id
        writeItineraries(_itineraries.value.filterNot { it.id == id })
    }

    private val offlineRoutes = com.example.navisarthi.data.OfflineRoutes(app)
    private val _downloaded = MutableStateFlow(_itineraries.value.map { it.id }.filter(offlineRoutes::exists).toSet())
    val downloaded = _downloaded.asStateFlow()
    private val _downloadStatus = MutableStateFlow("")
    val downloadStatus = _downloadStatus.asStateFlow()
    private val _downloadInProgress = MutableStateFlow(false)
    val downloadInProgress = _downloadInProgress.asStateFlow()

    @SuppressLint("MissingPermission")
    fun downloadItinerary(item: SavedItinerary) {
        if (_downloadInProgress.value) return
        val loc = runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
        if (loc == null) {
            _downloadStatus.value = "Current location unavailable. Get a GPS fix before downloading."
            return
        }
        _downloadInProgress.value = true
        viewModelScope.launch {
            try {
                var from = GeoPoint(loc.latitude, loc.longitude)
                val legs = mutableListOf<Route>()
                for ((index, stop) in item.stops.withIndex()) {
                    _downloadStatus.value = "Downloading leg ${index + 1} of ${item.stops.size}…"
                    val to = GeoPoint(requireNotNull(stop.lat), requireNotNull(stop.lng))
                    val route = Router.route(from, to, item.mode)
                    kotlin.coroutines.coroutineContext.ensureActive()
                    legs.add(route ?: error("Leg unavailable"))
                    from = to
                }
                withContext(Dispatchers.IO) { offlineRoutes.save(item.id, legs) }
                _downloaded.value = _downloaded.value + item.id
                _downloadStatus.value = "${item.name} is ready for offline guidance."
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _downloadStatus.value = "Download failed. Check your connection and try again."
            } finally {
                _downloadInProgress.value = false
            }
        }
    }

    fun openOffline(item: SavedItinerary) {
        routeRequest?.cancel()
        routeRequest = viewModelScope.launch {
            val legs = withContext(Dispatchers.IO) { runCatching { offlineRoutes.load(item.id) }.getOrNull() }
            if (legs == null || legs.size != item.stops.size) {
                _downloadStatus.value = "This download could not be opened. Download it again."
                return@launch
            }
            val distance = legs.sumOf { it.totalMeters }
            val duration = PlanningEstimate.seconds(distance, item.mode)
            _state.value = RouteState(destination = item.stops.last(), mode = item.mode,
                stops = item.stops, legs = legs, route = legs.first(), offline = true,
                distanceText = fmtKm(distance), durationText = fmtDuration(duration), etaText = fmtEta(duration))
            _downloadStatus.value = ""
        }
    }

    private val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val repo = com.example.navisarthi.data.SyncRepository.get(app)

    private val _saved = MutableStateFlow<List<com.example.navisarthi.data.SavedLocation>>(emptyList())
    /** Saved destinations the rider can tap to load + navigate again. */
    val saved = _saved.asStateFlow()

    init {
        reloadSaved()
        // Reflect saves made locally OR synced in from another device.
        viewModelScope.launch { repo.revision.collect { reloadSaved() } }
    }

    private fun reloadSaved() = viewModelScope.launch {
        _saved.value = withContext(Dispatchers.IO) { repo.savedLocations() }
    }

    /** Save the current resolved destination so it can be reused. No-op without coords. */
    fun saveCurrentDestination(name: String, note: String = "") {
        val d = _state.value.destination ?: return
        val lat = d.lat ?: return
        val lng = d.lng ?: return
        viewModelScope.launch { withContext(Dispatchers.IO) { repo.addSaved(name.ifBlank { d.name }, lat, lng, note) } }
    }

    fun renameSaved(loc: com.example.navisarthi.data.SavedLocation, name: String, note: String) =
        viewModelScope.launch { withContext(Dispatchers.IO) { repo.renameSaved(loc, name, note) } }

    fun deleteSaved(loc: com.example.navisarthi.data.SavedLocation) =
        viewModelScope.launch { withContext(Dispatchers.IO) { repo.deleteSaved(loc) } }

    /** A new place becomes a candidate while a journey already exists. */
    private fun selectPlace(place: SharedLocation, openRoute: Boolean, activeStops: List<SharedLocation>? = null) {
        routeRequest?.cancel()
        val previous = _state.value
        val stops = activeStops ?: previous.stops
        _state.value = RouteState(
            mode = previous.mode,
            destination = place,
            stops = stops,
            needsStopChoice = true,
            isResolving = place.needsExpansion,
            pendingNavigate = openRoute,
        )
    }

    fun selectSaved(loc: com.example.navisarthi.data.SavedLocation) {
        selectPlace(SharedLocation(loc.name, loc.lat, loc.lng), openRoute = false)
    }

    fun handleSharedText(text: String, activeStops: List<SharedLocation>? = null) {
        val loc = LocationParser.parse(text)
        selectPlace(loc, openRoute = true, activeStops = activeStops)
        if ((loc.lat == null || loc.lng == null) && loc.url != null) {
            viewModelScope.launch {
                val (urlCoords, resolvedName) = LocationParser.resolve(loc.url)
                val name = when {
                    loc.name.isNotBlank() && loc.name != "Loading…" -> loc.name
                    !resolvedName.isNullOrBlank() -> resolvedName
                    urlCoords != null -> "Dropped pin"
                    else -> "Shared location"
                }
                // Named places (e.g. "Third Wave Coffee, … Hyderabad") have no coords in
                // the URL — geocode the resolved address to get lat/lng.
                val coords = urlCoords ?: resolvedName?.let { geocode(it) }
                val resolved = loc.copy(
                    name = name, lat = coords?.first, lng = coords?.second, needsExpansion = false,
                )
                if (_state.value.destination !== loc) return@launch
                _state.value = _state.value.copy(destination = resolved, isResolving = false)
                if (coords == null) Log.w(TAG, "No coords for '$name' (url+geocode both empty)")
            }
        }
    }

    /** Geocode an address/place name to coordinates via the device's geocoder backend. */
    @Suppress("DEPRECATION")
    private suspend fun geocode(query: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) {
                Log.w(TAG, "No geocoder backend present")
                return@withContext null
            }
            val results = Geocoder(getApplication(), Locale.getDefault()).getFromLocationName(query, 1)
            val a = results?.firstOrNull() ?: run {
                Log.w(TAG, "Geocoder: no result for '$query'")
                return@withContext null
            }
            Dbg.i(TAG) { "Geocoded '$query' → ${a.latitude},${a.longitude}" }
            a.latitude to a.longitude
        } catch (e: Exception) {
            Log.w(TAG, "Geocoder failed: ${e.message}")
            null
        }
    }

    private var routeRequest: kotlinx.coroutines.Job? = null

    enum class StopChoice { Append, Replace }

    fun chooseStop(choice: StopChoice) {
        val state = _state.value
        val place = state.destination ?: return
        if (place.lat == null || place.lng == null) return
        if (choice != StopChoice.Replace && state.stops.size >= 8) return
        val proposed = when (choice) {
            StopChoice.Append -> state.stops + place
            StopChoice.Replace -> listOf(place)
        }
        updateStops(proposed.filterIndexed { index, stop ->
            index == 0 || proposed[index - 1].let { it.lat != stop.lat || it.lng != stop.lng }
        })
    }

    fun dismissStopChoice() = updateStops(_state.value.stops)

    fun removeStop(index: Int) {
        updateStops(_state.value.stops.filterIndexed { i, _ -> i != index })
    }

    fun moveStop(index: Int, offset: Int) {
        val stops = _state.value.stops.toMutableList()
        if (index !in stops.indices || index + offset !in stops.indices) return
        val stop = stops.removeAt(index)
        stops.add(index + offset, stop)
        updateStops(stops)
    }

    private fun updateStops(stops: List<SharedLocation>) {
        _state.value = _state.value.copy(stops = stops, destination = stops.lastOrNull(),
            needsStopChoice = false, isResolving = false)
        computeRoute()
    }

    fun setRouteMode(mode: RouteMode) {
        if (mode == _state.value.mode) return
        _state.value = _state.value.copy(mode = mode)
        computeRoute()
    }

    @SuppressLint("MissingPermission")
    fun computeRoute() {
        routeRequest?.cancel()
        val plan = _state.value
        _state.value = plan.copy(route = null, legs = emptyList(), offline = false, routing = false,
            routingError = false, distanceText = null, durationText = null, etaText = null)
        if (plan.stops.isEmpty() || plan.needsStopChoice) return
        val origin = runCatching {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }.getOrNull() ?: return

        _state.value = _state.value.copy(routing = true)
        routeRequest = viewModelScope.launch {
            var from = GeoPoint(origin.latitude, origin.longitude)
            val legs = mutableListOf<Route>()
            for (stop in plan.stops) {
                val to = GeoPoint(requireNotNull(stop.lat), requireNotNull(stop.lng))
                val leg = Router.route(from, to, plan.mode)
                kotlin.coroutines.coroutineContext.ensureActive()
                if (leg == null) {
                    _state.value = _state.value.copy(routing = false, routingError = true)
                    return@launch
                }
                legs.add(leg)
                from = to
            }
            val distance = legs.sumOf { it.totalMeters }
            val duration = PlanningEstimate.seconds(distance, plan.mode)
            _state.value = _state.value.copy(
                route = legs.first(), legs = legs.toList(), routing = false,
                distanceText = fmtKm(distance), durationText = fmtDuration(duration), etaText = fmtEta(duration),
            )
        }
    }

    fun onNavigated() { _state.value = _state.value.copy(pendingNavigate = false) }
    fun clear() { routeRequest?.cancel(); _state.value = RouteState(mode = _state.value.mode) }

    private fun fmtKm(m: Double) = if (m < 1000) "%.0f m".format(m) else "%.1f km".format(m / 1000.0)
    private fun fmtDuration(sec: Double): String {
        if (sec > 0 && sec < 60) return "<1m"
        val total = kotlin.math.ceil(sec / 60.0).toInt()
        return if (total >= 60) "${total / 60}h ${total % 60}m" else "${total}m"
    }
    private fun fmtEta(sec: Double): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(System.currentTimeMillis() + (sec * 1000).toLong()))
}
