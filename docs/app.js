import {
  LEARN_TOPICS, LEARN_ITEMS, REFLECTION_PROMPTS,
  MEDIA_GENRES, MEDIA_ITEMS, MEDIA_KINDS,
  REWARD_IDEAS, MOODS, MISS_REASONS, ROADMAP, PHASE_GUIDANCE, TRACKER_TYPES, JOURNAL_TEMPLATES,
} from "./content.js";
import * as store from "./storage.js";
import { buildIcs, downloadIcs, googleCalendarUrl } from "./calendar.js";
import {
  watchAuth, signUp, signIn, signOutUser, authErrorMessage,
  signInWithGoogle, handleGoogleRedirectResult,
} from "./firebase.js";

const ROUTES = [
  { id: "roadmap", label: "Roadmap" },
  { id: "journal", label: "Journal" },
  { id: "cycle", label: "Tracking" },
  { id: "tasks", label: "To-do" },
  { id: "mental-health", label: "Mental health" },
  { id: "learn", label: "Learn" },
  { id: "explore", label: "Explore" },
  { id: "birthdays", label: "Birthdays" },
  { id: "account", label: "Account" },
];

let currentUser = null;
let googleRedirectError = null;
watchAuth((user) => {
  currentUser = user;
  updateAuthTrigger();
  if (currentRoute() === "account") render();
});
// Picks up the user coming back from the Google sign-in redirect, if any. Errors here
// (unauthorized domain, provider not enabled, etc.) must surface on the Account page, not be
// swallowed silently — otherwise a failed sign-in just looks like nothing happened.
handleGoogleRedirectResult().catch((e) => {
  googleRedirectError = authErrorMessage(e);
  if (currentRoute() === "account") render();
});

const nav = document.getElementById("nav");
const page = document.getElementById("page");

// ---------------------------------------------------------------- Install / Add to Home Screen

if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("sw.js").catch(() => {});
  });
}

const installBtn = document.getElementById("install-btn");
let deferredInstallPrompt = null;

// Chrome/Edge/Android fire this when the page meets their installability criteria (manifest +
// service worker + HTTPS). Capturing it lets a real button trigger the native prompt, instead
// of relying on whatever each browser's own menu happens to call it.
window.addEventListener("beforeinstallprompt", (event) => {
  event.preventDefault();
  deferredInstallPrompt = event;
  installBtn.hidden = false;
});

installBtn.onclick = async () => {
  if (!deferredInstallPrompt) return;
  deferredInstallPrompt.prompt();
  await deferredInstallPrompt.userChoice;
  deferredInstallPrompt = null;
  installBtn.hidden = true;
};

window.addEventListener("appinstalled", () => {
  installBtn.hidden = true;
});

// iOS Safari never fires beforeinstallprompt and has no programmatic install API — the only
// way to add to the home screen there is the manual Share-sheet step, so show instructions
// instead of a non-functional button. Skipped if already running standalone (already added).
const isIos = /iphone|ipad|ipod/i.test(navigator.userAgent);
const isStandalone = window.matchMedia("(display-mode: standalone)").matches || navigator.standalone === true;
let iosTipDismissed = false;
try { iosTipDismissed = !!localStorage.getItem("focuslens_ios_tip_dismissed"); } catch (e) {}
if (isIos && !isStandalone && !iosTipDismissed) {
  const tip = document.getElementById("ios-install-tip");
  tip.hidden = false;
  document.getElementById("ios-install-dismiss").onclick = () => {
    tip.hidden = true;
    try { localStorage.setItem("focuslens_ios_tip_dismissed", "1"); } catch (e) {}
  };
}

function currentRoute() {
  const hash = location.hash.replace("#", "");
  return ROUTES.some((r) => r.id === hash) ? hash : "roadmap";
}

const navToggle = document.getElementById("nav-toggle");
navToggle.onclick = () => {
  const isOpen = nav.classList.toggle("open");
  navToggle.setAttribute("aria-expanded", String(isOpen));
};

function renderNav() {
  const active = currentRoute();
  nav.innerHTML = "";
  ROUTES.forEach((r) => {
    const btn = document.createElement("button");
    btn.textContent = r.label;
    btn.className = r.id === active ? "active" : "";
    btn.onclick = () => {
      location.hash = r.id;
      // Only relevant on the narrow/hamburger layout — harmless no-op otherwise.
      nav.classList.remove("open");
      navToggle.setAttribute("aria-expanded", "false");
    };
    nav.appendChild(btn);
  });
}

// ---------------------------------------------------------------- Sign-in popup

const authModal = document.getElementById("auth-modal");
const authModalBody = document.getElementById("auth-modal-body");
const authTrigger = document.getElementById("auth-trigger");

function updateAuthTrigger() {
  authTrigger.textContent = currentUser ? (currentUser.displayName || currentUser.email || "Account") : "Sign in";
}

function openAuthModal() {
  authModalBody.innerHTML = "";
  authModalBody.appendChild(el(`<h2 style="margin-top:0;margin-bottom:4px">Sign in</h2>`));
  const formContainer = el(`<div></div>`);
  authModalBody.appendChild(formContainer);
  renderAuthForm(formContainer, () => {
    closeAuthModal();
    updateAuthTrigger();
    if (currentRoute() === "account") render();
  });
  authModal.hidden = false;
}

