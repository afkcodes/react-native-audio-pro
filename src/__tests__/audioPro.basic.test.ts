import { NativeModules } from 'react-native';

import { AudioPro } from '../audioPro';
import { internalStore } from '../internalStore';

import type { AudioProTrack } from '../types';

// Helper function to mock internal store state
function useMockPlayerState(state: string) {
	internalStore.setState({
		// eslint-disable-next-line @typescript-eslint/no-explicit-any
		playerState: state as any,

		trackPlaying:
			// eslint-disable-next-line @typescript-eslint/no-explicit-any
			state !== 'IDLE' ? ({ id: 'test', url: 'test.mp3', title: 'Test' } as any) : null,
	});
}

// Reset state helper
function resetMockState() {
	internalStore.setState({
		// eslint-disable-next-line @typescript-eslint/no-explicit-any
		playerState: 'IDLE' as any,
		trackPlaying: null,
		position: 0,
		duration: 0,
		playbackSpeed: 1.0,
		volume: 1.0,
		activeTrackIndex: -1,
	});
}

describe('AudioPro basic functionality', () => {
	beforeEach(() => {
		jest.clearAllMocks();
		resetMockState();
	});
	// ... existing tests ...

	it('calls native play method with correct parameters', () => {
		AudioPro.play({
			// contentType is invalid here, removing
		});

		expect(NativeModules.AudioPro.play).toHaveBeenCalledWith(
			null, // Track is now null for resume/queue play
			expect.objectContaining({}),
		);
	});

	it('calls native pause method', () => {
		// Mock store to allow operation
		useMockPlayerState('PLAYING');
		AudioPro.pause();
		expect(NativeModules.AudioPro.pause).toHaveBeenCalled();
	});

	// Resume is handled by play()
	it('calls native play method for resume', () => {
		AudioPro.play();
		expect(NativeModules.AudioPro.play).toHaveBeenCalledWith(null, {});
	});

	it('calls native stop method', () => {
		AudioPro.stop();
		expect(NativeModules.AudioPro.stop).toHaveBeenCalled();
	});
});

describe('AudioPro ambient functionality', () => {
	beforeEach(() => {
		jest.clearAllMocks();
	});

	it('calls native ambientPlay method with correct parameters', () => {
		const options = {
			url: 'https://example.com/ambient.mp3',
			loop: true,
		};

		AudioPro.ambientPlay(options);

		expect(NativeModules.AudioPro.ambientPlay).toHaveBeenCalledWith(
			expect.objectContaining({
				url: 'https://example.com/ambient.mp3',
				loop: true,
			}),
		);
	});

	it('calls native ambientStop method', () => {
		AudioPro.ambientStop();
		expect(NativeModules.AudioPro.ambientStop).toHaveBeenCalled();
	});

	it('calls native ambientPause method', () => {
		AudioPro.ambientPause();
		expect(NativeModules.AudioPro.ambientPause).toHaveBeenCalled();
	});

	it('calls native ambientResume method', () => {
		AudioPro.ambientResume();
		expect(NativeModules.AudioPro.ambientResume).toHaveBeenCalled();
	});
});

describe('AudioPro playback control functionality', () => {
	beforeEach(() => {
		jest.clearAllMocks();
		useMockPlayerState('PLAYING');
	});

	it('calls native seekTo method with correct position', () => {
		AudioPro.seekTo(5000);
		expect(NativeModules.AudioPro.seekTo).toHaveBeenCalledWith(5000);
	});

	it('calls native seekBy method (forward)', () => {
		AudioPro.seekBy(5000);
		expect(NativeModules.AudioPro.seekBy).toHaveBeenCalledWith(5000);
	});

	it('calls native seekBy method (backward)', () => {
		AudioPro.seekBy(-5000);
		expect(NativeModules.AudioPro.seekBy).toHaveBeenCalledWith(-5000);
	});

	it('calls native setPlaybackSpeed method with correct speed', () => {
		const consoleSpy = jest.spyOn(console, 'warn').mockImplementation();
		// Mock trackPlaying to true so it calls native
		// eslint-disable-next-line @typescript-eslint/no-explicit-any
		internalStore.setState({ trackPlaying: { id: 'test' } as any });

		AudioPro.setPlaybackSpeed(1.5);
		expect(NativeModules.AudioPro.setPlaybackSpeed).toHaveBeenCalledWith(1.5);
		consoleSpy.mockRestore();
	});

	it('calls native setVolume method with correct volume', () => {
		const consoleSpy = jest.spyOn(console, 'warn').mockImplementation();
		// eslint-disable-next-line @typescript-eslint/no-explicit-any
		internalStore.setState({ trackPlaying: { id: 'test' } as any });

		AudioPro.setVolume(0.8);
		expect(NativeModules.AudioPro.setVolume).toHaveBeenCalledWith(0.8);
		consoleSpy.mockRestore();
	});

	it('updates progress interval in store', () => {
		AudioPro.setProgressInterval(1000);
		expect(internalStore.getState().setConfigureOptions).toHaveBeenCalledWith(
			expect.objectContaining({
				progressIntervalMs: 1000,
			}),
		);
	});
});

