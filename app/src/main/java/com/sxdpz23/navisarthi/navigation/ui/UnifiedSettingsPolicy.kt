package com.sxdpz23.navisarthi.navigation.ui

import android.content.Context

/** Only absent configurable keys get new defaults; removed choices are fixed on every launch. */
internal object UnifiedSettingsPolicy {
    val booleanDefaults = mapOf(
        "layers_button_on" to true, "parking_button_on" to true, "two_finger_tilt" to true,
        "keep_north_up" to false, "buildings_3d" to true, "building_overlay_on" to true,
        "map_show_pois" to true, "map_places_google_lookup" to true, "map_show_civic_pois" to true,
        "map_show_transit_stops" to true, "show_reviews" to true, "reviews_on_tap" to true,
        "load_photos" to true, "photos_on_tap" to true, "hide_adult" to false, "hide_external_links" to true,
        "route_picker_google" to true, "turn_arrows_on" to false, "bike_safe" to false,
        "prefer_buttons" to false, "pip_turn_card" to true, "faster_route_auto" to true,
        "confirm_end_nav" to false, "speed_display" to true, "speeding_alert" to false,
        "flock_cameras_on" to true, "speed_cameras_on" to true, "bridge_openings" to true,
        "google_free" to false, "route_traffic_on_tap" to false, "block_google_telemetry" to false,
        "speech_preload" to true, "place_full_load" to false, "turn_declutter" to true, "live_reviews_panel" to false, "details_retry" to true,
        "transit_stops_bus" to true, "transit_stops_metro" to true, "transit_stops_train" to true,
        "route_bar" to true, "nav_live_rechecks" to true, "haptics_drive" to false, "haptics_motorbike" to false,
    )
    val stringDefaults = mapOf("housenumber_zoom" to "near", "map_names" to "english_local",
        "map_places_source" to "both", "puck_size" to "normal", "road_label" to "puck", "category_chips" to "BUTTON", "link_action" to "place")

    fun missingDefaults(existing: Set<String>): Map<String, Any> = buildMap {
        booleanDefaults.forEach { (key,value) -> if (key !in existing && !(key == "route_picker_google" && "exp_google_chooser" in existing)) put(key,value) }
        stringDefaults.forEach { (key,value) -> if (key !in existing) put(key,value) }
        if ("ui_scale" !in existing) put("ui_scale", .9f)
    }

    fun apply(context: Context) {
        val prefs = context.getSharedPreferences("navigation_settings", Context.MODE_PRIVATE)
        val edit = prefs.edit()
        missingDefaults(prefs.all.keys).forEach { (key,value) -> when (value) {
            is Boolean -> edit.putBoolean(key,value)
            is String -> edit.putString(key,value)
            is Float -> edit.putFloat(key,value)
        } }
        if (!prefs.contains("route_picker_google") && prefs.contains("exp_google_chooser"))
            edit.putBoolean("route_picker_google", prefs.getBoolean("exp_google_chooser", true))
        edit.putString("theme_mode", "AMOLED").putString("theme_map_mode", "FOLLOW")
            .putString("puck_style", "blue").putBoolean("page_transitions", true)
            .putBoolean("dynamic_color", false).putBoolean("keep_screen_on_nav", false).apply()
        if (AppNameAlias.isGeneric(context)) AppNameAlias.setGeneric(context, false)
        val legacyUnits = context.getSharedPreferences("guidance", Context.MODE_PRIVATE)
        val units = context.getSharedPreferences("navigation_units", Context.MODE_PRIVATE)
        if (!units.contains("imperial") && legacyUnits.contains("units"))
            units.edit().putBoolean("imperial", legacyUnits.getString("units", "Kilometres") == "Miles").apply()
        val accessories = context.getSharedPreferences("accessory_battery", Context.MODE_PRIVATE)
        if (!accessories.contains("icons_only")) accessories.edit().putBoolean("icons_only", true).apply()
    }
}
