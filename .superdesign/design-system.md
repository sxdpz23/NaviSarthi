# NaviSarthi native UI system

Approved character: steady, legible, purposeful. Product world: motorcycle instruments and road maps. Native Android Compose / Material 3. Keep the existing logo and selected vehicle identity.

## Color

Dark ramp: Bg0 #0A0B0C, Bg1 #111315, Surf1 #181A1D, Surf2 #202326, Surf3 #2A2E32.
Text: primary #F3F1EC, secondary #9EA0A3, readable metadata #91969C; disabled #72777D is a separate state.
Hairlines: white at 7–12% opacity. Default accent follows VehicleModel; preserve explicit wallpaper colors, light/dark/AMOLED and semantic map/traffic/error colors.
Metadata contrast across the dark ramp is 4.59:1 or higher.

## Type and spacing

Geist interface, Geist Mono instrument figures. Shared CraftTypography: 12/14/16 body and labels; 22/24/28 titles; 28/32/40 figures. Normal small-text tracking, 400/500 weights and occasional 600 emphasis. Preserve custom, system and bundled font choices.
Spacing: 4/8/12/16/20/24/32dp; 2dp optical nudges are valid. Phone page edges 20dp; cards 20dp, dense cards 16dp. Interactive minimum 48dp. Header 24sp; supporting text 14sp/20sp. Content grows and scrolls rather than clipping large text.
Controls radius 10dp; cards 16dp; panels 20dp. Segmented inner radius 8dp and outer 12dp with 4dp padding.

## Material, icons and motion

Material icons with aligned 20dp control glyphs; 24dp map controls where appropriate. Directional keyboard-arrow icons mirror for RTL. Keep the custom helmet-intercom mark and physical maneuver/road glyphs.
The explicitly approved glass bar is the material exception: keep its refraction, blur, rim, selection, compact layout and spring. Do not spread glass to cards or settings.
Use real content: routes, maps, console previews, maintenance/fuel/ride figures and place photos. No decorative generated imagery. No breathing connection dots or expanding decorative rings. Keep real loading/rerouting feedback, reduced-motion preferences and gesture-driven sheets.

## Source of truth

- app/src/main/java/com/sxdpz23/navisarthi/ui/theme/Color.kt
- app/src/main/java/com/sxdpz23/navisarthi/ui/theme/Type.kt
- app/src/main/java/com/sxdpz23/navisarthi/ui/theme/CraftShapes.kt
- app/src/main/java/com/sxdpz23/navisarthi/ui/components/NaviSarthiComponents.kt
- app/src/main/java/com/sxdpz23/navisarthi/navigation/ui/theme/Theme.kt
- docs/unslop-app-report.md and docs/unslop-screen-inventory.md

Existing canvas draft remains the approved 0.6.0 shell reference; it has not been regenerated for this native polish pass.
Method credit: Unslop by Damir Sokolovsky (@heydamir), https://heydamir.design/unslop, CC BY 4.0.
