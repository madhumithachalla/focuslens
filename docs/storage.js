// Local-only persistence for now (localStorage, one browser/device). Each function here is
// written so swapping the body for a Firestore read/write later doesn't change any caller —
// this is the seam where cross-device sync will plug in once the Firebase web app exists.
import { MISS_SUGGESTIONS } from "./content.js";

const KEY = "focuslens_web_v1";

function loadAll() {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? JSON.parse(raw) : defaultState();
  } catch (e) {
    return defaultState();
  }
}

function saveAll(state) {
  try {
    localStorage.setItem(KEY, JSON.stringify(state));
  } catch (e) {
    // Private browsing / full storage: the app still works for this session, just doesn't persist.
  }
}

function defaultState() {
  return { tasks: [], checkIns: [], journal: [], cycle: [], learnRead: {}, nextId: 1 };
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

export function logCycle(mode, periodStart, energyLevel, moodTags) {
  const state = loadAll();
  const date = todayKey();
  const idx = state.cycle.findIndex((c) => c.date === date);
  const existing = idx >= 0 ? state.cycle[idx] : null;
  const entry = {
    date,
    mode,
    energyLevel: energyLevel != null ? energyLevel : existing ? existing.energyLevel : null,
    moodTags: moodTags != null ? moodTags : existing ? existing.moodTags : null,
    periodStart: periodStart || (existing ? existing.periodStart : false),
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

/** Rolling average of gaps between the last few logged period starts — your own data, not a clinical model. */
export function predictNextCycle() {
  const starts = loadAll()
    .cycle.filter((c) => c.periodStart)
    .map((c) => c.date)
    .sort((a, b) => (a < b ? 1 : -1))
    .slice(0, 6);
  if (starts.length < 2) return null;

  const gaps = [];
  for (let i = 0; i < starts.length - 1; i++) {
    const gap = daysBetweenKeys(starts[i + 1], starts[i]);
    if (gap >= 15 && gap <= 60) gaps.push(gap);
  }
  if (gaps.length === 0) return null;

  const avg = Math.round(gaps.reduce((a, b) => a + b, 0) / gaps.length);
  const lastStart = starts[0];
  const predicted = addDaysToKey(lastStart, avg);
  const today = todayKey();
  const currentDay = Math.max(1, daysBetweenKeys(lastStart, today) + 1);

  return { averageCycleLengthDays: avg, predictedNextStart: predicted, currentCycleDay: currentDay, isConfident: gaps.length >= 3 };
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

export { todayKey, addDaysToKey };
