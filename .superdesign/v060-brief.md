# NaviSarthi 0.6.0 — navigation shell

## Direction
Product: Android navigation and motorcycle-console companion for Royal Enfield riders.
Character: calm, precise, road-ready.
World: the map and the round Tripper instrument, with graphite controls and the selected vehicle's accent.
Moments: the working map and a translucent floating navigation bar.
Signature: the round console preview and a single moving selection lens.

Retain Geist and Geist Mono, existing vehicle colours, and Material Outlined icons. The user's explicit request for Apple's liquid-glass navigation takes precedence over generic advice against glass. Confine glass to the navigation chrome; avoid decorative glows, gradient headings, emoji, pulsing ornaments, and unrelated redesigns.

## Source audit
- AppNavigation.kt: fixed opaque full-width bottom row with a dot for selection; no shared moving indicator or directional tab transitions.
- AppNavigation.kt: Home, Route, Dash/Pod, optional Garage, Rides; Settings is outside the tab bar.
- HomeScreen.kt: repeats entry points to Route, console, and Settings. Those destinations become first-class tabs.
- DashScreen.kt: the circular console preview is a useful product signature. The streaming radial aura is optional decoration, not required in this design.
- Existing typography/paddings contain some one-off values. Retain the screen content and normalize only navigation-shell spacing.

## Requested architecture
1. Dash (or Pod for the selected console): existing view, connection and console preferences.
2. Nav: Vela's map-first interface, search, places, route chooser and full navigation; adapted to NaviSarthi's typography, accent and console handoff.
3. Garage: maintenance, fuel diary and Glovebox; remains optional, records retained when hidden.
4. Groups: a quiet page reading "Feature coming soon".
5. Settings: existing settings content. Keep Ride History reachable here as a secondary page so records remain accessible after removing the Rides tab. The user renamed the Preferences title and tab to Settings during review.

## Material icons
Dash/Pod: Speed (Material Outlined); Nav: Navigation; Garage: Build; Groups: side-profile helmet silhouette with a visible external boom microphone, based on the two user-provided references; Settings: Settings.
The Groups icon source is `.superdesign/helmet-intercom.svg`; use it both in the tab and the Groups page. It is a solid monochrome silhouette with visor cutouts, an intercom earpiece, boom and microphone. Default SVG foreground is black; inside the dark app it inherits the tab foreground/accent so the silhouette remains legible.
Use real Material icons, with a consistent 24dp visual box. Do not substitute emoji or a different icon family.

## Glass and motion
The Apple reference is a 7-second Music clip demonstrating expanded and scroll-collapsed glass navigation. It is visual inspiration, not a specification of Android internals.
- Floating capsule, 16dp side margins, 12dp above the system navigation inset; max width 480dp.
- Expanded height 72dp, inset selection lens with 6dp internal padding, legible labels. Five or four equal-width targets depending on Garage visibility.
- Neutral translucent tint, blurred content behind the bar, a fine light rim and a soft neutral shadow. Icons/text remain crisp. Never blur foreground labels.
- A shared moving selection lens, approximately 300ms settling, restrained stretch only during travel; active icon/text use the vehicle accent.
- Review feedback: increase glass depth and refraction. Add a bounded edge-normal displacement layer from the actual content below the capsule, stronger rim lighting and deeper neutral shadows. Keep foreground icons and text outside the filtered background layer. The web preview uses a capsule displacement map; native capture/shader integration is implemented; device rendering verification remains pending.
- Page transition: direction follows tab order; 16–24dp horizontal travel and opacity over 220–280ms. Keep the map renderer stable and do not animate a live native map's creation/destruction.
- Scroll-collapse demonstration: shorter icon-only bar after downward scroll; restore labels when scrolling back, selecting a tab, or requesting focus. All tabs remain directly reachable.
- Respect system animator/reduced-motion settings. Keep >=48dp touch targets, TalkBack labels, selected semantics, keyboard focus, high font scale, portrait/landscape and navigation/IME insets.
- Keep route controls, map attribution and the last scroll item above the bar. Collapse or move the bar without hiding critical active-navigation actions.

## Preview contract
Produce one mobile interactive navigation-shell prototype, not a claim of functioning Vela integration. Default state: Nav. Bottom tabs switch between five representative states. Add a demo control outside the product view for Garage visibility and Dash/Pod selection if supported. Use plausible, explicitly illustrative data, not invented real connection telemetry.
Nav must resemble the supplied Vela map screenshot: full-bleed map, search across the top, compact category chips, layer and recenter controls, map attribution. Add the new glass bar without converting the map into a small card. Other states reuse their existing content hierarchy.
Use only the existing fonts, palette and component vocabulary. The glass treatment is the requested exception. Keep the exact NaviSarthi logo if a logo position is rendered; the established Brand Asset is navisarthi-logo. Never invent a substitute mark.

## Apple Liquid Glass session

Reference: [Meet Liquid Glass, WWDC25 session 219](https://developer.apple.com/videos/play/wwdc2025/219/).
Apply navigation-only Regular material, edge lensing, touch-driven light/flex, background-aware contrast, scroll-edge separation and accessibility fallbacks. The selection is a thin overlay within one shared material, not a second glass layer.

Android implementation inference: use a captured background layer and bounded refraction shader, leaving icons/text sharp. Tune contrast and sampling for the map and list backgrounds. Prototype blur/highlights approximate this material; they are not Apple's native optical renderer. Respect reduced motion/transparency and increased contrast. Profile GPU cost and suspend material updates when hidden or the phone screen is off.

## Credit
Design audit method informed by Unslop, Damir Sokolovsky (https://heydamir.design/unslop), CC BY 4.0. Apple Music is the user-supplied motion reference. Vela Maps remains attributed to its upstream authors.

## Native implementation record
Revision 7 approved by the user. Implemented in the single NaviSarthi application for 0.6.0 (31); see `docs/0.6.0-ui-integration-review.md` for build results and remaining device checks.
