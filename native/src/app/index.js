import React, { useCallback, useState } from 'react';
import {
  Alert,
  FlatList,
  Keyboard,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { usePlayer } from '../context/PlayerContext';
import { TrackRow, DownloadState } from '../components/TrackRow';
import { searchYouTube } from '../api/youtube';
import { searchITunes, searchArchive } from '../api/providers';
import { theme } from '../theme';

function SectionLabel({ text }) {
  return <Text style={styles.section}>{text}</Text>;
}

export default function SearchScreen() {
  const { playTrack, download, downloads } = usePlayer();
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [searching, setSearching] = useState(false);
  const [msg, setMsg] = useState(null);

  const runSearch = useCallback(async () => {
    const q = query.trim();
    if (!q) return;
    Keyboard.dismiss();
    setSearching(true);
    setMsg(null);
    setResults([]);
    try {
      const [yt, it, ar] = await Promise.allSettled([
        searchYouTube(q),
        searchITunes(q),
        searchArchive(q),
      ]);
      const ytList = yt.status === 'fulfilled' ? yt.value : [];
      const itList = it.status === 'fulfilled' ? it.value : [];
      const arList = ar.status === 'fulfilled' ? ar.value : [];
      const seen = new Set();
      const merged = [];
      for (const t of [...ytList, ...itList, ...arList]) {
        const k = `${t.provider}:${t.id}`;
        if (seen.has(k)) continue;
        seen.add(k);
        merged.push(t);
      }
      setResults(merged);
      if (!merged.length) setMsg('No results. Try a different spelling.');
    } catch (err) {
      setMsg('Search failed: ' + (err.message || String(err)));
    } finally {
      setSearching(false);
    }
  }, [query]);

  const onSave = useCallback(
    (track) => {
      download(track).catch((err) =>
        Alert.alert('Download failed', err && err.message ? err.message : String(err))
      );
    },
    [download]
  );

  const onPlay = useCallback(
    (track) => {
      playTrack(track, results).catch((err) =>
        Alert.alert('Playback failed', err && err.message ? err.message : String(err))
      );
    },
    [playTrack, results]
  );

  return (
    <View style={styles.page}>
      <View style={styles.inputRow}>
        <TextInput
          style={styles.input}
          value={query}
          onChangeText={setQuery}
          placeholder="Song, artist, video..."
          placeholderTextColor={theme.textDim}
          autoCapitalize="none"
          autoCorrect={false}
          returnKeyType="search"
          onSubmitEditing={runSearch}
        />
        <Pressable
          onPress={runSearch}
          disabled={searching}
          style={({ pressed }) => [styles.searchBtn, pressed && styles.pressed]}
        >
          <Text style={styles.searchBtnText}>{searching ? '\u21BB' : '\u2315'}</Text>
        </Pressable>
      </View>

      {msg ? <Text style={styles.msg}>{msg}</Text> : null}

      <FlatList
        data={results}
        keyExtractor={(item) => `${item.provider}:${item.id}`}
        renderItem={({ item }) => (
          <TrackRow
            track={item}
            onPlay={() => onPlay(item)}
            extra={
              <Pressable
                onPress={() => onSave(item)}
                style={({ pressed }) => [styles.saveBtn, pressed && styles.pressed]}
              >
                {downloads[item.id] && downloads[item.id].status === 'downloading' ? (
                  <DownloadState track={item} downloads={downloads} />
                ) : (
                  <Text style={styles.saveBtnText}>{'\u2B06'}</Text>
                )}
              </Pressable>
            }
          />
        )}
        contentContainerStyle={styles.list}
        ListHeaderComponent={
          results.length ? <SectionLabel text={`${results.length} results`} /> : null
        }
      />
    </View>
  );
}

const styles = StyleSheet.create({
  page: { flex: 1, backgroundColor: theme.bg, paddingTop: 10 },
  inputRow: { flexDirection: 'row', paddingHorizontal: 12, marginBottom: 8 },
  input: {
    flex: 1,
    backgroundColor: theme.surface,
    color: theme.text,
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 10,
    fontSize: 15,
    borderWidth: 1,
    borderColor: theme.border,
  },
  searchBtn: {
    marginLeft: 8,
    width: 46,
    borderRadius: 12,
    backgroundColor: theme.indigo,
    alignItems: 'center',
    justifyContent: 'center',
  },
  searchBtnText: { color: '#fff', fontSize: 18, fontWeight: '700' },
  pressed: { opacity: 0.7 },
  section: { color: theme.textDim, fontSize: 12, marginBottom: 8, marginTop: 6 },
  msg: { color: theme.textDim, paddingHorizontal: 14, paddingVertical: 8, fontSize: 13 },
  list: { paddingHorizontal: 12, paddingBottom: 20 },
  saveBtn: {
    minWidth: 36,
    height: 36,
    borderRadius: 10,
    backgroundColor: theme.surface2,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 10,
  },
  saveBtnText: { color: theme.cyan, fontSize: 16 },
});