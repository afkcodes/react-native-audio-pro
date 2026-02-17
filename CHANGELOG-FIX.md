# Fix: Position Not Restored on App Reopen (Force Kill Scenario)

## Problem
When the app was playing a song and the user **force-killed the app** (swiped from recents), then reopened it:
1. Track would be restored but showed as "playing" (pause icon visible) when it should show "paused"
2. Duration was not set (showing 0:00 or undefined)
3. Playback position was not restored correctly
4. The player state was not properly synchronized between native and JavaScript

## Root Causes

### Issue 1: Missing STATE_READY Handler for Persisted Sessions
In the Android native code (`AudioProController.kt`), the `skipToWithSeek()` method only called `player.prepare()` if the player was in `STATE_IDLE`. However, when the app was backgrounded (not killed), the Media3 player session would persist and remain in `STATE_READY`. 

When restoration occurred:
1. `skipToWithSeek(index, position)` would be called
2. It would set `flowPendingSeekPosition = position` 
3. Call `player.seekToDefaultPosition(index)` to switch tracks
4. If player was `STATE_IDLE`, call `prepare()` to trigger `STATE_READY` callback
5. The `STATE_READY` callback would perform the actual seek to the saved position

**The bug:** If the player was already in `STATE_READY` (persisted session), `prepare()` was never called, so `STATE_READY` never fired again, and the pending seek was never executed.

### Issue 2: No STATE Emission During Restoration
Even after fixing Issue 1, when the app was force-killed and reopened fresh:
1. Player would be in `STATE_IDLE`
2. `skipToWithSeek` would call `prepare()`
3. But it didn't emit any intermediate state like `LOADING` or `PAUSED`
4. The JavaScript `internalStore` would stay in `IDLE` state
5. Components would show stale/incorrect state

### Issue 3: Missing State Synchronization
After `seekToDefaultPosition(index)` was called, `onMediaItemTransition` would fire and emit `TRACK_CHANGED`, but:
- No `STATE_CHANGED` event was emitted if prepare() was needed
- No `PROGRESS` event was emitted with duration information
- The UI had no indication that restoration was in progress

## Solutions

### Fix 1: Handle Already-READY State (Persisted Sessions)
Added a check in `skipToWithSeek()` to detect if the player is already in `STATE_READY` and perform the seek immediately:

```kotlin
if (player.playbackState == Player.STATE_READY) {
    log("skipToWithSeek: Player already READY, performing immediate seek to $position")
    flowIsRestorationSeek = false
    player.seekTo(position)
    
    val dur = safeDuration()
    val isPlaying = player.isPlaying
    
    // Emit PROGRESS to update UI position/duration
    emitNotice(AudioProModule.EVENT_TYPE_PROGRESS, position, dur, "skipToWithSeek(immediate)")
    
    // Emit STATE_CHANGED so internalStore syncs
    val state = if (isPlaying) AudioProModule.STATE_PLAYING else AudioProModule.STATE_PAUSED
    emitState(state, position, dur, "skipToWithSeek(immediate)")
}
```

### Fix 2: Emit LOADING State for Fresh Start (Force Kill)
When the player is in `STATE_IDLE` and needs `prepare()`, now emit `STATE_LOADING` immediately:

```kotlin
else if (player.playbackState == Player.STATE_IDLE) {
    log("skipToWithSeek: Player IDLE, emitting LOADING state and calling prepare()")
    
    // Emit LOADING state so UI shows loading indicator
    emitState(AudioProModule.STATE_LOADING, 0L, 0L, "skipToWithSeek(prepare)")
    
    // Prepare will trigger STATE_READY callback which handles the pending seek
    player.prepare()
}
```

### Fix 3: Enhanced Logging for Debugging
Added comprehensive logging throughout the restoration flow in both native and JavaScript:

**Native (AudioProController.kt):**
- Log player state, media item count, and duration at each step
- Log track title in `onMediaItemTransition` to confirm track loading
- Log state changes with timestamps and reasons

**JavaScript (AudioService.ts):**
- Log restoration start/completion
- Log queue size and each restoration step
- Log final state after restoration for verification
- Add small delay and query final state to confirm sync

## Testing Instructions

### Test 1: Background Persistence (User presses home)
1. Play a song and let it progress to 0:30
2. Press home button (don't kill app)
3. Reopen the app
4. ✅ Song should be at 0:30, showing paused state
5. ✅ Duration should be displayed correctly

### Test 2: Force Kill (User swipes from recents)
1. Play a song and let it progress to 0:30
2. Swipe app from recent apps to force kill
3. Reopen the app (cold start)
4. ✅ Song should be at 0:30, showing paused state
5. ✅ Duration should be displayed correctly
6. ✅ Should show LOADING state briefly during restoration

### Test 3: Check Logs
Use `adb logcat | grep AudioPro` or React Native debugger:

**Expected log sequence after force kill:**
```
[AudioService] Starting state restoration...
[AudioService] Restoring queue with X tracks
[AudioService] Restoring to track index: 0 position: 30000
...AudioProController: skipToWithSeek: index=0, position=30000, playerState=1
AudioProController: skipToWithSeek: Player IDLE, emitting LOADING state
AudioProController: onMediaItemTransition: Updated activeTrack, title=...
AudioProController: STATE_READY: Performing pending seek to 30000ms
[AudioService] State restoration complete
[AudioService] Final state: PAUSED
[AudioService] Final timings: {position: 30000, duration: 180000}
```

## Additional Improvements
- Better error handling in restoration flow
- State validation after restoration
- Graceful fallback if restoration fails
- No auto-play after restoration (user intent preserved)
