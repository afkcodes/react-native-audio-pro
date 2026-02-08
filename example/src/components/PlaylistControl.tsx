import React, { useState } from 'react';

import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';

interface PlaylistControlProps {
	onToggleLoop: (mode: 'OFF' | 'ONE' | 'ALL') => void;
	onToggleShuffle: (enabled: boolean) => void;
	onToggleSkipSilence: (enabled: boolean) => void;
	onAddToQueue: () => void;
	onClearQueue: () => void;
	onLogQueue: () => void;
}

export const PlaylistControl: React.FC<PlaylistControlProps> = ({
	onToggleLoop,
	onToggleShuffle,
	onToggleSkipSilence,
	onAddToQueue,
	onClearQueue,
	onLogQueue,
}) => {
	const [loopMode, setLoopMode] = useState<'OFF' | 'ONE' | 'ALL'>('OFF');
	const [shuffle, setShuffle] = useState(false);
	const [skipSilence, setSkipSilence] = useState(false);

	const handleLoop = () => {
		const modes: ('OFF' | 'ONE' | 'ALL')[] = ['OFF', 'ONE', 'ALL'];
		const currentIndex = modes.indexOf(loopMode);
		const nextIndex = (currentIndex + 1) % modes.length;
		const next = modes[nextIndex] || 'OFF';
		setLoopMode(next);
		onToggleLoop(next);
	};

	const handleShuffle = () => {
		const next = !shuffle;
		setShuffle(next);
		onToggleShuffle(next);
	};

	const handleSkipSilence = () => {
		const next = !skipSilence;
		setSkipSilence(next);
		onToggleSkipSilence(next);
	};

	return (
		<View style={styles.container}>
			<View style={styles.row}>
				<TouchableOpacity onPress={handleLoop} style={styles.button}>
					<Text style={styles.buttonText}>Loop: {loopMode}</Text>
				</TouchableOpacity>

				<TouchableOpacity onPress={handleShuffle} style={styles.button}>
					<Text style={styles.buttonText}>Shuffle: {shuffle ? 'ON' : 'OFF'}</Text>
				</TouchableOpacity>
			</View>

			<View style={styles.row}>
				<TouchableOpacity onPress={handleSkipSilence} style={styles.button}>
					<Text style={styles.buttonText}>
						Skip Silence: {skipSilence ? 'ON' : 'OFF'}
					</Text>
				</TouchableOpacity>
			</View>

			<View style={styles.queueRow}>
				<TouchableOpacity onPress={onAddToQueue} style={styles.smallButton}>
					<Text style={styles.smallButtonText}>+Queue</Text>
				</TouchableOpacity>
				<TouchableOpacity onPress={onClearQueue} style={styles.smallButton}>
					<Text style={styles.smallButtonText}>ClearQ</Text>
				</TouchableOpacity>
				<TouchableOpacity onPress={onLogQueue} style={styles.smallButton}>
					<Text style={styles.smallButtonText}>LogQ</Text>
				</TouchableOpacity>
			</View>
		</View>
	);
};

const styles = StyleSheet.create({
	container: {
		marginBottom: 20,
	},
	row: {
		flexDirection: 'row',
		justifyContent: 'center',
		marginBottom: 10,
	},
	queueRow: {
		flexDirection: 'row',
		justifyContent: 'space-around',
		marginTop: 10,
	},
	button: {
		padding: 10,
		backgroundColor: '#333',
		borderRadius: 5,
		marginHorizontal: 5,
	},
	buttonText: {
		color: '#fff',
		fontSize: 14,
	},
	smallButton: {
		padding: 8,
		backgroundColor: '#444',
		borderRadius: 5,
	},
	smallButtonText: {
		color: '#eee',
		fontSize: 12,
	},
});
