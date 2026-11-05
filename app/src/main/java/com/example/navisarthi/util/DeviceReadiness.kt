package com.example.navisarthi.util

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Android battery-exemption and background-settings guidance.
 * Exemption helps background operation but does not prove that manufacturer-specific
 * sleeping-app, autostart or background-activity restrictions are disabled.
 */
object DeviceReadiness {

    private const val PREFS = "navisarthi_readiness"
    private const val KEY_BATTERY_ASKED = "battery_exemption_asked"

    /** Standard Doze exemption only; manufacturer restrictions are not exposed by this check. */
    fun isIgnoringBatteryOptimizations(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    fun isBackgroundRestricted(ctx: Context): Boolean =
        ctx.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true

    /** Warn only while Android reports optimization or restricted background usage. */
    fun shouldShowNavigationBatteryGuidance(ctx: Context): Boolean =
        !isIgnoringBatteryOptimizations(ctx) || isBackgroundRestricted(ctx)

    /**
     * Whether we've already shown the rider the battery-exemption dialog once. We fold the request
     * into the first Connect tap and remember it here, so we never re-prompt on later connects even
     * if they declined (they can still grant it later from system settings).
     */
    fun batteryExemptionAsked(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_BATTERY_ASKED, false)

    fun markBatteryExemptionAsked(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_BATTERY_ASKED, true).apply()
    }

    /**
     * Standard system dialog to exempt the app from battery optimization — uniform across OEMs.
     * (Lint flags BatteryLife for Play Store apps; NaviSarthi is sideloaded per-rider, and the
     * exemption is essential for the screen-off use case, so the prompt is justified.)
     */
    @SuppressLint("BatteryLife")
    fun batteryExemptionIntent(ctx: Context): Intent =
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${ctx.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The app's system settings page — every OEM exposes its battery/autostart controls here. */
    fun appSettingsIntent(ctx: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${ctx.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Location services (the master toggle, not the permission) must be ON for WiFi scan results
     * to be returned on most OEMs — that's how a not-yet-paired dash SSID is discovered. Once the
     * dash is learned, scanning isn't needed, so this only matters on first pairing.
     */
    fun locationServicesEnabled(ctx: Context): Boolean = runCatching {
        (ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager).isLocationEnabled
    }.getOrDefault(true)
}