function closeAuthModal() {
  authModal.hidden = true;
}

document.getElementById("auth-modal-close").onclick = closeAuthModal;
authModal.addEventListener("click", (e) => {
  if (e.target === authModal) closeAuthModal();
});

authTrigger.onclick = () => {
  if (currentUser) {
    location.hash = "account";
    nav.classList.remove("open");
  } else {
    openAuthModal();
  }
};

function render() {
  renderNav();
  const route = currentRoute();
  page.innerHTML = "";
  const renderers = {
    roadmap: renderRoadmap,
    journal: renderJournal,
    cycle: renderCycle,
    tasks: renderTasks,
    "mental-health": renderMentalHealth,
    learn: renderLearn,
    explore: renderExplore,
    birthdays: renderBirthdays,
    account: renderAccount,
  };
  (renderers[route] || renderRoadmap)(page);
  window.scrollTo(0, 0);
}

window.addEventListener("hashchange", render);
render();

// ---------------------------------------------------------------- helpers

function el(html) {
  const t = document.createElement("template");
  t.innerHTML = html.trim();
  return t.content.firstElementChild;
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

// ---------------------------------------------------------------- Roadmap

function renderRoadmap(container) {
  container.appendChild(el(`
    <div>
      <h1>FocusLens — Web</h1>
      <p class="subtitle">Everything here works in any browser, on any device, including iPhone (no App Store needed — add this page to your home screen for an app-like icon). App/feature blocking stays on the native Android app, explained below.</p>
    </div>
  `));

  container.appendChild(el(`<div class="roadmap-section-title">Live now</div>`));
  ROADMAP.current.forEach((item) => {
    container.appendChild(el(`
      <div class="card">
        <div class="row mb-0"><span class="bold">${escapeHtml(item.title)}</span><span class="badge">Live</span></div>
        <p class="muted small mt-8" style="margin-bottom:0">${escapeHtml(item.brief)}</p>
      </div>
    `));
  });

  container.appendChild(el(`<div class="roadmap-section-title">Upcoming (needs the Firebase account system)</div>`));
  ROADMAP.upcoming.forEach((item) => {
    container.appendChild(el(`
      <div class="card">
        <div class="row mb-0"><span class="bold">${escapeHtml(item.title)}</span><span class="badge" style="color:#8a6d1f;background:#f6ecd3">Upcoming</span></div>
        <p class="muted small mt-8" style="margin-bottom:0">${escapeHtml(item.brief)}</p>
      </div>
    `));
  });

  container.appendChild(el(`<div class="roadmap-section-title">Android app only</div>`));
  ROADMAP.androidOnly.forEach((item) => {
    container.appendChild(el(`
      <div class="card soft">
        <div class="bold">${escapeHtml(item.title)}</div>
        <p class="muted small mt-8" style="margin-bottom:0">${escapeHtml(item.brief)}</p>
      </div>
    `));
  });

  container.appendChild(el(`
    <div class="card dark mt-8">
      <div class="bold" style="margin-bottom:6px">Get the Android app</div>
      <p class="small" style="opacity:0.85;margin-bottom:10px">Every push to main auto-builds a real, installable APK.</p>
      <a href="https://github.com/madhumithachalla/focuslens/releases/latest/download/focuslens-debug.apk" style="color:#fff;font-weight:600">Download latest APK →</a>
    </div>
  `));
}

// ---------------------------------------------------------------- Journal

function renderJournal(container) {
  const streak = store.journalStreak();
  const milestone = store.milestoneFor(streak);
  const today = store.todayKey();
  const entries = store.recentJournal(14);
  const todayEntry = entries.find((e) => e.date === today);

  container.appendChild(el(`
    <div>
      <h1>Journal</h1>
      <p class="subtitle">One entry a day. ${streak > 0 ? `Current streak: <span class="bold">${streak} day${streak === 1 ? "" : "s"}</span>.` : "No streak yet — today's entry starts one."}</p>
    </div>
  `));

  if (milestone) {
    container.appendChild(el(`<div class="card soft center bold">🎉 ${milestone}-day streak!</div>`));
  }

  if (todayEntry) {
    container.appendChild(renderRewardCard("You've already journaled today — nice"));
  }

  const card = el(`
    <div class="card">
      <div class="bold mb-0">${todayEntry ? "Update today's entry" : "Today's entry"}</div>
      <p class="field" id="mood-label">Mood</p>
      <div class="chips" id="mood-chips" role="group" aria-labelledby="mood-label"></div>
      <label class="field" for="journal-text">What's on your mind?</label>
      <textarea id="journal-text" placeholder="Write freely, or answer: what's one thing that went well today?"></textarea>
      <div class="mt-8"><button class="primary" id="journal-save">Save entry</button></div>
    </div>
  `);
  container.appendChild(card);

  const templatesCard = el(`
    <div class="card soft">
      <div class="bold mb-0" style="font-size:13px">Templates</div>
      <p class="small muted mt-8" style="margin-bottom:8px">Fills in the box above with a starting structure — or just leave it blank and write freely.</p>
      <div class="chips"></div>
    </div>
  `);
  const templateChips = templatesCard.querySelector(".chips");
  JOURNAL_TEMPLATES.forEach((t) => {
    const chip = el(`<button type="button" class="chip">${t.emoji} ${escapeHtml(t.title)}</button>`);
    chip.onclick = () => {
      card.querySelector("#journal-text").value = t.scaffold;
      card.querySelector("#journal-text").focus();
    };
    templateChips.appendChild(chip);
  });
  container.appendChild(templatesCard);

  let selectedMood = todayEntry?.mood || null;
  const moodChips = card.querySelector("#mood-chips");
  MOODS.forEach((m) => {
    const chip = el(`<button type="button" class="chip${selectedMood === m.id ? " active" : ""}" aria-pressed="${selectedMood === m.id}" aria-label="Mood: ${m.id}">${m.emoji}</button>`);
    chip.onclick = () => {
      selectedMood = m.id;
      moodChips.querySelectorAll(".chip").forEach((c) => { c.classList.remove("active"); c.setAttribute("aria-pressed", "false"); });
      chip.classList.add("active");
      chip.setAttribute("aria-pressed", "true");
    };
    moodChips.appendChild(chip);
  });
  card.querySelector("#journal-text").value = todayEntry?.text || "";

  card.querySelector("#journal-save").onclick = () => {
    const text = card.querySelector("#journal-text").value.trim();
    if (!text) return;
    store.saveJournal(text, selectedMood, null);
    render();
  };

  container.appendChild(el(`<div class="roadmap-section-title">Recent entries</div>`));
  if (entries.length === 0) {
    container.appendChild(el(`<p class="muted">Nothing yet.</p>`));
  }
  entries.forEach((e) => {
    const mood = MOODS.find((m) => m.id === e.mood);
    container.appendChild(el(`
      <div class="card">
        <div class="row mb-0"><span class="small muted">${formatDateKey(e.date)}</span><span>${mood ? mood.emoji : ""}</span></div>
        <p class="mt-8" style="margin-bottom:0">${escapeHtml(e.text)}</p>
      </div>
    `));
  });
}

function formatDateKey(key) {
  const d = new Date(Number(key.slice(0, 4)), Number(key.slice(4, 6)) - 1, Number(key.slice(6, 8)));
  return d.toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" });
}

// ---------------------------------------------------------------- Cycle

function renderCycle(container) {
  const prediction = store.predictNextCycle();

  container.appendChild(el(`
    <div>
      <h1>Tracking</h1>
      <p class="subtitle">Anyone who gets periods can log that directly below — everyone else can pick whichever of the other trackers fit. This is a rolling average of your own logged data, not a clinical prediction.</p>
    </div>
  `));

  if (prediction) {
    const guidance = PHASE_GUIDANCE[prediction.phase];
    container.appendChild(el(`
      <div class="card soft" style="border-left:5px solid ${guidance ? guidance.color : "var(--border)"}">
        <div class="bold mb-0">Day ${prediction.currentCycleDay} of your cycle — ${prediction.phase}</div>
        <p class="small mt-8" style="margin-bottom:4px">
          ${prediction.isConfident ? "Estimated" : "Early estimate (log a couple more cycles for a steadier average)"}
          next start around <span class="bold">${formatDateKey(prediction.predictedNextStart)}</span>,
          based on an average ${prediction.averageCycleLengthDays}-day cycle from your own logs.
          Estimated fertile window: day ${prediction.fertileWindowStartDay}–${prediction.fertileWindowEndDay}.
        </p>
        <p class="small muted" style="margin-bottom:0">
          Assumes a standard ~14-day luteal phase (real range ~11-17 days per person) — only basal body
          temperature, LH strips, or an ultrasound can actually confirm ovulation. Not a diagnosis or a
          contraception method.
        </p>
        ${guidance ? `
          <div class="mt-8" style="padding-top:10px;border-top:1px solid var(--border)">
            <span class="small bold" style="color:${guidance.color}">WHAT PEOPLE COMMONLY DO IN THIS PHASE</span>
            <p class="small mt-8" style="margin-bottom:0">${escapeHtml(guidance.text)}</p>
          </div>
        ` : ""}
      </div>
    `));
  } else {
    container.appendChild(el(`<p class="muted">Log at least two period starts to see a rolling-average estimate here.</p>`));
  }

  const periodCard = el(`
    <div class="card">
      <div class="bold mb-0">Period</div>
      <p class="small muted mt-8" style="margin-bottom:8px">For anyone who gets periods — logging this is what powers the cycle estimate above.</p>
      <label class="field">
        <input type="checkbox" id="period-start" style="width:auto;margin-right:6px" />
        Period started today
      </label>
      <div class="mt-8"><button class="primary" id="period-save">Save</button></div>
    </div>
  `);
  container.appendChild(periodCard);
  periodCard.querySelector("#period-save").onclick = () => {
    if (periodCard.querySelector("#period-start").checked) store.logCycle("MENSTRUAL", true, null, null);
    render();
  };

  const trackersCard = el(`
    <div class="card">
      <div class="bold mb-0">Trackers</div>
      <p class="small muted mt-8" style="margin-bottom:8px">Pick whichever you want to log today — none of these are required, and logging one doesn't require the others.</p>
    </div>
  `);
  container.appendChild(trackersCard);
  const todayEntry = store.recentCycleLogs(1).find((c) => c.date === store.todayKey());
  TRACKER_TYPES.forEach((t) => {
    const current = todayEntry && todayEntry.trackers ? todayEntry.trackers[t.id] : null;
    const row = el(`
      <div class="mt-8">
        <p class="field" id="tracker-${t.id}-label">${escapeHtml(t.label)} (${escapeHtml(t.hint)})</p>
        <div class="chips" role="group" aria-labelledby="tracker-${t.id}-label"></div>
      </div>
    `);
    const chipsEl = row.querySelector(".chips");
    [1, 2, 3, 4, 5].forEach((lvl) => {
      const active = current === lvl;
      const chip = el(`<button type="button" class="chip${active ? " active" : ""}" aria-pressed="${active}" aria-label="${escapeHtml(t.label)} ${lvl} of 5">${lvl}</button>`);
      chip.onclick = () => {
        store.logTrackerValue(t.id, lvl);
        chipsEl.querySelectorAll(".chip").forEach((c) => { c.classList.remove("active"); c.setAttribute("aria-pressed", "false"); });
        chip.classList.add("active");
        chip.setAttribute("aria-pressed", "true");
      };
      chipsEl.appendChild(chip);
    });
    trackersCard.appendChild(row);
  });

  const pastCard = el(`
    <div class="card">
      <div class="bold mb-0">Log a different date as period start</div>
      <p class="small muted mt-8">Forgot to log it on the day, or want to add past cycles to build a steadier average faster.</p>
      <div class="row gap-8 mt-8">
        <input type="date" id="past-period-date" style="flex:1" max="${new Date().toISOString().slice(0, 10)}" />
        <button class="primary" id="past-period-save">Save</button>
      </div>
    </div>
  `);
  container.appendChild(pastCard);
  pastCard.querySelector("#past-period-save").onclick = () => {
    const val = pastCard.querySelector("#past-period-date").value;
    if (!val) return;
    const dateKey = val.replace(/-/g, "");
    store.logCycle("MENSTRUAL", true, null, null, dateKey);
    render();
  };

  container.appendChild(el(`
    <p class="small muted" style="padding:0 2px">
      No watch-sync option here — pulling cycle data from a watch needs the device's own health
      platform (Android's Health Connect, Apple Health), which only a native app can access, not a
      website in any browser. That's available in the Android app's Cycle screen.
    </p>
  `));

  const recent = store.recentCycleLogs(14);
  container.appendChild(el(`<div class="roadmap-section-title">Last 14 days</div>`));
  if (recent.length === 0) container.appendChild(el(`<p class="muted">No logs yet.</p>`));
  recent.forEach((c) => {
    container.appendChild(el(`
      <div class="card">
        <div class="row mb-0">
          <span class="small muted">${formatDateKey(c.date)}</span>
          <span class="small">${c.periodStart ? "🔴 Period start" : c.energyLevel ? `Energy: ${c.energyLevel}/5` : ""}</span>
        </div>
      </div>
    `));
  });
}

// ---------------------------------------------------------------- Tasks

function renderTasks(container) {
  const tasks = store.listTasks();
  const reflection = store.weeklyReflection();

  container.appendChild(el(`
    <div>
      <h1>To-do</h1>
      <p class="subtitle">Add tasks, check them off, and get an honest reason when one slips.</p>
    </div>
  `));

  const addCard = el(`
    <div class="card">
      <input type="text" id="task-title" placeholder="What needs doing?" />
      <label class="field" for="task-when">Schedule it (optional — leave blank for a plain to-do with no time)</label>
      <input type="datetime-local" id="task-when" />
      <div class="mt-8"><button class="primary" id="task-add">Add task</button></div>
    </div>
  `);
  container.appendChild(addCard);
  addCard.querySelector("#task-add").onclick = () => {
    const input = addCard.querySelector("#task-title");
    const whenInput = addCard.querySelector("#task-when");
    const title = input.value.trim();
    if (!title) return;
    const scheduledAt = whenInput.value ? new Date(whenInput.value).getTime() : Date.now();
    store.addTask(title, "ADMIN", scheduledAt, null);
    render();
  };

  const open = tasks.filter((t) => !t.completedAt);
  const done = tasks.filter((t) => t.completedAt);

  container.appendChild(el(`<div class="roadmap-section-title">Open</div>`));
  if (open.length === 0) container.appendChild(el(`<p class="muted">Nothing open — nice.</p>`));
  open.forEach((t) => container.appendChild(renderTaskRow(t)));

  if (done.length > 0) {
    container.appendChild(el(`<div class="roadmap-section-title">Done</div>`));
    done.forEach((t) => container.appendChild(renderTaskRow(t)));
    container.appendChild(renderRewardCard("You've checked something off today"));
  }

  container.appendChild(el(`
    <div class="card soft mt-8">
      <div class="bold mb-0">This week</div>
      <p class="small mt-8" style="margin-bottom:4px">${reflection.completed} completed, ${reflection.missed} missed.</p>
      ${reflection.suggestion ? `<p class="small" style="margin-bottom:0">${escapeHtml(reflection.suggestion)}</p>` : ""}
    </div>
  `));
}

function renderTaskRow(t) {
  const isScheduled = t.scheduledAt > Date.now();
  const row = el(`
    <div class="card task-item" style="border-radius:14px;border:1px solid var(--border)">
      <div class="row">
        <div class="row gap-8" style="flex:1">
          <input type="checkbox" ${t.completedAt ? "checked" : ""} style="width:auto" />
          <span class="task-title${t.completedAt ? " done" : ""}">${escapeHtml(t.title)}</span>
        </div>
        <button class="text" style="color:var(--danger)">Delete</button>
      </div>
      ${isScheduled ? `
        <div class="row mt-8" style="flex-wrap:wrap;gap:8px">
          <span class="small muted">${new Date(t.scheduledAt).toLocaleString(undefined, { weekday: "short", month: "short", day: "numeric", hour: "numeric", minute: "2-digit" })}</span>
          <div class="row gap-8" style="width:auto">
            <a class="small" href="${googleCalendarUrl(t.title, t.scheduledAt)}" target="_blank" rel="noopener">Google Calendar</a>
            <button class="text small mb-0 ics-btn">Download .ics</button>
          </div>
        </div>
      ` : ""}
      <div class="miss-reasons" style="display:none;margin-top:10px"></div>
    </div>
  `);
  if (isScheduled) {
    row.querySelector(".ics-btn").onclick = () => {
      downloadIcs(`focuslens-${t.id}.ics`, buildIcs(t.title, t.scheduledAt));
    };
  }
  const checkbox = row.querySelector('input[type="checkbox"]');
  const missBox = row.querySelector(".miss-reasons");

  checkbox.onchange = () => {
    if (checkbox.checked) {
      store.markComplete(t.id);
      render();
    } else {
      missBox.style.display = "block";
      missBox.innerHTML = "";
      const label = el(`<p class="small muted mb-0">What got in the way?</p>`);
      missBox.appendChild(label);
      const chips = el(`<div class="chips mt-8"></div>`);
      MISS_REASONS.forEach((r) => {
        const chip = el(`<button type="button" class="chip">${r.label}</button>`);
        chip.onclick = () => {
          store.markIncomplete(t.id, r.id, null);
          render();
        };
        chips.appendChild(chip);
      });
      missBox.appendChild(chips);
    }
  };

  row.querySelector("button.text").onclick = () => {
    store.deleteTask(t.id);
    render();
  };

  return row;
}

// ---------------------------------------------------------------- Mental health

function renderMentalHealth(container) {
  container.appendChild(el(`
    <div>
      <h1>Mental health</h1>
      <p class="subtitle">A 14-day mood/energy trend built from your own Journal and Cycle entries — a mirror on your own pattern, not a diagnosis.</p>
    </div>
  `));

  const days = [];
  let cursor = store.todayKey();
  for (let i = 0; i < 14; i++) {
    days.unshift(cursor);
    cursor = store.addDaysToKey(cursor, -1);
  }

  const journal = store.recentJournal(60);
  const cycle = store.recentCycleLogs(60);

  const scores = days.map((date) => {
    const j = journal.find((e) => e.date === date);
    const c = cycle.find((e) => e.date === date);
    const moodScore = j && j.mood ? { low: 1, flat: 2, okay: 3, good: 4, great: 5 }[j.mood] : null;
    const energyScore = c && c.energyLevel ? c.energyLevel : null;
    if (moodScore && energyScore) return (moodScore + energyScore) / 2;
    return moodScore || energyScore || null;
  });

  const have = scores.filter((s) => s != null);
  let avgScore = null;
  if (have.length === 0) {
    container.appendChild(el(`<p class="muted">Log a journal mood or an energy level on the Journal/Cycle pages to see a trend here.</p>`));
  } else {
    avgScore = have.reduce((a, b) => a + b, 0) / have.length;
    container.appendChild(el(`<div class="card soft">14-day average: <span class="bold">${avgScore.toFixed(1)} / 5</span></div>`));

    const bars = el(`<div class="trend-bars"></div>`);
    scores.forEach((s) => {
      const height = s ? Math.round((s / 5) * 90) : 4;
      bars.appendChild(el(`<div class="trend-bar${s ? "" : " empty"}" style="height:${height}px"></div>`));
    });
    container.appendChild(bars);
    container.appendChild(el(`<p class="small muted">Oldest → newest, left to right. Blank bars are days with no log.</p>`));
  }

  renderSuggestionCard(container, avgScore);
}

/**
 * Picks one Learn topic to surface based on a few plain, inspectable rules — not AI, not a
 * diagnosis, just pointing at what your own recent data suggests might help: a low mood
 * average points at Mental Health resources, "tired" as your top missed-task reason points
 * at Sleep, "distracted" points at Focus & Productivity. Falls back to a general pick.
 */
function renderSuggestionCard(container, avgMoodScore) {
  const reflection = store.weeklyReflection();
  let topic = null;
  let why = "";

  if (avgMoodScore != null && avgMoodScore < 2.5) {
    topic = "MENTAL_HEALTH";
    why = "your mood/energy trend has been low lately";
  } else if (reflection.topMissReason === "TIRED") {
    topic = "SLEEP";
    why = "\"tired\" has been your top reason for missed tasks this week";
  } else if (reflection.topMissReason === "DISTRACTED") {
    topic = "FOCUS_PRODUCTIVITY";
    why = "\"distracted\" has been your top reason for missed tasks this week";
  }

  const pool = topic ? LEARN_ITEMS.filter((i) => i.topic === topic) : LEARN_ITEMS;
  const unread = pool.filter((i) => !store.isLearnRead(i.id));
  const item = (unread.length > 0 ? unread : pool)[Math.floor(Math.random() * (unread.length > 0 ? unread.length : pool.length))];
  if (!item) return;

  container.appendChild(el(`
    <div class="card" style="border-color:var(--accent)">
      <div class="small bold" style="color:var(--accent)">SUGGESTED FOR YOU</div>
      <p class="small muted mt-8" style="margin-bottom:8px">${why ? `Based on simple rules (not AI) — ${why}.` : "A pick from Learn, based on simple rules — not AI, not a diagnosis."}</p>
      <a href="${item.url}" target="_blank" rel="noopener" style="text-decoration:none;color:inherit">
        <div class="bold">${escapeHtml(item.title)}</div>
        <div class="small muted">${escapeHtml(item.source)}</div>
      </a>
    </div>
  `));
}

// ---------------------------------------------------------------- Learn

function renderLearn(container) {
  let topic = "ALL";

  function draw() {
    container.innerHTML = "";
    const readCount = store.learnReadCount(LEARN_ITEMS.map((i) => i.id));
    container.appendChild(el(`
      <div>
        <h1>Learn</h1>
        <p class="subtitle">Newsletters, articles and research hubs — a few honest starting points, not a feed. ${readCount} of ${LEARN_ITEMS.length} marked read.</p>
      </div>
    `));

    const chips = el(`<div class="chips"></div>`);
    LEARN_TOPICS.forEach((t) => {
      const chip = el(`<button type="button" class="chip${t.id === topic ? " active" : ""}" aria-pressed="${t.id === topic}">${t.label}</button>`);
      chip.onclick = () => { topic = t.id; draw(); };
      chips.appendChild(chip);
    });
    container.appendChild(chips);

    const visible = topic === "ALL" ? LEARN_ITEMS : LEARN_ITEMS.filter((i) => i.topic === topic);
    visible.forEach((item) => {
      const isRead = store.isLearnRead(item.id);
      const card = el(`
        <div class="card">
          <div class="row">
            <a href="${item.url}" target="_blank" rel="noopener" style="flex:1;text-decoration:none;color:inherit">
              <div class="bold" style="font-size:15px">${escapeHtml(item.title)}</div>
              <div class="small muted mt-8">${escapeHtml(item.source)} · ${escapeHtml(item.kind)}</div>
            </a>
            ${isRead ? '<span class="small" style="color:var(--accent);font-weight:600">✓ Read</span>' : ""}
          </div>
          ${isRead ? "" : '<div class="mt-8"><button class="outline mark-read">Mark as read</button></div>'}
        </div>
      `);
      if (!isRead) {
        card.querySelector(".mark-read").onclick = () => openReflection(item, draw);
      }
      container.appendChild(card);
    });
  }

  draw();
}

function openReflection(item, onDone) {
  const prompts = REFLECTION_PROMPTS.slice(0, item.complexity);
  const overlay = el(`
    <div style="position:fixed;inset:0;background:rgba(0,0,0,0.5);display:flex;align-items:center;justify-content:center;padding:20px;z-index:50">
      <div class="card" style="max-width:420px;width:100%;max-height:85vh;overflow:auto">
        <div class="bold">Before you mark "${escapeHtml(item.title)}" read</div>
        <p class="small muted mt-8">Quick self-check, not graded — just enough to make sure it wasn't a blind tap. Discarded immediately, never stored.</p>
        <div id="prompt-fields"></div>
        <div class="row mt-8">
          <button class="text" id="reflection-cancel">Not yet</button>
          <button class="primary" id="reflection-confirm" disabled>Mark as read</button>
        </div>
      </div>
    </div>
  `);
  const fields = overlay.querySelector("#prompt-fields");
  const inputs = prompts.map((p, i) => {
    const id = `reflection-${i}`;
    const wrap = el(`<div><label class="field" for="${id}">${escapeHtml(p)}</label><textarea id="${id}" placeholder="A sentence is enough"></textarea></div>`);
    fields.appendChild(wrap);
    return wrap.querySelector("textarea");
  });
  const confirmBtn = overlay.querySelector("#reflection-confirm");
  function checkValid() {
    confirmBtn.disabled = !inputs.every((i) => i.value.trim().length >= 3);
  }
  inputs.forEach((i) => i.addEventListener("input", checkValid));

  overlay.querySelector("#reflection-cancel").onclick = () => overlay.remove();
  confirmBtn.onclick = () => {
    store.markLearnRead(item.id);
    overlay.remove();
    onDone();
  };
  document.body.appendChild(overlay);
}

// ---------------------------------------------------------------- Explore

function renderExplore(container) {
  let genre = "ALL";

  function draw() {
    container.innerHTML = "";
    container.appendChild(el(`
      <div>
        <h1>Explore</h1>
        <p class="subtitle">Books, movies and series worth your time — a short curated starter list, not a live catalog.</p>
      </div>
    `));

    const chips = el(`<div class="chips"></div>`);
    MEDIA_GENRES.forEach((g) => {
      const chip = el(`<button type="button" class="chip${g.id === genre ? " active" : ""}" aria-pressed="${g.id === genre}">${g.label}</button>`);
      chip.onclick = () => { genre = g.id; draw(); };
      chips.appendChild(chip);
    });
    container.appendChild(chips);

    const visible = genre === "ALL" ? MEDIA_ITEMS : MEDIA_ITEMS.filter((m) => m.genres.includes(genre));
    MEDIA_KINDS.forEach((kind) => {
      const items = visible.filter((m) => m.kind === kind.id);
      if (items.length === 0) return;
      container.appendChild(el(`<div class="roadmap-section-title">${kind.label}</div>`));
      items.forEach((m) => {
        container.appendChild(el(`
          <div class="card">
            <div class="bold" style="font-size:15px">${escapeHtml(m.title)} (${m.y})</div>
            <div class="small muted">${escapeHtml(m.creator)}</div>
            <p class="small mt-8" style="margin-bottom:0">${escapeHtml(m.blurb)}</p>
          </div>
        `));
      });
    });
  }

  draw();
}

// ---------------------------------------------------------------- Birthdays

function formatMmdd(mmdd) {
  const names = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
  const month = parseInt(mmdd.slice(0, 2), 10);
  const day = parseInt(mmdd.slice(2, 4), 10);
  return `${names[month - 1]} ${day}`;
}

function renderBirthdays(container) {
  container.appendChild(el(`
    <div>
      <h1>Birthdays</h1>
      <p class="subtitle">Upcoming birthdays, and a checklist of who's wished you on yours.</p>
    </div>
  `));

  container.appendChild(el(`
    <div class="card soft">
      <p class="small" style="margin-bottom:0">
        No contacts import here — reading a Google account's contacts from a browser needs a Google sign-in
        consent flow this app hasn't built yet (and it would mean clicking through an "unverified app" warning
        until that's formally reviewed by Google). The Android app's Birthdays tab can import real contacts today,
        since it reads the phone's own Contacts app directly. Add people by hand here for now.
      </p>
    </div>
  `));

  const addCard = el(`
    <div class="card">
      <div class="bold">Add someone manually</div>
      <label class="field" for="person-name">Name</label>
      <input type="text" id="person-name" placeholder="Name" />
      <label class="field" for="person-bday">Birthday (optional)</label>
      <input type="date" id="person-bday" />
      <div class="mt-8"><button class="primary" id="person-add">Add</button></div>
    </div>
  `);
  container.appendChild(addCard);
  addCard.querySelector("#person-add").onclick = () => {
    const name = addCard.querySelector("#person-name").value.trim();
    if (!name) return;
    const bdayVal = addCard.querySelector("#person-bday").value;
    const mmdd = bdayVal ? bdayVal.slice(5, 7) + bdayVal.slice(8, 10) : null;
    store.addPerson(name, mmdd);
    render();
  };

  container.appendChild(el(`<div class="roadmap-section-title">Upcoming birthdays</div>`));
  const upcoming = store.upcomingBirthdays();
  if (upcoming.length === 0) {
    container.appendChild(el(`<p class="muted">None added yet.</p>`));
  } else {
    upcoming.forEach((p) => {
      const when = p.daysUntil === 0 ? "Today!" : p.daysUntil === 1 ? "Tomorrow" : `In ${p.daysUntil} days (${formatMmdd(p.birthday)})`;
      container.appendChild(el(`
        <div class="row" style="justify-content:space-between;padding:6px 2px">
          <span>${escapeHtml(p.name)}</span>
          <span class="small muted">${when}</span>
        </div>
      `));
    });
  }

  const currentYear = new Date().getFullYear();
  container.appendChild(el(`<div class="roadmap-section-title">${currentYear} birthday wishes</div>`));
  container.appendChild(el(`
    <p class="small muted">
      Tick off who's wished you this year so you don't lose track of who to thank or wish back — nothing here
      reads your messages automatically, you check these off yourself.
    </p>
  `));
  const people = store.listPeople();
  if (people.length === 0) {
    container.appendChild(el(`<p class="muted">Add people above to start a checklist.</p>`));
  } else {
    const wished = store.wishedThisYear(currentYear);
    people.forEach((p) => {
      const row = el(`
        <label class="field" style="display:flex;align-items:center;gap:8px;font-weight:400">
          <input type="checkbox" style="width:auto" ${wished.has(p.id) ? "checked" : ""} />
          ${escapeHtml(p.name)}
        </label>
      `);
      row.querySelector("input").onchange = (e) => {
        store.setBirthdayWish(p.id, currentYear, e.target.checked);
      };
      container.appendChild(row);
    });
  }
}

// ---------------------------------------------------------------- Reward suggestions

// ---------------------------------------------------------------- Account

// Shared by the Account page and the header's sign-in popup, so there's one real
// implementation of the form instead of two copies that could drift apart.
function renderAuthForm(container, onSuccess) {
  let mode = "signin";
  function draw() {
    container.innerHTML = "";

    const googleBtn = el(`<button type="button" class="outline" style="width:100%">Continue with Google</button>`);
    googleBtn.onclick = async () => {
      try {
        await signInWithGoogle();
      } catch (e) {
        alert(authErrorMessage(e));
      }
    };
    container.appendChild(googleBtn);
    if (googleRedirectError) {
      container.appendChild(el(`<p class="small mt-8" style="color:var(--danger)">${escapeHtml(googleRedirectError)}</p>`));
      googleRedirectError = null;
    }
    container.appendChild(el(`
      <div class="small mt-8" style="display:flex;align-items:center;gap:8px;color:var(--text-muted)">
        <span style="flex:1;height:1px;background:currentColor;opacity:0.3"></span>
        or use email
        <span style="flex:1;height:1px;background:currentColor;opacity:0.3"></span>
      </div>
    `));

    const tabs = el(`<div class="chips mt-8"></div>`);
    ["signin", "signup"].forEach((m) => {
      const chip = el(`<button type="button" class="chip${mode === m ? " active" : ""}" aria-pressed="${mode === m}">${m === "signin" ? "Sign in" : "Sign up"}</button>`);
      chip.onclick = () => { mode = m; draw(); };
      tabs.appendChild(chip);
    });
    container.appendChild(tabs);

    const card = el(`
      <div class="card">
        ${mode === "signup" ? '<label class="field" for="acct-name">Display name</label><input type="text" id="acct-name" placeholder="What should we call you?" />' : ""}
        <label class="field" for="acct-email">Email</label>
        <input type="email" id="acct-email" placeholder="you@example.com" />
        <label class="field" for="acct-password">Password</label>
        <input type="password" id="acct-password" placeholder="${mode === "signup" ? "At least 6 characters" : "Your password"}" />
        <p class="small" style="color:var(--danger);display:none" id="acct-error"></p>
        <div class="mt-8"><button class="primary" id="acct-submit">${mode === "signup" ? "Create account" : "Sign in"}</button></div>
      </div>
    `);
    container.appendChild(card);

    const emailInput = card.querySelector("#acct-email");
    const passInput = card.querySelector("#acct-password");
    const errorEl = card.querySelector("#acct-error");

    card.querySelector("#acct-submit").onclick = async () => {
      const email = emailInput.value.trim();
      const password = passInput.value;
      errorEl.style.display = "none";
      try {
        if (mode === "signup") {
          const name = card.querySelector("#acct-name").value.trim() || email.split("@")[0];
          await signUp(email, password, name);
        } else {
          await signIn(email, password);
        }
        onSuccess();
      } catch (e) {
        errorEl.textContent = authErrorMessage(e);
        errorEl.style.display = "block";
      }
    };
  }
  draw();
}

function renderAccount(container) {
  if (currentUser) {
    container.appendChild(el(`
      <div>
        <h1>Account</h1>
        <p class="subtitle">Signed in as <span class="bold">${escapeHtml(currentUser.displayName || currentUser.email)}</span>.</p>
      </div>
    `));
    container.appendChild(el(`
      <div class="card soft">
        <p class="small" style="margin-bottom:0">Community posts, achievement sharing and accountability partners aren't wired up yet — this account will unlock them once they're built. Your Journal/Cycle/To-do data on this page stays in this browser for now, not yet synced to this account.</p>
      </div>
    `));
    const out = el(`<button class="outline mt-8">Sign out</button>`);
    out.onclick = async () => { await signOutUser(); render(); };
    container.appendChild(out);
    return;
  }

  container.appendChild(el(`
    <div>
      <h1>Account</h1>
      <p class="subtitle">Sign in so your data can eventually follow you across devices, and so you can use community features once they're live.</p>
    </div>
  `));
  const formContainer = el(`<div></div>`);
  container.appendChild(formContainer);
  renderAuthForm(formContainer, () => render());
}

function renderRewardCard(title) {
  let idea = REWARD_IDEAS[Math.floor(Math.random() * REWARD_IDEAS.length)];
  const card = el(`
    <div class="card soft">
      <div class="small bold" style="color:var(--primary)">${escapeHtml(title)}</div>
      <div class="small reward-category mt-8" style="color:var(--accent);font-weight:700;text-transform:uppercase;letter-spacing:0.03em"></div>
      <p class="bold reward-text mt-8" style="margin-bottom:8px"></p>
      <button class="text mb-0" id="reward-shuffle">Something else</button>
    </div>
  `);
  const categoryEl = card.querySelector(".reward-category");
  const textEl = card.querySelector(".reward-text");
  function paint() {
    categoryEl.textContent = idea.category;
    textEl.textContent = idea.text;
  }
  paint();
  card.querySelector("#reward-shuffle").onclick = () => {
    const others = REWARD_IDEAS.filter((r) => r !== idea);
    idea = others[Math.floor(Math.random() * others.length)];
    paint();
  };
  return card;
}
