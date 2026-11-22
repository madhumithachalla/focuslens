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
| Track my usage on this phone: yes = snapshots every 6 hours, no = Digital Wellbeing numbers | Built | Consent on first launch and in Settings. Not checked on a phone yet |
| Instagram open interception: each open shows breathing or a maths question, alternating; Not now counted | Built | Not checked on a phone yet |
| Daily limit and reel cap: locked 12 hours from when it is used up | Built | Quiz unlock still works on the lock screen. Break-count lock stays until 6am. Not checked on a phone yet |
| Break after 1 hour of scrolling, 1 hour break (defaults, changeable in Focus) | Built | Not checked on a phone yet |
| Emergency pass: 5 minutes (was 10) | Built | Not checked on a phone yet |
| Redesign, Focus tab: status card, two stat tiles, details hidden under Details | Built | First screen of the redesign. The rest of the design boards are not built yet. Not checked on a phone |
| Pomodoro: focus phase (default 25 min) then break (default 5 min), repeating; social media apps and blocked apps paused in focus | Built | Social media apps are always included. Turn on and set lengths on the Focus tab. Not checked on a phone |
| Study room: one on one or group, join at random or with a code, invite, shared 25-min timer, random backdrop. Anonymous, no chat | Built | Needs the updated firestore.rules deployed in the Firebase console first. Not checked on a phone |
| Accountability screen: redesigned (dark cards, one partner card, your code with Share, tasks as tappable circles, short status text) | Built | First screen of the full design change. Same Firestore logic as before. Not checked on a phone |
| App-wide dark theme (design-board colours) | Built | Screens with their own light colours may still need a pass. Not checked on a phone |
| Updates screen and update messages show the version name only (no build number). Release 1.9.0 | Built | Build number stays internal, for update checks |
| Launcher: button opens the home app setting | Built | Not checked on a phone |
| Reels count: each reel counts when you swipe to the next and watch it 5 seconds; leaving Instagram ends the count | Built | Was one count per Reels visit. Not checked on a phone |
| Usage "Over time" shows a line chart | Built | Not checked on a phone |
| Light or dark colours (Settings, Light colours switch); dark is the default | Built | Greyish secondary text is still the same grey in both modes. Not checked on a phone |
| Focus tab Reels tile: reels today, minutes watched, reels left if a limit is set | Built | Not checked on a phone |
| Focus score: today from social media minutes vs yesterday, reels watched and stepping back; each part shows its number; up to 3 tips matched to the weakest part (on-device, no AI model) | Built | Not checked on a phone. An LLM or RAG model is not included: it would send usage data off the phone, which needs your explicit decision first |
| Search icon in the top bar: finds any page by name or keyword (water, period, habit, mood) | Built | Not checked on a phone |
| Symbols: water drops, mood faces, energy and sleep symbols, period drop, habit ticks and crosses | Built | Not checked on a phone |
| Feed reels: covered when the Reels switch is on, including full-screen reels in the feed | Built | Not checked on a phone. Audio can still play under the cover |
| Usage line graph: Today uses 24 hourly points so the line shows; longer ranges use one point per day | Built | Not checked on a phone |
| Medication: "+ Add a medicine" at the top with a how-to line; an empty state that says what to do | Built | Not checked on a phone |
| Medication history: "Days you took it" section under Today, with a 4-week grid for each medicine (taken, missed, not scheduled) | Built | Not checked on a phone |
| Group tasks in a study room: anyone in the room adds a task and ticks it off; only the person who added it can remove it. Rules updated | Built | Needs the updated firestore.rules published. Not checked on a phone |
| Week planner: seven themed days from the notebook (cooking, laundry, cleaning, career, job applying, content, fun), and a Money template | Built | Days can be renamed in the planner. Not checked on a phone |
| Week planner: fixed standard day names (Planning and admin, Money, Future you, Maintenance, Fun, Meal prep, Fun and self-care); each day is picked from the list, not typed. Default week set to these | Built | Not checked on a phone. Nightly and Sunday planning slots are suggested, not built |
| Quiz questions fetched from Open Trivia Database every 6 hours, cached, mixed with the built-in bank | Built | Needs internet. Not checked on a phone |
| Badges grouped by Journal, Tasks, Check-ins, Focus, Rewards; Focus badges count stepping back | Built | Not checked on a phone |
| Journal friendlier: "Your journal" subtitle, warmer headings, kinder empty state | Built | Partial redesign; the entry editor layout is unchanged. Not checked on a phone |
| Period tracker: next period shown as a range from your cycle lengths; a Period phase for the first 5 days | Built | Still a simple average, not Flo's model. Not checked on a phone |
| Spotify Playlists stuck on "Connecting": client ID is set, the connection hangs. Needs the dashboard check and a Copy report | Not fixed | Needs logcat or a Copy report |
| Interception questions: three per try from different categories (books, movies, vocabulary, general knowledge, English, maths), changing each time; alternates with breathing | Built | Question bank is on the phone. Live questions from online sources are not connected yet. Not checked on a phone |
| 20 hours away: Start on the Focus tab. Everything pauses except the apps you keep open, for 20 hours. End early, no penalty. Inspired by 20 Hours for 20Talk, not an official feature | Built | Not checked on a phone. The 5-minute emergency override is not in this card yet |
| Settings: Copy report button (version, device, blocking status, recent FocusLens log lines) | Built | Use it to send the developer what happened on the phone |
| Home launcher: search box open from the start, type an app name and tap it. Set it as home in Settings > Apps > Default apps | Built | Not checked on a phone |
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
| 20Talk fundraiser link | Built | Owner-provided URL in the docs. Owner confirmed it opens |
| Google Play internal or closed beta | Blocked | Needs Play Console access, release signing key and the Accessibility declaration |
| Device testing of challenges | Not started | No Android device available |

## Build verification (this branch)

| Item | Status | Notes |
|---|---|---|
| Gradle wrapper pinned to 8.14.4 (same as CI) | Built | Not run locally: no Android SDK in the working environment |
| JUnit added to the app module; CI runs `testDebugUnitTest` before the APK build | Built | Run in CI on this branch. Result pending |
| Publish step only runs on `main` | Built | Branch builds compile and test but do not push an APK |
| Existing features checked on a phone | Not started | Needs a device run through the checklist in the plan |
| Limits use drag-and-scroll pickers (doom-scroll, reels per day, quiz unlocks, emergency passes) | Built | Replaced +/- buttons with a shared WheelPicker. Compiles in CI on this branch; not device-tested |
| Release 1.8.1 (versionCode 10801), changelog entry added | Built | Not device-tested. Build number entry is 0 until CI assigns one |
| Keep all five tabs' extra pages (Learn, Explore, Guide, Birthdays, Playlists) | Decided | Owner: keep them all |
| Keep-going daily cap = 2 | Decided | Owner's suggestion. Design only; not yet in the app |
| Palette near-black, lilac-indigo accent, light view | Decided | Owner's suggestion. Design only; the app theme is not changed yet |
