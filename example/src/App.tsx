import { useCallback, useEffect, useState } from 'react';

import {
	DeviceEventEmitter,
	Image,
	SafeAreaView,
	ScrollView,
	StyleSheet,
	Text,
	TouchableOpacity,
	View,
} from 'react-native';

import Slider from '@react-native-community/slider';
import {
	AudioPro,
	AudioProAmbientEventType,
	AudioProContentType,
	AudioProEventType,
	AudioProState,
	EQUALIZER_BANDS,
	EQUALIZER_PRESETS,
	useAudioPro,
	type AudioProTrack,
} from 'react-native-audio-pro';

import { playlist } from './playlist';
import { formatTime } from './utils';

// Theme Colors
const COLORS = {
	bg: '#0A0A0F',
	card: '#16161D',
	cardLight: '#1E1E28',
	accent: '#6366F1',
	accentDim: '#4F46E5',
	text: '#FFFFFF',
	textSecondary: '#9CA3AF',
	textMuted: '#6B7280',
	success: '#22C55E',
	warning: '#F59E0B',
	error: '#EF4444',
	border: '#27272A',
};

type TabKey = 'queue' | 'settings' | 'effects' | 'extras';

// ===== URL Refresh Logic =====
// Throttle map to prevent redundant API calls
const lastRefreshMap = new Map<string, number>();
const REFRESH_THROTTLE_MS = 30 * 60 * 1000; // 30 minutes

/**
 * Refresh the stream URL for a Gaana track
 * @param index Track index in queue
 * @param track Track object
 * @param force If true, bypasses throttle (used for error recovery)
 * @returns true if URL was updated, false otherwise
 */
async function refreshTrackUrl(
	index: number,
	track: AudioProTrack,
	force: boolean = false,
): Promise<boolean> {
	if (!track || !track.id) return false;

	const source = (track as any).source || 'gaana';
	const now = Date.now();
	const lastRefresh = lastRefreshMap.get(track.id) || 0;

	// Throttle: skip if refreshed recently (unless forced by error)
	if (!force && now - lastRefresh < REFRESH_THROTTLE_MS) {
		console.log(
			`[URLRefresh] Skipping ${track.title} (refreshed ${Math.round((now - lastRefresh) / 60000)}m ago)`,
		);
		return false;
	}

	// Only refresh Gaana tracks (proactively)
	if (!force && source !== 'gaana') {
		return false;
	}

	console.log(
		`[URLRefresh] Fetching new URL for track ${index}: ${track.title} (forced: ${force})`,
	);

	try {
		const apiUrl = `https://api.sunoh.online/music/song/${track.id}/stream?provider=${source}`;
		console.log(`[URLRefresh] API URL: ${apiUrl}`);
		const response = await fetch(apiUrl);

		if (!response.ok) {
			throw new Error(`API Error: ${response.status}`);
		}

		const json = await response.json();
		console.log(
			`[URLRefresh] API response status: ${json.status}, data count: ${json.data?.length || 0}`,
		);

		if (json.status === 'success' && Array.isArray(json.data) && json.data.length > 0) {
			const links = json.data;
			let newUrl = links[0].link;

			// Prefer high quality
			const high = links.find((l: any) => l.quality === 'high' || l.quality === '320kbps');
			const medium = links.find(
				(l: any) => l.quality === 'medium' || l.quality === '160kbps',
			);

			if (high) newUrl = high.link;
			else if (medium) newUrl = medium.link;

			// Update last refresh timestamp
			lastRefreshMap.set(track.id, Date.now());

			console.log(`[URLRefresh] Got new URL, different from old: ${newUrl !== track.url}`);

			// Always update when forced (error recovery), even if URL looks the same
			if (newUrl && (force || newUrl !== track.url)) {
				console.log(`[URLRefresh] Updating track ${index} with new URL`);
				AudioPro.updateTrack(index, {
					...track,
					url: newUrl,
				});
				return true;
			}
		}
	} catch (err) {
		console.error(`[URLRefresh] Error fetching stream for ${track.id}:`, err);
	}
	return false;
}

