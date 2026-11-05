package com.example.navisarthi.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.navisarthi.ui.NaviSarthiIcons
import com.example.navisarthi.ui.components.*
import com.example.navisarthi.ui.theme.*
import com.example.navisarthi.viewmodel.ConnectionState
import com.example.navisarthi.viewmodel.DashViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    conn: ConnectionState,
    onConnChange: (ConnectionState) -> Unit,
    dashViewModel: DashViewModel,
    onVehicleSelected: (VehicleModel) -> Unit,
    garageEnabled: Boolean,
    onGarageEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val dashUi by dashViewModel.ui.collectAsState()
    var showSwitchDialog by remember { mutableStateOf(false) }
    var autoConnect by remember { mutableStateOf(true) }
    var screenOff   by remember { mutableStateOf(true) }
    var keepAwake   by remember { mutableStateOf(true) }
    var units       by remember { mutableStateOf("Kilometres") }

    // Real voice setting, shared with RouteScreen via the VoiceManager singleton.
    val ctx = LocalContext.current
    val voiceManager = remember { com.example.navisarthi.dash.nav.VoiceManager.get(ctx) }
    val voiceMode by voiceManager.mode.collectAsState()
    val voice = when (voiceMode) {
        com.example.navisarthi.dash.nav.VoiceMode.OFF   -> "Off"
        com.example.navisarthi.dash.nav.VoiceMode.CHIME -> "Chime"
        com.example.navisarthi.dash.nav.VoiceMode.FULL  -> "Full TTS"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
            .padding(bottom = 24.dp),
    ) {
        ScreenHeader(title = "Settings", onBack = onBack,
            hint = "Connection, screen-off streaming, voice guidance, units, and media/call permissions.")

        val vehicle = LocalVehicle.current
        var showVehiclePicker by remember { mutableStateOf(false) }
        ControlSection("Vehicle")
        NaviSarthiBtn(vehicle.label, onClick = { showVehiclePicker = true },
            icon = NaviSarthiIcons.Motor, variant = BtnVariant.Secondary, modifier = Modifier.fillMaxWidth())
        ControlHint("Tap to select another vehicle and change the app accent.")
        if (showVehiclePicker) {
            VehiclePicker(current = vehicle, onSelect = {
                onVehicleSelected(it)
                showVehiclePicker = false
            }, onDismiss = { showVehiclePicker = false })
        }

        ControlSection("App features")
        NaviSarthiCard(modifier = Modifier.fillMaxWidth(), padding = 6.dp) {
            SettingRow(NaviSarthiIcons.Wrench, "Enable Garage", "Maintenance, fuel diary and vehicle records",
                control = { NaviSarthiToggle(garageEnabled) { onGarageEnabledChange(it) } }, last = true)
        }
        ControlHint("Enable to show Garage in the bottom bar. Turn it off to hide Garage; your saved garage records are kept.")

        ControlSection("Connection")
        NaviSarthiCard(modifier = Modifier.fillMaxWidth(), padding = 6.dp) {
            SettingRow(NaviSarthiIcons.Bt, "Tripper Dash",
                sub = when (conn) { ConnectionState.Connected -> "Connected"; ConnectionState.Searching -> "Connecting…"; ConnectionState.Offline -> "Not connected" },
                control = { NaviSarthiChip(if (conn == ConnectionState.Connected) "Linked" else "Off", if (conn == ConnectionState.Connected) ChipTone.Gold else ChipTone.Off, dot = true) })
            NaviSarthiDivider(Modifier.padding(horizontal = 6.dp))
            // Switch to a different bike's dash. The app pins to the first dash it pairs with; this
            // forgets it so the next connect re-discovers and pairs with whichever RE dash is nearby.
            SettingRow(NaviSarthiIcons.Motor, "Switch bike",
                sub = if (dashUi.ssid.isNotBlank()) "Paired: ${dashUi.ssid} · tap to pair a different dash"
                      else "Not paired — pairs with any nearby Royal Enfield dash",
                control = { Icon(NaviSarthiIcons.ChevronRight, null, tint = TextLo, modifier = Modifier.size(18.dp)) },
                onClick = if (dashUi.ssid.isNotBlank()) ({ showSwitchDialog = true }) else null)
            NaviSarthiDivider(Modifier.padding(horizontal = 6.dp))
            SettingRow(NaviSarthiIcons.Sync, "Auto-connect on start", "Link when the bike is near",
                control = { NaviSarthiToggle(autoConnect) { autoConnect = it } })
            NaviSarthiDivider(Modifier.padding(horizontal = 6.dp))
            SettingRow(NaviSarthiIcons.Zap, "Stream quality", "Balanced · saves battery",
                control = { Icon(NaviSarthiIcons.ChevronRight, null, tint = TextLo, modifier = Modifier.size(18.dp)) }, last = true)
        }

        if (showSwitchDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showSwitchDialog = false },
                title = { Text("Switch to another bike?") },
                text = {
                    Text(
                        "This forgets the paired dash" +
                            (if (dashUi.ssid.isNotBlank()) " (${dashUi.ssid})" else "") +
                            ". The next connection pairs with whichever Royal Enfield dash is nearby — " +
                            "do it next to the other bike, with its dash on.",
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        dashViewModel.disconnect()
                        dashViewModel.forgetDash()
                        showSwitchDialog = false
                    }) { Text("Forget & switch", color = Gold) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showSwitchDialog = false }) { Text("Cancel") }
                },
            )
        }

        ControlSection("During a ride")
        NaviSarthiCard(modifier = Modifier.fillMaxWidth(), padding = 6.dp) {
            SettingRow(NaviSarthiIcons.Power, "Turn phone screen off", "Map keeps streaming to the dash",
                control = { NaviSarthiToggle(screenOff) { screenOff = it } })
            NaviSarthiDivider(Modifier.padding(horizontal = 6.dp))
            SettingRow(NaviSarthiIcons.Dash, "Keep dash awake", "Prevent Tripper sleep",
                control = { NaviSarthiToggle(keepAwake) { keepAwake = it } }, last = true)
        }

        ControlSection("Music on console")
        val musicEnabled by dashViewModel.musicEnabled.collectAsState()
        val playing by dashViewModel.nowPlaying.collectAsState()
        val musicStatus by dashViewModel.musicStatus.collectAsState()
        LaunchedEffect(Unit) { dashViewModel.refreshMusic() }
        NaviSarthiCard(modifier = Modifier.fillMaxWidth(), padding = 6.dp) {
            SettingRow(NaviSarthiIcons.Zap, "Enable music controls", musicStatus,
                control = { NaviSarthiToggle(musicEnabled) { dashViewModel.setMusicEnabled(it) } }, last = true)
        }
        playing?.let { track ->
            ControlHint(listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" · "))
            val actions = track.actions
            val playAction = if (track.isPlaying) android.media.session.PlaybackState.ACTION_PAUSE
                else android.media.session.PlaybackState.ACTION_PLAY
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NaviSarthiBtn("Previous", onClick = { dashViewModel.musicPrevious() },
                    enabled = actions and android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
                    modifier = Modifier.weight(1f))
                NaviSarthiBtn(if (track.isPlaying) "Pause" else "Play", onClick = { dashViewModel.musicPlayPause() },
                    enabled = actions and (playAction or android.media.session.PlaybackState.ACTION_PLAY_PAUSE) != 0L,
                    modifier = Modifier.weight(1f))
                NaviSarthiBtn("Next", onClick = { dashViewModel.musicNext() },
                    enabled = actions and android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
                    modifier = Modifier.weight(1f))
            }
        }
        ControlHint("Start Spotify or YouTube Music on your phone. Console media-mode left/right requests previous/next. Console play/pause mapping awaits bike verification. Audio stays on your selected phone/headset output.")

        ControlSection("Media & calls on dash")
        // Re-check the grant on ON_RESUME so the chip flips to "On" the moment the user comes back
        // from the system notification-access screen (the Settings value isn't observable on its own).
        val lifecycleOwner = LocalLifecycleOwner.current
        fun answerGranted() = androidx.core.content.ContextCompat.checkSelfPermission(
            ctx, android.Manifest.permission.ANSWER_PHONE_CALLS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        var mediaGranted by remember { mutableStateOf(com.example.navisarthi.media.MediaInfoProvider.isAccessGranted(ctx)) }
        var callAnswerGranted by remember { mutableStateOf(answerGranted()) }
        DisposableEffect(lifecycleOwner) {
            val obs = LifecycleEventObserver { _, e ->
                if (e == Lifecycle.Event.ON_RESUME) {
                    mediaGranted = com.example.navisarthi.media.MediaInfoProvider.isAccessGranted(ctx)
                    callAnswerGranted = answerGranted()
                    dashViewModel.refreshMusic()
                }
            }
            lifecycleOwner.lifecycle.addObserver(obs)
            onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
        }
        val callPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            callAnswerGranted = it
        }
        NaviSarthiCard(modifier = Modifier.fillMaxWidth(), padding = 6.dp) {
            SettingRow(
                NaviSarthiIcons.Zap,
                "Now playing & calls on dash",
                if (mediaGranted) "Access granted · music uses the toggle above"
                else "Tap to allow notification access",
                control = {
                    NaviSarthiChip(
                        if (mediaGranted) "On" else "Enable",
                        if (mediaGranted) ChipTone.Gold else ChipTone.Off, dot = true,
                    )
                },
                onClick = if (mediaGranted) null else {
                    { runCatching { ctx.startActivity(com.example.navisarthi.media.MediaInfoProvider.accessSettingsIntent()) } }
                },
            )
            NaviSarthiDivider(Modifier.padding(horizontal = 6.dp))
            SettingRow(
                NaviSarthiIcons.Bt,
                "Answer calls from joystick",
                if (callAnswerGranted) "Enabled · UP answers, DOWN rejects"
                else "Tap to allow answering/rejecting calls",
                control = {
                    NaviSarthiChip(
                        if (callAnswerGranted) "On" else "Enable",
                        if (callAnswerGranted) ChipTone.Gold else ChipTone.Off, dot = true,
                    )
                },
                last = true,
                onClick = if (callAnswerGranted) null else {
                    { callPermLauncher.launch(android.Manifest.permission.ANSWER_PHONE_CALLS) }
                },
            )
        }

        ControlSection("Voice & guidance")
        NaviSarthiCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp) {
            NaviSarthiSegmented(listOf("Off", "Chime", "Full TTS"), voice, {
                voiceManager.setMode(when (it) {
                    "Off"      -> com.example.navisarthi.dash.nav.VoiceMode.OFF
                    "Full TTS" -> com.example.navisarthi.dash.nav.VoiceMode.FULL
                    else       -> com.example.navisarthi.dash.nav.VoiceMode.CHIME
                })
            }, Modifier.fillMaxWidth())
        }

        ControlHint("Pick a chime or spoken directions. Pair your helmet headset in Android settings.")

        ControlSection("Units")
        NaviSarthiCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp) {
            NaviSarthiSegmented(listOf("Kilometres", "Miles"), units, { units = it }, Modifier.fillMaxWidth())
        }

        DriveBackupSettings()

        TestBuildCard()

        val appVersion = remember { com.example.navisarthi.data.UpdateChecker.currentVersionName(ctx) }
        Text(
            "NAVISARTHI v$appVersion",
            color = TextDis, fontSize = 11.sp, fontFamily = GeistMonoFamily,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp),
        )
        Text(
            "Independent community project — not affiliated with, endorsed by, or supported by " +
                "Royal Enfield. \"Royal Enfield\", \"Himalayan\" and \"Tripper\" are trademarks of " +
                "their respective owners, used here only to describe compatible hardware. The dash " +
                "link is unofficial and exists solely for interoperability with hardware you own — use at your own risk.",
            color = TextDis, fontSize = 10.sp, fontFamily = GeistFamily,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, start = 8.dp, end = 8.dp),
        )
    }
}

