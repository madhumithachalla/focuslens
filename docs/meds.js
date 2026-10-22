// Medications: keep track of what you take and when, get reminders, and send a private update to
// family. Everything is typed or confirmed by you from your own label. FocusLens never suggests a
// dose, a product or an interaction. Data stays on this device unless you opt in to account sync.
import * as store from "./storage.js";
import { renderMedSafety } from "./care.js";

function el(html) {
  const t = document.createElement("template");
  t.innerHTML = html.trim();
  return t.content.firstElementChild;
}
const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const DAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

// ---------------------------------------------------------------- Reading a label's text

const NOISE = /^(rx|dispense|pharmacy|patient|dr\.?|doctor|prescri|keep|store|each|contains|warning|caution|expiry|exp|batch|lot|use by|refills?|qty|quantity|date|ph:|tel|phone|www)/i;
const PLACE = /\b(pharmacy|chemist|clinic|hospital|pty|ltd|inc|street|st\.|road|rd\.|avenue)\b/i;
const STRENGTH = /(\d+(?:[.,]\d+)?)\s?(mg|mcg|µg|ug|g|ml|iu|units?|%)(?![a-z])/i;
const INSTR = /\b(take|taken|apply|use|inhale|dissolve|swallow|chew|insert)\b|\b(once|twice|daily|every|with food|after meals?|before (?:bed|meals?)|morning|night|bedtime)\b/i;

/**
 * Pulls a product name, strength, instruction line and suggested reminder times out of text
 * pasted from a label or box. These are only proposals for the user to confirm: the text is the
 * user's own label, nothing is looked up or recommended.
 */
export function parseLabelText(text) {
  const lines = String(text || "").split(/\r?\n/).map((l) => l.replace(/\s+/g, " ").trim()).filter(Boolean);
  let strength = "";
  for (const l of lines) {
    const m = l.match(STRENGTH);
    if (m) { strength = `${m[1].replace(",", ".")} ${m[2].toLowerCase().replace("µg", "mcg").replace("ug", "mcg")}`; break; }
  }
  // The product line is usually the one carrying the strength or "tablets"; otherwise take the
  // first line that isn't a pharmacy name, address or warning.
  const candidates = lines.filter((l) => !NOISE.test(l) && !PLACE.test(l) && !INSTR.test(l) && /[a-z]{3,}/i.test(l));
  const productLine = candidates.find((l) => STRENGTH.test(l) || /\b(tablets?|capsules?|caps|tabs|syrup|cream|drops)\b/i.test(l)) || candidates[0] || "";
  const name = productLine.replace(STRENGTH, "").replace(/\b(tablets?|capsules?|caps|tabs)\b/gi, "").replace(/[,;:\-–]+$/g, "").replace(/\s+/g, " ").trim();
  const instrLines = lines.filter((l) => INSTR.test(l)).slice(0, 2);
  const instructions = instrLines.join(" ");
  const low = instructions.toLowerCase();
  let times = [];
  if (/three times|3 times|\btds\b|\btid\b/.test(low)) times = ["08:00", "14:00", "20:00"];
  else if (/four times|4 times|\bqid\b/.test(low)) times = ["08:00", "12:00", "16:00", "20:00"];
  else if (/twice|2 times|\bbd\b|\bbid\b|two times/.test(low)) times = ["08:00", "20:00"];
  else if (/bedtime|at night|before bed|nocte/.test(low)) times = ["21:30"];
  else if (/once|daily|morning|every day|\bod\b/.test(low)) times = ["08:00"];
  return { name, strength, instructions, times };
}

// ---------------------------------------------------------------- Reminders (while the page is open)

let reminderTimer = null;
const notified = new Set();

function beep() {
  try {
    const ctx = new (window.AudioContext || window.webkitAudioContext)();
    const o = ctx.createOscillator(); const g = ctx.createGain();
    o.frequency.value = 660; g.gain.value = 0.15; o.connect(g); g.connect(ctx.destination);
    o.start(); o.stop(ctx.currentTime + 0.4);
  } catch (e) {}
}

