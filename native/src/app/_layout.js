import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Tabs } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { PlayerProvider } from '../context/PlayerContext';
import { PlayerBar } from '../components/PlayerBar';
import { theme } from '../theme';

function tabIcon(glyph) {
  return function Icon({ color, focused }) {
    return (
      <Text style={[styles.icon, focused && styles.iconActive, { color }]}>{glyph}</Text>
    );
  };
}

export default function RootLayout() {
  return (
    <PlayerProvider>
      <SafeAreaProvider>
        <View style={styles.root}>
          <StatusBar style="light" />
          <Tabs
            screenOptions={{
              headerStyle: { backgroundColor: theme.surface },
              headerTintColor: theme.text,
              headerTitleStyle: { fontWeight: '700' },
              tabBarActiveTintColor: theme.cyan,
              tabBarInactiveTintColor: theme.textDim,
              tabBarStyle: {
                backgroundColor: theme.surface,
                borderTopColor: theme.border,
              },
              sceneStyle: { backgroundColor: theme.bg },
            }}
          >
            <Tabs.Screen
              name="index"
              options={{ title: 'Search', tabBarIcon: tabIcon('\u2315') }}
            />
            <Tabs.Screen
              name="library"
              options={{ title: 'Library', tabBarIcon: tabIcon('\u266B') }}
            />
          </Tabs>
          <PlayerBar />
        </View>
      </SafeAreaProvider>
    </PlayerProvider>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: theme.bg },
  icon: { fontSize: 18 },
  iconActive: { fontWeight: '700' },
});