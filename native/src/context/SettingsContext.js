import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { useColorScheme } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import * as FileSystem from 'expo-file-system/legacy';
import { darkTheme, lightTheme } from '../theme';
import { translations } from '../i18n/translations';

const SETTINGS_KEY = '@cs_music_settings_v1';
const SettingsContext = createContext(null);

const DEFAULT_SETTINGS = {
  themeMode: 'dark', // 'dark' | 'light' | 'system'
  language: 'en', // 'en' | 'fr' | 'ar'
  autoplay: true,
  audioQuality: 'high',
  sleepTimerMinutes: null,
};

export function SettingsProvider({ children }) {
  const systemColorScheme = useColorScheme();
  const [settings, setSettings] = useState(DEFAULT_SETTINGS);
  const [cacheSize, setCacheSize] = useState(0);

  useEffect(() => {
    AsyncStorage.getItem(SETTINGS_KEY)
      .then((raw) => {
        if (raw) {
          try {
            const parsed = JSON.parse(raw);
            setSettings((prev) => ({ ...prev, ...parsed }));
          } catch (_) {}
        }
      })
      .catch((_) => {});
    FileSystem.getInfoAsync(FileSystem.cacheDirectory)
      .then((info) => {
        if (info?.exists && info?.size) {
          setCacheSize(info.size);
        }
      })
      .catch((_) => {});
  }, []);

  const saveSettings = useCallback(async (updated) => {
    setSettings(updated);
    try {
      await AsyncStorage.setItem(SETTINGS_KEY, JSON.stringify(updated));
    } catch (_) {}
  }, []);

  const setThemeMode = useCallback(
    (mode) => {
      saveSettings({ ...settings, themeMode: mode });
    },
    [settings, saveSettings]
  );

  const setLanguage = useCallback(
    (lang) => {
      saveSettings({ ...settings, language: lang });
    },
    [settings, saveSettings]
  );

  const setAutoplay = useCallback(
    (val) => {
      saveSettings({ ...settings, autoplay: val });
    },
    [settings, saveSettings]
  );

  const setSleepTimer = useCallback(
    (minutes) => {
      saveSettings({ ...settings, sleepTimerMinutes: minutes });
    },
    [settings, saveSettings]
  );

  const clearCache = useCallback(async () => {
    try {
      const cacheDir = FileSystem.cacheDirectory;
      if (cacheDir) {
        const info = await FileSystem.getInfoAsync(cacheDir);
        if (info.exists) {
          const files = await FileSystem.readDirectoryAsync(cacheDir);
          for (const file of files) {
            await FileSystem.deleteAsync(`${cacheDir}${file}`, { idempotent: true });
          }
        }
      }
      setCacheSize(0);
    } catch (_) {}
  }, []);

  const activeTheme = useMemo(() => {
    if (settings.themeMode === 'system') {
      return systemColorScheme === 'light' ? lightTheme : darkTheme;
    }
    return settings.themeMode === 'light' ? lightTheme : darkTheme;
  }, [settings.themeMode, systemColorScheme]);

  const lang = settings.language || 'en';
  const isRTL = lang === 'ar';

  const t = useCallback(
    (key) => {
      const dict = translations[lang] || translations.en;
      return dict[key] || translations.en[key] || key;
    },
    [lang]
  );

  const value = useMemo(
    () => ({
      settings,
      theme: activeTheme,
      language: lang,
      isRTL,
      cacheSize,
      t,
      setThemeMode,
      setLanguage,
      setAutoplay,
      setSleepTimer,
      clearCache,
    }),
    [
      settings,
      activeTheme,
      lang,
      isRTL,
      cacheSize,
      t,
      setThemeMode,
      setLanguage,
      setAutoplay,
      setSleepTimer,
      clearCache,
    ]
  );

  return <SettingsContext.Provider value={value}>{children}</SettingsContext.Provider>;
}

export function useSettings() {
  return useContext(SettingsContext);
}
