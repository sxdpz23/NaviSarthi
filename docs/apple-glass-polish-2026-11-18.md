# Liquid glass polish — 2026-11-18

Reference: [Meet Liquid Glass, WWDC25 session 219](https://developer.apple.com/videos/play/wwdc2025/219/), supplied by the user. This is a native Android adaptation of the reference, not Apple's system material or a claim of frame-identical rendering.

Unslop by Damir Sokolovsky, [heydamir.design/unslop](https://heydamir.design/unslop), CC BY 4.0. Attribution remains in the app's notices.

## Approved direction

- Product: motorcycle navigation and vehicle care for Royal Enfield riders.
- Character: steady, legible, purposeful.
- World: graphite instruments and road maps, with pitch-black backgrounds outside Nav.
- Moments: the actual route/map and Dash/Pod preview.
- Signature: vehicle-accent progress and selection.

Polish mode follows the user's previously approved direction and instruction to make the required changes. Retain the approved dark glass, helmet icon, font, switch animation, section order and features.

## Audit and changes

1. Spacing: preserve the completed app-wide spacing pass, common Nav insets, taskbar safe-area clearance and full-width selected tab when collapsed. These geometry constraints remain linked; no control is moved over another control.
2. Colour: the two shared glass renderers now use one edge, underlay and neutral wash. Quieter highlights and a slightly denser dark tint improve readability against bright satellite imagery. Larger panels receive additional tint and blur.
3. Icons: retain Material symbols, the approved helmet/intercom and vehicle-accent selections. No additional icon family or decorative symbols.
4. Copy: keep functional labels and guidance unchanged. No new claims, placeholder screens or features.
5. Type: retain Google Sans Flex and the existing readable roles. Background filtering still excludes text and interactive content.
6. Layout: shared non-Nav cards use the unified Settings group's 24 dp radius; buttons, inputs and square icon controls share 16 dp corners. Calm data surfaces remain opaque. A neutral 136 dp bottom fade separates scrolling pages from the floating taskbar; Nav's map, attribution and docked panels are excluded from that fade.
7. Details: Nav refraction now follows the supplied rounded outline rather than assuming every surface has a 28 dp corner. The taskbar consumes Nav's map-only recording, avoiding sampling the already-composited search, place or guidance glass. Registration follows the map host lifecycle, with layer-identity checks when disposing outgoing hosts. Root coordinates serve the shell; existing screen coordinates remain for popup glass alignment.
8. Visuals: retain actual map imagery, vehicle previews and saved wallpapers. No new raster assets or image prompt were needed. The existing map GraphicsLayer is reused; there is no second renderer or new frame ticker.
9. Motion: category chips and taskbar presses subtly energise the material, without scaling labels or hit targets. Selection travel, collapse proportions and the approved glass-switch sequence remain. Compose animations continue to use the system animation clock.

## Functionality boundaries

Follow-up header correction: Dash, Pod, Garage, Groups and the Settings hub now all use `ScreenHeader`: 24 sp medium Google Sans Flex, 32 sp line height, and shared 20 dp top/side insets beneath the shell's status-bar safe area. Titles align at the top rather than vertically centring against varying descriptions or status pills. Vehicle metadata sits below the Garage/Pod title. Settings home uses the shared header instead of a Material app bar that introduced a different inset. Child Settings pages keep their back buttons. Removed the stray divider preceding Navigation settings' first row; dividers between subsequent rows remain.

Nav retains route planning, guidance, location and map-style ownership. No changes to routes, voice, console encoding/transport, connection lifecycle, battery reporting, wallpaper storage or power-saving ownership. Visible transparent status bars and gesture-safe bottom margins remain. No version bump or APK is requested for this pass; the delivered 0.6.6 APK remains unchanged.

## Verification

- Final Android debug compilation and JVM regression suite passed: 150 tests across 33 suites, zero failures/errors/skips (`.superdesign/tmp/apple-glass-verified.log`).
- `git diff --check` passed. Shader callers supply the new thickness/pressure uniforms; tab interaction sources remain separate so keyboard focus is not broadcast to other tabs.
- The Settings hub no longer attaches an unused collapsing-app-bar scroll connection; content scrolling remains independent of its fixed shared header.
- Delivered 0.6.6 APK SHA-256 remains `e36f8e2eb256516c734cfb4474d9a746921326f8aa4ee3fd144bd1f2b0baf9c3`. Its completed release lint report is filed with that build (zero errors, 2,610 warnings); that report predates this design pass.
- Device rendering, shader execution, touch feedback and real console use remain unverified in this source pass. These require device validation; JVM tests cannot establish their visual quality. No new APK was built.
