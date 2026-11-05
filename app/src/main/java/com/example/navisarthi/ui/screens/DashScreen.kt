package com.example.navisarthi.ui.screens

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.navisarthi.ui.NaviSarthiIcons
import com.example.navisarthi.ui.components.*
import com.example.navisarthi.ui.theme.*
import com.example.navisarthi.viewmodel.ConnStage
import com.example.navisarthi.viewmodel.DashViewModel
import kotlinx.coroutines.delay

@Composable
fun DashScreen(vm: DashViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()
    val previewFrame by vm.previewFrame.collectAsState()
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
        mutableStateOf(com.example.navisarthi.util.DeviceReadiness.isIgnoringBatteryOptimizations(context))
    }

    // The battery-exemption system dialog, launched as the last step before connecting. Whatever
    // the rider chooses, we re-check and continue the connect they started — no second button.
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        batteryOk = com.example.navisarthi.util.DeviceReadiness.isIgnoringBatteryOptimizations(context)
        vm.connect()
    }

    // Connect, but first ask for background activity (battery-optimization exemption) once if it's
    // not already granted — folded into Connect so there's a single button. Remembered so we don't
    // re-prompt on later connects.
    fun connectWithBackgroundCheck() {
        if (!batteryOk && !com.example.navisarthi.util.DeviceReadiness.batteryExemptionAsked(context)) {
            com.example.navisarthi.util.DeviceReadiness.markBatteryExemptionAsked(context)
            val launched = runCatching {
                batteryLauncher.launch(com.example.navisarthi.util.DeviceReadiness.batteryExemptionIntent(context))
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
    // Tear the MapLibre map down the INSTANT a connection starts (Wi-Fi/auth), not at the streaming
    // transition. Disposing MapLibre's native MapView (TextureView GL surface) at the same moment the
    // MediaCodec encoder input Surface is created races the GL renderer thread's eglSwap on some OEMs
    // (MIUI especially) → SIGABRT in libgui/libhwui (mbgl AndroidGLRenderableResource::swap). Disposing
    // it seconds earlier, while connecting, lets the GL thread fully tear down before the encoder spins up.
    val connectingOrLive = ui.stage == ConnStage.WIFI || ui.stage == ConnStage.AUTH || streaming

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
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                batteryOk = com.example.navisarthi.util.DeviceReadiness.isIgnoringBatteryOptimizations(context)
                if (uiState.value.needsWifiOn &&
                    (context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE)
                        as android.net.wifi.WifiManager).isWifiEnabled &&
                    hasEssentialPermissions()
                ) {
                    vm.connect()
                }
                // Came back with Location services now enabled → retry the pairing automatically.
                if (uiState.value.needsLocationOn &&
                    com.example.navisarthi.util.DeviceReadiness.locationServicesEnabled(context) &&
                    hasEssentialPermissions()
                ) {
                    vm.connect()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
            .padding(bottom = 24.dp),
    ) {
        ScreenHeader(
            eyebrow = "What the rider sees",
            title = "Dash view",
            hint = "A live preview of the map sent to the dash. Tap Connect to join the bike's WiFi and stream — your phone screen can stay off.",
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (streaming && ui.gpsStatus != com.example.navisarthi.viewmodel.GpsStatus.GOOD) {
                        NaviSarthiChip(
                            if (ui.gpsStatus == com.example.navisarthi.viewmodel.GpsStatus.LOST) "GPS lost" else "GPS weak",
                            if (ui.gpsStatus == com.example.navisarthi.viewmodel.GpsStatus.LOST) ChipTone.Alert else ChipTone.Warn,
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
            NaviSarthiCard(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
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
                        color = TextLo, fontSize = 11.sp, fontFamily = GeistMonoFamily,
                    )
                }
                ui.lastButton?.let { btn ->
                    Text(
                        "Joystick: $btn",
                        color = Gold, fontSize = 11.sp, fontFamily = GeistMonoFamily,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }


        // Circular viewport with streaming aura
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        ) {
            if (streaming) {
                Box(
                    Modifier
                        .size(300.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Brush.radialGradient(listOf(GoldGlow, Color.Transparent), radius = 200f))
                )
            }
            // Round Tripper viewport. While streaming we show the EXACT frame being encoded to
            // the dash (same renderer → pixel-identical to the bike). Before connecting there's no
            // dash frame yet, so we fall back to the live MapLibre map for route preview.
            Box(
                modifier = Modifier
                    .size(272.dp)
                    .clip(CircleShape)
                    .border(6.dp, Color(0xFF0D0F10), CircleShape)
                    .border(2.dp, Line2, CircleShape),
            ) {
                val dashFrame = previewFrame
                when {
                    streaming && dashFrame != null -> androidx.compose.foundation.Image(
                        bitmap = dashFrame.asImageBitmap(),
                        contentDescription = "Live dash frame",
                        // The dash is round and shows the centred circle of the 526×300 frame;
                        // Crop into the circular viewport reproduces exactly that.
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Connecting OR streaming-before-first-frame: show a lightweight placeholder, NOT the
                    // MapLibre map. MapLibre's native MapView is disposed as soon as connecting starts
                    // (see [connectingOrLive]) so its GL/TextureView teardown finishes well before the
                    // encoder's MediaCodec Surface is created — disposing it concurrently raced the GL
                    // renderer thread and crashed the process (SIGSEGV/SIGABRT in libmaplibre/libgui).
                    connectingOrLive -> Box(
                        Modifier.fillMaxSize().background(Color(0xFF0B0D0E)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Preparing dash view…",
                            color = TextLo, fontSize = 12.sp, fontFamily = GeistMonoFamily,
                        )
                    }
                    else -> NaviSarthiMap(
                        riderLat = ui.riderLat,
                        riderLng = ui.riderLng,
                        dest = ui.destLatLng,
                        routePoints = ui.routePoints,
                        hasLocationPermission = hasEssentialPermissions(),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Next-turn banner (real turn-by-turn from the routing engine)
        ui.maneuver?.let { mv ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (ui.offRoute) Color(0x33D8853E) else GoldTint)
                    .border(1.dp, if (ui.offRoute) Warn else GoldTint2, RoundedCornerShape(13.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
            ) {
                Icon(NaviSarthiIcons.Navi, null, tint = if (ui.offRoute) Warn else Gold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(11.dp))
                Text(
                    if (ui.offRoute) "Off route — rerouting…" else mv,
                    color = TextHi, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
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
                            Text(u, color = TextLo, fontSize = 10.5.sp, fontFamily = GeistMonoFamily, modifier = Modifier.padding(bottom = 2.dp))
                        }
                    }
                    Eyebrow(k, Modifier.padding(top = 3.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        ControlHint("Live ETA uses your average speed since navigation started, including stopped time. It appears after movement is measured. GPS gaps can reduce accuracy.")

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
                    Text("Mirrors the bike joystick", color = TextLo, fontSize = 11.5.sp, modifier = Modifier.padding(top = 1.dp))
                }
            }
            NaviSarthiToggle(on = adjustMode, onChange = { adjustMode = it })
        }

        Spacer(Modifier.height(10.dp))

        // Heading-up toggle (rotate map to travel direction)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Surf1)
                .border(1.dp, Line, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(NaviSarthiIcons.Navi, null, tint = if (ui.headingUp) Gold else TextMid, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(11.dp))
                Column {
                    Text("Heading-up map", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = GeistFamily)
                    Text(if (ui.headingUp) "Rotates to travel direction" else "North-up", color = TextLo, fontSize = 11.5.sp, modifier = Modifier.padding(top = 1.dp))
                }
            }
            NaviSarthiToggle(on = ui.headingUp, onChange = { vm.toggleHeadingUp() })
        }
        ControlHint("Heading-up follows your direction of travel. Switch it off for north-up in TDV.")
        if (ui.stopCount > 1) {
            Text("Stop ${ui.stopNumber} of ${ui.stopCount}: ${ui.destinationName.orEmpty()}",
                color = TextHi, modifier = Modifier.padding(top = 10.dp))
            if (ui.stopNumber < ui.stopCount) {
                NaviSarthiBtn("Skip this stop", onClick = vm::skipStop,
                    variant = BtnVariant.Secondary, modifier = Modifier.fillMaxWidth())
                ControlHint("Continue to the next stop in your itinerary.")
            }
        }
        ControlSection("Map angle")
        NaviSarthiSegmented(
            options = listOf("TDV", "POV"),
            selected = if (ui.perspective) "POV" else "TDV",
            onSelect = { vm.setPerspective(it == "POV") },
            modifier = Modifier.fillMaxWidth(),
        )
        ControlHint("TDV shows the map from above. POV tilts it to show more of the road ahead.")
        var showTrafficSetup by remember { mutableStateOf(false) }
        var trafficKey by remember { mutableStateOf("") }
        ControlSection("Live traffic")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Show traffic along the active route", Modifier.weight(1f), color = TextMid)
            NaviSarthiToggle(on = ui.trafficEnabled && ui.trafficAvailable, enabled = ui.trafficAvailable, onChange = { enabled ->
                if (enabled && !ui.trafficConfigured) showTrafficSetup = true
                else vm.setTrafficEnabled(enabled)
            })
        }
        Text(
            if (ui.trafficAvailable) "Refreshes every 3 minutes while online."
            else "Live traffic is unavailable in Black with Golden Line.",
            color = TextLo, fontSize = 12.sp,
        )
        Text("Green: near free flow · Yellow: slower · Orange: heavy · Red: severe",
            color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Text("Red and light grey: road closed.", color = TextLo, fontSize = 12.sp)
        Text("Create a TomTom account, copy a key with Traffic API access, then paste it here and tap Enable.",
            color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        androidx.compose.material3.TextButton(onClick = {
            val guide = android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://docs.tomtom.com/platform/documentation/my-tomtom/how-to-get-a-tomtom-api-key"))
            try {
                context.startActivity(guide)
            } catch (_: android.content.ActivityNotFoundException) {
                android.widget.Toast.makeText(context, "No browser available", android.widget.Toast.LENGTH_SHORT).show()
            }
        }) { Text("Get a TomTom Traffic API key") }
        if (ui.trafficConfigured && ui.trafficAvailable) {
            androidx.compose.material3.TextButton(onClick = { showTrafficSetup = true }) {
                Text("Traffic provider settings")
            }
        }
        if (showTrafficSetup && ui.trafficAvailable) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showTrafficSetup = false; trafficKey = "" },
                title = { Text("Connect TomTom traffic") },
                text = {
                    Column {
                        Text("Enter a TomTom API key with Traffic API access. Live traffic needs internet.")
                        androidx.compose.material3.OutlinedTextField(
                            value = trafficKey,
                            onValueChange = { trafficKey = it.take(200) },
                            label = { Text("TomTom API key") },
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        )
                        if (ui.trafficConfigured) {
                            androidx.compose.material3.TextButton(onClick = {
                                vm.configureTraffic("")
                                trafficKey = ""
                                showTrafficSetup = false
                            }) { Text("Remove key") }
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(enabled = trafficKey.isNotBlank(), onClick = {
                        vm.configureTraffic(trafficKey)
                        vm.setTrafficEnabled(true)
                        trafficKey = ""
                        showTrafficSetup = false
                    }) { Text("Enable") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        trafficKey = ""
                        showTrafficSetup = false
                    }) { Text("Cancel") }
                },
            )
        }
        ControlSection("Map colour theme")
        NaviSarthiSegmented(
            options = listOf("Light", "Dark", "Black"),
            selected = ui.mapStyle.name,
            onSelect = { vm.setMapStyle(com.example.navisarthi.dash.map.MapRenderer.Style.valueOf(it)) },
            modifier = Modifier.fillMaxWidth(),
        )
        ControlHint("Light is for daylight, Dark reduces glare, and Black shows a label-free road map.")
        if (ui.mapStyle == com.example.navisarthi.dash.map.MapRenderer.Style.Black) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Adapt to Dash Golden Line colour", Modifier.weight(1f), color = TextMid)
                NaviSarthiToggle(on = ui.goldenRoute, onChange = vm::setGoldenRoute)
            }
            ControlHint("Use gold for the route, rider marker, and arrival message. Live traffic is paused while gold is on.")
        }

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
                NaviSarthiIconBtn(NaviSarthiIcons.Plus,  onClick = { vm.zoomIn() },  size = 52.dp)
                Text("z${ui.mapZoom}", color = Gold, fontSize = 12.sp, fontFamily = GeistMonoFamily, fontWeight = FontWeight.SemiBold)
                NaviSarthiIconBtn(NaviSarthiIcons.Minus, onClick = { vm.zoomOut() }, size = 52.dp)
                NaviSarthiIconBtn(NaviSarthiIcons.Recenter, onClick = { vm.recenter(); pan = Offset.Zero }, size = 52.dp, active = true)
            }
        }

        // Exit navigation → free roam (keeps streaming, just drops the route)
        if (streaming && ui.destinationName != null) {
            Spacer(Modifier.height(16.dp))
            NaviSarthiBtn(
                "Exit navigation",
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
