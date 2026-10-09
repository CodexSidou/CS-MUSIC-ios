const CACHE = 'cs-music-v7';
const ASSETS = [
  './',
  './index.html',
  './styles.css',
  './manifest.webmanifest',
  './js/app.js',
  './js/ui.js',
  './js/player.js',
  './js/providers.js',
  './js/db.js',
  './js/id3.js',
  './icons/apple-touch-icon.png',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './icons/icon-maskable-512.png'
];

self.addEventListener('install', e => {
  e.waitUntil(
    caches.open(CACHE).then(c => c.addAll(ASSETS)).then(() => self.skipWaiting()).catch(() => {})
  );
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin !== location.origin) return;
  e.respondWith(
    fetch(req).then(r => {
      if (r.ok && (r.type === 'basic' || r.type === 'default')) {
        const copy = r.clone();
        const key = req.mode === 'navigate' ? './index.html' : req;
        caches.open(CACHE).then(c => c.put(key, copy));
      }
      return r;
    }).catch(() => caches.match(req).then(c => c || caches.match('./index.html')))
  );
});
