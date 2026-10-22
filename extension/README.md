# FocusLens Distraction Blocker (browser extension)

The website counterpart of the FocusLens Android blocker. A web page can't block other sites, so
this is a real Manifest V3 extension for Chrome and Edge (Firefox 128+ also reads the manifest).

## Install (unpacked)
1. Download `focuslens-blocker-extension.zip` from the FocusLens site, or use this folder, and unzip it.
2. Open `chrome://extensions` (Edge: `edge://extensions`), switch on **Developer mode**.
3. Click **Load unpacked** and choose the folder that contains `manifest.json`.
4. Pin the extension and open its popup to add your first site.

## What it does
- **Blocklist** with preset packs (social, video, news, shopping, games, adult) and your own sites.
  Subdomains are covered. Blocked pages redirect to a friction screen.
- **Friction screen:** a countdown (0/5/10/30 s), "Take me out of here", emergency passes
  (default 3 a day, each allows one site for 10 minutes).
- **Question gate:** answer questions before continuing. Math and pattern questions are generated; there
  are also word, science, general and wellbeing questions. Difficulty rises as the day goes on (each pair
  of attempts starts you one level higher) and within a gate (two right in a row goes up, a miss goes down).
  You can also write your own 3-question quiz on a topic you're learning.
- **Schedules** (e.g. 07:30 to 10:30 on weekdays, crossing midnight supported), **focus mode** for 25/50/90 minutes,
  and **strict mode**, which locks settings and switches off passes while a schedule or focus session is active.
- **Stats** for the last 30 days, kept on your device.

## Privacy
Everything is stored in `storage.local` on your device. No network requests, no analytics, no accounts.
The extension needs access to all sites only so it can redirect the ones you block.

## Limits
- It can't block other apps (use the FocusLens Android app) and can't stop you disabling the extension unless
  your browser is managed by an administrator.
- Private/incognito windows only block if you allow the extension there.

## Development
`node --test test/logic.test.mjs` runs the logic tests. `quiz-engine.js` is a byte copy of `docs/quiz-engine.js`
in the web app; the test compares them when both are present.
