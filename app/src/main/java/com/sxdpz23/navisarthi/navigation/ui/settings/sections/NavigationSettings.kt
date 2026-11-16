package com.sxdpz23.navisarthi.navigation.ui.settings.sections

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sxdpz23.navisarthi.R
import com.sxdpz23.navisarthi.navigation.core.feedback.Haptics
import com.sxdpz23.navisarthi.navigation.core.model.TravelMode
import com.sxdpz23.navisarthi.navigation.ui.map.MapViewModel
import com.sxdpz23.navisarthi.navigation.ui.settings.GroupDivider
import com.sxdpz23.navisarthi.navigation.ui.settings.settingsAnchor
import com.sxdpz23.navisarthi.navigation.ui.settings.Hint
import com.sxdpz23.navisarthi.navigation.ui.settings.SelectableRow
import com.sxdpz23.navisarthi.navigation.ui.settings.SettingsGroup
import com.sxdpz23.navisarthi.navigation.ui.settings.SettingsScaffold
import com.sxdpz23.navisarthi.navigation.ui.settings.ToggleRow
import com.sxdpz23.navisarthi.navigation.ui.dpadHighlight
import com.sxdpz23.navisarthi.navigation.ui.map.NavFraming
import com.sxdpz23.navisarthi.navigation.ui.dpadRowSibling
import androidx.compose.foundation.shape.RoundedCornerShape as DpadShape

