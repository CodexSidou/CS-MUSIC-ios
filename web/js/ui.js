import { db, uid } from './db.js';
import { itunes, audius, youtube, archive, soundcloud, spotify, resolveStream, fetchWithProgress } from './providers.js';
import { player } from './player.js';

export const state = {
  screen: 'home',
  library: [],
  results: [],
  collection: null,
  charts: [],
  trending: [],
  source: 'all',
  libFilter: 'all',
  libSort: 'added',
  registry: new Map(),
  ctx: {},
  playlistView: null,
  online: true,
  searched: false,
  searchBusy: false
};

const $ = s => document.querySelector(s);
const $$ = s => Array.from(document.querySelectorAll(s));

const ICONS = {
  play: '<svg viewBox="0 0 24 24"><path d="M7 4.5l12 7.5-12 7.5v-15z" fill="currentColor"/></svg>',
  pause: '<svg viewBox="0 0 24 24"><path d="M7 4h4v16H7V4zm6 0h4v16h-4V4z" fill="currentColor"/></svg>',
  dots: '<svg viewBox="0 0 24 24"><circle cx="5" cy="12" r="2" fill="currentColor"/><circle cx="12" cy="12" r="2" fill="currentColor"/><circle cx="19" cy="12" r="2" fill="currentColor"/></svg>',
  dl: '<svg viewBox="0 0 24 24"><path d="M12 3v11m0 0l-4-4m4 4l4-4M5 18h14" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  check: '<svg viewBox="0 0 24 24"><path d="M5 12.5l4.5 4.5L19 7" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  music: '<svg viewBox="0 0 24 24"><path d="M9 18V6l10-2v11.5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/><circle cx="6.5" cy="18" r="2.5" fill="currentColor"/><circle cx="16.5" cy="15.5" r="2.5" fill="currentColor"/></svg>',
  queue: '<svg viewBox="0 0 24 24"><path d="M4 6h12M4 10h12M4 14h7M17 12v7.2a2.4 2.4 0 101.6 2.3V13.5l3-.8" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  plus: '<svg viewBox="0 0 24 24"><path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
  next: '<svg viewBox="0 0 24 24"><path d="M6 5l10 7-10 7V5zm10 0h2v14h-2V5z" fill="currentColor"/></svg>',
  trash: '<svg viewBox="0 0 24 24"><path d="M5 7h14M10 7V5h4v2m-7 0l1 13h8l1-13" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  list: '<svg viewBox="0 0 24 24"><path d="M4 6h16M4 12h16M4 18h10" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>',
  search: '<svg viewBox="0 0 24 24"><circle cx="11" cy="11" r="6.5" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="M16 16l4.5 4.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>',
  phone: '<svg viewBox="0 0 24 24"><rect x="7" y="3" width="10" height="18" rx="2.5" fill="none" stroke="currentColor" stroke-width="1.7"/><path d="M11 18h2" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>',
  info: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="1.7"/><path d="M12 11v5m0-8.5v.5" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/></svg>',
  storage: '<svg viewBox="0 0 24 24"><ellipse cx="12" cy="6" rx="7" ry="3" fill="none" stroke="currentColor" stroke-width="1.7"/><path d="M5 6v12c0 1.7 3.1 3 7 3s7-1.3 7-3V6M5 12c0 1.7 3.1 3 7 3s7-1.3 7-3" fill="none" stroke="currentColor" stroke-width="1.7"/></svg>',
  import: '<svg viewBox="0 0 24 24"><path d="M12 3v12m0 0l-4-4m4 4l4-4M4 17v2a2 2 0 002 2h12a2 2 0 002-2v-2" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>'
};

const SOURCE_BADGE = { itunes: 'iTunes', audius: 'Audius', youtube: 'YouTube', soundcloud: 'SoundCloud', spotify: 'Spotify', archive: 'Archive', local: '' };

function esc(s) {
  return String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

export function fmtTime(sec) {
  if (!sec || !isFinite(sec) || sec < 0) return '0:00';
  const m = Math.floor(sec / 60), s = Math.floor(sec % 60);
  return m + ':' + String(s).padStart(2, '0');
}

function fmtBytes(b) {
  if (b > 1073741824) return (b / 1073741824).toFixed(1) + ' GB';
  if (b > 1048576) return (b / 1048576).toFixed(1) + ' MB';
  return Math.max(1, Math.round(b / 1024)) + ' KB';
}

let toastTimer;
export function toast(msg, isErr = false) {
  const t = $('#toast');
  t.textContent = msg;
  t.classList.toggle('err', isErr);
  t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { t.hidden = true; }, isErr ? 4200 : 2400);
}

export function registerCtx(key, tracks) {
  state.ctx[key] = tracks;
}

function artworkHtml(t) {
  const art = t.artBlobUrl || t.artwork;
  const badge = SOURCE_BADGE[t.provider] ? `<span class="src-badge">${SOURCE_BADGE[t.provider]}</span>` : '';
  if (art) return `<img src="${esc(art)}" alt="" loading="lazy">${badge}`;
  return `<span style="color:rgba(124,89,251,.5)">${ICONS.music}</span>${badge}`;
}

function rowHtml(t, ctx, idx, extra = '') {
  const isSaved = !!t.file;
  const dur = t.duration && t.duration > 0 ? fmtTime(t.duration) : '';
  const saveBtn = t.provider && t.provider !== 'local'
    ? `<button class="tr-btn" data-act="${isSaved ? 'saved' : 'save'}" data-id="${esc(t.id)}" aria-label="${isSaved ? 'Saved' : 'Save offline'}">${isSaved ? ICONS.check : ICONS.dl}</button>`
    : '';
  const cur = player.current() && player.current().id === t.id;
  return `<div class="track-row${cur ? ' playing' : ''}" data-ctx="${esc(ctx)}" data-idx="${idx}" data-id="${esc(t.id)}">
    <div class="tr-art">${artworkHtml(t)}</div>
    <div class="tr-meta"><div class="tr-title">${esc(t.title)}</div><div class="tr-sub">${esc(t.artist || t.album || '')}${extra}</div></div>
    <div class="tr-right">${dur ? `<span class="tr-dur">${dur}</span>` : ''}${saveBtn}<button class="tr-btn" data-act="menu" data-id="${esc(t.id)}" aria-label="More">${ICONS.dots}</button></div>
  </div>`;
}

