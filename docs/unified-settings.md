# Unified settings — 2026-11-18

The taskbar Settings tab now opens the navigation settings hub and native grouped rows. The former Navigation and maps settings destination redirects to this same entry. One nested stack contains System, the selected vehicle's Dash or Pod section, Appearance, Map, Places, Navigation, Voice, Search, Saved places, Offline maps, Privacy, Performance, Diagnostics, and Data and licenses.

System keeps the existing vehicle switch callback and Garage storage (Garage remains off for new installs). Dash retains connection/switch-bike, music, Bluetooth batteries, phone/call permission and sharing handlers, plus ride history. Its auto-connect and ride preferences now persist. Auto-connect controls automatic connection on opening Dash; starting a navigation still targets the selected console. Turn phone screen off allows normal display sleep; disabling it holds the display during active Dash navigation, without forcing device locking or adding device-admin permissions. Keep dash awake controls the existing CPU/Wi-Fi connection locks; the projection/session heartbeats remain intact. Pod retains pairing/PIN, saved auto-connect, display/night mode, journey bottom line, clock synchronization, diagnostics and ride history.

Performance contains Android battery/background access, with restriction status, lifecycle refresh and fallback settings intents. Data and licenses contains the existing Google Drive backup handlers and licenses. Backup, garage, saved lists and paired-device data are retained. Phone unit displays now share Navigation's unit preference; an explicit old host units choice is migrated only if no Navigation unit choice exists.

## Appearance and removals

Outside the Nav tab, page backgrounds are pitch black. The navigation theme is locked to AMOLED/FOLLOW while its retained day/night override and map-look selections still control Nav. Settings themselves stay AMOLED even during a daylight drive. The bundled Google Sans Flex is used throughout phone UI, and interface scale applies once across the shell and Nav (90% on a fresh install). Map glyph fonts and native console instrument fonts are unaffected.

Removed controls: app theme choices, separate map light/dark choice, page-transition toggle, font picker, Material You, app-name alias, navigation keep-screen-on and arrow-colour picker. Transitions remain on; dynamic colours remain off; launcher branding is restored to NaviSarthi; navigation arrows use blue disc/white arrow. Retained navigation icon choices and Dash blue/accent/gold route ink are unchanged.

## Default policy

`UnifiedSettingsPolicy` runs before preference-holder initialization. Only absent keys receive configurable defaults; explicit existing choices are preserved, including the legacy route-picker key. Removed options are fixed to their requested values on launch. Unspecified choices such as units/clock/language, navigation north-up, traffic toggle, terrain, map start position, voice/search, compatibility rendering and camera warning options retain their existing defaults.

Attachment defaults covered:

- Appearance: 90% interface size; fixed Google Sans Flex, AMOLED/FOLLOW and transitions on.
- Map: layers/parking/two-finger tilt/3D buildings/fill missing on; north lock off; category shortcuts one button; house numbers near-only; English names then local fallback.
- Places: both icon sources; places/Google lookup/civic/transit/bus/metro/train on; reviews/photos on, on-tap loading on; full reviews panel off; popular-times wait on; adult hiding off; external-link hiding on.
- Navigation: Google picker and road-ahead bar on; route arrows/bike-safe/prefer-buttons off; mini-map turn card and faster-route acceptance on; road name under puck; normal arrow size; end confirmation off; speed display on; speeding alerts and turn vibration off; external links show place; surveillance/speed cameras, bridge openings and live traffic rechecks on.
- Privacy: Google-free off, both place sources, traffic-on-tap off, telemetry blocking off. Remaining sections retained.
- Performance: voice-search preload on, full photos/reviews loading off, turn declutter on; renderer compatibility default preserved.
- Dash: auto-connect/screen sleep/connection wake locks/Bluetooth batteries/icons-only on by default. Existing explicit accessory/Pod choices are retained.

## Validation

Android compilation and the full JVM suite passed after the final animation/layout changes: 150 tests across 33 suites, 0 failures/errors/skips. Device visual checks, permission-return flows, font/scale accessibility and physical Dash/Pod behaviour remain pending. No version bump or APK build is part of this request.

## Follow-up layout and toggles

The Settings home omits its back button while subpages retain theirs. Categories has a 16 dp gap below search and a 12 dp icon/label gap. All phone switch entry points now delegate to VehicleGlassSwitch: vehicle-accent enabled track, disabled-state treatment, white glass thumb, spring travel, press stretch and synchronized colour interpolation. Native toggle semantics, keyboard/D-pad activation, RTL placement and minimum 48 dp touch height remain. Compose animation settings honour system duration scaling. The user-supplied original MP4 was inspected for the inflate/travel/contract sequence. Native rendering is adapted to vehicle accents and phone touch sizes; exact frame-for-frame parity is not claimed. Physical animation/accessibility validation remains pending.

The supplied original MP4 was subsequently inspected at 60 fps (5.72 seconds). Shared switches now follow its inflate/hold, lens travel, and contraction sequence with transparent accent-tinted glass, edge highlights and a white resting thumb. A 1.55-second keyframed transition animates only during interaction; rapid state changes continue from the current geometry. The implementation is adapted to native touch sizes and system motion scaling; exact device/frame parity is not claimed.
