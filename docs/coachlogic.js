// FocusLens Coach: explainable prediction + planning from YOUR OWN logged data. Pure functions
// (no DOM, no storage, no network) so the same rules can be ported to Kotlin line for line and
// tested in Node. This is a transparent statistical/rules model, not an LLM and not clinical:
// every output carries a plain-language `why`. Nothing leaves the device.
//
// Input `state` is the web app's saved state blob (see storage.js): tasks, checkIns,
// focusSessions, sleep, journal, habits, matrix, sleepTargetHours.

const MIN = 60000;
const HOUR = 3600000;
const MOOD_SCORE = { low: 1, flat: 2, okay: 3, good: 4, great: 5 };

// ---------------------------------------------------------------- Best focus window

/**
 * Where your completed focus minutes cluster. Each session's minutes are spread over the clock
 * hours it covered; the best window is the 2-hour run with the most minutes. Needs 5+ sessions,
 * otherwise a mid-morning default is returned and flagged `isDefault`.
 */
export function focusProfile(state) {
  const sessions = state.focusSessions || [];
  const hours = Array(24).fill(0);
  sessions.forEach((s) => {
    const end = s.at;
    const start = end - s.minutes * MIN;
    for (let t = start; t < end; t += 5 * MIN) hours[new Date(t).getHours()] += 5;
  });
  if (sessions.length < 5) {
    return { isDefault: true, sessions: sessions.length, startHour: 9, endHour: 11, hours, why: "Using a mid-morning default until you've completed 5 focus blocks." };
  }
  let best = 0;
  let bestSum = -1;
  for (let h = 0; h < 23; h++) {
    const sum = hours[h] + hours[h + 1];
    // On a tie prefer the window that starts on the busier hour (so 14:10-15:00 reads 14-16, not 13-15).
    if (sum > bestSum || (sum === bestSum && hours[h] > hours[best])) { bestSum = sum; best = h; }
  }
  return {
    isDefault: false, sessions: sessions.length, startHour: best, endHour: best + 2, hours,
    why: `Most of your focus minutes (${bestSum} of ${hours.reduce((a, b) => a + b, 0)}) happen ${fmtHour(best)}–${fmtHour(best + 2)}.`,
  };
}

function fmtHour(h) {
  const hh = ((h % 24) + 24) % 24;
  return `${String(hh).padStart(2, "0")}:00`;
}

// ---------------------------------------------------------------- Miss-risk for tasks

function bucketOf(ms) {
  const h = new Date(ms).getHours();
  return h < 12 ? "morning" : h < 17 ? "afternoon" : "evening";
}

/** Smoothed historical miss rates: overall, per time-of-day bucket, and the top reason. */
export function missProfile(state) {
  const tasks = new Map((state.tasks || []).map((t) => [t.id, t]));
  const byBucket = { morning: [0, 0], afternoon: [0, 0], evening: [0, 0] }; // [missed, total]
  const reasons = {};
  let missed = 0;
  let total = 0;
  (state.checkIns || []).forEach((c) => {
    const t = tasks.get(c.taskId);
    if (!t) return;
    total++;
    const b = byBucket[bucketOf(t.scheduledAt)];
    b[1]++;
    if (!c.completed) {
      missed++;
      b[0]++;
      if (c.reason) reasons[c.reason] = (reasons[c.reason] || 0) + 1;
    }
  });
  const PRIOR = 0.3;
  const K = 5; // pseudo-observations: stops 1 miss out of 1 task from reading as 100% risk
  const smooth = (m, n, base) => (m + K * base) / (n + K);
  const overall = smooth(missed, total, PRIOR);
  const bucket = {};
  Object.entries(byBucket).forEach(([k, [m, n]]) => { bucket[k] = smooth(m, n, overall); });
  const topReason = Object.entries(reasons).sort((a, b) => b[1] - a[1])[0]?.[0] || null;
  return { total, missed, overall, bucket, topReason };
}

