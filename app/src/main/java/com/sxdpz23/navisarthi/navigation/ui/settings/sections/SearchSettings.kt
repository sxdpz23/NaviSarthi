package com.sxdpz23.navisarthi.navigation.ui.settings.sections

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sxdpz23.navisarthi.R
import com.sxdpz23.navisarthi.navigation.ui.map.MapViewModel
import com.sxdpz23.navisarthi.navigation.ui.settings.GroupDivider
import com.sxdpz23.navisarthi.navigation.ui.settings.PageIntro
import com.sxdpz23.navisarthi.navigation.ui.settings.SelectableRow
import com.sxdpz23.navisarthi.navigation.ui.settings.SettingsGroup
import com.sxdpz23.navisarthi.navigation.ui.settings.SettingsScaffold
import com.sxdpz23.navisarthi.navigation.ui.settings.SubHead
import com.sxdpz23.navisarthi.navigation.ui.settings.ToggleRow
import com.sxdpz23.navisarthi.navigation.ui.dpadClickable // D-pad-only operation (docs/dpad.md)
import com.sxdpz23.navisarthi.navigation.ui.dpadHighlight
import com.sxdpz23.navisarthi.navigation.ui.rememberDpadFocusKeeper // focus handoff for swap-in controls (docs/dpad.md)
import com.sxdpz23.navisarthi.navigation.ui.DpadFocusHandoff
import com.sxdpz23.navisarthi.navigation.ui.dpadFocusKept
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.shape.RoundedCornerShape as DpadShape

