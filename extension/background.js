// Service worker: keeps declarativeNetRequest dynamic rules in sync with settings, schedules, focus mode and passes.
import { getSettings, getState, setState, api } from './store.js';
import { effectiveDomains, buildRules, shouldBlockNow } from './logic.js';

let chain = Promise.resolve();
export function refresh() {
  chain = chain.then(doRefresh).catch((e) => console.warn('FocusLens refresh failed', e));
  return chain;
}

async function doRefresh() {
  const settings = await getSettings();
  const state = await getState();
  const now = Date.now();
  // drop expired focus session / allow windows
  if (settings.focusUntil && settings.focusUntil <= now) {
    settings.focusUntil = 0;
    await api.storage.local.set({ settings });
  }
  const live = Object.values(state.allow).some((v) => v > now);
  if (Object.keys(state.allow).length && !live) { state.allow = {}; await setState(state); }

  const active = shouldBlockNow(settings, new Date(now));
  const rules = active ? buildRules(effectiveDomains(settings), api.runtime.getURL('blocked.html'), state.allow, now) : [];
  const existing = await api.declarativeNetRequest.getDynamicRules();
  await api.declarativeNetRequest.updateDynamicRules({
    removeRuleIds: existing.map((r) => r.id),
    addRules: rules,
  });
  try {
    await api.action.setBadgeText({ text: active ? 'ON' : '' });
    await api.action.setBadgeBackgroundColor({ color: '#c2410c' });
  } catch { /* badge is optional */ }
}

function ensureAlarm() {
  api.alarms.create('focuslens-tick', { periodInMinutes: 1 });
}

api.runtime.onInstalled.addListener(() => { ensureAlarm(); refresh(); });
api.runtime.onStartup.addListener(() => { ensureAlarm(); refresh(); });
api.alarms.onAlarm.addListener((a) => { if (a.name === 'focuslens-tick') refresh(); });
api.storage.onChanged.addListener((changes, area) => {
  if (area === 'local' && (changes.settings || changes.state)) refresh();
});
api.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
  if (msg && msg.type === 'refresh') { refresh().then(() => sendResponse({ ok: true })); return true; }
  return false;
});

ensureAlarm();
refresh();
