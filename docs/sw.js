// Network-first for everything: this app redeploys on every push to main, so caching
// anything aggressively would risk serving stale JS/CSS after an update. The only job this
// service worker does is (a) satisfy the "has a fetch handler" requirement most browsers use
// to decide the app is installable, and (b) let the already-loaded shell keep working if the
// network drops mid-session, by falling back to cache only when the network request fails.
const CACHE = "focuslens-shell-v1";

self.addEventListener("install", (event) => {
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))
    )
  );
  self.clients.claim();
});

self.addEventListener("fetch", (event) => {
  if (event.request.method !== "GET") return;
  event.respondWith(
    fetch(event.request)
      .then((response) => {
        const copy = response.clone();
        caches.open(CACHE).then((cache) => cache.put(event.request, copy));
        return response;
      })
      .catch(() => caches.match(event.request))
  );
});
