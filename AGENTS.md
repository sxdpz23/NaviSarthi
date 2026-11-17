# Project instructions

Read `context.md` and `CLAUDE.md` for the project constraints before working.

## Build delivery location

The user requires all builds delivered for this project to be placed under:

`D:\Softwares\Projects\RE_TFTDash\builds and versions`

Use this directory for finished APKs and their accompanying checksums and release notes. Version-specific subdirectories may be used inside it. Do not deliver future builds in top-level `version 0.x.x` folders or leave the only deliverable in Gradle's output directory. Include a clickable absolute link to the delivered APK in the final response.

This is the standing project preference unless the user explicitly requests another destination.

Prioritise APK delivery for future builds. Defer lint checks unless the user explicitly requests them; retain package/signature/checksum verification before delivery.

Provide stage-based APK build progress updates during builds (compilation, packaging/signing, verification and delivery). Do not invent percentage estimates.

## Navigation ownership

Nav is the sole route planner, navigation/guidance session and map-style owner. Dash/Pod display that session using the existing headless/power-saving connection; do not introduce a separate console route planner, router or voice owner. There is no separate Console route screen. Saved Destinations is the Nav list feature formerly titled Your lists; do not add a duplicate console saved-destination entry. Phone map viewing/editing during console navigation is temporary and explicit. Keep Dash zoom, TDV/POV shortcut, power controls and blue/vehicle-accent/golden-line route colors.

The user authorized the 0.6.2 build after verification on 2026-11-17. Deliver the verified build under the standing build directory above. Do not bump beyond the explicitly requested version.

## Nav glass UI

Use the approved liquid glass treatment for the Nav search bar, category chips, layers and location controls. Do not put a Settings action in the search bar; Settings belongs in the taskbar. Place info uses dark-tinted glass in an inset rounded panel visually extending from the taskbar, while preserving place actions and sheet behaviour.

Keep map glass overlays and the taskbar on the same dark tint and horizontal inset. Route chooser and mobile navigation panels also dock visually into the taskbar. Starting navigation in Nav automatically targets the selected Dash/Pod; do not add a separate Use route on Dash action. Navigation pause is removed. Traffic paints above basemap roads; the highlighted route stays above road geometry and uses Nav's traffic hues when traffic is enabled, including on Dash.
