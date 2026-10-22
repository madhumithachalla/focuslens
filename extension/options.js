import { CATEGORIES } from './quiz-engine.js';
import { api, $, getSettings, getState, saveSettings, setState } from './store.js';
import { PRESET_PACKS, getLockInfo, normalizeDomain, validateSchedule, validateQuestion, sanitizeSettings, defaultSettings, summarizeStats, emptyStats } from './logic.js';

const DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
let s = defaultSettings();

function say(text, err) { const m = $('msg'); m.textContent = text; m.className = 'msg' + (err ? ' err' : ''); }
async function commit(next) {
  try { s = await saveSettings(next); say('Saved.'); } catch (e) { say(e.message, true); s = await getSettings(); }
  render();
}

function render() {
  const lock = getLockInfo(s);
  $('lock').hidden = !lock.locked; $('lock').textContent = lock.reason;
  for (const el of document.querySelectorAll('main input, main select, main button')) {
    if (el.id === 'export') continue;
    el.disabled = lock.locked;
  }
  // chips
  const chips = $('chips'); chips.textContent = '';
  for (const d of s.blocklist) {
    const li = document.createElement('li'); li.append(d);
    const b = document.createElement('button'); b.type = 'button'; b.textContent = '×'; b.setAttribute('aria-label', `Remove ${d}`); b.disabled = lock.locked;
    b.onclick = () => commit({ ...s, blocklist: s.blocklist.filter((x) => x !== d) });
    li.append(b); chips.append(li);
  }
  if (!s.blocklist.length) { const li = document.createElement('li'); li.textContent = 'No custom domains yet'; chips.append(li); }
  // packs
  const packs = $('packs'); packs.textContent = '';
  for (const [k, p] of Object.entries(PRESET_PACKS)) {
    const l = document.createElement('label'); l.style.marginRight = '1rem';
    const c = document.createElement('input'); c.type = 'checkbox'; c.checked = !!s.packs[k]; c.disabled = lock.locked;
    c.onchange = () => commit({ ...s, packs: { ...s.packs, [k]: c.checked } });
    l.append(c, ` ${p.label} `);
    const small = document.createElement('span'); small.className = 'muted'; small.textContent = `(${p.domains.length})`; small.title = p.domains.join(', ');
    l.append(small); packs.append(l);
  }
  // schedules
  const sl = $('schedlist'); sl.textContent = '';
  for (const sc of s.schedules) {
    const li = document.createElement('li'); li.style.display = 'flex';
    const t = document.createElement('span');
    t.textContent = `${sc.name} ${sc.start}-${sc.end} (${sc.days.map((d) => DAYS[d]).join(', ')})${sc.enabled ? '' : ' - off'}`;
    const tog = document.createElement('button'); tog.type = 'button'; tog.textContent = sc.enabled ? 'Turn off' : 'Turn on'; tog.disabled = lock.locked;
    tog.onclick = () => commit({ ...s, schedules: s.schedules.map((x) => (x.id === sc.id ? { ...x, enabled: !x.enabled } : x)) });
    const del = document.createElement('button'); del.type = 'button'; del.textContent = 'Delete'; del.disabled = lock.locked; del.setAttribute('aria-label', `Delete schedule ${sc.name}`);
    del.onclick = () => commit({ ...s, schedules: s.schedules.filter((x) => x.id !== sc.id) });
    li.append(t, tog, del); sl.append(li);
  }
  if (!s.schedules.length) { const li = document.createElement('li'); li.textContent = 'No schedules: blocking is always on.'; sl.append(li); }
  // friction
  $('delay').value = String(s.delaySec); $('passes').value = s.passesPerDay; $('strict').checked = s.strict;
  // adaptive question gate
  $('genabled').checked = s.gate.enabled; $('gneeded').value = s.gate.needed;
  const gc = $('gcats'); gc.innerHTML = '';
  CATEGORIES.forEach((c) => {
    const l = document.createElement('label'); l.style.display = 'block';
    const cb = document.createElement('input'); cb.type = 'checkbox'; cb.checked = s.gate.categories.includes(c.id);
    cb.onchange = () => {
      const cats = CATEGORIES.map((x, i) => ({ id: x.id, on: gc.querySelectorAll('input')[i].checked })).filter((x) => x.on).map((x) => x.id);
      commit({ ...s, gate: { ...s.gate, categories: cats } });
    };
    l.append(cb, ' ', c.label); gc.appendChild(l);
  });
  // quiz
  $('qenabled').checked = s.quiz.enabled; $('qtopic').value = s.quiz.topic;
  renderQuiz(lock.locked);
  renderStats();
}

