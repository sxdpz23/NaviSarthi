package com.example.navisarthi.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

// ---- Surfaces (warm-neutral near-black) ----
val Bg0    = Color(0xFF0A0B0C)
val Bg1    = Color(0xFF111315)
val Surf1  = Color(0xFF181A1D)
val Surf2  = Color(0xFF202326)
val Surf3  = Color(0xFF2A2E32)

// ---- Hairlines ----
val Line   = Color(0x12FFFFFF)
val Line2  = Color(0x1FFFFFFF)
val Line3  = Color(0x33FFFFFF)

// ---- Text ----
val TextHi  = Color(0xFFF3F1EC)
val TextMid = Color(0xFF9EA0A3)
val TextLo  = Color(0xFF65686B)
val TextDis = Color(0xFF44474A)

// Accent tokens follow the vehicle selected by the rider.
val Gold: Color @Composable @ReadOnlyComposable get() = LocalVehicle.current.accent
val GoldBright: Color @Composable @ReadOnlyComposable get() = LocalVehicle.current.bright
val GoldDeep: Color @Composable @ReadOnlyComposable get() = LocalVehicle.current.deep
val GoldTint: Color @Composable @ReadOnlyComposable get() = Gold.copy(alpha = 36 / 255f)
val GoldTint2: Color @Composable @ReadOnlyComposable get() = Gold.copy(alpha = 61 / 255f)
val GoldGlow: Color @Composable @ReadOnlyComposable get() = Gold.copy(alpha = 89 / 255f)
val OnGold: Color @Composable @ReadOnlyComposable get() = LocalVehicle.current.onAccent

// ---- Status ----
val Warn    = Color(0xFFD8853E)
val Alert   = Color(0xFFD45C42)
val Offline = Color(0xFF65686B)

// ---- Map ----
val MapBase = Color(0xFF0E1416)
val MapBase2 = Color(0xFF0C1413)
