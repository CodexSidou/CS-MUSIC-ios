import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import * as DocumentPicker from 'expo-document-picker';
import * as FileSystem from 'expo-file-system/legacy';

const LIB_KEY = '@cs_music_library_v1';
const PLAYLISTS_KEY = '@cs_music_playlists_v1';
const FAVORITES_KEY = '@cs_music_favorites_v1';
const HISTORY_KEY = '@cs_music_history_v1';

const LibraryContext = createContext(null);

export function LibraryProvider({ children }) {
  const [tracks, setTracks] = useState([]);
  const [playlists, setPlaylists] = useState([]);
  const [favorites, setFavorites] = useState([]);
  const [history, setHistory] = useState([]);

  useEffect(() => {
    Promise.all([
      AsyncStorage.getItem(LIB_KEY),
      AsyncStorage.getItem(PLAYLISTS_KEY),
      AsyncStorage.getItem(FAVORITES_KEY),
      AsyncStorage.getItem(HISTORY_KEY),
    ])
      .then(([tRaw, pRaw, fRaw, hRaw]) => {
        if (tRaw) setTracks(JSON.parse(tRaw) || []);
        if (pRaw) setPlaylists(JSON.parse(pRaw) || []);
        if (fRaw) setFavorites(JSON.parse(fRaw) || []);
        if (hRaw) setHistory(JSON.parse(hRaw) || []);
      })
      .catch((err) => console.warn('loadAllData error:', err));
  }, []);

  const saveTracks = useCallback(async (list) => {
    setTracks(list);
    try {
      await AsyncStorage.setItem(LIB_KEY, JSON.stringify(list));
    } catch (_) {}
  }, []);

  const savePlaylists = useCallback(async (list) => {
    setPlaylists(list);
    try {
      await AsyncStorage.setItem(PLAYLISTS_KEY, JSON.stringify(list));
    } catch (_) {}
  }, []);

  const saveFavorites = useCallback(async (list) => {
    setFavorites(list);
    try {
      await AsyncStorage.setItem(FAVORITES_KEY, JSON.stringify(list));
    } catch (_) {}
  }, []);

  const saveHistory = useCallback(async (list) => {
    setHistory(list);
    try {
      await AsyncStorage.setItem(HISTORY_KEY, JSON.stringify(list));
    } catch (_) {}
  }, []);

  // Add track to recently played history
  const addToHistory = useCallback(
    (track) => {
      const filtered = history.filter((t) => t.id !== track.id);
      const next = [{ ...track, playedAt: Date.now() }, ...filtered].slice(0, 50);
      saveHistory(next);
    },
    [history, saveHistory]
  );

  const clearHistory = useCallback(() => {
    saveHistory([]);
  }, [saveHistory]);

  // Toggle Favorite
  const toggleFavorite = useCallback(
    (track) => {
      const isFav = favorites.some((t) => t.id === track.id);
      if (isFav) {
        const next = favorites.filter((t) => t.id !== track.id);
        saveFavorites(next);
      } else {
        const next = [track, ...favorites];
        saveFavorites(next);
      }
    },
    [favorites, saveFavorites]
  );

  const isFavorite = useCallback(
    (trackId) => {
      return favorites.some((t) => t.id === trackId);
    },
    [favorites]
  );

  // Import local audio file via document picker
  const importLocalAudio = useCallback(async () => {
    try {
      const res = await DocumentPicker.getDocumentAsync({
        type: ['audio/*', 'audio/mpeg', 'audio/mp4', 'audio/wav', 'audio/x-m4a'],
        copyToCacheDirectory: true,
      });

      if (res.canceled || !res.assets || !res.assets.length) return null;
      const asset = res.assets[0];

      // Copy permanently into private application storage
      const localMusicDir = `${FileSystem.documentDirectory}imported/`;
      const dirInfo = await FileSystem.getInfoAsync(localMusicDir);
      if (!dirInfo.exists) {
        await FileSystem.makeDirectoryAsync(localMusicDir, { intermediates: true });
      }

      const safeName = asset.name.replace(/[^a-zA-Z0-9._-]/g, '_');
      const destUri = `${localMusicDir}${Date.now()}_${safeName}`;
      await FileSystem.copyAsync({ from: asset.uri, to: destUri });

      const newTrack = {
        id: `imported-${Date.now()}`,
        title: asset.name.replace(/\.[^/.]+$/, ''),
        artist: 'Imported Audio',
        album: 'Local Files',
        fileUri: destUri,
        duration: undefined,
        fileSize: asset.size,
        provider: 'local',
        importedAt: Date.now(),
      };

      const nextTracks = [newTrack, ...tracks];
      saveTracks(nextTracks);
      return newTrack;
    } catch (err) {
      console.warn('importLocalAudio error:', err);
      throw err;
    }
  }, [tracks, saveTracks]);

  // Create Playlist
  const createPlaylist = useCallback(
    (name) => {
      const newPlaylist = {
        id: `pl-${Date.now()}`,
        name: name.trim() || 'New Playlist',
        createdAt: Date.now(),
        tracks: [],
      };
      const next = [newPlaylist, ...playlists];
      savePlaylists(next);
      return newPlaylist;
    },
    [playlists, savePlaylists]
  );

  // Delete Playlist
  const deletePlaylist = useCallback(
    (playlistId) => {
      const next = playlists.filter((p) => p.id !== playlistId);
      savePlaylists(next);
    },
    [playlists, savePlaylists]
  );

  // Add track to playlist
  const addTrackToPlaylist = useCallback(
    (playlistId, track) => {
      const next = playlists.map((p) => {
        if (p.id !== playlistId) return p;
        if (p.tracks.some((t) => t.id === track.id)) return p;
        return { ...p, tracks: [...p.tracks, track] };
      });
      savePlaylists(next);
    },
    [playlists, savePlaylists]
  );

  // Remove track from playlist
  const removeTrackFromPlaylist = useCallback(
    (playlistId, trackId) => {
      const next = playlists.map((p) => {
        if (p.id !== playlistId) return p;
        return { ...p, tracks: p.tracks.filter((t) => t.id !== trackId) };
      });
      savePlaylists(next);
    },
    [playlists, savePlaylists]
  );

  const value = useMemo(
    () => ({
      tracks,
      playlists,
      favorites,
      history,
      saveTracks,
      addToHistory,
      clearHistory,
      toggleFavorite,
      isFavorite,
      importLocalAudio,
      createPlaylist,
      deletePlaylist,
      addTrackToPlaylist,
      removeTrackFromPlaylist,
    }),
    [
      tracks,
      playlists,
      favorites,
      history,
      saveTracks,
      addToHistory,
      clearHistory,
      toggleFavorite,
      isFavorite,
      importLocalAudio,
      createPlaylist,
      deletePlaylist,
      addTrackToPlaylist,
      removeTrackFromPlaylist,
    ]
  );

  return <LibraryContext.Provider value={value}>{children}</LibraryContext.Provider>;
}

export function useLibrary() {
  return useContext(LibraryContext);
}
