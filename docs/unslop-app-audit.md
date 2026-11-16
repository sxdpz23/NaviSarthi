# NaviSarthi screen craft audit

Status: source audit and proposed direction, awaiting the direction checkpoint required by Unslop. No product UI edits have been made in this pass. Do not bump the version, run an APK build, or package 0.6.1.

Unslop method: Damir Sokolovsky ([@heydamir](https://x.com/heydamir)), https://heydamir.design/unslop, CC BY 4.0.

## Evidence boundary

The current project source is the baseline, including the completed 0.6.0 integration and existing working changes. ADB reports no connected devices. This is a source audit, not a claim that every state has been rendered or tested on a phone. The [companion inventory](unslop-screen-inventory.md) records all Compose-rendering files and Android Auto screen files discovered in the app, including nested sheets and dialogs rather than only files named Screen.

Measured token contrast on `Bg1` (#111315): `TextLo` (#65686B) is 3.32:1; `TextDis` (#44474A) is 1.99:1; `TextMid` (#9EA0A3) is 7.10:1. Disabled text may remain subdued, but active captions using the first two tokens need a readable metadata color.

## Proposed direction

- **Product:** NaviSarthi helps Royal Enfield riders plan routes, guide their Dash or Pod, and look after their motorcycle.
- **Character:** Steady, legible, purposeful.
- **World:** Motorcycle instruments, road maps, and graphite hardware; the existing vehicle accent remains the identifying color.
- **Moments:** The real map and route, and the actual Dash/Pod preview. Garage and ride history use real maintenance, fuel, and ride data for emphasis.
- **Signature:** One restrained vehicle-accent route stroke, used for route progress and the current journey where it conveys real state.

Keep NaviSarthi's logo, Geist/Geist Mono, vehicle themes, Material icons and the approved helmet-intercom silhouette. Keep the expressly requested glass navigation and its approved motion as the intentional material exception. Do not spread glass onto cards, settings rows, or dialogs. Retain chosen custom/system fonts, dynamic color, light/dark/AMOLED, accessibility preferences, and all navigation capabilities.

## Findings

These are concrete source findings; visual severity must be confirmed on a device. The app needs a coordinated consistency pass, not a replacement brand or reordered features. Groups and parts of the navigation UI are already restrained and need only integration checks.

| Finding | Evidence | Planned treatment |
| --- | --- | --- |
| Two default type systems make the app read as separate products | `ui/theme/Type.kt:38` and `navigation/ui/theme/Type.kt:14`; navigation uses a 1.12x Material scale and a separate font preference | One shared default type hierarchy; preserve explicit font choices and map-label rendering |
| Default chrome accents diverge between console and navigation pages | `navigation/ui/theme/Theme.kt:26`, `:54` versus vehicle-aware `ui/theme/Color.kt:26` | Vehicle accent for default app controls; retain user-selected dynamic color and semantic map colors |
| Small text is tightened and headings use bold as the default hierarchy | `ui/theme/Type.kt:45`, `:53`, `:69`, `:77`; `ui/components/NaviSarthiComponents.kt:119`, `:350` | Normal tracking for small text, 400/500 body hierarchy and occasional 600 emphasis |
| Uppercase section chrome is imposed by the component | `ui/components/NaviSarthiComponents.kt:292` | Sentence-case section labels; retain genuine road-sign text, codes and units |
| Muted tokens are too dark for readable active captions | `ui/theme/Color.kt:23–24`; active metadata uses these in Settings, Route, Garage and charts | Separate readable metadata from disabled states; check 4.5:1 text contrast on actual backgrounds |
| Page and internal spacing vary between sibling screens | `PodScreen.kt:33` (18dp), `GroupsScreen.kt:17` (18dp vertical), `LoginScreen.kt:88` (22dp), `BarChart.kt:53` (7dp), `VehiclePicker.kt:44` (10dp) | Shared 4dp rhythm, 20dp phone edges, 16/20dp cards, 8/12/16dp internal spacing and 24/32dp sections; retain valid 2dp optical nudges |
| Some physical control sizes are compact for a phone | Shared components use a 30dp toggle and 36dp segmented options (`NaviSarthiComponents.kt:204`, `:246`) | Audit actual hit bounds and provide at least 48dp interactive bounds; do not assume small visuals prove small hit areas |
| Card/control radius rules differ between shared component sets | `NaviSarthiComponents.kt:32–36`; `navigation/ui/settings/SettingsComponents.kt:92`, `:113` | Consistent control/card hierarchy and nested corner treatment; retain shapes required for map controls and glass bar |
| Garage metadata is unusually small and emphatic | `GarageScreen.kt:263`, `:336` use 9sp bold labels, including FREE | Readable quiet metadata and sentence-case labels; preserve service and overdue information |
| Chart captions and value hierarchy need attention | `BarChart.kt:50`, `:75`: 11sp/10.5sp text; max value is automatically the accent | Readable captions, aligned numbers and a highlight that reflects useful meaning; retain real series/data and section order |
| Settings contains hard-to-read fine print | `SettingsScreen.kt:387`, `:395`, `:460` | Readable secondary text, short row summaries, complete explanations available without hiding consent or diagnostic behavior |
| A stale product title survived navigation integration | `navigation/ui/WelcomeScreen.kt:159`: Navigation Maps | Use NaviSarthi, with context-specific onboarding copy and the existing logo |
| Long-form guidance and primary actions need a per-screen hierarchy review | Console connection controls, Route editor, Garage forms, navigation settings spokes and place/route sheets | Preserve features/order; distinguish the primary current action from secondary and destructive actions |

No evidence was found for inventing replacement illustrations or adding decorative hero images. Maps, route traces, instrument previews, real place photos and real ride data already provide the appropriate visual content.

## Coverage and intended scope

| Family | Screens and states to carry through the nine passes |
| --- | --- |
| Shell | Dash/Pod, Nav, optional Garage, Groups, Settings; selected/compact bar, vehicle switch, screen restoration, insets and keyboard |
| Console | Dash and Pod previews, disconnected/searching/authorization/PIN/connected/error states, journey progress, controls, preferences, calls/music and permission prompts |
| Existing route tools | Destination resolution, shared route preview, modes, voice options, stops, saved destinations, itineraries, route handoff and errors |
| Garage | Maintenance/checks and schedules, fuel summary/diary/forms, Glovebox documents, empty/loading/error/confirmation states |
| Rides | Summary, recorded rides, route trace/detail, empty state and deletion |
| App settings | Dash settings, Pod settings, vehicle picker, backup/Drive controls, update prompt and existing dialogs |
| Map and search | Map controls, query/results/categories, layers, offline/replay controls, lists, selection, loading, errors and empty states |
| Places | Place sheet, photos/reviews, Street View, source chooser, saved places/lists, route top card and stop editor |
| Route and guidance | Route alternatives/modes, steps, active maneuver/ETA, transit, alerts, rerouting, completion, PiP and driving layouts |
| Navigation settings | Hub/search; Appearance, Map, Places, Navigation, Voice/library, Search/quick chips, Offline, Saved places, Privacy, Performance, Diagnostics, Map data/licenses and zoom-key assignment |
| Onboarding | Welcome/provider choice, location/notification/voice prompts, capture/rationale/confirmation dialogs |
| Supporting and retained UI | Login and legacy Home source: polish where retained, without reintroducing removed routes or account flows |
| Android Auto | Main, drive, active navigation, route preview, search, nearby/along route, saved places and settings; respect platform templates and driver restrictions |

## Implementation order after the direction checkpoint

1. Spacing: shared tokens/frames first, then every screen family; compare 4/5 tabs and larger text.
2. Color: one default neutral/text hierarchy and restrained vehicle accent; preserve meaning in routes, traffic, warnings and errors.
3. Icons: Material vocabulary, aligned boxes, consistent sizes for comparable controls; retain the helmet and necessary maneuver/road glyphs.
4. Copy: short context-specific labels, NaviSarthi identity, clear connection/permission states, no invented claims or data; preserve translations and explanations.
5. Type: shared defaults, readable captions, tabular figures; preserve script-aware fallback and custom font features.
6. Layout: mobile reading order, existing sections, clear actions, adaptive sheets and safe overlay/keyboard bounds.
7. Details: control states, radii, focus, charts and empty states.
8. Visuals: real map, route, instruments and actual content; no generated decorative assets planned.
9. Motion: purpose-driven feedback, shared durations and reduced motion; preserve approved glass motion, functional rerouting/loading and drag-driven sheet behavior.

## Verification plan and limits

Use source/static checks without a version bump or Gradle/APK build. Native rendering remains pending until an existing installed app/device or an explicitly authorized later build is available. Cover light/dark/AMOLED, four/five tabs, vehicle accents, large font scale, portrait/landscape, TalkBack, keyboard/D-pad, Arabic/Hebrew RTL and mixed-script text. Adapt web-oriented Unslop values to Android dp/sp and native minimum touch sizes; do not mechanically shrink mobile rows to desktop sidebar dimensions.

Preserve road-shield typography and colors, genuine live-state indicators, map attribution, provider/privacy settings, foreground navigation, console protocols and all existing feature routes. The audit identifies improvements; it does not authorize deleting navigation functionality to simplify the UI.
