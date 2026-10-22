// Local-only persistence for now (localStorage, one browser/device). Each function here is
// written so swapping the body for a Firestore read/write later doesn't change any caller —
// this is the seam where cross-device sync will plug in once the Firebase web app exists.
import { MISS_SUGGESTIONS } from "./content.js";
import { pushAppState, pullAppState } from "./firebase.js";

const KEY = "focuslens_web_v1";

// Set once signed in (see syncOnSignIn/clearSyncUser) so every local save also mirrors to
// Firestore. null while signed out — saves stay local-only, same as before accounts existed.
let syncUid = null;

function loadAll() {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? JSON.parse(raw) : defaultState();
  } catch (e) {
    return defaultState();
  }
}

function saveAll(state) {
  state.updatedAt = Date.now();
  try {
    localStorage.setItem(KEY, JSON.stringify(state));
  } catch (e) {
    // Private browsing / full storage: the app still works for this session, just doesn't persist.
  }
  if (syncUid) {
    // Fire-and-forget: a failed push (offline, rules not set up yet) shouldn't block local use.
    pushAppState(syncUid, state).catch(() => {});
  }
}

// Called once on sign-in. Pulls whichever copy (this browser's or the account's) is newer and
// makes that the local copy, then starts mirroring future saves to Firestore. Last-write-wins
// by timestamp — fine for one person's own data across their own devices, not a real-time merge.
export async function syncOnSignIn(uid) {
  syncUid = uid;
  const local = loadAll();
  let remote = null;
  try {
    remote = await pullAppState(uid);
  } catch (e) {
    return; // offline, or Firestore rules not set up yet — keep working locally
  }
  if (remote && (remote.updatedAt || 0) > (local.updatedAt || 0)) {
    saveAll(remote);
  } else {
    pushAppState(uid, local).catch(() => {});
  }
}

export function clearSyncUser() {
  syncUid = null;
}

function defaultState() {
  return { tasks: [], checkIns: [], journal: [], cycle: [], learnRead: {}, alarms: [], nextId: 1 };
}

