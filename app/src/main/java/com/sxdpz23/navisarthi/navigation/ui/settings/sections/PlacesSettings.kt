package com.sxdpz23.navisarthi.navigation.ui.settings.sections

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sxdpz23.navisarthi.R
import com.sxdpz23.navisarthi.navigation.ui.settings.GroupDivider
import com.sxdpz23.navisarthi.navigation.ui.settings.SettingsGroup
import com.sxdpz23.navisarthi.navigation.ui.settings.SettingsScaffold
import com.sxdpz23.navisarthi.navigation.ui.settings.ToggleRow

/**
 * Places sub-screen (was Place pages; 2026-09-17 settings reshuffle): where the map's places come
 * from, what the map draws for them, and the five place-page content toggles.
 */
@Composable
internal fun PlacesSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsScaffold(stringResource(R.string.settings_places), onBack) { topRow ->
        Spacer(Modifier.height(4.dp))
        PlacesSourceGroup(topRow)
        Spacer(Modifier.height(8.dp))
        PlacesOnMapGroup()
        Spacer(Modifier.height(8.dp))
        SettingsGroup(title = stringResource(R.string.settings_place_pages)) {
        ReviewAndPhotoRows()
        GroupDivider()
        ToggleRow(
            label = stringResource(R.string.settings_hide_adult),
            checked = com.sxdpz23.navisarthi.navigation.ui.HideAdult.on.value,
            onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.HideAdult.set(context, it) },
            hint = stringResource(R.string.settings_hide_adult_hint),
        )
        GroupDivider()
        ToggleRow(
            label = stringResource(R.string.settings_hide_external_links),
            checked = com.sxdpz23.navisarthi.navigation.ui.HideExternalLinks.on.value,
            onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.HideExternalLinks.set(context, it) },
            hint = stringResource(R.string.settings_hide_external_links_hint),
        )
        }
        Spacer(Modifier.height(24.dp))
    }
}