export default function App() {
	const {
		position,
		duration,
		bufferedPosition,
		state,
		playingTrack,
		activeTrackIndex,
		playbackSpeed,
		volume,
		queueSize,
		error,
	} = useAudioPro();

	const [activeTab, setActiveTab] = useState<TabKey>('queue');
	const [repeatMode, setRepeatModeState] = useState<'OFF' | 'ONE' | 'ALL'>('OFF');
	const [shuffleEnabled, setShuffleEnabled] = useState(false);
	const [skipSilence, setSkipSilenceState] = useState(false);
	const [liked, setLiked] = useState(false);
	const [bookmarked, setBookmarked] = useState(false);
	const [eqGains, setEqGains] = useState<number[]>(new Array(EQUALIZER_BANDS.length).fill(0));
	const [bassBoost, setBassBoost] = useState(0);
	const [eqPreset, setEqPreset] = useState('default');
	const [sleepMinutes, setSleepMinutes] = useState(15);
	const [sleepActive, setSleepActive] = useState(false);
	const [cacheSize, setCacheSize] = useState('--');
	const [ambientState, setAmbientState] = useState<'stopped' | 'playing' | 'paused'>('stopped');
	const [ambientVolume, setAmbientVolumeState] = useState(0.5);
	const [progressInterval, setProgressIntervalState] = useState(1000);

	// Initialize on mount
	useEffect(() => {
		// Configure the player
		AudioPro.configure({
			debug: true,
			contentType: AudioProContentType.MUSIC,
			progressIntervalMs: 1000,
		});

		// Set custom notification buttons
		AudioPro.setNotificationButtons(['LIKE', 'PREV', 'NEXT', 'BOOKMARK']);

		// Load initial queue
		AudioPro.addMediaItems(playlist as AudioProTrack[]);

		// Listen for events
		const mainSub = AudioPro.addEventListener((event) => {
			if (event.type === AudioProEventType.CUSTOM_ACTION) {
				const action = event.payload?.action;
				console.log('[AudioPro] Custom action:', action);
				if (action === 'LIKE') {
					setLiked((prev) => !prev);
				} else if (action === 'BOOKMARK') {
					setBookmarked((prev) => !prev);
				}
			}
			if (event.type === AudioProEventType.SLEEP_TIMER_COMPLETE) {
				setSleepActive(false);
			}

			// ===== URL Refresh Logic (Sliding Window + Error Recovery) =====

			// Sliding Window: On track change, pre-refresh next/prev tracks
			if (event.type === AudioProEventType.TRACK_CHANGED) {
				const { index } = event.payload || {};
				if (typeof index === 'number') {
					AudioPro.getMediaItems().then((queue) => {
						// Refresh next track (index + 1)
						if (index + 1 < queue.length) {
							const nextTrack = queue[index + 1];
							if (nextTrack && (nextTrack as any).source === 'gaana') {
								refreshTrackUrl(index + 1, nextTrack);
							}
						}
						// Refresh previous track (index - 1)
						if (index - 1 >= 0) {
							const prevTrack = queue[index - 1];
							if (prevTrack && (prevTrack as any).source === 'gaana') {
								refreshTrackUrl(index - 1, prevTrack);
							}
						}
					});
				}
			}

			// Error Recovery: On playback error, force-refresh current track and retry
			if (event.type === AudioProEventType.PLAYBACK_ERROR) {
				const { index } = event.payload || {};
				const errorMessage = event.payload?.error || 'Unknown error';
				const errorCode = event.payload?.errorCode;
				const recoverable = event.payload?.recoverable;

				console.log(
					`[URLRefresh] PLAYBACK_ERROR: index=${index}, error="${errorMessage}", code=${errorCode}, recoverable=${recoverable}`,
				);

				if (typeof index === 'number' && index >= 0) {
					console.log(`[URLRefresh] Attempting refresh for index ${index}...`);

					AudioPro.getMediaItems().then(async (queue) => {
						const track = queue[index];
						if (!track) {
							console.log(`[URLRefresh] No track found at index ${index}`);
							return;
						}

						// Check if source is gaana (stored in track metadata)
						const source = (track as any).source || 'gaana';
						console.log(`[URLRefresh] Track source: ${source}, id: ${track.id}`);

						if (source !== 'gaana') {
							console.log('[URLRefresh] Skipping - not a Gaana track');
							return;
						}

						// Force refresh (bypass throttle) on error
						const updated = await refreshTrackUrl(index, track, true);
						console.log(
							`[URLRefresh] Refresh result: ${updated ? 'URL updated' : 'No update'}`,
						);

						// Retry playback after a short delay - use seekToMediaItem + play
						setTimeout(() => {
							console.log(
								`[URLRefresh] Retrying playback: seekToMediaItem(${index}) + play()`,
							);
							AudioPro.seekToMediaItem(index);
							// Give the player a moment to prepare, then play
							setTimeout(() => {
								AudioPro.play();
							}, 100);
						}, 200);
					});
				} else {
					console.log(`[URLRefresh] Invalid index: ${index}, skipping refresh`);
				}
			}
		});

		const ambientSub = AudioPro.addAmbientListener((event) => {
			console.log('[AudioPro] Ambient event:', event.type);
			if (event.type === AudioProAmbientEventType.AMBIENT_TRACK_ENDED) {
				setAmbientState('stopped');
			}
		});

		const logSub = DeviceEventEmitter.addListener('AudioProLog', (event) => {
			console.log('[Native]', event.message);
		});

		// Get initial cache size
		refreshCacheSize();

		return () => {
			mainSub.remove();
			ambientSub.remove();
			logSub.remove();
		};
	}, []);

	// Update notification state when liked/bookmarked changes
	useEffect(() => {
		AudioPro.updateNotificationState({ liked, bookmarked });
	}, [liked, bookmarked]);

	const refreshCacheSize = async () => {
		try {
			const size = await AudioPro.getCacheSize();
			setCacheSize(`${(size / (1024 * 1024)).toFixed(1)} MB`);
		} catch {
			setCacheSize('Error');
		}
	};

	// Play/Pause handler
	const handlePlayPause = useCallback(() => {
		if (state === AudioProState.PLAYING) {
			AudioPro.pause();
		} else {
			AudioPro.play();
		}
	}, [state]);

	// Repeat mode toggle
	const handleRepeatToggle = () => {
		const modes: ('OFF' | 'ONE' | 'ALL')[] = ['OFF', 'ONE', 'ALL'];
		const next = modes[(modes.indexOf(repeatMode) + 1) % 3]!;
		setRepeatModeState(next);
		AudioPro.setRepeatMode(next);
	};

	// Shuffle toggle
	const handleShuffleToggle = () => {
		const next = !shuffleEnabled;
		setShuffleEnabled(next);
		AudioPro.setShuffleModeEnabled(next);
	};

	const isPlaying = state === AudioProState.PLAYING;
	const isLoading = state === AudioProState.LOADING;
	const currentTrack = playingTrack ?? (playlist[0] as AudioProTrack);

	// Tab Renderer
	const renderTab = () => {
		switch (activeTab) {
			case 'queue':
				return <QueueTab queueSize={queueSize} activeIndex={activeTrackIndex} />;
			case 'settings':
				return (
					<SettingsTab
						volume={volume}
						playbackSpeed={playbackSpeed}
						skipSilence={skipSilence}
						progressInterval={progressInterval}
						onVolumeChange={(v) => AudioPro.setVolume(v)}
						onSpeedChange={(s) => AudioPro.setPlaybackSpeed(s)}
						onSkipSilenceToggle={() => {
							const next = !skipSilence;
							setSkipSilenceState(next);
							AudioPro.setSkipSilence(next);
						}}
						onProgressIntervalChange={(ms) => {
							setProgressIntervalState(ms);
							AudioPro.setProgressInterval(ms);
						}}
					/>
				);
			case 'effects':
				return (
					<EffectsTab
						eqGains={eqGains}
						bassBoost={bassBoost}
						selectedPreset={eqPreset}
						onEqChange={(index, value) => {
							const newGains = [...eqGains];
							newGains[index] = value;
							setEqGains(newGains);
							AudioPro.setEqualizer(newGains);
							setEqPreset('custom');
						}}
						onPresetSelect={(preset) => {
							const p = EQUALIZER_PRESETS.find((x) => x.id === preset);
							if (p) {
								setEqGains([...p.gains]);
								AudioPro.setEqualizer(p.gains);
								setEqPreset(preset);
							}
						}}
						onBassBoostChange={(v) => {
							setBassBoost(v);
							AudioPro.setBassBoost(v);
						}}
					/>
				);
			case 'extras':
				return (
					<ExtrasTab
						sleepMinutes={sleepMinutes}
						sleepActive={sleepActive}
						cacheSize={cacheSize}
						ambientState={ambientState}
						ambientVolume={ambientVolume}
						onSleepMinutesChange={setSleepMinutes}
						onSleepStart={() => {
							AudioPro.startSleepTimer(sleepMinutes * 60);
							setSleepActive(true);
						}}
						onSleepCancel={() => {
							AudioPro.cancelSleepTimer();
							setSleepActive(false);
						}}
						onClearCache={async () => {
							await AudioPro.clearCache();
							refreshCacheSize();
						}}
						onRefreshCache={refreshCacheSize}
						onAmbientPlay={() => {
							AudioPro.ambientPlay({
								url: require('../assets/ambient-spring-forest-323801.mp3'),
								loop: true,
							});
							setAmbientState('playing');
						}}
						onAmbientPause={() => {
							AudioPro.ambientPause();
							setAmbientState('paused');
						}}
						onAmbientResume={() => {
							AudioPro.ambientResume();
							setAmbientState('playing');
						}}
						onAmbientStop={() => {
							AudioPro.ambientStop();
							setAmbientState('stopped');
						}}
						onAmbientVolumeChange={(v) => {
							setAmbientVolumeState(v);
							AudioPro.ambientSetVolume(v);
						}}
					/>
				);
		}
	};

	return (
		<SafeAreaView style={styles.container}>
			<ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
				{/* Header */}
				<Text style={styles.header}>AudioPro Demo</Text>

				{/* Now Playing Card */}
				<View style={styles.nowPlayingCard}>
					<Image
						source={
							typeof currentTrack.artwork === 'number'
								? currentTrack.artwork
								: { uri: currentTrack.artwork }
						}
						style={styles.artwork}
					/>
					<View style={styles.trackTextContainer}>
						<Text style={styles.trackTitle} numberOfLines={1}>
							{currentTrack.title}
						</Text>
						<Text style={styles.trackArtist} numberOfLines={1}>
							{currentTrack.artist}
						</Text>
					</View>

					{/* Like / Bookmark */}
					<View style={styles.actionRow}>
						<TouchableOpacity onPress={() => setLiked(!liked)} style={styles.actionBtn}>
							<Text style={[styles.actionIcon, liked && styles.actionActive]}>
								{liked ? '❤️' : '🤍'}
							</Text>
						</TouchableOpacity>
						<TouchableOpacity
							onPress={() => setBookmarked(!bookmarked)}
							style={styles.actionBtn}
						>
							<Text style={[styles.actionIcon, bookmarked && styles.actionActive]}>
								{bookmarked ? '🔖' : '📑'}
							</Text>
						</TouchableOpacity>
					</View>
				</View>

				{/* Progress Bar */}
				<View style={styles.progressContainer}>
					<Slider
						style={styles.progressSlider}
						minimumValue={0}
						maximumValue={duration || 1}
						value={position}
						minimumTrackTintColor={COLORS.accent}
						maximumTrackTintColor={COLORS.border}
						thumbTintColor={COLORS.accent}
						onSlidingComplete={(val) => AudioPro.seekTo(val)}
					/>
					{/* Buffered indicator */}
					<View
						style={[
							styles.bufferedBar,
							{ width: `${duration > 0 ? (bufferedPosition / duration) * 100 : 0}%` },
						]}
					/>
				</View>
				<View style={styles.timeRow}>
					<Text style={styles.timeText}>{formatTime(position)}</Text>
					<Text style={styles.timeText}>{formatTime(duration)}</Text>
				</View>

				{/* Main Controls */}
				<View style={styles.controlsRow}>
					<TouchableOpacity onPress={handleShuffleToggle} style={styles.sideControlBtn}>
						<Text
							style={[styles.controlIcon, shuffleEnabled && styles.controlIconActive]}
						>
							🔀
						</Text>
					</TouchableOpacity>

					<TouchableOpacity
						onPress={() => AudioPro.seekToPreviousMediaItem()}
						style={styles.controlBtn}
					>
						<Text style={styles.controlIcon}>⏮️</Text>
					</TouchableOpacity>

					<TouchableOpacity
						onPress={() => AudioPro.seekBy(-10000)}
						style={styles.controlBtn}
					>
						<Text style={styles.seekText}>-10</Text>
					</TouchableOpacity>

					<TouchableOpacity onPress={handlePlayPause} style={styles.playBtn}>
						{isLoading ? (
							<Text style={styles.playIcon}>⏳</Text>
						) : (
							<Text style={styles.playIcon}>{isPlaying ? '⏸️' : '▶️'}</Text>
						)}
					</TouchableOpacity>

					<TouchableOpacity
						onPress={() => AudioPro.seekBy(10000)}
						style={styles.controlBtn}
					>
						<Text style={styles.seekText}>+10</Text>
					</TouchableOpacity>

					<TouchableOpacity
						onPress={() => AudioPro.seekToNextMediaItem()}
						style={styles.controlBtn}
					>
						<Text style={styles.controlIcon}>⏭️</Text>
					</TouchableOpacity>

					<TouchableOpacity onPress={handleRepeatToggle} style={styles.sideControlBtn}>
						<Text
							style={[
								styles.controlIcon,
								repeatMode !== 'OFF' && styles.controlIconActive,
							]}
						>
							{repeatMode === 'ONE' ? '🔂' : '🔁'}
						</Text>
					</TouchableOpacity>
				</View>

				{/* Stop Button */}
				<TouchableOpacity onPress={() => AudioPro.stop()} style={styles.stopBtn}>
					<Text style={styles.stopBtnText}>⏹️ Stop</Text>
				</TouchableOpacity>

				{/* Error Display */}
				{error && (
					<View style={styles.errorBox}>
						<Text style={styles.errorText}>
							Error: {error.error} (Code: {error.errorCode})
						</Text>
						<Text style={styles.errorSubtext}>
							{error.recoverable ? 'Recoverable' : 'Unrecoverable'}
						</Text>
					</View>
				)}

				{/* Tabs */}
				<View style={styles.tabBar}>
					{(['queue', 'settings', 'effects', 'extras'] as TabKey[]).map((tab) => (
						<TouchableOpacity
							key={tab}
							onPress={() => setActiveTab(tab)}
							style={[styles.tabItem, activeTab === tab && styles.tabItemActive]}
						>
							<Text
								style={[styles.tabText, activeTab === tab && styles.tabTextActive]}
							>
								{tab.charAt(0).toUpperCase() + tab.slice(1)}
							</Text>
						</TouchableOpacity>
					))}
				</View>

				{/* Tab Content */}
				<View style={styles.tabContent}>{renderTab()}</View>
			</ScrollView>
		</SafeAreaView>
	);
}

