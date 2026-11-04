# Google Drive backup and restore setup

## Google Cloud configuration

1. Create a Google Cloud project for NaviSarthi and enable the Google Drive API.
2. Configure the Google Auth Platform consent screen. Use an External audience for personal Gmail accounts and add the testers while the app is in Testing.
3. Create an Android OAuth client for com.navisarthi.app using the SHA-1 fingerprint of the certificate that signs the APK. Register debug and release signing certificates separately if both builds will be tested.
4. Request https://www.googleapis.com/auth/drive.file through Google Identity authorization. The app asks for consent on the phone. Keep tokens and any future backend client secret out of the APK, backups and logs.

## App flow

- Settings offers Connect Google Drive, Back up now, a backup frequency, Restore and Disconnect. Automatic backups are opt-in.
- Create a visible NaviSarthi folder in My Drive if an app-authorized folder does not already exist. Identify it by Drive ID and app metadata after creation. If an existing folder is inaccessible under drive.file, offer a picker or create an app-owned folder.
- Write a versioned, timestamped JSON snapshot of supported app data. Inventory the database and preferences before implementation so every intended record type is covered. Exclude credentials, OAuth tokens and transient caches. Upload under the folder ID. Record success only after Drive confirms the upload.
- Use Android scheduled work with an internet constraint. Try authorization without UI. If Google requires user interaction, mark the backup as paused and surface Reconnect in Settings. If uploads must continue independently of the phone, configure a backend with Google offline authorization and secure refresh-token storage.
- Restore by authorizing the same Google account, listing the JSON snapshots in NaviSarthi, and showing available dates and record counts. Use one complete snapshot per restore so old snapshots cannot reintroduce deleted records. Validate the schema and relationships before writing. Preview the affected data, confirm with the user, then apply the restore in a transaction and refresh the screens.
- Test first upload, repeated backups, reinstall and restore, malformed JSON, interrupted uploads, revoked access and switching accounts.

## Status

Development build dev05 includes phone-only Drive authorization, manual and daily backup, and transactional restore of database records and attached files. Application settings and credentials are outside this snapshot version. The app opens directly to Home. A signed-device test is still required for Google consent, backup upload, daily execution, and restore.

## Registered test project

- Google Cloud project ID: navisarthi
- Android OAuth client ID: 1017723033990-2c14iqm4lh05hj59gq9b0jld6acldhd4.apps.googleusercontent.com
- Package: com.navisarthi.app
- dev04 APK SHA-1: 2B:98:CF:93:C4:25:F9:CF:A4:86:9E:58:30:F4:EE:2F:03:12:95:CD

Drive API enablement was confirmed by the project owner. Confirm consent and test-user access by authorizing Drive from a signed APK on the phone. The Android client registration is matched by package and signing certificate. The client ID is recorded here for troubleshooting.


## Data inventory for implementation

- Back up rides and their recorded tracks, saved locations, fuel fills, maintenance items, service history, scheduled services, odometer state and bike identity.
- Document records and service invoices can refer to phone-local files. Store those attachments with their data in the snapshot and remap the references on restore. A path alone cannot restore a file to a new phone.
- Restore user preferences from an explicit allowlist. Exclude the dash Wi-Fi password, auth state, device identifiers, diagnostic logs and transient caches.
- Treat backup and restore as a separate feature from Firebase sync. Test restore with sync enabled so local and cloud changes reconcile without duplicating records.
