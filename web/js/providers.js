const PIPED_INSTANCES = [
  'https://api.piped.private.coffee',
  'https://pipedapi.kavin.rocks',
  'https://pipedapi.adminforge.de',
  'https://api.piped.projectsegfau.lt',
  'https://pipedapi.r4fo.com',
  'https://pipedapi.qdi.fi',
  'https://piped-api.owo.si',
  'https://pipedapi.nosebs.ru',
  'https://piped-api.garudalinux.org',
  'https://api.piped.privacyredirect.com',
  'https://pipedapi.leptons.xyz',
  'https://pipedapi.reallyaweso.me',
  'https://pipedapi.moomoo.me',
  'https://pipedapi.syncpundit.io',
  'https://pipedapi.tokhmi.xyz'
];

const INVIDIOUS_INSTANCES = [
  'https://invidious.materialio.us',
  'https://invidious.f5.si',
  'https://yt.artemislena.eu',
  'https://invidious.privacyredirect.com',
  'https://iv.duti.dev',
  'https://invidious.jing.rocks',
  'https://inv.tux.pizza',
  'https://invidious.privacydev.net',
  'https://invidious.perennialte.ch',
  'https://inv.nadeko.net'
];

const AUDIUS_HOSTS = [
  'https://discoveryprovider.audius.co',
  'https://discoveryprovider2.audius.co',
  'https://discoveryprovider3.audius.co',
  'https://audius-discovery-1.altego.net'
];

const APP_NAME = 'CSMusic';
let pipedSticky = null;
let invidiousSticky = null;
let audiusSticky = null;

async function jget(url, timeoutMs = 8000) {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const r = await fetch(url, { signal: ctrl.signal, headers: { Accept: 'application/json' } });
    if (!r.ok) throw new Error('HTTP ' + r.status);
    return await r.json();
  } finally {
    clearTimeout(t);
  }
}

function art400(url) {
  if (!url) return null;
  return url.replace(/\/\d+x\d+bb\./, '/400x400bb.').replace(/\/\d+x\d+-\w\./, '/400x400bb.');
}

function fmtDur(sec) {
  if (!sec || sec < 0 || !isFinite(sec)) return -1;
  return Math.round(sec);
}

export const itunes = {
  async search(q) {
    const d = await jget(`https://itunes.apple.com/search?term=${encodeURIComponent(q)}&entity=song&limit=30`);
    return (d.results || []).filter(r => r.previewUrl).map(r => ({
      id: 'itunes:' + r.trackId,
      provider: 'itunes',
      title: r.trackName || 'Unknown',
      artist: r.artistName || '',
      album: r.collectionName || '',
      artwork: art400(r.artworkUrl100),
      duration: fmtDur((r.trackTimeMillis || 0) / 1000),
      streamUrl: r.previewUrl,
      saved: false
    }));
  },
  async charts() {
    const rss = await jget('https://itunes.apple.com/us/rss/topsongs/limit=25/json');
    const entries = (rss.feed && rss.feed.entry) || [];
    const ids = entries.map(e => e.id && e.id.attributes && e.id.attributes['im:id']).filter(Boolean);
    if (!ids.length) return [];
    const look = await jget(`https://itunes.apple.com/lookup?id=${ids.join(',')}`);
    const byId = {};
    for (const r of (look.results || [])) if (r.previewUrl) byId[String(r.trackId)] = r;
    const out = [];
    for (const id of ids) {
      const r = byId[id];
      if (!r) continue;
      out.push({
        id: 'itunes:' + r.trackId,
        provider: 'itunes',
        title: r.trackName || 'Unknown',
        artist: r.artistName || '',
        album: r.collectionName || '',
        artwork: art400(r.artworkUrl100),
        duration: fmtDur((r.trackTimeMillis || 0) / 1000),
        streamUrl: r.previewUrl,
        saved: false
      });
    }
    return out;
  }
};

