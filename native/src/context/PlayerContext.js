import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { createAudioPlayer, setAudioModeAsync, useAudioPlayerStatus } from 'expo-audio';
import { Directory, File, Paths } from 'expo-file-system';
import { resolveYouTube } from '../api/youtube';
import { resolveArchive } from '../api/providers';
import { loadLibrary, saveLibrary } from '../api/library';

const PlayerContext = createContext(null);

let _player = null;
let _audioInit = null;

function getPlayer() {
  if (!_player) _player = createAudioPlayer(null);
  return _player;
}

function ensureAudioMode() {
  if (!_audioInit) {
    _audioInit = setAudioModeAsync({
      playsInSilentMode: true,
      shouldPlayInBackground: true,
      interruptionMode: 'doNotMix',
    });
  }
  return _audioInit;
}

function sanitize(name) {
  const s = String(name || 'track')
    .replace(/[\\/:*?"<>|\u0000-\u001f]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 80);
  return s || 'track';
}

export function PlayerProvider({ children }) {
  const player = getPlayer();
  const status = useAudioPlayerStatus(player);
  const [queue, setQueue] = useState([]);
  const [index, setIndex] = useState(-1);
  const [library, setLibrary] = useState([]);
  const [downloads, setDownloads] = useState({});
  const downloadRef = useRef({});
  const libraryLoaded = useRef(false);

  const current = index >= 0 && index < queue.length ? queue[index] : null;

  useEffect(() => {
    ensureAudioMode().catch((err) => console.warn('audio mode:', err));
    loadLibrary().then((list) => {
      libraryLoaded.current = true;
      setLibrary(list);
    });
  }, []);

  const updateDownload = useCallback((id, patch) => {
    setDownloads((prev) => ({ ...prev, [id]: { ...(prev[id] || {}), ...patch } }));
  }, []);

  const playStream = useCallback(
    async (track, url, save) => {
      try {
        await ensureAudioMode();
        player.replace(url);
        player.setActiveForLockScreen(true, {
          title: track.title,
          artist: track.artist,
          albumTitle: track.album,
          artworkUrl: track.cover,
        });
        player.play();
        if (save && !library.some((t) => t.id === track.id && t.fileUri)) {
          const next = library
            .filter((t) => t.id !== track.id)
            .concat([{ ...track, fileUri: url, provider: track.provider || 'stream' }]);
          setLibrary(next);
          saveLibrary(next);
        }
      } catch (err) {
        console.warn('play failed:', err);
        throw err;
      }
    },
    [player, library]
  );

  const playTrack = useCallback(
    async (track, list) => {
      const q = list && list.length ? list : [track];
      const ix = Math.max(0, q.findIndex((t) => t.id === track.id));
      setQueue(q);
      setIndex(ix);
      const t = q[ix];
      if (t.fileUri) {
        await playStream(t, t.fileUri, false);
        return;
      }
      const stream = await resolveFor(t);
      await playStream(t, stream.url, false);
    },
    [playStream]
  );

  const toggle = useCallback(() => {
    if (!player) return;
    if (player.playing) player.pause();
    else if (player.currentTime > 0) player.play();
    else if (current) playTrack(current, queue);
  }, [player, current, playTrack, queue]);

  const seekTo = useCallback((seconds) => {
    player.seekTo(Math.max(0, seconds));
  }, [player]);

  const playAt = useCallback(
    async (ix) => {
      if (ix < 0 || ix >= queue.length) return;
      setIndex(ix);
      const t = queue[ix];
      if (t.fileUri) {
        await playStream(t, t.fileUri, false);
        return;
      }
      const stream = await resolveFor(t);
      await playStream(t, stream.url, false);
    },
    [queue, playStream]
  );

  const next = useCallback(() => {
    if (index < queue.length - 1) playAt(index + 1);
  }, [index, queue.length, playAt]);

  const prev = useCallback(() => {
    if (index > 0) playAt(index - 1);
    else player.seekTo(0);
  }, [index, playAt, player]);

  useEffect(() => {
    if (status.didJustFinish && index < queue.length - 1) {
      const id = setTimeout(() => playAt(index + 1), 0);
      return () => clearTimeout(id);
    }
  }, [status.didJustFinish, index, queue.length, playAt]);

  const download = useCallback(
    async (track) => {
      const id = track.id;
      if (downloadRef.current[id]) return downloadRef.current[id];
      updateDownload(id, { status: 'resolving', progress: 0, error: undefined });
      const dir = new Directory(Paths.document, 'music');
      if (!dir.exists) dir.create();
      const promise = (async () => {
        try {
          const stream = await resolveFor(track);
          const dest = new File(dir, `${sanitize(track.title)}${stream.ext}`);
          if (dest.exists) dest.delete();
          updateDownload(id, { status: 'downloading', progress: 0 });
          const task = File.createDownloadTask(stream.url, dest, {
            onProgress: ({ bytesWritten, totalBytes }) => {
              if (bytesWritten > 0) {
                updateDownload(id, {
                  status: 'downloading',
                  progress: totalBytes ? bytesWritten / totalBytes : 0,
                });
              }
            },
          });
          const file = await task.downloadAsync();
          if (!file || !file.exists) throw new Error('download produced no file');
          updateDownload(id, { status: 'done', progress: 1, fileUri: file.uri });
          const next = library
            .filter((t) => t.id !== id)
            .concat([{ ...track, fileUri: file.uri }]);
          setLibrary(next);
          saveLibrary(next);
          return file.uri;
        } catch (err) {
          updateDownload(id, { status: 'error', error: err.message || String(err) });
          throw err;
        }
      })();
      downloadRef.current[id] = promise;
      try {
        return await promise;
      } finally {
        delete downloadRef.current[id];
      }
    },
    [library, updateDownload]
  );

  const removeFromLibrary = useCallback(
    async (id) => {
      const t = library.find((x) => x.id === id);
      if (t && t.fileUri && t.fileUri.startsWith('file')) {
        try {
          const f = new File(t.fileUri);
          if (f.exists) f.delete();
        } catch (err) {
          console.warn('delete file failed:', err);
        }
      }
      const next = library.filter((x) => x.id !== id);
      setLibrary(next);
      saveLibrary(next);
    },
    [library]
  );

  const value = useMemo(
    () => ({
      player,
      status,
      queue,
      index,
      current,
      library,
      downloads,
      playTrack,
      playAt,
      toggle,
      next,
      prev,
      seekTo,
      download,
      removeFromLibrary,
    }),
    [
      player,
      status,
      queue,
      index,
      current,
      library,
      downloads,
      playTrack,
      playAt,
      toggle,
      next,
      prev,
      seekTo,
      download,
      removeFromLibrary,
    ]
  );

  return <PlayerContext.Provider value={value}>{children}</PlayerContext.Provider>;
}

export function usePlayer() {
  return useContext(PlayerContext);
}

async function resolveFor(track) {
  if (track.provider === 'archive') {
    return resolveArchive(track);
  }
  if (track.previewUrl && track.provider === 'itunes') {
    return { url: track.previewUrl, mimeType: 'audio/mp4', ext: '.m4a', provider: 'itunes' };
  }
  return resolveYouTube(track);
}