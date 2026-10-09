import React from 'react';
import { Image, Pressable, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import { usePlayer } from '../context/PlayerContext';
import { theme } from '../theme';

export function PlayerBar() {
  const { current, status, toggle, next, openFullPlayer } = usePlayer();

  if (!current) return null;

  const dur = status.duration || current.duration || 1;
  const pos = Math.min(status.currentTime || 0, dur);
  const pct = Math.max(0, Math.min(1, pos / dur));

  return (
    <View style={styles.container}>
      <Pressable onPress={openFullPlayer} style={styles.inner}>
        {/* Track Thumbnail */}
        <View style={styles.thumbWrap}>
          {current.cover ? (
            <Image source={{ uri: current.cover }} style={styles.thumb} />
          ) : (
            <View style={[styles.thumb, styles.thumbFallback]}>
              <Ionicons name="musical-note" size={18} color={theme.cyan} />
            </View>
          )}
        </View>

        {/* Track Metadata */}
        <View style={styles.meta}>
          <Text style={styles.title} numberOfLines={1}>
            {current.title}
          </Text>
          <Text style={styles.sub} numberOfLines={1}>
            {current.artist || 'CS Music'}
          </Text>
        </View>

        {/* Controls */}
        <View style={styles.controls}>
          <Pressable
            onPress={(e) => {
              e.stopPropagation();
              toggle();
            }}
            hitSlop={8}
            style={({ pressed }) => [styles.playBtn, pressed && styles.btnPressed]}
          >
            <Ionicons
              name={status.playing ? 'pause' : 'play'}
              size={20}
              color="#FFFFFF"
              style={status.playing ? null : { marginLeft: 2 }}
            />
          </Pressable>

          <Pressable
            onPress={(e) => {
              e.stopPropagation();
              next();
            }}
            hitSlop={8}
            style={({ pressed }) => [styles.nextBtn, pressed && styles.btnPressed]}
          >
            <Ionicons name="play-forward" size={20} color={theme.textDim} />
          </Pressable>
        </View>
      </Pressable>

      {/* Thin Bottom Progress Line */}
      <View style={styles.progressTrack}>
        <LinearGradient
          colors={[theme.cyan, theme.indigo]}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 0 }}
          style={[styles.progressFill, { width: `${pct * 100}%` }]}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: theme.surface,
    borderTopWidth: 1,
    borderTopColor: 'rgba(255, 255, 255, 0.08)',
    shadowColor: '#000000',
    shadowOffset: { width: 0, height: -4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 8,
  },
  inner: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 8,
    paddingHorizontal: 14,
  },
  thumbWrap: {
    width: 44,
    height: 44,
    borderRadius: 8,
    overflow: 'hidden',
    backgroundColor: theme.surface2,
  },
  thumb: {
    width: '100%',
    height: '100%',
  },
  thumbFallback: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  meta: {
    flex: 1,
    marginLeft: 12,
    marginRight: 8,
  },
  title: {
    color: theme.text,
    fontSize: 14,
    fontWeight: '700',
    letterSpacing: -0.2,
  },
  sub: {
    color: theme.textDim,
    fontSize: 12,
    marginTop: 2,
  },
  controls: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  playBtn: {
    width: 38,
    height: 38,
    borderRadius: 19,
    backgroundColor: theme.indigo,
    alignItems: 'center',
    justifyContent: 'center',
  },
  nextBtn: {
    width: 36,
    height: 36,
    alignItems: 'center',
    justifyContent: 'center',
  },
  btnPressed: {
    opacity: 0.6,
  },
  progressTrack: {
    height: 3,
    backgroundColor: 'rgba(255, 255, 255, 0.06)',
    width: '100%',
  },
  progressFill: {
    height: '100%',
  },
});