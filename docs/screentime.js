// Screen time page. Shows only what the Android app has synced to the signed-in account
// (users/{uid}/data/usage_YYYYMMDD, opt-in). Nothing is simulated: no account or no synced
// docs means an honest empty state.
import { listUsageDocs } from "./firebase.js";
import { todayKey, addDaysToKey } from "./storage.js";

function el(html) {
  const t = document.createElement("template");
  t.innerHTML = html.trim();
  return t.content.firstElementChild;
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

function fmtMinutes(m) {
  m = Math.round(Number(m) || 0);
  const h = Math.floor(m / 60);
  return h ? `${h}h ${m % 60}m` : `${m}m`;
}

const DAY_NAMES = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
let token = 0;

function emptyState(container, why) {
  container.appendChild(el(`
    <div class="card">
      <div class="bold">${escapeHtml(why)}</div>
      <p class="small muted">Screen time is collected by the FocusLens Android app, because a website can't see what other apps you use. To see it here, open the Android app, sign in with this same account, and turn on <span class="bold">Sync screen time to my account</span>. It is off by default. Once it has synced, your last 7 days appear on this page.</p>
    </div>`));
}

export async function renderScreenTime(container, ctx) {
  const my = ++token;
  container.appendChild(el(`
    <div>
      <h1>Screen time</h1>
      <p class="subtitle">Your phone usage from the Android app, shown only if you've turned sync on there.</p>
    </div>`));

  if (!ctx.user) {
    emptyState(container, "Sign in to see your synced screen time");
    const b = el(`<button class="primary">Sign in</button>`);
    b.onclick = ctx.openSignIn;
    container.appendChild(b);
    return;
  }

  const status = el(`<p class="muted" role="status">Loading…</p>`);
  container.appendChild(status);
  let docs;
  try {
    docs = await listUsageDocs(ctx.user.uid);
  } catch (e) {
    if (my !== token) return;
    status.textContent = "Couldn't load screen time right now. Check your connection and try again.";
    return;
  }
  if (my !== token) return;
  status.remove();

  const byDay = new Map();
  docs.forEach((d) => byDay.set(d.id.slice(6), d));
  const today = todayKey();
  const days = [];
  for (let i = 6; i >= 0; i--) days.push(addDaysToKey(today, -i));
  const recent = days.filter((k) => byDay.has(k));
  if (!recent.length) { emptyState(container, "No synced screen time yet"); return; }

  const minutesOf = (k) => (byDay.has(k) ? Math.max(0, Number(byDay.get(k).totalMinutes) || 0) : null);
  const max = Math.max(1, ...recent.map(minutesOf));
  const avg = recent.reduce((s, k) => s + minutesOf(k), 0) / recent.length;

  const bars = days.map((k) => {
    const m = minutesOf(k);
    const dow = DAY_NAMES[new Date(+k.slice(0, 4), +k.slice(4, 6) - 1, +k.slice(6, 8)).getDay()];
    const h = m === null ? 4 : Math.max(4, Math.round((m / max) * 90));
    return `<div style="flex:1;text-align:center"><div style="height:90px;display:flex;align-items:flex-end"><div class="trend-bar${m === null ? " empty" : ""}" style="width:100%;height:${h}px" role="img" aria-label="${dow}: ${m === null ? "no data" : fmtMinutes(m)}"></div></div><div class="small muted">${dow}</div><div class="small">${m === null ? "–" : fmtMinutes(m)}</div></div>`;
  }).join("");
  container.appendChild(el(`
    <div class="card">
      <div class="bold">Last 7 days</div>
      <div style="display:flex;gap:4px;margin-top:10px">${bars}</div>
      <p class="small muted" style="margin:8px 0 0">Weekly average: <span class="bold">${fmtMinutes(avg)}</span> per day across the ${recent.length} day${recent.length === 1 ? "" : "s"} with synced data.</p>
    </div>`));

  const todayDoc = byDay.get(today);
  const top = todayDoc && todayDoc.perApp && typeof todayDoc.perApp === "object"
    ? Object.entries(todayDoc.perApp).map(([app, m]) => [app, Number(m) || 0]).filter(([, m]) => m > 0).sort((a, b) => b[1] - a[1]).slice(0, 8)
    : [];
  const card = el(`<div class="card"><div class="bold">Today's top apps</div></div>`);
  if (!top.length) {
    card.appendChild(el(`<p class="small muted" style="margin:6px 0 0">${todayDoc ? "No per-app data synced for today yet." : "Nothing synced for today yet. The Android app syncs periodically."}</p>`));
  } else {
    top.forEach(([app, m]) => {
      card.appendChild(el(`<div class="mt-8"><div class="row"><span class="small">${escapeHtml(app)}</span><span class="small muted">${fmtMinutes(m)}</span></div><div style="height:6px;border-radius:3px;background:var(--border);overflow:hidden"><div style="height:100%;width:${Math.round((m / top[0][1]) * 100)}%;background:var(--accent)"></div></div></div>`));
    });
  }
  const last = recent.map((k) => byDay.get(k)).reduce((a, d) => Math.max(a, Number(d.updatedAt) || 0), 0);
  const dev = byDay.get(recent[recent.length - 1]).device;
  const meta = [dev ? `from ${escapeHtml(dev)}` : "", last ? `last synced ${new Date(last).toLocaleString()}` : ""].filter(Boolean).join(", ");
  if (meta) card.appendChild(el(`<p class="small muted" style="margin:10px 0 0">${meta}</p>`));
  container.appendChild(card);
}
