// FocusLens Distraction Blocker - pure logic (no browser APIs, no I/O).
// Imported by the service worker, the extension pages and the Node tests.

import { CATEGORIES } from './quiz-engine.js';

export const GATE_CATEGORY_IDS = CATEGORIES.map((c) => c.id);
export const DAY_MS = 86400000;
export const EMERGENCY_WINDOW_MS = 10 * 60 * 1000;
export const CONTINUE_WINDOW_MS = 5 * 60 * 1000;
export const FOCUS_CHOICES = [25, 50, 90];
export const DELAY_CHOICES = [0, 5, 10, 30];
export const STATS_DAYS = 30;

export const PRESET_PACKS = {
  social: { label: 'Social', domains: ['facebook.com', 'instagram.com', 'x.com', 'twitter.com', 'tiktok.com', 'snapchat.com', 'reddit.com', 'pinterest.com', 'tumblr.com', 'threads.net'] },
  video: { label: 'Video', domains: ['youtube.com', 'netflix.com', 'twitch.tv', 'hulu.com', 'disneyplus.com', 'primevideo.com', 'dailymotion.com', 'vimeo.com'] },
  news: { label: 'News', domains: ['cnn.com', 'bbc.com', 'foxnews.com', 'nytimes.com', 'theguardian.com', 'washingtonpost.com', 'huffpost.com', 'buzzfeed.com', 'reuters.com', 'news.google.com', 'ndtv.com', 'timesofindia.indiatimes.com'] },
  shopping: { label: 'Shopping', domains: ['amazon.com', 'ebay.com', 'aliexpress.com', 'etsy.com', 'walmart.com', 'target.com', 'flipkart.com', 'myntra.com', 'temu.com', 'shein.com'] },
  games: { label: 'Games', domains: ['store.steampowered.com', 'steamcommunity.com', 'roblox.com', 'epicgames.com', 'miniclip.com', 'poki.com', 'crazygames.com', 'y8.com', 'itch.io', 'chess.com'] },
  adult: { label: 'Adult content', domains: ['pornhub.com', 'xvideos.com', 'xnxx.com', 'xhamster.com', 'redtube.com', 'youporn.com', 'spankbang.com', 'chaturbate.com', 'stripchat.com', 'onlyfans.com'] },
};

// ---------- domains ----------

