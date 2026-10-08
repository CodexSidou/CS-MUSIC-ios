const DB_NAME = 'cs-music';
const DB_VER = 1;
let dbp = null;

function open() {
  if (dbp) return dbp;
  dbp = new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VER);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains('tracks')) db.createObjectStore('tracks', { keyPath: 'id' });
      if (!db.objectStoreNames.contains('playlists')) db.createObjectStore('playlists', { keyPath: 'id' });
      if (!db.objectStoreNames.contains('kv')) db.createObjectStore('kv');
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
  return dbp;
}

async function tx(store, mode, fn) {
  const db = await open();
  return new Promise((resolve, reject) => {
    const t = db.transaction(store, mode);
    const s = t.objectStore(store);
    let res;
    try { res = fn(s); } catch (e) { reject(e); return; }
    t.oncomplete = () => resolve(res && res.result !== undefined ? res.result : res);
    t.onerror = () => reject(t.error);
    t.onabort = () => reject(t.error || new Error('aborted'));
  });
}

function getAll(store) {
  return new Promise((resolve, reject) => {
    tx(store, 'readonly', s => s.getAll()).then(r => resolve(r || [])).catch(reject);
  });
}

export const db = {
  async allTracks() { return getAll('tracks'); },
  async putTrack(t) { return tx('tracks', 'readwrite', s => s.put(t)); },
  async deleteTrack(id) { return tx('tracks', 'readwrite', s => s.delete(id)); },
  async allPlaylists() { return getAll('playlists'); },
  async putPlaylist(p) { return tx('playlists', 'readwrite', s => s.put(p)); },
  async deletePlaylist(id) { return tx('playlists', 'readwrite', s => s.delete(id)); },
  async kvGet(key) {
    const d = await open();
    if (!d.objectStoreNames.contains('kv')) return undefined;
    return new Promise((resolve, reject) => {
      const r = d.transaction('kv', 'readonly').objectStore('kv').get(key);
      r.onsuccess = () => resolve(r.result);
      r.onerror = () => reject(r.error);
    });
  },
  async kvSet(key, val) {
    const d = await open();
    return new Promise((resolve, reject) => {
      const t = d.transaction('kv', 'readwrite');
      t.objectStore('kv').put(val, key);
      t.oncomplete = () => resolve();
      t.onerror = () => reject(t.error);
    });
  },
  async stats() {
    const tracks = await this.allTracks();
    let bytes = 0, withFile = 0;
    for (const t of tracks) {
      if (t.file) { bytes += t.file.size || 0; withFile++; }
      if (t.art && t.art.size) bytes += t.art.size;
    }
    return { tracks: tracks.length, files: withFile, bytes };
  },
  async clearAll() {
    const d = await open();
    return new Promise((resolve, reject) => {
      const t = d.transaction(['tracks', 'playlists', 'kv'], 'readwrite');
      t.objectStore('tracks').clear();
      t.objectStore('playlists').clear();
      t.objectStore('kv').clear();
      t.oncomplete = () => resolve();
      t.onerror = () => reject(t.error);
    });
  }
};

export function uid(prefix) {
  return prefix + ':' + Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
}