async function audiusHost() {
  if (audiusSticky) return audiusSticky;
  try {
    const d = await jget('https://api.audius.co', 5000);
    if (d && Array.isArray(d.data) && d.data.length) {
      audiusSticky = d.data[Math.floor(Math.random() * d.data.length)];
      return audiusSticky;
    }
  } catch { /* fall through */ }
  audiusSticky = AUDIUS_HOSTS[0];
  return audiusSticky;
}

function audiusTrack(r) {
  const art = r.artwork && (r.artwork['480x480'] || r.artwork['150x150'] || r.artwork['1000x1000']);
  return {
    id: 'audius:' + r.id,
    provider: 'audius',
    title: r.title || 'Unknown',
    artist: (r.user && r.user.name) || '',
    album: '',
    artwork: art || null,
    duration: fmtDur(r.duration),
    streamUrl: null,
    audiusId: r.id,
    saved: false
  };
}

export const audius = {
  async search(q) {
    const host = await audiusHost();
    const d = await jget(`${host}/v1/tracks/search?query=${encodeURIComponent(q)}&app_name=${APP_NAME}`);
    return (d.data || []).map(audiusTrack);
  },
  async trending() {
    const host = await audiusHost();
    const d = await jget(`${host}/v1/tracks/trending?app_name=${APP_NAME}&limit=25`);
    return (d.data || []).map(audiusTrack);
  },
  streamUrl(id) {
    return `${hostSync()}/v1/tracks/${id}/stream?app_name=${APP_NAME}`;
  }
};

function hostSync() { return audiusSticky || AUDIUS_HOSTS[0]; }

