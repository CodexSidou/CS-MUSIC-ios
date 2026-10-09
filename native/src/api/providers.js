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
      streamUrl: r.previewUrl,
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
        streamUrl: preview,
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
 * Universal Stream Resolver (spotDL Multi-Provider Strategy)
 * Resolves any track (Spotify, YouTube, SoundCloud, iTunes, Archive) to a playable/downloadable audio URL.
 */
export async function resolveAudioStream(track) {
  // 1. Direct stream / preview already present
  if (track.streamUrl) {
    return {
      url: track.streamUrl,
      mimeType: 'audio/mp4',
      ext: '.m4a',
      provider: track.provider,
    };
  }

  // 2. Archive.org
  if (track.provider === 'archive' || track.id.startsWith('archive-')) {
    return resolveArchive(track);
  }

  // 3. SoundCloud direct
  if (track.provider === 'soundcloud' || track.transcodingUrl) {
    try {
      return await resolveSoundCloudStream(track);
    } catch (e) {
      console.warn('Direct SC stream resolve failed, falling back:', e.message);
    }
  }

  // 4. YouTube full-length direct stream (primary for YouTube tracks)
  if (track.provider === 'youtube' && track.id && !track.id.startsWith('yt-sug-')) {
    try {
      const ytStream = await resolveYouTube(track);
      if (ytStream && ytStream.url) return ytStream;
    } catch (err) {
      console.warn('YouTube resolve failed:', err.message);
    }
  }

  // 5. SoundCloud full-track fallback (skips short previews / remixes)
  const searchQ = `${track.artist} - ${track.title}`.replace(/unknown/i, '').trim();
  try {
    const scResults = await searchSoundCloud(searchQ || track.title);
    if (scResults.length > 0) {
      let candidates = scResults.filter((m) => m.duration && m.duration >= 30);
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
          if (stream && stream.url) return stream;
        } catch (_) {}
      }
    }
  } catch (err) {
    console.warn('SoundCloud fallback search failed:', err.message);
  }

  // 6. Try iTunes search for previewUrl
  try {
    const itunesMatches = await searchITunes(searchQ || track.title);
    if (itunesMatches.length > 0 && itunesMatches[0].previewUrl) {
      return {
        url: itunesMatches[0].previewUrl,
        mimeType: 'audio/mp4',
        ext: '.m4a',
        provider: 'itunes',
      };
    }
  } catch (_) {}

  // 7. Spotify audio preview if available
  if (track.previewUrl) {
    return {
      url: track.previewUrl,
      mimeType: 'audio/mpeg',
      ext: '.mp3',
      provider: 'spotify',
    };
  }

  throw new Error(`Unable to find an audio stream for "${track.title}".`);
}