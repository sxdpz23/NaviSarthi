package com.sxdpz23.navisarthi.navigation.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

// Containers are set to blue tints too - otherwise Material's defaults leave
// primaryContainer/secondaryContainer a stock purple, which made the map FABs and
// selected chips read "weirdly purple" against the brand.
private val LightColors = lightColorScheme(
    // Neutral near-whites like Google Maps (no teal cast): a full-white page is
    // harsh, and the container roles step down so bars/cards still read as layers.
    background = androidx.compose.ui.graphics.Color(0xFFF8F9FA),
    surface = androidx.compose.ui.graphics.Color(0xFFF8F9FA),
    surfaceContainerLowest = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFFF1F3F4),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFFF3F1EC),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFFDADCE0),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFFDADCE0),
    primary = NavigationTeal,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFB6E7DF),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF00201D),
    secondary = NavigationTealDark,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFCDE9E3),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF06201C),
    tertiary = NavigationAmber,
    // Attention surfaces (the faster-route offer, warn-level notices) ride tertiaryContainer.
    // Without these, Material's baseline kicked in and drew them PINK (user 2026-07-14) -
    // amber tints keep them on-brand with the NavigationAmber accent.
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFFDCBE),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFF2C1600),
)

private val DarkColors = darkColorScheme(
    // Neutral near-black surfaces under the teal accent.
    background = androidx.compose.ui.graphics.Color(0xFF111315),
    surface = androidx.compose.ui.graphics.Color(0xFF111315),
    surfaceContainerLowest = androidx.compose.ui.graphics.Color(0xFF111315),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFF111315),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFF181A1D),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFF202326),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFF2A2E32),
    onSurface = androidx.compose.ui.graphics.Color(0xFFF3F1EC),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF9EA0A3),
    outline = androidx.compose.ui.graphics.Color(0xFF5F6368),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF5F6368),
    primary = NavigationTealLight,
    onPrimary = androidx.compose.ui.graphics.Color(0xFF003730),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF13534B),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFB6E7DF),
    secondary = NavigationTeal,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF1F4A44),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFCDE9E3),
    tertiary = NavigationAmber,
    // See LightColors: baseline tertiaryContainer is pink; keep the attention cards amber.
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFF6B4520),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFFDCBE),
)

// AMOLED: the dark scheme on TRUE BLACK surfaces (every lit pixel costs battery on OLED, and
// pure black is its own look). Container roles step up in near-blacks so cards and the title bar
// still read as layers; the thin borders on Settings cards carry the structure.
private val AmoledColors = DarkColors.onTrueBlack()

/** A dark scheme on true black surfaces, its accents kept: Navigation's own, or the wallpaper's. */
private fun androidx.compose.material3.ColorScheme.onTrueBlack() = copy(
    background = androidx.compose.ui.graphics.Color(0xFF000000),
    surface = androidx.compose.ui.graphics.Color(0xFF000000),
    surfaceDim = androidx.compose.ui.graphics.Color(0xFF000000),
    surfaceContainerLowest = androidx.compose.ui.graphics.Color(0xFF000000),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFF060809),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFF0B0E0F),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFF121617),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFF191E1F),
)

/**
 * App theme. Navigation's explicit Google-style light/dark schemes by default; Material You dynamic
 * color (issue #15) when the user opts in via Settings -> Appearance ([DynamicColor]).
 *
 * The dynamic scheme is sanity-checked before use: on some ROMs (observed on GrapheneOS)
 * `dynamicDarkColorScheme` handed back a *light* background, which broke "Dark" for every
 * MaterialTheme surface (Settings etc.). If the scheme's background luminance contradicts
 * the requested theme, Navigation falls back to its own colors - the Light/Dark switch is the
 * contract and always wins. Accent legibility comes from using the scheme's PAIRED slots
 * everywhere (primary with onPrimary, container with onContainer), which the system
 * generates at accessible contrast in both themes.
 */
@Composable
fun NavigationTheme(
    darkTheme: Boolean = isAppInDarkTheme(),
    dynamicColor: Boolean = DynamicColor.on.value,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val dyn = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            val saneBackground = if (darkTheme) dyn.background.luminance() < 0.4f else dyn.background.luminance() > 0.6f
            val scheme = if (saneBackground) dyn else if (darkTheme) DarkColors else LightColors
            // AMOLED with the wallpaper's colors: its accents on the same true black surfaces.
            // The wallpaper's own dark surfaces are a tinted gray, which made the two settings
            // together look like plain Dark on every menu screen (issue 709).
            if (darkTheme && AppTheme.mode.value == ThemeMode.AMOLED) scheme.onTrueBlack() else scheme
        }
        // AMOLED is a flavor of DARK, so it must yield when something resolves the app to light -
        // the day/night-while-navigating override (issue #262) does exactly that, and without the
        // darkTheme guard a daylight drive got a black UI over a light map.
        AppTheme.mode.value == ThemeMode.AMOLED && darkTheme -> AmoledColors
        darkTheme -> DarkColors
        else -> LightColors
    }
    val family = if (com.sxdpz23.navisarthi.navigation.ui.AppFont.appDefault.value) com.sxdpz23.navisarthi.ui.theme.GeistFamily else com.sxdpz23.navisarthi.navigation.ui.AppFont.family.value
    val typography = androidx.compose.runtime.remember(family) { navigationTypography(family) }
    val vehicle = com.sxdpz23.navisarthi.ui.theme.LocalVehicle.current
    // Explicit wallpaper colors remain authoritative. Defaults use the selected motorcycle.
    val themed = if (dynamicColor) colorScheme else {
        val accent = if (darkTheme) vehicle.accent else androidx.compose.ui.graphics.lerp(vehicle.accent, androidx.compose.ui.graphics.Color.Black, .42f)
        colorScheme.copy(
            primary = accent,
            onPrimary = if (darkTheme) vehicle.onAccent else androidx.compose.ui.graphics.Color.White,
            primaryContainer = androidx.compose.ui.graphics.lerp(colorScheme.surface, accent, .14f),
            onPrimaryContainer = if (darkTheme) vehicle.accent else accent,
            secondary = colorScheme.onSurfaceVariant,
            secondaryContainer = colorScheme.surfaceContainerHigh,
            onSecondaryContainer = colorScheme.onSurface,
            outlineVariant = colorScheme.onSurface.copy(alpha = .10f),
        )
    }
    MaterialTheme(colorScheme = themed, typography = typography, shapes = com.sxdpz23.navisarthi.ui.theme.CraftShapes, content = content)
}

/** True only while the wallpaper's colors are really in use: the setting is on AND the phone's
 *  scheme passed the check above. Where it fell back to Navigation's own scheme, a "wallpaper colored"
 *  button would come out teal. */
@Composable
fun wallpaperColorsInUse(): Boolean =
    DynamicColor.on.value && MaterialTheme.colorScheme.primary.let { it != NavigationTeal && it != NavigationTealLight }
