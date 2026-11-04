package com.example.navisarthi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

private fun colorScheme(vehicle: VehicleModel) = darkColorScheme(
    primary            = vehicle.accent,
    onPrimary          = vehicle.onAccent,
    primaryContainer   = vehicle.accent.copy(alpha = 36 / 255f),
    onPrimaryContainer = vehicle.accent,
    secondary          = TextMid,
    onSecondary        = Bg1,
    background         = Bg1,
    onBackground       = TextHi,
    surface            = Surf1,
    onSurface          = TextHi,
    surfaceVariant     = Surf2,
    onSurfaceVariant   = TextMid,
    outline            = Line2,
    error              = Alert,
    onError            = TextHi,
    scrim              = Bg0,
)

@Composable
fun NaviSarthiTheme(vehicle: VehicleModel = VehicleModel.Himalayan450, content: @Composable () -> Unit) {
    val scheme = remember(vehicle) { colorScheme(vehicle) }
    val typography = remember(vehicle) { vehicleTypography(vehicle.accent) }
    CompositionLocalProvider(LocalVehicle provides vehicle) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
