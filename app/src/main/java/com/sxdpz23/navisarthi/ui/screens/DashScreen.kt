package com.sxdpz23.navisarthi.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sxdpz23.navisarthi.ui.NaviSarthiIcons
import com.sxdpz23.navisarthi.ui.components.*
import com.sxdpz23.navisarthi.ui.components.SpacedControlHint as ControlHint
import com.sxdpz23.navisarthi.ui.theme.*
import com.sxdpz23.navisarthi.dash.nav.DashRouteColor
import com.sxdpz23.navisarthi.dash.protocol.EtaDisplayMode
import com.sxdpz23.navisarthi.viewmodel.ConnStage
import com.sxdpz23.navisarthi.viewmodel.DashViewModel
import kotlinx.coroutines.delay

@Composable
fun DashScreen(vm: DashViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()
    // Projection keeps the handset map/bitmap consumer stopped until Nav is opened temporarily.
    val previewFrame = if (!ui.navProjection) {
        val frame by vm.previewFrame.collectAsState()
        frame
    } else null
    val context = LocalContext.current

    // WiFi network request (13+: NEARBY_WIFI_DEVICES) + GPS for the map
    // Essential perms gate the connection; notifications are requested but optional.
    val essentialPermissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }.toTypedArray()
    }
    val requestedPermissions = remember {
        buildList {
            addAll(essentialPermissions)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }

    fun hasEssentialPermissions() = essentialPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    // Cross-OEM: is the app exempt from battery optimization? Without it, OxygenOS/One UI/MIUI/etc.
    // kill the stream when the screen turns off. Re-checked on resume (the rider grants it in a
    // system dialog and returns).
    var batteryOk by remember {
        mutableStateOf(com.sxdpz23.navisarthi.util.DeviceReadiness.isIgnoringBatteryOptimizations(context))
    }

    // The battery-exemption system dialog, launched as the last step before connecting. Whatever
    // the rider chooses, we re-check and continue the connect they started — no second button.
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        batteryOk = com.sxdpz23.navisarthi.util.DeviceReadiness.isIgnoringBatteryOptimizations(context)
        vm.connect()
    }

    // Connect, but first ask for background activity (battery-optimization exemption) once if it's
    // not already granted — folded into Connect so there's a single button. Remembered so we don't
    // re-prompt on later connects.
    fun connectWithBackgroundCheck() {
        if (!batteryOk && !com.sxdpz23.navisarthi.util.DeviceReadiness.batteryExemptionAsked(context)) {
            com.sxdpz23.navisarthi.util.DeviceReadiness.markBatteryExemptionAsked(context)
            val launched = runCatching {
                batteryLauncher.launch(com.sxdpz23.navisarthi.util.DeviceReadiness.batteryExemptionIntent(context))
            }.isSuccess
            if (!launched) vm.connect()  // OEM without the standard dialog — connect anyway
        } else {
            vm.connect()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val essentialOk = essentialPermissions.all { results[it] == true }
        if (essentialOk) connectWithBackgroundCheck()
    }

    // Local preview state (mirrors what the dash shows)
    var pan by remember { mutableStateOf(Offset.Zero) }
    var adjustMode by remember { mutableStateOf(true) }
    var joystickVelocity by remember { mutableStateOf(Offset.Zero) }

    // Physical joystick button → preview-only nudge (real map pan happens in the ViewModel)
    LaunchedEffect(ui.lastButton) {
        val b = ui.lastButton ?: return@LaunchedEffect
        when {
            b.startsWith("→") -> pan = Offset((pan.x - 12f).coerceIn(-46f, 46f), pan.y)
            b.startsWith("←") -> pan = Offset((pan.x + 12f).coerceIn(-46f, 46f), pan.y)
            b.startsWith("↓") -> pan = Offset(pan.x, (pan.y - 12f).coerceIn(-46f, 46f))
            b.startsWith("↑") -> pan = Offset(pan.x, (pan.y + 12f).coerceIn(-46f, 46f))
            b.startsWith("●") -> pan = Offset.Zero
        }
    }

    LaunchedEffect(joystickVelocity, adjustMode) {
        while (adjustMode && (joystickVelocity.x != 0f || joystickVelocity.y != 0f)) {
            pan = Offset(
                (pan.x - joystickVelocity.x * 2.4f).coerceIn(-46f, 46f),
                (pan.y - joystickVelocity.y * 2.4f).coerceIn(-46f, 46f),
            )
            vm.panBy(joystickVelocity.x * 4f, joystickVelocity.y * 4f)
            delay(16)
        }
    }

    val streaming = ui.stage == ConnStage.STREAMING

    // Auto-connect on opening the Dash screen, so the rider doesn't tap "Connect" every
    // ride — just open the app. Fires once; if the dash is off it errors out quietly and
    // the rider can retry. (Needs permissions already granted from a prior run.)
    // rememberSaveable so a config change (rotation/theme) doesn't reset this and silently
    // reconnect after the rider deliberately disconnected.
    var autoConnectTried by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!autoConnectTried && ui.stage == ConnStage.OFFLINE && hasEssentialPermissions()) {
            autoConnectTried = true
            vm.connect()
        }
    }

    // If the rider was prompted to turn Wi‑Fi on and comes back to the app with it now enabled
    // (e.g. from the Wi‑Fi panel), retry the connection automatically — no second tap needed.
    val uiState = rememberUpdatedState(ui)
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, ui.navProjection) {
        vm.setPreviewVisible(!ui.navProjection && lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) vm.setPreviewVisible(!uiState.value.navProjection)
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) vm.setPreviewVisible(false)
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                batteryOk = com.sxdpz23.navisarthi.util.DeviceReadiness.isIgnoringBatteryOptimizations(context)
                if (uiState.value.needsWifiOn &&
                    (context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE)
                        as android.net.wifi.WifiManager).isWifiEnabled &&
                    hasEssentialPermissions()
                ) {
                    vm.connect()
                }
                // Came back with Location services now enabled → retry the pairing automatically.
                if (uiState.value.needsLocationOn &&
                    com.sxdpz23.navisarthi.util.DeviceReadiness.locationServicesEnabled(context) &&
                    hasEssentialPermissions()
                ) {
                    vm.connect()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs); vm.setPreviewVisible(false) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ControlSpacing.PageHorizontal, vertical = 20.dp)
            .padding(bottom = ControlSpacing.PageBottom),
        verticalArrangement = Arrangement.spacedBy(ControlSpacing.Item),
    ) {
        ScreenHeader(
            title = "Dash",
            hint = "Nav owns your route, voice guidance and map. Dash keeps the connection and power-saving display controls.",
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (streaming && ui.gpsStatus != com.sxdpz23.navisarthi.viewmodel.GpsStatus.GOOD) {
                        NaviSarthiChip(
                            if (ui.gpsStatus == com.sxdpz23.navisarthi.viewmodel.GpsStatus.LOST) "GPS lost" else "GPS weak",
                            if (ui.gpsStatus == com.sxdpz23.navisarthi.viewmodel.GpsStatus.LOST) ChipTone.Alert else ChipTone.Warn,
                        )
                    }
                    if (streaming && ui.thermal != "OK") {
                        NaviSarthiChip(
                            ui.thermal,
                            if (ui.thermal == "Warm") ChipTone.Warn else ChipTone.Alert,
                        )
                    }
                    when (ui.stage) {
                        ConnStage.STREAMING -> NaviSarthiChip("LIVE", ChipTone.Gold, dot = true)
                        ConnStage.WIFI,
                        ConnStage.AUTH      -> NaviSarthiChip("Connecting…", ChipTone.Neutral)
                        ConnStage.ERROR     -> NaviSarthiChip("Error", ChipTone.Alert)
                        ConnStage.OFFLINE   -> NaviSarthiChip("Offline", ChipTone.Off)
                    }
                }
            },
        )

        // Single connection card (hidden once streaming)
        if (!streaming) {
            NaviSarthiCard(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            when (ui.stage) {
                                ConnStage.OFFLINE -> "Connect to Tripper Dash"
                                ConnStage.WIFI    -> if (ui.ssid.isNotBlank()) "Joining ${ui.ssid}…" else "Finding your dash…"
                                ConnStage.AUTH    -> if (ui.retryAttempt > 0) "Retrying handshake…" else "Pairing with dash…"
                                ConnStage.ERROR   -> "Couldn't connect"
                                ConnStage.STREAMING -> "Streaming"
                            },
                            color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            when (ui.stage) {
                                ConnStage.OFFLINE -> "Turn the dash on, stand near the bike, then tap Connect"
                                ConnStage.WIFI    -> "Accept the system dialog if it appears"
                                ConnStage.AUTH    -> if (ui.retryAttempt > 0)
                                    "Handshake attempt ${ui.retryAttempt} of 4 — this is normal"
                                    else "Securing the link to firmware 11.63…"
                                ConnStage.ERROR   -> ui.errorMessage
                                    ?: "Make sure the dash is on and you're beside the bike, then try again."
                                ConnStage.STREAMING -> ""
                            },
                            color = if (ui.stage == ConnStage.ERROR) Warn else TextMid,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    if (ui.stage == ConnStage.OFFLINE || ui.stage == ConnStage.ERROR) {
                        NaviSarthiBtn(
                            when {
                                ui.needsWifiOn -> "Turn on Wi‑Fi"
                                ui.needsLocationOn -> "Turn on Location"
                                ui.stage == ConnStage.ERROR -> "Try again"
                                else -> "Connect"
                            },
                            onClick = {
                                when {
                                    // Android 10+ won't let us toggle Wi‑Fi — open the system Wi‑Fi
                                    // panel (slide-up, stays in-app). connect() auto-retries on resume.
                                    ui.needsWifiOn -> runCatching {
                                        context.startActivity(
                                            android.content.Intent(android.provider.Settings.Panel.ACTION_WIFI)
                                        )
                                    }
                                    // Location services (master toggle) must be ON to resolve the dash
                                    // SSID for the auth handshake on first pairing. Open the location
                                    // settings; connect() auto-retries on resume.
                                    ui.needsLocationOn -> runCatching {
                                        context.startActivity(
                                            android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                        )
                                    }
                                    // Connect handles the background-activity ask itself (single button).
                                    hasEssentialPermissions() -> connectWithBackgroundCheck()
                                    else -> permissionLauncher.launch(requestedPermissions)
                                }
                            },
                            icon = NaviSarthiIcons.Wifi,
                            variant = BtnVariant.Primary,
                            size = BtnSize.Sm,
                        )
                    } else {
                        NaviSarthiBtn(
                            "Cancel",
                            onClick = { vm.disconnect() },
                            variant = BtnVariant.Ghost,
                            size = BtnSize.Sm,
                        )
                    }
                }
                if (ui.frameCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Frames encoded: ${ui.frameCount}",
                        color = TextLo, fontSize = 12.sp, fontFamily = GeistMonoFamily,
                    )
                }
                ui.lastButton?.let { btn ->
                    Text(
                        "Joystick: $btn",
                        color = Gold, fontSize = 12.sp, fontFamily = GeistMonoFamily,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }


        if (ui.navProjection) {
            NaviSarthiCard(modifier = Modifier.fillMaxWidth()) {
                Text("Navigation is on Dash", color = TextHi, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                ControlHint("The phone map is hidden to save power. Open Nav to temporarily view progress or change the route, then return it to Dash.")
            }
        } else {
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(272.dp).clip(CircleShape)
                    .border(6.dp, Color(0xFF0D0F10), CircleShape)
                    .border(2.dp, Line2, CircleShape).background(Bg0), contentAlignment = Alignment.Center) {
                    val frame = previewFrame
                    if (streaming && frame != null) {
                        androidx.compose.foundation.Image(
                            bitmap = frame.asImageBitmap(), contentDescription = "Dash display preview",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(if (streaming) "Preparing Dash…" else "Choose a route in Nav",
                            color = TextMid, modifier = Modifier.padding(24.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }

        if (!ui.navProjection) {
            // Next-turn banner (real turn-by-turn from the routing engine)
            ui.maneuver?.let { mv ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (ui.offRoute) Color(0x33D8853E) else GoldTint)
                        .border(1.dp, if (ui.offRoute) Warn else GoldTint2, RoundedCornerShape(13.dp))
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                ) {
                    Icon(NaviSarthiIcons.Navi, null, tint = if (ui.offRoute) Warn else Gold, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(11.dp))
                    Text(
                        if (ui.offRoute) "Off route — rerouting…" else mv,
                        color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            // Live info strip — real remaining distance, ETA, zoom
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    Triple(
                        ui.remainingKm?.let { if (it >= 10) "%.0f".format(it) else "%.1f".format(it) } ?: "—",
                        if (ui.remainingKm != null) "km" else "", "Remaining",
                    ),
                    Triple(ui.etaMinutes?.toString() ?: "—", if (ui.etaMinutes != null) "min" else "", "ETA"),
                    Triple("z${ui.mapZoom}", "", "Zoom"),
                ).forEach { (v, u, k) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(13.dp))
                            .background(Surf1)
                            .border(1.dp, Line, RoundedCornerShape(13.dp))
                            .padding(11.dp),
                    ) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                            Text(v, color = TextHi, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = GeistMonoFamily)
                            if (u.isNotEmpty()) {
                                Spacer(Modifier.width(3.dp))
                                Text(u, color = TextLo, fontSize = 12.sp, fontFamily = GeistMonoFamily, modifier = Modifier.padding(bottom = 2.dp))
                            }
                        }
                        Eyebrow(k, Modifier.padding(top = 3.dp))
                    }
                }
            }

        }


        if (streaming) ControlHint("Automatic power profile: ${ui.powerProfile}. Precision and console connection remain active.")
        ControlSection("ETA display")
        NaviSarthiSegmented(
            options = EtaDisplayMode.entries.map { it.label },
            selected = ui.etaDisplayMode.label,
            onSelect = { label -> vm.setEtaDisplayMode(EtaDisplayMode.entries.first { it.label == label }) },
            modifier = Modifier.fillMaxWidth(),
        )
        ControlHint("Show arrival time and minutes or hours remaining in the ribbon or in a pill below the ride cursor. ETA uses your average speed since navigation started, including stopped time, and appears after movement is measured.")

        // Map adjust mode toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (adjustMode) GoldTint else Surf1)
                .border(1.dp, if (adjustMode) GoldTint2 else Line, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(NaviSarthiIcons.Cross, null, tint = if (adjustMode) Gold else TextMid, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(11.dp))
                Column {
                    Text("Map adjust mode", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = GeistFamily)
                    Text("Mirrors the bike joystick", color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 1.dp))
                }
            }
            NaviSarthiToggle(on = adjustMode, onChange = { adjustMode = it })
        }

        Spacer(Modifier.height(10.dp))

        if (ui.stopCount > 1) {
            Text("Stop ${ui.stopNumber} of ${ui.stopCount}: ${ui.destinationName.orEmpty()}",
                color = TextHi, modifier = Modifier.padding(top = 10.dp))
            if (ui.stopNumber < ui.stopCount) {
                NaviSarthiBtn("Skip this stop", onClick = vm::skipStop,
                    variant = BtnVariant.Secondary, modifier = Modifier.fillMaxWidth())
                ControlHint("Continue to the next stop in your itinerary.")
            }
        }
        ControlSection("Dash view")
        NaviSarthiSegmented(
            options = listOf("TDV", "POV"),
            selected = if (ui.perspective) "POV" else "TDV",
            onSelect = { vm.setPerspective(it == "POV") },
            modifier = Modifier.fillMaxWidth(),
        )
        ControlHint("TDV looks down from above; POV looks along the road. This shortcut changes the same view selected in Nav.")
        ControlSection("Route colour on Dash")
        val routeColours = listOf(
            DashRouteColor.BLUE to "Blue",
            DashRouteColor.VEHICLE_ACCENT to "Vehicle accent",
            DashRouteColor.GOLDEN_LINE to "Golden line",
        )
        NaviSarthiSegmented(
            options = routeColours.map { it.second },
            selected = routeColours.first { it.first == ui.routeColor }.second,
            onSelect = { label -> vm.setRouteColor(routeColours.first { it.second == label }.first) },
            modifier = Modifier.fillMaxWidth(),
        )
        ControlHint("Blue is the default. Change the route colour here; select the map scheme, traffic and route alternatives in Nav.")

        Spacer(Modifier.height(16.dp))

        ControlSection("Map controls")
        ControlHint("Pan with the on-screen joystick. Use − or + to change the dash zoom.", Modifier.padding(bottom = 8.dp))

        // Controls: joystick + zoom (drive the actual dash map)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Surf1)
                    .border(1.dp, Line, RoundedCornerShape(18.dp))
                    .padding(vertical = 16.dp, horizontal = 12.dp),
            ) {
                Joystick(
                    size = 128.dp,
                    onMove = { v -> joystickVelocity = if (adjustMode) v else Offset.Zero },
                )
                Spacer(Modifier.height(9.dp))
                Eyebrow("Pan")
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NaviSarthiIconBtn(NaviSarthiIcons.Plus,  onClick = { vm.zoomIn() }, contentDescription = "Zoom in", size = 52.dp)
                Text("z${ui.mapZoom}", color = Gold, fontSize = 12.sp, fontFamily = GeistMonoFamily, fontWeight = FontWeight.SemiBold)
                NaviSarthiIconBtn(NaviSarthiIcons.Minus, onClick = { vm.zoomOut() }, contentDescription = "Zoom out", size = 52.dp)
                NaviSarthiIconBtn(NaviSarthiIcons.Recenter, onClick = { vm.recenter(); pan = Offset.Zero }, size = 52.dp, active = true, contentDescription = "Recenter map")
            }
        }

        // Nav owns stop/completion; the view-model forwards this action to that same session.
        if (streaming && ui.destinationName != null) {
            Spacer(Modifier.height(16.dp))
            NaviSarthiBtn(
                "Stop navigation",
                onClick = { vm.exitNavigation() },
                icon = NaviSarthiIcons.Navi,
                variant = BtnVariant.Ghost,
                size = BtnSize.Md,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Disconnect button when streaming
        if (streaming) {
            Spacer(Modifier.height(20.dp))
            NaviSarthiBtn(
                "Disconnect",
                onClick = { vm.disconnect() },
                icon = NaviSarthiIcons.Power,
                variant = BtnVariant.Danger,
                size = BtnSize.Md,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
