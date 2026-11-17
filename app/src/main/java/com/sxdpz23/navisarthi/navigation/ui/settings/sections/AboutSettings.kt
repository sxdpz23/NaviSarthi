package com.sxdpz23.navisarthi.navigation.ui.settings.sections

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sxdpz23.navisarthi.BuildConfig
import com.sxdpz23.navisarthi.R
import com.sxdpz23.navisarthi.navigation.ui.map.MapViewModel
import com.sxdpz23.navisarthi.navigation.ui.settings.*

/** Navigation acknowledgements. App identity and updates belong to the main Settings page. */
@Composable
internal fun AboutSettingsScreen(vm: MapViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var license by remember { mutableStateOf<String?>(null) }
    SettingsScaffold("Data and licenses", onBack) { _ ->
        LocalAppSettingsPages.current?.backup?.invoke()
        com.sxdpz23.navisarthi.ui.screens.TestBuildCard()
        PageIntro(stringResource(R.string.navigation_credits_intro))
        SettingsGroup(title = stringResource(R.string.settings_map_data)) {
            Hint(stringResource(R.string.settings_map_data_hint))
            Hint(stringResource(R.string.settings_map_data_fuel_gb))
            listOf(
                "OpenStreetMap contributors" to "https://www.openstreetmap.org/copyright",
                "Overture Maps · CDLA Permissive 2.0" to "https://cdla.dev/permissive-2-0/",
                "Open Government Licence" to "https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/",
                "Vela navigation source · GPL-3.0" to "https://github.com/PimpinPumpkin/Vela",
            ).forEach { (label, url) ->
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }) { Text(label) }
            }
            TextButton(onClick = {
                license = context.assets.open("licenses/Navigation-GPL-3.0.txt").bufferedReader().use { it.readText() }
            }) { Text(stringResource(R.string.navigation_license)) }
        }
        Spacer(Modifier.height(12.dp))
        Hint("NaviSarthi ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    }
    license?.let { text ->
        AlertDialog(
            onDismissRequest = { license = null },
            title = { Text("GNU GPL-3.0") },
            text = { Text(text, modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { license = null }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
}
