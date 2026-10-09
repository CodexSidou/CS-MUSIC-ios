import { resolveSoundCloudStream, searchSoundCloud } from './soundcloud';
import { resolveYouTube } from './youtube';

const REQ_TIMEOUT = 12000;

async function j(url) {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), REQ_TIMEOUT);
  try {
    const res = await fetch(url, { signal: ctrl.signal });
    clearTimeout(t);
    if (!res.ok) throw new Error(`http ${res.status}`);
    return await res.json();
  } catch (err) {
    clearTimeout(t);
    throw err;
  }
}

export async function searchITunes(query) {
  try {
    const url =
      'https://itunes.apple.com/search?media=music&entity=song&limit=25&term=' +
      encodeURIComponent(query);
    const data = await j(url);
    return (data.results || []).map((r) => ({
      id: `itunes-${r.trackId}`,
      trackId: r.trackId,
      title: r.trackName,
      artist: r.artistName,
      album: r.collectionName,
      cover: r.artworkUrl100 ? r.artworkUrl100.replace('100x100bb', '600x600bb') : undefined,
      duration: r.trackTimeMillis ? Math.round(r.trackTimeMillis / 1000) : undefined,
      provider: 'itunes',
      previewUrl: r.previewUrl,
      streamUrl: null,
    }));
  } catch (err) {
    console.warn('itunes search failed:', err);
    return [];
  }
}

export async function fetchTrendingCharts(country = 'us') {
  try {
    const url = `https://itunes.apple.com/${country.toLowerCase()}/rss/topsongs/limit=30/json`;
    const data = await j(url);
    const entries = data?.feed?.entry || [];
    return entries.map((e, idx) => {
      const title = e['im:name']?.label || 'Top Song';
      const artist = e['im:artist']?.label || 'Top Artist';
      const images = e['im:image'] || [];
      const cover = images.length ? images[images.length - 1]?.label : undefined;
      const id = e.id?.attributes?.['im:id'] || `chart-${idx}`;
      const preview = e.link?.find((l) => l.attributes?.type?.includes('audio'))?.attributes?.href;

      return {
        id: `chart-${id}`,
        title,
        artist,
        cover,
        provider: 'charts',
        previewUrl: preview,
        streamUrl: null,
      };
    });
  } catch (err) {
    console.warn('fetchTrendingCharts failed:', err);
    return [];
  }
}

export async function searchArchive(query) {
  try {
    const url =
      'https://archive.org/advancedsearch.php?q=' +
      encodeURIComponent('mediatype:audio AND (' + query + ')') +
      '&fl[]=identifier,title,creator&rows=20&output=json';
    const data = await j(url);
    const docs = data?.response?.docs || [];
    const out = [];
    for (const d of docs) {
      if (!d.identifier) continue;
      out.push({
        id: `archive-${d.identifier}`,
        archiveId: d.identifier,
        title: d.title || d.identifier,
        artist: d.creator || 'Archive.org',
        album: undefined,
        cover: `https://archive.org/services/img/${d.identifier}`,
        duration: undefined,
        provider: 'archive',
      });
    }
    return out;
  } catch (err) {
    console.warn('archive search failed:', err);
    return [];
  }
}

export async function resolveArchive(track) {
  const archiveId = track.archiveId || track.id.replace('archive-', '');
  const data = await j(`https://archive.org/metadata/${encodeURIComponent(archiveId)}`);
  const files = (data.files || []).filter((f) => {
    const n = ((f.name || '') + (f.format || '')).toLowerCase();
    return (
      !n.includes('_files.xml') &&
      !n.includes('_meta.xml') &&
      !n.includes('_reviews.xml') &&
      !f.name.endsWith('.h5') &&
      (f.format === 'VBR MP3' ||
        f.format === 'MP3' ||
        f.format === 'FLAC' ||
        f.format === 'Ogg Vorbis' ||
        f.format === '64Kbps M3U')
    );
  });
  if (!files.length) throw new Error('No audio files found in archive item');
  const f = files[0];
  const name = encodeURIComponent(f.name);
  const ext = extOf(f.name);
  return {
    url: `https://archive.org/download/${encodeURIComponent(archiveId)}/${name}`,
    mimeType: f.format || 'audio/mpeg',
    ext,
    provider: 'archive',
    size: f.size,
  };
}

function extOf(name) {
  const m = /\.(\w{2,4})$/.exec(name || '');
  return m ? '.' + m[1].toLowerCase() : '.mp3';
}

/**
 * Check if a URL is an Apple/Spotify ~30s preview clip
 */
function isPreviewUrl(url) {
  if (!url || typeof url !== 'string') return true;
  return /\.apple\.com\/.*\/.*\.m4a/i.test(url)
      || /audio-ak-spotify/i.test(url)
      || /p\.scdn\.co/i.test(url)
      || /audio-fa\.scdn\.co/i.test(url)
      || /preview/i.test(url);
}

function cleanQuery(artist, title) {
  const cleanTitle = (title || '')
    .replace(/\s*[\(\[](feat\.|ft\.|remastered|official|video|audio|deluxe|version|edit|remix).*?[\)\]]/gi, '')
    .replace(/-\s*single/gi, '')
    .trim();
  const cleanArtist = (artist || '').replace(/\s*(feat\.|ft\.).*/gi, '').trim();
  return {
    q1: cleanArtist + ' ' + cleanTitle,
    q2: cleanTitle,
  };
}

