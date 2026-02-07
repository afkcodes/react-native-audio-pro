jest.useFakeTimers();

jest.mock('react-native', () => ({
	Platform: {
		OS: 'ios',
	},
	NativeModules: {
		AudioPro: {
			play: jest.fn(),
			pause: jest.fn(),
			resume: jest.fn(),
			stop: jest.fn(),
			ambientPlay: jest.fn(),
			ambientStop: jest.fn(),
			ambientPause: jest.fn(),
			ambientResume: jest.fn(),

			seekTo: jest.fn(),
			seekBy: jest.fn(),
			setPlaybackSpeed: jest.fn(),
			setVolume: jest.fn(),
			clear: jest.fn(),
			// New methods
			addToQueue: jest.fn(),
			clearQueue: jest.fn(),
			playNext: jest.fn(),
			playPrevious: jest.fn(),
			skipTo: jest.fn(),
			removeTrack: jest.fn(),
			setRepeatMode: jest.fn(),
			setShuffleMode: jest.fn(),
			getQueue: jest.fn().mockResolvedValue([]),
		},
	},
	NativeEventEmitter: jest.fn().mockImplementation(() => ({
		addListener: jest.fn(() => ({ remove: jest.fn() })),
		removeListener: jest.fn(),
	})),
}));

const mockState = {
	playerState: 'IDLE',
	position: 0,
	duration: 0,
	trackPlaying: null,
	volume: 1.0,
	playbackSpeed: 1.0,
	activeTrackIndex: -1,
	configureOptions: {
		progressIntervalMs: 1000,
	},
	error: null,
	debug: false,
	debugIncludesProgress: false,
};

const mockActions = {
	setTrackPlaying: jest.fn(),
	setError: jest.fn(),
	setPlaybackSpeed: jest.fn(),
	setVolume: jest.fn(),
	setConfigureOptions: jest.fn(),
	setDebug: jest.fn(),
	setDebugIncludesProgress: jest.fn(),
	updateFromEvent: jest.fn(),
};

function createInternalStoreMock(modulePath) {
	jest.mock(modulePath, () => {
		let currentState = { ...mockState, ...mockActions };

		const internalStore = jest.fn((selector) =>
			selector ? selector(currentState) : currentState,
		);
		internalStore.getState = () => currentState;
		internalStore.setState = (update) => {
			currentState = { ...currentState, ...update };
		};
		internalStore.subscribe = jest.fn();
		return { internalStore };
	});
}

function createEmitterMock(modulePath) {
	jest.mock(modulePath, () => ({
		emitter: {
			emit: jest.fn(),
			addListener: jest.fn(() => ({ remove: jest.fn() })),
		},
		ambientEmitter: {
			emit: jest.fn(),
			addListener: jest.fn(() => ({ remove: jest.fn() })),
		},
	}));
}

createInternalStoreMock('./src/internalStore');
createEmitterMock('./src/emitter');
