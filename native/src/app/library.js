import React, { useCallback, useMemo, useState } from 'react';
import {
  Alert,
  FlatList,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import { usePlayer } from '../context/PlayerContext';
import { TrackRow } from '../components/TrackRow';
import { theme } from '../theme';

function formatSize(bytes) {
  if (!bytes || bytes <= 0) return '';
  const mb = bytes / (1024 * 1024);
  return `${mb.toFixed(1)} MB`;
}

export default function LibraryScreen() {
  const { library, playTrack, removeFromLibrary, current, status } = usePlayer();
  const [searchFilter, setSearchFilter] = useState('');

  const filteredLibrary = useMemo(() => {
    if (!searchFilter.trim()) return library;
    const q = searchFilter.toLowerCase();
    return library.filter(
      (t) =>
        t.title?.toLowerCase().includes(q) ||
        t.artist?.toLowerCase().includes(q) ||
        t.album?.toLowerCase().includes(q)
    );
  }, [library, searchFilter]);

  const totalBytes = useMemo(() => {
    return library.reduce((acc, t) => acc + (t.fileSize || 0), 0);
  }, [library]);

  const onPlay = useCallback(
    (track) => {
      playTrack(track, filteredLibrary).catch((err) =>
        Alert.alert('Playback Error', err?.message || String(err))
      );
    },
    [playTrack, filteredLibrary]
  );

  const onPlayAll = useCallback(() => {
    if (!filteredLibrary.length) return;
    playTrack(filteredLibrary[0], filteredLibrary).catch((err) =>
      Alert.alert('Playback Error', err?.message || String(err))
    );
  }, [filteredLibrary, playTrack]);

  const onShuffleAll = useCallback(() => {
    if (!filteredLibrary.length) return;
    const shuffled = [...filteredLibrary].sort(() => Math.random() - 0.5);
    playTrack(shuffled[0], shuffled).catch((err) =>
      Alert.alert('Playback Error', err?.message || String(err))
    );
  }, [filteredLibrary, playTrack]);

  const onDelete = useCallback(
    (track) => {
      Alert.alert('Delete from iPhone?', track.title, [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: () =>
            removeFromLibrary(track.id).catch((err) =>
              Alert.alert('Error', String(err))
            ),
        },
      ]);
    },
    [removeFromLibrary]
  );

  return (
    <View style={styles.page}>
      {!library.length ? (
        <View style={styles.emptyContainer}>
          <View style={styles.emptyIconWrap}>
            <Ionicons name="cloud-download-outline" size={48} color={theme.indigo} />
          </View>
          <Text style={styles.emptyTitle}>No Downloaded Songs</Text>
          <Text style={styles.emptySub}>
            Search for any song or paste a Spotify / YouTube link in the Search tab to download it directly to your iPhone for offline listening.
          </Text>
        </View>
      ) : (
        <FlatList
          data={filteredLibrary}
          keyExtractor={(item) => `${item.provider}:${item.id}`}
          contentContainerStyle={styles.list}
          ListHeaderComponent={
            <View style={styles.header}>
              {/* Storage & Count Stats */}
              <View style={styles.statsRow}>
                <View style={styles.statItem}>
                  <Text style={styles.statValue}>{library.length}</Text>
                  <Text style={styles.statLabel}>Saved Tracks</Text>
                </View>
                {totalBytes > 0 ? (
                  <View style={styles.statItem}>
                    <Text style={styles.statValue}>{formatSize(totalBytes)}</Text>
                    <Text style={styles.statLabel}>Offline Storage</Text>
                  </View>
                ) : null}
              </View>

              {/* Action Buttons: Play All & Shuffle */}
              <View style={styles.actionRow}>
                <Pressable
                  onPress={onPlayAll}
                  style={({ pressed }) => [styles.playAllBtn, pressed && styles.pressed]}
                >
                  <LinearGradient
                    colors={theme.gradients.primary}
                    style={styles.gradientBtn}
                  >
                    <Ionicons name="play" size={16} color="#FFFFFF" />
                    <Text style={styles.playAllText}>Play All</Text>
                  </LinearGradient>
                </Pressable>

                <Pressable
                  onPress={onShuffleAll}
                  style={({ pressed }) => [styles.shuffleBtn, pressed && styles.pressed]}
                >
                  <Ionicons name="shuffle" size={18} color={theme.cyan} />
                  <Text style={styles.shuffleText}>Shuffle</Text>
                </Pressable>
              </View>

              {/* Library Search Filter */}
              {library.length > 5 ? (
                <View style={styles.searchBar}>
                  <Ionicons name="search" size={16} color={theme.textMuted} />
                  <TextInput
                    style={styles.searchInput}
                    value={searchFilter}
                    onChangeText={setSearchFilter}
                    placeholder="Search in downloaded songs..."
                    placeholderTextColor={theme.textMuted}
                  />
                  {searchFilter.length > 0 ? (
                    <Pressable onPress={() => setSearchFilter('')}>
                      <Ionicons name="close-circle" size={16} color={theme.textDim} />
                    </Pressable>
                  ) : null}
                </View>
              ) : null}
            </View>
          }
          renderItem={({ item }) => (
            <TrackRow
              track={item}
              isPlaying={current?.id === item.id && status.playing}
              onPlay={() => onPlay(item)}
              extra={
                <Pressable
                  onPress={() => onDelete(item)}
                  hitSlop={8}
                  style={({ pressed }) => [styles.deleteBtn, pressed && styles.pressed]}
                >
                  <Ionicons name="trash-outline" size={18} color={theme.danger} />
                </Pressable>
              }
            />
          )}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  page: {
    flex: 1,
    backgroundColor: theme.bg,
  },
  header: {
    paddingVertical: 14,
  },
  statsRow: {
    flexDirection: 'row',
    gap: 16,
    marginBottom: 16,
  },
  statItem: {
    flex: 1,
    backgroundColor: theme.surface,
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.05)',
  },
  statValue: {
    fontSize: 20,
    fontWeight: '800',
    color: theme.text,
    letterSpacing: -0.5,
  },
  statLabel: {
    fontSize: 11,
    fontWeight: '600',
    color: theme.textDim,
    marginTop: 2,
  },
  actionRow: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 14,
  },
  playAllBtn: {
    flex: 1,
    height: 46,
    borderRadius: 14,
    overflow: 'hidden',
  },
  gradientBtn: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  playAllText: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '700',
  },
  shuffleBtn: {
    flex: 1,
    height: 46,
    borderRadius: 14,
    backgroundColor: theme.surface,
    borderWidth: 1,
    borderColor: theme.border,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  shuffleText: {
    color: theme.cyan,
    fontSize: 15,
    fontWeight: '700',
  },
  searchBar: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: theme.surface,
    borderRadius: 12,
    paddingHorizontal: 12,
    height: 40,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.06)',
    marginTop: 4,
    marginBottom: 8,
  },
  searchInput: {
    flex: 1,
    marginLeft: 8,
    color: theme.text,
    fontSize: 13,
  },
  deleteBtn: {
    padding: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pressed: {
    opacity: 0.7,
    transform: [{ scale: 0.97 }],
  },
  list: {
    paddingHorizontal: 16,
    paddingBottom: 28,
  },
  emptyContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 40,
  },
  emptyIconWrap: {
    width: 88,
    height: 88,
    borderRadius: 44,
    backgroundColor: theme.surface,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 20,
    borderWidth: 1,
    borderColor: theme.border,
  },
  emptyTitle: {
    fontSize: 20,
    fontWeight: '800',
    color: theme.text,
    marginBottom: 8,
  },
  emptySub: {
    fontSize: 13,
    color: theme.textDim,
    textAlign: 'center',
    lineHeight: 20,
  },
});