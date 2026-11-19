# Android collection sync

Configure your Firebase project/public Web API key and sign in. Explicitly enable
sync and select the motorcycle representing Android's single bike. Sign-in disables
sync pending opt-in. REST maps state/bike, fuel, services, saved, rides, maintenance
under users/{uid}; IDs are preserved/deterministically converted, update masks keep
unknown remote fields. Vehicle identity/documents/invoices/expenses and iOS-only
fields remain in full snapshots. Tracks use polyline precision 5.

Per-owner/motorcycle links retain baseline hashes/update versions. Three-way policy
propagates one-sided edits/deletions; concurrent changes require Local/Cloud choice.
Known records absent from listings get individual checks before deletion. Writes
use preconditions; edits during awaits are checked before transactional apply.
Initial differing unmatched data can conflict rather than silently overwrite.

Local checks run roughly every 10 seconds, remote polls 120 seconds while the app
task runs, not an unlimited background service. Existing owner-only rules must
permit paths; no backend/rules migration was performed. Normal usage charges apply.
Explicit cloud snapshots are separate atomic checksummed commits limited to 4 MB;
older chunks remain. Local files support 100 MB. Credentials remain in Keychain.

No live account tests ran. Test first sync, concurrent edits/deletes, account
switches, interrupted commits and denied permissions with disposable records.
References: https://firebase.google.com/docs/reference/rest/auth and
https://firebase.google.com/docs/firestore/use-rest-api