export function getHideNameInNotifications() {
  try { return localStorage.getItem("focuslens_med_hide_name") !== "0"; } catch (e) { return true; }
}
function setHideName(v) { try { localStorage.setItem("focuslens_med_hide_name", v ? "1" : "0"); } catch (e) {} }
export function remindersEnabled() {
  try { return localStorage.getItem("focuslens_med_reminders") === "1"; } catch (e) { return false; }
}
function setRemindersEnabled(v) { try { localStorage.setItem("focuslens_med_reminders", v ? "1" : "0"); } catch (e) {} }

function checkDue() {
  if (!remindersEnabled()) return;
  const day = store.todayKey();
  store.dosesToday().filter((d) => d.state === "due").forEach((d) => {
    const k = `${day}:${d.planId}@${d.time}`;
    if (notified.has(k)) return;
    notified.add(k);
    beep();
    try {
      if (window.Notification && Notification.permission === "granted") {
        const body = getHideNameInNotifications() ? "Time for a medication" : `Time for ${d.name}${d.strength ? " " + d.strength : ""}`;
        new Notification("FocusLens reminder", { body, tag: k });
      }
    } catch (e) {}
  });
}

/** Called once at app start so reminders fire whichever page is open. */
export function startMedReminders() {
  if (reminderTimer) return;
  reminderTimer = setInterval(checkDue, 20000);
  checkDue();
}

// ---------------------------------------------------------------- Page

const STATE_LABEL = { taken: "Taken", due: "Due now", upcoming: "Upcoming", missed: "Missed" };

export function renderMeds(container, rerender) {
  container.appendChild(el(`
    <div>
      <h1>Medications</h1>
      <p class="subtitle">Track what you take and when, get reminders, and send a quick update to someone who looks out for you.</p>
    </div>`));

  // Privacy
  const local = store.getMedsLocalOnly();
  const priv = el(`
    <div class="card soft">
      <div class="bold">Private by default</div>
      <p class="small" style="margin:6px 0 8px">Your medication list and dose log stay on this device. They are not uploaded to your account, not shared, and not used by the Coach. Sharing happens only when you tap Share, through your own messaging app.</p>
      <label class="small" style="display:flex;gap:8px;align-items:center"><input type="checkbox" id="med-sync" ${local ? "" : "checked"} /> Also sync my medications to my FocusLens account (for my other devices)</label>
    </div>`);
  priv.querySelector("#med-sync").onchange = (e) => { store.setMedsLocalOnly(!e.target.checked); rerender(); };
  container.appendChild(priv);

  // Today
  const doses = store.dosesToday();
  const todayCard = el(`<div class="card"><div class="bold">Today</div></div>`);
  if (!doses.length) todayCard.appendChild(el(`<p class="muted">Nothing scheduled today. Add a medication below.</p>`));
  doses.forEach((d) => {
    const row = el(`
      <div class="row task-item">
        <div>
          <div class="bold">${esc(d.time)} · ${esc(d.name)}${d.strength ? ` <span class="small muted">${esc(d.strength)}</span>` : ""}</div>
          <div class="small muted">${STATE_LABEL[d.state]}${d.takenAt ? ` at ${new Date(d.takenAt).toTimeString().slice(0, 5)}` : ""}</div>
        </div>
        <button class="${d.state === "taken" ? "text" : "primary"}">${d.state === "taken" ? "Undo" : "Mark taken"}</button>
      </div>`);
    row.querySelector("button").onclick = () => { store.markDose(d.planId, d.time, d.state !== "taken"); rerender(); };
    todayCard.appendChild(row);
  });
  container.appendChild(todayCard);

  // Reminders
  const rem = el(`
    <div class="card">
      <div class="bold">Reminders</div>
      <label class="small" style="display:flex;gap:8px;align-items:center;margin-top:8px"><input type="checkbox" id="med-rem" ${remindersEnabled() ? "checked" : ""} /> Remind me when a dose is due</label>
      <label class="small" style="display:flex;gap:8px;align-items:center;margin-top:6px"><input type="checkbox" id="med-hide" ${getHideNameInNotifications() ? "checked" : ""} /> Hide the medication name in notifications</label>
      <p class="small muted" style="margin:8px 0 0">On the web, reminders only fire while FocusLens is open in a tab or installed as an app, a limit of every website. For reminders that work with the phone locked or the app closed, use the Android app, which schedules them with the system alarm.</p>
    </div>`);
  rem.querySelector("#med-rem").onchange = async (e) => {
    setRemindersEnabled(e.target.checked);
    if (e.target.checked && window.Notification && Notification.permission === "default") { try { await Notification.requestPermission(); } catch (err) {} }
    startMedReminders();
  };
  rem.querySelector("#med-hide").onchange = (e) => setHideName(e.target.checked);
  container.appendChild(rem);

  // Share
  container.appendChild(renderShare());

  // Add
  container.appendChild(renderAddForm(rerender));

  // List
  const plans = store.listMedPlans();
  const listCard = el(`<div class="card"><div class="bold">My medications</div></div>`);
  if (!plans.length) listCard.appendChild(el(`<p class="muted">None yet.</p>`));
  plans.forEach((p) => {
    const row = el(`
      <div class="row task-item">
        <div>
          <div class="bold">${esc(p.name)} ${p.strength ? `<span class="small muted">${esc(p.strength)}</span>` : ""}</div>
          <div class="small muted">${esc((p.times || []).join(", ") || "No times set")} · ${p.days && p.days.length ? p.days.map((d) => DAYS[d]).join(" ") : "Every day"}</div>
          ${p.instructions ? `<div class="small muted">${esc(p.instructions)}</div>` : ""}
        </div>
        <button class="text" style="color:var(--danger)">Delete</button>
      </div>`);
    row.querySelector("button").onclick = () => { if (confirm(`Delete ${p.name}?`)) { store.deleteMedPlan(p.id); rerender(); } };
    listCard.appendChild(row);
  });
  container.appendChild(listCard);

  container.appendChild(el(`<p class="small muted">FocusLens never suggests doses, products or interactions. It only keeps what you enter from your own label. Follow your doctor's or pharmacist's advice.</p>`));
  renderMedSafety(container);
}

