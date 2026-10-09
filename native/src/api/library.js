import AsyncStorage from '@react-native-async-storage/async-storage';

const KEY = 'cs.music.library.v1';

export async function loadLibrary() {
  try {
    const raw = await AsyncStorage.getItem(KEY);
    if (!raw) return [];
    const list = JSON.parse(raw);
    return Array.isArray(list) ? list : [];
  } catch (err) {
    console.warn('loadLibrary failed:', err);
    return [];
  }
}

export async function saveLibrary(list) {
  try {
    await AsyncStorage.setItem(KEY, JSON.stringify(list));
  } catch (err) {
    console.warn('saveLibrary failed:', err);
  }
}