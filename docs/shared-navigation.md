# Shared Nav and console navigation

Nav owns route planning, stops, Car/Motorbike selection, guidance, GPS fixes, map appearance and the TDV/POV choice. Dash and Pod observe this session. The old Console route destination redirects to Nav; no separate route editor is mounted. Saved Destinations uses the existing Nav lists and their stored contents.

Dash keeps the existing headless H.264/RTP transport, zoom/joystick controls and motion/power cadence. Its headless MapLibre display uses Nav's style, downloaded basemap and selected route geometry. The console route ink can be blue (default), the vehicle accent or golden line. Dash's TDV/POV shortcut updates Nav's shared choice. Pairing alone does not start a second GPS feed or show an independent map. Pod translates shared maneuvers into its existing BLE guidance packets.

During console navigation the handset map is unmounted by default, including the Dash/Pod preview. Nav offers explicit phone viewing for two minutes, or route/stops editing until Return to Dash/Pod. Voice stays in Nav. Console Stop delegates to Nav's full stop path, including its service and trip cleanup. The console power policy changes the cadence of Nav's location subscription; explicit phone viewing/editing restores the normal phone cadence.

Motorbike requests use Valhalla motorcycle costing online and the bundled OsmAnd motorcycle-derived profile offline. Stops remain real route legs. Legacy walking/cycling/transit preference values remain readable but normalize to Car; the reachable route pickers offer Car and Motorbike.

## Map glass controls

The search bar, category chips (including More), layers button and location button use one reusable glass surface. It samples only the map layer, excluding floating UI to prevent feedback, with background blur/refraction, a tint and a highlight edge. Foreground text, icons, focus and touch handling remain separate. The search-bar Settings action is removed; Settings remains in the taskbar.

The place-info sheet uses dark-tinted glass, a 28 dp rounded outline and the taskbar's inset/max-width alignment in portrait. Its background extends behind the taskbar, while its content reserves 96 dp to keep the tabs clear. Existing photos, place actions, tabs, scrolling, drag detents and list/save operations remain wired to their original handlers. Landscape retains its side-panel arrangement.

The taskbar and map controls now share one shader and tint. Parking, the layers menu, route endpoints, both route choosers and the maneuver/ETA panels use the same glass surface. Portrait search and docked panels share the 16 dp inset and 480 dp content cap. The compact taskbar removes label layout space and centres a circular selection inside equal tab slots. Route overview, TDV/POV, voice and search controls occupy one spaced navigation stack; the native navigation compass is suppressed to avoid collisions with that stack.

Navigation starts on the selected console automatically. There is no additional handoff button and no navigation pause control, notification action, setting or public pause mutator. Recorded-trip playback pause remains a separate replay control.

Traffic raster layers paint above basemap geometry. Navigation uses the shared route's traffic offsets/hues when traffic is enabled, with a contrasting route edge above the basemap. Dash caches those same traffic spans on the shared route geometry, preserving its blue/accent/gold ink outside congestion segments. No new traffic estimator or routing session is introduced.

When traffic is explicitly enabled and Google access is permitted, an online motorcycle route can inherit congestion colours from a matching road course. A divergent course is rejected; only traffic spans are copied/scaled. The motorcycle geometry, stop legs and motorcycle timing remain unchanged. Requests are bounded by the remaining route-fetch budget. Offline and Google-free routes do not gain fabricated traffic.

## Validation status

Kotlin PSI parsing and resource/structure checks passed. The existing pure JVM regression tests also ran in isolation before build authorization. After authorization, version 0.6.2 passed 137 Android unit tests, full release lint (0 errors; translation/style warnings remain), release assembly and APK signature/package/alignment/checksum verification. See `conversation-verification.md` for the results and outstanding device checks.

On actual hardware, validate Car/Motorbike planning and rerouting, multi-stop edits, temporary phone return/timeout, route-color/view synchronization, offline basemaps, Bluetooth guidance and screen-off Dash streaming. Inspect glass contrast and place-sheet/taskbar alignment in portrait, landscape, light/dark themes and enlarged text.

## Post-delivery GPS and map-style regression

The rider reported continuous GPS lost and styles failing to reflect on the console in the delivered 0.6.2 APK. The previous compile/unit/lint checks did not exercise this hardware path.

The GPS handoff incorrectly required the smoothed Nav coordinate to equal the raw GPS coordinate. At low speeds Nav deliberately smooths positions, so Dash received the first fix but subsequent measurements could be suppressed until that fix expired. The handoff now distinguishes a held outlier from a fresh, smoothed measurement, preserves the original monotonic timestamp, and uses Nav's accepted accuracy fallback. Routing receives the same accepted-measurement flag. Dash's freshness window also accommodates the existing 5-second power-saving interval; old and inaccurate fixes still fail validation.

The headless renderer requested its themed follow-up synchronously from MapLibre's snapshot callback. The installed SDK clears that callback after invoking it and rejects a nested start. Follow-ups now use a coalescing main-thread queue. Style changes discard the previous look, selected JSON specifications are cached for console Wi-Fi without internet, and a missing specification no longer bypasses satellite injection by silently loading an unmodified URL. Unavailable styles produce a visible retry message. These changes retain Nav's selected source, palette, route and GPS ownership.

`SharedDashRegressionTest` covers smoothing, parked fixes, outlier rejection/recovery, power cadence, callback sequencing and selected-style caching. Console GPS recovery, Black/Dark/Satellite rendering, joystick/view shortcuts and screen-off behaviour still require the bike and an Android device. Source fixes do not replace the previously delivered APK automatically.

Validation after these fixes: `:app:testDebugUnitTest` passed with 143 tests across 31 suites, including all six new regression cases (0 failures/errors/skips). Debug Android/Kotlin compilation passed. No device was attached; release lint/assembly have not been rerun and no replacement APK or version bump was produced for this follow-up.

The subsequent stops-editor overlap report is fixed in source for both planning and mid-navigation editing. Both retain 96 dp of taskbar clearance outside their scrolling content; long lists and the Add stop/Done actions can scroll above the dock. Planning now uses the same portrait panel margins/width cap and landscape column as the other glass sheets. Both callers apply IME insets. `:app:compileDebugKotlin` passed after this layout change; device/keyboard visual verification remains pending. No APK was generated.

## Idle Dash wallpaper

Dash offers a system photo picker, a circular crop preview with zoom and horizontal/vertical positioning, and Change/Remove actions. Only the explicitly confirmed crop is saved as a 512 px PNG in private files using AtomicFile. Source decoding is bounded to 2048 px and runs off the UI thread. The cropped copy is independent of the gallery URI and restored after restart or reconnect. Navigation always replaces the idle wallpaper; removing it restores the start-navigation guide. Wallpaper changes invalidate the headless idle frame without touching the connection, routing or GPS ownership. Physical round-display/crop acceptance remains pending.