/** Search sub-screen: the voice-search toggle, the on-device ASR engine picker, the provider picker. */
@Composable
internal fun SearchSettingsScreen(vm: MapViewModel, onBack: () -> Unit, onOpenQuickChips: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SettingsScaffold(stringResource(R.string.settings_search), onBack) { topRow ->
        Spacer(Modifier.height(4.dp))
        SettingsGroup {
        ToggleRow(
            label = stringResource(R.string.settings_voice_search_toggle),
            checked = com.sxdpz23.navisarthi.navigation.ui.VoiceSearch.enabled.value,
            onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.VoiceSearch.set(context, it) },
            hint = stringResource(R.string.settings_voice_search_hint),
            // The top focusable control: Back routes its DOWN here, UP from here goes back to Back.
            switchModifier = topRow,
        )
        OtherLocationsRow()
        // Contacts-in-search (issue #243): READ_CONTACTS is asked HERE, at the point of use —
        // flipping the toggle on — never at install/onboarding. A denial leaves it off.
        val scope = rememberCoroutineScope()
        val contactsPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                com.sxdpz23.navisarthi.navigation.ui.ContactsSearch.set(context, true)
                scope.launch(Dispatchers.IO) { com.sxdpz23.navisarthi.navigation.data.ContactAddresses.reload(context) }
            }
        }
        ToggleRow(
            label = stringResource(R.string.settings_contacts_search),
            checked = com.sxdpz23.navisarthi.navigation.ui.ContactsSearch.enabled.value,
            onCheckedChange = { on ->
                when {
                    !on -> com.sxdpz23.navisarthi.navigation.ui.ContactsSearch.set(context, false)
                    com.sxdpz23.navisarthi.navigation.data.ContactAddresses.hasPermission(context) -> {
                        com.sxdpz23.navisarthi.navigation.ui.ContactsSearch.set(context, true)
                        scope.launch(Dispatchers.IO) { com.sxdpz23.navisarthi.navigation.data.ContactAddresses.reload(context) }
                    }
                    else -> contactsPermission.launch(android.Manifest.permission.READ_CONTACTS)
                }
            },
            hint = stringResource(R.string.settings_contacts_search_hint),
        )
        GroupDivider()
        QuickChipsRow(onOpenQuickChips)
        }

        // What the mic and the search bar understand as commands (discussion #365), in the app
        // language, straight from the phrase list the parser is tested against.
        Spacer(Modifier.height(8.dp))
        VoiceCommandList()

        // On-device voice search (tier-1): a PER-ENGINE picker (upstream 5d2a6636 + 137beea9).
        // Whisper (multilingual, smallest, the default) plus opt-in SenseVoice / Moonshine.
        // Download one or more, pick which the mic uses ("Use"), remove to free space. D-pad
        // safety: every control is a FULL-WIDTH single focus stop - an installed engine renders a
        // "Use/Active" row AND a SEPARATE "Remove" row (never two nested targets in one row), a
        // not-installed engine a "Download" row. One download at a time (the collision guard).
        LaunchedEffect(Unit) { vm.refreshAsr() }
        Spacer(Modifier.height(4.dp))
        PageIntro(stringResource(R.string.settings_asr_engines_hint))
        val anyAsrBusy = state.asrDownloadingId != null
        SettingsGroup {
        com.sxdpz23.navisarthi.navigation.voice.AsrEngine.entries.forEachIndexed { engineIdx, engine ->
            if (engineIdx > 0) GroupDivider()
            val installed = engine.id in state.asrInstalledIds
            val active = engine.id == state.asrActiveId && installed
            val thisDownloading = state.asrDownloadingId == engine.id
            val langs = stringResource(
                when (engine) {
                    com.sxdpz23.navisarthi.navigation.voice.AsrEngine.WHISPER_TINY, com.sxdpz23.navisarthi.navigation.voice.AsrEngine.WHISPER_BASE, com.sxdpz23.navisarthi.navigation.voice.AsrEngine.WHISPER_SMALL -> R.string.settings_asr_langs_whisper
                    com.sxdpz23.navisarthi.navigation.voice.AsrEngine.SENSE_VOICE -> R.string.settings_asr_langs_sensevoice
                    com.sxdpz23.navisarthi.navigation.voice.AsrEngine.MOONSHINE -> R.string.settings_asr_langs_moonshine
                },
            )
            val meta = stringResource(R.string.settings_asr_engine_meta, langs, engine.sizeMb)
            // D-pad: a keeper PER ENGINE ROW (same idiom as the routing-region rows) so when this
            // engine's control swaps Download -> spinner -> Use/Remove, the highlight stays on it
            // instead of teleporting to the top of the page. DpadFocusHandoff inside each branch,
            // dpadFocusKept on every variant, one retarget per row (docs/dpad.md; AGENTS.md).
            val keeper = rememberDpadFocusKeeper()
            when {
                thisDownloading -> {
                    val pct = state.asrDownloadPct ?: 0f
                    val queued = state.asrDownloadPct == null
                    DpadFocusHandoff(keeper)
                    Column(Modifier.fillMaxWidth().dpadFocusKept(keeper).dpadHighlight(DpadShape(6.dp)).focusable().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(engine.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                        Text(
                            when {
                                queued -> stringResource(R.string.map_asr_waiting)
                                state.asrInstalling -> stringResource(R.string.settings_voice_search_installing)
                                else -> stringResource(R.string.settings_voice_search_downloading, (pct * 100).toInt())
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(6.dp))
                        // Indeterminate while queued/installing (nothing to count): a frozen bar reads as stuck.
                        com.sxdpz23.navisarthi.navigation.ui.NavigationProgressBar(if (queued || state.asrInstalling) null else pct)
                        // No cancel during the unpack - the bytes are already down.
                        if (!state.asrInstalling) {
                            androidx.compose.material3.TextButton(
                                onClick = { vm.cancelAsrDownload() },
                                modifier = Modifier.dpadHighlight(androidx.compose.foundation.shape.CircleShape),
                            ) { Text(stringResource(R.string.settings_cancel)) }
                        }
                    }
                }
                installed -> {
                    // ONE focus stop = the whole row (dpadClickable); the RadioButton is read-only
                    // (onClick = null) so it isn't a second stop, matching the SelectableRow idiom.
                    // OK selects this engine; the ring is the only focus signal (dpadClickable drops
                    // Material's gray layer under key input).
                    DpadFocusHandoff(keeper)
                    Row(
                        Modifier.fillMaxWidth().dpadHighlight(DpadShape(6.dp)).dpadClickable(enabled = !active) { vm.selectAsrEngine(engine) }.dpadFocusKept(keeper).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = active, onClick = null)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(engine.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            stringResource(if (active) R.string.settings_asr_active else R.string.settings_asr_use),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    // Remove is a SEPARATE full-width row (its own single focus stop), never a nested
                    // target inside the row above - the anti-pattern the audits reject.
                    Row(
                        Modifier.fillMaxWidth().dpadHighlight(DpadShape(6.dp)).dpadClickable { vm.deleteAsrEngine(engine) }.padding(start = 56.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.settings_voice_search_remove), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                else -> {
                    DpadFocusHandoff(keeper)
                    Row(
                        Modifier.fillMaxWidth().dpadHighlight(DpadShape(6.dp)).dpadClickable(enabled = !anyAsrBusy) { vm.downloadAsrEngine(engine) }.dpadFocusKept(keeper).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(engine.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(stringResource(R.string.settings_voice_search_download, engine.sizeMb), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            LaunchedEffect(thisDownloading, installed, active) { keeper.retarget() }
            Spacer(Modifier.height(4.dp))
        }
        }
        // The engine picker only matters when there's actually a choice (the model AND a voice app,
        // or two voice apps): "Navigation Voice" = AUTO (the model wins, provider as graceful fallback),
        // "Android default" = the implicit intent Android routes, or each installed app pinned by name
        // so Android never interjects its own chooser. Enumerated off the main thread (a binder query
        // + per-app label load, the same class of call as the TTS engine list).
        val voiceProviders by produceState(initialValue = emptyList<com.sxdpz23.navisarthi.navigation.ui.VoiceSearch.Provider>()) {
            value = withContext(Dispatchers.IO) { com.sxdpz23.navisarthi.navigation.ui.VoiceSearch.providers(context) }
        }
        if ((state.asrInstalledIds.isNotEmpty() && voiceProviders.isNotEmpty()) || voiceProviders.size > 1) {
            Spacer(Modifier.height(8.dp))
            SubHead(stringResource(R.string.settings_voice_search_engine_title))
            SettingsGroup {
            val eng = com.sxdpz23.navisarthi.navigation.ui.VoiceSearch.engine.value
            val savedPick = com.sxdpz23.navisarthi.navigation.ui.VoiceSearch.provider.value
            val savedValid = voiceProviders.any { it.component.flattenToString() == savedPick }
            if (state.asrInstalledIds.isNotEmpty()) {
                SelectableRow(stringResource(R.string.settings_voice_search_engine_auto), eng != com.sxdpz23.navisarthi.navigation.ui.VoiceSearch.Engine.SYSTEM, onClick = { com.sxdpz23.navisarthi.navigation.ui.VoiceSearch.setEngine(context, com.sxdpz23.navisarthi.navigation.ui.VoiceSearch.Engine.AUTO) })
            }
            if (voiceProviders.isNotEmpty()) {
                if (state.asrInstalledIds.isNotEmpty()) GroupDivider()
                SelectableRow(
                    stringResource(R.string.settings_voice_search_engine_default),
                    eng == com.sxdpz23.navisarthi.navigation.ui.VoiceSearch.Engine.SYSTEM && !savedValid,
                    onClick = {
                        com.sxdpz23.navisarthi.navigation.ui.VoiceSearch.clearProvider(context)
                        com.sxdpz23.navisarthi.navigation.ui.VoiceSearch.setEngine(context, com.sxdpz23.navisarthi.navigation.ui.VoiceSearch.Engine.SYSTEM)
                    },
                )
            }
            voiceProviders.forEach { p ->
                GroupDivider()
                SelectableRow(
                    p.label,
                    eng == com.sxdpz23.navisarthi.navigation.ui.VoiceSearch.Engine.SYSTEM && savedValid && p.component.flattenToString() == savedPick,
                    onClick = {
                        com.sxdpz23.navisarthi.navigation.ui.VoiceSearch.setProvider(context, p.component)
                        com.sxdpz23.navisarthi.navigation.ui.VoiceSearch.setEngine(context, com.sxdpz23.navisarthi.navigation.ui.VoiceSearch.Engine.SYSTEM)
                    },
                )
            }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun VoiceCommandList() {
    val examples = remember { com.sxdpz23.navisarthi.navigation.core.search.VoiceCommandExamples.forLanguage(com.sxdpz23.navisarthi.navigation.ui.AppLocale.effective().language) }
    SettingsGroup(title = stringResource(R.string.voice_capture_examples_title)) {
        val kinds = examples.map { it.kind }.distinct()
        kinds.forEachIndexed { i, kind ->
            if (i > 0) GroupDivider()
            Column(Modifier.fillMaxWidth().dpadHighlight(DpadShape(10.dp)).focusable().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    stringResource(
                        when (kind) {
                            com.sxdpz23.navisarthi.navigation.core.search.VoiceCommandExamples.Kind.HOME -> R.string.voice_cmd_home
                            com.sxdpz23.navisarthi.navigation.core.search.VoiceCommandExamples.Kind.WORK -> R.string.voice_cmd_work
                            com.sxdpz23.navisarthi.navigation.core.search.VoiceCommandExamples.Kind.GO -> R.string.voice_cmd_go
                            com.sxdpz23.navisarthi.navigation.core.search.VoiceCommandExamples.Kind.ROUTE -> R.string.voice_cmd_route
                            com.sxdpz23.navisarthi.navigation.core.search.VoiceCommandExamples.Kind.NEARBY -> R.string.voice_cmd_nearby
                            com.sxdpz23.navisarthi.navigation.core.search.VoiceCommandExamples.Kind.ETA -> R.string.voice_cmd_eta
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                examples.filter { it.kind == kind }.forEach { e ->
                    Text(
                        "\u201C${e.phrase}\u201D",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    Text(
        stringResource(R.string.voice_cmd_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
