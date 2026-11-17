# NaviSarthi 0.6.2 (33)

Android 14+, arm64-v8a. Signed with the same certificate as the delivered 0.6.1 APK.

- Nav owns the route, guidance, GPS and map appearance; starting navigation automatically displays it on the selected Dash/Pod. Explicit temporary phone viewing and route editing remain available.
- Removed the separate Console route screen, manual console handoff action and navigation pause. Saved Destinations uses the existing Nav lists.
- Car and Motorbike route choices use genuine routing profiles; motorcycle traffic colours are inherited only from a matching course, preserving motorcycle geometry, stops and timing.
- Consistent darker liquid glass across map controls, place/route panels and mobile guidance, with matching portrait margins and panels extending into the taskbar. Compact taskbar icons and selection remain centred and symmetrical.
- Separated route overview and TDV/POV controls. Traffic paints above roads, with contrasting route edging and shared traffic hues on Dash.
- Retained Dash headless H.264 streaming, zoom/joystick controls, power saving and blue/vehicle-accent/golden-line route ink.
- Verification follow-ups fixed permission-denial handling, supported map pitch limits, locale-aware resource access, plural resources and CSV BOM formatting.

Verification: **137 tests passed across 30 suites**, no failures/errors/skips. Full release lint passed with **0 errors**; **2,576 warnings** remain, including **1,431 incomplete-translation findings** retained as warnings because partial locales use English fallback. These warnings were not hidden. Package/version, signature, previous-certificate match, ZIP integrity, 16 KiB ZIP alignment and delivered SHA-256 checks passed. See `VERIFICATION.json` and `lint-release.txt`.

Device rendering, live/offline route integration, and physical Dash/Pod ride acceptance remain pending. No device installation or console firmware change was performed.
