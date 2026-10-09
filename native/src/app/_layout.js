import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Tabs } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { PlayerProvider } from '../context/PlayerContext';
import { PlayerBar } from '../components/PlayerBar';
import { FullPlayerModal } from '../components/FullPlayerModal';
import { theme } from '../theme';

export default function RootLayout() {
  return (
    <PlayerProvider>
      <SafeAreaProvider>
        <View style={styles.root}>
          <StatusBar style="light" />
          <Tabs
            screenOptions={{
              headerStyle: {
                backgroundColor: theme.bgDark,
                borderBottomColor: 'rgba(255, 255, 255, 0.06)',
                borderBottomWidth: 1,
                elevation: 0,
                shadowOpacity: 0,
              },
              headerTintColor: theme.text,
              headerTitleStyle: {
                fontWeight: '800',
                fontSize: 18,
                letterSpacing: -0.3,
              },
              tabBarActiveTintColor: theme.cyan,
              tabBarInactiveTintColor: theme.textMuted,
              tabBarStyle: {
                backgroundColor: theme.bgDark,
                borderTopColor: 'rgba(255, 255, 255, 0.08)',
                borderTopWidth: 1,
                height: 58,
                paddingBottom: 8,
                paddingTop: 6,
              },
              tabBarLabelStyle: {
                fontSize: 11,
                fontWeight: '700',
              },
              sceneStyle: { backgroundColor: theme.bg },
            }}
          >
            <Tabs.Screen
              name="index"
              options={{
                title: 'Search & Explore',
                tabBarIcon: ({ color, size, focused }) => (
                  <Ionicons
                    name={focused ? 'search' : 'search-outline'}
                    size={22}
                    color={color}
                  />
                ),
              }}
            />
            <Tabs.Screen
              name="library"
              options={{
                title: 'Library',
                tabBarIcon: ({ color, size, focused }) => (
                  <Ionicons
                    name={focused ? 'library' : 'library-outline'}
                    size={22}
                    color={color}
                  />
                ),
              }}
            />
          </Tabs>

          {/* Mini Player */}
          <PlayerBar />

          {/* Full Player Modal */}
          <FullPlayerModal />
        </View>
      </SafeAreaProvider>
    </PlayerProvider>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: theme.bg,
  },
});