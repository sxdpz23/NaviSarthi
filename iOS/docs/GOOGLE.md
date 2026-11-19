# Google sign-in and Drive

Use an iOS OAuth client for your final bundle ID/project. Enable Firebase Google
sign-in, Drive API and consent/test users as needed. On Mac run:
GOOGLE_IOS_CLIENT_ID=your-client.apps.googleusercontent.com bash tools/setup-google-macos.sh
The helper adds client ID/reversed callback. ASWebAuthenticationSession uses state
and PKCE, no client secret. Refresh tokens stay in Keychain; Firebase exchanges
Google identity via signInWithIdp.

Drive consent is explicit/separate, drive.file scoped: files must be accessible to
this OAuth application; another client's Android files are not automatically visible.
My Drive/NaviSarthi stores backups. ios-backup- names prevent Android mistaking the
extended format for its own. Transfers limit 64 MB; temporary uploads are protected.
Optional BGProcessingTask daily backup is opportunistic, with 24-hour earliest time.
Expired consent needs foreground reconnect; disconnect stops scheduling.

No live Google access tests ran. Test consent/account mismatch, interruption and
restore. Reference: https://developers.google.com/identity/protocols/oauth2/native-app
