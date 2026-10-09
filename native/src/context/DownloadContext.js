import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import * as FileSystem from 'expo-file-system/legacy';
import { resolveAudioStream } from '../api/providers';

const DOWNLOADS_KEY = '@cs_music_downloads_v1';
const DownloadContext = createContext(null);

function sanitize(name) {
  const s = String(name || 'track')
    .replace(/[\\/:*?"<>|\u0000-\u001f]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 60);
  return s || 'track';
}

export function DownloadProvider({ children }) {
  const [downloadTasks, setDownloadTasks] = useState({});
  const [completedDownloads, setCompletedDownloads] = useState([]);
  const activeResumables = useRef({});

  useEffect(() => {
    AsyncStorage.getItem(DOWNLOADS_KEY)
      .then((raw) => {
        if (raw) {
          setCompletedDownloads(JSON.parse(raw) || []);
        }
      })
      .catch((_) => {});
  }, []);

  const saveCompleted = useCallback(async (list) => {
    setCompletedDownloads(list);
    try {
      await AsyncStorage.setItem(DOWNLOADS_KEY, JSON.stringify(list));
    } catch (_) {}
  }, []);

  const updateTask = useCallback((id, patch) => {
    setDownloadTasks((prev) => ({
      ...prev,
      [id]: { ...(prev[id] || {}), ...patch },
    }));
  }, []);

  const startDownload = useCallback(
    async (track) => {
      const id = track.id;
      if (downloadTasks[id]?.status === 'downloading') return;

      updateTask(id, {
        id,
        track,
        status: 'resolving',
        progress: 0,
        error: null,
      });

      try {
        const stream = await resolveAudioStream(track);
        const musicDir = `${FileSystem.documentDirectory}downloads/`;

        const dirInfo = await FileSystem.getInfoAsync(musicDir);
        if (!dirInfo.exists) {
          await FileSystem.makeDirectoryAsync(musicDir, { intermediates: true });
        }

        const safeId = String(id).replace(/[^a-zA-Z0-9_-]/g, '_');
        const ext = stream.ext || '.mp3';
        const destUri = `${musicDir}${sanitize(track.title)}_${safeId}${ext}`;

        const resumable = FileSystem.createDownloadResumable(
          stream.url,
          destUri,
          {},
          (progressEvent) => {
            const total = progressEvent.totalBytesExpectedToWrite;
            const written = progressEvent.totalBytesWritten;
            if (total > 0) {
              updateTask(id, {
                status: 'downloading',
                progress: Math.min(0.99, written / total),
              });
            }
          }
        );

        activeResumables.current[id] = resumable;
        updateTask(id, { status: 'downloading', progress: 0.05 });

        const result = await resumable.downloadAsync();
        delete activeResumables.current[id];

        if (!result || !result.uri) {
          throw new Error('Download did not return a valid file URI');
        }

        const fileInfo = await FileSystem.getInfoAsync(result.uri);
        const downloadedItem = {
          ...track,
          fileUri: result.uri,
          fileSize: fileInfo.size || 0,
          downloadedAt: Date.now(),
        };

        updateTask(id, { status: 'completed', progress: 1, fileUri: result.uri });

        const nextCompleted = [
          downloadedItem,
          ...completedDownloads.filter((d) => d.id !== id),
        ];
        saveCompleted(nextCompleted);

        return result.uri;
      } catch (err) {
        delete activeResumables.current[id];
        updateTask(id, { status: 'failed', error: err.message || String(err) });
        throw err;
      }
    },
    [downloadTasks, completedDownloads, saveCompleted, updateTask]
  );

  const cancelDownload = useCallback(
    async (id) => {
      if (activeResumables.current[id]) {
        try {
          await activeResumables.current[id].cancelAsync();
        } catch (_) {}
        delete activeResumables.current[id];
      }
      updateTask(id, { status: 'failed', error: 'Cancelled' });
    },
    [updateTask]
  );

  const deleteDownloadedFile = useCallback(
    async (id) => {
      const item = completedDownloads.find((d) => d.id === id);
      if (item?.fileUri) {
        try {
          await FileSystem.deleteAsync(item.fileUri, { idempotent: true });
        } catch (_) {}
      }
      const nextCompleted = completedDownloads.filter((d) => d.id !== id);
      saveCompleted(nextCompleted);
      setDownloadTasks((prev) => {
        const copy = { ...prev };
        delete copy[id];
        return copy;
      });
    },
    [completedDownloads, saveCompleted]
  );

  const totalStorageBytes = useMemo(() => {
    return completedDownloads.reduce((acc, d) => acc + (d.fileSize || 0), 0);
  }, [completedDownloads]);

  const value = useMemo(
    () => ({
      downloadTasks,
      completedDownloads,
      totalStorageBytes,
      startDownload,
      cancelDownload,
      deleteDownloadedFile,
    }),
    [
      downloadTasks,
      completedDownloads,
      totalStorageBytes,
      startDownload,
      cancelDownload,
      deleteDownloadedFile,
    ]
  );

  return <DownloadContext.Provider value={value}>{children}</DownloadContext.Provider>;
}

export function useDownload() {
  return useContext(DownloadContext);
}
