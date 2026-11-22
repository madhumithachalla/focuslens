# Setup checklist

Everything the app needs from you that can't be set up by code. Tick each item when it's done.
Update this file when something changes. The build also warns about missing secrets.

## Needs you (not done yet)

- [ ] **Firestore rules.** Paste `firestore.rules` into the Firebase console (Firestore, then Rules), then Publish. Study rooms and accountability won't work until this is done.
- [ ] **Home app.** On the phone: Settings, Apps, Default apps, Home app, FocusLens. The launcher only opens from Home when this is set. Or use the "Make FocusLens my home app" button in the launcher.
- [ ] **Permissions on the phone.** Accessibility (FocusLens on), Usage access, and battery set to "Unrestricted" for FocusLens. Without these, blocks and usage don't work.
- [ ] **Spotify.** Add the `SPOTIFY_CLIENT_ID` secret in GitHub (Settings, Secrets and variables, Actions). In the Spotify developer dashboard, check the package name `com.focuslens`, the debug SHA-1 fingerprint, and that your account is under User Management. If it still fails, send a Copy report from Settings.
- [ ] **Google sign-in.** Turn on Google in Firebase (Authentication, Sign-in method), then add its Web client ID as the `GOOGLE_WEB_CLIENT_ID` secret.
- [ ] **Signing (verified commits).** Commits on branches show as unverified. Merge through a pull request to get verified commits on `main`, or register a signing key on your GitHub account.

## Decisions waiting on you

- [ ] **Live interception questions** from online sources such as Open Trivia Database. Needs internet and which sources to use.
- [ ] **AI model (LLM and RAG).** Needs your choice of provider, an API key, and consent for usage summaries to leave the phone. Nothing is connected yet.
- [ ] **Navigation.** Five tabs with "More", or keep the current menu.
- [ ] **Reels in the feed.** Cover only (sound can still play), or leave Instagram when a feed reel shows.

## Done

- [x] Build on `main` publishes the APK (PR #2 and PR #3 merged).
- [x] Pomodoro counts continuous use, not the clock, and skips LinkedIn by default.
- [x] Interception: three questions per try from six categories, plus breathing.
- [x] Study room and accountability code (waiting on the rules above).

## Not built yet

- [ ] Body, Usage and Reels redesign to match the design boards.
- [ ] Device check of the Reels counter, the feed cover, and blocks inside apps.