function hcardHtml(t, ctx, idx) {
  const art = t.artBlobUrl || t.artwork;
  return `<div class="hcard" data-ctx="${esc(ctx)}" data-idx="${idx}" data-id="${esc(t.id)}">
    <div class="art">${art ? `<img src="${esc(art)}" alt="" loading="lazy">` : `<div style="height:100%;display:flex;align-items:center;justify-content:center;color:rgba(124,89,251,.5)">${ICONS.music}</div>`}</div>
    <div class="t">${esc(t.title)}</div><div class="s">${esc(t.artist || '')}</div>
  </div>`;
}

function registerTracks(list, ctx) {
  for (const t of list) state.registry.set(t.id, t);
  registerCtx(ctx, list);
}

async function ensureArtUrls(list) {
  for (const t of list) {
    if (t.art && !t.artBlobUrl) t.artBlobUrl = URL.createObjectURL(t.art);
  }
}

export function loadLibraryIntoState(tracks) {
  state.library = tracks;
}

export async function refreshLibrary() {
  const tracks = await db.allTracks();
  tracks.sort((a, b) => (b.addedAt || 0) - (a.addedAt || 0));
  await ensureArtUrls(tracks);
  state.library = tracks;
  registerTracks(tracks, 'library');
  return tracks;
}

function isStandalone() {
  return window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone === true;
}

function isIOS() {
  return /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
}

export function renderHome() {
  const el = $('#home-content');
  const lib = state.library;
  const recents = lib.filter(t => t.lastPlayedAt).sort((a, b) => b.lastPlayedAt - a.lastPlayedAt).slice(0, 12);
  registerTracks(recents, 'recents');
  const top = lib.slice(0, 12);
  registerTracks(top, 'top');
  const charts = state.charts;
  registerTracks(charts, 'charts');
  const trending = state.trending;
  registerTracks(trending, 'trending');

  let html = '';
  if (!state.online) {
    html += `<div class="offline-pill off"><i></i> Offline — your library still plays</div>`;
  }
  if (isIOS() && !isStandalone()) {
    html += `<div class="banner accent" id="install-banner"><h3>Install CS music on your iPhone</h3>
      <p>Tap the <b>Share</b> button in Safari, then choose <b>Add to Home Screen</b>. It opens like a real app and works offline.</p>
      <div class="banner-btns"><button class="pill-btn primary" data-ui="install-help">Show me how</button><button class="pill-btn" data-ui="dismiss-install">Later</button></div></div>`;
  }
  if (!lib.length) {
    html += `<div class="empty"><div class="big">🎵</div><h3>Your library is empty</h3>
      <p>Import songs from this iPhone to listen offline — or search millions of songs online.</p>
      <div class="banner-btns" style="justify-content:center"><button class="pill-btn primary" data-ui="import">${ICONS.import} Import music</button><button class="pill-btn" data-ui="gosearch">${ICONS.search} Search online</button></div></div>`;
  } else {
    if (recents.length) {
      html += `<div class="sec-head"><h2>Recently played</h2></div><div class="hrow">${recents.map((t, i) => hcardHtml(t, 'recents', i)).join('')}</div>`;
    }
    html += `<div class="sec-head"><h2>Your library</h2><span class="link" data-ui="golib">${lib.length} songs</span></div>
      <div class="card-list">${top.map((t, i) => rowHtml(t, 'top', i)).join('')}</div>`;
  }
  if (state.online) {
    html += `<div class="sec-head"><h2>Top songs</h2><span class="link">iTunes</span></div>`;
    html += `<div id="charts-slot">${state.charts.length ? `<div class="hrow">${charts.map((t, i) => hcardHtml(t, 'charts', i)).join('')}</div>` : '<div class="skeleton"></div><div class="skeleton"></div>'}</div>`;
    html += `<div class="sec-head"><h2>Trending now</h2><span class="link">Audius</span></div>`;
    html += `<div id="trending-slot">${state.trending.length ? `<div class="card-list">${trending.map((t, i) => rowHtml(t, 'trending', i)).join('')}</div>` : '<div class="skeleton"></div><div class="skeleton"></div>'}</div>`;
  }
  html += `<div class="about-note">CS Music Web · Offline &amp; online · Zero tracking<br><a href="https://github.com/CodexSidou/CS-MUSIC-ios" target="_blank" rel="noopener">Source on GitHub</a></div>`;
  el.innerHTML = html;
}

export async function loadHomeFeeds() {
  if (!state.online) return;
  const slotCharts = () => $('#charts-slot');
  const slotTrend = () => $('#trending-slot');
  try {
    const c = await itunes.charts();
    state.charts = c;
    registerTracks(c, 'charts');
    if (slotCharts()) slotCharts().innerHTML = c.length ? `<div class="hrow">${c.map((t, i) => hcardHtml(t, 'charts', i)).join('')}</div>` : '';
  } catch { if (slotCharts()) slotCharts().innerHTML = ''; }
  try {
    const t = await audius.trending();
    state.trending = t;
    registerTracks(t, 'trending');
    if (slotTrend()) slotTrend().innerHTML = t.length ? `<div class="card-list">${t.map((x, i) => rowHtml(x, 'trending', i)).join('')}</div>` : '';
  } catch { if (slotTrend()) slotTrend().innerHTML = ''; }
}

const SOURCE_TABS = [
  { key: 'all', label: 'All Sources' },
  { key: 'spotify', label: 'Spotify' },
  { key: 'soundcloud', label: 'SoundCloud' },
  { key: 'youtube', label: 'YouTube' },
  { key: 'itunes', label: 'iTunes' },
  { key: 'audius', label: 'Audius' },
  { key: 'archive', label: 'Archive' }
];

export function renderSearch() {
  const el = $('#search-content');
  el.innerHTML = `
    <div class="screen-title">Search</div>
    <div class="screen-sub">Search online or paste a Spotify link (track, album, playlist) to save offline.</div>
    <form class="search-bar" id="search-form">
      <div class="search-field">${ICONS.search}<input type="search" id="search-input" placeholder="Search song or paste Spotify URL…" autocomplete="off" enterkeyhint="search"></div>
    </form>
    <div class="src-tabs">${SOURCE_TABS.map(s => `<button class="src-tab${state.source === s.key ? ' active' : ''}" data-src="${s.key}">${s.label}</button>`).join('')}</div>
    <div id="search-results"></div>`;
  renderSearchResults();
  $('#search-form').addEventListener('submit', e => {
    e.preventDefault();
    doSearch($('#search-input').value.trim());
  });
}

