// Intercept page: practise the question gate, tune it, write your own questions, see real stats.
// runGate() is also used by the Focus timer's "Block distractions" mode.
import { CATEGORIES, QuizSession, MAX_LEVEL, baseLevelForAttempts } from "./quiz-engine.js";
import * as is from "./intercept-store.js";

function el(html) {
  const t = document.createElement("template");
  t.innerHTML = html.trim();
  return t.content.firstElementChild;
}
function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

/**
 * Mounts a gate into `host`. opts: { needed, delaySeconds, categories, attemptsToday, customQuestions,
 * practice (bool; results are tagged), heading, exitLabel, onFinish({passed, answered, correct, level}) }.
 * Returns { destroy }.
 */
export function runGate(host, opts = {}) {
  const session = new QuizSession({
    categories: opts.categories, attemptsToday: opts.harder === false ? 0 : opts.attemptsToday || 0,
    needed: opts.needed || 3, customQuestions: opts.customQuestions || [],
  });
  if (opts.floor > 1) { session.base = Math.max(session.base, opts.floor); session.level = Math.max(session.level, opts.floor); }
  let timerId = null, finished = false, unlockId = null;
  const finish = (passed) => {
    if (finished) return;
    finished = true;
    clearInterval(timerId);
    if (opts.onFinish) opts.onFinish({ passed, answered: session.asked, correct: session.correct, level: session.level });
  };
  const exitBtn = () => `<button type="button" class="outline" data-exit style="color:var(--danger);border-color:var(--danger)">${escapeHtml(opts.exitLabel || "TAKE ME OUT OF HERE")}</button>`;
  const wireExit = (root) => { const b = root.querySelector("[data-exit]"); if (b) b.onclick = () => { finish(false); renderEnd(false); }; };

  function renderEnd(passed) {
    host.innerHTML = "";
    if (passed) {
      let left = 600;
      const c = el(`<div class="card soft center" data-state="unlocked"><div class="bold" style="font-size:20px">Unlocked for 10 minutes</div><div id="gate-left" class="small muted"></div><p class="small muted" style="margin:6px 0 0">${session.correct} correct out of ${session.asked}.</p></div>`);
      const upd = () => { const m = Math.floor(left / 60), s = left % 60; c.querySelector("#gate-left").textContent = `${m}:${String(s).padStart(2, "0")} left`; };
      upd(); host.appendChild(c);
      unlockId = setInterval(() => { left--; if (left <= 0) { clearInterval(unlockId); c.querySelector("#gate-left").textContent = "Locked again"; } else upd(); }, 1000);
    } else {
      host.appendChild(el(`<div class="card center" data-state="exited"><div class="bold">Gate closed</div><p class="small muted" style="margin:6px 0 0">You walked away. The app stays locked.</p></div>`));
    }
  }

  function renderQuestion() {
    const q = session.next();
    host.innerHTML = "";
    const card = el(`<div class="card" data-state="question">
      <div class="row" style="justify-content:space-between"><span class="small muted" data-progress>Question ${Math.min(session.correct + 1, session.needed)} of ${session.needed} needed · ${session.correct} correct</span>
      <span class="small bold" data-level>Level ${session.level}/${MAX_LEVEL}</span></div>
      <div class="meter" role="meter" aria-valuemin="1" aria-valuemax="${MAX_LEVEL}" aria-valuenow="${session.level}" style="display:flex;gap:4px;margin:8px 0 12px">
        ${Array.from({ length: MAX_LEVEL }, (_, i) => `<span style="flex:1;height:8px;border-radius:4px;background:${i < session.level ? "var(--accent)" : "var(--border)"}"></span>`).join("")}</div>
      <div class="bold" style="margin-bottom:10px">${escapeHtml(q.prompt)}</div>
      <div data-choices style="display:grid;gap:8px"></div>
      <div data-feedback class="small" style="margin-top:10px"></div>
      <div style="margin-top:12px;display:flex;gap:8px;flex-wrap:wrap"><button type="button" class="primary" data-next hidden>Next</button>${exitBtn()}</div>
    </div>`);
    const wrap = card.querySelector("[data-choices]");
    q.choices.forEach((c, i) => {
      const b = el(`<button type="button" class="outline" data-choice="${i}" style="text-align:left">${escapeHtml(c)}</button>`);
      b.onclick = () => {
        const r = session.answer(i);
        is.recordResult(q.category, q.level, r.right, !!opts.practice);
        wrap.querySelectorAll("button").forEach((x, j) => {
          x.disabled = true;
          if (j === r.correctIndex) x.style.cssText += ";background:var(--primary-soft);border-color:var(--accent)";
          else if (j === i) x.style.cssText += ";border-color:var(--danger)";
        });
        card.querySelector("[data-feedback]").innerHTML = `<b style="color:${r.right ? "var(--accent)" : "var(--danger)"}">${r.right ? "Correct." : "Not quite."}</b> ${escapeHtml(r.explain)}`;
        const nb = card.querySelector("[data-next]");
        nb.hidden = false;
        nb.textContent = r.done ? "Unlock" : "Next question";
        nb.onclick = () => { if (r.done) { finish(true); renderEnd(true); } else renderQuestion(); };
      };
      wrap.appendChild(b);
    });
    wireExit(card);
    host.appendChild(card);
  }

  function renderDelay() {
    let left = Math.max(0, opts.delaySeconds || 0);
    if (!left) return renderQuestion();
    host.innerHTML = "";
    const c = el(`<div class="card center" data-state="delay"><div class="small muted">${escapeHtml(opts.heading || "Take a breath before you open it")}</div><div id="gate-count" style="font-size:48px;font-weight:700">${left}</div><div style="margin-top:8px">${exitBtn()}</div></div>`);
    wireExit(c); host.appendChild(c);
    timerId = setInterval(() => {
      left--;
      if (left <= 0) { clearInterval(timerId); renderQuestion(); } else c.querySelector("#gate-count").textContent = left;
    }, 1000);
  }

  renderDelay();
  return { destroy() { clearInterval(timerId); clearInterval(unlockId); finished = true; host.innerHTML = ""; } };
}

