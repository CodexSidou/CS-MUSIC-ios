import React, { useCallback } from 'react';
import { Alert, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { usePlayer } from '../context/PlayerContext';
import TrackRow, { DownloadState } from '../components/TrackRow';
import { theme } from '../theme';

export default function LibraryScreen() {
  const { library, playTrack, removeFromLibrary, downloads } = usePlayer();

  const onPlay = useCallback(
    (track) => {
      playTrack(track, library).catch((err) =>
        Alert.alert('Playback failed', err && err.message ? err.message : String(err))
      );
    },
    [playTrack, library]
  );

  const onDelete = useCallback(
    (track) => {
      Alert.alert('Remove from library?', track.title, [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Remove',
          style: 'destructive',
          onPress: () => removeFromLibrary(track.id).catch((err) => Alert.alert('Error', String(err))),
        },
      ]);
    },
    [removeFromLibrary]
  );

  return (
    <View style={styles.page}>
      {!library.length ? (
        <View style={styles.empty}>
          <Text style={styles.emptyGlyph}>{'\u266B'}</Text>
          <Text style={styles.emptyText}>Nothing saved yet.</Text>
          <Text style={styles.emptySub}>
            Use Search, then tap the upload button to download a song to this iPhone.
          </Text>
        </View>
      ) : (
        <FlatList
          data={library}
          keyExtractor={(item) => `${item.provider}:${item.id}`}
          ListHeaderComponent={
            <Text style={styles.section}>{library.length} saved</Text>
          }
          renderItem={({ item }) => (
            <TrackRow
              track={item}
              onPlay={() => onPlay(item)}
              extra={
                <View style={styles.actions}>
                  <DownloadState track={item} downloads={downloads} />
                  <Pressable
                    onPress={() => onDelete(item)}
                    style={({ pressed }) => [styles.delBtn, pressed && styles.pressed]}
                  >
                    <Text style={styles.delText}>{'\u2715'}</Text>
                  </Pressable>
                </View>
              }
            />
          )}
          contentContainerStyle={styles.list}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  page: { flex: 1, backgroundColor: theme.bg, paddingTop: 10 },
  list: { paddingHorizontal: 12, paddingBottom: 20 },
  section: { color: theme.textDim, fontSize: 12, marginBottom: 8, marginTop: 6 },
  empty: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 40 },
  emptyGlyph: { color: theme.indigo, fontSize: 44, marginBottom: 12 },
  emptyText: { color: theme.text, fontSize: 17, fontWeight: '700' },
  emptySub: { color: theme.textDim, textAlign: 'center', marginTop: 8, fontSize: 13 },
  actions: { flexDirection: 'row', alignItems: 'center' },
  delBtn: {
    width: 34,
    height: 34,
    borderRadius: 10,
    backgroundColor: theme.surface2,
    alignItems: 'center',
    justifyContent: 'center',
    marginLeft: 8,
  },
  delText: { color: theme.danger, fontSize: 14, fontWeight: '700' },
  pressed: { opacity: 0.7 },
});