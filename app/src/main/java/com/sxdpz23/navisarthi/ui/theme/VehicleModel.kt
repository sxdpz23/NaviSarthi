package com.sxdpz23.navisarthi.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.sxdpz23.navisarthi.console.ConsoleType

enum class VehicleModel(
    val label: String,
    val accent: Color,
    val bright: Color,
    val deep: Color,
    val onAccent: Color,
    val consoleType: ConsoleType = ConsoleType.TRIPPER_DASH,
) {
    Meteor350("Meteor 350", Color(0xFFE7A0B0), Color(0xFFFFC0CE), Color(0xFFAE667A), Color(0xFF241016), ConsoleType.TRIPPER_POD),
    Himalayan450("Himalayan 450", Color(0xFFE9B949), Color(0xFFFFCD5E), Color(0xFFC99A30), Color(0xFF1A1402)),
    Guerrilla450("Guerrilla 450", Color(0xFF65ADFF), Color(0xFF9ACEFF), Color(0xFF3886D6), Color(0xFF041426)),
    Bear650("Bear 650", Color(0xFF48D2AC), Color(0xFF7CE7C9), Color(0xFF249F83), Color(0xFF031B14)),
    Himalayan750("Himalayan 750", Color(0xFFFF793E), Color(0xFFFFA374), Color(0xFFCE501E), Color(0xFF250C03)),
}

internal val LocalVehicle = staticCompositionLocalOf { VehicleModel.Himalayan450 }
