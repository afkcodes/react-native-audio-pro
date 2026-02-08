import React from 'react';

import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';

interface VolumeControlProps {
	volume: number;
	onIncrease: () => void;
	onDecrease: () => void;
}

export const VolumeControl: React.FC<VolumeControlProps> = ({ volume, onIncrease, onDecrease }) => {
	return (
		<View style={styles.container}>
			<TouchableOpacity onPress={onDecrease} style={styles.button}>
				<Text style={styles.buttonText}>-</Text>
			</TouchableOpacity>
			<Text style={styles.valueText}>Vol: {Math.round(volume * 100)}%</Text>
			<TouchableOpacity onPress={onIncrease} style={styles.button}>
				<Text style={styles.buttonText}>+</Text>
			</TouchableOpacity>
		</View>
	);
};

const styles = StyleSheet.create({
	container: {
		flexDirection: 'row',
		alignItems: 'center',
		justifyContent: 'center',
		marginVertical: 10,
	},
	button: {
		padding: 10,
		backgroundColor: '#333',
		borderRadius: 5,
		marginHorizontal: 10,
		minWidth: 40,
		alignItems: 'center',
	},
	buttonText: {
		color: '#fff',
		fontSize: 18,
		fontWeight: 'bold',
	},
	valueText: {
		color: '#fff',
		fontSize: 16,
		minWidth: 80,
		textAlign: 'center',
	},
});
