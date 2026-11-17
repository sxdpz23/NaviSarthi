# NaviSarthi UI source coverage index

Source inventory for the Unslop audit; not a rendered-screen or runtime validation record.

Includes nested Compose sheets/dialogs, supporting components, themes and Android Auto template screens.

92 rendering/template source files; annotation counts are discovery aids, not a count of user-visible screens.

| Source under `com/sxdpz23/navisarthi` | @Composable annotations |
| --- | ---: |
| `ui/components/BarChart.kt` | 1 |
| `ui/components/CircularDash.kt` | 1 |
| `ui/components/CraftFocus.kt` | 1 |
| `ui/components/Joystick.kt` | 1 |
| `ui/components/NaviSarthiComponents.kt` | 15 |
| `ui/components/NaviSarthiMap.kt` | 1 |
| `ui/components/UpdatePrompt.kt` | 1 |
| `ui/components/VehiclePicker.kt` | 1 |
| `ui/navigation/AppNavigation.kt` | 1 |
| `ui/navigation/LiquidGlassBar.kt` | 1 |
| `ui/screens/DashScreen.kt` | 1 |
| `ui/screens/DriveBackupSettings.kt` | 1 |
| `ui/screens/GarageScreen.kt` | 15 |
| `ui/screens/GroupsScreen.kt` | 1 |
| `ui/screens/HomeScreen.kt` | 1 |
| `ui/screens/LoginScreen.kt` | 1 |
| `ui/screens/NavigationSettingsScreen.kt` | 1 |
| `ui/screens/NavScreen.kt` | 1 |
| `ui/screens/PodScreen.kt` | 3 |
| `ui/screens/PodSettingsScreen.kt` | 3 |
| `ui/screens/RidesScreen.kt` | 7 |
| `ui/screens/RouteScreen.kt` | 3 |
| `ui/screens/SettingsScreen.kt` | 4 |
| `ui/theme/Color.kt` | 7 |
| `ui/theme/Theme.kt` | 2 |
| `navigation/ui/CameraBadges.kt` | 2 |
| `navigation/ui/DpadFocus.kt` | 6 |
| `navigation/ui/map/DrawBar.kt` | 1 |
| `navigation/ui/map/LayersPanel.kt` | 11 |
| `navigation/ui/map/ListBulk.kt` | 1 |
| `navigation/ui/map/MapFloaters.kt` | 2 |
| `navigation/ui/map/MapScreen.kt` | 62 |
| `navigation/ui/map/MoreCategoriesChip.kt` | 1 |
| `navigation/ui/map/NavigationChromeTransition.kt` | 2 |
| `navigation/ui/map/NavigationMapView.kt` | 1 |
| `navigation/ui/map/ReplayControls.kt` | 1 |
| `navigation/ui/map/ScaleBar.kt` | 1 |
| `navigation/ui/map/SwipeRow.kt` | 3 |
| `navigation/ui/nav/DrivingChrome.kt` | 1 |
| `navigation/ui/nav/NavOverlays.kt` | 24 |
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
| `navigation/ui/settings/sections/PerformanceSettings.kt` | 1 |
| `navigation/ui/settings/sections/PlacesSettings.kt` | 1 |
| `navigation/ui/settings/sections/PrivacySettings.kt` | 4 |
| `navigation/ui/settings/sections/QuickChipsSettings.kt` | 3 |
| `navigation/ui/settings/sections/SavedPlacesSettings.kt` | 2 |
| `navigation/ui/settings/sections/SearchSettings.kt` | 2 |
| `navigation/ui/settings/sections/VoiceLibrary.kt` | 2 |
| `navigation/ui/settings/sections/VoiceSettings.kt` | 1 |
| `navigation/ui/settings/sections/ZoomKeysSettings.kt` | 3 |
| `navigation/ui/settings/SettingsComponents.kt` | 13 |
| `navigation/ui/settings/SettingsHub.kt` | 3 |
| `navigation/ui/settings/SettingsScaffold.kt` | 2 |
| `navigation/ui/settings/SettingsScreen.kt` | 1 |
| `navigation/ui/SheetFold.kt` | 2 |
| `navigation/ui/SheetTransition.kt` | 2 |
| `navigation/ui/theme/AppTheme.kt` | 4 |
| `navigation/ui/theme/Theme.kt` | 3 |
| `navigation/ui/VoiceCaptureDialog.kt` | 2 |
| `navigation/ui/WelcomeScreen.kt` | 13 |
| `navigation/car/screen/ActiveNavCarScreen.kt` | 0 |
| `navigation/car/screen/AlongRouteCarScreen.kt` | 0 |
| `navigation/car/screen/CarSettingsScreen.kt` | 0 |
| `navigation/car/screen/DriveCarScreen.kt` | 0 |
| `navigation/car/screen/MainCarScreen.kt` | 0 |
| `navigation/car/screen/NearbyCarScreen.kt` | 0 |
| `navigation/car/screen/RoutePreviewCarScreen.kt` | 0 |
| `navigation/car/screen/SavedCarScreen.kt` | 0 |
| `navigation/car/screen/SearchCarScreen.kt` | 0 |

Physical console renderers and map-style semantics require separate treatment from application chrome; no protocol or frame rendering changes are proposed.

Audit method: Unslop by Damir Sokolovsky (@heydamir), https://heydamir.design/unslop, CC BY 4.0.
