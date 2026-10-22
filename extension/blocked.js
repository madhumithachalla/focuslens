import { api, $, getSettings, getState, noteBlocked, grantAllow, refreshRules } from './store.js';
import { QuizSession, MAX_LEVEL } from './quiz-engine.js';
import { originalUrlFromHash, matchEntry, effectiveDomains, isQuizReady, isGateReady, attemptsBefore, checkQuiz, passesLeft, EMERGENCY_WINDOW_MS, CONTINUE_WINDOW_MS } from './logic.js';

const original = originalUrlFromHash(location.hash);
let entry = null;
let delayLeft = 0;
let quizOk = true;
let gateOk = true;
let busy = false;

function updateButtons(settings, left) {
  const ready = delayLeft <= 0 && quizOk && gateOk && !!original && !busy;
  $('cont').disabled = !ready;
  $('cont').textContent = delayLeft > 0 ? `Continue anyway (available in ${delayLeft}s)` : 'Continue anyway';
  const canPass = quizOk && gateOk && left > 0 && !settings.strict && !!original && !busy;
  $('pass').disabled = !canPass;
  $('pass').textContent = settings.strict ? 'Emergency pass (off in Strict mode)' : `Emergency pass (${left} left today)`;
  $('passinfo').textContent = settings.strict
    ? 'Strict mode is on, so emergency passes are switched off.'
    : `An emergency pass allows ${entry || 'this site'} for 10 minutes.`;
}

function buildQuiz(quiz) {
  const form = $('quizform');
  $('quiztopic').textContent = quiz.topic ? `Topic: ${quiz.topic}` : '';
  quiz.questions.forEach((q, i) => {
    const fs = document.createElement('fieldset');
    fs.className = 'q';
    const lg = document.createElement('legend');
    lg.textContent = `${i + 1}. ${q.q}`;
    fs.appendChild(lg);
    if (q.type === 'mc') {
      q.choices.forEach((c, k) => {
        if (!c.trim()) return;
        const l = document.createElement('label');
        const r = document.createElement('input');
        r.type = 'radio'; r.name = 'q' + i; r.value = String(k);
        l.append(r, ' ', c);
        fs.appendChild(l);
      });
    } else {
      const t = document.createElement('input');
      t.type = 'text'; t.name = 'q' + i; t.autocomplete = 'off';
      t.setAttribute('aria-label', `Answer to question ${i + 1}`);
      fs.appendChild(t);
    }
    form.appendChild(fs);
  });
  const b = document.createElement('button');
  b.type = 'submit'; b.textContent = 'Check my answers';
  form.appendChild(b);
}

