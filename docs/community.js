// Community chat + accountability partners. Needs a signed-in account and the Firestore rules
// in firestore.rules. Live data arrives through onSnapshot listeners, which are all tracked in
// `listeners` and torn down by stopCommunity() whenever the page changes.
import {
  ensureProfile, renameProfile, findProfileByCode,
  watchPosts, createPost, deletePost, listLikes, setLike, listComments, addComment, deleteComment, reportPost,
  watchPacts, requestPact, setPactStatus, watchPactTasks, addPactTask, updatePactTask, deletePactTask,
} from "./firebase.js";

const listeners = [];
// Bumped on every page change. Rendering here awaits the network, so each render remembers the
// token it started with and quietly stops if the user has already moved to another page.
let token = 0;
const taskCache = new Map(); // pactId -> last tasks snapshot, so a repaint doesn't blank the list
export function stopCommunity() {
  token++;
  taskCache.clear();
  while (listeners.length) {
    try { listeners.pop()(); } catch (e) {}
  }
}

function el(html) {
  const t = document.createElement("template");
  t.innerHTML = html.trim();
  return t.content.firstElementChild;
}
function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}
function fmtWhen(ts) {
  const d = ts && ts.toDate ? ts.toDate() : new Date();
  return d.toLocaleString(undefined, { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" });
}
function friendlyError(e) {
  console.error("FocusLens community error:", e);
  const code = e && e.code ? e.code : "";
  if (code.includes("permission-denied")) return "Firestore refused this. The community rules (firestore.rules) may not be published to your Firebase project yet.";
  if (code.includes("unavailable") || e instanceof TypeError) return "Couldn't reach Google's servers. Check your connection and try again.";
  return "Something went wrong. Please try again.";
}

function needSignIn(container, ctx, what) {
  container.appendChild(el(`
    <div class="card">
      <div class="bold">Sign in to use ${what}</div>
      <p class="small muted">${what[0].toUpperCase() + what.slice(1)} uses your FocusLens account, so your name and posts stay linked to you across devices.</p>
      <button class="primary" id="cc-signin">Sign in</button>
    </div>`));
  container.querySelector("#cc-signin").onclick = ctx.openSignIn;
}

// ---------------------------------------------------------------- Community chat

const ROOMS = [
  { id: "general", label: "General", blurb: "Wins, questions, tips, encouragement." },
  { id: "focus", label: "Quiet focus room", blurb: "Say what you're working on, then get on with it. Check back after your block." },
];

export async function renderCommunity(container, ctx) {
  stopCommunity();
  const my = token;
  container.appendChild(el(`
    <div>
      <h1>Community</h1>
      <p class="subtitle">A shared space for FocusLens users to post and talk.</p>
    </div>`));
  if (!ctx.user) { needSignIn(container, ctx, "the community"); return; }

  container.appendChild(el(`
    <div class="card soft">
      <p class="small" style="margin:0"><span class="bold">Be kind, keep it private.</span> Posts are visible to every signed-in FocusLens user, with your display name only (never your email). Don't share phone numbers, addresses or anything you wouldn't say in public. Peers aren't professionals: if you're struggling, see <a href="#help">Help</a>. Use Report on anything harmful.</p>
    </div>`));

  let profile;
  try { profile = await ensureProfile(ctx.user); }
  catch (e) { if (my === token) container.appendChild(el(`<p class="muted">${escapeHtml(friendlyError(e))}</p>`)); return; }
  if (my !== token) return;

  const me = el(`
    <div class="card row">
      <div class="small">Posting as <span class="bold" id="cc-name">${escapeHtml(profile.displayName)}</span></div>
      <button class="text" id="cc-rename">Change name</button>
    </div>`);
  me.querySelector("#cc-rename").onclick = async () => {
    const v = prompt("Display name (shown on your posts):", profile.displayName);
    if (v == null) return;
    const name = v.trim().slice(0, 40);
    if (!name) return;
    try {
      await renameProfile(ctx.user.uid, name);
      profile.displayName = name;
      me.querySelector("#cc-name").textContent = name;
    } catch (e) { alert(friendlyError(e)); }
  };
  container.appendChild(me);

  let room = "general";
  let lastPosts = [];
  const roomChips = el(`<div class="chips"></div>`);
  const blurb = el(`<p class="small muted"></p>`);
  const composer = el(`
    <div class="card">
      <label class="field" for="cc-text">Write a post</label>
      <textarea id="cc-text" maxlength="500" placeholder="What's on your mind?"></textarea>
      <div class="row mt-8"><span class="small muted" id="cc-count">0 / 500</span><button class="primary" id="cc-send">Post</button></div>
      <p class="small" id="cc-err" style="color:var(--danger);margin:6px 0 0" role="alert"></p>
    </div>`);
  const feed = el(`<div></div>`);
  container.append(roomChips, blurb, composer, feed);

  const paintChips = () => {
    roomChips.innerHTML = "";
    ROOMS.forEach((r) => {
      const chip = el(`<button type="button" class="chip${r.id === room ? " active" : ""}">${r.label}</button>`);
      chip.onclick = () => { room = r.id; paintChips(); paintFeed(); };
      roomChips.appendChild(chip);
    });
    blurb.textContent = ROOMS.find((r) => r.id === room).blurb;
  };

  const ta = composer.querySelector("#cc-text");
  ta.oninput = () => { composer.querySelector("#cc-count").textContent = `${ta.value.length} / 500`; };
  let lastPostAt = 0;
  composer.querySelector("#cc-send").onclick = async () => {
    const text = ta.value.trim();
    const err = composer.querySelector("#cc-err");
    err.textContent = "";
    if (!text) return;
    // Courtesy throttle in the browser only; Firestore rules can't rate-limit, so a determined
    // user could still bypass this.
    if (Date.now() - lastPostAt < 5000) { err.textContent = "Slow down a little — wait a few seconds between posts."; return; }
    try {
      await createPost(ctx.user, profile.displayName, text, room);
      lastPostAt = Date.now();
      ta.value = "";
      composer.querySelector("#cc-count").textContent = "0 / 500";
    } catch (e) { err.textContent = friendlyError(e); }
  };

  const likesCache = new Map(); // postId -> array of uids; refreshed only for posts not seen yet
  const paintFeed = () => {
    feed.innerHTML = "";
    const posts = lastPosts.filter((p) => p.room === room);
    if (!posts.length) feed.appendChild(el(`<p class="muted">No posts here yet. Be the first.</p>`));
    posts.forEach((p) => feed.appendChild(renderPost(p)));
  };

  function renderPost(p) {
    const mine = p.authorUid === ctx.user.uid;
    const card = el(`
      <div class="card">
        <div class="row mb-0"><span class="bold">${escapeHtml(p.authorName)}</span><span class="small muted">${fmtWhen(p.createdAt)}</span></div>
        <p class="mt-8" style="white-space:pre-wrap;margin-bottom:8px">${escapeHtml(p.text)}</p>
        <div class="row" style="justify-content:flex-start;gap:14px">
          <button class="text" data-act="like"></button>
          <button class="text" data-act="comments">Comments</button>
          ${mine ? `<button class="text" data-act="delete">Delete</button>` : `<button class="text" data-act="report">Report</button>`}
        </div>
        <div data-box="comments" hidden></div>
      </div>`);
    const likeBtn = card.querySelector('[data-act="like"]');
    const paintLike = () => {
      const likes = likesCache.get(p.id) || [];
      const liked = likes.includes(ctx.user.uid);
      likeBtn.textContent = `${liked ? "♥" : "♡"} ${likes.length}`;
      likeBtn.setAttribute("aria-pressed", String(liked));
    };
    paintLike();
    if (!likesCache.has(p.id)) {
      likesCache.set(p.id, []);
      listLikes(p.id).then((ids) => { likesCache.set(p.id, ids); paintLike(); }).catch(() => {});
    }
    likeBtn.onclick = async () => {
      const likes = likesCache.get(p.id) || [];
      const liked = likes.includes(ctx.user.uid);
      try {
        await setLike(p.id, ctx.user.uid, !liked);
        likesCache.set(p.id, liked ? likes.filter((u) => u !== ctx.user.uid) : [...likes, ctx.user.uid]);
        paintLike();
      } catch (e) { alert(friendlyError(e)); }
    };

    const box = card.querySelector('[data-box="comments"]');
    card.querySelector('[data-act="comments"]').onclick = async () => {
      if (!box.hidden) { box.hidden = true; return; }
      box.hidden = false;
      await paintComments(p, box);
    };
    const del = card.querySelector('[data-act="delete"]');
    if (del) del.onclick = async () => {
      if (!confirm("Delete this post?")) return;
      try { await deletePost(p.id); } catch (e) { alert(friendlyError(e)); }
    };
    const rep = card.querySelector('[data-act="report"]');
    if (rep) rep.onclick = async () => {
      const reason = prompt("What's wrong with this post? (a few words)");
      if (!reason || !reason.trim()) return;
      try { await reportPost(ctx.user, p.id, reason.trim().slice(0, 200)); alert("Thanks — reported."); }
      catch (e) { alert(friendlyError(e)); }
    };
    return card;
  }

  async function paintComments(p, box) {
    box.innerHTML = `<p class="small muted">Loading…</p>`;
    let comments = [];
    try { comments = await listComments(p.id); } catch (e) { box.innerHTML = `<p class="small muted">${escapeHtml(friendlyError(e))}</p>`; return; }
    box.innerHTML = "";
    comments.forEach((c) => {
      const canDelete = c.authorUid === ctx.user.uid || p.authorUid === ctx.user.uid;
      const row = el(`<div class="task-item"><div class="small"><span class="bold">${escapeHtml(c.authorName)}</span> <span class="muted">${fmtWhen(c.createdAt)}</span></div><div class="small" style="white-space:pre-wrap">${escapeHtml(c.text)}</div>${canDelete ? `<button class="text small">Remove</button>` : ""}</div>`);
      const b = row.querySelector("button");
      if (b) b.onclick = async () => { try { await deleteComment(p.id, c.id); await paintComments(p, box); } catch (e) { alert(friendlyError(e)); } };
      box.appendChild(row);
    });
    const form = el(`<div class="mt-8"><input type="text" maxlength="300" placeholder="Add a comment" aria-label="Add a comment" /><div class="mt-8"><button class="outline">Comment</button></div></div>`);
    form.querySelector("button").onclick = async () => {
      const input = form.querySelector("input");
      const text = input.value.trim();
      if (!text) return;
      try { await addComment(p.id, ctx.user, profile.displayName, text); await paintComments(p, box); }
      catch (e) { alert(friendlyError(e)); }
    };
    box.appendChild(form);
  }

  paintChips();
  paintFeed();
  if (my !== token) return;
  listeners.push(watchPosts(
    (posts) => { lastPosts = posts; paintFeed(); },
    (e) => { feed.innerHTML = `<p class="muted">${escapeHtml(friendlyError(e))}</p>`; },
  ));
}

// ---------------------------------------------------------------- Accountability partners + dares

// Fixed list: a task stores only the NUMBER of the dare (Firestore rules require 0-11), so a
// partner can never write their own dare text. Keep the length at 12 to match the rules.
export const DARES = [
  "Do 20 jumping jacks, then tell your partner you did.",
  "Drink a full glass of water and take a 5-minute walk.",
  "Write down 3 things you're grateful for and send them.",
  "Tidy one surface (desk, counter) for 5 minutes.",
  "Message someone you've been meaning to get in touch with.",
  "Do a 2-minute wall-sit or plank.",
  "Prepare a healthy snack you've been putting off.",
  "Delete or archive 10 old files or photos.",
  "Spend 10 minutes outside with your phone left behind.",
  "Tell your partner one specific thing you appreciate about working with them.",
  "Do a 10-minute focus block on the task you missed.",
  "Stretch for 5 minutes, then note how you feel.",
];

export async function renderPartners(container, ctx) {
  stopCommunity();
  const my = token;
  container.appendChild(el(`
    <div>
      <h1>Accountability</h1>
      <p class="subtitle">Pick a friend to hold you accountable on tasks. If a task isn't done by its due time, a dare gets assigned.</p>
    </div>`));
  if (!ctx.user) { needSignIn(container, ctx, "accountability partners"); return; }

  let profile;
  try { profile = await ensureProfile(ctx.user); }
  catch (e) { if (my === token) container.appendChild(el(`<p class="muted">${escapeHtml(friendlyError(e))}</p>`)); return; }
  if (my !== token) return;

  container.appendChild(el(`
    <div class="card soft">
      <p class="small" style="margin:0"><span class="bold">How it works.</span> Share your invite code with one friend (or enter theirs below). They accept, and you each add tasks with a due time. Your partner can see only the tasks you add to the pact, never your other data. Missed tasks get a light, friendly dare to complete, nothing that costs money or embarrasses anyone. Either of you can end the pact at any time.</p>
    </div>`));

  const codeCard = el(`
    <div class="card">
      <div class="bold">Your invite code</div>
      <div style="font-size:26px;font-weight:700;letter-spacing:.12em;margin:6px 0;font-variant-numeric:tabular-nums" id="pt-code">${escapeHtml(profile.inviteCode)}</div>
      <button class="outline" id="pt-copy">Copy</button>
      <label class="field" for="pt-friend">Enter a friend's code</label>
      <input type="text" id="pt-friend" maxlength="8" placeholder="8-character code" autocapitalize="characters" />
      <div class="mt-8"><button class="primary" id="pt-request">Send pact request</button></div>
      <p class="small" id="pt-msg" style="margin:6px 0 0" role="status"></p>
    </div>`);
  codeCard.querySelector("#pt-copy").onclick = async () => {
    try { await navigator.clipboard.writeText(profile.inviteCode); codeCard.querySelector("#pt-msg").textContent = "Copied."; } catch (e) {}
  };
  codeCard.querySelector("#pt-request").onclick = async () => {
    const msg = codeCard.querySelector("#pt-msg");
    const code = codeCard.querySelector("#pt-friend").value.trim().toUpperCase();
    msg.style.color = "var(--danger)";
    if (code.length !== 8) { msg.textContent = "Codes are 8 characters."; return; }
    if (code === profile.inviteCode) { msg.textContent = "That's your own code."; return; }
    try {
      const partner = await findProfileByCode(code);
      if (!partner) { msg.textContent = "No one has that code."; return; }
      await requestPact(ctx.user, profile.displayName, partner);
      msg.style.color = "var(--text)";
      msg.textContent = `Request sent to ${partner.displayName}. They need to accept it.`;
      codeCard.querySelector("#pt-friend").value = "";
    } catch (e) { msg.textContent = friendlyError(e); }
  };
  container.appendChild(codeCard);

  const list = el(`<div></div>`);
  container.appendChild(list);
  const taskUnsubs = new Map();
  const paintPacts = (pacts) => {
    // Rebuild the pact list, keeping one live task listener per active pact.
    const activeIds = new Set(pacts.filter((p) => p.status === "active").map((p) => p.id));
    [...taskUnsubs.keys()].forEach((id) => { if (!activeIds.has(id)) { taskUnsubs.get(id)(); taskUnsubs.delete(id); } });
    list.innerHTML = "";
    const visible = pacts.filter((p) => p.status !== "ended");
    if (!visible.length) list.appendChild(el(`<p class="muted">No pacts yet. Share your code with a friend to start one.</p>`));
    visible.forEach((p) => {
      const partnerUid = p.memberUids.find((u) => u !== ctx.user.uid);
      const partnerName = (p.names && p.names[partnerUid]) || "Partner";
      const card = el(`<div class="card"><div class="row"><span class="bold">With ${escapeHtml(partnerName)}</span><span class="badge">${p.status}</span></div></div>`);
      if (p.status === "pending") {
        if (p.requesterUid === ctx.user.uid) {
          card.appendChild(el(`<p class="small muted">Waiting for ${escapeHtml(partnerName)} to accept.</p>`));
          const cancel = el(`<button class="text">Cancel request</button>`);
          cancel.onclick = () => setPactStatus(p.id, "ended").catch((e) => alert(friendlyError(e)));
          card.appendChild(cancel);
        } else {
          card.appendChild(el(`<p class="small">${escapeHtml(partnerName)} wants you to be accountability partners.</p>`));
          const acc = el(`<button class="primary">Accept</button>`);
          const dec = el(`<button class="text">Decline</button>`);
          acc.onclick = () => setPactStatus(p.id, "active").catch((e) => alert(friendlyError(e)));
          dec.onclick = () => setPactStatus(p.id, "ended").catch((e) => alert(friendlyError(e)));
          card.append(acc, dec);
        }
      } else {
        const box = el(`<div></div>`);
        card.appendChild(box);
        renderPactBody(box, p, partnerName, ctx, taskUnsubs);
        const end = el(`<button class="text" style="color:var(--danger)">End this pact</button>`);
        end.onclick = () => { if (confirm("End this pact? Neither of you will be able to add tasks.")) setPactStatus(p.id, "ended").catch((e) => alert(friendlyError(e))); };
        card.appendChild(end);
      }
      list.appendChild(card);
    });
  };
  if (my !== token) return;
  listeners.push(watchPacts(ctx.user.uid, paintPacts, (e) => { list.innerHTML = `<p class="muted">${escapeHtml(friendlyError(e))}</p>`; }));
  listeners.push(() => { taskUnsubs.forEach((u) => u()); taskUnsubs.clear(); });
}

function renderPactBody(box, pact, partnerName, ctx, taskUnsubs) {
  const form = el(`
    <div class="mt-8">
      <label class="field" for="pt-title-${pact.id}">I will…</label>
      <input type="text" id="pt-title-${pact.id}" maxlength="120" placeholder="Finish the report intro" />
      <label class="field" for="pt-due-${pact.id}">By</label>
      <input type="datetime-local" id="pt-due-${pact.id}" />
      <div class="mt-8"><button class="primary">Pledge it</button></div>
      <p class="small" style="color:var(--danger);margin:6px 0 0" role="alert"></p>
    </div>`);
  form.querySelector("button").onclick = async () => {
    const title = form.querySelector("input[type=text]").value.trim();
    const due = form.querySelector("input[type=datetime-local]").value;
    const err = form.querySelector("p");
    err.textContent = "";
    if (!title || !due) { err.textContent = "Add a task and a due time."; return; }
    const ms = new Date(due).getTime();
    if (!(ms > Date.now())) { err.textContent = "The due time has to be in the future."; return; }
    try { await addPactTask(pact.id, ctx.user.uid, title, ms); form.querySelector("input[type=text]").value = ""; }
    catch (e) { err.textContent = friendlyError(e); }
  };
  box.appendChild(form);
  const tasksBox = el(`<div class="mt-8"></div>`);
  box.appendChild(tasksBox);

  if (!taskUnsubs.has(pact.id)) {
    taskUnsubs.set(pact.id, watchPactTasks(pact.id, (tasks) => {
      taskCache.set(pact.id, tasks);
      const target = tasksBoxFor(pact.id);
      if (target) paintTasks(target, pact, partnerName, tasks, ctx);
    }, () => {}));
  }
  tasksBox.dataset.pact = pact.id;
  if (taskCache.has(pact.id)) paintTasks(tasksBox, pact, partnerName, taskCache.get(pact.id), ctx);
}

function tasksBoxFor(pactId) {
  return document.querySelector(`[data-pact="${pactId}"]`);
}

function paintTasks(box, pact, partnerName, tasks, ctx) {
  box.innerHTML = "";
  if (!tasks.length) { box.appendChild(el(`<p class="small muted">No tasks yet.</p>`)); return; }
  const fail = (e) => alert(friendlyError(e));
  tasks.forEach((t) => {
    const mine = t.ownerUid === ctx.user.uid;
    const who = mine ? "You" : partnerName;
    const overdue = t.status === "open" && Date.now() > t.dueAt;
    const due = new Date(t.dueAt).toLocaleString(undefined, { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" });
    let state = t.status === "done" ? (t.verified ? "Done · verified ✅" : "Done · awaiting verification") : t.status === "missed" ? "Missed" : overdue ? "Overdue" : "Open";
    const row = el(`<div class="task-item"><div class="bold">${escapeHtml(t.title)}</div><div class="small muted">${who} · due ${due} · ${state}</div></div>`);
    const actions = el(`<div class="chips mt-8" style="margin-bottom:0"></div>`);
    const btn = (label, fn) => { const b = el(`<button type="button" class="chip">${label}</button>`); b.onclick = () => fn().catch(fail); actions.appendChild(b); };

    if (t.status === "open") {
      if (mine) btn("Mark done", () => updatePactTask(pact.id, t.id, { status: "done" }));
      if (mine && !overdue) btn("Withdraw", () => deletePactTask(pact.id, t.id));
      if (overdue) btn("Mark missed + assign dare", () => updatePactTask(pact.id, t.id, { status: "missed", dareId: Math.floor(Math.random() * DARES.length), dareStatus: "assigned" }));
    }
    if (t.status === "done" && !t.verified && !mine) btn("Verify it's done", () => updatePactTask(pact.id, t.id, { verified: true }));
    if (actions.children.length) row.appendChild(actions);

    if (t.status === "missed" && typeof t.dareId === "number") {
      const dare = el(`<div class="card soft mt-8" style="margin-bottom:0"><div class="small bold">Dare: ${escapeHtml(DARES[t.dareId] || "")}</div></div>`);
      if (t.proof) dare.appendChild(el(`<div class="small mt-8">Proof: ${escapeHtml(t.proof)}</div>`));
      const ds = t.dareStatus;
      if (ds === "accepted") dare.appendChild(el(`<div class="small mt-8">Dare completed ✅</div>`));
      else if (mine && (ds === "assigned" || ds === "rejected")) {
        if (ds === "rejected") dare.appendChild(el(`<div class="small mt-8">${escapeHtml(partnerName)} asked for another try.</div>`));
        const input = el(`<input type="text" maxlength="300" placeholder="Tell your partner how you did it" aria-label="Dare proof" />`);
        const submit = el(`<button class="primary mt-8">Submit proof</button>`);
        submit.onclick = () => {
          const v = input.value.trim();
          if (v) updatePactTask(pact.id, t.id, { dareStatus: "proof_submitted", proof: v.slice(0, 300) }).catch(fail);
        };
        dare.append(input, submit);
      } else if (mine && ds === "proof_submitted") dare.appendChild(el(`<div class="small mt-8">Waiting for ${escapeHtml(partnerName)} to check your proof.</div>`));
      else if (!mine && ds === "proof_submitted") {
        const ok = el(`<button type="button" class="chip mt-8">Accept</button>`);
        const again = el(`<button type="button" class="chip mt-8">Ask for another try</button>`);
        ok.onclick = () => updatePactTask(pact.id, t.id, { dareStatus: "accepted" }).catch(fail);
        again.onclick = () => updatePactTask(pact.id, t.id, { dareStatus: "rejected" }).catch(fail);
        dare.append(ok, again);
      } else if (!mine) dare.appendChild(el(`<div class="small mt-8">Waiting for ${escapeHtml(partnerName)} to do it.</div>`));
      row.appendChild(dare);
    }
    box.appendChild(row);
  });
}