// ---------------------------------------------------------------- Page

export function renderIntercept(container, rerender) {
  const prefs = is.interceptPrefs();
  const today = is.attemptsToday();
  const base = baseLevelForAttempts(today);

  container.appendChild(el(`<div class="card soft"><div class="bold">Get the browser extension</div><p class="small" style="margin:6px 0 8px">The real blocker for websites. Unzip, then Load unpacked in chrome://extensions (Developer mode). Steps are in its README.</p><a class="chip" href="focuslens-blocker-extension.zip" download>Download extension (.zip)</a></div>`));
  container.appendChild(el(`<div><h1>Intercept</h1>
    <p class="subtitle">Before a blocked app or site opens, you answer a few questions. They get harder the more often you reach for it today, so a craving has to survive some effort.</p></div>`));

  container.appendChild(el(`<div class="card soft">
    <div class="bold">${today} real attempt${today === 1 ? "" : "s"} today · starting level ${base}/${MAX_LEVEL}</div>
    <div class="small muted">Every two attempts raise the starting level by one. Within a run, two right answers in a row go up a level and a miss goes down one.</div>
    <p class="small muted" style="margin:8px 0 0">A website can't block other sites or apps. The real gate runs in the FocusLens Android app and the browser extension. This page is where you practise, tune it and see your stats.</p>
  </div>`));

  // Practice gate
  const gateCard = el(`<div class="card"><div class="bold">Try the intercept</div>
    <p class="small muted">${prefs.needed} correct answers needed, ${prefs.delaySeconds} s delay first.</p>
    <label class="small" style="display:block;margin-bottom:8px"><input type="checkbox" id="count-attempt"> Count this as an attempt (raises today's level)</label>
    <button type="button" class="primary" id="gate-start">Start practice gate</button>
    <div id="gate-host" style="margin-top:12px"></div></div>`);
  const gateHost = gateCard.querySelector("#gate-host");
  gateCard.querySelector("#gate-start").onclick = () => {
    const counted = gateCard.querySelector("#count-attempt").checked;
    gateCard.querySelector("#gate-start").hidden = true;
    gateCard.querySelector("#count-attempt").disabled = true;
    runGate(gateHost, {
      categories: prefs.categories, needed: prefs.needed, delaySeconds: prefs.delaySeconds,
      attemptsToday: today, floor: prefs.floor, harder: prefs.harder, customQuestions: is.listCustom(), practice: !counted,
      onFinish: (r) => {
        if (counted) is.recordAttempt(r.passed, r.level, r.answered);
        const again = el(`<button type="button" class="text" id="gate-again">${counted ? "Done (attempt counted). Refresh stats" : "Done (practice, not counted). Refresh stats"}</button>`);
        again.onclick = () => rerender();
        gateCard.appendChild(again);
      },
    });
  };
  container.appendChild(gateCard);

  // Categories
  const catCard = el(`<div class="card"><div class="bold">Question types</div><p class="small muted">Tap to enable or disable. At least one stays on.</p><div class="chips" id="cat-chips"></div></div>`);
  const chips = catCard.querySelector("#cat-chips");
  CATEGORIES.forEach((c) => {
    const on = prefs.categories.includes(c.id);
    const chip = el(`<button type="button" class="chip${on ? " active" : ""}" aria-pressed="${on}">${c.label}</button>`);
    chip.onclick = () => {
      let next = on ? prefs.categories.filter((x) => x !== c.id) : [...prefs.categories, c.id];
      if (!next.length) return;
      is.setInterceptCategories(next);
      rerender();
    };
    chips.appendChild(chip);
  });
  container.appendChild(catCard);

  // Custom questions
  const custom = is.listCustom();
  const cq = el(`<div class="card"><div class="bold">Write my own questions</div>
    <p class="small muted">Your questions mix into the gate about one time in four.</p>
    <div id="cq-list"></div>
    <div class="field"><input type="text" id="cq-prompt" placeholder="Question"></div>
    ${[0, 1, 2, 3].map((i) => `<div class="field"><input type="text" id="cq-c${i}" placeholder="Choice ${i + 1}"></div>`).join("")}
    <div class="field"><select id="cq-correct">${[1, 2, 3, 4].map((n) => `<option value="${n - 1}">Correct answer: choice ${n}</option>`).join("")}</select></div>
    <div class="field"><input type="text" id="cq-explain" placeholder="Explanation (shown after answering)"></div>
    <div id="cq-err" class="small" style="color:var(--danger)"></div>
    <button type="button" class="primary" id="cq-add">Add question</button></div>`);
  const list = cq.querySelector("#cq-list");
  custom.forEach((q) => {
    const row = el(`<div class="row" style="justify-content:space-between;gap:8px;margin-bottom:6px"><span class="small">${escapeHtml(q.prompt)} <span class="muted">(answer: ${escapeHtml(q.choices[q.answerIndex])})</span></span><button type="button" class="text">Delete</button></div>`);
    row.querySelector("button").onclick = () => { is.deleteCustom(q.id); rerender(); };
    list.appendChild(row);
  });
  cq.querySelector("#cq-add").onclick = () => {
    const v = (id) => cq.querySelector(id).value.trim();
    const choices = [0, 1, 2, 3].map((i) => v(`#cq-c${i}`));
    const err = cq.querySelector("#cq-err");
    if (!v("#cq-prompt") || choices.some((c) => !c)) { err.textContent = "Fill in the question and all four choices."; return; }
    if (new Set(choices.map((c) => c.toLowerCase())).size < 4) { err.textContent = "The four choices must be different."; return; }
    is.addCustom({ prompt: v("#cq-prompt"), choices, answerIndex: Number(cq.querySelector("#cq-correct").value), explain: v("#cq-explain") || "That's the answer you set." });
    rerender();
  };
  container.appendChild(cq);

  // Stats
  const st = is.stats();
  const week = is.attemptsLast7();
  const max = Math.max(1, ...week.map((d) => d.count));
  const catRows = CATEGORIES.filter((c) => st.byCat[c.id]).concat(st.byCat.custom ? [{ id: "custom", label: "Your questions" }] : [])
    .map((c) => { const r = st.byCat[c.id]; return `<div class="small">${c.label}: ${Math.round((r.right / r.answered) * 100)}% (${r.right}/${r.answered})</div>`; }).join("");
  container.appendChild(el(`<div class="card"><div class="bold">Stats</div>
    ${st.answered ? `<div class="small muted">${st.answered} question${st.answered === 1 ? "" : "s"} answered (practice and real) · best level reached ${st.bestLevel || "none yet"}</div><div style="margin:6px 0">${catRows}</div>` : `<p class="small muted">No questions answered yet. Try the practice gate above.</p>`}
    <div class="small bold" style="margin-top:8px">Real attempts, last 7 days</div>
    <div style="display:flex;gap:6px;align-items:flex-end;height:70px" data-bars>${week.map((d) => `<div style="flex:1;text-align:center"><div title="${d.date}: ${d.count}" style="height:${Math.round((d.count / max) * 48)}px;min-height:${d.count ? 3 : 0}px;background:var(--accent);border-radius:4px 4px 0 0"></div><div class="small muted">${d.label}${d.count ? " " + d.count : ""}</div></div>`).join("")}</div>
  </div>`));
}