export async function resolveSaavnStream(track) {
  const instances = [
    'https://jiosaavn-api-2.vercel.app',
    'https://saavn-api.vercel.app',
  ];
  const { q1, q2 } = cleanQuery(track.artist, track.title);
  for (const q of [q1, q2]) {
    if (!q || !q.trim()) continue;
    for (const base of instances) {
      try {
        const d = await j(`${base}/search/songs?query=${encodeURIComponent(q.trim())}`);
        const list = d?.results || (Array.isArray(d?.data) ? d.data : d?.data?.results) || [];
        if (!list.length) continue;
        const match = list.find(s => s.downloadUrl && s.downloadUrl.length > 0);
        if (match) {
          const dl = match.downloadUrl;
          const best = dl.find(u => u.quality === '320kbps')
            || dl.find(u => u.quality === '160kbps')
            || dl.find(u => u.quality === '96kbps')
            || dl[dl.length - 1];
          if (best && best.link) {
            return {
              url: best.link,
              mimeType: 'audio/mp4',
              ext: '.m4a',
              provider: 'saavn',
            };
          }
        }
      } catch (_) { }
    }
  }
  return null;
}

export async function resolveAudiusStream(track) {
  const AUDIUS_HOSTS = [
    'https://discoveryprovider.audius.co',
    'https://discoveryprovider2.audius.co',
  ];
  const { q1, q2 } = cleanQuery(track.artist, track.title);
  for (const q of [q1, q2]) {
    if (!q) continue;
    for (const host of AUDIUS_HOSTS) {
      try {
        const d = await j(`${host}/v1/tracks/search?query=${encodeURIComponent(q)}&app_name=CSMusic&limit=10`);
        const list = d?.data || [];
        const match = list.find(x => x.duration && x.duration >= 45) || list[0];
        if (match && match.id) {
          return {
            url: `${host}/v1/tracks/${match.id}/stream?app_name=CSMusic`,
            mimeType: 'audio/mpeg',
            ext: '.mp3',
            provider: 'audius',
          };
        }
      } catch (_) { }
    }
  }
  return null;
}

/**
 * Universal Stream Resolver — Full-Track Priority
 * Resolves any track to a playable/downloadable audio URL.
 * Always resolves full-length tracks (2-5 minutes). Never plays or downloads 20s/30s preview clips.
 */
export async function resolveAudioStream(track) {
  // 1. If the track already has a verified full-length stream URL, use it
  if (track.streamUrl && !isPreviewUrl(track.streamUrl)) {
    return {
      url: track.streamUrl,
      mimeType: 'audio/mp4',
      ext: '.m4a',
      provider: track.provider,
    };
  }

  // 2. Try Saavn first (High-quality 320k/160k full audio stream)
  try {
    const saavnRes = await resolveSaavnStream(track);
    if (saavnRes && saavnRes.url && !isPreviewUrl(saavnRes.url)) {
      return saavnRes;
    }
  } catch (_) { }

  // 3. Try Audius (Full DRM-free MP3 stream)
  try {
    const audiusRes = await resolveAudiusStream(track);
    if (audiusRes && audiusRes.url && !isPreviewUrl(audiusRes.url)) {
      return audiusRes;
    }
  } catch (_) { }

  // 4. Archive.org — always full files
  if (track.provider === 'archive' || (track.id && track.id.startsWith('archive-'))) {
    return resolveArchive(track);
  }

  // 5. SoundCloud direct (if track is from SC)
  if (track.provider === 'soundcloud' || track.transcodingUrl) {
    try {
      return await resolveSoundCloudStream(track);
    } catch (e) {
      console.warn('Direct SC stream resolve failed, falling back:', e.message);
    }
  }

  // 6. YouTube full-length audio stream
  if (track.provider === 'youtube' && track.id && !track.id.startsWith('yt-sug-')) {
    try {
      const ytStream = await resolveYouTube(track);
      if (ytStream && ytStream.url && !isPreviewUrl(ytStream.url)) return ytStream;
    } catch (err) {
      console.warn('YouTube resolve failed:', err.message);
    }
  }

  // 7. SoundCloud full-track search fallback
  const searchQ = `${track.artist} - ${track.title}`.replace(/unknown/i, '').trim();
  try {
    const scResults = await searchSoundCloud(searchQ || track.title);
    if (scResults.length > 0) {
      let candidates = scResults.filter((m) => m.duration && m.duration >= 45);
      const targetSec = track.duration;
      if (targetSec && candidates.length) {
        candidates.sort(
          (a, b) => Math.abs(a.duration - targetSec) - Math.abs(b.duration - targetSec)
        );
      }
      if (!candidates.length) candidates = scResults;
      for (const match of candidates.slice(0, 3)) {
        try {
          const stream = await resolveSoundCloudStream(match);
          if (stream && stream.url && !isPreviewUrl(stream.url)) return stream;
        } catch (_) { }
      }
    }
  } catch (err) {
    console.warn('SoundCloud fallback search failed:', err.message);
  }

  // 8. YouTube search fallback (for non-YT tracks)
  if (track.provider !== 'youtube') {
    try {
      const ytStream = await resolveYouTube({
        ...track,
        provider: 'youtube',
        id: null,
      });
      if (ytStream && ytStream.url && !isPreviewUrl(ytStream.url)) return ytStream;
    } catch (_) { }
  }

  // 9. CRITICAL: Never return 20s/30s preview as a full song stream
  throw new Error(`Unable to find a full-length audio stream for "${track.title}".`);
}