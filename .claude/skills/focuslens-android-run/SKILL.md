---
name: focuslens-android-run
description: Build, install, launch and inspect the FocusLens Android app on a desktop with Android Studio, an emulator or a USB phone. Use when asked to run the app, check a screen, read FocusLens logs, or take an app screenshot on a real device or emulator. Needs a desktop with Android Studio and adb; it cannot run in the cloud container.
---

# FocusLens on Android (desktop)

Runs the real app, not the website. Use it on the desktop where Android Studio is installed.

## One-time setup

1. Install Android Studio. Let it install the Android SDK (Settings → Android SDK) and JDK 17.
2. Open the repo folder in Android Studio. It creates `local.properties` with `sdk.dir`.
3. Create an emulator (Device Manager, Medium Phone, API 34 or newer), or enable USB debugging on your phone.
4. Check `adb` is on the PATH (`adb version`). Android Studio's platform-tools folder usually holds it.

## Run it

From the repo root:

```
bash .claude/skills/focuslens-android-run/scripts/run.sh devices    # list connected devices
bash .claude/skills/focuslens-android-run/scripts/run.sh build      # gradle :app:assembleDebug (same as CI)
bash .claude/skills/focuslens-android-run/scripts/run.sh install    # install the built APK on the device
bash .claude/skills/focuslens-android-run/scripts/run.sh launch     # open FocusLens
bash .claude/skills/focuslens-android-run/scripts/run.sh logs       # FocusLens log lines (Ctrl+C to stop)
bash .claude/skills/focuslens-android-run/scripts/run.sh shot       # screenshot to the scratchpad
bash .claude/skills/focuslens-android-run/scripts/run.sh all        # build, install, launch, screenshot
```

If more than one device is connected, set `ADB_SERIAL=<serial>` first.

Or in Android Studio: pick the emulator or phone in the toolbar, then press Run ▶ on `app`.

## Notes

- The build needs `local.properties`. Spotify and Google sign-in values are optional; without them, those features show a "not configured" message.
- Screenshots use `adb exec-out screencap`. They are the real screen, so blocking and overlay screens appear too.
- Logs use the `FocusLens` tag. Reels checks print lines starting with `feature check:` and blocks print `block shown:`.
- Usage access, Accessibility and "Display over other apps" must be granted by hand on the device the first time. `run.sh` cannot grant them.
