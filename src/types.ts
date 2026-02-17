import {
	AudioProAmbientEventType,
	AudioProContentType,
	AudioProEventType,
	AudioProRepeatMode,
	AudioProState,
	AudioProTriggerSource,
} from './values';

// Re-exports removed to avoid duplicate identifier errors

// ==============================
// TRACK
// ==============================

export type AudioProArtwork = string;

export type AudioProTrack = {
	id: string;
	url: string;
	title: string;
	artwork: AudioProArtwork;
	album?: string;
	artist?: string;
	[key: string]: unknown; // custom properties
};

export type AudioProEqualizerBand = {
	frequency: number;
	label: string;
};

export type AudioProEqualizerPreset = {
	name: string;
	id: string;
	gains: number[];
	description?: string;
};

// ==============================

// ==============================
// CONFIGURE OPTIONS
// ==============================

export type AudioProConfigureOptions = {
	contentType?: AudioProContentType;
	debug?: boolean;
	debugIncludesProgress?: boolean;
	progressIntervalMs?: number;
	skipIntervalMs?: number;

	/**
	 * Default repeat mode
	 */
	repeatMode?: AudioProRepeatMode;

	/**
	 * Default shuffle mode
	 */
	/**
	 * Default shuffle mode
	 */
	shuffleMode?: boolean;

	/**
	 * Maximum cache size in bytes. Default is 500MB.
	 * Note: this is a global setting and might only take effect on first initialization.
	 */
	/**
	 * Maximum cache size in bytes. Default is 500MB.
	 * Note: this is a global setting and might only take effect on first initialization.
	 */
	maxCacheSize?: number;

	/**
	 * Enable or disable cache. Default is true.
	 * Note: Changing this requires a session restart (e.g. force quit app or clear() then re-configure) to take full effect on the underlying DataSource construction.
	 */
	cacheEnabled?: boolean;

	/**
	 * Enable or disable silence skipping. Default is false.
	 */
	skipSilence?: boolean;
};

// ==============================
// NOTIFICATION BUTTONS
// ==============================

export type AudioProNotificationButton =
	| 'PLAY'
	| 'PAUSE'
	| 'PREV'
	| 'NEXT'
	| 'LIKE'
	| 'DISLIKE'
	| 'SAVE'
	| 'BOOKMARK'
	| 'REWIND_30'
	| 'FORWARD_30';

// ==============================
// PLAY OPTIONS
// ==============================

export type AudioProHeaders = {
	audio?: Record<string, string>;
	artwork?: Record<string, string>;
};

export type AudioProPlayOptions = {
	autoPlay?: boolean;
	headers?: AudioProHeaders;
	startTimeMs?: number;
	addTrack?: boolean;
};

// ==============================
// EVENTS
// ==============================

export type AudioProEventCallback = (event: AudioProEvent) => void;

export interface AudioProEvent {
	type: AudioProEventType;
	track: AudioProTrack | null; // Required for all events except REMOTE_NEXT and REMOTE_PREV
	payload?: {
		state?: AudioProState;
		position?: number;
		duration?: number;
		bufferedPosition?: number; // For PROGRESS events
		error?: string;
		errorCode?: number;
		recoverable?: boolean; // For PLAYBACK_ERROR events
		cause?: string; // For PLAYBACK_ERROR events
		speed?: number;
		index?: number;
		action?: string; // For CUSTOM_ACTION events: 'LIKE', 'SAVE', 'REWIND_30', etc.
		timerDuration?: number; // For SLEEP_TIMER events
		size?: number; // For QUEUE_CHANGED events: queue size
		currentIndex?: number; // For QUEUE_CHANGED events
		audioSessionId?: number; // For AUDIO_SESSION_CHANGED events
	};
}

export interface AudioProStateChangedPayload {
	state: AudioProState;
	position: number;
	duration: number;
}

export interface AudioProTrackEndedPayload {
	position: number;
	duration: number;
}

export interface AudioProPlaybackErrorPayload {
	error: string;
	/** JS-friendly error code (mapped from Media3 error codes) */
	errorCode?: AudioProErrorCode | number;
	/** Whether the error is recoverable (true = player still usable, will retry) */
	recoverable?: boolean;
	/** Optional cause string from the underlying exception */
	cause?: string;
	/** Track index where the error occurred */
	index?: number;
}

/**
 * Media3-aligned error codes.
 * Grouped by category:
 * - 1xxx: Network errors (recoverable)
 * - 2xxx: Decoding/codec errors (unrecoverable)
 * - 3xxx: DRM errors (unrecoverable)
 * - 4xxx: Content errors (may be recoverable)
 */
export enum AudioProErrorCode {
	// Unknown
	UNKNOWN = 0,

	// Network errors (1xxx) - typically recoverable
	NETWORK_TIMEOUT = 1001,
	NETWORK_FAILED = 1002,
	TIMEOUT = 1003,
	IO_UNSPECIFIED = 1004,
	INVALID_CONTENT_TYPE = 1005,

	// Decoding errors (2xxx) - typically unrecoverable
	DECODING_FAILED = 2001,
	AUDIO_TRACK_INIT_FAILED = 2002,
	DECODER_INIT_FAILED = 2003,
	DECODER_QUERY_FAILED = 2004,
	FORMAT_UNSUPPORTED = 2005,
	FORMAT_EXCEEDS_CAPABILITIES = 2006,

	// DRM errors (3xxx) - typically unrecoverable
	DRM_UNSPECIFIED = 3001,
	DRM_SCHEME_UNSUPPORTED = 3002,
	DRM_PROVISIONING_FAILED = 3003,
	DRM_LICENSE_ACQUISITION_FAILED = 3004,
	DRM_LICENSE_EXPIRED = 3005,

	// Content errors (4xxx)
	CONTENT_NOT_FOUND = 4001,
	BAD_HTTP_STATUS = 4002,
	CONTAINER_MALFORMED = 4003,
	MANIFEST_MALFORMED = 4004,
	BEHIND_LIVE_WINDOW = 4005,
}

export interface AudioProProgressPayload {
	position: number;
	duration: number;
	bufferedPosition: number;
}

export interface AudioProSeekCompletePayload {
	position: number;
	duration: number;
	/** Indicates who initiated the seek: user or system */
	triggeredBy: AudioProTriggerSource;
}

export interface AudioProPlaybackSpeedChangedPayload {
	speed: number;
}

export interface AudioProQueueChangedPayload {
	size: number;
	currentIndex: number;
}

export interface AudioProAudioSessionChangedPayload {
	audioSessionId: number;
}

// ==============================
// AMBIENT AUDIO
// ==============================

export interface AmbientAudioPlayOptions {
	url: string;
	loop?: boolean;
}

export type AudioProAmbientEventCallback = (event: AudioProAmbientEvent) => void;

export interface AudioProAmbientEvent {
	type: AudioProAmbientEventType;
	payload?: {
		error?: string;
	};
}

export interface AudioProAmbientErrorPayload {
	error: string;
}