(async () => {
  const settings = await getSettings();
  const state = await getState();
  let host = null;
  try { host = original ? new URL(original).hostname.replace(/^www\./, '') : null; } catch { /* ignore */ }
  entry = host ? matchEntry(host, effectiveDomains(settings)) : null;
  const shown = entry || host;
  if (shown) $('site').textContent = shown;
  if (!original) $('err').textContent = 'No destination was passed to this page, so "Continue" is unavailable.';

  // count each blocked page view once (reloads of the same view are not recounted)
  try {
    if (shown && !sessionStorage.getItem('counted')) { sessionStorage.setItem('counted', '1'); await noteBlocked(shown); }
  } catch { if (shown) await noteBlocked(shown); }

  delayLeft = settings.delaySec;
  const gateOn = isGateReady(settings.gate);
  gateOk = !gateOn;
  if (gateOn) runGate(settings, state, () => { gateOk = true; updateButtons(settings, passesLeft(settings, state.passes)); });
  const quizOn = isQuizReady(settings.quiz);
  quizOk = !quizOn;
  if (quizOn) { $('quizbox').hidden = false; buildQuiz(settings.quiz); }
  const left = passesLeft(settings, state.passes);
  updateButtons(settings, left);
  const timer = setInterval(() => {
    if (delayLeft > 0) delayLeft--;
    updateButtons(settings, left);
    if (delayLeft <= 0) clearInterval(timer);
  }, 1000);

  $('quizform').addEventListener('submit', (e) => {
    e.preventDefault();
    const answers = settings.quiz.questions.map((q, i) => {
      const f = e.target.elements['q' + i];
      if (q.type === 'mc') { const c = e.target.querySelector(`input[name="q${i}"]:checked`); return c ? c.value : ''; }
      return f ? f.value : '';
    });
    const res = checkQuiz(settings.quiz.questions, answers);
    quizOk = res.allCorrect;
    const bad = res.correct.map((c, i) => (c ? null : i + 1)).filter(Boolean);
    $('quizmsg').className = 'msg' + (quizOk ? '' : ' err');
    $('quizmsg').textContent = quizOk ? 'Nice, all correct. You can continue if you still want to.' : `Not quite. Check question${bad.length > 1 ? 's' : ''} ${bad.join(', ')} and try again.`;
    updateButtons(settings, left);
  });

  async function proceed(ms, pass) {
    if (!original || busy) return;
    busy = true; updateButtons(settings, left);
    await grantAllow(entry || host, ms, { pass });
    await refreshRules();
    location.replace(original);
  }
  $('cont').addEventListener('click', () => { if (delayLeft <= 0 && quizOk && gateOk) proceed(CONTINUE_WINDOW_MS, false); });
  $('pass').addEventListener('click', async () => {
    const s = await getSettings(); const st = await getState();
    if (s.strict || passesLeft(s, st.passes) <= 0 || !quizOk || !gateOk) return;
    proceed(EMERGENCY_WINDOW_MS, true);
  });
})();

$('out').addEventListener('click', async () => {
  if (history.length > 1) { history.back(); return; }
  try { await api.tabs.update({ url: 'chrome://newtab/' }); } catch { location.href = 'about:blank'; }
});

// The built-in adaptive question gate. Starting difficulty rises with how many times you have
// already hit a blocked page today (attemptsBefore), so each try is a little harder than the last.
function runGate(settings, state, onDone) {
  $('gatebox').hidden = false;
  const attempts = attemptsBefore(state.stats);
  const session = new QuizSession({ categories: settings.gate.categories, attemptsToday: attempts, needed: settings.gate.needed });
  $('why').textContent = attempts > 0 ? `This is attempt ${attempts + 1} today, so the questions start at level ${session.base}.` : '';

  const paintMeter = () => {
    const m = $('meter'); m.innerHTML = '';
    for (let i = 1; i <= MAX_LEVEL; i++) { const b = document.createElement('i'); if (i <= session.level) b.className = 'on'; m.appendChild(b); }
    m.setAttribute('aria-valuenow', String(session.level));
    m.setAttribute('aria-valuetext', `Level ${session.level} of ${MAX_LEVEL}`);
  };
  const show = () => {
    const q = session.next();
    $('gateprogress').textContent = `${session.correct} of ${session.needed} correct`;
    $('gateq').textContent = q.prompt;
    $('gateexplain').textContent = ''; $('gatenext').hidden = true;
    paintMeter();
    const box = $('gatechoices'); box.innerHTML = '';
    q.choices.forEach((c, i) => {
      const b = document.createElement('button'); b.type = 'button'; b.textContent = c;
      b.onclick = () => {
        const r = session.answer(i);
        box.querySelectorAll('button').forEach((x, k) => { x.disabled = true; if (k === r.correctIndex) x.classList.add('right'); });
        if (!r.right) b.classList.add('wrong');
        $('gateexplain').className = 'msg' + (r.right ? '' : ' err');
        $('gateexplain').textContent = `${r.right ? 'Correct.' : 'Not quite.'} ${r.explain}`;
        $('gateprogress').textContent = `${session.correct} of ${session.needed} correct`;
        paintMeter();
        if (r.done) { $('gatetitle').textContent = 'Gate cleared'; $('meternote').textContent = ''; onDone(); }
        else { $('gatenext').hidden = false; $('gatenext').focus(); }
      };
      box.appendChild(b);
    });
  };
  $('gatenext').onclick = show;
  show();
}
