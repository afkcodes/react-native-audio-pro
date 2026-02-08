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
		error?: string;
		errorCode?: number;
		speed?: number;
		index?: number;
		action?: string; // For CUSTOM_ACTION events: 'LIKE', 'SAVE', 'REWIND_30', etc.
		timerDuration?: number; // For SLEEP_TIMER events
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
	errorCode?: number;
}

export interface AudioProProgressPayload {
	position: number;
	duration: number;
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