// ============ QUEUE TAB ============
interface QueueTabProps {
	queueSize: number;
	activeIndex: number;
}

function QueueTab({ queueSize, activeIndex }: QueueTabProps) {
	const [queue, setQueue] = useState<AudioProTrack[]>([]);

	const loadQueue = async () => {
		const items = await AudioPro.getMediaItems();
		setQueue(items);
	};

	useEffect(() => {
		loadQueue();
	}, [queueSize]);

	return (
		<View>
			<Text style={styles.sectionTitle}>
				Queue ({queueSize} items) — Playing #{activeIndex + 1}
			</Text>

			{/* Queue Actions */}
			<View style={styles.row}>
				<TouchableOpacity
					style={styles.smallBtn}
					onPress={() => AudioPro.addMediaItems(playlist as AudioProTrack[])}
				>
					<Text style={styles.smallBtnText}>+ Add All</Text>
				</TouchableOpacity>
				<TouchableOpacity
					style={styles.smallBtn}
					onPress={() => AudioPro.clearMediaItems()}
				>
					<Text style={styles.smallBtnText}>Clear All</Text>
				</TouchableOpacity>
				<TouchableOpacity style={styles.smallBtn} onPress={loadQueue}>
					<Text style={styles.smallBtnText}>Refresh</Text>
				</TouchableOpacity>
			</View>

			{/* Queue Items */}
			{queue.slice(0, 10).map((item, index) => (
				<View
					key={item.id || index}
					style={[styles.queueItem, index === activeIndex && styles.queueItemActive]}
				>
					<TouchableOpacity
						style={styles.queueItemContent}
						onPress={() => AudioPro.seekToMediaItem(index)}
					>
						<Text style={styles.queueIndex}>{index + 1}</Text>
						<View style={styles.queueTextContainer}>
							<Text style={styles.queueTitle} numberOfLines={1}>
								{item.title}
							</Text>
							<Text style={styles.queueArtist} numberOfLines={1}>
								{item.artist}
							</Text>
						</View>
					</TouchableOpacity>
					<TouchableOpacity
						style={styles.queueRemoveBtn}
						onPress={() => AudioPro.removeMediaItem(index)}
					>
						<Text style={styles.queueRemoveText}>✕</Text>
					</TouchableOpacity>
				</View>
			))}

			{queue.length > 10 && (
				<Text style={styles.mutedText}>... and {queue.length - 10} more items</Text>
			)}

			{/* Advanced Queue Operations */}
			<Text style={[styles.sectionTitle, { marginTop: 20 }]}>Queue Operations</Text>
			<View style={styles.row}>
				<TouchableOpacity
					style={styles.smallBtn}
					onPress={() =>
						AudioPro.addMediaItemsAt(0, {
							id: 'inserted',
							url: (playlist[0] as AudioProTrack).url,
							title: 'Inserted at Start',
							artist: 'Demo',
							artwork: (playlist[0] as AudioProTrack).artwork,
						})
					}
				>
					<Text style={styles.smallBtnText}>Insert at 0</Text>
				</TouchableOpacity>
				<TouchableOpacity
					style={styles.smallBtn}
					onPress={() => {
						if (queue.length >= 2) AudioPro.moveMediaItem(0, 1);
					}}
				>
					<Text style={styles.smallBtnText}>Move 0→1</Text>
				</TouchableOpacity>
				<TouchableOpacity
					style={styles.smallBtn}
					onPress={() => {
						if (queue.length >= 2) AudioPro.removeMediaItems(0, 2);
					}}
				>
					<Text style={styles.smallBtnText}>Remove 0-1</Text>
				</TouchableOpacity>
			</View>
			<TouchableOpacity
				style={[styles.smallBtn, { marginTop: 10 }]}
				onPress={() => AudioPro.setMediaItems(playlist.slice(0, 3) as AudioProTrack[])}
			>
				<Text style={styles.smallBtnText}>Replace with First 3</Text>
			</TouchableOpacity>
		</View>
	);
}

