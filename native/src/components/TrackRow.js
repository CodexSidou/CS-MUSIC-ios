import React from 'react';
import { Image, Pressable, StyleSheet, Text, View } from 'react-native';
import { theme } from '../theme';

function fmt(sec) {
  if (!Number.isFinite(sec) || sec <= 0) return null;
  const m = Math.floor(sec / 60);
  const s = Math.floor(sec % 60);
  return `${m}:${s < 10 ? '0' : ''}${s}`;
}

export function TrackRow({ track, onPlay, extra }) {
  const dur = fmt(track.duration);
  return (
    <Pressable
      onPress={onPlay}
      style={({ pressed }) => [styles.row, pressed && styles.rowPressed]}
    >
      {track.cover ? (
        <Image source={{ uri: track.cover }} style={styles.cover} />
      ) : (
        <View style={[styles.cover, styles.coverFallback]}>
          <Text style={styles.coverGlyph}>{'\u266B'}</Text>
        </View>
      )}
      <View style={styles.mid}>
        <Text style={styles.title} numberOfLines={1}>
          {track.title}
        </Text>
        <Text style={styles.sub} numberOfLines={1}>
          {track.artist || track.provider}
          {track.album ? `  \u00B7  ${track.album}` : ''}
          {dur ? `  \u00B7  ${dur}` : ''}
        </Text>
      </View>
      {extra}
    </Pressable>
  );
}

export function DownloadState({ track, downloads }) {
  const d = downloads[track.id];
  if (track.fileUri) {
    return (
      <Text style={styles.badgeDone}>{'\u2713'}</Text>
    );
  }
  if (!d) return null;
  if (d.status === 'resolving') {
    return (
      <View style={styles.spinWrap}>
        <Text style={styles.spinText}>{'\u21BB'}</Text>
      </View>
    );
  }
  if (d.status === 'downloading') {
    const pct = Math.round((d.progress || 0) * 100);
    return (
      <View style={styles.dlWrap}>
        <Text style={styles.dlText}>{pct}%</Text>
        <View style={styles.dlTrack}>
          <View style={[styles.dlFill, { width: `${Math.max(4, pct)}%` }]} />
        </View>
      </View>
    );
  }
  if (d.status === 'done') {
    return <Text style={styles.badgeDone}>{'\u2713'}</Text>;
  }
  return <Text style={styles.badgeErr}>{'!'}</Text>;
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 8,
    paddingHorizontal: 12,
    backgroundColor: theme.surface,
    borderRadius: 12,
    marginBottom: 8,
  },
  rowPressed: { opacity: 0.75 },
  cover: { width: 48, height: 48, borderRadius: 8, backgroundColor: theme.surface2 },
  coverFallback: { alignItems: 'center', justifyContent: 'center' },
  coverGlyph: { color: theme.cyan, fontSize: 20 },
  mid: { flex: 1, marginLeft: 12, marginRight: 8 },
  title: { color: theme.text, fontWeight: '600', fontSize: 15 },
  sub: { color: theme.textDim, fontSize: 12, marginTop: 3 },
  badgeDone: { color: theme.success, fontSize: 18, fontWeight: '700' },
  badgeErr: { color: theme.danger, fontSize: 16, fontWeight: '700' },
  spinWrap: { alignItems: 'center', justifyContent: 'center', minWidth: 28 },
  spinText: { color: theme.indigo, fontSize: 18 },
  dlWrap: { alignItems: 'flex-end', minWidth: 46 },
  dlText: { color: theme.cyan, fontSize: 12, marginBottom: 3, fontVariant: ['tabular-nums'] },
  dlTrack: { width: 44, height: 4, borderRadius: 2, backgroundColor: theme.surface2, overflow: 'hidden', marginBottom: 3 },
  dlFill: { height: 4, backgroundColor: theme.cyan },
});