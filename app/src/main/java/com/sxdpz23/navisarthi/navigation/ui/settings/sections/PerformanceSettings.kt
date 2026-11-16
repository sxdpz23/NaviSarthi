package com.sxdpz23.navisarthi.navigation.ui.settings.sections

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sxdpz23.navisarthi.R
import com.sxdpz23.navisarthi.navigation.ui.settings.GroupDivider
import com.sxdpz23.navisarthi.navigation.ui.settings.PageIntro
import com.sxdpz23.navisarthi.navigation.ui.settings.SettingsGroup
import com.sxdpz23.navisarthi.navigation.ui.settings.SettingsScaffold
import com.sxdpz23.navisarthi.navigation.ui.settings.ToggleRow

/** Performance (2026-09-22): what Navigation loads ahead of time and how the map is drawn. Speed against
 *  memory is a per-phone call, so the choices live together here instead of under Diagnostics. */
@Composable
internal fun PerformanceSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("navigation_settings", android.content.Context.MODE_PRIVATE) }
    SettingsScaffold(stringResource(R.string.settings_performance), onBack) { topRow ->
        Spacer(Modifier.height(4.dp))
        PageIntro(stringResource(R.string.settings_performance_intro))
        SettingsGroup {
        ToggleRow(
            label = stringResource(R.string.settings_speech_preload),
            checked = com.sxdpz23.navisarthi.navigation.ui.SpeechPreload.on.value,
            onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.SpeechPreload.set(context, it) },
            hint = stringResource(R.string.settings_speech_preload_hint),
            switchModifier = topRow,
        )
        GroupDivider()
        FullPlaceLoadRow()
        GroupDivider()
        // From Navigation (2026-10-03): it exists for the frame rate in turns, so it lives with the
        // other speed choices.
        ToggleRow(
            label = stringResource(R.string.settings_turn_declutter),
            checked = com.sxdpz23.navisarthi.navigation.ui.TurnDeclutterPref.on.value,
            onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.TurnDeclutterPref.set(context, it) },
            hint = stringResource(R.string.settings_turn_declutter_hint),
        )
        GroupDivider()
        // Compatibility (TextureView) rendering - a hardware escape hatch (port of upstream
        // PimpinPumpkin/NaviSarthi 261156e2 + df2b8570). Writes the "texture_render" pref that
        // NavigationMapView reads when it creates the map; needs an app restart to apply. Also flips
        // itself on via the two-crash sentinel when a GPU driver kills the map at init.
        var textureRender by remember { mutableStateOf(prefs.getBoolean("texture_render", com.sxdpz23.navisarthi.navigation.ui.map.fragileGpuDefault())) }
        // When the sentinel flipped it on by itself, SAY SO on the row (with the date): a user who
        // never touched this must be able to see the app did, and that turning it off is safe to try.
        var textureAutoMs by remember { mutableStateOf(prefs.getLong("texture_render_auto_ms", 0L)) }
        ToggleRow(
            label = stringResource(R.string.settings_texture_render),
            checked = textureRender,
            onCheckedChange = { on ->
                textureRender = on
                prefs.edit().putBoolean("texture_render", on).remove("texture_render_auto_ms").apply()
                textureAutoMs = 0L
            },
            hint = if (textureRender && textureAutoMs > 0L) {
                stringResource(
                    R.string.settings_texture_render_auto_hint,
                    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(textureAutoMs)),
                )
            } else stringResource(R.string.settings_texture_render_hint),
        )
        }
    }
}
