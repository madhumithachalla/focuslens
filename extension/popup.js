import { api, $, getSettings, getState, saveSettings, patchSettingsUnlocked, refreshRules } from './store.js';
import { getLockInfo, normalizeDomain, matchEntry, effectiveDomains, summarizeStats, isFocusActive, shouldBlockNow } from './logic.js';

let tabHost = null;

async function render() {
  const s = await getSettings();
  const st = await getState();
  const lock = getLockInfo(s);
  $('lock').hidden = !lock.locked;
  $('lock').textContent = lock.reason;
  $('enabled').checked = s.enabled;
  $('enabled').disabled = lock.locked;
  const on = shouldBlockNow(s);
  $('state').textContent = !s.enabled ? 'Paused' : on ? 'Active now' : 'Outside schedule';

  const covered = tabHost && matchEntry(tabHost, effectiveDomains(s));
  if (!tabHost) { $('site').textContent = 'This tab cannot be blocked (not a regular website).'; $('add').disabled = true; }
  else if (covered) { $('site').textContent = `${tabHost} is already blocked (${covered}).`; $('add').disabled = true; }
  else { $('site').textContent = tabHost; $('add').disabled = lock.locked; }

  const fa = isFocusActive(s);
  $('focusinfo').textContent = fa ? `Focus mode until ${new Date(s.focusUntil).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}.` : 'Block everything in your list right now, ignoring schedules.';
  document.querySelectorAll('#focusbtns button[data-min]').forEach((b) => { b.disabled = fa; });
  $('stopfocus').hidden = !fa;
  $('stopfocus').disabled = lock.locked;

  const sum = summarizeStats(st.stats);
  $('today').textContent = sum.today.blocked;
  $('gave').textContent = sum.today.gaveIn;
  const top = Object.entries(sum.today.sites).sort((a, b) => b[1] - a[1]).slice(0, 3);
  $('top').textContent = top.length ? 'Most blocked today: ' + top.map(([d, n]) => `${d} (${n})`).join(', ') : 'No blocked attempts today.';
}

function flash(msg, err) { const m = $('addmsg'); m.textContent = msg; m.className = 'msg' + (err ? ' err' : ''); }

$('enabled').addEventListener('change', async (e) => {
  try { const s = await getSettings(); await saveSettings({ ...s, enabled: e.target.checked }); } catch (err) { flash(err.message, true); }
  render();
});
$('add').addEventListener('click', async () => {
  try {
    const s = await getSettings();
    await saveSettings({ ...s, blocklist: [...s.blocklist, tabHost] });
    flash(`Blocked ${tabHost}.`);
  } catch (err) { flash(err.message, true); }
  render();
});
document.querySelectorAll('#focusbtns button[data-min]').forEach((b) => b.addEventListener('click', async () => {
  await patchSettingsUnlocked({ focusUntil: Date.now() + Number(b.dataset.min) * 60000, enabled: true });
  render();
}));
$('stopfocus').addEventListener('click', async () => {
  try { const s = await getSettings(); await saveSettings({ ...s, focusUntil: 0 }); } catch (err) { flash(err.message, true); }
  render();
});
$('opts').addEventListener('click', () => { api.runtime.openOptionsPage(); window.close(); });

(async () => {
  try {
    const [tab] = await api.tabs.query({ active: true, currentWindow: true });
    if (tab && tab.url && /^https?:/i.test(tab.url)) tabHost = normalizeDomain(tab.url);
  } catch { /* no access */ }
  render();
  api.storage.onChanged.addListener(render);
})();