// ============ SETTINGS TAB ============
interface SettingsTabProps {
	volume: number;
	playbackSpeed: number;
	skipSilence: boolean;
	progressInterval: number;
	onVolumeChange: (v: number) => void;
	onSpeedChange: (s: number) => void;
	onSkipSilenceToggle: () => void;
	onProgressIntervalChange: (ms: number) => void;
}

function SettingsTab({
	volume,
	playbackSpeed,
	skipSilence,
	progressInterval,
	onVolumeChange,
	onSpeedChange,
	onSkipSilenceToggle,
	onProgressIntervalChange,
}: SettingsTabProps) {
	return (
		<View>
			{/* Volume */}
			<Text style={styles.sectionTitle}>Volume</Text>
			<View style={styles.sliderRow}>
				<Slider
					style={styles.slider}
					minimumValue={0}
					maximumValue={1}
					value={volume}
					onSlidingComplete={onVolumeChange}
					minimumTrackTintColor={COLORS.accent}
					maximumTrackTintColor={COLORS.border}
					thumbTintColor={COLORS.accent}
				/>
				<Text style={styles.sliderValue}>{Math.round(volume * 100)}%</Text>
			</View>

			{/* Speed */}
			<Text style={styles.sectionTitle}>Playback Speed</Text>
			<View style={styles.speedRow}>
				{[0.5, 0.75, 1.0, 1.25, 1.5, 2.0].map((s) => (
					<TouchableOpacity
						key={s}
						style={[styles.speedBtn, playbackSpeed === s && styles.speedBtnActive]}
						onPress={() => onSpeedChange(s)}
					>
						<Text
							style={[
								styles.speedBtnText,
								playbackSpeed === s && styles.speedBtnTextActive,
							]}
						>
							{s}x
						</Text>
					</TouchableOpacity>
				))}
			</View>

			{/* Skip Silence */}
			<Text style={styles.sectionTitle}>Skip Silence</Text>
			<TouchableOpacity
				style={[styles.toggleBtn, skipSilence && styles.toggleBtnActive]}
				onPress={onSkipSilenceToggle}
			>
				<Text style={styles.toggleBtnText}>{skipSilence ? 'Enabled' : 'Disabled'}</Text>
			</TouchableOpacity>

			{/* Progress Interval */}
			<Text style={styles.sectionTitle}>Progress Interval</Text>
			<View style={styles.speedRow}>
				{[250, 500, 1000, 2000].map((ms) => (
					<TouchableOpacity
						key={ms}
						style={[styles.speedBtn, progressInterval === ms && styles.speedBtnActive]}
						onPress={() => onProgressIntervalChange(ms)}
					>
						<Text
							style={[
								styles.speedBtnText,
								progressInterval === ms && styles.speedBtnTextActive,
							]}
						>
							{ms}ms
						</Text>
					</TouchableOpacity>
				))}
			</View>

			{/* State Getters Demo */}
			<Text style={[styles.sectionTitle, { marginTop: 20 }]}>State Getters</Text>
			<TouchableOpacity
				style={styles.smallBtn}
				onPress={() => {
					const timings = AudioPro.getTimings();
					const playbackState = AudioPro.getPlaybackState();
					const currentItem = AudioPro.getCurrentMediaItem();
					const currentIndex = AudioPro.getCurrentMediaItemIndex();
					const speed = AudioPro.getPlaybackSpeed();
					const vol = AudioPro.getVolume();
					const err = AudioPro.getError();
					const interval = AudioPro.getProgressInterval();

					console.log('=== State Getters ===');
					console.log('Timings:', timings);
					console.log('PlaybackState:', playbackState);
					console.log('CurrentItem:', currentItem?.title);
					console.log('CurrentIndex:', currentIndex);
					console.log('Speed:', speed);
					console.log('Volume:', vol);
					console.log('Error:', err);
					console.log('ProgressInterval:', interval);
				}}
			>
				<Text style={styles.smallBtnText}>Log All Getters to Console</Text>
			</TouchableOpacity>
		</View>
	);
}

