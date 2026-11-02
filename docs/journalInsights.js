// Journal prompts built from the user's own real data, not a fixed list — the web
// equivalent of the Android app's JournalInsights.kt. A website can't read phone-wide app
// usage, so this leans on what's actually available here: the mood/energy trend (Journal +
// Cycle entries, same calculation as the Mental health page), the weekly to-do miss reason
// (same source as its own suggestion card), and, only when signed in with screen-time sync
// turned on in the Android app, the real synced usage numbers (same data the Screen time
// page shows) — gracefully skipped otherwise, never guessed or simulated.
import * as store from "./storage.js";
import { listUsageDocs } from "./firebase.js";

const MOOD_SCORE = { low: 1, flat: 2, okay: 3, good: 4, great: 5 };

function fmtMinutes(m) {
  m = Math.round(Number(m) || 0);
  const h = Math.floor(m / 60);
  return h ? `${h}h ${m % 60}m` : `${m}m`;
}

function moodTrendPrompt() {
  const days = [];
  let cursor = store.todayKey();
  for (let i = 0; i < 14; i++) {
    days.unshift(cursor);
    cursor = store.addDaysToKey(cursor, -1);
  }
  const journal = store.recentJournal(60);
  const cycle = store.recentCycleLogs(60);
  const scores = days
    .map((date) => {
      const j = journal.find((e) => e.date === date);
      const c = cycle.find((e) => e.date === date);
      const moodScore = j && j.mood ? MOOD_SCORE[j.mood] : null;
      const energyScore = c && c.energyLevel ? c.energyLevel : null;
      if (moodScore && energyScore) return (moodScore + energyScore) / 2;
      return moodScore || energyScore || null;
    })
    .filter((s) => s != null);
  if (!scores.length) return null;
  const avg = scores.reduce((a, b) => a + b, 0) / scores.length;
  if (avg >= 2.5) return null;
  return { emoji: "🌤️", text: "Your mood/energy trend has been low lately. What's one real thing underneath that, if you had to name it?" };
}

function missReasonPrompt() {
  const r = store.weeklyReflection();
  if (!r.topMissReason || r.missed < 2) return null;
  const phrasing = {
    TIRED: `"Tired" has come up as your top reason for missed tasks this week. What would actually need to change for that to ease up?`,
    DISTRACTED: `"Distracted" has been your top reason for missed tasks this week. What's usually pulling your attention away, specifically?`,
    TOO_BIG: `Tasks feeling too big has come up most this week. What's one of them, broken into a first step small enough you'd actually start it?`,
    FORGOT: `Forgetting has been your top reason for missed tasks this week. Is there a pattern to what slips — time of day, type of task?`,
    OTHER: `Something's gotten in the way of your tasks most this week. What's actually been competing for your time?`,
  };
  const text = phrasing[r.topMissReason];
  return text ? { emoji: "✅", text } : null;
}

async function usagePrompt(user) {
  if (!user) return null;
  try {
    const docs = await listUsageDocs(user.uid);
    if (!docs.length) return null;
    const today = store.todayKey();
    const byDay = new Map();
    docs.forEach((d) => byDay.set(d.id.slice(6), d));
    const days = [];
    for (let i = 6; i >= 0; i--) days.push(store.addDaysToKey(today, -i));
    const recent = days.filter((k) => byDay.has(k));
    if (!recent.length) return null;
    const avgMinutes = recent.reduce((s, k) => s + (Number(byDay.get(k).totalMinutes) || 0), 0) / recent.length;

    const todayDoc = byDay.get(today);
    const top = todayDoc && todayDoc.perApp && typeof todayDoc.perApp === "object"
      ? Object.entries(todayDoc.perApp).map(([app, m]) => [app, Number(m) || 0]).filter(([, m]) => m > 0).sort((a, b) => b[1] - a[1])[0]
      : null;
    if (top) {
      return { emoji: "📱", text: `${top[0]} has had the most time on your phone today (${fmtMinutes(top[1])} so far). What's it actually giving you right now?` };
    }
    if (avgMinutes > 0) {
      return { emoji: "📱", text: `Your phone's averaged ${fmtMinutes(avgMinutes)} a day this week (synced from the Android app). Worth a minute on where that time's actually going?` };
    }
    return null;
  } catch (e) {
    return null;
  }
}

/** Returns up to 3 {emoji, text} prompts, each from a different real signal — never fabricated,
 *  never shown when the underlying data doesn't support it. */
export async function getJournalSuggestions(user) {
  const results = await Promise.all([
    Promise.resolve(moodTrendPrompt()),
    Promise.resolve(missReasonPrompt()),
    usagePrompt(user),
  ]);
  return results.filter(Boolean);
}
