const REQ_TIMEOUT = 12000;

export function isSpotifyUrl(text) {
  if (!text || typeof text !== 'string') return false;
  const t = text.trim();
  return (
    /open\.spotify\.com\/(track|album|playlist)\/([a-zA-Z0-9]+)/i.test(t) ||
    /spotify:(track|album|playlist):([a-zA-Z0-9]+)/i.test(t)
  );
}

export function parseSpotifyTypeAndId(url) {
  const t = String(url || '').trim();
  const webMatch = t.match(/open\.spotify\.com\/(track|album|playlist)\/([a-zA-Z0-9]+)/i);
  if (webMatch) {
    return { type: webMatch[1].toLowerCase(), id: webMatch[2] };
  }
  const uriMatch = t.match(/spotify:(track|album|playlist):([a-zA-Z0-9]+)/i);
  if (uriMatch) {
    return { type: uriMatch[1].toLowerCase(), id: uriMatch[2] };
  }
  return null;
}

export async function resolveSpotifyUrl(url) {
  const parsed = parseSpotifyTypeAndId(url);
  if (!parsed) throw new Error('Invalid Spotify link. Please paste a track, album, or playlist link.');

  const embedUrl = `https://open.spotify.com/embed/${parsed.type}/${parsed.id}`;
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), REQ_TIMEOUT);

  try {
    const res = await fetch(embedUrl, {
      signal: ctrl.signal,
      headers: {
        'User-Agent':
          'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1',
      },
    });
    clearTimeout(timer);

    if (!res.ok) {
      // Fallback to oEmbed
      return resolveSpotifyOEmbed(url, parsed);
    }

    const html = await res.text();
    const nextDataMatch = html.match(
      /<script id="__NEXT_DATA__" type="application\/json">([\s\S]*?)<\/script>/
    );

    if (nextDataMatch) {
      try {
        const nextData = JSON.parse(nextDataMatch[1]);
        const entity = nextData?.props?.pageProps?.state?.data?.entity;
        if (entity) {
          return formatSpotifyEntity(entity, parsed);
        }
      } catch (_err) {
        console.warn('Failed to parse __NEXT_DATA__:', _err);
      }
    }

    // Fallback to oEmbed
    return resolveSpotifyOEmbed(url, parsed);
  } catch (_err) {
    clearTimeout(timer);
    return resolveSpotifyOEmbed(url, parsed);
  }
}

async function resolveSpotifyOEmbed(url, parsed) {
  try {
    const oembedUrl = `https://open.spotify.com/oembed?url=${encodeURIComponent(url)}`;
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), 8000);
    const res = await fetch(oembedUrl, { signal: ctrl.signal });
    clearTimeout(timer);
    if (!res.ok) throw new Error(`Spotify oEmbed HTTP ${res.status}`);
    const data = await res.json();

    const titleParts = (data.title || 'Unknown').split(' - ');
    const songTitle = titleParts.length > 1 ? titleParts[1].trim() : titleParts[0].trim();
    const artistName = titleParts.length > 1 ? titleParts[0].trim() : (data.author_name || 'Spotify');

    const track = {
      id: `spotify-${parsed.id}`,
      spotifyId: parsed.id,
      title: songTitle,
      artist: artistName,
      album: parsed.type === 'album' ? songTitle : undefined,
      cover: data.thumbnail_url,
      duration: undefined,
      provider: 'spotify',
      spotifyUrl: url,
    };

    return {
      type: parsed.type,
      title: data.title || 'Spotify Music',
      cover: data.thumbnail_url,
      tracks: [track],
    };
  } catch (_err) {
    throw new Error('Could not load Spotify info. Check your network or URL.');
  }
}

function formatSpotifyEntity(entity, parsed) {
  const images = entity.visualIdentity?.image || [];
  const bestCover =
    images.sort((a, b) => (b.maxWidth || 0) - (a.maxWidth || 0))[0]?.url ||
    images[0]?.url;

  if (parsed.type === 'track') {
    const artists = (entity.artists || []).map((a) => a.name).join(', ') || 'Spotify Artist';
    const track = {
      id: `spotify-${entity.id || parsed.id}`,
      spotifyId: entity.id || parsed.id,
      title: entity.name || entity.title || 'Unknown',
      artist: artists,
      album: entity.album?.name,
      cover: bestCover,
      duration: entity.duration ? Math.round(entity.duration / 1000) : undefined,
      previewUrl: entity.audioPreview?.url,
      provider: 'spotify',
      spotifyUrl: `https://open.spotify.com/track/${entity.id || parsed.id}`,
    };

    return {
      type: 'track',
      title: track.title,
      artist: track.artist,
      cover: bestCover,
      tracks: [track],
    };
  }

  // Playlist or Album
  const rawList = entity.trackList || [];
  const tracks = rawList.map((item, index) => {
    const rawId = item.id || item.uri?.replace('spotify:track:', '') || `${parsed.id}-${index}`;
    return {
      id: `spotify-${rawId}`,
      spotifyId: rawId,
      title: item.title || item.name || 'Unknown',
      artist: item.subtitle || entity.artists?.[0]?.name || entity.owner?.name || 'Spotify',
      album: entity.name,
      cover: bestCover,
      duration: item.duration ? Math.round(item.duration / 1000) : undefined,
      previewUrl: item.audioPreview?.url,
      provider: 'spotify',
      spotifyUrl: `https://open.spotify.com/track/${rawId}`,
    };
  });

  return {
    type: parsed.type,
    title: entity.name || 'Spotify Collection',
    artist: entity.owner?.name || entity.artists?.[0]?.name || 'Spotify',
    cover: bestCover,
    trackCount: tracks.length,
    tracks,
  };
}
