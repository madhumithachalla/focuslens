# FocusLens security plan

This is the plan for keeping your data safe, what is already in place, and the
decisions still open. It is written so anyone can check the claims against the code.

## What we protect

| Data | Where it lives | Who can read it |
|---|---|---|
| Screen-time history, blocks, journal, tasks, medication, diary | Your phone only (app-private storage) | Only FocusLens on your phone |
| Account (email, name) | Firebase Authentication | You |
| Your private data, if you opt in to account sync | Firestore `users/{uid}` | You only (rules) |
| Accountability pact: partner's name and tasks | Firestore `pacts` | The two people in the pact (rules) |
| Community posts | Firestore `posts` | Signed-in users (rules) |

## Threats and what is in place

1. **Someone else reads your data on the server.** Firestore rules allow each
   person's private data (`users/{uid}`) only to that person. Pacts are readable
   only by the two members. Reports are write-only. *In place.* Check: `firestore.rules`.
2. **A pact partner edits your tasks or rewrites history.** Rules let a partner only
   confirm a task, never change its owner or title, and pacts can only move from
   pending to active or ended. *In place.*
3. **Another app on the phone talks to FocusLens.** Only the launcher activity and the
   Spotify sign-in return (`focuslens://auth`) are reachable from outside. Every other
   activity, service and receiver is not exported. *In place.* Check: `AndroidManifest.xml`.
4. **Plain-http network traffic is intercepted.** The app refuses cleartext traffic.
   *In place (this change).*
5. **Backups copy your data off the phone without you knowing.** Android backup is off.
   *In place.*
6. **Web page injects code through community posts.** Every user-written value is
   escaped before it goes into the page, and the page now sets a content security
   policy that blocks scripts from anywhere except the site and Firebase. *In place
   (this change).* The page has no inline scripts, so the policy does not break it.
7. **Your data is deleted by a structure change.** Usage history and diary live in
   their own files or database, separate from the main database. Before a main-database
   structure change, a copy of the existing data is kept on the phone. *In place.*
   Restoring the copy is not automatic yet, because it needs the older layouts.
8. **Your Spotify or Google login is misused.** Spotify is off until you turn it on.
   Sign-in uses Google and Firebase, and the app stores no passwords. *In place.*

## Decisions still open (need you)

A. **Signing key.** The app is signed with a debug key that is committed to the
   repo (`app/debug.keystore`). Anyone with the repo could sign an update that
   replaces the app on your phone. The fix is a release key kept only in GitHub
   secrets. Changing the key also changes the fingerprint Google sign-in and Spotify
   use, so both must be re-registered. *Decision: approve the switch, and I'll set it up.*

B. **Accountability pact partner visibility.** The `profiles` collection lets any
   signed-in user read a display name and an invite code. Anyone with a code can
   send a pact request, but the other person must accept. *Decision: keep as is, or
   make invite codes expire.*

C. **Publishing the Firestore rules.** The repo's `firestore.rules` only protects
   data after you paste it into the Firebase console. *Action: paste it, then
   check the Rules tab shows the same text.*

D. **Accessibility permission.** FocusLens reads on-screen text only for the
   YouTube and Instagram checks, and keeps nothing except counts. This is
   stated in the app. *Decision: keep, or narrow it further.*

## Checks to run after each release

- `git grep -n "Log.d"` shows only counts and labels, never message text or contacts.
- `firestore.rules` in the console matches the repo file.
- `AndroidManifest.xml`: only `MainActivity` and the Spotify return are exported.
- `docs/index.html` still has the Content-Security-Policy line.
