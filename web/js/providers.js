const PIPED_INSTANCES = [
  'https://api.piped.private.coffee',
  'https://pipedapi.tokhmi.xyz',
  'https://piped-api.lunar.icu',
  'https://pipedapi.leptons.xyz',
];

const INVIDIOUS_INSTANCES = [
  'https://inv.nadeko.net',
  'https://invidious.nerdvpn.de',
  'https://invidious.private.coffee',
  'https://yt.artemislena.eu',
];

const AUDIUS_HOSTS = [
  'https://discoveryprovider.audius.co',
  'https://discoveryprovider2.audius.co',
  'https://discoveryprovider3.audius.co',
];

let scClientId = null;
const SC_FALLBACK_CLIENT_ID = 'IysGhRTI7AdLDwRrKw5hy3UqwhvyW3Vw';

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
  return url.replace(/\/\d+x\d+bb\./, '/600x600bb.').replace(/\/\d+x\d+-\w\./, '/600x600bb.');
}

function fmtDur(sec) {
  if (!sec || sec < 0 || !isFinite(sec)) return -1;
  return Math.round(sec);
}

// SoundCloud Provider
export const soundcloud = {
  async getClientId() {
    if (scClientId) return scClientId;
    try {
      const ctrl = new AbortController();
      const t = setTimeout(() => ctrl.abort(), 5000);
      const res = await fetch('https://soundcloud.com', { signal: ctrl.signal });
      clearTimeout(t);
      if (res.ok) {
        const html = await res.text();
        const scriptUrls = Array.from(html.matchAll(/src="([^"]*sndcdn\.com\/assets\/[^"]+\.js)"/g)).map(m => m[1]);
        for (const sUrl of scriptUrls.slice(-4)) {
          try {
            const sRes = await fetch(sUrl, { signal: AbortSignal.timeout(3000) });
            const sCode = await sRes.text();
            const m = sCode.match(/client_id[:=]["']([a-zA-Z0-9]{32})["']/);
            if (m && m[1]) {
              scClientId = m[1];
              return scClientId;
            }
          } catch (_) {}
        }
      }
    } catch (_) {}
    scClientId = SC_FALLBACK_CLIENT_ID;
    return scClientId;
  },

  async search(q) {
    const cid = await this.getClientId();
    try {
      const d = await jget(`https://api-v2.soundcloud.com/search/tracks?q=${encodeURIComponent(q)}&client_id=${cid}&limit=25`);
      const items = d?.collection || [];
      return items.filter(t => t && t.title).map(t => {
        const trans = t.media?.transcodings || [];
        const prog = trans.find(tr => tr.format?.protocol === 'progressive') || trans[0];
        return {
          id: 'sc:' + t.id,
          provider: 'soundcloud',
          title: t.title,
          artist: t.user?.username || 'SoundCloud Artist',
          album: '',
          artwork: t.artwork_url ? t.artwork_url.replace('-large', '-t500x500') : null,
          duration: fmtDur((t.duration || 0) / 1000),
          streamUrl: null,
          transcodingUrl: prog?.url,
          saved: false,
        };
      });
    } catch (err) {
      console.warn('SoundCloud search failed:', err);
      return [];
    }
  },

  async resolve(track) {
    const cid = await this.getClientId();
    let transUrl = track.transcodingUrl;
    if (!transUrl) {
      const q = `${track.artist} - ${track.title}`.replace(/unknown/i, '').trim();
      const results = await this.search(q || track.title);
      if (results.length > 0 && results[0].transcodingUrl) {
        transUrl = results[0].transcodingUrl;
      }
    }
    if (!transUrl) throw new Error('No SoundCloud stream found');

    const res = await jget(`${transUrl}?client_id=${cid}`);
    if (!res.url) throw new Error('Empty SoundCloud stream url');
    return res.url;
  }
};

// Spotify Provider
export const spotify = {
  isUrl(text) {
    if (!text || typeof text !== 'string') return false;
    return /open\.spotify\.com\/(track|album|playlist)\/([a-zA-Z0-9]+)/i.test(text.trim());
  },

  parseUrl(text) {
    const m = String(text || '').trim().match(/open\.spotify\.com\/(track|album|playlist)\/([a-zA-Z0-9]+)/i);
    return m ? { type: m[1].toLowerCase(), id: m[2] } : null;
  },

  async resolve(url) {
    const parsed = this.parseUrl(url);
    if (!parsed) throw new Error('Invalid Spotify link');

    const embedUrl = `https://open.spotify.com/embed/${parsed.type}/${parsed.id}`;
    try {
      const ctrl = new AbortController();
      const t = setTimeout(() => ctrl.abort(), 8000);
      const res = await fetch(embedUrl, { signal: ctrl.signal });
      clearTimeout(t);
      if (res.ok) {
        const html = await res.text();
        const m = html.match(/<script id="__NEXT_DATA__" type="application\/json">([\s\S]*?)<\/script>/);
        if (m) {
          const nextData = JSON.parse(m[1]);
          const entity = nextData?.props?.pageProps?.state?.data?.entity;
          if (entity) {
            return this.formatEntity(entity, parsed, url);
          }
        }
      }
    } catch (_) {}

    // Fallback oEmbed
    const oembed = await jget(`https://open.spotify.com/oembed?url=${encodeURIComponent(url)}`);
    const parts = (oembed.title || 'Unknown').split(' - ');
    const title = parts.length > 1 ? parts[1].trim() : parts[0].trim();
    const artist = parts.length > 1 ? parts[0].trim() : (oembed.author_name || 'Spotify');

    return {
      type: parsed.type,
      title: oembed.title || 'Spotify Music',
      artwork: oembed.thumbnail_url,
      tracks: [{
        id: 'sp:' + parsed.id,
        provider: 'spotify',
        title,
        artist,
        album: parsed.type === 'album' ? title : '',
        artwork: oembed.thumbnail_url,
        duration: -1,
        saved: false,
      }],
    };
  },

  formatEntity(entity, parsed, rawUrl) {
    const images = entity.visualIdentity?.image || [];
    const bestCover = images.sort((a, b) => (b.maxWidth || 0) - (a.maxWidth || 0))[0]?.url || images[0]?.url;

    if (parsed.type === 'track') {
      const artists = (entity.artists || []).map(a => a.name).join(', ') || 'Spotify Artist';
      return {
        type: 'track',
        title: entity.name || entity.title || 'Unknown',
        artwork: bestCover,
        tracks: [{
          id: 'sp:' + (entity.id || parsed.id),
          provider: 'spotify',
          title: entity.name || entity.title || 'Unknown',
          artist: artists,
          album: entity.album?.name || '',
          artwork: bestCover,
          duration: entity.duration ? Math.round(entity.duration / 1000) : -1,
          streamUrl: entity.audioPreview?.url,
          saved: false,
        }],
      };
    }

    const rawList = entity.trackList || [];
    const tracks = rawList.map((item, idx) => {
      const trackId = item.id || item.uri?.replace('spotify:track:', '') || `${parsed.id}-${idx}`;
      return {
        id: 'sp:' + trackId,
        provider: 'spotify',
        title: item.title || item.name || 'Unknown',
        artist: item.subtitle || entity.artists?.[0]?.name || 'Spotify',
        album: entity.name || '',
        artwork: bestCover,
        duration: item.duration ? Math.round(item.duration / 1000) : -1,
        streamUrl: item.audioPreview?.url,
        saved: false,
      };
    });

    return {
      type: parsed.type,
      title: entity.name || 'Spotify Collection',
      artist: entity.owner?.name || entity.artists?.[0]?.name || 'Spotify',
      artwork: bestCover,
      tracks,
    };
  },

  async search(q) {
    if (this.isUrl(q)) {
      const res = await this.resolve(q);
      return res.tracks || [];
    }
    try {
      const itunesList = await itunes.search(q);
      return itunesList.map(t => ({
        ...t,
        id: 'sp:' + t.id.replace('itunes:', ''),
        provider: 'spotify',
      }));
    } catch (_) {
      return [];
    }
  }
};

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
    try {
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
    } catch (_) {
      return [];
    }
  }
};