function renderQuiz(locked) {
  const box = $('qlist'); box.textContent = '';
  s.quiz.questions.forEach((q, i) => {
    const fs = document.createElement('fieldset'); const lg = document.createElement('legend'); lg.textContent = `Question ${i + 1}`; fs.append(lg);
    fs.innerHTML += `
      <p><label>Type <select data-f="type" data-i="${i}"><option value="short">Short answer</option><option value="mc">Multiple choice</option></select></label></p>
      <p><label>Question <input type="text" data-f="q" data-i="${i}" style="width:100%"></label></p>
      <div data-mc="${i}">${[0, 1, 2, 3].map((k) => `<p><label><input type="radio" name="ans${i}" data-f="ans" data-i="${i}" value="${k}"> Choice ${k + 1} is correct</label> <input type="text" data-f="choice${k}" data-i="${i}" aria-label="Choice ${k + 1} text" style="width:60%"></p>`).join('')}</div>
      <p data-sh="${i}"><label>Correct answer <input type="text" data-f="short" data-i="${i}"></label></p>`;
    box.append(fs);
    const set = (f, v) => { const el = fs.querySelector(`[data-f="${f}"]`); el.value = v; el.disabled = locked; };
    set('type', q.type); set('q', q.q); set('short', q.type === 'short' ? q.answer : '');
    [0, 1, 2, 3].forEach((k) => set('choice' + k, q.choices[k]));
    fs.querySelectorAll('[data-f="ans"]').forEach((r) => { r.checked = q.type === 'mc' && Number(r.value) === q.answer; r.disabled = locked; });
    const sync = () => { const mc = fs.querySelector('[data-f="type"]').value === 'mc'; fs.querySelector(`[data-mc="${i}"]`).hidden = !mc; fs.querySelector(`[data-sh="${i}"]`).hidden = mc; };
    fs.querySelector('[data-f="type"]').addEventListener('change', sync); sync();
  });
}

function readQuiz() {
  const questions = [0, 1, 2].map((i) => {
    const g = (f) => document.querySelector(`#qlist [data-f="${f}"][data-i="${i}"]`);
    const type = g('type').value;
    const checked = document.querySelector(`#qlist input[name="ans${i}"]:checked`);
    return { type, q: g('q').value.trim(), choices: [0, 1, 2, 3].map((k) => g('choice' + k).value.trim()), answer: type === 'mc' ? (checked ? Number(checked.value) : -1) : g('short').value.trim() };
  });
  return { enabled: $('qenabled').checked, topic: $('qtopic').value.trim(), questions };
}

function renderStats() {
  getState().then((st) => {
    const sum = summarizeStats(st.stats);
    $('s-blocked').textContent = sum.blocked30; $('s-gave').textContent = sum.gaveIn30;
    const max = Math.max(1, ...sum.perDay.map((d) => d.blocked));
    const bars = $('bars'); bars.textContent = '';
    for (const d of sum.perDay) { const b = document.createElement('span'); b.style.height = Math.round((d.blocked / max) * 100) + '%'; b.title = `${d.day}: ${d.blocked}`; bars.append(b); }
    const tb = $('sites'); tb.textContent = '';
    if (!sum.topSites.length) { const tr = document.createElement('tr'); tr.innerHTML = '<td colspan="2">Nothing blocked yet.</td>'; tb.append(tr); }
    for (const [site, n] of sum.topSites.slice(0, 15)) { const tr = document.createElement('tr'); const a = document.createElement('td'); a.textContent = site; const c = document.createElement('td'); c.textContent = n; tr.append(a, c); tb.append(tr); }
  });
}

