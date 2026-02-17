# React Native Audio Pro

> [!IMPORTANT]
> This repository contains custom modifications tailored for specific application needs. These changes are currently **Android-only** and are **not intended to be merged** back into the original fork.

Modern, background-capable audio playback for React Native — built for podcasts, audiobooks, and long-form media. 

**Now with Advanced Features:**
- 🌙 **Sleep Timer**
- 🔇 **Silence Skipping**
- 💾 **Offline Caching Controls**
- 🔁 **Sliding Window URL Refreshing**
- 🎚️ **Equalizer & Bass Boost** (Android)

Works out of the box with background playback, lock screen controls, and clean hooks-based state. Under the hood: Android uses Media3 (not old-school ExoPlayer), giving you up-to-date media session support. iOS uses AVFoundation.

[![npm version](https://img.shields.io/npm/v/react-native-audio-pro?logo=npm&logoColor=white&labelColor=grey&color=blue)](https://www.npmjs.com/package/react-native-audio-pro)
[![website](https://img.shields.io/badge/website-rnap.dev-grey?logo=google-chrome&logoColor=white&color=blue)](https://rnap.dev)
[![GitHub](https://img.shields.io/badge/evergrace--co-react--native--audio--pro-grey?logo=github&logoColor=white&labelColor=grey&color=blue)](https://github.com/evergrace-co/react-native-audio-pro)

## Table of Contents

- [✅️ Core Features](#-core-features)
- [✨ Advanced Features](#-advanced-features)
- [⚙️ Requirements](#%EF%B8%8F-requirements)
- [🚀 Installation](#-installation)
- [📚 API Overview](#-api-overview)
- [⚡️ React Hook](#%EF%B8%8F-react-hook)
- [🧩 Types](#-types)
- [📱 Example App](#-example-app)

## ✅ Core Features

These are fully supported, maintained features and the foundation of the library:

- 🎵 **Remote Audio File Playback** — Play MP3, AAC, M3U8 (HLS), and other formats.
- 📱 **Background Playback** — Works with screen locked or app backgrounded.
- 🔒 **Lock Screen Controls** — Native media controls on Android and iOS.
- 🖼 **Artwork Support** — Display album art on lock screen.
- 🪟 **Notification Center** — Android media session support.
- ⚙️ **Imperative API** — `play`, `pause`, `stop`, `seekTo`, `setVolume`, `setPlaybackSpeed`.
- 🕘 **Start Time Support** — Begin playback from a specific position.
- 🪪 **HTTP Headers** — Pass custom headers for audio and artwork URLs.
- 🧩 **Fully Typed API** — First-class TypeScript support.

## ✨ Advanced Features

### 🌙 Sleep Timer
Automatically pause playback after a set duration.
```typescript
AudioPro.startSleepTimer(60 * 30); // Stop after 30 minutes
AudioPro.cancelSleepTimer();
```

### 🔇 Silence Skipping
Automatically skip silent parts of audio (Android).
```typescript
AudioPro.setSkipSilence(true);
```

### 💾 Cache Management
Control the offline cache size and clear it when needed.
```typescript
const sizeBytes = await AudioPro.getCacheSize();
await AudioPro.clearCache();
```

### 🔁 URL Refreshing (Sliding Window)
Handle expiring URLs (e.g., signed S3/CloudFront links) by updating tracks in the queue without interrupting playback.
```typescript
AudioPro.updateTrack(index, { ...track, url: 'new-signed-url' });
```
*See `example/src/components/URLRefreshLogic.tsx` for a full implementation of the Sliding Window strategy.*

### 🎚️ Equalizer & Bass Boost (Android Only)
Access the native Android equalizer and bass boost settings.
```typescript
// 10-band equalizer (values in dB, typically -10 to 10)
const gains = [0, 2, 4, 2, 0, -2, -4, -2, 0, 2];
AudioPro.setEqualizer(gains);

// Bass Boost (0 to 1000)
AudioPro.setBassBoost(500); // 50% strength
```
*Note: This feature is currently Android-only.*

### 🌧️ Ambient Audio
Play a secondary audio track (e.g., white noise, rain sounds) simultaneously with the main media.
```typescript
AudioPro.ambientPlay({
  url: 'https://example.com/rain.mp3',
  loop: true,
});
AudioPro.ambientSetVolume(0.5);
```

### 🎵 Gapless Playback
Gapless playback is enabled by default. Simply add multiple tracks to the queue, and the player will transition between them seamlessly without interruptions.
```typescript
AudioPro.play(track1);
AudioPro.addToQueue([track2, track3]);
```

### 🔔 Custom Notification Actions
Add custom buttons like "Like", "Save", or "Rewind 30s" to the notification/lock screen.
```typescript
AudioPro.setNotificationButtons(['LIKE', 'REWIND_30', 'NEXT']);

AudioPro.addEventListener(event => {
  if (event.type === 'CUSTOM_ACTION') {
    console.log('Action:', event.payload.action); // 'LIKE'
  }
});
```

## ⚙️ Requirements

- **TypeScript:** 5.0+
- **React Native:** 0.72+
- **iOS:** iOS 17.0+
- **Android:** Android 8.0 (API 26)+

## 🚀 Installation

```bash
npm install react-native-audio-pro
# or
yarn add react-native-audio-pro
```

### 🍎 iOS Installation
```bash
npx pod-install
```
*Enable **Background Modes** -> **Audio, AirPlay, and Picture in Picture** in Xcode.*

### 📦 Installation from GitHub

Since this is a private fork, you can install it directly from GitHub:

```bash
# Install specific branch (recommended)
yarn add github:afkcodes/react-native-audio-pro#feature/main

# OR using npm
npm install github:afkcodes/react-native-audio-pro#feature/main
```

*Note: This repository uses `react-native-builder-bob`. The `prepare` script will automatically build the necessary JS files upon installation.*

### 🤖 Android Installation
Ensure `compileSdkVersion` and `targetSdkVersion` are **35** in `android/build.gradle`.

## 🛠️ Configuration

Initialize the player early in your app's lifecycle (e.g., `index.js` or `App.tsx`).

```typescript
import { AudioPro, AudioProContentType } from 'react-native-audio-pro';

AudioPro.configure({
  // Audio Focus & Content Type
  contentType: AudioProContentType.MUSIC, // or SPEECH

  // Cache Settings
  cacheEnabled: true,
  maxCacheSize: 1024 * 1024 * 500, // 500MB

  // Features
  skipSilence: false, // Enable android silence skipping
  
  // Debugging
  debug: true,
  debugIncludesProgress: false,
});
```

## 📚 API Overview

### 🎮 Player Controls
| Method | Description |
| :--- | :--- |
| `play(options?)` | Start or resume playback. |
| `pause()` | Pause playback. |
| `stop()` | Stop playback and reset to start. |
| `seekTo(ms)` | Seek to specific position. |
| `seekBy(ms)` | Seek relative to current position. |
| `setPlaybackSpeed(0.25-2.0)` | Set playback speed. |
| `setRepeatMode(mode)` | `'OFF'`, `'ONE'`, `'ALL'`. |
| `setShuffleMode(boolean)` | Enable/disable shuffle. |
| `startSleepTimer(seconds)` | Pause after duration. |
| `cancelSleepTimer()` | Cancel active sleep timer. |

### 📋 Queue Management
| Method | Description |
| :--- | :--- |
| `addToQueue(track[])` | Add tracks to end of queue. |
| `playNext()` | Skip to next track. |
| `playPrevious()` | Skip to previous track. |
| `skipTo(index)` | Skip to specific queue index. |
| `removeTrack(index)` | Remove track from queue. |
| `clearQueue()` | Clear all tracks. |
| `getQueue()` | Get current queue (Promise). |
| `updateTrack(index, track)` | Update track metadata/URL. |
| `getActiveTrackIndex()` | Get current track index. |
| `getPlayingTrack()` | Get current track object. |

### 🔊 Audio Settings
| Method | Description |
| :--- | :--- |
| `setVolume(0.0-1.0)` | Set player volume. |
| `setSkipSilence(boolean)` | Skip silent parts (Android). |
| `setEqualizer(gains[])` | Set 10-band equalizer (Android). |
| `setBassBoost(0-1000)` | Set bass boost strength (Android). |
| `setNotificationButtons(...)` | Customize lock screen buttons. |
| `getCacheSize()` | Get cache usage in bytes. |
| `clearCache()` | Clear offline cache. |

### 🌧️ Ambient Audio
| Method | Description |
| :--- | :--- |
| `ambientPlay({ url, loop })` | Play secondary audio. |
| `ambientStop()` | Stop ambient audio. |
| `ambientPause()` | Pause ambient audio. |
| `ambientResume()` | Resume ambient audio. |
| `ambientSetVolume(0.0-1.0)` | Set ambient volume. |

### 📡 Events
Listen to events using `AudioPro.addEventListener(callback)`.

| Event Type | Description |
| :--- | :--- |
| `playback-state` | State changed (playing, paused, etc). |
| `playback-track-changed` | Active track changed. |
| `playback-progress` | Progress update (position/duration). |
| `playback-error` | Error occurred. |
| `playback-queue-ended` | Queue reached the end. |
| `remote-play`, `remote-pause`, etc. | Lock screen controls. |
| `remote-custom-action` | Custom notification button pressed. |
| `sleep-timer-complete` | Sleep timer finished. |

### ℹ️ Status & Info
| Method | Description |
| :--- | :--- |
| `getState()` | Get current playback state. |
| `getTimings()` | Get position and duration. |
| `getVolume()` | Get current volume. |
| `getPlaybackSpeed()` | Get current speed. |
| `getError()` | Get last error. |
| `getProgressInterval()` | Get progress update interval. |

## ⚡️ React Hook

```typescript jsx
import { useAudioPro } from 'react-native-audio-pro';

const Player = () => {
  const { 
    state, 
    position, 
    duration, 
    playingTrack, 
    playbackSpeed, 
    volume, 
    error 
  } = useAudioPro();

  return (
    <View>
      <Text>{playingTrack?.title}</Text>
      <Text>{position} / {duration}</Text>
    </View>
  );
};
```

## 📱 Example App

The `example` folder contains a fully refactored, production-ready implementation demonstrating all features.

### Structure:
- `components/`: Modular UI components (Controls, ProgressBar, etc.)
- `components/URLRefreshLogic.tsx`: Headless component demonstrating expiring URL handling.

### Running the Example:
```bash
yarn install
yarn example android # or ios
```

---
Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
