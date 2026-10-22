// Storage helpers shared by background, popup, options and blocked pages. Local only (storage.local).
import { sanitizeSettings, getLockInfo, emptyStats, pruneStats, recordBlock, recordGaveIn, dayKey, defaultSettings } from './logic.js';

export const api = globalThis.browser ?? globalThis.chrome;

export async function getSettings() {
  const { settings } = await api.storage.local.get('settings');
  return settings ? sanitizeSettings(settings) : defaultSettings();
}

export async function getState() {
  const { state } = await api.storage.local.get('state');
  const s = state || {};
  return { passes: s.passes || { date: '', used: 0 }, allow: s.allow || {}, stats: s.stats || emptyStats() };
}

export async function setState(state) {
  const allow = {};
  const now = Date.now();
  for (const [k, v] of Object.entries(state.allow || {})) if (v > now) allow[k] = v;
  await api.storage.local.set({ state: { ...state, allow, stats: pruneStats(state.stats) } });
}

/** Save settings; refuses while strict mode has them locked. */
export async function saveSettings(next, { force = false } = {}) {
  const cur = await getSettings();
  const lock = getLockInfo(cur);
  if (lock.locked && !force) throw new Error(lock.reason);
  const clean = sanitizeSettings(next);
  await api.storage.local.set({ settings: clean });
  return clean;
}

/** Internal writes that are not user edits of the blocklist (e.g. starting a focus session). */
export async function patchSettingsUnlocked(patch) {
  const cur = await getSettings();
  const clean = sanitizeSettings({ ...cur, ...patch });
  await api.storage.local.set({ settings: clean });
  return clean;
}

export async function noteBlocked(site) {
  const st = await getState();
  st.stats = recordBlock(st.stats, site);
  await setState(st);
}

export async function grantAllow(entry, ms, { pass = false } = {}) {
  const st = await getState();
  st.allow[entry] = Date.now() + ms;
  if (pass) {
    const k = dayKey();
    st.passes = st.passes.date === k ? { date: k, used: st.passes.used + 1 } : { date: k, used: 1 };
  }
  st.stats = recordGaveIn(st.stats);
  await setState(st);
}

export function refreshRules() {
  return new Promise((resolve) => {
    try { api.runtime.sendMessage({ type: 'refresh' }, () => { void api.runtime.lastError; resolve(); }); }
    catch { resolve(); }
  });
}

export function $(id) { return document.getElementById(id); }

export function applyTheme() { /* theme follows prefers-color-scheme in CSS */ }
