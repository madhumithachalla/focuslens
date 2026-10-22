// Care near me: lists nearby health services from OpenStreetMap (Overpass) with no API key.
// Nothing runs until the person presses a button; coordinates are never stored.

const OVERPASS_URL = "https://overpass-api.de/api/interpreter";
const NOMINATIM_URL = "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=";

const TYPES = [
  { id: "mental", label: "Mental health" },
  { id: "doctor", label: "Doctors" },
  { id: "clinic", label: "Clinics" },
  { id: "hospital", label: "Hospitals" },
];

function esc(s) {
  return String(s == null ? "" : s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

function el(html) {
  const t = document.createElement("template");
  t.innerHTML = html.trim();
  return t.content.firstChild;
}

function haversineKm(lat1, lon1, lat2, lon2) {
  const r = (d) => (d * Math.PI) / 180;
  const a = Math.sin(r(lat2 - lat1) / 2) ** 2 + Math.cos(r(lat1)) * Math.cos(r(lat2)) * Math.sin(r(lon2 - lon1) / 2) ** 2;
  return 6371 * 2 * Math.asin(Math.sqrt(a));
}

function buildQuery(lat, lon, km) {
  const m = km * 1000;
  const filters = [
    "healthcare=psychotherapist", "healthcare=counselling", "healthcare=psychiatrist",
    "amenity=doctors", "amenity=clinic", "amenity=hospital",
  ];
  const parts = [];
  for (const f of filters) {
    const [k, v] = f.split("=");
    parts.push(`nwr["${k}"="${v}"](around:${m},${lat},${lon});`);
  }
  return `[out:json][timeout:25];(${parts.join("")});out center tags 40;`;
}

function classify(tags) {
  const h = tags.healthcare;
  if (h === "psychotherapist" || h === "counselling" || h === "psychiatrist") {
    return { type: "mental", label: h === "counselling" ? "Counselling" : h === "psychiatrist" ? "Psychiatrist" : "Psychotherapist" };
  }
  if (tags.amenity === "hospital") return { type: "hospital", label: "Hospital" };
  if (tags.amenity === "clinic") return { type: "clinic", label: "Clinic" };
  return { type: "doctor", label: "Doctor" };
}

function address(t) {
  const street = [t["addr:housenumber"], t["addr:street"]].filter(Boolean).join(" ");
  return [street, t["addr:suburb"] || t["addr:city"], t["addr:postcode"]].filter(Boolean).join(", ");
}

function safeUrl(u) {
  if (!u) return null;
  const v = /^https?:\/\//i.test(u) ? u : `https://${u}`;
  try {
    const p = new URL(v);
    return p.protocol === "http:" || p.protocol === "https:" ? p.href : null;
  } catch (e) {
    return null;
  }
}

async function fetchPlaces(lat, lon, km) {
  let res;
  try {
    res = await fetch(OVERPASS_URL, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: "data=" + encodeURIComponent(buildQuery(lat, lon, km)),
    });
  } catch (e) {
    const err = new Error("network");
    err.kind = "network";
    throw err;
  }
  if (res.status === 429 || res.status === 504 || res.status === 503) {
    const err = new Error("busy");
    err.kind = "busy";
    throw err;
  }
  if (!res.ok) {
    const err = new Error("http");
    err.kind = "network";
    throw err;
  }
  const json = await res.json();
  return (json.elements || [])
    .map((e) => {
      const tags = e.tags || {};
      const plat = e.lat != null ? e.lat : e.center && e.center.lat;
      const plon = e.lon != null ? e.lon : e.center && e.center.lon;
      if (plat == null || plon == null) return null;
      const c = classify(tags);
      return {
        name: tags.name || `Unnamed ${c.label.toLowerCase()}`,
        type: c.type,
        typeLabel: c.label,
        lat: plat,
        lon: plon,
        km: haversineKm(lat, lon, plat, plon),
        address: address(tags),
        phone: tags.phone || tags["contact:phone"] || "",
        website: safeUrl(tags.website || tags["contact:website"]),
      };
    })
    .filter(Boolean)
    .sort((a, b) => a.km - b.km);
}

export function renderCare(container, rerender) {
  let radius = 5;
  let filter = "all";
  let places = null;
  let status = "";
  let busy = false;
  let origin = null;

  container.innerHTML = "";
  container.appendChild(el(`
    <div>
      <h1>Care near me</h1>
      <p class="subtitle">Find mental-health and health services close to you.</p>
    </div>
  `));
  container.appendChild(el(`
    <div class="card soft">
      <p class="small" style="margin:0"><span class="bold">In an emergency</span> go to the nearest hospital emergency department or call your local emergency number. This page lists places; it does not recommend or vouch for anyone.</p>
    </div>
  `));

  const finder = el(`<div class="card"></div>`);
  container.appendChild(finder);
  const medHost = el(`<div></div>`);
  container.appendChild(medHost);
  renderMedSafety(medHost);

  function draw() {
    finder.innerHTML = "";
    finder.appendChild(el(`<p class="small">When you press a button, your coordinates are sent to overpass-api.de (OpenStreetMap data) for this one query. FocusLens never stores them. OpenStreetMap data can be incomplete or out of date, so phone ahead before you go.</p>`));

    const radii = el(`<div class="chips" role="group" aria-label="Search radius"></div>`);
    [2, 5, 10].forEach((km) => {
      const b = el(`<button type="button" class="chip${radius === km ? " active" : ""}" aria-pressed="${radius === km}">${km} km</button>`);
      b.onclick = () => { radius = km; draw(); };
      radii.appendChild(b);
    });
    finder.appendChild(radii);

    const useBtn = el(`<button type="button" class="primary" id="care-locate"${busy ? " disabled" : ""}>Use my location</button>`);
    useBtn.onclick = locate;
    finder.appendChild(el(`<div class="mt-8"></div>`)).appendChild(useBtn);

    finder.appendChild(el(`<label class="field" for="care-place">Search a place instead</label>`));
    finder.appendChild(el(`<input type="text" id="care-place" placeholder="Suburb or city, e.g. Newtown, Sydney" autocomplete="off" />`));
    const searchBtn = el(`<button type="button" class="outline mt-8" id="care-search"${busy ? " disabled" : ""}>Search this place</button>`);
    searchBtn.onclick = searchPlace;
    finder.appendChild(searchBtn);
    finder.appendChild(el(`<p class="small">Place search uses nominatim.openstreetmap.org. Type only a suburb or city, nothing personal.</p>`));

    finder.appendChild(el(`<p class="small mt-8" id="care-status" role="status"></p>`)).textContent = status;

    if (places) {
      const chips = el(`<div class="chips" role="group" aria-label="Filter by type"></div>`);
      [{ id: "all", label: "All" }, ...TYPES].forEach((t) => {
        const b = el(`<button type="button" class="chip${filter === t.id ? " active" : ""}" aria-pressed="${filter === t.id}">${t.label}</button>`);
        b.onclick = () => { filter = t.id; draw(); };
        chips.appendChild(b);
      });
      finder.appendChild(chips);
      const shown = places.filter((p) => filter === "all" || p.type === filter);
      if (!shown.length) {
        finder.appendChild(el(`<p class="small">${places.length ? "Nothing of that type in the list. Try another filter." : "No places found within this radius. Try a larger radius or a different place; the map data may simply be missing some."}</p>`));
      }
      shown.forEach((p) => {
        const osm = `https://www.openstreetmap.org/?mlat=${p.lat}&mlon=${p.lon}#map=17/${p.lat}/${p.lon}`;
        const gm = `https://www.google.com/maps/search/?api=1&query=${p.lat},${p.lon}`;
        const phoneHref = p.phone.replace(/[^+\d]/g, "");
        finder.appendChild(el(`
          <div class="card soft care-result">
            <p style="margin:0"><span class="bold">${esc(p.name)}</span></p>
            <p class="small" style="margin:2px 0">${esc(p.typeLabel)} &middot; ${p.km.toFixed(1)} km away</p>
            ${p.address ? `<p class="small" style="margin:2px 0">${esc(p.address)}</p>` : ""}
            ${p.phone ? `<p class="small" style="margin:2px 0">Phone: <a href="tel:${esc(phoneHref)}">${esc(p.phone)}</a></p>` : ""}
            ${p.website ? `<p class="small" style="margin:2px 0"><a href="${esc(p.website)}" target="_blank" rel="noopener noreferrer">Website</a></p>` : ""}
            <p class="small" style="margin:2px 0"><a href="${osm}" target="_blank" rel="noopener noreferrer">Open in OpenStreetMap</a> &middot; <a href="${gm}" target="_blank" rel="noopener noreferrer">Open in Google Maps</a></p>
          </div>
        `));
      });
      if (places.length >= 40) finder.appendChild(el(`<p class="small">Showing up to 40 nearest results from the map data.</p>`));
    }
  }

  async function runSearch(lat, lon) {
    origin = { lat, lon };
    busy = true;
    status = "Looking for places nearby...";
    places = null;
    draw();
    try {
      places = await fetchPlaces(lat, lon, radius);
      status = places.length ? `Found ${places.length} place${places.length === 1 ? "" : "s"} within ${radius} km, nearest first.` : "";
    } catch (e) {
      places = null;
      status = e.kind === "busy"
        ? "The map service is busy or timed out. Please wait a minute and try again, or try a smaller radius."
        : "Couldn't reach the map service. Check your connection and try again.";
    }
    busy = false;
    draw();
  }

  function locate() {
    if (!navigator.geolocation) {
      status = "Your browser can't share location. Use \"Search a place instead\" below.";
      draw();
      return;
    }
    busy = true;
    status = "Waiting for your location...";
    draw();
    navigator.geolocation.getCurrentPosition(
      (pos) => runSearch(pos.coords.latitude, pos.coords.longitude),
      (err) => {
        busy = false;
        status = err && err.code === 1
          ? "Location permission was denied. That's fine: type a suburb or city under \"Search a place instead\"."
          : "Couldn't work out your location. Try again, or search for a place instead.";
        draw();
      },
      { enableHighAccuracy: false, timeout: 15000, maximumAge: 300000 }
    );
  }

  async function searchPlace() {
    const q = (finder.querySelector("#care-place").value || "").trim();
    if (!q) {
      status = "Type a suburb or city first.";
      draw();
      return;
    }
    busy = true;
    status = "Finding that place...";
    draw();
    try {
      const res = await fetch(NOMINATIM_URL + encodeURIComponent(q));
      if (!res.ok) throw new Error("http");
      const hits = await res.json();
      if (!hits.length) {
        busy = false;
        status = "Couldn't find that place. Try a nearby suburb or the city name.";
        draw();
        return;
      }
      await runSearch(parseFloat(hits[0].lat), parseFloat(hits[0].lon));
    } catch (e) {
      busy = false;
      status = "Couldn't look that place up right now. Please try again shortly.";
      draw();
    }
  }

  draw();
}

const MED_QUESTIONS = [
  "What is this medicine for?",
  "How and when should I take it (with food, time of day)?",
  "Does it interact with my other medicines, supplements, alcohol or caffeine?",
  "What side effects should I watch for, and when should I call you?",
  "What should I do if I miss a dose?",
  "How long will I need to take it, and how do I stop?",
  "Is it safe with my other health conditions, or if I am or might become pregnant or am breastfeeding?",
];

export function renderMedSafety(container) {
  const card = el(`
    <div class="card">
      <h2 style="margin-top:0">Medication questions to ask</h2>
      <p class="small">A checklist to take to your doctor or pharmacist. FocusLens never suggests doses or products; those decisions belong with a qualified professional who knows your history.</p>
      <ul class="med-checklist" style="list-style:none;padding:0;margin:0">
        ${MED_QUESTIONS.map((q, i) => `<li><label style="display:flex;gap:8px;align-items:flex-start;padding:4px 0"><input type="checkbox" id="medq-${i}" style="width:auto;margin-top:4px" /><span>${esc(q)}</span></label></li>`).join("")}
      </ul>
      <p class="small bold mt-8" style="margin-bottom:4px">Official medicine information</p>
      <p class="small" style="margin:0">
        <a href="https://medlineplus.gov/druginformation.html" target="_blank" rel="noopener noreferrer">MedlinePlus drug information (US)</a> &middot;
        <a href="https://www.nhs.uk/medicines/" target="_blank" rel="noopener noreferrer">NHS medicines (UK)</a> &middot;
        <a href="https://www.tga.gov.au/resources/consumer-medicine-information-cmi" target="_blank" rel="noopener noreferrer">TGA consumer medicine information (Australia)</a>
      </p>
      <div class="mt-8"><button type="button" class="outline" id="med-print">Print checklist</button></div>
    </div>
  `);
  card.querySelector("#med-print").onclick = () => {
    const w = window.open("", "_blank");
    if (!w) { window.print(); return; }
    w.document.write(`<!doctype html><title>Medication questions</title><body style="font-family:sans-serif;max-width:640px;margin:24px auto"><h1>Questions to ask about a medicine</h1><p>Medicine name: ____________________</p><ul style="list-style:none;padding:0">${MED_QUESTIONS.map((q) => `<li style="margin:14px 0">&#9744; ${esc(q)}<br><span style="display:block;border-bottom:1px solid #999;height:22px"></span></li>`).join("")}</ul></body>`);
    w.document.close();
    w.focus();
    w.print();
  };
  container.appendChild(card);
}