$('addform').addEventListener('submit', (e) => {
  e.preventDefault();
  const d = normalizeDomain($('newdomain').value);
  if (!d) return say('That does not look like a valid domain.', true);
  $('newdomain').value = '';
  if (s.blocklist.includes(d)) return say(`${d} is already on the list.`, true);
  commit({ ...s, blocklist: [...s.blocklist, d] });
});

$('sdays').innerHTML = DAYS.map((n, i) => `<label><input type="checkbox" value="${i}" ${i >= 1 && i <= 5 ? 'checked' : ''}> ${n}</label>`).join('');
$('schedform').addEventListener('submit', (e) => {
  e.preventDefault();
  const sc = { id: 's' + Date.now().toString(36), name: $('sname').value.trim(), start: $('sstart').value, end: $('send').value, enabled: true,
    days: [...document.querySelectorAll('#sdays input:checked')].map((c) => Number(c.value)) };
  const errs = validateSchedule(sc);
  if (errs.length) return say(errs.join(' '), true);
  $('sname').value = '';
  commit({ ...s, schedules: [...s.schedules, sc] });
});

$('delay').onchange = () => commit({ ...s, delaySec: Number($('delay').value) });
$('passes').onchange = () => { const n = Math.max(0, Math.min(20, parseInt($('passes').value, 10) || 0)); commit({ ...s, passesPerDay: n }); };
$('genabled').onchange = () => commit({ ...s, gate: { ...s.gate, enabled: $('genabled').checked } });
$('gneeded').onchange = () => { const n = Math.max(1, Math.min(5, parseInt($('gneeded').value, 10) || 2)); commit({ ...s, gate: { ...s.gate, needed: n } }); };
$('strict').onchange = () => commit({ ...s, strict: $('strict').checked });
$('qsave').onclick = () => {
  const quiz = readQuiz();
  if (quiz.enabled && !quiz.questions.every(validateQuestion)) return say('To enable the quiz, fill in all 3 questions with a correct answer (multiple choice needs at least 2 choices and one marked correct).', true);
  commit({ ...s, quiz });
};

$('export').onclick = () => {
  const blob = new Blob([JSON.stringify({ focuslens_blocker: 1, settings: s }, null, 2)], { type: 'application/json' });
  const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = 'focuslens-blocker-settings.json'; a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
};
$('import').onchange = async (e) => {
  const f = e.target.files[0]; if (!f) return;
  try {
    const j = JSON.parse(await f.text());
    const raw = j && j.settings ? j.settings : j;
    if (!raw || typeof raw !== 'object' || !Array.isArray(raw.blocklist || [])) throw new Error('Not a FocusLens settings file.');
    await commit({ ...sanitizeSettings(raw), focusUntil: 0 });
  } catch (err) { say('Import failed: ' + err.message, true); }
  e.target.value = '';
};
$('reset').onclick = async () => {
  if (!confirm('Reset all settings and stats? This cannot be undone.')) return;
  try { await saveSettings(defaultSettings()); await setState({ passes: { date: '', used: 0 }, allow: {}, stats: emptyStats() }); say('Everything was reset.'); } catch (e) { say(e.message, true); }
  s = await getSettings(); render();
};

(async () => { s = await getSettings(); render(); api.storage.onChanged.addListener(async () => { const n = await getSettings(); if (JSON.stringify(n) !== JSON.stringify(s)) { s = n; render(); } else renderStats(); }); })();
