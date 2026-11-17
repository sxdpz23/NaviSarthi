package com.sxdpz23.navisarthi.ui.screens

import androidx.compose.runtime.*
import com.sxdpz23.navisarthi.ui.components.VehiclePicker
import androidx.compose.ui.platform.LocalContext
import com.sxdpz23.navisarthi.navigation.ui.settings.*
import com.sxdpz23.navisarthi.ui.theme.*
import com.sxdpz23.navisarthi.viewmodel.*

@Composable
fun UnifiedSettingsScreen(navigation: com.sxdpz23.navisarthi.navigation.ui.map.MapViewModel,
    dash: DashViewModel, pod: com.sxdpz23.navisarthi.pod.PodViewModel, conn: ConnectionState,
    onConnChange: (ConnectionState) -> Unit, onVehicleSelected: (VehicleModel) -> Unit,
    garageEnabled: Boolean, onGarageEnabledChange: (Boolean) -> Unit, onBack: () -> Unit, onOpenRides: () -> Unit) {
    val vehicle = LocalVehicle.current
    val pages = AppSettingsPages(vehicle.consoleType,
        system = { back ->
            var picker by remember { mutableStateOf(false) }
            SettingsScaffold("System", back) { _ ->
                SettingsGroup {
                    SettingsActionRow("Vehicle type", vehicle.label, onClick = { picker = true })
                    ToggleRow("Enable Garage", garageEnabled, onGarageEnabledChange, hint = "Maintenance reminders, fuel diary and glovebox")
                }
            }
            if (picker) VehiclePicker(vehicle, onSelect = { onVehicleSelected(it); picker = false }, onDismiss = { picker = false })
        },
        dash = { back -> DashSettingsPage(conn, onConnChange, dash, pod, onVehicleSelected, garageEnabled, onGarageEnabledChange, back, onOpenRides = onOpenRides) },
        pod = { back -> PodSettingsScreen(pod, onVehicleSelected, garageEnabled, onGarageEnabledChange, back, onOpenRides = onOpenRides) },
        backup = { DriveBackupSettings() },
    )
    CompositionLocalProvider(LocalAppSettingsPages provides pages) { NavigationSettingsScreen(navigation, onBack) }
}
