import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { usePlayer } from '../context/PlayerContext';
import { theme } from '../theme';

function fmt(sec) {
  if (!Number.isFinite(sec) || sec < 0) return '0:00';
  const m = Math.floor(sec / 60);
  const s = Math.floor(sec % 60);
  return `${m}:${s < 10 ? '0' : ''}${s}`;
}

export function PlayerBar() {
  const { current, status, toggle, next, prev, seekTo } = usePlayer();
  if (!current) return null;
  const dur = status.duration || current.duration || 1;
  const pos = Math.min(status.currentTime || 0, dur);
  const pct = Math.max(0, Math.min(1, pos / dur));

  return (
    <View style={styles.wrap}>
      <View style={styles.meta}>
        <View style={styles.thumb}>
          <Text style={styles.thumbText} numberOfLines={1}>
            {'\u266B'}
          </Text>
        </View>
        <View style={styles.metaText}>
          <Text style={styles.title} numberOfLines={1}>
            {current.title}
          </Text>
          <Text style={styles.sub} numberOfLines={1}>
            {current.artist || 'CS music'}
          </Text>
        </View>
        <Pressable
          onPress={() => {
            const target = pct >= 0.98 ? 0 : pos + 15;
            seekTo(target >= dur ? dur - 2 : target);
          }}
          style={({ pressed }) => [styles.btn, pressed && styles.btnPressed]}
        >
          <Text style={styles.btnText}>{fmt(pos)}</Text>
        </Pressable>
      </View>
      <View style={styles.progressTrack}>
        <View style={[styles.progressFill, { width: `${pct * 100}%` }]} />
      </View>
      <View style={styles.controls}>
        <Pressable onPress={prev} style={({ pressed }) => [styles.fab, pressed && styles.btnPressed]}>
          <Text style={styles.fabText}>{'\u23EE'}</Text>
        </Pressable>
        <Pressable onPress={toggle} style={({ pressed }) => [styles.play, pressed && styles.btnPressed]}>
          <Text style={styles.playText}>{status.playing ? '\u23F8' : '\u25B6'}</Text>
        </Pressable>
        <Pressable onPress={next} style={({ pressed }) => [styles.fab, pressed && styles.btnPressed]}>
          <Text style={styles.fabText}>{'\u23ED'}</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    borderTopWidth: 1,
    borderTopColor: theme.border,
    backgroundColor: theme.surface,
    paddingTop: 10,
    paddingHorizontal: 14,
    paddingBottom: 8,
  },
  meta: { flexDirection: 'row', alignItems: 'center' },
  thumb: {
    width: 40,
    height: 40,
    borderRadius: 8,
    backgroundColor: theme.surface2,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 10,
  },
  thumbText: { color: theme.cyan, fontSize: 18 },
  metaText: { flex: 1, marginRight: 8 },
  title: { color: theme.text, fontWeight: '700', fontSize: 14 },
  sub: { color: theme.textDim, fontSize: 12, marginTop: 2 },
  btn: {
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 8,
    backgroundColor: theme.surface2,
  },
  btnPressed: { opacity: 0.7 },
  btnText: { color: theme.cyan, fontSize: 12, fontVariant: ['tabular-nums'] },
  progressTrack: {
    height: 4,
    borderRadius: 2,
    backgroundColor: theme.surface2,
    marginTop: 10,
    overflow: 'hidden',
  },
  progressFill: { height: 4, backgroundColor: theme.indigo },
  controls: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    paddingTop: 10,
  },
  play: {
    width: 52,
    height: 52,
    borderRadius: 26,
    backgroundColor: theme.indigo,
    alignItems: 'center',
    justifyContent: 'center',
    marginHorizontal: 24,
  },
  playText: { color: '#fff', fontSize: 20, marginLeft: 2 },
  fab: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: theme.surface2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  fabText: { color: theme.text, fontSize: 16 },
});