export function renderSearchResults() {
  const el = $('#search-results');
  if (!el) return;
  if (state.searchBusy) {
    el.innerHTML = '<div class="skeleton"></div><div class="skeleton"></div><div class="skeleton"></div><div class="skeleton"></div>';
    return;
  }
  if (!state.searched) {
    el.innerHTML = `<div class="empty"><div class="big">🔍</div><h3>Search online music</h3>
      <p>Search any song, artist, album, or paste a Spotify link (track, album, playlist) to play and download offline on iPhone.</p></div>`;
    return;
  }
  if (!state.results.length) {
    el.innerHTML = `<div class="empty"><div class="big">🤷</div><h3>No results</h3><p>Try different keywords or switch the source above.</p></div>`;
    return;
  }
  registerTracks(state.results, 'results');

  let bannerHtml = '';
  if (state.collection) {
    bannerHtml = `
      <div class="col-banner">
        <div class="col-art">${state.collection.artwork ? `<img src="${esc(state.collection.artwork)}" alt="">` : ICONS.music}</div>
        <div class="col-meta">
          <span class="col-badge">SPOTIFY ${esc(state.collection.type.toUpperCase())}</span>
          <div class="col-title">${esc(state.collection.title)}</div>
          <div class="col-sub">${esc(state.collection.artist)} · ${state.collection.tracks.length} tracks</div>
          <div class="col-actions">
            <button class="pill-btn primary" id="btn-col-play">${ICONS.play} Play all</button>
            <button class="pill-btn" id="btn-col-dl">${ICONS.dl} Save all</button>
          </div>
        </div>
      </div>
    `;
  }

  el.innerHTML = bannerHtml + `<div class="card-list">${state.results.map((t, i) => rowHtml(t, 'results', i)).join('')}</div>`;

  if (state.collection) {
    const playBtn = $('#btn-col-play');
    if (playBtn) {
      playBtn.onclick = () => player.playTracks(state.collection.tracks, 0);
    }
    const dlBtn = $('#btn-col-dl');
    if (dlBtn) {
      dlBtn.onclick = async () => {
        dlBtn.disabled = true;
        dlBtn.textContent = 'Saving…';
        let savedCount = 0;
        for (let idx = 0; idx < state.collection.tracks.length; idx++) {
          const trk = state.collection.tracks[idx];
          toast(`Saving ${idx + 1}/${state.collection.tracks.length}: ${trk.title}`);
          const ok = await saveOnline(trk);
          if (ok) savedCount++;
        }
        dlBtn.disabled = false;
        dlBtn.innerHTML = `${ICONS.check} Saved (${savedCount})`;
        toast(`Saved ${savedCount} songs to offline library!`);
      };
    }
  }
}

export async function doSearch(q) {
  if (!q) return;
  if (!state.online) { toast('You are offline', true); return; }
  state.searched = true;
  state.searchBusy = true;
  state.collection = null;
  renderSearchResults();

  try {
    // 1. Detect Spotify URL (tracks, albums, playlists)
    if (spotify.isUrl(q)) {
      const spData = await spotify.resolve(q);
      if (spData.type === 'album' || spData.type === 'playlist') {
        state.collection = spData;
        state.results = spData.tracks || [];
      } else {
        state.collection = null;
        state.results = spData.tracks || [];
      }
      state.searchBusy = false;
      renderSearchResults();
      toast(`Loaded Spotify ${spData.type}`);
      return;
    }

    let res = [];
    if (state.source === 'all') {
      const [sc, sp, yt] = await Promise.allSettled([
        soundcloud.search(q),
        spotify.search(q),
        youtube.search(q)
      ]);
      const scList = sc.status === 'fulfilled' ? sc.value : [];
      const spList = sp.status === 'fulfilled' ? sp.value : [];
      const ytList = yt.status === 'fulfilled' ? yt.value : [];
      const maxLen = Math.max(scList.length, spList.length, ytList.length);
      const combined = [];
      for (let i = 0; i < maxLen; i++) {
        if (scList[i]) combined.push(scList[i]);
        if (spList[i]) combined.push(spList[i]);
        if (ytList[i]) combined.push(ytList[i]);
      }
      res = combined.slice(0, 35);
    } else if (state.source === 'spotify') {
      res = await spotify.search(q);
    } else if (state.source === 'soundcloud') {
      res = await soundcloud.search(q);
    } else if (state.source === 'youtube') {
      res = await youtube.search(q);
    } else if (state.source === 'itunes') {
      res = await itunes.search(q);
    } else if (state.source === 'audius') {
      res = await audius.search(q);
    } else if (state.source === 'archive') {
      res = await archive.search(q);
    }
    state.results = res;
    state.searchBusy = false;
    renderSearchResults();
    if (!res.length) toast('No results found');
  } catch (e) {
    state.searchBusy = false;
    state.results = [];
    renderSearchResults();
    toast('Search failed — check your connection or switch source', true);
  }
}

export function renderLibrary() {
  const el = $('#library-content');
  let list = state.library.slice();
  if (state.libFilter === 'local') list = list.filter(t => t.provider === 'local');
  if (state.libFilter === 'saved') list = list.filter(t => t.provider !== 'local' && t.file);
  if (state.libSort === 'added') list.sort((a, b) => (b.addedAt || 0) - (a.addedAt || 0));
  if (state.libSort === 'title') list.sort((a, b) => (a.title || '').localeCompare(b.title || ''));
  if (state.libSort === 'plays') list.sort((a, b) => (b.playCount || 0) - (a.playCount || 0));
  const sortLabel = { added: 'Recently added', title: 'Title', plays: 'Most played' }[state.libSort];
  const filters = [['all', 'All'], ['local', 'On this iPhone'], ['saved', 'Saved offline']];
  let html = `<div class="screen-title">Library</div>
    <div class="meta-line"><span><b>${state.library.length}</b> songs</span><span><b>${state.library.filter(t => t.file).length}</b> offline</span></div>
    <div class="chips">${filters.map(f => `<button class="src-tab${state.libFilter === f[0] ? ' active' : ''}" data-libfilter="${f[0]}">${f[1]}</button>`).join('')}
    <button class="src-tab" data-ui="sort">↕ ${sortLabel}</button>
    <button class="src-tab" data-ui="import">＋ Import</button></div>`;
  if (!state.library.length) {
    html += `<div class="empty"><div class="big">📂</div><h3>Nothing here yet</h3><p>Import audio files from this iPhone, or save songs you find in Search.</p><button class="pill-btn primary" data-ui="import">${ICONS.import} Import music</button></div>`;
  } else if (!list.length) {
    html += `<div class="empty"><div class="big">🔍</div><h3>No songs match</h3><p>Switch the filter above.</p></div>`;
  } else {
    registerTracks(list, 'libview');
    html += `<div class="card-list">${list.map((t, i) => rowHtml(t, 'libview', i)).join('')}</div>`;
  }
  el.innerHTML = html;
}

