# NaviSarthi app-wide polish — 0.6.1

The user approved the direction below, then authorized a build of the completed changes. This pass keeps the product, features, section order, vehicle identities, logo, helmet-intercom icon and approved liquid-glass navigation.

## Direction

- Product: navigation and motorcycle care for Royal Enfield riders.
- Character: steady, legible, purposeful.
- World: graphite motorcycle instruments and road maps, with the existing vehicle accent.
- Moments: the real route/map and Dash/Pod preview; real maintenance/fuel/ride data supplies the emphasis elsewhere.
- Signature: restrained vehicle-accent journey progress, used for actual state rather than decoration.

## Nine passes

| Pass | Applied treatment |
| --- | --- |
| Spacing | Normalized one-off UI padding/gaps to the shared rhythm; preserved drawing coordinates, camera/drag math and console geometry. Shared phone controls use 48dp minimum interactive height and expanding text layouts. |
| Color | Readable metadata tokens; graphite navigation sheets and dark surfaces match the app ramp. Default navigation controls follow vehicle accents. Explicit wallpaper colors, light/dark/AMOLED and semantic route/traffic/warning colors are retained. |
| Icons | Material vocabulary retained; back/forward chevrons use the available auto-mirrored Material keyboard-arrow equivalents. Added action labels for back, Settings, map zoom and recenter controls. The approved helmet icon remains. |
| Copy | Removed forced uppercase section labels, corrected the leftover Navigation Maps title to NaviSarthi, and made service metadata sentence case. Existing explanatory and permission text remains available. |
| Type | Shared default typography for console/app and navigation chrome, readable 12–16sp captions/body, normal tracking on small text and 400/500 hierarchy with restrained 600 emphasis. Default Geist and tabular figures match the existing identity. System, bundled Google Sans Flex and custom font choices remain available; an explicit NaviSarthi-default option exposes the shared default. |
| Layout | Headers, settings rows, cards and actions share spacing and shape hierarchy. Buttons and row titles wrap rather than relying on fixed single-line heights. Feature sections and their order are retained. |
| Details | Standard native switch behavior/semantics, selected segment semantics, visible keyboard focus outlines, differentiated disabled buttons, readable settings fine print, aligned row icon boxes and consistent corners. |
| Visuals | Kept the actual map, routes, instruments and existing content. No generated images, decorative hero assets or invented data were added. The fuel chart uses solid semantic emphasis, readable labels and safe zero-value rendering. |
| Motion | Removed decorative connected-dot/ring loops, keeping a steady state indicator. Native page transitions now honor the existing Page transitions preference. Preserved approved glass spring/refraction, real rerouting/loading feedback and gesture-driven sheets. |

## Coverage

See [the screen inventory](unslop-screen-inventory.md) for the rendering/template files, including nested dialogs, place/route sheets, onboarding, app/console preferences, all navigation settings spokes and Android Auto screens. Forty existing source/resource files received direct changes; shared typography, colors, shapes and components carry the treatment into other screens. New focus/shape components and the bundled attribution supplement those changes.

Android Auto retains platform-owned templates and driver restrictions. Maneuver/road-shield glyphs and semantic colors remain specialized. Physical console frame rendering, BLE/Wi-Fi protocols, routing models, services and view-model behavior were not changed by this pass. Legacy Home/Login source is polished where retained, without reintroducing removed navigation routes.

User-selectable emoji for saved-map/list icons remain as personalization content. They are not replaced or removed as decorative chrome; application controls retain Material icons.

The approved glass implementation is byte-identical to the pre-pass snapshot. Its existing compact-label size/material/motion is an intentional exception to the general caption and surface treatments.

## Verification

- Kotlin PSI syntax parsing: 201 source files, zero syntax errors; this is separate from Android compilation.
- XML: 56 resource XML files parsed successfully.
- Metadata contrast: 6.61:1 on Bg0, 6.25:1 on Bg1, 5.85:1 on Surf1, 5.30:1 on Surf2, and 4.59:1 on Surf3. Disabled text remains a separate state.
- Source whitespace check passed.
- Debug Kotlin compilation passed after correcting a chart color capture inside a drawing callback.
- JVM regression tests: 127 passed with no failures/errors. Release assembly and release lint passed. APK signature, package/version (com.navisarthi.app, 0.6.1/32), arm64 contents and bundled licenses were verified.
- Local APK: `version 0.6.1/NaviSarthi-0.6.1.apk`; SHA-256: `7ef492c7129e50c162a091696f42d16dc1e5b142be03ff8a00e50407e8228cea`.
- No connected Android device was available. Native visual comparison, large-text wrapping, light/dark/AMOLED, RTL, TalkBack/D-pad, keyboard/sheet interactions, map guidance, Android Auto and physical console acceptance remain device checks. Do not infer their runtime validation from static checks or compilation.

No image prompts or story video were produced: the app's actual content supplies the visual moments, and no story video was requested.

## Credit

Unslop was created by Damir Sokolovsky ([@heydamir](https://x.com/heydamir)), https://heydamir.design/unslop, CC BY 4.0. Its nine-pass method is adapted here for Android dp/sp, native control semantics and the existing NaviSarthi brand. Attribution is also bundled in `assets/licenses/Unslop-NOTICE.txt`.
