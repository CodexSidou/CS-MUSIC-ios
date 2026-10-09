import React from 'react';
import { ActivityIndicator, Image, Pressable, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { theme } from '../theme';

function fmt(sec) {
  if (!Number.isFinite(sec) || sec <= 0) return null;
  const m = Math.floor(sec / 60);
  const s = Math.floor(sec % 60);
  return `${m}:${s < 10 ? '0' : ''}${s}`;
}

function SourceBadge({ provider }) {
  let iconName = 'musical-notes';
  let color = theme.cyan;
  let bg = 'rgba(0, 240, 255, 0.12)';

  if (provider === 'spotify') {
    iconName = 'logo-spotify';
    color = theme.spotify;
    bg = 'rgba(29, 185, 84, 0.15)';
  } else if (provider === 'youtube') {
    iconName = 'logo-youtube';
    color = theme.youtube;
    bg = 'rgba(255, 0, 51, 0.15)';
  } else if (provider === 'soundcloud') {
    iconName = 'logo-soundcloud';
    color = theme.soundcloud;
    bg = 'rgba(255, 85, 0, 0.15)';
  } else if (provider === 'itunes' || provider === 'charts') {
    iconName = 'logo-apple';
    color = theme.itunes;
    bg = 'rgba(252, 60, 68, 0.15)';
  } else if (provider === 'archive') {
    iconName = 'archive';
    color = theme.purple;
    bg = 'rgba(155, 81, 224, 0.15)';
  }

  return (
    <View style={[styles.badge, { backgroundColor: bg }]}>
      <Ionicons name={iconName} size={11} color={color} />
      <Text style={[styles.badgeText, { color }]}>{provider || 'audio'}</Text>
    </View>
  );
}

export function TrackRow({ track, onPlay, isPlaying, extra }) {
  const dur = fmt(track.duration);

  return (
    <Pressable
      onPress={onPlay}
      style={({ pressed }) => [
        styles.row,
        isPlaying && styles.rowActive,
        pressed && styles.rowPressed,
      ]}
    >
      <View style={styles.coverWrapper}>
        {track.cover ? (
          <Image source={{ uri: track.cover }} style={styles.cover} />
        ) : (
          <View style={[styles.cover, styles.coverFallback]}>
            <Ionicons name="musical-notes" size={20} color={theme.cyan} />
          </View>
        )}
        {isPlaying ? (
          <View style={styles.playingOverlay}>
            <Ionicons name="volume-high" size={16} color="#FFFFFF" />
          </View>
        ) : null}
      </View>

      <View style={styles.mid}>
        <Text style={[styles.title, isPlaying && styles.titleActive]} numberOfLines={1}>
          {track.title}
        </Text>
        <View style={styles.subRow}>
          <SourceBadge provider={track.provider} />
          <Text style={styles.sub} numberOfLines={1}>
            {track.artist || 'Unknown Artist'}
            {dur ? `  \u00B7  ${dur}` : ''}
          </Text>
        </View>
      </View>

      {extra}
    </Pressable>
  );
}

export function DownloadButton({ track, downloads, onDownload }) {
  const d = downloads[track.id];
  const isDownloaded = Boolean(track.fileUri);

  if (isDownloaded) {
    return (
      <View style={styles.doneBtn}>
        <Ionicons name="checkmark-circle" size={22} color={theme.success} />
      </View>
    );
  }

  if (d && (d.status === 'resolving' || d.status === 'downloading')) {
    const pct = Math.round((d.progress || 0) * 100);
    return (
      <View style={styles.loadingBtn}>
        <ActivityIndicator size="small" color={theme.cyan} />
        {pct > 0 && pct < 100 ? (
          <Text style={styles.pctText}>{pct}%</Text>
        ) : null}
      </View>
    );
  }

  if (d && d.status === 'done') {
    return (
      <View style={styles.doneBtn}>
        <Ionicons name="checkmark-circle" size={22} color={theme.success} />
      </View>
    );
  }

  return (
    <Pressable
      onPress={onDownload}
      hitSlop={8}
      style={({ pressed }) => [styles.dlBtn, pressed && styles.btnPressed]}
    >
      <Ionicons name="arrow-down-circle-outline" size={24} color={theme.cyan} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    paddingHorizontal: 12,
    backgroundColor: theme.surface,
    borderRadius: 14,
    marginBottom: 8,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.04)',
  },
  rowActive: {
    borderColor: theme.indigo,
    backgroundColor: theme.surfaceLight,
  },
  rowPressed: {
    opacity: 0.75,
    transform: [{ scale: 0.99 }],
  },
  coverWrapper: {
    position: 'relative',
    width: 50,
    height: 50,
    borderRadius: 10,
    overflow: 'hidden',
  },
  cover: {
    width: '100%',
    height: '100%',
    backgroundColor: theme.surface2,
  },
  coverFallback: {
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: theme.surface2,
  },
  playingOverlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(124, 89, 251, 0.65)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  mid: {
    flex: 1,
    marginLeft: 12,
    marginRight: 8,
  },
  title: {
    color: theme.text,
    fontWeight: '600',
    fontSize: 15,
    letterSpacing: -0.2,
  },
  titleActive: {
    color: theme.cyan,
  },
  subRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 4,
  },
  sub: {
    color: theme.textDim,
    fontSize: 12,
    flex: 1,
    marginLeft: 6,
  },
  badge: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 6,
    gap: 3,
  },
  badgeText: {
    fontSize: 10,
    fontWeight: '700',
    textTransform: 'capitalize',
  },
  dlBtn: {
    padding: 6,
    alignItems: 'center',
    justifyContent: 'center',
  },
  loadingBtn: {
    padding: 6,
    alignItems: 'center',
    justifyContent: 'center',
    minWidth: 32,
  },
  pctText: {
    color: theme.cyan,
    fontSize: 9,
    fontWeight: '700',
    marginTop: 2,
  },
  doneBtn: {
    padding: 6,
    alignItems: 'center',
    justifyContent: 'center',
  },
  btnPressed: {
    opacity: 0.6,
  },
});