/** Risk 0-100 that an open task is missed, with the reasons behind the number. */
export function taskRisk(task, profile, now = Date.now()) {
  const why = [];
  let p = profile.bucket[bucketOf(task.scheduledAt)];
  const b = bucketOf(task.scheduledAt);
  if (profile.total >= 5) why.push(`You've missed about ${Math.round(profile.bucket[b] * 100)}% of ${b} tasks.`);
  else why.push("Not enough history yet; using a general baseline.");
  const hoursLeft = (task.scheduledAt - now) / HOUR;
  if (hoursLeft < 0) { p = Math.min(0.98, p + 0.25); why.push("It's already overdue."); }
  else if (hoursLeft < 2) { p = Math.min(0.98, p + 0.1); why.push("It's due within 2 hours."); }
  const risk = Math.round(p * 100);
  return { risk, level: risk >= 60 ? "high" : risk >= 35 ? "medium" : "low", why };
}

// ---------------------------------------------------------------- Mood forecast

/** Least-squares slope over daily mood scores; projects the next 3 days. */
export function moodForecast(state, todayKey) {
  const byDate = {};
  (state.journal || []).forEach((e) => { if (e.mood && MOOD_SCORE[e.mood]) byDate[e.date] = MOOD_SCORE[e.mood]; });
  const dates = Object.keys(byDate).sort().slice(-14);
  if (dates.length < 4) return { enough: false, points: dates.length, why: "Log at least 4 journal moods to see a trend." };
  const ys = dates.map((d) => byDate[d]);
  const n = ys.length;
  const xs = ys.map((_, i) => i);
  const mx = xs.reduce((a, b) => a + b, 0) / n;
  const my = ys.reduce((a, b) => a + b, 0) / n;
  const slope = xs.reduce((a, x, i) => a + (x - mx) * (ys[i] - my), 0) / xs.reduce((a, x) => a + (x - mx) * (x - mx), 0);
  const clamp = (v) => Math.max(1, Math.min(5, v));
  const next = [1, 2, 3].map((k) => Math.round(clamp(my + slope * (n - 1 + k - mx)) * 10) / 10);
  const recent = ys.slice(-3).reduce((a, b) => a + b, 0) / Math.min(3, n);
  const direction = slope > 0.1 ? "improving" : slope < -0.1 ? "declining" : "steady";
  return {
    enough: true, points: n, slope: Math.round(slope * 100) / 100, direction, average: Math.round(my * 10) / 10, recent: Math.round(recent * 10) / 10, next,
    why: `Across ${n} logged days your mood is ${direction} (${slope >= 0 ? "+" : ""}${(Math.round(slope * 100) / 100)} per day).`,
  };
}

// ---------------------------------------------------------------- Sleep + load

export function sleepSummary(state, todayKey) {
  const target = (state.sleepTargetHours || 8) * 60;
  const logs = (state.sleep || []).filter((s) => s.date <= todayKey).sort((a, b) => b.date.localeCompare(a.date));
  const last7 = logs.slice(0, 7);
  const debt = last7.reduce((a, s) => a + Math.max(0, target - s.minutes), 0);
  const wakes = last7.map((s) => { const [h, m] = s.wake.split(":").map(Number); return h * 60 + m; }).sort((a, b) => a - b);
  const medianWake = wakes.length ? wakes[Math.floor(wakes.length / 2)] : null;
  return { nights: last7.length, debtMin: debt, lastNight: logs[0] || null, targetMin: target, medianWakeMin: medianWake };
}

/** Bedtime for 5 sleep cycles + 14 min to fall asleep, working back from your usual wake time. */
export function suggestedBedtime(medianWakeMin) {
  if (medianWakeMin == null) return null;
  let t = medianWakeMin - 5 * 90 - 14;
  t = ((t % 1440) + 1440) % 1440;
  return t;
}

