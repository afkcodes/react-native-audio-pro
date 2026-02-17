import { create } from 'zustand';

import { normalizeVolume } from './utils';
import { AudioProEventType, AudioProState, DEFAULT_CONFIG } from './values';

import type {
	AudioProConfigureOptions,
	AudioProEvent,
	AudioProPlaybackErrorPayload,
	AudioProTrack,
} from './types';

export interface AudioProStore {
	playerState: AudioProState;
	position: number;
	duration: number;
	bufferedPosition: number;
	playbackSpeed: number;
	volume: number;
	debug: boolean;
	debugIncludesProgress: boolean;
	trackPlaying: AudioProTrack | null;
	configureOptions: AudioProConfigureOptions;
	error: AudioProPlaybackErrorPayload | null;
	/** True while a user-initiated seek is in-flight (set by seekTo/seekBy, cleared by SEEK_COMPLETE). */
	isSeeking: boolean;
	/** Current index in the queue (0-based, -1 if no queue) */
	activeTrackIndex: number;
	/** Number of tracks in the queue */
	queueSize: number;
	setDebug: (debug: boolean) => void;
	setDebugIncludesProgress: (includeProgress: boolean) => void;
	setTrackPlaying: (track: AudioProTrack | null) => void;
	setConfigureOptions: (options: AudioProConfigureOptions) => void;
	setPlaybackSpeed: (speed: number) => void;
	setVolume: (volume: number) => void;
	setError: (error: AudioProPlaybackErrorPayload | null) => void;
	updateFromEvent: (event: AudioProEvent) => void;
}

function hasTrackMetadataChanged(prev: AudioProTrack, next: AudioProTrack) {
	return (
		prev.id !== next.id ||
		prev.url !== next.url ||
		prev.title !== next.title ||
		prev.artwork !== next.artwork ||
		prev.album !== next.album ||
		prev.artist !== next.artist
	);
}