function renderShare() {
  const card = el(`
    <div class="card">
      <div class="bold">Share today's update</div>
      <p class="small muted">Builds a short text you can send to a family member. Nothing is sent by FocusLens: you choose the app and the person.</p>
      <label class="small" style="display:flex;gap:8px;align-items:center"><input type="checkbox" id="share-notes" /> Include my notes</label>
      <textarea id="share-text" rows="5" readonly class="mt-8"></textarea>
      <div class="chips mt-8" style="margin-bottom:0">
        <button type="button" class="chip" id="share-native">Share…</button>
        <button type="button" class="chip" id="share-copy">Copy</button>
        <a class="chip" id="share-wa" target="_blank" rel="noopener noreferrer">WhatsApp</a>
        <a class="chip" id="share-mail">Email</a>
        <a class="chip" id="share-sms">Text message</a>
      </div>
      <p class="small" id="share-msg" style="margin:6px 0 0" role="status"></p>
    </div>`);
  const ta = card.querySelector("#share-text");
  const paint = () => {
    const text = store.medShareText(Date.now(), card.querySelector("#share-notes").checked);
    ta.value = text;
    const enc = encodeURIComponent(text);
    card.querySelector("#share-wa").href = `https://wa.me/?text=${enc}`;
    card.querySelector("#share-mail").href = `mailto:?subject=${encodeURIComponent("Medication update")}&body=${enc}`;
    card.querySelector("#share-sms").href = `sms:?&body=${enc}`;
  };
  paint();
  card.querySelector("#share-notes").onchange = paint;
  const msg = card.querySelector("#share-msg");
  const nativeBtn = card.querySelector("#share-native");
  if (!navigator.share) nativeBtn.hidden = true;
  nativeBtn.onclick = async () => { try { await navigator.share({ title: "Medication update", text: ta.value }); } catch (e) {} };
  card.querySelector("#share-copy").onclick = async () => {
    try { await navigator.clipboard.writeText(ta.value); msg.textContent = "Copied."; }
    catch (e) { ta.select(); msg.textContent = "Select and copy the text above."; }
  };
  return card;
}