/**
 * Test-build channel: installs a freshly pushed APK WITHOUT a version bump. Compares the running
 * APK's CHECKSUM against the published one (Firestore `meta/test_build`) and, if they differ,
 * offers a one-tap download+install (same signing key → installs in place, keeping data). Checksum
 * means it's correct however the build was installed, and the card clears itself once the matching
 * APK is running. Invisible when up to date / no build published / Firebase off.
 */
@Composable
private fun TestBuildCard() {
    // Debug-channel only: the test-build installer must never appear in a public release build.
    if (!com.example.navisarthi.BuildConfig.DEBUG) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var build by remember { mutableStateOf<com.example.navisarthi.data.TestBuildChecker.TestBuild?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(true) }

    suspend fun refresh() {
        checking = true
        build = com.example.navisarthi.data.TestBuildChecker.fetchLatest(ctx)
        checking = false
    }
    LaunchedEffect(Unit) { refresh() }

    val b = build
    // Nothing published, or the running APK already matches the published checksum → no card.
    if (b == null || !com.example.navisarthi.data.TestBuildChecker.needsInstall(ctx, b)) return

    fun installNow() {
        busy = true; status = "Downloading…"
        scope.launch {
            val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.example.navisarthi.data.UpdateChecker.download(ctx, b.url) { p ->
                    status = "Downloading… ${(p * 100).toInt()}%"
                }
            }
            if (file == null) { status = "Download failed — try again"; busy = false; return@launch }
            status = "Opening installer…"
            // No bookkeeping needed: once the new APK is running its checksum matches the published
            // one, so needsInstall() returns false and this card disappears on its own.
            val started = com.example.navisarthi.data.UpdateChecker.install(ctx, file)
            if (!started) status = "Allow “install unknown apps”, then tap again"
            busy = false
        }
    }

    NaviSarthiCard(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("New test build", color = Gold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    buildString {
                        append(b.builtAt.ifBlank { b.buildId })
                        if (b.sizeBytes > 0) append(" · ${b.sizeBytes / (1024 * 1024)} MB")
                    },
                    color = TextMid, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp),
                )
                if (b.notes.isNotBlank())
                    Text(b.notes, color = TextLo, fontSize = 11.5.sp, modifier = Modifier.padding(top = 2.dp))
                if (status.isNotBlank())
                    Text(status, color = TextLo, fontSize = 11.sp, fontFamily = GeistMonoFamily, modifier = Modifier.padding(top = 4.dp))
            }
            NaviSarthiBtn(
                if (busy) "…" else "Install",
                onClick = { if (!busy) installNow() },
                icon = NaviSarthiIcons.Wifi,
                variant = BtnVariant.Primary,
                size = BtnSize.Sm,
            )
        }
    }
}


@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    sub: String? = null,
    control: @Composable () -> Unit,
    last: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 6.dp, vertical = 13.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(Surf2).border(1.dp, Line, RoundedCornerShape(11.dp)),
        ) {
            Icon(icon, contentDescription = null, tint = TextMid, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextHi, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = GeistFamily)
            if (sub != null) Text(sub, color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        control()
    }
}
