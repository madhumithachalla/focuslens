// Body & readiness page. Everything here is real: heart rate comes from a Bluetooth LE heart-rate
// sensor you pair yourself (Web Bluetooth), HRV is computed from that sensor's beat intervals,
// and the readiness score is a plain rules model over your own FocusLens data. Nothing is
// simulated — with no sensor connected the page shows no numbers and says what is needed.
import * as store from "./storage.js";

const BODY_KEY = "focuslens_body_v1";
const APP_KEY = "focuslens_web_v1";
const MAX_SAVED = 60;
const TRACE_MS = 120000; // chart + HRV window
const MIN_CLEAN_BEATS = 30;
const MIN_BASELINE_READINGS = 5;

function el(html) {
  const t = document.createElement("template");
  t.innerHTML = html.trim();
  return t.content.firstElementChild;
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

// ---------------------------------------------------------------- parsing + maths (exported for tests)

// Bluetooth Heart Rate Measurement characteristic (0x2A37).
// flags bit0: HR value is uint16 (else uint8); bit1-2: sensor contact; bit3: energy expended
// present (uint16, skipped); bit4: RR intervals present, uint16 LE each in 1/1024 s.
export function parseHeartRateMeasurement(dv) {
  if (!dv || dv.byteLength < 2) return null;
  const flags = dv.getUint8(0);
  let i = 1;
  let bpm;
  if (flags & 0x01) {
    if (dv.byteLength < 3) return null;
    bpm = dv.getUint16(i, true);
    i += 2;
  } else {
    bpm = dv.getUint8(i);
    i += 1;
  }
  if (flags & 0x08) i += 2;
  const rr = [];
  if (flags & 0x10) {
    while (i + 1 < dv.byteLength) {
      rr.push((dv.getUint16(i, true) / 1024) * 1000);
      i += 2;
    }
  }
  const contactSupported = !!(flags & 0x04);
  return { bpm, rr, contact: contactSupported ? !!(flags & 0x02) : null };
}

function median(arr) {
  if (!arr.length) return null;
  const s = arr.slice().sort((a, b) => a - b);
  const m = Math.floor(s.length / 2);
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
}

// rrMs: beat intervals in ms, in order. Rejects intervals outside 300-2000 ms and any more than
// 20% away from the previous in-range interval. RMSSD only uses differences between two
// directly adjacent kept beats, so a dropped beat never creates a fake large difference.
export function computeHrv(rrMs) {
  const clean = [];
  const diffs = [];
  let rejected = 0;
  let prevRaw = null; // previous in-range interval (kept or not)
  let prevKept = false;
  for (const rr of rrMs) {
    if (!(rr >= 300 && rr <= 2000)) {
      rejected++;
      prevRaw = null;
      prevKept = false;
      continue;
    }
    if (prevRaw !== null && Math.abs(rr - prevRaw) / prevRaw > 0.2) {
      rejected++;
      prevRaw = rr;
      prevKept = false;
      continue;
    }
    if (prevKept) diffs.push(rr - clean[clean.length - 1]);
    clean.push(rr);
    prevRaw = rr;
    prevKept = true;
  }
  const n = clean.length;
  const out = { total: rrMs.length, clean: n, rejected, rmssd: null, sdnn: null, avgBpm: null, ready: n >= MIN_CLEAN_BEATS && diffs.length > 1 };
  if (n > 0) {
    const mean = clean.reduce((a, b) => a + b, 0) / n;
    out.avgBpm = 60000 / mean;
    if (out.ready) {
      out.rmssd = Math.sqrt(diffs.reduce((a, d) => a + d * d, 0) / diffs.length);
      out.sdnn = Math.sqrt(clean.reduce((a, r) => a + (r - mean) * (r - mean), 0) / (n - 1));
    }
  }
  return out;
}

function loadReadings() {
  try {
    const v = JSON.parse(localStorage.getItem(BODY_KEY));
    return Array.isArray(v) ? v.filter((r) => r && Number.isFinite(r.rmssd)) : [];
  } catch (e) {
    return [];
  }
}

function saveReadings(list) {
  try { localStorage.setItem(BODY_KEY, JSON.stringify(list.slice(-MAX_SAVED))); } catch (e) {}
}

export function baselineOf(readings) {
  return readings.length ? median(readings.map((r) => r.rmssd)) : null;
}

export function stressBand(ratio) {
  if (ratio >= 0.9) return { label: "Relaxed", note: "At or near your usual resting variability." };
  if (ratio >= 0.7) return { label: "Normal", note: "A little below your usual, within everyday range." };
  if (ratio >= 0.5) return { label: "Elevated", note: "Clearly below your usual. Common after poor sleep, caffeine, illness, hard training or a stressful stretch." };
  return { label: "High strain", note: "Far below your usual. Treat it gently, and check the reading was taken seated and still." };
}

// ---------------------------------------------------------------- readiness (rules model)

function readAppState() {
  try { return JSON.parse(localStorage.getItem(APP_KEY)) || {}; } catch (e) { return {}; }
}

const clamp = (x, lo, hi) => Math.max(lo, Math.min(hi, x));
const MOOD_SCORE = { low: 1, flat: 2, okay: 3, good: 4, great: 5 };

// state: parsed focuslens_web_v1; hrvRatio: current RMSSD / baseline or null; today: "YYYY-MM-DD".
export function computeReadiness(state, hrvRatio, today = store.todayKey()) {
  const comps = [];
  const missing = [];
  const targetMin = (state.sleepTargetHours || 8) * 60;
  const sleep = (state.sleep || []).slice().sort((a, b) => b.date.localeCompare(a.date));

  const yesterday = store.addDaysToKey(today, -1);
  const last = sleep.find((s) => s.date === today || s.date === yesterday);
  if (last) {
    const durMax = 30;
    let pts = durMax * Math.min(1, last.minutes / targetMin);
    let max = durMax;
    let detail = `${(last.minutes / 60).toFixed(1)} h slept vs ${(targetMin / 60).toFixed(1)} h target`;
    if (last.quality >= 1 && last.quality <= 5) {
      pts += 10 * ((last.quality - 1) / 4);
      max += 10;
      detail += `, rested ${last.quality}/5`;
    }
    comps.push({ id: "sleep", label: "Last night's sleep", pts, max, detail });
  } else missing.push("a sleep log for last night");

  const weekCut = store.addDaysToKey(today, -6);
  const week = sleep.filter((s) => s.date >= weekCut && s.date <= today);
  if (week.length >= 3) {
    const debt = week.reduce((a, s) => a + Math.max(0, targetMin - s.minutes), 0);
    const pts = 25 * clamp(1 - debt / 360, 0, 1); // 6 h of debt or more scores 0
    comps.push({ id: "debt", label: "7-day sleep debt", pts, max: 25, detail: `${(debt / 60).toFixed(1)} h short of target across ${week.length} logged nights` });
  } else missing.push("at least 3 sleep logs in the last 7 days");

  const moodCut = store.addDaysToKey(today, -3);
  const moods = (state.journal || [])
    .filter((e) => MOOD_SCORE[e.mood] && e.date >= moodCut)
    .sort((a, b) => b.date.localeCompare(a.date))
    .slice(0, 3);
  if (moods.length) {
    const avg = moods.reduce((a, e) => a + MOOD_SCORE[e.mood], 0) / moods.length;
    comps.push({ id: "mood", label: "Recent mood", pts: 15 * ((avg - 1) / 4), max: 15, detail: `average ${avg.toFixed(1)} / 5 over ${moods.length} journal ${moods.length === 1 ? "entry" : "entries"} in the last 3 days` });
  } else missing.push("a journal mood from the last 3 days");

  if (hrvRatio != null) {
    comps.push({ id: "hrv", label: "HRV vs your baseline", pts: 20 * clamp((hrvRatio - 0.4) / 0.5, 0, 1), max: 20, detail: `${Math.round(hrvRatio * 100)}% of your baseline RMSSD` });
  } else missing.push("a current HRV reading and a baseline of 5 saved readings");

  if (comps.length < 2) return { score: null, comps, missing, suggestions: [] };
  const got = comps.reduce((a, c) => a + c.pts, 0);
  const max = comps.reduce((a, c) => a + c.max, 0);
  const score = Math.round((got / max) * 100);

  const sug = [];
  const byId = Object.fromEntries(comps.map((c) => [c.id, c]));
  if (score < 50) sug.push("Plan a lighter day: one or two priorities, shorter focus blocks, and real breaks.");
  else if (score < 75) sug.push("A normal day should be fine. Put your hardest task early and keep breaks regular.");
  else sug.push("You look well set up. A good day for demanding or deep-focus work.");
  if (byId.debt && byId.debt.pts < 15) sug.push("Your sleep debt is building. Try an earlier bedtime tonight; the Sleep page can suggest times.");
  else if (byId.sleep && byId.sleep.pts / byId.sleep.max < 0.6) sug.push("Short night: avoid caffeine late in the day and aim for an earlier bedtime.");
  else if (byId.hrv && byId.hrv.pts / byId.hrv.max < 0.4) sug.push("Your HRV is low for you. Consider lighter training and a calmer evening.");
  else if (byId.mood && byId.mood.pts / byId.mood.max < 0.4) sug.push("Mood has been low lately. A short walk or talking to someone you trust can help.");
  return { score, comps, missing, suggestions: sug.slice(0, 2) };
}

// ---------------------------------------------------------------- live sensor state (module-level so it survives page changes)

const live = {
  status: "idle", // idle | connecting | connected | disconnected | error
  message: "",
  device: null,
  char: null,
  bpm: null,
  contact: null,
  trace: [], // {t, bpm}
  rr: [], // {t, rr}
  onUpdate: null,
  userDisconnect: false,
};

function trim(now) {
  const cut = now - TRACE_MS;
  while (live.trace.length && live.trace[0].t < cut) live.trace.shift();
  while (live.rr.length && live.rr[0].t < cut) live.rr.shift();
}

function onNotify(ev) {
  const p = parseHeartRateMeasurement(ev.target.value);
  if (!p) return;
  const now = Date.now();
  if (p.bpm > 0) {
    live.bpm = p.bpm;
    live.trace.push({ t: now, bpm: p.bpm });
  }
  live.contact = p.contact;
  p.rr.forEach((r, k) => live.rr.push({ t: now + k, rr: r }));
  trim(now);
  if (live.onUpdate) live.onUpdate();
}

function onGattDisconnected() {
  live.char = null;
  if (live.userDisconnect) return;
  live.status = "disconnected";
  live.message = "The sensor disconnected (out of range, battery, or it stopped sharing). Reconnect to continue.";
  if (live.onUpdate) live.onUpdate(true);
}

async function attach(device) {
  live.status = "connecting";
  live.message = "Connecting to " + (device.name || "sensor") + "…";
  if (live.onUpdate) live.onUpdate(true);
  const server = await device.gatt.connect();
  const service = await server.getPrimaryService("heart_rate");
  const char = await service.getCharacteristic("heart_rate_measurement");
  char.addEventListener("characteristicvaluechanged", onNotify);
  await char.startNotifications();
  live.char = char;
  live.status = "connected";
  live.message = "Connected to " + (device.name || "heart-rate sensor") + ". Waiting for the first beat…";
}

function friendlyError(e) {
  if (e && e.name === "NotFoundError") return "No device was chosen. Nothing was connected.";
  if (e && e.name === "SecurityError") return "Bluetooth is blocked here. It needs a secure (https) page and permission.";
  if (e && e.name === "NotSupportedError") return "That device does not offer a standard heart-rate service. Your watch may not be sharing heart rate (see the tips below).";
  return "Could not connect: " + ((e && e.message) || "unknown error") + ". Make sure the sensor is on, awake and not connected to another app, then try again.";
}

async function connect(reuse) {
  live.userDisconnect = false;
  try {
    let device = reuse && live.device;
    if (!device) {
      device = await navigator.bluetooth.requestDevice({ filters: [{ services: ["heart_rate"] }] });
      if (live.device && live.device !== device) live.device.removeEventListener("gattserverdisconnected", onGattDisconnected);
      live.device = device;
      device.addEventListener("gattserverdisconnected", onGattDisconnected);
    }
    await attach(device);
  } catch (e) {
    live.status = e && e.name === "NotFoundError" ? "idle" : "error";
    live.message = friendlyError(e);
  }
  if (live.onUpdate) live.onUpdate(true);
}

function disconnect() {
  live.userDisconnect = true;
  try { if (live.char) live.char.removeEventListener("characteristicvaluechanged", onNotify); } catch (e) {}
  try { if (live.device && live.device.gatt.connected) live.device.gatt.disconnect(); } catch (e) {}
  live.char = null;
  live.status = "idle";
  live.message = "Disconnected.";
  if (live.onUpdate) live.onUpdate(true);
}

// ---------------------------------------------------------------- charts

function cssVar(name, fallback) {
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return v || fallback;
}

function prepCanvas(canvas) {
  const dpr = window.devicePixelRatio || 1;
  const w = canvas.clientWidth || 300;
  const h = canvas.clientHeight || 140;
  if (canvas.width !== Math.round(w * dpr)) { canvas.width = Math.round(w * dpr); canvas.height = Math.round(h * dpr); }
  const ctx = canvas.getContext("2d");
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, w, h);
  return { ctx, w, h };
}