export const audius = {
  async search(q) {
    for (const host of AUDIUS_HOSTS) {
      try {
        const d = await jget(`${host}/v1/tracks/search?query=${encodeURIComponent(q)}&app_name=CSMusic&limit=25`);
        if (!d.data || !d.data.length) continue;
        return d.data.map(x => ({
          id: 'audius:' + x.id,
          provider: 'audius',
          title: x.title || 'Unknown',
          artist: (x.user && x.user.name) || '',
          album: '',
          artwork: x.artwork && (x.artwork['480x480'] || x.artwork['150x150']),
          duration: fmtDur(x.duration),
          streamUrl: `${host}/v1/tracks/${x.id}/stream?app_name=CSMusic`,
          audiusId: x.id,
          saved: false
        }));
      } catch (_) {}
    }
    return [];
  },
  async trending() {
    for (const host of AUDIUS_HOSTS) {
      try {
        const d = await jget(`${host}/v1/tracks/trending?app_name=CSMusic&limit=25`);
        if (!d.data || !d.data.length) continue;
        return d.data.map(x => ({
          id: 'audius:' + x.id,
          provider: 'audius',
          title: x.title || 'Unknown',
          artist: (x.user && x.user.name) || '',
          album: '',
          artwork: x.artwork && (x.artwork['480x480'] || x.artwork['150x150']),
          duration: fmtDur(x.duration),
          streamUrl: `${host}/v1/tracks/${x.id}/stream?app_name=CSMusic`,
          audiusId: x.id,
          saved: false
        }));
      } catch (_) {}
    }
    return [];
  }
};

