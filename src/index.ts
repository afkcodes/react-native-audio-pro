/**
 * React Native Audio Pro
 *
 * A comprehensive audio playback library for React Native applications, supporting both foreground and background playback,
 * with advanced features like ambient audio playback, event handling, and state management.
 *
 * @packageDocumentation
 */

/**
 * Main AudioPro class for managing audio playback
 * @see {@link ./audioPro}
 */
export { AudioPro } from './audioPro';

/**
 * React hook for easy integration of AudioPro functionality in React components
 * @see {@link ./useAudioPro}
 */
export { useAudioPro } from './useAudioPro';

/**
 * Type definitions for AudioPro
 * @see {@link ./types}
 */
export type {
	// Ambient audio types
	/** Options for ambient audio playback */
	AmbientAudioPlayOptions,
	/** Payload for ambient audio error events */
	AudioProAmbientErrorPayload,
	/** Type of ambient audio events that can be emitted */
	AudioProAmbientEvent,
	/** Callback function type for ambient audio events */
	AudioProAmbientEventCallback,
	// Equalizer types
	/** Configuration for an equalizer band */
	AudioProEqualizerBand,
	/** Configuration for an equalizer preset */
	AudioProEqualizerPreset,
	/** Type of audio events that can be emitted */
	AudioProEvent,
	/** Callback function type for audio events */
	AudioProEventCallback,
	/** Payload for playback error events */
	AudioProPlaybackErrorPayload,
	/** Payload for playback speed change events */
	AudioProPlaybackSpeedChangedPayload,
	/** Payload for progress update events */
	AudioProProgressPayload,
	/** Payload for seek completion events */
	AudioProSeekCompletePayload,
	/** Payload for state change events */
	AudioProStateChangedPayload,
	/** Represents an audio track with its properties */
	AudioProTrack,
	/** Payload for track ended events */
	AudioProTrackEndedPayload,
	/** Source that triggered an action */
	AudioProTriggerSource,
} from './types';

/**
 * Constants and enums used throughout the library
 * @see {@link ./values}
 */
export {
	/** Types of ambient audio events */
	AudioProAmbientEventType,
	/** Types of audio content supported */
	AudioProContentType,
	/** Types of events that can be emitted */
	AudioProEventType,
	/** Possible states of the audio player */
	AudioProState,
} from './values';

/**
 * Equalizer constants
 * @see {@link ./constants/equalizer}
 */
export {
	EQUALIZER_ADVANCED_PRESETS,
	EQUALIZER_BANDS,
	EQUALIZER_PRESETS,
} from './constants/equalizer';
