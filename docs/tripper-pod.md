# Tripper Pod interoperability — NaviSarthi 0.5.0

Meteor 350 selects Tripper Pod BLE. Himalayan 450, Guerrilla 450, Bear 650 and
Himalayan 750 select Tripper Dash Wi-Fi. Physical Pod qualification is pending.

## Evidence

Supplied original RE app: com.royalenfield.reprime 10.1.23(45), code 193,
SHA-256 D08478E8C564624503B440834A40499CD4ACDC1C8572E49FE1B4830472AD37A4.
Only interoperability facts are recorded; proprietary code/APKs are not included.

Static inspection of zs7 establishes UART service
01FF0100-BA5E-F4EE-5CA1-EB1E5E4B1CE0 and characteristic
01FF0101-BA5E-F4EE-5CA1-EB1E5E4B1CE0. yd1.N and
sg1.onCharacteristicWriteRequest establish a phone-hosted GATT server for replies.
yd1.O and DeviceListFragment.l5 establish the 20-byte handshake and six ASCII
PIN digits following command 20 (hex). Handshake 21 uses 01 for new devices and
00 for saved devices. yd1.m recognizes command 20/value 01 as successful PIN
authorization. e22.a establishes CRC-16/CCITT-FALSE over 18 bytes, polynomial
1021, initial FFFF, stored big-endian. yd1.P supplies clock command 50; commands
03 and 30 provide firmware and serial replies.

Navigation fields, Pod glyphs and watchdog/keepalive behaviour were cross-checked
against the implementation author's primary interoperability notes:
https://github.com/jnsdias/wazetripper/blob/main/docs/PROTOCOL.md
This is unofficial, not a manufacturer specification. NaviSarthi implements its
own packet builder/client. Manufacturer pairing/navigation documentation:
https://www.royalenfield.com/in/en/motorcycles/tripper/

## Behaviour and limits

- Discover RE_DISP names or the UART service, then validate the actual service and
  writable characteristic. New pairing requires a valid authorization response.
  Saved resumption requires the saved address, resume handshake and a valid
  firmware reply. Bluetooth connection/local write success alone is insufficient.
- Each attempt owns a fresh epoch, GATT client/server and serialized write queue.
  Switching/disconnecting invalidates callbacks and purges queued writes.
- Headless navigation reuses route models/router, GPS, voice and ride recording
  without starting Dash Wi-Fi, video encoding or media forwarding.
- Pod output is metric. Its bottom field supports distance, remaining time or
  24-hour arrival time. Clock synchronization supports 12/24-hour encoding.
  Phone-side Pod summaries respect the shared units preference.
- Day/night and recalculating glyphs are implemented. Progressive-fill, radar/call
  alerts and firmware updates are not exposed pending sufficient evidence.
- Stopping guidance purges pending screens. Some firmware restores its clock by
  dropping the screen watchdog connection; auto-reconnect resumes without sending
  a navigation screen. An ignition cycle may be necessary to advertise again.
- Auto-connect requires selected Meteor, saved Pod, permission and the enabled
  preference. Connection retries are bounded. Missing/stale GPS suppresses stale
  guidance; rerouting waits for trusted direction. Offline routes never request
  online rerouting.

## Hardware acceptance pending

Test leading-zero/incorrect PINs, reverse GATT replies, saved resumption,
firmware/serial information, both clock formats, day/night, all maneuver types,
roundabout exits, secondary arrows, metric boundaries and each bottom-field mode.
Ride with the phone locked; test GPS loss/recovery, off-route behaviour, multiple
stops, ignition cycles, Bluetooth off/on, permission revocation and retry exhaustion.

Switch vehicles during scanning, pairing, reconnecting and navigation in both
directions. Old services/packets/voice must stop; the itinerary must remain; Back
must not reopen the old console screen. Repeat all Dash profiles and verify their
existing navigation, media and joystick functions.

Local writes, compilation and unit tests do not establish hardware rendering,
reconnect reliability or battery-life improvements.

## Namespace migration

Source and generated R/BuildConfig namespace is com.sxdpz23.navisarthi; installed
application ID remains com.navisarthi.app. The merged manifest resolves activities
and services to the new classes. Existing app-private preferences and route data
remain under the same installed identity. Android notification-listener access
is component-specific: Dash users may need to grant access again for the renamed
listener component after installing an update. Existing permission-recovery UI
uses the new component name. No APK has been packaged as part of this change.
