const CACHE = 'kho-nha-minh-shell-v201';
const SHELL = ['/', '/manifest.webmanifest', '/app.css?v=2.0.0', '/app.js?v=2.0.0', '/icons/icon-192.png', '/icons/icon-512.png'];

self.addEventListener('install', e => {
  self.skipWaiting();
  // Cache each file on its own so one failed file never blocks the install.
  e.waitUntil(caches.open(CACHE).then(c => Promise.allSettled(SHELL.map(u => c.add(u)))));
});

self.addEventListener('activate', e => e.waitUntil(Promise.all([
  caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k)))),
  self.clients.claim()
])));

self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  // Let the browser handle cross-origin, API and health requests directly.
  if (url.origin !== self.location.origin) return;
  if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/actuator/')) return;

  e.respondWith((async () => {
    try {
      const res = await fetch(req);
      if (res && res.ok && res.type === 'basic') {
        const copy = res.clone();
        caches.open(CACHE).then(c => c.put(req, copy)).catch(() => {});
      }
      return res;
    } catch (_) {
      const cached = await caches.match(req, { ignoreSearch: false });
      if (cached) return cached;
      if (req.mode === 'navigate') {
        const shell = await caches.match('/');
        if (shell) return shell;
      }
      // Never resolve with undefined: that turns into a hard network error.
      return new Response('Offline', { status: 503, statusText: 'Offline', headers: { 'Content-Type': 'text/plain; charset=utf-8' } });
    }
  })());
});
