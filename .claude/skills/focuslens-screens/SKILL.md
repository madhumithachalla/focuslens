---
name: focuslens-screens
description: Run the FocusLens web app locally and capture its screens as phone-size PNGs, so UI and UX can be compared with designs. Use when asked to run, screenshot, or compare the FocusLens UI. The Android APK cannot run in this environment; this covers the website in docs/.
---

# FocusLens screens

Runs the static website in `docs/` on a local port and saves phone-size screenshots
to the scratchpad, so they can be compared with the Claude Design canvas.

## Run it

```
node .claude/skills/focuslens-screens/scripts/capture.js [out-dir] [route ...]
```

- `out-dir` defaults to the scratchpad folder `focuslens-screens`.
- Each `route` is a path under `docs/`, for example `index.html` or `index.html#focus`.
  With no routes, it captures `index.html` only.
- Output: one PNG per route at 390x844 (phone), at 2x scale, plus a full-page PNG.

## Limits (say these when reporting)

- This is the website, not the Android app. Blocking screens, the minimal home screen,
  the launcher, and anything that needs Accessibility or Usage access are not shown.
- The APK cannot be installed or run here: there is no emulator or `adb`.
- Headless browser: fonts and hover states can differ from a real phone.

## Requirements

- `playwright` (preinstalled, uses the bundled chromium at /opt/pw-browsers).
- Python 3 for the local server (`python3 -m http.server`).
