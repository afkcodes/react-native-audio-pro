import { NativeModules } from 'react-native';

import { ambientEmitter, emitter } from './emitter';
import { internalStore } from './internalStore';
import {
	guardTrackPlaying,
	logDebug,
	normalizeVolume,
	validateFilePath,
	validateTrack,
} from './utils';
import { AudioProAmbientEventType, AudioProState, DEFAULT_CONFIG } from './values';

import type {
	AmbientAudioPlayOptions,
	AudioProAmbientEventCallback,
	AudioProConfigureOptions,
	AudioProEventCallback,
	AudioProNotificationButton,
	AudioProPlayOptions,
	AudioProTrack,
} from './types';

const NativeAudioPro = NativeModules.AudioPro;

/**
 * Checks if the current player state is valid for the given operation
 *
 * @param operation - The operation name for logging purposes
 * @returns true if the player state is valid for the operation, false otherwise
 * @internal
 */
function isValidPlayerStateForOperation(operation: string): boolean {
	const { playerState } = internalStore.getState();
	if (playerState === AudioProState.IDLE || playerState === AudioProState.ERROR) {
		logDebug(`AudioPro: ${operation} ignored - player in`, playerState, 'state');
		return false;
	}
	return true;
}

export const AudioPro = {
	/**
	 * Configure the audio player with the specified options.
	 *
	 * Note: Configuration changes are stored but only applied when the next `play()` call is made.
	 * This is by design and applies to all configuration options.
	 *
	 * @param options - Configuration options for the audio player
	 * @param options.contentType - Type of content being played (MUSIC or SPEECH)
	 * @param options.debug - Enable debug logging
	 * @param options.debugIncludesProgress - Include progress events in debug logs
	 * @param options.progressIntervalMs - Interval in milliseconds for progress events
	 * @param options.skipIntervalMs - Interval in milliseconds for skip forward/back actions
	 */
	configure(options: AudioProConfigureOptions): void {
		const { setConfigureOptions, setDebug, setDebugIncludesProgress } =
			internalStore.getState();
		const config: AudioProConfigureOptions = { ...DEFAULT_CONFIG, ...options };
		setConfigureOptions(config);

		// Pass configuration to native side
		NativeAudioPro.configure(config);

		if (options.maxCacheSize) {
			logDebug('AudioPro: Configuring maxCacheSize', options.maxCacheSize);
		}
		setDebug(!!options.debug);
		setDebugIncludesProgress(options.debugIncludesProgress ?? false);

		if (options.skipSilence !== undefined) {
			this.setSkipSilence(options.skipSilence);
		}

		logDebug('AudioPro: configure()', config);
	},

	/**
	 * Sync JS state from native.
	 * Call this when native service survived but JS was killed (e.g., after swipe from recents).
	 * Triggers STATE_CHANGED, PROGRESS, and TRACK_CHANGED events to update the internal store.
	 */
	syncFromNative(): void {
		logDebug('AudioPro: syncFromNative()');
		NativeAudioPro.syncFromNative();
	},

	/**
	 * Resume playback or start playback if paused/stopped.
	 * To play a specific track, use `addToQueue` then `play` or `skipTo`.
	 *
	 * @param options - Optional playback options
	 */
	play(options: AudioProPlayOptions = {}) {
		// play() is valid in IDLE state as it starts playback
		logDebug('AudioPro: play()', options);
		NativeAudioPro.play(null, options); // Pass null track to indicate resume/start queue
	},

	/**
	 * Pause the current playback
	 */
	pause() {
		if (!guardTrackPlaying('pause')) return;
		logDebug('AudioPro: pause()');
		if (!isValidPlayerStateForOperation('pause()')) return;
		NativeAudioPro.pause();
	},

	/**
	 * Stop playback and reset position
	 */
	stop() {
		logDebug('AudioPro: stop()');
		const { setError } = internalStore.getState();
		setError(null);
		NativeAudioPro.stop();
	},

	/**
	 * Add media items to the end of the queue.
	 * Media3 equivalent: player.addMediaItems(items)
	 * @param items - Single item or array of items to add
	 */
	addMediaItems(items: AudioProTrack | AudioProTrack[]): void {
		const itemList = Array.isArray(items) ? items : [items];
		const validItems = itemList
			.map((t) => {
				const rt = { ...t };
				validateFilePath(rt.url);
				validateFilePath(rt.artwork);
				return rt;
			})
			.filter((t) => validateTrack(t));

		if (validItems.length === 0) {
			console.warn('[react-native-audio-pro]: No valid items provided to addMediaItems().');
			return;
		}

		logDebug('AudioPro: addMediaItems()', validItems.length, 'items');
		NativeAudioPro.addMediaItems(validItems);
	},

	/**
	 * Clear all media items from the queue.
	 * Media3 equivalent: player.clearMediaItems()
	 */
	clearMediaItems(): void {
		logDebug('AudioPro: clearMediaItems()');
		NativeAudioPro.clearMediaItems();
	},

	/**
	 * Insert media items at a specific position in the queue.
	 * Media3 equivalent: player.addMediaItems(index, items)
	 * @param index - Position to insert at (0-based)
	 * @param items - Single item or array of items to insert
	 */
	addMediaItemsAt(index: number, items: AudioProTrack | AudioProTrack[]): void {
		const itemList = Array.isArray(items) ? items : [items];
		const validItems = itemList
			.map((t) => {
				const rt = { ...t };
				validateFilePath(rt.url);
				validateFilePath(rt.artwork);
				return rt;
			})
			.filter((t) => validateTrack(t));

		if (validItems.length === 0) {
			console.warn('[react-native-audio-pro]: No valid items provided to addMediaItemsAt().');
			return;
		}

		logDebug('AudioPro: addMediaItemsAt()', index, validItems.length, 'items');
		NativeAudioPro.addMediaItemsAt(index, validItems);
	},

	/**
	 * Remove media items in range [fromIndex, toIndex).
	 * Media3 equivalent: player.removeMediaItems(fromIndex, toIndex)
	 * @param fromIndex - Start index (inclusive, 0-based)
	 * @param toIndex - End index (exclusive)
	 */
	removeMediaItems(fromIndex: number, toIndex: number): void {
		logDebug('AudioPro: removeMediaItems()', fromIndex, toIndex);
		NativeAudioPro.removeMediaItems(fromIndex, toIndex);
	},

	/**
	 * Move a media item from one position to another.
	 * Media3 equivalent: player.moveMediaItem(currentIndex, newIndex)
	 * @param currentIndex - Current position of the item
	 * @param newIndex - New position for the item
	 */
	moveMediaItem(currentIndex: number, newIndex: number): void {
		logDebug('AudioPro: moveMediaItem()', currentIndex, '->', newIndex);
		NativeAudioPro.moveMediaItem(currentIndex, newIndex);
	},

	/**
	 * Set media items (replaces entire queue).
	 * Media3 equivalent: player.setMediaItems(items)
	 * @param items - Array of items to set as the queue
	 */
	setMediaItems(items: AudioProTrack[]): void {
		const validItems = items
			.map((t) => {
				const rt = { ...t };
				validateFilePath(rt.url);
				validateFilePath(rt.artwork);
				return rt;
			})
			.filter((t) => validateTrack(t));

		if (validItems.length === 0) {
			console.warn('[react-native-audio-pro]: No valid items provided to setMediaItems().');
			return;
		}

		logDebug('AudioPro: setMediaItems()', validItems.length, 'items');
		NativeAudioPro.setMediaItems(validItems);
	},

	/**
	 * Seek to the next media item in the queue.
	 * Media3 equivalent: player.seekToNextMediaItem()
	 */
	seekToNextMediaItem(): void {
		logDebug('AudioPro: seekToNextMediaItem()');
		NativeAudioPro.seekToNextMediaItem();
	},

	/**
	 * Seek to the previous media item in the queue.
	 * Media3 equivalent: player.seekToPreviousMediaItem()
	 */
	seekToPreviousMediaItem(): void {
		logDebug('AudioPro: seekToPreviousMediaItem()');
		NativeAudioPro.seekToPreviousMediaItem();
	},

	/**
	 * Seek to a specific media item in the queue.
	 * Media3 equivalent: player.seekToMediaItem(index, positionMs)
	 * @param index - The index to seek to (0-based)
	 * @param positionMs - Optional position in milliseconds to seek to once ready
	 */
	seekToMediaItem(index: number, positionMs?: number): void {
		if (positionMs !== undefined) {
			logDebug('AudioPro: seekToMediaItem()', index, positionMs);
			NativeAudioPro.seekToMediaItemWithPosition(index, positionMs);
		} else {
			logDebug('AudioPro: seekToMediaItem()', index);
			NativeAudioPro.seekToMediaItem(index);
		}
	},

	/**
	 * Remove a single media item from the queue.
	 * Media3 equivalent: player.removeMediaItem(index)
	 * @param index - Index of the item to remove (0-based)
	 */
	removeMediaItem(index: number): void {
		logDebug('AudioPro: removeMediaItem()', index);
		NativeAudioPro.removeMediaItem(index);
	},

	/**
	 * Seek to a specific position in the current track
	 * @param positionMs - Position in milliseconds
	 */
	seekTo(positionMs: number) {
		if (!guardTrackPlaying('seekTo')) return;
		logDebug('AudioPro: seekTo()', positionMs);
		if (!isValidPlayerStateForOperation('seekTo()')) return;
		if (positionMs < 0) return;

		// Optimistic update for UI snappiness + set seeking flag to
		// suppress transient PAUSED/LOADING states from native during seek
		internalStore.setState({ position: positionMs, isSeeking: true });

		NativeAudioPro.seekTo(positionMs);
	},

	/**
	 * Seek by a relative offset
	 * @param offsetMs - Offset in milliseconds (positive for forward, negative for backward)
	 */
	seekBy(offsetMs: number) {
		if (!guardTrackPlaying('seekBy')) return;
		logDebug('AudioPro: seekBy()', offsetMs);
		if (!isValidPlayerStateForOperation('seekBy()')) return;
		internalStore.setState({ isSeeking: true });
		NativeAudioPro.seekBy(offsetMs);
	},

	/**
	 * Set the repeat mode
	 * @param mode - "OFF" | "ONE" | "ALL"
	 */
	setRepeatMode(mode: 'OFF' | 'ONE' | 'ALL') {
		logDebug('AudioPro: setRepeatMode()', mode);
		NativeAudioPro.setRepeatMode(mode);
	},

	/**
	 * Set shuffle mode enabled/disabled.
	 * Media3 equivalent: player.setShuffleModeEnabled(shuffleModeEnabled)
	 * @param enabled - true to enable shuffle, false to disable
	 */
	setShuffleModeEnabled(enabled: boolean) {
		logDebug('AudioPro: setShuffleModeEnabled()', enabled);
		NativeAudioPro.setShuffleModeEnabled(enabled);
	},

	/**
	 * Set custom notification buttons for lock screen and notification controls.
	 *
	 * Configures which action buttons appear on the media notification.
	 * Changes take effect on next playback session. Call clear() first to apply to current session.
	 *
	 * Available buttons:
	 * - PLAY/PAUSE: Automatically included in slot 1
	 * - PREV: Previous track button
	 * - NEXT: Next track button
	 * - LIKE: Like/favorite button (heart icon)
	 * - DISLIKE: Dislike button (thumbs down icon)
	 * - SAVE: Save to playlist button
	 * - BOOKMARK: Bookmark button
	 * - REWIND_30: Rewind 30 seconds button
	 * - FORWARD_30: Forward 30 seconds button
	 *
	 * @param buttons - Array of button types to display. Max 5 buttons (play/pause counts as 1).
	 *
	 * @example
	 * // Basic playback controls
	 * AudioPro.setNotificationButtons(['PREV', 'NEXT']);
	 *
	 * @example
	 * // With custom actions
	 * AudioPro.setNotificationButtons(['LIKE', 'PREV', 'NEXT', 'SAVE']);
	 *
	 * @example
	 * // With seek controls
	 * AudioPro.setNotificationButtons(['REWIND_30', 'PREV', 'NEXT', 'FORWARD_30']);
	 */
	setNotificationButtons(buttons: AudioProNotificationButton[]) {
		logDebug('AudioPro: setNotificationButtons()', buttons);
		NativeAudioPro.setNotificationButtons(buttons);
	},

	/**
	 * Add a listener for audio player events
	 *
	 * @param callback - Callback function to handle audio player events
	 * @returns Subscription that can be used to remove the listener
	 */
	addEventListener(callback: AudioProEventCallback) {
		return emitter.addListener('AudioProEvent', callback);
	},

	/**
	 * Get the current playback position and total duration
	 *
	 * @returns Object containing position and duration in milliseconds
	 */
	getTimings() {
		const { position, duration } = internalStore.getState();
		return { position, duration };
	},

	/**
	 * Get the current playback state.
	 * Media3 equivalent: player.getPlaybackState()
	 * @returns Current playback state (IDLE, STOPPED, LOADING, PLAYING, PAUSED, ERROR)
	 */
	getPlaybackState() {
		return internalStore.getState().playerState;
	},

	/**
	 * Get the current media item.
	 * Media3 equivalent: player.getCurrentMediaItem()
	 * @returns Currently playing media item or null if none
	 */
	getCurrentMediaItem() {
		return internalStore.getState().trackPlaying;
	},

	/**
	 * Get the index of the current media item in the queue.
	 * Media3 equivalent: player.getCurrentMediaItemIndex()
	 * @returns Index of the current item, or -1 if none
	 */
	getCurrentMediaItemIndex() {
		return internalStore.getState().activeTrackIndex;
	},

	/**
	 * Set the playback speed rate
	 *
	 * @param speed - Playback speed rate (0.25 to 2.0, normal speed is 1.0)
	 */
	setPlaybackSpeed(speed: number) {
		const validatedSpeed = Math.max(0.25, Math.min(2.0, speed));
		if (validatedSpeed !== speed) {
			console.warn(
				`[react-native-audio-pro]: Playback speed ${speed} out of range, clamped to ${validatedSpeed}`,
			);
		}

		logDebug('AudioPro: setPlaybackSpeed()', validatedSpeed);
		const { setPlaybackSpeed, trackPlaying } = internalStore.getState();
		setPlaybackSpeed(validatedSpeed);

		if (trackPlaying) {
			if (!isValidPlayerStateForOperation('setPlaybackSpeed() native call')) return;
			NativeAudioPro.setPlaybackSpeed(validatedSpeed);
		}
	},

	/**
	 * Get the current playback speed rate
	 *
	 * @returns Current playback speed rate (0.25 to 2.0, normal speed is 1.0)
	 */
	getPlaybackSpeed() {
		return internalStore.getState().playbackSpeed;
	},

	/**
	 * Set the playback volume
	 *
	 * @param volume - Volume level (0.0 to 1.0, where 0.0 is mute and 1.0 is full volume)
	 */
	setVolume(volume: number) {
		const clampedVolume = Math.max(0, Math.min(1, volume));
		if (clampedVolume !== volume) {
			console.warn(
				`[react-native-audio-pro]: Volume ${volume} out of range, clamped to ${clampedVolume}`,
			);
		}

		const normalizedVolume = normalizeVolume(clampedVolume);
		logDebug('AudioPro: setVolume()', normalizedVolume);

		const { setVolume, trackPlaying } = internalStore.getState();
		setVolume(normalizedVolume);

		if (trackPlaying) {
			if (!isValidPlayerStateForOperation('setVolume()')) return;
			NativeAudioPro.setVolume(normalizedVolume);
		}
	},

	/**
	 * Set equalizer gains
	 * @param gains - Array of gain values in decibels (-10 to 10 usually)
	 */
	setEqualizer(gains: number[]) {
		logDebug('AudioPro: setEqualizer()', gains);
		NativeAudioPro.setEqualizer(gains);
	},

	/**
	 * Set bass boost strength
	 * @param strength - Strength of bass boost (0 to 1000)
	 */
	setBassBoost(strength: number) {
		logDebug('AudioPro: setBassBoost()', strength);
		NativeAudioPro.setBassBoost(strength);
	},

	/**
	 * Get the current playback volume
	 *
	 * @returns Current volume level (0.0 to 1.0)
	 */
	getVolume() {
		return internalStore.getState().volume;
	},

	/**
	 * Get the last error that occurred
	 *
	 * @returns Last error or null if no error has occurred
	 */
	getError() {
		return internalStore.getState().error;
	},

	/**
	 * Set the frequency at which progress events are emitted
	 *
	 * @param ms - Interval in milliseconds (100ms to 10000ms)
	 */
	setProgressInterval(ms: number) {
		const MIN_INTERVAL = 100;
		const MAX_INTERVAL = 10000;

		const clampedMs = Math.max(MIN_INTERVAL, Math.min(MAX_INTERVAL, ms));
		if (clampedMs !== ms) {
			console.warn(
				`[react-native-audio-pro]: Progress interval ${ms}ms out of range, clamped to ${clampedMs}ms`,
			);
		}

		logDebug('AudioPro: setProgressInterval()', clampedMs);
		const { setConfigureOptions, configureOptions } = internalStore.getState();
		setConfigureOptions({ ...configureOptions, progressIntervalMs: clampedMs });
	},

	/**
	 * Get all media items in the queue.
	 * Media3 equivalent: getting all media items from player
	 * @returns Promise resolving to the list of media items in the queue
	 */
	getMediaItems(): Promise<AudioProTrack[]> {
		logDebug('AudioPro: getMediaItems()');
		return NativeAudioPro.getMediaItems();
	},

	/**
	 * Get the current progress interval
	 *
	 * @returns The current progress interval in milliseconds
	 */
	getProgressInterval() {
		return (
			internalStore.getState().configureOptions.progressIntervalMs ??
			DEFAULT_CONFIG.progressIntervalMs
		);
	},

	// ==============================
	// AMBIENT AUDIO METHODS
	// ==============================

	/**
	 * Play an ambient audio track
	 *
	 * @param options - Ambient audio options
	 * @param options.url - URL of the audio file to play (http://, https://, or file://)
	 * @param options.loop - Whether to loop the audio (default: true)
	 */
	ambientPlay(options: AmbientAudioPlayOptions): void {
		const { url: originalUrl, loop = true } = options;

		if (!originalUrl) {
			const errorMessage = '[react-native-audio-pro]: Invalid URL provided to ambientPlay().';
			console.error(errorMessage);
			ambientEmitter.emit('AudioProAmbientEvent', {
				type: AudioProAmbientEventType.AMBIENT_ERROR,
				payload: {
					error: errorMessage,
				},
			});
			return;
		}
		// Validate URL scheme for ambient track
		validateFilePath(originalUrl);
		const resolvedUrl = originalUrl;

		const { debug } = internalStore.getState();

		logDebug('AudioPro: ambientPlay()', { url: resolvedUrl, loop });
		NativeAudioPro.ambientPlay({ url: resolvedUrl, loop, debug });
	},

	/**
	 * Stop ambient audio playback
	 */
	ambientStop(): void {
		logDebug('AudioPro: ambientStop()');
		NativeAudioPro.ambientStop();
	},

	/**
	 * Set the volume of ambient audio playback
	 *
	 * @param volume - Volume level (0.0 to 1.0)
	 */
	ambientSetVolume(volume: number): void {
		const clampedVolume = Math.max(0, Math.min(1, volume));
		if (clampedVolume !== volume) {
			console.warn(
				`[react-native-audio-pro]: Volume ${volume} out of range, clamped to ${clampedVolume}`,
			);
		}

		const normalizedVolume = normalizeVolume(clampedVolume);
		logDebug('AudioPro: ambientSetVolume()', normalizedVolume);
		NativeAudioPro.ambientSetVolume(normalizedVolume);
	},

	/**
	 * Pause ambient audio playback
	 * No-op if already paused or not playing
	 */
	ambientPause(): void {
		logDebug('AudioPro: ambientPause()');
		NativeAudioPro.ambientPause();
	},

	/**
	 * Resume ambient audio playback
	 * No-op if already playing or no active track
	 */
	ambientResume(): void {
		logDebug('AudioPro: ambientResume()');
		NativeAudioPro.ambientResume();
	},

	/**
	 * Seek to a specific position in the ambient audio
	 * Silently ignore if not supported or no active track
	 *
	 * @param positionMs - Position in milliseconds
	 */
	ambientSeekTo(positionMs: number): void {
		logDebug('AudioPro: ambientSeekTo()', positionMs);
		NativeAudioPro.ambientSeekTo(positionMs);
	},

	/**
	 * Add a listener for ambient audio events
	 *
	 * @param callback - Callback function to handle ambient audio events
	 * @returns Subscription that can be used to remove the listener
	 */
	addAmbientListener(callback: AudioProAmbientEventCallback) {
		return ambientEmitter.addListener('AudioProAmbientEvent', callback);
	},

	/**
	 * Get the current cache size in bytes.
	 * @returns Promise resolving to the size in bytes.
	 */
	getCacheSize(): Promise<number> {
		logDebug('AudioPro: getCacheSize()');
		return NativeAudioPro.getCacheSize();
	},

	/**
	 * Clear the cache.
	 * @returns Promise resolving to true if successful.
	 */
	clearCache(): Promise<boolean> {
		logDebug('AudioPro: clearCache()');
		return NativeAudioPro.clearCache();
	},

	/**
	 * Start the sleep timer to pause playback after a specified duration.
	 * @param seconds - Duration in seconds before pausing playback.
	 */
	startSleepTimer(seconds: number): void {
		logDebug('AudioPro: startSleepTimer()', seconds);
		NativeAudioPro.startSleepTimer(seconds);
	},

	/**
	 * Cancel the active sleep timer.
	 */
	cancelSleepTimer(): void {
		logDebug('AudioPro: cancelSleepTimer()');
		NativeAudioPro.cancelSleepTimer();
	},

	/**
	 * Enable or disable silence skipping.
	 * @param enabled - true to enable, false to disable.
	 */
	setSkipSilence(enabled: boolean): void {
		logDebug('AudioPro: setSkipSilence()', enabled);
		NativeAudioPro.setSkipSilence(enabled);
	},

	/**
	 * Update notification button states (like/dislike/bookmark).
	 * Call this when the track's liked/bookmarked state changes to update notification icons.
	 * @param options - Object with liked, disliked, and bookmarked boolean states.
	 */
	updateNotificationState(options: {
		liked?: boolean;
		disliked?: boolean;
		bookmarked?: boolean;
	}): void {
		logDebug('AudioPro: updateNotificationState()', options);
		NativeAudioPro.updateNotificationState(
			options.liked ?? false,
			options.disliked ?? false,
			options.bookmarked ?? false,
		);
	},

	/**
	 * Update a track in the queue with new details (e.g. fresh URL).
	 * @param index - Index of the track to update.
	 * @param track - The new track object.
	 */
	updateTrack(index: number, track: AudioProTrack): void {
		const validatedTrack = { ...track };
		validateFilePath(validatedTrack.url);
		validateFilePath(validatedTrack.artwork);

		if (!validateTrack(validatedTrack)) {
			console.warn('[react-native-audio-pro]: Invalid track provided to updateTrack().');
			return;
		}

		logDebug('AudioPro: updateTrack()', index, validatedTrack.title);
		NativeAudioPro.updateTrack(index, validatedTrack);
	},
};