/** Flags a heavy week: lots of focus on top of a sleep deficit. */
export function overloadCheck(state, now = Date.now()) {
  const day = (ms) => Math.floor(ms / (24 * HOUR));
  const today = day(now);
  const perDay = Array(14).fill(0);
  (state.focusSessions || []).forEach((s) => {
    const d = today - day(s.at);
    if (d >= 0 && d < 14) perDay[d] += s.minutes;
  });
  const lastWeek = perDay.slice(0, 7).reduce((a, b) => a + b, 0);
  const prevWeek = perDay.slice(7).reduce((a, b) => a + b, 0);
  return { lastWeek, prevWeek, surge: prevWeek >= 60 && lastWeek > prevWeek * 1.5 };
}

// ---------------------------------------------------------------- Day plan

const pad = (n) => String(n).padStart(2, "0");
export const clock = (ms) => `${pad(new Date(ms).getHours())}:${pad(new Date(ms).getMinutes())}`;

/**
 * Builds today's timeline. Work items: open tasks due today or overdue (highest miss-risk first,
 * then earliest due) plus 'Do now' Eisenhower items. They're scheduled as 25-minute focus blocks
 * with 5-minute breaks (15 after every 4th). If your best focus window is still ahead, the first
 * block starts there; otherwise at the next quarter hour. Nothing is placed after 21:00 or inside
 * the wind-down hour before your suggested bedtime. Blocks that don't fit go to `overflow`.
 */
export function planDay(state, now = Date.now(), opts = {}) {
  const focus = focusProfile(state);
  const miss = missProfile(state);
  const sleep = sleepSummary(state, dayKeyOf(now));
  const dayStart = new Date(now); dayStart.setHours(0, 0, 0, 0);
  const at = (h, m = 0) => dayStart.getTime() + h * HOUR + m * MIN;
  const endOfDay = dayStart.getTime() + 24 * HOUR;

  const items = [];
  // A task is still "open" only if you haven't checked it off or marked it missed. Missed tasks
  // keep completedAt empty but do have a check-in, so they must not be re-planned forever.
  const resolved = new Set((state.checkIns || []).map((c) => c.taskId));
  (state.tasks || []).filter((t) => !t.completedAt && !resolved.has(t.id) && t.scheduledAt < endOfDay).forEach((t) => {
    const r = taskRisk(t, miss, now);
    items.push({ kind: "task", id: t.id, title: t.title, due: t.scheduledAt, risk: r.risk, why: r.why[0] });
  });
  (state.matrix || []).filter((m) => m.urgent && m.important).forEach((m) => {
    items.push({ kind: "matrix", id: `m${m.id}`, title: m.text, due: endOfDay, risk: 50, why: "You marked it urgent and important." });
  });
  items.sort((a, b) => b.risk - a.risk || a.due - b.due);

  const bedtimeMin = suggestedBedtime(sleep.medianWakeMin);
  const windDown = bedtimeMin != null && bedtimeMin >= 17 * 60 ? dayStart.getTime() + (bedtimeMin - 60) * MIN : at(21);
  const hardStop = Math.min(at(21), windDown);

  const quarter = 15 * MIN;
  let cursor = Math.ceil(now / quarter) * quarter;
  const bestStart = at(focus.startHour);
  const bestEnd = at(focus.endHour);
  if (bestStart > cursor && bestStart < hardStop) cursor = bestStart;
  if (cursor < at(7)) cursor = at(7);

  const blocks = [];
  const overflow = [];
  let done = 0;
  items.forEach((it) => {
    const end = cursor + 25 * MIN;
    if (end > hardStop) { overflow.push(it); return; }
    const inWindow = cursor >= bestStart && end <= bestEnd;
    blocks.push({
      kind: "focus", start: cursor, end, title: it.title, taskId: it.kind === "task" ? it.id : null,
      why: `${it.why}${inWindow ? " Placed in your best focus window." : ""}`,
    });
    done++;
    cursor = end + (done % 4 === 0 ? 15 : 5) * MIN;
    blocks.push({ kind: "break", start: end, end: cursor, title: done % 4 === 0 ? "Longer break" : "Short break", why: "Rest between focus blocks." });
  });
  if (blocks.length && blocks[blocks.length - 1].kind === "break") blocks.pop();

  const extras = [];
  const water = opts.water || null;
  if (water && water.ml < water.target * 0.5 && now < at(15)) extras.push({ kind: "habit", start: Math.max(now, at(11)), end: Math.max(now, at(11)) + 5 * MIN, title: "Drink water", why: `You're at ${water.ml} of ${water.target} ml.` });
  let habitCursor = Math.max(now, at(18));
  (state.habits || []).forEach((h) => {
    if (!(h.days || {})[dayKeyOf(now)] && extras.length < 3) {
      extras.push({ kind: "habit", start: habitCursor, end: habitCursor + 10 * MIN, title: h.title, why: "Not ticked off yet today." });
      habitCursor += 10 * MIN; // stagger so two habits never share a time slot
    }
  });
  if (bedtimeMin != null) {
    const bedMs = dayStart.getTime() + bedtimeMin * MIN;
    if (bedtimeMin >= 17 * 60) {
      extras.push({ kind: "wind-down", start: bedMs - 60 * MIN, end: bedMs, title: "Wind down (screens off, dim lights)", why: `Bed by ${clock(bedMs)} gives 5 full sleep cycles for your usual wake time.` });
    }
  }
  const all = [...blocks, ...extras].sort((a, b) => a.start - b.start);
  return { focus, miss, sleep, blocks: all, overflow, bedtimeMin };
}