function renderAddForm(rerender) {
  const times = [];
  const days = new Set();
  const card = el(`
    <div class="card">
      <div class="bold">Add a medication</div>
      <details class="mt-8"><summary class="small bold" style="cursor:pointer">Fill from label text</summary>
        <p class="small muted">Paste the text from your box or label (on a phone, your keyboard or camera's "copy text" works). FocusLens proposes a name, strength and times for you to check. Nothing is looked up online. The Android app can also scan the label with the camera, on the device.</p>
        <textarea id="med-paste" rows="4" placeholder="Paste label text here"></textarea>
        <div class="mt-8"><button type="button" class="outline" id="med-parse">Read it</button></div>
      </details>
      <label class="field" for="med-name">Name (as printed)</label>
      <input type="text" id="med-name" maxlength="80" />
      <label class="field" for="med-strength">Strength (as printed, optional)</label>
      <input type="text" id="med-strength" maxlength="40" placeholder="e.g. 500 mg" />
      <label class="field" for="med-instr">Instructions on the label (optional)</label>
      <input type="text" id="med-instr" maxlength="160" />
      <label class="field" for="med-time">Times</label>
      <div style="display:flex;gap:8px"><input type="time" id="med-time" value="08:00" /><button type="button" class="outline" id="med-addtime">Add time</button></div>
      <div class="chips mt-8" id="med-times" style="margin-bottom:0"></div>
      <label class="field" style="margin-bottom:6px">Days (none selected = every day)</label>
      <div class="chips" id="med-days" style="margin-bottom:0"></div>
      <label class="field" for="med-notes">Notes (optional, only you see them unless you share them)</label>
      <input type="text" id="med-notes" maxlength="160" />
      <p class="small" id="med-err" style="color:var(--danger);margin:6px 0 0" role="alert"></p>
      <div class="mt-8"><button class="primary" id="med-save">Save medication</button></div>
    </div>`);
  const timesBox = card.querySelector("#med-times");
  const paintTimes = () => {
    timesBox.innerHTML = "";
    times.sort().forEach((t, i) => {
      const chip = el(`<button type="button" class="chip active" aria-label="Remove ${t}">${t} ×</button>`);
      chip.onclick = () => { times.splice(times.indexOf(t), 1); paintTimes(); };
      timesBox.appendChild(chip);
    });
  };
  card.querySelector("#med-addtime").onclick = () => {
    const v = card.querySelector("#med-time").value;
    if (v && !times.includes(v)) { times.push(v); paintTimes(); }
  };
  DAYS.forEach((d, i) => {
    const chip = el(`<button type="button" class="chip" aria-pressed="false">${d}</button>`);
    chip.onclick = () => { if (days.has(i)) days.delete(i); else days.add(i); chip.classList.toggle("active", days.has(i)); chip.setAttribute("aria-pressed", String(days.has(i))); };
    card.querySelector("#med-days").appendChild(chip);
  });
  card.querySelector("#med-parse").onclick = () => {
    const r = parseLabelText(card.querySelector("#med-paste").value);
    if (r.name) card.querySelector("#med-name").value = r.name;
    if (r.strength) card.querySelector("#med-strength").value = r.strength;
    if (r.instructions) card.querySelector("#med-instr").value = r.instructions;
    r.times.forEach((t) => { if (!times.includes(t)) times.push(t); });
    paintTimes();
    card.querySelector("#med-err").textContent = "Please check these against your box before saving. Reading text can be wrong.";
  };
  card.querySelector("#med-save").onclick = () => {
    const name = card.querySelector("#med-name").value.trim();
    const err = card.querySelector("#med-err");
    if (!name) { err.textContent = "Add the medication's name."; return; }
    if (!times.length) { err.textContent = "Add at least one time so you can be reminded."; return; }
    store.addMedPlan({
      name, strength: card.querySelector("#med-strength").value.trim(), instructions: card.querySelector("#med-instr").value.trim(),
      times: [...times].sort(), days: [...days].sort(), notes: card.querySelector("#med-notes").value.trim(),
    });
    rerender();
  };
  return card;
}