/** Turn user input (domain or full URL) into a bare lowercase hostname, or null. */
export function normalizeDomain(input) {
  if (typeof input !== 'string') return null;
  let s = input.trim().toLowerCase();
  if (!s || /\s/.test(s)) return null;
  if (!/^[a-z][a-z0-9+.-]*:\/\//.test(s)) s = 'http://' + s;
  let host;
  try { host = new URL(s).hostname; } catch { return null; }
  host = host.replace(/\.$/, '');
  if (host.startsWith('www.')) host = host.slice(4);
  return isValidDomain(host) ? host : null;
}

export function isValidDomain(host) {
  if (typeof host !== 'string' || host.length < 1 || host.length > 253) return false;
  if (host === 'localhost') return true;
  if (/^\d{1,3}(\.\d{1,3}){3}$/.test(host)) return host.split('.').every((n) => Number(n) <= 255);
  const labels = host.split('.');
  if (labels.length < 2) return false;
  if (!labels.every((l) => /^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$/.test(l))) return false;
  return /^([a-z]{2,63}|xn--[a-z0-9-]{1,59})$/.test(labels[labels.length - 1]);
}

/** Does `host` equal `entry` or is it a subdomain of it? */
export function hostMatches(host, entry) {
  return host === entry || host.endsWith('.' + entry);
}

/** Most specific blocklist entry that covers `host`, or null. */
export function matchEntry(host, entries) {
  let best = null;
  for (const e of entries) if (hostMatches(host, e) && (!best || e.length > best.length)) best = e;
  return best;
}

/** User domains + enabled preset packs, normalised and de-duplicated. */
export function effectiveDomains(settings) {
  const out = new Set();
  for (const d of settings.blocklist || []) { const n = normalizeDomain(d); if (n) out.add(n); }
  for (const [key, on] of Object.entries(settings.packs || {})) {
    if (on && PRESET_PACKS[key]) PRESET_PACKS[key].domains.forEach((d) => out.add(d));
  }
  return [...out].sort();
}

// ---------- schedules ----------

export function parseHHMM(s) {
  const m = /^([01]?\d|2[0-3]):([0-5]\d)$/.exec(String(s || '').trim());
  return m ? Number(m[1]) * 60 + Number(m[2]) : null;
}

/** Is the schedule's window active at `date` (local time)? days: 0=Sun..6=Sat. Supports crossing midnight. */
export function isWindowActive(schedule, date = new Date()) {
  const start = parseHHMM(schedule.start);
  const end = parseHHMM(schedule.end);
  if (start === null || end === null || start === end) return false;
  const days = Array.isArray(schedule.days) ? schedule.days : [];
  const today = date.getDay();
  const yesterday = (today + 6) % 7;
  const now = date.getHours() * 60 + date.getMinutes();
  if (start < end) return days.includes(today) && now >= start && now < end;
  return (days.includes(today) && now >= start) || (days.includes(yesterday) && now < end);
}

export function activeSchedules(settings, date = new Date()) {
  return (settings.schedules || []).filter((s) => s.enabled !== false && isWindowActive(s, date));
}

export function isFocusActive(settings, now = Date.now()) {
  return Number(settings.focusUntil) > now;
}

/** Should the blocklist be enforced right now? */
export function shouldBlockNow(settings, date = new Date()) {
  if (!settings.enabled) return false;
  if (isFocusActive(settings, date.getTime())) return true;
  const scheds = (settings.schedules || []).filter((s) => s.enabled !== false);
  if (scheds.length === 0) return true;
  return scheds.some((s) => isWindowActive(s, date));
}

/** Strict-mode lock: settings cannot be edited or paused while a window (or focus session) is active. */
export function getLockInfo(settings, date = new Date()) {
  if (!settings.strict) return { locked: false, reason: '' };
  const act = activeSchedules(settings, date);
  if (act.length) {
    const s = act[0];
    return { locked: true, reason: `Strict mode is on and "${s.name}" is active until ${s.end}. Settings are locked until it ends.` };
  }
  if (isFocusActive(settings, date.getTime())) {
    const mins = Math.ceil((settings.focusUntil - date.getTime()) / 60000);
    return { locked: true, reason: `Strict mode is on and a focus session is running (${mins} min left). Settings are locked until it ends.` };
  }
  return { locked: false, reason: '' };
}

export function validateSchedule(s) {
  const errors = [];
  if (!s || typeof s.name !== 'string' || !s.name.trim()) errors.push('Give the schedule a name.');
  if (parseHHMM(s && s.start) === null) errors.push('Start time must be HH:MM.');
  if (parseHHMM(s && s.end) === null) errors.push('End time must be HH:MM.');
  if (parseHHMM(s && s.start) !== null && parseHHMM(s.start) === parseHHMM(s.end)) errors.push('Start and end must differ.');
  if (!Array.isArray(s && s.days) || s.days.length === 0 || !s.days.every((d) => Number.isInteger(d) && d >= 0 && d <= 6)) errors.push('Pick at least one day.');
  return errors;
}

// ---------- DNR rules ----------

/**
 * Build declarativeNetRequest dynamic rules. Each blocked domain (subdomains included, via requestDomains)
 * redirects main-frame navigations to `blockedPageUrl` + '#' + original URL.
 * `allowed` maps blocklist entries to an expiry timestamp (ms) for temporary passes.
 */
export function buildRules(domains, blockedPageUrl, allowed = {}, now = Date.now()) {
  const rules = [];
  let id = 1;
  for (const d of domains) {
    if ((allowed[d] || 0) > now) continue;
    rules.push({
      id: id++,
      priority: 1,
      action: { type: 'redirect', redirect: { regexSubstitution: blockedPageUrl + '#\\1' } },
      condition: { regexFilter: '^(https?://.*)$', requestDomains: [d], resourceTypes: ['main_frame'] },
    });
  }
  return rules;
}

// ---------- quiz ----------

export function normalizeAnswer(s) {
  return String(s ?? '').toLowerCase().normalize('NFKD').replace(/[̀-ͯ]/g, '').replace(/[^\p{L}\p{N}\s]/gu, ' ').replace(/\s+/g, ' ').trim();
}

export function validateQuestion(q) {
  if (!q || typeof q.q !== 'string' || !q.q.trim()) return false;
  if (q.type === 'mc') {
    const ch = Array.isArray(q.choices) ? q.choices.filter((c) => typeof c === 'string' && c.trim()) : [];
    return ch.length >= 2 && Number.isInteger(q.answer) && q.answer >= 0 && q.answer < (q.choices || []).length && !!String(q.choices[q.answer] || '').trim();
  }
  if (q.type === 'short') return typeof q.answer === 'string' && normalizeAnswer(q.answer.split('|')[0]) !== '';
  return false;
}

/** Is the built-in adaptive question gate active? */
export function isGateReady(gate) {
  return !!(gate && gate.enabled && Array.isArray(gate.categories) && gate.categories.length > 0);
}

/** Unlock attempts so far today = blocked page views today, not counting the one being shown now. */
export function attemptsBefore(stats, date = new Date()) {
  const today = (stats && stats.days && stats.days[dayKey(date)]) || { blocked: 0 };
  return Math.max(0, today.blocked - 1);
}

export function isQuizReady(quiz) {
  return !!(quiz && quiz.enabled && Array.isArray(quiz.questions) && quiz.questions.length === 3 && quiz.questions.every(validateQuestion));
}

/** Check one answer. MC: answer is a choice index; short: alternatives separated by '|'. */
export function checkAnswer(question, given) {
  if (!validateQuestion(question)) return false;
  if (question.type === 'mc') return Number(given) === question.answer && given !== '' && given !== null && given !== undefined;
  const g = normalizeAnswer(given);
  if (!g) return false;
  return question.answer.split('|').map(normalizeAnswer).filter(Boolean).includes(g);
}

export function checkQuiz(questions, answers) {
  const correct = questions.map((q, i) => checkAnswer(q, answers[i]));
  return { correct, allCorrect: correct.length > 0 && correct.every(Boolean) };
}

// ---------- settings / state ----------

export function defaultSettings() {
  return {
    enabled: true,
    blocklist: [],
    packs: { social: false, video: false, news: false, shopping: false, games: false, adult: false },
    schedules: [],
    delaySec: 5,
    quiz: { enabled: false, topic: '', questions: [0, 1, 2].map(() => ({ type: 'short', q: '', choices: ['', '', '', ''], answer: '' })) },
    gate: { enabled: true, categories: ['math', 'logic', 'wellbeing'], needed: 2 },
    passesPerDay: 3,
    strict: false,
    focusUntil: 0,
  };
}

export function sanitizeSettings(raw) {
  const d = defaultSettings();
  const r = raw && typeof raw === 'object' ? raw : {};
  const out = { ...d };
  out.enabled = r.enabled !== false;
  out.blocklist = [...new Set((Array.isArray(r.blocklist) ? r.blocklist : []).map(normalizeDomain).filter(Boolean))];
  out.packs = { ...d.packs };
  for (const k of Object.keys(d.packs)) out.packs[k] = !!(r.packs && r.packs[k]);
  out.schedules = (Array.isArray(r.schedules) ? r.schedules : [])
    .map((s, i) => ({
      id: String(s && s.id || 's' + i + '_' + Math.random().toString(36).slice(2, 8)),
      name: String(s && s.name || '').trim().slice(0, 60),
      days: Array.isArray(s && s.days) ? [...new Set(s.days.filter((x) => Number.isInteger(x) && x >= 0 && x <= 6))].sort() : [],
      start: String(s && s.start || ''),
      end: String(s && s.end || ''),
      enabled: !(s && s.enabled === false),
    }))
    .filter((s) => validateSchedule(s).length === 0)
    .slice(0, 50);
  out.delaySec = DELAY_CHOICES.includes(Number(r.delaySec)) ? Number(r.delaySec) : d.delaySec;
  out.passesPerDay = Number.isInteger(r.passesPerDay) && r.passesPerDay >= 0 && r.passesPerDay <= 20 ? r.passesPerDay : d.passesPerDay;
  out.strict = !!r.strict;
  out.focusUntil = Number.isFinite(r.focusUntil) ? r.focusUntil : 0;
  const g = r.gate && typeof r.gate === 'object' ? r.gate : {};
  out.gate = {
    enabled: g.gate === undefined && g.enabled === undefined ? d.gate.enabled : !!g.enabled,
    categories: Array.isArray(g.categories) ? [...new Set(g.categories.filter((c) => GATE_CATEGORY_IDS.includes(c)))] : [...d.gate.categories],
    needed: Number.isInteger(g.needed) && g.needed >= 1 && g.needed <= 5 ? g.needed : d.gate.needed,
  };
  const q = r.quiz && typeof r.quiz === 'object' ? r.quiz : {};
  out.quiz = {
    enabled: !!q.enabled,
    topic: String(q.topic || '').slice(0, 80),
    questions: [0, 1, 2].map((i) => {
      const x = (Array.isArray(q.questions) && q.questions[i]) || {};
      const type = x.type === 'mc' ? 'mc' : 'short';
      const choices = [0, 1, 2, 3].map((k) => String((Array.isArray(x.choices) ? x.choices[k] : '') ?? '').slice(0, 120));
      return {
        type, q: String(x.q || '').slice(0, 200), choices,
        answer: type === 'mc' ? (Number.isInteger(x.answer) ? x.answer : 0) : String(x.answer || '').slice(0, 120),
      };
    }),
  };
  return out;
}

// ---------- passes & stats ----------

export function dayKey(date = new Date()) {
  const p = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())}`;
}

export function passesLeft(settings, passes, date = new Date()) {
  if (settings.strict) return 0;
  const used = passes && passes.date === dayKey(date) ? passes.used : 0;
  return Math.max(0, settings.passesPerDay - used);
}

export function emptyStats() { return { days: {} }; }

export function pruneStats(stats, date = new Date()) {
  const cutoff = new Date(date.getFullYear(), date.getMonth(), date.getDate() - (STATS_DAYS - 1));
  const ck = dayKey(cutoff);
  const days = {};
  for (const [k, v] of Object.entries((stats && stats.days) || {})) if (k >= ck) days[k] = v;
  return { days };
}

export function recordBlock(stats, site, date = new Date()) {
  const s = pruneStats(stats, date);
  const k = dayKey(date);
  const day = s.days[k] || { blocked: 0, sites: {}, gaveIn: 0 };
  day.blocked += 1;
  day.sites[site] = (day.sites[site] || 0) + 1;
  s.days[k] = day;
  return s;
}

export function recordGaveIn(stats, date = new Date()) {
  const s = pruneStats(stats, date);
  const k = dayKey(date);
  const day = s.days[k] || { blocked: 0, sites: {}, gaveIn: 0 };
  day.gaveIn += 1;
  s.days[k] = day;
  return s;
}

export function summarizeStats(stats, date = new Date()) {
  const s = pruneStats(stats, date);
  const today = s.days[dayKey(date)] || { blocked: 0, sites: {}, gaveIn: 0 };
  const sites = {};
  let blocked = 0, gaveIn = 0;
  for (const day of Object.values(s.days)) {
    blocked += day.blocked; gaveIn += day.gaveIn;
    for (const [site, n] of Object.entries(day.sites)) sites[site] = (sites[site] || 0) + n;
  }
  const topSites = Object.entries(sites).sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]));
  const perDay = [];
  for (let i = STATS_DAYS - 1; i >= 0; i--) {
    const d = new Date(date.getFullYear(), date.getMonth(), date.getDate() - i);
    perDay.push({ day: dayKey(d), blocked: (s.days[dayKey(d)] || {}).blocked || 0 });
  }
  return { today, blocked30: blocked, gaveIn30: gaveIn, topSites, perDay };
}

export function originalUrlFromHash(hash) {
  const raw = String(hash || '').replace(/^#/, '');
  if (!/^https?:\/\//i.test(raw)) return null;
  try { return new URL(raw).href; } catch { return null; }
}