export function dayKeyOf(ms) {
  const d = new Date(ms);
  return `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}`;
}

// ---------------------------------------------------------------- Insights

/** Plain-language findings, strongest first. Each has a headline and the evidence for it. */
export function insights(state, now = Date.now()) {
  const out = [];
  const today = dayKeyOf(now);
  const focus = focusProfile(state);
  if (!focus.isDefault) out.push({ tone: "good", head: `Your best focus window is ${fmtHour(focus.startHour)}–${fmtHour(focus.endHour)}`, why: focus.why });
  const mood = moodForecast(state, today);
  if (mood.enough && mood.direction === "declining" && mood.recent < 3) {
    out.push({ tone: "care", head: "Your mood has been trending down", why: `${mood.why} Forecast for the next 3 days: ${mood.next.join(", ")} out of 5. Consider the calm-down exercises or talking to someone you trust.` });
  } else if (mood.enough && mood.direction === "improving") {
    out.push({ tone: "good", head: "Your mood is trending up", why: mood.why });
  }
  const sleep = sleepSummary(state, today);
  if (sleep.nights >= 3 && sleep.debtMin >= 180) {
    out.push({ tone: "warn", head: `Sleep debt is ${Math.floor(sleep.debtMin / 60)}h ${sleep.debtMin % 60}m this week`, why: "That much debt usually shows up as slips in attention. Protect an earlier bedtime before adding more tasks." });
  }
  const load = overloadCheck(state, now);
  if (load.surge) {
    out.push({ tone: "warn", head: "Focus time jumped sharply this week", why: `${load.lastWeek} min vs ${load.prevWeek} min the week before. Plan a lighter day so it doesn't tip into burnout.` });
  }
  const miss = missProfile(state);
  if (miss.total >= 5) {
    const worst = Object.entries(miss.bucket).sort((a, b) => b[1] - a[1])[0];
    if (worst[1] > miss.overall + 0.1) out.push({ tone: "warn", head: `${worst[0][0].toUpperCase() + worst[0].slice(1)} tasks slip most often`, why: `About ${Math.round(worst[1] * 100)}% missed vs ${Math.round(miss.overall * 100)}% overall. Move important tasks to another part of the day.` });
    if (miss.topReason) out.push({ tone: "info", head: `Top reason for missed tasks: ${miss.topReason.toLowerCase().replace("_", " ")}`, why: `From ${miss.missed} missed check-ins.` });
  }
  if (!out.length) out.push({ tone: "info", head: "Not enough data yet", why: "Complete a few focus blocks, log sleep and journal moods, and check tasks off or mark them missed. The coach learns from what you log." });
  return out;
}
