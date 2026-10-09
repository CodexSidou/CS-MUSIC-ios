const PIPED_INSTANCES = [
  'https://api.piped.private.coffee',
  'https://pipedapi.tokhmi.xyz',
  'https://piped-api.lunar.icu',
  'https://pipedapi.leptons.xyz',
];

const REQ_TIMEOUT = 12000;

let _innertubePromise = null;

function getInnertube() {
  if (!_innertubePromise) {
    _innertubePromise = (async () => {
      const mod = require('youtubei.js');
      const { Innertube } = mod;
      return Innertube.create({ generate_session_locally: true, retrieve_player: false });
    })();
  }
  return _innertubePromise;
}

function chooseFormat(fmts, targetSec) {
  const score = (f) => {
    let s = 0;
    const mt = f.mime_type || '';
    if (mt.includes('audio/mp4')) s += 8;
    if (mt.includes('opus')) s += 4;
    if (targetSec && f.approx_duration_ms) {
      const d = f.approx_duration_ms / 1000;
      if (d >= targetSec - 5 && d <= targetSec + 45) s += 6;
      else if (d >= targetSec * 0.5) s += 1;
    } else if (!targetSec) {
      s += 3;
    }
    if (f.bitrate) s += Math.log2(f.bitrate) / 20;
    return s;
  };
  return fmts.slice().sort((a, b) => score(b) - score(a))[0];
}

async function resolveInnertube(track) {
  const yt = await getInnertube();
  const info = await yt.getInfo(track.id);
  const playability = info.playability_status?.status;
  if (playability && playability !== 'OK') {
    throw new Error(
      `YouTube playability ${playability}${info.playability_status?.reason ? ': ' + info.playability_status.reason : ''}`
    );
  }
  const fmts = (info.streaming_data?.adaptive_formats || []).filter(
    (f) => f.has_audio && (f.mime_type || '').startsWith('audio/')
  );
  if (!fmts.length) throw new Error('No audio formats returned by YouTube');

  const pick = chooseFormat(fmts, track.duration);
  let url = pick.url;
  if (!url && pick.has_signature) {
    url = await pick.decipher(yt.session.player);
  }
  if (!url) throw new Error('Empty stream URL after decipher');

  const mime = (pick.mime_type || '').split(';')[0].trim() || 'audio/mp4';
  return {
    url,
    mimeType: mime,
    ext: mime.includes('webm') ? '.webm' : '.m4a',
    provider: 'youtube',
    size: pick.content_length,
  };
}

export async function searchYouTube(query) {
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
      if (!items.length) continue;

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
    } catch (_err) {
      // Try next instance
    }
  }

  // Fallback to youtube search suggestions
  try {
    const sugRes = await fetch(
      `https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=${encodeURIComponent(query)}`,
      { signal: AbortSignal.timeout(4000) }
    );
    if (sugRes.ok) {
      const sugData = await sugRes.json();
      const suggestions = sugData[1] || [];
      return suggestions.slice(0, 5).map((s, idx) => ({
        id: `yt-sug-${idx}`,
        title: s,
        artist: 'YouTube',
        provider: 'youtube',
      }));
    }
  } catch (_) {}

  return [];
}

export async function resolveYouTube(track) {
  try {
    return await resolveInnertube(track);
  } catch (err) {
    console.warn('innertube resolve failed:', err.message);
  }
  for (const base of PIPED_INSTANCES) {
    try {
      const ctrl = new AbortController();
      const t = setTimeout(() => ctrl.abort(), 8000);
      const res = await fetch(`${base}/streams/${track.id}`, { signal: ctrl.signal });
      clearTimeout(t);
      if (!res.ok) continue;
      const data = await res.json();
      if (!data) continue;
      const streams = (data.audioStreams || []).concat(data.videoStreams || []);
      if (!streams.length) continue;

      const best = streams.find((s) => (s.mimeType || '').includes('audio')) || streams[0];
      const mime = (best.mimeType || '').split(';')[0].trim() || 'audio/mp4';
      return {
        url: best.proxyUrl || best.url,
        mimeType: mime,
        ext: mime.includes('webm') ? '.webm' : '.m4a',
        provider: 'youtube',
        size: best.contentLength,
      };
    } catch (_) {}
  }

  throw new Error('No YouTube direct stream available on mirrors');
}