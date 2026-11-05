package com.example.navisarthi.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.UnfoldMore
import kotlin.math.roundToInt
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.navisarthi.util.DeviceReadiness
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.navisarthi.ui.NaviSarthiIcons
import com.example.navisarthi.ui.components.*
import com.example.navisarthi.ui.theme.*
import com.example.navisarthi.viewmodel.RouteViewModel

@Composable
fun RouteScreen(
    onBack: () -> Unit,
    onSentToDash: (String) -> Unit,
    routeViewModel: RouteViewModel = viewModel(),
) {
    val routeState by routeViewModel.state.collectAsState()
    val dest       = routeState.destination
    val destName   = dest?.name?.ifBlank { "Shared location" } ?: "Shared location"
    val destSub    = when {
        dest?.lat != null && dest.lng != null ->
            "%.5f, %.5f".format(dest.lat, dest.lng)
        dest?.url != null -> "Maps link"
        else              -> ""
    }

    var deleting by remember { mutableStateOf<com.example.navisarthi.data.SavedLocation?>(null) }
    val downloaded by routeViewModel.downloaded.collectAsState()
    val downloadStatus by routeViewModel.downloadStatus.collectAsState()
    val downloading by routeViewModel.downloadInProgress.collectAsState()
    val itineraries by routeViewModel.itineraries.collectAsState()
    var showSaveItinerary by remember { mutableStateOf(false) }
    var itineraryName by remember { mutableStateOf("") }
    var deleteItinerary by remember { mutableStateOf<RouteViewModel.SavedItinerary?>(null) }
    val savedList by routeViewModel.saved.collectAsState()
    val ctx = LocalContext.current
    val voiceManager = remember { com.example.navisarthi.dash.nav.VoiceManager.get(ctx) }
    val voiceMode by voiceManager.mode.collectAsState()
    val voice = when (voiceMode) {
        com.example.navisarthi.dash.nav.VoiceMode.OFF   -> "Off"
        com.example.navisarthi.dash.nav.VoiceMode.CHIME -> "Chime only"
        com.example.navisarthi.dash.nav.VoiceMode.FULL  -> "Full TTS"
    }
    var sent by remember { mutableStateOf(false) }
    var showBatteryGuidance by rememberSaveable { mutableStateOf(false) }
    var batteryExempt by remember { mutableStateOf(DeviceReadiness.isIgnoringBatteryOptimizations(ctx)) }
    var backgroundRestricted by remember { mutableStateOf(DeviceReadiness.isBackgroundRestricted(ctx)) }
    var batterySettingsError by remember { mutableStateOf<String?>(null) }
    var showSave by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<com.example.navisarthi.data.SavedLocation?>(null) }

    // Nav needs GPS (+ NEARBY_WIFI_DEVICES on 13+ to reach the dash). The Dash screen
    // requests these, but a rider can go straight Share → Route → Start navigation
    // without ever opening Dash — so request them here too. Without this the ride
    // starts with no GPS (and previously crashed the foreground service on 14+).
    val navPermissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }.toTypedArray()
    }
    fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    // Whether or not the grant succeeds, proceed (the service start is now defensive and
    // the rider can still preview); denying just means no live GPS until they grant it.
    val navPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { sent = true }

    fun startAfterBatteryGuidance() {
        showBatteryGuidance = false
        if (hasLocationPermission()) sent = true else navPermLauncher.launch(navPermissions)
    }
    val navigationBatterySettings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        batteryExempt = DeviceReadiness.isIgnoringBatteryOptimizations(ctx)
        backgroundRestricted = DeviceReadiness.isBackgroundRestricted(ctx)
        // A cancelled pending start must never be resumed by a late settings result.
        if (showBatteryGuidance && batteryExempt && !backgroundRestricted) startAfterBatteryGuidance()
    }
    fun openNavigationBatterySettings(requestExemption: Boolean) {
        batterySettingsError = null
        val intent = if (requestExemption) DeviceReadiness.batteryExemptionIntent(ctx)
            else DeviceReadiness.appSettingsIntent(ctx)
        val launched = runCatching { navigationBatterySettings.launch(intent) }.isSuccess
        if (!launched) {
            val fallback = runCatching {
                navigationBatterySettings.launch(DeviceReadiness.appSettingsIntent(ctx))
            }.isSuccess
            if (!fallback) batterySettingsError = "Open Android Settings → Apps → NaviSarthi → Battery and allow background use, then return here."
        }
    }

    LaunchedEffect(sent) {
        if (sent) {
            kotlinx.coroutines.delay(650)
            onSentToDash(destName)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Map preview (top 46%)
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.46f)
                .background(MapBase),
        ) {
            // Real Google Maps preview — destination pin + route line.
            NaviSarthiMap(
                riderLat = null,
                riderLng = null,
                dest = dest?.let { d -> if (d.lat != null && d.lng != null) d.lat to d.lng else null },
                routePoints = routeState.legs.flatMap { it.geometry },
                hasLocationPermission = true,
                fitRoute = true,
                modifier = Modifier.fillMaxSize(),
            )

            // Top bar overlay
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color(0xD9080C0C), Color.Transparent))
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                NaviSarthiIconBtn(
                    NaviSarthiIcons.ChevronLeft,
                    onClick = onBack,
                    size = 40.dp,
                    modifier = Modifier.background(Color(0xB30D0F11), IconBtnShape),
                )
                Spacer(Modifier.width(12.dp))
                NaviSarthiChip(
                    if (routeState.isResolving) "Resolving link…" else "Shared from Google Maps",
                    tone = if (routeState.isResolving) ChipTone.Gold else ChipTone.Neutral,
                    icon = NaviSarthiIcons.Share,
                    modifier = Modifier.background(Color(0xB30D0F11), CircleShape),
                )
            }
        }

        // Detail sheet (overlapping bottom portion)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = (-22).dp)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(Bg1)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp)
                .padding(bottom = 20.dp),
        ) {
            // Drag handle
            Box(
                Modifier
                    .width(40.dp).height(4.dp)
                    .clip(CircleShape).background(Line3)
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 16.dp)
            )

            Spacer(Modifier.height(12.dp))

            // No destination yet → tell the rider exactly how to start one. A route ALWAYS begins by
            // sharing a place from Google Maps into NaviSarthi; there's no in-app search by design.
            if ((dest?.lat == null || dest.lng == null) && !routeState.isResolving) {
                NaviSarthiCard(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), padding = 16.dp) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(NaviSarthiIcons.Share, contentDescription = null, tint = Gold, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Start a route from Google Maps", color = TextHi, fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold, fontFamily = GeistFamily)
                            Text(
                                "Open Google Maps → pick a place → tap Share → choose NaviSarthi. " +
                                    "The destination lands here, then tap Start navigation to send it to the dash.",
                                color = TextMid, fontSize = 13.sp, fontFamily = GeistFamily,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }

            Eyebrow("Destination", Modifier.padding(bottom = 6.dp))

            Row(verticalAlignment = Alignment.Top) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GoldTint),
                ) {
                    Icon(NaviSarthiIcons.LocationPin, contentDescription = null, tint = Gold, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    if (routeState.isResolving) {
                        Text("Resolving…", color = TextLo, fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = GeistFamily, letterSpacing = (-0.38).sp)
                    } else {
                        Text(destName, color = TextHi, fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = GeistFamily, letterSpacing = (-0.38).sp)
                    }
                    if (destSub.isNotBlank()) {
                        Text(destSub, color = TextMid, fontSize = 13.sp, fontFamily = GeistMonoFamily, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Route stats (real values once routing completes)
            val routing = routeState.routing
            val statsList = listOf(
                Triple(if (routing) "…" else (routeState.distanceText ?: "—"), "", "Distance"),
                Triple(if (routing) "…" else (routeState.durationText ?: "—"), "", "Duration"),
                Triple(if (routing) "…" else (routeState.etaText ?: "—"), "", "Arrive"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                statsList.forEach { (v, u, k) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Surf1)
                            .border(1.dp, Line, RoundedCornerShape(14.dp))
                            .padding(13.dp),
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(v, color = TextHi, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = GeistMonoFamily)
                            Spacer(Modifier.width(4.dp))
                            Text(u, color = TextLo, fontSize = 11.sp, fontFamily = GeistMonoFamily, modifier = Modifier.padding(bottom = 2.dp))
                        }
                        Eyebrow(k, Modifier.padding(top = 4.dp))
                    }
                }
            }

            ControlHint(
                "Pre-journey estimate: ${com.example.navisarthi.dash.nav.PlanningEstimate.averageKmh(routeState.mode)} km/h average (${routeState.mode.name}). " +
                    "Covers all stops; excludes stopover time and traffic delays. During navigation, live ETA uses your average speed since starting, including stopped time."
            )
            Spacer(Modifier.height(20.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp),
            ) {
                Eyebrow("Voice guidance")
                Icon(
                    if (voice == "Off") NaviSarthiIcons.SpeakerOff else NaviSarthiIcons.Speaker,
                    contentDescription = null,
                    tint = if (voice == "Off") TextLo else Gold,
                    modifier = Modifier.size(18.dp),
                )
            }

            NaviSarthiSegmented(
                options = listOf("Off", "Chime only", "Full TTS"),
                selected = voice,
                onSelect = {
                    voiceManager.setMode(when (it) {
                        "Off"  -> com.example.navisarthi.dash.nav.VoiceMode.OFF
                        "Full TTS" -> com.example.navisarthi.dash.nav.VoiceMode.FULL
                        else   -> com.example.navisarthi.dash.nav.VoiceMode.CHIME
                    })
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp),
            )

            ControlHint("Use a chime for a short cue or Full TTS for spoken directions.")
            ControlSection("Route type")
            NaviSarthiSegmented(
                options = listOf("Car", "Bike"),
                selected = routeState.mode.name,
                onSelect = { routeViewModel.setRouteMode(com.example.navisarthi.dash.nav.RouteMode.valueOf(it)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            )
            if (routeState.routingError) {
                Text("Could not preview every leg. Check your connection and retry.", color = TextLo)
                TextButton(onClick = routeViewModel::computeRoute) { Text("Retry preview") }
            }

            ControlHint("Car and Bike use different routing profiles. Bike is for motorcycles.")
            if (routeState.stops.isNotEmpty()) {
                ControlSection("Journey stops")
                val rowHeight = 64.dp
                val rowPixels = with(LocalDensity.current) { rowHeight.toPx() }
                var draggedIndex by remember { mutableStateOf<Int?>(null) }
                var dragOffset by remember { mutableStateOf(0f) }
                val stopCount = routeState.stops.size
                routeState.stops.forEachIndexed { index, stop ->
                    Row(
                        Modifier.fillMaxWidth().height(rowHeight)
                            .zIndex(if (draggedIndex == index) 1f else 0f)
                            .graphicsLayer { translationY = if (draggedIndex == index) dragOffset else 0f }
                            .background(if (draggedIndex == index) Surf2 else Color.Transparent),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${index + 1}. ${stop.name}", Modifier.weight(1f), color = TextHi, maxLines = 2)
                        TextButton(enabled = !routeState.needsStopChoice && draggedIndex == null,
                            onClick = { routeViewModel.removeStop(index) }) { Text("Remove") }
                        Box(
                            Modifier.size(48.dp).pointerInput(index, stopCount, routeState.needsStopChoice) {
                                if (!routeState.needsStopChoice) detectDragGesturesAfterLongPress(
                                    onDragStart = { draggedIndex = index; dragOffset = 0f },
                                    onDragCancel = { draggedIndex = null; dragOffset = 0f },
                                    onDragEnd = {
                                        val target = (index + (dragOffset / rowPixels).roundToInt()).coerceIn(0, stopCount - 1)
                                        draggedIndex = null
                                        dragOffset = 0f
                                        if (target != index) routeViewModel.moveStop(index, target - index)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset = (dragOffset + amount.y).coerceIn(
                                            -index * rowPixels, (stopCount - index - 1) * rowPixels)
                                    },
                                )
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.UnfoldMore, contentDescription = "Hold and drag to reorder stop", tint = TextMid)
                        }
                    }
                }
                ControlHint("Hold the chevron and drag a stop to its new position. Release to apply the order.")

            }

            if (routeState.needsStopChoice && dest?.lat != null && dest.lng != null) {
                Text("Add ${dest.name} to this journey?", color = TextHi)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NaviSarthiBtn("Append", enabled = routeState.stops.size < 8, onClick = {
                        routeViewModel.chooseStop(RouteViewModel.StopChoice.Append)
                    }, variant = BtnVariant.Secondary, modifier = Modifier.weight(1f))
                    NaviSarthiBtn("Replace", onClick = {
                        routeViewModel.chooseStop(RouteViewModel.StopChoice.Replace)
                    }, variant = BtnVariant.Secondary, modifier = Modifier.weight(1f))
                    NaviSarthiBtn("Cancel", onClick = routeViewModel::dismissStopChoice,
                        variant = BtnVariant.Secondary, modifier = Modifier.weight(1f))
                }
                ControlHint("Append adds this place at the end. Replace starts a new route with this place. Cancel keeps your current stops.")
                if (routeState.stops.size >= 8) ControlHint("Maximum 8 stops reached. Remove a stop to append another destination.")
            }
            if (routeState.needsStopChoice && (dest?.lat == null || dest.lng == null)) {
                NaviSarthiBtn("Cancel", onClick = routeViewModel::dismissStopChoice,
                    variant = BtnVariant.Secondary, modifier = Modifier.fillMaxWidth())
            }

            if (routeState.offline) {
                Text("Offline route · Start near the downloaded route. Rerouting needs an online route.",
                    color = TextLo, fontSize = 12.sp)
            }
            val canStart = routeState.stops.isNotEmpty() && !routeState.needsStopChoice &&
                !routeState.isResolving && !routeState.routing
            if (routeState.stops.size < 8) {
                ControlHint("Pick a place in Google Maps, then Share to NaviSarthi. You can add up to 8 stops.",
                    Modifier.padding(bottom = 8.dp))
                NaviSarthiBtn(
                    label = "Add next stop/destination",
                    onClick = { com.example.navisarthi.util.openGoogleMaps(ctx) },
                    icon = NaviSarthiIcons.Pin,
                    variant = BtnVariant.Secondary,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
            }
            NaviSarthiBtn(
                label = when {
                    sent                   -> "Starting navigation…"
                    routeState.isResolving -> "Resolving destination…"
                    routeState.routing     -> "Finding route…"
                    else                   -> "Start navigation"
                },
                onClick = {
                    batteryExempt = DeviceReadiness.isIgnoringBatteryOptimizations(ctx)
                    backgroundRestricted = DeviceReadiness.isBackgroundRestricted(ctx)
                    if (DeviceReadiness.shouldShowNavigationBatteryGuidance(ctx)) {
                        batterySettingsError = null
                        showBatteryGuidance = true
                    } else if (hasLocationPermission()) sent = true else navPermLauncher.launch(navPermissions)
                },
                icon = if (sent) NaviSarthiIcons.Check else NaviSarthiIcons.Navi,
                variant = if (sent) BtnVariant.Secondary else BtnVariant.Primary,
                size = BtnSize.Lg,
                enabled = !sent && canStart,
                modifier = Modifier.fillMaxWidth(),
            )

            if (canStart) {
                ControlHint("Send the stops to the TFT and guide each leg in order.")
                Spacer(Modifier.height(10.dp))
                NaviSarthiBtn(
                    label = "Save this destination",
                    onClick = { showSave = true },
                    icon = NaviSarthiIcons.Pin,
                    variant = BtnVariant.Ghost,
                    size = BtnSize.Md,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (routeState.stops.isNotEmpty() && !routeState.needsStopChoice) {
                NaviSarthiBtn("Save itinerary", onClick = {
                    itineraryName = ""
                    showSaveItinerary = true
                }, variant = BtnVariant.Secondary, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            }

            if (itineraries.isNotEmpty()) {
                ControlSection("Saved itineraries")
                ControlHint("Open a saved stop list, or download its route while online to follow it without data.", Modifier.padding(bottom = 6.dp))
                if (downloadStatus.isNotBlank()) Text(downloadStatus, color = TextLo)
                itineraries.forEach { item ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = TextHi)
                            Text("${item.stops.size} stops · ${item.mode.name}", color = TextLo, fontSize = 12.sp)
                        }
                        TextButton(onClick = { routeViewModel.loadItinerary(item) }) { Text("Open") }
                        TextButton(enabled = !downloading, onClick = { deleteItinerary = item }) { Text("Delete") }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        if (item.id in downloaded) {
                            TextButton(onClick = { routeViewModel.openOffline(item) }) { Text("Open offline") }
                        }
                        TextButton(enabled = !downloading, onClick = { routeViewModel.downloadItinerary(item) }) {
                            Text(if (item.id in downloaded) "Download again" else "Download offline")
                        }
                    }
                }
            }

            // ── Saved destinations ──
            if (savedList.isNotEmpty()) {
                ControlSection("Saved destinations")
                ControlHint("Tap a saved place to use it in your itinerary.", Modifier.padding(bottom = 6.dp))
                NaviSarthiCard(modifier = Modifier.fillMaxWidth(), padding = 6.dp) {
                    savedList.forEachIndexed { i, loc ->
                        if (i > 0) NaviSarthiDivider(Modifier.padding(horizontal = 4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { routeViewModel.selectSaved(loc) }
                                .padding(horizontal = 6.dp, vertical = 12.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(GoldTint),
                            ) { Icon(NaviSarthiIcons.LocationPin, null, tint = Gold, modifier = Modifier.size(20.dp)) }
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text(loc.name, color = TextHi, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = GeistFamily, maxLines = 1)
                                Text(
                                    if (loc.note.isNotBlank()) loc.note else "%.4f, %.4f".format(loc.lat, loc.lng),
                                    color = TextLo, fontSize = 12.sp, fontFamily = GeistMonoFamily,
                                    modifier = Modifier.padding(top = 2.dp), maxLines = 1,
                                )
                            }
                            Icon(
                                NaviSarthiIcons.Edit, "edit", tint = TextLo,
                                modifier = Modifier.size(18.dp).clickable { editing = loc },
                            )
                            TextButton(onClick = { deleting = loc }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }

    if (showBatteryGuidance) {
        AlertDialog(
            onDismissRequest = { showBatteryGuidance = false },
            title = { Text("Keep navigation running") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Battery saving can stop NaviSarthi while your phone is locked or another app is open. Maps, console guidance and voice directions may stop abruptly.")
                    Text("Before riding, set NaviSarthi's battery usage to Unrestricted or No restrictions. Allow background activity or Auto-start if available, and remove NaviSarthi from Sleeping apps.")
                    if (batteryExempt) Text("Battery optimization is disabled, but Android still reports restricted background usage. Allow background activity in app settings.")
                    if (backgroundRestricted) Text("Android still reports background usage as restricted. Open app settings and allow background use.")
                    batterySettingsError?.let { Text(it) }
                    if (!batteryExempt) TextButton(onClick = { openNavigationBatterySettings(false) }) { Text("Open app settings") }
                }
            },
            confirmButton = {
                TextButton(onClick = { openNavigationBatterySettings(!batteryExempt) }) {
                    Text(if (batteryExempt) "Review background settings" else "Allow background use")
                }
            },
            dismissButton = {
                TextButton(onClick = { startAfterBatteryGuidance() }) { Text("Start anyway") }
            },
        )
    }

    if (showSaveItinerary) {
        AlertDialog(
            onDismissRequest = { showSaveItinerary = false },
            title = { Text("Save itinerary") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = itineraryName,
                    onValueChange = { itineraryName = it.take(80) },
                    label = { Text("Itinerary name") },
                    singleLine = true,
                )
            },
            confirmButton = { TextButton(enabled = itineraryName.isNotBlank(), onClick = {
                routeViewModel.saveItinerary(itineraryName)
                showSaveItinerary = false
            }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showSaveItinerary = false }) { Text("Cancel") } },
        )
    }
    deleteItinerary?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteItinerary = null },
            title = { Text("Delete saved itinerary?") },
            text = { Text(item.name) },
            confirmButton = { TextButton(onClick = {
                routeViewModel.deleteItinerary(item.id)
                deleteItinerary = null
            }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteItinerary = null }) { Text("Cancel") } },
        )
    }

    if (showSave) SaveLocationDialog(
        defaultName = destName,
        onSave = { name, note -> routeViewModel.saveCurrentDestination(name, note); showSave = false },
        onDismiss = { showSave = false },
    )
    deleting?.let { loc ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete saved destination?") },
            text = { Text(loc.name) },
            confirmButton = { TextButton(onClick = { routeViewModel.deleteSaved(loc); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
    editing?.let { loc ->
        EditLocationDialog(
            loc = loc,
            onSave = { name, note -> routeViewModel.renameSaved(loc, name, note); editing = null },
            onDelete = { routeViewModel.deleteSaved(loc); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun SaveLocationDialog(defaultName: String, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(defaultName) }
    var note by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { androidx.compose.material3.TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), note.trim()) }) { Text("Save", color = if (name.isNotBlank()) Gold else TextLo) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel", color = TextMid) } },
        title = { Text("Save destination", color = TextHi) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                androidx.compose.material3.OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        containerColor = Surf1,
    )
}

@Composable
private fun EditLocationDialog(loc: com.example.navisarthi.data.SavedLocation, onSave: (String, String) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(loc.name) }
    var note by remember { mutableStateOf(loc.note) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { androidx.compose.material3.TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), note.trim()) }) { Text("Save", color = if (name.isNotBlank()) Gold else TextLo) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDelete) { Text("Delete", color = Alert) } },
        title = { Text("Edit destination", color = TextHi) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                androidx.compose.material3.OutlinedTextField(note, { note = it }, label = { Text("Note") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        containerColor = Surf1,
    )
}

