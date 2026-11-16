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
import com.sxdpz23.navisarthi.navigation.ui.settings.Hint
import com.sxdpz23.navisarthi.navigation.ui.settings.SectionTitle
import com.sxdpz23.navisarthi.navigation.ui.settings.SettingsGroup
import com.sxdpz23.navisarthi.navigation.ui.settings.ToggleRow

// The switches that decide what Google is asked. Each is one row here, drawn on its own page
// and again in [GoogleUsesSection] (Settings > Privacy, and the first run). The state lives in
// process-wide holders, so the two copies stay in step.

/** Whether a tapped open place is looked up on Google. Only meaningful while open places draw. */
@Composable
internal fun TappedLookupRow(compact: Boolean = false) {
    val context = LocalContext.current
    ToggleRow(
        label = stringResource(R.string.settings_places_lookup),
        checked = com.sxdpz23.navisarthi.navigation.ui.MapPoiPrefs.lookupTappedPlaces.value,
        onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.MapPoiPrefs.setLookupTappedPlaces(context, it) },
        hint = if (compact) null else stringResource(R.string.settings_places_lookup_hint),
    )
}

/** What a place page loads from Google: reviews, the full reviews page, photos, popular times. */
@Composable
internal fun ReviewAndPhotoRows(compact: Boolean = false) {
    val context = LocalContext.current
    ToggleRow(
        label = stringResource(R.string.settings_show_reviews),
        checked = com.sxdpz23.navisarthi.navigation.ui.ShowReviews.on.value,
        onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.ShowReviews.set(context, it) },
        hint = if (compact) null else stringResource(R.string.settings_show_reviews_hint),
    )
    if (com.sxdpz23.navisarthi.navigation.ui.ShowReviews.on.value) {
        GroupDivider()
        ToggleRow(
            label = stringResource(R.string.settings_reviews_on_tap),
            checked = com.sxdpz23.navisarthi.navigation.ui.ReviewsOnTap.on.value,
            onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.ReviewsOnTap.set(context, it) },
            hint = if (compact) null else stringResource(R.string.settings_reviews_on_tap_hint),
        )
    }
    GroupDivider()
    ToggleRow(
        label = stringResource(R.string.settings_read_all_reviews),
        checked = com.sxdpz23.navisarthi.navigation.ui.LiveReviews.on.value,
        onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.LiveReviews.set(context, it) },
        hint = if (compact) null else stringResource(R.string.settings_read_all_reviews_hint),
    )
    GroupDivider()
    ToggleRow(
        label = stringResource(R.string.settings_load_photos),
        checked = com.sxdpz23.navisarthi.navigation.ui.LoadPhotos.on.value,
        onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.LoadPhotos.set(context, it) },
        hint = if (compact) null else stringResource(R.string.settings_load_photos_hint),
    )
    if (com.sxdpz23.navisarthi.navigation.ui.LoadPhotos.on.value) {
        GroupDivider()
        ToggleRow(
            label = stringResource(R.string.settings_photos_on_tap),
            checked = com.sxdpz23.navisarthi.navigation.ui.PhotosOnTap.on.value,
            onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.PhotosOnTap.set(context, it) },
            hint = if (compact) null else stringResource(R.string.settings_photos_on_tap_hint),
        )
    }
    GroupDivider()
    ToggleRow(
        label = stringResource(R.string.settings_details_retry),
        checked = com.sxdpz23.navisarthi.navigation.ui.DetailsRetry.on.value,
        onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.DetailsRetry.set(context, it) },
        hint = if (compact) null else stringResource(R.string.settings_details_retry_hint),
    )
}

@Composable
internal fun FullPlaceLoadRow(compact: Boolean = false) {
    val context = LocalContext.current
    ToggleRow(
        label = stringResource(R.string.settings_full_place_load),
        checked = com.sxdpz23.navisarthi.navigation.ui.FullPlaceLoad.on.value,
        onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.FullPlaceLoad.set(context, it) },
        hint = if (compact) null else stringResource(R.string.settings_full_place_load_hint),
    )
}