export async function renderPlaylists() {
  const el = $('#playlists-content');
  if (state.playlistView) {
    const p = state.library.length ? (await db.allPlaylists()).find(x => x.id === state.playlistView) : null;
    if (!p) { state.playlistView = null; return renderPlaylists(); }
    const tracks = p.trackIds.map(id => state.library.find(t => t.id === id)).filter(Boolean);
    registerTracks(tracks, 'playlist');
    const cover = tracks[0] && (tracks[0].artBlobUrl || tracks[0].artwork);
    el.innerHTML = `
      <div style="display:flex;align-items:center;gap:6px;margin:6px 0 12px">
        <button class="icon-btn" data-ui="pl-back" aria-label="Back"><svg viewBox="0 0 24 24"><path d="M15 5l-7 7 7 7" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg></button>
        <span style="font-size:13px;color:var(--text2);font-weight:650">PLAYLIST</span>
        <span style="flex:1"></span>
        <button class="icon-btn" data-ui="pl-rename" aria-label="Rename">${ICONS.list}</button>
        <button class="icon-btn" data-ui="pl-delete" aria-label="Delete">${ICONS.trash}</button>
      </div>
      <div style="display:flex;gap:16px;align-items:center;margin-bottom:18px">
        <div class="pl-cover" style="width:96px;height:96px;border-radius:18px">${cover ? `<img src="${esc(cover)}" alt="">` : ICONS.music}</div>
        <div><div style="font-size:21px;font-weight:800;letter-spacing:-.5px">${esc(p.name)}</div>
        <div style="font-size:13px;color:var(--text2);margin-top:4px">${tracks.length} song${tracks.length === 1 ? '' : 's'}</div></div>
      </div>
      <div class="banner-btns" style="margin-bottom:14px">
        <button class="pill-btn primary" data-ui="pl-play">${ICONS.play} Play all</button>
        <button class="pill-btn" data-ui="pl-shuffle">⇄ Shuffle</button>
        <button class="pill-btn" data-ui="pl-add">＋ Add songs</button>
      </div>`;
    if (tracks.length) html_appendRows(el, tracks);
    else el.innerHTML += `<div class="empty"><div class="big">🎵</div><h3>Empty playlist</h3><p>Tap “Add songs” to fill it from your library.</p></div>`;
    return;
  }
  const pls = await db.allPlaylists();
  let html = `<div class="screen-title">Playlists</div><div class="screen-sub">Your mixes, kept on this device.</div>
    <button class="pill-btn primary" data-ui="pl-new" style="margin-bottom:16px">${ICONS.plus} New playlist</button>`;
  if (!pls.length) {
    html += `<div class="empty"><div class="big">📋</div><h3>No playlists yet</h3><p>Create one and add songs from your library.</p></div>`;
  } else {
    html += `<div class="card-list">`;
    for (const p of pls) {
      const tracks = p.trackIds.map(id => state.library.find(t => t.id === id)).filter(Boolean);
      const cover = tracks[0] && (tracks[0].artBlobUrl || tracks[0].artwork);
      html += `<div class="pl-row" data-pl="${esc(p.id)}"><div class="pl-cover">${cover ? `<img src="${esc(cover)}" alt="">` : ICONS.music}</div>
        <div class="pl-meta"><div class="pl-name">${esc(p.name)}</div><div class="pl-sub">${tracks.length} song${tracks.length === 1 ? '' : 's'}</div></div>
        <button class="tr-btn" data-ui="pl-row-menu" data-pl="${esc(p.id)}">${ICONS.dots}</button></div>`;
    }
    html += `</div>`;
  }
  el.innerHTML = html;
}

function html_appendRows(el, tracks) {
  el.innerHTML += `<div class="card-list">${tracks.map((t, i) => rowHtml(t, 'playlist', i)).join('')}</div>`;
}

export async function importFiles(files, onProgress) {
  const list = Array.from(files).filter(f => f.name.match(/\.(mp3|m4a|aac|flac|wav|ogg|opus|webm|mp4)$/i) || (f.type && f.type.startsWith('audio/')));
  if (!list.length) { toast('No audio files found', true); return 0; }
  let done = 0;
  const { readTags } = await import('./id3.js');
  for (const f of list) {
    try {
      const tags = await readTags(f);
      const ext = (f.name.split('.').pop() || '').toLowerCase();
      const track = {
        id: uid('local'),
        provider: 'local',
        title: tags.title || f.name,
        artist: tags.artist || '',
        album: tags.album || '',
        duration: tags.duration || -1,
        artwork: null,
        art: tags.art || null,
        artBlobUrl: null,
        file: f,
        saved: true,
        addedAt: Date.now(),
        lastPlayedAt: 0,
        playCount: 0,
        ext
      };
      if (track.art) track.artBlobUrl = URL.createObjectURL(track.art);
      await db.putTrack(track);
      done++;
    } catch (e) { /* skip file */ }
    if (onProgress) onProgress(done, list.length);
  }
  await refreshLibrary();
  if (state.screen === 'home') renderHome();
  if (state.screen === 'library') renderLibrary();
  toast(`Imported ${done} song${done === 1 ? '' : 's'}`);
  probeDurations();
  return done;
}

