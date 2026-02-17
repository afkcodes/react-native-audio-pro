# Media3 Alignment Specification

> **Version**: 1.0.0  
> **Target Media3 Version**: 1.6.x  
> **Date**: February 2026

This specification defines how react-native-audio-pro will align 100% with AndroidX Media3 standards. All implementation must follow this spec.

---

## Table of Contents

1. [Design Philosophy](#1-design-philosophy)
2. [Architecture Overview](#2-architecture-overview)
3. [Session Types](#3-session-types)
4. [Player States](#4-player-states)
5. [Service Lifecycle](#5-service-lifecycle)
6. [Event System](#6-event-system)
7. [Commands & Actions](#7-commands--actions)
8. [Queue Management](#8-queue-management)
9. [Notification System](#9-notification-system)
10. [Audio Focus](#10-audio-focus)
11. [Error Handling](#11-error-handling)
12. [TypeScript API](#12-typescript-api)
13. [Implementation Checklist](#13-implementation-checklist)

---

## 1. Design Philosophy

### Media3 Core Principles

1. **Service-Based Playback**: Audio playback runs in a `MediaSessionService` (or `MediaLibraryService`), not the app's main process. This enables true background playback.

2. **Session-Controller Architecture**: 
   - **Session** (in Service): Owns the `Player`, publishes state, handles media buttons
   - **Controller** (in App): Observes state, sends commands, never directly touches the Player

3. **Declarative State**: The Player's state IS the source of truth. Don't maintain parallel state. React to `Player.Listener` callbacks.

4. **Automatic Lifecycle**: Media3 handles:
   - Foreground service promotion when playing
   - Notification creation/updates
   - Service stopping when idle
   - Audio focus management (with `handleAudioFocus = true`)

5. **Command-Based API**: Use `Player.Commands` and `SessionCommand` for all operations. Never send raw intents.

### What This Means for Us

| Old Pattern | New Pattern |
|-------------|-------------|
| Manual `startForeground()` calls | Let `MediaSessionService` handle it |
| Manual notification management | Use `MediaNotificationProvider` |
| Progress timer with `Handler.postDelayed` | React to `Player.Listener.onEvents()` |
| Separate `flowLastEmittedState` tracking | Read from `player.playbackState` directly |
| `stopService()` calls | Remove media items + let service auto-stop |
| Custom `NEXT`/`PREV` session commands | Use `Player.COMMAND_SEEK_TO_NEXT` |

---

## 2. Architecture Overview

### Component Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                     React Native (JS)                           │
│  ┌─────────────────┐  ┌─────────────────┐  ┌────────────────┐   │
│  │   AudioPro API  │  │  useAudioPro()  │  │  internalStore │   │
│  │   (Imperative)  │  │     (Hook)      │  │    (Zustand)   │   │
│  └────────┬────────┘  └────────┬────────┘  └────────▲───────┘   │
│           │                    │                    │           │
└───────────┼────────────────────┼────────────────────┼───────────┘
            │                    │                    │
            │  ┌─────────────────▼────────────────────┤
            │  │            Native Bridge             │
            │  │         (AudioProModule.kt)          │
            │  └─────────────────┬────────────────────┘
            │                    │
            ▼                    ▼
┌───────────────────────────────────────────────────────────────┐
│                    AudioProController                          │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │                   MediaController                        │  │
│  │   - Sends commands (play, pause, seek, etc.)            │  │
│  │   - Observes Player state via listener                  │  │
│  │   - Emits events to JS                                  │  │
│  └─────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────┘
                              │
                    Service Binding
                              │
                              ▼
┌───────────────────────────────────────────────────────────────┐
│                AudioProPlaybackService                         │
│  (extends MediaSessionService OR MediaLibraryService)          │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │                       ExoPlayer                          │  │
│  │  - Actual playback engine                               │  │
│  │  - Owns AudioAttributes, handles focus                  │  │
│  └─────────────────────────────────────────────────────────┘  │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │                     MediaSession                         │  │
│  │  - Publishes Now Playing metadata                       │  │
│  │  - Handles media button events                          │  │
│  │  - Manages available commands                           │  │
│  └─────────────────────────────────────────────────────────┘  │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │               MediaNotificationProvider                  │  │
│  │  - Creates notification from session state              │  │
│  │  - Updates automatically on state change                │  │
│  └─────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────┘
```

---

## 3. Session Types

### MediaSession vs MediaLibrarySession

| Feature | MediaSession | MediaLibrarySession |
|---------|--------------|---------------------|
| **Use Case** | Simple playback control | Browsable content (Android Auto, etc.) |
| **Controller** | `MediaController` | `MediaBrowser` |
| **Browse Tree** | Not supported | Required (`onGetLibraryRoot`, `onGetChildren`) |
| **Complexity** | Lower | Higher |
| **Recommended For** | Music players, podcast apps | Content providers, Android Auto apps |

### Our Approach

We will support **both** session types via a configuration option:

```typescript
AudioPro.configure({
  sessionType: 'session' | 'library', // Default: 'session'
  // Only relevant when sessionType === 'library':
  libraryConfig: {
    rootId: string,
    rootExtras: Record<string, string>,
  },
});
```

**Default**: `MediaSession` (simpler, covers 90% of use cases)  
**Optional**: `MediaLibrarySession` when app needs Android Auto / browsable content

---

## 4. Player States

### Media3 `Player.State` Values

| Value | Name | Meaning |
|-------|------|---------|
| `1` | `STATE_IDLE` | Player instantiated but no media loaded |
| `2` | `STATE_BUFFERING` | Media is loading/buffering |
| `3` | `STATE_READY` | Media is loaded and ready (may or may not be playing) |
| `4` | `STATE_ENDED` | Playback completed to end of media |

### Combined with `playWhenReady`

The actual "is playing" state is determined by `playbackState == STATE_READY && playWhenReady == true`:

| playbackState | playWhenReady | Effective State | Our JS State |
|---------------|---------------|-----------------|--------------|
| IDLE | false | No media | `IDLE` |
| IDLE | true | No media (invalid) | `IDLE` |
| BUFFERING | false | Paused, buffering | `LOADING` |
| BUFFERING | true | Playing, buffering | `LOADING` |
| READY | false | Paused | `PAUSED` |
| READY | true | Playing | `PLAYING` |
| ENDED | false | Finished | `ENDED` |
| ENDED | true | Will repeat (if enabled) | `ENDED` |

### New JS State Enum

```typescript
export enum AudioProState {
  /** No media loaded. Initial state. */
  IDLE = 'IDLE',
  
  /** Media is buffering/loading. */
  LOADING = 'LOADING',
  
  /** Media is ready and currently playing. */
  PLAYING = 'PLAYING',
  
  /** Media is ready but paused. */
  PAUSED = 'PAUSED',
  
  /** Playback reached end of current media item. */
  ENDED = 'ENDED',
  
  /** Unrecoverable error occurred. */
  ERROR = 'ERROR',
}
```

**Note**: We remove `STOPPED` as it conflated IDLE and PAUSED. Use:
- `IDLE` for no media loaded
- `PAUSED` for paused at position 0
- `ENDED` for finished playing

---

## 5. Service Lifecycle

### Media3 Automatic Lifecycle

Media3 manages the foreground service lifecycle automatically:

1. **Service Binds**: When `MediaController.Builder.buildAsync()` connects
2. **Foreground Promotion**: When `player.playWhenReady = true` and notification is posted
3. **Foreground Demotion**: When `player.playWhenReady = false` for extended period
4. **Service Stops**: When no controllers bound AND no recent playback

### DO NOT (Anti-Patterns)

```kotlin
// ❌ NEVER do this
context.stopService(Intent(context, AudioProPlaybackService::class.java))

// ❌ NEVER do this
startForeground(NOTIFICATION_ID, notification)

// ❌ NEVER do this
stopForeground(STOP_FOREGROUND_REMOVE)
```

### DO (Correct Patterns)

```kotlin
// ✅ To stop playback and allow service to wind down:
player.stop()           // or player.pause()
player.clearMediaItems()

// ✅ To configure foreground timeout (optional):
// In MediaSession.Builder:
.setForegroundServiceTimeoutMs(10 * 60 * 1000L) // 10 minutes

// ✅ To handle task removal:
override fun onTaskRemoved(rootIntent: Intent?) {
  // Option A: Keep playing (default Media3 behavior)
  // Do nothing
  
  // Option B: Stop on swipe
  player.pause()
  player.clearMediaItems()
  // Service will auto-stop when idle
}
```

### Service Timeout Configuration

The `setForegroundServiceTimeoutMs()` method controls how long the service stays in foreground after playback stops:

```kotlin
MediaSession.Builder(this, player)
  .setSessionCallback(callback)
  .setForegroundServiceTimeoutMs(10 * 60 * 1000L) // 10 minutes
  .build()
```

- **Default**: 10 minutes
- **Setting to 0**: Service stays in foreground indefinitely (not recommended)
- **Recommended**: 5-10 minutes for music apps

---

## 6. Event System

### Media3 Player.Listener

The `Player.Listener` interface provides all necessary callbacks. Key methods:

```kotlin
interface Player.Listener {
  // State changes
  fun onPlaybackStateChanged(playbackState: Int)
  fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int)
  fun onIsPlayingChanged(isPlaying: Boolean)
  
  // Media changes
  fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int)
  fun onTimelineChanged(timeline: Timeline, reason: Int)
  
  // Position changes
  fun onPositionDiscontinuity(oldPosition: PositionInfo, newPosition: PositionInfo, reason: Int)
  
  // Errors
  fun onPlayerError(error: PlaybackException)
  fun onPlayerErrorChanged(error: PlaybackException?)
  
  // Batched events (for efficiency)
  fun onEvents(player: Player, events: Player.Events)
}
```

### Using `onEvents()` for Efficient Updates

Instead of reacting to each individual callback (which can cause multiple JS emissions per frame), use `onEvents()`:

```kotlin
override fun onEvents(player: Player, events: Player.Events) {
  // Check what changed
  if (events.containsAny(
    Player.EVENT_PLAYBACK_STATE_CHANGED,
    Player.EVENT_PLAY_WHEN_READY_CHANGED,
    Player.EVENT_IS_PLAYING_CHANGED
  )) {
    emitStateChanged(player)
  }
  
  if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
    emitTrackChanged(player)
  }
  
  if (events.contains(Player.EVENT_POSITION_DISCONTINUITY)) {
    if (isSeekComplete) {
      emitSeekComplete(player)
    }
  }
}
```

### New Event Types

```typescript
export enum AudioProEventType {
  /** Player state changed (IDLE, LOADING, PLAYING, PAUSED, ENDED, ERROR) */
  STATE_CHANGED = 'STATE_CHANGED',
  
  /** Progress update (position, duration, buffered) */
  PROGRESS = 'PROGRESS',
  
  /** Current media item changed */
  MEDIA_ITEM_CHANGED = 'MEDIA_ITEM_CHANGED', // Renamed from TRACK_CHANGED
  
  /** Seek operation completed */
  SEEK_COMPLETE = 'SEEK_COMPLETE',
  
  /** Playback error occurred */
  ERROR = 'ERROR', // Renamed from PLAYBACK_ERROR
  
  /** Playback speed changed */
  SPEED_CHANGED = 'SPEED_CHANGED',
  
  /** Queue/timeline changed */
  QUEUE_CHANGED = 'QUEUE_CHANGED', // New
  
  /** Audio session ID changed (for visualizers/EQ) */
  AUDIO_SESSION_CHANGED = 'AUDIO_SESSION_CHANGED', // New
  
  /** Remote command received (for logging/analytics) */
  REMOTE_COMMAND = 'REMOTE_COMMAND', // Replaces REMOTE_NEXT, REMOTE_PREV
}
```

### Event Payloads

```typescript
interface AudioProStateChangedEvent {
  type: 'STATE_CHANGED';
  state: AudioProState;
  playWhenReady: boolean;
  playbackState: number; // Raw Media3 state for debugging
  position: number;
  duration: number;
  currentIndex: number;
}

interface AudioProProgressEvent {
  type: 'PROGRESS';
  position: number;
  duration: number;
  bufferedPosition: number; // New - how much is buffered
}

interface AudioProMediaItemChangedEvent {
  type: 'MEDIA_ITEM_CHANGED';
  reason: 'auto' | 'seek' | 'set_media_item' | 'repeat';
  mediaItem: AudioProMediaItem | null;
  currentIndex: number;
  previousIndex: number;
}

interface AudioProErrorEvent {
  type: 'ERROR';
  code: number;
  message: string;
  cause?: string;
  recoverable: boolean; // New - whether player can recover
}
```

---

## 7. Commands & Actions

### Media3 Player Commands

Media3 separates **capabilities** (available commands) from **execution** (calling methods):

```kotlin
// Check if command is available
if (controller.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT)) {
  controller.seekToNextMediaItem()
}
```

Available commands are configured via `MediaSession.Callback.onConnect()`:

```kotlin
override fun onConnect(
  session: MediaSession,
  controller: MediaSession.ControllerInfo
): MediaSession.ConnectionResult {
  return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
    .setAvailablePlayerCommands(
      MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
        .addAll(
          Player.COMMAND_SEEK_TO_NEXT,
          Player.COMMAND_SEEK_TO_PREVIOUS,
          Player.COMMAND_SEEK_BACK,
          Player.COMMAND_SEEK_FORWARD,
          Player.COMMAND_SET_SPEED_AND_PITCH,
          Player.COMMAND_SET_REPEAT_MODE,
          Player.COMMAND_SET_SHUFFLE_MODE,
        )
        .build()
    )
    .setAvailableSessionCommands(
      // Custom commands (for app-specific features)
      SessionCommands.Builder()
        .add(SessionCommand("LIKE", Bundle.EMPTY))
        .add(SessionCommand("SET_EQUALIZER", Bundle.EMPTY))
        .build()
    )
    .build()
}
```

### Standard Controls (Use Player Commands)

| Action | Player Command | Native Call |
|--------|----------------|-------------|
| Play | `COMMAND_PLAY_PAUSE` | `controller.play()` |
| Pause | `COMMAND_PLAY_PAUSE` | `controller.pause()` |
| Seek to position | `COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM` | `controller.seekTo(positionMs)` |
| Next track | `COMMAND_SEEK_TO_NEXT` | `controller.seekToNextMediaItem()` |
| Previous track | `COMMAND_SEEK_TO_PREVIOUS` | `controller.seekToPreviousMediaItem()` |
| Skip forward | `COMMAND_SEEK_FORWARD` | `controller.seekForward()` |
| Skip back | `COMMAND_SEEK_BACK` | `controller.seekBack()` |
| Set speed | `COMMAND_SET_SPEED_AND_PITCH` | `controller.setPlaybackSpeed(speed)` |
| Set repeat | `COMMAND_SET_REPEAT_MODE` | `controller.repeatMode = mode` |
| Set shuffle | `COMMAND_SET_SHUFFLE_MODE` | `controller.shuffleModeEnabled = true` |

#### Why Standard Player Commands (Not Custom Actions)

**Never use custom SessionCommands for standard navigation (next/previous/seek).**

Android's MediaStyle notification has reserved slots for Next and Previous buttons. When you define 
these as Custom Actions instead of Standard Player Commands:

1. **Disabled Buttons (OxygenOS, OneUI, etc.)**: The system sees a media player but doesn't detect 
   `COMMAND_SEEK_TO_NEXT` or `COMMAND_SEEK_TO_PREVIOUS` capabilities, so it disables its native 
   buttons in the reserved slots.

2. **Wrong Layout**: Custom buttons get crammed into "additional actions" slots, appearing in the 
   wrong order or only in expanded notification view.

3. **Inconsistent Icons**: Some Android skins invert or replace icons for custom actions, leading 
   to visual bugs.

By using Standard Player Commands:
- Android/OxygenOS lights up its native Next/Prev buttons in the correct reserved slots
- Buttons are properly enabled when there's a queue
- Layout is correct (standard left/right positioning)
- System handles icons consistently

**Rule**: Use the system's native controls. Only use `SessionCommand` for features that have no 
Player API equivalent (like, equalizer, sleep timer, etc.).

### Custom Commands (For App-Specific Features)

Only use `SessionCommand` for features that aren't part of the Player API:

| Feature | Session Command | Args |
|---------|-----------------|------|
| Like/Heart | `LIKE` | `{}` |
| Save to Library | `SAVE` | `{}` |
| Set Equalizer | `SET_EQUALIZER` | `{ gains: float[] }` |
| Set Bass Boost | `SET_BASS_BOOST` | `{ strength: int }` |
| Sleep Timer | `SET_SLEEP_TIMER` | `{ durationMs: long }` |

---

## 8. Queue Management

### Media3 Queue Model

Media3 uses a `Timeline` structure, not a simple list:

```kotlin
// DON'T think of it as:
val queue: List<Track>

// Think of it as:
val timeline: Timeline  // Contains windows (media items)
```

### Key Operations

```kotlin
// Add media items
player.addMediaItem(mediaItem)
player.addMediaItems(listOf(item1, item2, item3))
player.addMediaItem(index, mediaItem)  // Insert at position

// Remove media items
player.removeMediaItem(index)
player.removeMediaItems(fromIndex, toIndex)
player.clearMediaItems()

// Replace
player.setMediaItem(mediaItem)      // Clear + add
player.setMediaItems(items)         // Clear + add all
player.replaceMediaItem(index, item)

// Move (reorder)
player.moveMediaItem(fromIndex, toIndex)
player.moveMediaItems(fromIndex, toIndex, newIndex)
```

### Queue Change Detection

Listen to `onTimelineChanged()`:

```kotlin
override fun onTimelineChanged(timeline: Timeline, reason: Int) {
  when (reason) {
    Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE -> {
      // Media source updated (e.g., live stream timeline)
    }
    Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED -> {
      // Queue was modified (add/remove/move/set)
      emitQueueChanged(buildQueueFromTimeline(timeline))
    }
  }
}
```

### JS API for Queue

```typescript
// Queue manipulation
AudioPro.addToQueue(items: AudioProMediaItem | AudioProMediaItem[])
AudioPro.addToQueueAt(index: number, items: AudioProMediaItem | AudioProMediaItem[])
AudioPro.removeFromQueue(index: number)
AudioPro.removeFromQueueRange(fromIndex: number, toIndex: number)
AudioPro.clearQueue()
AudioPro.moveInQueue(fromIndex: number, toIndex: number)
AudioPro.replaceQueue(items: AudioProMediaItem[])

// Queue access
AudioPro.getQueue(): Promise<AudioProMediaItem[]>
AudioPro.getQueueSize(): number // Sync getter
AudioPro.getCurrentIndex(): number // Sync getter
```

---

## 9. Notification System

### DefaultMediaNotificationProvider

Media3 provides `DefaultMediaNotificationProvider` which handles most notification logic. We extend it only for customization:

```kotlin
class AudioProNotificationProvider(context: Context) : DefaultMediaNotificationProvider(context) {

  init {
    // Configure channel
    setChannelName(R.string.notification_channel_name)
    setChannelImportance(NotificationManager.IMPORTANCE_LOW)
  }

  override fun getMediaButtons(
    session: MediaSession,
    playerCommands: Player.Commands,
    customLayout: ImmutableList<CommandButton>,
    showPauseButton: Boolean
  ): ImmutableList<CommandButton> {
    // Use custom layout from session callback if provided
    if (customLayout.isNotEmpty()) {
      return customLayout
    }
    // Otherwise use default
    return super.getMediaButtons(session, playerCommands, customLayout, showPauseButton)
  }
}
```

### Notification Buttons via `setMediaButtonPreferences()`

Configure buttons in session callback's `onConnect()`:

```kotlin
override fun onConnect(...): MediaSession.ConnectionResult {
  val buttons = mutableListOf<CommandButton>()
  
  // Previous
  buttons.add(
    CommandButton.Builder(CommandButton.ICON_PREVIOUS)
      .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
      .build()
  )
  
  // Play/Pause is automatic
  
  // Next
  buttons.add(
    CommandButton.Builder(CommandButton.ICON_NEXT)
      .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
      .build()
  )
  
  // Custom: Like
  buttons.add(
    CommandButton.Builder(CommandButton.ICON_HEART_UNFILLED)
      .setSessionCommand(SessionCommand("LIKE", Bundle.EMPTY))
      .build()
  )
  
  return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
    .setMediaButtonPreferences(buttons)
    .build()
}
```

### Updating Buttons at Runtime

Use `MediaSession.setMediaButtonPreferences()`:

```kotlin
fun updateLikedState(isLiked: Boolean) {
  val icon = if (isLiked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
  
  val buttons = currentButtons.map {
    if (it.sessionCommand?.customAction == "LIKE") {
      CommandButton.Builder(icon)
        .setSessionCommand(SessionCommand("LIKE", Bundle.EMPTY))
        .build()
    } else {
      it
    }
  }
  
  session.mediaButtonPreferences = buttons
}
```

---

## 10. Audio Focus

### Media3 Audio Focus Handling

With `handleAudioFocus = true` in AudioAttributes, Media3 handles:

1. **Focus Request**: When `playWhenReady = true` and playback starts
2. **Focus Loss (transient)**: Pause → Resume automatically when regained
3. **Focus Loss (duck)**: Volume reduction → Restore when regained
4. **Focus Loss (permanent)**: Pause playback

```kotlin
val audioAttributes = AudioAttributes.Builder()
  .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
  .setUsage(C.USAGE_MEDIA)
  .build()

val player = ExoPlayer.Builder(context)
  .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
  .build()
```

### Audio Becoming Noisy

Handle headphone disconnect:

```kotlin
player.setHandleAudioBecomingNoisy(true) // Pause when headphones unplugged
```

---

## 11. Error Handling

### Media3 Error Types

```kotlin
sealed class PlaybackException {
  // Error codes
  ERROR_CODE_UNSPECIFIED            // Generic error
  ERROR_CODE_REMOTE_ERROR           // Server error
  ERROR_CODE_BEHIND_LIVE_WINDOW     // Live stream seeked too far back
  ERROR_CODE_TIMEOUT                // Network timeout
  ERROR_CODE_DECODING_FAILED        // Codec error
  ERROR_CODE_AUDIO_TRACK_INIT_FAILED // Audio initialization failed
  ERROR_CODE_DRM_*                  // DRM-related errors
  ERROR_CODE_IO_*                   // Network/IO errors
}
```

### Recovery Strategy

```kotlin
override fun onPlayerError(error: PlaybackException) {
  when (error.errorCode) {
    // Recoverable errors - retry
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> {
      // Emit error event but don't tear down
      emitError(error, recoverable = true)
      // Auto-retry with exponential backoff is handled by Media3
    }
    
    // Unrecoverable errors - tear down
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> {
      emitError(error, recoverable = false)
      player.stop()
      player.clearMediaItems()
    }
  }
}
```

### JS Error Payload

```typescript
interface AudioProError {
  code: AudioProErrorCode;
  message: string;
  recoverable: boolean;
  cause?: string;
}

enum AudioProErrorCode {
  UNKNOWN = 0,
  NETWORK_TIMEOUT = 1001,
  NETWORK_FAILED = 1002,
  DECODE_FAILED = 2001,
  AUDIO_INIT_FAILED = 2002,
  DRM_FAILED = 3001,
  CONTENT_NOT_FOUND = 4001,
  PERMISSION_DENIED = 4002,
}
```

---

## 12. TypeScript API

The TypeScript API mirrors Media3's `Player` interface as closely as possible, with method names matching native for consistency.

### Mapping: JS ↔ Media3 Player

| JavaScript Method | Media3 Player Method | Notes |
|------------------|---------------------|-------|
| `play()` | `play()` | ✓ Same |
| `pause()` | `pause()` | ✓ Same |
| `stop()` | `stop()` | ✓ Same |
| `prepare()` | `prepare()` | ✓ Same |
| `seekTo(positionMs)` | `seekTo(positionMs)` | ✓ Same |
| `seekForward()` | `seekForward()` | ✓ Same |
| `seekBack()` | `seekBack()` | ✓ Same |
| `seekToNextMediaItem()` | `seekToNextMediaItem()` | ✓ Same |
| `seekToPreviousMediaItem()` | `seekToPreviousMediaItem()` | ✓ Same |
| `seekToMediaItem(index)` | `seekToDefaultPosition(index)` | Simplified name |
| `setMediaItems(items)` | `setMediaItems(items)` | ✓ Same |
| `addMediaItem(item)` | `addMediaItem(item)` | ✓ Same |
| `addMediaItems(items)` | `addMediaItems(items)` | ✓ Same |
| `addMediaItemAt(index, item)` | `addMediaItem(index, item)` | Explicit name |
| `removeMediaItem(index)` | `removeMediaItem(index)` | ✓ Same |
| `removeMediaItems(from, to)` | `removeMediaItems(from, to)` | ✓ Same |
| `moveMediaItem(from, to)` | `moveMediaItem(from, to)` | ✓ Same |
| `clearMediaItems()` | `clearMediaItems()` | ✓ Same |
| `replaceMediaItem(index, item)` | `replaceMediaItem(index, item)` | ✓ Same |
| `setRepeatMode(mode)` | `setRepeatMode(mode)` | ✓ Same |
| `setShuffleModeEnabled(enabled)` | `setShuffleModeEnabled(enabled)` | ✓ Same |
| `setPlaybackSpeed(speed)` | `setPlaybackSpeed(speed)` | ✓ Same |
| `setVolume(volume)` | `setVolume(volume)` | ✓ Same |
| `isPlaying()` | `isPlaying()` | ✓ Same |
| `getPlaybackState()` | `getPlaybackState()` | ✓ Same |
| `getCurrentPosition()` | `getCurrentPosition()` | ✓ Same |
| `getDuration()` | `getDuration()` | ✓ Same |
| `getBufferedPosition()` | `getBufferedPosition()` | ✓ Same |
| `getCurrentMediaItem()` | `getCurrentMediaItem()` | ✓ Same |
| `getCurrentMediaItemIndex()` | `getCurrentMediaItemIndex()` | ✓ Same |
| `getMediaItemCount()` | `getMediaItemCount()` | ✓ Same |

### AudioPro Class API

```typescript
export const AudioPro = {
  // ─────────────────────────────────────────────────────────────
  // Configuration
  // ─────────────────────────────────────────────────────────────
  
  /**
   * Configure the audio player. Call once at app startup.
   */
  configure(options: AudioProConfig): void;
  
  // ─────────────────────────────────────────────────────────────
  // Playback Control (mirrors Player interface)
  // ─────────────────────────────────────────────────────────────
  
  /** Prepare the player with current media items */
  prepare(): void;
  
  /** Start or resume playback (sets playWhenReady = true) */
  play(): void;
  
  /** Pause playback (sets playWhenReady = false) */
  pause(): void;
  
  /** Stop playback, release resources, and reset to IDLE state */
  stop(): void;
  
  // ─────────────────────────────────────────────────────────────
  // Seeking (mirrors Player interface)
  // ─────────────────────────────────────────────────────────────
  
  /** Seek to absolute position in milliseconds */
  seekTo(positionMs: number): void;
  
  /** Seek forward by seekForwardIncrementMs (configured value) */
  seekForward(): void;
  
  /** Seek back by seekBackIncrementMs (configured value) */
  seekBack(): void;
  
  /** Seek to specific media item in queue */
  seekToMediaItem(mediaItemIndex: number): void;
  
  /** Seek to next media item */
  seekToNextMediaItem(): void;
  
  /** Seek to previous media item */
  seekToPreviousMediaItem(): void;
  
  // ─────────────────────────────────────────────────────────────
  // Queue Management (mirrors Player interface)
  // ─────────────────────────────────────────────────────────────
  
  /** Set media items (replaces entire queue) */
  setMediaItems(items: AudioProMediaItem[], startIndex?: number, startPositionMs?: number): void;
  
  /** Set single media item (replaces entire queue) */
  setMediaItem(item: AudioProMediaItem, startPositionMs?: number): void;
  
  /** Add single item to end of queue */
  addMediaItem(item: AudioProMediaItem): void;
  
  /** Add multiple items to end of queue */
  addMediaItems(items: AudioProMediaItem[]): void;
  
  /** Insert item at specific index */
  addMediaItemAt(index: number, item: AudioProMediaItem): void;
  
  /** Remove item at index */
  removeMediaItem(index: number): void;
  
  /** Remove items in range [fromIndex, toIndex) */
  removeMediaItems(fromIndex: number, toIndex: number): void;
  
  /** Move item from one position to another */
  moveMediaItem(currentIndex: number, newIndex: number): void;
  
  /** Replace item at index with new item */
  replaceMediaItem(index: number, item: AudioProMediaItem): void;
  
  /** Clear all media items */
  clearMediaItems(): void;
  
  // ─────────────────────────────────────────────────────────────
  // Playback Parameters (mirrors Player interface)
  // ─────────────────────────────────────────────────────────────
  
  /** Set playback speed (0.25 to 2.0) */
  setPlaybackSpeed(speed: number): void;
  
  /** Set volume (0.0 to 1.0) */
  setVolume(volume: number): void;
  
  /** Set repeat mode (OFF, ONE, ALL) */
  setRepeatMode(mode: RepeatMode): void;
  
  /** Enable/disable shuffle mode */
  setShuffleModeEnabled(enabled: boolean): void;
  
  /** Enable/disable silence skipping (Android only) */
  setSkipSilenceEnabled(enabled: boolean): void;
  
  // ─────────────────────────────────────────────────────────────
  // State Getters (mirrors Player interface)
  // ─────────────────────────────────────────────────────────────
  
  /** Get playback state (STATE_IDLE, STATE_BUFFERING, STATE_READY, STATE_ENDED) */
  getPlaybackState(): PlaybackState;
  
  /** Check if currently playing (STATE_READY + playWhenReady) */
  isPlaying(): boolean;
  
  /** Get current position in milliseconds */
  getCurrentPosition(): number;
  
  /** Get total duration in milliseconds */
  getDuration(): number;
  
  /** Get buffered position in milliseconds */
  getBufferedPosition(): number;
  
  /** Get current playback speed */
  getPlaybackSpeed(): number;
  
  /** Get current volume */
  getVolume(): number;
  
  /** Get current repeat mode */
  getRepeatMode(): RepeatMode;
  
  /** Check if shuffle mode is enabled */
  getShuffleModeEnabled(): boolean;
  
  // ─────────────────────────────────────────────────────────────
  // Queue Getters (mirrors Player interface)
  // ─────────────────────────────────────────────────────────────
  
  /** Get current media item */
  getCurrentMediaItem(): AudioProMediaItem | null;
  
  /** Get current index in queue */
  getCurrentMediaItemIndex(): number;
  
  /** Get number of items in queue */
  getMediaItemCount(): number;
  
  /** Get media item at index */
  getMediaItemAt(index: number): AudioProMediaItem | null;
  
  /** Check if there's a next media item */
  hasNextMediaItem(): boolean;
  
  /** Check if there's a previous media item */
  hasPreviousMediaItem(): boolean;
  
  // ─────────────────────────────────────────────────────────────
  // Full Queue Access (async because queue may be large)
  // ─────────────────────────────────────────────────────────────
  
  /** Get all media items in queue */
  getMediaItems(): Promise<AudioProMediaItem[]>;
  
  // ─────────────────────────────────────────────────────────────
  // Events (mirrors Player.Listener pattern)
  // ─────────────────────────────────────────────────────────────
  
  /** Subscribe to player events */
  addListener(callback: AudioProEventCallback): () => void;
  
  // ─────────────────────────────────────────────────────────────
  // Media Session (notification/lock screen)
  // ─────────────────────────────────────────────────────────────
  
  /** Configure which buttons appear on notification */
  setMediaButtonPreferences(buttons: MediaButtonType[]): void;
  
  // ─────────────────────────────────────────────────────────────
  // Audio Effects (Android only)
  // ─────────────────────────────────────────────────────────────
  
  /** Set 10-band equalizer gains (in dB, typically -10 to +10) */
  setEqualizerGains(gains: number[]): void;
  
  /** Set bass boost strength (0 to 1000) */
  setBassBoostStrength(strength: number): void;
  
  // ─────────────────────────────────────────────────────────────
  // Cache Management (Android only)
  // ─────────────────────────────────────────────────────────────
  
  /** Get current cache size in bytes */
  getCacheSize(): Promise<number>;
  
  /** Clear all cached data */
  clearCache(): Promise<void>;
  
  // ─────────────────────────────────────────────────────────────
  // Sleep Timer
  // ─────────────────────────────────────────────────────────────
  
  /** Start sleep timer (pauses after durationMs) */
  setSleepTimer(durationMs: number): void;
  
  /** Cancel active sleep timer */
  cancelSleepTimer(): void;
  
  // ─────────────────────────────────────────────────────────────
  // Ambient Audio (secondary player)
  // ─────────────────────────────────────────────────────────────
  
  /** Start ambient audio playback */
  ambientPlay(options: AmbientPlayOptions): void;
  
  /** Stop ambient audio */
  ambientStop(): void;
  
  /** Set ambient audio volume */
  ambientSetVolume(volume: number): void;
  
  /** Pause ambient audio */
  ambientPause(): void;
  
  /** Resume ambient audio */
  ambientResume(): void;
};
```

### Types (aligned with Media3 constants)

```typescript
// ─────────────────────────────────────────────────────────────
// Player States (matches Player.STATE_* exactly)
// ─────────────────────────────────────────────────────────────
export const PlaybackState = {
  /** Player not initialized or reset. Call prepare() to load. */
  STATE_IDLE: 1,
  /** Player is buffering data. Playback will resume when ready. */
  STATE_BUFFERING: 2,
  /** Player is ready and playback can proceed. */
  STATE_READY: 3,
  /** Playback reached end of media or playlist. */
  STATE_ENDED: 4,
} as const;

export type PlaybackState = typeof PlaybackState[keyof typeof PlaybackState];

// ─────────────────────────────────────────────────────────────
// Repeat Modes (matches Player.REPEAT_MODE_* exactly)
// ─────────────────────────────────────────────────────────────
export const RepeatMode = {
  /** Normal playback without repeating. */
  REPEAT_MODE_OFF: 0,
  /** Repeat the currently playing media item indefinitely. */
  REPEAT_MODE_ONE: 1,
  /** Repeat the entire playlist indefinitely. */
  REPEAT_MODE_ALL: 2,
} as const;

export type RepeatMode = typeof RepeatMode[keyof typeof RepeatMode];

// ─────────────────────────────────────────────────────────────
// Audio Content Type (matches AudioAttributes.CONTENT_TYPE_*)
// ─────────────────────────────────────────────────────────────
export const ContentType = {
  /** Content type for music. */
  CONTENT_TYPE_MUSIC: 2,
  /** Content type for speech (podcasts, audiobooks). */
  CONTENT_TYPE_SPEECH: 1,
} as const;

export type ContentType = typeof ContentType[keyof typeof ContentType];

// ─────────────────────────────────────────────────────────────
// Configuration
// ─────────────────────────────────────────────────────────────
export interface AudioProConfig {
  /** Content type for audio focus. Default: CONTENT_TYPE_MUSIC */
  contentType?: ContentType;
  
  /** Enable debug logging */
  debug?: boolean;
  
  /** Progress event interval in ms. Default: 1000 */
  progressIntervalMs?: number;
  
  /** Seek forward increment in ms. Default: 10000 (10 seconds) */
  seekForwardIncrementMs?: number;
  
  /** Seek back increment in ms. Default: 10000 (10 seconds) */
  seekBackIncrementMs?: number;
  
  /** Session type. Default: 'session' */
  sessionType?: 'session' | 'library';
  
  /** Cache settings (Android only) */
  cache?: {
    enabled: boolean;
    maxSizeBytes?: number;
  };
}

// ─────────────────────────────────────────────────────────────
// Media Item (matches MediaItem structure)
// ─────────────────────────────────────────────────────────────
export interface AudioProMediaItem {
  /** Unique media ID */
  mediaId: string;
  
  /** Media URL (URI in Media3 terms) */
  uri: string;
  
  /** Title (MediaMetadata.title) */
  title: string;
  
  /** Artist (MediaMetadata.artist) */
  artist?: string;
  
  /** Album title (MediaMetadata.albumTitle) */
  albumTitle?: string;
  
  /** Artwork URI (MediaMetadata.artworkUri) */
  artworkUri?: string;
  
  /** Duration in milliseconds (MediaMetadata.durationMs) */
  durationMs?: number;
  
  /** Custom extras (MediaItem.MediaItemExtras - preserved across bridge) */
  extras?: Record<string, unknown>;
}

// ─────────────────────────────────────────────────────────────
// Media Button Types (for notification customization)
// ─────────────────────────────────────────────────────────────
export const MediaButtonType = {
  PLAY_PAUSE: 'playPause',
  PREVIOUS: 'previous',
  NEXT: 'next',
  SEEK_FORWARD: 'seekForward',
  SEEK_BACK: 'seekBack',
} as const;

export type MediaButtonType = typeof MediaButtonType[keyof typeof MediaButtonType];

// ─────────────────────────────────────────────────────────────
// Events (mirrors Player.Listener callbacks)
// ─────────────────────────────────────────────────────────────
export type AudioProEvent =
  | { type: 'playbackStateChanged'; playbackState: PlaybackState }
  | { type: 'playWhenReadyChanged'; playWhenReady: boolean }
  | { type: 'isPlayingChanged'; isPlaying: boolean }
  | { type: 'mediaItemTransition'; mediaItem: AudioProMediaItem | null; reason: MediaItemTransitionReason }
  | { type: 'timelineChanged'; reason: TimelineChangeReason }
  | { type: 'positionDiscontinuity'; oldPosition: number; newPosition: number; reason: PositionDiscontinuityReason }
  | { type: 'repeatModeChanged'; repeatMode: RepeatMode }
  | { type: 'shuffleModeEnabledChanged'; shuffleModeEnabled: boolean }
  | { type: 'playbackParametersChanged'; playbackSpeed: number }
  | { type: 'playerError'; error: AudioProError }
  | { type: 'progress'; currentPosition: number; duration: number; bufferedPosition: number };

// Event reason constants (match Media3 exactly)
export const MediaItemTransitionReason = {
  MEDIA_ITEM_TRANSITION_REASON_REPEAT: 0,
  MEDIA_ITEM_TRANSITION_REASON_AUTO: 1,
  MEDIA_ITEM_TRANSITION_REASON_SEEK: 2,
  MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED: 3,
} as const;

export type MediaItemTransitionReason = typeof MediaItemTransitionReason[keyof typeof MediaItemTransitionReason];

export const TimelineChangeReason = {
  TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED: 0,
  TIMELINE_CHANGE_REASON_SOURCE_UPDATE: 1,
} as const;

export type TimelineChangeReason = typeof TimelineChangeReason[keyof typeof TimelineChangeReason];

export const PositionDiscontinuityReason = {
  DISCONTINUITY_REASON_AUTO_TRANSITION: 0,
  DISCONTINUITY_REASON_SEEK: 1,
  DISCONTINUITY_REASON_SEEK_ADJUSTMENT: 2,
  DISCONTINUITY_REASON_SKIP: 3,
  DISCONTINUITY_REASON_REMOVE: 4,
  DISCONTINUITY_REASON_INTERNAL: 5,
} as const;

export type PositionDiscontinuityReason = typeof PositionDiscontinuityReason[keyof typeof PositionDiscontinuityReason];

export type AudioProEventCallback = (event: AudioProEvent) => void;
```

### useAudioPro Hook (reactive state access)

```typescript
export interface UseAudioProState {
  // ─────────────────────────────────────────────────────────────
  // Playback State (mirrors Player interface getters)
  // ─────────────────────────────────────────────────────────────
  
  /** Current playback state (STATE_IDLE, STATE_BUFFERING, STATE_READY, STATE_ENDED) */
  playbackState: PlaybackState;
  
  /** Whether playback should proceed when ready */
  playWhenReady: boolean;
  
  /** Derived: playbackState === STATE_READY && playWhenReady */
  isPlaying: boolean;
  
  // ─────────────────────────────────────────────────────────────
  // Position & Duration (in milliseconds)
  // ─────────────────────────────────────────────────────────────
  
  /** Current playback position in milliseconds */
  currentPosition: number;
  
  /** Duration of current media item in milliseconds */
  duration: number;
  
  /** Buffered position in milliseconds */
  bufferedPosition: number;
  
  // ─────────────────────────────────────────────────────────────
  // Current Media Item
  // ─────────────────────────────────────────────────────────────
  
  /** Current media item or null if queue is empty */
  currentMediaItem: AudioProMediaItem | null;
  
  /** Index of current media item in timeline (-1 if empty) */
  currentMediaItemIndex: number;
  
  /** Total number of media items in timeline */
  mediaItemCount: number;
  
  // ─────────────────────────────────────────────────────────────
  // Playback Parameters
  // ─────────────────────────────────────────────────────────────
  
  /** Current playback speed (1.0 = normal) */
  playbackSpeed: number;
  
  /** Current volume (0.0 to 1.0) */
  volume: number;
  
  /** Current repeat mode */
  repeatMode: RepeatMode;
  
  /** Whether shuffle mode is enabled */
  shuffleModeEnabled: boolean;
  
  // ─────────────────────────────────────────────────────────────
  // Error State
  // ─────────────────────────────────────────────────────────────
  
  /** Current error or null */
  error: AudioProError | null;
}

/**
 * Hook for reactive access to player state.
 * Uses Zustand for efficient selective subscriptions.
 */
export function useAudioPro(): UseAudioProState;

/**
 * Selector variant for optimal re-render performance.
 * Only triggers re-render when selected value changes.
 */
export function useAudioPro<T>(
  selector: (state: UseAudioProState) => T,
  equalityFn?: (a: T, b: T) => boolean
): T;

// ─────────────────────────────────────────────────────────────
// Error Types
// ─────────────────────────────────────────────────────────────
export interface AudioProError {
  /** Error code matching PlaybackException.errorCode */
  code: number;
  /** Human-readable error message */
  message: string;
  /** Whether this error is recoverable (retryable) */
  isRecoverable: boolean;
}
```

---

## 13. Implementation Checklist

### Phase 1: Core Architecture

- [ ] Create `MediaSession`-based service (default)
- [ ] Create `MediaLibrarySession`-based service (optional)
- [ ] Implement proper `Player.Listener` using `onEvents()`
- [ ] Remove manual `Handler.postDelayed` progress timer
- [ ] Remove all manual `startForeground`/`stopService` calls
- [ ] Implement proper `MediaSession.Callback`

### Phase 2: Event System

- [ ] Define new event types
- [ ] Implement batched event emission via `onEvents()`
- [ ] Add `bufferedPosition` to progress events
- [ ] Add `QUEUE_CHANGED` event
- [ ] Add `AUDIO_SESSION_CHANGED` event

### Phase 3: Commands

- [ ] Replace custom NEXT/PREV session commands with Player commands
- [ ] Implement `setAvailableCommands()` properly
- [ ] Keep only app-specific features as session commands

### Phase 4: Notification

- [ ] Use `DefaultMediaNotificationProvider` properly
- [ ] Implement `setMediaButtonPreferences()` updates
- [ ] Add dynamic button state (like/unlike)

### Phase 5: Queue

- [ ] Implement all queue manipulation methods
- [ ] Emit `QUEUE_CHANGED` events
- [ ] Store extras in MediaItem metadata

### Phase 6: Error Handling

- [ ] Implement recoverable vs unrecoverable error classification
- [ ] Add proper error codes
- [ ] Remove full teardown on recoverable errors

### Phase 7: TypeScript API

- [ ] Rename methods to match Media3 Player interface (e.g., `seekToNextMediaItem`, `addMediaItems`)
- [ ] Update `AudioProMediaItem` fields to match MediaItem structure (`mediaId`, `uri`, `artworkUri`, `albumTitle`, `durationMs`)
- [ ] Replace enum `RepeatMode` with const object matching `Player.REPEAT_MODE_*` values
- [ ] Replace string states with numeric `PlaybackState` matching `Player.STATE_*`
- [ ] Add event types matching `Player.Listener` callbacks
- [ ] Add reason constants for events (`MediaItemTransitionReason`, `TimelineChangeReason`, etc.)
- [ ] Update `useAudioPro` hook state to use new types

### Phase 8: Migration

- [ ] Write migration guide
- [ ] Add deprecation warnings for old API

---

## References

- [Media3 Getting Started](https://developer.android.com/media/media3/getting-started)
- [Media3 Session](https://developer.android.com/media/media3/session)
- [Media3 ExoPlayer](https://developer.android.com/media/media3/exoplayer)
- [MediaSession and Controller](https://developer.android.com/media/media3/session/media-session)
- [Background Playback](https://developer.android.com/media/media3/session/background-playback)
- [Media Notifications](https://developer.android.com/media/media3/session/media-notifications)
- [Audio Focus](https://developer.android.com/media/media3/exoplayer/audio-focus)