function todayKey(d = new Date()) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}${m}${day}`;
}

function parseDayKey(key) {
  const y = Number(key.slice(0, 4));
  const m = Number(key.slice(4, 6)) - 1;
  const d = Number(key.slice(6, 8));
  return new Date(y, m, d);
}

function addDaysToKey(key, days) {
  const d = parseDayKey(key);
  d.setDate(d.getDate() + days);
  return todayKey(d);
}

function daysBetweenKeys(fromKey, toKey) {
  const a = parseDayKey(fromKey).getTime();
  const b = parseDayKey(toKey).getTime();
  return Math.round((b - a) / 86400000);
}

function nextId(state) {
  const id = state.nextId || 1;
  state.nextId = id + 1;
  return id;
}

// ---- Tasks + weekly reflection (ports TaskRepository.kt) ----

export function listTasks() {
  return loadAll().tasks.slice().sort((a, b) => b.scheduledAt - a.scheduledAt);
}

export function addTask(title, type, scheduledAt, recallNote) {
  const state = loadAll();
  const task = { id: nextId(state), title, type, scheduledAt, recallNote: recallNote || null, completedAt: null };
  state.tasks.push(task);
  saveAll(state);
  return task;
}

export function markComplete(taskId) {
  const state = loadAll();
  const task = state.tasks.find((t) => t.id === taskId);
  if (!task) return;
  task.completedAt = Date.now();
  state.checkIns.push({ taskId, completed: true, reason: null, note: null, createdAt: Date.now() });
  saveAll(state);
}

export function markIncomplete(taskId, reason, note) {
  const state = loadAll();
  const task = state.tasks.find((t) => t.id === taskId);
  if (!task) return;
  task.completedAt = null;
  state.checkIns.push({ taskId, completed: false, reason: reason || null, note: note || null, createdAt: Date.now() });
  saveAll(state);
}

export function deleteTask(taskId) {
  const state = loadAll();
  state.tasks = state.tasks.filter((t) => t.id !== taskId);
  saveAll(state);
}

export function weeklyReflection() {
  const state = loadAll();
  const since = Date.now() - 7 * 86400000;
  const recent = state.checkIns.filter((c) => c.createdAt >= since);
  const completed = recent.filter((c) => c.completed).length;
  const missed = recent.filter((c) => !c.completed).length;
  const counts = {};
  recent.forEach((c) => {
    if (!c.completed && c.reason) counts[c.reason] = (counts[c.reason] || 0) + 1;
  });
  let top = null;
  let topCount = 0;
  for (const [reason, count] of Object.entries(counts)) {
    if (count > topCount) {
      top = reason;
      topCount = count;
    }
  }
  return { completed, missed, topMissReason: top, suggestion: top ? MISS_SUGGESTIONS[top] : null };
}

// ---- Journal (ports JournalRepository.kt) ----

export function saveJournal(text, mood, prompt) {
  const state = loadAll();
  const date = todayKey();
  const existingIdx = state.journal.findIndex((j) => j.date === date);
  const entry = { date, prompt: prompt || null, text, mood: mood || null, createdAt: Date.now() };
  if (existingIdx >= 0) state.journal[existingIdx] = entry;
  else state.journal.push(entry);
  saveAll(state);
}

export function recentJournal(limit = 30) {
  return loadAll()
    .journal.slice()
    .sort((a, b) => (a.date < b.date ? 1 : -1))
    .slice(0, limit);
}

export function journalStreak() {
  const entries = new Set(loadAll().journal.map((j) => j.date));
  if (entries.size === 0) return 0;
  let cursor = todayKey();
  if (!entries.has(cursor)) cursor = addDaysToKey(cursor, -1);
  let streak = 0;
  while (entries.has(cursor)) {
    streak++;
    cursor = addDaysToKey(cursor, -1);
  }
  return streak;
}

export function milestoneFor(streak) {
  return [3, 7, 14, 30, 60, 100].includes(streak) ? streak : null;
}

// ---- Cycle & energy (ports CycleRepository.kt) ----

export function logCycle(mode, periodStart, energyLevel, moodTags, date = todayKey()) {
  const state = loadAll();
  const idx = state.cycle.findIndex((c) => c.date === date);
  const existing = idx >= 0 ? state.cycle[idx] : null;
  const entry = {
    date,
    mode,
    energyLevel: energyLevel != null ? energyLevel : existing ? existing.energyLevel : null,
    moodTags: moodTags != null ? moodTags : existing ? existing.moodTags : null,
    periodStart: periodStart || (existing ? existing.periodStart : false),
    trackers: existing ? existing.trackers || {} : {},
  };
  if (idx >= 0) state.cycle[idx] = entry;
  else state.cycle.push(entry);
  saveAll(state);
}

// Generic per-day tracker value (energy/mood/sleep/water/…), independent of period logging —
// a day can have any subset of these, never all-or-nothing. "energy" also mirrors into the
// legacy energyLevel field so the existing Mental Health trend keeps reading it unchanged.
export function logTrackerValue(trackerType, value, date = todayKey()) {
  const state = loadAll();
  const idx = state.cycle.findIndex((c) => c.date === date);
  const existing = idx >= 0 ? state.cycle[idx] : null;
  const entry = {
    date,
    mode: existing ? existing.mode : null,
    energyLevel: trackerType === "energy" ? value : existing ? existing.energyLevel : null,
    moodTags: existing ? existing.moodTags : null,
    periodStart: existing ? existing.periodStart : false,
    trackers: { ...(existing ? existing.trackers : {}), [trackerType]: value },
  };
  if (idx >= 0) state.cycle[idx] = entry;
  else state.cycle.push(entry);
  saveAll(state);
}

export function recentCycleLogs(days = 30) {
  const cutoff = addDaysToKey(todayKey(), -days);
  return loadAll()
    .cycle.filter((c) => c.date >= cutoff)
    .sort((a, b) => (a.date < b.date ? 1 : -1));
}

const LUTEAL_PHASE_DAYS = 14;

/**
 * Rolling average of gaps between the last few logged period starts — your own data, not a
 * clinical model. The phase/fertile-window estimate assumes the standard reproductive-health
 * rule of thumb that the luteal phase (after ovulation) is a fairly consistent ~14 days,
 * while the follicular phase (before it) is what actually varies — so ovulation is placed 14
 * days before the next predicted start. Real luteal phases range roughly 11-17 days across
 * people, so this is a population-average estimate, not a measurement: only basal body
 * temperature, LH strips, or an ultrasound can actually confirm ovulation. Not a diagnosis
 * or a contraception method.
 */
export function predictNextCycle() {
  // Up to 13 most recent starts -> up to 12 gaps.
  const starts = loadAll()
    .cycle.filter((c) => c.periodStart)
    .map((c) => c.date)
    .sort((a, b) => (a < b ? 1 : -1))
    .slice(0, 13);
  if (starts.length < 2) return null;

  // gaps[0] is the most recent cycle. Gaps outside 15-60 days are treated as a missed log,
  // not a real cycle, and dropped.
  const gaps = [];
  for (let i = 0; i < starts.length - 1; i++) {
    const gap = daysBetweenKeys(starts[i + 1], starts[i]);
    if (gap >= 15 && gap <= 60) gaps.push(gap);
  }
  if (gaps.length === 0) return null;

  // Exponentially weighted average: each older cycle counts 70% as much as the one after it, so
  // the estimate follows recent changes instead of an all-time mean.
  const DECAY = 0.7;
  let wSum = 0;
  let weighted = 0;
  gaps.forEach((g, i) => {
    const w = Math.pow(DECAY, i);
    wSum += w;
    weighted += w * g;
  });
  const avg = Math.round(weighted / wSum);

  // Regularity = standard deviation of cycle length (needs 2+ cycles).
  let sd = null;
  let regularity = null;
  if (gaps.length >= 2) {
    const mean = gaps.reduce((a, b) => a + b, 0) / gaps.length;
    sd = Math.sqrt(gaps.reduce((a, g) => a + (g - mean) * (g - mean), 0) / gaps.length);
    regularity = sd <= 2 ? "Very regular" : sd <= 4 ? "Regular" : sd <= 7 ? "Somewhat variable" : "Highly variable";
  }

  const lastStart = starts[0];
  const predicted = addDaysToKey(lastStart, avg);
  const today = todayKey();
  const currentDay = Math.max(1, daysBetweenKeys(lastStart, today) + 1);

  const ovulationDay = Math.max(7, avg - LUTEAL_PHASE_DAYS);
  const fertileStart = Math.max(1, ovulationDay - 5);
  const fertileEnd = ovulationDay + 1;
  const MENSTRUAL_DAYS = 5; // typical bleed length; shown as a phase label only
  let phase;
  if (currentDay >= fertileStart && currentDay <= fertileEnd) phase = currentDay === ovulationDay ? "Ovulation" : "Fertile window";
  else if (currentDay <= MENSTRUAL_DAYS && currentDay < fertileStart) phase = "Menstrual";
  else if (currentDay < fertileStart) phase = "Follicular phase";
  else phase = "Luteal phase";

  return {
    averageCycleLengthDays: avg,
    predictedNextStart: predicted,
    currentCycleDay: currentDay,
    isConfident: gaps.length >= 3,
    cyclesUsed: gaps.length,
    cycleSdDays: sd,
    regularity,
    ovulationDay,
    fertileWindowStartDay: fertileStart,
    fertileWindowEndDay: fertileEnd,
    phase,
  };
}

// ---- Learn read-state ----

export function isLearnRead(id) {
  return !!loadAll().learnRead[id];
}

export function markLearnRead(id) {
  const state = loadAll();
  state.learnRead[id] = true;
  saveAll(state);
}

export function learnReadCount(ids) {
  const state = loadAll();
  return ids.filter((id) => state.learnRead[id]).length;
}

// ---- Birthdays ----
// `birthday` is MMdd (month+day only) — the year doesn't matter for a recurring birthday.

export function addPerson(name, mmdd) {
  const state = loadAll();
  if (!state.people) state.people = [];
  const person = { id: state.nextId++, name, birthday: mmdd || null, createdAt: Date.now() };
  state.people.push(person);
  saveAll(state);
  return person;
}

export function listPeople() {
  return (loadAll().people || []).slice().sort((a, b) => a.name.localeCompare(b.name));
}

export function deletePerson(id) {
  const state = loadAll();
  state.people = (state.people || []).filter((p) => p.id !== id);
  saveAll(state);
}

function daysUntilBirthday(mmdd) {
  const month = parseInt(mmdd.slice(0, 2), 10);
  const day = parseInt(mmdd.slice(2, 4), 10);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const next = new Date(today.getFullYear(), month - 1, day);
  if (next < today) next.setFullYear(next.getFullYear() + 1);
  return Math.round((next - today) / 86_400_000);
}

export function upcomingBirthdays() {
  return listPeople()
    .filter((p) => p.birthday)
    .map((p) => ({ ...p, daysUntil: daysUntilBirthday(p.birthday) }))
    .sort((a, b) => a.daysUntil - b.daysUntil);
}

export function setBirthdayWish(personId, year, wished) {
  const state = loadAll();
  if (!state.birthdayWishes) state.birthdayWishes = {};
  const key = `${year}:${personId}`;
  if (wished) state.birthdayWishes[key] = true;
  else delete state.birthdayWishes[key];
  saveAll(state);
}

export function wishedThisYear(year) {
  const state = loadAll();
  const wishes = state.birthdayWishes || {};
  const prefix = `${year}:`;
  return new Set(
    Object.keys(wishes)
      .filter((k) => k.startsWith(prefix))
      .map((k) => parseInt(k.slice(prefix.length), 10))
  );
}

// --------------------------------------------------------------------- Alarms
// time: "HH:MM" (24h, local). days: array of 0(Sun)-6(Sat) to repeat on, or [] for a one-off
// that fires once then disables itself. lastFiredKey guards against re-firing twice in the
// same minute while the check loop is running.
export function listAlarms() {
  const state = loadAll();
  return state.alarms || [];
}

export function addAlarm(time, label, days) {
  const state = loadAll();
  if (!state.alarms) state.alarms = [];
  const alarm = {
    id: state.nextId++,
    time,
    label: label || "Alarm",
    days: days || [],
    enabled: true,
    lastFiredKey: null,
  };
  state.alarms.push(alarm);
  saveAll(state);
  return alarm;
}

export function deleteAlarm(id) {
  const state = loadAll();
  state.alarms = (state.alarms || []).filter((a) => a.id !== id);
  saveAll(state);
}

export function toggleAlarm(id, enabled) {
  const state = loadAll();
  const a = (state.alarms || []).find((a) => a.id === id);
  if (a) a.enabled = enabled;
  saveAll(state);
}

export function markAlarmFired(id, firedKey) {
  const state = loadAll();
  const a = (state.alarms || []).find((a) => a.id === id);
  if (!a) return;
  a.lastFiredKey = firedKey;
  if (a.days.length === 0) a.enabled = false; // one-off: don't fire again
  saveAll(state);
}

export { todayKey, addDaysToKey };

// ---- Habits, hydration, medication/supplement checklist, life pillars ----
// Every list is created lazily (`state.x = state.x || ...`) so state saved before these
// features existed — locally or in Firestore — keeps loading.

export function listHabits() {
  return (loadAll().habits || []).slice();
}

export function addHabit(title) {
  const state = loadAll();
  state.habits = state.habits || [];
  state.habits.push({ id: nextId(state), title, days: {} });
  saveAll(state);
}

export function deleteHabit(id) {
  const state = loadAll();
  state.habits = (state.habits || []).filter((h) => h.id !== id);
  saveAll(state);
}

export function toggleHabitDay(id, dayKey) {
  const state = loadAll();
  const habit = (state.habits || []).find((h) => h.id === id);
  if (!habit) return;
  if (habit.days[dayKey]) delete habit.days[dayKey];
  else habit.days[dayKey] = true;
  saveAll(state);
}

// Consecutive done days ending today. If today isn't ticked yet the streak counts back from
// yesterday, so it doesn't read as broken before the day is over.
export function habitStreak(habit) {
  let cursor = todayKey();
  if (!habit.days[cursor]) cursor = addDaysToKey(cursor, -1);
  let n = 0;
  while (habit.days[cursor]) {
    n++;
    cursor = addDaysToKey(cursor, -1);
  }
  return n;
}

export function waterToday() {
  const w = loadAll().water || { target: 2500, byDay: {} };
  return { target: w.target, ml: w.byDay[todayKey()] || 0 };
}

export function addWater(ml) {
  const state = loadAll();
  state.water = state.water || { target: 2500, byDay: {} };
  const key = todayKey();
  state.water.byDay[key] = Math.max(0, (state.water.byDay[key] || 0) + ml);
  saveAll(state);
}

export function setWaterTarget(ml) {
  const state = loadAll();
  state.water = state.water || { target: 2500, byDay: {} };
  state.water.target = ml;
  saveAll(state);
}

export function listMeds() {
  return (loadAll().meds || []).slice();
}

export function addMed(name, slot) {
  const state = loadAll();
  state.meds = state.meds || [];
  state.meds.push({ id: nextId(state), name, slot });
  saveAll(state);
}

export function deleteMed(id) {
  const state = loadAll();
  state.meds = (state.meds || []).filter((m) => m.id !== id);
  saveAll(state);
}

export function medsTakenToday() {
  const log = (loadAll().medLog || {})[todayKey()] || {};
  return log;
}

export function setMedTaken(id, taken) {
  const state = loadAll();
  state.medLog = state.medLog || {};
  const key = todayKey();
  state.medLog[key] = state.medLog[key] || {};
  if (taken) state.medLog[key][id] = true;
  else delete state.medLog[key][id];
  saveAll(state);
}

export function listPillars() {
  return (loadAll().pillars || []).slice();
}

export function addPillar(title, goal) {
  const state = loadAll();
  state.pillars = state.pillars || [];
  state.pillars.push({ id: nextId(state), title, goal });
  saveAll(state);
}

export function deletePillar(id) {
  const state = loadAll();
  state.pillars = (state.pillars || []).filter((p) => p.id !== id);
  saveAll(state);
}

// ---- Sleep log + debt ----

export function logSleep(wakeDateKey, bed, wake, quality) {
  const [bh, bm] = bed.split(":").map(Number);
  const [wh, wm] = wake.split(":").map(Number);
  let minutes = (wh * 60 + wm) - (bh * 60 + bm);
  if (minutes <= 0) minutes += 24 * 60; // crossed midnight
  const state = loadAll();
  state.sleep = (state.sleep || []).filter((s) => s.date !== wakeDateKey);
  state.sleep.push({ date: wakeDateKey, bed, wake, quality, minutes });
  saveAll(state);
}

export function recentSleep(days = 14) {
  const cutoff = addDaysToKey(todayKey(), -(days - 1));
  return (loadAll().sleep || [])
    .filter((s) => s.date >= cutoff)
    .sort((a, b) => b.date.localeCompare(a.date));
}

export function deleteSleep(dateKey) {
  const state = loadAll();
  state.sleep = (state.sleep || []).filter((s) => s.date !== dateKey);
  saveAll(state);
}

export function getSleepTargetHours() {
  return loadAll().sleepTargetHours || 8;
}

export function setSleepTargetHours(h) {
  const state = loadAll();
  state.sleepTargetHours = h;
  saveAll(state);
}

// Minutes short of the nightly target across the last 7 days that have a log. Nights over
// target don't "pay back" debt here — a deliberately simple, inspectable rule.
export function weeklySleepDebtMinutes() {
  const target = getSleepTargetHours() * 60;
  const logs = recentSleep(7);
  return logs.reduce((sum, s) => sum + Math.max(0, target - s.minutes), 0);
}

export function getChronotype() {
  return loadAll().chronotype || "bear";
}

export function setChronotype(c) {
  const state = loadAll();
  state.chronotype = c;
  saveAll(state);
}

// ---- Focus sessions ----

export function logFocusSession(minutes, mode) {
  const state = loadAll();
  state.focusSessions = state.focusSessions || [];
  state.focusSessions.push({ at: Date.now(), date: todayKey(), minutes, mode });
  saveAll(state);
}

export function focusMinutesToday() {
  const key = todayKey();
  return (loadAll().focusSessions || [])
    .filter((s) => s.date === key)
    .reduce((sum, s) => sum + s.minutes, 0);
}

export function focusMinutesLast7() {
  const cutoff = addDaysToKey(todayKey(), -6);
  return (loadAll().focusSessions || [])
    .filter((s) => s.date >= cutoff)
    .reduce((sum, s) => sum + s.minutes, 0);
}

// ---- Screener results (kept on this device / your account, never shown to anyone else) ----

export function saveScreenerResult(type, score, band) {
  const state = loadAll();
  state.screeners = state.screeners || [];
  state.screeners.push({ at: Date.now(), type, score, band });
  saveAll(state);
}

export function listScreenerResults(type) {
  return (loadAll().screeners || [])
    .filter((r) => r.type === type)
    .sort((a, b) => b.at - a.at);
}

// ---- Custom journal prompts ----

export function listCustomPrompts() {
  return (loadAll().customPrompts || []).slice();
}

export function addCustomPrompt(text) {
  const state = loadAll();
  state.customPrompts = state.customPrompts || [];
  state.customPrompts.push({ id: nextId(state), text });
  saveAll(state);
}

export function deleteCustomPrompt(id) {
  const state = loadAll();
  state.customPrompts = (state.customPrompts || []).filter((p) => p.id !== id);
  saveAll(state);
}

// ---- Activity, meals and diet plan (all self-entered; the web can't read phone sensors) ----

export function logActivity(kind, minutes, steps, calories) {
  const state = loadAll();
  state.activity = state.activity || [];
  state.activity.push({ id: nextId(state), date: todayKey(), kind, minutes, steps, calories });
  saveAll(state);
}

export function activityToday() {
  const key = todayKey();
  const rows = (loadAll().activity || []).filter((a) => a.date === key);
  return {
    rows,
    minutes: rows.reduce((s, r) => s + (r.minutes || 0), 0),
    steps: rows.reduce((s, r) => s + (r.steps || 0), 0),
    calories: rows.reduce((s, r) => s + (r.calories || 0), 0),
  };
}

export function deleteActivity(id) {
  const state = loadAll();
  state.activity = (state.activity || []).filter((a) => a.id !== id);
  saveAll(state);
}

export function getStepGoal() {
  return loadAll().stepGoal || 8000;
}

export function setStepGoal(n) {
  const state = loadAll();
  state.stepGoal = n;
  saveAll(state);
}

export function logMeal(slot, text) {
  const state = loadAll();
  state.meals = state.meals || [];
  state.meals.push({ id: nextId(state), date: todayKey(), slot, text });
  saveAll(state);
}

export function mealsToday() {
  const key = todayKey();
  return (loadAll().meals || []).filter((m) => m.date === key);
}

export function deleteMeal(id) {
  const state = loadAll();
  state.meals = (state.meals || []).filter((m) => m.id !== id);
  saveAll(state);
}

export function getDietPlan() {
  return loadAll().dietPlan || "";
}

export function setDietPlan(text) {
  const state = loadAll();
  state.dietPlan = text;
  saveAll(state);
}

// ---- Eisenhower matrix (urgent x important) ----

export function listMatrix() {
  return (loadAll().matrix || []).slice();
}

export function addMatrixItem(text, urgent, important) {
  const state = loadAll();
  state.matrix = state.matrix || [];
  state.matrix.push({ id: nextId(state), text, urgent, important });
  saveAll(state);
}

export function deleteMatrixItem(id) {
  const state = loadAll();
  state.matrix = (state.matrix || []).filter((m) => m.id !== id);
  saveAll(state);
}

// ---- Progress (XP / level / quests / boss) — derived from what you actually logged ----
// Nothing is stored for this: XP is recomputed from the real records every time, so it can
// never drift from, or be inflated beyond, your actual activity.

const XP_PER = { focusMinute: 1, journal: 20, habitTick: 5, task: 10, sleepLog: 10 };

export function progressSummary() {
  const state = loadAll();
  const focus = (state.focusSessions || []).reduce((s, f) => s + f.minutes, 0);
  const journal = (state.journal || []).length;
  const habitTicks = (state.habits || []).reduce((s, h) => s + Object.keys(h.days).length, 0);
  const tasks = (state.tasks || []).filter((t) => t.completedAt).length;
  const sleep = (state.sleep || []).length;
  const xp = focus * XP_PER.focusMinute + journal * XP_PER.journal + habitTicks * XP_PER.habitTick + tasks * XP_PER.task + sleep * XP_PER.sleepLog;
  const level = Math.floor(Math.sqrt(xp / 50)) + 1;
  const levelStart = 50 * (level - 1) * (level - 1);
  const levelEnd = 50 * level * level;
  return { xp, level, levelStart, levelEnd, focus, journal, habitTicks, tasks, sleep };
}

export function dailyQuests() {
  const today = todayKey();
  const state = loadAll();
  const water = waterToday();
  return [
    { id: "focus", label: "Focus for 25 minutes", done: focusMinutesToday() >= 25 },
    { id: "journal", label: "Write today's journal entry", done: (state.journal || []).some((e) => e.date === today) },
    { id: "habit", label: "Tick off a habit", done: (state.habits || []).some((h) => h.days[today]) },
    { id: "water", label: "Reach your water target", done: water.ml >= water.target },
  ];
}

export const BOSS_WEEKLY_HP = 300; // focus minutes needed to defeat this week's boss
export function bossStatus() {
  const dealt = focusMinutesLast7();
  return { hp: BOSS_WEEKLY_HP, dealt, left: Math.max(0, BOSS_WEEKLY_HP - dealt), defeated: dealt >= BOSS_WEEKLY_HP };
}

export function bestHabitStreak() {
  return (loadAll().habits || []).reduce((m, h) => Math.max(m, habitStreak(h)), 0);
}

// Everything the doctor summary needs in one read.
export function summaryData() {
  const state = loadAll();
  const moods = (state.journal || []).filter((e) => e.mood).slice(-14);
  const sleepLogs = recentSleep(14);
  return {
    screeners: ["PHQ9", "GAD7", "ASRS"].map((t) => ({ type: t, latest: listScreenerResults(t)[0] || null })),
    moodEntries: moods,
    avgSleepMin: sleepLogs.length ? sleepLogs.reduce((s, l) => s + l.minutes, 0) / sleepLogs.length : null,
    sleepNights: sleepLogs.length,
    cycle: predictNextCycle(),
    meds: (state.meds || []).map((m) => `${m.name} (${m.slot})`),
  };
}