let probeChain = Promise.resolve();
function probeDurations() {
  const pending = state.library.filter(t => (!t.duration || t.duration <= 0) && t.file);
  for (const t of pending) {
    probeChain = probeChain.then(async () => {
      if (t.duration && t.duration > 0) return;
      const url = URL.createObjectURL(t.file);
      try {
        const d = await new Promise((res, rej) => {
          const a = document.createElement('audio');
          a.preload = 'metadata';
          a.onloadedmetadata = () => res(a.duration);
          a.onerror = () => rej(new Error('meta'));
          a.src = url;
          setTimeout(() => rej(new Error('timeout')), 6000);
        });
        if (isFinite(d) && d > 0) {
          t.duration = Math.round(d);
          await db.putTrack(t);
        }
      } catch { /* leave unknown */ }
      finally { URL.revokeObjectURL(url); }
    });
  }
}

export async function saveOnline(track, onProgress) {
  if (track.file) return true;
  if (!state.online) { toast('You are offline', true); return false; }
  const attempt = async (fresh) => {
    if (fresh) { track._resolved = null; track._resolvedAt = 0; }
    const url = await resolveStream(track);
    const urls = Array.isArray(track._candidates) && track._candidates.length ? [url].concat(track._candidates.filter(u => u !== url)) : [url];
    let blob = null;
    for (const u of urls) {
      try {
        const b = await fetchWithProgress(u, onProgress);
        if (b && b.size >= 1024) { blob = b; break; }
      } catch { /* try next candidate */ }
    }
    if (!blob) throw new Error('download failed');
    track.file = blob;
    track.saved = true;
    track.provider = track.provider === 'local' ? 'local' : track.provider;
    track.addedAt = track.addedAt || Date.now();
    if (!track.duration || track.duration <= 0) {
      try {
        const tmp = URL.createObjectURL(blob);
        const d = await new Promise((res, rej) => {
          const a = document.createElement('audio');
          a.preload = 'metadata';
          a.onloadedmetadata = () => res(a.duration);
          a.onerror = () => rej(new Error('meta'));
          a.src = tmp;
          setTimeout(() => rej(new Error('t')), 6000);
        });
        if (isFinite(d) && d > 0) track.duration = Math.round(d);
        URL.revokeObjectURL(tmp);
      } catch { /* unknown duration */ }
    }
    await db.putTrack(track);
    await refreshLibrary();
    return true;
  };
  try {
    return await attempt(false);
  } catch {
    try {
      return await attempt(true);
    } catch {
      toast('This stream cannot be saved offline', true);
      return false;
    }
  }
}

export async function removeTrack(id) {
  const t = state.library.find(x => x.id === id);
  if (!t) return;
  if (player.current() && player.current().id === id) {
    toast('Playing now — remove after', true);
    return;
  }
  await db.deleteTrack(id);
  const pls = await db.allPlaylists();
  for (const p of pls) {
    if (p.trackIds.includes(id)) {
      p.trackIds = p.trackIds.filter(x => x !== id);
      await db.putPlaylist(p);
    }
  }
  await refreshLibrary();
  rerender();
  toast('Removed from library');
}

export function rerender() {
  if (state.screen === 'home') renderHome();
  else if (state.screen === 'search') renderSearchResults();
  else if (state.screen === 'library') renderLibrary();
  else if (state.screen === 'playlists') renderPlaylists();
}

export function go(screen) {
  state.screen = screen;
  $$('.screen').forEach(s => s.classList.toggle('active', s.dataset.screen === screen));
  $$('.tab').forEach(t => t.classList.toggle('active', t.dataset.tab === screen));
  if (screen === 'home') renderHome();
  if (screen === 'search') renderSearch();
  if (screen === 'library') renderLibrary();
  if (screen === 'playlists') renderPlaylists();
}

function sheet(title, items) {
  const host = $('#sheet-host');
  $('#sheet-title').textContent = title;
  $('#sheet-body').innerHTML = items.map((it, i) => {
    if (it.hint) return `<div class="sheet-item hint">${it.hint}</div>`;
    if (it.sep) return '<div class="sheet-sep"></div>';
    return `<button class="sheet-item${it.danger ? ' danger' : ''}" data-sheet-i="${i}">${it.icon || ''}<span>${esc(it.label)}</span></button>`;
  }).join('');
  host.hidden = false;
  $('#sheet-body').onclick = e => {
    const b = e.target.closest('[data-sheet-i]');
    if (!b) return;
    const it = items[Number(b.dataset.sheetI)];
    closeSheet();
    if (it && it.onClick) it.onClick();
  };
}

function closeSheet() { $('#sheet-host').hidden = true; }

function dialog({ title, msg, input, okLabel = 'OK', danger = false }) {
  return new Promise(resolve => {
    const host = $('#dialog-host');
    $('#dialog-title').textContent = title;
    $('#dialog-msg').textContent = msg || '';
    $('#dialog-msg').hidden = !msg;
    const inp = $('#dialog-input');
    inp.hidden = !input;
    inp.value = input || '';
    $('#dialog-actions').innerHTML = `<button data-d="0">Cancel</button><button class="${danger ? 'danger' : 'primary'}" data-d="1">${esc(okLabel)}</button>`;
    host.hidden = false;
    const done = v => { host.hidden = true; resolve(v); };
    $('#dialog-actions').onclick = e => {
      const b = e.target.closest('[data-d]');
      if (!b) return;
      done(b.dataset.d === '1' ? (input ? inp.value.trim() || null : true) : null);
    };
    $('#dialog-scrim').onclick = () => done(null);
    if (input) setTimeout(() => inp.focus(), 60);
  });
}

function trackMenu(track) {
  const items = [
    { label: 'Play next', icon: ICONS.next, onClick: () => playNext(track) },
    { label: 'Add to queue', icon: ICONS.queue, onClick: () => { player.enqueue([track]); toast('Added to queue'); } },
    { label: 'Add to playlist', icon: ICONS.plus, onClick: () => addToPlaylistPicker(track) }
  ];
  if (track.provider !== 'local' && !track.file) {
    items.push({ sep: true });
    items.push({ label: 'Save offline', icon: ICONS.dl, onClick: () => saveWithUi(track) });
  }
  if (track.file && track.provider !== 'local') {
    items.push({ sep: true });
    items.push({ label: 'Remove offline copy', icon: ICONS.trash, danger: true, onClick: async () => { track.file = null; await db.putTrack(track); await refreshLibrary(); rerender(); toast('Offline copy removed'); } });
  }
  if (state.screen === 'library' || state.screen === 'home') {
    items.push({ sep: true });
    items.push({ label: 'Remove from library', icon: ICONS.trash, danger: true, onClick: () => confirmRemove(track) });
  }
  sheet(track.title, items);
}

