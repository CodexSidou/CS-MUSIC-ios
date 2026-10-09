import 'react-native-get-random-values';

const PIPED_INSTANCES = [
  'https://api.piped.private.coffee',
  'https://pipedapi.kavin.rocks',
  'https://pipedapi.adminforge.de',
];

const REQ_TIMEOUT = 20000;

let _yt = null;

async function getInnertube() {
  if (_yt) return _yt;
  const { Innertube } = require('youtubei.js');
  _yt = await Innertube.create({
    client_type: 'WEB',
    generate_session_locally: true,
  });
  return _yt;
}

function thumbOfUrls(urls) {
  if (!urls) return undefined;
  if (urls.best) return urls.best;
  if (Array.isArray(urls.items) && urls.items.length) return urls.items[0].url;
  if (Array.isArray(urls) && urls.length) {
    const s = urls.slice().sort((a, b) => (b.width || 0) - (a.width || 0));
    return s[0]?.url;
  }
  return undefined;
}

function pickAudioFormat(formats) {
  if (!Array.isArray(formats) || !formats.length) return undefined;
  const audio = formats.filter((f) => {
    const m = f.mimeType && f.mimeType.toLowerCase();
    return m && m.startsWith('audio/');
  });
  const pool = audio.length ? audio : formats.filter((f) => f.has_audio !== false);
  if (!pool.length) return formats[0];
  const score = (f) => {
    const m = (f.mimeType || '').toLowerCase();
    let s = 0;
    if (m.includes('mp4') || m.includes('m4a')) s += 100;
    else if (m.includes('webm')) s += 30;
    if (m.includes('opus')) s += 10;
    else if (m.includes('aac')) s += 40;
    else if (m.includes('mp4a')) s += 30;
    s += (f.bitrate || 0) / 10000;
    return s;
  };
  return pool.slice().sort((a, b) => score(b) - score(a))[0];
}

export async function searchYouTube(query) {
  try {
    const yt = await getInnertube();
    const res = await yt.search(query);
    const videos = (res?.videos || []).filter((v) => v && v.id);
    return videos.map((v) => ({
      id: v.id,
      title: v.title?.toString?.() || v.title || 'Unknown',
      artist: v.author?.name?.toString?.() || v.author?.name || 'YouTube',
      album: undefined,
      cover: v.thumbnail && thumbOfUrls(v.thumbnail),
      duration: v.duration?.seconds ?? undefined,
      provider: 'youtube',
      streamUrl: undefined,
    }));
  } catch (err) {
    console.warn('youtubei.js search failed:', err);
    return searchPiped(query);
  }
}

async function searchPiped(query) {
  for (const base of PIPED_INSTANCES) {
    try {
      const ctrl = new AbortController();
      const t = setTimeout(() => ctrl.abort(), REQ_TIMEOUT);
      const res = await fetch(`${base}/search?q=${encodeURIComponent(query)}&filter=music_songs`, {
        signal: ctrl.signal,
      });
      clearTimeout(t);
      if (!res.ok) continue;
      const data = await res.json();
      if (!data || !Array.isArray(data.items)) continue;
      const items = data.items.filter((i) => i && /v=([\w-]{11})/.test(i.url || ''));
      return items.map((i) => ({
        id: (i.url.match(/v=([\w-]{11})/) || [])[1] || i.id,
        title: i.title || i.name || 'Unknown',
        artist: i.uploaderName || 'YouTube',
        album: i.album,
        cover: i.thumbnail,
        duration: typeof i.duration === 'number' ? i.duration : undefined,
        provider: 'youtube',
        streamUrl: undefined,
      }));
    } catch (err) {
      console.warn(`piped search ${base} failed:`, err);
    }
  }
  return [];
}

export async function resolveYouTube(track) {
  const errors = [];
  try {
    return await resolveInnertube(track.id);
  } catch (err) {
    errors.push(err.message || String(err));
  }
  for (const base of PIPED_INSTANCES) {
    try {
      const stream = await resolvePiped(base, track.id);
      if (stream) return stream;
    } catch (err) {
      errors.push(String(err.message || err));
    }
  }
  throw new Error('No YouTube stream available: ' + errors.join(' | '));
}

async function resolveInnertube(videoId) {
  const yt = await getInnertube();
  const info = await yt.getStreamingData(videoId);
  if (!info) throw new Error('no streaming data');
  const ps = info.playabilityStatus;
  if (ps && ps.status && ps.status !== 'OK') {
    throw new Error(ps.reason || ps.status);
  }
  const sd = info.streamingData;
  if (!sd) throw new Error('streaming data empty');
  const fmts = (sd.formats || []).concat(sd.adaptiveFormats || []);
  const fmt = pickAudioFormat(fmts);
  if (!fmt) throw new Error('no formats');
  if (!fmt.url) throw new Error('no deciphered url');
  const mime = (fmt.mimeType || '').split(';')[0].trim();
  return {
    url: fmt.url,
    mimeType: mime,
    ext: extForMime(mime),
    provider: 'youtube',
    size: fmt.content_length,
  };
}

async function resolvePiped(base, videoId) {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), REQ_TIMEOUT);
  const res = await fetch(`${base}/streams/${videoId}`, { signal: ctrl.signal });
  clearTimeout(t);
  if (!res.ok) throw new Error(`piped streams http ${res.status}`);
  const data = await res.json();
  if (!data) throw new Error('empty piped response');
  if (data.error) throw new Error(String(data.error));
  const streams = (data.audioStreams || []).concat(data.videoStreams || []);
  const score = (s) => {
    const m = (s.mimeType || '').toLowerCase();
    let sc = 0;
    if (m.includes('mp4') || m.includes('m4a')) sc += 100;
    else if (m.includes('webm')) sc += 30;
    if (m.includes('opus')) sc += 10;
    if (m.includes('aac') || m.includes('mp4a')) sc += 30;
    sc += (s.bitrate || 0) / 10000;
    if (s.proxyUrl) sc += 5;
    return sc;
  };
  if (!streams.length) throw new Error('piped has no streams');
  const best = streams.slice().sort((a, b) => score(b) - score(a))[0];
  const mime = (best.mimeType || '').split(';')[0].trim();
  return {
    url: best.proxyUrl || best.url,
    mimeType: mime,
    ext: extForMime(mime),
    provider: 'piped',
    size: best.contentLength,
  };
}

function extForMime(mime) {
  const m = (mime || '').toLowerCase();
  if (m.includes('webm')) return '.webm';
  if (m.includes('ogg')) return '.ogg';
  if (m.includes('aac')) return '.aac';
  if (m.includes('mp3')) return '.mp3';
  if (m.includes('mpeg') || m.includes('mpg')) return '.mpg';
  return '.m4a';
}