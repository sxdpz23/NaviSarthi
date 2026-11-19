# Destination sharing

Extension source accepts text/URL/GPX into a protected App Group inbox. Open the app
to preview explicitly; no private host-launch trick. Active guidance defers inbox
processing. On Mac register an App Group for BOTH bundle IDs and run:
APPLE_APP_GROUP=group.your.id bash tools/setup-sharing-macos.sh
The native helper creates/embeds the target/dependency/entitlements. Windows
baseline does not yet embed it. Compile/test sharing from Maps and Files.

Parsing accepts coordinates, Apple/Google destinations, text, geo/navisarthi schemes.
Google short links use bounded HTTPS redirects to trusted Maps hosts; viewport
coordinates are not destinations. GPX limit 10 MB; text 10,000 characters; inbox 20.
Owned-domain universal links need associated domains and hosted AASA; not configured.
