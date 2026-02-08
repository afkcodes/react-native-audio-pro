import React, { useState } from 'react';

import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';

interface AmbientPlayerProps {
	onPlay: () => void;
	onStop: () => void;
	onPause: () => void;
	onResume: () => void;
}

export const AmbientPlayer: React.FC<AmbientPlayerProps> = ({
	onPlay,
	onStop,
	onPause,
	onResume,
}) => {
	const [state, setState] = useState<'stopped' | 'playing' | 'paused'>('stopped');

	const handlePlay = () => {
		onPlay();
		setState('playing');
	};

	const handleStop = () => {
		onStop();
		setState('stopped');
	};

	const handleToggle = () => {
		if (state === 'playing') {
			onPause();
			setState('paused');
		} else if (state === 'paused') {
			onResume();
			setState('playing');
		}
	};

	return (
		<View style={styles.container}>
			<Text style={styles.title}>Ambient Audio</Text>
			<View style={styles.controls}>
				{state === 'stopped' ? (
					<TouchableOpacity onPress={handlePlay} style={styles.button}>
						<Text style={styles.buttonText}>Play Ambient</Text>
					</TouchableOpacity>
				) : (
					<TouchableOpacity onPress={handleToggle} style={styles.button}>
						<Text style={styles.buttonText}>
							{state === 'playing' ? 'Pause' : 'Resume'}
						</Text>
					</TouchableOpacity>
				)}
				<TouchableOpacity onPress={handleStop} style={[styles.button, styles.stopButton]}>
					<Text style={styles.buttonText}>Stop</Text>
				</TouchableOpacity>
			</View>
		</View>
	);
};

const styles = StyleSheet.create({
	container: {
		backgroundColor: '#222',
		padding: 15,
		borderRadius: 10,
		marginBottom: 20,
	},
	title: {
		color: '#fff',
		fontSize: 16,
		fontWeight: 'bold',
		marginBottom: 10,
		textAlign: 'center',
	},
	controls: {
		flexDirection: 'row',
		justifyContent: 'center',
	},
	button: {
		paddingVertical: 8,
		paddingHorizontal: 15,
		backgroundColor: '#1E90FF',
		borderRadius: 5,
		marginHorizontal: 5,
	},
	stopButton: {
		backgroundColor: '#555',
	},
	buttonText: {
		color: '#fff',
		fontWeight: 'bold',
	},
});