function normalizePipedItem(it) {
  const vid = (it.url || '').split('v=')[1] || (it.url || '').replace(/^.*\//, '');
  return {
    id: 'yt:' + vid,
    provider: 'youtube',
    title: it.title || 'Unknown',
    artist: it.uploaderName || '',
    album: '',
    artwork: it.thumbnail || (vid ? `https://i.ytimg.com/vi/${vid}/hqdefault.jpg` : null),
    duration: fmtDur(it.duration),
    streamUrl: null,
    ytId: vid,
    saved: false
  };
}

async function rotate(stickyGet, stickySet, bases, path, timeoutMs = 7000) {
  const order = [];
  const s = stickyGet();
  if (s) order.push(s);
  for (const b of bases) if (b !== s) order.push(b);
  let lastErr;
  for (let i = 0; i < order.length; i++) {
    const tries = i === 0 ? 2 : 1;
    for (let t = 0; t < tries; t++) {
      try {
        const d = await jget(order[i] + path, timeoutMs);
        stickySet(order[i]);
        return d;
      } catch (e) { lastErr = e; }
    }
  }
  throw lastErr || new Error('all instances failed');
}

export const youtube = {
  async search(q) {
    let d;
    try {
      d = await rotate(() => pipedSticky, v => { pipedSticky = v; }, PIPED_INSTANCES, `/search?q=${encodeURIComponent(q)}&filter=music_songs`);
      if (!d.items || !d.items.length) d = await jget(pipedSticky + `/search?q=${encodeURIComponent(q)}&filter=all`, 7000);
    } catch {
      d = await rotate(() => invidiousSticky, v => { invidiousSticky = v; }, INVIDIOUS_INSTANCES, `/api/v1/search?q=${encodeURIComponent(q)}&type=video`);
      return (d || []).filter(x => x.type === 'video').slice(0, 25).map(x => ({
        id: 'yt:' + x.videoId,
        provider: 'youtube',
        title: x.title || 'Unknown',
        artist: x.author || '',
        album: '',
        artwork: x.videoThumbnails && (x.videoThumbnails.find(t => t.quality === 'maxres') || x.videoThumbnails[0]) ? (x.videoThumbnails.find(t => t.quality === 'maxres') || x.videoThumbnails[0]).url : null,
        duration: fmtDur(x.lengthSeconds),
        streamUrl: null,
        ytId: x.videoId,
        saved: false
      }));
    }
    return (d.items || []).filter(x => x.type === 'stream' || x.type === 'video').slice(0, 25).map(normalizePipedItem);
  },
  async resolve(track) {
    const ytId = track.ytId || (track.id || '').replace('yt:', '');
    if (!ytId) throw new Error('no video id');
    if (track._resolved && track._resolvedAt && Date.now() - track._resolvedAt < 4 * 3600 * 1000 && canPlaySrc(track._resolved)) {
      return track._resolved;
    }
    const candidates = await resolveCandidates(ytId);
    const pick = candidates.find(c => canPlaySrc(c.url)) || candidates[0];
    if (!pick) throw new Error('no playable stream');
    track._resolved = pick.url;
    track._resolvedAt = Date.now();
    return pick.url;
  }
};

export const archive = {
  async search(q) {
    const query = `"${q}" AND mediatype:audio AND format:(MP3)`;
    const d = await jget(`https://archive.org/advancedsearch.php?q=${encodeURIComponent(query)}&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator&sort%5B%5D=downloads+desc&rows=25&page=1&output=json`);
    const docs = (d.response && d.response.docs) || [];
    return docs.map(x => ({
      id: 'arch:' + x.identifier,
      provider: 'archive',
      title: Array.isArray(x.title) ? x.title[0] : (x.title || 'Unknown'),
      artist: Array.isArray(x.creator) ? x.creator.join(', ') : (x.creator || ''),
      album: '',
      artwork: x.identifier ? `https://archive.org/services/img/${x.identifier}` : null,
      duration: -1,
      streamUrl: null,
      archId: x.identifier,
      saved: false
    }));
  },
  async resolve(track) {
    const id = track.archId || (track.id || '').replace('arch:', '');
    if (!id) throw new Error('no item id');
    if (track._resolved && track._resolvedAt && Date.now() - track._resolvedAt < 12 * 3600 * 1000) return track._resolved;
    const d = await jget(`https://archive.org/metadata/${encodeURIComponent(id)}`);
    const files = (d.files || []).filter(f => /\.mp3$/i.test(f.name) && (!f.private || f.private === '0' || f.private === 'false'));
    const pick = files.find(f => (f.format || '').includes('VBR')) || files.find(f => (f.format || '') === 'MP3') || files[0];
    if (!pick) throw new Error('no audio file');
    if (!track.duration && pick.length) {
      const secs = parseFloat(pick.length);
      if (!isNaN(secs) && secs > 0) track.duration = Math.round(secs);
    }
    const url = `https://archive.org/download/${encodeURIComponent(id)}/${pick.name.split('/').map(encodeURIComponent).join('/')}`;
    track._resolved = url;
    track._resolvedAt = Date.now();
    return url;
  }
};

function rewriteGoogle(url, apiBase) {
  if (!url || !apiBase || !url.includes('googlevideo.com/videoplayback')) return url;
  const query = url.split('videoplayback?')[1] || '';
  return apiBase + '/videoplayback?' + query;
}

function hostBonus(url, apiBase) {
  if (!url) return 0;
  try {
    const h = new URL(url).host;
    if (apiBase && h === new URL(apiBase).host) return 25;
    if (h.includes('piped')) return 25;
    if (h.includes('odycdn')) return -2;
  } catch { /* ignore */ }
  return 0;
}

function mimePenalty(mime) {
  const m = (mime || '').toLowerCase();
  if (m.includes('mpegurl') || m.includes('m3u8') || m.includes('hls')) return -30;
  return 0;
}

async function resolveCandidates(ytId) {
  const out = [];
  try {
    const d = await rotate(() => pipedSticky, v => { pipedSticky = v; }, PIPED_INSTANCES, `/streams/${ytId}`, 9000);
    const apiBase = pipedSticky;
    for (const a of (d.audioStreams || [])) {
      const url = rewriteGoogle(a.url, apiBase);
      out.push({ url: url, mime: a.mimeType || '', audioOnly: true, score: scoreAudio(a.mimeType || '', a.bitrate || 0) + hostBonus(url, apiBase) + mimePenalty(a.mimeType || '') });
    }
    for (const v of (d.videoStreams || [])) {
      if (!v.videoOnly) {
        const url = rewriteGoogle(v.url, apiBase);
        out.push({ url: url, mime: v.mimeType || '', audioOnly: false, score: scoreAudio(v.mimeType || '', 0) - 5 + hostBonus(url, apiBase) + mimePenalty(v.mimeType || '') });
      }
    }
  } catch { /* try invidious */ }
  if (!out.length) {
    try {
      const d = await rotate(() => invidiousSticky, v => { invidiousSticky = v; }, INVIDIOUS_INSTANCES, `/api/v1/videos/${ytId}`, 9000);
      const apiBase = invidiousSticky;
      for (const f of (d.adaptiveFormats || [])) {
        if ((f.type || '').startsWith('audio')) {
          const url = rewriteGoogle(f.url, apiBase);
          out.push({ url: url, mime: f.type || '', audioOnly: true, score: scoreAudio(f.type || '', f.bitrate || 0) + hostBonus(url, apiBase) + mimePenalty(f.type || '') });
        }
      }
      for (const f of (d.formatStreams || [])) {
        if (f.type && f.type.includes('mp4')) {
          const url = rewriteGoogle(f.url, apiBase);
          out.push({ url: url, mime: f.type, audioOnly: false, score: scoreAudio(f.type, 0) - 5 + hostBonus(url, apiBase) + mimePenalty(f.type) });
        }
      }
    } catch { /* both failed */ }
  }
  out.sort((a, b) => b.score - a.score);
  return out;
}

function scoreAudio(mime, bitrate) {
  let s = 0;
  const m = mime.toLowerCase();
  if (m.includes('mp4a') || m.includes('audio/mp4') || m.includes('audio/aac')) s += 50;
  else if (m.includes('mpeg') || m.includes('mp3')) s += 40;
  else if (m.includes('opus') || m.includes('webm')) s -= 20;
  if (bitrate) s += Math.min(20, bitrate / 8000);
  return s;
}

export function canPlaySrc(url) {
  if (typeof document === 'undefined') return true;
  const a = document.getElementById('media') || document.createElement('audio');
  const u = url.split('?')[0].toLowerCase();
  if (u.endsWith('.webm') || u.includes('audio/webm')) return !!a.canPlayType('audio/webm; codecs="opus"');
  if (u.endsWith('.flac') || u.includes('audio/flac')) return a.canPlayType('audio/flac') !== '';
  return true;
}

export async function resolveStream(track) {
  if (track.file) return URL.createObjectURL(track.file);
  if (track.provider === 'itunes') {
    const trackId = (track.id || '').replace('itunes:', '');
    if (trackId) {
      try {
        const d = await jget(`https://itunes.apple.com/lookup?id=${trackId}`);
        const r = (d.results || [])[0];
        if (r && r.previewUrl) {
          track.streamUrl = r.previewUrl;
          return r.previewUrl;
        }
      } catch { /* fall back to stored url */ }
    }
    if (!track.streamUrl) throw new Error('no preview url');
    return track.streamUrl;
  }
  if (track.provider === 'audius') return audius.streamUrl(track.audiusId || (track.id || '').replace('audius:', ''));
  if (track.provider === 'archive') return archive.resolve(track);
  if (track.provider === 'youtube') return youtube.resolve(track);
  throw new Error('unknown source');
}

export async function fetchWithProgress(url, onProgress) {
  const r = await fetch(url);
  if (!r.ok) throw new Error('HTTP ' + r.status);
  const total = Number(r.headers.get('content-length')) || 0;
  const reader = r.body.getReader();
  const chunks = [];
  let got = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    chunks.push(value);
    got += value.length;
    if (onProgress && total) onProgress(got / total, got, total);
  }
  const type = (r.headers.get('content-type') || 'audio/mpeg').split(';')[0];
  return new Blob(chunks, { type });
}
