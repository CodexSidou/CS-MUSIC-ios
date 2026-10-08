import { state, initUI, go, renderHome, loadHomeFeeds, refreshLibrary, importFiles, toast } from './ui.js';

const handlers = {
  onImportClick: () => document.getElementById('import-input').click()
};

async function detectOnline() {
  if (!navigator.onLine) return false;
  try {
    const ctrl = new AbortController();
    const t = setTimeout(() => ctrl.abort(), 5000);
    const r = await fetch('https://itunes.apple.com/search?term=1&limit=1', { signal: ctrl.signal, method: 'GET' });
    clearTimeout(t);
    return r.ok;
  } catch {
    return false;
  }
}

async function boot() {
  if ('serviceWorker' in navigator && (location.protocol === 'https:' || location.hostname === 'localhost' || location.hostname === '127.0.0.1')) {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  }

  initUI(handlers);
  go('home');

  await refreshLibrary();
  renderHome();

  state.online = await detectOnline();
  renderHome();
  loadHomeFeeds();

  window.addEventListener('online', () => { state.online = true; renderHome(); loadHomeFeeds(); toast('Back online'); });
  window.addEventListener('offline', () => { state.online = false; renderHome(); toast('Offline mode'); });

  const input = document.getElementById('import-input');
  input.addEventListener('change', async () => {
    if (!input.files || !input.files.length) return;
    const total = input.files.length;
    toast(`Importing ${total} file${total === 1 ? '' : 's'}…`);
    await importFiles(input.files, (done, all) => {
      if (done === all) toast(`Imported ${done} song${done === 1 ? '' : 's'}`);
    });
    input.value = '';
  });

  let dragDepth = 0;
  window.addEventListener('dragenter', e => {
    e.preventDefault();
    dragDepth++;
    document.getElementById('dropzone').hidden = false;
  });
  window.addEventListener('dragover', e => e.preventDefault());
  window.addEventListener('dragleave', () => {
    dragDepth = Math.max(0, dragDepth - 1);
    if (!dragDepth) document.getElementById('dropzone').hidden = true;
  });
  window.addEventListener('drop', async e => {
    e.preventDefault();
    dragDepth = 0;
    document.getElementById('dropzone').hidden = true;
    if (e.dataTransfer && e.dataTransfer.files.length) await importFiles(e.dataTransfer.files);
  });

  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible' && navigator.onLine && !state.online) {
      detectOnline().then(ok => { if (ok) { state.online = true; renderHome(); } });
    }
  });
}

boot().catch(err => {
  console.error(err);
  const z = document.createElement('div');
  z.style.cssText = 'position:fixed;inset:0;display:flex;align-items:center;justify-content:center;background:#04011B;color:#f88;font:14px monospace;padding:30px;text-align:center;z-index:9999';
  z.textContent = 'CS music failed to start: ' + (err && err.message ? err.message : err);
  document.body.appendChild(z);
});
