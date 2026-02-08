import { useEffect, useState } from 'react';

import {
	DeviceEventEmitter,
	Image,
	SafeAreaView,
	ScrollView,
	Text,
	TextInput,
	TouchableOpacity,
	View,
} from 'react-native';

import Slider from '@react-native-community/slider';
import { AudioPro, AudioProState, type AudioProTrack, useAudioPro } from 'react-native-audio-pro';

import { EqualizerScreen } from './EqualizerScreen';
import { playlist } from './playlist';
import { styles as originalStyles } from './styles';
import { formatTime } from './utils';

const styles = {
	...originalStyles,
	eqButton: {
		backgroundColor: '#1db954',
		padding: 12,
		borderRadius: 8,
		alignItems: 'center',
		marginVertical: 10,
	} as const,
	eqButtonText: {
		color: '#fff',
		fontWeight: 'bold',
		fontSize: 16,
	} as const,
	section: {
		marginBottom: 20,
	} as const,
};

export default function App() {
	const {
		position,
		duration,
		state,
		playingTrack,
		activeTrackIndex,
		playbackSpeed,
		volume,
		error,
	} = useAudioPro();

	// Use playingTrack directly from native (source of truth)
	// Fallback to first track in playlist if nothing is playing
	const currentTrack = playingTrack ?? playlist[0];

	// For queue controls, we still need an index reference
	const queueIndex = activeTrackIndex === -1 ? 0 : activeTrackIndex;
	const [ambientState, setAmbientState] = useState<'stopped' | 'playing' | 'paused'>('stopped');

	// Track whether we need to load a new track before playing
	const [needsTrackLoad, setNeedsTrackLoad] = useState(true);

	// new loop/shuffle state
	const [loopMode, setLoopMode] = useState<'OFF' | 'ONE' | 'ALL'>('OFF');
	const [shuffle, setShuffle] = useState(false);
	const [showEqualizer, setShowEqualizer] = useState(false);
	const [cacheSize, setCacheSize] = useState<string>('Checking...');

	const [sleepTimerSeconds, setSleepTimerSeconds] = useState('10');
	const [skipSilence, setSkipSilence] = useState(false);

	useEffect(() => {
		checkCache();
	}, []);

	const checkCache = async () => {
		try {
			const size = await AudioPro.getCacheSize();
			const mb = (size / (1024 * 1024)).toFixed(2);
			setCacheSize(`${mb} MB`);
		} catch (e) {
			console.error('Failed to get cache size', e);
			setCacheSize('Error');
		}
	};

	const handleClearCache = async () => {
		try {
			await AudioPro.clearCache();
			checkCache();
		} catch (e) {
			console.error('Failed to clear cache', e);
		}
	};

	// Update needsTrackLoad based on state
	useEffect(() => {
		if (state === AudioProState.IDLE || state === AudioProState.STOPPED) {
			// If we are stopped/idle and index implies we want to play, we might need load?
			// Actually, if we rely on native queue, we don't need this complex logic.
			// But for the "Play" button to work initially:
		}
		if (state === AudioProState.PLAYING) {
			setNeedsTrackLoad(false);
		}
	}, [state]);

	// Set up ambient audio event listeners
	useEffect(() => {
		// Configure notification buttons with custom actions
		AudioPro.setNotificationButtons(['LIKE', 'PREV', 'NEXT']);

		// Add main audio event listener for custom actions
		const mainListener = AudioPro.addEventListener((event) => {
			if (event.type === 'CUSTOM_ACTION') {
				console.log('Custom action triggered:', event.payload?.action);
				// Handle custom actions
				switch (event.payload?.action) {
					case 'LIKE':
						console.log('User liked the track!');
						// Add your like logic here
						break;
					case 'SAVE':
						console.log('User saved the track!');
						// Add your save logic here
						break;
					case 'REWIND_30':
						console.log('User rewound 30 seconds');
						break;
					case 'FORWARD_30':
						console.log('User forwarded 30 seconds');
						break;
				}
			}
		});

		// Add ambient audio event listeners
		const ambientListener = AudioPro.addAmbientListener((event) => {
			console.log('Ambient audio event:', event.type);

			switch (event.type) {
				case 'AMBIENT_TRACK_ENDED':
					console.log('Ambient track ended');
					// Update state if loop is false and track ended
					setAmbientState('stopped');
					break;

				case 'AMBIENT_ERROR':
					console.warn('Ambient error:', event.payload?.error);
					// Update state on error
					setAmbientState('stopped');
					break;
			}
		});

		const logListener = DeviceEventEmitter.addListener('AudioProLog', (event) => {
			console.log('[NativeLog]', event.message);
		});

		// Clean up listeners when component unmounts
		return () => {
			mainListener.remove();
			ambientListener.remove();
			logListener.remove();
		};
	}, []);

	// Track whether we need to load a new track before playing

	if (!currentTrack) return null;

	// Handle play/pause button press
	const handlePlayPause = () => {
		if (state === AudioProState.PLAYING) {
			AudioPro.pause();
		} else if (
			state === AudioProState.PAUSED ||
			state === AudioProState.STOPPED ||
			state === AudioProState.IDLE
		) {
			if (needsTrackLoad) {
				// Initial load
				AudioPro.play();
				setNeedsTrackLoad(false);
			} else {
				// Just resume
				AudioPro.play();
			}
		}
	};

	const handleStop = () => {
		AudioPro.stop();
		setNeedsTrackLoad(true);
	};

	const handleClear = () => {
		AudioPro.clearQueue();
		setNeedsTrackLoad(true);
	};

	const handleSeek = (value: number) => {
		AudioPro.seekTo(value);
	};

	const handleSeekBack = () => {
		AudioPro.seekBy(-5000);
	};

	const handleSeekForward = () => {
		AudioPro.seekBy(5000);
	};

	const handleIncreaseSpeed = () => {
		const newSpeed = Math.min(2.0, playbackSpeed + 0.25);
		AudioPro.setPlaybackSpeed(newSpeed);
	};

	const handleDecreaseSpeed = () => {
		const newSpeed = Math.max(0.25, playbackSpeed - 0.25);
		AudioPro.setPlaybackSpeed(newSpeed);
	};

	const handleIncreaseVolume = () => {
		const newVolume = Math.min(1.0, volume + 0.1);
		AudioPro.setVolume(newVolume);
	};

	const handleDecreaseVolume = () => {
		const newVolume = Math.max(0.0, volume - 0.1);
		AudioPro.setVolume(newVolume);
	};

	// Handle ambient audio playback
	const handleAmbientPlay = () => {
		// Play ambient audio from local file
		AudioPro.ambientPlay({
			url: require('../assets/ambient-spring-forest-323801.mp3'),
			loop: true,
		});
		setAmbientState('playing');
	};

	// Handle ambient audio stop
	const handleAmbientStop = () => {
		// Stop ambient audio
		AudioPro.ambientStop();
		setAmbientState('stopped');
	};

	// Toggle ambient audio pause/resume
	const handleAmbientTogglePause = () => {
		if (ambientState === 'playing') {
			// Pause ambient audio
			AudioPro.ambientPause();
			setAmbientState('paused');
		} else if (ambientState === 'paused') {
			// Resume ambient audio
			AudioPro.ambientResume();
			setAmbientState('playing');
		}
	};

	return (
		<SafeAreaView style={styles.container}>
			<ScrollView
				contentContainerStyle={styles.scrollContent}
				showsVerticalScrollIndicator={false}
			>
				<Image
					source={
						typeof currentTrack.artwork === 'number'
							? currentTrack.artwork
							: { uri: currentTrack.artwork }
					}
					style={styles.artwork}
				/>
				<Text style={styles.title}>{currentTrack.title}</Text>
				<Text style={styles.artist}>{currentTrack.artist}</Text>
				<View style={styles.sliderContainer}>
					<Text style={styles.timeText}>{formatTime(position)}</Text>
					<Slider
						style={styles.slider}
						minimumValue={0}
						maximumValue={duration}
						value={position}
						minimumTrackTintColor="#1EB1FC"
						maximumTrackTintColor="#8E8E93"
						thumbTintColor="#1EB1FC"
						onSlidingComplete={handleSeek}
					/>
					<Text style={styles.timeText}>
						{formatTime(Math.max(0, duration - position))}
					</Text>
				</View>
				<View style={styles.controlsRow}>
					<TouchableOpacity onPress={() => AudioPro.playPrevious()}>
						<Image
							source={{
								uri: 'https://img.icons8.com/ios-glyphs/60/ffffff/skip-to-start.png',
							}}
							style={styles.controlIcon}
						/>
					</TouchableOpacity>

					<TouchableOpacity onPress={handleSeekBack}>
						<Text style={styles.controlText}>-5s</Text>
					</TouchableOpacity>

					<TouchableOpacity onPress={handlePlayPause} style={styles.playPauseButton}>
						<Image
							source={{
								uri:
									state === AudioProState.PLAYING ||
									state === AudioProState.LOADING
										? 'https://img.icons8.com/ios-glyphs/60/ffffff/pause--v1.png'
										: 'https://img.icons8.com/ios-glyphs/60/ffffff/play--v1.png',
							}}
							style={styles.playPauseIcon}
						/>
					</TouchableOpacity>

					<TouchableOpacity onPress={handleSeekForward}>
						<Text style={styles.controlText}>+5s</Text>
					</TouchableOpacity>

					<TouchableOpacity onPress={() => AudioPro.playNext()}>
						<Image
							source={{
								uri: 'https://img.icons8.com/ios-glyphs/60/ffffff/end--v1.png',
							}}
							style={styles.controlIcon}
						/>
					</TouchableOpacity>
				</View>

				<View style={[styles.speedRow, { marginTop: 10 }]}>
					<TouchableOpacity
						onPress={() => {
							const modes: ('OFF' | 'ONE' | 'ALL')[] = ['OFF', 'ONE', 'ALL'];
							const currentIndex = modes.indexOf(loopMode);
							const nextIndex = (currentIndex + 1) % modes.length;
							const next = modes[nextIndex] || 'OFF';
							setLoopMode(next);
							AudioPro.setRepeatMode(next);
						}}
					>
						<Text style={styles.controlText}>Loop: {loopMode}</Text>
					</TouchableOpacity>

					<TouchableOpacity
						onPress={() => {
							const next = !shuffle;
							setShuffle(next);
							AudioPro.setShuffleMode(next);
						}}
					>
						<Text style={styles.controlText}>Shuffle: {shuffle ? 'ON' : 'OFF'}</Text>
					</TouchableOpacity>
				</View>

				<View style={[styles.speedRow, { marginTop: 10 }]}>
					<TouchableOpacity
						onPress={() => {
							const next = !skipSilence;
							setSkipSilence(next);
							AudioPro.setSkipSilence(next);
						}}
					>
						<Text style={styles.controlText}>
							Skip Silence: {skipSilence ? 'ON' : 'OFF'}
						</Text>
					</TouchableOpacity>
				</View>

				<View style={styles.speedRow}>
					<TouchableOpacity onPress={handleDecreaseSpeed}>
						<Text style={styles.controlText}>-</Text>
					</TouchableOpacity>
					<Text style={styles.speedText}>Speed: {playbackSpeed}x</Text>
					<TouchableOpacity onPress={handleIncreaseSpeed}>
						<Text style={styles.controlText}>+</Text>
					</TouchableOpacity>
				</View>
				<View style={styles.generalRow}>
					<View style={styles.speedRow}>
						<TouchableOpacity onPress={handleDecreaseVolume}>
							<Text style={styles.controlText}>-</Text>
						</TouchableOpacity>
						<Text style={styles.speedText}>Vol: {Math.round(volume * 100)}%</Text>
						<TouchableOpacity onPress={handleIncreaseVolume}>
							<Text style={styles.controlText}>+</Text>
						</TouchableOpacity>
					</View>

					{/* Progress interval controls moved or removed if cleaner UI desired, keeping for debug */}
				</View>
				<View style={styles.stopRow}>
					<TouchableOpacity onPress={handleStop}>
						<Text style={styles.controlText}>stop()</Text>
					</TouchableOpacity>
					<TouchableOpacity onPress={handleClear}>
						<Text style={styles.controlText}>clear()</Text>
					</TouchableOpacity>
				</View>

				<View style={styles.ambientSection}>
					<Text style={styles.sectionTitle}>Queue Controls</Text>
					<View style={styles.stopRow}>
						<TouchableOpacity
							onPress={() => {
								AudioPro.addToQueue(playlist as AudioProTrack[]);
							}}
						>
							<Text style={styles.controlText}>+2 Tracks</Text>
						</TouchableOpacity>
						<TouchableOpacity
							onPress={() => {
								AudioPro.clearQueue();
								console.log('Queue cleared');
							}}
						>
							<Text style={styles.controlText}>ClearQ</Text>
						</TouchableOpacity>
						<TouchableOpacity
							onPress={async () => {
								const q = await AudioPro.getQueue();
								console.log('Queue:', q);
							}}
						>
							<Text style={styles.controlText}>LogQ</Text>
						</TouchableOpacity>
						<TouchableOpacity
							onPress={() => {
								const nextIndex = queueIndex + 1;
								AudioPro.removeTrack(nextIndex);
								console.log('Removed track at index:', nextIndex);
							}}
						>
							<Text style={styles.controlText}>RmNext</Text>
						</TouchableOpacity>
					</View>
				</View>

				<View style={styles.ambientSection}>
					<Text style={styles.sectionTitle}>Sleep Timer</Text>
					<View
						style={{
							flexDirection: 'row',
							alignItems: 'center',
							justifyContent: 'center',
							marginBottom: 10,
						}}
					>
						<Text style={{ color: '#fff', marginRight: 10 }}>Seconds:</Text>
						<TextInput
							style={{
								backgroundColor: '#333',
								color: '#fff',
								padding: 5,
								width: 60,
								borderRadius: 4,
								textAlign: 'center',
							}}
							value={sleepTimerSeconds}
							onChangeText={setSleepTimerSeconds}
							keyboardType="numeric"
						/>
					</View>

					<View style={styles.stopRow}>
						<TouchableOpacity
							onPress={() => {
								const sec = parseInt(sleepTimerSeconds, 10);
								if (!isNaN(sec) && sec > 0) {
									AudioPro.startSleepTimer(sec);
									console.log('Started sleep timer for', sec);
								}
							}}
						>
							<Text style={styles.controlText}>Start ({sleepTimerSeconds}s)</Text>
						</TouchableOpacity>
						<TouchableOpacity
							onPress={() => {
								AudioPro.cancelSleepTimer();
								console.log('Canceled sleep timer');
							}}
						>
							<Text style={styles.controlText}>Cancel</Text>
						</TouchableOpacity>
					</View>
				</View>

				<View style={styles.ambientSection}>
					<Text style={styles.sectionTitle}>Ambient Audio</Text>
					<View style={styles.stopRow}>
						{ambientState === 'stopped' ? (
							<TouchableOpacity onPress={handleAmbientPlay}>
								<Text style={styles.controlText}>ambientPlay()</Text>
							</TouchableOpacity>
						) : (
							<TouchableOpacity onPress={handleAmbientTogglePause}>
								<Text style={styles.controlText}>
									{ambientState === 'playing'
										? 'ambientPause()'
										: 'ambientResume()'}
								</Text>
							</TouchableOpacity>
						)}
						<TouchableOpacity onPress={handleAmbientStop}>
							<Text style={styles.controlText}>ambientStop()</Text>
						</TouchableOpacity>
					</View>
				</View>

				<View style={styles.section}>
					<TouchableOpacity
						style={styles.eqButton}
						onPress={() => setShowEqualizer(!showEqualizer)}
					>
						<Text style={styles.eqButtonText}>
							{showEqualizer ? 'Hide Equalizer' : 'Show Equalizer'}
						</Text>
					</TouchableOpacity>

					<View style={styles.ambientSection}>
						<Text style={styles.sectionTitle}>Cache Management</Text>
						<Text style={[styles.timeText, { textAlign: 'center', marginBottom: 10 }]}>
							Size: {cacheSize}
						</Text>
						<View style={styles.stopRow}>
							<TouchableOpacity onPress={checkCache}>
								<Text style={styles.controlText}>Refresh</Text>
							</TouchableOpacity>
							<TouchableOpacity onPress={handleClearCache}>
								<Text style={styles.controlText}>Clear Cache</Text>
							</TouchableOpacity>
						</View>
					</View>
				</View>

				{showEqualizer && <EqualizerScreen />}

				{error && (
					<View style={styles.errorContainer}>
						<Text style={styles.errorText}>Error: {error.error}</Text>
					</View>
				)}
			</ScrollView>
		</SafeAreaView>
	);
}
