// Prompt & template browser for the Journal page: categories of prompts, structured templates,
// research topics, and the user's own templates (add, use, delete).
import * as store from "./storage.js";
import { JOURNAL_CATEGORIES, JOURNAL_TEMPLATES, RESEARCH_TEMPLATE, RESEARCH_TOPICS } from "./content.js";

function el(html) {
  const t = document.createElement("template");
  t.innerHTML = html.trim();
  return t.content.firstElementChild;
}
const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

/**
 * @param {{ insertPrompt(text:string):void, insertTemplate(text:string):void, currentText():string }} io
 * @param {() => void} rerender
 */
export function renderPromptBrowser(io, rerender) {
  const card = el(`
    <div class="card soft">
      <div class="bold">Prompts &amp; templates</div>
      <p class="small muted" style="margin:6px 0 10px">Pick a topic, tap a question to start writing, or fill in a template. You can make your own templates too.</p>
      <div class="chips" id="pb-cats"></div>
      <div id="pb-body"></div>
    </div>`);
  const cats = card.querySelector("#pb-cats");
  const body = card.querySelector("#pb-body");
  const tabs = [
    ...JOURNAL_CATEGORIES.map((c) => ({ id: c.id, label: `${c.emoji} ${c.label}` })),
    { id: "templates", label: "🧩 Templates" },
    { id: "research", label: "🔎 Research" },
    { id: "mine", label: "⭐ My templates" },
  ];
  let active = "reflect";

  const paintTabs = () => {
    cats.innerHTML = "";
    tabs.forEach((t) => {
      const chip = el(`<button type="button" class="chip${t.id === active ? " active" : ""}" aria-pressed="${t.id === active}">${esc(t.label)}</button>`);
      chip.onclick = () => { active = t.id; paintTabs(); paintBody(); };
      cats.appendChild(chip);
    });
  };

  const promptButton = (text) => {
    const b = el(`<button type="button" class="chip" style="text-align:left;border-radius:14px;white-space:normal">${esc(text)}</button>`);
    b.onclick = () => io.insertPrompt(text);
    return b;
  };

  const paintBody = () => {
    body.innerHTML = "";
    const cat = JOURNAL_CATEGORIES.find((c) => c.id === active);
    if (cat) {
      const shuffle = el(`<button type="button" class="outline" style="margin-bottom:10px">Surprise me</button>`);
      shuffle.onclick = () => io.insertPrompt(cat.prompts[Math.floor(Math.random() * cat.prompts.length)]);
      body.appendChild(shuffle);
      const list = el(`<div class="chips" style="flex-direction:column;align-items:stretch"></div>`);
      cat.prompts.forEach((p) => list.appendChild(promptButton(p)));
      body.appendChild(list);
      return;
    }
    if (active === "templates") {
      const list = el(`<div class="chips"></div>`);
      JOURNAL_TEMPLATES.forEach((t) => {
        const b = el(`<button type="button" class="chip">${t.emoji} ${esc(t.title)}</button>`);
        b.onclick = () => io.insertTemplate(t.scaffold);
        list.appendChild(b);
      });
      body.appendChild(list);
      return;
    }
    if (active === "research") {
      body.appendChild(el(`<p class="small muted">Choose something you're curious about. Starter questions go into your entry; write what you find in your own words. Look for sources in <a href="#learn">Learn</a> or your library.</p>`));
      RESEARCH_TOPICS.forEach((r) => {
        const row = el(`<details class="task-item"><summary class="bold" style="cursor:pointer">${esc(r.topic)}</summary></details>`);
        r.questions.forEach((q) => row.appendChild(promptButton(q)));
        const start = el(`<button type="button" class="outline mt-8">Start a research entry on this</button>`);
        start.onclick = () => io.insertTemplate(`Topic: ${r.topic}\n\n${RESEARCH_TEMPLATE}`);
        row.appendChild(start);
        body.appendChild(row);
      });
      return;
    }
    // My templates
    const mine = store.listUserTemplates();
    if (!mine.length) body.appendChild(el(`<p class="muted">No templates of your own yet. Create one below, or save what you've written as a template.</p>`));
    mine.forEach((t) => {
      const row = el(`<div class="row task-item"><div><div class="bold">${esc(t.title)}</div><div class="small muted">${esc(t.scaffold.slice(0, 80))}${t.scaffold.length > 80 ? "…" : ""}</div></div><div style="display:flex;gap:8px"><button class="outline">Use</button><button class="text" style="color:var(--danger)">Delete</button></div></div>`);
      row.querySelector(".outline").onclick = () => io.insertTemplate(t.scaffold);
      row.querySelector(".text").onclick = () => { if (confirm(`Delete the template "${t.title}"?`)) { store.deleteUserTemplate(t.id); rerender(); } };
      body.appendChild(row);
    });
    const form = el(`
      <div class="mt-8">
        <label class="field" for="ut-title">Template name</label>
        <input type="text" id="ut-title" maxlength="60" placeholder="e.g. Weekly review" />
        <label class="field" for="ut-body">Structure (the headings or questions you want each time)</label>
        <textarea id="ut-body" rows="5" placeholder="What went well:&#10;&#10;What I'd change:&#10;&#10;Next week's focus:"></textarea>
        <p class="small" id="ut-err" style="color:var(--danger);margin:6px 0 0" role="alert"></p>
        <div class="chips mt-8" style="margin-bottom:0">
          <button type="button" class="primary" id="ut-save">Save template</button>
          <button type="button" class="outline" id="ut-fromentry">Use what I've written</button>
        </div>
      </div>`);
    form.querySelector("#ut-fromentry").onclick = () => { form.querySelector("#ut-body").value = io.currentText(); };
    form.querySelector("#ut-save").onclick = () => {
      const title = form.querySelector("#ut-title").value.trim();
      const text = form.querySelector("#ut-body").value;
      const err = form.querySelector("#ut-err");
      if (!title) { err.textContent = "Give the template a name."; return; }
      if (!text.trim()) { err.textContent = "Add the structure you want."; return; }
      store.addUserTemplate(title, "mine", text);
      rerender();
    };
    body.appendChild(form);
  };

  paintTabs();
  paintBody();
  return card;
}
