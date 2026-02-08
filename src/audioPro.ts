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
		if (options.maxCacheSize) {
			// TODO: Pass this to native side if dynamic cache size configuration is supported
			logDebug('AudioPro: Configuring maxCacheSize', options.maxCacheSize);
		}
		setDebug(!!options.debug);
		setDebugIncludesProgress(options.debugIncludesProgress ?? false);
		logDebug('AudioPro: configure()', config);
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
	 * Add tracks to the queue.
	 *
	 * @param tracks - Single track or array of tracks to add
	 */
	addToQueue(tracks: AudioProTrack | AudioProTrack[]): void {
		const trackList = Array.isArray(tracks) ? tracks : [tracks];
		const validTracks = trackList
			.map((t) => {
				const rt = { ...t };
				validateFilePath(rt.url);
				validateFilePath(rt.artwork);
				return rt;
			})
			.filter((t) => validateTrack(t));

		if (validTracks.length === 0) {
			console.warn('[react-native-audio-pro]: No valid tracks provided to addToQueue().');
			return;
		}

		logDebug('AudioPro: addToQueue()', validTracks.length, 'tracks');
		NativeAudioPro.addToQueue(validTracks);
	},

	/**
	 * Clear the playback queue
	 */
	clearQueue(): void {
		logDebug('AudioPro: clearQueue()');
		NativeAudioPro.clearQueue();
	},

	/**
	 * Skip to the next track
	 */
	playNext(): void {
		logDebug('AudioPro: playNext()');
		NativeAudioPro.playNext();
	},

	/**
	 * Skip to the previous track
	 */
	playPrevious(): void {
		logDebug('AudioPro: playPrevious()');
		NativeAudioPro.playPrevious();
	},

	/**
	 * Skip to a specific index in the queue
	 * @param index - The index to skip to (0-based)
	 */
	skipTo(index: number): void {
		logDebug('AudioPro: skipTo()', index);
		NativeAudioPro.skipTo(index);
	},

	/**
	 * Remove a track from the queue at the specified index
	 * @param index - Index of the track to remove (0-based)
	 */
	removeTrack(index: number): void {
		logDebug('AudioPro: removeTrack()', index);
		NativeAudioPro.removeTrack(index);
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
	 * Set shuffle mode
	 * @param enabled - true to enable shuffle, false to disable
	 */
	setShuffleMode(enabled: boolean) {
		logDebug('AudioPro: setShuffleMode()', enabled);
		NativeAudioPro.setShuffleMode(enabled);
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
	 * Get the current playback state
	 *
	 * @returns Current playback state (IDLE, STOPPED, LOADING, PLAYING, PAUSED, ERROR)
	 */
	getState() {
		return internalStore.getState().playerState;
	},

	/**
	 * Get the currently playing track
	 *
	 * @returns Currently playing track or null if no track is playing
	 */
	getPlayingTrack() {
		return internalStore.getState().trackPlaying;
	},

	/**
	 * Get the index of the currently playing track in the queue
	 * @returns Index of the current track, or -1 if no track is playing
	 */
	getActiveTrackIndex() {
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
	 * Get the current playback queue
	 *
	 * @returns Promise resolving to the list of tracks in the queue
	 */
	getQueue(): Promise<AudioProTrack[]> {
		logDebug('AudioPro: getQueue()');
		return NativeAudioPro.getQueue();
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
};