// ============ EFFECTS TAB ============
interface EffectsTabProps {
	eqGains: number[];
	bassBoost: number;
	selectedPreset: string;
	onEqChange: (index: number, value: number) => void;
	onPresetSelect: (preset: string) => void;
	onBassBoostChange: (v: number) => void;
}

function EffectsTab({
	eqGains,
	bassBoost,
	selectedPreset,
	onEqChange,
	onPresetSelect,
	onBassBoostChange,
}: EffectsTabProps) {
	return (
		<View>
			{/* Presets */}
			<Text style={styles.sectionTitle}>EQ Presets</Text>
			<ScrollView horizontal showsHorizontalScrollIndicator={false}>
				{EQUALIZER_PRESETS.map((preset) => (
					<TouchableOpacity
						key={preset.id}
						style={[
							styles.presetBtn,
							selectedPreset === preset.id && styles.presetBtnActive,
						]}
						onPress={() => onPresetSelect(preset.id)}
					>
						<Text
							style={[
								styles.presetBtnText,
								selectedPreset === preset.id && styles.presetBtnTextActive,
							]}
						>
							{preset.name}
						</Text>
					</TouchableOpacity>
				))}
			</ScrollView>

			{/* EQ Bands */}
			<Text style={[styles.sectionTitle, { marginTop: 20 }]}>10-Band Equalizer</Text>
			{EQUALIZER_BANDS.map((band, index) => (
				<View key={band.frequency} style={styles.eqBandRow}>
					<Text style={styles.eqBandLabel}>{band.label}</Text>
					<Slider
						style={styles.eqSlider}
						minimumValue={-10}
						maximumValue={10}
						value={eqGains[index]}
						onValueChange={(val) => onEqChange(index, val)}
						minimumTrackTintColor={COLORS.success}
						maximumTrackTintColor={COLORS.border}
						thumbTintColor={COLORS.success}
					/>
					<Text style={styles.eqValue}>{eqGains[index]?.toFixed(1)}</Text>
				</View>
			))}

			{/* Bass Boost */}
			<Text style={[styles.sectionTitle, { marginTop: 20 }]}>Bass Boost</Text>
			<View style={styles.sliderRow}>
				<Slider
					style={styles.slider}
					minimumValue={0}
					maximumValue={1000}
					value={bassBoost}
					onSlidingComplete={onBassBoostChange}
					minimumTrackTintColor={COLORS.warning}
					maximumTrackTintColor={COLORS.border}
					thumbTintColor={COLORS.warning}
				/>
				<Text style={styles.sliderValue}>{bassBoost}</Text>
			</View>
		</View>
	);
}

