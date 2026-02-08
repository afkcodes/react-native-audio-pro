import { useEffect, useState } from 'react';

import {
	DeviceEventEmitter,
	SafeAreaView,
	ScrollView,
	StyleSheet,
	Text,
	TouchableOpacity,
	View,
} from 'react-native';

import { AudioPro, AudioProState, type AudioProTrack, useAudioPro } from 'react-native-audio-pro';

// Components
import { AmbientPlayer } from './components/AmbientPlayer';
import { CacheManager } from './components/CacheManager';
import { PlayerControls } from './components/PlayerControls';
import { PlaylistControl } from './components/PlaylistControl';
import { ProgressBar } from './components/ProgressBar';
import { SleepTimer } from './components/SleepTimer';
import { SpeedControl } from './components/SpeedControl';
import { TrackInfo } from './components/TrackInfo';
import { URLRefreshLogic } from './components/URLRefreshLogic';
import { VolumeControl } from './components/VolumeControl';
import { EqualizerScreen } from './EqualizerScreen';
import { playlist } from './playlist';

export default function App() {

	const { position, duration, state, playingTrack, playbackSpeed, volume, error } = useAudioPro();

	const [showEqualizer, setShowEqualizer] = useState(false);
	const [needsTrackLoad, setNeedsTrackLoad] = useState(true);

	// Use playingTrack directly from native (source of truth)
	// Fallback to first track in playlist if nothing is playing
	const currentTrack = playingTrack ?? (playlist[0] as AudioProTrack);

	// Global Listeners setup
	useEffect(() => {
		// Configure notification buttons
		AudioPro.setNotificationButtons(['LIKE', 'PREV', 'NEXT']);

		const mainListener = AudioPro.addEventListener((event) => {
			if (event.type === 'CUSTOM_ACTION') {
				console.log('Custom action triggered:', event.payload?.action);
				// Handle custom actions like LIKE, SAVE here
			}
		});

		const logListener = DeviceEventEmitter.addListener('AudioProLog', (event) => {
			console.log('[NativeLog]', event.message);
		});

		return () => {
			mainListener.remove();
			logListener.remove();
		};
	}, []);

	useEffect(() => {
		if (state === AudioProState.PLAYING) {
			setNeedsTrackLoad(false);
		}
	}, [state]);

	// Handlers
	const handlePlayPause = () => {
		if (state === AudioProState.PLAYING) {
			AudioPro.pause();
		} else if (
			state === AudioProState.PAUSED ||
			state === AudioProState.STOPPED ||
			state === AudioProState.IDLE
		) {
			if (needsTrackLoad) {
				AudioPro.play();
				setNeedsTrackLoad(false);
			} else {
				AudioPro.play();
			}
		}
	};

	return (
		<SafeAreaView style={styles.container}>
			<URLRefreshLogic />

			<ScrollView
				contentContainerStyle={styles.scrollContent}
				showsVerticalScrollIndicator={false}
			>
				<Text style={styles.header}>AudioPro Player</Text>

				<TrackInfo track={currentTrack} />

				<ProgressBar
					position={position}
					duration={duration}
					onSeek={(val) => AudioPro.seekTo(val)}
				/>

				<PlayerControls
					state={state}
					onPlayPause={handlePlayPause}
					onNext={() => AudioPro.playNext()}
					onPrev={() => AudioPro.playPrevious()}
					onSeekForward={() => AudioPro.seekBy(5000)}
					onSeekBack={() => AudioPro.seekBy(-5000)}
				/>

				<VolumeControl
					volume={volume}
					onIncrease={() => AudioPro.setVolume(Math.min(1.0, volume + 0.1))}
					onDecrease={() => AudioPro.setVolume(Math.max(0.0, volume - 0.1))}
				/>

				<SpeedControl
					speed={playbackSpeed}
					onIncrease={() =>
						AudioPro.setPlaybackSpeed(Math.min(2.0, playbackSpeed + 0.25))
					}
					onDecrease={() =>
						AudioPro.setPlaybackSpeed(Math.max(0.25, playbackSpeed - 0.25))
					}
				/>

				<View style={styles.divider} />

				<PlaylistControl
					onToggleLoop={(mode) => AudioPro.setRepeatMode(mode)}
					onToggleShuffle={(enabled) => AudioPro.setShuffleMode(enabled)}
					onToggleSkipSilence={(enabled) => AudioPro.setSkipSilence(enabled)}
					onAddToQueue={() => AudioPro.addToQueue(playlist as AudioProTrack[])}
					onClearQueue={() => {
						AudioPro.clearQueue();
						setNeedsTrackLoad(true);
					}}
					onLogQueue={async () => console.log('Queue:', await AudioPro.getQueue())}
				/>

				<View style={styles.divider} />

				<SleepTimer
					onStart={(sec) => AudioPro.startSleepTimer(sec)}
					onCancel={() => AudioPro.cancelSleepTimer()}
				/>

				<AmbientPlayer
					onPlay={() =>
						AudioPro.ambientPlay({
							url: require('../assets/ambient-spring-forest-323801.mp3'),
							loop: true,
						})
					}
					onStop={() => AudioPro.ambientStop()}
					onPause={() => AudioPro.ambientPause()}
					onResume={() => AudioPro.ambientResume()}
				/>

				<CacheManager />

				<TouchableOpacity
					style={styles.eqButton}
					onPress={() => setShowEqualizer(!showEqualizer)}
				>
					<Text style={styles.eqButtonText}>
						{showEqualizer ? 'Hide Equalizer' : 'Show Equalizer'}
					</Text>
				</TouchableOpacity>

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

const styles = StyleSheet.create({
	container: {
		flex: 1,
		backgroundColor: '#121212',
	},
	scrollContent: {
		padding: 20,
		paddingBottom: 50,
	},
	header: {
		fontSize: 20,
		fontWeight: 'bold',
		color: '#fff',
		textAlign: 'center',
		marginBottom: 20,
		marginTop: 10,
	},
	divider: {
		height: 1,
		backgroundColor: '#333',
		marginVertical: 20,
	},
	eqButton: {
		backgroundColor: '#1E90FF',
		padding: 12,
		borderRadius: 8,
		alignItems: 'center',
		marginBottom: 20,
	},
	eqButtonText: {
		color: '#fff',
		fontWeight: 'bold',
		fontSize: 16,
	},
	errorContainer: {
		padding: 10,
		backgroundColor: 'rgba(255, 0, 0, 0.2)',
		borderRadius: 8,
		marginTop: 10,
	},
	errorText: {
		color: '#ff4444',
		textAlign: 'center',
	},
});
