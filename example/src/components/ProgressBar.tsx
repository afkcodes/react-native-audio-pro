import React from 'react';

import { StyleSheet, Text, View } from 'react-native';

import Slider from '@react-native-community/slider';

import { formatTime } from '../utils';

interface ProgressBarProps {
	position: number;
	duration: number;
	onSeek: (value: number) => void;
}

export const ProgressBar: React.FC<ProgressBarProps> = ({ position, duration, onSeek }) => {
	return (
		<View style={styles.container}>
			<Text style={styles.timeText}>{formatTime(position)}</Text>
			<Slider
				style={styles.slider}
				minimumValue={0}
				maximumValue={duration}
				value={position}
				minimumTrackTintColor="#1EB1FC"
				maximumTrackTintColor="#8E8E93"
				thumbTintColor="#1EB1FC"
				onSlidingComplete={onSeek}
			/>
			<Text style={styles.timeText}>{formatTime(Math.max(0, duration - position))}</Text>
		</View>
	);
};

const styles = StyleSheet.create({
	container: {
		flexDirection: 'row',
		alignItems: 'center',
		width: '100%',
		marginBottom: 20,
	},
	slider: {
		flex: 1,
		marginHorizontal: 10,
	},
	timeText: {
		color: '#FFFFFF',
		fontSize: 12,
		fontVariant: ['tabular-nums'],
		width: 45,
		textAlign: 'center',
	},
});
