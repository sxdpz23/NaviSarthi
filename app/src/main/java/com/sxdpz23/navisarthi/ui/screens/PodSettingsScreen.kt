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
import com.sxdpz23.navisarthi.dash.nav.VoiceManager
import com.sxdpz23.navisarthi.dash.nav.VoiceMode
import com.sxdpz23.navisarthi.ui.components.*
import com.sxdpz23.navisarthi.ui.theme.*
import com.sxdpz23.navisarthi.util.DeviceReadiness

@Composable
internal fun PodSettingsScreen(vm: PodViewModel, onVehicleSelected: (VehicleModel) -> Unit,
    garageEnabled: Boolean, onGarageEnabledChange: (Boolean) -> Unit, onBack: () -> Unit) {
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader(title = "Settings", onBack = onBack, hint = "Meteor 350 · Tripper Pod, Bluetooth and turn guidance")
        ControlSection("Vehicle")
        NaviSarthiBtn(LocalVehicle.current.label, onClick = { showPicker = true }, variant = BtnVariant.Secondary,
            modifier = Modifier.fillMaxWidth())
        ControlHint("Changing vehicle stops navigation and disconnects the current device. Your planned route and saved devices are kept.")
        if (showPicker) VehiclePicker(LocalVehicle.current, onSelect = { onVehicleSelected(it); showPicker = false },
            onDismiss = { showPicker = false })
        ControlSection("App features")
        PodToggle("Enable Garage", "Maintenance, fuel diary and vehicle records", garageEnabled, onGarageEnabledChange)
        PodConnectionControls(vm)
        PodToggle("Auto-connect saved Pod", "Reconnect at app start and after a dropped Bluetooth link", auto, vm::setAutoConnect)
        ControlSection("Pod display")
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
        ControlSection("Pod clock")
        PodToggle("24-hour clock", "Clock syncs automatically when the Pod authorizes", clock24, vm::setClock24)
        NaviSarthiBtn("Sync time now", onClick = vm.ble::syncClock, enabled = state.stage == PodStage.READY,
            variant = BtnVariant.Secondary, modifier = Modifier.fillMaxWidth())
        ControlHint("After stopping guidance, the Pod may briefly reconnect to return to its native clock. Restart the ignition if it is no longer discoverable.")
        ControlSection("Device information")
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
        ControlSection("Battery & background usage")
        ControlHint(if (restricted) "Allow unrestricted battery and background usage so Pod guidance can continue with the phone locked." else "No Android battery restrictions detected.")
        NaviSarthiBtn("Review background usage", onClick = {
            settingsError = runCatching { settings.launch(DeviceReadiness.appSettingsIntent(ctx)) }.isFailure
        }, variant = BtnVariant.Secondary, modifier = Modifier.fillMaxWidth())
        if (settingsError) ControlHint("Open Android Settings → Apps → NaviSarthi → Battery and allow background usage.")
        SharedVoiceAndUnitsSettings(pod = true)
        DriveBackupSettings()
        TestBuildCard()
        Text("NAVISARTHI v${com.sxdpz23.navisarthi.BuildConfig.VERSION_NAME}", color = TextLo)
    }
}

@Composable
private fun PodToggle(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    NaviSarthiCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) { Text(title, color = TextHi); Text(hint, color = TextMid) }
            NaviSarthiToggle(checked, onChange = onChange)
        }
    }
}

@Composable
internal fun SharedVoiceAndUnitsSettings(pod: Boolean = false) {
    val ctx = LocalContext.current
    val voice = remember { VoiceManager.get(ctx) }
    val mode by voice.mode.collectAsState()
    ControlSection("Voice & guidance")
    NaviSarthiSegmented(listOf("Off", "Chime", "Full TTS"), when (mode) {
        VoiceMode.OFF -> "Off"; VoiceMode.CHIME -> "Chime"; VoiceMode.FULL -> "Full TTS"
    }, { voice.setMode(when (it) { "Off" -> VoiceMode.OFF; "Full TTS" -> VoiceMode.FULL; else -> VoiceMode.CHIME }) }, Modifier.fillMaxWidth())
    ControlHint("Voice guidance uses your phone's selected audio output. Pair your helmet headset in Android settings.")
    ControlSection("Units")
    // Units here describe phone-side summaries. Pod wire distances remain the verified metric format.
    val prefs = remember { ctx.getSharedPreferences("guidance", android.content.Context.MODE_PRIVATE) }
    var units by remember { mutableStateOf(prefs.getString("units", "Kilometres") ?: "Kilometres") }
    NaviSarthiSegmented(listOf("Kilometres", "Miles"), units, {
        units = it; prefs.edit().putString("units", it).apply()
    }, Modifier.fillMaxWidth())
    if (pod) ControlHint("Phone summaries follow this setting. Pod distances use metres and kilometres.")
}
