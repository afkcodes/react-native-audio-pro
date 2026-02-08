import React, { useState } from 'react';

import { StyleSheet, Text, TextInput, TouchableOpacity, View } from 'react-native';

interface SleepTimerProps {
	onStart: (seconds: number) => void;
	onCancel: () => void;
}

export const SleepTimer: React.FC<SleepTimerProps> = ({ onStart, onCancel }) => {
	const [seconds, setSeconds] = useState('10');

	const handleStart = () => {
		const sec = parseInt(seconds, 10);
		if (!isNaN(sec) && sec > 0) {
			onStart(sec);
		}
	};

	return (
		<View style={styles.container}>
			<Text style={styles.title}>Sleep Timer</Text>
			<View style={styles.inputContainer}>
				<Text style={styles.label}>Seconds:</Text>
				<TextInput
					style={styles.input}
					value={seconds}
					onChangeText={setSeconds}
					keyboardType="numeric"
					placeholderTextColor="#666"
				/>
			</View>
			<View style={styles.controls}>
				<TouchableOpacity onPress={handleStart} style={styles.button}>
					<Text style={styles.buttonText}>Start</Text>
				</TouchableOpacity>
				<TouchableOpacity onPress={onCancel} style={[styles.button, styles.cancelButton]}>
					<Text style={styles.buttonText}>Cancel</Text>
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
	inputContainer: {
		flexDirection: 'row',
		alignItems: 'center',
		justifyContent: 'center',
		marginBottom: 10,
	},
	label: {
		color: '#ccc',
		marginRight: 10,
	},
	input: {
		backgroundColor: '#333',
		color: '#fff',
		padding: 5,
		width: 60,
		borderRadius: 4,
		textAlign: 'center',
	},
	controls: {
		flexDirection: 'row',
		justifyContent: 'center',
	},
	button: {
		paddingVertical: 8,
		paddingHorizontal: 15,
		backgroundColor: '#1db954',
		borderRadius: 5,
		marginHorizontal: 5,
	},
	cancelButton: {
		backgroundColor: '#d32f2f',
	},
	buttonText: {
		color: '#fff',
		fontWeight: 'bold',
	},
});
