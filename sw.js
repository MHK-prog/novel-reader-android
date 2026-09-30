const CACHE = 'novel-library-app-v18';
const ASSETS = ['./', './index.html', './app.js', './fonts/Vazirmatn-wght.woff2', './manifest.webmanifest', './icons/icon-192.png', './icons/icon-512.png', './icons/like-empty.svg', './icons/like-filled.svg', './icons/dislike-empty.svg', './icons/dislike-filled.svg', './icons/open-book.svg', './icons/pencil.svg', './icons/save.svg', './icons/arrow-prev.svg', './icons/arrow-next.svg', './icons/paperclip.svg', './icons/menu.svg', './icons/add-book.svg', './icons/tag.svg', './icons/read-empty.svg', './icons/read-done.svg', './icons/scroll-top.svg', './icons/scroll-bottom.svg'];
self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(ASSETS)));
  self.skipWaiting();
});
self.addEventListener('activate', event => {
  event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(key => key !== CACHE).map(key => caches.delete(key)))));
  self.clients.claim();
});
self.addEventListener('fetch', event => {
  const request = event.request;
  if (request.method !== 'GET' || new URL(request.url).origin !== self.location.origin) return;
  event.respondWith(caches.match(request).then(cached => cached || fetch(request).then(response => {
    if (response.ok) caches.open(CACHE).then(cache => cache.put(request, response.clone()));
    return response;
  }).catch(() => caches.match('./index.html'))));
});