export const youtube = {
  async search(q) {
    for (const base of PIPED_INSTANCES) {
      try {
        const d = await jget(`${base}/search?q=${encodeURIComponent(q)}&filter=music_songs`, 6000);
        if (d && Array.isArray(d.items) && d.items.length) {
          return d.items.filter(x => x && (x.type === 'stream' || x.url)).map(x => {
            const vId = (x.url || '').match(/v=([\w-]{11})/)?.[1] || x.id;
            return {
              id: 'yt:' + vId,
              provider: 'youtube',
              title: x.title || 'Unknown',
              artist: x.uploaderName || 'YouTube',
              album: '',
              artwork: x.thumbnail,
              duration: fmtDur(x.duration),
              streamUrl: null,
              ytId: vId,
              saved: false,
            };
          });
        }
      } catch (_) {}
    }

    // Suggestions fallback
    try {
      const sug = await jget(`https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=${encodeURIComponent(q)}`, 4000);
      const list = sug[1] || [];
      return list.slice(0, 10).map((name, i) => ({
        id: `yt:sug-${i}`,
        provider: 'youtube',
        title: name,
        artist: 'YouTube Music',
        duration: -1,
        saved: false,
      }));
    } catch (_) {
      return [];
    }
  },

  async resolve(track) {
    let ytId = track.ytId || ((track.id || '').startsWith('yt:') && !track.id.startsWith('yt:sug') ? track.id.replace('yt:', '') : null);
    if (!ytId) {
      const q = `${track.artist} - ${track.title}`.replace(/unknown/i, '').trim() || track.title;
      const list = await this.search(q);
      const found = list.find(x => x.ytId);
      if (found) ytId = found.ytId;
    }
    if (!ytId) throw new Error('No YouTube stream found');

    for (const base of PIPED_INSTANCES) {
      try {
        const d = await jget(`${base}/streams/${ytId}`, 6000);
        const streams = (d.audioStreams || []).concat(d.videoStreams || []);
        if (streams.length > 0) {
          const best = streams.find(s => (s.mimeType || '').includes('audio')) || streams[0];
          return best.proxyUrl || best.url;
        }
      } catch (_) {}
    }

    for (const base of INVIDIOUS_INSTANCES) {
      try {
        const d = await jget(`${base}/api/v1/videos/${ytId}`, 6000);
        const streams = (d.adaptiveFormats || []).filter(s => (s.type || '').includes('audio'));
        if (streams.length > 0) {
          return streams[0].url;
        }
      } catch (_) {}
    }

    throw new Error('No YouTube stream found');
  }
};

export const archive = {
  async search(q) {
    try {
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
    } catch (_) {
      return [];
    }
  },
  async resolve(track) {
    const id = track.archId || (track.id || '').replace('arch:', '');
    const d = await jget(`https://archive.org/metadata/${encodeURIComponent(id)}`);
    const files = (d.files || []).filter(f => /\.mp3$/i.test(f.name));
    const pick = files.find(f => (f.format || '').includes('VBR')) || files[0];
    if (!pick) throw new Error('No audio file found');
    return `https://archive.org/download/${encodeURIComponent(id)}/${encodeURIComponent(pick.name)}`;
  }
};

/**
 * Universal Stream Resolver (spotDL Multi-tier Strategy for PWA)
 */
export async function resolveStream(track) {
  if (track.file) return URL.createObjectURL(track.file);
  if (track.streamUrl) return track.streamUrl;

  const searchQ = `${track.artist} - ${track.title}`.replace(/unknown/i, '').trim();

  // 1. Try SoundCloud first (full progressive MP3 streams)
  try {
    const scStream = await soundcloud.resolve(track);
    if (scStream) return scStream;
  } catch (_) {}

  // 2. Try YouTube / Piped / Invidious
  try {
    const ytStream = await youtube.resolve(track);
    if (ytStream) return ytStream;
  } catch (_) {}

  // 3. Try iTunes preview
  try {
    const itunesMatches = await itunes.search(searchQ || track.title);
    if (itunesMatches.length > 0 && itunesMatches[0].streamUrl) {
      return itunesMatches[0].streamUrl;
    }
  } catch (_) {}

  // 4. Try Archive.org
  if (track.provider === 'archive' || track.archId) {
    try {
      return await archive.resolve(track);
    } catch (_) {}
  }

  // 5. Try Audius
  if (track.provider === 'audius') {
    return track.streamUrl;
  }

  throw new Error(`Unable to resolve stream for "${track.title}"`);
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