async function confirmRemove(track) {
  const ok = await dialog({ title: 'Remove song?', msg: `“${track.title}” will be deleted from your library and playlists.`, okLabel: 'Remove', danger: true });
  if (ok) removeTrack(track.id);
}

function playNext(track) {
  const cur = player.current();
  if (!cur) { player.playTracks([track], 0); return; }
  const ci = player.queue.indexOf(cur);
  player.queue.splice(ci + 1, 0, track);
  player.emit('queue');
  toast('Will play next');
}

async function saveWithUi(track, btn) {
  const started = await saveOnline(track, p => {
    if (btn) { btn.classList.add('busy'); btn.innerHTML = `<span class="spin"></span>`; }
    if (btn && p) btn.dataset.pct = Math.round(p * 100);
  });
  if (btn) {
    btn.classList.remove('busy');
    btn.innerHTML = started ? ICONS.check : ICONS.dl;
    btn.dataset.act = started ? 'saved' : 'save';
  }
  if (started) {
    toast('Saved for offline');
    if (state.screen === 'library') renderLibrary();
    if (state.screen === 'home') renderHome();
  }
}

async function addToPlaylistPicker(track) {
  const pls = await db.allPlaylists();
  const items = [{ label: 'New playlist…', icon: ICONS.plus, onClick: async () => {
    const name = await dialog({ title: 'New playlist', input: 'My playlist', okLabel: 'Create' });
    if (!name) return;
    const p = { id: uid('pl'), name, trackIds: [track.id], createdAt: Date.now() };
    await db.putPlaylist(p);
    toast(`Created “${name}”`);
    if (state.screen === 'playlists') renderPlaylists();
  } }];
  for (const p of pls) {
    items.push({ label: p.name, icon: ICONS.music, onClick: async () => {
      if (p.trackIds.includes(track.id)) { toast('Already in playlist'); return; }
      p.trackIds.push(track.id);
      await db.putPlaylist(p);
      toast(`Added to “${p.name}”`);
      if (state.screen === 'playlists') renderPlaylists();
    } });
  }
  sheet('Add to playlist', items);
}

function openTrackFromDom(el) {
  const ctx = el.dataset.ctx;
  const idx = Number(el.dataset.idx);
  const list = state.ctx[ctx];
  if (!list || !list[idx]) return;
  player.playTracks(list, idx);
}

function updateMini() {
  const mp = $('#mini-player');
  const t = player.current();
  if (!t) { mp.hidden = true; return; }
  mp.hidden = false;
  $('#mini-title').textContent = t.title;
  $('#mini-artist').textContent = t.artist || (SOURCE_BADGE[t.provider] || '');
  const art = t.artBlobUrl || t.artwork;
  $('#mini-art').innerHTML = art ? `<img src="${esc(art)}" alt="">` : `<span style="color:rgba(124,89,251,.5)">${ICONS.music}</span>`;
  $('#mini-toggle').innerHTML = player.loading && player.playing === false && !mediaEl().src ? '<span class="spin"></span>' : (player.playing ? ICONS.pause : ICONS.play);
}

function mediaEl() { return document.getElementById('media'); }

function updateNowPlaying() {
  const t = player.current();
  if (!t) return;
  const art = t.artBlobUrl || t.artwork;
  $('#np-title').textContent = t.title;
  $('#np-artist').textContent = [t.artist, t.album].filter(Boolean).join(' · ');
  $('#np-art').innerHTML = art ? `<img src="${esc(art)}" alt="">` : `<span class="ph">K</span>`;
  $('#np-bg').style.backgroundImage = art ? `url("${art}")` : 'none';
  $('#np-source-label').textContent = ({ local: 'ON THIS IPHONE', itunes: 'iTUNES PREVIEW', audius: 'AUDIUS', youtube: 'YOUTUBE', archive: 'INTERNET ARCHIVE' }[t.provider]) || 'NOW PLAYING';
  const save = $('#btn-save-offline');
  if (t.provider === 'local' || t.file) { save.hidden = true; }
  else { save.hidden = false; save.textContent = 'Save offline'; save.disabled = false; }
  $('#btn-shuffle').classList.toggle('on', player.shuffle);
  $('#btn-repeat').classList.toggle('on', player.repeat !== 'off');
  $('#btn-repeat').innerHTML = player.repeat === 'one'
    ? '<svg viewBox="0 0 24 24"><path d="M17 3l3 3-3 3M20 6H8a4 4 0 00-4 4v1M7 21l-3-3 3-3M4 18h12a4 4 0 004-4v-1" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/><text x="10.5" y="15.5" font-size="8" fill="currentColor" font-weight="800">1</text></svg>'
    : '<svg viewBox="0 0 24 24"><path d="M17 3l3 3-3 3M20 6H8a4 4 0 00-4 4v1M7 21l-3-3 3-3M4 18h12a4 4 0 004-4v-1" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>';
  updateTime();
}

let seeking = false;
function updateTime() {
  const m = mediaEl();
  const cur = m.currentTime || 0;
  const dur = isFinite(m.duration) ? m.duration : (player.current() && player.current().duration > 0 ? player.current().duration : 0);
  $('#t-cur').textContent = fmtTime(cur);
  $('#t-dur').textContent = dur ? fmtTime(dur) : '0:00';
  const pct = dur ? (cur / dur) * 100 : 0;
  const seek = $('#seek');
  if (!seeking) { seek.value = String(Math.round(pct * 10)); seek.style.setProperty('--pct', pct + '%'); }
  $('#mini-progress').style.width = pct + '%';
}

function updatePlayButtons() {
  const playing = player.playing;
  const load = player.loading;
  $('#btn-play').innerHTML = playing ? ICONS.pause : (load ? '<span class="spin" style="border-top-color:#fff;border-color:rgba(255,255,255,.35);border-top-color:#fff"></span>' : ICONS.play);
  $('#mini-toggle').innerHTML = playing ? ICONS.pause : ICONS.play;
  $('#np-eq').hidden = !player.current();
  $('#np-eq').classList.toggle('on', playing);
  updateMini();
}

function openNowPlaying() {
  if (!player.current()) return;
  updateNowPlaying();
  $('#now-playing').classList.remove('closed');
  $('#now-playing').setAttribute('aria-hidden', 'false');
}

