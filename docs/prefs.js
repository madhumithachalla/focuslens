// User preferences (Settings page). One typed schema, stored as a single JSON object in
// localStorage ("focuslens_prefs_v1"). Reads always fall back to the schema default, so a
// missing, corrupt or hand-edited value can never break the app. Theme / contrast / large text /
// reduce motion are NOT here: they live in the existing "focuslens_a11y" key (features.js).
import { CATEGORIES } from "./quiz-engine.js";

export const PREFS_KEY = "focuslens_prefs_v1";

export const ACCENTS = {
  teal: "#2E7D5B", violet: "#6D4FC2", coral: "#D2553F", amber: "#B7791F",
  blue: "#2F6FB5", rose: "#C23A6B", forest: "#3F6B2E",
};

export const FONT_STACKS = {
  jakarta: "'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
  system: "system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif",
  "dyslexia-friendly": "'OpenDyslexic', 'Atkinson Hyperlegible', system-ui, sans-serif",
};

// Pages that can never be hidden, and the pages kept when nav is "focus-only".
export const ALWAYS_VISIBLE_PAGES = ["settings", "help"];
export const FOCUS_ONLY_PAGES = ["roadmap", "focus", "intercept", "tasks", "alarms", "sleep", "progress", "account", "settings", "help"];

const CATEGORY_IDS = CATEGORIES.map((c) => c.id);

// type: enum | int | intenum | bool | time | string | idlist | intlist
export const SCHEMA = {
  accent: { type: "enum", def: "teal", options: Object.keys(ACCENTS) },
  density: { type: "enum", def: "comfortable", options: ["comfortable", "compact"] },
  radius: { type: "enum", def: "soft", options: ["soft", "round", "sharp"] },
  font: { type: "enum", def: "jakarta", options: Object.keys(FONT_STACKS) },
  nav: { type: "enum", def: "full", options: ["full", "focus-only"] },
  hiddenPages: { type: "idlist", def: [], max: 40 },

  pomoFocus: { type: "int", def: 25, min: 1, max: 180 },
  pomoRest: { type: "int", def: 5, min: 1, max: 60 },
  r52Focus: { type: "int", def: 52, min: 1, max: 180 },
  r52Rest: { type: "int", def: 17, min: 1, max: 60 },
  ultraFocus: { type: "int", def: 90, min: 1, max: 240 },
  ultraRest: { type: "int", def: 20, min: 1, max: 60 },
  autoStart: { type: "bool", def: false },
  sound: { type: "bool", def: true },
  volume: { type: "int", def: 60, min: 0, max: 100 },

  qCategories: { type: "idlist", def: CATEGORY_IDS.slice(), allowed: CATEGORY_IDS, max: 20 },
  qNeeded: { type: "int", def: 3, min: 1, max: 5 },
  qFloor: { type: "int", def: 1, min: 1, max: 5 },
  qHarder: { type: "bool", def: true },
  qDelay: { type: "intenum", def: 0, options: [0, 5, 10, 30] },
  qPasses: { type: "int", def: 2, min: 0, max: 10 },

  wakeTime: { type: "time", def: "07:00" },
  sleepLatency: { type: "int", def: 15, min: 0, max: 90 },

  waterQuick: { type: "intlist", def: [250, 500, 750], min: 50, max: 2000, maxLen: 6 },
  weekStart: { type: "enum", def: "mon", options: ["mon", "sun"] },
  units: { type: "enum", def: "metric", options: ["metric", "imperial"] },
  periodLen: { type: "int", def: 5, min: 3, max: 8 },
  lutealLen: { type: "int", def: 14, min: 11, max: 17 },

  journalTemplate: { type: "string", def: "", maxLen: 80 },
  journalReminder: { type: "time", def: "", allowEmpty: true },
};

function coerce(key, v) {
  const s = SCHEMA[key];
  if (!s) return undefined;
  const def = Array.isArray(s.def) ? s.def.slice() : s.def;
  switch (s.type) {
    case "enum": return s.options.includes(v) ? v : def;
    case "intenum": { const n = Number(v); return s.options.includes(n) ? n : def; }
    case "int": { const n = Math.round(Number(v)); return Number.isFinite(n) ? Math.min(s.max, Math.max(s.min, n)) : def; }
    case "bool": return typeof v === "boolean" ? v : def;
    case "time": return typeof v === "string" && (/^([01]\d|2[0-3]):[0-5]\d$/.test(v) || (s.allowEmpty && v === "")) ? v : def;
    case "string": return typeof v === "string" ? v.slice(0, s.maxLen || 200) : def;
    case "idlist": {
      if (!Array.isArray(v)) return def;
      let out = [...new Set(v.filter((x) => typeof x === "string" && /^[a-z0-9-]{1,40}$/.test(x)))];
      if (s.allowed) out = out.filter((x) => s.allowed.includes(x));
      if (key === "hiddenPages") out = out.filter((x) => !ALWAYS_VISIBLE_PAGES.includes(x));
      return out.slice(0, s.max || 50);
    }
    case "intlist": {
      if (!Array.isArray(v)) return def;
      const out = v.map((x) => Math.round(Number(x))).filter((n) => Number.isFinite(n) && n >= s.min && n <= s.max);
      return [...new Set(out)].slice(0, s.maxLen);
    }
    default: return def;
  }
}

