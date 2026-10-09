import React from 'react';
import {
  Dimensions,
  Image,
  Modal,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { LinearGradient } from 'expo-linear-gradient';
import { Ionicons } from '@expo/vector-icons';
import { usePlayer } from '../context/PlayerContext';
import { theme } from '../theme';

const { width: SCREEN_WIDTH } = Dimensions.get('window');
const ARTWORK_SIZE = Math.min(SCREEN_WIDTH - 64, 320);

function fmt(sec) {
  if (!Number.isFinite(sec) || sec < 0) return '0:00';
  const m = Math.floor(sec / 60);
  const s = Math.floor(sec % 60);
  return `${m}:${s < 10 ? '0' : ''}${s}`;
}

export function FullPlayerModal() {
  const {
    current,
    status,
    isPlayerOpen,
    closeFullPlayer,
    toggle,
    next,
    prev,
    seekTo,
    isShuffle,
    toggleShuffle,
    repeatMode,
    toggleRepeat,
    download,
    downloads,
  } = usePlayer();

  if (!current) return null;

  const dur = status.duration || current.duration || 1;
  const pos = Math.min(status.currentTime || 0, dur);
  const pct = Math.max(0, Math.min(1, pos / dur));

  const isDownloaded = Boolean(current.fileUri);
  const dlStatus = downloads[current.id]?.status;

  const handleSeek = (event) => {
    const { locationX } = event.nativeEvent;
    const barWidth = SCREEN_WIDTH - 64;
    const ratio = Math.max(0, Math.min(1, locationX / barWidth));
    seekTo(ratio * dur);
  };

  return (
    <Modal
      visible={isPlayerOpen}
      animationType="slide"
      presentationStyle="pageSheet"
      onRequestClose={closeFullPlayer}
    >
      <View style={styles.container}>
        <LinearGradient
          colors={['#1F134A', '#0D0826', '#050314']}
          style={StyleSheet.absoluteFillObject}
        />

        <SafeAreaView style={styles.safe}>
          {/* Header */}
          <View style={styles.header}>
            <Pressable
              onPress={closeFullPlayer}
              hitSlop={12}
              style={({ pressed }) => [styles.iconBtn, pressed && styles.pressed]}
            >
              <Ionicons name="chevron-down" size={28} color={theme.text} />
            </Pressable>

            <View style={styles.headerTitleWrap}>
              <Text style={styles.headerSub}>PLAYING FROM</Text>
              <Text style={styles.headerTitle} numberOfLines={1}>
                {current.provider?.toUpperCase() || 'CS MUSIC'}
              </Text>
            </View>

            <Pressable
              onPress={() => download(current)}
              hitSlop={12}
              style={({ pressed }) => [styles.iconBtn, pressed && styles.pressed]}
            >
              <Ionicons
                name={
                  isDownloaded || dlStatus === 'done'
                    ? 'checkmark-circle'
                    : dlStatus === 'downloading'
                    ? 'cloud-download'
                    : 'arrow-down-circle-outline'
                }
                size={26}
                color={isDownloaded || dlStatus === 'done' ? theme.success : theme.cyan}
              />
            </Pressable>
          </View>

          {/* Artwork */}
          <View style={styles.artworkContainer}>
            <View style={styles.artworkShadow}>
              {current.cover ? (
                <Image source={{ uri: current.cover }} style={styles.artwork} />
              ) : (
                <View style={[styles.artwork, styles.artworkFallback]}>
                  <Ionicons name="musical-notes" size={90} color={theme.cyan} />
                </View>
              )}
            </View>
          </View>

          {/* Track Info */}
          <View style={styles.infoContainer}>
            <View style={styles.titleWrap}>
              <Text style={styles.title} numberOfLines={1}>
                {current.title}
              </Text>
              <Text style={styles.artist} numberOfLines={1}>
                {current.artist || 'Unknown Artist'}
                {current.album ? ` — ${current.album}` : ''}
              </Text>
            </View>
          </View>

          {/* Progress Bar */}
          <View style={styles.progressContainer}>
            <Pressable onPress={handleSeek} style={styles.scrubBarTouch}>
              <View style={styles.track}>
                <LinearGradient
                  colors={[theme.cyan, theme.indigo]}
                  start={{ x: 0, y: 0 }}
                  end={{ x: 1, y: 0 }}
                  style={[styles.fill, { width: `${pct * 100}%` }]}
                />
              </View>
            </Pressable>
            <View style={styles.timeRow}>
              <Text style={styles.timeText}>{fmt(pos)}</Text>
              <Text style={styles.timeText}>{fmt(dur)}</Text>
            </View>
          </View>

          {/* Main Controls */}
          <View style={styles.controls}>
            <Pressable
              onPress={toggleShuffle}
              hitSlop={10}
              style={({ pressed }) => [styles.secondaryBtn, pressed && styles.pressed]}
            >
              <Ionicons
                name="shuffle"
                size={22}
                color={isShuffle ? theme.cyan : theme.textDim}
              />
            </Pressable>

            <Pressable
              onPress={prev}
              hitSlop={12}
              style={({ pressed }) => [styles.skipBtn, pressed && styles.pressed]}
            >
              <Ionicons name="play-skip-back" size={32} color={theme.text} />
            </Pressable>

            <Pressable
              onPress={toggle}
              style={({ pressed }) => [styles.playBtn, pressed && styles.pressed]}
            >
              <LinearGradient
                colors={theme.gradients.primary}
                style={styles.playGradient}
              >
                <Ionicons
                  name={status.playing ? 'pause' : 'play'}
                  size={36}
                  color="#FFFFFF"
                  style={status.playing ? null : { marginLeft: 3 }}
                />
              </LinearGradient>
            </Pressable>

            <Pressable
              onPress={next}
              hitSlop={12}
              style={({ pressed }) => [styles.skipBtn, pressed && styles.pressed]}
            >
              <Ionicons name="play-skip-forward" size={32} color={theme.text} />
            </Pressable>

            <Pressable
              onPress={toggleRepeat}
              hitSlop={10}
              style={({ pressed }) => [styles.secondaryBtn, pressed && styles.pressed]}
            >
              <Ionicons
                name={repeatMode === 'one' ? 'repeat-outline' : 'repeat'}
                size={22}
                color={repeatMode !== 'off' ? theme.cyan : theme.textDim}
              />
              {repeatMode === 'one' && <Text style={styles.repeatBadge}>1</Text>}
            </Pressable>
          </View>

          {/* Bottom Device/Offline indicator */}
          <View style={styles.bottomBar}>
            <Ionicons
              name={isDownloaded ? 'cloud-done' : 'wifi'}
              size={14}
              color={theme.textDim}
            />
            <Text style={styles.bottomText}>
              {isDownloaded
                ? 'Downloaded to iPhone (Offline Ready)'
                : 'Streaming via High Quality Audio'}
            </Text>
          </View>
        </SafeAreaView>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: theme.bgDark,
  },
  safe: {
    flex: 1,
    paddingHorizontal: 24,
    justifyContent: 'space-between',
    paddingBottom: 20,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingTop: 8,
  },
  headerTitleWrap: {
    alignItems: 'center',
  },
  headerSub: {
    fontSize: 10,
    fontWeight: '700',
    color: theme.textMuted,
    letterSpacing: 1.2,
  },
  headerTitle: {
    fontSize: 12,
    fontWeight: '700',
    color: theme.cyan,
    marginTop: 2,
    letterSpacing: 0.5,
  },
  iconBtn: {
    width: 40,
    height: 40,
    alignItems: 'center',
    justifyContent: 'center',
  },
  artworkContainer: {
    alignItems: 'center',
    justifyContent: 'center',
    marginVertical: 18,
  },
  artworkShadow: {
    shadowColor: theme.magenta,
    shadowOffset: { width: 0, height: 16 },
    shadowOpacity: 0.35,
    shadowRadius: 28,
    elevation: 20,
  },
  artwork: {
    width: ARTWORK_SIZE,
    height: ARTWORK_SIZE,
    borderRadius: 22,
    backgroundColor: theme.surface2,
  },
  artworkFallback: {
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: theme.border,
  },
  infoContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 8,
  },
  titleWrap: {
    flex: 1,
  },
  title: {
    fontSize: 22,
    fontWeight: '800',
    color: theme.text,
    letterSpacing: -0.4,
  },
  artist: {
    fontSize: 15,
    fontWeight: '500',
    color: theme.textDim,
    marginTop: 4,
  },
  progressContainer: {
    paddingHorizontal: 8,
    marginTop: 6,
  },
  scrubBarTouch: {
    paddingVertical: 10,
  },
  track: {
    height: 6,
    borderRadius: 3,
    backgroundColor: 'rgba(255, 255, 255, 0.1)',
    overflow: 'hidden',
  },
  fill: {
    height: '100%',
    borderRadius: 3,
  },
  timeRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 2,
  },
  timeText: {
    fontSize: 12,
    color: theme.textDim,
    fontVariant: ['tabular-nums'],
    fontWeight: '500',
  },
  controls: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 12,
    marginTop: 8,
  },
  secondaryBtn: {
    width: 44,
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
  },
  repeatBadge: {
    position: 'absolute',
    top: 8,
    right: 8,
    fontSize: 9,
    fontWeight: '800',
    color: theme.cyan,
  },
  skipBtn: {
    width: 52,
    height: 52,
    alignItems: 'center',
    justifyContent: 'center',
  },
  playBtn: {
    width: 74,
    height: 74,
    borderRadius: 37,
    shadowColor: theme.indigo,
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.5,
    shadowRadius: 16,
    elevation: 10,
  },
  playGradient: {
    width: '100%',
    height: '100%',
    borderRadius: 37,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pressed: {
    opacity: 0.7,
    transform: [{ scale: 0.95 }],
  },
  bottomBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    paddingTop: 8,
  },
  bottomText: {
    fontSize: 11,
    color: theme.textMuted,
    fontWeight: '500',
  },
});