function closeNowPlaying() {
  $('#now-playing').classList.add('closed');
  $('#now-playing').setAttribute('aria-hidden', 'true');
}

function queueSheet() {
  const items = player.queue.map((t, i) => ({
    label: (i === player.index ? '▶ ' : '') + t.title + (t.artist ? ' — ' + t.artist : ''),
    icon: i === player.index ? ICONS.music : ICONS.queue,
    onClick: () => player.playTracks(player.queue, i)
  }));
  if (!items.length) { toast('Queue is empty'); return; }
  sheet('Queue · ' + items.length, items);
}

async function newPlaylistFlow() {
  const name = await dialog({ title: 'New playlist', input: 'My playlist', okLabel: 'Create' });
  if (!name) return null;
  const p = { id: uid('pl'), name, trackIds: [], createdAt: Date.now() };
  await db.putPlaylist(p);
  renderPlaylists();
  toast(`Created “${name}”`);
  return p;
}

async function playlistAddSongs(playlist) {
  const available = state.library.filter(t => !playlist.trackIds.includes(t.id));
  if (!available.length) { toast('All library songs are already in this playlist'); return; }
  const items = available.slice(0, 50).map(t => ({
    label: t.title + (t.artist ? ' — ' + t.artist : ''),
    icon: ICONS.plus,
    onClick: async () => {
      playlist.trackIds.push(t.id);
      await db.putPlaylist(playlist);
      renderPlaylists();
      toast('Added');
    }
  }));
  sheet('Add songs to ' + playlist.name, items);
}

async function showSettings() {
  const stats = await db.stats();
  const items = [
    { label: 'Import music from this iPhone', icon: ICONS.import, onClick: () => $('#import-input').click() },
    { label: 'How to install on iPhone', icon: ICONS.phone, onClick: showInstallHelp },
    { label: `Storage · ${stats.tracks} songs · ${fmtBytes(stats.bytes)}`, icon: ICONS.storage, onClick: showStorageInfo },
    { sep: true },
    { label: 'About CS music', icon: ICONS.info, onClick: showAbout },
    { label: 'Clear all data', icon: ICONS.trash, danger: true, onClick: async () => {
      const ok = await dialog({ title: 'Clear everything?', msg: 'All imported songs, saved offline copies and playlists will be permanently deleted.', okLabel: 'Delete all', danger: true });
      if (!ok) return;
      await db.clearAll();
      await refreshLibrary();
      state.charts = []; state.trending = [];
      go('home');
      toast('All data cleared');
    } }
  ];
  sheet('Settings', items);
}

function showStorageInfo() {
  sheet('Storage', [
    { hint: 'Music and playlists live only on this iPhone — nothing is uploaded anywhere.' },
    { hint: 'Saved offline songs keep playing with no internet connection. Search needs a connection.' },
    { hint: 'iOS may ask you to re-pick files after a system update — keep your originals in Files.' }
  ]);
}

function showInstallHelp() {
  sheet('Install on iPhone', [
    { hint: '<b>1.</b> Open this page in <b>Safari</b> (Chrome can’t add home-screen apps).' },
    { hint: '<b>2.</b> Tap the <b>Share</b> button (square with arrow) in the bottom bar.' },
    { hint: '<b>3.</b> Choose <b>Add to Home Screen</b>, then <b>Add</b>.' },
    { hint: '<b>4.</b> Launch CS music from your home screen — it runs full screen like a native app and works offline.' }
  ]);
}

function showAbout() {
  sheet('About', [
    { hint: '<b>CS music Web</b> v1.0 — the iPhone edition of CS music.' },
    { hint: 'Offline library · playlists · iTunes previews · Audius full tracks · YouTube streaming. Zero servers, zero tracking.' },
    { hint: '<a href="https://github.com/CodexSidou/CS-MUSIC-ios" target="_blank" rel="noopener" style="color:var(--green)">github.com/CodexSidou/CS-MUSIC-ios</a>' }
  ]);
}

async function pickPlaylistTracksScreen() {
  const p = state.playlistView ? (await db.allPlaylists()).find(x => x.id === state.playlistView) : null;
  if (p) playlistAddSongs(p);
}

