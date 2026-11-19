package com.sxdpz23.navisarthi.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.sxdpz23.navisarthi.comms.*
import com.sxdpz23.navisarthi.ui.components.*
import com.sxdpz23.navisarthi.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import com.sxdpz23.navisarthi.util.ShareUtils
import androidx.activity.result.PickVisualMediaRequest
import java.text.DateFormat
import java.util.Date
import kotlin.math.ceil

@Composable
fun CommsScreen(vm: CommsViewModel = viewModel(), onOpenMap: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val repo = vm.repository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scanOptions by remember { mutableStateOf(false) }
    var readingImage by remember { mutableStateOf(false) }
    var create by remember { mutableStateOf(false) }
    var invitation by remember { mutableStateOf(false) }
    var management by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var permissionAction by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val action = permissionAction; permissionAction = null
        val needed = if (action == CommsService.JOIN_AUDIO) Manifest.permission.RECORD_AUDIO else Manifest.permission.ACCESS_FINE_LOCATION
        if (grants[needed] == true || ContextCompat.checkSelfPermission(context, needed) == PackageManager.PERMISSION_GRANTED) {
            action?.let { CommsService.start(context, it) }
        } else repo.reportError(if (action == CommsService.JOIN_AUDIO) "Allow microphone access to join ride audio." else "Allow precise location to share your position.")
    }
    fun startMedia(action: String) {
        permissionAction = action
        val permissions = if (action == CommsService.JOIN_AUDIO) listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS)
            else listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
        permission.launch(permissions.toTypedArray())
    }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result -> result.contents?.let(repo::scan) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            readingImage = true
            try {
                repo.scan(readCommsQrImage(context, uri))
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                repo.reportError("No readable NaviSarthi ride QR found. Choose a clearer image.")
            } finally {
                readingImage = false
            }
        }
    }
    if (scanOptions) AlertDialog(onDismissRequest = { scanOptions = false }, title = { Text("Scan ride QR") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NaviSarthiBtn("Use camera", {
                scanOptions = false
                scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Scan a NaviSarthi ride QR").setBeepEnabled(false).setOrientationLocked(false))
            }, modifier = Modifier.fillMaxWidth())
            NaviSarthiBtn("Choose image from device", {
                scanOptions = false
                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, variant = BtnVariant.Secondary, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = { TextButton(onClick = { scanOptions = false }) { Text("Cancel") } })

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(title = "Comms", hint = "Ride chat, location and audio")
        state.error?.let { message ->
            NaviSarthiCard(Modifier.fillMaxWidth(), padding = 16.dp) {
                Text(message, color = TextHi, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = repo::dismissError) { Text("Dismiss", color = Gold) }
            }
        }
        if (state.busy || readingImage) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Gold)
        val snapshot = state.snapshot
        when {
            !state.configured -> NaviSarthiCard(Modifier.fillMaxWidth()) {
                Text("Comms unavailable", color = TextHi, fontFamily = GeistFamily, fontSize = 20.sp)
                Spacer(Modifier.height(4.dp))
                ControlHint("Comms is not available in this build yet. Once connected, you can scan a ride QR to chat, share locations and join audio.")
            }
            state.pendingAdmission -> NaviSarthiCard(Modifier.fillMaxWidth()) {
                Text("Finish connecting to your ride", color = TextHi, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                ControlHint("Your connection attempt is saved securely. Retry to recover the same membership.")
                Spacer(Modifier.height(12.dp))
                NaviSarthiBtn("Retry connection", repo::retryAdmission, enabled = !state.busy)
            }
            state.hasSession && snapshot == null -> NaviSarthiCard(Modifier.fillMaxWidth()) {
                Text("Reconnecting to your ride", color = TextHi)
                Spacer(Modifier.height(4.dp))
                ControlHint("Checking that your ride is still active before showing messages and locations.")
                Spacer(Modifier.height(12.dp))
                NaviSarthiBtn("Reconnect", repo::retryConnection, variant = BtnVariant.Secondary, enabled = !state.busy)
                if (state.audio != CommsAudioState.OFF) {
                    Spacer(Modifier.height(16.dp))
                    Text("Ride audio is active", color = TextHi)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = repo::toggleMute, enabled = state.audio == CommsAudioState.CONNECTED) { Text(if (state.muted) "Unmute" else "Mute") }
                        TextButton(onClick = repo::leaveAudio) { Text("Leave audio") }
                    }
                }
            }
            snapshot == null -> {
                NaviSarthiCard(Modifier.fillMaxWidth()) {
                    Text("Start or join a ride", color = TextHi, fontFamily = GeistFamily, fontSize = 22.sp)
                    Spacer(Modifier.height(4.dp))
                    ControlHint("Create a group for your riders or scan an invitation QR. No account needed.")
                    Spacer(Modifier.height(16.dp))
                    NaviSarthiBtn("Create a ride", { create = true }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy && !readingImage)
                    Spacer(Modifier.height(8.dp))
                    NaviSarthiBtn("Scan ride QR", {
                        scanOptions = true
                    }, variant = BtnVariant.Secondary, modifier = Modifier.fillMaxWidth(), enabled = !state.busy && !readingImage)
                }
                ControlHint("One ride at a time, with up to 12 riders. Each ride lasts at most 14 days. Chat and shared locations are cleared when it ends.")
            }
            else -> {
                val group = snapshot.group
                val owner = group.ownerId == snapshot.memberId
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(group.name, color = TextHi, fontFamily = GeistFamily, fontSize = 20.sp, maxLines = 2)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(8.dp).background(if (state.connected) Gold else TextMid, CircleShape))
                            Text("${group.members.size}/12 riders · ${if (state.connected) "Connected" else "Reconnecting"}", color = TextMid, fontSize = 12.sp)
                        }
                        Text("Ends ${commsDate(group.expiresAt)}", color = TextMid, fontSize = 12.sp)
                    }
                    IconButton(onClick = { management = true }) { Icon(Icons.Outlined.MoreHoriz, "Ride settings", tint = TextHi) }
                    if (state.audio == CommsAudioState.CONNECTED || state.audio == CommsAudioState.RECONNECTING) {
                        IconButton(onClick = repo::toggleMute, enabled = state.audio == CommsAudioState.CONNECTED) {
                            Icon(
                                if (state.muted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                                if (state.muted) "Unmute microphone" else "Mute microphone",
                                tint = if (state.audio != CommsAudioState.CONNECTED) TextDis else if (state.muted) Gold else TextHi,
                            )
                        }
                    }
                    if (owner) IconButton(onClick = { invitation = true }) { Icon(Icons.Outlined.QrCode, "Show ride QR", tint = TextHi) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    NaviSarthiBtn(if (state.sharing) "Stop sharing" else "Share location", {
                        if (state.sharing) repo.stopSharing() else startMedia(CommsService.START_LOCATION)
                    }, modifier = Modifier.weight(1f), size = BtnSize.Sm, variant = BtnVariant.Secondary, enabled = state.sharing || state.connected)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (state.audio != CommsAudioState.OFF) {
                            Text(
                                when (state.audio) {
                                    CommsAudioState.CONNECTING -> "Joining audio…"
                                    CommsAudioState.RECONNECTING -> "Audio reconnecting…"
                                    else -> "${state.audioParticipants} in audio"
                                },
                                color = TextMid, fontSize = 12.sp,
                            )
                        }
                        NaviSarthiBtn(if (state.audio == CommsAudioState.OFF) "Join audio" else "Leave audio", {
                            if (state.audio == CommsAudioState.OFF) startMedia(CommsService.JOIN_AUDIO) else repo.leaveAudio()
                        }, modifier = Modifier.fillMaxWidth(), size = BtnSize.Sm, variant = BtnVariant.Secondary,
                            enabled = state.audio != CommsAudioState.OFF || (group.audioAvailable && state.connected))
                    }
                }
                Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Chat", "Riders").forEachIndexed { index, label ->
                        val selected = tab == index
                        Column(
                            Modifier.weight(1f).selectable(selected, role = Role.Tab, onClick = { tab = index }),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.heightIn(min = 44.dp).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                                Text(label, color = if (selected) TextHi else TextMid, style = MaterialTheme.typography.labelLarge)
                            }
                            HorizontalDivider(thickness = 2.dp, color = if (selected) Gold else Line)
                        }
                    }
                }
                if (tab == 0) ChatPane(state, repo, Modifier.weight(1f)) else RiderPane(state, onOpenMap, Modifier.weight(1f))
            }
        }
    }
    if (create) AdmissionDialog("Create a ride", includeRide = true, onDismiss = { create = false }) { nickname, name, days -> create = false; repo.create(nickname, name, days) }
    state.preview?.let { preview ->
        AdmissionDialog("Join ${preview.name}", hint = "${preview.memberCount}/${preview.capacity} riders · Ends ${commsDate(preview.expiresAt)}", onDismiss = repo::dismissPreview) { nickname, _, _ -> repo.join(nickname) }
    }
    if (invitation && state.snapshot != null) QrDialog(state.invitation, !state.busy, repo::rotateInvitation) { invitation = false }
    if (management && state.snapshot != null) {
        val snapshot = state.snapshot!!; val group = snapshot.group
        var days by remember(group.expiresAt) { mutableIntStateOf(ceil((group.expiresAt - group.createdAt).toDouble() / COMMS_DAY_MS).toInt().coerceIn(1, 14)) }
        val glassShape = RoundedCornerShape(24.dp)
        Dialog(onDismissRequest = { management = false }) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xF032343A), Color(0xE6191B20))), glassShape)
                    .border(1.dp, Color.White.copy(alpha = 0.17f), glassShape)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Ride settings", color = Color.White, style = MaterialTheme.typography.titleLarge)
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Extend ride to day $days of 14", color = Color(0xFFD8DBDF))
                            Slider(days.toFloat(), { days = it.toInt() }, valueRange = 1f..14f, steps = 12)
                            Text(commsDate(group.createdAt + days * COMMS_DAY_MS), color = Color(0xFFD8DBDF), style = MaterialTheme.typography.bodySmall)
                            FilledTonalButton(
                                modifier = Modifier.align(Alignment.End),
                                onClick = { repo.extend(days); management = false },
                                enabled = group.createdAt + days * COMMS_DAY_MS > group.expiresAt && !state.busy,
                                shape = RoundedCornerShape(14.dp),
                            ) { Text("Extend ride") }
                        }
                    }
                    if (group.ownerId == snapshot.memberId) items(group.members.filter { it.id != snapshot.memberId }, key = { it.id }) { member ->
                        Column {
                            Text(member.nickname, color = Color.White)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { repo.transfer(member.id); management = false }, shape = RoundedCornerShape(14.dp)) { Text("Make organiser") }
                                OutlinedButton(onClick = { repo.remove(member.id); management = false }, shape = RoundedCornerShape(14.dp)) { Text("Remove", color = Color(0xFFFFB4AB)) }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val openConfirmation = { management = false; confirmLeave = true }
                    if (group.ownerId == snapshot.memberId) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = openConfirmation,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C242C), contentColor = Color.White),
                        ) { Text("End ride for everyone", textAlign = TextAlign.Center) }
                    } else {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = openConfirmation,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FFB4AB)),
                        ) { Text("Leave ride", color = Color(0xFFFFB4AB), textAlign = TextAlign.Center) }
                    }
                    FilledTonalButton(onClick = { management = false }, shape = RoundedCornerShape(14.dp)) { Text("Done") }
                }
            }
        }
    }
    if (confirmLeave && state.snapshot != null) {
        val owner = state.snapshot!!.group.ownerId == state.snapshot!!.memberId
        AlertDialog(onDismissRequest = { confirmLeave = false }, title = { Text(if (owner) "End this ride?" else "Leave this ride?") },
            text = { Text(if (owner) "This ends audio and location sharing for everyone and starts deleting all ride communications." else "This stops your audio and location sharing and clears Comms on this phone. Messages you sent remain until the ride ends.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; repo.leave(owner) }) { Text(if (owner) "End ride" else "Leave", color = Alert) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Cancel") } })
    }
}

