# FocusLens: pending list

Keep this file current. Every push that changes a status updates its row.

Status key: **Built** = in code and compiles in CI. **Device-tested** = you have run it on a phone. **Broken** = known not working. **Not built** = not started. **Blocked** = needs an action from the owner.

## Blocking and focus

| Item | Status | Notes |
|---|---|---|
| App, feature and Reels blocking, reasons, counts, quiz unlock | Built | Device-tested in earlier versions |
| Reels blocking for Facebook, YouTube Shorts, TikTok, Snapchat | Built | Only Instagram confirmed. Snapchat and TikTok need device samples |
| Skip reel pop-up with Ignore (10 minutes) | Removed | Replaced by reel covers (`938b96d`) |
| Reels restricted: full card on the Reels screen (Back, no kick-out); reel covers in the feed | Built | Not device-tested. Instagram only for covers |
| Reels "even better" | Not built | Owner to define what should change |
| Phone lockdown (daily and peak-hour limits, allowed apps) | Built | Not device-tested. Settings, dialler and FocusLens always stay open |
| Monochrome mode | Not built | |
| Notification filter | Not built | |
| General short-video block (Facebook, Snapchat) | Not built | |
| Blocking screens show a FocusLens mark | Built | Not device-tested |
| Blocking screens do not freeze the phone | Built | Needs a real-phone check |
| Stale app no longer blocked over FocusLens (accidental kick-outs) | Built | Fixed in `1406599`. Needs a real-phone check |

## Usage

| Item | Status | Notes |
|---|---|---|
| Usage totals match Digital Wellbeing (last 7 days) | Built | Uses Android's own totals. Not checked against your phone |
| Time stops counting when the screen turns off | Built | Not device-tested |
| "Inside the app" shows measured coverage | Built | |
| Peak hours from usage history | Built | Needs several days of history |

## Home screen

| Item | Status | Notes |
|---|---|---|
| Minimal launcher: plain-text apps, favourites, search and notification gestures | Built | Not device-tested |
| Hide apps | Built | Not device-tested |
| Mindful launch delay (15 seconds) | Built | Not device-tested |
| Clock formats, shortcuts, A–Z index, colour themes, fonts | Not built | |

## Planning and tasks

| Item | Status | Notes |
|---|---|---|
| Daily planner: tasks, intention, morning and evening journals | Built | Not device-tested |
| Student and adult categories; task, event and reminder sections | Built | Not device-tested |
| Reminders go off at their time | Built | Not device-tested. Needs exact-alarm permission |
| Week planner: themes, templates, next-up task, routines, calendar conflicts | Built | Not device-tested |
| Week planner: your five days | Blocked | Owner to type them in |
| Week planner: calendar check on planning day | Not built | Currently only confirms the calendar is connected |

## Health and mind

| Item | Status | Notes |
|---|---|---|
| Medication: habit grid, monthly and date-range schedules | Built | Not device-tested |
| Mental health chart and wording | Built | Not device-tested |
| Sleep tracking, estimated from screen use | Built | Not device-tested. Off by default |
| Sleep check-in when you come back to the phone | Built | Not device-tested |
| Sleep: alarm-dismiss signal | Not built | |
| Sleep: charging signal | Not built | |
| Hydration, reading, diary, accountability pact | Built | Not device-tested |

## Setup and tools

| Item | Status | Notes |
|---|---|---|
| Setup checklist (permissions with live status) | Built | Not device-tested |
| App version shown as 1.0.0 (build N), changelog grouped by version | Built | Bump `versionName` by hand per release |
| Country for helplines: detected from the phone, then asked once | Built (detection only) | Not yet shown to the person; the helpline card is not built |
| Spotify connection | Broken | "Explicit user authorization is required". Fix is in the Spotify dashboard and on the phone |
| Firestore rules | Blocked | Owner to publish in the Firebase console |
| Restore old data | Blocked | Needs older database layouts |
| "App name under the icon" | Blocked | Owner to say which screen and what text |

## Claude tools

| Item | Status | Notes |
|---|---|---|
| `focuslens-screens` skill (website screenshots) | Built | Working in this environment |
| `claude-skill-android-run` (public repo, plugin and installer) | Built | Not run on a real device |
| Claude Design redesign | Partly built | 2 of about 30 screens. Canvas index was overwritten once |

## Decisions for the owner

- Release signing key
- Invite-code expiry
- Whether the quiz can override the daily limit
- Sleep: minimum quiet time (currently 1.5 hours, an assumption)

## Last build

Builds #148 to #152 passed compilation. Compiling is not the same as working on a phone.

## Screen-break challenges (beta work)

| Item | Status | Notes |
|---|---|---|
| NoBlue for 20 Hours and Semi-Blue for a Day: rules (`challenge/ChallengeLogic.kt`) | Built | Not compiled: no Android SDK in the working environment. See `docs/SCREEN_BREAK_CHALLENGES.md` |
| Challenge rules unit tests (`ChallengeLogicTest.kt`) | Built | Not run: test dependencies not wired into the app module yet |
| Challenge setup and active screens, app picker for Semi-Blue | Not built | Needs the Android SDK to compile |
| Wiring the challenge into the accessibility blocker | Not built | Must reuse existing blocking; do not claim blocking without it |
| 20Talk fundraiser link | Blocked | No verified fundraiser URL in the repository. Placeholder in the docs |
| Google Play internal or closed beta | Blocked | Needs Play Console access, release signing key and the Accessibility declaration |
| Device testing of challenges | Not started | No Android device available |