describe('AudioPro queue functionality', () => {
	beforeEach(() => {
		jest.clearAllMocks();
	});

	it('calls native addToQueue with correct parameters', () => {
		const tracks: AudioProTrack[] = [
			{
				id: 'track-1',
				url: 'https://example.com/1.mp3',
				title: 'Track 1',
				artwork: 'https://example.com/1.jpg',
				artist: 'Artist 1',
			},
			{
				id: 'track-2',
				url: 'https://example.com/2.mp3',
				title: 'Track 2',
				artwork: 'https://example.com/2.jpg',
				artist: 'Artist 2',
			},
		];

		AudioPro.addToQueue(tracks);
		expect(NativeModules.AudioPro.addToQueue).toHaveBeenCalledWith(tracks);
	});

	it('calls native clearQueue method', () => {
		AudioPro.clearQueue();
		expect(NativeModules.AudioPro.clearQueue).toHaveBeenCalled();
	});

	it('calls native removeTrack with correct index', () => {
		AudioPro.removeTrack(2);
		expect(NativeModules.AudioPro.removeTrack).toHaveBeenCalledWith(2);
	});

	it('calls native getQueue', async () => {
		await AudioPro.getQueue();
		expect(NativeModules.AudioPro.getQueue).toHaveBeenCalled();
	});
});

describe('AudioPro navigation controls', () => {
	beforeEach(() => {
		jest.clearAllMocks();
	});

	it('calls native playNext', () => {
		AudioPro.playNext();
		expect(NativeModules.AudioPro.playNext).toHaveBeenCalled();
	});

	it('calls native playPrevious', () => {
		AudioPro.playPrevious();
		expect(NativeModules.AudioPro.playPrevious).toHaveBeenCalled();
	});

	it('calls native skipTo with correct index', () => {
		AudioPro.skipTo(3);
		expect(NativeModules.AudioPro.skipTo).toHaveBeenCalledWith(3);
	});
});

describe('AudioPro playback modes', () => {
	beforeEach(() => {
		jest.clearAllMocks();
	});

	it('calls native setRepeatMode', () => {
		AudioPro.setRepeatMode('ONE');
		expect(NativeModules.AudioPro.setRepeatMode).toHaveBeenCalledWith('ONE');
	});

	it('calls native setShuffleMode', () => {
		AudioPro.setShuffleMode(true);
		expect(NativeModules.AudioPro.setShuffleMode).toHaveBeenCalledWith(true);
	});
});

describe('AudioPro getter methods', () => {
	it('returns correct timings', () => {
		const timings = AudioPro.getTimings();
		expect(timings).toEqual({
			position: 0,
			duration: 0,
		});
	});

	it('returns correct player state', () => {
		// eslint-disable-next-line @typescript-eslint/no-explicit-any
		internalStore.setState({ playerState: 'IDLE' as any });
		const state = AudioPro.getState();
		expect(state).toBe('IDLE'); // Default state
	});

	// Helper to mock state for other tests
	useMockPlayerState('PLAYING');

	it('returns correct playing track', () => {
		// eslint-disable-next-line @typescript-eslint/no-explicit-any
		internalStore.setState({ trackPlaying: { url: 'https://example.com/audio.mp3' } as any });
		const track = AudioPro.getPlayingTrack();
		expect(track).toEqual({
			url: 'https://example.com/audio.mp3',
		});
	});

	it('returns correct playback speed', () => {
		const speed = AudioPro.getPlaybackSpeed();
		expect(speed).toBe(1.0);
	});

	it('returns correct volume', () => {
		const volume = AudioPro.getVolume();
		expect(volume).toBe(1.0);
	});

	it('returns correct progress interval', () => {
		const interval = AudioPro.getProgressInterval();
		expect(interval).toBe(1000); // Default
	});

	it('returns correct active track index', () => {
		const index = AudioPro.getActiveTrackIndex();
		expect(index).toBe(-1); // Default initial state
	});
});

// Helper function to mock internal store state
