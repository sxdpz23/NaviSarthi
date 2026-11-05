# Native music protocol findings from OGRE.apk

Original package: `com.royalenfield.reprime`, version `10.1.23(45)` (code 193).

APK supplied by the user: SHA-256 `D08478E8C564624503B440834A40499CD4ACDC1C8572E49FE1B4830472AD37A4`.

These are interoperability facts from static inspection, not copied application implementation. Decompiled source remains in ignored `.gradle/ogre-analysis/`. APK was not executed. No credentials or unrelated application content are included.

## Confirmed commands

Each row is a K1G TLV (`type sub length value`); the existing packet builder supplies the outer header and rolling sequence.

| Purpose | TLV |
| --- | --- |
| Music available | `05 17 00 01 55` |
| Music unavailable | `05 17 00 01 AA` |
| Player playing | `05 19 00 01 55` |
| Player paused | `05 19 00 01 AA` |
| Metadata | `05 0D [length:2 big endian] album 00 title 00 artist` |

Evidence: `bluconnect.djf.e.f(PlaybackStateCompat)` first sends `w2c.l1`, then `w2c.i1` for Android state 3 (playing), or `w2c.j1` for state 2 (paused). `djf.A1(MediaMetadataCompat)` sends `w2c.m1` when all metadata is absent, otherwise builds album/title/artist. Field constants were independently read from the DEX static-value table for `bluconnect.w2c` in classes8.dex: l1=05/17/55, m1=05/17/AA, i1=05/19/55, j1=05/19/AA.

`djf.A1` limits populated text fields to 19 UTF-16 units. `bluconnect.l3d.n` encodes each unit as one byte when <=255 and FF otherwise. It is not UTF-8. There is no terminal NUL after artist. Embedded NUL is sanitized by NaviSarthi to protect field boundaries.

## Diagnosis and implementation

NaviSarthi startup sent music-unavailable (05/17/AA), but never reversed that state when metadata became available. It also omitted playback-state (05/19) entirely. This is a concrete protocol discrepancy and a strong explanation for the blank native ribbon and absent play/pause glyph; actual display behavior still requires hardware verification.

0.4.0-dev05 sends availability, actual playback state, then metadata while streaming. Loss of metadata or disabling music sends unavailable and clears stale metadata. Immutable snapshots keep fields and playback state from different songs from mixing across threads. Existing transport mappings, source selection, and phone audio routing are retained.

The original app's state callback sends updates on events. NaviSarthi repeats the state with its existing one-second metadata cadence to recover UDP loss and reconnects. It never optimistically changes the icon solely because a command was dispatched.

## Hardware acceptance

With Spotify and YouTube Music, verify title left, artist right, pause icon during playback and play icon while paused; test both middle press and phone playback controls. Switch tracks, pause before connecting, reconnect, revoke notification access and disable music. Check ribbon clears without interrupting navigation; retain working left/right and volume controls. Unsupported non-Latin-1 characters follow the reference's FF substitution, not full Unicode support.

Targeted decompilation and DEX static-value inspection confirmed the relevant classes. Broad decompilation was stopped after the required evidence was extracted; no claim is made that all application methods decompiled successfully. The constant strings were also verified in locally decompiled w2c.java (i1:459, j1:474, l1:504, m1:519).

Validation: dev05 assembleDebug and testDebugUnitTest passed (31 tests, 0 failures, 0 errors). Golden tests match full availability and playback-state packets, field order, one-byte encoding and field lengths. Console rendering remains unverified.


## dev06: native zoom, album covers and recent calls

