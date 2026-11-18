package com.sxdpz23.navisarthi.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.sxdpz23.navisarthi.comms.*
import com.sxdpz23.navisarthi.ui.components.*
import com.sxdpz23.navisarthi.ui.theme.*
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import kotlin.math.ceil

@Composable
fun CommsScreen(vm: CommsViewModel = viewModel(), onOpenMap: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val repo = vm.repository
    val context = LocalContext.current
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

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader(title = "Comms", hint = "Your ride, together")
        state.error?.let { message ->
            NaviSarthiCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                Text(message, color = TextHi, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = repo::dismissError) { Text("Dismiss", color = Gold) }
            }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Gold)
        val snapshot = state.snapshot
        when {
            !state.configured -> NaviSarthiCard(Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Forum, null, tint = Gold, modifier = Modifier.size(32.dp))
                Text("Ride communications", color = TextHi, fontFamily = GeistFamily, fontSize = 22.sp)
                ControlHint("Comms is not available in this build yet. Once connected, you can scan a ride QR to chat, share locations and join audio.")
            }
            state.pendingAdmission -> NaviSarthiCard(Modifier.fillMaxWidth()) {
                Text("Finish connecting to your ride", color = TextHi, fontWeight = FontWeight.SemiBold)
                ControlHint("Your connection attempt is saved securely. Retry to recover the same membership.")
                NaviSarthiBtn("Retry connection", repo::retryAdmission, enabled = !state.busy)
            }
            state.hasSession && snapshot == null -> NaviSarthiCard(Modifier.fillMaxWidth()) {
                Text("Reconnecting to your ride", color = TextHi)
                ControlHint("Checking that your ride is still active before showing messages and locations.")
                NaviSarthiBtn("Reconnect", repo::retryConnection, variant = BtnVariant.Secondary, enabled = !state.busy)
            }
            snapshot == null -> {
                NaviSarthiCard(Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Forum, null, tint = Gold, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Stay with the ride", color = TextHi, fontFamily = GeistFamily, fontSize = 24.sp)
                    ControlHint("Text the group, see where everyone is, and join ride audio when you want. No account or phone number needed.")
                    Spacer(Modifier.height(12.dp))
                    NaviSarthiBtn("Create a ride", { create = true }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy)
                    Spacer(Modifier.height(8.dp))
                    NaviSarthiBtn("Scan ride QR", {
                        scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Scan a NaviSarthi ride QR").setBeepEnabled(false).setOrientationLocked(false))
                    }, variant = BtnVariant.Secondary, modifier = Modifier.fillMaxWidth(), enabled = !state.busy)
                }
                ControlHint("One ride at a time, with up to 12 riders. Each ride lasts at most 14 days. Chat and shared locations are cleared when it ends.")
            }
            else -> {
                val group = snapshot.group
                val owner = group.ownerId == snapshot.memberId
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(group.name, color = TextHi, fontFamily = GeistFamily, fontSize = 20.sp, maxLines = 2)
                        Text("${group.members.size}/12 riders · ${if (state.connected) "Connected" else "Reconnecting"}", color = TextMid, fontSize = 12.sp)
                        Text("Ends ${commsDate(group.expiresAt)}", color = TextMid, fontSize = 12.sp)
                    }
                    IconButton(onClick = { management = true }) { Icon(Icons.Outlined.MoreHoriz, "Ride settings", tint = TextHi) }
                    if (owner) IconButton(onClick = { invitation = true }) { Icon(Icons.Outlined.QrCode, "Show ride QR", tint = Gold) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NaviSarthiBtn(if (state.sharing) "Stop sharing" else "Share location", {
                        if (state.sharing) repo.stopSharing() else startMedia(CommsService.START_LOCATION)
                    }, modifier = Modifier.weight(1f), size = BtnSize.Sm, variant = BtnVariant.Secondary, enabled = state.sharing || state.connected)
                    NaviSarthiBtn(if (state.audio == CommsAudioState.OFF) "Join audio" else "Leave audio", {
                        if (state.audio == CommsAudioState.OFF) startMedia(CommsService.JOIN_AUDIO) else repo.leaveAudio()
                    }, modifier = Modifier.weight(1f), size = BtnSize.Sm, variant = BtnVariant.Secondary,
                        enabled = state.audio != CommsAudioState.OFF || (group.audioAvailable && state.connected))
                }
                if (state.audio != CommsAudioState.OFF) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(when (state.audio) { CommsAudioState.CONNECTING -> "Joining audio…"; CommsAudioState.RECONNECTING -> "Audio reconnecting…"; else -> "${state.audioParticipants} in audio" }, color = TextMid, modifier = Modifier.weight(1f))
                    TextButton(onClick = repo::toggleMute, enabled = state.audio == CommsAudioState.CONNECTED) { Text(if (state.muted) "Unmute" else "Mute", color = Gold) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(tab == 0, { tab = 0 }, label = { Text("Chat") })
                    FilterChip(tab == 1, { tab = 1 }, label = { Text("Riders") })
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
        AlertDialog(onDismissRequest = { management = false }, title = { Text("Ride settings") }, text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("Extend ride to day $days of 14")
                    Slider(days.toFloat(), { days = it.toInt() }, valueRange = 1f..14f, steps = 12)
                    Text(commsDate(group.createdAt + days * COMMS_DAY_MS), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { repo.extend(days); management = false }, enabled = group.createdAt + days * COMMS_DAY_MS > group.expiresAt && !state.busy) { Text("Extend ride") }
                }
                if (group.ownerId == snapshot.memberId) items(group.members.filter { it.id != snapshot.memberId }, key = { it.id }) { member ->
                    Column {
                        Text(member.nickname)
                        Row {
                            TextButton(onClick = { repo.transfer(member.id); management = false }) { Text("Make organiser") }
                            TextButton(onClick = { repo.remove(member.id); management = false }) { Text("Remove", color = Alert) }
                        }
                    }
                }
                item { TextButton(onClick = { management = false; confirmLeave = true }) { Text(if (group.ownerId == snapshot.memberId) "End ride for everyone" else "Leave ride", color = Alert) } }
            }
        }, confirmButton = { TextButton(onClick = { management = false }) { Text("Done") } })
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
            OutlinedTextField(nickname, { nickname = it.take(40) }, label = { Text("Ride nickname") }, singleLine = true)
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
    val names = state.snapshot!!.group.members.associate { it.id to it.nickname }
    Column(modifier) {
        if (state.messages.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("The ride conversation starts here.", color = TextMid) }
        else LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(state.messages, key = { it.seq }) { message ->
                val own = message.memberId == state.snapshot.memberId
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (own) Alignment.End else Alignment.Start) {
                    Column(Modifier.widthIn(max = 310.dp).background(if (own) GoldTint else Surf1, RoundedCornerShape(14.dp)).padding(12.dp)) {
                        Text(if (own) "You" else names[message.memberId] ?: "Former rider", color = TextMid, fontSize = 11.sp)
                        Text(message.text, color = TextHi)
                        Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.sentAt)), color = TextMid, fontSize = 10.sp)
                    }
                }
            }
        }
        state.pendingMessage?.let { pending ->
            Text("Not confirmed: ${pending.text.take(80)}", color = TextMid, maxLines = 2)
            TextButton(onClick = repo::retryMessage, enabled = state.connected && !state.busy) { Text("Retry message", color = Gold) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.imePadding()) {
            OutlinedTextField(draft, { draft = it.take(2000) }, modifier = Modifier.weight(1f), placeholder = { Text("Message the ride") }, maxLines = 3, enabled = state.pendingMessage == null)
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
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { NaviSarthiBtn("View riders in Nav", onOpenMap, variant = BtnVariant.Secondary, modifier = Modifier.fillMaxWidth()) }
        items(snapshot.group.members, key = { it.id }) { member ->
            val position = snapshot.positions[member.id]
            val age = position?.let { (now - it.receivedAt).coerceAtLeast(0) }
            NaviSarthiCard(Modifier.fillMaxWidth(), padding = 14.dp) {
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
        }
        if (!state.sharing) item { ControlHint("Share your location to see distances from your current position.") }
    }
}

@Composable private fun QrDialog(token: String?, enabled: Boolean, onRotate: () -> Unit, onDismiss: () -> Unit) {
    val bitmap = remember(token) { token?.let {
        val json = "{\"v\":1,\"type\":\"navisarthi-ride\",\"inviteToken\":\"$it\"}"
        val matrix = QRCodeWriter().encode(json, BarcodeFormat.QR_CODE, 640, 640, mapOf(EncodeHintType.MARGIN to 3))
        Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888).apply {
            setPixels(IntArray(640 * 640) { i -> if (matrix[i % 640, i / 640]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }, 0, 640, 0, 0, 640, 640)
        }
    } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Invite riders") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            bitmap?.let { Image(it.asImageBitmap(), "Ride invitation QR. Anyone with this code can join.", Modifier.fillMaxWidth().aspectRatio(1f).background(Color.White)) }
            Text(if (token == null) "Create a fresh invitation for this ride." else "Anyone with this QR can join immediately. Only show it to riders you want in the group.")
            TextButton(onClick = onRotate, enabled = enabled) { Text(if (token == null) "Create invitation" else "Replace invitation") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}
private fun commsDate(time: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))