// ============ EXTRAS TAB ============
interface ExtrasTabProps {
	sleepMinutes: number;
	sleepActive: boolean;
	cacheSize: string;
	ambientState: 'stopped' | 'playing' | 'paused';
	ambientVolume: number;
	onSleepMinutesChange: (m: number) => void;
	onSleepStart: () => void;
	onSleepCancel: () => void;
	onClearCache: () => void;
	onRefreshCache: () => void;
	onAmbientPlay: () => void;
	onAmbientPause: () => void;
	onAmbientResume: () => void;
	onAmbientStop: () => void;
	onAmbientVolumeChange: (v: number) => void;
}

function ExtrasTab({
	sleepMinutes,
	sleepActive,
	cacheSize,
	ambientState,
	ambientVolume,
	onSleepMinutesChange,
	onSleepStart,
	onSleepCancel,
	onClearCache,
	onRefreshCache,
	onAmbientPlay,
	onAmbientPause,
	onAmbientResume,
	onAmbientStop,
	onAmbientVolumeChange,
}: ExtrasTabProps) {
	return (
		<View>
			{/* Sleep Timer */}
			<Text style={styles.sectionTitle}>Sleep Timer</Text>
			<View style={styles.speedRow}>
				{[5, 10, 15, 30, 60].map((m) => (
					<TouchableOpacity
						key={m}
						style={[styles.speedBtn, sleepMinutes === m && styles.speedBtnActive]}
						onPress={() => onSleepMinutesChange(m)}
					>
						<Text
							style={[
								styles.speedBtnText,
								sleepMinutes === m && styles.speedBtnTextActive,
							]}
						>
							{m}m
						</Text>
					</TouchableOpacity>
				))}
			</View>
			<View style={[styles.row, { marginTop: 10 }]}>
				<TouchableOpacity
					style={[styles.smallBtn, sleepActive && styles.smallBtnActive]}
					onPress={sleepActive ? onSleepCancel : onSleepStart}
				>
					<Text style={styles.smallBtnText}>
						{sleepActive ? '⏰ Cancel Timer' : '⏰ Start Timer'}
					</Text>
				</TouchableOpacity>
			</View>

			{/* Cache */}
			<Text style={[styles.sectionTitle, { marginTop: 20 }]}>Cache Management</Text>
			<Text style={styles.cacheText}>Cache Size: {cacheSize}</Text>
			<View style={styles.row}>
				<TouchableOpacity style={styles.smallBtn} onPress={onRefreshCache}>
					<Text style={styles.smallBtnText}>Refresh</Text>
				</TouchableOpacity>
				<TouchableOpacity
					style={[styles.smallBtn, styles.dangerBtn]}
					onPress={onClearCache}
				>
					<Text style={styles.smallBtnText}>Clear Cache</Text>
				</TouchableOpacity>
			</View>

			{/* Ambient Audio */}
			<Text style={[styles.sectionTitle, { marginTop: 20 }]}>Ambient Audio</Text>
			<Text style={styles.mutedText}>State: {ambientState}</Text>
			<View style={styles.row}>
				{ambientState === 'stopped' && (
					<TouchableOpacity style={styles.smallBtn} onPress={onAmbientPlay}>
						<Text style={styles.smallBtnText}>▶️ Play</Text>
					</TouchableOpacity>
				)}
				{ambientState === 'playing' && (
					<TouchableOpacity style={styles.smallBtn} onPress={onAmbientPause}>
						<Text style={styles.smallBtnText}>⏸️ Pause</Text>
					</TouchableOpacity>
				)}
				{ambientState === 'paused' && (
					<TouchableOpacity style={styles.smallBtn} onPress={onAmbientResume}>
						<Text style={styles.smallBtnText}>▶️ Resume</Text>
					</TouchableOpacity>
				)}
				{ambientState !== 'stopped' && (
					<TouchableOpacity style={styles.smallBtn} onPress={onAmbientStop}>
						<Text style={styles.smallBtnText}>⏹️ Stop</Text>
					</TouchableOpacity>
				)}
			</View>

			{/* Ambient Volume */}
			{ambientState !== 'stopped' && (
				<View style={styles.sliderRow}>
					<Text style={styles.mutedText}>Vol:</Text>
					<Slider
						style={styles.slider}
						minimumValue={0}
						maximumValue={1}
						value={ambientVolume}
						onSlidingComplete={onAmbientVolumeChange}
						minimumTrackTintColor={COLORS.accent}
						maximumTrackTintColor={COLORS.border}
						thumbTintColor={COLORS.accent}
					/>
					<Text style={styles.sliderValue}>{Math.round(ambientVolume * 100)}%</Text>
				</View>
			)}

			{/* Ambient Seek */}
			{ambientState !== 'stopped' && (
				<View style={styles.row}>
					<TouchableOpacity
						style={styles.smallBtn}
						onPress={() => AudioPro.ambientSeekTo(0)}
					>
						<Text style={styles.smallBtnText}>Seek to 0s</Text>
					</TouchableOpacity>
					<TouchableOpacity
						style={styles.smallBtn}
						onPress={() => AudioPro.ambientSeekTo(30000)}
					>
						<Text style={styles.smallBtnText}>Seek to 30s</Text>
					</TouchableOpacity>
				</View>
			)}

			{/* Update Track Demo */}
			<Text style={[styles.sectionTitle, { marginTop: 20 }]}>Track Update</Text>
			<TouchableOpacity
				style={styles.smallBtn}
				onPress={async () => {
					const items = await AudioPro.getMediaItems();
					if (items.length > 0) {
						const updated = { ...items[0]!, title: 'Updated Title @ ' + Date.now() };
						AudioPro.updateTrack(0, updated);
						console.log('Updated track 0 title');
					}
				}}
			>
				<Text style={styles.smallBtnText}>Update Track #1 Title</Text>
			</TouchableOpacity>
		</View>
	);
}