function drawLine(canvas, pts, opts) {
  const { ctx, w, h } = prepCanvas(canvas);
  const border = cssVar("--border", "#ddd");
  const muted = cssVar("--text-muted", "#666");
  const color = cssVar("--accent", "#2E7D5B");
  const padL = 34, padR = 8, padT = 8, padB = 18;
  ctx.font = "11px sans-serif";
  if (pts.length < 2) {
    ctx.fillStyle = muted;
    ctx.fillText(opts.empty, padL, h / 2);
    return;
  }
  const now = opts.now;
  let lo = Math.min(...pts.map((p) => p.y));
  let hi = Math.max(...pts.map((p) => p.y));
  const pad = Math.max(4, (hi - lo) * 0.15);
  lo = Math.floor(lo - pad);
  hi = Math.ceil(hi + pad);
  const X = (t) => padL + ((t - (now - TRACE_MS)) / TRACE_MS) * (w - padL - padR);
  const Y = (v) => padT + (1 - (v - lo) / (hi - lo)) * (h - padT - padB);
  ctx.strokeStyle = border;
  ctx.fillStyle = muted;
  ctx.lineWidth = 1;
  [lo, (lo + hi) / 2, hi].forEach((v) => {
    ctx.beginPath(); ctx.moveTo(padL, Y(v)); ctx.lineTo(w - padR, Y(v)); ctx.stroke();
    ctx.fillText(String(Math.round(v)), 2, Y(v) + 4);
  });
  ctx.fillText("2 min ago", padL, h - 4);
  ctx.fillText("now", w - padR - 22, h - 4);
  ctx.strokeStyle = color;
  ctx.fillStyle = color;
  ctx.lineWidth = 2;
  if (opts.dots) {
    pts.forEach((p) => { ctx.beginPath(); ctx.arc(X(p.t), Y(p.y), 2, 0, 6.3); ctx.fill(); });
  }
  ctx.beginPath();
  pts.forEach((p, i) => (i ? ctx.lineTo(X(p.t), Y(p.y)) : ctx.moveTo(X(p.t), Y(p.y))));
  ctx.stroke();
}

