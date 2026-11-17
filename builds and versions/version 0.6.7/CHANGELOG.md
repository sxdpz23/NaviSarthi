# NaviSarthi 0.6.7 (38)

- Expand Nav search interaction to the full glass shell and full-height text region; edge taps open search while voice/lists/back/clear retain separate actions.

- Refine shared dark glass tint, panel readability, corner refraction and touch feedback. Nav taskbar samples the map directly, avoiding compounded glass overlays.
- Align Dash, Pod, Garage, Groups and Settings-home headings with consistent typography and top/side spacing. Remove the separator before Navigation settings' first item.
- Preserve wallpaper aspect ratio on idle render surfaces by cropping instead of stretching; existing saved wallpapers benefit automatically.
- Clip route and traffic overlays behind the perspective camera, preventing mirrored route paths at close console zoom. Clip crossing segments against the snapshot's camera plane.
- Match the font, bold weight, size and baseline of “Ready for” and “Adventure”, retaining the green accent.

Includes the unified settings, glass switches and earlier functionality delivered in 0.6.6. Nav remains the sole route/guidance owner; Dash/Pod retain their existing connection and power-saving display architecture.

See VERIFICATION.json for package, signature, checksum, test and lint results. Physical console, shader rendering and device visual verification remain pending. Unslop by Damir Sokolovsky (CC BY 4.0); attribution remains in app notices.
