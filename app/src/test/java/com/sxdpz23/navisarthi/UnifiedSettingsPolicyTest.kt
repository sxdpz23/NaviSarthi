package com.sxdpz23.navisarthi

import com.sxdpz23.navisarthi.navigation.ui.UnifiedSettingsPolicy
import org.junit.Assert.*
import org.junit.Test

class UnifiedSettingsPolicyTest {
    @Test fun explicitUserChoicesAreNotOverwrittenByNewDefaults() {
        val existing = setOf("ui_scale", "turn_arrows_on", "map_places_source", "speed_cameras_on")
        val changes = UnifiedSettingsPolicy.missingDefaults(existing)
        assertTrue(existing.none { it in changes })
        assertEquals(true, changes["reviews_on_tap"])
    }
    @Test fun legacyRoutePickerChoiceIsNotReplacedByDefault() {
        assertFalse("route_picker_google" in UnifiedSettingsPolicy.missingDefaults(setOf("exp_google_chooser")))
    }
    @Test fun unspecifiedDefaultsAndPersonalDataAreNotSeeded() {
        val changes = UnifiedSettingsPolicy.missingDefaults(emptySet())
        for (key in listOf("spoken_directions", "theme_nav_day_night", "units_imperial", "clock_24", "nav_north_up", "texture_render", "home", "work"))
            assertFalse(key, key in changes)
    }
    @Test fun requestedFreshInstallDefaultsAreApplied() {
        val changes = UnifiedSettingsPolicy.missingDefaults(emptySet())
        assertEquals(.9f, changes["ui_scale"])
        assertEquals("both", changes["map_places_source"])
        assertEquals("near", changes["housenumber_zoom"])
        assertEquals("english_local", changes["map_names"])
        assertEquals("BUTTON", changes["category_chips"])
        assertEquals(false, changes["turn_arrows_on"])
        assertEquals(true, changes["route_bar"])
        assertEquals(false, changes["live_reviews_panel"])
        assertEquals(true, changes["speed_cameras_on"])
        assertEquals(false, changes["haptics_motorbike"])
    }
}