- Zoom: w2c.M2/N2/O2 are 06/0C values 10/20/30. NavigationRootFragment.X7 (zoom in) sends O2 while in range and N2 near maximum; Y7 (zoom out) sends O2 in range and M2 near minimum. Therefore 10 enables plus only, 20 minus only, 30 both. NaviSarthi supplies its own 11..20 range rather than Google's map zoom limits.
- Album art: djf.A1 extracts a 75x75 thumbnail; h1/e1 compress JPEG quality 50. c3c.a constructs 05/40 payload `[00][total JPEG length:BE16][first <=1000 data bytes]`. c3c.e uses `[fragment index][<=1000 data bytes]` in intermediate packets and `[FF][remaining data bytes]` for the last. Each packet has a 10 ms gap. A single chunk also gets an empty final FF packet. NaviSarthi handles exact multiples of 1000 without dropping the final full chunk (the reference's remainder handling is defective for that boundary). Generated source remains local and ignored.
- Recent calls: b52 delegates category fetch to r42.j/t. 09/01 request value AA/AA=incoming, AA/BB=missed, AA/CC=outgoing (AA/55=favorites, outside requested history scope; return empty slots). r42.p assigns display slots 05/1C through 05/20. Each is encrypted with the negotiated 32-byte AES key: fresh 16-byte IV, then AES-CBC padded ciphertext of reference byte text plus a NUL. r42.v confirms selected category with 05/23 value AA, BB or CC. omf.g supplies encryption; omf.j confirms the same key goes into the SSID/RSA session handshake. NaviSarthi uses standard AES/CBC/PKCS5Padding, equivalent padding for the AES block size.
- Opt-in sharing reads the latest five cached names/numbers only from the requested category, never writes phone history or initiates calls. Five slots are always sent (unused slots encrypted empty), and disabling/revoking permission clears the displayed list. While streaming, a five-second refresh checks access and updates the last requested category when labels change. Requests selecting a specific call are not implemented as dialing commands.
- Cover transfers restart for changed artwork and reconnects; identical bytes are not resent. Missing or unreadable artwork sends a generated neutral music tile so the previous song's cover is not retained. Player-owned source bitmaps are never recycled.

Device acceptance for dev06: verify native minus/plus at intermediate/minimum/maximum zoom, art with Spotify/YouTube Music including tracks lacking art, rapid track changes and reconnects, history with fewer than five calls, category switching, denied/revoked permission and sharing disabled. Android may deny restricted call-log access; the app reports this and sends empty slots. This is not a request to become the default dialer.

Dev06 validation: final debug build and 38 JVM tests passed with 0 failures. Artifact: dist/NaviSarthi-0.4.0-dev06.apk. Signature matches prior debug previews; native display and permission behavior require device testing.


## dev07: favorites, console dialing and missed-call-only count

Favorite requests use the existing AA/55 category; query starred phone contacts, choose one preferred number per contact and send five encrypted label slots. Names/phone numbers stay in memory. READ_CONTACTS, favorite sharing, CALL_PHONE and dialing are separate opt-ins. Existing READ_CALL_LOG/history sharing remains independent.

Original r42.q maps 09/01 payload `[index:00..04][category:55/AA/BB/CC]` to favorites/incoming/missed/outgoing. NaviSarthi resolves that selection strictly against the last completed displayed snapshot, not a fresh query; numbers are invalidated during changed transfers and on access loss/disconnect. Unknown/empty/private rows are not dialed. TelecomManager.placeCall uses the selected tel number without audio-route overrides; a ten-second cooldown suppresses repeated selection events. Requests during an observed incoming/active call are blocked. Android may show SIM/account selection or deny a call; dispatch success is not a connected-call guarantee.

Original r42.n queries `type=3 AND new=1`; r42.e with signal 060A serializes the missed-call count as BE16. NaviSarthi clamps to 0..65535 and resets to zero when the badge is disabled or call-log access is unavailable. It does not update NEW/IS_READ on the phone. No 06/09 unread-message packets, SMS permissions or inbox access are added.

Acceptance: favorite ordering/preferred numbers and fewer than five rows; native favorite/history selection calls the displayed contact; duplicate selections do not redial; grant/deny/revoke each permission independently; disable sharing before selecting a stale row; phone lock/background behavior; dual-SIM system selection; missed badge changes with phone call-log NEW flags and clears on disable/revocation. Real calling requires explicit device testing by the user and was not invoked by automated tests.