/** Navigation sub-screen: guidance toggles, vibrate chips, cameras, live re-checks. Parking
 *  history moved to Saved places and the demo modes to Diagnostics (2026-09-17). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun NavigationSettingsScreen(vm: MapViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("navigation_settings", android.content.Context.MODE_PRIVATE) }
    SettingsScaffold(stringResource(R.string.settings_navigation), onBack) { topRow ->
        Spacer(Modifier.height(4.dp))
        var keepAwake by remember { mutableStateOf(prefs.getBoolean("keep_screen_on_nav", true)) }
        SettingsGroup {
        ToggleRow(
            label = stringResource(R.string.settings_keep_screen_on),
            checked = keepAwake,
            onCheckedChange = {
                keepAwake = it
                prefs.edit().putBoolean("keep_screen_on_nav", it).apply()
            },
            hint = stringResource(R.string.settings_keep_screen_on_hint),
            // The top focusable control: Back routes its DOWN here, UP from here goes back to Back.
            switchModifier = topRow,
        )

        // Which route chooser Directions opens. The Google-style picker graduated from an
        // experiment to the default on 2026-09-18; the classic panel stays one toggle away.
        GroupDivider()
        ToggleRow(
            label = stringResource(R.string.settings_route_picker),
            checked = com.sxdpz23.navisarthi.navigation.ui.RoutePicker.googleStyle.value,
            onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.RoutePicker.set(context, it) },
            hint = stringResource(R.string.settings_route_picker_hint),
        )

        // Route bar (issue #228). Off by default: it is extra chrome on the nav screen, and the
        // congestion color already on the route line covers some of the same ground.
        var routeBar by remember { mutableStateOf(prefs.getBoolean("route_bar", false)) }
        GroupDivider()
        // Arrows on the route at the turns ahead (issue #804), on by default.
        ToggleRow(
            label = stringResource(R.string.settings_turn_arrows),
            checked = com.sxdpz23.navisarthi.navigation.ui.TurnArrows.on.value,
            onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.TurnArrows.set(context, it) },
            hint = stringResource(R.string.settings_turn_arrows_hint),
        )
        GroupDivider()
        ToggleRow(
            label = stringResource(R.string.settings_route_bar),
            checked = routeBar,
            onCheckedChange = { routeBar = it; vm.setRouteBar(it) },
            hint = stringResource(R.string.settings_route_bar_hint),
        )
        GroupDivider()
        ToggleRow(
            label = stringResource(R.string.settings_nav_north_up),
            checked = com.sxdpz23.navisarthi.navigation.ui.NavNorthUp.on.value,
            onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.NavNorthUp.set(context, it) },
            hint = stringResource(R.string.settings_nav_north_up_hint),
        )
        // Zoom to the next turn (issue #781): heading-up only, the camera pulls out until the next
        // maneuver sits under the turn card (NavFraming, SPEC 4.7).
        GroupDivider()
        ToggleRow(
            label = stringResource(R.string.settings_zoom_to_turn),
            checked = com.sxdpz23.navisarthi.navigation.ui.ZoomToTurn.on.value,
            onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.ZoomToTurn.set(context, it) },
            hint = stringResource(R.string.settings_zoom_to_turn_hint),
        )
        // How far out it may go (issue #781): three floors as chips, the pick kept on its own.
        if (com.sxdpz23.navisarthi.navigation.ui.ZoomToTurn.on.value) {
            androidx.compose.foundation.layout.Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.settings_zoom_to_turn_floor), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    val floorFocus = remember { List(3) { FocusRequester() } }
                    val picked = com.sxdpz23.navisarthi.navigation.ui.ZoomToTurn.floor.value.takeIf { it > 0.0 } ?: NavFraming.ZOOM_TURN_MIN
                    listOf(
                        R.string.settings_zoom_to_turn_floor_town,
                        R.string.settings_zoom_to_turn_floor_region,
                        R.string.settings_zoom_to_turn_floor_far,
                    ).forEachIndexed { i, label ->
                        val value = com.sxdpz23.navisarthi.navigation.ui.ZoomToTurn.FLOORS[i]
                        FilterChip(
                            selected = picked == value,
                            onClick = { com.sxdpz23.navisarthi.navigation.ui.ZoomToTurn.setFloor(context, value) },
                            label = { Text(stringResource(label)) },
                            shape = CircleShape,
                            modifier = Modifier.dpadHighlight(CircleShape).dpadRowSibling(floorFocus, i),
                        )
                    }
                }
                Hint(stringResource(R.string.settings_zoom_to_turn_floor_hint))
            }
        }
        // Road numbers first (issue #836): a numbered road goes by its number on the pill, the car
        // screen and the directions, instead of a dedication name no sign shows.
        GroupDivider()
        ToggleRow(
            label = stringResource(R.string.settings_road_numbers),
            checked = com.sxdpz23.navisarthi.navigation.ui.RoadNumbers.on.value,
            onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.RoadNumbers.set(context, it) },
            hint = stringResource(R.string.settings_road_numbers_hint),
        )
        GroupDivider()
        ToggleRow(
            label = stringResource(R.string.settings_route_trail),
            checked = com.sxdpz23.navisarthi.navigation.ui.RouteTrail.on.value,
            onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.RouteTrail.set(context, it) },
            hint = stringResource(R.string.settings_route_trail_hint),
        )

        // Bike routing preference (issue #401): safety over speed, on by default.
        GroupDivider()
        ToggleRow(
            label = stringResource(R.string.settings_bike_safe),
            checked = com.sxdpz23.navisarthi.navigation.ui.BikeSafe.on.value,
            onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.BikeSafe.set(context, it) },
            hint = stringResource(R.string.settings_bike_safe_hint),
        )
        GroupDivider()
        ToggleRow(
            label = stringResource(R.string.settings_prefer_buttons),
            checked = com.sxdpz23.navisarthi.navigation.ui.PreferButtons.on.value,
            onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.PreferButtons.set(context, it) },
            hint = stringResource(R.string.settings_prefer_buttons_hint),
        )
        GroupDivider()
        ZoomKeyRows()
        GroupDivider()
        ToggleRow(
            label = stringResource(R.string.settings_pip_turn_card),
            checked = com.sxdpz23.navisarthi.navigation.ui.PipTurnCard.on.value,
            onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.PipTurnCard.set(context, it) },
            hint = stringResource(R.string.settings_pip_turn_card_hint),
        )
        GroupDivider()
        ToggleRow(
            label = stringResource(R.string.settings_faster_auto),
            checked = com.sxdpz23.navisarthi.navigation.ui.FasterRouteAuto.accept.value,
            onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.FasterRouteAuto.set(context, it) },
            hint = stringResource(R.string.settings_faster_auto_hint),
        )
        GroupDivider()
        ToggleRow(
            label = stringResource(R.string.settings_pause_in_bar),
            checked = com.sxdpz23.navisarthi.navigation.ui.PauseInBar.on.value,
            onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.PauseInBar.set(context, it) },
            // With Prefer buttons on the bar carries the step-list button as well, so say so.
            hint = stringResource(
                if (com.sxdpz23.navisarthi.navigation.ui.PreferButtons.on.value) R.string.settings_pause_in_bar_hint_buttons
                else R.string.settings_pause_in_bar_hint
            ),
        )
        }
        Spacer(Modifier.height(12.dp))
        SettingsGroup {
        Text(
            stringResource(R.string.settings_road_label),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
        )
        listOf(
            com.sxdpz23.navisarthi.navigation.ui.RoadLabel.BAR to stringResource(R.string.settings_road_label_bar),
            com.sxdpz23.navisarthi.navigation.ui.RoadLabel.IN_BAR to stringResource(R.string.settings_road_label_inbar),
            com.sxdpz23.navisarthi.navigation.ui.RoadLabel.PUCK to stringResource(R.string.settings_road_label_puck),
            com.sxdpz23.navisarthi.navigation.ui.RoadLabel.OFF to stringResource(R.string.settings_road_label_off),
        ).forEach { (id, label) ->
            SelectableRow(
                label = label,
                selected = com.sxdpz23.navisarthi.navigation.ui.RoadLabel.mode.value == id,
                onClick = { com.sxdpz23.navisarthi.navigation.ui.RoadLabel.set(context, id) },
            )
        }
        Hint(stringResource(R.string.settings_road_label_hint))

        // Arrow size + colors (issue #344): bigger targets for aging eyes, and a white disc so
        // the puck does not blend into the blue route line.
        GroupDivider()
        Text(
            stringResource(R.string.settings_puck_size),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.settingsAnchor(stringResource(R.string.settings_puck_size)).padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
        )
        listOf(
            com.sxdpz23.navisarthi.navigation.ui.PuckStyle.SIZE_NORMAL to stringResource(R.string.settings_puck_size_normal),
            com.sxdpz23.navisarthi.navigation.ui.PuckStyle.SIZE_LARGE to stringResource(R.string.settings_puck_size_large),
            com.sxdpz23.navisarthi.navigation.ui.PuckStyle.SIZE_XL to stringResource(R.string.settings_puck_size_xl),
        ).forEach { (id, label) ->
            SelectableRow(
                label = label,
                selected = com.sxdpz23.navisarthi.navigation.ui.PuckStyle.size.value == id,
                onClick = { com.sxdpz23.navisarthi.navigation.ui.PuckStyle.setSize(context, id) },
            )
        }
        if (com.sxdpz23.navisarthi.navigation.ui.PuckStyle.shape.value == com.sxdpz23.navisarthi.navigation.ui.PuckStyle.SHAPE_ARROW) {
        GroupDivider()
        Text(
            stringResource(R.string.settings_puck_style),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.settingsAnchor(stringResource(R.string.settings_puck_style)).padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
        )
        listOf(
            com.sxdpz23.navisarthi.navigation.ui.PuckStyle.STYLE_BLUE to stringResource(R.string.settings_puck_style_blue),
            com.sxdpz23.navisarthi.navigation.ui.PuckStyle.STYLE_WHITE to stringResource(R.string.settings_puck_style_white),
        ).forEach { (id, label) ->
            SelectableRow(
                label = label,
                selected = com.sxdpz23.navisarthi.navigation.ui.PuckStyle.style.value == id,
                onClick = { com.sxdpz23.navisarthi.navigation.ui.PuckStyle.setStyle(context, id) },
            )
        }
        }

        // The nav icon (discussion #611): the arrow above, or a car in one of a few colors.
        GroupDivider()
        Text(
            stringResource(R.string.settings_puck_shape),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
        )
        listOf(
            com.sxdpz23.navisarthi.navigation.ui.PuckStyle.SHAPE_ARROW to stringResource(R.string.settings_puck_shape_arrow),
            com.sxdpz23.navisarthi.navigation.ui.PuckStyle.SHAPE_CAR to stringResource(R.string.settings_puck_shape_car),
            com.sxdpz23.navisarthi.navigation.ui.PuckStyle.SHAPE_UFO to stringResource(R.string.settings_puck_shape_ufo),
            com.sxdpz23.navisarthi.navigation.ui.PuckStyle.SHAPE_SHIP to stringResource(R.string.settings_puck_shape_ship),
            com.sxdpz23.navisarthi.navigation.ui.PuckStyle.SHAPE_DUCK to stringResource(R.string.settings_puck_shape_duck),
        ).forEach { (id, label) ->
            SelectableRow(
                label = label,
                selected = com.sxdpz23.navisarthi.navigation.ui.PuckStyle.shape.value == id,
                onClick = { com.sxdpz23.navisarthi.navigation.ui.PuckStyle.setShape(context, id) },
            )
        }
        // What the drive will show, drawn by the same code.
        val puckKey = com.sxdpz23.navisarthi.navigation.ui.PuckStyle.key()
        val puckPreview = androidx.compose.runtime.remember(puckKey) { run {
            // The alternatives preview as the 3D model they drive as; the arrow stays its flat self.
            val mesh = com.sxdpz23.navisarthi.navigation.ui.map.PuckModels.forShape(com.sxdpz23.navisarthi.navigation.ui.PuckStyle.shape.value, com.sxdpz23.navisarthi.navigation.ui.PuckStyle.carColor.value)
            (if (mesh != null) com.sxdpz23.navisarthi.navigation.ui.map.puck3DPreviewBitmap(mesh, 202) else com.sxdpz23.navisarthi.navigation.ui.map.navPuckBitmap()).asImageBitmap()
        } }
        androidx.compose.foundation.Image(
            puckPreview, contentDescription = null,
            modifier = Modifier.padding(start = 20.dp, top = 4.dp).size(64.dp),
        )
        if (com.sxdpz23.navisarthi.navigation.ui.PuckStyle.isCar()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "red" to R.string.settings_car_red, "blue" to R.string.settings_car_blue,
                    "white" to R.string.settings_car_white, "green" to R.string.settings_car_green,
                    "yellow" to R.string.settings_car_yellow,
                ).forEach { (id, label) ->
                    FilterChip(
                        selected = com.sxdpz23.navisarthi.navigation.ui.PuckStyle.carColor.value == id,
                        onClick = { com.sxdpz23.navisarthi.navigation.ui.PuckStyle.setCarColor(context, id) },
                        label = { Text(stringResource(label)) },
                        shape = CircleShape,
                        modifier = Modifier.dpadHighlight(CircleShape),
                    )
                }
            }
        }

        var trafficLights by remember { mutableStateOf(prefs.getBoolean("nav_traffic_lights", false)) }
        GroupDivider()
        ToggleRow(
            label = stringResource(R.string.settings_nav_tap_places),
            checked = com.sxdpz23.navisarthi.navigation.ui.MapPoiPrefs.navTapPlaces.value,
            onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.MapPoiPrefs.setNavTapPlaces(context, it) },
            hint = stringResource(R.string.settings_nav_tap_places_hint),
        )
        GroupDivider()
        ToggleRow(
            label = stringResource(R.string.settings_traffic_lights),
            checked = trafficLights,
            onCheckedChange = {
                trafficLights = it
                prefs.edit().putBoolean("nav_traffic_lights", it).apply()
            },
            hint = stringResource(R.string.settings_traffic_lights_hint),
        )
        // Ask before ending a drive (issue #624), off by default.
        GroupDivider()
        ToggleRow(
            label = stringResource(R.string.settings_confirm_end_nav),
            checked = com.sxdpz23.navisarthi.navigation.ui.NavEndConfirm.on.value,
            onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.NavEndConfirm.set(context, it) },
            hint = stringResource(R.string.settings_confirm_end_nav_hint),
        )
        // The speed + limit badge, phone and car (issue #625). On by default; a dash that shows
        // both already makes it clutter.
        GroupDivider()
        ToggleRow(
            label = stringResource(R.string.settings_speed_display),
            checked = com.sxdpz23.navisarthi.navigation.ui.SpeedDisplay.on.value,
            onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.SpeedDisplay.set(context, it) },
            hint = stringResource(R.string.settings_speed_display_hint),
        )
        // The car's own badge, nested: off by default since the car has a speedometer.
        if (com.sxdpz23.navisarthi.navigation.ui.SpeedDisplay.on.value) {
            ToggleRow(
                label = stringResource(R.string.settings_car_speed),
                checked = com.sxdpz23.navisarthi.navigation.ui.CarSpeed.on.value,
                onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.CarSpeed.set(context, it) },
                hint = stringResource(R.string.settings_car_speed_hint),
            )
        }
        // Over-the-limit voice alert (issue #404): its own opt-in, off by default. Sits with the
        // other spoken extras; the timing is in :core SpeedingAlerts.
        GroupDivider()
        ToggleRow(
            label = stringResource(R.string.settings_speeding_alert),
            checked = com.sxdpz23.navisarthi.navigation.ui.SpeedingAlert.on.value,
            onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.SpeedingAlert.set(context, it) },
            hint = stringResource(R.string.settings_speeding_alert_hint),
        )
        }

        SettingsGroup {
        androidx.compose.foundation.layout.Column(Modifier.padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.settings_vibrate_on_turns), style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, modifier = Modifier.padding(top = 2.dp))
        // One chip per travel mode (was four stacked switch rows - a lot of vertical space
        // for a setting most people touch once). Selected = that mode vibrates at turns.
        // FlowRow, not a scrollable Row: on a narrow screen / low density the fourth chip
        // rendered partially cut with no hint that the row scrolls (user report, 2026-07-16) -
        // wrapping onto a second line keeps every chip fully visible instead.
        androidx.compose.foundation.layout.FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // The ROOT swallows bare LEFT/RIGHT (SettingsScaffold), so this horizontal row drives
            // its OWN LEFT/RIGHT via FocusRequesters - requestFocus (not moveFocus) never clears at
            // the ends, and consuming the key stops it reaching the root swallow.
            val chipFocus = remember { List(4) { FocusRequester() } }
            listOf(
                TravelMode.DRIVE to stringResource(R.string.settings_mode_driving),
                TravelMode.WALK to stringResource(R.string.settings_mode_walking),
                TravelMode.BICYCLE to stringResource(R.string.settings_mode_cycling),
                TravelMode.TRANSIT to stringResource(R.string.settings_mode_transit),
            ).forEachIndexed { i, (mode, label) ->
                var on by remember(mode) {
                    val default = if (!prefs.getBoolean(Haptics.KEY, true)) false else Haptics.defaultFor(mode)
                    mutableStateOf(prefs.getBoolean(Haptics.keyFor(mode), default))
                }
                FilterChip(
                    selected = on,
                    onClick = {
                        on = !on
                        prefs.edit().putBoolean(Haptics.keyFor(mode), on).apply()
                    },
                    label = { Text(label) },
                    shape = androidx.compose.foundation.shape.CircleShape,
                    modifier = Modifier
                        .dpadHighlight(androidx.compose.foundation.shape.CircleShape)
                        .focusRequester(chipFocus[i])
                        .onKeyEvent { ev ->
                            if (ev.key == Key.DirectionRight || ev.key == Key.DirectionLeft) {
                                if (ev.type == KeyEventType.KeyDown) {
                                    if (ev.key == Key.DirectionRight && i < chipFocus.lastIndex) chipFocus[i + 1].requestFocus()
                                    if (ev.key == Key.DirectionLeft && i > 0) chipFocus[i - 1].requestFocus()
                                }
                                true
                            } else {
                                false
                            }
                        },
                )
            }
        }
        Hint(stringResource(R.string.settings_vibrate_hint))
        }
        }

        Spacer(Modifier.height(8.dp))
        // What a location link from another app does (discussion #640).
        SettingsGroup(title = stringResource(R.string.settings_link_action)) {
            listOf(
                com.sxdpz23.navisarthi.navigation.ui.LinkAction.PLACE to R.string.settings_link_action_place,
                com.sxdpz23.navisarthi.navigation.ui.LinkAction.DIRECTIONS to R.string.settings_link_action_directions,
                com.sxdpz23.navisarthi.navigation.ui.LinkAction.START to R.string.settings_link_action_start,
            ).forEachIndexed { i, (key, label) ->
                if (i > 0) GroupDivider()
                SelectableRow(
                    label = stringResource(label),
                    selected = com.sxdpz23.navisarthi.navigation.ui.LinkAction.mode.value == key,
                    onClick = { com.sxdpz23.navisarthi.navigation.ui.LinkAction.set(context, key) },
                )
            }
        }
        Hint(stringResource(R.string.settings_link_action_hint))
        Spacer(Modifier.height(8.dp))
        CameraSettingsGroup()
        Spacer(Modifier.height(8.dp))
        SettingsGroup(title = stringResource(R.string.settings_road_alerts_group)) {
            ToggleRow(
                label = stringResource(R.string.settings_bridge_openings),
                checked = com.sxdpz23.navisarthi.navigation.ui.BridgeAlert.on.value,
                onCheckedChange = { com.sxdpz23.navisarthi.navigation.ui.BridgeAlert.set(context, it) },
                hint = stringResource(R.string.settings_bridge_openings_hint),
            )
        }
        Spacer(Modifier.height(8.dp))
        LiveRechecksGroup(vm)
        Spacer(Modifier.height(24.dp))
    }
}
