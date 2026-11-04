package com.example.navisarthi.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.navisarthi.data.DriveBackup
import com.example.navisarthi.ui.components.*
import com.example.navisarthi.ui.theme.TextMid
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class DriveAction { Link, Backup, List, Restore }

@Composable
internal fun DriveBackupSettings() {
    val context = LocalContext.current
    val prefs = remember { DriveBackup.preferences(context) }
    val scope = rememberCoroutineScope()
    var connected by remember { mutableStateOf(prefs.contains("owner")) }
    var daily by remember { mutableStateOf(prefs.getBoolean("daily", false)) }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<DriveAction?>(null) }
    var snapshots by remember { mutableStateOf<List<DriveBackup.Snapshot>?>(null) }
    var selected by remember { mutableStateOf<DriveBackup.Snapshot?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    var status by remember {
        mutableStateOf(if (prefs.getBoolean("paused", false)) "Scheduled backup paused. Reconnect Google Drive." else "")
    }

    suspend fun perform(action: DriveAction, token: String) {
        when (action) {
            DriveAction.Link -> {
                DriveBackup.link(context, token)
                connected = true
                status = "Google Drive connected."
            }
            DriveAction.Backup -> {
                DriveBackup.backup(context, token)
                status = "Backup saved to My Drive / NaviSarthi."
            }
            DriveAction.List -> {
                val available = DriveBackup.list(context, token)
                if (available.isEmpty()) status = "No NaviSarthi backups found."
                else snapshots = available
            }
            DriveAction.Restore -> {
                DriveBackup.restore(context, token, checkNotNull(selected))
                selected = null
                status = "Backup restored."
            }
        }
    }

    fun runAction(block: suspend () -> Unit) {
        busy = true
        status = ""
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                status = "Drive action failed. Check your connection and account access, then try again."
            } finally {
                busy = false
            }
        }
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val action = pending
        pending = null
        if (action != null && result.resultCode == android.app.Activity.RESULT_OK) {
            runAction {
                val token = Identity.getAuthorizationClient(context)
                    .getAuthorizationResultFromIntent(result.data).accessToken
                perform(action, checkNotNull(token))
            }
        } else status = "Google Drive authorization cancelled."
    }

    fun start(action: DriveAction) = runAction {
        val auth = DriveBackup.authorize(context)
        val resolution = auth.pendingIntent
        if (auth.hasResolution() && resolution != null) {
            pending = action
            consent.launch(IntentSenderRequest.Builder(resolution.intentSender).build())
        } else perform(action, checkNotNull(auth.accessToken))
    }

    ControlSection("Google Drive backup")
    NaviSarthiCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp) {
        Text(
            if (connected) prefs.getString("email", "")?.ifBlank { "Google Drive connected" }.orEmpty()
            else "Save a copy of your app data to your own Google Drive.",
            color = TextMid,
        )
        NaviSarthiBtn(
            if (connected) "Reconnect Google Drive" else "Connect Google Drive",
            onClick = { start(DriveAction.Link) },
            enabled = !busy && pending == null,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        ControlHint("Authorize the Google account you want to use for backups.")
        if (connected) {
            NaviSarthiBtn("Back up now", onClick = { start(DriveAction.Backup) },
                enabled = !busy && pending == null, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            ControlHint("Save a new snapshot in My Drive / NaviSarthi.")
            NaviSarthiBtn("Restore from Drive", onClick = { start(DriveAction.List) },
                enabled = !busy && pending == null, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            ControlHint("Choose a snapshot and confirm before replacing local records.")
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Daily backup", Modifier.weight(1f), color = TextMid)
                NaviSarthiToggle(on = daily, onChange = {
                    DriveBackup.setDaily(context, it)
                    daily = it
                })
            }
            ControlHint("Back up automatically when the phone has internet access.")
            TextButton(enabled = !busy && pending == null, onClick = {
                DriveBackup.disconnect(context)
                connected = false
                daily = false
                status = "Google Drive disconnected."
            }) { Text("Disconnect Google Drive") }
        }
        if (busy || status.isNotBlank()) Text(if (busy) "Working…" else status, color = TextMid)
    }

    snapshots?.let { available ->
        AlertDialog(
            onDismissRequest = { snapshots = null },
            title = { Text("Choose a backup") },
            text = {
                Column(Modifier.heightIn(max = 300.dp).then(Modifier)) {
                    androidx.compose.foundation.lazy.LazyColumn {
                        items(available.size) { index ->
                            val snapshot = available[index]
                            TextButton(onClick = {
                                selected = snapshot
                                snapshots = null
                                confirmRestore = true
                            }) { Text(snapshot.name) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { snapshots = null }) { Text("Cancel") } },
        )
    }
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Replace local data?") },
            text = { Text("Restore ${selected?.name.orEmpty()} and replace the app records on this phone.") },
            confirmButton = { TextButton(onClick = {
                confirmRestore = false
                start(DriveAction.Restore)
            }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
        )
    }
}
