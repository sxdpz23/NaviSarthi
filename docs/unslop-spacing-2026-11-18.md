# App-wide Unslop spacing pass — 2026-11-18

Polish mode, using the previously approved direction. Unslop by Damir Sokolovsky (https://heydamir.design/unslop), CC BY 4.0; original credit remains in app notices.

## Direction

- Product: motorcycle navigation and vehicle care for Royal Enfield riders.
- Character: steady, legible, purposeful.
- World: graphite motorcycle instruments and road maps; retain vehicle accents and pitch-black page backgrounds.
- Moments: the real Nav map/route and Dash/Pod display previews.
- Signature: the vehicle-accent journey stroke and meaningful vehicle-accent selections.

The user requested this polish and previously approved this direction. The approved glass surfaces, helmet icon, custom idle screen, Google Sans Flex, switch animation, feature set and section order are retained. The skill's generic preferences about removing glass or elastic motion do not override those explicit approvals.

## Audit findings

- Mixed 6/10/14/18 dp insets in cards, map overlays, forms and dialogs broke the common 4 dp rhythm.
- Unified Settings mixed 24 dp group headings with older page headings, loose action buttons and decorative permission rows.
- Settings-group inner padding, help text, radio rows and toggle rows used different vertical rhythms.
- Large settings app bars consumed disproportionate space on smaller displays.
- Settings home subtitles truncated useful descriptions to one line.
- Native group dividers used thick black bands instead of quiet separators.
- Wallpaper selection/crop controls were separate page children instead of one coherent group; actions could overflow at enlarged text.
- Pod, Groups and nested settings used 112 dp bottom clearance while other shell pages used 120 dp.
- Taskbar's 6 dp inner inset is coupled to selection-slot calculations: it is an intentional 2 dp optical step and is retained, not rounded independently.
- Map marker geometry, camera insets calculated in pixels, coordinate math and console protocol dimensions are not ordinary UI spacing and are excluded from mechanical normalization.

## Nine passes

1. Spacing: source audit across all discovered Compose screens/components and template sources. Standardized simple control padding and peer gaps in 35 rendering sources, shared 120 dp shell clearance, 16 dp setting-row insets, 64 dp minimum setting toggle/permission rows, 56 dp action rows and 48 dp interactive controls. Long rows grow with their content.
2. Colour: keep approved black/graphite surfaces and vehicle accents; use quiet inset dividers. No new palette or decorative gradients.
3. Icons: keep existing Material symbol set and helmet/intercom mark; action rows use the same trailing chevron, with accessible labels on switch controls.
4. Copy: retain feature descriptions and section order; supporting copy sits with its control, not in separate decorative cards. No invented claims or mock data.
5. Type: retain Google Sans Flex and the existing role scale; console permission hints now use the navigation settings' readable supporting-text style.
6. Layout: compact settings headers, grouped System/Ride-history actions, two-line hub descriptions and a grouped wallpaper editor with wrapping actions. Common edge and dock clearances are preserved.
7. Details: match group/card corner treatment; quiet inset separators; consistent action and selection columns. Keep taskbar selection geometry coordinated. Preserve keyboard/D-pad behaviours and logical start/end insets.
8. Visuals: real map/route, vehicle previews and saved wallpapers remain the product content. No generated imagery or filler was needed; no image prompt was written.
9. Motion: retain the user-approved glass switch sequence and taskbar animation; this pass does not add looping animation or animate layout margins.

## Validation limits

This is a source audit, not a claim that every screen was visually rendered. Device checks must cover small screens, landscape, 90%/130% interface size, enlarged system text, keyboard-open editors, RTL settings, and real Dash/Pod displays. No APK/version bump is requested in this pass. Final Android compilation and the full JVM suite passed: 150 tests across 33 suites, with 0 failures/errors/skips. No Android device was attached, so no device render was claimed.

## Coverage inventory

| Source under `com/sxdpz23/navisarthi` | Compose declarations |
| --- | ---: |
| `navigation/car/NavigationCarAppService.kt` | 0 |
| `navigation/car/screen/ActiveNavCarScreen.kt` | 0 |
| `navigation/car/screen/AlongRouteCarScreen.kt` | 0 |
| `navigation/car/screen/CarSettingsScreen.kt` | 0 |
| `navigation/car/screen/DriveCarScreen.kt` | 0 |
| `navigation/car/screen/MainCarScreen.kt` | 0 |
| `navigation/car/screen/NearbyCarScreen.kt` | 0 |
| `navigation/car/screen/RoutePreviewCarScreen.kt` | 0 |
| `navigation/car/screen/SavedCarScreen.kt` | 0 |
| `navigation/car/screen/SearchCarScreen.kt` | 0 |
| `navigation/ui/CameraBadges.kt` | 2 |
| `navigation/ui/DpadFocus.kt` | 6 |
| `navigation/ui/map/DrawBar.kt` | 1 |
| `navigation/ui/map/LayersPanel.kt` | 11 |
| `navigation/ui/map/ListBulk.kt` | 1 |
| `navigation/ui/map/MapFloaters.kt` | 2 |
| `navigation/ui/map/MapScreen.kt` | 62 |
| `navigation/ui/map/MoreCategoriesChip.kt` | 1 |
| `navigation/ui/map/NavGlass.kt` | 9 |
| `navigation/ui/map/NavigationChromeTransition.kt` | 2 |
| `navigation/ui/map/NavigationMapView.kt` | 1 |
| `navigation/ui/map/ReplayControls.kt` | 1 |
| `navigation/ui/map/ScaleBar.kt` | 1 |
| `navigation/ui/map/SwipeRow.kt` | 3 |
| `navigation/ui/nav/DrivingChrome.kt` | 1 |
| `navigation/ui/nav/NavOverlays.kt` | 23 |
| `navigation/ui/nav/RoundaboutGlyph.kt` | 1 |
| `navigation/ui/nav/RouteBarStrip.kt` | 1 |
| `navigation/ui/nav/RouteShield.kt` | 6 |
| `navigation/ui/nav/StepsSheet.kt` | 10 |
| `navigation/ui/NavigationDialog.kt` | 4 |
| `navigation/ui/NavigationMenu.kt` | 4 |
| `navigation/ui/NavigationProgress.kt` | 2 |
| `navigation/ui/NavigationRoot.kt` | 2 |
| `navigation/ui/PermissionRationale.kt` | 1 |
| `navigation/ui/place/GoogleChooser.kt` | 7 |
| `navigation/ui/place/PlaceSheet.kt` | 50 |
| `navigation/ui/place/RouteTopCard.kt` | 11 |
| `navigation/ui/place/StopsEditor.kt` | 2 |
| `navigation/ui/place/StreetViewScreen.kt` | 1 |
| `navigation/ui/RatingStars.kt` | 3 |
| `navigation/ui/search/SearchBar.kt` | 1 |
| `navigation/ui/settings/sections/AboutSettings.kt` | 1 |
| `navigation/ui/settings/sections/AppearanceSettings.kt` | 1 |
| `navigation/ui/settings/sections/DiagnosticsSettings.kt` | 4 |
| `navigation/ui/settings/sections/GoogleUses.kt` | 8 |
| `navigation/ui/settings/sections/MapSettings.kt` | 2 |
| `navigation/ui/settings/sections/MovedGroups.kt` | 6 |
| `navigation/ui/settings/sections/NavigationSettings.kt` | 1 |
| `navigation/ui/settings/sections/OfflineSettings.kt` | 6 |
| `navigation/ui/settings/sections/PerformanceSettings.kt` | 2 |
| `navigation/ui/settings/sections/PlacesSettings.kt` | 1 |
| `navigation/ui/settings/sections/PrivacySettings.kt` | 4 |
| `navigation/ui/settings/sections/QuickChipsSettings.kt` | 3 |
| `navigation/ui/settings/sections/SavedPlacesSettings.kt` | 2 |
| `navigation/ui/settings/sections/SearchSettings.kt` | 2 |
| `navigation/ui/settings/sections/VoiceLibrary.kt` | 2 |
| `navigation/ui/settings/sections/VoiceSettings.kt` | 1 |
| `navigation/ui/settings/sections/ZoomKeysSettings.kt` | 3 |
| `navigation/ui/settings/SettingsComponents.kt` | 14 |
| `navigation/ui/settings/SettingsHub.kt` | 3 |
| `navigation/ui/settings/SettingsScaffold.kt` | 2 |
| `navigation/ui/settings/SettingsScreen.kt` | 1 |
| `navigation/ui/SheetFold.kt` | 2 |
| `navigation/ui/SheetTransition.kt` | 2 |
| `navigation/ui/theme/AppTheme.kt` | 4 |
| `navigation/ui/theme/Theme.kt` | 3 |
| `navigation/ui/VoiceCaptureDialog.kt` | 2 |
| `navigation/ui/WelcomeScreen.kt` | 13 |
| `navigation/web/ReviewsPanel.kt` | 1 |
| `ui/components/BarChart.kt` | 1 |
| `ui/components/CircularDash.kt` | 1 |
| `ui/components/CraftFocus.kt` | 1 |
| `ui/components/Joystick.kt` | 1 |
| `ui/components/NaviSarthiComponents.kt` | 15 |
| `ui/components/NaviSarthiMap.kt` | 1 |
| `ui/components/UpdatePrompt.kt` | 1 |
| `ui/components/VehicleGlassSwitch.kt` | 1 |
| `ui/components/VehiclePicker.kt` | 1 |
| `ui/navigation/AppNavigation.kt` | 1 |
| `ui/navigation/LiquidGlassBar.kt` | 1 |
| `ui/screens/DashScreen.kt` | 1 |
| `ui/screens/DashWallpaperControls.kt` | 1 |
| `ui/screens/DriveBackupSettings.kt` | 1 |
| `ui/screens/GarageScreen.kt` | 15 |
| `ui/screens/GroupsScreen.kt` | 1 |
| `ui/screens/HomeScreen.kt` | 1 |
| `ui/screens/LoginScreen.kt` | 1 |
| `ui/screens/NavigationSettingsScreen.kt` | 1 |
| `ui/screens/NavScreen.kt` | 1 |
| `ui/screens/PodScreen.kt` | 3 |
| `ui/screens/PodSettingsScreen.kt` | 2 |
| `ui/screens/RidesScreen.kt` | 7 |
| `ui/screens/RouteScreen.kt` | 3 |
| `ui/screens/SettingsScreen.kt` | 4 |
| `ui/screens/UnifiedSettingsScreen.kt` | 1 |
| `ui/theme/Color.kt` | 7 |
| `ui/theme/Theme.kt` | 2 |