function readRaw() {
  try {
    const o = JSON.parse(localStorage.getItem(PREFS_KEY));
    return o && typeof o === "object" && !Array.isArray(o) ? o : {};
  } catch (e) { return {}; }
}

function writeRaw(o) {
  try { localStorage.setItem(PREFS_KEY, JSON.stringify(o)); return true; } catch (e) { return false; }
}

export function getPref(key) {
  const s = SCHEMA[key];
  if (!s) return undefined;
  const raw = readRaw();
  const v = Object.prototype.hasOwnProperty.call(raw, key) ? coerce(key, raw[key]) : coerce(key, s.def);
  // An empty category list would make the intercept unanswerable, so it falls back to all.
  if (key === "qCategories" && !v.length) return s.def.slice();
  return v;
}

export function setPref(key, value) {
  if (!SCHEMA[key]) return undefined;
  const v = coerce(key, value);
  const raw = readRaw();
  raw[key] = v;
  writeRaw(raw);
  applyPrefs();
  return v;
}

export function allPrefs() {
  const out = {};
  Object.keys(SCHEMA).forEach((k) => { out[k] = getPref(k); });
  return out;
}

export function resetPrefs() {
  try { localStorage.removeItem(PREFS_KEY); } catch (e) {}
  applyPrefs();
}

export function exportPrefs() {
  return JSON.stringify({ app: "focuslens-prefs", version: 1, prefs: allPrefs() }, null, 2);
}

// Accepts the exportPrefs() shape (or a bare prefs object). Unknown keys are ignored, invalid
// values fall back to defaults. Returns { ok, applied, error }.
export function importPrefs(json) {
  let o;
  try { o = typeof json === "string" ? JSON.parse(json) : json; } catch (e) { return { ok: false, applied: 0, error: "That is not valid JSON." }; }
  if (!o || typeof o !== "object" || Array.isArray(o)) return { ok: false, applied: 0, error: "Expected a JSON object." };
  const src = o.prefs && typeof o.prefs === "object" ? o.prefs : o;
  const keys = Object.keys(src).filter((k) => Object.prototype.hasOwnProperty.call(SCHEMA, k));
  if (!keys.length) return { ok: false, applied: 0, error: "No FocusLens settings found in that file." };
  const raw = readRaw();
  keys.forEach((k) => { raw[k] = coerce(k, src[k]); });
  writeRaw(raw);
  applyPrefs();
  return { ok: true, applied: keys.length, error: null };
}

// Pages hidden from the menu: the user's list, plus everything outside FOCUS_ONLY_PAGES when
// nav is "focus-only". Settings and Help are never hidden.
export function isPageHidden(id, allIds) {
  if (ALWAYS_VISIBLE_PAGES.includes(id)) return false;
  if (getPref("hiddenPages").includes(id)) return true;
  return getPref("nav") === "focus-only" && !FOCUS_ONLY_PAGES.includes(id) && (!allIds || allIds.includes(id));
}

// Sets data-* attributes (and two CSS variables) on <html>. Call once at startup and after any
// change; setPref/resetPrefs/importPrefs already call it.
export function applyPrefs() {
  if (typeof document === "undefined") return;
  const root = document.documentElement;
  const accent = getPref("accent");
  const font = getPref("font");
  root.dataset.accent = accent;
  root.dataset.density = getPref("density");
  root.dataset.radius = getPref("radius");
  root.dataset.font = font;
  root.dataset.nav = getPref("nav");
  root.dataset.units = getPref("units");
  root.dataset.weekstart = getPref("weekStart");
  root.dataset.hiddenPages = getPref("hiddenPages").join(" ");
  root.style.setProperty("--pref-accent", ACCENTS[accent]);
  root.style.setProperty("--pref-font", FONT_STACKS[font]);
}
