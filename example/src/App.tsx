import { useEffect, useState } from 'react';

import {
	DeviceEventEmitter,
	Image,
	SafeAreaView,
	ScrollView,
	Text,
	TouchableOpacity,
	View,
} from 'react-native';

import Slider from '@react-native-community/slider';
import { AudioPro, AudioProState, type AudioProTrack, useAudioPro } from 'react-native-audio-pro';

import { playlist } from './playlist';
import { styles } from './styles';
import { formatTime } from './utils';

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
	const [loopMode, setLoopMode] = useState<'OFF' | 'TRACK' | 'QUEUE'>('OFF');
	const [shuffle, setShuffle] = useState(false);

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
							const modes: ('OFF' | 'TRACK' | 'QUEUE')[] = ['OFF', 'TRACK', 'QUEUE'];
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
								const nextIndex = (queueIndex + 1) % playlist.length;
								const track1 = playlist[nextIndex];
								const track2 = playlist[(nextIndex + 1) % playlist.length];

								AudioPro.addToQueue([track1, track2] as AudioProTrack[]);
								console.log('Added 2 tracks to queue');
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
								// Simple alert via console for now
							}}
						>
							<Text style={styles.controlText}>LogQ</Text>
						</TouchableOpacity>
						<TouchableOpacity
							onPress={() => {
								// Remove the next track in the queue (if any)
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

				{error && (
					<View style={styles.errorContainer}>
						<Text style={styles.errorText}>Error: {error.error}</Text>
					</View>
				)}
			</ScrollView>
		</SafeAreaView>
	);
}
