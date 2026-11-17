package com.sxdpz23.navisarthi.ui.screens

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sxdpz23.navisarthi.pod.*
import com.sxdpz23.navisarthi.ui.components.*
import com.sxdpz23.navisarthi.ui.theme.*

@Composable
fun PodScreen(vm: PodViewModel) {
    val nav by vm.navigation.collectAsState()
    val connection by vm.ble.state.collectAsState()
    val ctx = LocalContext.current
    val miles = com.sxdpz23.navisarthi.navigation.ui.Units.imperial.value
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).padding(bottom = com.sxdpz23.navisarthi.ui.components.ControlSpacing.PageBottom),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenHeader(title = "Meteor 350 · Tripper Pod", hint = "Turn-by-turn guidance over Bluetooth")
        if (!nav.navProjection) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PodPreview(nav.cue, nav.turnMeters?.let {
                if (miles) "${(it * 3.28084).toInt()} ft" else "${it.toInt()} m"
            } ?: "Tripper Pod")
        }
        if (nav.navProjection) {
            NaviSarthiCard(modifier = Modifier.fillMaxWidth()) {
                Text("Navigation is on Pod", color = TextHi)
                ControlHint("Use Nav to temporarily view progress or edit the shared route")
            }
        }
        if (!nav.navProjection && nav.destination != null) {
            NaviSarthiCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
                Text(nav.destination!!, color = TextHi, fontSize = 19.sp)
                if (nav.stopCount > 0) Text("Stop ${nav.stopNumber} of ${nav.stopCount}", color = TextMid)
                nav.remainingMeters?.let {
                    val distance = if (miles) "%.1f mi".format(it / 1609.344) else "%.1f km".format(it / 1000)
                    Text("$distance remaining · ${nav.etaMinutes ?: "—"} min", color = Gold)
                }
                if (!nav.gpsReady) Text("Waiting for accurate GPS", color = Warn)
                nav.error?.let { Text(it, color = Warn) }
            }
            NaviSarthiBtn("Stop navigation", onClick = vm::exitNavigation, variant = BtnVariant.Secondary,
                modifier = Modifier.fillMaxWidth())
        }
        PodConnectionControls(vm)
        if (connection.stage == PodStage.READY) ControlHint("Guidance continues with the phone locked. Keep Bluetooth and precise location enabled.")
    }
}

@Composable
internal fun PodPreview(cue: String = "Turn-by-turn navigation", detail: String = "Bluetooth · RE_DISP") {
    Box(Modifier.size(210.dp).background(Bg0, CircleShape).border(2.dp, GoldDeep, CircleShape).padding(24.dp),
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(cue, color = Gold, fontSize = 19.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(detail, color = TextMid, fontSize = 13.sp)
        }
    }
}

@Composable
internal fun PodConnectionControls(vm: PodViewModel) {
    val state by vm.ble.state.collectAsState()
    val context = LocalContext.current
    var action by remember { mutableStateOf<(() -> Unit)?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val pending = action; action = null
        if (vm.ble.hasPermissions()) { localError = null; pending?.invoke() }
        else localError = "Nearby devices access is needed. Allow it in Android app settings and retry."
    }
    fun withBluetooth(block: () -> Unit) {
        if (vm.ble.hasPermissions()) block() else {
            action = block
            permissions.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.POST_NOTIFICATIONS))
        }
    }
    val status = when (state.stage) {
        PodStage.OFFLINE -> "Not connected"
        PodStage.SCANNING -> "Searching for your Pod…"
        PodStage.CONNECTING -> "Connecting over Bluetooth…"
        PodStage.RECONNECTING -> "Reconnecting to your Pod…"
        PodStage.AUTHORIZING -> "Waiting for Pod authorization…"
        PodStage.PIN_REQUIRED -> "Enter the PIN displayed on your Pod"
        PodStage.READY -> "Connected and authorized"
        PodStage.ERROR -> "Connection needs attention"
    }
    ControlSection("Tripper Pod connection")
    Text(status, color = if (state.stage == PodStage.READY) Gold else TextHi)
    (localError ?: state.error)?.let { Text(it, color = Warn) }
    if (localError != null) NaviSarthiBtn("Open app permissions", onClick = {
        context.startActivity(com.sxdpz23.navisarthi.util.DeviceReadiness.appSettingsIntent(context))
    }, variant = BtnVariant.Secondary, modifier = Modifier.fillMaxWidth())
    if (state.stage !in listOf(PodStage.READY, PodStage.CONNECTING, PodStage.RECONNECTING, PodStage.AUTHORIZING, PodStage.PIN_REQUIRED)) {
        if (vm.ble.savedAddress != null) NaviSarthiBtn("Connect saved Pod", onClick = {
            withBluetooth { vm.ble.connect() }
        }, modifier = Modifier.fillMaxWidth())
        NaviSarthiBtn(if (state.stage == PodStage.SCANNING) "Scan again" else "Find Tripper Pod", onClick = {
            withBluetooth { vm.ble.discover() }
        }, variant = BtnVariant.Secondary, modifier = Modifier.fillMaxWidth())
        ControlHint("Turn on your Meteor's ignition and Bluetooth. Select its RE_DISP device below. Close the Royal Enfield app if it is connected.")
        state.devices.forEach { device ->
            NaviSarthiBtn("${device.name} · ${device.address.takeLast(8)}", onClick = {
                withBluetooth { vm.ble.connect(device.address) }
            }, variant = BtnVariant.Secondary, modifier = Modifier.fillMaxWidth())
        }
        NaviSarthiBtn("Bluetooth settings", onClick = {
            runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        }, variant = BtnVariant.Ghost, modifier = Modifier.fillMaxWidth())
    }
    if (state.stage == PodStage.PIN_REQUIRED) {
        var pin by remember(state.address) { mutableStateOf("") }
        OutlinedTextField(pin, { pin = it.filter { c -> c in '0'..'9' }.take(6) },
            label = { Text("Six-digit Pod PIN") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth())
        NaviSarthiBtn("Authorize Pod", onClick = { vm.ble.submitPin(pin); pin = "" }, enabled = pin.length == 6,
            modifier = Modifier.fillMaxWidth())
    }
    if (state.stage in listOf(PodStage.READY, PodStage.SCANNING, PodStage.CONNECTING, PodStage.RECONNECTING, PodStage.AUTHORIZING, PodStage.PIN_REQUIRED)) {
        NaviSarthiBtn("Disconnect", onClick = vm::disconnect, variant = BtnVariant.Secondary, modifier = Modifier.fillMaxWidth())
    }
    if (vm.ble.savedAddress != null) {
        var forget by remember { mutableStateOf(false) }
        NaviSarthiBtn("Forget Pod", onClick = { forget = true }, variant = BtnVariant.Ghost, modifier = Modifier.fillMaxWidth())
        if (forget) AlertDialog(onDismissRequest = { forget = false }, title = { Text("Forget Tripper Pod?") },
            text = { Text("Navigation stops and the saved pairing is removed. You will need the Pod's PIN to pair again.") },
            confirmButton = { TextButton(onClick = { vm.forget(); forget = false }) { Text("Forget") } },
            dismissButton = { TextButton(onClick = { forget = false }) { Text("Cancel") } })
    }
}