@Composable
internal fun OtherLocationsRow(compact: Boolean = false) {
    val context = LocalContext.current
    ToggleRow(
        label = stringResource(R.string.settings_other_locations_auto),
        checked = com.sxdpz23.navisarthi.navigation.ui.OtherLocationsAuto.on.value,
        onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.OtherLocationsAuto.set(context, it) },
        hint = if (compact) null else stringResource(R.string.settings_other_locations_auto_hint),
    )
}

@Composable
internal fun LiveTrafficRow(switchModifier: Modifier = Modifier, compact: Boolean = false) {
    val context = LocalContext.current
    ToggleRow(
        label = stringResource(R.string.settings_live_traffic),
        checked = com.sxdpz23.navisarthi.navigation.ui.Traffic.on.value,
        onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.Traffic.set(context, it) },
        hint = if (compact) null else stringResource(R.string.settings_live_traffic_hint),
        switchModifier = switchModifier,
    )
}

@Composable
internal fun RouteTrafficOnTapRow(vm: com.sxdpz23.navisarthi.navigation.ui.map.MapViewModel, compact: Boolean = false) {
    val context = LocalContext.current
    ToggleRow(
        label = stringResource(R.string.settings_route_traffic_on_tap),
        checked = com.sxdpz23.navisarthi.navigation.ui.RouteTrafficOnTap.on.value,
        onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.RouteTrafficOnTap.set(context, it); vm.syncRouteTraffic() },
        hint = if (compact) null else stringResource(R.string.settings_route_traffic_on_tap_hint),
    )
}

/** The traffic and route re-check during navigation, a Google request about every two minutes. */
@Composable
internal fun LiveRechecksRow(vm: com.sxdpz23.navisarthi.navigation.ui.map.MapViewModel, compact: Boolean = false) {
    var liveRechecks by remember { mutableStateOf(vm.liveRechecksOn()) }
    ToggleRow(
        label = stringResource(R.string.settings_live_rechecks),
        checked = liveRechecks,
        onCheckedChange = { on ->
            liveRechecks = on
            vm.setLiveRechecks(on)
        },
        hint = if (compact) null else stringResource(R.string.settings_live_rechecks_hint),
    )
}

/**
 * Every switch that decides what Google is asked, in one list: where the map's places come from,
 * what a place page loads, and what a route and a drive ask. Shown in Settings > Privacy while
 * Google is on, and on the first run behind "Choose what Google is used for". Labels only: each
 * row's explanation is on its own page, and here it would bury the list.
 */
@Composable
internal fun GoogleUsesSection(vm: com.sxdpz23.navisarthi.navigation.ui.map.MapViewModel, topRow: Modifier = Modifier, title: Boolean = true) {
    if (title) SectionTitle(stringResource(R.string.settings_google_uses))
    Hint(stringResource(R.string.settings_google_uses_hint))
    Spacer(Modifier.height(4.dp))
    PlacesSourceGroup(topRow, compact = true)
    Spacer(Modifier.height(8.dp))
    SettingsGroup(title = stringResource(R.string.settings_place_pages)) {
        if (com.sxdpz23.navisarthi.navigation.ui.MapPoiPrefs.showPois.value && com.sxdpz23.navisarthi.navigation.ui.MapPoiPrefs.openPlaces) {
            TappedLookupRow(compact = true)
            GroupDivider()
        }
        ReviewAndPhotoRows(compact = true)
        GroupDivider()
        FullPlaceLoadRow(compact = true)
        GroupDivider()
        OtherLocationsRow(compact = true)
    }
    Spacer(Modifier.height(8.dp))
    SettingsGroup(title = stringResource(R.string.settings_google_uses_traffic)) {
        RouteTrafficOnTapRow(vm, compact = true)
        GroupDivider()
        LiveRechecksRow(vm, compact = true)
        GroupDivider()
        LiveTrafficRow(compact = true)
    }
}
