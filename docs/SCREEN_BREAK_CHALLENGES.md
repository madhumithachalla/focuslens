# Screen-break challenges

Two optional, opt-in modes. Nothing starts unless the user chooses it.

## Modes

**NoBlue for 20 Hours.** A strict challenge for 20 hours away from the device. Every app is paused except the essential apps the user keeps (calls, transport, WhatsApp and similar), plus FocusLens itself.

**Semi-Blue for a Day.** A flexible day. Only the social apps the user picks are paused. Essential apps stay open.

Both modes:
- Explain what the mode does before it starts.
- Can be paused, resumed or ended at any time. Ending early is never a penalty.
- Have an emergency or essential-use override that turns blocking off for 5 minutes.
- Have no streaks, scores, guilt messages or failure state. Finishing the full time is a completion.

## Honest limits

Enforcement uses the existing FocusLens accessibility-based blocking. It can be bypassed, for example by force-stopping FocusLens or disabling its accessibility service. The app must not claim an app is blocked when the blocker has not actually applied it.

## Permissions and data

- Accessibility service (already in the app): used to notice which app is in front and to pause it. Requires a Play Console Accessibility declaration (see below).
- Usage access (already in the app): used to show time in each app.
- Display over other apps (already in the app): used for the pause screen.
- No new sensitive permissions are proposed.
- Challenge settings and personal reflections stay on the device. Nothing is synced.

## Google Play status (checked by web search, 13 November 2026)

- New personal developer accounts created after 13 November 2023 must run a closed test with at least 12 testers, opted in continuously for 14 days, before applying for production access. Source: <https://support.google.com/googleplay/android-developer/answer/14151465>
- Apps that use the Accessibility API for a non-accessibility purpose must declare it in Play Console and disclose it prominently. Source: <https://support.google.com/googleplay/android-developer/answer/10964491>
- The exact wording for a wellbeing app that pauses other apps was not found in search results. Check the full Accessibility policy in Play Console before submitting.

## 20 Hours for 20Talk

The feature is inspired by the owner's participation in 20 Hours for 20Talk. It is not an official 20Talk feature, partnership or endorsement, and it is not a replacement for the event. No 20Talk logo or branding is used without documented permission.

Fundraiser link: **[PLACEHOLDER: verified personal fundraiser URL, not in repository]**. Add it only after it is verified. Donations are not required, and no donation collection happens inside FocusLens.

## Status

- `ChallengeLogic.kt`: pure rules for both modes. Built, not compiled (no Android SDK in the working environment).
- `ChallengeLogicTest.kt`: unit tests for the rules. Written, not run (test dependencies not wired).
- Not built: setup screen, active-challenge screen, wiring into the blocker, Play beta release.
- Device testing: outstanding. No Android device was available.

## Remaining steps

1. Install the Android SDK and wire JUnit into the app module, then run the unit tests.
2. Build the feature screens in the existing Compose design system.
3. Wire the challenge rules into `FocusAccessibilityService`.
4. Test on a real device with 18–30 year old testers, with voluntary participation.
5. Complete the Play Console Accessibility declaration and Data safety form.
6. Create a Play internal or closed testing release. Use the Play test link.
7. Public production release stays separate and is not part of this beta.