export function initUI(handlers) {
  $$('.tab').forEach(t => t.addEventListener('click', () => go(t.dataset.tab)));
  $('#btn-import').addEventListener('click', () => handlers.onImportClick());
  $('#btn-settings').addEventListener('click', showSettings);
  $('#sheet-scrim').addEventListener('click', closeSheet);
  $('#np-close').addEventListener('click', closeNowPlaying);
  $('#np-queue-btn').addEventListener('click', queueSheet);
  $('#mini-player').addEventListener('click', e => {
    if (e.target.closest('#mini-toggle') || e.target.closest('#mini-next')) return;
    openNowPlaying();
  });
  $('#mini-toggle').addEventListener('click', e => { e.stopPropagation(); player.toggle(); });
  $('#mini-next').addEventListener('click', e => { e.stopPropagation(); player.next(); });
  $('#btn-play').addEventListener('click', () => player.toggle());
  $('#btn-next').addEventListener('click', () => player.next());
  $('#btn-prev').addEventListener('click', () => player.prev());
  $('#btn-shuffle').addEventListener('click', () => {
    const on = player.toggleShuffle();
    $('#btn-shuffle').classList.toggle('on', on);
    toast(on ? 'Shuffle on' : 'Shuffle off');
  });
  $('#btn-repeat').addEventListener('click', () => {
    const r = player.cycleRepeat();
    $('#btn-repeat').classList.toggle('on', r !== 'off');
    toast(r === 'off' ? 'Repeat off' : r === 'all' ? 'Repeat all' : 'Repeat one');
  });
  $('#btn-save-offline').addEventListener('click', async () => {
    const t = player.current();
    if (!t) return;
    const btn = $('#btn-save-offline');
    btn.disabled = true;
    btn.textContent = 'Saving…';
    const ok = await saveOnline(t);
    btn.disabled = false;
    if (ok) { btn.textContent = 'Saved ✓'; toast('Saved for offline'); }
    else { btn.textContent = 'Save offline'; }
  });
  $('#btn-add-playlist').addEventListener('click', () => {
    const t = player.current();
    if (t) addToPlaylistPicker(t);
  });
  const seek = $('#seek');
  seek.addEventListener('input', () => {
    seeking = true;
    const pct = Number(seek.value) / 10;
    seek.style.setProperty('--pct', pct + '%');
    const m = mediaEl();
    const dur = isFinite(m.duration) ? m.duration : 0;
    $('#t-cur').textContent = fmtTime((pct / 100) * dur);
  });
  const commitSeek = () => {
    if (!seeking) return;
    seeking = false;
    const pct = Number(seek.value) / 10;
    const m = mediaEl();
    if (isFinite(m.duration)) player.seek((pct / 100) * m.duration);
  };
  seek.addEventListener('change', commitSeek);
  seek.addEventListener('touchend', commitSeek);

  let npDragY = null;
  const handle = $('#np-handle');
  handle.addEventListener('touchstart', e => { npDragY = e.touches[0].clientY; }, { passive: true });
  handle.addEventListener('touchmove', e => {
    if (npDragY == null) return;
    const dy = e.touches[0].clientY - npDragY;
    if (dy > 0) $('#now-playing').style.transform = `translateY(${dy}px)`;
  }, { passive: true });
  handle.addEventListener('touchend', e => {
    if (npDragY == null) return;
    const dy = (e.changedTouches[0].clientY - npDragY);
    $('#now-playing').style.transform = '';
    npDragY = null;
    if (dy > 110) closeNowPlaying();
  });

  document.addEventListener('click', e => {
    const ui = e.target.closest('[data-ui]');
    if (ui) {
      const a = ui.dataset.ui;
      if (a === 'import') handlers.onImportClick();
      if (a === 'gosearch') go('search');
      if (a === 'golib') go('library');
      if (a === 'install-help') showInstallHelp();
      if (a === 'dismiss-install') { const b = $('#install-banner'); if (b) b.remove(); db.kvSet('installDismissed', 1); }
      if (a === 'sort') {
        state.libSort = state.libSort === 'added' ? 'title' : state.libSort === 'title' ? 'plays' : 'added';
        renderLibrary();
      }
      if (a === 'pl-new') newPlaylistFlow();
      if (a === 'pl-back') { state.playlistView = null; renderPlaylists(); }
      if (a === 'pl-play') {
        const pList = state.ctx['playlist'];
        if (pList && pList.length) player.playTracks(pList, 0);
      }
      if (a === 'pl-shuffle') {
        const pList = state.ctx['playlist'];
        if (pList && pList.length) { player.shuffle = true; player.playTracks(pList.slice().sort(() => Math.random() - .5), 0); }
      }
      if (a === 'pl-add') pickPlaylistTracksScreen();
      if (a === 'pl-rename') {
        (async () => {
          const p = (await db.allPlaylists()).find(x => x.id === state.playlistView);
          if (!p) return;
          const name = await dialog({ title: 'Rename playlist', input: p.name, okLabel: 'Rename' });
          if (name) { p.name = name; await db.putPlaylist(p); renderPlaylists(); }
        })();
      }
      if (a === 'pl-delete') {
        (async () => {
          const p = (await db.allPlaylists()).find(x => x.id === state.playlistView);
          if (!p) return;
          const ok = await dialog({ title: `Delete “${p.name}”?`, msg: 'The songs stay in your library.', okLabel: 'Delete', danger: true });
          if (ok) { await db.deletePlaylist(p.id); state.playlistView = null; renderPlaylists(); toast('Playlist deleted'); }
        })();
      }
      return;
    }
    const srcTab = e.target.closest('[data-src]');
    if (srcTab) {
      state.source = srcTab.dataset.src;
      $$('.src-tab[data-src]').forEach(x => x.classList.toggle('active', x.dataset.src === state.source));
      if (state.searched) doSearch($('#search-input').value.trim());
      else renderSearchResults();
      return;
    }
    const lf = e.target.closest('[data-libfilter]');
    if (lf) { state.libFilter = lf.dataset.libfilter; renderLibrary(); return; }
    const plRow = e.target.closest('.pl-row');
    if (plRow && !e.target.closest('[data-ui="pl-row-menu"]')) {
      state.playlistView = plRow.dataset.pl;
      renderPlaylists();
      return;
    }
    const plMenu = e.target.closest('[data-ui="pl-row-menu"]');
    if (plMenu) {
      (async () => {
        const p = (await db.allPlaylists()).find(x => x.id === plMenu.dataset.pl);
        if (!p) return;
        sheet(p.name, [
          { label: 'Play', icon: ICONS.play, onClick: async () => {
            const tracks = p.trackIds.map(id => state.library.find(t => t.id === id)).filter(Boolean);
            if (tracks.length) player.playTracks(tracks, 0); else toast('Playlist is empty');
          } },
          { label: 'Open', icon: ICONS.list, onClick: () => { state.playlistView = p.id; renderPlaylists(); } },
          { sep: true },
          { label: 'Delete playlist', icon: ICONS.trash, danger: true, onClick: async () => {
            const ok = await dialog({ title: `Delete “${p.name}”?`, okLabel: 'Delete', danger: true });
            if (ok) { await db.deletePlaylist(p.id); renderPlaylists(); toast('Playlist deleted'); }
          } }
        ]);
      })();
      return;
    }
    const act = e.target.closest('[data-act]');
    if (act) {
      const t = state.registry.get(act.dataset.id) || player.queue.find(x => x.id === act.dataset.id);
      if (!t) return;
      if (act.dataset.act === 'menu') trackMenu(t);
      if (act.dataset.act === 'save') saveWithUi(t, act);
      if (act.dataset.act === 'saved') toast('Already saved offline');
      return;
    }
    const row = e.target.closest('.track-row, .hcard');
    if (row && row.dataset.ctx) openTrackFromDom(row);
  });

  player.on('state', () => { updatePlayButtons(); markPlayingRows(); });
  player.on('track', () => { updateNowPlaying(); updateMini(); markPlayingRows(); });
  player.on('time', updateTime);
  player.on('queue', () => { if (!$('#sheet-host').hidden) queueSheet(); });
  player.on('error', d => { if (d && d.error) toast('Could not play this track', true); });
  player.on('history', t => { db.putTrack(t).catch(() => {}); });
  player.bindMediaEvents();

  updatePlayButtons();
  updateTime();
}

function markPlayingRows() {
  const cur = player.current();
  $$('.track-row').forEach(r => r.classList.toggle('playing', !!cur && r.dataset.id === cur.id));
}
