# FocusLens

An Android app that shows where your phone time really goes and helps you cut it down.
It reads the same Android usage data that Digital Wellbeing uses, blocks apps you choose
with short interactive challenges, and exports everything it can see as CSV files.

Status: personal project, early version. It has been built from source but only lightly
tested on one phone (Pixel 6a, Android 16).

## What it does

**Usage tab**
- Time in each app for Today, Last 7 days, Last 6 months and All available
- Tap an app to see time by screen (for example WhatsApp calls vs chats)
- Insights card, plus a plain explanation of why phone numbers can look wrong
- Export everything as a ZIP of CSV files saved to a place you choose

**Focus tab**
- Pick apps to limit and set a daily limit for each
- Opening a limited app shows a pause screen first: a breathing exercise, maths
  questions, or typing a sentence. It gets harder the more you open the app, and
  harder again once you are over your limit
- "Not now, take me home" counts as a win

**In-app sections (experimental)**
- Measures time in Reels, Feed, Stories, Messages and Explore (Instagram) and Shorts,
  Home and Watching (YouTube) by reading which tab is selected
- Depends on how those apps build their screens, so an app update can break it

## Permissions

- **Usage access:** read app usage. Same data as Digital Wellbeing.
- **Accessibility service:** detect which app is open so the pause screen can appear.
  For Instagram and YouTube it also reads which tab is selected. It keeps only internal
  element names and a fixed list of tab words. It does not store text, messages or posts.
- Nothing leaves the phone. There is no account, server or network code.

## Download

Not on the Play Store yet, but every push to `main` auto-builds a real, installable
debug APK: [download the latest APK](https://github.com/madhumithachalla/focuslens/releases/latest/download/focuslens-debug.apk).
It's unsigned/sideloaded, so Android will show a Play Protect warning before
installing — tap "More details" then "Install anyway" to proceed.

## Run it

1. Install Android Studio and open this folder. Let Gradle sync.
2. Turn on Developer options and USB debugging on the phone, plug it in, press Run.
3. Open the app, grant Usage access, then use the Focus tab to turn on the
   accessibility service.

## Export contents

`daily_summary`, `daily_app_usage`, `hourly_usage`, `app_sessions`,
`activity_sessions`, `screen_sessions`, `raw_events`, `system_usage_stats`,
`in_app_sections`, `ui_debug_ids`, `installed_apps`, `device_info` and a README inside
the ZIP explaining each file.

## Limits that come from Android

- Detailed history (sessions, hourly, raw events) is kept for roughly 10 days. Older
  days only exist as monthly and yearly totals.
- Normal apps cannot see what you do inside an app, notification text, calls, messages
  or location.
- Someone determined can switch the accessibility service off, so this is a nudge,
  not a lock.

## Structure

- `UsageRepository.kt` reads usage events and builds totals and per-screen breakdowns
- `DataExporter.kt` writes the CSV export
- `FocusAccessibilityService.kt`, `BlockActivity.kt`, `FocusPrefs.kt` blocking and challenges
- `SectionDetector.kt` Reels, Feed and Shorts detection
- `Insights.kt`, `MainActivity.kt`, `FocusScreen.kt` user interface

## Ideas for next

- Daily snapshots stored on the phone so history is kept beyond 10 days
- Chrome extension for site blocking with shared rules
- Weekly report and streaks