// ============ STYLES ============
const styles = StyleSheet.create({
	container: {
		flex: 1,
		backgroundColor: COLORS.bg,
	},
	scroll: {
		padding: 16,
		paddingBottom: 50,
	},
	header: {
		fontSize: 20,
		fontWeight: '700',
		color: COLORS.text,
		textAlign: 'center',
		marginVertical: 10,
	},

	// Now Playing
	nowPlayingCard: {
		backgroundColor: COLORS.card,
		borderRadius: 16,
		padding: 20,
		alignItems: 'center',
		marginBottom: 16,
	},
	artwork: {
		width: 200,
		height: 200,
		borderRadius: 12,
		backgroundColor: COLORS.cardLight,
		marginBottom: 16,
	},
	trackTextContainer: {
		alignItems: 'center',
		marginBottom: 12,
	},
	trackTitle: {
		fontSize: 18,
		fontWeight: '600',
		color: COLORS.text,
		marginBottom: 4,
	},
	trackArtist: {
		fontSize: 14,
		color: COLORS.textSecondary,
	},
	actionRow: {
		flexDirection: 'row',
		gap: 16,
	},
	actionBtn: {
		padding: 8,
	},
	actionIcon: {
		fontSize: 24,
	},
	actionActive: {
		transform: [{ scale: 1.1 }],
	},

	// Progress
	progressContainer: {
		position: 'relative',
		marginBottom: 4,
	},
	progressSlider: {
		width: '100%',
		height: 40,
	},
	bufferedBar: {
		position: 'absolute',
		left: 0,
		top: 18,
		height: 4,
		backgroundColor: 'rgba(99, 102, 241, 0.3)',
		borderRadius: 2,
	},
	timeRow: {
		flexDirection: 'row',
		justifyContent: 'space-between',
		marginBottom: 16,
	},
	timeText: {
		fontSize: 12,
		color: COLORS.textMuted,
		fontVariant: ['tabular-nums'],
	},

	// Controls
	controlsRow: {
		flexDirection: 'row',
		alignItems: 'center',
		justifyContent: 'center',
		gap: 8,
		marginBottom: 12,
	},
	controlBtn: {
		padding: 8,
	},
	sideControlBtn: {
		padding: 6,
	},
	controlIcon: {
		fontSize: 24,
		opacity: 0.7,
	},
	controlIconActive: {
		opacity: 1,
		color: COLORS.accent,
	},
	seekText: {
		fontSize: 12,
		color: COLORS.textSecondary,
		fontWeight: '600',
	},
	playBtn: {
		width: 64,
		height: 64,
		borderRadius: 32,
		backgroundColor: COLORS.accent,
		alignItems: 'center',
		justifyContent: 'center',
		marginHorizontal: 8,
	},
	playIcon: {
		fontSize: 28,
	},
	stopBtn: {
		alignSelf: 'center',
		paddingVertical: 8,
		paddingHorizontal: 16,
		backgroundColor: COLORS.cardLight,
		borderRadius: 8,
		marginBottom: 16,
	},
	stopBtnText: {
		color: COLORS.textSecondary,
		fontSize: 14,
	},

	// Error
	errorBox: {
		backgroundColor: 'rgba(239, 68, 68, 0.15)',
		borderRadius: 8,
		padding: 12,
		marginBottom: 16,
	},
	errorText: {
		color: COLORS.error,
		fontSize: 14,
		textAlign: 'center',
	},
	errorSubtext: {
		color: COLORS.textMuted,
		fontSize: 12,
		textAlign: 'center',
		marginTop: 4,
	},

	// Tabs
	tabBar: {
		flexDirection: 'row',
		backgroundColor: COLORS.card,
		borderRadius: 12,
		padding: 4,
		marginBottom: 16,
	},
	tabItem: {
		flex: 1,
		paddingVertical: 10,
		alignItems: 'center',
		borderRadius: 8,
	},
	tabItemActive: {
		backgroundColor: COLORS.accent,
	},
	tabText: {
		fontSize: 13,
		fontWeight: '500',
		color: COLORS.textMuted,
	},
	tabTextActive: {
		color: COLORS.text,
	},
	tabContent: {
		backgroundColor: COLORS.card,
		borderRadius: 12,
		padding: 16,
	},

	// Section
	sectionTitle: {
		fontSize: 14,
		fontWeight: '600',
		color: COLORS.textSecondary,
		marginBottom: 12,
	},
	row: {
		flexDirection: 'row',
		flexWrap: 'wrap',
		gap: 8,
	},
	smallBtn: {
		paddingVertical: 8,
		paddingHorizontal: 14,
		backgroundColor: COLORS.cardLight,
		borderRadius: 8,
	},
	smallBtnActive: {
		backgroundColor: COLORS.accent,
	},
	smallBtnText: {
		color: COLORS.text,
		fontSize: 13,
		fontWeight: '500',
	},
	dangerBtn: {
		backgroundColor: COLORS.error,
	},
	mutedText: {
		color: COLORS.textMuted,
		fontSize: 13,
		marginBottom: 8,
	},
	cacheText: {
		color: COLORS.textSecondary,
		fontSize: 14,
		marginBottom: 12,
	},

	// Queue
	queueItem: {
		flexDirection: 'row',
		alignItems: 'center',
		paddingVertical: 10,
		paddingHorizontal: 12,
		backgroundColor: COLORS.cardLight,
		borderRadius: 8,
		marginBottom: 6,
	},
	queueItemActive: {
		backgroundColor: COLORS.accentDim,
	},
	queueItemContent: {
		flex: 1,
		flexDirection: 'row',
		alignItems: 'center',
	},
	queueIndex: {
		width: 24,
		fontSize: 12,
		color: COLORS.textMuted,
		fontWeight: '600',
	},
	queueTextContainer: {
		flex: 1,
	},
	queueTitle: {
		fontSize: 14,
		color: COLORS.text,
		fontWeight: '500',
	},
	queueArtist: {
		fontSize: 12,
		color: COLORS.textMuted,
	},
	queueRemoveBtn: {
		padding: 8,
	},
	queueRemoveText: {
		color: COLORS.textMuted,
		fontSize: 14,
	},

	// Slider
	sliderRow: {
		flexDirection: 'row',
		alignItems: 'center',
		marginBottom: 16,
	},
	slider: {
		flex: 1,
		height: 40,
	},
	sliderValue: {
		width: 50,
		textAlign: 'right',
		color: COLORS.textSecondary,
		fontSize: 13,
	},

	// Speed
	speedRow: {
		flexDirection: 'row',
		flexWrap: 'wrap',
		gap: 8,
		marginBottom: 16,
	},
	speedBtn: {
		paddingVertical: 8,
		paddingHorizontal: 12,
		backgroundColor: COLORS.cardLight,
		borderRadius: 8,
	},
	speedBtnActive: {
		backgroundColor: COLORS.accent,
	},
	speedBtnText: {
		color: COLORS.textMuted,
		fontSize: 13,
		fontWeight: '500',
	},
	speedBtnTextActive: {
		color: COLORS.text,
	},

	// Toggle
	toggleBtn: {
		paddingVertical: 10,
		paddingHorizontal: 16,
		backgroundColor: COLORS.cardLight,
		borderRadius: 8,
		alignSelf: 'flex-start',
		marginBottom: 16,
	},
	toggleBtnActive: {
		backgroundColor: COLORS.success,
	},
	toggleBtnText: {
		color: COLORS.text,
		fontSize: 14,
		fontWeight: '500',
	},

	// EQ
	eqBandRow: {
		flexDirection: 'row',
		alignItems: 'center',
		marginBottom: 4,
	},
	eqBandLabel: {
		width: 50,
		color: COLORS.textMuted,
		fontSize: 11,
	},
	eqSlider: {
		flex: 1,
		height: 30,
	},
	eqValue: {
		width: 40,
		textAlign: 'right',
		color: COLORS.textSecondary,
		fontSize: 11,
	},

	// Presets
	presetBtn: {
		paddingVertical: 8,
		paddingHorizontal: 14,
		backgroundColor: COLORS.cardLight,
		borderRadius: 8,
		marginRight: 8,
	},
	presetBtnActive: {
		backgroundColor: COLORS.accent,
	},
	presetBtnText: {
		color: COLORS.textMuted,
		fontSize: 13,
	},
	presetBtnTextActive: {
		color: COLORS.text,
	},
});
