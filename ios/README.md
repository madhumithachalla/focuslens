# FocusLens iOS shell

A native iPhone app that opens the FocusLens website in its own window. Sign-in uses the
Firebase project `focuslens-d5379`; the `GoogleService-Info.plist` in `FocusLens/Resources` is that
project's iOS config (bundle ID `com.focuslens`).

This is a shell, not a native app: the blocking and screen-time features are Android-only, as
noted on the Roadmap.

## Build it (needs a Mac with Xcode 15 or later)
1. `brew install xcodegen`
2. In `ios/`, run `xcodegen generate`. This creates `FocusLens.xcodeproj`.
3. Open `FocusLens.xcodeproj` in Xcode.
4. Under Signing & Capabilities, choose your Team. Apple Developer membership is needed to run on a
   real iPhone (a free Apple ID works for the simulator only).
5. Choose an iPhone simulator or your phone, then press Run.

## Notes
- The shell was written without a Mac, so it has not been compiled yet. Expect small fixes on the first build.
- Google sign-in can be blocked inside embedded browsers. Email sign-in works in the shell.
