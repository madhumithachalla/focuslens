// Coach page: explainable predictions and a planned day, computed on this device from your own
// logged data (see coachlogic.js). Not AI, not medical advice.
import * as store from "./storage.js";
import { insights, planDay, taskRisk, missProfile, moodForecast, clock, dayKeyOf } from "./coachlogic.js";
import { googleCalendarUrl } from "./calendar.js";

function el(html) {
  const t = document.createElement("template");
  t.innerHTML = html.trim();
  return t.content.firstElementChild;
}
function esc(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

const TONE = { good: "✅", warn: "⚠️", care: "💚", info: "ℹ️" };
const KIND_LABEL = { focus: "Focus", break: "Break", habit: "Habit", "wind-down": "Wind down" };

export function renderCoach(container) {
  const now = Date.now();
  const state = store.getStateSnapshot();
  const water = store.waterToday();

  container.appendChild(el(`
    <div>
      <h1>Coach</h1>
      <p class="subtitle">Looks at what you've logged, spots patterns, and lays out today. Every suggestion shows the reason behind it, so you can disagree.</p>
    </div>`));

  // Insights
  const ins = insights(state, now);
  const insCard = el(`<div class="card"><div class="bold">What I notice</div></div>`);
  ins.forEach((i) => insCard.appendChild(el(`<div class="task-item"><div class="bold">${TONE[i.tone] || ""} ${esc(i.head)}</div><div class="small muted">${esc(i.why)}</div></div>`)));
  container.appendChild(insCard);

  // Today's plan
  const plan = planDay(state, now, { water });
  const planCard = el(`<div class="card"><div class="bold">Your plan for today</div></div>`);
  if (!plan.blocks.length) {
    planCard.appendChild(el(`<p class="muted">Nothing due today. Add tasks on the To-do page and they'll be scheduled here, in your best focus time.</p>`));
  }
  plan.blocks.forEach((b) => {
    const row = el(`
      <div class="task-item">
        <div class="row mb-0"><span class="bold">${clock(b.start)}–${clock(b.end)} · ${esc(b.title)}</span><span class="badge">${KIND_LABEL[b.kind]}</span></div>
        <div class="small muted">${esc(b.why)}</div>
      </div>`);
    if (b.kind === "focus") {
      const actions = el(`<div class="chips mt-8" style="margin-bottom:0"></div>`);
      const start = el(`<a class="chip" href="#focus">Start in Focus</a>`);
      const cal = el(`<a class="chip" target="_blank" rel="noopener noreferrer">Add to calendar</a>`);
      cal.href = googleCalendarUrl(b.title, b.start, b.end, "Planned by FocusLens Coach");
      actions.append(start, cal);
      row.appendChild(actions);
    }
    planCard.appendChild(row);
  });
  if (plan.overflow.length) {
    planCard.appendChild(el(`<p class="small muted" style="margin-bottom:0">Won't fit today without cutting into sleep: ${plan.overflow.map((o) => esc(o.title)).join(", ")}. Consider moving or shrinking them.</p>`));
  }
  container.appendChild(planCard);

  // Task risk
  const miss = missProfile(state);
  const resolved = new Set((state.checkIns || []).map((c) => c.taskId));
  const open = (state.tasks || []).filter((t) => !t.completedAt && !resolved.has(t.id)).sort((a, b) => a.scheduledAt - b.scheduledAt).slice(0, 8);
  const riskCard = el(`<div class="card"><div class="bold">Which tasks might slip</div><p class="small muted">Based on how often you've missed tasks at that time of day, and how close the deadline is.</p></div>`);
  if (!open.length) riskCard.appendChild(el(`<p class="muted">No open tasks.</p>`));
  open.forEach((t) => {
    const r = taskRisk(t, miss, now);
    riskCard.appendChild(el(`<div class="task-item"><div class="row mb-0"><span>${esc(t.title)}</span><span class="badge">${r.level} · ${r.risk}%</span></div><div class="small muted">${esc(r.why.join(" "))}</div></div>`));
  });
  container.appendChild(riskCard);

  // Mood forecast
  const mf = moodForecast(state, dayKeyOf(now));
  container.appendChild(el(`
    <div class="card">
      <div class="bold">Mood outlook</div>
      <p class="small" style="margin:6px 0 0">${mf.enough ? `${esc(mf.why)} If the trend continues, the next 3 days look like <span class="bold">${mf.next.join(", ")}</span> out of 5.` : esc(mf.why)}</p>
    </div>`));

  container.appendChild(el(`<p class="small muted">How it works: simple statistics on your own logs (best focus window from your focus sessions, miss rates from your check-ins, a straight-line mood trend). It runs on this device and sends nothing anywhere. It's a transparent rules model, not AI and not medical advice. It gets better the more you log.</p>`));
}
