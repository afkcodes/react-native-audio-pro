import React, { useEffect, useState } from 'react';

import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';

import { AudioPro } from 'react-native-audio-pro';

export const CacheManager: React.FC = () => {
	const [cacheSize, setCacheSize] = useState<string>('Checking...');

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

	useEffect(() => {
		checkCache();
		// Listen for track changes to update cache size dynamically
		const sub = AudioPro.addEventListener((event) => {
			if (event.type === 'TRACK_CHANGED') {
				checkCache();
			}
		});
		return () => sub.remove();
	}, []);

	return (
		<View style={styles.container}>
			<Text style={styles.title}>Cache Manager</Text>
			<Text style={styles.sizeText}>Size: {cacheSize}</Text>
			<View style={styles.controls}>
				<TouchableOpacity onPress={checkCache} style={styles.button}>
					<Text style={styles.buttonText}>Refresh</Text>
				</TouchableOpacity>
				<TouchableOpacity
					onPress={handleClearCache}
					style={[styles.button, styles.clearButton]}
				>
					<Text style={styles.buttonText}>Clear Cache</Text>
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
		marginBottom: 5,
		textAlign: 'center',
	},
	sizeText: {
		color: '#ccc',
		fontSize: 14,
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
		backgroundColor: '#555',
		borderRadius: 5,
		marginHorizontal: 5,
	},
	clearButton: {
		backgroundColor: '#d32f2f',
	},
	buttonText: {
		color: '#fff',
		fontWeight: 'bold',
	},
});