export const internalStore = create<AudioProStore>((set, get) => ({
	playerState: AudioProState.IDLE,
	position: 0,
	duration: 0,
	bufferedPosition: 0,
	activeTrackIndex: -1,
	queueSize: 0,
	playbackSpeed: 1.0,
	volume: normalizeVolume(1.0),
	debug: false,
	debugIncludesProgress: false,
	trackPlaying: null,
	configureOptions: { ...DEFAULT_CONFIG },
	error: null,
	isSeeking: false,
	setDebug: (debug) => set({ debug }),
	setDebugIncludesProgress: (includeProgress) => set({ debugIncludesProgress: includeProgress }),
	setTrackPlaying: (track) => set({ trackPlaying: track }),
	setConfigureOptions: (options) => set({ configureOptions: options }),
	setPlaybackSpeed: (speed) => set({ playbackSpeed: speed }),
	setVolume: (volume) => set({ volume: normalizeVolume(volume) }),
	setError: (error) => set({ error }),
	updateFromEvent: (event) => {
		const { type, track, payload } = event;
		const current = get();
		const updates: Partial<AudioProStore> = {};

		// ─────────────────────────────────────────────────────────────────────
		// Handle events that don't require state updates
		// ─────────────────────────────────────────────────────────────────────
		if (type === AudioProEventType.REMOTE_NEXT || type === AudioProEventType.REMOTE_PREV) {
			// Remote commands are informational - apps can listen to these
			// via addEventListener but they don't change internal store state
			return;
		}

		// ─────────────────────────────────────────────────────────────────────
		// 1. SEEK_COMPLETE - clear seeking flag
		// ─────────────────────────────────────────────────────────────────────
		if (type === AudioProEventType.SEEK_COMPLETE) {
			if (current.isSeeking) {
				updates.isSeeking = false;
			}
			// Skip position update from SEEK_COMPLETE - the optimistic update
			// from seekTo() already set the correct value. Native position can
			// differ slightly due to keyframe alignment, causing micro-jumps.
		}

		// ─────────────────────────────────────────────────────────────────────
		// 2. STATE_CHANGED - update player state
		// ─────────────────────────────────────────────────────────────────────
		if (type === AudioProEventType.STATE_CHANGED && payload?.state) {
			const newState = payload.state;
			const shouldUpdateState = newState !== current.playerState;

			if (shouldUpdateState) {
				// During a seek, Media3/AVPlayer briefly transitions through
				// PAUSED/LOADING before settling back to PLAYING. Suppress
				// these transient states so the play/pause icon doesn't flicker.
				const isTransientSeekState =
					current.isSeeking &&
					current.playerState === AudioProState.PLAYING &&
					(newState === AudioProState.PAUSED || newState === AudioProState.LOADING);

				if (!isTransientSeekState) {
					updates.playerState = newState;

					// Clear error when leaving ERROR state
					if (newState !== AudioProState.ERROR && current.error !== null) {
						updates.error = null;
					}
				}
			}
		}

		// ─────────────────────────────────────────────────────────────────────
		// 3. PLAYBACK_ERROR - store error info (don't change state)
		// ─────────────────────────────────────────────────────────────────────
		if (type === AudioProEventType.PLAYBACK_ERROR && payload?.error) {
			updates.error = {
				error: payload.error,
				errorCode: payload.errorCode,
				recoverable: payload.recoverable,
				cause: payload.cause,
				index: payload.index,
			};
			// Note: Native is responsible for emitting STATE_CHANGED: ERROR
		}

		// ─────────────────────────────────────────────────────────────────────
		// 4. PLAYBACK_SPEED_CHANGED - update speed
		// ─────────────────────────────────────────────────────────────────────
		if (
			type === AudioProEventType.PLAYBACK_SPEED_CHANGED &&
			payload?.speed !== undefined &&
			payload.speed !== current.playbackSpeed
		) {
			updates.playbackSpeed = payload.speed;
		}

		// ─────────────────────────────────────────────────────────────────────
		// 5. QUEUE_CHANGED - update queue size and active index
		// ─────────────────────────────────────────────────────────────────────
		if (type === AudioProEventType.QUEUE_CHANGED) {
			if (payload?.size !== undefined && payload.size !== current.queueSize) {
				updates.queueSize = payload.size;
			}
			if (
				payload?.currentIndex !== undefined &&
				payload.currentIndex !== current.activeTrackIndex
			) {
				updates.activeTrackIndex = payload.currentIndex;
			}
		}

		// ─────────────────────────────────────────────────────────────────────
		// 6. PROGRESS - update position, duration, buffered (most frequent)
		// ─────────────────────────────────────────────────────────────────────
		if (type === AudioProEventType.PROGRESS) {
			if (payload?.position !== undefined && payload.position !== current.position) {
				updates.position = payload.position;
			}
			if (payload?.duration !== undefined && payload.duration !== current.duration) {
				updates.duration = payload.duration;
			}
			if (
				payload?.bufferedPosition !== undefined &&
				payload.bufferedPosition !== current.bufferedPosition
			) {
				updates.bufferedPosition = payload.bufferedPosition;
			}
		}

		// ─────────────────────────────────────────────────────────────────────
		// 7. STATE_CHANGED may also carry position/duration (initial load, etc)
		// Only accept position=0 from STATE_CHANGED if it's a terminal state.
		// Non-terminal states (LOADING, PAUSED, PLAYING) may erroneously send 0.
		// PROGRESS events are the authoritative source for position.
		// ─────────────────────────────────────────────────────────────────────
		if (type === AudioProEventType.STATE_CHANGED) {
			const isTerminalState =
				payload?.state === AudioProState.STOPPED || payload?.state === AudioProState.IDLE;

			// Only update position if:
			// 1. It's non-zero (always valid), OR
			// 2. It's zero AND we're transitioning to a terminal state
			if (payload?.position !== undefined && payload.position !== current.position) {
				if (payload.position > 0 || isTerminalState) {
					updates.position = payload.position;
				}
			}
			if (payload?.duration !== undefined && payload.duration !== current.duration) {
				if (payload.duration > 0 || isTerminalState) {
					updates.duration = payload.duration;
				}
			}
			if (payload?.index !== undefined && payload.index !== current.activeTrackIndex) {
				updates.activeTrackIndex = payload.index;
			}
		}

		// ─────────────────────────────────────────────────────────────────────
		// 8. TRACK_CHANGED - update track and index
		// ─────────────────────────────────────────────────────────────────────
		if (type === AudioProEventType.TRACK_CHANGED) {
			const isActuallyNewTrack =
				track && (!current.trackPlaying || track.id !== current.trackPlaying.id);

			if (
				track &&
				(!current.trackPlaying || hasTrackMetadataChanged(current.trackPlaying, track))
			) {
				updates.trackPlaying = track;
			}
			if (payload?.index !== undefined && payload.index !== current.activeTrackIndex) {
				updates.activeTrackIndex = payload.index;
			}
			// Only reset position to 0 if track actually changed (different ID).
			// When returning from background, native may re-emit TRACK_CHANGED
			// for the same track with position=0, which would incorrectly reset progress.
			if (payload?.position !== undefined && payload.position !== current.position) {
				if (payload.position > 0 || isActuallyNewTrack) {
					updates.position = payload.position;
				}
			}
			if (payload?.duration !== undefined && payload.duration !== current.duration) {
				updates.duration = payload.duration;
			}
		}

		// ─────────────────────────────────────────────────────────────────────
		// 9. Track updates from other events (STATE_CHANGED with new track)
		// ─────────────────────────────────────────────────────────────────────
		if (
			type === AudioProEventType.STATE_CHANGED &&
			track &&
			(payload?.state === AudioProState.LOADING || payload?.state === AudioProState.PLAYING)
		) {
			// Track loading or playing - adopt the track if it's new/changed
			if (!current.trackPlaying || hasTrackMetadataChanged(current.trackPlaying, track)) {
				updates.trackPlaying = track;
			}
		}

		// ─────────────────────────────────────────────────────────────────────
		// 10. Explicit track unload (track: null in event)
		// ─────────────────────────────────────────────────────────────────────
		if (
			track === null &&
			current.trackPlaying !== null &&
			type !== AudioProEventType.PLAYBACK_ERROR
		) {
			updates.trackPlaying = null;
		}

		// ─────────────────────────────────────────────────────────────────────
		// Apply all batched updates
		// ─────────────────────────────────────────────────────────────────────
		if (Object.keys(updates).length > 0) {
			set(updates);
		}
	},
}));
