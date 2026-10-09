# CS music — native iOS app (Expo / React Native)

A real iPhone app for finding music on YouTube and downloading songs to the phone for offline playback. Because it runs **on your phone**, stream resolution happens from your own (residential) IP, so it keeps working even though YouTube bot-blocks web/datacenter proxies.

## Run it on your iPhone (10 minutes, no Mac needed)

1. Install **Expo Go** from the App Store on your iPhone.
2. On your computer, make sure your phone and computer are on the same Wi-Fi network:
   ```
   cd native
   npm install
   npx expo start
   ```
3. On the iPhone, open **Expo Go** and scan the QR code shown in the terminal (iOS camera app works too).
4. The app opens as *CS music*. Search a song, tap a result to play, tap the upload `up-arrow` button to download it. Saved songs appear in the **Library** tab and play offline.

Troubleshooting:
- Phone can't connect: run `npx expo start --tunnel`.
- YouTube plays but download is slow: it is downloading the actual audio track; bigger files take longer.
- A song fails to resolve: some videos are region-locked or removed; try another.

## What it does

- **Search** — YouTube (searched + resolved on-device via `youtubei.js`, with a Piped API fallback), plus iTunes and Internet Archive preview tracks.
- **Play** — streaming player bar with previous / play-pause / next and a seek progress bar.
- **Download** — saves the best audio stream (m4a preferred) into the app's Documents folder with live progress, and persists the library with AsyncStorage.
- **Offline library** — play, stream, or delete saved songs.

## Layout

```
src/app/            screens (Expo Router: Search, Library)
src/api/youtube.js  youtubei.js resolver + Piped fallback (search + streams)
src/api/providers.js iTunes / Internet Archive providers
src/api/library.js  AsyncStorage persistence
src/context/PlayerContext.js  audio engine (expo-audio), queue, download tasks
src/components/     PlayerBar, TrackRow
```

## Build a standalone ipa

Expo Go is fine for daily use. To ship a real .ipa (no Expo Go needed) you need an Apple Developer account; build it in the cloud:

```
npx eas-cli build --platform ios
```