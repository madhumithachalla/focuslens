// Focus timer, Sleep, Wellness (habits / hydration / meds / pillars / grounding), Screeners and
// Glossary pages. Kept out of app.js so that file stays about routing + the older pages.
// Everything here is real: timer, calculator and trackers run on your own input. Nothing is
// simulated — wearable heart-rate/HRV and app-usage data need the Android app and are not
// faked on the web.
import * as store from "./storage.js";
import { GLOSSARY, SCREENERS, CRISIS_RESOURCES } from "./content.js";

function el(html) {
  const t = document.createElement("template");
  t.innerHTML = html.trim();
  return t.content.firstElementChild;
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

const DAY_INITIALS = ["S", "M", "T", "W", "T", "F", "S"];

// ---------------------------------------------------------------- Focus timer

const FOCUS_MODES = {
  pomo: { label: "Pomodoro 25/5", focus: 25, rest: 5 },
  rule52: { label: "52/17", focus: 52, rest: 17 },
  ultra: { label: "90-minute sprint", focus: 90, rest: 20 },
};

// Module-level so a running timer keeps going when you open another page. Uses an absolute end
// time rather than counting ticks, so a throttled background tab still shows the right time.
const timer = { mode: "pomo", phase: "focus", running: false, endAt: 0, leftMs: 25 * 60000, tickId: null, onDone: null };

function timerBeep() {
  try {
    const ctx = new (window.AudioContext || window.webkitAudioContext)();
    [0, 0.35, 0.7].forEach((t) => {
      const o = ctx.createOscillator();
      const g = ctx.createGain();
      o.frequency.value = 880;
      g.gain.value = 0.2;
      o.connect(g);
      g.connect(ctx.destination);
      o.start(ctx.currentTime + t);
      o.stop(ctx.currentTime + t + 0.25);
    });
  } catch (e) {}
}

function timerPhaseMs() {
  const m = FOCUS_MODES[timer.mode];
  return (timer.phase === "focus" ? m.focus : m.rest) * 60000;
}

function fmtClock(ms) {
  const total = Math.max(0, Math.ceil(ms / 1000));
  return `${String(Math.floor(total / 60)).padStart(2, "0")}:${String(total % 60).padStart(2, "0")}`;
}

function timerTick() {
  const left = timer.endAt - Date.now();
  if (left <= 0) {
    clearInterval(timer.tickId);
    timer.running = false;
    const finished = timer.phase;
    if (finished === "focus") store.logFocusSession(FOCUS_MODES[timer.mode].focus, timer.mode);
    timerBeep();
    try {
      if (window.Notification && Notification.permission === "granted") {
        new Notification(finished === "focus" ? "Focus block done — take a break" : "Break over — ready for the next block");
      }
    } catch (e) {}
    timer.phase = finished === "focus" ? "break" : "focus";
    timer.leftMs = timerPhaseMs();
    if (timer.onDone) timer.onDone();
    return;
  }
  timer.leftMs = left;
  const d = document.getElementById("timer-display");
  if (d) d.textContent = fmtClock(left);
}

function timerStart() {
  if (timer.running) return;
  if (window.Notification && Notification.permission === "default") Notification.requestPermission().catch(() => {});
  timer.running = true;
  timer.endAt = Date.now() + timer.leftMs;
  timer.tickId = setInterval(timerTick, 250);
}

function timerPause() {
  if (!timer.running) return;
  clearInterval(timer.tickId);
  timer.running = false;
  timer.leftMs = Math.max(0, timer.endAt - Date.now());
}

function timerReset(mode) {
  clearInterval(timer.tickId);
  timer.running = false;
  if (mode) timer.mode = mode;
  timer.phase = "focus";
  timer.leftMs = timerPhaseMs();
}

export function renderFocus(container, rerender) {
  timer.onDone = rerender;
  container.appendChild(el(`
    <div>
      <h1>Focus</h1>
      <p class="subtitle">Timed focus blocks with breaks. The timer keeps running if you switch pages; it needs this tab or the installed app to stay open.</p>
    </div>
  `));

  const modeChips = el(`<div class="chips"></div>`);
  Object.entries(FOCUS_MODES).forEach(([id, m]) => {
    const chip = el(`<button type="button" class="chip${id === timer.mode ? " active" : ""}">${m.label}</button>`);
    chip.onclick = () => { timerReset(id); rerender(); };
    modeChips.appendChild(chip);
  });
  container.appendChild(modeChips);

  const card = el(`
    <div class="card center">
      <div class="small muted" style="text-transform:uppercase;letter-spacing:.08em">${timer.phase === "focus" ? "Focus" : "Break"}</div>
      <div id="timer-display" style="font-size:64px;font-weight:700;font-variant-numeric:tabular-nums;margin:6px 0 12px">${fmtClock(timer.leftMs)}</div>
      <button class="primary" id="timer-toggle">${timer.running ? "Pause" : "Start"}</button>
      <button class="text" id="timer-reset">Reset</button>
    </div>
  `);
  card.querySelector("#timer-toggle").onclick = () => {
    if (timer.running) timerPause(); else timerStart();
    rerender();
  };
  card.querySelector("#timer-reset").onclick = () => { timerReset(); rerender(); };
  container.appendChild(card);

  container.appendChild(el(`
    <div class="card soft">
      <div class="bold">${store.focusMinutesToday()} min focused today</div>
      <div class="small muted">${store.focusMinutesLast7()} min over the last 7 days. Only completed focus blocks count, not breaks.</div>
    </div>
  `));
  container.appendChild(el(`<p class="small muted">Blocking distracting apps and sites needs OS-level permissions a website can't get — use the Android app for that.</p>`));
}

// ---------------------------------------------------------------- Sleep

const CHRONOTYPES = {
  lark: { label: "Morning lark", tip: "You tend to get sleepy early. Dim lights and screens from about 20:00, and get bright light soon after waking." },
  bear: { label: "In-between", tip: "Most people. Start dimming lights around 21:30 and aim for a steady wake time every day." },
  owl: { label: "Night owl", tip: "Your body clock runs late. Cut bright light after about 23:00 and get daylight early to shift earlier." },
};

function fmtHm(minutes) {
  const h = Math.floor(minutes / 60);
  const m = Math.round(minutes % 60);
  return `${h}h ${String(m).padStart(2, "0")}m`;
}

function bedtimesFor(wake, latencyMin = 14) {
  const [h, m] = wake.split(":").map(Number);
  const wakeMin = h * 60 + m;
  return [6, 5, 4].map((cycles) => {
    let t = wakeMin - cycles * 90 - latencyMin;
    t = ((t % 1440) + 1440) % 1440;
    const hh = String(Math.floor(t / 60)).padStart(2, "0");
    const mm = String(t % 60).padStart(2, "0");
    return { cycles, hours: cycles * 1.5, time: `${hh}:${mm}` };
  });
}

export function renderSleep(container, rerender) {
  container.appendChild(el(`
    <div>
      <h1>Sleep</h1>
      <p class="subtitle">Plan a bedtime around 90-minute sleep cycles, log your nights, and see how far behind (or not) you are.</p>
    </div>
  `));

  // Bedtime calculator
  const calc = el(`
    <div class="card">
      <div class="bold">Bedtime planner</div>
      <label class="field" for="wake-input">I need to wake at</label>
      <input type="time" id="wake-input" value="07:00" />
      <div id="bedtime-out" class="mt-8"></div>
      <p class="small muted" style="margin-bottom:0">Assumes ~14 minutes to fall asleep and 90-minute cycles. Real cycles vary (about 70-120 min), so use these as a starting point and adjust to how you feel.</p>
    </div>
  `);
  const wakeInput = calc.querySelector("#wake-input");
  const out = calc.querySelector("#bedtime-out");
  const paint = () => {
    if (!wakeInput.value) { out.innerHTML = ""; return; }
    out.innerHTML = bedtimesFor(wakeInput.value).map((b) => `
      <div class="row" style="padding:6px 0">
        <span>${b.cycles} cycles (${b.hours} h)${b.cycles === 5 ? ` <span class="badge">Suggested</span>` : ""}</span>
        <span class="bold" style="font-variant-numeric:tabular-nums">${b.time}</span>
      </div>`).join("");
  };
  wakeInput.oninput = paint;
  paint();
  container.appendChild(calc);

  // Sleep log
  const target = store.getSleepTargetHours();
  const today = store.todayKey();
  const form = el(`
    <div class="card">
      <div class="bold">Log last night</div>
      <label class="field" for="sl-bed">Went to bed</label>
      <input type="time" id="sl-bed" value="23:00" />
      <label class="field" for="sl-wake">Woke up</label>
      <input type="time" id="sl-wake" value="07:00" />
      <label class="field" style="margin-bottom:6px">How rested do you feel?</label>
      <div class="chips" id="sl-quality" style="margin-bottom:0"></div>
      <div class="mt-8"><button class="primary" id="sl-save">Save</button></div>
      <label class="field" for="sl-target">Nightly sleep target (hours)</label>
      <input type="number" id="sl-target" min="4" max="12" step="0.5" value="${target}" />
    </div>
  `);
  let quality = 3;
  const qChips = form.querySelector("#sl-quality");
  ["1 Poor", "2", "3 OK", "4", "5 Great"].forEach((label, i) => {
    const chip = el(`<button type="button" class="chip${i + 1 === quality ? " active" : ""}">${label}</button>`);
    chip.onclick = () => {
      quality = i + 1;
      qChips.querySelectorAll(".chip").forEach((c, j) => c.classList.toggle("active", j === i));
    };
    qChips.appendChild(chip);
  });
  form.querySelector("#sl-save").onclick = () => {
    const bed = form.querySelector("#sl-bed").value;
    const wake = form.querySelector("#sl-wake").value;
    if (!bed || !wake) return;
    store.logSleep(today, bed, wake, quality);
    rerender();
  };
  form.querySelector("#sl-target").onchange = (e) => {
    const v = Number(e.target.value);
    if (v >= 4 && v <= 12) { store.setSleepTargetHours(v); rerender(); }
  };
  container.appendChild(form);

  // Debt + history
  const logs = store.recentSleep(14);
  const debt = store.weeklySleepDebtMinutes();
  const last7 = store.recentSleep(7);
  const avg = last7.length ? last7.reduce((s, l) => s + l.minutes, 0) / last7.length : null;
  container.appendChild(el(`
    <div class="card soft">
      <div class="bold">Sleep debt, last 7 days: ${last7.length ? fmtHm(debt) : "—"}</div>
      <div class="small muted">${avg != null ? `Average ${fmtHm(avg)} over ${last7.length} logged night${last7.length === 1 ? "" : "s"}. ` : "Log a night to see this. "}Debt is minutes short of your ${target} h target; extra sleep on one night doesn't cancel it.</div>
    </div>
  `));

  if (logs.length) {
    const hist = el(`<div class="card"><div class="bold">Recent nights</div></div>`);
    logs.forEach((l) => {
      const row = el(`
        <div class="row task-item">
          <div>
            <div>${l.date.slice(6)}/${l.date.slice(4, 6)} — ${fmtHm(l.minutes)}</div>
            <div class="small muted">${l.bed} → ${l.wake} · rested ${l.quality}/5</div>
          </div>
          <button class="text">Delete</button>
        </div>`);
      row.querySelector("button").onclick = () => { store.deleteSleep(l.date); rerender(); };
      hist.appendChild(row);
    });
    container.appendChild(hist);
  }

  // Chronotype
  const chrono = store.getChronotype();
  const cCard = el(`
    <div class="card">
      <div class="bold">Body-clock type</div>
      <div class="chips mt-8" id="chrono-chips"></div>
      <p class="small muted" id="chrono-tip" style="margin-bottom:0">${CHRONOTYPES[chrono].tip}</p>
    </div>
  `);
  Object.entries(CHRONOTYPES).forEach(([id, c]) => {
    const chip = el(`<button type="button" class="chip${id === chrono ? " active" : ""}">${c.label}</button>`);
    chip.onclick = () => { store.setChronotype(id); rerender(); };
    cCard.querySelector("#chrono-chips").appendChild(chip);
  });
  container.appendChild(cCard);
  container.appendChild(el(`<p class="small muted">Wellness estimates, not medical advice. If you regularly sleep badly or feel unrefreshed, mention it to a doctor. Phone-sensor sleep stages and wearable heart rate need the Android app.</p>`));
}

// ---------------------------------------------------------------- Wellness

function lastNDays(n) {
  const days = [];
  let k = store.todayKey();
  for (let i = 0; i < n; i++) { days.unshift(k); k = store.addDaysToKey(k, -1); }
  return days;
}

function dayInitial(key) {
  return DAY_INITIALS[new Date(Number(key.slice(0, 4)), Number(key.slice(4, 6)) - 1, Number(key.slice(6, 8))).getDay()];
}

export function renderWellness(container, rerender) {
  container.appendChild(el(`
    <div>
      <h1>Wellness</h1>
      <p class="subtitle">Habits, water, medication / supplement checklist, and the areas of life you want to look after.</p>
    </div>
  `));

  // Habits
  const days = lastNDays(7);
  const habitsCard = el(`<div class="card"><div class="bold">Habits</div></div>`);
  const habits = store.listHabits();
  if (!habits.length) habitsCard.appendChild(el(`<p class="muted">No habits yet. Add one below.</p>`));
  habits.forEach((h) => {
    const row = el(`
      <div class="task-item">
        <div class="row" style="margin-bottom:6px">
          <span>${escapeHtml(h.title)} <span class="small muted">· ${store.habitStreak(h)}-day streak</span></span>
          <button class="text">Delete</button>
        </div>
        <div class="chips" style="margin-bottom:0"></div>
      </div>`);
    row.querySelector("button").onclick = () => { store.deleteHabit(h.id); rerender(); };
    days.forEach((d) => {
      const done = !!h.days[d];
      const chip = el(`<button type="button" class="chip${done ? " active" : ""}" aria-label="${escapeHtml(h.title)} ${d}" aria-pressed="${done}">${dayInitial(d)}</button>`);
      chip.onclick = () => { store.toggleHabitDay(h.id, d); rerender(); };
      row.querySelector(".chips").appendChild(chip);
    });
    habitsCard.appendChild(row);
  });
  const addHabit = el(`
    <div class="mt-8">
      <input type="text" id="habit-new" placeholder="e.g. 10 minutes of stretching" />
      <div class="mt-8"><button class="primary" id="habit-add">Add habit</button></div>
    </div>`);
  addHabit.querySelector("#habit-add").onclick = () => {
    const v = addHabit.querySelector("#habit-new").value.trim();
    if (v) { store.addHabit(v); rerender(); }
  };
  habitsCard.appendChild(addHabit);
  container.appendChild(habitsCard);

  // Hydration
  const w = store.waterToday();
  const pct = Math.min(100, Math.round((w.ml / w.target) * 100));
  const water = el(`
    <div class="card">
      <div class="bold">Water</div>
      <div class="row" style="margin:6px 0"><span>${w.ml} / ${w.target} ml</span><span class="small muted">${pct}%</span></div>
      <div style="height:8px;border-radius:4px;background:var(--border);overflow:hidden" role="progressbar" aria-valuenow="${pct}" aria-valuemin="0" aria-valuemax="100"><div style="height:100%;width:${pct}%;background:var(--accent)"></div></div>
      <div class="chips mt-8" style="margin-bottom:0"></div>
      <label class="field" for="water-target">Daily target (ml)</label>
      <input type="number" id="water-target" min="500" max="6000" step="100" value="${w.target}" />
    </div>`);
  [[250, "+250"], [500, "+500"], [-250, "Undo 250"]].forEach(([ml, label]) => {
    const chip = el(`<button type="button" class="chip">${label}</button>`);
    chip.onclick = () => { store.addWater(ml); rerender(); };
    water.querySelector(".chips").appendChild(chip);
  });
  water.querySelector("#water-target").onchange = (e) => {
    const v = Number(e.target.value);
    if (v >= 500 && v <= 6000) { store.setWaterTarget(v); rerender(); }
  };
  container.appendChild(water);

  // Medication / supplements checklist
  const meds = store.listMeds();
  const taken = store.medsTakenToday();
  const medCard = el(`<div class="card"><div class="bold">Medication &amp; supplements</div><p class="small muted">A reminder checklist you write yourself. FocusLens doesn't suggest doses or products — follow your prescriber's or pharmacist's advice.</p></div>`);
  if (!meds.length) medCard.appendChild(el(`<p class="muted">Nothing on your list yet.</p>`));
  meds.forEach((m) => {
    const row = el(`
      <div class="row task-item">
        <label style="display:flex;gap:8px;align-items:center"><input type="checkbox" ${taken[m.id] ? "checked" : ""} /> <span>${escapeHtml(m.name)} <span class="small muted">· ${escapeHtml(m.slot)}</span></span></label>
        <button class="text">Delete</button>
      </div>`);
    row.querySelector("input").onchange = (e) => { store.setMedTaken(m.id, e.target.checked); rerender(); };
    row.querySelector("button").onclick = () => { store.deleteMed(m.id); rerender(); };
    medCard.appendChild(row);
  });
  const addMed = el(`
    <div class="mt-8">
      <input type="text" id="med-name" placeholder="Name" />
      <select id="med-slot" class="mt-8"><option>Morning</option><option>Midday</option><option>Evening</option><option>Bedtime</option></select>
      <div class="mt-8"><button class="primary" id="med-add">Add</button></div>
    </div>`);
  addMed.querySelector("#med-add").onclick = () => {
    const name = addMed.querySelector("#med-name").value.trim();
    if (name) { store.addMed(name, addMed.querySelector("#med-slot").value); rerender(); }
  };
  medCard.appendChild(addMed);
  container.appendChild(medCard);

  // Life pillars
  const pillars = store.listPillars();
  const pCard = el(`<div class="card"><div class="bold">Life pillars</div><p class="small muted">The few areas you want to keep an eye on, each with one line on what looking after it means.</p></div>`);
  pillars.forEach((p) => {
    const row = el(`
      <div class="row task-item">
        <div><div class="bold">${escapeHtml(p.title)}</div><div class="small muted">${escapeHtml(p.goal)}</div></div>
        <button class="text">Delete</button>
      </div>`);
    row.querySelector("button").onclick = () => { store.deletePillar(p.id); rerender(); };
    pCard.appendChild(row);
  });
  const addP = el(`
    <div class="mt-8">
      <input type="text" id="pil-title" placeholder="Pillar, e.g. Health" />
      <input type="text" id="pil-goal" class="mt-8" placeholder="What it means for you" />
      <div class="mt-8"><button class="primary" id="pil-add">Add pillar</button></div>
    </div>`);
  addP.querySelector("#pil-add").onclick = () => {
    const t = addP.querySelector("#pil-title").value.trim();
    if (t) { store.addPillar(t, addP.querySelector("#pil-goal").value.trim()); rerender(); }
  };
  pCard.appendChild(addP);
  container.appendChild(pCard);

  renderGrounding(container);
}

// 5-4-3-2-1 grounding + box breathing. The breathing loop stops itself when its card leaves the page.
function renderGrounding(container) {
  const STEPS = [
    "Name 5 things you can see.",
    "Name 4 things you can feel (feet on the floor, fabric, air on your skin).",
    "Name 3 things you can hear.",
    "Name 2 things you can smell.",
    "Name 1 thing you can taste.",
  ];
  const card = el(`
    <div class="card">
      <div class="bold">Calm down now</div>
      <p class="small muted">For when your mind is racing. Try one, or both.</p>
      <div id="ground-step" class="mt-8"></div>
      <div id="breath" class="center" style="margin-top:14px"></div>
    </div>`);
  const stepBox = card.querySelector("#ground-step");
  let i = -1;
  const drawStep = () => {
    if (i < 0) {
      stepBox.innerHTML = `<button class="primary" id="g-start">5-4-3-2-1 grounding</button>`;
      stepBox.querySelector("#g-start").onclick = () => { i = 0; drawStep(); };
    } else if (i < STEPS.length) {
      stepBox.innerHTML = `<div style="font-size:18px;margin-bottom:8px">${STEPS[i]}</div><button class="primary" id="g-next">${i === STEPS.length - 1 ? "Done" : "Next"}</button>`;
      stepBox.querySelector("#g-next").onclick = () => { i++; drawStep(); };
    } else {
      stepBox.innerHTML = `<div style="margin-bottom:8px">Well done. Notice how your body feels now.</div><button class="text" id="g-again">Again</button>`;
      stepBox.querySelector("#g-again").onclick = () => { i = 0; drawStep(); };
    }
  };
  drawStep();

  const breath = card.querySelector("#breath");
  const PHASES = ["Breathe in", "Hold", "Breathe out", "Hold"];
  let running = null;
  const drawBreathIdle = () => {
    breath.innerHTML = `<button class="primary" id="b-start">Box breathing (4-4-4-4)</button>`;
    breath.querySelector("#b-start").onclick = startBreath;
  };
  function startBreath() {
    let t = 0;
    breath.innerHTML = `<div id="b-label" style="font-size:22px;font-weight:600"></div><div id="b-count" style="font-size:40px;font-variant-numeric:tabular-nums"></div><button class="text" id="b-stop">Stop</button>`;
    const label = breath.querySelector("#b-label");
    const count = breath.querySelector("#b-count");
    const paint = () => {
      label.textContent = PHASES[Math.floor(t / 4) % 4];
      count.textContent = String(4 - (t % 4));
    };
    paint();
    running = setInterval(() => {
      if (!document.body.contains(card)) { clearInterval(running); return; }
      t++;
      paint();
    }, 1000);
    breath.querySelector("#b-stop").onclick = () => { clearInterval(running); drawBreathIdle(); };
  }
  drawBreathIdle();
  container.appendChild(card);
}

// ---------------------------------------------------------------- Screeners

function scoreScreener(type, answers) {
  const s = SCREENERS[type];
  if (type === "ASRS") {
    const flagged = answers.filter((a, i) => a >= s.flagAt[i]).length;
    return { score: flagged, label: flagged >= s.flagThreshold ? "Responses consistent with ADHD — worth discussing with a clinician" : "Below the screening threshold" };
  }
  const score = answers.reduce((a, b) => a + b, 0);
  const band = s.bands.find(([lo, hi]) => score >= lo && score <= hi);
  return { score, label: band ? band[2] : "" };
}

export function renderScreeners(container, rerender) {
  container.appendChild(el(`
    <div>
      <h1>Screeners</h1>
      <p class="subtitle">Standard self-report questionnaires. They show how you've been feeling, not what's wrong. They are not a diagnosis.</p>
    </div>
  `));
  container.appendChild(el(`
    <div class="card soft"><p class="small" style="margin:0">Answers and results stay in this browser (and your FocusLens account if you're signed in). If you're struggling, talking to a doctor or counsellor is a good next step whatever the score.</p></div>
  `));

  Object.entries(SCREENERS).forEach(([type, s]) => {
    const history = store.listScreenerResults(type);
    const last = history[0];
    const card = el(`
      <div class="card row">
        <div>
          <div class="bold">${s.title}</div>
          <div class="small muted">${last ? `Last: ${last.score}/${s.max} — ${escapeHtml(last.band)} (${new Date(last.at).toLocaleDateString()})` : "Not taken yet"}</div>
        </div>
        <button class="primary">${last ? "Retake" : "Start"}</button>
      </div>`);
    card.querySelector("button").onclick = () => {
      page().innerHTML = "";
      renderScreenerRun(page(), type, rerender);
      window.scrollTo(0, 0);
    };
    container.appendChild(card);
  });

  renderCrisisCard(container);
}

function page() { return document.getElementById("page"); }

function renderCrisisCard(container) {
  const card = el(`<div class="card"><div class="bold">If you need to talk to someone now</div></div>`);
  CRISIS_RESOURCES.forEach((r) => card.appendChild(el(`<p class="small" style="margin:6px 0"><span class="bold">${r.region}:</span> ${escapeHtml(r.text)}</p>`)));
  container.appendChild(card);
}

function renderScreenerRun(container, type, rerender) {
  const s = SCREENERS[type];
  const answers = Array(s.questions.length).fill(null);
  container.appendChild(el(`<div><h1>${s.title}</h1><p class="subtitle">${escapeHtml(s.intro)}</p></div>`));
  const form = el(`<div></div>`);
  s.questions.forEach((q, qi) => {
    const card = el(`<div class="card"><div style="margin-bottom:8px">${qi + 1}. ${escapeHtml(q)}</div><div class="chips" style="margin-bottom:0"></div></div>`);
    s.options.forEach((opt, oi) => {
      const chip = el(`<button type="button" class="chip">${opt}</button>`);
      chip.onclick = () => {
        answers[qi] = oi;
        card.querySelectorAll(".chip").forEach((c, j) => c.classList.toggle("active", j === oi));
      };
      card.querySelector(".chips").appendChild(chip);
    });
    form.appendChild(card);
  });
  const msg = el(`<p class="small" style="color:var(--danger)" role="alert" hidden>Please answer every question.</p>`);
  const submit = el(`<button class="primary">See result</button>`);
  const back = el(`<button class="text">Cancel</button>`);
  back.onclick = () => rerender();
  submit.onclick = () => {
    if (answers.some((a) => a == null)) { msg.hidden = false; return; }
    const { score, label } = scoreScreener(type, answers);
    store.saveScreenerResult(type, score, label);
    container.innerHTML = "";
    const showSafety = s.safetyItem != null && answers[s.safetyItem] > 0;
    container.appendChild(el(`
      <div>
        <h1>${s.title}</h1>
        <div class="card soft"><div class="bold" style="font-size:20px">${score} / ${s.max}</div><div>${escapeHtml(label)}</div></div>
        <p class="small muted">This is a screening score from your own answers, not a diagnosis. Bring it to a doctor or counsellor if it worries you or you've felt this way for a while.</p>
      </div>`));
    if (showSafety) {
      container.appendChild(el(`<div class="card" style="border-color:var(--danger)"><p class="bold" style="margin-top:0">You marked thoughts of being better off dead or hurting yourself.</p><p class="small">Thank you for being honest. Please reach out to someone today — you don't have to be sure it's serious to ask for help.</p></div>`));
      renderCrisisCard(container);
    }
    const done = el(`<button class="primary">Back to screeners</button>`);
    done.onclick = () => rerender();
    container.appendChild(done);
    window.scrollTo(0, 0);
  };
  form.appendChild(msg);
  form.appendChild(submit);
  form.appendChild(back);
  container.appendChild(form);
}

// ---------------------------------------------------------------- Glossary

export function renderGlossary(container) {
  container.appendChild(el(`
    <div>
      <h1>Glossary</h1>
      <p class="subtitle">What the terms and numbers in FocusLens mean, with where each idea comes from.</p>
    </div>
  `));
  const search = el(`<input type="text" id="gloss-search" placeholder="Search, e.g. REM, HRV, sleep debt" aria-label="Search glossary" />`);
  container.appendChild(search);
  const list = el(`<div class="mt-8"></div>`);
  container.appendChild(list);
  const paint = () => {
    const q = search.value.trim().toLowerCase();
    list.innerHTML = "";
    const hits = GLOSSARY.filter((g) => !q || `${g.term} ${g.def} ${g.group}`.toLowerCase().includes(q));
    if (!hits.length) list.appendChild(el(`<p class="muted">No matching terms.</p>`));
    hits.forEach((g) => list.appendChild(el(`
      <div class="card">
        <div class="row mb-0"><span class="bold">${escapeHtml(g.term)}</span><span class="badge">${g.group}</span></div>
        <p class="small mt-8" style="margin-bottom:6px">${escapeHtml(g.def)}</p>
        <p class="small muted" style="margin:0">Source: ${escapeHtml(g.cite)}</p>
      </div>`)));
  };
  search.oninput = paint;
  paint();
}
