// Spotter offline service worker (generated into docs/sw.js by build.py)
const CACHE = "spotter-c0c4aec7d1";
const SHELL = ["./", "index.html", "manifest.webmanifest", "vendor/anthropic-sdk.js", "icons/icon-192.png", "icons/icon-512.png", "icons/apple-touch-icon.png"];
const FONT_HOSTS = ["fonts.googleapis.com", "fonts.gstatic.com"];

self.addEventListener("install", event => {
  event.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", event => {
  event.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k.startsWith("spotter-") && k !== CACHE && k !== "spotter-fonts").map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", event => {
  const req = event.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);

  // Google Fonts: serve cached, refresh in the background
  if (FONT_HOSTS.includes(url.hostname)) {
    event.respondWith(caches.open("spotter-fonts").then(async c => {
      const hit = await c.match(req);
      const net = fetch(req).then(res => { if (res.ok || res.type === "opaque") c.put(req, res.clone()); return res; }).catch(() => hit);
      return hit || net;
    }));
    return;
  }
  if (url.origin !== self.location.origin) return; // e.g. the Claude API: always network

  // The app page: network first so updates arrive, cached copy when offline
  if (req.mode === "navigate") {
    event.respondWith(
      fetch(req).then(res => { caches.open(CACHE).then(c => c.put("index.html", res.clone())); return res; })
        .catch(() => caches.match("index.html"))
    );
    return;
  }
  // Other app files: cache first
  event.respondWith(caches.match(req).then(hit => hit || fetch(req)));
});
