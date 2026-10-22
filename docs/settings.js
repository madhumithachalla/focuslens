// Settings page: grouped, persisted preferences (prefs.js) plus data export / import / delete.
// Theme, contrast, text size and motion stay on the Help page (focuslens_a11y); sleep hours,
// water target and step goal are stored in the main app state through storage.js.
import * as store from "./storage.js";
import { CATEGORIES } from "./quiz-engine.js";
import { JOURNAL_TEMPLATES } from "./content.js";
import {
  SCHEMA, ACCENTS, ALWAYS_VISIBLE_PAGES, FOCUS_ONLY_PAGES, PREFS_KEY,
  getPref, setPref, resetPrefs, exportPrefs, importPrefs, applyPrefs,
} from "./prefs.js";
import { applyA11y } from "./features.js";

function el(html) {
  const t = document.createElement("template");
  t.innerHTML = html.trim();
  return t.content.firstElementChild;
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

const PAGES = [
  ["roadmap", "Roadmap"], ["coach", "Coach"], ["progress", "Progress"],
  ["focus", "Focus"], ["intercept", "Intercept"], ["tasks", "To-do"], ["alarms", "Alarms"],
  ["screentime", "Screen time"], ["partners", "Accountability"],
  ["sleep", "Sleep"], ["cycle", "Tracking"], ["body", "Body"], ["meds", "Medications"],
  ["wellness", "Wellness"], ["journal", "Journal"], ["mental-health", "Mental health"], ["screeners", "Screeners"],
  ["learn", "Learn"], ["explore", "Explore"], ["glossary", "Glossary"], ["birthdays", "Birthdays"],
  ["community", "Community"], ["care", "Care near me"], ["account", "Account"],
];

const SECTIONS = [
  ["appearance", "Appearance"], ["focus", "Focus & timers"], ["intercept", "Intercept & blocking"],
  ["sleep", "Sleep"], ["health", "Health & tracking"], ["journal", "Journal"],
  ["notify", "Notifications & sounds"], ["privacy", "Privacy & data"],
  ["access", "Accessibility links"], ["advanced", "Advanced"],
];

const DATA_PREFIX = "focuslens_";
const MAIN_KEY = "focuslens_web_v1";
const MED_HIDE_KEY = "focuslens_med_hide_name";

const CSS = `
.st-nav{position:sticky;z-index:5;display:flex;gap:6px;overflow-x:auto;padding:8px 0;margin:0 0 8px;background:var(--bg);-webkit-overflow-scrolling:touch}
.st-nav .chip{flex:0 0 auto;white-space:nowrap}
.st-sec{scroll-margin-top:110px}
.st-sec h2{font-size:18px;margin:0 0 4px}
.st-row{display:flex;flex-direction:column;gap:4px;padding:10px 0;border-bottom:1px solid var(--border)}
.st-row:last-child{border-bottom:none}
.st-row label,.st-row .st-lbl{font-size:14px;font-weight:600;color:var(--text)}
.st-row input[type=number],.st-row input[type=time],.st-row input[type=text],.st-row select,.st-row textarea{max-width:100%;width:100%}
@media(min-width:560px){.st-row input[type=number],.st-row input[type=time],.st-row select{width:200px}}
.st-check{display:flex;gap:10px;align-items:flex-start;font-size:14px;font-weight:600}
.st-check input{margin-top:3px;flex:0 0 auto}
.st-help{font-size:12px;color:var(--text-muted);margin:0}
.st-pair{display:grid;grid-template-columns:1fr 1fr;gap:10px}
.st-pair>div{display:flex;flex-direction:column;gap:4px;min-width:0}
.st-swatches{display:flex;flex-wrap:wrap;gap:10px}
.st-swatch{width:44px;height:44px;border-radius:50%;border:3px solid transparent;cursor:pointer;padding:0;box-shadow:0 0 0 1px var(--border)}
.st-swatch[aria-checked=true]{border-color:var(--text)}
.st-preview{display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-top:6px}
.st-preview .st-pbtn{border:none;color:#fff;padding:8px 14px;border-radius:999px;font-weight:600}
.st-pages{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:6px 12px}
.st-pages label{display:flex;gap:8px;align-items:center;font-size:14px;font-weight:400}
.st-actions{display:flex;gap:8px;flex-wrap:wrap;margin-top:8px}
.st-msg{font-size:13px;margin:8px 0 0}
.st-msg.err{color:var(--danger)}
.st-confirm{border:1px solid var(--danger);border-radius:12px;padding:12px;margin-top:10px}
.st-table{width:100%;border-collapse:collapse;font-size:13px}
.st-table td,.st-table th{border-bottom:1px solid var(--border);padding:6px 4px;text-align:left;vertical-align:top}
button.danger{background:var(--danger);color:#fff;border:none;border-radius:999px;padding:8px 14px;font-weight:600;cursor:pointer}
`;

function injectCss() {
  if (document.getElementById("st-css")) return;
  const s = document.createElement("style");
  s.id = "st-css";
  s.textContent = CSS;
  document.head.appendChild(s);
}

// ---- small builders -------------------------------------------------------------------------

function row(inner, help) {
  return `<div class="st-row">${inner}${help ? `<p class="st-help">${help}</p>` : ""}</div>`;
}

function numRow(id, label, key, help, extra = "") {
  const s = SCHEMA[key];
  return row(`<label for="${id}">${label}</label><input type="number" inputmode="numeric" id="${id}" min="${s.min}" max="${s.max}" step="1" value="${getPref(key)}" ${extra} />`, help);
}

function selRow(id, label, key, options, help) {
  const cur = String(getPref(key));
  const opts = options.map(([v, t]) => `<option value="${escapeHtml(v)}" ${String(v) === cur ? "selected" : ""}>${escapeHtml(t)}</option>`).join("");
  return row(`<label for="${id}">${label}</label><select id="${id}">${opts}</select>`, help);
}

function checkRow(id, label, checked, help) {
  return row(`<label class="st-check" for="${id}"><input type="checkbox" id="${id}" ${checked ? "checked" : ""} /> <span>${label}</span></label>`, help);
}

function card(id, title, intro, body) {
  return el(`<section class="card st-sec" id="st-${id}" aria-labelledby="st-h-${id}"><h2 id="st-h-${id}">${title}</h2>${intro ? `<p class="st-help">${intro}</p>` : ""}${body}</section>`);
}

// Binds a control to a pref; reads through setPref so the stored (clamped) value is shown back.
function bindPref(root, id, key, read) {
  const node = root.querySelector("#" + id);
  if (!node) return;
  node.onchange = () => {
    const v = setPref(key, read ? read(node) : node.value);
    if (node.type === "checkbox") node.checked = !!v;
    else node.value = Array.isArray(v) ? v.join(", ") : v;
  };
}

const readNum = (n) => (n.value === "" ? NaN : Number(n.value));

function safeGet(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }
function safeSet(k, v) { try { localStorage.setItem(k, v); return true; } catch (e) { return false; } }
function safeRemove(k) { try { localStorage.removeItem(k); } catch (e) {} }
function dataKeys() {
  const out = [];
  try { for (let i = 0; i < localStorage.length; i++) { const k = localStorage.key(i); if (k && k.startsWith(DATA_PREFIX)) out.push(k); } } catch (e) {}
  return out.sort();
}

function beep(volumePct) {
  try {
    const Ctx = window.AudioContext || window.webkitAudioContext;
    const ctx = new Ctx();
    const o = ctx.createOscillator();
    const g = ctx.createGain();
    o.frequency.value = 660;
    g.gain.value = Math.max(0, Math.min(1, volumePct / 100)) * 0.3;
    o.connect(g); g.connect(ctx.destination);
    o.start(); o.stop(ctx.currentTime + 0.35);
    setTimeout(() => { try { ctx.close(); } catch (e) {} }, 600);
  } catch (e) {}
}

function download(name, text, mime) {
  const url = URL.createObjectURL(new Blob([text], { type: mime }));
  const a = document.createElement("a");
  a.href = url; a.download = name; a.rel = "noopener";
  document.body.appendChild(a); a.click(); a.remove();
  return url;
}

// Categories the user can clear individually (keys inside the main app state).
const CLEAR_GROUPS = [
  { id: "journal", label: "Journal entries, custom prompts and templates", keys: ["journal", "customPrompts", "userTemplates"] },
  { id: "tracking", label: "Cycle and tracker logs", keys: ["cycle"] },
  { id: "sleep", label: "Sleep logs", keys: ["sleep"] },
  { id: "focus", label: "Focus sessions", keys: ["focusSessions"] },
  { id: "tasks", label: "To-dos, alarms and priority matrix", keys: ["tasks", "alarms", "matrix"] },
  { id: "habits", label: "Habits, hydration and pillars", keys: ["habits", "pillars", "water"] },
  { id: "body", label: "Activity and meals", keys: ["activity", "meals"] },
  { id: "checkins", label: "Check-ins and screener results", keys: ["checkIns", "screeners"] },
  { id: "meds", label: "Medication plans and dose log", keys: ["medPlans", "doseLog"] },
];

function clearGroup(g) {
  const raw = safeGet(MAIN_KEY);
  if (!raw) return;
  let st;
  try { st = JSON.parse(raw); } catch (e) { return; }
  if (!st || typeof st !== "object") return;
  g.keys.forEach((k) => {
    if (k === "doseLog") st[k] = {};
    else if (k === "water") delete st.water;
    else st[k] = [];
  });
  st.updatedAt = Date.now();
  safeSet(MAIN_KEY, JSON.stringify(st));
  if (g.id === "meds") safeRemove("focuslens_med_reminders");
}

// Validates an exported file before anything is written. Returns { ok, error, data, summary }.
function validateBackup(text) {
  if (typeof text !== "string" || !text.trim()) return { ok: false, error: "Choose a file or paste the exported JSON first." };
  if (text.length > 20 * 1024 * 1024) return { ok: false, error: "That file is too large to be a FocusLens backup." };
  let o;
  try { o = JSON.parse(text); } catch (e) { return { ok: false, error: "That is not valid JSON." }; }
  if (!o || typeof o !== "object" || o.app !== "focuslens" || !o.data || typeof o.data !== "object" || Array.isArray(o.data)) {
    return { ok: false, error: "This does not look like a FocusLens backup (missing app marker or data)." };
  }
  const keys = Object.keys(o.data);
  if (!keys.length) return { ok: false, error: "The backup contains no data." };
  for (const k of keys) {
    if (!/^focuslens_[a-z0-9_]{1,60}$/.test(k)) return { ok: false, error: `Unexpected key "${k.slice(0, 40)}" in backup.` };
    if (typeof o.data[k] !== "string") return { ok: false, error: `Value for "${k}" is not text.` };
    if ([MAIN_KEY, "focuslens_body_v1", PREFS_KEY, "focuslens_a11y"].includes(k)) {
      let v;
      try { v = JSON.parse(o.data[k]); } catch (e) { return { ok: false, error: `"${k}" is damaged and cannot be read.` }; }
      if (!v || typeof v !== "object") return { ok: false, error: `"${k}" has an unexpected shape.` };
    }
  }
  return { ok: true, data: o.data, summary: { count: keys.length, at: typeof o.exportedAt === "string" ? o.exportedAt : "" } };
}

function fmtBytes(n) { return n < 1024 ? `${n} B` : `${(n / 1024).toFixed(1)} KB`; }

// ---- page -----------------------------------------------------------------------------------

export function renderSettings(container, rerender) {
  injectCss();
  const redo = () => { applyPrefs(); applyA11y(); if (rerender) rerender(); };

  container.appendChild(el(`<div><h1>Settings</h1><p class="subtitle">Everything here is saved on this device and applied straight away. Nothing is sent anywhere unless you sign in and leave sync on.</p></div>`));

  const nav = el(`<nav class="st-nav" aria-label="Settings sections"></nav>`);
  SECTIONS.forEach(([id, label]) => {
    const b = el(`<button type="button" class="chip" data-sec="${id}">${label}</button>`);
    b.onclick = () => {
      const t = container.querySelector("#st-" + id);
      if (t) { t.scrollIntoView({ behavior: "smooth", block: "start" }); const h = t.querySelector("h2"); if (h) { h.tabIndex = -1; h.focus({ preventScroll: true }); } }
    };
    nav.appendChild(b);
  });
  container.appendChild(nav);
  const placeNav = () => {
    const tb = document.querySelector(".topbar");
    const h = tb && getComputedStyle(tb).position === "sticky" ? tb.offsetHeight : 0;
    nav.style.top = h + "px";
    container.querySelectorAll(".st-sec").forEach((s) => { s.style.scrollMarginTop = h + 60 + "px"; });
  };
  placeNav();
  const onResize = () => { if (!nav.isConnected) window.removeEventListener("resize", onResize); else placeNav(); };
  window.addEventListener("resize", onResize, { passive: true });

  // -- Appearance
  const accent = getPref("accent");
  const swatches = Object.keys(ACCENTS).map((k) => `<button type="button" role="radio" class="st-swatch" id="accent-${k}" data-accent="${k}" aria-checked="${k === accent}" aria-label="${k[0].toUpperCase() + k.slice(1)} accent" title="${k}" style="background:${ACCENTS[k]}"></button>`).join("");
  const app = card("appearance", "Appearance", "How FocusLens looks. Light / dark theme and contrast are on the Help page (see Accessibility links).",
    row(`<span class="st-lbl" id="accent-lbl">Accent colour</span><div class="st-swatches" role="radiogroup" aria-labelledby="accent-lbl">${swatches}</div><div class="st-preview" aria-hidden="true"><span class="st-pbtn" id="accent-preview" style="background:${ACCENTS[accent]}">Preview</span><span class="muted" id="accent-name">${accent}</span></div>`, "Applies instantly.") +
    selRow("pref-density", "Density", "density", [["comfortable", "Comfortable"], ["compact", "Compact"]], "Compact fits more on screen with tighter spacing.") +
    selRow("pref-radius", "Corner style", "radius", [["soft", "Soft"], ["round", "Round"], ["sharp", "Sharp"]], "Shape of cards and buttons.") +
    selRow("pref-font", "Font", "font", [["jakarta", "Plus Jakarta Sans (default)"], ["system", "System font"], ["dyslexia-friendly", "Dyslexia-friendly (Atkinson Hyperlegible)"]], "Dyslexia-friendly uses OpenDyslexic or Atkinson Hyperlegible if installed, otherwise your system font.") +
    selRow("pref-nav", "Menu", "nav", [["full", "Show every page"], ["focus-only", "Focus only (hide non-essential pages)"]], `Focus only keeps: ${FOCUS_ONLY_PAGES.join(", ")}.`));
  app.querySelectorAll(".st-swatch").forEach((b) => {
    b.onclick = () => {
      const v = setPref("accent", b.dataset.accent);
      app.querySelectorAll(".st-swatch").forEach((x) => x.setAttribute("aria-checked", String(x.dataset.accent === v)));
      app.querySelector("#accent-preview").style.background = ACCENTS[v];
      app.querySelector("#accent-name").textContent = v;
    };
    b.onkeydown = (e) => {
      const list = [...app.querySelectorAll(".st-swatch")];
      const i = list.indexOf(b);
      const d = e.key === "ArrowRight" || e.key === "ArrowDown" ? 1 : e.key === "ArrowLeft" || e.key === "ArrowUp" ? -1 : 0;
      if (d) { e.preventDefault(); const n = list[(i + d + list.length) % list.length]; n.focus(); n.click(); }
    };
  });
  ["density", "radius", "font", "nav"].forEach((k) => bindPref(app, "pref-" + k, k));
  container.appendChild(app);

  // -- Focus
  const dur = (id, key, label) => `<div><label for="${id}">${label}</label><input type="number" inputmode="numeric" id="${id}" min="${SCHEMA[key].min}" max="${SCHEMA[key].max}" value="${getPref(key)}" /></div>`;
  const focus = card("focus", "Focus & timers", "Block lengths in minutes for each timer mode.",
    row(`<span class="st-lbl">Pomodoro</span><div class="st-pair">${dur("pref-pomoFocus", "pomoFocus", "Focus (min)")}${dur("pref-pomoRest", "pomoRest", "Break (min)")}</div>`, "Default 25 / 5.") +
    row(`<span class="st-lbl">52 / 17</span><div class="st-pair">${dur("pref-r52Focus", "r52Focus", "Focus (min)")}${dur("pref-r52Rest", "r52Rest", "Break (min)")}</div>`, "Default 52 / 17.") +
    row(`<span class="st-lbl">90-minute sprint</span><div class="st-pair">${dur("pref-ultraFocus", "ultraFocus", "Focus (min)")}${dur("pref-ultraRest", "ultraRest", "Break (min)")}</div>`, "Default 90 / 20.") +
    checkRow("pref-autoStart", "Start the next block automatically", getPref("autoStart"), "When a focus or break block ends, begin the next one without tapping."));
  ["pomoFocus", "pomoRest", "r52Focus", "r52Rest", "ultraFocus", "ultraRest"].forEach((k) => bindPref(focus, "pref-" + k, k, readNum));
  bindPref(focus, "pref-autoStart", "autoStart", (n) => n.checked);
  container.appendChild(focus);

  // -- Intercept
  const cats = getPref("qCategories");
  const catBoxes = CATEGORIES.map((c) => `<label><input type="checkbox" class="q-cat" id="q-cat-${c.id}" value="${c.id}" ${cats.includes(c.id) ? "checked" : ""} /> ${escapeHtml(c.label)}</label>`).join("");
  const lvl = [1, 2, 3, 4, 5].map((n) => [n, `Level ${n}`]);
  const inter = card("intercept", "Intercept & blocking", "The question you answer before a blocked app or site opens.",
    row(`<span class="st-lbl" id="q-cats-lbl">Question categories</span><div class="st-pages" role="group" aria-labelledby="q-cats-lbl">${catBoxes}</div>`, "At least one stays on; if you untick all, every category is used.") +
    selRow("pref-qNeeded", "Correct answers needed to unlock", "qNeeded", [1, 2, 3, 4, 5].map((n) => [n, String(n)]), "More answers means more friction.") +
    selRow("pref-qFloor", "Starting difficulty (minimum)", "qFloor", lvl, "Questions never start easier than this. 1 is easiest, 5 hardest.") +
    checkRow("pref-qHarder", "Get harder as the day goes on", getPref("qHarder"), "Each unlock attempt starts one level higher than the last.") +
    selRow("pref-qDelay", "Wait before the first question", "qDelay", [[0, "No delay"], [5, "5 seconds"], [10, "10 seconds"], [30, "30 seconds"]], "A short pause that gives the urge time to pass.") +
    numRow("pref-qPasses", "Emergency passes per day", "qPasses", "Skip the questions for a genuine need. 0 turns passes off."));
  const syncCats = () => {
    const picked = [...inter.querySelectorAll(".q-cat:checked")].map((n) => n.value);
    setPref("qCategories", picked);
    const now = getPref("qCategories");
    inter.querySelectorAll(".q-cat").forEach((n) => { n.checked = now.includes(n.value); });
  };
  inter.querySelectorAll(".q-cat").forEach((n) => { n.onchange = syncCats; });
  ["qNeeded", "qFloor", "qDelay"].forEach((k) => bindPref(inter, "pref-" + k, k));
  bindPref(inter, "pref-qHarder", "qHarder", (n) => n.checked);
  bindPref(inter, "pref-qPasses", "qPasses", readNum);
  container.appendChild(inter);

  // -- Sleep
  const sleep = card("sleep", "Sleep", "Your sleep goal and routine.",
    row(`<label for="sl-target">Target hours per night</label><input type="number" id="sl-target" min="4" max="12" step="0.5" value="${store.getSleepTargetHours()}" />`, "Most adults do well on 7 to 9.") +
    row(`<label for="pref-wakeTime">Usual wake time</label><input type="time" id="pref-wakeTime" value="${getPref("wakeTime")}" />`, "Used to work out a suggested bedtime.") +
    numRow("pref-sleepLatency", "Minutes to fall asleep", "sleepLatency", "How long you usually take to drift off after lights out."));
  sleep.querySelector("#sl-target").onchange = (e) => {
    const n = Math.min(12, Math.max(4, Math.round(Number(e.target.value) * 2) / 2));
    const v = Number.isFinite(n) ? n : 8;
    store.setSleepTargetHours(v);
    e.target.value = v;
  };
  bindPref(sleep, "pref-wakeTime", "wakeTime");
  bindPref(sleep, "pref-sleepLatency", "sleepLatency", readNum);
  container.appendChild(sleep);

  // -- Health
  const water = store.waterToday();
  const health = card("health", "Health & tracking", "Targets and display choices for Wellness, Tracking and Body.",
    row(`<label for="h-water">Daily water target (ml)</label><input type="number" id="h-water" min="500" max="6000" step="50" value="${water.target}" />`, "Saved to your tracker data.") +
    row(`<label for="h-quick">Quick-add sizes (ml)</label><input type="text" id="h-quick" inputmode="numeric" value="${getPref("waterQuick").join(", ")}" />`, "Up to six sizes between 50 and 2000, separated by commas.") +
    row(`<label for="h-steps">Daily step goal</label><input type="number" id="h-steps" min="1000" max="50000" step="500" value="${store.getStepGoal()}" />`, "Saved to your tracker data.") +
    selRow("pref-weekStart", "Week starts on", "weekStart", [["mon", "Monday"], ["sun", "Sunday"]], "Used for weekly summaries and calendars.") +
    selRow("pref-units", "Units", "units", [["metric", "Metric (ml, km, kg)"], ["imperial", "Imperial (fl oz, mi, lb)"]], "Display only. Stored values stay metric.") +
    `<h3 style="margin:14px 0 0;font-size:15px">Cycle estimator</h3>` +
    numRow("pref-periodLen", "Typical period length (days)", "periodLen", "3 to 8 days.") +
    numRow("pref-lutealLen", "Luteal phase length (days)", "lutealLen", "11 to 17 days; 14 is the common default. Estimates only, not medical advice."));
  health.querySelector("#h-water").onchange = (e) => {
    const n = Math.min(6000, Math.max(500, Math.round(Number(e.target.value))));
    const v = Number.isFinite(n) ? n : 2500;
    store.setWaterTarget(v); e.target.value = v;
  };
  health.querySelector("#h-steps").onchange = (e) => {
    const n = Math.min(50000, Math.max(1000, Math.round(Number(e.target.value))));
    const v = Number.isFinite(n) ? n : 8000;
    store.setStepGoal(v); e.target.value = v;
  };
  health.querySelector("#h-quick").onchange = (e) => {
    const parts = e.target.value.split(/[\s,;]+/).filter(Boolean);
    let v = setPref("waterQuick", parts);
    if (!v.length) v = setPref("waterQuick", SCHEMA.waterQuick.def);
    e.target.value = v.join(", ");
  };
  ["weekStart", "units"].forEach((k) => bindPref(health, "pref-" + k, k));
  ["periodLen", "lutealLen"].forEach((k) => bindPref(health, "pref-" + k, k, readNum));
  container.appendChild(health);

  // -- Journal
  const tplOpts = [["", "Blank page"], ...JOURNAL_TEMPLATES.map((t) => [t.title, t.title])];
  const medHide = safeGet(MED_HIDE_KEY) !== "0";
  const journal = card("journal", "Journal", "Defaults for new entries.",
    selRow("pref-journalTemplate", "Default template", "journalTemplate", tplOpts, "Pre-fills new entries with this structure.") +
    row(`<label for="pref-journalReminder">Daily reminder time</label><div class="st-actions" style="margin:0"><input type="time" id="pref-journalReminder" value="${getPref("journalReminder")}" /><button type="button" class="text" id="j-rem-clear">No reminder</button></div>`, "Leave empty for no reminder."));
  bindPref(journal, "pref-journalTemplate", "journalTemplate");
  bindPref(journal, "pref-journalReminder", "journalReminder");
  journal.querySelector("#j-rem-clear").onclick = () => { setPref("journalReminder", ""); journal.querySelector("#pref-journalReminder").value = ""; };
  container.appendChild(journal);

  // -- Notifications & sounds
  const perm = (window.Notification && Notification.permission) || "unsupported";
  const permText = { granted: "Allowed", denied: "Blocked in your browser settings", default: "Not asked yet", unsupported: "Not supported in this browser" };
  const notify = card("notify", "Notifications & sounds", "Alerts for timers, alarms and reminders.",
    row(`<span class="st-lbl">Notification permission</span><div class="st-actions" style="margin:0"><span id="n-status" role="status">${permText[perm]}</span><button type="button" class="text" id="n-btn" ${perm === "granted" || perm === "denied" || perm === "unsupported" ? "hidden" : ""}>Allow notifications</button></div>`, "Blocked permission can only be changed in your browser's site settings.") +
    checkRow("pref-sound", "Play sounds", getPref("sound"), "Timer and alarm chimes.") +
    row(`<label for="pref-volume">Volume: <span id="vol-val">${getPref("volume")}</span>%</label><input type="range" id="pref-volume" min="0" max="100" step="5" value="${getPref("volume")}" style="width:100%" /><div class="st-actions" style="margin:0"><button type="button" class="text" id="vol-test">Play test sound</button></div>`) +
    checkRow("m-hide", "Hide medication names in notifications", medHide, "Reminders say \"Time for a dose\" instead of naming the medication. Same setting as on the Medications page.") +
    checkRow("m-local", "Keep medication data on this device only", store.getMedsLocalOnly(), "When on, medication plans and the dose log are never uploaded to your account."));
  notify.querySelector("#n-btn").onclick = async () => {
    try { await Notification.requestPermission(); } catch (e) {}
    const p = (window.Notification && Notification.permission) || "unsupported";
    notify.querySelector("#n-status").textContent = permText[p];
    if (p !== "default") notify.querySelector("#n-btn").hidden = true;
  };
  bindPref(notify, "pref-sound", "sound", (n) => n.checked);
  const vol = notify.querySelector("#pref-volume");
  vol.oninput = () => { notify.querySelector("#vol-val").textContent = vol.value; };
  vol.onchange = () => { const v = setPref("volume", Number(vol.value)); vol.value = v; notify.querySelector("#vol-val").textContent = v; };
  notify.querySelector("#vol-test").onclick = () => beep(getPref("volume"));
  notify.querySelector("#m-hide").onchange = (e) => safeSet(MED_HIDE_KEY, e.target.checked ? "1" : "0");
  notify.querySelector("#m-local").onchange = (e) => store.setMedsLocalOnly(e.target.checked);
  container.appendChild(notify);

  // -- Privacy & data
  const hidden = getPref("hiddenPages");
  const pageBoxes = PAGES.map(([id, label]) => `<label><input type="checkbox" class="hp" id="hp-${id}" value="${id}" ${hidden.includes(id) ? "checked" : ""} /> ${escapeHtml(label)}</label>`).join("");
  const privacy = card("privacy", "Privacy & data", "Your data lives in this browser. If you sign in and sync is on, a copy is also kept in your account (medication data excepted unless you opt in).", `
    <div class="st-row"><span class="st-lbl">Hidden pages</span>
      <p class="st-help">Tick pages to hide from the menu. They still work if you open the link. ${ALWAYS_VISIBLE_PAGES.map((p) => p[0].toUpperCase() + p.slice(1)).join(" and ")} can never be hidden.</p>
      <div class="st-pages" role="group" aria-label="Hidden pages">${pageBoxes}</div></div>
    <div class="st-row"><span class="st-lbl">Export all my data</span>
      <p class="st-help">Downloads one JSON file with everything stored on this device.</p>
      <div class="st-actions"><button type="button" class="primary" id="d-export">Export data (JSON)</button></div>
      <div id="d-export-out" class="st-msg" role="status"></div></div>
    <div class="st-row"><span class="st-lbl">Import data</span>
      <p class="st-help">Restore from a FocusLens export. This replaces the matching data on this device.</p>
      <label for="d-file" class="st-help">Backup file</label><input type="file" id="d-file" accept="application/json,.json" />
      <label for="d-paste" class="st-help">Or paste the JSON</label><textarea id="d-paste" rows="3" spellcheck="false"></textarea>
      <div class="st-actions"><button type="button" class="text" id="d-check">Check backup</button></div>
      <div id="d-import-out" role="status" aria-live="polite"></div></div>
    <div class="st-row"><span class="st-lbl">Clear specific data</span>
      <p class="st-help">Pick what to erase. Everything else is kept.</p>
      <div class="st-pages" role="group" aria-label="Data to clear">${CLEAR_GROUPS.map((g) => `<label><input type="checkbox" class="cg" id="cg-${g.id}" value="${g.id}" /> ${escapeHtml(g.label)}</label>`).join("")}</div>
      <div class="st-actions"><button type="button" class="text" id="d-clear">Clear selected</button></div>
      <div id="d-clear-out" role="status" aria-live="polite"></div></div>
    <div class="st-row"><span class="st-lbl">Where is my data?</span><div id="d-where"></div></div>
    <div class="st-row"><span class="st-lbl">Delete all local data</span>
      <p class="st-help">Erases everything FocusLens stored in this browser, including settings. It does not delete an account copy.</p>
      <div class="st-actions"><button type="button" class="danger" id="d-del">Delete all local data...</button></div>
      <div id="d-del-out" role="status" aria-live="polite"></div></div>`);

  privacy.querySelectorAll(".hp").forEach((n) => {
    n.onchange = () => setPref("hiddenPages", [...privacy.querySelectorAll(".hp:checked")].map((x) => x.value));
  });

  // Export
  const outBox = privacy.querySelector("#d-export-out");
  privacy.querySelector("#d-export").onclick = () => {
    const data = {};
    dataKeys().forEach((k) => { const v = safeGet(k); if (v !== null) data[k] = v; });
    const text = JSON.stringify({ app: "focuslens", version: 1, exportedAt: new Date().toISOString(), data }, null, 2);
    const name = `focuslens-backup-${new Date().toISOString().slice(0, 10)}.json`;
    let url;
    try { url = download(name, text, "application/json"); } catch (e) { url = null; }
    outBox.innerHTML = "";
    outBox.append(`Exported ${Object.keys(data).length} items. If nothing downloaded: `);
    if (url) {
      const a = el(`<a href="${url}" download="${name}">save ${name}</a>`);
      outBox.appendChild(a);
    } else {
      outBox.append("copy the text below.");
    }
    const ta = el(`<textarea readonly rows="3" aria-label="Exported data" style="margin-top:6px"></textarea>`);
    ta.value = text;
    outBox.appendChild(ta);
  };

  // Import (validate, then confirm in page)
  const impOut = privacy.querySelector("#d-import-out");
  const fileIn = privacy.querySelector("#d-file");
  const paste = privacy.querySelector("#d-paste");
  fileIn.onchange = () => {
    const f = fileIn.files && fileIn.files[0];
    if (!f) return;
    if (f.size > 20 * 1024 * 1024) { impOut.innerHTML = `<p class="st-msg err">That file is too large to be a FocusLens backup.</p>`; return; }
    const r = new FileReader();
    r.onload = () => { paste.value = String(r.result || ""); privacy.querySelector("#d-check").click(); };
    r.onerror = () => { impOut.innerHTML = `<p class="st-msg err">Could not read that file.</p>`; };
    r.readAsText(f);
  };
  privacy.querySelector("#d-check").onclick = () => {
    const res = validateBackup(paste.value);
    if (!res.ok) { impOut.innerHTML = `<p class="st-msg err" role="alert">${escapeHtml(res.error)}</p>`; return; }
    impOut.innerHTML = `<div class="st-confirm"><p class="st-msg" style="margin:0">Backup looks valid: ${res.summary.count} items${res.summary.at ? `, exported ${escapeHtml(res.summary.at.slice(0, 10))}` : ""}. Importing replaces the matching data on this device.</p>
      <div class="st-actions"><button type="button" class="primary" id="d-imp-go">Replace my data</button><button type="button" class="text" id="d-imp-no">Cancel</button></div></div>`;
    impOut.querySelector("#d-imp-no").onclick = () => { impOut.innerHTML = ""; };
    impOut.querySelector("#d-imp-go").onclick = () => {
      let failed = 0;
      Object.keys(res.data).forEach((k) => { if (!safeSet(k, res.data[k])) failed++; });
      if (failed) { impOut.innerHTML = `<p class="st-msg err" role="alert">Browser storage is full or blocked; ${failed} items were not saved.</p>`; return; }
      impOut.innerHTML = `<p class="st-msg" role="status">Imported ${res.summary.count} items.</p>`;
      redo();
    };
  };

  // Clear categories (two-step)
  const clrOut = privacy.querySelector("#d-clear-out");
  privacy.querySelector("#d-clear").onclick = () => {
    const ids = [...privacy.querySelectorAll(".cg:checked")].map((n) => n.value);
    if (!ids.length) { clrOut.innerHTML = `<p class="st-msg err" role="alert">Tick at least one category first.</p>`; return; }
    clrOut.innerHTML = `<div class="st-confirm"><p class="st-msg" style="margin:0">Erase ${ids.length} categor${ids.length === 1 ? "y" : "ies"} from this device? This cannot be undone.</p>
      <div class="st-actions"><button type="button" class="danger" id="d-clr-go">Yes, erase</button><button type="button" class="text" id="d-clr-no">Cancel</button></div></div>`;
    clrOut.querySelector("#d-clr-no").onclick = () => { clrOut.innerHTML = ""; };
    clrOut.querySelector("#d-clr-go").onclick = () => {
      CLEAR_GROUPS.filter((g) => ids.includes(g.id)).forEach(clearGroup);
      privacy.querySelectorAll(".cg").forEach((n) => { n.checked = false; });
      clrOut.innerHTML = `<p class="st-msg" role="status">Cleared.</p>`;
      paintWhere();
    };
  };

  // Where is my data
  function paintWhere() {
    const keys = dataKeys();
    const sync = store.getSyncEnabled();
    const labels = { [MAIN_KEY]: "Journal, tasks, sleep, habits, medications and other app data", focuslens_body_v1: "Body page measurements", [PREFS_KEY]: "These settings", focuslens_a11y: "Theme and accessibility", focuslens_sync_enabled: "Sync on/off choice", focuslens_meds_local_only: "Medication privacy choice" };
    const rows = keys.map((k) => `<tr><td>${escapeHtml(labels[k] || k.replace(DATA_PREFIX, ""))}</td><td>${fmtBytes((safeGet(k) || "").length)}</td></tr>`).join("");
    privacy.querySelector("#d-where").innerHTML = `
      <p class="st-help"><strong>This device:</strong> everything below, in this browser's storage.<br>
      <strong>Your account:</strong> ${sync ? "a synced copy of app data when you are signed in" : "nothing, because sync is off (this device only)"}${store.getMedsLocalOnly() ? "; medication data is never uploaded" : "; medication data is included in sync"}. Manage sign-in and sync on the <a href="#account">Account page</a>.</p>
      <table class="st-table"><caption class="st-help" style="text-align:left">Stored on this device</caption><thead><tr><th>Item</th><th>Size</th></tr></thead><tbody>${rows || `<tr><td colspan="2">Nothing stored yet.</td></tr>`}</tbody></table>`;
  }
  paintWhere();

  // Delete everything (two-step)
  const delOut = privacy.querySelector("#d-del-out");
  privacy.querySelector("#d-del").onclick = () => {
    delOut.innerHTML = `<div class="st-confirm" role="alertdialog" aria-label="Confirm delete"><p class="st-msg" style="margin:0"><strong>Delete everything stored on this device?</strong> Journal, logs, tasks, medication plans and settings will be erased. Export first if you might want it back.</p>
      <div class="st-actions"><button type="button" class="danger" id="d-del-go">Yes, delete everything</button><button type="button" class="text" id="d-del-no">Cancel</button></div></div>`;
    delOut.querySelector("#d-del-no").onclick = () => { delOut.innerHTML = ""; privacy.querySelector("#d-del").focus(); };
    delOut.querySelector("#d-del-go").onclick = () => {
      dataKeys().forEach(safeRemove);
      delOut.innerHTML = `<p class="st-msg" role="status">All local data deleted.</p>`;
      redo();
    };
    delOut.querySelector("#d-del-no").focus();
  };
  container.appendChild(privacy);

  // -- Accessibility links
  const a11y = (() => { try { return JSON.parse(safeGet("focuslens_a11y")) || {}; } catch (e) { return {}; } })();
  const access = card("access", "Accessibility links", "Theme, contrast, text size and motion are kept in one place so they never disagree.",
    row(`<span class="st-lbl">Current display settings</span><p class="st-help" id="a-now">Theme: ${escapeHtml(a11y.theme || "match device")} · High contrast: ${a11y.contrast ? "on" : "off"} · Larger text: ${a11y.large ? "on" : "off"} · Reduce motion: ${a11y.reduceMotion ? "on" : "off"}</p>`) +
    row(`<a href="#help" id="a-help-link">Change theme, contrast, text size and motion on the Help page</a>`, "The Help page also has helplines and care finders.") +
    row(`<span class="st-help">Every control in the app works with a keyboard and screen reader. The font choice above includes a dyslexia-friendly option.</span>`));
  container.appendChild(access);

  // -- Advanced
  const adv = card("advanced", "Advanced", "Settings backup and reset.", `
    <div class="st-row"><span class="st-lbl">Settings as JSON</span>
      <p class="st-help">Copy these settings to another device, or paste settings here and apply them. Only this page's settings; not your journal or logs.</p>
      <label for="adv-json" class="st-help">Settings JSON</label><textarea id="adv-json" rows="5" spellcheck="false"></textarea>
      <div class="st-actions"><button type="button" class="text" id="adv-load">Show current</button><button type="button" class="text" id="adv-apply">Apply pasted settings</button></div>
      <div id="adv-out" class="st-msg" role="status" aria-live="polite"></div></div>
    <div class="st-row"><span class="st-lbl">Reset all settings</span>
      <p class="st-help">Puts every setting on this page back to its default. Your data, theme and accessibility choices are not touched.</p>
      <div class="st-actions"><button type="button" class="danger" id="adv-reset">Reset all settings...</button></div>
      <div id="adv-reset-out" role="status" aria-live="polite"></div></div>`);
  const ta = adv.querySelector("#adv-json");
  const advOut = adv.querySelector("#adv-out");
  adv.querySelector("#adv-load").onclick = () => { ta.value = exportPrefs(); advOut.className = "st-msg"; advOut.textContent = "Current settings shown."; };
  adv.querySelector("#adv-apply").onclick = () => {
    const r = importPrefs(ta.value);
    advOut.className = "st-msg" + (r.ok ? "" : " err");
    advOut.textContent = r.ok ? `Applied ${r.applied} settings.` : r.error;
    if (r.ok) redo();
  };
  const rsOut = adv.querySelector("#adv-reset-out");
  adv.querySelector("#adv-reset").onclick = () => {
    rsOut.innerHTML = `<div class="st-confirm"><p class="st-msg" style="margin:0">Reset every setting to its default?</p><div class="st-actions"><button type="button" class="danger" id="adv-reset-go">Yes, reset</button><button type="button" class="text" id="adv-reset-no">Cancel</button></div></div>`;
    rsOut.querySelector("#adv-reset-no").onclick = () => { rsOut.innerHTML = ""; };
    rsOut.querySelector("#adv-reset-go").onclick = () => { resetPrefs(); redo(); };
  };
  container.appendChild(adv);
}
