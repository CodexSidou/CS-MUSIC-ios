const REQ_TIMEOUT = 15000;

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
      'https://itunes.apple.com/search?media=music&entity=song&limit=20&term=' +
      encodeURIComponent(query);
    const data = await j(url);
    return (data.results || []).map((r) => ({
      id: String(r.trackId),
      title: r.trackName,
      artist: r.artistName,
      album: r.collectionName,
      cover: r.artworkUrl100 ? r.artworkUrl100.replace('100x100bb', '300x300bb') : undefined,
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
        id: d.identifier,
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
  try {
    const data = await j(
      `https://archive.org/metadata/${encodeURIComponent(track.id)}`
    );
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
    if (!files.length) throw new Error('no audio files');
    const f = files[0];
    const name = encodeURIComponent(f.name);
    return {
      url: `https://archive.org/download/${encodeURIComponent(track.id)}/${name}`,
      mimeType: f.format || 'audio/mpeg',
      ext: extOf(f.name),
      provider: 'archive',
      size: f.size,
    };
  } catch (err) {
    throw err;
  }
}

function extOf(name) {
  const m = /\.(\w{2,4})$/.exec(name || '');
  return m ? '.' + m[1].toLowerCase() : '.mp3';
}