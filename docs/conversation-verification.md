# Conversation verification — 2026-11-17

This audit covers the requested UI and shared-navigation changes. “Source checked” means the implementation and handlers were inspected; it does not mean the Android UI was rendered or the bike connection was exercised.

| Request | Current implementation / verification |
| --- | --- |
| Native NaviSarthi integration of navigation | Navigation sources/resources live inside the NaviSarthi app namespace; notices remain preserved. Source checked. |
| Dash/Pod, Nav, optional Garage, Groups, Settings tabs | Native host uses these tabs; Garage is conditional, Groups uses the approved helmet/intercom vector and coming-soon page. Source checked. |
| Glass taskbar, stronger depth/refraction and page transitions | Existing approved shell retained. Taskbar and Nav overlays now share a shader and dark tint. Source checked; rendering pending. |
| Symmetrical compact taskbar | One animation drives shell height, selection width and disappearing label space. Compact icons are centred in equal slots with a circular highlight. Source checked. |
| All requested map controls use glass | Search, categories/More, layers/menu, parking, locate, route endpoints, place info, both route choosers and maneuver/ETA surfaces use shared glass components. Source checked. |
| Uniform margins and taskbar extensions | Search and portrait docked panels use 16 dp margins / 480 dp content cap; panel content reserves taskbar clearance. Classic chooser alignment was corrected during audit. Source checked; visual/device checks pending. |
| Settings only in taskbar | Search-bar Settings parameter and gear action removed. Source checked. |
| One Nav-owned session/map | NavDisplaySession publishes Nav route, accepted GPS fix, view/style/offline archive and stops. Dash headless MapLibre and Pod guidance consume it; active handset previews are suppressed. Source checked. |
| Automatic console navigation | Starting Nav activates the selected Dash/Pod adapter. No separate Use route on Dash button exists. Source checked; actual connection pending. |
| Temporary phone viewing/editing | Explicit view action expires after two minutes; editing stays until explicit return. Leaving Nav hides the phone view. Source checked; lifecycle/device checks pending. |
| Remove Console route screen | Restored legacy route destination redirects to Nav. No RouteScreen is mounted by the host. Source checked. |
| Saved Destinations is Your lists | Existing list storage/handlers retained under the renamed default resource. No console saved-destination entry added. Source checked. |
| Car and Motorbike only | Reachable pickers use road modes; old non-road preferences normalize to Car. Motorcycle routing uses Valhalla costing and OsmAnd's motorcycle-derived profile, preserving stops. Motorcycle request/parser regression tests passed; live/offline device routing validation remains pending. |
| Retain zoom, joystick, power saving and route ink | Dash keeps existing controls/transport and blue/accent/gold selection. Its power policy updates Nav's location cadence. Relevant isolated regression tests passed. |
| TDV/POV, alternate routes and style owned by Nav | Shared state drives handset and Dash; Dash view shortcut writes back to Nav. Source checked. |
| Fix overlapping mobile view controls | Overview, TDV/POV, voice and search are in one spaced column; navigation compass overlay is disabled. Source checked; short-screen and landscape validation pending. |
| Remove navigation pause | Widget, setting, notification action, host callbacks and public session mutator removed. Media and replay playback controls retained. Source checked. |
| Route/traffic above roads | Phone route inserted above basemap geometry with required contrasting edging; traffic inserts above geometry and below route. Headless style ordering likewise places imagery/traffic above geometry. Source checked. |
| Navigation route traffic hues on phone and Dash | Traffic toggle gates route hues. Dash draws Nav's traffic segments and the same overall-traffic fallback. Motorcycle traffic colours are accepted only from a matching course, preserving motorcycle geometry/stops/timing; new regression tests passed. Rendered/live traffic validation remains pending. |
| Build/version authorization | The user subsequently authorized 0.6.2 after verification. Version 0.6.2/code 33 was built, signed, verified and delivered under `builds and versions/version 0.6.2`. |
| Future build delivery directory | Standing instruction retained in AGENTS.md: `builds and versions`. |

## Executed checks

- Existing pure JVM regression suites: **32 tests passed** across NavigationPowerPolicyTest, RouteMatchPolicyTest, RouteCursorTest, RerouteDirectionTest, ConsoleNavigationControlTest and PodProtocolTest. Only their selected JVM sources/test classes were compiled into temporary verification output; the Android app was not built.
- Full Android debug unit-test task: **137 tests passed across 30 suites**, zero failures, errors or skips. This includes three RoadTravelModeTest cases, three MotorcycleTrafficTest cases and four MapOverlayOrderTest cases, compiled against the current production source.
- Kotlin PSI parse of main and test sources: **603 files, zero syntax errors**. Full debug and release Android compilation also passed.
- **59 source/resource contract checks passed**, including parsing and duplicate-name validation for **24 string XML files**. `git diff --check` passed (an existing line-ending warning remains on the earlier Unslop audit document).
- Full release lint passed: **0 errors, 2,576 warnings, 55 hints**. The first full run identified 1,431 missing-translation errors and 41 other errors. Partial locales deliberately retain English fallback, so missing translations remain visible as warnings. The remaining errors were corrected or narrowly documented where detector limitations applied: the public Activity dispatcher override, delegated predictive-back Flow collection, and concrete annotated JavaScript bridges behind an abstract return type.
- Release assembly passed. APK verified as `com.navisarthi.app`, **0.6.2 / 33**, minSdk 34, targetSdk 36, arm64-v8a, non-debuggable. Signature matches the delivered 0.6.1 certificate; ZIP integrity, 16 KiB ZIP alignment and copied-file SHA-256 checks passed. Detailed receipt accompanies the APK.

## Remaining verification

Full compilation, unit tests, release lint and package/signature checks passed for the delivered Android 0.6.2 build. Compose rendering, instrumented UI tests, APK installation and physical Dash/Pod rides require suitable devices and were not performed. In particular, verify glass sampling across popup windows, contrast on every map style, font scaling, panel dragging/keyboard/landscape clearance, live and offline motorcycle routes, traffic hue alignment, route edits, view timeout, reconnect and screen-off streaming.

The source audit and passing isolated tests do not establish that every app interaction is runtime-correct. The UI and shared-session integration remain pending those checks.
