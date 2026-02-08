import React from 'react';

import { Image, StyleSheet, Text, TouchableOpacity, View } from 'react-native';

import { AudioProState } from 'react-native-audio-pro';

interface PlayerControlsProps {
	state: AudioProState;
	onPlayPause: () => void;
	onNext: () => void;
	onPrev: () => void;
	onSeekForward: () => void;
	onSeekBack: () => void;
}

export const PlayerControls: React.FC<PlayerControlsProps> = ({
	state,
	onPlayPause,
	onNext,
	onPrev,
	onSeekForward,
	onSeekBack,
}) => {
	const isPlaying = state === AudioProState.PLAYING || state === AudioProState.LOADING;

	return (
		<View style={styles.container}>
			<TouchableOpacity onPress={onPrev}>
				<Image
					source={{
						uri: 'https://img.icons8.com/ios-glyphs/60/ffffff/skip-to-start.png',
					}}
					style={styles.controlIcon}
				/>
			</TouchableOpacity>

			<TouchableOpacity onPress={onSeekBack}>
				<Text style={styles.seekText}>-5s</Text>
			</TouchableOpacity>

			<TouchableOpacity onPress={onPlayPause} style={styles.playPauseButton}>
				<Image
					source={{
						uri: isPlaying
							? 'https://img.icons8.com/ios-glyphs/60/ffffff/pause--v1.png'
							: 'https://img.icons8.com/ios-glyphs/60/ffffff/play--v1.png',
					}}
					style={styles.playPauseIcon}
				/>
			</TouchableOpacity>

			<TouchableOpacity onPress={onSeekForward}>
				<Text style={styles.seekText}>+5s</Text>
			</TouchableOpacity>

			<TouchableOpacity onPress={onNext}>
				<Image
					source={{
						uri: 'https://img.icons8.com/ios-glyphs/60/ffffff/end--v1.png',
					}}
					style={styles.controlIcon}
				/>
			</TouchableOpacity>
		</View>
	);
};

const styles = StyleSheet.create({
	container: {
		flexDirection: 'row',
		justifyContent: 'space-around',
		alignItems: 'center',
		width: '100%',
		marginBottom: 20,
	},
	controlIcon: {
		width: 35,
		height: 35,
		tintColor: '#FFFFFF',
	},
	playPauseButton: {
		backgroundColor: '#1EB1FC',
		borderRadius: 40,
		width: 70,
		height: 70,
		justifyContent: 'center',
		alignItems: 'center',
	},
	playPauseIcon: {
		width: 30,
		height: 30,
		tintColor: '#FFFFFF',
	},
	seekText: {
		color: '#fff',
		fontSize: 14,
	},
});
