package com.focuslens

/** What changed in each published build. Add an entry for every build that ships a change. */
data class BuildNote(val build: Int, val points: List<String>)

val APP_CHANGELOG: List<BuildNote> = listOf(
    BuildNote(140, listOf(
        "Usage: time stops counting when the screen turns off. Apps left open no longer collect hours.",
        "Inside-the-app section shows how much of the app's time it could measure.",
        "Storage: find and delete old APK installer files, with Android's confirmation.",
        "Minimal home screen (choose it in Android's home settings): time, date, extra time zones, app names as text, favourites, swipe up to search, swipe down for notifications.",
        "Planner: time only when you set one. Student and adult categories. Task, event and reminder sections."
    )),
    BuildNote(98, listOf(
        "Counts swipes, scroll reminders, blocked open attempts and feature blocks per app.",
        "Report shows a 'What FocusLens did' section."
    )),
    BuildNote(97, listOf(
        "Downloadable simple report (HTML): Android totals and FocusLens copies side by side, per day."
    )),
    BuildNote(96, listOf(
        "Usage history moved to its own database so later updates do not delete it.",
        "A message says why an app closed when its daily limit is reached."
    )),
    BuildNote(95, listOf(
        "Instagram: the Reels button in the bottom bar no longer counts as Reels."
    )),
    BuildNote(94, listOf(
        "Install update downloads the new version and opens Android's installer.",
        "Update card on the Usage page and an App updates page in the menu."
    )),
    BuildNote(93, listOf(
        "YouTube: Shorts only counts when the Shorts player or Shorts tab is up."
    )),
    BuildNote(89, listOf(
        "Focus tab shows the Reels check line: accessibility, service connected, last screen seen."
    )),
    BuildNote(85, listOf(
        "Reels/Shorts switches show for every installed feature app, even with no recent usage."
    ))
)