// ---------------------------------------------------------------- page

const fmt = (x, d = 0) => (x == null ? "–" : x.toFixed(d));

export function renderBody(container, rerender) {
  container.innerHTML = "";
  container.appendChild(el(`<h1>Body</h1>`));
  container.appendChild(el(`<p class="subtitle">Live heart rate from a Bluetooth sensor, heart-rate variability and a simple daily readiness score. All numbers come from your own sensor and your own FocusLens data.</p>`));

  const supported = typeof navigator !== "undefined" && !!navigator.bluetooth;

  // ---- Connect card
  const conn = el(`
    <div class="card">
      <div class="bold">Heart rate sensor</div>
      <div id="bd-msg" class="small muted" role="status" aria-live="polite" style="margin:6px 0 10px"></div>
      <div class="row gap-8" id="bd-actions"></div>
      <div id="bd-help"></div>
    </div>`);
  container.appendChild(conn);
  const msgEl = conn.querySelector("#bd-msg");
  const actEl = conn.querySelector("#bd-actions");
  const helpEl = conn.querySelector("#bd-help");

  const tips = `
    <details class="mt-8"><summary class="small bold">Which devices work?</summary>
      <p class="small muted">The sensor must advertise the standard Bluetooth heart-rate service. Chest straps (Polar H10, Wahoo TICKR, Garmin HRM) do. Many watches can do it through a setting such as <em>Heart rate broadcast</em> / <em>Broadcast heart rate</em> (Garmin, Polar, Wahoo, Amazfit and others). Some watches, including most smartwatches that only sync to their own app, cannot share live heart rate at all. HRV needs beat-to-beat (RR) intervals; some devices send heart rate without them, in which case you will see BPM but no HRV.</p>
    </details>`;

  if (!supported) {
    msgEl.textContent = "This browser can't talk to Bluetooth sensors. Web Bluetooth is not available in iOS Safari, Firefox or most in-app browsers. Open this page in Chrome or Edge on Android or on a desktop computer. No data is shown here because none can be read.";
    helpEl.innerHTML = tips;
  } else {
    helpEl.innerHTML = tips;
  }

  function drawActions() {
    actEl.innerHTML = "";
    if (!supported) return;
    const mk = (label, cls, fn) => {
      const b = el(`<button type="button" class="${cls}">${label}</button>`);
      b.onclick = fn;
      actEl.appendChild(b);
    };
    if (live.status === "connected" || live.status === "connecting") {
      mk("Disconnect", "outline", () => disconnect());
    } else if (live.status === "disconnected" && live.device) {
      mk("Reconnect", "primary", () => connect(true));
      mk("Choose another sensor", "text", () => connect(false));
    } else {
      mk("Connect heart rate sensor", "primary", () => connect(false));
    }
  }

  // ---- Live card
  const liveCard = el(`
    <div class="card">
      <div class="bold">Heart rate trace</div>
      <div class="row" style="align-items:baseline;gap:12px;margin:6px 0">
        <div id="bd-bpm" style="font-size:44px;font-weight:700;font-variant-numeric:tabular-nums">–</div>
        <div class="muted">BPM <span id="bd-contact" class="small"></span></div>
      </div>
      <canvas id="bd-hr" role="img" aria-label="Heart rate trace over the last two minutes" style="width:100%;height:140px;display:block"></canvas>
      <div class="bold mt-8">Beat-interval (RR) tachogram</div>
      <canvas id="bd-rr" role="img" aria-label="Beat interval tachogram, milliseconds between beats" style="width:100%;height:120px;display:block"></canvas>
      <p class="small muted mb-0">A trace of the heart rate your sensor reports (last 2 minutes), not a PPG waveform. The lower chart shows the time between beats in milliseconds.</p>
    </div>`);
  container.appendChild(liveCard);

  // ---- HRV card
  const hrvCard = el(`
    <div class="card">
      <div class="bold">Heart rate variability (HRV)</div>
      <div id="bd-hrv" style="margin:8px 0"></div>
      <div class="row gap-8"><button type="button" class="primary" id="bd-save" disabled>Save as resting reading</button></div>
      <p class="small muted" id="bd-save-note">For a resting reading sit still, breathe normally and keep connected for about 2 minutes first.</p>
    </div>`);
  container.appendChild(hrvCard);

  // ---- Stress card
  const stressCard = el(`<div class="card soft"><div class="bold">Stress and recovery estimate</div><div id="bd-stress" style="margin:8px 0"></div><p class="small muted mb-0">A wellness estimate compared with your own baseline. It is not a medical measure and cannot detect any condition.</p></div>`);
  container.appendChild(stressCard);

  // ---- Readiness card (static between rerenders except for HRV)
  const readyCard = el(`<div class="card"><div class="bold">Daily readiness</div><div id="bd-ready" style="margin:8px 0"></div><p class="small muted mb-0">A simple rules model over your own logged data, shown so you can see exactly how the number is made. It is not AI and not clinical.</p></div>`);
  container.appendChild(readyCard);

  // ---- Saved readings
  const savedCard = el(`<div class="card"><div class="bold">Saved resting readings</div><div id="bd-saved" style="margin:8px 0"></div></div>`);
  container.appendChild(savedCard);

  container.appendChild(el(`<div class="card soft"><p class="small" style="margin:0"><strong>Not a medical device.</strong> FocusLens does not diagnose, monitor or treat any condition, and consumer sensors can be wrong. If you have chest pain, fainting, severe breathlessness, dizziness or a racing or irregular heartbeat that worries you, stop and seek medical care or call your local emergency number.</p></div>`));

  // ---- Updaters
  let hrvNow = null;
  function currentHrv() {
    return computeHrv(live.rr.map((r) => r.rr));
  }

  function refreshHrv() {
    const h = (hrvNow = currentHrv());
    const box = hrvCard.querySelector("#bd-hrv");
    const save = hrvCard.querySelector("#bd-save");
    if (h.ready) {
      box.innerHTML = `<div class="row" style="gap:24px"><div><div style="font-size:30px;font-weight:700">${fmt(h.rmssd, 0)} <span class="small muted">ms</span></div><div class="small muted">RMSSD</div></div><div><div style="font-size:30px;font-weight:700">${fmt(h.sdnn, 0)} <span class="small muted">ms</span></div><div class="small muted">SDNN</div></div></div><div class="small muted">From ${h.clean} clean beats in the last 2 minutes${h.rejected ? " (" + h.rejected + " rejected as artifacts)" : ""}.</div>`;
      save.disabled = false;
    } else {
      box.innerHTML = `<div class="muted">Not enough clean beats yet: ${h.clean} of ${MIN_CLEAN_BEATS} needed${h.rejected ? " (" + h.rejected + " rejected as artifacts)" : ""}.${live.status === "connected" && live.bpm != null && !live.rr.length ? " Your sensor is sending heart rate but no beat intervals, so HRV can't be computed from it." : ""}</div>`;
      save.disabled = true;
    }
    refreshStress();
  }

  function refreshStress() {
    const readings = loadReadings();
    const base = baselineOf(readings);
    const box = stressCard.querySelector("#bd-stress");
    const h = hrvNow;
    const cur = h && h.ready ? h.rmssd : null;
    if (readings.length < MIN_BASELINE_READINGS) {
      box.innerHTML = `<div>${cur != null ? `Current RMSSD: <strong>${fmt(cur)} ms</strong>. ` : ""}No personal baseline yet. Save ${MIN_BASELINE_READINGS} resting readings (you have ${readings.length}) on different days or times and an estimate will appear here. Without your own baseline, RMSSD can't be judged, because it differs a lot between people.</div>`;
    } else if (cur == null) {
      box.innerHTML = `<div>Your baseline is <strong>${fmt(base)} ms</strong> (median of ${readings.length} readings). Connect a sensor and hold still for about 2 minutes to compare.</div>`;
    } else {
      const ratio = cur / base;
      const b = stressBand(ratio);
      box.innerHTML = `<div style="font-size:24px;font-weight:700">${b.label}</div><div>Current ${fmt(cur)} ms vs baseline ${fmt(base)} ms (${Math.round(ratio * 100)}%).</div><div class="small muted">${b.note}</div>`;
    }
    refreshReadiness();
  }

  function refreshReadiness() {
    const readings = loadReadings();
    const base = baselineOf(readings);
    const ratio = readings.length >= MIN_BASELINE_READINGS && hrvNow && hrvNow.ready && base ? hrvNow.rmssd / base : null;
    const r = computeReadiness(readAppState(), ratio);
    const box = readyCard.querySelector("#bd-ready");
    let html = "";
    if (r.score == null) {
      html += `<div>Not enough data for a score yet; at least 2 components are needed.</div>`;
    } else {
      html += `<div style="font-size:36px;font-weight:700">${r.score} <span class="small muted">/ 100</span></div>`;
    }
    if (r.comps.length) {
      html += `<table style="width:100%;border-collapse:collapse;margin:8px 0" class="small"><tbody>` +
        r.comps.map((c) => `<tr style="border-top:1px solid var(--border)"><td style="padding:6px 0"><div class="bold">${escapeHtml(c.label)}</div><div class="muted">${escapeHtml(c.detail)}</div></td><td style="text-align:right;white-space:nowrap;font-variant-numeric:tabular-nums">${c.pts.toFixed(1)} / ${c.max}</td></tr>`).join("") +
        `</tbody></table><div class="small muted">Score = points earned ÷ points available, so missing components don't count against you.</div>`;
    }
    if (r.missing.length) html += `<div class="small" style="margin-top:6px">Missing: ${r.missing.map(escapeHtml).join("; ")}.</div>`;
    if (r.suggestions.length) html += `<ul style="margin:8px 0 0;padding-left:18px">${r.suggestions.map((s) => `<li>${escapeHtml(s)}</li>`).join("")}</ul>`;
    box.innerHTML = html;
  }

  function refreshSaved() {
    const readings = loadReadings();
    const box = savedCard.querySelector("#bd-saved");
    if (!readings.length) { box.innerHTML = `<div class="muted">None yet.</div>`; return; }
    const base = baselineOf(readings);
    box.innerHTML = `<div class="small">Baseline (median RMSSD): <strong>${fmt(base)} ms</strong> from ${readings.length} reading${readings.length === 1 ? "" : "s"}${readings.length < MIN_BASELINE_READINGS ? ` (${MIN_BASELINE_READINGS} needed for the estimate)` : ""}.</div>` +
      readings.slice(-8).reverse().map((r) => `<div class="task-item small">${escapeHtml(new Date(r.t).toLocaleString())}: RMSSD ${fmt(r.rmssd)} ms, avg ${fmt(r.avgBpm)} BPM</div>`).join("") +
      `<div class="mt-8"><button type="button" class="text" id="bd-clear">Delete all saved readings</button></div>`;
    box.querySelector("#bd-clear").onclick = () => {
      if (confirm("Delete all saved resting readings?")) { saveReadings([]); refreshSaved(); refreshStress(); }
    };
  }

  hrvCard.querySelector("#bd-save").onclick = () => {
    const h = currentHrv();
    if (!h.ready) return;
    const list = loadReadings();
    list.push({ t: Date.now(), rmssd: Math.round(h.rmssd * 10) / 10, avgBpm: Math.round(h.avgBpm * 10) / 10 });
    saveReadings(list);
    hrvCard.querySelector("#bd-save-note").textContent = "Saved. Resting readings taken at a similar time of day make the baseline more meaningful.";
    refreshSaved();
    refreshStress();
  };

  let lastSlow = 0;
  function paint(structural) {
    msgEl.textContent = supported ? live.message || (live.status === "idle" ? "Not connected. Put your sensor on, turn on heart rate broadcast if it's a watch, then connect." : "") : msgEl.textContent;
    if (structural) drawActions();
    const bpmEl = liveCard.querySelector("#bd-bpm");
    bpmEl.textContent = live.status === "connected" || live.trace.length ? (live.bpm != null ? String(live.bpm) : "–") : "–";
    const c = liveCard.querySelector("#bd-contact");
    c.textContent = live.contact === false ? "· sensor not touching skin" : "";
    const now = Date.now();
    drawLine(liveCard.querySelector("#bd-hr"), live.trace.map((p) => ({ t: p.t, y: p.bpm })), { now, empty: "Waiting for heart rate from a sensor…" });
    drawLine(liveCard.querySelector("#bd-rr"), live.rr.map((p) => ({ t: p.t, y: p.rr })), { now, dots: true, empty: "No beat intervals yet (RR)." });
    if (structural || now - lastSlow > 1000) { lastSlow = now; refreshHrv(); }
  }

  live.onUpdate = (structural) => {
    if (!document.body.contains(container)) { live.onUpdate = null; return; }
    paint(structural);
  };

  drawActions();
  refreshSaved();
  // Layout is final only after insertion; defer the first paint a frame so canvases have a size.
  requestAnimationFrame(() => paint(true));
}
