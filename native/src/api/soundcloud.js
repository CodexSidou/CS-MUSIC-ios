let _cachedClientId = null;
let _clientIdPromise = null;

const FALLBACK_CLIENT_ID = 'IysGhRTI7AdLDwRrKw5hy3UqwhvyW3Vw';
const REQ_TIMEOUT = 10000;

export async function getSoundCloudClientId() {
  if (_cachedClientId) return _cachedClientId;
  if (_clientIdPromise) return _clientIdPromise;

  _clientIdPromise = (async () => {
    try {
      const ctrl = new AbortController();
      const timer = setTimeout(() => ctrl.abort(), 6000);
      const res = await fetch('https://soundcloud.com', {
        signal: ctrl.signal,
        headers: {
          'User-Agent':
            'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko)',
        },
      });
      clearTimeout(timer);

      if (res.ok) {
        const html = await res.text();
        const scriptUrls = Array.from(
          html.matchAll(/src="([^"]*sndcdn\.com\/assets\/[^"]+\.js)"/g)
        ).map((m) => m[1]);

        for (const sUrl of scriptUrls.slice(-4)) {
          try {
            const sRes = await fetch(sUrl, { signal: AbortSignal.timeout(4000) });
            const sCode = await sRes.text();
            const m = sCode.match(/client_id[:=]["']([a-zA-Z0-9]{32})["']/);
            if (m && m[1]) {
              _cachedClientId = m[1];
              return _cachedClientId;
            }
          } catch (_) {}
        }
      }
    } catch (err) {
      console.warn('SoundCloud dynamic client_id lookup failed:', err);
    }
    _cachedClientId = FALLBACK_CLIENT_ID;
    return _cachedClientId;
  })();

  return _clientIdPromise;
}

export async function searchSoundCloud(query) {
  try {
    const clientId = await getSoundCloudClientId();
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), REQ_TIMEOUT);
    const url = `https://api-v2.soundcloud.com/search/tracks?q=${encodeURIComponent(
      query
    )}&client_id=${clientId}&limit=15`;
    const res = await fetch(url, { signal: ctrl.signal });
    clearTimeout(timer);

    if (!res.ok) return [];
    const data = await res.json();
    const items = data?.collection || [];

    return items
      .filter((t) => t && t.title)
      .map((t) => {
        const trans = t.media?.transcodings || [];
        const progressive =
          trans.find((tr) => tr.format?.protocol === 'progressive') || trans[0];

        return {
          id: `sc-${t.id}`,
          scId: t.id,
          title: t.title,
          artist: t.user?.username || 'SoundCloud Artist',
          album: undefined,
          cover: t.artwork_url ? t.artwork_url.replace('-large', '-t500x500') : undefined,
          duration: t.duration ? Math.round(t.duration / 1000) : undefined,
          provider: 'soundcloud',
          transcodingUrl: progressive?.url,
        };
      });
  } catch (err) {
    console.warn('SoundCloud search failed:', err);
    return [];
  }
}

export async function resolveSoundCloudStream(track) {
  const clientId = await getSoundCloudClientId();

  // If we already have the transcodingUrl
  let transcodingUrl = track.transcodingUrl;

  // Otherwise search for the track on SoundCloud
  if (!transcodingUrl) {
    const searchQuery = `${track.title} ${track.artist || ''}`.trim();
    const matches = await searchSoundCloud(searchQuery);
    if (matches.length > 0 && matches[0].transcodingUrl) {
      transcodingUrl = matches[0].transcodingUrl;
    }
  }

  if (!transcodingUrl) {
    throw new Error('No SoundCloud audio stream stream found');
  }

  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), REQ_TIMEOUT);
  const streamRes = await fetch(`${transcodingUrl}?client_id=${clientId}`, {
    signal: ctrl.signal,
  });
  clearTimeout(timer);

  if (!streamRes.ok) {
    throw new Error(`SoundCloud stream error: HTTP ${streamRes.status}`);
  }

  const streamData = await streamRes.json();
  if (!streamData.url) {
    throw new Error('Empty SoundCloud stream URL');
  }

  return {
    url: streamData.url,
    mimeType: 'audio/mpeg',
    ext: '.mp3',
    provider: 'soundcloud',
  };
}
