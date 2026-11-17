# NaviSarthi version plans

Maintain one plan per version. Before each feature addition, update its plan with scope, implementation decisions, acceptance checks, status, and outstanding evidence. After implementation, record verification results and deviations. Planned features are not claims of current functionality.

## Requirements

- Personal use among friends; India-only; Android 14+; 2025+ Guerrilla 450, Himalayan 450 and Bear 650 intended, subject to per-console qualification.
- Existing console Wi-Fi protocol only. Versions before 0.8.0 must not install or modify console firmware. From 0.8.0, allow user-initiated official firmware updates only through the qualified workflow in [the console OTA plan](0.8.0.md). Preserve console-owned instruments and upper map viewport/reference layout.
- Google Maps place sharing; editable ordered stops; ask Add next stop / Append stop / Replace destination during an active journey.
- Fastest suitable motorcycle route respecting access restrictions; automatic rerouting; accurate location; joystick zoom; helmet voice guidance while Spotify/YouTube Music remain on the phone/headset.
- Navigation continues with display timeout, power-button lock, or switching apps. Force-stop/removal from recents is outside the guarantee.
- Prefer free services; optional pay-as-you-go only, no mandatory subscription, target maximum INR 400/month. Verify provider terms and enforce usage controls before enabling billing.
- Mobile internet must coexist with console Wi-Fi. Traffic and offline operation are later milestones.

## Versions

1. 0.1.0 � branding and planning foundation.
2. 0.2.0 � measured location, background and streaming reliability.
3. 0.3.0 � multi-stop navigation, Google Maps sharing and rerouting.
4. 0.4.0 � setup, joystick zoom, console music controls via the app and headset guidance qualification; first navigation/music pilot.
5. 0.5.0 � offline maps, saved custom routes, ride history.
6. 0.6.0 � live traffic and traffic-aware routing.
7. [0.6.2 — shared navigation and glass consistency](0.6.2.md): verified signed Android build; device and console acceptance pending.
8. 0.7.0 � wallpaper, garage and fuel diary integration; music moved to 0.4.0.

9. [0.8.0 — Console OTA updates](0.8.0.md): official TFT firmware discovery, download, validation, installation and post-reboot verification; planned and gated on package access and hardware qualification.

Existing inherited features may remain but must not be presented as newly qualified. Hardware validation gates all supported-model claims. Do not assume high frame rates are supported by the console.
