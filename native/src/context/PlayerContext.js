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
import { resolveAudioStream } from '../api/providers';
import { useSettings } from './SettingsContext';
import { useLibrary } from './LibraryContext';

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

export function PlayerProvider({ children }) {
  const { settings, setSleepTimer } = useSettings();
  const { addToHistory } = useLibrary();

  const player = getPlayer();
  const status = useAudioPlayerStatus(player);
  const [queue, setQueue] = useState([]);
  const [index, setIndex] = useState(-1);
  const [isPlayerOpen, setIsPlayerOpen] = useState(false);
  const [isShuffle, setIsShuffle] = useState(false);
  const [repeatMode, setRepeatMode] = useState('off'); // 'off' | 'all' | 'one'
  const [playbackSpeed, setPlaybackSpeed] = useState(1.0);

  const current = index >= 0 && index < queue.length ? queue[index] : null;
  const sleepTimerRef = useRef(null);

  useEffect(() => {
    ensureAudioMode().catch((err) => console.warn('audio mode error:', err));
  }, []);

  // Sleep Timer Handler
  useEffect(() => {
    if (sleepTimerRef.current) {
      clearTimeout(sleepTimerRef.current);
      sleepTimerRef.current = null;
    }
    if (settings.sleepTimerMinutes && settings.sleepTimerMinutes > 0) {
      sleepTimerRef.current = setTimeout(() => {
        if (player) player.pause();
        setSleepTimer(null);
      }, settings.sleepTimerMinutes * 60 * 1000);
    }
    return () => {
      if (sleepTimerRef.current) clearTimeout(sleepTimerRef.current);
    };
  }, [settings.sleepTimerMinutes, player, setSleepTimer]);

  const playStream = useCallback(
    async (track, url) => {
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
        if (addToHistory) {
          addToHistory(track);
        }
      } catch (err) {
        console.warn('play failed:', err);
        throw err;
      }
    },
    [player, addToHistory]
  );

  const playTrack = useCallback(
    async (track, list) => {
      const q = list && list.length ? list : [track];
      const ix = Math.max(0, q.findIndex((t) => t.id === track.id));
      setQueue(q);
      setIndex(ix);
      const t = q[ix];
      if (t.fileUri) {
        await playStream(t, t.fileUri);
        return;
      }
      const stream = await resolveAudioStream(t);
      await playStream(t, stream.url);
    },
    [playStream]
  );

  const toggle = useCallback(() => {
    if (!player) return;
    if (player.playing) {
      player.pause();
    } else if (player.currentTime > 0) {
      player.play();
    } else if (current) {
      playTrack(current, queue);
    }
  }, [player, current, playTrack, queue]);

  const seekTo = useCallback(
    (seconds) => {
      if (player) {
        player.seekTo(Math.max(0, seconds));
      }
    },
    [player]
  );

  const playAt = useCallback(
    async (ix) => {
      if (ix < 0 || ix >= queue.length) return;
      setIndex(ix);
      const t = queue[ix];
      if (t.fileUri) {
        await playStream(t, t.fileUri);
        return;
      }
      const stream = await resolveAudioStream(t);
      await playStream(t, stream.url);
    },
    [queue, playStream]
  );

  const next = useCallback(() => {
    if (repeatMode === 'one' && current) {
      seekTo(0);
      player.play();
      return;
    }
    if (isShuffle && queue.length > 1) {
      const randIx = Math.floor(Math.random() * queue.length);
      playAt(randIx);
      return;
    }
    if (index < queue.length - 1) {
      playAt(index + 1);
    } else if (repeatMode === 'all' && queue.length > 0) {
      playAt(0);
    }
  }, [index, queue.length, playAt, repeatMode, current, isShuffle, seekTo, player]);

  const prev = useCallback(() => {
    if (player.currentTime > 3) {
      seekTo(0);
      return;
    }
    if (index > 0) {
      playAt(index - 1);
    } else {
      seekTo(0);
    }
  }, [index, playAt, player, seekTo]);

  // Autoplay progression when track finishes
  useEffect(() => {
    if (status.didJustFinish) {
      if (settings.autoplay !== false) {
        const id = setTimeout(() => next(), 150);
        return () => clearTimeout(id);
      }
    }
  }, [status.didJustFinish, next, settings.autoplay]);

  const toggleShuffle = useCallback(() => {
    setIsShuffle((prev) => !prev);
  }, []);

  const toggleRepeat = useCallback(() => {
    setRepeatMode((prev) => {
      if (prev === 'off') return 'all';
      if (prev === 'all') return 'one';
      return 'off';
    });
  }, []);

  const changeSpeed = useCallback(
    (speed) => {
      setPlaybackSpeed(speed);
      // If native player supports playbackRate, can set it
    },
    []
  );

  const openFullPlayer = useCallback(() => setIsPlayerOpen(true), []);
  const closeFullPlayer = useCallback(() => setIsPlayerOpen(false), []);

  const value = useMemo(
    () => ({
      player,
      status,
      queue,
      index,
      current,
      isPlayerOpen,
      isShuffle,
      repeatMode,
      playbackSpeed,
      playTrack,
      playAt,
      toggle,
      next,
      prev,
      seekTo,
      toggleShuffle,
      toggleRepeat,
      changeSpeed,
      openFullPlayer,
      closeFullPlayer,
    }),
    [
      player,
      status,
      queue,
      index,
      current,
      isPlayerOpen,
      isShuffle,
      repeatMode,
      playbackSpeed,
      playTrack,
      playAt,
      toggle,
      next,
      prev,
      seekTo,
      toggleShuffle,
      toggleRepeat,
      changeSpeed,
      openFullPlayer,
      closeFullPlayer,
    ]
  );

  return <PlayerContext.Provider value={value}>{children}</PlayerContext.Provider>;
}

export function usePlayer() {
  return useContext(PlayerContext);
}