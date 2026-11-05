# Tokens: background #0A0B0C, elevated #111315/#181A1D/#202326. Text #F3F1EC/#9EA0A3/#65686B. Himalayan gold #E9B949, Guerrilla blue #65ADFF, Bear mint #48D2AC. Geist text, Geist Mono metrics. Spacing mostly 4/8/12/16/18/20/24 dp, cards rounded. Native Android Compose, Material 3.

## app\src\main\java\com\example\navisarthi\ui\theme\Color.kt
~~~kotlin
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

~~~


## app\src\main\java\com\example\navisarthi\ui\theme\Theme.kt
~~~kotlin
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

~~~


## app\src\main\java\com\example\navisarthi\ui\theme\Type.kt
~~~kotlin
package com.example.navisarthi.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.example.navisarthi.R

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

val GeistFamily = FontFamily(
    Font(googleFont = GoogleFont("Geist"), fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("Geist"), fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = GoogleFont("Geist"), fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = GoogleFont("Geist"), fontProvider = provider, weight = FontWeight.Bold),
)

val GeistMonoFamily = FontFamily(
    Font(googleFont = GoogleFont("Geist Mono"), fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("Geist Mono"), fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = GoogleFont("Geist Mono"), fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = GoogleFont("Geist Mono"), fontProvider = provider, weight = FontWeight.Bold),
)

internal fun vehicleTypography(accent: androidx.compose.ui.graphics.Color) = Typography(
    displayLarge = TextStyle(
        fontFamily = GeistMonoFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 38.sp,
        color = accent,
    ),
    headlineLarge = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = (-0.48).sp,
        color = TextHi,
    ),
    headlineMedium = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        letterSpacing = (-0.38).sp,
        color = TextHi,
    ),
    titleLarge = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 23.sp,
        letterSpacing = (-0.46).sp,
        color = TextHi,
    ),
    titleMedium = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = (-0.16).sp,
        color = TextHi,
    ),
    bodyLarge = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        color = TextMid,
    ),
    bodyMedium = TextStyle(
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        color = TextMid,
    ),
    labelSmall = TextStyle(
        fontFamily = GeistMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 0.16.sp,
        color = TextLo,
    ),
)

~~~


## app\src\main\java\com\example\navisarthi\ui\theme\VehicleModel.kt
~~~kotlin
package com.example.navisarthi.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class VehicleModel(
    val label: String,
    val accent: Color,
    val bright: Color,
    val deep: Color,
    val onAccent: Color,
) {
    Himalayan450("Himalayan 450", Color(0xFFE9B949), Color(0xFFFFCD5E), Color(0xFFC99A30), Color(0xFF1A1402)),
    Guerrilla450("Guerrilla 450", Color(0xFF65ADFF), Color(0xFF9ACEFF), Color(0xFF3886D6), Color(0xFF041426)),
    Bear650("Bear 650", Color(0xFF48D2AC), Color(0xFF7CE7C9), Color(0xFF249F83), Color(0xFF031B14)),
    Himalayan750("Himalayan 750", Color(0xFFFF793E), Color(0xFFFFA374), Color(0xFFCE501E), Color(0xFF250C03)),
}

internal val LocalVehicle = staticCompositionLocalOf { VehicleModel.Himalayan450 }

~~~

