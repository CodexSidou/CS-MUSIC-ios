import React, { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  FlatList,
  Image,
  Keyboard,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import { usePlayer } from '../context/PlayerContext';
import { TrackRow, DownloadButton } from '../components/TrackRow';
import { isSpotifyUrl, resolveSpotifyUrl } from '../api/spotify';
import { searchYouTube } from '../api/youtube';
import { searchSoundCloud } from '../api/soundcloud';
import { fetchTrendingCharts, searchITunes } from '../api/providers';
import { theme } from '../theme';

const FILTERS = ['All', 'Spotify', 'YouTube', 'SoundCloud', 'Charts'];

export default function SearchScreen() {
  const { playTrack, download, downloadAll, downloads, current, status } = usePlayer();

  const [query, setQuery] = useState('');
  const [activeFilter, setActiveFilter] = useState('All');
  const [results, setResults] = useState([]);
  const [trending, setTrending] = useState([]);
  const [searching, setSearching] = useState(false);
  const [spotifyCollection, setSpotifyCollection] = useState(null);
  const [msg, setMsg] = useState(null);

  // Load trending songs on first mount
  useEffect(() => {
    fetchTrendingCharts('us')
      .then((items) => setTrending(items))
      .catch((err) => console.warn('Trending fetch error:', err));
  }, []);

  const runSearch = useCallback(async () => {
    const q = query.trim();
    if (!q) return;

    Keyboard.dismiss();
    setSearching(true);
    setMsg(null);
    setSpotifyCollection(null);
    setResults([]);

    // Check if the query is a Spotify link
    if (isSpotifyUrl(q)) {
      try {
        const spotifyData = await resolveSpotifyUrl(q);
        if (spotifyData.tracks && spotifyData.tracks.length > 0) {
          if (spotifyData.type === 'playlist' || spotifyData.type === 'album') {
            setSpotifyCollection(spotifyData);
          }
          setResults(spotifyData.tracks);
        } else {
          setMsg('No tracks found in this Spotify link.');
        }
      } catch (err) {
        setMsg('Spotify error: ' + (err.message || String(err)));
      } finally {
        setSearching(false);
      }
      return;
    }

    try {
      const searches = [];

      if (activeFilter === 'All' || activeFilter === 'YouTube') {
        searches.push(searchYouTube(q));
      }
      if (activeFilter === 'All' || activeFilter === 'Spotify' || activeFilter === 'Charts') {
        searches.push(searchITunes(q));
      }
      if (activeFilter === 'All' || activeFilter === 'SoundCloud') {
        searches.push(searchSoundCloud(q));
      }

      const settled = await Promise.allSettled(searches);
      const merged = [];
      const seen = new Set();

      for (const res of settled) {
        if (res.status === 'fulfilled' && Array.isArray(res.value)) {
          for (const track of res.value) {
            const key = `${track.title.toLowerCase()}-${track.artist.toLowerCase()}`;
            if (!seen.has(key)) {
              seen.add(key);
              merged.push(track);
            }
          }
        }
      }

      setResults(merged);
      if (!merged.length) {
        setMsg(`No matches found for "${q}". Try another song or artist name.`);
      }
    } catch (err) {
      setMsg('Search failed: ' + (err.message || String(err)));
    } finally {
      setSearching(false);
    }
  }, [query, activeFilter]);

  const onSave = useCallback(
    (track) => {
      download(track).catch((err) =>
        Alert.alert('Download Error', err?.message || String(err))
      );
    },
    [download]
  );

  const onDownloadAllCollection = useCallback(() => {
    if (!spotifyCollection || !spotifyCollection.tracks) return;
    Alert.alert(
      'Download All Tracks',
      `Download all ${spotifyCollection.tracks.length} tracks to your iPhone?`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Download All',
          onPress: () => {
            downloadAll(spotifyCollection.tracks);
          },
        },
      ]
    );
  }, [spotifyCollection, downloadAll]);

  const onPlay = useCallback(
    (track, list) => {
      playTrack(track, list || results).catch((err) =>
        Alert.alert('Playback Error', err?.message || String(err))
      );
    },
    [playTrack, results]
  );

  const displayList = results.length > 0 ? results : trending;
  const isShowingTrending = results.length === 0 && !searching;

  return (
    <View style={styles.page}>
      {/* Search Input Bar */}
      <View style={styles.searchSection}>
        <View style={styles.inputContainer}>
          <Ionicons name="search" size={20} color={theme.textMuted} style={styles.searchIcon} />
          <TextInput
            style={styles.input}
            value={query}
            onChangeText={setQuery}
            placeholder="Search song, or paste Spotify / YouTube link..."
            placeholderTextColor={theme.textMuted}
            autoCapitalize="none"
            autoCorrect={false}
            returnKeyType="search"
            onSubmitEditing={runSearch}
          />
          {query.length > 0 ? (
            <Pressable
              onPress={() => {
                setQuery('');
                setResults([]);
                setSpotifyCollection(null);
                setMsg(null);
              }}
              hitSlop={8}
              style={styles.clearBtn}
            >
              <Ionicons name="close-circle" size={18} color={theme.textDim} />
            </Pressable>
          ) : null}
        </View>

        <Pressable
          onPress={runSearch}
          disabled={searching}
          style={({ pressed }) => [styles.searchBtn, pressed && styles.pressed]}
        >
          {searching ? (
            <ActivityIndicator size="small" color="#FFFFFF" />
          ) : (
            <Ionicons name="arrow-forward" size={20} color="#FFFFFF" />
          )}
        </Pressable>
      </View>

      {/* Filter Tabs */}
      <View style={styles.filtersWrapper}>
        <FlatList
          horizontal
          showsHorizontalScrollIndicator={false}
          data={FILTERS}
          keyExtractor={(item) => item}
          contentContainerStyle={styles.filterList}
          renderItem={({ item }) => {
            const isActive = activeFilter === item;
            return (
              <Pressable
                onPress={() => {
                  setActiveFilter(item);
                  if (query.trim()) {
                    setTimeout(() => runSearch(), 50);
                  }
                }}
                style={[styles.filterChip, isActive && styles.filterChipActive]}
              >
                <Text style={[styles.filterText, isActive && styles.filterTextActive]}>
                  {item}
                </Text>
              </Pressable>
            );
          }}
        />
      </View>

      {/* Spotify Album / Playlist Header Banner */}
      {spotifyCollection ? (
        <View style={styles.collectionBanner}>
          <LinearGradient
            colors={['rgba(29, 185, 84, 0.25)', 'rgba(29, 185, 84, 0.05)']}
            style={StyleSheet.absoluteFillObject}
          />
          {spotifyCollection.cover ? (
            <Image source={{ uri: spotifyCollection.cover }} style={styles.collectionCover} />
          ) : null}
          <View style={styles.collectionInfo}>
            <View style={styles.spotifyTag}>
              <Ionicons name="logo-spotify" size={12} color={theme.spotify} />
              <Text style={styles.spotifyTagText}>
                {spotifyCollection.type?.toUpperCase()} IMPORTED
              </Text>
            </View>
            <Text style={styles.collectionTitle} numberOfLines={1}>
              {spotifyCollection.title}
            </Text>
            <Text style={styles.collectionSub}>
              {spotifyCollection.tracks?.length} tracks
            </Text>
          </View>
          <Pressable
            onPress={onDownloadAllCollection}
            style={({ pressed }) => [styles.downloadAllBtn, pressed && styles.pressed]}
          >
            <Ionicons name="cloud-download" size={16} color="#FFFFFF" />
            <Text style={styles.downloadAllText}>Download All</Text>
          </Pressable>
        </View>
      ) : null}

      {/* Status or Error Message */}
      {msg ? (
        <View style={styles.msgCard}>
          <Ionicons name="information-circle" size={18} color={theme.cyan} />
          <Text style={styles.msgText}>{msg}</Text>
        </View>
      ) : null}

      {/* Results / Trending List */}
      <FlatList
        data={displayList}
        keyExtractor={(item) => `${item.provider}:${item.id}`}
        contentContainerStyle={styles.list}
        ListHeaderComponent={
          isShowingTrending ? (
            <View style={styles.sectionHeader}>
              <Ionicons name="flame" size={18} color={theme.magenta} />
              <Text style={styles.sectionTitle}>Trending & Top Hits</Text>
            </View>
          ) : results.length > 0 ? (
            <View style={styles.sectionHeader}>
              <Ionicons name="musical-notes" size={16} color={theme.cyan} />
              <Text style={styles.sectionTitle}>{results.length} Songs Found</Text>
            </View>
          ) : null
        }
        renderItem={({ item }) => (
          <TrackRow
            track={item}
            isPlaying={current?.id === item.id && status.playing}
            onPlay={() => onPlay(item, displayList)}
            extra={
              <DownloadButton
                track={item}
                downloads={downloads}
                onDownload={() => onSave(item)}
              />
            }
          />
        )}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  page: {
    flex: 1,
    backgroundColor: theme.bg,
  },
  searchSection: {
    flexDirection: 'row',
    paddingHorizontal: 16,
    paddingTop: 12,
    paddingBottom: 8,
    gap: 10,
  },
  inputContainer: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: theme.surface,
    borderRadius: 14,
    paddingHorizontal: 12,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.08)',
  },
  searchIcon: {
    marginRight: 8,
  },
  input: {
    flex: 1,
    height: 44,
    color: theme.text,
    fontSize: 14,
    fontWeight: '500',
  },
  clearBtn: {
    padding: 4,
  },
  searchBtn: {
    width: 44,
    height: 44,
    borderRadius: 14,
    backgroundColor: theme.indigo,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pressed: {
    opacity: 0.7,
    transform: [{ scale: 0.96 }],
  },
  filtersWrapper: {
    paddingVertical: 6,
  },
  filterList: {
    paddingHorizontal: 16,
    gap: 8,
  },
  filterChip: {
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderRadius: 20,
    backgroundColor: theme.surface,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.06)',
  },
  filterChipActive: {
    backgroundColor: theme.indigo,
    borderColor: theme.indigo,
  },
  filterText: {
    fontSize: 12,
    fontWeight: '600',
    color: theme.textDim,
  },
  filterTextActive: {
    color: '#FFFFFF',
    fontWeight: '700',
  },
  collectionBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    marginHorizontal: 16,
    marginTop: 8,
    marginBottom: 6,
    padding: 12,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: 'rgba(29, 185, 84, 0.3)',
    overflow: 'hidden',
  },
  collectionCover: {
    width: 48,
    height: 48,
    borderRadius: 8,
  },
  collectionInfo: {
    flex: 1,
    marginLeft: 12,
  },
  spotifyTag: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    marginBottom: 2,
  },
  spotifyTagText: {
    fontSize: 10,
    fontWeight: '800',
    color: theme.spotify,
    letterSpacing: 0.5,
  },
  collectionTitle: {
    fontSize: 14,
    fontWeight: '700',
    color: theme.text,
  },
  collectionSub: {
    fontSize: 12,
    color: theme.textDim,
  },
  downloadAllBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: theme.spotify,
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 10,
  },
  downloadAllText: {
    fontSize: 12,
    fontWeight: '700',
    color: '#FFFFFF',
  },
  msgCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginHorizontal: 16,
    marginVertical: 8,
    padding: 12,
    backgroundColor: 'rgba(0, 240, 255, 0.08)',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: 'rgba(0, 240, 255, 0.2)',
  },
  msgText: {
    flex: 1,
    color: theme.text,
    fontSize: 13,
  },
  sectionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginTop: 12,
    marginBottom: 10,
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: '700',
    color: theme.text,
    letterSpacing: -0.2,
  },
  list: {
    paddingHorizontal: 16,
    paddingBottom: 24,
  },
});