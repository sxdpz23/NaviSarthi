package com.sxdpz23.navisarthi.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sxdpz23.navisarthi.dash.nav.NavDisplaySession
import com.sxdpz23.navisarthi.navigation.ui.map.MapViewModel
import com.sxdpz23.navisarthi.ui.components.*
import com.sxdpz23.navisarthi.ui.theme.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

/** One route/guidance owner. The handset MapView exists only while explicitly needed. */
@Composable
fun NavScreen(vm: MapViewModel, pod: Boolean, onOpenSettings: () -> Unit) {
    val shared by NavDisplaySession.state.collectAsState()
    val placeOpen by remember(vm) { vm.state.map { it.selected != null || it.directionsOpen || it.navigating }.distinctUntilChanged() }.collectAsState(false)
    val mapBottomSpace = if (placeOpen) 0.dp else 112.dp
    val console = if (pod) "Pod" else "Dash"
    BackHandler(shared.active && shared.phoneVisible) { NavDisplaySession.hidePhone() }
    Column(Modifier.fillMaxSize().padding(bottom = mapBottomSpace)) {
        if (shared.active && !shared.phoneVisible) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ScreenHeader(title = "Navigation is on $console", hint = "Nav is handling the route and voice guidance. You can lock the phone while riding.")
                NaviSarthiCard(Modifier.fillMaxWidth()) {
                    Text(shared.nav.destinationLabel.ifBlank { "Current journey" }, color = TextHi)
                    ControlHint(if (shared.nav.paused) "Navigation paused" else "The phone map is hidden to save power")
                }
                NaviSarthiBtn("View on phone temporarily", onClick = { NavDisplaySession.showPhone() }, modifier = Modifier.fillMaxWidth())
                NaviSarthiBtn("Edit route or stops", onClick = {
                    NavDisplaySession.showPhone(edit = true); vm.openStopsEditor()
                }, variant = BtnVariant.Secondary, modifier = Modifier.fillMaxWidth())
                NaviSarthiBtn("Stop navigation", onClick = vm::stopNav, variant = BtnVariant.Danger, modifier = Modifier.fillMaxWidth())
                ControlHint("Temporary viewing closes after two minutes. Route editing stays open until you return to $console.")
            }
        } else {
            if (shared.active) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                NaviSarthiBtn("Return to $console", onClick = NavDisplaySession::hidePhone,
                    variant = BtnVariant.Secondary, size = BtnSize.Sm, modifier = Modifier.fillMaxWidth())
            }
            Box(Modifier.weight(1f)) {
                CompositionLocalProvider(com.sxdpz23.navisarthi.navigation.ui.LocalHostMapOverflow provides mapBottomSpace) {
                    com.sxdpz23.navisarthi.navigation.ui.theme.NavigationTheme {
                        com.sxdpz23.navisarthi.navigation.ui.NavigationRoot(vm, onOpenSettings)
                    }
                }
            }
        }
    }
}
