package com.sxdpz23.navisarthi.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sxdpz23.navisarthi.navigation.ui.map.MapViewModel
import com.sxdpz23.navisarthi.navigation.core.model.TravelMode
import com.sxdpz23.navisarthi.ui.components.NaviSarthiBtn
import com.sxdpz23.navisarthi.ui.NaviSarthiIcons
import com.sxdpz23.navisarthi.ui.theme.*
import com.sxdpz23.navisarthi.data.SharedLocation
import com.sxdpz23.navisarthi.dash.nav.Route
import com.sxdpz23.navisarthi.dash.nav.NavigationRouting

@Composable
fun NavScreen(vm: MapViewModel, pod: Boolean, onOpenSettings: () -> Unit, onSend: (List<SharedLocation>, List<Route>, Boolean) -> Unit) {
    val state = vm.state.collectAsState()
    val route by remember { derivedStateOf { state.value.activeRoute } }
    val drive by remember { derivedStateOf { state.value.travelMode == TravelMode.DRIVE } }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().consumeWindowInsets(WindowInsets.statusBars).padding(bottom = 112.dp)) {
        Box(Modifier.weight(1f)) {
            CompositionLocalProvider(com.sxdpz23.navisarthi.navigation.ui.LocalHostMapOverflow provides (112.dp + if (route != null && drive) 68.dp else 0.dp)) {
            com.sxdpz23.navisarthi.navigation.ui.theme.NavigationTheme(darkTheme = com.sxdpz23.navisarthi.navigation.ui.theme.isAppInDarkTheme()) {
                com.sxdpz23.navisarthi.navigation.ui.NavigationRoot(vm, onOpenSettings)
            }
            }
        }
        if (route != null && drive) {
            error?.let { Text(it, color = Warn, modifier = Modifier.padding(horizontal = 20.dp)) }
            NaviSarthiBtn("Use route on ${if (pod) "Pod" else "Dash"}", icon = NaviSarthiIcons.Dash,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), onClick = {
                    try {
                        val selected = requireNotNull(route)
                        val legs = NavigationRouting.consoleLegs(selected)
                        val places = state.value.directionsWaypoints + listOfNotNull(state.value.selected)
                        val stops = legs.mapIndexed { index, leg ->
                            val point = requireNotNull(leg.destination)
                            SharedLocation(places.getOrNull(index)?.name ?: "Stop ${index + 1}", point.lat, point.lng)
                        }
                        vm.stopNav() // The console becomes the sole owner of voice/location guidance.
                        onSend(stops, legs, selected.offline)
                        error = null
                    } catch (_: IllegalArgumentException) { error = "This route is not ready for the console yet" }
                })
        }
    }
}