@Composable private fun AdmissionDialog(title: String, hint: String? = null, includeRide: Boolean = false, onDismiss: () -> Unit, onConfirm: (String, String, Int) -> Unit) {
    var nickname by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }; var days by remember { mutableIntStateOf(1) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            hint?.let { Text(it) }
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it.take(8) },
                label = { Text("Your nickname in ride") },
                singleLine = true,
            )
            if (includeRide) {
                OutlinedTextField(name, { name = it.take(80) }, label = { Text("Ride name") }, singleLine = true)
                Text("Ride length: $days ${if (days == 1) "day" else "days"}")
                Slider(days.toFloat(), { days = it.toInt() }, valueRange = 1f..14f, steps = 12)
                Text("Ends ${commsDate(System.currentTimeMillis() + days * COMMS_DAY_MS)}", style = MaterialTheme.typography.bodySmall)
            }
            Text("Location and microphone stay off until you enable them.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { onConfirm(nickname, name, days) }, enabled = nickname.isNotBlank() && (!includeRide || name.isNotBlank())) { Text(if (includeRide) "Create" else "Join ride") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable private fun ChatPane(state: CommsState, repo: CommsRepository, modifier: Modifier) {
    var draft by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(state.messages.lastOrNull()?.seq) { if (state.messages.isNotEmpty()) list.animateScrollToItem(state.messages.lastIndex) }
    val snapshot = state.snapshot!!
    val names = snapshot.group.members.associate { it.id to it.nickname }
    val colors = remember(snapshot.group.id, snapshot.group.members) { RiderColors.forMembers(snapshot.group) }
    var timestampMessage by remember(snapshot.group.id) { mutableStateOf<String?>(null) }
    var timestampTap by remember(snapshot.group.id) { mutableIntStateOf(0) }
    LaunchedEffect(timestampTap) {
        if (timestampMessage != null) {
            delay(3500)
            timestampMessage = null
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.messages.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("No messages yet", color = TextMid) }
        else LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(vertical = 4.dp)) {
            itemsIndexed(state.messages, key = { _, message -> message.seq }) { index, message ->
                val own = message.memberId == snapshot.memberId
                val bubbleShape = RoundedCornerShape(16.dp)
                val previous = state.messages.getOrNull(index - 1)
                val gap = when {
                    previous == null -> 0.dp
                    previous.memberId == message.memberId -> 4.dp
                    else -> 8.dp
                }
                Column(
                    Modifier.fillMaxWidth().padding(top = gap),
                    horizontalAlignment = if (own) Alignment.End else Alignment.Start,
                ) {
                    Column(
                        Modifier.widthIn(max = 310.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (!own) {
                            Text(
                                names[message.memberId] ?: "Former rider",
                                color = colors[message.memberId]?.let { Color(it) } ?: TextMid,
                                fontSize = 12.sp,
                            )
                        }
                        Text(
                            message.text,
                            color = TextHi,
                            modifier = Modifier.clip(bubbleShape).background(if (own) GoldTint else Surf1)
                                .clickable(onClickLabel = "Show message time") {
                                    timestampMessage = message.id
                                    timestampTap++
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                        if (timestampMessage == message.id) {
                            Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.sentAt)), color = TextMid, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        state.pendingMessage?.let { pending ->
            Text("Not confirmed: ${pending.text.take(80)}", color = TextMid, maxLines = 2)
            TextButton(onClick = repo::retryMessage, enabled = state.connected && !state.busy) { Text("Retry message", color = Gold) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.imePadding()) {
            OutlinedTextField(draft, { draft = it.take(2000) }, modifier = Modifier.weight(1f), placeholder = { Text("Message the ride") }, shape = RoundedCornerShape(28.dp), maxLines = 3, enabled = state.pendingMessage == null)
            IconButton(onClick = { repo.send(draft); draft = "" }, enabled = state.connected && !state.busy && state.pendingMessage == null && draft.isNotBlank()) {
                Icon(Icons.Outlined.Send, "Send message", tint = if (state.connected && draft.isNotBlank()) Gold else TextDis)
            }
        }
    }
}

@Composable private fun RiderPane(state: CommsState, onOpenMap: () -> Unit, modifier: Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis() + state.serverOffsetMs) }
    LaunchedEffect(state.serverOffsetMs) { while (true) { now = System.currentTimeMillis() + state.serverOffsetMs; delay(1000) } }
    val snapshot = state.snapshot!!
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 8.dp)) {
        item {
            NaviSarthiBtn("View riders in Nav", onOpenMap, variant = BtnVariant.Secondary, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        }
        items(snapshot.group.members, key = { it.id }) { member ->
            val position = snapshot.positions[member.id]
            val age = position?.let { (now - it.receivedAt).coerceAtLeast(0) }
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(member.nickname + if (member.id == snapshot.memberId) " · You" else "", color = TextHi, fontWeight = FontWeight.SemiBold)
                val own = state.ownPosition?.takeIf { now - it.receivedAt < 30_000 }
                val distance = if (position != null && own != null && member.id != snapshot.memberId) commsDistanceMeters(own, position) else null
                ControlHint(when {
                    age == null || age >= 120_000 -> "Location unavailable"
                    age >= 30_000 -> "Last seen ${age / 1000}s ago"
                    distance != null -> "${com.sxdpz23.navisarthi.navigation.ui.formatDistance(distance)} away · straight-line"
                    else -> "Location updated ${age / 1000}s ago"
                })
            }
            HorizontalDivider(color = Line)
        }
        if (!state.sharing) item {
            Spacer(Modifier.height(12.dp))
            ControlHint("Share your location to see distances from your current position.")
        }
    }
}

@Composable private fun QrDialog(token: String?, enabled: Boolean, onRotate: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }
    val accent = Gold.toArgb()
    val bitmap = remember(token, accent) { token?.let { renderRideQr(context, it, accent) } }
    val glass = Brush.verticalGradient(listOf(Color(0xEE292C31), Color(0xE5141619)))
    val shape = RoundedCornerShape(16.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val view = androidx.compose.ui.platform.LocalView.current
        SideEffect {
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.apply {
                addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(0.8f)
            }
        }
        Column(
            Modifier.padding(horizontal = 32.dp, vertical = 16.dp).widthIn(max = 360.dp)
                .fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.align(Alignment.End).background(glass, shape)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), shape).padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(enabled = bitmap != null && enabled && !sharing, onClick = {
                    val image = bitmap ?: return@IconButton

                    scope.launch {
                        sharing = true
                        shareError = null
                        try {
                            val file = withContext(Dispatchers.IO) {
                                val directory = File(context.cacheDir, "comms-invitations").apply { mkdirs() }
                                File(directory, "ride-qr-${UUID.randomUUID()}.png").also { target ->
                                    target.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                                }
                            }
                            if (CommsRepository.get(context).state.value.invitation == token) {
                                ShareUtils.shareFile(context, file, "image/png", "Share ride QR")
                            } else {
                                file.delete()
                                shareError = "This invitation has changed or the ride has ended. Open the current QR to share it."
                            }
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            shareError = "Could not share the invitation. Please try again."
                        } finally {
                            sharing = false
                        }
                    }
                }) { Icon(Icons.Outlined.Share, "Share QR image", tint = Color.White.copy(alpha = if (bitmap != null && enabled && !sharing) 1f else 0.38f)) }
                IconButton(onClick = onRotate, enabled = enabled && !sharing) {
                    Icon(Icons.Outlined.Refresh, if (token == null) "Create invitation" else "Replace invitation",
                        tint = Color.White.copy(alpha = if (enabled && !sharing) 1f else 0.38f))
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.KeyboardArrowDown, "Close invitation", tint = Color.White) }
            }
            bitmap?.let {
                Spacer(Modifier.height(16.dp))
                Image(it.asImageBitmap(), "Ride invitation QR", Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)))
            }
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier.fillMaxWidth().background(glass, shape)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), shape).padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Invite friends", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (token == null) "Create a fresh invitation for this ride." else "Anyone with this QR can join immediately. Only show it to riders you want in the group.",
                    color = Color(0xFFD8DBDF), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                )
                shareError?.let { Text(it, color = Color(0xFFFFB4AB), textAlign = TextAlign.Center) }
            }
        }
    }
}

private fun commsDate(time: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))
