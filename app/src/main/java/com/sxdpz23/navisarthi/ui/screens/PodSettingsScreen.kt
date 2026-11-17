package com.sxdpz23.navisarthi.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sxdpz23.navisarthi.pod.*
import com.sxdpz23.navisarthi.ui.components.*
import com.sxdpz23.navisarthi.ui.theme.*
import com.sxdpz23.navisarthi.util.DeviceReadiness

@Composable
internal fun PodSettingsScreen(vm: PodViewModel, onVehicleSelected: (VehicleModel) -> Unit,
    garageEnabled: Boolean, onGarageEnabledChange: (Boolean) -> Unit, onBack: () -> Unit, onOpenNavigationSettings: () -> Unit = {}, onOpenRides: () -> Unit = {}) {
    val ctx = LocalContext.current
    val state by vm.ble.state.collectAsState()
    val night by vm.night.collectAsState()
    val bottom by vm.bottom.collectAsState()
    val clock24 by vm.clock24.collectAsState()
    val auto by vm.autoConnect.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    var restricted by remember { mutableStateOf(!DeviceReadiness.isIgnoringBatteryOptimizations(ctx) || DeviceReadiness.isBackgroundRestricted(ctx)) }
    var settingsError by remember { mutableStateOf(false) }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        restricted = !DeviceReadiness.isIgnoringBatteryOptimizations(ctx) || DeviceReadiness.isBackgroundRestricted(ctx)
    }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) restricted = !DeviceReadiness.isIgnoringBatteryOptimizations(ctx) || DeviceReadiness.isBackgroundRestricted(ctx)
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    com.sxdpz23.navisarthi.navigation.ui.settings.SettingsScaffold("Pod", onBack) { _ ->
        com.sxdpz23.navisarthi.navigation.ui.settings.SettingsGroup {
            com.sxdpz23.navisarthi.navigation.ui.settings.SettingsActionRow("Ride history", onClick = onOpenRides)
        }
        PodConnectionControls(vm)
        PodToggle("Auto-connect saved Pod", "Reconnect at app start and after a dropped Bluetooth link", auto, vm::setAutoConnect)
        com.sxdpz23.navisarthi.navigation.ui.settings.SubHead("Pod display")
        PodToggle("Night mode", "Use the Pod's dark navigation background", night, vm::setNight)
        Text("Journey information", color = TextHi)
        NaviSarthiSegmented(listOf("Distance", "Time left", "Arrival"), when (bottom) {
            PodBottomLine.DISTANCE -> "Distance"
            PodBottomLine.TIME_REMAINING -> "Time left"
            PodBottomLine.ARRIVAL_TIME -> "Arrival"
        }, { vm.setBottom(when (it) {
            "Time left" -> PodBottomLine.TIME_REMAINING
            "Arrival" -> PodBottomLine.ARRIVAL_TIME
            else -> PodBottomLine.DISTANCE
        }) }, Modifier.fillMaxWidth())
        com.sxdpz23.navisarthi.navigation.ui.settings.SubHead("Pod clock")
        PodToggle("24-hour clock", "Clock syncs automatically when the Pod authorizes", clock24, vm::setClock24)
        NaviSarthiBtn("Sync time now", onClick = vm.ble::syncClock, enabled = state.stage == PodStage.READY,
            variant = BtnVariant.Secondary, modifier = Modifier.fillMaxWidth())
        ControlHint("After stopping guidance, the Pod may briefly reconnect to return to its native clock. Restart the ignition if it is no longer discoverable.")
        com.sxdpz23.navisarthi.navigation.ui.settings.SubHead("Device information")
        NaviSarthiCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp) {
            Text("Tripper Pod · ${state.address ?: vm.ble.savedAddress ?: "Not paired"}", color = TextHi)
            Text("Firmware: ${state.firmware ?: "Not reported"}", color = TextMid)
            Text("Serial: ${state.serial ?: "Not reported"}", color = TextMid)
        }
        if (com.sxdpz23.navisarthi.BuildConfig.DEBUG) NaviSarthiBtn("Copy Pod diagnostics", onClick = {
            ctx.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(
                android.content.ClipData.newPlainText("Tripper Pod diagnostics",
                    "NaviSarthi ${com.sxdpz23.navisarthi.BuildConfig.VERSION_NAME}\nStage: ${state.stage}\nFirmware: ${state.firmware}\nSent packets: ${state.sent}\nMalformed replies: ${state.malformed}\nError: ${state.error ?: "none"}"))
        }, variant = BtnVariant.Secondary, modifier = Modifier.fillMaxWidth())

    }
}

@Composable
private fun PodToggle(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    com.sxdpz23.navisarthi.navigation.ui.settings.SettingsGroup {
        com.sxdpz23.navisarthi.navigation.ui.settings.ToggleRow(title, checked, onChange, hint = hint)
    }
}
