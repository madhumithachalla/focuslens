# FocusLens: visual redesign plan

Goal: less text, more pictures. Each screen says one thing, shows it as a picture, and keeps detail behind a tap.

## Rules for every screen
1. One title, one short line, at most.
2. A picture first: ring, bar, dots, icons, or a trend line.
3. Numbers only where they help the decision in front of the person.
4. Detail and explanations go behind an "i" or "Show more" tap, never on the main screen.
5. Every screen has Back, and the navigation bar is never covered by anything.
6. The person picks the look (calm, clear, dark) and that choice applies everywhere.

## Phase 1: shared visual parts (this change)
- `Visuals.kt`: ring, mini bar, dot row, icon tile, trend line, and a "one thing" card.
- Done when: the parts compile and render in the build.

## Phase 2: apply to the most-used screens
- Today: ring of tasks done, one "next" card, focus and sleep as tiles.
- Usage: ring for today, app bars, a 7-day trend.
- Reels and limits: the counter as a dot row (used dots, left dots).
- Sleep: one bar per night, average as a line.
- Water: a glass that fills.
- Done when: you have used each screen on your phone and it reads at a glance.

## Phase 3: remove the paragraphs
- Move explanations to "i" buttons: Usage, Sleep, Account, Guide, Birthdays, Learn.
- Remove developer text (for example the Reels check line and the profile photo note).

## Phase 4: navigation
- Five tabs: Today, Focus, Body, Journal, Me. Options and sub-options inside each.
- Back, forward and exit work on every screen.
- About sits under More.

## Phase 5: check on your phone
- You try each phase on your phone. The next phase starts only after you approve it.

## Open decisions for you
- Default look: calm, clear or dark?
- Do Learn, Explore, Birthdays, Playlists and Guide stay (under More) or go?
