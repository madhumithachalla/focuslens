// Local storage for the Intercept page and focus-quality records. Kept apart from storage.js.
// Everything is wrapped in try/catch so private windows / blocked storage never break a page.
import { CATEGORIES } from "./quiz-engine.js";

const KEY = "focuslens_intercept_v1";
const PREFS_KEY = "focuslens_prefs_v1";

function dayKey(ts = Date.now()) {
  const d = new Date(ts);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function load() {
  try {
    const s = JSON.parse(localStorage.getItem(KEY) || "{}");
    return { attempts: s.attempts || [], results: s.results || [], custom: s.custom || [], focus: s.focus || [] };
  } catch (e) { return { attempts: [], results: [], custom: [], focus: [] }; }
}

function save(s) {
  try { localStorage.setItem(KEY, JSON.stringify(s)); } catch (e) {}
}

function readPrefs() {
  try { const p = JSON.parse(localStorage.getItem(PREFS_KEY) || "{}"); return p && typeof p === "object" ? p : {}; } catch (e) { return {}; }
}

/** Settings written by the Settings page, read defensively with defaults. */
export function interceptPrefs() {
  const p = readPrefs();
  const ids = CATEGORIES.map((c) => c.id);
  let cats = Array.isArray(p.qCategories) ? p.qCategories.filter((c) => ids.includes(c)) : ids;
  if (!cats.length) cats = ids;
  const num = (v, d, lo, hi) => (Number.isFinite(Number(v)) && v !== null && v !== "" ? Math.max(lo, Math.min(hi, Math.round(Number(v)))) : d);
  return {
    categories: cats,
    needed: num(p.qNeeded, 3, 1, 10),
    delaySeconds: num(p.qDelay, 5, 0, 60),
    passes: num(p.qPasses, 2, 0, 10),
    floor: num(p.qFloor, 1, 1, 5),
    harder: p.qHarder !== false,
  };
}

export function setInterceptCategories(cats) {
  try {
    const p = readPrefs();
    p.qCategories = cats;
    localStorage.setItem(PREFS_KEY, JSON.stringify(p));
  } catch (e) {}
}

// ---- Attempts (real gate runs only) ----
export function recordAttempt(passed, level, answered) {
  const s = load();
  s.attempts.push({ at: Date.now(), date: dayKey(), passed: !!passed, level, answered });
  save(s);
}
export function attemptsToday() {
  const k = dayKey();
  return load().attempts.filter((a) => a.date === k).length;
}
export function attemptsLast7() {
  const s = load();
  const out = [];
  for (let i = 6; i >= 0; i--) {
    const d = new Date(); d.setDate(d.getDate() - i);
    const k = dayKey(d.getTime());
    out.push({ date: k, label: ["S", "M", "T", "W", "T", "F", "S"][d.getDay()], count: s.attempts.filter((a) => a.date === k).length });
  }
  return out;
}

// ---- Answered questions ----
export function recordResult(category, level, right, practice) {
  const s = load();
  s.results.push({ at: Date.now(), date: dayKey(), category, level, right: !!right, practice: !!practice });
  if (s.results.length > 2000) s.results = s.results.slice(-2000);
  save(s);
}
export function stats() {
  const s = load();
  const byCat = {};
  let best = 0;
  s.results.forEach((r) => {
    const c = (byCat[r.category] = byCat[r.category] || { answered: 0, right: 0 });
    c.answered++; if (r.right) c.right++;
    if (r.right && r.level > best) best = r.level;
  });
  return { answered: s.results.length, byCat, bestLevel: best };
}

// ---- Custom questions ----
export function listCustom() { return load().custom; }
export function addCustom(q) {
  const s = load();
  s.custom.push({ id: `c${Date.now()}`, ...q });
  save(s);
}
export function deleteCustom(id) {
  const s = load();
  s.custom = s.custom.filter((q) => q.id !== id);
  save(s);
}

// ---- Focus quality (one record per completed focus block) ----
export function focusQuality(awayCount, awaySeconds) {
  return Math.max(0, 100 - 5 * awayCount - Math.floor(awaySeconds / 10));
}
export function recordFocusBlock({ minutes, mode, awayCount, awaySeconds }) {
  const s = load();
  s.focus.push({ at: Date.now(), date: dayKey(), minutes, mode, awayCount, awaySeconds, quality: focusQuality(awayCount, awaySeconds) });
  save(s);
}
export function focusQualityAvg7() {
  const cutoff = Date.now() - 7 * 86400000;
  const f = load().focus.filter((x) => x.at >= cutoff);
  if (!f.length) return null;
  return Math.round(f.reduce((a, x) => a + x.quality, 0) / f.length);
}
